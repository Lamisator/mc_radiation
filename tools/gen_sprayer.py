"""Draws the decontamination sprayer's 16x16 item texture (Pillow). Run from the repo root."""
from PIL import Image
im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = im.load()
Y = (232, 190, 30, 255); Yd = (176, 138, 18, 255); Yl = (250, 222, 90, 255); K = (40, 40, 40, 255)
G = (150, 150, 155, 255); Gd = (95, 95, 100, 255); B = (70, 130, 220, 255)
for y in range(7, 15):  # pressure tank
    for x in range(1, 8):
        if (x, y) not in [(1, 7), (7, 7), (1, 14), (7, 14)]:
            px[x, y] = Y
for y in range(8, 14): px[2, y] = Yl
for y in range(8, 14): px[7, y] = Yd
for x in range(2, 7): px[x, 14] = Yd
for x in range(1, 8): px[x, 10] = K  # hazard stripe
px[4, 12] = B
for x in range(3, 6): px[x, 5] = K  # pump handle
px[4, 6] = K
for x, y in [(6, 6), (7, 5), (8, 5), (9, 6), (9, 7), (10, 7)]: px[x, y] = K  # hose
for i in range(6):  # lance
    x, y = 9 + i, 9 - i
    px[x, y] = G
    if x + 1 < 16: px[x + 1, y] = Gd
px[9, 10] = K; px[10, 10] = K; px[9, 11] = K  # trigger
px[15, 3] = B; px[14, 2] = B; px[15, 1] = (140, 180, 240, 255)  # spray
im.save('src/main/resources/assets/radiation/textures/item/decontamination_sprayer.png')
