#!/usr/bin/env python3
"""Draws the app icon (a TV showing a rosette of five photo cards in the Immich colors) and derives every bitmap from that one drawing:

    python3 tools/branding/generate_icons.py

Writes app/src/main/res/drawable/icon.png (512), icon_600px.png (home screen channel logo), icon_mark.png (rosette only, used in the app),
app/src/main/res/mipmap-xhdpi/icon_banner_next.png (640x360 TV banner) and the Google Play assets
store/assets/icon-512.png and store/assets/tv-banner-1280x720.png. Needs Pillow; the banner text uses
the Ubuntu font if installed, otherwise DejaVu Sans.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SS = 4  # supersampling factor for smooth edges

INK = (15, 17, 21)
FRAME = (228, 230, 236)
ACCENT = (173, 198, 255)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def vertical_gradient(size, stops):
    """stops: [(position 0..1, (r, g, b)), ...]"""
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
            if p0 <= t <= p1:
                color = lerp(c0, c1, (t - p0) / max(p1 - p0, 1e-9))
                break
        for x in range(w):
            px[x, y] = color
    return img


def rounded_mask(size, box, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius=radius, fill=255)
    return mask


# the five accent colors that make the Immich family recognizable (red, amber, green, blue, pink)
PETALS = [(250, 41, 33), (255, 179, 0), (28, 189, 80), (41, 121, 255), (236, 128, 184)]


def rosette(layer_size, cx, cy, radius, petal_w, petal_h, gap=14, corner=0.42, start=-90.0, shape="oval"):
    """Five rounded 'photo cards' fanned around a point, like a rosette (one RGBA layer)."""
    out = Image.new("RGBA", (layer_size, layer_size), (0, 0, 0, 0))
    # pink first, so it is covered by both neighbours instead of covering them
    for i in (4, 0, 1, 2, 3):
        color = PETALS[i]
        card = Image.new("RGBA", (layer_size, layer_size), (0, 0, 0, 0))
        cd = ImageDraw.Draw(card)
        box = (cx - petal_w / 2, cy - radius - petal_h / 2, cx + petal_w / 2, cy - radius + petal_h / 2)
        if shape == "oval":
            cd.ellipse(box, fill=color + (255,))
        else:
            cd.rounded_rectangle(box, radius=petal_w * corner, fill=color + (255,))
        card = card.rotate(-(72 * i) - (start + 90), center=(cx, cy), resample=Image.BICUBIC)
        # a thin dark rim separates overlapping cards
        rim = card.filter(ImageFilter.MaxFilter(3 if gap < 10 else 5))
        shadow = Image.new("RGBA", card.size, (0, 0, 0, 0))
        shadow.paste((15, 17, 21, 255), (0, 0), rim.split()[3])
        out = Image.alpha_composite(out, shadow)
        out = Image.alpha_composite(out, card)
    return out


def fitted_rosette(width, height):
    """The rosette scaled to fit into width x height (RGBA, transparent around it) and centered exactly."""
    base = 1024
    layer = rosette(base, base / 2, base / 2, base * 0.24, base * 0.30, base * 0.48)
    layer = layer.crop(layer.getchannel("A").getbbox())
    scale = min(width / layer.width, height / layer.height)
    layer = layer.resize((max(1, round(layer.width * scale)), max(1, round(layer.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    out.paste(layer, ((width - layer.width) // 2, (height - layer.height) // 2), layer)
    return out


def draw_mark(size=512):
    """Only the five-card rosette on a transparent background, tightly framed (the logo in the navigation rail)."""
    return fitted_rosette(size, size)


def draw_tile(size=1024, variant="tv-rosette"):
    """The icon: a dark rounded tile with a TV. Variants: "tv-rosette" (default, five colored photo cards on the screen),
    "tv" (sunset over mountains) and "rosette" (cards only)."""
    n = size * SS // 1  # work at a higher resolution
    s = n / 1024.0

    def p(v):
        return v * s

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    # tile background with a soft indigo glow at the top
    tile = vertical_gradient((n, n), [(0, (30, 38, 92)), (0.55, (17, 21, 40)), (1, INK)])
    img.paste(tile, (0, 0), rounded_mask((n, n), (0, 0, n, n), p(230)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((p(22), p(22), p(1002), p(1002)), radius=p(210), outline=(255, 255, 255, 70), width=max(2, int(p(7))))

    if variant == "rosette":
        layer = rosette(n, n / 2, n / 2, p(150), p(230), p(300), gap=14)
        img = Image.alpha_composite(img, layer)
        return img.resize((size, size), Image.LANCZOS)

    # TV frame
    fx0, fy0, fx1, fy1 = p(168), p(250), p(856), p(668)
    d.rounded_rectangle((fx0, fy0, fx1, fy1), radius=p(64), fill=FRAME)
    # screen
    sx0, sy0, sx1, sy1 = fx0 + p(26), fy0 + p(26), fx1 - p(26), fy1 - p(26)
    sw, sh = int(sx1 - sx0), int(sy1 - sy0)
    screen = vertical_gradient((sw, sh), [(0, (52, 64, 168)), (0.5, (124, 88, 196)), (0.85, (255, 138, 101)), (1, (255, 183, 77))])
    if variant == "tv-rosette":
        screen = Image.new("RGB", (sw, sh), (17, 21, 40))
        mark = fitted_rosette(sw, sh)
        # fitted to the screen height with a margin
        inner = fitted_rosette(int(sh * 0.86), int(sh * 0.86))
        mark = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
        mark.paste(inner, ((sw - inner.width) // 2, (sh - inner.height) // 2), inner)
        screen = Image.alpha_composite(screen.convert("RGBA"), mark).convert("RGB")
    sd = ImageDraw.Draw(screen)

    def sp(x, y):  # screen-local coordinates in 0..1
        return (x * sw, y * sh)

    scene = variant == "tv"

    if scene:
        # sun with a glow
        glow = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((sw * 0.56, sh * 0.30, sw * 0.80, sh * 0.30 + sw * 0.24), fill=(255, 224, 130, 150))
        glow = glow.filter(ImageFilter.GaussianBlur(sw * 0.05))
        screen = Image.alpha_composite(screen.convert("RGBA"), glow)
        sd = ImageDraw.Draw(screen)
        sd.ellipse((sw * 0.60, sh * 0.34, sw * 0.76, sh * 0.34 + sw * 0.16), fill=(255, 236, 170))
        # mountains: far range lighter, near range dark
        sd.polygon([sp(0, 0.80), sp(0.22, 0.42), sp(0.38, 0.62), sp(0.55, 0.48), sp(0.80, 0.78), sp(1, 0.66), sp(1, 1), sp(0, 1)], fill=(63, 74, 156))
        sd.polygon([sp(0, 0.92), sp(0.18, 0.62), sp(0.36, 0.84), sp(0.58, 0.58), sp(0.84, 0.88), sp(1, 0.78), sp(1, 1), sp(0, 1)], fill=(23, 29, 74))
    mask = rounded_mask((sw, sh), (0, 0, sw, sh), p(40))
    img.paste(screen.convert("RGB"), (int(sx0), int(sy0)), mask)

    # stand
    d.rounded_rectangle((p(462), fy1 - p(4), p(562), p(740)), radius=p(10), fill=FRAME)
    d.rounded_rectangle((p(372), p(728), p(652), p(774)), radius=p(23), fill=FRAME)
    return img.resize((size, size), Image.LANCZOS)


def font(size):
    for path in (
        "/usr/share/fonts/truetype/ubuntu/Ubuntu[wdth,wght].ttf",
        "/usr/share/fonts/truetype/ubuntu/Ubuntu-L.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if Path(path).exists():
            f = ImageFont.truetype(path, size)
            try:
                f.set_variation_by_axes([100, 300])  # width 100, weight Light
            except Exception:
                pass
            return f
    return ImageFont.load_default()


def banner(width, height):
    """TV banner: dark gradient, the tile on the left and the app name on the right."""
    img = Image.new("RGB", (width * SS, height * SS))
    bg = vertical_gradient((width * SS, height * SS), [(0, (11, 14, 22)), (1, (30, 36, 92))])
    # diagonal feel: blend a horizontal ramp over the vertical one
    ramp = Image.linear_gradient("L").rotate(90).resize((width * SS, height * SS))
    img = Image.composite(Image.new("RGB", img.size, (35, 43, 110)), bg, ramp.point(lambda v: int(v * 0.55)))
    d = ImageDraw.Draw(img)
    k = height * SS / 360.0
    tile = draw_tile(int(200 * k)).resize((int(200 * k), int(200 * k)), Image.LANCZOS)
    img.paste(tile, (int(56 * k), int(80 * k)), tile)
    f = font(int(54 * k))
    x = int(290 * k)
    d.text((x, int(86 * k)), "Immich", font=f, fill=(238, 240, 246))
    d.text((x, int(146 * k)), "Android TV", font=f, fill=(238, 240, 246))
    d.text((x, int(206 * k)), "Next", font=f, fill=ACCENT)
    return img.resize((width, height), Image.LANCZOS)


def main():
    tile = draw_tile(1024)
    res = ROOT / "app/src/main/res"
    tile.resize((512, 512), Image.LANCZOS).save(res / "drawable/icon.png")
    tile.resize((600, 600), Image.LANCZOS).save(res / "drawable/icon_600px.png")
    draw_mark(512).save(res / "drawable/icon_mark.png")
    banner(640, 360).save(res / "mipmap-xhdpi/icon_banner_next.png")
    store = ROOT / "store/assets"
    store.mkdir(parents=True, exist_ok=True)
    tile.resize((512, 512), Image.LANCZOS).save(store / "icon-512.png")
    banner(1280, 720).save(store / "tv-banner-1280x720.png")
    print("wrote icons and banners")


if __name__ == "__main__":
    main()
