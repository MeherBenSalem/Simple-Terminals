#!/usr/bin/env python3
"""Backpack art and data: per-tier 3D backpack models and textures, upgrade card sprites, blockstates,
item models, recipes, loot tables and tags.

One multi-element model (main body, lid, flap with buckle, front pocket with an omnium-gem clasp, side
pockets, quilted back padding, shoulder straps, top handle and tier-coloured metal trim) is shared by the
item, the placed block and the backpack worn on the player's back. Every tier gets its own hand-shaded
16x16 sheets (fabric, front, lid, strap, trim, back) painted procedurally from the kit palette.

Usage (from the repository root): python3 tools/assets/backpacks.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from kit import (AMBER, ASSETS, BLACK, CLEAR, COPPER, CYAN, DATA, DIAMOND, GOLD, GREEN, GUNMETAL, IRON, MOD,  # noqa: E402
                 NETHERITE, RED, STEEL, VIOLET, Model, Tex, blockstate_horizontal, item_model, mix, rgb,
                 save_animation, shade, shaped, smithing, tag, write_json)

# =============================================================================================
# Palettes
# =============================================================================================


def ramp(*hexes):
    return [rgb(h) for h in hexes]


TIERS = ['leather', 'copper', 'iron', 'gold', 'diamond', 'netherite']

# fabric: the bag itself; strap: leather straps and handle; trim: metal fittings; stitch: thread colour.
PALETTES = {
    # Leather is dyeable: its fabric is a light neutral ramp that the dye colour (default leather brown) tints.
    'leather': dict(
        fabric=ramp('6b6560', '8c857f', 'aca59f', 'c8c1bb', 'e0dad4', 'f5f0eb'),
        strap=ramp('22140c', '3a2314', '553420', '6e4630', '8a5c40', 'a87656'),
        trim=ramp('1c1d20', '33353a', '4d5057', '6a6e77', '8a8f98', 'b0b5bd'),
        stitch=rgb('f7f1e6'), tinted=True),
    'copper': dict(
        fabric=ramp('3a2416', '573822', '74502f', '92683f', 'ad8253', 'c79e6c'),
        strap=ramp('1f120a', '341f12', '4c2e1b', '643f27', '7d5236', '966748'),
        trim=COPPER, stitch=rgb('ecd5a8'), tinted=False),
    'iron': dict(
        fabric=ramp('1e252e', '2b3541', '3c4857', '4f5d6f', '667689', '8292a5'),
        strap=ramp('16130f', '29231c', '3d3429', '52463a', '68594b', '7f6e5f'),
        trim=IRON, stitch=rgb('d2dae4'), tinted=False),
    'gold': dict(
        fabric=ramp('2a080e', '450e18', '631623', '80202f', '9d3240', 'b94b58'),
        strap=ramp('1a0f08', '2e1c10', '45291a', '5c3825', '744a32', '8d5e42'),
        trim=GOLD, stitch=rgb('f5c542'), tinted=False),
    'diamond': dict(
        fabric=ramp('08202a', '0e3240', '164757', '1f5e70', '2c7889', '3f94a5'),
        strap=ramp('0a1118', '131e29', '1d2c3b', '283b4e', '354c63', '445f7a'),
        trim=DIAMOND, stitch=rgb('a3fff8'), tinted=False),
    'netherite': dict(
        fabric=ramp('0c0a0d', '161319', '211c25', '2c2631', '39323f', '47404e'),
        strap=ramp('070608', '100e11', '1a171c', '252128', '312c35', '3e3843'),
        trim=NETHERITE, stitch=rgb('ff7a33'), tinted=False),
}

SEEDS = {name: 1000 + i * 97 for i, name in enumerate(TIERS)}


# =============================================================================================
# Painting helpers (pixel coordinates are inclusive)
# =============================================================================================


BAYER = ((0.125, 0.625), (0.875, 0.375))


def dithered(ramp_, value, x, y):
    """Quantises a fractional ramp position with a 2x2 ordered dither, the classic hand-shaded gradient."""
    base = int(value // 1)
    if value - base > BAYER[y % 2][x % 2]:
        base += 1
    return ramp_[max(0, min(len(ramp_) - 1, base))]


def cloth(t: Tex, x0, y0, x1, y1, ramp_, top=3.0, bottom=1.9, twill=0.22, fleck=0.05, seed_shift=0):
    """Woven fabric: a dithered top-to-bottom light falloff, a faint diagonal twill and the odd fleck."""
    height = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            value = top + (bottom - top) * (y - y0) / height
            if (x + 2 * y + seed_shift) % 4 == 0:
                value += twill
            color = dithered(ramp_, value, x, y)
            if t.rng.random() < fleck:
                color = mix(color, ramp_[min(5, int(value) + 1)], 0.5)
            t.set(x, y, color)
    return t


def grain(t: Tex, x0, y0, x1, y1, ramp_, base=2.0, spread=0.35):
    """Leather grain: mostly the base tone with soft pebbling."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t.set(x, y, dithered(ramp_, base + t.rng.uniform(-spread, spread), x, y))
    return t


