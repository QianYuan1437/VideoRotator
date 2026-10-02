# -*- coding: utf-8 -*-
"""
VideoRotator 启动图标生成器
设计：VR 字母组合标记
  - V 只保留左侧斜线，底部与 R 的左侧竖线平齐连接
  - 字母左右各一条旋转箭头弧线包络（顺时针旋转感）
输出：
  - legacy PNG (mdpi~xxxhdpi) + round 版本
  - 自适应图标前景 PNG (各密度)
"""
import math
import os
from PIL import Image, ImageChops, ImageDraw

S = 1000          # 设计空间边长
SS = 4            # 超采样倍数
N = S * SS        # 实际渲染边长

C1 = (0xB3, 0x9D, 0xDB)   # 浅紫（左上）
C2 = (0x7E, 0x57, 0xC2)   # 深紫（右下）

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(_PROJECT_ROOT, "app", "src", "main", "res")

def P(x, y):
    """设计空间 -> 渲染空间"""
    return (x * SS, y * SS)

def bb(cx, cy, r):
    """圆的外接矩形（渲染空间）"""
    return [P(cx - r, cy - r), P(cx + r, cy + r)]

class Glyph:
    """在 1000x1000 设计空间绘制白色字形蒙版"""

    def __init__(self, draw):
        self.d = draw

    def poly(self, pts, v=255):
        self.d.polygon([P(*p) for p in pts], fill=v)

    def rect(self, x0, y0, x1, y1, v=255):
        self.d.rectangle([P(x0, y0), P(x1, y1)], fill=v)

    def ring(self, cx, cy, r, w, a0, a1):
        """居中于半径 r 的圆弧带（扇形相减）"""
        self.d.pieslice(bb(cx, cy, r + w / 2), a0, a1, fill=255)
        self.d.pieslice(bb(cx, cy, r - w / 2), a0, a1, fill=0)

    def head(self, cx, cy, r, ang, reach=38, back=18, half=33):
        """在 ang 度处沿切线绘制箭头三角形"""
        a = math.radians(ang)
        px, py = cx + r * math.cos(a), cy + r * math.sin(a)
        tx, ty = -math.sin(a), math.cos(a)      # 角度增加方向的单位切线
        ox, oy = -ty, tx                        # 法线
        tip = P(px + reach * tx, py + reach * ty)
        base = (px - back * tx, py - back * ty)
        c1 = P(base[0] + half * ox, base[1] + half * oy)
        c2 = P(base[0] - half * ox, base[1] - half * oy)
        self.d.polygon([tip, c1, c2], fill=255)

    def draw_letters(self):
        # ---- V 左侧斜线：顶边 (182..256, 250) -> 底边 (486..546, 750)，与 R 竖线底部齐平连接 ----
        self.poly([(182, 250), (256, 250), (546, 750), (486, 750)])

        # ---- R 左侧竖线 ----
        self.rect(486, 250, 546, 750)

        # ---- R 肚：右半环（外 r=120 / 内 r=60，圆心 666,370）+ 上下横梁 ----
        self.d.pieslice(bb(666, 370, 120), -90, 90, fill=255)
        self.d.pieslice(bb(666, 370, 60), -90, 90, fill=0)
        self.rect(486, 250, 666, 310)
        self.rect(486, 430, 666, 490)

        # ---- R 斜腿 ----
        self.poly([(549, 430), (624, 430), (804, 750), (729, 750)])

    def draw_arrows(self):
        # ---- 左旋转箭头：130° -> 230°，箭头在 230°（左上），切线朝右上 ----
        self.ring(500, 500, 440, 40, 130, 230)
        self.head(500, 500, 440, 230)

        # ---- 右旋转箭头：310° -> 50°，箭头在 50°（右下），切线朝左下 ----
        self.ring(500, 500, 440, 40, 310, 360)
        self.ring(500, 500, 440, 40, 0, 50)
        self.head(500, 500, 440, 50)

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

def render_glyph(n):
    """返回 n x n 的白色字形 RGBA（透明底）"""
    scale = n / S

    # 包一层，把 P/bb 重映射到目标尺寸
    class Rasterizer(Glyph):
        def poly(self, pts, v=255):
            self.d.polygon([(x * scale, y * scale) for x, y in pts], fill=v)
        def rect(self, x0, y0, x1, y1, v=255):
            self.d.rectangle([x0 * scale, y0 * scale, x1 * scale, y1 * scale], fill=v)
        def ring(self, cx, cy, r, w, a0, a1):
            self.d.pieslice([(cx - r - w / 2) * scale, (cy - r - w / 2) * scale,
                             (cx + r + w / 2) * scale, (cy + r + w / 2) * scale], a0, a1, fill=255)
            self.d.pieslice([(cx - r + w / 2) * scale, (cy - r + w / 2) * scale,
                             (cx + r - w / 2) * scale, (cy + r - w / 2) * scale], a0, a1, fill=0)
        def head(self, cx, cy, r, ang, reach=38, back=18, half=33):
            a = math.radians(ang)
            px, py = cx + r * math.cos(a), cy + r * math.sin(a)
            tx, ty = -math.sin(a), math.cos(a)
            ox, oy = -ty, tx
            tip = ((px + reach * tx) * scale, (py + reach * ty) * scale)
            bx, by = px - back * tx, py - back * ty
            c1 = ((bx + half * ox) * scale, (by + half * oy) * scale)
            c2 = ((bx - half * ox) * scale, (by - half * oy) * scale)
            self.d.polygon([tip, c1, c2], fill=255)

    # 字母与箭头分层绘制（箭头环用扇形相减，不能画在同一层上），再取并集
    letters = Image.new("L", (n, n), 0)
    arrows = Image.new("L", (n, n), 0)
    rz = Rasterizer(None)
    rz.d = ImageDraw.Draw(letters)
    rz.draw_letters()
    rz.d = ImageDraw.Draw(arrows)
    rz.draw_arrows()
    mask = ImageChops.lighter(letters, arrows)

    glyph = Image.new("RGBA", (n, n), (255, 255, 255, 0))
    glyph.putalpha(mask)
    return glyph

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

    # ---------- legacy 图标 ----------
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, size in densities.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        ss = size * SS
        g = gradient_hi.resize((ss, ss), Image.BICUBIC).convert("RGBA")
        fg = glyph_hi.resize((ss, ss), Image.LANCZOS)
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

    # ---------- 自适应图标前景（中心缩放 0.68 以适配 72dp 可视圆） ----------
    fg_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    scaled = scale_about_center(glyph_hi, 0.68)
    for name, size in fg_sizes.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        scaled.resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_foreground.png"))

    print("icons generated")

if __name__ == "__main__":
    main()
