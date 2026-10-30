#!/usr/bin/env python3
"""OmniVault drawers: 1x1 / 1x2 / 2x2 drawers, the compacting drawer, the drawer controller and trim,
the drawer key and the drawer upgrades.

Writes textures, multi-element block models, blockstates, item models, loot tables (that keep a drawer's
contents), recipes and block/item tags. Run from the repository root:

    python3 tools/assets/drawers.py

Look: dark-stained walnut carcasses with a bevelled gunmetal front frame, polished steel corner caps and
handles, recessed raised-panel drawer fronts, smoked-glass label plates and a thin glowing omnium vein
(full-bright through NeoForge's per-element light data). The compacting drawer and the controller are the
"machine" members of the family: gunmetal casings with violet accents.

The front geometry (panels, handles, label plates) mirrors
1.21.1/common/src/main/java/com/nightbeam/omnivault/drawer/DrawerLayout.java: the renderer draws each
compartment's item and count at the positions defined there, so keep both in sync.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from kit import (AMBER, BLACK, CLEAR, CYAN, DATA, DIAMOND, GOLD, GREEN, GUNMETAL, IRON, MOD, NETHERITE,  # noqa: E402
                 RESOURCES, STEEL, VIOLET, Model, Tex, blockstate_horizontal, blockstate_single, item_model,
                 loot_self, mix, rgb, shade, shaped, shapeless, write_json)

# ---------------------------------------------------------------------------------------------
# Palette additions
# ---------------------------------------------------------------------------------------------

# Dark-stained walnut, dark -> light.
WALNUT = [rgb('1f1008'), rgb('331b0e'), rgb('4a2a16'), rgb('613a20'), rgb('7d4f2d'), rgb('9c6a40')]
# Smoked display glass for the count labels.
GLASS = [rgb('05080c'), rgb('0a1118'), rgb('101b25'), rgb('172836'), rgb('1f3a4c'), rgb('2d5a72')]

D = 'block/drawer/'  # texture folder


def clamp_index(ramp, i):
    return ramp[max(0, min(len(ramp) - 1, i))]


# ---------------------------------------------------------------------------------------------
# Texture helpers (kept here so kit.py stays shared and untouched)
# ---------------------------------------------------------------------------------------------


def grain(t: Tex, ramp, x0, y0, x1, y1, vertical=False, plank=4, base=2, seams=True, knots=1):
    """Planked wood: each plank has its own tone, streaky grain runs along it, seams are dark with a
    lit edge, and the odd knot breaks the pattern."""
    rng = t.rng
    across = range(x0, x1 + 1) if vertical else range(y0, y1 + 1)
    along = list(range(y0, y1 + 1) if vertical else range(x0, x1 + 1))
    start = (x0 if vertical else y0)
    end = (x1 if vertical else y1)
    seam_at = set()
    pos = start + rng.randint(plank - 1, plank + 1)
    while pos < end:
        seam_at.add(pos)
        pos += plank + rng.choice([-1, 0, 0, 1])
    tone = base + rng.choice([-1, 0, 0, 1])
    for i in across:
        if i in seam_at:
            tone = base + rng.choice([-1, 0, 0, 1])
        line_tone = max(1, min(len(ramp) - 2, tone + (rng.random() < 0.18) * rng.choice([-1, 1])))
        run = 0
        run_tone = line_tone
        for j in along:
            if run <= 0:
                run = rng.randint(2, 5)
                run_tone = line_tone + (rng.choice([-1, 1]) if rng.random() < 0.32 else 0)
            run -= 1
            color = clamp_index(ramp, run_tone)
            if seams and i in seam_at:
                color = clamp_index(ramp, base - 2)
            elif seams and (i - 1) in seam_at:
                color = mix(color, clamp_index(ramp, base + 2), 0.45)
            x, y = (i, j) if vertical else (j, i)
            t.set(x, y, color)
    for _ in range(knots):
        kx = rng.randint(x0 + 2, max(x0 + 2, x1 - 2))
        ky = rng.randint(y0 + 2, max(y0 + 2, y1 - 2))
        if x1 - x0 < 4 or y1 - y0 < 4:
            break
        t.set(kx, ky, clamp_index(ramp, base - 2))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(kx + dx, ky + dy, mix(t.get(kx + dx, ky + dy), clamp_index(ramp, base - 1), 0.6))
        for dx, dy in ((1, 1), (-1, -1)):
            t.set(kx + dx, ky + dy, mix(t.get(kx + dx, ky + dy), clamp_index(ramp, base + 1), 0.4))
    return t


def bevel_edges(t: Tex, x0, y0, x1, y1, light, dark, corner=None):
    """Lit top/left edge and shadowed bottom/right edge on an existing surface."""
    t.hline(x0, x1, y0, light)
    t.vline(x0, y0, y1, light)
    t.hline(x0, x1, y1, dark)
    t.vline(x1, y0, y1, dark)
    if corner:
        t.set(x1, y0, corner)
        t.set(x0, y1, corner)
    return t


def speckle(t: Tex, x0, y0, x1, y1, color, chance=0.08, strength=0.5):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if t.rng.random() < chance:
                t.set(x, y, mix(t.get(x, y), color, strength))
    return t


def bracket(t: Tex, cx, cy, dx, dy, arm=4, ramp=STEEL):
    """An L-shaped steel corner bracket with a screw, pointing into the face from corner (cx, cy)."""
    for i in range(arm):
        t.set(cx + dx * i, cy, ramp[3] if i else ramp[4])
        t.set(cx, cy + dy * i, ramp[3] if i else ramp[4])
    t.set(cx + dx * (arm - 1), cy, ramp[2])
    t.set(cx, cy + dy * (arm - 1), ramp[2])
    # A shadow under the bracket gives it thickness.
    for i in range(1, arm):
        sx, sy = cx + dx * i, cy + dy
        t.set(sx, sy, mix(t.get(sx, sy), BLACK, 0.35))
        sx, sy = cx + dx, cy + dy * i
        t.set(sx, sy, mix(t.get(sx, sy), BLACK, 0.35))
    t.set(cx + dx, cy + dy, ramp[1])
    return t


def rivet(t: Tex, x, y, ramp=STEEL):
    t.set(x, y, ramp[4])
    t.set(x + 1, y, ramp[2])
    t.set(x, y + 1, ramp[2])
    t.set(x + 1, y + 1, ramp[0])
    return t


def outline_sprite(t: Tex, color):
    """Adds a 1px outline around every opaque pixel of a sprite."""
    src = t.img.copy().load()
    for y in range(t.h):
        for x in range(t.w):
            if src[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < t.w and 0 <= ny < t.h and src[nx, ny][3] != 0:
                    t.set(x, y, color)
                    break
    return t


def paint(t: Tex, rows: list[str], palette: dict, ox=0, oy=0):
    """Draws an ASCII pixel map; '.' and ' ' are left untouched."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in '. ':
                continue
            t.set(ox + x, oy + y, palette[ch])
    return t