def outline(t: Tex, x0, y0, x1, y1, ramp_, light, dark, top=True, left=True, bottom=True, right=True):
    """A panel's silhouette: lit top edge, softer left edge, shadowed right and bottom edges."""
    if left:
        t.vline(x0, y0, y1, ramp_[max(0, light - 1)])
    if right:
        t.vline(x1, y0, y1, ramp_[dark + 1 if dark + 1 < light else dark])
    if top:
        t.hline(x0, x1, y0, ramp_[light])
    if bottom:
        t.hline(x0, x1, y1, ramp_[dark])
    return t


def seam(t: Tex, points, thread_color, hole=None):
    """A running stitch: thread on every other pixel; between stitches the fabric shows (or a needle hole)."""
    for i, (x, y) in enumerate(points):
        if i % 2 == 0:
            t.set(x, y, thread_color)
        elif hole is not None:
            t.set(x, y, mix(t.get(x, y), hole, 0.5))
    return t


def h(x0, x1, y):
    return [(x, y) for x in range(x0, x1 + 1)]


def v(x, y0, y1):
    return [(x, y) for y in range(y0, y1 + 1)]


def thread(pal, ramp_):
    """Thread colour, blended a little into the fabric so seams read without shouting."""
    return mix(pal['stitch'], ramp_[3], 0.5)


def bevel_tile(t: Tex, x0, y0, x1, y1, ramp_, base=3):
    """A small metal fitting: bright top-left, shadowed bottom-right, a glint in the corner."""
    t.rect(x0, y0, x1, y1, ramp_[base])
    t.hline(x0, x1, y0, ramp_[min(5, base + 1)])
    t.vline(x0, y0, y1, ramp_[min(5, base + 1)])
    t.hline(x0, x1, y1, ramp_[max(0, base - 2)])
    t.vline(x1, y0, y1, ramp_[max(0, base - 2)])
    t.set(x0, y0, ramp_[5])
    return t


# =============================================================================================
# Texture sheets. Every region below is referenced by exact UVs in the model.
# =============================================================================================

# body sheet
B_FRONT = (0, 0, 10, 11)      # body front/back, 10x11
B_SIDE = (10, 0, 16, 11)      # body sides, 6x11
B_BOTTOM = (0, 11, 10, 16)    # body underside
B_SMALL = (10, 11, 16, 16)    # plain fabric for small faces

# front sheet
F_FLAP = (0, 0, 9, 3.5)       # main flap front
F_FLAP_EDGE = (9, 0, 10, 3.5)  # flap side thickness
F_FLAP_UNDER = (0, 4, 9, 4.75)
F_POCKET = (0, 5, 8, 10)      # front pocket face, 8x5
F_POCKET_SIDE = (10, 5, 12, 10)
F_POCKET_BOTTOM = (0, 15, 8, 16)
F_PFLAP = (0, 10, 8.5, 12.5)  # pocket flap front
F_PFLAP_TOP = (0, 12.5, 8.5, 14.9)
F_PFLAP_SIDE = (9, 10, 11.4, 12.5)

# lid sheet
L_TOP = (0, 0, 11, 7)
L_FRONT = (0, 7, 11, 8.5)
L_BACK = (0, 9, 11, 10.5)
L_SIDE = (0, 11, 7, 12.5)
L_UNDER = (0, 13, 11, 16)
L_SPOCKET = (11, 0, 15, 5.5)   # side pocket outer face, 4x5.5
L_SPOCKET_EDGE = (15, 0, 16, 5.5)
L_SFLAP = (11, 6, 15.5, 7.75)  # side pocket flap outer face
L_SFLAP_TOP = (11, 8, 15.5, 9.25)
L_SFLAP_EDGE = (11, 10, 12.5, 11.75)

# strap sheet
S_LEFT = (0, 0, 2, 10.5)
S_RIGHT = (4, 0, 6, 10.5)
S_EDGE = (2, 0, 2.75, 10.5)
S_TOP = (8, 0, 10, 2.75)       # strap over the lid, seen from above
S_FOLD = (8, 3, 10, 4.4)
S_HANDLE = (0, 12, 4, 12.75)   # handle bar, long faces
S_HANDLE_TOP = (0, 13, 4, 14)
S_POST = (12, 0, 12.75, 1.5)

# trim sheet
T_BAND = (0, 0, 11, 1.5)
T_BAND_SIDE = (0, 2, 7, 3.5)
T_PLATE = (0, 4, 11, 11)
T_BUCKLE = (12, 0, 14.5, 2.5)
T_BEZEL = (12, 4, 14.5, 6.25)
T_CAP = (12, 8, 14, 10)
T_SMALL = (14, 8, 16, 10)
T_THIN = (0, 12, 11.25, 12.5)
T_THIN_SIDE = (0, 13, 7.25, 13.5)

# back sheet
K_PAD = (0, 0, 9, 9)
K_EDGE = (9, 0, 10, 9)
K_TOPEDGE = (0, 9, 9, 10)

# gem (animated, emissive)
G_FACE = (5, 5, 11, 11)
G_SIDE = (6, 6, 10, 8)


