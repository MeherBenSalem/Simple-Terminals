#!/usr/bin/env python3
"""Builds the listing scenes with server commands and captures them from the QA client (see marketing/README.md).

Run against the 1.21.1 NeoForge dev server (``tools/qa/devrun.sh setup-server``, then ``level-name=showcase`` and
``view-distance=10`` in ``runs/server/server.properties``) with the QA client online, op'd and in creative:

  python3 tools/qa/marketing_shots.py stock              # once per world: builds and fills the hero base
  python3 tools/qa/marketing_shots.py [scene...]         # captures; without scenes, all of them

The world is superflat; every scene is built on its own plot (see PLOTS), so scenes can be retaken in any order.
``stock`` fills a few cells to amber/red for real (one at a time, through an Import Bus), then builds the base and lets
its Import Buses move the stock out of drawers, chests and cauldrons into the cells; ``base``, ``terminal`` and
``crafting`` shoot that base. Every other scene builds and stocks its own plot the same way (no item counts are
written by hand). ``vaults`` starts a second, small client ("Helper") to hold the Omnium Vault open; it is stopped at
the end. The client uses GUI scale 3 and a 60 degree field of view (``fov:-0.25`` in ``runs/client/options.txt``).
"""
from __future__ import annotations

import argparse
import math
import os
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import Session, PLAYER  # noqa: E402

HELPER = 'Helper'
WORKSPACE = Path(__file__).resolve().parents[2] / '1.21.1' / 'neoforge'

NS = 'omnivault'
OUT = Path(__file__).resolve().parents[2] / 'marketing' / 'images' / 'screenshots'
# The pre-filled cells' IDs, which only mean something in the world that holds their contents.
FILLED = WORKSPACE / 'runs' / 'server' / 'marketing_cells.txt'
GROUND = -60  # first air block above the superflat grass

# Plot origins (x, z). Indoor scenes face north (towards -z); the plots are far enough apart not to show in each other.
PLOTS = {
    'fill': (0, -400),
    'base': (0, 0),
    'early': (-120, 0),
    'vaults': (120, 0),
    'drawers': (240, 0),
    'backpacks': (-240, 0),
    'late': (0, 160),
    'auto': (120, 160),
}

# Item types the base network stores. Big stacks come from drawers (counts as shown on the monitors), the rest from chests.
BULK = [('iron_ingot', 24576), ('gold_ingot', 9472), ('diamond', 3136), ('redstone', 18240), ('emerald', 1408),
        ('copper_ingot', 30208), ('lapis_lazuli', 7680), ('coal', 21504), ('quartz', 11264), ('cobblestone', 31744),
        ('oak_log', 6144), ('glass', 4608), ('stone', 15360), ('deepslate', 12800), ('oak_planks', 8960),
        ('sand', 5120), ('netherrack', 9984), ('obsidian', 448)]
VARIETY = [
    'granite', 'diorite', 'andesite', 'tuff', 'calcite', 'dripstone_block', 'dirt', 'gravel', 'clay_ball', 'flint',
    'spruce_log', 'birch_log', 'jungle_log', 'acacia_log', 'dark_oak_log', 'mangrove_log', 'cherry_log', 'bamboo_block',
    'spruce_planks', 'birch_planks', 'cherry_planks', 'stick', 'torch', 'lantern', 'chain', 'iron_nugget',
    'gold_nugget', 'raw_iron', 'raw_gold', 'raw_copper', 'amethyst_shard', 'prismarine_shard', 'glowstone_dust',
    'string', 'leather', 'feather', 'bone', 'gunpowder', 'slime_ball', 'ender_pearl', 'blaze_rod', 'ghast_tear',
    'magma_cream', 'nether_wart', 'blaze_powder', 'sugar_cane', 'paper', 'book', 'wheat', 'wheat_seeds', 'carrot',
    'potato', 'beetroot', 'apple', 'melon_slice', 'pumpkin', 'egg', 'bread', 'cooked_beef', 'cooked_porkchop',
    'baked_potato', 'golden_carrot', 'honey_bottle', 'honeycomb', 'kelp', 'cactus', 'bamboo', 'moss_block',
    'white_wool', 'red_wool', 'blue_wool', 'black_wool', 'white_concrete', 'cyan_concrete', 'terracotta', 'bricks',
    'stone_bricks', 'deepslate_bricks', 'polished_andesite', 'smooth_stone', 'sandstone', 'red_sand', 'soul_sand',
    'basalt', 'blackstone', 'glass_pane', 'tinted_glass', 'sea_lantern', 'redstone_torch', 'repeater', 'comparator',
    'hopper', 'piston', 'sticky_piston', 'observer', 'dispenser', 'rail', 'powered_rail', 'chest', 'barrel',
    'furnace', 'crafting_table', 'bucket', 'water_bucket', 'shears', 'iron_pickaxe', 'diamond_pickaxe', 'arrow',
    'bow', 'shield', 'name_tag', 'saddle', 'lead', 'experience_bottle', 'emerald_block', 'iron_block', 'gold_block',
]
BULK_IDS = [item for item, _ in BULK]
MONITORS = ['iron_ingot', 'gold_ingot', 'diamond', 'redstone', 'copper_ingot', 'lapis_lazuli', 'coal', 'quartz', 'emerald']


# Vanilla stack sizes for the items above that don't stack to 64 (containers get real stacks, never 64 shields).
SINGLE = {'water_bucket', 'shears', 'iron_pickaxe', 'diamond_pickaxe', 'iron_axe', 'bow', 'shield', 'saddle'}
SIXTEEN = {'bucket', 'egg', 'ender_pearl', 'honey_bottle', 'snowball'}


def stack_size(item, count):
    name = item.split(':')[-1]
    return min(count, 1 if name in SINGLE else 16 if name in SIXTEEN else 64)


def ov(name):
    return f'{NS}:{name}'


def items_nbt(stacks, key='Items'):
    """`stacks` is a list of (slot, id, count[, components snbt])."""
    parts = []
    for entry in stacks:
        slot, item, count = entry[:3]
        comp = f',components:{entry[3]}' if len(entry) > 3 and entry[3] else ''
        item = item if ':' in item else f'minecraft:{item}'
        parts.append(f'{{Slot:{slot}b,id:"{item}",count:{stack_size(item, count)}{comp}}}')
    return f'{key}:[{",".join(parts)}]'


def upgrades_nbt(*names):
    return 'upgrades:{' + items_nbt([(i, ov(n), 1) for i, n in enumerate(names)]) + '}'


