"""Generates the mod's textures and sounds. Run once with Pillow, numpy and soundfile installed.
Vanilla leather textures (extracted from the client jar) are recoloured into the hazmat suit."""
import math, sys, os
import numpy as np
from PIL import Image, ImageDraw
import soundfile as sf

VANILLA = sys.argv[1]
OUT = sys.argv[2]
A = f"{OUT}/assets/radiation"
for d in ["textures/item", "textures/block", "textures/mob_effect",
          "textures/entity/equipment/humanoid", "textures/entity/equipment/humanoid_leggings",
          "textures/entity/equipment/humanoid_baby", "sounds"]:
    os.makedirs(f"{A}/{d}", exist_ok=True)

YELLOW = (232, 190, 30)

def tint(path, rgb):
    im = Image.open(path).convert("RGBA")
    px = np.array(im).astype(float)
    lum = px[..., :3].mean(axis=2, keepdims=True) / 255.0
    px[..., :3] = lum * np.array(rgb)
    return Image.fromarray(px.clip(0, 255).astype(np.uint8))

def over(base, top_path):
    top = Image.open(top_path).convert("RGBA")
    return Image.alpha_composite(base, top)

# --- hazmat armor (worn) ---
V = f"{VANILLA}/assets/minecraft/textures"
for layer in ["humanoid", "humanoid_leggings", "humanoid_baby"]:
    im = over(tint(f"{V}/entity/equipment/{layer}/leather.png", YELLOW), f"{V}/entity/equipment/{layer}/leather_overlay.png")
    if layer == "humanoid":
        d = ImageDraw.Draw(im)
        # visor across the face (head front is u 8..15, v 8..15)
        d.rectangle([9, 10, 14, 12], fill=(40, 60, 70, 255))
        d.rectangle([9, 10, 10, 10], fill=(150, 200, 210, 255))
        # respirator filter on the mouth
        d.rectangle([11, 14, 12, 15], fill=(50, 50, 50, 255))
        # black stripe around the chest (body front u 20..27, v 20..31)
        d.rectangle([20, 26, 27, 27], fill=(30, 30, 30, 255))
    im.save(f"{A}/textures/entity/equipment/{layer}/hazmat.png")

# --- hazmat items ---
for piece in ["helmet", "chestplate", "leggings", "boots"]:
    im = over(tint(f"{V}/item/leather_{piece}.png", YELLOW), f"{V}/item/leather_{piece}_overlay.png")
    if piece == "helmet":
        d = ImageDraw.Draw(im)
        d.rectangle([5, 7, 10, 8], fill=(40, 60, 70, 255))
        d.point((5, 7), fill=(150, 200, 210, 255))
    im.save(f"{A}/textures/item/hazmat_{piece}.png")

def canvas(n=16):
    return Image.new("RGBA", (n, n), (0, 0, 0, 0))

def trefoil(d, cx, cy, r, fg, inner=0.18):
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - r, cy - r, cx + r, cy + r], a0, a0 + 60, fill=fg)
    ri = r * inner * 1.6
    d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=fg)

# --- RadAway: IV bag ---
im = canvas(); d = ImageDraw.Draw(im)
d.rectangle([7, 0, 8, 2], fill=(200, 200, 200, 255))          # hanger
d.rectangle([4, 3, 11, 12], fill=(240, 240, 235, 255))          # bag
d.rectangle([5, 6, 10, 11], fill=(214, 120, 40, 255))           # orange fluid
d.rectangle([5, 6, 10, 6], fill=(240, 160, 80, 255))
d.rectangle([4, 3, 11, 3], fill=(255, 255, 255, 255))
d.rectangle([3, 4, 3, 11], fill=(170, 170, 165, 255)); d.rectangle([12, 4, 12, 11], fill=(170, 170, 165, 255))
d.rectangle([4, 12, 11, 12], fill=(170, 170, 165, 255))
d.rectangle([7, 13, 8, 15], fill=(200, 200, 200, 255))          # tube
d.point((7, 8), fill=(255, 255, 255, 255)); d.point((8, 8), fill=(255, 255, 255, 255))
im.save(f"{A}/textures/item/radaway.png")