def paint_body(pal, seed):
    f = pal['fabric']
    t = Tex(16, seed=seed)
    st = thread(pal, f)
    # Front/back panel (mostly behind the flap, pocket and padding): seams down both sides and a waist seam.
    cloth(t, 0, 0, 9, 10, f, top=2.9, bottom=1.7)
    outline(t, 0, 0, 9, 10, f, light=4, dark=0)
    seam(t, v(1, 1, 9), st, f[1])
    seam(t, v(8, 1, 9), st, f[1])
    t.hline(1, 8, 4, f[1])
    seam(t, h(1, 8, 5), st, f[2])
    # Side panel: a gusset fold near the top, a vertical seam and a worn highlight.
    cloth(t, 10, 0, 15, 10, f, top=2.8, bottom=1.6, seed_shift=1)
    outline(t, 10, 0, 15, 10, f, light=4, dark=0)
    t.hline(11, 14, 2, f[4])
    t.hline(11, 14, 3, f[1])
    t.vline(13, 4, 9, f[1])
    seam(t, v(12, 4, 9), st, f[2])
    t.set(11, 6, f[3])
    t.set(11, 7, f[3])
    # Underside and a plain swatch for tiny faces.
    grain(t, 0, 11, 9, 15, f, base=1.2)
    cloth(t, 10, 11, 15, 15, f, top=2.6, bottom=2.0, twill=0.1, fleck=0)
    return t


def paint_front(pal, seed):
    f = pal['fabric']
    t = Tex(16, seed=seed)
    grain(t, 0, 0, 15, 15, f, base=2.0)
    st = thread(pal, f)
    # Main flap: lit fold along the top, stitched border, darker rolled hem.
    cloth(t, 0, 0, 8, 3, f, top=3.7, bottom=2.9, twill=0.15)
    t.hline(0, 8, 0, f[5])
    t.set(0, 0, f[4])
    t.set(8, 0, f[4])
    seam(t, v(1, 1, 2), st, f[2])
    seam(t, v(7, 1, 2), st, f[2])
    seam(t, h(1, 7, 2), st, f[2])
    t.hline(0, 8, 3, f[1])
    t.set(0, 3, f[0])
    t.set(8, 3, f[0])
    t.rect(9, 0, 9, 3, f[2])
    t.set(9, 0, f[3])
    t.hline(0, 8, 4, f[0])
    # Front pocket: puffed centre, two creases, stitched outline, shadow under its flap.
    cloth(t, 0, 5, 7, 9, f, top=2.7, bottom=1.6, seed_shift=2)
    for y in range(6, 9):
        t.set(3, y, mix(t.get(3, y), f[4], 0.35))
        t.set(4, y, mix(t.get(4, y), f[4], 0.2))
    outline(t, 0, 5, 7, 9, f, light=3, dark=0, top=False)
    t.hline(0, 7, 5, f[0])
    t.hline(1, 6, 6, f[1])
    seam(t, v(1, 7, 8), st, f[1])
    seam(t, v(6, 7, 8), st, f[1])
    seam(t, h(2, 5, 8), st, f[1])
    t.rect(10, 5, 11, 9, f[1])
    t.vline(10, 5, 9, f[2])
    t.hline(0, 7, 15, f[0])
    # Pocket flap: a rounded hem, a stitched edge and a darker underside.
    cloth(t, 0, 10, 8, 12, f, top=3.9, bottom=3.0, twill=0.1, fleck=0.02)
    t.hline(0, 8, 10, f[4])
    seam(t, h(1, 7, 11), st, f[3])
    t.hline(1, 7, 12, f[1])
    t.set(0, 12, f[0])
    t.set(8, 12, f[0])
    t.set(0, 11, f[2])
    t.set(8, 11, f[2])
    cloth(t, 0, 13, 8, 14, f, top=4.3, bottom=3.6, twill=0.1, fleck=0)
    seam(t, h(0, 8, 14), st, f[3])
    t.rect(9, 10, 11, 12, f[2])
    t.vline(9, 10, 12, f[3])
    return t


def paint_lid(pal, seed):
    f = pal['fabric']
    t = Tex(16, seed=seed)
    grain(t, 0, 0, 15, 15, f, base=2.2)
    st = thread(pal, f)
    # Lid top: brightest surface, a stitched inset panel, a rolled front edge.
    cloth(t, 0, 0, 10, 6, f, top=3.9, bottom=3.3, twill=0.15, fleck=0.03)
    outline(t, 0, 0, 10, 6, f, light=5, dark=2)
    seam(t, h(1, 9, 1), st, f[3])
    seam(t, h(1, 9, 5), st, f[2])
    seam(t, v(1, 2, 4), st, f[3])
    seam(t, v(9, 2, 4), st, f[2])
    t.hline(3, 7, 3, mix(f[4], f[5], 0.3))
    # Lid edges.
    t.rect(0, 7, 10, 8, f[3])
    t.hline(0, 10, 7, f[4])
    seam(t, h(0, 10, 8), st, f[2])
    t.rect(0, 9, 10, 10, f[2])
    t.hline(0, 10, 9, f[3])
    t.rect(0, 11, 6, 12, f[3])
    t.hline(0, 6, 11, f[4])
    t.hline(0, 6, 12, f[2])
    t.rect(0, 13, 10, 15, f[0])
    t.hline(0, 10, 13, f[1])
    # Side pocket (outer face): shaded under its flap, a highlight down the bulge, stitched sides.
    cloth(t, 11, 0, 14, 5, f, top=2.4, bottom=1.7, twill=0.1)
    t.hline(11, 14, 0, f[0])
    t.vline(12, 1, 4, mix(f[3], f[4], 0.4))
    outline(t, 11, 0, 14, 5, f, light=3, dark=0, top=False)
    seam(t, h(12, 13, 4), st, f[1])
    t.rect(15, 0, 15, 5, f[1])
    # Side pocket flap.
    cloth(t, 11, 6, 15, 7, f, top=3.8, bottom=3.0, twill=0.1, fleck=0)
    t.hline(11, 15, 7, f[1])
    seam(t, h(11, 15, 6), st, f[4])
    t.rect(11, 8, 15, 9, f[4])
    t.hline(11, 15, 9, f[3])
    t.rect(11, 10, 12, 11, f[2])
    return t


