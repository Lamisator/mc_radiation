"""Concrete for shielding and blast protection (Radiation 1.3.0): textures, models, loot, tags, recipes, language.
Run from the repository root after gen_vault.py: python3 tools/gen_concrete.py"""
import json
import os
import random

from PIL import Image, ImageDraw

RES = "src/main/resources"
A = f"{RES}/assets/radiation"
D = f"{RES}/data/radiation"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def concrete(base, seed, rebar=False, speck=None):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), base + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = r.randint(-9, 9)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in base) + (255,)
    d = ImageDraw.Draw(img)
    for _ in range(10):
        x, y = r.randint(0, 15), r.randint(0, 15)
        c = speck or tuple(max(0, c - 30) for c in base)
        d.point((x, y), fill=c + (255,))
    # formwork tie holes
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=tuple(max(0, c - 55) for c in base) + (255,))
    if rebar:
        d.rectangle([0, 0, 15, 15], outline=tuple(max(0, c - 25) for c in base) + (255,))
    return img


os.makedirs(f"{A}/textures/block", exist_ok=True)
concrete((150, 150, 146), 1, True).save(f"{A}/textures/block/reinforced_concrete.png")
concrete((92, 90, 98), 2, True, (150, 120, 90)).save(f"{A}/textures/block/heavy_concrete.png")

for n in ("reinforced_concrete", "heavy_concrete"):
    write(f"{A}/models/block/{n}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"radiation:block/{n}"}})
    write(f"{A}/blockstates/{n}.json", {"variants": {"": {"model": f"radiation:block/{n}"}}})
    write(f"{A}/items/{n}.json", {"model": {"type": "minecraft:model", "model": f"radiation:block/{n}"}})
    write(f"{D}/loot_table/blocks/{n}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": f"radiation:{n}"}]}], "random_sequence": f"radiation:blocks/{n}"})

tagp = f"{RES}/data/minecraft/tags/block/mineable/pickaxe.json"
tag = json.load(open(tagp))
for n in ("reinforced_concrete", "heavy_concrete"):
    if f"radiation:{n}" not in tag["values"]:
        tag["values"].append(f"radiation:{n}")
write(tagp, tag)
dtp = f"{RES}/data/minecraft/tags/block/needs_diamond_tool.json"
dt = json.load(open(dtp))
for n in ("reinforced_concrete", "heavy_concrete"):
    if f"radiation:{n}" not in dt["values"]:
        dt["values"].append(f"radiation:{n}")
write(dtp, dt)

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
          "green", "red", "black"]
write(f"{D}/tags/block/shielding_concrete.json", {"replace": False, "values": ["radiation:reinforced_concrete", "radiation:vault_wall",
    "radiation:vault_wall_stripe", "radiation:vault_wall_pipes", "radiation:vault_floor", "radiation:vault_hazard_stripes"]
    + [f"minecraft:{c}_concrete" for c in COLORS]})
write(f"{D}/tags/block/shielding_heavy.json", {"replace": False, "values": ["radiation:heavy_concrete", "radiation:vault_door",
    "radiation:vault_door_part", "radiation:vault_door_frame", "minecraft:iron_block", "minecraft:gold_block", "minecraft:netherite_block",
    "minecraft:obsidian", "minecraft:crying_obsidian"]})

write(f"{D}/recipe/reinforced_concrete.json", {"type": "minecraft:crafting_shaped", "category": "building",
    "key": {"C": "minecraft:gray_concrete", "I": "minecraft:iron_ingot"}, "pattern": ["CCC", "CIC", "CCC"],
    "result": {"id": "radiation:reinforced_concrete", "count": 8}})
write(f"{D}/recipe/heavy_concrete.json", {"type": "minecraft:crafting_shaped", "category": "building",
    "key": {"C": "radiation:reinforced_concrete", "R": "minecraft:raw_iron"}, "pattern": ["CRC", "RCR", "CRC"],
    "result": {"id": "radiation:heavy_concrete", "count": 5}})

langp = f"{A}/lang/en_us.json"
lang = json.load(open(langp))
lang.update({
    "block.radiation.reinforced_concrete": "Reinforced Concrete",
    "block.radiation.reinforced_concrete.desc": "Steel-reinforced: shrugs off explosions and absorbs 55 % of radiation per block",
    "block.radiation.heavy_concrete": "Heavy Concrete",
    "block.radiation.heavy_concrete.desc": "Iron-ore aggregate: blast-proof and absorbs 75 % of radiation per block",
})
write(langp, lang)
print("concrete assets written")
