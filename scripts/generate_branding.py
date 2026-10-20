#!/usr/bin/env python3
"""Build the Singular Bot Blocks cover art from the game's own robot textures.

Every image shows the default white robot exactly as scripts/generate_textures.py paints it
(same faces, palette, silver-blue edge line and edge light), so the art always matches the game.
The word mark is set in Jersey 10 (SIL Open Font License, art/branding/OFL.txt) at its native
pixel size and enlarged by whole numbers only. No Minecraft logos, fonts, textures or
screenshots are used. Requires Pillow:

    python3 scripts/generate_branding.py

Writes art/branding/{cover,social_preview,logo,icon,icon_curseforge}.png and the mod list logo
src/main/resources/logo.png.
"""
import pathlib, sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

root = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'scripts'))
import generate_textures as g  # noqa: E402

out = root / 'art/branding'
FONT = ImageFont.truetype(str(out / 'Jersey10-Regular.ttf'), 20)  # Jersey 10 is drawn on a 20 px grid
TRACKING = 2  # extra pixels between letters; at 0 the letters touch
BACKGROUND, DOT = (32, 37, 43, 255), (40, 46, 54, 255)
OUTLINE, SHADOW = (17, 20, 25, 255), (20, 23, 28, 255)
BRASS, BRASS_LIGHT, BRASS_DARK = (201, 150, 78, 255), (240, 206, 131, 255), (163, 108, 50, 255)
# The still images show one moment of the animated edge light: the whole front edge lit, with the
# bright head of the light climbing the left edge.
EDGE_FRAME = 100


def faces(colour='white', eye='open'):
    """The robot's front, top and left faces as generate_textures.robot_textures paints them."""
    body = {'up': g.load('body_top'), 'north': g.load('body_front'), 'west': g.load('body_left')}
    body['up'] = g.realign(body['up'], g.MISALIGNED['body_top'])
    body = {k: g.solid(g.symmetric(v, vertical=True)) for k, v in body.items()}
    body = {k: g.outline(g.plain(v) if k in ('west', 'up') else v, g.EDGE) for k, v in body.items()}
    tones = g.ramp(g.COLOURS[colour])
    body = {k: g.recolour(v, tones) for k, v in body.items()}
    x, y, _, _ = g.EYES_BOX
    body['north'].paste(g.load(f'eyes_{eye}'), (x, y))
    for k, v in body.items():
        v.alpha_composite(g.edge_signal(k, *v.size, EDGE_FRAME % g.EDGE_FRAMES))
    return body