def paint_strap(pal, seed):
    s = pal['strap']
    t = Tex(16, seed=seed)
    grain(t, 0, 0, 15, 15, s, base=2.2)
    st = mix(pal['stitch'], s[3], 0.5)
    for x0 in (0, 4):
        t.vline(x0, 0, 10, s[3])
        t.vline(x0 + 1, 0, 10, s[2])
        seam(t, v(x0, 1, 5), st, s[3])
        # Adjustment holes near the bottom, each with a lit lower lip.
        for y in (7, 9):
            t.set(x0, y, s[0])
            t.set(x0 + 1, y, s[1])
            t.set(x0, y + 1, s[4])
        t.set(x0, 0, s[4])
        t.set(x0 + 1, 0, s[3])
    t.rect(2, 0, 2, 10, s[1])
    t.rect(8, 0, 9, 2, s[3])
    t.vline(8, 0, 2, s[4])
    t.rect(8, 3, 9, 4, s[2])
    t.hline(8, 9, 4, s[0])
    # Handle grip: lit top, wrapped with thread.
    t.rect(0, 12, 3, 12, s[3])
    t.set(1, 12, st)
    t.set(3, 12, st)
    t.rect(0, 13, 3, 13, s[4])
    t.rect(12, 0, 12, 1, s[2])
    return t


def paint_trim(pal, seed):
    m = pal['trim']
    t = Tex(16, seed=seed)
    t.brushed(m, 0, 0, 15, 15, base=2)
    # Long rim band with rivets.
    for y0 in (0, 2):
        t.hline(0, 15, y0, m[4])
        t.hline(0, 15, y0 + 1, m[2])
        for x in range(1, 16, 4):
            t.set(x, y0 + 1, m[5])
            t.set(x + 1, y0 + 1, m[1])
    t.hline(0, 15, 3, m[1])
    # Brushed plate (rim top ring).
    t.brushed(m, 0, 4, 11, 11, base=3)
    t.hline(0, 11, 4, m[5])
    # Buckle: bright frame around a dark hole.
    bevel_tile(t, 12, 0, 14, 2, m, base=3)
    t.set(13, 1, pal['strap'][0])
    # Gem bezel: a ring.
    bevel_tile(t, 12, 4, 14, 6, m, base=3)
    t.set(13, 5, m[0])
    # Corner caps and small fittings.
    bevel_tile(t, 12, 8, 13, 9, m, base=3)
    bevel_tile(t, 14, 8, 15, 9, m, base=2)
    # Thin bands.
    t.hline(0, 15, 12, m[4])
    t.hline(0, 15, 13, m[3])
    return t


def paint_back(pal, seed):
    f = pal['fabric']
    t = Tex(16, seed=seed)
    grain(t, 0, 0, 15, 15, f, base=1.6)
    # Quilted padding: diamond seams with a puff of light in each cell.
    for y in range(0, 9):
        for x in range(0, 9):
            d = (x + y) % 4
            e = (x - y) % 4
            if d == 0 or e == 0:
                t.set(x, y, f[1])
            elif d == 2 and e == 2:
                t.set(x, y, f[4])
            elif d == 1 or e == 1:
                t.set(x, y, f[3])
            else:
                t.set(x, y, f[2])
    t.hline(0, 8, 0, f[4])
    t.hline(0, 8, 8, f[0])
    t.rect(9, 0, 9, 8, f[1])
    t.rect(0, 9, 8, 9, f[2])
    return t


def paint_gem_frames():
    """An omnium gem: violet facets around a cyan-white core. Eight frames of a slow pulse."""
    frames = []
    for i in range(8):
        pulse = 0.5 + 0.5 * math.cos(i / 8 * 2 * math.pi)
        t = Tex(16, seed=7)
        t.radial(8, 8, 11.5, VIOLET[4], VIOLET[1])
        # Facets.
        for y in range(16):
            for x in range(16):
                if (x + y) % 5 == 0:
                    t.set(x, y, mix(t.get(x, y), VIOLET[2], 0.5))
                if (x - y) % 5 == 0:
                    t.set(x, y, mix(t.get(x, y), VIOLET[5], 0.25))
        t.radial(8, 8, 3.5 + pulse, mix(CYAN[5], VIOLET[5], 0.2), VIOLET[3])
        for (x, y) in ((6, 6), (7, 6), (6, 7)):
            t.set(x, y, mix(CYAN[5], (255, 255, 255, 255), 0.5))
        t.set(9, 9, VIOLET[4])
        frames.append(t)
    return frames


# =============================================================================================
# The model
# =============================================================================================


