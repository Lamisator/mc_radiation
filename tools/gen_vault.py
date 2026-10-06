"""Generates every asset of the vault blocks: textures, block and item models, blockstates, loot tables, recipes,
tags, language entries and sounds. Run from the repository root: python3 tools/gen_vault.py
Needs Pillow, numpy, soundfile and oggenc (vorbis-tools)."""
import json
import math
import os
import random
import subprocess
import tempfile

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFilter

RES = "src/main/resources"
A = f"{RES}/assets/radiation"
D = f"{RES}/data/radiation"
rnd = random.Random(73)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(img, rel):
    path = f"{A}/textures/{rel}.png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def noise(img, amount=6, seed=0):
    r = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            p = px[x, y]
            if len(p) == 4 and p[3] == 0:
                continue
            d = r.randint(-amount, amount)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in p[:3]) + tuple(p[3:])
    return img


# ---------------------------------------------------------------- palette
STEEL = (122, 134, 146)
STEEL_DARK = (78, 88, 100)
STEEL_LIGHT = (160, 170, 180)
BLUE = (46, 92, 156)
BLUE_DARK = (30, 62, 110)
YELLOW = (226, 184, 38)
YELLOW_DARK = (168, 128, 20)
BLACK = (28, 28, 30)


def panel(base, edge, seed, rivets=True):
    img = Image.new("RGBA", (16, 16), base + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=edge + (255,))
    d.line([(1, 1), (14, 1)], fill=tuple(min(255, c + 25) for c in base) + (255,))
    d.line([(1, 1), (1, 14)], fill=tuple(min(255, c + 18) for c in base) + (255,))
    if rivets:
        for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            d.point((x, y), fill=STEEL_LIGHT + (255,))
            d.point((x + 1, y + 1), fill=edge + (255,))
    return noise(img, 4, seed)


# ---------------------------------------------------------------- block textures
wall = panel(STEEL, STEEL_DARK, 1)
save(wall, "block/vault_wall")

stripe = panel(STEEL, STEEL_DARK, 2, rivets=False)
d = ImageDraw.Draw(stripe)
d.rectangle([0, 5, 15, 10], fill=BLUE + (255,))
d.rectangle([0, 7, 15, 8], fill=YELLOW + (255,))
d.line([(0, 5), (15, 5)], fill=BLUE_DARK + (255,))
d.line([(0, 10), (15, 10)], fill=BLUE_DARK + (255,))
save(noise(stripe, 3, 3), "block/vault_wall_stripe")

