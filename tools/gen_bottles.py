"""Plastic bottle textures: empty, Water (unfiltered, clean), Dirty Water (irradiated), Purified Water (Pillow). Run from the repo root."""
from PIL import Image

A = 'src/main/resources/assets/radiation/textures/item/'
# a PET bottle, 6 wide body, ridged, a narrow neck with a screw cap
SHAPE = [
    "......CC........",
    "......CC........",
    ".....NNNN.......",
    ".....NNNN.......",
    "....SSSSSS......",
    "...BBBBBBBB.....",
    "...BBBBBBBB.....",
    "...RRRRRRRR.....",
    "...BBBBBBBB.....",
    "...LLLLLLLL.....",
    "...LLLLLLLL.....",
    "...BBBBBBBB.....",
    "...RRRRRRRR.....",
    "...BBBBBBBB.....",
    "...BBBBBBBB.....",
    "....bbbbbb......",
]


def bottle(kind):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    water_top = 99 if kind == 'empty' else 6  # rows filled with water from here down
    if kind == 'dirty':
        w, wd, wl = (122, 116, 70, 235), (92, 86, 50, 240), (150, 144, 96, 235)
    elif kind == 'water':
        w, wd, wl = (108, 176, 196, 215), (78, 146, 172, 225), (170, 222, 232, 215)
    else:
        w, wd, wl = (96, 170, 236, 225), (60, 130, 210, 235), (170, 220, 255, 225)
    plastic, plastic_d, plastic_l = (214, 230, 240, 120), (170, 190, 205, 160), (245, 252, 255, 170)
    cap = {'dirty': (60, 60, 64, 255), 'water': (205, 208, 212, 255)}.get(kind, (40, 110, 210, 255))
    for y, row in enumerate(SHAPE):
        xs = [x for x, c in enumerate(row) if c != '.']
        for x in xs:
            c = row[x]
            edge_l, edge_r = x == xs[0], x == xs[-1]
            if c == 'C':
                px[x, y] = cap if y else tuple(min(255, v + 40) for v in cap[:3]) + (255,)
            elif c in 'NS':
                px[x, y] = plastic_d if edge_l or edge_r else plastic
            elif c == 'L' and kind == 'water':  # no label: plain plastic over the water
                px[x, y] = wl if edge_l else wd if edge_r else w
            elif c == 'L':  # label
                if kind == 'purified':
                    px[x, y] = (250, 250, 250, 255) if y == 9 else (40, 120, 210, 255)
                elif kind == 'dirty':
                    px[x, y] = (200, 190, 160, 255) if y == 9 else (150, 120, 80, 255)
                else:
                    px[x, y] = (235, 240, 245, 200) if y == 9 else (60, 140, 220, 200)
            else:
                filled = y >= water_top
                if c == 'R':
                    base = wd if filled else plastic_d
                elif c == 'b':
                    base = wd if filled else plastic_d
                else:
                    base = w if filled else plastic
                if edge_l:
                    base = wl if filled else plastic_l
                if edge_r:
                    base = wd if filled else plastic_d
                px[x, y] = base
    if kind == 'purified':
        px[5, 7] = (255, 255, 255, 230)  # glint
        px[5, 8] = (255, 255, 255, 200)
    if kind == 'dirty':
        for x, y in [(6, 13), (8, 12), (5, 14), (9, 14)]:
            px[x, y] = (70, 60, 36, 255)  # sediment
    im.save(A + {'empty': 'plastic_bottle', 'water': 'water', 'dirty': 'dirty_water', 'purified': 'purified_water'}[kind] + '.png')


for k in ('empty', 'water', 'dirty', 'purified'):
    bottle(k)