def scaled(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def shade(img, f):
    r, gr, b, a = img.split()
    return Image.merge('RGBA', [c.point(lambda v: int(v * f)) for c in (r, gr, b)] + [a])


def cube(k=1):
    """The robot in cabinet projection with no texel lost: each front texel is 2k x 2k, each side
    column k wide and k higher than the one before, each top row k tall and k further right."""
    f = faces()
    front, top, side = f['north'], f['up'], shade(f['west'], .78)
    w, h, depth = front.width, front.height, side.width
    s = 2 * k
    img = Image.new('RGBA', (w * s + depth * k, h * s + depth * k))
    y0 = depth * k
    img.alpha_composite(scaled(front, s), (0, y0))
    side = side.resize((depth * k, h * s), Image.NEAREST)
    for j in range(depth):
        img.alpha_composite(side.crop((j * k, 0, j * k + k, h * s)), (w * s + j * k, y0 - j * k))
    top = top.resize((w * s, top.height * k), Image.NEAREST)
    for r in range(top.height):  # the texture's bottom row is the front edge
        row = top.height - 1 - r
        img.alpha_composite(top.crop((0, row * k, w * s, row * k + k)), ((r + 1) * k, y0 - (r + 1) * k))
    return img


def letters(text):
    """A 1-bit mask of the text at the font's native size, letter by letter with TRACKING."""
    ascent = -min(FONT.getbbox(ch)[1] for ch in text if ch != ' ')
    width = sum(4 if ch == ' ' else FONT.getbbox(ch)[2] + TRACKING for ch in text)
    mask = Image.new('L', (width + 4, 24))
    draw = ImageDraw.Draw(mask)
    draw.fontmode = '1'
    x = 0
    for ch in text:
        if ch == ' ':
            x += 4
            continue
        left, _, right, _ = FONT.getbbox(ch)
        draw.text((x - left, ascent), ch, font=FONT, fill=255)
        x += right - left + TRACKING
    return mask.crop(mask.getbbox())


def word_line(text, pad=2):
    """Brass letters with a dark outline, a drop shadow, a lit top edge and a shaded underside."""
    glyph = letters(text)
    a = Image.new('L', (glyph.width + 2 * pad, glyph.height + 2 * pad))
    a.paste(glyph, (pad, pad))
    ring = a.filter(ImageFilter.MaxFilter(3))
    moved = lambda m, dx, dy: ImageChops.offset(m, dx, dy)
    lit = ImageChops.subtract(a, moved(a, 0, 1))  # letter pixels with nothing above
    under = ImageChops.multiply(ImageChops.subtract(a, moved(a, 0, -1)), moved(a, 0, 1))  # bottom, not top
    img = Image.new('RGBA', a.size)
    for colour, m in ((SHADOW, moved(ring, 1, 1)), (OUTLINE, ring), (BRASS, a), (BRASS_LIGHT, lit), (BRASS_DARK, under)):
        img.paste(colour, (0, 0), m)
    return img


def word_mark(lines):
    parts = [scaled(word_line(text), k) for text, k in lines]
    img = Image.new('RGBA', (max(p.width for p in parts), sum(p.height for p in parts)))
    y = 0
    for p in parts:
        img.alpha_composite(p, (0, y))
        y += p.height
    return img


def save(img, k, name):
    scaled(img, k).save(out / f'{name}.png', optimize=True)


def banner(w, h, sizes, robot, gap, k, name):
    """The word mark and the robot's front face side by side, centred on a dotted panel."""
    img = Image.new('RGBA', (w, h), BACKGROUND)
    draw = ImageDraw.Draw(img)
    for y in range(6, h, 12):
        for x in range(6, w, 12):
            draw.point((x, y), fill=DOT)
    mark, face = word_mark([('Singular Bot', sizes[0]), ('Blocks', sizes[1])]), scaled(faces()['north'], robot)
    x = (w - mark.width - gap - face.width) // 2
    img.alpha_composite(mark, (x, (h - mark.height) // 2 - 4))
    fx, fy = x + mark.width + gap, (h - face.height) // 2 - 6
    cx, sy = fx + face.width // 2, fy + face.height + 3
    draw.rectangle((cx - face.width // 2 + 4, sy, cx + face.width // 2 - 4, sy + 1), fill=(22, 26, 31, 255))
    draw.rectangle((cx - face.width // 2 + 7, sy + 2, cx + face.width // 2 - 7, sy + 2), fill=(26, 30, 36, 255))
    img.alpha_composite(face, (fx, fy))
    save(img, k, name)


def main():
    banner(480, 270, (2, 4), 5, 36, 4, 'cover')  # 1920 x 1080, 16:9
    banner(640, 320, (3, 5), 6, 44, 2, 'social_preview')  # 1280 x 640, GitHub's recommended size

    logo = Image.new('RGBA', (26, 26))  # the front face with a one-texel transparent margin
    logo.alpha_composite(faces()['north'], (1, 1))
    save(logo, 16, 'logo')  # 416 x 416
    scaled(logo, 16).save(root / 'src/main/resources/logo.png', optimize=True)

    icon = Image.new('RGBA', (80, 80))
    draw = ImageDraw.Draw(icon)
    draw.rounded_rectangle((0, 0, 79, 79), radius=10, fill=OUTLINE)
    draw.rounded_rectangle((2, 2, 77, 77), radius=9, fill=(48, 56, 64, 255))
    robot = cube()
    icon.alpha_composite(robot, ((80 - robot.width) // 2, (80 - robot.height) // 2 + 1))
    save(icon, 8, 'icon')  # 640 x 640 for Modrinth
    save(icon, 5, 'icon_curseforge')  # 400 x 400, CurseForge's required avatar size


if __name__ == '__main__':
    main()
