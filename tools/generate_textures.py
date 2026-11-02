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


def sheet(seed, base):
    """A flat plate of metal in the hand, with its edges shaded."""
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(4, 12):
        for x in range(2, 14):
            edge = y in (4, 11) or x in (2, 13)
            pixels[y][x] = shade(base, (-30 if edge else 14) + rng.randint(-6, 6))
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

for material, color in {'wood': (128, 94, 58), 'ceramic': (176, 120, 88), 'copper': (190, 112, 74), 'lead': (116, 120, 130), 'wrought_iron': (110, 110, 114), 'steel': (150, 154, 160)}.items():
    pipe_side = noise(50 + len(material), color, 8)
    for i in range(16):
        pipe_side[i][0] = pipe_side[i][15] = shade(color, -30)
    write_png(ROOT / f'block/pipe/{material}.png', pipe_side)
    item_pipe = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(5, 11):
        for x in range(1, 15):
            item_pipe[y][x] = shade(color, 18 if y == 5 else (-26 if y == 10 else 0))
    write_png(ROOT / f'item/pipe/{material}.png', item_pipe)

iron = (104, 104, 108)
fitting_side = noise(60, iron, 6)
write_png(ROOT / 'block/pipe_fitting.png', fitting_side)


def fitting_face(seed, mark, draw):
    face = noise(seed, iron, 6)
    for y in range(16):
        for x in range(16):
            if draw(x - 7.5, y - 7.5):
                face[y][x] = shade(mark, (x + y) % 3 * 4)
    return face


write_png(ROOT / 'block/pipe_valve.png', fitting_face(61, (176, 60, 44), lambda x, y: 4 < (x * x + y * y) ** 0.5 < 6.5 or abs(x) < 1 or abs(y) < 1))  # A red hand wheel
write_png(ROOT / 'block/pipe_vent.png', fitting_face(62, (36, 32, 30), lambda x, y: (x * x + y * y) ** 0.5 < 6))  # An open throat
write_png(ROOT / 'block/pipe_tap.png', fitting_face(63, (190, 112, 74), lambda x, y: (x * x + y * y) ** 0.5 < 3 or (abs(x) < 1 and y > 0 and y < 7)))  # A copper spout
write_png(ROOT / 'block/safety_valve.png', fitting_face(64, (60, 58, 54), lambda x, y: abs(y + 3) < 1 or (x * x + (y - 2) ** 2) ** 0.5 < 3))  # Lever and weight
write_png(ROOT / 'block/piston_pump.png', fitting_face(65, (190, 112, 74), lambda x, y: 3 < (x * x + y * y) ** 0.5 < 6))  # The cylinder head

manometer = noise(66, (150, 124, 90), 6)
for y in range(1, 15):
    for x in (5, 10):
        manometer[y][x] = shade((206, 226, 232), 0)  # The glass U-tube
for x in range(5, 11):
    manometer[14][x] = shade((206, 226, 232), 0)
for y in range(9, 15):
    for x in (5, 10):
        manometer[y][x] = shade((176, 180, 188), 0)  # Mercury
write_png(ROOT / 'block/mercury_manometer.png', manometer)

vessel = noise(67, (92, 92, 96), 6)
for i in range(16):
    for j in (2, 13):
        vessel[j][i] = shade((64, 64, 68), 0)
        if i % 3 == 0:
            vessel[j][i] = shade((140, 140, 146), 0)  # Rivets
write_png(ROOT / 'block/pressure_vessel.png', vessel)

write_png(ROOT / 'item/powder/borax.png', heap(75, (226, 226, 214)))
write_png(ROOT / 'item/powder/lead_oxide.png', heap(76, (214, 186, 92)))
write_png(ROOT / 'item/powder/apatite.png', heap(77, (138, 186, 170)))
write_png(ROOT / 'item/metal/ingot/lead.png', ingot(78, (116, 120, 130)))
write_png(ROOT / 'item/metal/ingot/work_lead.png', ingot(148, (98, 104, 116)))  # A little darker: the silver in it
write_png(ROOT / 'item/metal/sheet/lead.png', sheet(149, (116, 120, 130)))
write_png(ROOT / 'item/powder/bone_ash.png', heap(150, (222, 218, 206)))  # Off-white, as the bone it came from


def overlay(seed, base, density):
    """Crystals of a mineral through rock, with the rock showing between them."""
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for _ in range(int(density * 8)):
        x, y = rng.randint(1, 14), rng.randint(1, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            if rng.random() < 0.8:
                pixels[(y + dy) % 16][(x + dx) % 16] = shade(base, rng.randint(-22, 26))
    return pixels


def piece(seed, base, size):
    """A lump of ore in the hand."""
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 8.5) ** 2 <= size * size * (0.75 + 0.25 * rng.random()):
                pixels[y][x] = shade(base, rng.randint(-24, 24) + (10 if y < 8 else -8))
    return pixels