def backpack_model(dyeable: bool) -> Model:
    """The backpack, front facing north. Faces map to exact regions of the sheets painted above."""
    m = Model(parent='block/block', ambient_occlusion=False)
    tint = 0 if dyeable else None

    def box(f, t, faces, tinted=False, **kw):
        m.box(f, t, faces=faces, tint=tint if tinted else None, **kw)

    def uv(tex, region):
        return (tex, list(region))

    # Metal base rim the bag sits on.
    box((2.5, 0, 5.5), (13.5, 1.5, 12.5), {
        'north': uv('trim', T_BAND), 'south': uv('trim', T_BAND),
        'west': uv('trim', T_BAND_SIDE), 'east': uv('trim', T_BAND_SIDE),
        'up': uv('trim', T_PLATE), 'down': uv('trim', T_PLATE)}, name='base_rim')
    # Main body.
    box((3, 1, 6), (13, 12, 12), {
        'north': uv('body', B_FRONT), 'south': uv('body', B_FRONT),
        'west': uv('body', B_SIDE), 'east': uv('body', B_SIDE)}, tinted=True, name='body')
    # Lid with a metal band round its hem.
    box((2.5, 11.5, 5), (13.5, 13, 12), {
        'up': uv('lid', L_TOP), 'north': uv('lid', L_FRONT), 'south': uv('lid', L_BACK),
        'west': uv('lid', L_SIDE), 'east': uv('lid', L_SIDE), 'down': uv('lid', L_UNDER)}, tinted=True, name='lid')
    box((2.4, 11.25, 4.9), (13.6, 11.75, 12.1), {
        'north': uv('trim', T_THIN), 'south': uv('trim', T_THIN),
        'west': uv('trim', T_THIN_SIDE), 'east': uv('trim', T_THIN_SIDE),
        'down': uv('trim', T_PLATE)}, name='lid_band')
    # Main flap hanging over the front, closed with a buckle.
    box((3.5, 8, 5.25), (12.5, 11.5, 6), {
        'north': uv('front', F_FLAP), 'west': uv('front', F_FLAP_EDGE), 'east': uv('front', F_FLAP_EDGE),
        'down': uv('front', F_FLAP_UNDER)}, tinted=True, name='flap')
    box((6.75, 6.75, 4.85), (9.25, 9.25, 5.25), {
        'north': uv('trim', T_BUCKLE), 'west': uv('trim', T_SMALL), 'east': uv('trim', T_SMALL),
        'up': uv('trim', T_SMALL), 'down': uv('trim', T_SMALL)}, name='buckle')
    # Front pocket with its own flap; an omnium gem in a metal bezel is the clasp.
    box((4, 1.5, 4), (12, 6.5, 6), {
        'north': uv('front', F_POCKET), 'west': uv('front', F_POCKET_SIDE), 'east': uv('front', F_POCKET_SIDE),
        'down': uv('front', F_POCKET_BOTTOM)}, tinted=True, name='pocket')
    box((3.75, 4.5, 3.6), (12.25, 7, 6), {
        'north': uv('front', F_PFLAP), 'up': uv('front', F_PFLAP_TOP),
        'west': uv('front', F_PFLAP_SIDE), 'east': uv('front', F_PFLAP_SIDE), 'down': uv('front', F_FLAP_UNDER)},
        tinted=True, name='pocket_flap')
    box((6.75, 3.75, 3.25), (9.25, 6, 3.6), {
        'north': uv('trim', T_BEZEL), 'west': uv('trim', T_SMALL), 'east': uv('trim', T_SMALL),
        'up': uv('trim', T_SMALL), 'down': uv('trim', T_SMALL)}, name='gem_bezel')
    box((7.25, 4.25, 2.95), (8.75, 5.5, 3.25), {
        'north': uv('gem', G_FACE), 'west': uv('gem', G_SIDE), 'east': uv('gem', G_SIDE),
        'up': uv('gem', G_SIDE), 'down': uv('gem', G_SIDE)}, emissive=True, shade=False, name='omnium_gem')
    for x0 in (3.75, 11):
        box((x0, 1.25, 3.75), (x0 + 1.25, 2.5, 6), {
            'north': uv('trim', T_CAP), 'west': uv('trim', T_CAP), 'east': uv('trim', T_CAP),
            'up': uv('trim', T_SMALL), 'down': uv('trim', T_SMALL)}, name='pocket_corner')
    # Side pockets with flaps.
    for side, (x0, x1, fx0, fx1) in {'west': (2, 3, 1.75, 3), 'east': (13, 14, 13, 14.25)}.items():
        box((x0, 1.5, 7), (x1, 7, 11), {
            side: uv('lid', L_SPOCKET), 'north': uv('lid', L_SPOCKET_EDGE), 'south': uv('lid', L_SPOCKET_EDGE),
            'down': uv('lid', L_SPOCKET_EDGE)}, tinted=True, name=f'side_pocket_{side}')
        box((fx0, 5.5, 6.75), (fx1, 7.25, 11.25), {
            side: uv('lid', L_SFLAP), 'up': uv('lid', L_SFLAP_TOP), 'north': uv('lid', L_SFLAP_EDGE),
            'south': uv('lid', L_SFLAP_EDGE), 'down': uv('lid', L_SFLAP_EDGE)}, tinted=True, name=f'side_flap_{side}')
    # Quilted back padding and shoulder straps.
    box((3.5, 2, 12), (12.5, 11, 12.5), {
        'south': uv('back', K_PAD), 'west': uv('back', K_EDGE), 'east': uv('back', K_EDGE),
        'down': uv('back', K_TOPEDGE)}, tinted=True, name='back_padding')
    for x0, region in ((4.25, S_LEFT), (9.75, S_RIGHT)):
        box((x0, 1.5, 12.5), (x0 + 2, 12, 13.25), {
            'south': uv('strap', region), 'west': uv('strap', S_EDGE), 'east': uv('strap', S_EDGE),
            'down': uv('strap', S_FOLD)}, name='shoulder_strap')
        box((x0, 12, 10.5), (x0 + 2, 13.2, 13.25), {
            'up': uv('strap', S_TOP), 'south': uv('strap', S_FOLD), 'north': uv('strap', S_FOLD),
            'west': uv('strap', S_FOLD), 'east': uv('strap', S_FOLD)}, name='strap_anchor')
        box((x0 - 0.25, 3.75, 13.25), (x0 + 2.25, 4.75, 13.6), {
            'south': uv('trim', T_BAND), 'west': uv('trim', T_SMALL), 'east': uv('trim', T_SMALL),
            'up': uv('trim', T_SMALL), 'down': uv('trim', T_SMALL)}, name='strap_adjuster')
    # Top handle on two metal mounts.
    for x0 in (5.75, 9.25):
        box((x0, 13, 8), (x0 + 1, 13.5, 9.5), {
            'north': uv('trim', T_SMALL), 'south': uv('trim', T_SMALL), 'west': uv('trim', T_SMALL),
            'east': uv('trim', T_SMALL), 'up': uv('trim', T_CAP)}, name='handle_mount')
        px0 = x0 + 0.25 if x0 < 8 else x0
        box((px0, 13.5, 8.25), (px0 + 0.75, 14.75, 9.25), {
            'north': uv('strap', S_POST), 'south': uv('strap', S_POST), 'west': uv('strap', S_POST),
            'east': uv('strap', S_POST)}, name='handle_post')
    box((6, 14.5, 8.25), (10, 15.25, 9.25), {
        'north': uv('strap', S_HANDLE), 'south': uv('strap', S_HANDLE), 'up': uv('strap', S_HANDLE_TOP),
        'down': uv('strap', S_HANDLE), 'west': uv('strap', S_POST), 'east': uv('strap', S_POST)}, name='handle')

    m.display = {
        # Silhouette under this rotation spans x -7..6.4, y -7.5..9 px: centre it and fill ~14 px of the slot.
        'gui': {'rotation': [22, 200, 0], 'translation': [0.25, 0.6, 0], 'scale': [0.85, 0.85, 0.85]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2.5, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, -0.5, 0], 'scale': [0.75, 0.75, 0.75]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 0], 'scale': [1, 1, 1]},
        'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, -1.5, 1.5], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, -1.5, 1.5], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 200, 0], 'translation': [1, 1.5, -1], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 160, 0], 'translation': [1, 1.5, -1], 'scale': [0.5, 0.5, 0.5]},
    }
    return m


