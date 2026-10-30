#!/usr/bin/env python3
"""Art and data for OmniVault autocrafting: patterns, the Pattern Encoding Terminal, Pattern Provider,
Assembler and Crafting Processor.

Textures are 16x16 hand-shaded procedural pixel art on the shared kit palette (gunmetal casings,
bevelled steel trim, cyan energy, violet omnium). Glowing parts are separate model elements marked
emissive (full-bright on NeoForge); "on"/"working" variants swap in lit, partly animated textures.

Usage (from the repository root): python3 tools/assets/autocraft.py
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kit  # noqa: E402
from kit import (  # noqa: E402
    AMBER, BLACK, CLEAR, CYAN, DATA, GUNMETAL, RESOURCES, STEEL, VIOLET, Model, Tex, blockstate_facing,
    blockstate_horizontal, item_model, loot_self, mix, save_animation, shade, shaped, shapeless, write_json, ASSETS, MOD,
)

BLOCKS = ['pattern_terminal', 'pattern_provider', 'assembler', 'crafting_processor']


# ---------------------------------------------------------------------------------------------
# Texture helpers (local to autocrafting; the kit stays untouched)
# ---------------------------------------------------------------------------------------------


def casing(seed: int, base: int = 2, screws: bool = True) -> Tex:
    """Brushed gunmetal plate with a bevelled rim and corner screws."""
    t = Tex(seed=seed).brushed(GUNMETAL, base=base)
    t.frame(GUNMETAL, width=1, base=3)
    if screws:
        for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
            t.screw(x, y)
    return t


def steel_trim(seed: int, vertical: bool = False) -> Tex:
    """Bevelled steel for frame bars and bezels: brushed, bright upper-left edge, dark lower-right."""
    t = Tex(seed=seed).brushed(STEEL, base=2, vertical=vertical)
    for i in range(16):
        t.set(i, 0, STEEL[4])
        t.set(0, i, STEEL[4])
        t.set(i, 15, STEEL[0])
        t.set(15, i, STEEL[0])
    # A machined groove down the middle.
    if vertical:
        t.vline(7, 1, 14, STEEL[1])
        t.vline(8, 1, 14, STEEL[3])
    else:
        t.hline(1, 14, 7, STEEL[1])
        t.hline(1, 14, 8, STEEL[3])
    return t


def glow_frame(t: Tex, x0, y0, x1, y1, ramp, lit: bool):
    """A thin energy outline: bright when lit, a dead dark line otherwise."""
    t.outline(x0, y0, x1, y1, ramp[3] if lit else ramp[0])
    if lit:
        for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            t.set(x, y, ramp[5])
    return t


def crystal(seed: int, lit: bool) -> Tex:
    """Faceted omnium: diagonal violet facets lit from the top, bright facet edges, cyan glints when powered."""
    t = Tex(seed=seed)
    for y in range(16):
        for x in range(16):
            body = mix(VIOLET[3], VIOLET[1], y / 15)
            facet = ((x + y) // 5) % 3
            body = shade(body, (1.15, 0.95, 0.8)[facet])
            t.set(x, y, body if lit else shade(body, 0.55))
    for y in range(16):
        for x in range(16):
            if (x + y) % 5 == 0:
                t.set(x, y, VIOLET[4] if lit else VIOLET[2])
            if (x - y) % 8 == 0 and y < 8:
                t.set(x, y, VIOLET[5] if lit else VIOLET[2])
    if lit:
        for x, y in ((3, 2), (11, 5), (6, 9), (13, 12), (2, 13)):
            t.set(x, y, CYAN[5])
            t.set(x + 1, y, CYAN[4])
    return t


def pattern_grid(t: Tex, x0: int, y0: int, cell: int, gap: int, lit: bool, accent=CYAN, frame=VIOLET, phase: int = -1):
    """A 3x3 pattern grid. ``phase`` (0..8) lights cells up to that index, like an encoding sweep."""
    size = cell * 3 + gap * 2
    t.rect(x0 - 1, y0 - 1, x0 + size, y0 + size, frame[1] if lit else GUNMETAL[0])
    for i in range(9):
        cx = x0 + (i % 3) * (cell + gap)
        cy = y0 + (i // 3) * (cell + gap)
        on = lit and (phase < 0 or i <= phase)
        color = accent[3] if on else (accent[1] if lit else GUNMETAL[1])
        t.rect(cx, cy, cx + cell - 1, cy + cell - 1, color)
        if on and cell > 1:
            t.set(cx, cy, accent[5])
    return t


# ---------------------------------------------------------------------------------------------
# Shared textures
# ---------------------------------------------------------------------------------------------


def shared_textures():
    steel_trim(11).save('block/autocraft_trim')
    steel_trim(12, vertical=True).save('block/autocraft_trim_vertical')
    casing(13, base=1).save('block/autocraft_casing_dark')
    crystal(14, False).save('block/omnium_crystal')
    crystal(14, True).save('block/omnium_crystal_on')
    # Network connector socket for device backs.
    t = casing(15)
    t.bevel(4, 4, 11, 11, GUNMETAL, base=1, inset=True)
    t.rect(6, 6, 9, 9, BLACK)
    t.outline(5, 5, 10, 10, STEEL[2])
    t.set(5, 5, STEEL[4])
    t.save('block/autocraft_connector')


# ---------------------------------------------------------------------------------------------
# Pattern Encoding Terminal
# ---------------------------------------------------------------------------------------------


def terminal_textures():
    side = casing(21)
    side.vents(4, 3, 11, 8, spacing=2)
    side.bevel(3, 10, 12, 13, GUNMETAL, base=1, inset=True)
    for i, x in enumerate(range(4, 12, 2)):
        side.set(x, 11, VIOLET[3] if i % 2 == 0 else CYAN[2])
        side.set(x + 1, 12, VIOLET[1])
    side.save('block/pattern_terminal_side')

    top = casing(22)
    top.bevel(3, 3, 12, 12, GUNMETAL, base=1, inset=True)
    top.vents(4, 4, 11, 11, spacing=2)
    top.save('block/pattern_terminal_top')

    back = casing(23)
    back.bevel(4, 4, 11, 11, GUNMETAL, base=1, inset=True)
    back.rect(6, 6, 9, 9, BLACK)
    back.outline(5, 5, 10, 10, STEEL[2])
    back.save('block/pattern_terminal_back')

    # Screen, off: dark glass with a faint reflection.
    off = Tex(seed=24).fill(rgb_dark())
    off.gradient_v(0, 0, 15, 15, (16, 22, 30, 255), (8, 11, 16, 255))
    off.line(3, 12, 9, 4, (34, 44, 58, 255))
    off.line(5, 12, 11, 4, (24, 31, 42, 255))
    off.save('block/pattern_terminal_screen')

    # Screen, on: a recipe grid encoding into a pattern card, cell by cell, with a scanline.
    frames = []
    for f in range(10):
        t = Tex(seed=25)
        t.gradient_v(0, 0, 15, 15, (10, 26, 44, 255), (6, 14, 26, 255))
        pattern_grid(t, 3, 5, 2, 1, True, accent=CYAN, frame=VIOLET, phase=min(f, 8))
        # Arrow and the pattern card it writes.
        t.hline(10, 11, 8, CYAN[2] if f < 9 else CYAN[4])
        t.set(11, 7, CYAN[3])
        t.set(11, 9, CYAN[3])
        card = VIOLET[4] if f >= 9 else VIOLET[2]
        t.rect(12, 5, 13, 11, card)
        t.vline(12, 6, 10, VIOLET[5] if f >= 9 else VIOLET[3])
        scan = 3 + (f * 1) % 10
        for x in range(2, 14):
            t.set(x, scan, mix(t.get(x, scan), CYAN[5], 0.35))
        frames.append(t)
    save_animation('block/pattern_terminal_screen_on', frames, frametime=4, interpolate=False)

    keys = Tex(seed=26).fill(GUNMETAL[1])
    for x in range(0, 16, 3):
        for y in range(0, 16, 3):
            keys.rect(x, y, x + 1, y + 1, GUNMETAL[3])
            keys.set(x, y, GUNMETAL[4])
    keys.save('block/pattern_terminal_keys')
    lit_keys = keys.copy()
    for i, x in enumerate(range(0, 16, 3)):
        lit_keys.set(x + 1, 1 + (i % 3) * 3, VIOLET[4] if i % 2 else CYAN[4])
    lit_keys.save('block/pattern_terminal_keys_on')

    for lit in (False, True):
        plate = Tex(seed=27).fill(GUNMETAL[0])
        plate.outline(0, 0, 15, 15, STEEL[1])
        pattern_grid(plate, 3, 3, 2, 2, lit, accent=VIOLET, frame=CYAN)
        plate.save('block/pattern_glyph_plate' + ('_on' if lit else ''))


def rgb_dark():
    return (12, 16, 22, 255)


def terminal_model(on: bool) -> Model:
    s = '_on' if on else ''
    m = Model(textures={
        'particle': 'block/pattern_terminal_side', 'side': 'block/pattern_terminal_side', 'top': 'block/pattern_terminal_top',
        'bottom': 'block/autocraft_casing_dark', 'back': 'block/pattern_terminal_back', 'trim': 'block/autocraft_trim',
        'trimv': 'block/autocraft_trim_vertical', 'screen': 'block/pattern_terminal_screen' + s,
        'keys': 'block/pattern_terminal_keys' + s, 'plate': 'block/pattern_glyph_plate' + s,
        'crystal': 'block/omnium_crystal' + s, 'connector': 'block/autocraft_connector',
    })
    # Chassis, slightly narrower than the block so the side plates sit proud of it.
    m.box((0.5, 0, 2), (15.5, 16, 16), faces={'north': 'side', 'east': 'side', 'west': 'side', 'up': 'top',
                                              'down': 'bottom', 'south': 'back'}, cull=True, name='chassis')
    # Steel bezel around the screen.
    m.box((0, 13, 0), (16, 16, 2), tex='trim', cull=True, name='bezel_top')
    m.box((0, 0, 0), (16, 3, 2), tex='trim', cull=True, name='bezel_bottom')
    m.box((0, 3, 0), (2, 13, 2), tex='trimv', cull=True, name='bezel_left')
    m.box((14, 3, 0), (16, 13, 2), tex='trimv', cull=True, name='bezel_right')
    # The screen sits in the recess; full-bright when the network runs.
    m.box((2, 4.5, 1.5), (14, 13, 2), faces={'north': ('screen', [2, 3, 14, 13])}, emissive=on, name='screen')
    # Encoding deck: a key ledge under the screen.
    m.box((2, 3, 0.5), (14, 4.5, 2), faces={'north': 'trim', 'up': 'keys', 'east': None, 'west': None, 'down': None,
                                           'south': None}, name='deck')
    m.box((3, 4.5, 1.0), (13, 4.75, 1.5), faces={'up': 'keys', 'north': 'keys'}, emissive=on, name='deck_keys')
    # Rivets on the bezel corners.
    for x in (0.5, 14.5):
        for y in (0.5, 14.5):
            m.box((x, y, -0.25), (x + 1, y + 1, 0), tex='trim', name='rivet')
    # Pattern glyph plates on both sides.
    m.box((15.5, 4, 5), (16, 12, 13), faces={'east': 'plate', 'up': 'trim', 'down': 'trim', 'north': 'trim', 'south': 'trim'},
          emissive=on, name='plate_east')
    m.box((0, 4, 5), (0.5, 12, 13), faces={'west': 'plate', 'up': 'trim', 'down': 'trim', 'north': 'trim', 'south': 'trim'},
          emissive=on, name='plate_west')
    # Top: vent grille and a little omnium crystal antenna on a steel socket.
    m.box((3, 16, 5), (13, 16.5, 12), faces={'up': 'top', 'north': 'trim', 'south': 'trim', 'east': 'trim', 'west': 'trim'},
          name='grille')
    m.box((10.5, 16, 12.5), (13.5, 17, 15.5), tex='trim', name='socket')
    m.box((11.25, 17, 13.25), (12.75, 20, 14.75), tex='crystal', emissive=on, rotation=(45, 'y', (12, 18, 14)), name='crystal')
    m.box((11.6, 20, 13.6), (12.4, 21, 14.4), tex='crystal', emissive=on, rotation=(45, 'y', (12, 18, 14)), name='crystal_tip')
    # Back connector.
    m.box((5, 5, 16), (11, 11, 16.25), faces={'south': 'connector'}, name='connector')
    # Side light strips along the chassis edges.
    for x0, x1 in ((0.25, 0.5), (15.5, 15.75)):
        m.box((x0, 1, 3), (x1, 2, 15), faces={'east': 'plate', 'west': 'plate', 'up': 'plate'}, emissive=on, name='strip')
    return m.block_display()


# ---------------------------------------------------------------------------------------------
# Pattern Provider
# ---------------------------------------------------------------------------------------------


def provider_textures():
    for lit in (False, True):
        side = casing(31, base=1)
        # Three pattern-card slots stacked like a card reader.
        for i, y in enumerate((3, 7, 11)):
            side.bevel(3, y, 12, y + 2, GUNMETAL, base=0, inset=True)
            side.hline(4, 11, y + 1, VIOLET[3] if lit and i != 1 else (CYAN[3] if lit else VIOLET[0]))
        side.save('block/pattern_provider_side' + ('_on' if lit else ''))

        port = Tex(seed=32).fill(GUNMETAL[1])
        port.radial(8, 8, 7.5, STEEL[3], STEEL[1])
        port.radial(8, 8, 5.5, GUNMETAL[0], GUNMETAL[1])
        ring = CYAN[3] if lit else CYAN[0]
        for a in range(0, 360, 8):
            x = 8 + math.cos(math.radians(a)) * 4.5
            y = 8 + math.sin(math.radians(a)) * 4.5
            port.set(int(x), int(y), ring)
        port.rect(6, 6, 9, 9, CYAN[2] if lit else BLACK)
        port.rect(7, 7, 8, 8, CYAN[5] if lit else GUNMETAL[0])
        port.save('block/pattern_provider_port' + ('_on' if lit else ''))

    # The emitter core, animated when lit: a pulsing push beam.
    frames = []
    for f in range(8):
        t = Tex(seed=33)
        pulse = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        t.radial(8, 8, 8, mix(CYAN[5], CYAN[3], 1 - pulse), CYAN[1])
        frames.append(t)
    save_animation('block/pattern_provider_core_on', frames, frametime=3)
    Tex(seed=34).radial(8, 8, 8, CYAN[1], GUNMETAL[0]).save('block/pattern_provider_core')


def provider_model(on: bool) -> Model:
    s = '_on' if on else ''
    m = Model(textures={
        'particle': 'block/pattern_provider_side', 'side': 'block/pattern_provider_side' + s, 'trim': 'block/autocraft_trim',
        'trimv': 'block/autocraft_trim_vertical', 'port': 'block/pattern_provider_port' + s,
        'core': 'block/pattern_provider_core' + s, 'connector': 'block/autocraft_connector', 'dark': 'block/autocraft_casing_dark',
    })
    # Inner body.
    m.box((1, 1, 1), (15, 15, 15), faces={'north': 'dark', 'south': 'connector', 'east': 'side', 'west': 'side',
                                          'up': 'side', 'down': 'side'}, name='body')
    # Steel cage along all twelve edges.
    for x in (0, 14):
        for z in (0, 14):
            m.box((x, 0, z), (x + 2, 16, z + 2), tex='trimv', cull=True, name='pillar')
    for y in (0, 14):
        for z in (0, 14):
            m.box((2, y, z), (14, y + 2, z + 2), tex='trim', cull=True, name='beam_x')
        for x in (0, 14):
            m.box((x, y, 2), (x + 2, y + 2, 14), tex='trimv', cull=True, name='beam_z')
    # The working face: a port plate and the emitter core that pushes into the machine.
    m.box((2.5, 2.5, 0.5), (13.5, 13.5, 1), faces={'north': 'port', 'east': 'trim', 'west': 'trim', 'up': 'trim',
                                                  'down': 'trim'}, name='port')
    m.box((6, 6, -0.5), (10, 10, 0.5), tex='core', emissive=on, name='core')
    for x0, y0, x1, y1 in ((4, 4, 5, 12), (11, 4, 12, 12)):
        m.box((x0, y0, 0), (x1, y1, 0.5), tex='core', emissive=on, name='port_light')
    # Back: a network socket ring.
    m.box((4, 4, 15), (12, 12, 15.5), faces={'south': 'connector', 'east': 'trim', 'west': 'trim', 'up': 'trim',
                                            'down': 'trim'}, name='socket')
    return m.block_display()


# ---------------------------------------------------------------------------------------------
# Assembler
# ---------------------------------------------------------------------------------------------


def assembler_textures():
    base = casing(41)
    base.vents(3, 5, 12, 10, spacing=2)
    base.save('block/assembler_base')

    top = casing(42)
    top.radial(8, 8, 6, GUNMETAL[0], GUNMETAL[3])
    top.outline(3, 3, 12, 12, STEEL[2])
    top.rect(6, 6, 9, 9, BLACK)
    top.save('block/assembler_top')

    pillar = Tex(seed=43).brushed(STEEL, base=2, vertical=True)
    pillar.vline(0, 0, 15, STEEL[4])
    pillar.vline(15, 0, 15, STEEL[0])
    pillar.vline(7, 0, 15, GUNMETAL[1])
    pillar.vline(8, 0, 15, GUNMETAL[2])
    pillar.save('block/assembler_pillar')

    # Overlay strips for the pillars (only the light line is opaque).
    dead = Tex(seed=44)
    dead.vline(7, 1, 14, CYAN[0])
    dead.vline(8, 1, 14, CYAN[0])
    dead.save('block/assembler_strip')
    steady = Tex(seed=44)
    steady.vline(7, 1, 14, CYAN[2])
    steady.vline(8, 1, 14, CYAN[3])
    steady.save('block/assembler_strip_on')
    frames = []
    for f in range(8):
        t = Tex(seed=44)
        for y in range(1, 15):
            d = (y - (14 - f * 2)) % 16
            color = CYAN[5] if d < 2 else CYAN[4] if d < 4 else CYAN[3]
            t.set(7, y, color)
            t.set(8, y, shade(color, 0.85))
        frames.append(t)
    save_animation('block/assembler_strip_working', frames, frametime=2)

    glass = Tex(seed=45, fill=(120, 220, 255, 46))
    glass.outline(0, 0, 15, 15, (180, 240, 255, 120))
    glass.line(2, 13, 7, 8, (230, 250, 255, 110))
    glass.line(4, 13, 9, 8, (230, 250, 255, 70))
    glass.line(11, 4, 13, 2, (230, 250, 255, 90))
    glass.save('block/assembler_glass')

    for name, lit in (('assembler_emitter', False), ('assembler_emitter_on', True)):
        t = Tex(seed=46).fill(GUNMETAL[1])
        t.outline(0, 0, 15, 15, STEEL[1])
        t.radial(8, 8, 6.5, CYAN[3] if lit else CYAN[0], CYAN[1] if lit else GUNMETAL[1])
        pattern_grid(t, 4, 4, 2, 1, lit, accent=VIOLET, frame=CYAN)
        t.save('block/' + name)
    frames = []
    for f in range(8):
        t = Tex(seed=46).fill(GUNMETAL[1])
        t.outline(0, 0, 15, 15, CYAN[3])
        pulse = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        t.radial(8, 8, 6 + pulse * 2, mix(CYAN[5], VIOLET[4], pulse), CYAN[1])
        pattern_grid(t, 4, 4, 2, 1, True, accent=VIOLET, frame=CYAN, phase=f)
        frames.append(t)
    save_animation('block/assembler_emitter_working', frames, frametime=2)

    for name, lit in (('assembler_light', False), ('assembler_light_on', True)):
        t = Tex(seed=47).fill(GUNMETAL[0])
        t.rect(2, 2, 13, 13, AMBER[3] if lit else AMBER[0])
        t.rect(4, 4, 11, 11, AMBER[4] if lit else AMBER[1])
        t.save('block/' + name)

    # Rings for the renderer: an energy band, animated.
    frames = []
    for f in range(8):
        t = Tex(seed=48)
        for x in range(16):
            for y in range(16):
                d = (x + y + f * 2) % 8
                t.set(x, y, CYAN[5] if d == 0 else CYAN[4] if d < 3 else CYAN[3])
        frames.append(t)
    save_animation('block/assembler_ring', frames, frametime=2)


def assembler_model(state: str) -> Model:
    """state: 'off' (no network), 'on' (online, idle) or 'working'."""
    lit = state != 'off'
    working = state == 'working'
    emitter = {'off': 'block/assembler_emitter', 'on': 'block/assembler_emitter_on', 'working': 'block/assembler_emitter_working'}[state]
    strip = {'off': 'block/assembler_strip', 'on': 'block/assembler_strip_on', 'working': 'block/assembler_strip_working'}[state]
    m = Model(textures={
        'particle': 'block/assembler_base', 'base': 'block/assembler_base', 'top': 'block/assembler_top',
        'dark': 'block/autocraft_casing_dark', 'pillar': 'block/assembler_pillar', 'strip': strip, 'glass': 'block/assembler_glass',
        'emitter': emitter, 'trim': 'block/autocraft_trim', 'crystal': 'block/omnium_crystal' + ('_on' if lit else ''),
        'light': 'block/assembler_light' + ('_on' if working else ''),
    }, render_type='minecraft:translucent')
    # Plinth and cap.
    m.box((0, 0, 0), (16, 3, 16), faces={'north': 'base', 'south': 'base', 'east': 'base', 'west': 'base', 'up': 'dark',
                                        'down': 'dark'}, cull=True, name='plinth', uv={f: [0, 13, 16, 16] for f in
                                                                                     ('north', 'south', 'east', 'west')})
    m.box((0, 13, 0), (16, 16, 16), faces={'north': 'base', 'south': 'base', 'east': 'base', 'west': 'base', 'up': 'top',
                                          'down': 'dark'}, cull=True, name='cap', uv={f: [0, 0, 16, 3] for f in
                                                                                    ('north', 'south', 'east', 'west')})
    # Corner pillars with light strips on their outer faces.
    for x in (0, 13):
        for z in (0, 13):
            m.box((x, 3, z), (x + 3, 13, z + 3), tex='pillar', name='pillar')
            m.box((x - 0.01, 3.5, z - 0.01), (x + 3.01, 12.5, z + 3.01), tex='strip', faces={'up': None, 'down': None},
                  uv={f: [5, 1, 11, 15] for f in ('north', 'south', 'east', 'west')}, emissive=lit, name='pillar_strip')
    # Emitter plates top and bottom, the fabrication nozzle and the pedestal.
    m.box((3, 3, 3), (13, 3.5, 13), faces={'up': 'emitter'}, emissive=lit, name='floor_emitter')
    m.box((3, 12.5, 3), (13, 13, 13), faces={'down': 'emitter'}, emissive=lit, name='ceiling_emitter')
    m.box((6.5, 3.5, 6.5), (9.5, 4.5, 9.5), tex='trim', name='pedestal')
    m.box((7, 11, 7), (9, 12.5, 9), tex='trim', name='nozzle')
    m.box((7.5, 10.25, 7.5), (8.5, 11, 8.5), tex='crystal', emissive=lit, name='nozzle_tip')
    # Status lights on each face of the plinth.
    for f, (a, b) in {'north': ((7, 1, -0.25), (9, 2, 0)), 'south': ((7, 1, 16), (9, 2, 16.25)),
                      'west': ((-0.25, 1, 7), (0, 2, 9)), 'east': ((16, 1, 7), (16.25, 2, 9))}.items():
        m.box(a, b, tex='light', emissive=working, name='status_' + f)
    # Crystal finials on the cap.
    for x, z in ((1.5, 1.5), (13.5, 1.5), (1.5, 13.5), (13.5, 13.5)):
        m.box((x - 0.75, 16, z - 0.75), (x + 0.75, 17.5, z + 0.75), tex='crystal', emissive=lit,
              rotation=(45, 'y', (x, 16, z)), name='finial')
    # Glass panes last so they sort behind the solid parts.
    for f, (a, b) in {'north': ((3, 3, 0.5), (13, 13, 1)), 'south': ((3, 3, 15), (13, 13, 15.5)),
                      'west': ((0.5, 3, 3), (1, 13, 13)), 'east': ((15, 3, 3), (15.5, 13, 13))}.items():
        m.box(a, b, faces={f: 'glass', {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[f]: 'glass'},
              name='glass_' + f)
    return m.block_display()


def assembler_ring_model() -> Model:
    m = Model(parent=None, textures={'particle': 'block/assembler_ring', 'ring': 'block/assembler_ring',
                                     'crystal': 'block/omnium_crystal_on'}, ambient_occlusion=False)
    m.box((3, 7.5, 3), (13, 8.5, 4), tex='ring', emissive=True, name='ring_n')
    m.box((3, 7.5, 12), (13, 8.5, 13), tex='ring', emissive=True, name='ring_s')
    m.box((3, 7.5, 4), (4, 8.5, 12), tex='ring', emissive=True, name='ring_w')
    m.box((12, 7.5, 4), (13, 8.5, 12), tex='ring', emissive=True, name='ring_e')
    for x, z in ((2.5, 2.5), (11.5, 2.5), (2.5, 11.5), (11.5, 11.5)):
        m.box((x, 7, z), (x + 2, 9, z + 2), tex='crystal', emissive=True, name='node')
    return m


# ---------------------------------------------------------------------------------------------
# Crafting Processor
# ---------------------------------------------------------------------------------------------


def processor_textures():
    side = casing(51, base=1)
    side.vents(3, 12, 12, 14, spacing=2)
    side.save('block/crafting_processor_side')

    fins = Tex(seed=52).brushed(STEEL, base=1)
    for y in range(0, 16, 3):
        fins.hline(0, 15, y, STEEL[4])
        fins.hline(0, 15, y + 1, STEEL[2])
        fins.hline(0, 15, y + 2, GUNMETAL[0])
    fins.save('block/crafting_processor_fins')

    def leds(seed, palette_for):
        t = Tex(seed=seed).fill(GUNMETAL[0])
        t.outline(0, 0, 15, 15, STEEL[1])
        for row in range(5):
            for col in range(5):
                x = 2 + col * 3
                y = 2 + row * 3
                t.rect(x, y, x + 1, y + 1, palette_for(row, col))
        return t

    leds(53, lambda r, c: GUNMETAL[2]).save('block/crafting_processor_leds')
    leds(53, lambda r, c: CYAN[1] if (r + c) % 3 else CYAN[3]).save('block/crafting_processor_leds_on')
    frames = []
    for f in range(12):
        rng_seed = 1000 + f * 17

        def busy(r, c, f=f, rng_seed=rng_seed):
            v = (r * 7 + c * 13 + rng_seed) % 11
            if (r + f) % 5 == c:
                return VIOLET[5]
            if v < 3:
                return VIOLET[3]
            if v < 6:
                return CYAN[3]
            if v == 6:
                return AMBER[3]
            return CYAN[1]
        frames.append(leds(53, busy))
    save_animation('block/crafting_processor_leds_busy', frames, frametime=3, interpolate=False)


def processor_model(state: str) -> Model:
    """state: 'off', 'on' or 'busy'."""
    lit = state != 'off'
    leds = {'off': 'block/crafting_processor_leds', 'on': 'block/crafting_processor_leds_on',
            'busy': 'block/crafting_processor_leds_busy'}[state]
    m = Model(textures={
        'particle': 'block/crafting_processor_side', 'side': 'block/crafting_processor_side', 'fins': 'block/crafting_processor_fins',
        'dark': 'block/autocraft_casing_dark', 'trimv': 'block/autocraft_trim_vertical', 'trim': 'block/autocraft_trim',
        'leds': leds, 'crystal': 'block/omnium_crystal' + ('_on' if lit else ''),
    })
    m.box((1, 1, 1), (15, 14, 15), tex='side', faces={'up': 'dark', 'down': 'dark'}, name='chassis')
    m.box((0, 0, 0), (16, 1, 16), tex='trim', faces={'up': 'dark'}, cull=True, name='base',
          uv={f: [0, 0, 16, 1] for f in ('north', 'south', 'east', 'west')})
    for x in (0, 14):
        for z in (0, 14):
            m.box((x, 1, z), (x + 2, 16, z + 2), tex='trimv', cull=True, name='rail')
    # LED matrices on all four sides.
    full = [0, 0, 16, 16]
    for face, (a, b) in {'north': ((3, 3, 0.5), (13, 12, 1)), 'south': ((3, 3, 15), (13, 12, 15.5)),
                         'west': ((0.5, 3, 3), (1, 12, 13)), 'east': ((15, 3, 3), (15.5, 12, 13))}.items():
        m.box(a, b, faces={f: (('leds', full) if f == face else None) for f in ('north', 'south', 'east', 'west', 'up', 'down')},
              emissive=lit, name='leds_' + face)
    # Heat-sink fins across the top, split around the core crystal.
    for i in range(5):
        z = 2.5 + i * 2.4
        if 6 <= z <= 9:
            m.box((2, 14, z), (5.5, 16, z + 1), tex='fins', name='fin')
            m.box((10.5, 14, z), (14, 16, z + 1), tex='fins', name='fin')
        else:
            m.box((2, 14, z), (14, 16, z + 1), tex='fins', name='fin')
    m.box((6.5, 14, 6.5), (9.5, 17.5, 9.5), tex='crystal', emissive=lit, rotation=(45, 'y', (8, 16, 8)), name='core')
    m.box((7.25, 17.5, 7.25), (8.75, 18.5, 8.75), tex='crystal', emissive=lit, rotation=(45, 'y', (8, 16, 8)), name='core_tip')
    return m.block_display()


# ---------------------------------------------------------------------------------------------
# Pattern items
# ---------------------------------------------------------------------------------------------


def pattern_card(seed: int) -> Tex:
    """The slate every pattern is printed on: a gunmetal card with a steel rim and a clipped corner."""
    t = Tex(seed=seed)
    t.bevel(2, 1, 13, 14, GUNMETAL, base=2)
    t.outline(2, 1, 13, 14, STEEL[1])
    t.hline(3, 12, 1, STEEL[3])
    t.vline(2, 2, 13, STEEL[3])
    # Clipped top-right corner and a contact strip at the bottom.
    t.set(13, 1, CLEAR)
    t.set(12, 1, STEEL[0])
    t.set(13, 2, STEEL[0])
    for x in range(4, 12, 2):
        t.set(x, 13, AMBER[3])
        t.set(x + 1, 13, AMBER[1])
    return t


def item_textures():
    blank = pattern_card(61)
    for i in range(9):
        x = 4 + (i % 3) * 3
        y = 3 + (i // 3) * 3
        blank.rect(x, y, x + 1, y + 1, GUNMETAL[0])
        blank.set(x + 1, y + 1, GUNMETAL[3])
    blank.save('item/blank_pattern')

    # Crafting: a filled 3x3 grid; the glow layer lights it cyan.
    craft = pattern_card(62)
    for i in range(9):
        x = 4 + (i % 3) * 3
        y = 3 + (i // 3) * 3
        craft.rect(x, y, x + 1, y + 1, CYAN[1])
    craft.save('item/crafting_pattern')
    glow = Tex(seed=63)
    for i in range(9):
        x = 4 + (i % 3) * 3
        y = 3 + (i // 3) * 3
        glow.rect(x, y, x + 1, y + 1, CYAN[3] if i != 4 else CYAN[5])
        glow.set(x, y, CYAN[4])
    glow.hline(4, 11, 12, CYAN[2])
    glow.save('item/crafting_pattern_glow')

    # Processing: input -> machine -> output glyph in violet.
    proc = pattern_card(64)
    proc.rect(4, 4, 6, 6, VIOLET[1])
    proc.rect(9, 8, 11, 10, VIOLET[1])
    proc.save('item/processing_pattern')
    glow = Tex(seed=65)
    glow.rect(4, 4, 6, 6, VIOLET[3])
    glow.set(5, 5, VIOLET[5])
    glow.line(7, 5, 10, 5, VIOLET[4])
    glow.line(10, 5, 10, 7, VIOLET[4])
    glow.set(9, 6, VIOLET[4])
    glow.set(11, 6, VIOLET[4])
    glow.rect(9, 8, 11, 10, VIOLET[3])
    glow.set(10, 9, VIOLET[5])
    # A flame-ish spark below: it is for machines.
    glow.set(5, 9, AMBER[4])
    glow.set(5, 10, AMBER[3])
    glow.set(4, 10, AMBER[2])
    glow.set(6, 10, AMBER[2])
    glow.hline(4, 11, 12, VIOLET[2])
    glow.save('item/processing_pattern_glow')


def glowing_item_model(name: str):
    write_json(ASSETS / 'models' / 'item' / (name + '.json'), {
        'parent': 'neoforge:item/default',
        'loader': 'neoforge:item_layers',
        'textures': {'layer0': f'{MOD}:item/{name}', 'layer1': f'{MOD}:item/{name}_glow'},
        'neoforge_data': {'layers': {'1': {'block_light': 15, 'sky_light': 15}}},
    })


# ---------------------------------------------------------------------------------------------
# Blockstates, item models, loot, tags, recipes
# ---------------------------------------------------------------------------------------------


def blockstate_variants(name: str, variants: dict[str, str]):
    write_json(ASSETS / 'blockstates' / (name + '.json'),
               {'variants': {k: {'model': f'{MOD}:{v}'} for k, v in variants.items()}})


def merge_tag(registry: str, name: str, values: list[str], namespace: str = 'minecraft'):
    """Adds values to a tag file other features may also write, keeping theirs."""
    kit.merge_tag(registry, name, values, namespace)


def data():
    for block in BLOCKS:
        loot_self(block)
    merge_tag('block', 'mineable/pickaxe', [f'{MOD}:{b}' for b in BLOCKS])

    shaped('blank_pattern', ['GRG', 'RLR', 'III'], {
        'G': 'minecraft:glowstone_dust', 'R': 'minecraft:redstone', 'L': f'{MOD}:logic_processor', 'I': 'minecraft:iron_ingot',
    }, count=4)
    shapeless('pattern_terminal', [f'{MOD}:terminal', f'{MOD}:advanced_processor', 'minecraft:crafting_table', f'{MOD}:blank_pattern'])
    shaped('pattern_provider', ['IOI', 'CLC', 'IOI'], {
        'I': 'minecraft:iron_ingot', 'O': f'{MOD}:omnium_ingot', 'C': 'minecraft:crafting_table', 'L': f'{MOD}:logic_processor',
    })
    shaped('assembler', ['OAO', 'PCP', 'OGO'], {
        'O': f'{MOD}:omnium_ingot', 'A': f'{MOD}:advanced_processor', 'P': 'minecraft:piston', 'C': 'minecraft:crafter',
        'G': 'minecraft:glass',
    })
    shaped('crafting_processor', ['OGO', 'ALA', 'OGO'], {
        'O': f'{MOD}:omnium_ingot', 'G': 'minecraft:gold_ingot', 'A': f'{MOD}:advanced_processor', 'L': f'{MOD}:logic_processor',
    })
    # Wiping a pattern in a crafting grid works too (sneak-use is the quick way).
    shapeless('blank_pattern_from_crafting', [f'{MOD}:crafting_pattern'], result=f'{MOD}:blank_pattern')
    shapeless('blank_pattern_from_processing', [f'{MOD}:processing_pattern'], result=f'{MOD}:blank_pattern')


def main():
    shared_textures()
    terminal_textures()
    provider_textures()
    assembler_textures()
    processor_textures()
    item_textures()

    terminal_model(False).save('block/pattern_terminal')
    terminal_model(True).save('block/pattern_terminal_on')
    blockstate_horizontal('pattern_terminal', extra={'active=false': 'block/pattern_terminal', 'active=true': 'block/pattern_terminal_on'})

    provider_model(False).save('block/pattern_provider')
    provider_model(True).save('block/pattern_provider_on')
    blockstate_facing('pattern_provider', extra={'active=false': 'block/pattern_provider', 'active=true': 'block/pattern_provider_on'})

    assembler_model('off').save('block/assembler')
    assembler_model('on').save('block/assembler_on')
    assembler_model('working').save('block/assembler_working')
    assembler_ring_model().save('block/assembler_ring')
    blockstate_variants('assembler', {
        'active=false,working=false': 'block/assembler', 'active=true,working=false': 'block/assembler_on',
        'active=false,working=true': 'block/assembler_working', 'active=true,working=true': 'block/assembler_working',
    })

    processor_model('off').save('block/crafting_processor')
    processor_model('on').save('block/crafting_processor_on')
    processor_model('busy').save('block/crafting_processor_busy')
    blockstate_variants('crafting_processor', {
        'active=false,working=false': 'block/crafting_processor', 'active=true,working=false': 'block/crafting_processor_on',
        'active=false,working=true': 'block/crafting_processor_busy', 'active=true,working=true': 'block/crafting_processor_busy',
    })

    item_model('pattern_terminal', parent='block/pattern_terminal_on')
    item_model('pattern_provider', parent='block/pattern_provider_on')
    item_model('assembler', parent='block/assembler_on')
    item_model('crafting_processor', parent='block/crafting_processor_on')
    item_model('blank_pattern')
    glowing_item_model('crafting_pattern')
    glowing_item_model('processing_pattern')

    data()
    counts = {n: len(json.loads((ASSETS / 'models' / 'block' / (n + '.json')).read_text())['elements'])
              for n in ('pattern_terminal_on', 'pattern_provider_on', 'assembler_working', 'crafting_processor_busy', 'assembler_ring')}
    print('Autocraft art written; elements per model:', counts)


if __name__ == '__main__':
    main()
