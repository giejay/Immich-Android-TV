#!/usr/bin/env python3
"""Builds every icon and banner bitmap from the two source images in tools/branding/source:

    python3 tools/branding/build_assets.py

  pinwheel-source.png  the colored pinwheel on pure black
  banner-source.png    the 16:9 banner "Immich TV Next (Unofficial)"

Writes app/src/main/res/drawable/{icon,icon_600px,icon_mark}.png, the 640x360 TV banner
app/src/main/res/mipmap-xhdpi/icon_banner_next.png and the Google Play assets in store/assets
(icon-512.png full bleed, tv-banner-1280x720.png, feature-graphic-1024x500.png). Needs Pillow only.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(__file__).resolve().parent / "source"
BG_TOP, BG_BOTTOM = (20, 24, 70), (9, 11, 30)


def pinwheel_cutout() -> Image.Image:
    """The pinwheel with a transparent background, cropped to its extent."""
    img = Image.open(SRC / "pinwheel-source.png").convert("RGB")
    work = img.copy()
    magenta = (255, 0, 255)
    for xy in [(1, 1), (img.width - 2, 1), (1, img.height - 2), (img.width - 2, img.height - 2)]:
        if work.getpixel(xy) != magenta:
            ImageDraw.floodfill(work, xy, magenta, thresh=30)
    alpha = Image.new("L", img.size, 255)
    wp, ap = work.load(), alpha.load()
    for y in range(img.height):
        for x in range(img.width):
            if wp[x, y] == magenta:
                ap[x, y] = 0
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
    out = img.convert("RGBA")
    out.putalpha(alpha)
    return out.crop(alpha.point(lambda v: 255 if v > 20 else 0).getbbox())


def fit(image: Image.Image, size: int, fill: float) -> Image.Image:
    """[image] scaled to fill * size on its longer side and centered on a transparent square."""
    scale = size * fill / max(image.size)
    scaled = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2), scaled)
    return out


def gradient(size: int) -> Image.Image:
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        d.line([(0, y), (size, y)], fill=tuple(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)))
    return img


def tile(mark: Image.Image, size: int, rounded: bool) -> Image.Image:
    """Dark navy tile with the pinwheel; rounded corners for the in-app icon, full bleed for Play."""
    big = size * 4
    base = gradient(big).convert("RGBA")
    base.alpha_composite(fit(mark, big, 0.64))
    if rounded:
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=round(big * 0.225), fill=255)
        base.putalpha(mask)
    return base.resize((size, size), Image.LANCZOS)


def main():
    mark = pinwheel_cutout()
    res = ROOT / "app/src/main/res"
    fit(mark, 512, 0.96).save(res / "drawable/icon_mark.png")
    tile(mark, 512, rounded=True).save(res / "drawable/icon.png")
    tile(mark, 600, rounded=True).save(res / "drawable/icon_600px.png")
    banner = Image.open(SRC / "banner-source.png").convert("RGB")
    banner.resize((640, 360), Image.LANCZOS).save(res / "mipmap-xhdpi/icon_banner_next.png")
    store = ROOT / "store/assets"
    store.mkdir(parents=True, exist_ok=True)
    tile(mark, 512, rounded=False).convert("RGB").save(store / "icon-512.png")
    banner.resize((1280, 720), Image.LANCZOS).save(store / "tv-banner-1280x720.png")
    # Play feature graphic 1024x500 (2.048:1): the banner content sits in the middle, so cut a band from it
    scaled = banner.resize((1280, 720), Image.LANCZOS)
    band = round(1280 / 2.048)
    top = (720 - band) // 2 + 6
    scaled.crop((0, top, 1280, top + band)).resize((1024, 500), Image.LANCZOS).save(store / "feature-graphic-1024x500.png")
    print("wrote icons and banners")


if __name__ == "__main__":
    main()