class Scenes:
    def __init__(self, s: Session):
        self.s = s
        self.ox, self.oz = 0, 0

    # Helpers ---------------------------------------------------------------------------------------------------------

    def plot(self, name):
        self.ox, self.oz = PLOTS[name]
        # Keep the plot loaded (and ticking) while the player is elsewhere, so networks keep running.
        self.s.cmd(f'forceload add {self.ox - 32} {self.oz - 32} {self.ox + 32} {self.oz + 32}')
        return self

    def at(self, dx, dy, dz):
        return f'{self.ox + dx} {GROUND + dy} {self.oz + dz}'

    def cmd(self, *commands):
        out = ''
        for c in commands:
            out = self.s.cmd(c)
            if any(w in out for w in ('Unknown', 'Incorrect', 'Expected', 'Invalid', 'Could not', 'Malformed', 'not loaded')):
                print('!!', c[:160], '->', out[:200], flush=True)
        return out

    def fill(self, a, b, block, mode=''):
        (x1, y1, z1), (x2, y2, z2) = a, b
        lo, hi = min(x1, x2), max(x1, x2)
        step = max(1, 32000 // ((abs(y2 - y1) + 1) * (abs(z2 - z1) + 1)))
        for x in range(lo, hi + 1, step):
            self.cmd(f'fill {self.at(x, y1, z1)} {self.at(min(hi, x + step - 1), y2, z2)} {block} {mode}'.strip())

    def put(self, d, block):
        if '{' in block:
            # setblock refuses an identical block state and would then skip the NBT: clear the spot first.
            self.s.cmd(f'setblock {self.at(*d)} minecraft:air')
        self.cmd(f'setblock {self.at(*d)} {block}')

    def tp(self, d, facing):
        self.s.tp(self.ox + d[0], GROUND + d[1], self.oz + d[2],
                  facing=(self.ox + facing[0], GROUND + facing[1], self.oz + facing[2]))
        time.sleep(1.0)

    def hold(self, item='minecraft:air', hotbar=(), slot=0):
        self.cmd(f'clear {PLAYER}')
        self.cmd(f'item replace entity {PLAYER} hotbar.0 with {item}')
        for i, extra in enumerate(hotbar, start=1):
            self.cmd(f'item replace entity {PLAYER} hotbar.{i} with {extra}')
        self.s.key(str(slot + 1))

    def hud(self, visible):
        """Shows or hides the HUD, checking the screen for the hotbar (the client may have been left either way)."""
        from PIL import Image
        probe = Path('/tmp') / f'ov-hud-{PLAYER}.png'
        self.s.shot(probe)
        im = Image.open(probe).convert('RGB')
        edge, frame = im.getpixel((960, 1077)), im.getpixel((960, 1075))
        # The hotbar's black outline at the bottom centre, with its grey frame just above.
        shown = sum(edge) < 20 and 80 <= frame[0] <= 110 and max(frame) - min(frame) < 6
        self.s.hud_hidden = not shown
        self.s.hud(visible)

    def shot(self, name, wait=1.5):
        time.sleep(wait)
        OUT.mkdir(parents=True, exist_ok=True)
        png = OUT / name.replace('.jpg', '.png')
        self.s.shot(png)
        from PIL import Image
        Image.open(png).convert('RGB').save(OUT / name, quality=92)
        png.unlink()
        print('captured', name, flush=True)

    def wait_until(self, check, timeout=600, every=3.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if check():
                return True
            time.sleep(every)
        return False

    def clear_plot(self, x1, x2, z1, z2, height=12, floor='minecraft:grass_block'):
        self.fill((x1, 0, z1), (x2, height, z2), 'minecraft:air')
        self.fill((x1, -1, z1), (x2, -1, z2), floor)
        self.cmd('kill @e[type=item]')

    def room(self, x1, x2, z1, z2, height=6, floor='minecraft:polished_deepslate', wall='minecraft:deepslate_bricks',
             ceiling='minecraft:deepslate_tiles', open_south=True):
        """A closed hall: floor at y=-1, walls on x1/x2/z1 (and z2 unless open_south), ceiling at `height`."""
        self.fill((x1, -1, z1), (x2, -1, z2), floor)
        self.fill((x1, 0, z1), (x2, height, z1), wall)
        self.fill((x1, 0, z1), (x1, height, z2), wall)
        self.fill((x2, 0, z1), (x2, height, z2), wall)
        if not open_south:
            self.fill((x1, 0, z2), (x2, height, z2), wall)
        self.fill((x1, height, z1), (x2, height, z2), ceiling)

    def light(self, points, level=15):
        for p in points:
            self.put(p, f'minecraft:light[level={level}]')

    # Network pieces ----------------------------------------------------------------------------------------------------

    @staticmethod
    def cells_nbt(cells):
        """`cells`: up to ten entries, each a cell name or (name, components snbt) or None."""
        stacks = []
        for slot, c in enumerate(cells):
            if c is None:
                continue
            name, comp = (c, None) if isinstance(c, str) else c
            stacks.append((slot, ov(name), 1, comp))
        return items_nbt(stacks)

    def drive(self, d, facing, cells, priority=0):
        self.put(d, f'{ov("drive")}[facing={facing}]{{{self.cells_nbt(cells)},priority:{priority}}}')

    def drawer_source(self, d, facing, item, count):
        item = item if ':' in item else f'minecraft:{item}'
        self.put(d, f'{ov("drawer_1x1")}[facing={facing}]{{Compartments:[{{Item:{{id:"{item}",count:1}},Count:{count}L}}],'
                    f'Upgrades:{{Items:[{{Slot:0b,id:"{ov("netherite_drawer_upgrade")}",count:1}}]}}}}')

    def chest_with(self, d, facing, names, count=64):
        self.put(d, f'minecraft:chest[facing={facing}]{{{items_nbt([(i, n, count) for i, n in enumerate(names[:27])])}}}')

    def import_bus(self, d, facing):
        self.put(d, f'{ov("import_bus")}[facing={facing}]{{{upgrades_nbt("speed_upgrade", "speed_upgrade", "speed_upgrade", "stack_upgrade")}}}')

    def monitor(self, d, facing, item):
        self.put(d, f'{ov("storage_monitor")}[facing={facing}]{{displayed:{{id:"minecraft:{item}",count:1}}}}')

    # Stocking ---------------------------------------------------------------------------------------------------------

    def fill_cells(self):
        """Fills single cells for real through an Import Bus and returns their item components (cell id + usage).

        Each cell sits alone in a drive on a small network while the bus empties a drawer into it, so the amount it
        ends up holding (and so its LED) is exact: 4K cells at 3,300 items show amber, at 4,096 red.
        """
        self.plot('fill')
        self.clear_plot(-4, 4, -4, 4, height=4, floor='minecraft:stone')
        self.put((0, 0, 0), ov('nexus_core'))
        targets = [('storage_cell_4k', 'cobblestone', 4096), ('storage_cell_4k', 'dirt', 3400),
                   ('storage_cell_4k', 'gravel', 3500), ('storage_cell_16k', 'sandstone', 13000),
                   ('storage_cell_4k', 'andesite', 4096), ('storage_cell_16k', 'diorite', 12800),
                   ('storage_cell_4k', 'granite', 3300), ('storage_cell_4k', 'calcite', 3600)]
        filled = []
        for cell, item, count in targets:
            self.drive((1, 0, 0), 'north', [cell])
            self.put((1, 0, 1), ov('nexus_cable'))
            self.import_bus((0, 0, 1), 'south')
            self.import_bus((1, 0, 2), 'west')
            self.drawer_source((0, 0, 2), 'south', item, count)

            def drained():
                out = self.cmd(f'data get block {self.at(0, 0, 2)} Compartments[0].Count')
                return 'Found no' in out or out.strip().endswith(': 0L')

            done = self.wait_until(drained, timeout=300, every=2)
            time.sleep(2)
            comp = self.cmd(f'data get block {self.at(1, 0, 0)} Items[0].components')
            m = re.search(r'(\{.*\})', comp)
            print('filled', cell, item, count, 'done' if done else 'TIMEOUT', (m.group(1) if m else comp)[:140], flush=True)
            if m:
                filled.append((cell, m.group(1)))
            # Take the cell out without dropping it, so it only ever exists once (in the base).
            self.cmd(f'data remove block {self.at(1, 0, 0)} Items')
            self.put((1, 0, 0), 'minecraft:air')
            self.cmd('kill @e[type=item]')
        return filled

    def stock(self):
        filled = self.fill_cells()
        FILLED.write_text('\n'.join(f'{c}\t{comp}' for c, comp in filled))
        self.build_base(filled)
        # Bulk sources behind the drive wall: each Import Bus empties its drawer or chest into the network.
        self.base_sources()

    # The base ----------------------------------------------------------------------------------------------------------

    def load_filled(self):
        if not FILLED.exists():
            return []
        return [tuple(line.split('\t', 1)) for line in FILLED.read_text().splitlines() if '\t' in line]

    BACK_CELLS = ['storage_cell_16m', 'storage_cell_1m', 'storage_cell_1m', 'storage_cell_256k', 'storage_cell_256k',
                  'storage_cell_64k', 'storage_cell_256k', 'storage_cell_64k', 'fluid_cell_256k', 'storage_cell_64k']

    def build_base(self, filled=None):
        """The hero base: a deepslate hall with a U of Cell Drives, monitors, the Nexus Core and both terminals."""
        filled = list(filled if filled is not None else self.load_filled())
        self.plot('base')
        self.clear_plot(-14, 14, -18, 10, height=10)
        self.room(-8, 8, -13, 6, height=6)
        self.fill((-7, -1, -12), (7, -1, 5), 'minecraft:polished_deepslate')
        self.fill((-1, -1, -8), (1, -1, 5), 'minecraft:deepslate_tiles')
        # The alcove: solid wall around the drives, a beam above them, pillars on the hall walls.
        self.fill((-7, 0, -12), (7, 5, -10), 'minecraft:deepslate_bricks')
        self.fill((-7, 0, -9), (-6, 5, -5), 'minecraft:deepslate_bricks')
        self.fill((6, 0, -9), (7, 5, -5), 'minecraft:deepslate_bricks')
        self.fill((-5, 4, -9), (5, 5, -9), 'minecraft:deepslate_bricks')
        self.fill((-5, 4, -8), (5, 4, -8), 'minecraft:polished_blackstone_brick_slab[type=top]')
        for x in (-7, 7):
            for z in (-3, 1, 5):
                self.fill((x, 0, z), (x, 5, z), 'minecraft:polished_deepslate')
        for x in (-4, 0, 4):
            for z in (-6, -1, 3):
                self.put((x, 6, z), 'minecraft:sea_lantern')

        tiers = [
            ['storage_cell_64k', 'storage_cell_64k', 'storage_cell_16k', 'storage_cell_64k', 'storage_cell_256k',
             'storage_cell_16k', 'storage_cell_64k', 'storage_cell_64k', 'fluid_cell_64k', 'fluid_cell_16k'],
            ['storage_cell_256k', 'storage_cell_256k', 'storage_cell_1m', 'storage_cell_64k', 'storage_cell_256k',
             'storage_cell_1m', 'storage_cell_256k', 'storage_cell_16m', 'storage_cell_64k', 'fluid_cell_256k'],
            ['storage_cell_16k', 'storage_cell_4k', 'storage_cell_16k', 'storage_cell_16k', 'storage_cell_64k',
             'storage_cell_4k', 'storage_cell_16k', 'storage_cell_64k', 'storage_cell_4k', 'fluid_cell_4k'],
        ]
        spots = []  # where the pre-filled (amber and red) cells go: (x, y, z, slot)
        for y in range(3):
            for x in range(-4, 5):
                if x == 0 and y == 1:
                    self.drive((x, y, -9), 'south', self.BACK_CELLS, priority=50)  # the bulk drive
                    continue
                cells = [tiers[y][(k + (x + 4) * 3) % 10] for k in range(10)]
                if (x * 5 + y * 3) % 4 == 1 and filled:
                    cell, comp = filled.pop()
                    cells[(x + 2 * y) % 10] = (cell, comp)
                self.drive((x, y, -9), 'south', cells)
        for i, x in enumerate(range(-4, 5)):
            self.monitor((x, 3, -9), 'south', MONITORS[i])
        # Side banks facing the aisle, with a cable column in each corner joining them to the back wall.
        for facing, x in (('east', -5), ('west', 5)):
            for y in range(3):
                for j, z in enumerate(range(-8, -5)):
                    cells = [tiers[(y + 1) % 3][(k + j * 2 + y) % 10] for k in range(10)]
                    if (j + y + x) % 3 == 0 and filled:
                        cell, comp = filled.pop()
                        cells[(j * 3 + y) % 10] = (cell, comp)
                    self.drive((x, y, z), facing, cells)
            self.fill((x, 0, -9), (x, 3, -9), ov('nexus_cable'))
            for j, z in enumerate(range(-8, -5)):
                self.monitor((x, 3, z), facing, ['emerald', 'oak_log', 'glass', 'obsidian', 'stone', 'netherrack'][j + (3 if x > 0 else 0)])
        # The core in front of the drives; floor cables to both banks and the terminals.
        self.put((0, 0, -5), ov('nexus_core'))
        self.put((0, 1, -5), ov('wireless_transmitter'))
        self.fill((-5, 0, -5), (-1, 0, -5), ov('nexus_cable'))
        self.fill((1, 0, -5), (5, 0, -5), ov('nexus_cable'))
        for x, kind, item in ((-3, 'terminal', 'diamond'), (3, 'crafting_terminal', 'iron_ingot')):
            self.put((x, 0, -4), ov('nexus_cable'))
            self.put((x, 0, -3), f'{ov(kind)}[facing=south]')
            self.monitor((x, 1, -3), 'south', item)
        self.light([(-3, 3, -1), (3, 3, -1), (0, 3, -6), (-3, 2, -6), (3, 2, -6), (0, 4, 1)], level=14)
        self.cmd('kill @e[type=item]')

    def base_sources(self):
        """Drawers and chests behind the drive wall, each emptied into the network by an Import Bus."""
        self.plot('base')
        sources = [('drawer', item, count) for item, count in BULK]
        for i in range(0, len(VARIETY), 27):
            sources.append(('chest', VARIETY[i:i + 27], 64))
        spots = [(x, y) for y in (0, 1, 2) for x in range(-4, 5) if not (x == 0 and y == 1)]
        # Buses sit right behind the drives (z=-10) facing north into their sources (z=-11).
        for (kind, what, count), (x, y) in zip(sources, spots):
            self.import_bus((x, y, -10), 'north')
            if kind == 'drawer':
                self.drawer_source((x, y, -11), 'north', what, count)
            else:
                self.chest_with((x, y, -11), 'north', what, count)
        print('sources', len(sources), 'spots', len(spots), flush=True)
        # Fluids for the terminal's fluid view: buses in the last three spots drain cauldrons that are refilled here.
        cauldrons = [('water_cauldron[level=3]', spots[-3]), ('water_cauldron[level=3]', spots[-2]), ('lava_cauldron', spots[-1])]
        for _, (x, y) in cauldrons:
            self.import_bus((x, y, -10), 'north')
        for _ in range(450):
            for block, (x, y) in cauldrons:
                self.s.cmd(f'setblock {self.at(x, y, -11)} minecraft:{block}')
            time.sleep(0.05)

    def base(self):
        """Scene 1: the hero shot of the base."""
        self.plot('base')
        self.cmd('time set 18000')
        self.hold()
        self.hud(False)
        self.tp((0.5, 0.1, 3.2), facing=(0.5, 1.3, -8.0))
        self.shot('base.jpg', wait=4)

    # GUI helpers (GUI scale 3 on a 1920x1080 window: one GUI pixel is three screen pixels) -----------------------------

    GUI = 3

    def gui_xy(self, width, height, x, y):
        """Screen pixel of GUI point (x, y) inside a centred container screen of the given size."""
        left = (1920 // self.GUI - width) // 2
        top = (1080 // self.GUI - height) // 2
        return (left + x) * self.GUI, (top + y) * self.GUI

    def use(self, stand, target, sneak=False):
        """Stands at `stand`, looks at `target` and right-clicks it."""
        self.tp(stand, facing=target)
        if sneak:
            self.s.xdo('keydown', 'shift')
            time.sleep(0.3)
        self.s.click(3)
        if sneak:
            time.sleep(0.2)
            self.s.xdo('keyup', 'shift')
        time.sleep(1.8)

    def game_menu(self):
        """True while the pause menu is showing (its grey "Back to Game" button)."""
        from PIL import Image
        probe = Path('/tmp') / f'ov-menu-{PLAYER}.png'
        self.s.shot(probe)
        r, g, b = Image.open(probe).convert('RGB').getpixel((700, 295))
        return abs(r - g) < 10 and abs(g - b) < 10 and 90 < r < 170

    def close(self):
        """Closes the open screen. A focused search box takes the first Escape, so press it twice; if that opened
        the pause menu, close it again."""
        self.s.key('Escape', delay=0.7)
        self.s.key('Escape', delay=0.7)
        if self.game_menu():
            self.s.key('Escape', delay=0.7)

    # Scene 2: the terminals ---------------------------------------------------------------------------------------------

    TERMINAL_H = 20 + 7 * 18 + 16 + 82

    def terminal(self):
        """The Storage Terminal over the base, sorted by count, with a tooltip; then its fluid view."""
        self.plot('base')
        self.hold()
        self.hud(False)
        self.use((-2.5, 0, -1.2), (-2.5, 0.5, -2.5))
        # Hover the iron ingots (third slot of the first row) for the tooltip with the exact count.
        x, y = self.gui_xy(194, self.TERMINAL_H, 8 + 2 * 18 + 9, 20 + 9)
        self.s.move(x, y)
        self.shot('terminal.jpg', wait=1.2)
        # The fluid view: the fourth button on the left (leftPos - 20, topPos + 84).
        x, y = self.gui_xy(194, self.TERMINAL_H, -20 + 9, 84 + 9)
        self.s.click(1, x, y)
        time.sleep(0.8)
        x, y = self.gui_xy(194, self.TERMINAL_H, 8 + 9, 20 + 9)
        self.s.move(x, y)
        self.shot('terminal-fluids.jpg', wait=1.2)
        x, y = self.gui_xy(194, self.TERMINAL_H, -20 + 9, 84 + 9)
        self.s.click(1, x, y)
        time.sleep(0.5)
        self.close()

    def crafting(self):
        """The Crafting Terminal with a piston recipe in its grid (the grid is saved on the block)."""
        self.plot('base')
        self.hold()
        grid = ['oak_planks', 'oak_planks', 'oak_planks', 'cobblestone', 'iron_ingot', 'cobblestone', 'cobblestone',
                'redstone', 'cobblestone']
        self.cmd(f'data merge block {self.at(3, 0, -3)} {{{items_nbt([(i, n, 1) for i, n in enumerate(grid)])}}}')
        self.hud(False)
        self.use((3.5, 0, -1.2), (3.5, 0.5, -2.5))
        height = 20 + 4 * 18 + 16 + 82 + 3 * 18 + 16  # 4 rows, plus the crafting panel (approximate)
        self.s.move(1700, 900)
        self.shot('crafting.jpg', wait=1.2)
        self.close()

    # A second, small client --------------------------------------------------------------------------------------------

    def windows(self):
        """Minecraft windows as {id: width}; the Builder client is the 1920-wide one, a helper is 854 wide."""
        found = {}
        ids = subprocess.run(['xdotool', 'search', '--name', 'Minecraft'], env=self.s.env, capture_output=True,
                             text=True).stdout.split()
        for w in ids:
            geo = subprocess.run(['xdotool', 'getwindowgeometry', w], env=self.s.env, capture_output=True, text=True).stdout
            m = re.search(r'Geometry: (\d+)x', geo)
            if m:
                found[w] = int(m.group(1))
        return found

    def builder_window(self):
        """Pins xdotool to the Builder client's window (a helper client has the same title) and puts it back on screen."""
        win = next((w for w, width in self.windows().items() if width == 1920), None)
        if win:
            subprocess.run(['xdotool', 'windowmove', win, '0', '0'], env=self.s.env)
            self.s.window = lambda: win
        return win

    def start_helper(self):
        """Starts a second client ("Helper") with the QA client's own command line, in runs/helper at 854x480.

        A vault lid only opens while a player has the vault open, so the helper opens it (invisible, behind the vault)
        while the Builder client takes the picture. Its window is moved off the captured screen afterwards.
        """
        self.builder_window()
        if self.helper_window():
            return
        pid = None
        for proc in Path('/proc').iterdir():
            if proc.name.isdigit():
                try:
                    if (os.readlink(proc / 'cwd') == str(WORKSPACE / 'runs' / 'client')
                            and b'devlaunch' in (proc / 'cmdline').read_bytes()):
                        pid = proc.name
                except OSError:
                    continue
        args = [a.decode() for a in (Path('/proc') / pid / 'cmdline').read_bytes().split(b'\0')[:-1]]
        game = WORKSPACE / 'runs' / 'helper'
        game.mkdir(parents=True, exist_ok=True)
        (game / 'options.txt').write_text('onboardAccessibility:false\nguiScale:2\npauseOnLostFocus:false\nnarrator:0\n'
                                          'soundCategory_master:0.0\nrenderDistance:4\nmaxFps:10\ntutorialStep:none\n'
                                          'joinedFirstServer:true\n')
        for i, a in enumerate(args):
            if a.startswith('@') and a.endswith('ProgramArgs.txt'):
                text = Path(a[1:]).read_text().replace('Builder', HELPER).replace('1920', '854').replace('1080', '480')
                mine = game / 'programArgs.txt'
                mine.write_text(text)
                args[i] = '@' + str(mine)
        env = dict(os.environ, DISPLAY=self.s.env['DISPLAY'], LIBGL_ALWAYS_SOFTWARE='1', GALLIUM_DRIVER='llvmpipe')
        log = open(game / 'helper.log', 'w')
        subprocess.Popen(args, cwd=game, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        self.wait_until(lambda: HELPER in self.cmd('list'), timeout=400, every=5)
        time.sleep(8)
        self.cmd(f'gamemode creative {HELPER}', f'effect give {HELPER} minecraft:invisibility infinite 0 true',
                 f'clear {HELPER}')

    def stop_helper(self):
        if HELPER in self.cmd('list'):
            self.cmd(f'kick {HELPER}')
        for proc in Path('/proc').iterdir():
            if proc.name.isdigit():
                try:
                    if os.readlink(proc / 'cwd') == str(WORKSPACE / 'runs' / 'helper'):
                        os.kill(int(proc.name), 15)
                except OSError:
                    continue

    def helper_window(self):
        return next((w for w, width in self.windows().items() if width != 1920), None)

    def helper_use(self, stand, target):
        """Makes the helper right-click `target` from `stand` (plot coordinates, like tp)."""
        win = self.helper_window()
        x, y, z = self.ox + stand[0], GROUND + stand[1], self.oz + stand[2]
        dx, dy, dz = self.ox + target[0] - x, GROUND + target[1] - (y + 1.62), self.oz + target[2] - z
        yaw = math.degrees(math.atan2(-dx, dz))
        pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        self.cmd(f'clear {HELPER}', f'tp {HELPER} {x} {y} {z} {yaw:.2f} {pitch:.2f}')
        time.sleep(2.0)
        # The Builder client grabs the pointer while in game: open its inventory so clicks can reach the helper.
        self.s.key('e', delay=0.8)
        xdo = lambda *a: subprocess.run(['xdotool', *a], env=self.s.env)
        xdo('windowmove', win, '0', '0')
        xdo('windowraise', win)
        xdo('windowfocus', '--sync', win)
        time.sleep(1.0)
        probe = Path('/tmp') / 'ov-helper.png'
        subprocess.run(['import', '-window', 'root', '-crop', '854x480+0+0', str(probe)], env=self.s.env)
        from PIL import Image
        r, g, b = Image.open(probe).convert('RGB').getpixel((250, 143))
        if abs(r - g) < 8 and abs(g - b) < 8 and r > 90:
            xdo('mousemove', '427', '143')  # paused: "Back to Game"
            time.sleep(0.3)
            xdo('click', '1')
            time.sleep(1.0)
        xdo('mousemove', '427', '240')
        for _ in range(2):  # the first click only grabs the pointer
            xdo('click', '3')
            time.sleep(1.2)
        # Out of the captured area; the helper keeps its screen (and the vault) open.
        xdo('windowmove', win, '1920', '0')
        self.s.focus()
        time.sleep(0.5)
        self.close()

    # Scene 5: vaults ---------------------------------------------------------------------------------------------------

    VAULT_TIERS = ['copper', 'iron', 'gold', 'diamond', 'netherite', 'omnium']

    def build_vaults(self):
        self.plot('vaults')
        self.clear_plot(-12, 12, -10, 8, height=8)
        self.room(-9, 9, -7, 6, height=6, floor='minecraft:spruce_planks', wall='minecraft:stone_bricks',
                  ceiling='minecraft:spruce_planks')
        self.fill((-8, -1, -5), (8, -1, -3), 'minecraft:dark_oak_planks')
        self.fill((-8, -1, -4), (8, -1, -4), 'minecraft:polished_deepslate')
        for x in (-8, -4, 0, 4, 8):
            self.fill((x, 0, -6), (x, 5, -6), 'minecraft:stripped_spruce_log[axis=y]')
        for x in (-6, -2, 2, 6):
            self.put((x, 2, -7), 'minecraft:chiseled_stone_bricks')
        self.fill((-8, 5, -6), (8, 5, -6), 'minecraft:stripped_spruce_log[axis=x]')
        for x in (-7, -3, 1, 5):
            self.put((x, 5, -3), 'minecraft:lantern[hanging=true]')
            self.put((x, 5, 1), 'minecraft:lantern[hanging=true]')
        for i, tier in enumerate(self.VAULT_TIERS):
            self.put((-3 + i, 0, -4), f'{ov(tier + "_vault")}[facing=south]')
        self.light([(-4, 3, -1), (0, 3, -1), (4, 3, -1), (0, 3, 2)], level=12)
        self.light([(x, 1, -2) for x in (-3, 0, 3)], level=13)
        # A pit for a low camera (eye level just above the vault lids).
        self.put((2, -1, 0), 'minecraft:air')
        self.cmd('kill @e[type=item]')

    def fill_vault(self, d, slots, skip=()):
        """Fills a block's container slot by slot. `/item` only reaches slots 0-53, so the NBT list is appended to
        instead, one stack per command (a whole vault does not fit in one RCON request)."""
        pool = BULK_IDS + VARIETY
        self.cmd(f'data modify block {self.at(*d)} Items set value []')
        for slot in range(slots):
            if slot in skip:
                continue
            item = pool[(slot * 7) % len(pool)]
            count = 64 if slot % 6 else 8 + (slot * 13) % 57
            byte = slot if slot < 128 else slot - 256
            self.s.cmd(f'data modify block {self.at(*d)} Items append value {{Slot:{byte}b,id:"minecraft:{item}",count:{stack_size(item, count)}}}')

    def vaults(self):
        self.build_vaults()
        self.fill_vault((2, 0, -4), 162, skip=(23, 61, 62, 100, 131, 140, 155, 156, 157, 158, 159, 160, 161))
        self.start_helper()
        self.helper_use((2.5, 0, -5.4), (2.5, 0.7, -4.0))
        self.hold()
        self.hud(False)
        self.tp((2.4, -1.0, -0.2), facing=(-0.5, 0.42, -4.0))
        self.shot('vaults.jpg', wait=3)
        # The Omnium Vault's 162 slots.
        self.use((2.5, 0, -1.8), (2.5, 0.5, -3.6))
        self.s.move(1890, 1050)
        self.shot('vault-gui.jpg', wait=1.0)
        self.close()

    # Scene 6: drawers -------------------------------------------------------------------------------------------------

    def drawer(self, d, kind, entries, upgrades=(), locked=False, facing='south'):
        """`entries`: (item, count) per compartment (None for an empty one)."""
        comps = []
        for e in entries:
            if e is None:
                comps.append('{}')
            else:
                item, count = e
                comps.append(f'{{Item:{{id:"minecraft:{item}",count:1}},Count:{count}L}}')
        nbt = f'Compartments:[{",".join(comps)}]'
        if upgrades:
            nbt += ',Upgrades:{' + items_nbt([(i, ov(u + '_drawer_upgrade'), 1) for i, u in enumerate(upgrades)]) + '}'
        if locked:
            nbt += ',Locked:1b'
        self.put(d, f'{ov(kind)}[facing={facing}]{{{nbt}}}')

    def compacting(self, d, chain, amount, upgrades=(), facing='south'):
        """`chain`: (item, unit) from the most compressed form down, `amount` in base items."""
        tiers = ','.join(f'{{Item:{{id:"minecraft:{i}",count:1}},Unit:{u}}}' for i, u in chain)
        nbt = f'Tiers:[{tiers}],Amount:{amount}L'
        if upgrades:
            nbt += ',Upgrades:{' + items_nbt([(i, ov(u + '_drawer_upgrade'), 1) for i, u in enumerate(upgrades)]) + '}'
        self.put(d, f'{ov("compacting_drawer")}[facing={facing}]{{{nbt}}}')

    def build_drawers(self):
        self.plot('drawers')
        self.clear_plot(-12, 12, -10, 8, height=8)
        self.room(-8, 8, -7, 6, height=6, floor='minecraft:spruce_planks', wall='minecraft:polished_deepslate',
                  ceiling='minecraft:dark_oak_planks')
        self.fill((-7, 0, -6), (7, 5, -6), 'minecraft:deepslate_tiles')
        for x in (-7, 7):
            self.fill((x, 0, -5), (x, 5, -5), 'minecraft:stripped_dark_oak_log[axis=y]')
        # The wall: trim frame, a controller in the middle, compacting drawers either side of it.
        self.fill((-5, 0, -5), (5, 4, -5), ov('drawer_trim'))
        s = 'drawer_1x1'
        d2 = 'drawer_1x2'
        d4 = 'drawer_2x2'
        rows = {
            3: [(d4, [('wheat', 384), ('carrot', 512), ('potato', 488), ('beetroot', 216)]),
                (d4, [('oak_sapling', 37), ('wheat_seeds', 512), ('pumpkin_seeds', 64), ('melon_seeds', 96)]),
                (d4, [('bone', 412), ('string', 296), ('gunpowder', 180), ('rotten_flesh', 507)]),
                (d4, [('egg', 64), ('feather', 233), ('leather', 145), ('ink_sac', 38)]),
                (d4, [('red_dye', 64), ('yellow_dye', 41), ('blue_dye', 77), ('green_dye', 12)]),
                (d4, [('arrow', 433), ('flint', 190), ('slime_ball', 61), ('ender_pearl', 16)]),
                (d4, [('kelp', 512), ('bamboo', 498), ('sugar_cane', 302), ('cactus', 71)])],
            2: [(d2, [('oak_log', 1024), ('birch_log', 640)]),
                (d2, [('spruce_log', 873), ('dark_oak_log', 315)]),
                (d2, [('oak_planks', 1024), ('stick', 588)]),
                (d2, [('glass', 960), ('glass_pane', 352)]),
                (d2, [('torch', 640), ('lantern', 48)]),
                (d2, [('sand', 1024), ('red_sand', 420)]),
                (d2, [('gravel', 712), ('clay_ball', 256)])],
            1: [(s, [('cobblestone', 8192)], ('gold',)),
                (s, [('cobbled_deepslate', 6144)], ('iron', 'iron')),
                (s, [('stone', 3712)], ('iron',)),
                (s, [('dirt', 2048)]),
                (s, [('andesite', 1873)]),
                (s, [('diorite', 1456)]),
                (s, [('netherrack', 16384)], ('netherite',))],
            0: [(s, [('redstone', 4096)], ('iron', 'void')),
                (s, [('lapis_lazuli', 1555)]),
                ('compacting', ([('iron_block', 81), ('iron_ingot', 9), ('iron_nugget', 1)], 31833)),
                ('controller', None),
                ('compacting', ([('gold_block', 81), ('gold_ingot', 9), ('gold_nugget', 1)], 12051)),
                (s, [('diamond', 0)], (), True),
                (s, [('emerald', 731)])],
        }
        for y, row in rows.items():
            for i, entry in enumerate(row):
                d = (-3 + i, y, -5)
                kind = entry[0]
                if kind == 'controller':
                    self.put(d, f'{ov("drawer_controller")}[facing=south]')
                elif kind == 'compacting':
                    chain, amount = entry[1]
                    self.compacting(d, chain, amount)
                else:
                    ups = entry[2] if len(entry) > 2 else ()
                    locked = entry[3] if len(entry) > 3 else False
                    self.drawer(d, kind, entry[1], ups, locked)
        for x in (-6, -2, 2, 6):
            self.put((x, 5, -3), 'minecraft:lantern[hanging=true]')
        self.light([(-3, 2, -2), (0, 2, -2), (3, 2, -2), (0, 4, -1)], level=14)
        self.cmd('kill @e[type=item]')

    def drawers(self):
        self.build_drawers()
        self.hold()
        self.hud(False)
        self.tp((2.2, 0.3, 2.6), facing=(-0.1, 2.0, -5.0))
        self.shot('drawers.jpg', wait=3)

    # Scene 7: backpacks -----------------------------------------------------------------------------------------------

    BACKPACK_TIERS = ['leather', 'copper', 'iron', 'gold', 'diamond', 'netherite']

    def build_backpacks(self):
        self.plot('backpacks')
        self.clear_plot(-24, 24, -24, 16, height=16)
        # A camp in a clearing: trees around, a campfire, the six tiers set down on the grass.
        for x, z, feature in ((-9, -9, 'fancy_oak'), (8, -11, 'birch'), (-14, -2, 'oak'), (13, -3, 'fancy_oak'),
                              (-4, -16, 'spruce'), (4, -17, 'spruce'), (-18, -12, 'birch'), (17, -14, 'oak'),
                              (-11, 6, 'oak'), (12, 7, 'birch')):
            self.cmd(f'place feature minecraft:{feature} {self.at(x, 0, z)}')
        for x, z in ((-6, -3), (5, -5), (-3, -8), (7, 0), (-8, 2), (2, -10), (9, -7), (-12, -6)):
            self.cmd(f'place feature minecraft:patch_grass {self.at(x, 0, z)}')
        for x, z in ((-5, -6), (4, -8), (8, -2), (-9, -1)):
            self.cmd(f'place feature minecraft:flower_default {self.at(x, 0, z)}')
        self.cmd('kill @e[type=item]')
        self.fill((-2, -1, -5), (2, -1, -1), 'minecraft:coarse_dirt')
        self.put((0, 0, -3), 'minecraft:campfire[lit=true]')
        for (x, z, facing), tier in zip(((-2, -5, 'south'), (-3, -3, 'east'), (-2, -1, 'east'), (2, -1, 'west'),
                                         (3, -3, 'west'), (2, -5, 'south')), self.BACKPACK_TIERS):
            self.put((x, 0, z), f'{ov(tier + "_backpack")}[facing={facing}]')

    def backpacks(self):
        """Six tiers around a campfire; the player wears a Diamond Backpack (third person, from behind)."""
        self.build_backpacks()
        self.cmd('time set 12000', 'weather clear')
        self.hold()
        self.cmd(f'item replace entity {PLAYER} armor.chest with {ov("diamond_backpack")}')
        self.hud(False)
        self.tp((0.5, 0.0, 2.3), facing=(0.5, 0.2, -3.5))
        self.s.key('F5', delay=1.5)
        self.shot('backpacks.jpg', wait=3)
        self.s.key('F5')
        self.s.key('F5', delay=0.8)
        self.cmd(f'item replace entity {PLAYER} armor.chest with minecraft:air', 'time set 18000')

    def shift_click(self, x, y):
        self.s.move(x, y)
        time.sleep(0.08)
        self.s.xdo('keydown', 'shift')
        self.s.xdo('click', '1')
        self.s.xdo('keyup', 'shift')
        time.sleep(0.12)

    def backpack_gui(self):
        """A Diamond Backpack opened with B: five upgrades installed, half full, the Magnet tab open."""
        self.plot('backpacks')
        self.hold()
        self.cmd(f'item replace entity {PLAYER} armor.chest with {ov("diamond_backpack")}')
        upgrades = ['crafting', 'magnet', 'pickup', 'feeding', 'restock']
        for i, u in enumerate(upgrades):
            self.cmd(f'item replace entity {PLAYER} inventory.{i} with {ov(u + "_upgrade")}')
        self.hud(False)
        self.tp((0.5, 0.0, 2.3), facing=(0.5, 0.2, -3.5))
        self.s.key('b', delay=2.0)
        # Diamond: 12 columns, 6 of 9 rows visible; window 244 x 223, inventory at (42, 141), hotbar 58 below.
        w, h, ix, iy = 244, 223, 42, 141
        slot = lambda col, row: self.gui_xy(w, h, ix + col * 18 + 8, iy + row * 18 + 8)
        for i in range(len(upgrades)):
            self.shift_click(*slot(i, 0))
        pool = BULK_IDS + VARIETY
        for batch in range(2):
            for k in range(27):
                item = pool[(batch * 36 + k) * 3 % len(pool)]
                self.s.cmd(f'item replace entity {PLAYER} inventory.{k} with minecraft:{item} {stack_size(item, 64)}')
            for k in range(9):
                item = pool[(batch * 36 + 27 + k) * 3 % len(pool)]
                self.s.cmd(f'item replace entity {PLAYER} hotbar.{k} with minecraft:{item} {stack_size(item, 16 + k * 6)}')
            time.sleep(0.6)
            for row in range(3):
                for col in range(9):
                    self.shift_click(*slot(col, row))
            for col in range(9):
                x, y = self.gui_xy(w, h, ix + col * 18 + 8, iy + 58 + 8)
                self.shift_click(x, y)
        # Open the Magnet tab (the crafting tab is always first): tabs at (imageWidth - 3, 6 + 26 * n).
        x, y = self.gui_xy(w, h, w - 3 + 13, 6 + 26 + 12)
        self.s.click(1, x, y)
        time.sleep(0.8)
        self.s.move(60, 1050)
        self.shot('backpack-gui.jpg', wait=1.0)
        self.close()
        self.cmd(f'clear {PLAYER}')

    # Scene 3: early game ----------------------------------------------------------------------------------------------

    EARLY = [
        ['cobblestone', 'cobblestone', 'cobblestone', 'dirt', 'dirt', 'gravel', 'sand', 'andesite', 'diorite', 'granite',
         'flint', 'clay_ball', 'coal', 'coal', 'raw_iron', 'raw_copper', 'iron_ingot', 'copper_ingot'],
        ['oak_log', 'oak_log', 'birch_log', 'oak_planks', 'oak_planks', 'stick', 'stick', 'torch', 'crafting_table',
         'furnace', 'chest', 'oak_sapling', 'apple', 'ladder', 'oak_fence'],
        ['wheat', 'wheat_seeds', 'carrot', 'potato', 'bread', 'cooked_beef', 'cooked_chicken', 'egg', 'leather',
         'feather', 'string', 'bone', 'rotten_flesh', 'arrow', 'gunpowder', 'spider_eye', 'white_wool', 'sugar_cane'],
        ['redstone', 'lapis_lazuli', 'gold_ingot', 'raw_gold', 'diamond', 'emerald', 'amethyst_shard', 'quartz',
         'glass', 'glass_pane', 'iron_nugget', 'gold_nugget', 'paper', 'book', 'name_tag'],
    ]

    def build_early(self):
        """A wooden storeroom: chests and barrels on Inventory Cable, a Storage Hub and a Storage Terminal."""
        self.plot('early')
        self.clear_plot(-12, 12, -10, 8, height=8)
        self.room(-8, 8, -7, 6, height=6, floor='minecraft:oak_planks', wall='minecraft:stone_bricks',
                  ceiling='minecraft:spruce_planks')
        self.fill((-7, 0, -6), (7, 0, -6), 'minecraft:cobblestone')
        for x in (-8, -4, 4, 8):
            self.fill((x, 0, -6), (x, 5, -6), 'minecraft:spruce_log[axis=y]')
        self.fill((-7, 5, -6), (7, 5, -6), 'minecraft:spruce_log[axis=x]')
        self.fill((-7, -1, -5), (7, -1, -3), 'minecraft:spruce_planks')
        # Bottom row: chests (one double); top row: barrels; the cable runs between them and touches both.
        x0 = -3
        chests = [('chest[facing=south,type=right]', 0), ('chest[facing=south,type=left]', 1)]
        for i in range(7):
            x = x0 + i
            pool = self.EARLY[i % 4]
            stacks = [(k, pool[(k + i) % len(pool)], 64 if (k + i) % 3 else 23 + k) for k in range(min(27, 9 + i * 2))]
            if i < 2:
                block = 'minecraft:' + chests[i][0]
            else:
                block = 'minecraft:chest[facing=south]' if i % 2 else 'minecraft:barrel[facing=south]'
            self.put((x, 0, -5), f'{block}{{{items_nbt(stacks)}}}')
            top = [(k, pool[(k * 2 + i) % len(pool)], 64) for k in range(6 + i)]
            self.put((x, 2, -5), f'minecraft:barrel[facing=south]{{{items_nbt(top)}}}')
        self.fill((x0, 1, -5), (x0 + 6, 1, -5), ov('inventory_cable'))
        self.put((x0 + 7, 1, -5), ov('storage_hub'))
        self.put((x0 + 7, 0, -5), f'{ov("terminal")}[facing=south]')
        self.put((x0 + 7, 2, -5), f'{ov("crafting_terminal")}[facing=south]')
        self.put((x0 - 1, 0, -5), 'minecraft:barrel[facing=up]')
        self.put((x0 - 1, 1, -5), f'{ov("copper_vault")}[facing=south]')
        for x in (-6, -1, 4):
            self.put((x, 5, -3), 'minecraft:lantern[hanging=true]')
        self.put((-6, 0, -5), 'minecraft:crafting_table')
        self.put((-5, 0, -5), 'minecraft:furnace[facing=south,lit=true]')
        self.light([(-2, 2, -2), (2, 2, -2), (5, 2, -2), (0, 3, 1)], level=14)
        self.cmd('kill @e[type=item]')

    def early(self):
        self.build_early()
        self.hold()
        self.hud(False)
        self.tp((2.9, 0.0, 1.8), facing=(0.4, 1.3, -5.0))
        self.shot('early.jpg', wait=3)
        self.use((4.5, 0, -2.6), (4.5, 0.5, -4.5))
        x, y = self.gui_xy(194, self.TERMINAL_H, 8 + 9, 20 + 9)
        self.s.move(x, y)
        self.shot('early-terminal.jpg', wait=1.2)
        self.close()

    # Scene 4: autocrafting --------------------------------------------------------------------------------------------

    @staticmethod
    def stack_snbt(entry):
        if entry is None:
            return '{}'
        item, count = entry if isinstance(entry, tuple) else (entry, 1)
        item = item if ':' in item else f'minecraft:{item}'
        return f'{{id:"{item}",count:{count}}}'

    def pattern_snbt(self, inputs, outputs, processing=False):
        """An encoded pattern item (without Slot): `inputs` nine grid entries (or up to nine stacks), `outputs` stacks."""
        # Crafting patterns keep all nine grid cells; processing patterns list only real inputs.
        cells = [e for e in inputs if e is not None] if processing else (list(inputs) + [None] * 9)[:9]
        ins = ','.join(self.stack_snbt(e) for e in cells)
        outs = ','.join(self.stack_snbt(e) for e in outputs)
        kind = 'processing_pattern' if processing else 'crafting_pattern'
        return (f'id:"{ov(kind)}",count:1,components:{{"{ov("pattern")}":{{processing:{1 if processing else 0}b,'
                f'inputs:[{ins}],outputs:[{outs}]}}}}')

    def provider(self, d, facing, patterns):
        self.put(d, f'{ov("pattern_provider")}[facing={facing}]')
        for slot, pat in enumerate(patterns):
            self.cmd(f'data modify block {self.at(*d)} patterns.Items append value {{Slot:{slot}b,{pat}}}')

    def storage_bus(self, d, facing, priority=0):
        self.put(d, f'{ov("storage_bus")}[facing={facing}]{{priority:{priority},access:0}}')

    OMNIUM_PATTERNS = None

    def omnium_patterns(self):
        dust = self.pattern_snbt(['amethyst_shard', 'quartz', 'redstone', 'redstone'], [(ov('omnium_dust'), 4)])
        blend = self.pattern_snbt(['iron_ingot', ov('omnium_dust'), ov('omnium_dust')], [(ov('omnium_blend'), 1)])
        logic = self.pattern_snbt([None, 'redstone', None, 'gold_ingot', ov('omnium_ingot'), 'gold_ingot', None, 'redstone', None],
                                  [(ov('logic_processor'), 1)])
        advanced = self.pattern_snbt([None, 'diamond', None, ov('logic_processor'), ov('omnium_ingot'), ov('logic_processor'),
                                      None, 'quartz', None], [(ov('advanced_processor'), 1)])
        piston = self.pattern_snbt(['oak_planks', 'oak_planks', 'oak_planks', 'cobblestone', 'iron_ingot', 'cobblestone',
                                    'cobblestone', 'redstone', 'cobblestone'], [('piston', 1)])
        sticky = self.pattern_snbt(['slime_ball', None, None, 'piston'], [('sticky_piston', 1)])
        ingot = self.pattern_snbt([(ov('omnium_blend'), 1)], [(ov('omnium_ingot'), 1)], processing=True)
        return dict(dust=dust, blend=blend, logic=logic, advanced=advanced, piston=piston, sticky=sticky, ingot=ingot)

    def build_auto(self):
        """An autocrafting room: a wall of Assemblers, Pattern Providers, Crafting Processors and a blast furnace."""
        self.plot('auto')
        self.clear_plot(-12, 12, -15, 8, height=8)
        self.room(-9, 9, -13, 6, height=6)
        self.fill((-8, -1, -12), (8, -1, 5), 'minecraft:polished_deepslate')
        self.fill((-1, -1, -9), (1, -1, 5), 'minecraft:deepslate_tiles')
        self.fill((-8, 0, -12), (8, 5, -12), 'minecraft:deepslate_bricks')
        self.fill((-8, 3, -11), (8, 5, -10), 'minecraft:deepslate_bricks')
        self.fill((-8, 2, -9), (8, 2, -9), 'minecraft:polished_blackstone_brick_slab[type=top]')
        for x in (-4, 0, 4):
            for z in (-6, -1, 3):
                self.put((x, 6, z), 'minecraft:sea_lantern')
        pats = self.omnium_patterns()
        # Machine wall (z = -10), left to right.
        self.put((-7, 0, -10), ov('nexus_core'))
        self.put((-7, 1, -10), f'{ov("storage_monitor")}[facing=south]{{displayed:{{id:"{ov("advanced_processor")}",count:1}}}}')
        self.put((-6, 0, -10), f'{ov("pattern_terminal")}[facing=south]')
        self.put((-6, 1, -10), f'{ov("storage_monitor")}[facing=south]{{displayed:{{id:"{ov("logic_processor")}",count:1}}}}')
        self.provider((-5, 0, -10), 'south', [pats['dust'], pats['blend'], pats['logic'], pats['advanced']])
        self.provider((-5, 1, -10), 'south', [pats['piston'], pats['sticky']])
        for x in range(-4, 2):
            for y in (0, 1):
                self.put((x, y, -10), ov('assembler'))
        for x in (2, 3):
            for y in (0, 1):
                self.put((x, y, -10), ov('crafting_processor'))
        # Smelting: a provider above a blast furnace pushes Omnium Blend in; an import bus below takes the ingots out.
        self.fill((4, -1, -10), (4, 1, -10), ov('nexus_cable'))
        self.put((5, 0, -10), 'minecraft:blast_furnace[facing=south]{Items:[{Slot:1b,id:"minecraft:coal_block",count:64}]}')
        self.provider((5, 1, -10), 'down', [pats['ingot']])
        self.put((5, -1, -10), f'{ov("import_bus")}[facing=up]')
        # Cells for whatever the network crafts (the drawers behind the wall only take their own items).
        for y in (0, 1):
            self.drive((6, y, -10), 'south', ['storage_cell_256k', 'storage_cell_64k', 'storage_cell_64k', 'storage_cell_256k',
                                              'storage_cell_16k', 'storage_cell_64k', 'storage_cell_16k', 'storage_cell_4k', None, None])
        self.put((7, 0, -10), f'{ov("storage_monitor")}[facing=south]{{displayed:{{id:"{ov("omnium_ingot")}",count:1}}}}')
        self.put((7, 1, -10), f'{ov("storage_monitor")}[facing=south]{{displayed:{{id:"minecraft:sticky_piston",count:1}}}}')
        # Stock behind the wall: Import Buses empty drawers into the cells.
        stock = [('iron_ingot', 6144), ('gold_ingot', 3072), ('redstone', 8192), ('quartz', 3072), ('amethyst_shard', 1024),
                 ('diamond', 2048), ('oak_planks', 8192), ('cobblestone', 8192), ('slime_ball', 2048)]
        for i, (item, count) in enumerate(stock):
            x, y = -4 + i % 6, i // 6
            self.import_bus((x, y, -11), 'north')
            self.drawer_source((x, y, -12), 'north', item, count)
        # The request terminal in front, on a floor cable.
        self.fill((-1, 0, -9), (-1, 0, -7), ov('nexus_cable'))
        self.put((-1, 0, -6), f'{ov("terminal")}[facing=south]')
        # Patterns written with /data don't announce themselves: re-placing an assembler makes the network re-read them.
        self.put((-4, 0, -10), 'minecraft:air')
        self.put((-4, 0, -10), ov('assembler'))
        self.light([(-5, 3, -6), (0, 3, -6), (5, 3, -6), (0, 4, 0)], level=14)
        self.cmd('kill @e[type=item]')

    def window_top_is_accent(self, height):
        """True if a centred screen `height` GUI pixels tall is open: its cyan accent strip is where it should be."""
        from PIL import Image
        probe = Path('/tmp') / f'ov-top-{PLAYER}.png'
        self.s.shot(probe)
        y = ((1080 // self.GUI - height) // 2 + 2) * self.GUI + 1
        r, g, b = Image.open(probe).convert('RGB').getpixel((960, y))
        return b > 150 and g > 120 and r < 120

    def request(self, search, amount, terminal_stand, terminal_target, shot_name=None):
        """Opens a terminal, finds a craftable item by name, asks for `amount` and starts the job."""
        for attempt in range(3):
            self.use(terminal_stand, terminal_target)
            time.sleep(1.0)
            if not self.window_top_is_accent(self.TERMINAL_H):
                print('terminal did not open, retrying', flush=True)
                time.sleep(2)
                continue
            x, y = self.gui_xy(194, self.TERMINAL_H, 140, 10)
            self.s.click(1, x, y)
            time.sleep(0.3)
            self.s.xdo('key', 'ctrl+a')
            self.s.xdo('key', 'BackSpace')
            self.s.type(search)
            time.sleep(2.0)
            x, y = self.gui_xy(194, self.TERMINAL_H, 8 + 9, 20 + 9)
            self.s.click(2, x, y)  # middle-click: craft
            time.sleep(2.0)
            if self.window_top_is_accent(232):
                break
            print('craft screen did not open, retrying', flush=True)
            self.close()
        self.s.xdo('key', 'ctrl+a')
        self.s.type(str(amount))
        x, y = self.gui_xy(194, 232, 48, 64)
        self.s.click(1, x, y)  # Calculate
        time.sleep(2.5)
        self.s.move(1890, 1050)
        if shot_name:
            self.shot(shot_name, wait=1.0)
        x, y = self.gui_xy(194, 232, 158, 219)
        self.s.click(1, x, y)  # Start
        time.sleep(1.5)
        # Back in the terminal: empty the search box, then close it.
        if self.window_top_is_accent(self.TERMINAL_H):
            x, y = self.gui_xy(194, self.TERMINAL_H, 140, 10)
            self.s.click(1, x, y)
            self.s.xdo('key', 'ctrl+a')
            self.s.xdo('key', 'BackSpace')
            time.sleep(0.3)
        self.close()

    def auto(self):
        self.build_auto()
        # Let the buses move the stock into the cells (8,192 items at 160 a second).
        self.wait_until(lambda: 'Found no' in self.cmd(f'data get block {self.at(-4, 1, -12)} Compartments[0].Count'), timeout=180)
        time.sleep(3)
        self.hold()
        self.hud(False)
        # The Pattern Encoding Terminal with the Logic Processor recipe in its editor and blank patterns.
        grid = [None, 'redstone', None, 'gold_ingot', ov('omnium_ingot'), 'gold_ingot', None, 'redstone', None]
        ins = items_nbt([(i, e, 1) for i, e in enumerate(grid) if e])
        self.cmd(f'data merge block {self.at(-6, 0, -10)} {{inputs:{{{ins}}},outputs:{{Items:[{{Slot:0b,id:"{ov("logic_processor")}",count:1}}]}},'
                 f'patterns:{{Items:[{{Slot:0b,id:"{ov("blank_pattern")}",count:16}}]}},processing:0b}}')
        time.sleep(1)
        self.use((-5.5, 0, -7.6), (-5.5, 0.5, -9.5))
        self.s.move(1890, 1050)
        self.shot('pattern-terminal.jpg', wait=1.2)
        self.close()
        # A five-level plan: Advanced Processors from Logic Processors, Omnium Ingots (smelted), Blend and Dust.
        self.request('advanced', 16, (-0.5, 0, -3.8), (-0.5, 0.5, -5.5), shot_name='request.jpg')
        # A long crafting-only job keeps the assemblers busy for the room shot.
        self.request('sticky', 1536, (-0.5, 0, -3.8), (-0.5, 0.5, -5.5))
        time.sleep(3)
        self.tp((1.2, 0.3, -2.6), facing=(-0.2, 0.9, -10.0))
        self.shot('autocraft.jpg', wait=2.0)
        self.use((2.5, 0, -7.6), (2.5, 0.5, -9.5))
        self.s.move(1890, 1050)
        self.shot('crafting-status.jpg', wait=1.5)
        self.close()

    # Scene 8: late game -----------------------------------------------------------------------------------------------

    def build_late(self):
        """Two network segments across a chasm, joined by an entangled Quantum Bridge pair: Singularity cells on one
        side, Quantum Assemblers on the other."""
        self.plot('late')
        self.clear_plot(-14, 14, -16, 8, height=9)
        self.room(-11, 11, -13, 6, height=7, floor='minecraft:polished_blackstone', wall='minecraft:polished_blackstone_bricks',
                  ceiling='minecraft:polished_blackstone_bricks')
        self.fill((-10, 0, -12), (10, 6, -11), 'minecraft:polished_blackstone_bricks')
        for x in (-10, -4, 4, 10):
            self.fill((x, 0, -10), (x, 6, -10), 'minecraft:obsidian')
        for x in (-7, 7):
            self.fill((x, 6, -12), (x, 6, 5), 'minecraft:crying_obsidian')
        # The chasm between the segments (the superflat world is three blocks deep): blackstone walls, amethyst floor.
        self.fill((-3, -3, -11), (3, -1, -1), 'minecraft:polished_blackstone_bricks')
        self.fill((-2, -2, -10), (2, -1, -2), 'minecraft:air')
        self.fill((-2, -3, -10), (2, -3, -2), 'minecraft:amethyst_block')
        self.fill((-1, -2, -10), (1, -2, -2), 'minecraft:light[level=13]')
        for z in (-9, -6, -3):
            self.put((-2, -2, z), 'minecraft:amethyst_cluster[facing=up]')
            self.put((2, -2, z + 1), 'minecraft:amethyst_cluster[facing=up]')
        self.fill((-2, -1, -1), (2, -1, -1), 'minecraft:polished_blackstone_brick_slab[type=top]')
        # Segment A: the core and a bank of drives full of Singularity and 16M cells.
        bank = ['storage_cell_singularity', 'storage_cell_16m', 'storage_cell_singularity', 'storage_cell_1m',
                'storage_cell_singularity', 'storage_cell_16m', 'fluid_cell_singularity', 'storage_cell_singularity',
                'storage_cell_16m', 'storage_cell_singularity']
        for x in range(-9, -5):
            for y in range(3):
                cells = [bank[(k + x + y * 3) % 10] for k in range(10)]
                self.drive((x, y, -10), 'south', cells, priority=10 if (x, y) == (-6, 1) else 0)
        for x, item in zip(range(-9, -5), ['iron_ingot', 'redstone', 'oak_planks', 'cobblestone']):
            self.monitor((x, 3, -10), 'south', item)
        self.fill((-5, 0, -10), (-5, 0, -7), ov('nexus_cable'))
        self.put((-6, 0, -7), ov('nexus_core'))
        self.put((-6, 1, -7), ov('wireless_transmitter'))
        self.put((-4, 0, -7), ov('nexus_cable'))
        self.put((-3, 0, -7), ov('quantum_bridge'))
        # Stock for segment A, moved into the cells by Import Buses behind the bank.
        stock = [('oak_planks', 9216), ('cobblestone', 12288), ('iron_ingot', 3072), ('redstone', 3072), ('slime_ball', 3072),
                 ('netherite_scrap', 192), ('echo_shard', 96), ('diamond', 1152)]
        for i, (item, count) in enumerate(stock):
            x, y = -9 + i % 4, i // 4
            self.import_bus((x, y, -11), 'north')
            self.drawer_source((x, y, -12), 'north', item, count)
        # Segment B: the other bridge, Quantum Assemblers, a Crafting Processor and the patterns.
        pats = self.omnium_patterns()
        self.put((3, 0, -7), ov('quantum_bridge'))
        self.fill((4, 0, -7), (5, 0, -7), ov('nexus_cable'))
        for x in (6, 7):
            for y in (0, 1):
                self.put((x, y, -7), ov('quantum_assembler'))
        self.put((8, 0, -7), ov('crafting_processor'))
        self.provider((8, 1, -7), 'south', [pats['piston'], pats['sticky']])
        self.put((8, 0, -6), ov('nexus_cable'))
        self.put((8, 0, -5), f'{ov("terminal")}[facing=south]')
        self.put((5, 1, -7), f'{ov("storage_monitor")}[facing=south]{{displayed:{{id:"minecraft:sticky_piston",count:1}}}}')
        self.put((7, 0, -7), 'minecraft:air')  # re-placed so the network re-reads the provider's patterns
        self.put((7, 0, -7), ov('quantum_assembler'))
        self.light([(-6, 3, -4), (6, 3, -4), (0, 4, -6), (-3, 2, -5), (3, 2, -5), (0, 3, 1), (-7, 2, -8), (7, 2, -8)], level=15)
        self.cmd('kill @e[type=item]')

    def link_bridges(self):
        """Entangles the pair with a Quantum Link Card: use it on one bridge, then the other."""
        self.hold(ov('quantum_link_card'))
        self.use((-2.5, 0, -4.8), (-2.5, 0.5, -6.6))
        self.use((3.5, 0, -4.8), (3.5, 0.5, -6.6))
        self.hold()

    def late(self):
        self.build_late()
        self.link_bridges()
        self.wait_until(lambda: 'Found no' in self.cmd(f'data get block {self.at(-8, 0, -12)} Compartments[0].Count'), timeout=240)
        time.sleep(3)
        self.hud(False)
        self.request('sticky', 2048, (8.5, 0, -3.1), (8.5, 0.5, -4.6))
        time.sleep(2)
        self.tp((0.5, 0.0, 1.2), facing=(0.5, 0.9, -8.0))
        self.shot('late.jpg', wait=2.5)
        self.tp((3.6, 0.3, -3.0), facing=(6.4, 0.9, -7.0))
        self.shot('quantum-assembler.jpg', wait=1.5)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=25575)
    ap.add_argument('scenes', nargs='*')
    args = ap.parse_args()
    s = Session(port=args.port)
    scenes = Scenes(s)
    s.focus()
    s.cmd('gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
          'gamerule sendCommandFeedback false', 'weather clear', 'difficulty peaceful', f'gamemode creative {PLAYER}',
          f'attribute {PLAYER} minecraft:player.block_interaction_range base set 12')
    wanted = args.scenes or ['base', 'terminal', 'crafting', 'early', 'auto', 'vaults', 'drawers', 'backpacks',
                             'backpack_gui', 'late']
    scenes.builder_window()
    try:
        for name in wanted:
            getattr(scenes, name)()
    finally:
        scenes.stop_helper()
    return 0


if __name__ == '__main__':
    sys.exit(main())
