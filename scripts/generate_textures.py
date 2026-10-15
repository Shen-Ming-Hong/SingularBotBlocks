#!/usr/bin/env python3
"""Build every Singular Bot Blocks texture from the Codex source art in art/codex/.

The sources were generated for this project with OpenAI Codex image generation
(see art/codex/REPORT.md): pixel art drawn on a fixed target grid, exported with each
target pixel as an 8 x 8 block, using only the shared palette in art/codex/standard.json.
This script shrinks each block back to one pixel, recolours the neutral paint greys of
the robot body for each of the 16 colours, and places every face at its UV in the CC
robot models (models/block/robot_body.json and robot_decoration.json). Requires Pillow:

    python3 scripts/generate_textures.py
"""
import colorsys, json, pathlib
from PIL import Image

root = pathlib.Path(__file__).resolve().parents[1]
source = root / 'art/codex'
assets = root / 'src/main/resources/assets/singularbots'
blocks, gui, items = assets / 'textures/block', assets / 'textures/gui/legacy', assets / 'textures/item'
standard = json.loads((source / 'standard.json').read_text())
SCALE = standard['target_pixel_scale']
PAINT = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in standard['palette']['paint_gray']]  # dark to light

# Must match turtle.Appearance.COLOURS and JuniorBodyModels.NAMES order.
COLOURS = {'white': 0xf0f0f0, 'orange': 0xf2b233, 'magenta': 0xe57fd8, 'light_blue': 0x99b2f2,
           'yellow': 0xdede6c, 'lime': 0x7fcc19, 'pink': 0xf2b2cc, 'gray': 0x4c4c4c,
           'light_gray': 0x999999, 'cyan': 0x4c99b2, 'purple': 0xb266e5, 'blue': 0x3366cc,
           'brown': 0x7f664c, 'green': 0x57a64e, 'red': 0xcc4c4c, 'black': 0x111111}
DECORATIONS = ['3dglasses', 'bandana_blue', 'bandana_purple', 'bandana_red', 'bandana_yellow', 'creeper', 'crown',
               'elf', 'flames', 'frown', 'moustache', 'smile', 'snorkel', 'strawhat', 'sunglasses', 'teeth', 'tuxedo']
BUTTONS = ['abort', 'dig', 'moveback', 'movedown', 'moveforward', 'moveup', 'next', 'pause', 'place', 'previous',
           'recall', 'run', 'show_normal_view', 'show_turtle_view', 'step', 'stop', 'turnleft', 'turnright', 'close']
TABS = ['program', 'customise', 'inventory', 'remote']


def load(name):
    """Read one source PNG and turn each SCALE x SCALE block back into a single pixel."""
    big = Image.open(source / f'{name}.png').convert('RGBA')
    w, h = big.width // SCALE, big.height // SCALE
    img = Image.new('RGBA', (w, h))
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), big.getpixel((x * SCALE + SCALE // 2, y * SCALE + SCALE // 2)))
    return img


def solid(img):
    """Fill transparent texels from their opaque neighbours. A cube face with holes lets light
    through the model edges (it looks like a paper box), so body and pack faces must be opaque."""
    img = img.copy()
    while True:
        holes = [(x, y) for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] == 0]
        if not holes:
            return img
        filled = {}
        for x, y in holes:
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3]:
                    filled[(x, y)] = img.getpixel((nx, ny)); break
        for xy, colour in filled.items():
            img.putpixel(xy, colour)


def realign(img, start):
    """Some Codex faces were cut from their sheet a few rows low: the top rows hold a sliver of the
    neighbouring face and padding, and the bottom edge is lost. Move the face up to its real top
    edge and rebuild the lost bottom edge as a mirror of the top edge."""
    w, h = img.size
    body = img.crop((0, start, w, h))
    out = Image.new('RGBA', (w, h))
    out.paste(body, (0, 0))
    out.paste(img.crop((0, start, w, start * 2)).transpose(Image.FLIP_TOP_BOTTOM), (0, h - start))
    return out


def realign_left(img, start):
    """realign() for a face cut a few columns too far to the left."""
    return realign(img.transpose(Image.TRANSPOSE), start).transpose(Image.TRANSPOSE)


OUTLINE = (0x11, 0x14, 0x19, 255)


def outline(img):
    """Ring the face with the palette outline. Codex drew the dark edge on some sides of some faces
    only, so on the cube a side without it looked cut off; every face edge is a cube edge."""
    img = img.copy()
    w, h = img.size
    for x in range(w):
        img.putpixel((x, 0), OUTLINE); img.putpixel((x, h - 1), OUTLINE)
    for y in range(h):
        img.putpixel((0, y), OUTLINE); img.putpixel((w - 1, y), OUTLINE)
    return img