# ---------------------------------------------------------------------------------------------
# Block textures: walnut drawers
# ---------------------------------------------------------------------------------------------


def walnut_casing(seed, vertical=True, straps=False):
    t = Tex(seed=seed)
    grain(t, WALNUT, 0, 0, 15, 15, vertical=vertical, plank=5, knots=2)
    # Gunmetal edge band with a bevel, then a soft shadow just inside it.
    bevel_edges(t, 0, 0, 15, 15, GUNMETAL[4], GUNMETAL[1])
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            t.set(x, y, mix(t.get(x, y), BLACK, 0.35))
        for (x, y) in ((i, 14), (14, i)):
            t.set(x, y, mix(t.get(x, y), WALNUT[4], 0.2))
    if straps:
        for y in (5, 10):
            t.hline(1, 14, y, STEEL[2])
            t.hline(1, 14, y - 1, mix(t.get(7, y - 1), STEEL[4], 0.5))
            t.hline(1, 14, y + 1, mix(t.get(7, y + 1), BLACK, 0.4))
            for x in (3, 12):
                t.set(x, y, STEEL[4])
    for cx, cy, dx, dy in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        bracket(t, cx, cy, dx, dy)
    return t


def walnut_back():
    t = Tex(seed=31)
    grain(t, WALNUT, 0, 0, 15, 15, vertical=False, plank=4, base=2, knots=2)
    bevel_edges(t, 0, 0, 15, 15, GUNMETAL[4], GUNMETAL[1])
    # A steel mounting plate with four screws.
    t.bevel(4, 4, 11, 11, STEEL, base=2)
    t.brushed(STEEL, 5, 5, 10, 10, base=2)
    for x, y in ((5, 5), (9, 5), (5, 9), (9, 9)):
        rivet(t, x, y)
    t.hline(4, 11, 12, mix(t.get(7, 12), BLACK, 0.4))
    t.vline(12, 4, 12, mix(t.get(12, 7), BLACK, 0.4))
    return t


def frame_texture():
    """Gunmetal front frame. The outermost ring is what the 1px frame bars show (lit top/left, shadowed
    bottom/right) and the central cross is what dividers show."""
    t = Tex(seed=41)
    t.brushed(GUNMETAL, base=3)
    speckle(t, 0, 0, 15, 15, GUNMETAL[5], 0.06, 0.5)
    t.hline(0, 15, 0, GUNMETAL[5])
    t.vline(0, 0, 15, GUNMETAL[5])
    t.hline(0, 15, 15, GUNMETAL[1])
    t.vline(15, 0, 15, GUNMETAL[1])
    t.hline(1, 14, 7, GUNMETAL[4])
    t.hline(1, 14, 8, GUNMETAL[2])
    t.vline(7, 1, 14, GUNMETAL[4])
    t.vline(8, 1, 14, GUNMETAL[2])
    # Fine scratches catch the light.
    for _ in range(6):
        x, y = t.rng.randint(1, 14), t.rng.randint(1, 14)
        t.set(x, y, STEEL[1])
    return t


def recess_texture():
    """The dark cavity behind the drawer fronts: light falls in from the top."""
    t = Tex(seed=43)
    t.gradient_v(0, 0, 15, 15, GUNMETAL[2], GUNMETAL[0])
    speckle(t, 0, 0, 15, 15, BLACK, 0.25, 0.6)
    speckle(t, 0, 0, 15, 6, GUNMETAL[3], 0.12, 0.5)
    return t


def walnut_panel(width, height, seed, groove):
    """A raised drawer front: walnut field, lit top/left bevel, shadowed bottom/right, an inset groove."""
    t = Tex(seed=seed)
    grain(t, WALNUT, 0, 0, 15, 15, vertical=False, plank=3, base=3, knots=0)
    x1, y1 = width - 1, height - 1
    grain(t, WALNUT, 0, 0, x1, y1, vertical=False, plank=3 if height > 6 else 2, base=3, knots=1 if height > 6 else 0)
    bevel_edges(t, 0, 0, x1, y1, WALNUT[5], WALNUT[0], WALNUT[3])
    if groove and width > 6 and height > 6:
        # Inset groove one pixel in: shadowed top/left, lit bottom/right (reads as a cut line).
        bevel_edges(t, 1, 1, x1 - 1, y1 - 1, WALNUT[1], WALNUT[4], WALNUT[2])
        # Slightly darker field where the item sits, so icons pop.
        for y in range(3, y1 - 2):
            for x in range(3, x1 - 2):
                t.set(x, y, shade(t.get(x, y), 0.88))
    else:
        t.hline(1, x1 - 1, 1, mix(t.get(2, 1), WALNUT[4], 0.4))
    return t


def steel_texture():
    """Polished steel for handles, knobs and pulls. Row 1 is the bright highlight band, row 5 the dark
    underside; models pick the rows they need."""
    t = Tex(seed=47)
    t.brushed(STEEL, base=3)
    rows = {0: STEEL[2], 1: STEEL[5], 2: STEEL[4], 3: STEEL[3], 4: STEEL[2], 5: STEEL[1]}
    for y, color in rows.items():
        for x in range(16):
            jitter = t.rng.random() < 0.2
            t.set(x, y, mix(color, STEEL[3], 0.25) if jitter else color)
    return t