pipes = panel(STEEL_DARK, BLACK, 4, rivets=False)
d = ImageDraw.Draw(pipes)
for x0, col in [(2, (150, 110, 70)), (7, STEEL_LIGHT), (11, BLUE)]:
    d.rectangle([x0, 0, x0 + 2, 15], fill=col + (255,))
    d.line([(x0, 0), (x0, 15)], fill=tuple(min(255, c + 40) for c in col) + (255,))
    d.line([(x0 + 2, 0), (x0 + 2, 15)], fill=tuple(c // 2 for c in col) + (255,))
    d.rectangle([x0 - 1, 6, x0 + 3, 7], fill=BLACK + (255,))
save(noise(pipes, 3, 5), "block/vault_wall_pipes")

floor = Image.new("RGBA", (16, 16), (92, 98, 106, 255))
d = ImageDraw.Draw(floor)
for y in range(0, 16, 4):
    for x in range(0, 16, 4):
        ox = 2 if (y // 4) % 2 else 0
        d.line([((x + ox) % 16, y + 1), ((x + ox) % 16 + 1, y + 2)], fill=(140, 146, 154, 255))
        d.point(((x + ox) % 16 + 1, y + 1), fill=(60, 64, 70, 255))
d.rectangle([0, 0, 15, 15], outline=(64, 68, 76, 255))
save(noise(floor, 4, 6), "block/vault_floor")

grate = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(grate)
for i in range(0, 16, 3):
    d.line([(i, 0), (i, 15)], fill=(110, 116, 124, 255))
    d.line([(0, i), (15, i)], fill=(96, 102, 110, 255))
d.rectangle([0, 0, 15, 15], outline=(70, 74, 82, 255))
save(grate, "block/vault_grate")

haz = Image.new("RGBA", (16, 16), YELLOW + (255,))
px = haz.load()
for y in range(16):
    for x in range(16):
        if ((x + y) // 4) % 2 == 0:
            px[x, y] = BLACK + (255,)
save(noise(haz, 5, 7), "block/vault_hazard_stripes")

frame = Image.new("RGBA", (16, 16), (58, 62, 68, 255))
d = ImageDraw.Draw(frame)
d.rectangle([0, 0, 15, 15], outline=(36, 38, 42, 255))
d.rectangle([2, 2, 13, 13], outline=(82, 86, 94, 255))
for x, y in [(4, 4), (11, 4), (4, 11), (11, 11)]:
    d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(130, 134, 140, 255))
save(noise(frame, 4, 8), "block/vault_door_frame")

light = Image.new("RGBA", (16, 16), (210, 230, 245, 255))
d = ImageDraw.Draw(light)
d.rectangle([0, 0, 15, 15], outline=(120, 128, 138, 255))
d.rectangle([1, 1, 14, 14], outline=(170, 178, 188, 255))
for x in range(3, 13, 3):
    d.line([(x, 2), (x, 13)], fill=(235, 245, 255, 255))
save(light, "block/vault_light_panel")

for name, col in [("blue", (70, 170, 255)), ("yellow", (255, 214, 70)), ("white", (235, 245, 255))]:
    img = Image.new("RGBA", (16, 16), col + (255,))
    d = ImageDraw.Draw(img)
    core = tuple(min(255, c + 70) for c in col)
    d.rectangle([0, 6, 15, 9], fill=core + (255,))
    d.rectangle([0, 0, 1, 15], fill=(90, 96, 104, 255))
    d.rectangle([14, 0, 15, 15], fill=(90, 96, 104, 255))
    save(img, f"block/vault_neon_{name}")

# console: side, top (screen + button), front
side = panel(STEEL_DARK, BLACK, 9, rivets=False)
d = ImageDraw.Draw(side)
d.rectangle([0, 12, 15, 15], fill=YELLOW + (255,))
for x in range(0, 16, 4):
    d.polygon([(x, 15), (x + 2, 12), (x + 3, 12), (x + 1, 15)], fill=BLACK + (255,))
save(side, "block/vault_console_side")
top = Image.new("RGBA", (16, 16), STEEL + (255,))
d = ImageDraw.Draw(top)
d.rectangle([0, 0, 15, 15], outline=STEEL_DARK + (255,))
d.rectangle([2, 2, 9, 8], fill=(20, 40, 24, 255), outline=BLACK + (255,))
for y in range(3, 8, 2):
    d.line([(3, y), (3 + rnd.randint(2, 5), y)], fill=(90, 230, 110, 255))
d.ellipse([10, 9, 14, 13], fill=(200, 30, 30, 255), outline=(90, 10, 10, 255))
d.point((11, 10), fill=(255, 120, 120, 255))
for i, c in enumerate([(60, 220, 90), (240, 200, 40), (230, 50, 40)]):
    d.point((3 + i * 2, 12), fill=c + (255,))
save(noise(top, 3, 10), "block/vault_console_top")
front = panel(STEEL, STEEL_DARK, 11, rivets=False)
d = ImageDraw.Draw(front)
d.rectangle([3, 3, 12, 6], fill=BLUE + (255,))
d.rectangle([4, 4, 11, 5], fill=YELLOW + (255,))
save(front, "block/vault_console_front")

# ---------------------------------------------------------------- entity textures
# vault door: 256 x 256; front art 0-128, back art 128-256 (v 0-128); rim strip at v 128-144
S = 128
door = Image.new("RGBA", (256, 256), (0, 0, 0, 255))
arr = np.zeros((S, S, 3))
yy, xx = np.mgrid[0:S, 0:S]
r = np.hypot(xx - S / 2 + 0.5, yy - S / 2 + 0.5) / (S / 2)
ang = np.arctan2(yy - S / 2, xx - S / 2)
steel = np.array(STEEL, float)
rng = np.random.default_rng(73)
grain = rng.normal(0, 5, (S, S))
for k in range(3):
    arr[..., k] = steel[k] + grain + 10 * np.cos(ang * 2) * (r < 0.8)
# concentric grooves
for rr, w in [(0.22, 0.012), (0.36, 0.015), (0.56, 0.012), (0.76, 0.02)]:
    m = np.abs(r - rr) < w
    arr[m] = np.array(STEEL_DARK)
# hub
arr[r < 0.2] = np.array((96, 104, 112)) + grain[r < 0.2, None]
# yellow outer band and teeth
band = r > 0.78
arr[band] = np.array(YELLOW) + grain[band, None] * 1.5
arr[(r > 0.78) & (r < 0.8)] = np.array(YELLOW_DARK)
# wear on the paint
wear = band & (rng.random((S, S)) < 0.05)
arr[wear] = np.array(STEEL_LIGHT)
# bolts
img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
d = ImageDraw.Draw(img)
for i in range(16):
    a = 2 * math.pi * i / 16
    cx, cy = S / 2 + math.cos(a) * S * 0.33, S / 2 + math.sin(a) * S * 0.33
    d.ellipse([cx - 2.5, cy - 2.5, cx + 2.5, cy + 2.5], fill=(70, 76, 84), outline=(40, 44, 50))
    d.point((cx - 1, cy - 1), fill=(170, 176, 184))
for i in range(12):
    a = 2 * math.pi * (i + 0.25) / 12
    cx, cy = S / 2 + math.cos(a) * S * 0.45, S / 2 + math.sin(a) * S * 0.45
    d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=(50, 54, 60))
door.paste(img, (0, 0))
# back: plain steel, heavy hub and cross braces of the hydraulics
back = Image.fromarray(np.clip(np.stack([steel[k] - 15 + grain for k in range(3)], -1), 0, 255).astype(np.uint8))
d = ImageDraw.Draw(back)
for a in range(0, 180, 45):
    t = math.radians(a)
    dx, dy = math.cos(t) * S * 0.47, math.sin(t) * S * 0.47
    d.line([(S / 2 - dx, S / 2 - dy), (S / 2 + dx, S / 2 + dy)], fill=(70, 76, 84), width=7)
    d.line([(S / 2 - dx, S / 2 - dy), (S / 2 + dx, S / 2 + dy)], fill=(120, 126, 134), width=2)
d.ellipse([S / 2 - 22, S / 2 - 22, S / 2 + 22, S / 2 + 22], fill=(62, 66, 74), outline=(36, 40, 46), width=3)
d.ellipse([S / 2 - 10, S / 2 - 10, S / 2 + 10, S / 2 + 10], fill=(150, 156, 164), outline=(60, 64, 70), width=2)
d.ellipse([2, 2, S - 3, S - 3], outline=(60, 64, 70), width=4)
door.paste(back, (128, 0))
# rim strip
rim = Image.new("RGB", (256, 16), (110, 118, 128))
d = ImageDraw.Draw(rim)
d.line([(0, 0), (255, 0)], fill=YELLOW_DARK)
d.line([(0, 15), (255, 15)], fill=(60, 64, 70))
for x in range(0, 256, 4):
    d.line([(x, 3), (x, 12)], fill=(96, 102, 112))
door.paste(rim, (0, 128))
# screw arm: steel (v 144-160), chrome piston (160-176), hazard sleeve (176-192), head face (u 0-64, v 192-256),
# housing panel (u 128-192, v 192-256)
arm = Image.new("RGB", (256, 16), (96, 104, 114))
d = ImageDraw.Draw(arm)
for x in range(0, 256, 16):
    d.line([(x, 0), (x, 15)], fill=(70, 76, 84))
    d.point((x + 3, 3), fill=(170, 176, 184))
    d.point((x + 3, 12), fill=(170, 176, 184))
door.paste(noise(arm.convert("RGBA"), 4, 30).convert("RGB"), (0, 144))
chrome = Image.new("RGB", (256, 16))
for y in range(16):
    c = int(150 + 80 * math.sin(y / 15 * math.pi))
    ImageDraw.Draw(chrome).line([(0, y), (255, y)], fill=(c - 10, c, c + 8))
door.paste(chrome, (0, 160))
sleeve = Image.new("RGB", (256, 16), YELLOW)
px = sleeve.load()
for y in range(16):
    for x in range(256):
        if ((x + y) // 8) % 2 == 0:
            px[x, y] = BLACK
door.paste(sleeve, (0, 176))
head = Image.new("RGB", (64, 64), (84, 90, 98))
d = ImageDraw.Draw(head)
d.ellipse([1, 1, 62, 62], fill=(120, 128, 138), outline=(50, 54, 60), width=2)
for i in range(3):
    # the screw's thread as a spiral
    pts = []
    for k in range(60):
        a = i * 2 * math.pi / 3 + k * 0.09
        rr = 6 + k * 0.42
        pts.append((32 + math.cos(a) * rr, 32 + math.sin(a) * rr))
    d.line(pts, fill=(60, 64, 72), width=3)
for i in range(8):
    a = i * math.pi / 4
    d.ellipse([32 + math.cos(a) * 26 - 2, 32 + math.sin(a) * 26 - 2, 32 + math.cos(a) * 26 + 2, 32 + math.sin(a) * 26 + 2], fill=YELLOW)
d.ellipse([26, 26, 38, 38], fill=(180, 186, 194), outline=(60, 64, 70))
door.paste(head, (0, 192))
housing = Image.new("RGB", (64, 64), (88, 96, 108))
d = ImageDraw.Draw(housing)
d.rectangle([0, 0, 63, 63], outline=(50, 54, 60), width=3)
d.rectangle([8, 8, 55, 55], outline=(70, 76, 86), width=2)
d.rectangle([0, 50, 63, 63], fill=YELLOW)
for x in range(0, 64, 8):
    d.polygon([(x, 63), (x + 4, 50), (x + 8, 50), (x + 4, 63)], fill=BLACK)
for x, y in [(5, 5), (58, 5), (5, 44), (58, 44)]:
    d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(150, 156, 164))
d.rectangle([22, 18, 41, 30], fill=(30, 34, 38))
d.rectangle([24, 20, 30, 28], fill=(230, 160, 40))
door.paste(noise(housing.convert("RGBA"), 3, 31).convert("RGB"), (128, 192))
os.makedirs(f"{A}/textures/entity", exist_ok=True)
door.save(f"{A}/textures/entity/vault_door.png")

# sliding door: 64 x 64; front 0-16 x 0-32 (16 px wide = 1 block, 32 px = 2 blocks), back 16-32, edges 32-36
sd = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
def door_face(seed, mirror=False):
    f = Image.new("RGB", (16, 32), STEEL)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 15, 31], outline=STEEL_DARK)
    d.rectangle([2, 3, 13, 13], outline=STEEL_DARK)
    d.rectangle([4, 5, 11, 9], fill=(60, 90, 110))
    d.line([(5, 6), (7, 6)], fill=(140, 180, 200))
    d.rectangle([2, 16, 13, 24], outline=STEEL_DARK)
    d.line([(4, 20), (11, 20)], fill=BLUE)
    for x in range(0, 16, 4):
        d.polygon([(x, 31), (x + 2, 27), (x + 4, 27), (x + 2, 31)], fill=BLACK)
    d.line([(0, 27), (15, 27)], fill=YELLOW_DARK)
    for x in range(0, 16):
        if f.getpixel((x, 29)) != BLACK:
            f.putpixel((x, 29), YELLOW)
            f.putpixel((x, 30), YELLOW)
            f.putpixel((x, 28), YELLOW)
    if mirror:
        f = f.transpose(Image.FLIP_LEFT_RIGHT)
    return noise(f.convert("RGBA"), 3, seed)
