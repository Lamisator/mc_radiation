"""Textures for thirst: the canteen, the water purifier and the HUD droplets (Pillow). Run from the repo root."""
from PIL import Image

A = 'src/main/resources/assets/radiation/textures/'


def img(w=16, h=16):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return im, im.load()


def save(im, path):
    im.save(A + path)


# ---------------------------------------------------------------- canteen (olive drab, cork-free screw cap, strap)
im, px = img()
O = (98, 112, 58, 255); Od = (66, 76, 38, 255); Ol = (132, 148, 82, 255); K = (36, 36, 34, 255)
M = (160, 160, 165, 255); Md = (105, 105, 110, 255); S = (120, 84, 46, 255); Sd = (86, 58, 30, 255)
body = set()
for y in range(4, 15):
    for x in range(3, 13):
        r = ((x - 7.5) / 5.0) ** 2 + ((y - 9.5) / 5.6) ** 2
        if r <= 1.0:
            body.add((x, y))
for (x, y) in body:
    px[x, y] = O
for (x, y) in body:  # outline + shading
    if (x - 1, y) not in body or (x, y - 1) not in body:
        px[x, y] = Ol
    if (x + 1, y) not in body or (x, y + 1) not in body:
        px[x, y] = Od
for x in range(5, 11):
    px[x, 9] = Od  # seam
for y in range(1, 4):
    for x in range(6, 10):
        px[x, y] = M  # screw cap
for x in range(6, 10):
    px[x, 1] = Md
    px[x, 3] = Md
px[9, 2] = Md
for i in range(5):  # strap from the cap ring to the side
    px[2 + i // 2, 7 - i] = S if i % 2 == 0 else Sd
    px[13 - i // 2, 7 - i] = S if i % 2 == 0 else Sd
px[2, 8] = Sd
px[13, 8] = Sd
px[7, 11] = (190, 190, 120, 255)  # stencil dot
px[8, 11] = (190, 190, 120, 255)
save(im, 'item/canteen.png')

# ---------------------------------------------------------------- purifier
St = (150, 156, 162, 255); Std = (104, 110, 116, 255); Stl = (186, 192, 198, 255); Bk = (40, 44, 48, 255)
Bl = (52, 120, 196, 255); Bll = (120, 190, 240, 255); Y = (232, 190, 30, 255); Kd = (30, 30, 30, 255)


def casing():
    im, px = img()
    for y in range(16):
        for x in range(16):
            px[x, y] = St
    for i in range(16):
        px[i, 0] = Stl; px[0, i] = Stl; px[i, 15] = Std; px[15, i] = Std
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        px[x, y] = Std  # rivets
    return im, px


def front(on):
    im, px = casing()
    # two filter columns behind glass
    for cx in (3, 7):
        for y in range(3, 13):
            for x in range(cx, cx + 3):
                px[x, y] = Bk
        for y in range(4, 12):
            for x in range(cx, cx + 3):
                px[x, y] = (Bll if on and (y + x) % 3 == 0 else Bl) if on else (40, 70, 100, 255)
        for y in range(3, 13):
            px[cx - 1, y] = Std
            px[cx + 3, y] = Std
    # panel: LED, display, hazard stripe
    for y in range(3, 8):
        for x in range(11, 14):
            px[x, y] = Kd
    px[12, 4] = (80, 230, 110, 255) if on else (90, 30, 30, 255)
    px[11, 6] = (120, 200, 255, 255) if on else (40, 50, 60, 255)
    px[12, 6] = (120, 200, 255, 255) if on else (40, 50, 60, 255)
    px[13, 6] = (120, 200, 255, 255) if on else (40, 50, 60, 255)
    for x in range(11, 14):  # tap
        px[x, 10] = Std
    px[12, 11] = Md if False else (105, 105, 110, 255)
    px[12, 12] = (90, 150, 220, 255) if on else (105, 105, 110, 255)
    for x in range(1, 15):
        px[x, 14] = Y if x % 4 < 2 else Kd
    return im


save(front(False), 'block/water_purifier_front.png')
save(front(True), 'block/water_purifier_front_on.png')

im, px = casing()  # side: vents and a pipe
for y in (4, 6, 8, 10):
    for x in range(3, 10):
        px[x, y] = Bk
for y in range(2, 14):
    px[12, y] = Bl
    px[13, y] = (36, 88, 150, 255)
px[12, 2] = Stl; px[13, 2] = Stl; px[12, 13] = Stl; px[13, 13] = Stl
save(im, 'block/water_purifier_side.png')

im, px = casing()  # top: tank lid
for y in range(3, 13):
    for x in range(3, 13):
        if ((x - 7.5) ** 2 + (y - 7.5) ** 2) <= 20:
            px[x, y] = Std
for y in range(5, 11):
    for x in range(5, 11):
        if ((x - 7.5) ** 2 + (y - 7.5) ** 2) <= 8:
            px[x, y] = Stl
px[7, 7] = Bk; px[8, 8] = Bk; px[7, 8] = Bk; px[8, 7] = Bk
save(im, 'block/water_purifier_top.png')

im, px = casing()
save(im, 'block/water_purifier_bottom.png')

# ---------------------------------------------------------------- HUD droplets (9x9, like the hunger bar's icons)
DROP = ["....#....",
        "...#o#...",
        "..#ooo#..",
        ".#oooo.#.",
        "#oooooo.#",
        "#ooooooo#",
        "#ooooooo#",
        ".#ooooo#.",
        "..#####.."]


def droplet(kind):
    im, px = img(9, 9)
    for y, row in enumerate(DROP):
        for x, c in enumerate(row):
            if c == '#':
                px[x, y] = (16, 16, 24, 255)
            elif c in 'o.' and 0 < x < 8 and DROP[y][0:x].count('#') >= 1 and DROP[y][x:].count('#') >= 1:
                if kind == 'empty':
                    px[x, y] = (44, 40, 40, 255)
                else:
                    full = kind == 'full' or x <= 4
                    if full:
                        light = (x + y) < 7 or (x == 2 and y == 5)
                        px[x, y] = (150, 210, 255, 255) if light else (48, 112, 230, 255) if y < 7 else (32, 78, 180, 255)
                    else:
                        px[x, y] = (0, 0, 0, 0)
    if kind != 'empty':
        px[3, 4] = (230, 245, 255, 255)
    return im


for k in ('empty', 'half', 'full'):
    import os
    os.makedirs(A + 'gui/sprites/hud', exist_ok=True)
    save(droplet(k), 'gui/sprites/hud/thirst_%s.png' % k)