def cap_texture():
    """Corner caps: (0,0)-(1,1) is a domed bolt head; the rest is brushed steel for the cap sides."""
    t = Tex(seed=53)
    t.brushed(STEEL, base=2, vertical=True)
    t.set(0, 0, STEEL[5])
    t.set(1, 0, STEEL[4])
    t.set(0, 1, STEEL[3])
    t.set(1, 1, STEEL[1])
    # (2,0)-(3,1): a darker gunmetal bolt for the machine blocks.
    t.set(2, 0, GUNMETAL[5])
    t.set(3, 0, GUNMETAL[4])
    t.set(2, 1, GUNMETAL[3])
    t.set(3, 1, GUNMETAL[1])
    # (0,2)-(2,4): a 3x3 boss with a centre jewel socket for the 2x2 drawer cross.
    paint(t, ['abc', 'bdc', 'cce'], {'a': STEEL[5], 'b': STEEL[4], 'c': STEEL[2], 'd': GUNMETAL[0], 'e': STEEL[1]}, 0, 2)
    return t


def label_texture():
    """Smoked-glass count plates with steel end caps: row 0-1 the 7x2 plate (1x1 drawer), row 4 the
    5-wide plate (1x2 / compacting top), row 6 the 4-wide plate (2x2 / compacting bottom)."""
    t = Tex(seed=59)
    t.noise(GLASS, base=1, chance=0.3)

    def plate(y0, y1, width):
        for y in range(y0, y1 + 1):
            for x in range(width):
                tone = 2 if y == y0 else 1
                c = clamp_index(GLASS, tone + (1 if t.rng.random() < 0.15 else 0))
                t.set(x, y, c)
            t.set(0, y, STEEL[3] if y == y0 else STEEL[2])
            t.set(width - 1, y, STEEL[2] if y == y0 else STEEL[1])
        # A faint cyan reflection streak.
        t.set(1, y0, mix(GLASS[3], CYAN[2], 0.35))
        t.set(2, y0, mix(GLASS[3], CYAN[2], 0.2))

    plate(0, 1, 7)
    plate(4, 4, 5)
    plate(6, 6, 4)
    return t


def glow_texture():
    """Omnium glow for emissive elements. Row 0 holds indicator colours (green, cyan, violet, amber in
    4px blocks); rows 2..15 run cyan -> violet with a bright core along rows 7-8."""
    t = Tex(seed=61)
    for i, ramp in enumerate((GREEN, CYAN, VIOLET, AMBER)):
        for x in range(i * 4, i * 4 + 4):
            t.set(x, 0, ramp[4] if x % 4 in (1, 2) else ramp[3])
            t.set(x, 1, ramp[3])
    for y in range(2, 16):
        core = 1 - min(1.0, abs(y - 7.5) / 6.5)
        for x in range(16):
            base = mix(CYAN[3], VIOLET[3], x / 15)
            bright = mix(CYAN[5], VIOLET[5], x / 15)
            c = mix(base, bright, core * 0.8)
            if t.rng.random() < 0.12:
                c = mix(c, CYAN[5], 0.4)
            t.set(x, y, c)
    return t


def lock_icon():
    t = Tex(seed=67)
    rows = [
        '................',
        '................',
        '.....abbbba.....',
        '....ab....ca....',
        '....b......c....',
        '....b......c....',
        '....b......c....',
        '...GGGGGGGGGG...',
        '...GHHHHHHHHg...',
        '...GHhhkkhhhg...',
        '...GHhkkkkhhg...',
        '...GHhhkkhhhg...',
        '...GHhhkkhhhg...',
        '...GHhhhhhhhg...',
        '...gggggggggg...',
        '................',
    ]
    paint(t, rows, {'a': STEEL[3], 'b': STEEL[4], 'c': STEEL[2], 'G': GOLD[4], 'H': GOLD[3], 'h': GOLD[2],
                    'g': GOLD[1], 'k': GUNMETAL[0]})
    outline_sprite(t, BLACK)
    return t


def void_icon():
    t = Tex(seed=71)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            if r > 6.6:
                continue
            angle = math.atan2(dy, dx)
            swirl = (math.sin(angle * 2 + r * 1.25) + 1) / 2
            if r < 1.8:
                c = BLACK
            elif r > 5.8:
                c = VIOLET[4] if angle < 0 else VIOLET[3]
            else:
                c = mix(VIOLET[1], VIOLET[4], swirl * (r / 6))
                if swirl > 0.85 and r > 3:
                    c = mix(c, CYAN[4], 0.5)
            t.set(x, y, c)
    _ = (cx, cy)
    outline_sprite(t, BLACK)
    return t


# ---------------------------------------------------------------------------------------------
# Block textures: compacting drawer, controller, trim
# ---------------------------------------------------------------------------------------------


def gunmetal_casing(seed, emblem):
    t = Tex(seed=seed)
    t.brushed(GUNMETAL, base=2)
    speckle(t, 0, 0, 15, 15, GUNMETAL[4], 0.05, 0.6)
    bevel_edges(t, 0, 0, 15, 15, GUNMETAL[5], GUNMETAL[0])
    # Raised inner plate.
    bevel_edges(t, 2, 2, 13, 13, GUNMETAL[4], GUNMETAL[1])
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(t, x, y)
    emblem(t)
    return t


def compression_emblem(t: Tex):
    """Two arrows squeezing a bar: the compacting drawer's mark."""
    for i, y in enumerate((3, 4, 5)):
        t.hline(5 + i, 10 - i, y, VIOLET[2 + i])
    for i, y in enumerate((12, 11, 10)):
        t.hline(5 + i, 10 - i, y, VIOLET[2 + i])
    t.hline(5, 10, 7, VIOLET[5])
    t.hline(5, 10, 8, VIOLET[3])
    t.set(5, 7, VIOLET[4])
    t.set(10, 8, VIOLET[2])
    t.hline(5, 10, 9, mix(t.get(7, 9), BLACK, 0.4))