sd.paste(door_face(20), (0, 0))
sd.paste(door_face(21, True), (16, 0))
edge = Image.new("RGBA", (4, 64), STEEL_DARK + (255,))
ImageDraw.Draw(edge).line([(1, 0), (1, 63)], fill=STEEL + (255,))
sd.paste(edge, (32, 0))
sd.save(f"{A}/textures/entity/vault_sliding_door.png")

# ---------------------------------------------------------------- item icons
icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(icon)
for i in range(12):
    a = 2 * math.pi * (i + 0.25) / 12
    x, y = 7.5 + math.cos(a) * 7, 7.5 + math.sin(a) * 7
    d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=YELLOW + (255,))
d.ellipse([1, 1, 14, 14], fill=YELLOW + (255,), outline=YELLOW_DARK + (255,))
d.ellipse([3, 3, 12, 12], fill=STEEL + (255,))
d.ellipse([6, 6, 9, 9], fill=STEEL_DARK + (255,))
d.text((4, 3), "", fill=(0, 0, 0, 255))
save(icon, "item/vault_door")
sdi = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
sdi.paste(door_face(22).resize((8, 16), Image.NEAREST), (4, 0))
save(sdi, "item/vault_sliding_door")

# ---------------------------------------------------------------- models and blockstates
def model(name, obj):
    write(f"{A}/models/block/{name}.json", obj)