# =============================================================================================
# Upgrade cards
# =============================================================================================

UPGRADES = ['pickup', 'magnet', 'void', 'feeding', 'restock', 'compacting', 'crafting', 'everlasting', 'deposit']
ACCENTS = {'pickup': GREEN, 'magnet': RED, 'void': VIOLET, 'feeding': AMBER, 'restock': CYAN,
           'compacting': COPPER, 'crafting': STEEL, 'everlasting': GOLD, 'deposit': DIAMOND}

WOOD = ramp('3b2412', '5a3a1c', '7d5530', 'a0723f', 'c49a5f', 'dcbd86')

# Icon pixel maps (placed in the card window, top-left at (4, 4)); '.' is transparent.
ICONS = {
    'pickup': ([
        "...HG...",
        "...HG...",
        "..HHGg..",
        ".HHGGgg.",
        "..HGGg..",
        "S..Gg..S",
        "Ss....sS",
        ".SSSSSd.",
    ], {'H': GREEN[4], 'G': GREEN[3], 'g': GREEN[2], 'S': STEEL[4], 's': STEEL[2], 'd': STEEL[1]}),
    'magnet': ([
        ".hRRRRr.",
        "hRRrrRRr",
        "hR....Rr",
        "hR....Rr",
        "hR....Rr",
        "hR....Rr",
        "WW....WW",
        "ww....ww",
    ], {'h': RED[4], 'R': RED[3], 'r': RED[2], 'W': STEEL[5], 'w': STEEL[3]}),
    'void': ([
        "..pVVp..",
        ".pVvvVp.",
        "pVo..oVv",
        "Vo.kk.oV",
        "Vo.kk.ov",
        "vVo..oVp",
        ".pVvvVp.",
        "..pVVp..",
    ], {'V': VIOLET[3], 'v': VIOLET[2], 'p': VIOLET[1], 'o': VIOLET[0], 'k': BLACK, '.': None}),
    'feeding': ([
        "....Ll..",
        "...Ll...",
        ".rRbRRr.",
        "rHHRRRRr",
        "rHRRRRRr",
        "rRRRRRrr",
        ".rRRRrr.",
        "..rrrr..",
    ], {'L': GREEN[4], 'l': GREEN[2], 'b': rgb('5a3a1c'), 'H': RED[4], 'R': RED[3], 'r': RED[2]}),
    'restock': ([
        "...CC...",
        "..CCCc..",
        ".CCCCcc.",
        "...Cc...",
        "SSSCcSSS",
        "S.YYYy.s",
        "S.Yyyy.s",
        "Sssssss.",
    ], {'C': CYAN[4], 'c': CYAN[2], 'S': STEEL[4], 's': STEEL[1], 'Y': GOLD[4], 'y': GOLD[2]}),
    'compacting': ([
        "WWWWWWWW",
        "wwwwwwww",
        "...Ss...",
        "...Ss...",
        ".BBBBBB.",
        ".BHHhhB.",
        ".BHhhbB.",
        ".BBBbbB.",
    ], {'W': WOOD[4], 'w': WOOD[2], 'S': STEEL[4], 's': STEEL[2], 'B': IRON[2], 'H': IRON[5], 'h': IRON[4], 'b': IRON[1]}),
    'crafting': ([
        "TTTTTTTT",
        "TgTgTgTT",
        "TTTTTTTT",
        "TgTgTgTT",
        "FFFFFFFF",
        "FhFFFsFF",
        "FhhFFsFF",
        "f.ffff.f",
    ], {'T': WOOD[4], 'g': WOOD[1], 'F': WOOD[2], 'f': WOOD[1], 'h': STEEL[4], 's': STEEL[3]}),
    'everlasting': ([
        "GGGGGGGG",
        ".Sa..aS.",
        "..SaaS..",
        "...aS...",
        "...Sa...",
        "..S..S..",
        ".SaaaaS.",
        "gggggggg",
    ], {'G': GOLD[4], 'g': GOLD[2], 'S': CYAN[5], 'a': AMBER[4]}),
    'deposit': ([
        "...Cc...",
        ".CCCCcc.",
        "..CCcc..",
        "WWWccWWW",
        "WwwwwwwW",
        "WWWLLWWW",
        "WwwLlwwW",
        "WWWWWWWW",
    ], {'C': GREEN[4], 'c': GREEN[2], 'W': WOOD[3], 'w': WOOD[2], 'L': GOLD[4], 'l': GOLD[2]}),
}


