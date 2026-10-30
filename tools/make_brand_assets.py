#!/usr/bin/env python3
"""Generate the Attention Stats launcher icons, Play store icon and feature graphic.

    uv run --with pillow python tools/make_brand_assets.py [--store-dir DIR]

The mark: an evergreen tile with a mint "attention ring" (a gently open circle, like the
in-app progress ring) around a calm centre dot. Drawn at 4x and downsampled for clean edges.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
TOP, BOTTOM = (10, 45, 58), (5, 18, 28)  # deep navy-teal gradient
MINT, CREAM, AMBER = (143, 224, 187), (241, 251, 245), (242, 194, 122)


def gradient(size, top=TOP, bottom=BOTTOM):
    image = Image.new("RGB", size, top)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        draw.line([(0, y), (size[0], y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image


def _gradient_fill(size, stops, vertical=True):
    """Diagonal/vertical multi-stop gradient image."""
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(0, w):
            t = (y / max(1, h - 1)) if vertical else ((x + y) / max(1, w + h - 2))
            for k in range(len(stops) - 1):
                a, b = stops[k], stops[k + 1]
                if a[0] <= t <= b[0]:
                    u = (t - a[0]) / max(1e-6, b[0] - a[0])
                    px[x, y] = tuple(int(a[1][c] + (b[1][c] - a[1][c]) * u) for c in range(3))
                    break
    return img


def shield_points(cx, top, w, h):
    """Classic heater shield: gentle top arch, straight upper sides, curved point."""
    import math
    pts = []
    left, right = cx - w / 2, cx + w / 2
    for i in range(21):  # top arch (slight dip in the middle like a crest)
        t = i / 20
        x = left + (right - left) * t
        y = top + h * 0.06 * math.sin(math.pi * t) * 0.6
        pts.append((x, y))
    for i in range(1, 41):  # right side curving to the point
        t = i / 40
        x = right - (w / 2) * (t ** 2.2)
        y = top + h * (0.08 + 0.92 * t)
        pts.append((x, y))
    for i in range(39, 0, -1):
        t = i / 40
        x = left + (w / 2) * (t ** 2.2)
        y = top + h * (0.08 + 0.92 * t)
        pts.append((x, y))
    return pts


def _bezier(p0, p1, p2, p3, steps=40):
    pts = []
    for k in range(steps + 1):
        t = k / steps
        mt = 1 - t
        pts.append((mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0],
                    mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]))
    return pts


def _stroke(draw, pts, width, fill):
    draw.line(pts, fill=fill, width=int(width), joint="curve")
    r = width / 2
    for x, y in (pts[0], pts[-1]):
        draw.ellipse([x - r, y - r, x + r, y + r], fill=fill)


# Left-hemisphere brain line art in a unit box; mirrored for the right side.
BRAIN_OUTLINE = [((0.47, 0.13), (0.40, 0.02), (0.22, 0.03), (0.18, 0.16)),
                 ((0.18, 0.16), (0.05, 0.18), (0.01, 0.34), (0.08, 0.45)),
                 ((0.08, 0.45), (0.00, 0.56), (0.04, 0.72), (0.17, 0.75)),
                 ((0.17, 0.75), (0.19, 0.89), (0.36, 0.93), (0.47, 0.85))]
BRAIN_FOLDS = [((0.47, 0.33), (0.38, 0.29), (0.30, 0.33), (0.31, 0.43)),
               ((0.17, 0.56), (0.25, 0.60), (0.33, 0.58), (0.36, 0.66))]


def mark(canvas, center, radius, scale=1.0):
    """Solid gradient shield (protection), white line-art brain (attention), rising bars (stats)."""
    from PIL import ImageFilter
    cx, cy = center
    R = radius
    sw, sh = R * 2.0, R * 2.25
    top = cy - sh * 0.5
    # Shield body with a cyan -> blue gradient and a soft top highlight.
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).polygon(shield_points(cx, top, sw, sh), fill=255)
    body = _gradient_fill(canvas.size, [(0, (34, 211, 238)), (0.55, (37, 140, 235)), (1, (29, 78, 216))])
    glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    glow.paste((56, 189, 248, 255), mask=mask)
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(R * 0.16)))
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(body, mask=mask)
    highlight = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(highlight).polygon(shield_points(cx, top + sh * 0.03, sw * 0.9, sh * 0.5), fill=46)
    layer.paste((255, 255, 255, 255), mask=Image.composite(highlight, Image.new("L", canvas.size, 0), mask))
    canvas.alpha_composite(layer)
    # Brain line art.
    d = ImageDraw.Draw(canvas)
    box = R * 1.36
    bx, by = cx - box / 2, top + sh * 0.11
    white = (240, 252, 255, 255)
    width = box * 0.052
    def place(pt, mirror):
        x, y = pt
        if mirror:
            x = 1 - x
        return (bx + x * box, by + y * box * 0.92)
    for mirror in (False, True):
        for curve in BRAIN_OUTLINE + BRAIN_FOLDS:
            _stroke(d, _bezier(*[place(p, mirror) for p in curve]), width, white)
    # Rising bars at the base of the shield.
    bar_w = R * 0.17
    base = top + sh * 0.86
    for k, frac in enumerate((0.07, 0.11, 0.155)):
        x = cx - bar_w * 1.62 + k * bar_w * 1.12
        d.rounded_rectangle([x, base - sh * frac, x + bar_w * 0.86, base], radius=bar_w * 0.28,
                            fill=(167, 243, 208, 255) if k == 2 else (224, 247, 255, 235))


def tile(size, rounded=True, circle=False, ss=4):
    big = size * ss
    image = gradient((big, big)).convert("RGBA")
    mark(image, (big / 2, big * 0.5), big * 0.335)
    mask = Image.new("L", (big, big), 0)
    shape = ImageDraw.Draw(mask)
    if circle:
        shape.ellipse([0, 0, big - 1, big - 1], fill=255)
    elif rounded:
        shape.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.22), fill=255)
    else:
        shape.rectangle([0, 0, big, big], fill=255)
    image.putalpha(mask)
    return image.resize((size, size), Image.LANCZOS)


def foreground(size, ss=4):
    """Adaptive-icon foreground: mark inside the 66/108 safe zone on transparency."""
    big = size * ss
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    mark(image, (big / 2, big / 2), big * 0.235)
    return image.resize((size, size), Image.LANCZOS)


def font(size, bold=True):
    for name in (["segoeuib.ttf", "seguisb.ttf"] if bold else ["segoeui.ttf"]) + ["DejaVuSans-Bold.ttf", "arial.ttf"]:
        for folder in [Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/dejavu")]:
            path = folder / name
            if path.exists():
                return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def feature_graphic(path):
    ss = 2
    w, h = 1024 * ss, 500 * ss
    image = gradient((w, h)).convert("RGBA")
    mark(image, (int(h * 0.5), h // 2), int(h * 0.27))
    draw = ImageDraw.Draw(image)
    x = int(h * 0.92)
    draw.text((x, int(h * 0.27)), "Attention Stats", font=font(72 * ss), fill=CREAM)
    draw.text((x, int(h * 0.52)), "See where your attention goes.", font=font(36 * ss, bold=False), fill=MINT)
    draw.text((x, int(h * 0.64)), "Private · Open source · No ads", font=font(30 * ss, bold=False), fill=(169, 210, 191))
    image.convert("RGB").resize((1024, 500), Image.LANCZOS).save(path)


SOURCE = ROOT / "design/brand/icon-source.png"


def edge_color(image):
    """Average of the four corners: the artwork's own background colour."""
    w, h = image.size
    pts = [image.getpixel((x, y)) for x, y in ((4, 4), (w - 5, 4), (4, h - 5), (w - 5, h - 5))]
    return tuple(sum(p[c] for p in pts) // 4 for c in range(3))


def from_source(size, shape="rounded", content=1.0):
    """Square artwork scaled to `content` of the tile over its own background colour."""
    art = Image.open(SOURCE).convert("RGB")
    tile_img = Image.new("RGB", (size, size), edge_color(art))
    inner = max(1, int(size * content))
    tile_img.paste(art.resize((inner, inner), Image.LANCZOS), ((size - inner) // 2, (size - inner) // 2))
    tile_img = tile_img.convert("RGBA")
    if shape == "square":
        return tile_img
    big = size * 4
    mask = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(mask)
    if shape == "circle":
        d.ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.22), fill=255)
    tile_img.putalpha(mask.resize((size, size), Image.LANCZOS))
    return tile_img


def feature_from_source(path):
    from PIL import ImageFilter
    art = Image.open(SOURCE).convert("RGB")
    bg = edge_color(art)
    w, h = 1024 * 2, 500 * 2
    image = Image.new("RGBA", (w, h), bg + (255,))
    size = int(h * 0.98)
    # Feather the artwork into the background so its square edge disappears.
    feather = Image.new("L", (size, size), 0)
    ImageDraw.Draw(feather).ellipse([size * 0.06, size * 0.06, size * 0.94, size * 0.94], fill=255)
    feather = feather.filter(ImageFilter.GaussianBlur(size * 0.06))
    image.paste(art.resize((size, size), Image.LANCZOS), (int(h * 0.04), (h - size) // 2), feather)
    draw = ImageDraw.Draw(image)
    x = int(h * 1.04)
    draw.text((x, int(h * 0.31)), "Attention Stats", font=font(70 * 2), fill=(241, 251, 255))
    draw.text((x, int(h * 0.50)), "See where your attention goes.", font=font(33 * 2, bold=False), fill=(125, 211, 252))
    draw.text((x, int(h * 0.60)), "Private · Open source · No ads", font=font(28 * 2, bold=False), fill=(143, 224, 187))
    image.convert("RGB").resize((1024, 500), Image.LANCZOS).save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--store-dir", type=Path)
    args = parser.parse_args()
    use_source = SOURCE.exists()
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, factor in densities.items():
        folder = RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        if use_source:
            from_source(int(48 * factor), "rounded", 1.12).save(folder / "ic_launcher.png")
            from_source(int(48 * factor), "circle", 1.12).save(folder / "ic_launcher_round.png")
            # Adaptive layers show the centre 72 of 108 dp; keep the shield well inside it.
            from_source(int(108 * factor), "square", 0.86).save(folder / "ic_launcher_foreground.png")
        else:
            tile(int(48 * factor)).save(folder / "ic_launcher.png")
            tile(int(48 * factor), circle=True).save(folder / "ic_launcher_round.png")
            foreground(int(108 * factor)).save(folder / "ic_launcher_foreground.png")
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(exist_ok=True)
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background" />\n'
                '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
                '</adaptive-icon>\n')
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / name).write_text(adaptive, encoding="utf-8")
    color = "#%02X%02X%02X" % (edge_color(Image.open(SOURCE).convert("RGB")) if use_source else (8, 32, 44))
    (RES / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{color}</color>\n</resources>\n', encoding="utf-8")
    if use_source:
        (ROOT / "src/assets").mkdir(exist_ok=True)
        from_source(240, "rounded", 1.0).save(ROOT / "src/assets/app-icon.png")
    if args.store_dir:
        args.store_dir.mkdir(parents=True, exist_ok=True)
        (from_source(512, "square", 1.0) if use_source else tile(512, rounded=False)).convert("RGB").save(
            args.store_dir / "icon-512.png")
        (feature_from_source if use_source else feature_graphic)(args.store_dir / "feature-graphic-1024x500.png")
    print("Brand assets written" + (" from design/brand/icon-source.png" if use_source else ""))


if __name__ == "__main__":
    main()