def blockstate(name, obj):
    write(f"{A}/blockstates/{name}.json", obj)


def item_def(name, model_id):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model_id}})


CUBES = ["vault_wall", "vault_wall_stripe", "vault_wall_pipes", "vault_floor", "vault_hazard_stripes", "vault_door_frame"]
for n in CUBES:
    model(n, {"parent": "minecraft:block/cube_all", "textures": {"all": f"radiation:block/{n}"}})
    blockstate(n, {"variants": {"": {"model": f"radiation:block/{n}"}}})
    item_def(n, f"radiation:block/{n}")
model("vault_grate", {"parent": "minecraft:block/cube_all", "textures": {"all": "radiation:block/vault_grate"}})
blockstate("vault_grate", {"variants": {"": {"model": "radiation:block/vault_grate"}}})
item_def("vault_grate", "radiation:block/vault_grate")

# invisible blocks only need a particle texture
model("vault_door", {"textures": {"particle": "radiation:block/vault_door_frame"}})
blockstate("vault_door", {"variants": {"": {"model": "radiation:block/vault_door"}}})
blockstate("vault_door_part", {"variants": {"": {"model": "radiation:block/vault_door"}}})
item_def("vault_door", "radiation:item/vault_door")
write(f"{A}/models/item/vault_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "radiation:item/vault_door"}})
model("vault_sliding_door", {"textures": {"particle": "radiation:block/vault_wall"}})
blockstate("vault_sliding_door", {"variants": {"": {"model": "radiation:block/vault_sliding_door"}}})
item_def("vault_sliding_door", "radiation:item/vault_sliding_door")
write(f"{A}/models/item/vault_sliding_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "radiation:item/vault_sliding_door"}})


def faces(tex, uv=None):
    f = {}
    for side in ["north", "south", "east", "west", "up", "down"]:
        f[side] = {"texture": tex}
        if uv:
            f[side]["uv"] = uv
    return f


model("vault_console", {
    "parent": "minecraft:block/block",
    "textures": {"side": "radiation:block/vault_console_side", "top": "radiation:block/vault_console_top",
                 "front": "radiation:block/vault_console_front", "particle": "radiation:block/vault_console_side"},
    "elements": [
        {"from": [3, 0, 3], "to": [13, 11, 13], "faces": {
            "north": {"texture": "#side", "uv": [3, 5, 13, 16]}, "south": {"texture": "#side", "uv": [3, 5, 13, 16]},
            "east": {"texture": "#side", "uv": [3, 5, 13, 16]}, "west": {"texture": "#side", "uv": [3, 5, 13, 16]},
            "down": {"texture": "#side", "cullface": "down"}}},
        {"from": [1, 11, 1], "to": [15, 15, 15], "faces": {
            "up": {"texture": "#top"}, "down": {"texture": "#side"},
            "north": {"texture": "#front", "uv": [1, 0, 15, 4]}, "south": {"texture": "#side", "uv": [1, 0, 15, 4]},
            "east": {"texture": "#side", "uv": [1, 0, 15, 4]}, "west": {"texture": "#side", "uv": [1, 0, 15, 4]}}},
    ]})
blockstate("vault_console", {"variants": {f"facing={f}": {"model": "radiation:block/vault_console", **({"y": r} if r else {})}
                                          for f, r in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}})
item_def("vault_console", "radiation:block/vault_console")

ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
for name, tube in [("vault_light_panel", False), ("vault_neon_blue", True), ("vault_neon_yellow", True), ("vault_neon_white", True)]:
    tex = f"radiation:block/{name}"
    if tube:
        el = {"from": [0, 0, 7], "to": [16, 2, 9], "faces": {
            "up": {"texture": "#t", "uv": [0, 6, 16, 10]}, "down": {"texture": "#t", "uv": [0, 6, 16, 10]},
            "north": {"texture": "#t", "uv": [0, 6, 16, 8]}, "south": {"texture": "#t", "uv": [0, 6, 16, 8]},
            "east": {"texture": "#t", "uv": [0, 0, 2, 2]}, "west": {"texture": "#t", "uv": [0, 0, 2, 2]}}}
    else:
        el = {"from": [2, 0, 2], "to": [14, 1, 14], "faces": {
            "up": {"texture": "#t"}, "down": {"texture": "#t", "cullface": "down"},
            "north": {"texture": "#t", "uv": [2, 0, 14, 1]}, "south": {"texture": "#t", "uv": [2, 0, 14, 1]},
            "east": {"texture": "#t", "uv": [2, 0, 14, 1]}, "west": {"texture": "#t", "uv": [2, 0, 14, 1]}}}
    model(name, {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"t": tex, "particle": tex}, "elements": [el]})
    blockstate(name, {"variants": {f"facing={f}": {"model": f"radiation:block/{name}", **r} for f, r in ROT.items()}})
    item_def(name, f"radiation:block/{name}")

# ---------------------------------------------------------------- loot, tags, recipes
DROPS = CUBES + ["vault_grate", "vault_door", "vault_console", "vault_light_panel", "vault_neon_blue", "vault_neon_yellow", "vault_neon_white"]
for n in DROPS:
    write(f"{D}/loot_table/blocks/{n}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": f"radiation:{n}"}]}], "random_sequence": f"radiation:blocks/{n}"})