def tiers_emblem(t: Tex):
    """Three nested squares: the three compression tiers."""
    t.outline(4, 4, 11, 11, STEEL[2])
    t.outline(5, 5, 10, 10, GUNMETAL[1])
    t.outline(6, 6, 9, 9, VIOLET[3])
    t.rect(7, 7, 8, 8, VIOLET[4])
    t.set(7, 7, VIOLET[5])


def vent_emblem(t: Tex):
    t.vents(4, 5, 11, 10, GUNMETAL)
    t.vline(3, 5, 10, GUNMETAL[1])
    t.vline(12, 5, 10, GUNMETAL[4])
    t.set(7, 12, CYAN[3])
    t.set(8, 12, CYAN[2])


def circuit_emblem(t: Tex):
    paths = [((3, 4), (7, 4), (7, 7)), ((12, 4), (9, 4), (9, 7)), ((3, 11), (6, 11), (6, 9)), ((12, 11), (9, 11), (9, 9))]
    for path in paths:
        for (ax, ay), (bx, by) in zip(path, path[1:]):
            t.line(ax, ay, bx, by, CYAN[1])
        t.set(*path[0], CYAN[3])
    t.rect(7, 7, 8, 8, VIOLET[3])
    t.set(7, 7, VIOLET[5])


def gunmetal_panel(width, height, seed):
    t = Tex(seed=seed)
    t.brushed(GUNMETAL, base=3)
    x1, y1 = width - 1, height - 1
    t.brushed(GUNMETAL, 0, 0, x1, y1, base=3)
    bevel_edges(t, 0, 0, x1, y1, STEEL[2], GUNMETAL[0], GUNMETAL[3])
    if height > 3:
        t.hline(1, x1 - 1, 1, GUNMETAL[4])
    return t


def controller_front():
    """The faceplate behind the controller's core ring: dark circuit board with glowing traces."""
    t = Tex(seed=79)
    t.noise(GUNMETAL, base=1, chance=0.3)
    traces = [((1, 3), (4, 3), (4, 5)), ((14, 3), (11, 3)), ((1, 7), (4, 7)), ((14, 7), (12, 7)),
              ((2, 11), (4, 11), (4, 14)), ((13, 11), (11, 11), (11, 14)), ((6, 1), (6, 3)), ((9, 1), (9, 3)),
              ((7, 11), (7, 14)), ((8, 11), (8, 14))]
    for path in traces:
        for (ax, ay), (bx, by) in zip(path, path[1:]):
            t.line(ax, ay, bx, by, CYAN[1])
        t.set(*path[-1], CYAN[2])
    for x, y in ((2, 3), (13, 7), (3, 11), (12, 11)):
        t.set(x, y, CYAN[3])
    # Soft violet halo around the core window.
    for y in range(3, 12):
        for x in range(4, 12):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 7)
            if d < 4.5:
                t.set(x, y, mix(t.get(x, y), VIOLET[2], max(0, 0.55 - d / 9)))
    return t


def controller_core():
    """A faceted omnium crystal: violet body, cyan facets, bright glints."""
    t = Tex(seed=83)
    for y in range(16):
        for x in range(16):
            k = (x + y) / 30
            c = mix(VIOLET[3], CYAN[3], k)
            if (x - y) % 6 == 0:
                c = mix(c, VIOLET[2], 0.6)
            if (x + 2 * y) % 9 == 0:
                c = mix(c, CYAN[4], 0.7)
            t.set(x, y, c)
    for x, y in ((4, 4), (11, 6), (6, 11), (12, 12)):
        t.set(x, y, CYAN[5])
    bevel_edges(t, 0, 0, 15, 15, VIOLET[5], VIOLET[1])
    return t


def trim_texture():
    """The face of a trim block: a walnut panel framed in steel with a small omnium boss."""
    t = Tex(seed=89)
    grain(t, WALNUT, 0, 0, 15, 15, vertical=True, plank=4, base=2, knots=1)
    bevel_edges(t, 2, 2, 13, 13, WALNUT[4], WALNUT[0], WALNUT[2])
    bevel_edges(t, 3, 3, 12, 12, WALNUT[1], WALNUT[3])
    t.rect(7, 7, 8, 8, STEEL[3])
    t.set(7, 7, STEEL[5])
    t.set(8, 8, STEEL[1])
    return t


# ---------------------------------------------------------------------------------------------
# Item sprites
# ---------------------------------------------------------------------------------------------


def upgrade_card(ramp, glow, lit, seed, window=None):
    """A drawer upgrade module: tier-metal card with a cut corner, a recessed glowing window, tier
    pips (1-4 lit) and gold edge contacts."""
    t = Tex(seed=seed)
    x0, y0, x1, y1 = 2, 1, 13, 13
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x - x1) + (y0 - y) > -3:  # cut top-right corner
                continue
            t.set(x, y, ramp[2])
    t.brushed(ramp, x0 + 1, y0 + 1, x1 - 1, y1 - 1, base=2)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x - x1) + (y0 - y) > -3:
                t.set(x, y, CLEAR)
    # Bevel: lit top/left, dark bottom/right, lit diagonal on the cut.
    t.hline(x0, x1 - 3, y0, ramp[5])
    t.vline(x0, y0, y1, ramp[4])
    t.hline(x0, x1, y1, ramp[0])
    t.vline(x1, y0 + 3, y1, ramp[1])
    for i in range(3):
        t.set(x1 - 2 + i, y0 + i, ramp[4])
    # Recessed window.
    wx0, wy0, wx1, wy1 = 4, 3, 11, 9
    t.rect(wx0, wy0, wx1, wy1, GUNMETAL[0])
    t.hline(wx0, wx1, wy0, BLACK)
    t.vline(wx0, wy0, wy1, BLACK)
    t.hline(wx0, wx1, wy1 + 1, ramp[4])
    t.vline(wx1 + 1, wy0, wy1 + 1, ramp[3])
    if window:
        window(t, wx0, wy0, wx1, wy1)
    else:
        # A bold up-arrow: more room inside.
        arrow = [(8, 4, 8), (7, 5, 9), (6, 6, 10), (7, 7, 9), (7, 8, 9), (7, 9, 9)]
        for xa, y, xb in arrow:
            t.hline(xa, xb, y, glow[4] if y < 7 else glow[3])
            t.set(xa, y, glow[5] if y < 7 else glow[4])
        t.set(8, 4, glow[5])
        t.hline(5, 11, 9, mix(t.get(5, 9), glow[1], 0.5))
        t.set(10, 6, glow[2])
    # Tier pips.
    for i, x in enumerate((4, 6, 8, 10)):
        t.set(x, 11, glow[4] if i < lit else GUNMETAL[1])
        t.set(x + 1, 11, glow[2] if i < lit else GUNMETAL[0])
    # Gold contacts along the bottom edge.
    for x in (4, 6, 8, 10):
        t.set(x, 14, GOLD[3])
        t.set(x + 1, 14, GOLD[1])
    outline_sprite(t, mix(BLACK, ramp[0], 0.3))
    return t