# --- Rad-X: pill bottle ---
im = canvas(); d = ImageDraw.Draw(im)
d.rectangle([5, 1, 10, 3], fill=(235, 235, 235, 255))           # cap
d.rectangle([5, 3, 10, 3], fill=(190, 190, 190, 255))
d.rectangle([4, 4, 11, 14], fill=(200, 110, 30, 255))           # amber bottle
d.rectangle([4, 4, 4, 14], fill=(150, 75, 20, 255)); d.rectangle([11, 4, 11, 14], fill=(150, 75, 20, 255))
d.rectangle([4, 14, 11, 14], fill=(130, 65, 15, 255))
d.rectangle([5, 7, 10, 11], fill=(245, 245, 240, 255))          # label
d.rectangle([6, 8, 9, 8], fill=(60, 120, 200, 255)); d.rectangle([6, 10, 8, 10], fill=(60, 120, 200, 255))
d.point((5, 5), fill=(240, 170, 90, 255)); d.point((5, 6), fill=(240, 170, 90, 255))
im.save(f"{A}/textures/item/rad_x.png")

# --- Geiger counter ---
im = canvas(); d = ImageDraw.Draw(im)
d.rectangle([2, 5, 12, 14], fill=(205, 170, 40, 255))           # body
d.rectangle([2, 14, 12, 14], fill=(140, 110, 20, 255)); d.rectangle([12, 5, 12, 14], fill=(140, 110, 20, 255))
d.rectangle([2, 5, 12, 5], fill=(240, 210, 90, 255))
d.rectangle([4, 7, 10, 10], fill=(235, 235, 220, 255))          # dial
d.line([5, 10, 9, 7], fill=(200, 30, 30, 255))                  # needle
d.rectangle([4, 12, 5, 12], fill=(50, 50, 50, 255)); d.rectangle([8, 12, 9, 12], fill=(50, 50, 50, 255))
d.rectangle([4, 2, 9, 4], fill=(80, 80, 80, 255))               # handle
d.rectangle([5, 3, 8, 4], fill=(0, 0, 0, 0))
d.line([13, 9, 14, 6], fill=(40, 40, 40, 255)); d.rectangle([13, 2, 15, 5], fill=(110, 110, 110, 255))  # probe
im.save(f"{A}/textures/item/geiger_counter.png")

# --- Nuclear waste barrel block ---
def barrel_side():
    im = Image.new("RGBA", (16, 16), (200, 170, 30, 255)); d = ImageDraw.Draw(im)
    rng = np.random.default_rng(7)
    for _ in range(40):
        x, y = rng.integers(0, 16, 2); c = int(rng.integers(-25, 15))
        d.point((int(x), int(y)), fill=(200 + c, 170 + c, 30, 255))
    for y in (0, 1, 14, 15):
        d.line([0, y, 15, y], fill=(110, 110, 105, 255))
    d.line([0, 1, 15, 1], fill=(150, 150, 145, 255))
    for y in (5, 10):
        d.line([0, y, 15, y], fill=(150, 125, 20, 255))
    trefoil(d, 7.5, 7.5, 4.2, (25, 25, 25, 255))
    d.ellipse([6.6, 6.6, 8.4, 8.4], fill=(200, 170, 30, 255)); d.point((7, 7), fill=(25, 25, 25, 255)); d.point((8, 8), fill=(25, 25, 25, 255))
    for x, y in [(1, 13), (2, 12), (3, 13), (13, 12), (14, 13)]:
        d.point((x, y), fill=(120, 230, 60, 255))  # leaking ooze
    return im
