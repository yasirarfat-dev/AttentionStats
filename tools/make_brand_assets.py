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
TOP, BOTTOM = (23, 74, 56), (14, 42, 32)  # evergreen gradient
MINT, CREAM, AMBER = (143, 224, 187), (241, 251, 245), (242, 194, 122)


def gradient(size, top=TOP, bottom=BOTTOM):
    image = Image.new("RGB", size, top)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        draw.line([(0, y), (size[0], y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image


def mark(canvas, center, radius, scale=1.0):
    """A progress ring (12 o'clock clockwise to ~10 o'clock), amber head, cream centre dot."""
    draw = ImageDraw.Draw(canvas)
    cx, cy = center
    width = max(2, int(radius * 0.22))
    box = [cx - radius, cy - radius, cx + radius, cy + radius]
    draw.arc(box, start=-90, end=210, fill=MINT, width=width)
    # Amber head where progress currently is: "almost there", the gentle nudge.
    import math
    angle = math.radians(210)
    sx, sy = cx + radius * math.cos(angle), cy + radius * math.sin(angle)
    dot = width * 0.62
    draw.ellipse([sx - dot, sy - dot, sx + dot, sy + dot], fill=AMBER)
    core = radius * 0.36
    draw.ellipse([cx - core, cy - core, cx + core, cy + core], fill=CREAM)


def tile(size, rounded=True, circle=False, ss=4):
    big = size * ss
    image = gradient((big, big)).convert("RGBA")
    mark(image, (big / 2, big / 2), big * 0.30)
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
    mark(image, (big / 2, big / 2), big * 0.20)
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--store-dir", type=Path)
    args = parser.parse_args()
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, factor in densities.items():
        folder = RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        tile(int(48 * factor)).save(folder / "ic_launcher.png")
        tile(int(48 * factor), circle=True).save(folder / "ic_launcher_round.png")
        foreground(int(108 * factor)).save(folder / "ic_launcher_foreground.png")
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(exist_ok=True)
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background" />\n'
                '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n'
                '</adaptive-icon>\n')
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / name).write_text(adaptive, encoding="utf-8")
    (RES / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <color name="ic_launcher_background">#17463A</color>\n</resources>\n', encoding="utf-8")
    if args.store_dir:
        args.store_dir.mkdir(parents=True, exist_ok=True)
        tile(512, rounded=False).convert("RGB").save(args.store_dir / "icon-512.png")
        feature_graphic(args.store_dir / "feature-graphic-1024x500.png")
    print("Brand assets written")


if __name__ == "__main__":
    main()