write(f"{D}/loot_table/blocks/vault_sliding_door.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [
    {"condition": "minecraft:block_state_property", "block": "radiation:vault_sliding_door", "properties": {"half": "lower"}},
    {"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": "radiation:vault_sliding_door"}]}],
    "random_sequence": "radiation:blocks/vault_sliding_door"})

tagp = f"{RES}/data/minecraft/tags/block/mineable/pickaxe.json"
tag = json.load(open(tagp))
for n in DROPS + ["vault_sliding_door", "vault_door_part"]:
    if f"radiation:{n}" not in tag["values"]:
        tag["values"].append(f"radiation:{n}")
write(tagp, tag)
write(f"{RES}/data/minecraft/tags/block/needs_diamond_tool.json", {"replace": False, "values": ["radiation:vault_door", "radiation:vault_door_part"]})
write(f"{RES}/data/minecraft/tags/block/needs_iron_tool.json", {"replace": False, "values": ["radiation:vault_door_frame"]})


def shaped(name, pattern, key, count=1, category="building"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                      "result": {"id": f"radiation:{name}", **({"count": count} if count > 1 else {})}})


def shapeless(name, ingredients, count=1, category="building"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
                                      "result": {"id": f"radiation:{name}", **({"count": count} if count > 1 else {})}})