def symmetric(img, vertical=False):
    """Mirror the right half onto the left (and, if vertical, the top half onto the bottom). The Codex
    faces came back a few pixels off centre (screen and gear shifted, a bevel stripe on one side
    only), which reads as plainly lopsided on a cube."""
    w, h = img.size
    half = img.crop((w // 2, 0, w, h))
    out = Image.new('RGBA', (w, h))
    out.paste(half, (w - half.width, 0)); out.paste(half.transpose(Image.FLIP_LEFT_RIGHT), (0, 0))
    if vertical:
        top = out.crop((0, 0, w, (h + 1) // 2))
        out.paste(top.transpose(Image.FLIP_TOP_BOTTOM), (0, h - top.height))
    return out


# Rows (or columns, for body_back) to drop from each misaligned face (see realign).
MISALIGNED = {'body_top': 5, 'body_bottom': 6, 'pack_back': 3, 'body_back': 2}


def ramp(colour, steps=(-.42, -.2, 0, .3)):
    """Four tones, shadow to highlight, in the same order as the paint greys."""
    r, g, b = ((colour >> s & 255) / 255 for s in (16, 8, 0))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    out = []
    for k in steps:
        nl = l + (1 - l) * k * .75 if k >= 0 else l * (1 + k)
        rr, gg, bb = colorsys.hls_to_rgb(h, max(0, min(1, nl)), s)
        out.append((round(rr * 255), round(gg * 255), round(bb * 255)))
    return out


def recolour(img, tones):
    """Swap the neutral paint greys for the given tones; brass, glass and bolts stay as drawn."""
    out = img.copy()
    lookup = dict(zip(PAINT, tones))
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a and (r, g, b) in lookup:
                out.putpixel((x, y), lookup[(r, g, b)] + (a,))
    return out


def split_paint(img):
    """Paint pixels only (as light greys for the GUI's colour tint) and everything else."""
    paint, trim = Image.new('RGBA', img.size), Image.new('RGBA', img.size)
    light = PAINT[-1][0]
    for y in range(img.height):
        for x in range(img.width):
            p = img.getpixel((x, y))
            if p[3] and p[:3] in PAINT:
                v = min(255, round(p[0] * 255 / light))
                paint.putpixel((x, y), (v, v, v, 255))
            elif p[3]:
                trim.putpixel((x, y), p)
    return paint, trim


# The screen glass inside body_front (centred once the face is made symmetric); eyes_*.png frames are drawn to exactly this box.
EYES_BOX = (7, 8, 11, 8)
EYES = ['open', 'half', 'closed', 'look_left', 'look_right', 'happy', 'wink', 'surprised', 'sleepy', 'sad', 'love',
        'confused', 'cursor', 'type_1', 'type_2', 'type_3', 'type_heart_1', 'type_heart_2']
BLINK = [('half', 2), ('closed', 3), ('half', 2)]
# One loop of about 80 seconds (ticks): mostly calm eyes that blink, glance around, and now and
# then type a little face onto the screen like a terminal before going back to normal.
EYE_TIMELINE = [('open', 80), *BLINK, ('open', 60), ('look_left', 20), ('open', 10), ('look_right', 20), ('open', 60),
                *BLINK, ('open', 40), ('cursor', 10), ('type_1', 6), ('type_2', 6), ('type_3', 40), ('cursor', 10),
                ('open', 50), *BLINK, ('open', 4), *BLINK, ('open', 60), ('happy', 40), ('open', 60), ('wink', 20),
                ('open', 60), *BLINK, ('open', 30), ('cursor', 10), ('type_heart_1', 6), ('type_heart_2', 40),
                ('cursor', 10), ('open', 50), ('surprised', 30), ('open', 60), *BLINK, ('open', 40), ('sleepy', 40),
                ('closed', 20), ('half', 4), ('open', 40), ('confused', 30), ('open', 40), ('love', 30), ('open', 60),
                *BLINK, ('open', 40), ('sad', 30), ('open', 40)]


def regions(model, element, k=2):
    """Face name -> (x, y, w, h, flipped) on a (64 * k) px texture."""
    data = json.loads((assets / 'models/block' / model).read_text())['elements'][element]['faces']
    out = {}
    for name, face in data.items():
        u0, v0, u1, v1 = (round(n * 4 * k) for n in face['uv'])
        out[name] = (min(u0, u1), min(v0, v1), abs(u1 - u0), abs(v1 - v0), v0 > v1)
    return out


BODY, PACK = regions('robot_body.json', 0), regions('robot_body.json', 1)
SHELL = regions('robot_decoration.json', 0)


def paste(sheet, layout, drawn):
    for name, img in drawn.items():
        x, y, w, h, flip = layout[name]
        assert img.size == (w, h), (name, img.size, (w, h))
        sheet.alpha_composite(img.transpose(Image.FLIP_TOP_BOTTOM) if flip else img, (x, y))


def robot_textures():
    body = {'down': load('body_bottom'), 'up': load('body_top'), 'north': load('body_front'),
            'south': load('body_back'), 'west': load('body_left'), 'east': load('body_right')}
    pack = {'down': load('pack_top'), 'up': load('pack_top'), 'south': load('pack_back'),
            'west': load('pack_side'), 'east': load('pack_side')}
    body['up'], body['down'] = realign(body['up'], MISALIGNED['body_top']), realign(body['down'], MISALIGNED['body_bottom'])
    pack['south'] = realign(pack['south'], MISALIGNED['pack_back'])
    body['south'] = realign_left(body['south'], MISALIGNED['body_back'])
    body = {k: symmetric(v, vertical=True) for k, v in body.items()}
    body['east'] = body['west']  # body_right is cut low too; the symmetric left side serves for both
    body = {k: outline(solid(v)) for k, v in body.items()}
    pack = {k: outline(solid(v)) for k, v in pack.items()}
    eyes = {name: load(f'eyes_{name}') for name in EYES}
    x, y, w, h = EYES_BOX
    assert all(img.size == (w, h) for img in eyes.values())
    timeline = {'animation': {'interpolate': False,
                              'frames': [{'index': EYES.index(name), 'time': ticks} for name, ticks in EYE_TIMELINE]}}
    for name, colour in COLOURS.items():
        tones = ramp(colour)
        painted = {k: recolour(v, tones) for k, v in body.items()}
        # Animated texture: one 128 x 128 frame per eye drawing, stacked vertically.
        strip = Image.new('RGBA', (128, 128 * len(EYES)))
        for i, eye in enumerate(EYES):
            sheet = Image.new('RGBA', (128, 128))
            front = painted['north'].copy(); front.paste(eyes[eye], (x, y))
            paste(sheet, BODY, {**painted, 'north': front})
            paste(sheet, PACK, pack)
            strip.paste(sheet, (0, 128 * i))
        strip.save(blocks / f'turtle_junior_{name}.png')
        (blocks / f'turtle_junior_{name}.png.mcmeta').write_text(json.dumps(timeline, indent=1) + '\n')
    paint, trim = split_paint(body['north'])
    x3 = lambda img: img.resize((img.width * 3, img.height * 3), Image.NEAREST)  # 72 x 72, drawn at 36 x 36
    x3(paint).save(gui / 'turtle.png'); x3(trim).save(gui / 'turtle_overlay.png')


def decorations():
    for name in DECORATIONS:
        front = load(f'deco_{name}')
        sheet = Image.new('RGBA', (128, 128))
        paste(sheet, SHELL, {'north': front})
        sheet.save(blocks / f'turtle_junior_overlay_{name}.png')
        front.resize((72, 72), Image.NEAREST).save(gui / f'{name}_icon.png')


def widgets():
    """Atlas read by LegacyGui: panel nine-slice, side tab backgrounds, slot well and text field."""
    img = Image.new('RGBA', (256, 256))
    img.alpha_composite(load('panel'), (130, 0))
    img.alpha_composite(load('tab_selected'), (12, 0))
    img.alpha_composite(load('tab_normal'), (12, 24))
    img.alpha_composite(load('slot'), (0, 64))
    img.alpha_composite(load('field'), (0, 96))
    img.save(gui / 'widgets.png')


def main():
    robot_textures()
    decorations()
    for name in BUTTONS:
        load(f'button_{name}').save(gui / f'button_{name}.png')
    # Back is forward turned over, so the two green arrows match exactly.
    load('button_moveforward').transpose(Image.FLIP_TOP_BOTTOM).save(gui / 'button_moveback.png')
    for name in TABS:
        load(f'tab_{name}').save(gui / f'tab_{name}.png')
    widgets()
    load('remote_item').save(items / 'remote.png')


if __name__ == '__main__':
    main()