def card_frame(accent) -> Tex:
    """The common upgrade card: a bevelled gunmetal card, a coloured label strip, a recessed icon window
    and gold contacts along the bottom edge."""
    t = Tex(16, seed=3)
    x0, y0, x1, y1 = 2, 1, 13, 14
    t.rect(x0, y0, x1, y1, BLACK)
    t.gradient_v(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GUNMETAL[4], GUNMETAL[2])
    t.hline(x0 + 1, x1 - 1, y0 + 1, GUNMETAL[5])
    t.vline(x0 + 1, y0 + 1, y1 - 1, GUNMETAL[4])
    t.vline(x1 - 1, y0 + 1, y1 - 1, GUNMETAL[1])
    t.hline(x0 + 1, x1 - 1, y1 - 1, GUNMETAL[1])
    for (cx, cy) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        t.set(cx, cy, CLEAR)
    # Label strip in the upgrade's colour, with a notch.
    t.hline(x0 + 2, x1 - 3, y0 + 2, accent[4])
    t.hline(x0 + 2, x1 - 3, y0 + 3, accent[2])
    t.set(x1 - 2, y0 + 2, GUNMETAL[0])
    t.set(x1 - 2, y0 + 3, STEEL[3])
    # Recessed icon window.
    t.rect(4, 5, 11, 12, GUNMETAL[0])
    t.hline(3, 12, 4, GUNMETAL[0])
    t.vline(3, 4, 12, GUNMETAL[0])
    t.hline(4, 12, 13, GUNMETAL[4])
    t.vline(12, 5, 13, GUNMETAL[3])
    t.radial(8, 9, 5, mix(accent[1], GUNMETAL[1], 0.45), GUNMETAL[0])
    # Gold contacts.
    for x in range(4, 12, 2):
        t.set(x, y1, GOLD[3])
    t.set(3, y1 - 1, GOLD[2])
    t.set(12, y1 - 1, GOLD[2])
    return t


def upgrade_sprite(name: str) -> Tex:
    t = card_frame(ACCENTS[name])
    rows, palette = ICONS[name]
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            color = palette.get(ch) if ch != '.' else None
            if color is not None:
                t.set(4 + dx, 5 + dy, color)
    # A drop shadow under the icon so it reads as raised.
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == '.' and dx > 0 and dy > 0 and row[dx - 1] != '.' and rows[dy - 1][dx - 1] != '.':
                t.set(4 + dx, 5 + dy, shade(t.get(4 + dx, 5 + dy), 0.6))
    return t


# =============================================================================================
# Data: recipes, loot tables, tags
# =============================================================================================


def tier_upgrade_recipe(name, previous, material):
    """Custom shaped recipe (omnivault:backpack_upgrade) that keeps the backpack's UUID and contents."""
    write_json(DATA / 'recipe' / (name + '.json'), {
        'type': f'{MOD}:backpack_upgrade', 'category': 'equipment',
        'pattern': ['MMM', 'MBM', 'MMM'],
        'key': {'M': material, 'B': {'item': f'{MOD}:{previous}'}},
        'result': {'id': f'{MOD}:{name}', 'count': 1},
    })


def backpack_loot(name):
    """Drops the stored backpack item (same UUID, same contents) via the block's dynamic drop."""
    write_json(DATA / 'loot_table' / 'blocks' / (name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:dynamic', 'name': f'{MOD}:backpack'}]}],
    })