ORE_COLORS = {'galena': (120, 126, 136), 'bauxite': (162, 92, 58), 'chromite': (46, 42, 44), 'ilmenite': (66, 62, 70), 'pyrolusite': (34, 32, 38)}
for index, (name, color) in enumerate(ORE_COLORS.items()):
    for grade, density, size in (('poor', 2, 3.2), ('normal', 3.5, 4.2), ('rich', 5, 5.2)):
        write_png(ROOT / f'block/ore/{grade}_{name}.png', overlay(90 + index, color, density))
        write_png(ROOT / f'item/ore/{grade}_{name}.png', piece(100 + index, color, size))
    write_png(ROOT / f'item/ore/small_{name}.png', piece(110 + index, color, 2.4))
for index, (name, color) in enumerate({'apatite': (102, 172, 152), 'barite': (224, 208, 198)}.items()):
    write_png(ROOT / f'block/ore/{name}.png', overlay(120 + index, color, 3.5))
    write_png(ROOT / f'item/ore/{name}.png', piece(125 + index, color, 4.2))
write_png(ROOT / 'item/slag/iron.png', heap(71, (72, 58, 54)))
write_png(ROOT / 'item/slag/furnace.png', heap(72, (168, 168, 150)))

converter_side = noise(73, (84, 82, 86), 6)
for x in range(16):
    for y in (3, 12):
        converter_side[y][x] = shade((58, 56, 60), 0)  # The bands of the shell
        if x % 3 == 0:
            converter_side[y][x] = shade((132, 130, 136), 0)  # Rivets
write_png(ROOT / 'block/converter_side.png', converter_side)

converter_top = bricks(74, (170, 118, 84))
for y in range(16):
    for x in range(16):
        distance = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if distance < 5:
            converter_top[y][x] = shade((230, 120, 40), int(distance * 6) - 12)  # The mouth, with the glow of the iron in it
write_png(ROOT / 'block/converter_top.png', converter_top)

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


# Coal chemistry: coke is grey and porous, lignite char is dull brown-black and crumbling
write_png(ROOT / 'item/coke.png', piece(130, (122, 120, 126), 5.0))
write_png(ROOT / 'item/lignite_char.png', piece(131, (72, 60, 52), 4.6))

oven_side = bricks(132, (160, 110, 80))
for y in range(8, 13):
    for x in range(4, 12):
        oven_side[y][x] = shade((46, 40, 38), (x + y) % 3 - 1)  # The charging door
for x in range(3, 13):
    oven_side[7][x] = oven_side[13][x] = shade((92, 88, 90), 0)  # Its iron frame
write_png(ROOT / 'block/coke_oven_side.png', oven_side)

oven_top = bricks(133, (146, 100, 74))
for y in range(6, 10):
    for x in range(6, 10):
        oven_top[y][x] = shade((96, 94, 98), (x + y) % 2 * 6)  # The standpipe the gas leaves by
write_png(ROOT / 'block/coke_oven_top.png', oven_top)

burner_side = noise(134, (96, 96, 100), 6)
for y in range(16):
    burner_side[y][0] = burner_side[y][15] = shade((70, 70, 74), 0)
for y in (5, 6):
    for x in range(2, 14):
        burner_side[y][x] = shade((176, 60, 44), (x % 3) * 4)  # The hand wheel
write_png(ROOT / 'block/gas_burner_side.png', burner_side)
write_png(ROOT / 'block/gas_burner_bottom.png', noise(135, (80, 80, 84), 6))

for name, ring, core in (('gas_burner_top', (70, 66, 64), (36, 34, 34)), ('gas_burner_top_lit', (70, 66, 64), (96, 150, 240))):
    burner_top = noise(136, (96, 96, 100), 5)
    for y in range(16):
        for x in range(16):
            distance = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if distance < 6:
                burner_top[y][x] = shade(ring, 0)
            if distance < 4:
                burner_top[y][x] = shade(core, int(distance * 8) if name.endswith('lit') else 0)  # The nozzle, with its flame when lit
    write_png(ROOT / f'block/{name}.png', burner_top)


# Phosphorus and the basic converter: ingots of phosphoric pig iron (grey-brown, coarse) and cold short iron (pale, bright), slags, and dolomite
write_png(ROOT / 'item/metal/ingot/phosphoric_pig_iron.png', ingot(140, (128, 112, 104)))
write_png(ROOT / 'item/metal/ingot/cold_short_iron.png', ingot(141, (156, 152, 158)))
write_png(ROOT / 'item/slag/phosphoric_iron.png', heap(142, (98, 66, 44)))
write_png(ROOT / 'item/slag/thomas.png', heap(143, (84, 78, 92)))
write_png(ROOT / 'item/powder/thomas_meal.png', heap(144, (74, 70, 78)))
write_png(ROOT / 'item/powder/dolomite.png', heap(145, (218, 214, 204)))
write_png(ROOT / 'item/powder/burnt_dolomite.png', heap(146, (186, 164, 138)))