def vortex(t: Tex, x0, y0, x1, y1):
    cx, cy = (x0 + x1 + 1) / 2, (y0 + y1 + 1) / 2
    for y in range(y0 + 1, y1 + 1):
        for x in range(x0 + 1, x1 + 1):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            s = (math.sin(a * 2 + r * 1.9) + 1) / 2
            if r < 1.1:
                c = BLACK
            else:
                c = mix(VIOLET[1], VIOLET[4], s * min(1, r / 3.5))
                if s > 0.9:
                    c = mix(c, CYAN[4], 0.45)
            t.set(x, y, c)


def drawer_key():
    t = Tex(seed=97)
    rows = [
        '................',
        '..abbba.........',
        '.ab...ca........',
        'ab.vVv.cd.......',
        'b.vWVvv.d.......',
        'b.VVvvu.d.......',
        'b.vvvuu.d.......',
        'ac.vuu.cdA......',
        '.acc..cdAAd.....',
        '..addddAAd......',
        '.......dAAd.....',
        '........dAAd....',
        '.........dAAdG..',
        '..........dAAGg.',
        '...........dGGg.',
        '............gg..',
    ]
    paint(t, rows, {'a': STEEL[4], 'b': STEEL[3], 'c': STEEL[2], 'd': STEEL[1], 'A': STEEL[3],
                    'v': VIOLET[3], 'V': VIOLET[4], 'W': VIOLET[5], 'u': VIOLET[2],
                    'G': GOLD[3], 'g': GOLD[1]})
    # Polish the shaft: lit upper edge.
    for i in range(7, 14):
        if t.get(i + 1, i)[3]:
            t.set(i + 1, i, STEEL[4])
    outline_sprite(t, BLACK)
    return t


# ---------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------


def fbox(m: Model, u0, v0, u1, v1, z0, z1, **kw):
    """A box given in face pixels (u from the left, v from the top of the north-facing front) and depth
    (z from the front plane)."""
    return m.box((16 - u1, 16 - v1, z0), (16 - u0, 16 - v0, z1), **kw)


# Compartment fronts, mirroring DrawerLayout.java.
def single():
    return [dict(panel=(1.5, 1.5, 14.5, 14.5), kind='large', handle='cup', label=(4.5, 11.5, 11.5, 13.5), plate='large')]


def wide(top):
    return dict(panel=(1.5, top, 14.5, top + 5), kind='wide', handle='knobs', label=(5.5, top + 3.8, 10.5, top + 4.7), plate='wide')


def small(cu, top):
    return dict(panel=(cu - 2.5, top, cu + 2.5, top + 5), kind='small', handle='pull', label=(cu - 2, top + 3.85, cu + 2, top + 4.7),
                plate='small')


LAYOUTS = {
    'single': single(),
    'double': [wide(1.5), wide(9.5)],
    'quad': [small(4, 1.5), small(12, 1.5), small(4, 9.5), small(12, 9.5)],
    'compacting': [wide(1.5), small(4, 9.5), small(12, 9.5)],
}
PANEL_SIZE = {'large': (13, 13), 'wide': (13, 5), 'small': (5, 5)}
PLATE_UV = {'large': [0, 0, 7, 2], 'wide': [0, 4, 5, 5], 'small': [0, 6, 4, 7]}
BACK = ('south',)


def casing(m: Model, side, top, back, front='recess'):
    """The body behind the front frame."""
    m.box((0, 0, 1.5), (16, 16, 16), faces={'north': front, 'south': back, 'east': side, 'west': side, 'up': top, 'down': top},
          cull=True, name='casing')


def front_frame(m: Model, bolt=(0, 0, 2, 2)):
    """1px bevelled frame flush with the block sides, with protruding steel corner caps."""
    fbox(m, 2, 0, 14, 1, 0, 1.5, tex='frame', skip=BACK, cull=True, name='frame_top')
    fbox(m, 2, 15, 14, 16, 0, 1.5, tex='frame', skip=BACK, cull=True, name='frame_bottom')
    fbox(m, 0, 2, 1, 14, 0, 1.5, tex='frame', skip=BACK, cull=True, name='frame_left')
    fbox(m, 15, 2, 16, 14, 0, 1.5, tex='frame', skip=BACK, cull=True, name='frame_right')
    side_uv = [4, 4, 6, 6]
    for u, v in ((0, 0), (14, 0), (0, 14), (14, 14)):
        fbox(m, u, v, u + 2, v + 2, -0.5, 1.5, faces={'north': ('cap', list(bolt)), 'east': ('cap', side_uv),
                                                       'west': ('cap', side_uv), 'up': ('cap', side_uv),
                                                       'down': ('cap', side_uv)}, skip=BACK, name='corner_cap')