shaped("vault_door", ["III", "IRI", "III"], {"I": "minecraft:iron_block", "R": "minecraft:redstone_block"}, category="redstone")
shaped("vault_console", [" B ", "GRG", "III"], {"B": "minecraft:stone_button", "G": "minecraft:glass_pane", "R": "minecraft:redstone",
                                                "I": "minecraft:iron_ingot"}, category="redstone")
shaped("vault_sliding_door", ["II", "PI", "II"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston"}, 2, "redstone")
shaped("vault_wall", ["SSS", "SIS", "SSS"], {"S": "minecraft:smooth_stone", "I": "minecraft:iron_ingot"}, 8)
shapeless("vault_wall_stripe", ["radiation:vault_wall", "radiation:vault_wall", "minecraft:yellow_dye", "minecraft:blue_dye"], 2)
shapeless("vault_wall_pipes", ["radiation:vault_wall", "minecraft:copper_ingot"])
shapeless("vault_floor", ["radiation:vault_wall", "minecraft:gray_dye"])
shaped("vault_grate", ["BB", "BB"], {"B": "minecraft:iron_bars"}, 4)
shapeless("vault_hazard_stripes", ["radiation:vault_wall", "minecraft:yellow_dye", "minecraft:black_dye"])
shaped("vault_door_frame", ["IO", "OI"], {"I": "minecraft:iron_ingot", "O": "minecraft:obsidian"}, 4)
shaped("vault_light_panel", ["NGN"], {"N": "minecraft:iron_nugget", "G": "minecraft:glowstone"}, 2, "redstone")
for color in ["blue", "yellow", "white"]:
    shapeless(f"vault_neon_{color}", ["minecraft:glass_pane", "minecraft:glowstone_dust", f"minecraft:{color}_dye"], 2, "redstone")

# ---------------------------------------------------------------- language
langp = f"{A}/lang/en_us.json"
lang = json.load(open(langp))
lang.update({
    "block.radiation.vault_door": "Vault Door",
    "block.radiation.vault_door.desc": "A cog-shaped vault door for a round 5×5 opening, opened by a screw arm behind it. Needs room behind it: 7 blocks deep, 4 above its centre, 8 to the side. Sneak-use to set its number.",
    "block.radiation.vault_door.no_room": "The vault door needs a free 5×5 opening.",
    "block.radiation.vault_door.use_console": "Use a Vault Door Console to open the door.",
    "block.radiation.vault_door.no_permission": "Only operators can renumber a vault door.",
    "block.radiation.vault_door_part": "Vault Door",
    "block.radiation.vault_console": "Vault Door Console",
    "block.radiation.vault_console.desc": "Opens and closes the nearest vault door within 16 blocks.",
    "block.radiation.vault_console.no_door": "No vault door within 16 blocks.",
    "block.radiation.vault_console.opening": "Vault door %s opening",
    "block.radiation.vault_console.closing": "Vault door %s closing",
    "block.radiation.vault_sliding_door": "Vault Sliding Door",
    "block.radiation.vault_sliding_door.desc": "Slides up into the wall above. Neighbouring doors in the same wall open together.",
    "block.radiation.vault_wall": "Vault Wall Panel",
    "block.radiation.vault_wall_stripe": "Striped Vault Wall Panel",
    "block.radiation.vault_wall_pipes": "Vault Pipe Panel",
    "block.radiation.vault_floor": "Vault Floor Plate",
    "block.radiation.vault_grate": "Vault Floor Grate",
    "block.radiation.vault_hazard_stripes": "Hazard Stripes",
    "block.radiation.vault_door_frame": "Vault Door Frame",
    "block.radiation.vault_light_panel": "Vault Light Panel",
    "block.radiation.vault_neon_blue": "Blue Neon Tube",
    "block.radiation.vault_neon_yellow": "Yellow Neon Tube",
    "block.radiation.vault_neon_white": "White Neon Tube",
    "screen.radiation.vault_door": "Vault Door",
    "screen.radiation.vault_door.number": "Printed number (0–999)",
    "screen.radiation.vault_door.roll_left": "Rolls aside: to the left",
    "screen.radiation.vault_door.roll_right": "Rolls aside: to the right",
    "subtitles.radiation.vault_door_open": "Vault door opens",
    "subtitles.radiation.vault_door_close": "Vault door closes",
    "subtitles.radiation.sliding_door_open": "Sliding door opens",
    "subtitles.radiation.sliding_door_close": "Sliding door closes",
    "subtitles.radiation.vault_console_beep": "Vault console beeps",
})
write(langp, lang)

# ---------------------------------------------------------------- sounds
SR = 44100
rs = np.random.default_rng(7)


def ogg(name, x, volume=0.9):
    x = x / max(1e-6, np.abs(x).max()) * volume
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        sf.write(wav, x.astype(np.float32), SR)
        subprocess.run(["oggenc", "-Q", "-q", "5", "-o", f"{A}/sounds/{name}.ogg", wav], check=True)


def lowpass(x, a):
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def env(n, attack, release):
    e = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    e[:a] = np.linspace(0, 1, a)
    e[n - r:] = np.linspace(1, 0, r)
    return e


def vault_door(closing):
    # 0-1.25 s arm extends, 1.25-2.25 screws in, 2.25-4.05 pulls the door, 4.05-4.75 unscrews, 4.75-8.75 door rolls
    dur = 9.3
    n = int(dur * SR)
    t = np.arange(n) / SR
    mech = np.zeros(n)
    # arm motor whine while extending
    ext = (t < 1.25)
    mech += ext * 0.35 * np.sin(2 * np.pi * (140 + 60 * t) * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t))
    # screwing: ratcheting clicks with a rising whine
    for k in np.arange(1.3, 2.25, 0.07):
        i = int(k * SR)
        m = int(0.03 * SR)
        mech[i:i + m] += rs.normal(0, 1, m) * np.exp(-np.arange(m) / (0.006 * SR)) * 0.9
    scr = (t > 1.25) & (t < 2.25)
    mech += scr * 0.25 * np.sin(2 * np.pi * (300 + 200 * (t - 1.25)) * t)
    i = int(2.2 * SR)
    m = int(0.25 * SR)
    mech[i:i + m] += np.sin(2 * np.pi * 80 * np.arange(m) / SR) * np.exp(-np.arange(m) / (0.06 * SR)) * 1.4
    # hydraulic pull
    mech += lowpass(rs.normal(0, 1, n), 0.5) * np.exp(-((t - 3.1) / 0.7) ** 2) * 0.7
    mech += ((t > 2.25) & (t < 4.05)) * 0.4 * np.sin(2 * np.pi * 55 * t)
    # unscrewing clicks
    for k in np.arange(4.1, 4.75, 0.08):
        i = int(k * SR)
        m = int(0.03 * SR)
        mech[i:i + m] += rs.normal(0, 1, m) * np.exp(-np.arange(m) / (0.006 * SR)) * 0.7
    # rolling: rumble and gear teeth
    roll = (t > 4.75) & (t < 8.75)
    rumble = lowpass(rs.normal(0, 1, n), 0.01) * 6
    teeth = np.zeros(n)
    for k in np.arange(4.9, 8.7, 0.32):
        i = int(k * SR)
        m = min(n - i, int(0.12 * SR))
        teeth[i:i + m] += np.sin(2 * np.pi * 95 * np.arange(m) / SR) * np.exp(-np.arange(m) / (0.03 * SR))
    mech += roll * (rumble * 0.8 + 0.5 * np.sin(2 * np.pi * 48 * t)) + teeth * 0.8
    i = int(8.75 * SR)
    m = n - i
    mech[i:] += np.sin(2 * np.pi * 60 * np.arange(m) / SR) * np.exp(-np.arange(m) / (0.15 * SR)) * 1.5
    if closing:
        mech = mech[::-1]
    # klaxon over the first 2.5 s either way
    tone = np.where((t * 2.5) % 1 < 0.5, 420.0, 330.0)
    phase = np.cumsum(tone) / SR * 2 * np.pi
    alarm = (t < 2.5) * 0.3 * np.sign(np.sin(phase)) * (0.6 + 0.4 * np.sin(2 * np.pi * 5 * t))
    return (mech + alarm) * env(n, 0.01, 0.3)


def sliding(opening):
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    hiss = lowpass(rs.normal(0, 1, n), 0.35) * np.exp(-((t - 0.25) / 0.18) ** 2)
    motor = 0.4 * np.sin(2 * np.pi * (110 if opening else 90) * t) * (t < 0.6)
    clunk = np.zeros(n)
    i = int(0.6 * SR)
    m = n - i
    clunk[i:] = np.sin(2 * np.pi * 70 * np.arange(m) / SR) * np.exp(-np.arange(m) / (0.05 * SR)) * 1.4
    return (hiss + motor + clunk) * env(n, 0.005, 0.05)


def beep():
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * 1320 * t) * ((t < 0.1) | ((t > 0.16) & (t < 0.26)))
    return x * 0.5


os.makedirs(f"{A}/sounds", exist_ok=True)
ogg("vault_door_open", vault_door(False))
ogg("vault_door_close", vault_door(True))
ogg("sliding_door_open", sliding(True))
ogg("sliding_door_close", sliding(False))
ogg("vault_console_beep", beep(), 0.6)
sp = f"{A}/sounds.json"
sounds = json.load(open(sp))
for name in ["vault_door_open", "vault_door_close", "sliding_door_open", "sliding_door_close", "vault_console_beep"]:
    sounds[name] = {"sounds": [{"name": f"radiation:{name}", "attenuation_distance": 48 if name.startswith("vault_door") else 16}],
                    "subtitle": f"subtitles.radiation.{name}"}
write(sp, sounds)
print("vault assets written")