def data_files():
    shaped('leather_backpack', ['SLS', 'LCL', 'LLL'],
           {'S': 'minecraft:string', 'L': 'minecraft:leather', 'C': 'minecraft:chest'})
    materials = {'copper': {'tag': 'c:ingots/copper'}, 'iron': {'tag': 'c:ingots/iron'},
                 'gold': {'tag': 'c:ingots/gold'}, 'diamond': {'tag': 'c:gems/diamond'}}
    previous = 'leather'
    for tier in ('copper', 'iron', 'gold', 'diamond'):
        tier_upgrade_recipe(f'{tier}_backpack', f'{previous}_backpack', materials[tier])
        previous = tier
    smithing('netherite_backpack', 'minecraft:netherite_upgrade_smithing_template', f'{MOD}:diamond_backpack',
             'minecraft:netherite_ingot', f'{MOD}:netherite_backpack')

    base = f'{MOD}:upgrade_base'
    shaped('pickup_upgrade', [' H ', 'SUS', ' R '],
           {'H': 'minecraft:hopper', 'S': 'minecraft:string', 'U': base, 'R': 'minecraft:redstone'})
    shaped('magnet_upgrade', ['E L', 'IPI', ' C '],
           {'E': 'minecraft:ender_pearl', 'L': 'minecraft:lapis_lazuli', 'I': 'minecraft:iron_ingot',
            'P': f'{MOD}:pickup_upgrade', 'C': f'{MOD}:logic_processor'})
    shaped('void_upgrade', [' E ', 'OUO', ' O '],
           {'E': 'minecraft:ender_pearl', 'O': 'minecraft:obsidian', 'U': base})
    shaped('feeding_upgrade', [' G ', 'AUB', ' C '],
           {'G': 'minecraft:golden_carrot', 'A': 'minecraft:apple', 'B': 'minecraft:bread', 'U': base,
            'C': f'{MOD}:logic_processor'})
    shaped('restock_upgrade', [' C ', 'DUD', ' P '],
           {'C': 'minecraft:chest', 'D': 'minecraft:dropper', 'U': base, 'P': f'{MOD}:logic_processor'})
    shaped('compacting_upgrade', [' P ', 'PUP', ' T '],
           {'P': 'minecraft:piston', 'U': base, 'T': 'minecraft:crafting_table'})
    shaped('crafting_upgrade', ['T', 'U'], {'T': 'minecraft:crafting_table', 'U': base})
    shaped('everlasting_upgrade', ['GEG', 'OUO', 'GOG'],
           {'G': 'minecraft:ghast_tear', 'E': 'minecraft:end_crystal', 'O': f'{MOD}:omnium_ingot', 'U': base})
    shaped('deposit_upgrade', [' H ', 'CUC', ' R '],
           {'H': 'minecraft:hopper', 'C': 'minecraft:chest', 'U': base, 'R': 'minecraft:redstone'})

    for tier in TIERS:
        backpack_loot(f'{tier}_backpack')

    tag('item', 'backpacks', [f'{MOD}:{t}_backpack' for t in TIERS])
    tag('item', 'backpack_upgrades', [f'{MOD}:{u}_upgrade' for u in UPGRADES])
    # Shulker boxes are already refused by Item.canFitInsideContainerItems(); the convention tag catches modded ones.
    tag('item', 'not_allowed_in_backpacks', [f'#{MOD}:backpacks', {'id': '#c:shulker_boxes', 'required': False}])
    tag('item', 'dyeable', [f'{MOD}:leather_backpack'], namespace='minecraft')


# =============================================================================================
# Main
# =============================================================================================


def main():
    save_animation('block/backpack/omnium_gem', paint_gem_frames(), frametime=4, interpolate=True)
    for template, dyeable in (('backpack_template', False), ('backpack_template_dyeable', True)):
        backpack_model(dyeable).save(f'block/{template}')
    for tier in TIERS:
        pal = PALETTES[tier]
        seed = SEEDS[tier]
        paint_body(pal, seed).save(f'block/backpack/{tier}/body')
        paint_front(pal, seed + 1).save(f'block/backpack/{tier}/front')
        paint_lid(pal, seed + 2).save(f'block/backpack/{tier}/lid')
        paint_strap(pal, seed + 3).save(f'block/backpack/{tier}/strap')
        paint_trim(pal, seed + 4).save(f'block/backpack/{tier}/trim')
        paint_back(pal, seed + 5).save(f'block/backpack/{tier}/back')
        name = f'{tier}_backpack'
        textures = {part: f'block/backpack/{tier}/{part}' for part in ('body', 'front', 'lid', 'strap', 'trim', 'back')}
        textures['gem'] = 'block/backpack/omnium_gem'
        textures['particle'] = f'block/backpack/{tier}/body'
        parent = 'backpack_template_dyeable' if pal['tinted'] else 'backpack_template'
        write_json(ASSETS / 'models' / 'block' / (name + '.json'),
                   {'parent': f'{MOD}:block/{parent}', 'textures': {k: f'{MOD}:{v}' for k, v in textures.items()}})
        blockstate_horizontal(name)
        item_model(name, parent=f'block/{name}')
    for name in UPGRADES:
        upgrade_sprite(name).save(f'item/{name}_upgrade')
        item_model(f'{name}_upgrade')
    data_files()
    print(f'Wrote backpack assets for {len(TIERS)} tiers and {len(UPGRADES)} upgrades')


if __name__ == '__main__':
    main()