def dividers(m: Model, layout):
    if layout in ('double', 'quad', 'compacting'):
        fbox(m, 1, 7, 15, 9, 0.25, 1.5, tex='frame', skip=BACK + ('east', 'west'), name='divider')
    if layout == 'quad':
        fbox(m, 7, 1, 9, 7, 0.25, 1.5, tex='frame', skip=BACK + ('up', 'down'), name='divider')
        fbox(m, 7, 9, 9, 15, 0.25, 1.5, tex='frame', skip=BACK + ('up', 'down'), name='divider')
        # Centre boss with a glowing omnium jewel.
        fbox(m, 6.5, 6.5, 9.5, 9.5, -0.25, 1.5, faces={'north': ('cap', [0, 2, 3, 5]), 'east': ('cap', [4, 4, 5.75, 7]),
                                                       'west': ('cap', [4, 4, 5.75, 7]), 'up': ('cap', [4, 4, 7, 5.75]),
                                                       'down': ('cap', [4, 4, 7, 5.75])}, skip=BACK, name='boss')
        fbox(m, 7.5, 7.5, 8.5, 8.5, -0.4, -0.25, faces={f: ('glow', [9, 0, 10, 1]) for f in ('north', 'east', 'west', 'up', 'down')},
             skip=BACK, emissive=True, name='jewel')
    if layout == 'compacting':
        fbox(m, 7, 9, 9, 15, 0.25, 1.5, tex='frame', skip=BACK + ('up', 'down'), name='divider')
    if layout in ('double', 'compacting'):
        glow_row = 12 if layout == 'compacting' else 7
        fbox(m, 2.5, 7.75, 13.5, 8.25, 0.2, 0.25, faces={'north': ('glow', [0, glow_row, 16, glow_row + 1])},
             emissive=True, name='omnium_vein')
    if layout == 'compacting':
        fbox(m, 7.75, 9.75, 8.25, 14.25, 0.2, 0.25, faces={'north': ('glow', [15, 2, 16, 15])}, emissive=True, name='omnium_vein')


def bottom_vein(m: Model, row=7):
    fbox(m, 5, 15.25, 11, 15.75, -0.06, 0, faces={'north': ('glow', [3, row, 13, row + 1])}, emissive=True, name='omnium_vein')


def compartment(m: Model, c, panel_tex: dict):
    u0, v0, u1, v1 = c['panel']
    w, h = PANEL_SIZE[c['kind']]
    fbox(m, u0, v0, u1, v1, 1.0, 1.5, faces={'north': (panel_tex[c['kind']], [0, 0, w, h]), 'east': 'edge', 'west': 'edge',
                                             'up': 'edge', 'down': 'edge'}, skip=BACK, name='drawer_front')
    # Handles.
    if c['handle'] == 'cup':
        fbox(m, 6, 2.5, 10, 3.5, 0.25, 0.75, faces={'north': ('steel', [0, 1, 4, 2]), 'up': ('steel', [0, 2, 4, 2.5]),
                                                   'down': ('steel', [0, 5, 4, 5.5]), 'east': ('steel', [0, 3, 0.5, 4]),
                                                   'west': ('steel', [0, 3, 0.5, 4]), 'south': ('steel', [0, 4, 4, 5])},
             name='handle')
        for pu in (6, 9.25):
            fbox(m, pu, 2.6, pu + 0.75, 3.4, 0.75, 1.0, tex='steel', skip=BACK + ('north',), name='handle_post')
    elif c['handle'] == 'knobs':
        mid = (v0 + v1) / 2
        for ku in (u0 + 1.25, u1 - 2.25):
            fbox(m, ku - 0.25, mid - 0.75, ku + 1.25, mid + 0.75, 0.85, 1.0, faces={'north': ('cap', [4, 6, 5.5, 7.5])},
                 skip=BACK, name='knob_rose')
            fbox(m, ku, mid - 0.5, ku + 1, mid + 0.5, 0.35, 0.85, faces={'north': ('cap', [0, 0, 2, 2]), 'up': ('steel', [0, 1, 1, 1.5]),
                                                                         'down': ('steel', [0, 5, 1, 5.5]), 'east': ('steel', [0, 3, 0.5, 4]),
                                                                         'west': ('steel', [0, 2, 0.5, 3])}, skip=BACK, name='knob')
    elif c['handle'] == 'pull':
        cu = (u0 + u1) / 2
        fbox(m, cu - 1, v0 + 0.4, cu + 1, v0 + 0.9, 0.5, 1.0, faces={'north': ('steel', [0, 1, 2, 1.5]), 'up': ('steel', [0, 2, 2, 2.5]),
                                                                     'down': ('steel', [0, 5, 2, 5.5]), 'east': ('steel', [0, 3, 0.5, 3.5]),
                                                                     'west': ('steel', [0, 3, 0.5, 3.5])}, skip=BACK, name='pull')
    # Count plate.
    lu0, lv0, lu1, lv1 = c['label']
    fbox(m, lu0, lv0, lu1, lv1, 0.75, 1.0, faces={'north': ('label', PLATE_UV[c['plate']]), 'east': ('steel', [0, 3, 0.25, 4]),
                                                  'west': ('steel', [0, 3, 0.25, 4]), 'up': ('steel', [0, 2, 4, 2.25]),
                                                  'down': ('steel', [0, 5, 4, 5.25])}, skip=BACK, name='label_plate')


THEMES = {
    'walnut': dict(side=D + 'walnut_side', top=D + 'walnut_top', back=D + 'walnut_back', edge=D + 'walnut_top',
                   panels={'large': D + 'panel_large', 'wide': D + 'panel_wide', 'small': D + 'panel_small'}),
    'gunmetal': dict(side=D + 'compacting_side', top=D + 'compacting_top', back=D + 'compacting_side', edge=D + 'frame',
                     panels={'large': D + 'compacting_panel_wide', 'wide': D + 'compacting_panel_wide', 'small': D + 'compacting_panel_small'}),
}


def drawer_model(name, layout, theme):
    th = THEMES[theme]
    textures = {'particle': th['side'], 'side': th['side'], 'top': th['top'], 'back': th['back'], 'edge': th['edge'],
                'frame': D + 'frame', 'recess': D + 'recess', 'cap': D + 'cap', 'steel': D + 'steel', 'label': D + 'label',
                'glow': D + 'glow'}
    panel_tex = {}
    for kind, path in th['panels'].items():
        var = 'panel_' + kind
        textures[var] = path
        panel_tex[kind] = var
    m = Model(textures=textures)
    casing(m, 'side', 'top', 'back')
    front_frame(m)
    dividers(m, layout)
    for c in LAYOUTS[layout]:
        compartment(m, c, panel_tex)
    bottom_vein(m, row=12 if theme == 'gunmetal' else 7)
    m.save(f'block/{name}')
    return m


