# -*- coding: utf-8 -*-
"""
VideoRotator 启动图标生成器
设计：VR 字母组合标记
  - V 只保留左侧斜线，底部与 R 的左侧竖线平齐连接
  - 字母左右各一条旋转箭头弧线包络（顺时针旋转感）
  - 所有笔画（字母、弧线）统一线宽，端点 / 拐角 / 箭头全部圆角
输出：
  - legacy PNG (mdpi~xxxhdpi) + round 版本
  - 自适应图标前景 PNG (各密度)
用法：python3 tools/gen_icon.py
"""
import math
import os
from PIL import Image, ImageDraw

S = 1000          # 设计空间边长
SS = 4            # 超采样倍数
N = S * SS        # 实际渲染边长

W = 76            # 统一笔画宽度（设计空间）

C1 = (0xB3, 0x9D, 0xDB)   # 浅紫（左上）
C2 = (0x7E, 0x57, 0xC2)   # 深紫（右下）

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(_PROJECT_ROOT, "app", "src", "main", "res")


class Pen:
    """在 n x n 的灰度蒙版上用圆头圆角的粗线绘制（坐标为 1000 设计空间）"""

    def __init__(self, n):
        self.k = n / S
        self.img = Image.new("L", (n, n), 0)
        self.d = ImageDraw.Draw(self.img)

    def _p(self, x, y):
        return (x * self.k, y * self.k)

    def dot(self, x, y, r):
        cx, cy = self._p(x, y)
        rr = r * self.k
        self.d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=255)

    def polyline(self, pts, w=W):
        """折线：线段 + 每个顶点画圆，得到圆头圆角"""
        self.d.line([self._p(*p) for p in pts], fill=255, width=max(1, round(w * self.k)))
        for p in pts:
            self.dot(*p, w / 2)

    def arc(self, cx, cy, r, a0, a1, w=W):
        """圆弧笔画（角度为 PIL 约定：从 3 点钟方向顺时针，单位度）"""
        if a1 < a0:
            a1 += 360
        steps = max(8, int((a1 - a0) * 2))
        pts = []
        for i in range(steps + 1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        self.polyline(pts, w)

    def rounded_triangle(self, pts, corner):
        """圆角三角形：内缩三角形 ⊕ 半径 corner 的圆（闵可夫斯基和）"""
        self.d.polygon([self._p(*p) for p in pts], fill=255)
        self.polyline(list(pts) + [pts[0]], corner * 2)


def arrow_head(pen, cx, cy, r, ang):
    """在圆弧 ang 度处沿切线方向（角度增大方向）画圆角箭头"""
    corner = W * 0.2
    reach = W * 1.0                          # 箭尖超出弧端的距离
    back = W * 0.3                           # 箭头底边落后于弧端的距离
    half = W * 0.85                          # 底边半宽
    a = math.radians(ang)
    px, py = cx + r * math.cos(a), cy + r * math.sin(a)
    # 箭头朝向取弧端前方半个箭头处的切线，让箭头顺着弧线弯曲方向，不向外撇
    h = a + reach / (2 * r)
    tx, ty = -math.sin(h), math.cos(h)       # 切线
    ox, oy = -ty, tx                         # 法线
    tip = (px + reach * tx, py + reach * ty)
    bx, by = px - back * tx, py - back * ty
    c1 = (bx + half * ox, by + half * oy)
    c2 = (bx - half * ox, by - half * oy)
    pen.rounded_triangle([tip, c1, c2], corner)


LETTER_SCALE = 0.8   # 字母相对画布中心的缩放，给两侧箭头留出间距


def _L(x, y):
    """字母坐标：以 (500, 500) 为中心缩放"""
    return (500 + (x - 500) * LETTER_SCALE, 500 + (y - 500) * LETTER_SCALE)


def render_glyph(n):
    """返回 n x n 的白色字形 RGBA（透明底）"""
    pen = Pen(n)

    # ---- V 左侧斜线 + R 竖线：一笔折线，底部尖角处圆角相连 ----
    pen.polyline([_L(226, 262), _L(512, 738), _L(512, 262)])

    # ---- R 肚：上横 → 半圆 → 下横，一笔完成 ----
    top, bottom, bx = 262, 482, 640
    rr = (bottom - top) / 2
    pen.polyline([_L(512, top), _L(bx, top)])
    cx, cy = _L(bx, top + rr)
    pen.arc(cx, cy, rr * LETTER_SCALE, -90, 90)
    pen.polyline([_L(bx, bottom), _L(512, bottom)])

    # ---- R 斜腿 ----
    pen.polyline([_L(600, bottom), _L(772, 738)])

    # ---- 旋转箭头：左弧 130°→228°（箭头朝右上），右弧 310°→48°（箭头朝左下）----
    R = 420
    pen.arc(500, 500, R, 132, 226)
    arrow_head(pen, 500, 500, R, 226)
    pen.arc(500, 500, R, 312, 406)
    arrow_head(pen, 500, 500, R, 46)

    glyph = Image.new("RGBA", (n, n), (255, 255, 255, 0))
    glyph.putalpha(pen.img)
    return glyph


def make_gradient(n):
    """对角线性渐变：左上浅紫 -> 右下深紫"""
    g = 512
    img = Image.new("RGB", (g, g))
    px = img.load()
    for y in range(g):
        for x in range(g):
            t = (x + y) / (2 * (g - 1))
            px[x, y] = tuple(int(C1[i] + (C2[i] - C1[i]) * t) for i in range(3))
    return img.resize((n, n), Image.BICUBIC)


def scale_about_center(img, factor):
    """以中心缩放并保持画布尺寸"""
    w, _ = img.size
    nw = max(1, int(w * factor))
    small = img.resize((nw, nw), Image.LANCZOS)
    canvas = Image.new("RGBA", img.size, (0, 0, 0, 0))
    canvas.paste(small, ((w - nw) // 2, (w - nw) // 2))
    return canvas


def main():
    glyph_hi = render_glyph(N)                       # 4000px 字形
    gradient_hi = make_gradient(N)

    # legacy 图标内容缩放：给圆形遮罩留出边距，避免箭头贴边
    glyph_legacy = scale_about_center(glyph_hi, 0.84)

    # ---------- legacy 图标 ----------
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, size in densities.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        ss = size * SS
        g = gradient_hi.resize((ss, ss), Image.BICUBIC).convert("RGBA")
        fg = glyph_legacy.resize((ss, ss), Image.LANCZOS)
        icon = Image.new("RGBA", (ss, ss))
        icon.paste(g)
        icon.alpha_composite(fg)
        icon = icon.resize((size, size), Image.LANCZOS)
        icon.convert("RGB").save(os.path.join(folder, "ic_launcher.png"))

        # 圆形版本
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
        m = m.resize((size, size), Image.LANCZOS)
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0), m)
        round_icon.save(os.path.join(folder, "ic_launcher_round.png"))

    # ---------- 自适应图标前景 ----------
    # 内容最大半径约 476/500，缩放 0.6 后直径约 61.7dp，落在 66dp 安全区内
    fg_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    scaled = scale_about_center(glyph_hi, 0.6)
    for name, size in fg_sizes.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        scaled.resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_foreground.png"))

    print("icons generated")


if __name__ == "__main__":
    main()
