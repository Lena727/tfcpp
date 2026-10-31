"""Generates tfcpp's original textures, using nothing but the standard library. Run from the project root."""
import random
import struct
import zlib
from pathlib import Path

ROOT = Path('src/main/resources/assets/tfcpp/textures')


def write_png(path, pixels):
    size = len(pixels)
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                     + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw))
                     + chunk(b'IEND', b''))


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color) + (255,)


def noise(seed, base, spread):
    rng = random.Random(seed)
    return [[shade(base, rng.randint(-spread, spread)) for _ in range(16)] for _ in range(16)]


def bricks(seed, base):
    pixels = noise(seed, base, 10)
    for y in range(16):
        for x in range(16):
            offset = 4 if (y // 4) % 2 else 0
            if y % 4 == 3 or (x + offset) % 8 == 7:
                pixels[y][x] = shade(base, -45)
    return pixels


def ingot(seed, base):
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(5, 12):
        inset = (11 - y) // 2
        for x in range(2 + inset, 14 - inset):
            highlight = 26 if y == 5 else (-34 if y == 11 or x == 13 - inset else 0)
            pixels[y][x] = shade(base, highlight + rng.randint(-6, 6))
    return pixels


def heap(seed, base):
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(4, 14):
        half = int((y - 3) * 0.75) + 1
        for x in range(8 - half, 8 + half):
            if rng.random() > 0.08:
                pixels[y][x] = shade(base, rng.randint(-26, 22))
    return pixels


def bulb(seed, base, alpha):
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 10) ** 2 < 16 or (6 <= x <= 9 and 2 <= y <= 7):
                pixels[y][x] = shade(base, rng.randint(-10, 10))[:3] + (alpha,)
    return pixels


write_png(ROOT / 'item/metal/ingot/arsenical_copper.png', ingot(1, (168, 120, 82)))
for index, (grade, color) in enumerate({'small': (214, 212, 198), 'poor': (196, 190, 170), 'normal': (206, 204, 190), 'rich': (226, 226, 216)}.items()):
    write_png(ROOT / f'item/calcine/{grade}_zinc.png', heap(10 + index, color))
write_png(ROOT / 'item/glass_bulb.png', bulb(20, (196, 222, 226), 150))
write_png(ROOT / 'item/mercury_bulb.png', bulb(21, (176, 180, 188), 255))

for name, color in {'arsenic_trioxide': (236, 236, 228), 'antimony_trioxide': (222, 220, 208), 'zinc_oxide': (240, 240, 236)}.items():
    write_png(ROOT / f'item/powder/{name}.png', heap(30 + len(name), color))


def grid(seed, base, step):
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(2, 14):
        for x in range(2, 14):
            if x == 2 or x == 13 or y == 2 or y == 13 or x % step == 0 or y % step == 0:
                pixels[y][x] = shade(base, rng.randint(-14, 14))
    return pixels


write_png(ROOT / 'item/wire_gauze.png', grid(40, (190, 112, 74), 2))

cage = grid(41, (176, 104, 70), 3)
write_png(ROOT / 'item/animal_cage.png', cage)

sentinel = [row[:] for row in cage]
for y in range(8, 12):
    for x in range(6, 10):
        sentinel[y][x] = shade((226, 196, 74), (x + y) % 3 * 6)  # A small, bright bird
sentinel[9][10] = shade((214, 120, 50), 0)
write_png(ROOT / 'item/sentinel_cage.png', sentinel)

lamp = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
for y in range(3, 11):
    for x in range(5, 11):
        lamp[y][x] = shade((196, 120, 80), 18) if x % 2 == 0 or y % 2 == 0 else (0, 0, 0, 0)  # The gauze
lamp[7][7] = lamp[7][8] = lamp[8][7] = lamp[8][8] = shade((250, 200, 90), 0)  # The flame
for y in range(11, 15):
    for x in range(4, 12):
        lamp[y][x] = shade((166, 98, 62), (x + y) % 3 * 5 - 5)  # The oil vessel
for x in range(6, 10):
    lamp[2][x] = shade((166, 98, 62), 0)
write_png(ROOT / 'item/davy_lamp.png', lamp)

baffle = bricks(6, (150, 112, 88))
for y in range(16):
    for x in range(16):
        if y % 4 == 1:
            baffle[y][x] = shade((182, 104, 70), x % 3 * 4)  # Copper plates
write_png(ROOT / 'block/settling_baffle.png', baffle)

fan_side = noise(7, (120, 104, 92), 8)
for i in range(16):
    fan_side[0][i] = fan_side[15][i] = fan_side[i][0] = fan_side[i][15] = shade((86, 74, 66), 0)
write_png(ROOT / 'block/mine_fan_side.png', fan_side)

fan_front = [row[:] for row in fan_side]
for y in range(16):
    for x in range(16):
        r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if r < 7:
            fan_front[y][x] = shade((48, 42, 38), 0)  # The dark throat
        if r < 6.5 and (abs(x - y) < 1.5 or abs(x + y - 15) < 1.5):
            fan_front[y][x] = shade((182, 104, 70), int(r) * 3)  # Copper blades
write_png(ROOT / 'block/mine_fan_front.png', fan_front)

side = bricks(3, (170, 118, 84))
for y in range(9, 12):
    for x in range(5, 11):
        side[y][x] = shade((52, 44, 40), (x + y) % 3 - 1)  # The sealed charging door
write_png(ROOT / 'block/retort_side.png', side)

top = bricks(4, (150, 104, 76))
for y in range(5, 11):
    for x in range(5, 11):
        top[y][x] = shade((70, 62, 56), (x * y) % 5 - 2)  # The vent
write_png(ROOT / 'block/retort_top.png', top)

condenser = bricks(5, (150, 112, 88))
for y in range(16):
    for x in (3, 4, 11, 12):
        condenser[y][x] = shade((182, 104, 70), (y % 4) * 4)  # Copper cooling plates
write_png(ROOT / 'block/retort_condenser.png', condenser)