def controller_model():
    m = Model(textures={'particle': D + 'controller_side', 'side': D + 'controller_side', 'top': D + 'controller_top',
                        'face': D + 'controller_front', 'frame': D + 'frame', 'cap': D + 'cap', 'steel': D + 'steel',
                        'glow': D + 'glow', 'core': D + 'controller_core'})
    casing(m, 'side', 'top', 'side', front='face')
    front_frame(m, bolt=(2, 0, 4, 2))
    # Steel ring around the core.
    across = dict(faces={'north': ('steel', [0, 1, 7, 2]), 'up': ('steel', [0, 2, 7, 3]), 'down': ('steel', [0, 5, 7, 6]),
                         'east': ('steel', [0, 3, 1, 4]), 'west': ('steel', [0, 3, 1, 4])}, skip=BACK)
    upright = dict(faces={'north': ('steel', [7, 0, 8, 6]), 'east': ('steel', [8, 0, 9, 6]), 'west': ('steel', [9, 0, 10, 6])},
                   skip=BACK + ('up', 'down'))
    fbox(m, 4.5, 3, 11.5, 4, 0.5, 1.5, name='ring', **across)
    fbox(m, 4.5, 10, 11.5, 11, 0.5, 1.5, name='ring', **across)
    fbox(m, 4.5, 4, 5.5, 10, 0.5, 1.5, name='ring', **upright)
    fbox(m, 10.5, 4, 11.5, 10, 0.5, 1.5, name='ring', **upright)
    # The crystal, turned 45 degrees into a diamond (the faceplate paints its halo).
    m.box((6.5, 7.5, 0.25), (9.5, 10.5, 1.4), tex='core', rotation=(45, 'z', (8, 9, 0.8)), skip=BACK, emissive=True, name='core')
    # Indicator plate with four status lights.
    fbox(m, 3.5, 11.75, 12.5, 13.75, 1.25, 1.5, faces={'north': ('steel', [0, 3, 9, 5]), 'up': ('steel', [0, 2, 9, 2.25]),
                                                       'east': ('steel', [0, 3, 0.25, 5]), 'west': ('steel', [0, 3, 0.25, 5]),
                                                       'down': ('steel', [0, 5, 9, 5.25])}, skip=BACK, name='status_plate')
    for i, u in enumerate((4.5, 6.5, 8.5, 10.5)):
        col = (0, 4, 4, 8)[i]
        fbox(m, u, 12.25, u + 1, 13.25, 1.0, 1.25, faces={f: ('glow', [col + 1, 0, col + 2, 1]) for f in ('north', 'east', 'west', 'up', 'down')},
             skip=BACK, emissive=True, name='status_light')
    bottom_vein(m)
    m.save('block/drawer_controller')
    return m


def trim_model():
    m = Model(textures={'particle': D + 'trim', 'face': D + 'trim', 'frame': D + 'frame', 'cap': D + 'cap'})
    m.box((0.5, 0.5, 0.5), (15.5, 15.5, 15.5), tex='face', name='panel')
    lo, hi = (0, 1), (15, 16)
    for a in (lo, hi):
        for b in (lo, hi):
            m.box((2, a[0], b[0]), (14, a[1], b[1]), tex='frame', skip=('east', 'west'), cull=True, name='edge')
            m.box((a[0], 2, b[0]), (a[1], 14, b[1]), tex='frame', skip=('up', 'down'), cull=True, name='edge')
            m.box((a[0], b[0], 2), (a[1], b[1], 14), tex='frame', skip=('north', 'south'), cull=True, name='edge')
    for x in (0, 14):
        for y in (0, 14):
            for z in (0, 14):
                m.box((x, y, z), (x + 2, y + 2, z + 2), faces={f: ('cap', [0, 0, 2, 2]) for f in ('north', 'south', 'east', 'west', 'up', 'down')},
                      cull=True, name='corner_cap')
    m.save('block/drawer_trim')
    return m


# ---------------------------------------------------------------------------------------------
# Data
# ---------------------------------------------------------------------------------------------

DRAWER_COMPONENTS = ['minecraft:custom_name', f'{MOD}:drawer_contents', f'{MOD}:compacting_contents', f'{MOD}:drawer_locked',
                     f'{MOD}:drawer_upgrades']


def loot_drawer(name):
    """Drops itself with its contents, lock and upgrades. No explosion check: contents are never lost."""
    return write_json(DATA / 'loot_table' / 'blocks' / (name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1, 'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:{name}', 'functions': [
                {'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': DRAWER_COMPONENTS}]}],
        }],
        'random_sequence': f'{MOD}:blocks/{name}',
    })


def merge_tag(namespace, registry, name, values):
    """Adds values to a tag file, keeping whatever other features already put there."""
    path = RESOURCES / 'data' / namespace / 'tags' / registry / (name + '.json')
    current = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace': False, 'values': []}
    merged = list(current.get('values', []))
    for value in values:
        if value not in merged:
            merged.append(value)
    return write_json(path, {'replace': current.get('replace', False), 'values': merged})


WOOD_DRAWERS = ['drawer_1x1', 'drawer_1x2', 'drawer_2x2']
MACHINE_DRAWERS = ['compacting_drawer']
CAPACITY_UPGRADES = [('iron', IRON, CYAN, 1), ('gold', GOLD, AMBER, 2), ('diamond', DIAMOND, CYAN, 3), ('netherite', NETHERITE, VIOLET, 4)]