def brick(seed, base):
    """A single brick in the hand, with a lit top and a dark underside."""
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(5, 12):
        for x in range(2, 14):
            highlight = 24 if y == 5 else (-36 if y == 11 or x == 13 else 0)
            pixels[y][x] = shade(base, highlight + rng.randint(-8, 8))
    return pixels


write_png(ROOT / 'item/basic_brick.png', brick(147, (74, 62, 56)))  # Dolomite with tar: nearly black

# The cupellation hearth: fire brick, as the converter and retort are, with a lighter tone that a bone-ash floor would make
write_png(ROOT / 'block/cupel_hearth.png', bricks(150, (196, 132, 96)))


# The condensation tower: fire brick with a frame of lead sheet at the edges, where the walls are joined
tower = bricks(151, (176, 120, 88))
for y in range(16):
    for x in range(16):
        if min(x, y, 15 - x, 15 - y) < 2:
            inner = min(x, y, 15 - x, 15 - y) == 1
            tower[y][x] = shade((116, 120, 130), (-26 if inner else 6) + (x * 7 + y * 3) % 7)
write_png(ROOT / 'block/condensation_tower.png', tower)


# The lead chamber: lead sheet joined at the seams, which are the dark lines between plates
chamber = noise(161, (118, 122, 132), 6)
for y in range(16):
    for x in range(16):
        if x % 8 == 7 or y % 8 == 7:
            chamber[y][x] = shade((118, 122, 132), -40)
write_png(ROOT / 'block/lead_chamber.png', chamber)

# The sulfur burner: fire brick with a yellow crust of sulfur on it
write_png(ROOT / 'block/sulfur_burner.png', bricks(162, (196, 170, 84)))


# Stage F: borosilicate glass, the carboy, the glass retort, the sand bath, and the salts that distillation leaves
write_png(ROOT / 'item/borosilicate_glass_batch.png', heap(170, (204, 224, 232)))  # Pale: sand, lime and borax
write_png(ROOT / 'item/powder/potassium_bisulfate.png', heap(171, (232, 230, 220)))
write_png(ROOT / 'item/powder/sodium_bisulfate.png', heap(172, (242, 238, 226)))
write_png(ROOT / 'item/powder/superphosphate.png', heap(173, (170, 160, 142)))  # Grey-brown, as the rock it is made of
write_png(ROOT / 'item/powder/ammonium_sulfate.png', heap(174, (224, 230, 238)))


def carboy(seed):
    """A big round bottle of glass with a narrow neck, in a wicker basket."""
    rng = random.Random(seed)
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(1, 5):
        for x in range(6, 10):
            pixels[y][x] = shade((190, 222, 214), rng.randint(-8, 8))[:3] + (200,)  # The neck
    for y in range(5, 15):
        for x in range(16):
            if ((x - 7.5) / 6.0) ** 2 + ((y - 9.5) / 5.2) ** 2 <= 1.0:
                glass = shade((176, 216, 206), rng.randint(-10, 10))[:3] + (200,)
                pixels[y][x] = glass
                if y >= 10:
                    pixels[y][x] = shade((158, 112, 70), ((x + y) % 2) * 14 - 8 + rng.randint(-5, 5))  # The wicker basket
    pixels[6][5] = pixels[7][4] = shade((236, 250, 244), 0)[:3] + (230,)  # A glint
    return pixels


write_png(ROOT / 'item/glass_carboy.png', carboy(175))

glass_side = noise(176, (170, 212, 196), 7)
for i in range(16):
    glass_side[0][i] = glass_side[15][i] = glass_side[i][0] = glass_side[i][15] = shade((110, 158, 142), 0)  # The frame of the case
for y in range(2, 14):
    for x in range(2, 14):
        if (x + y) % 7 == 0:
            glass_side[y][x] = shade((226, 246, 238), 0)  # Streaks of light on the glass
for y in range(9, 13):
    for x in range(5, 11):
        glass_side[y][x] = shade((150, 190, 176), (x + y) % 3)  # The belly of the flask
write_png(ROOT / 'block/glass_retort_side.png', glass_side)

glass_top = noise(177, (160, 204, 188), 6)
for y in range(16):
    for x in range(16):
        distance = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if distance < 5.5:
            glass_top[y][x] = shade((200, 232, 222), int(distance * 3))  # The neck of the flask
        if distance < 3:
            glass_top[y][x] = shade((84, 110, 104), 0)  # And its mouth
write_png(ROOT / 'block/glass_retort_top.png', glass_top)

bath_side = noise(178, (96, 94, 98), 6)
for y in range(16):
    for x in range(16):
        if y < 3:
            bath_side[y][x] = shade((196, 176, 120), (x * 3 + y) % 7 - 3)  # Sand heaped over the rim
        elif y in (3, 15):
            bath_side[y][x] = shade((62, 60, 64), 0)  # The edge of the iron tray
write_png(ROOT / 'block/sand_bath_side.png', bath_side)
write_png(ROOT / 'block/sand_bath_top.png', noise(179, (206, 186, 128), 12))