barrel_side().save(f"{A}/textures/block/nuclear_waste_barrel_side.png")
im = Image.new("RGBA", (16, 16), (130, 130, 125, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(95, 95, 90, 255)); d.rectangle([1, 1, 14, 14], outline=(160, 160, 155, 255))
d.ellipse([3, 3, 12, 12], fill=(90, 200, 50, 255)); d.ellipse([5, 5, 10, 10], fill=(150, 255, 90, 255))
d.ellipse([11, 2, 13, 4], fill=(70, 70, 65, 255))
im.save(f"{A}/textures/block/nuclear_waste_barrel_top.png")
im = Image.new("RGBA", (16, 16), (110, 110, 105, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(80, 80, 75, 255)); d.ellipse([4, 4, 11, 11], outline=(90, 90, 85, 255))
im.save(f"{A}/textures/block/nuclear_waste_barrel_bottom.png")
# glowing ooze layer (emissive animation not needed; keep static)

# --- mob effect icons (18x18) ---
im = Image.new("RGBA", (18, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.ellipse([1, 1, 16, 16], fill=(230, 190, 30, 255)); trefoil(d, 8.5, 8.5, 6.5, (30, 30, 30, 255))
d.ellipse([7.4, 7.4, 9.6, 9.6], fill=(230, 190, 30, 255)); d.ellipse([7.9, 7.9, 9.1, 9.1], fill=(30, 30, 30, 255))
im.save(f"{A}/textures/mob_effect/radiation_sickness.png")
im = Image.new("RGBA", (18, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.polygon([(9, 1), (16, 4), (15, 11), (9, 17), (3, 11), (2, 4)], fill=(60, 120, 210, 255))
d.polygon([(9, 3), (14, 5), (13, 10), (9, 15), (5, 10), (4, 5)], fill=(100, 170, 240, 255))
trefoil(d, 9, 8.5, 3.6, (20, 40, 80, 255))
im.save(f"{A}/textures/mob_effect/rad_resistance.png")
im = Image.new("RGBA", (18, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rectangle([5, 2, 12, 13], fill=(240, 240, 235, 255)); d.rectangle([6, 6, 11, 12], fill=(214, 120, 40, 255))
d.rectangle([8, 0, 9, 2], fill=(200, 200, 200, 255)); d.rectangle([8, 13, 9, 17], fill=(200, 200, 200, 255))
im.save(f"{A}/textures/mob_effect/radaway.png")

# --- mod icon ---
im = Image.new("RGBA", (128, 128), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rounded_rectangle([2, 2, 125, 125], radius=18, fill=(232, 190, 30, 255), outline=(30, 30, 30, 255), width=5)
trefoil(d, 63.5, 63.5, 48, (25, 25, 25, 255))
d.ellipse([52, 52, 75, 75], fill=(232, 190, 30, 255)); d.ellipse([56.5, 56.5, 70.5, 70.5], fill=(25, 25, 25, 255))
os.makedirs(f"{A}", exist_ok=True); im.save(f"{A}/icon.png")

# --- Geiger counter clicks ---
rate = 44100
rng = np.random.default_rng(42)
for i in range(1, 5):
    n = int(rate * 0.018)
    t = np.arange(n) / rate
    env = np.exp(-t * (900 + 150 * i))
    noise = rng.standard_normal(n)
    ring = np.sin(2 * math.pi * (2600 + 400 * i) * t)
    sig = (0.65 * noise + 0.5 * ring) * env
    sig[:3] *= np.array([0.3, 0.7, 1.0])
    sig = 0.9 * sig / np.abs(sig).max()
    sf.write(f"{A}/sounds/geiger_click{i}.ogg", sig.astype(np.float32), rate, format="OGG", subtype="VORBIS")
# warning beep for entering a new sickness stage
t = np.arange(int(rate * 0.5)) / rate
beep = np.sin(2 * math.pi * 880 * t) * ((t % 0.25) < 0.15) * 0.5
beep *= np.minimum(1, (0.5 - t) * 40)
sf.write(f"{A}/sounds/rad_warning.ogg", beep.astype(np.float32), rate, format="OGG", subtype="VORBIS")
print("ok")