def recipes():
    om = MOD
    shaped('drawer_1x1', ['PPP', 'NCN', 'PPP'], {'P': '#minecraft:planks', 'N': 'minecraft:iron_nugget', 'C': '#c:chests/wooden'})
    shaped('drawer_1x2', ['PCP', 'NPN', 'PCP'], {'P': '#minecraft:planks', 'N': 'minecraft:iron_nugget', 'C': '#c:chests/wooden'}, count=2)
    shaped('drawer_2x2', ['CPC', 'NPN', 'CPC'], {'P': '#minecraft:planks', 'N': 'minecraft:iron_nugget', 'C': '#c:chests/wooden'}, count=4)
    shaped('compacting_drawer', ['III', 'PDP', 'ILI'], {'I': 'minecraft:iron_ingot', 'P': 'minecraft:piston',
                                                        'D': f'{om}:drawer_1x1', 'L': f'{om}:logic_processor'})
    shaped('drawer_controller', ['IOI', 'CDC', 'ILI'], {'I': 'minecraft:iron_ingot', 'O': f'{om}:omnium_ingot', 'C': 'minecraft:comparator',
                                                        'D': f'#{om}:drawers', 'L': f'{om}:logic_processor'})
    shaped('drawer_trim', ['SPS', 'PNP', 'SPS'], {'S': 'minecraft:stick', 'P': '#minecraft:planks', 'N': 'minecraft:iron_nugget'}, count=4)
    shaped('drawer_key', ['O', 'I', 'N'], {'O': f'{om}:omnium_ingot', 'I': 'minecraft:iron_ingot', 'N': 'minecraft:gold_nugget'})
    shaped('iron_drawer_upgrade', ['SIS', 'IUI', 'SIS'], {'S': 'minecraft:stick', 'I': 'minecraft:iron_ingot', 'U': f'{om}:upgrade_base'})
    shaped('gold_drawer_upgrade', ['SGS', 'GUG', 'SGS'], {'S': 'minecraft:stick', 'G': 'minecraft:gold_ingot', 'U': f'{om}:iron_drawer_upgrade'})
    shaped('diamond_drawer_upgrade', ['SDS', 'DUD', 'SDS'], {'S': 'minecraft:stick', 'D': 'minecraft:diamond', 'U': f'{om}:gold_drawer_upgrade'})
    shapeless('netherite_drawer_upgrade', [f'{om}:diamond_drawer_upgrade', 'minecraft:netherite_ingot'])
    shaped('void_drawer_upgrade', ['SOS', 'OUO', 'SES'], {'S': 'minecraft:stick', 'O': 'minecraft:obsidian', 'E': 'minecraft:ender_pearl',
                                                          'U': f'{om}:upgrade_base'})


def main():
    # Textures ---------------------------------------------------------------------------------
    walnut_casing(13, vertical=True).save(D + 'walnut_side')
    walnut_casing(17, vertical=True, straps=True).save(D + 'walnut_top')
    walnut_back().save(D + 'walnut_back')
    frame_texture().save(D + 'frame')
    recess_texture().save(D + 'recess')
    walnut_panel(13, 13, 101, groove=True).save(D + 'panel_large')
    walnut_panel(13, 5, 103, groove=False).save(D + 'panel_wide')
    walnut_panel(5, 5, 107, groove=False).save(D + 'panel_small')
    steel_texture().save(D + 'steel')
    cap_texture().save(D + 'cap')
    label_texture().save(D + 'label')
    glow_texture().save(D + 'glow')
    lock_icon().save(D + 'lock_icon')
    void_icon().save(D + 'void_icon')
    gunmetal_casing(109, compression_emblem).save(D + 'compacting_side')
    gunmetal_casing(113, tiers_emblem).save(D + 'compacting_top')
    gunmetal_panel(13, 5, 127).save(D + 'compacting_panel_wide')
    gunmetal_panel(5, 5, 131).save(D + 'compacting_panel_small')
    gunmetal_casing(137, vent_emblem).save(D + 'controller_side')
    gunmetal_casing(139, circuit_emblem).save(D + 'controller_top')
    controller_front().save(D + 'controller_front')
    controller_core().save(D + 'controller_core')
    trim_texture().save(D + 'trim')

    for tier, ramp, glow, lit in CAPACITY_UPGRADES:
        upgrade_card(ramp, glow, lit, seed=150 + lit).save(f'item/{tier}_drawer_upgrade')
    upgrade_card(GUNMETAL, VIOLET, 0, seed=160, window=vortex).save('item/void_drawer_upgrade')
    drawer_key().save('item/drawer_key')

    # Models, blockstates, item models ---------------------------------------------------------
    for name, layout in (('drawer_1x1', 'single'), ('drawer_1x2', 'double'), ('drawer_2x2', 'quad')):
        drawer_model(name, layout, 'walnut')
    drawer_model('compacting_drawer', 'compacting', 'gunmetal')
    controller_model()
    trim_model()
    for name in WOOD_DRAWERS + MACHINE_DRAWERS + ['drawer_controller']:
        blockstate_horizontal(name)
        item_model(name, parent=f'block/{name}')
    blockstate_single('drawer_trim')
    item_model('drawer_trim', parent='block/drawer_trim')
    for name in ['drawer_key', 'void_drawer_upgrade'] + [f'{tier}_drawer_upgrade' for tier, *_ in CAPACITY_UPGRADES]:
        item_model(name)

    # Data ---------------------------------------------------------------------------------------
    for name in WOOD_DRAWERS + MACHINE_DRAWERS:
        loot_drawer(name)
    loot_self('drawer_controller')
    loot_self('drawer_trim')
    recipes()
    merge_tag('minecraft', 'block', 'mineable/axe', [f'{MOD}:{n}' for n in WOOD_DRAWERS + ['drawer_trim']])
    merge_tag('minecraft', 'block', 'mineable/pickaxe', [f'{MOD}:{n}' for n in MACHINE_DRAWERS + ['drawer_controller']])
    merge_tag(MOD, 'block', 'drawers', [f'{MOD}:{n}' for n in WOOD_DRAWERS + MACHINE_DRAWERS])
    merge_tag(MOD, 'item', 'drawers', [f'{MOD}:{n}' for n in WOOD_DRAWERS])
    print('Drawer assets written.')


if __name__ == '__main__':
    main()
