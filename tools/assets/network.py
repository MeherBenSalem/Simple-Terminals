#!/usr/bin/env python3
"""Generates every asset of the storage network and the core content: textures, block/item models,
blockstates, loot tables, recipes and tags.

Models are built from boxes whose faces are painted individually: every face gets its own bevelled
texture region at exactly one texel per model pixel, packed into a per-block texture sheet
(textures/block/<block>/parts.png, 64x64 = sixteen 16x16 tiles). Screens and other animated surfaces
are separate textures. Glowing parts are separate elements rendered full-bright (kit's emissive=True).

Usage (from the repository root): python3 tools/assets/network.py
Preview the result with tools/assets/render_models.py (for example ``--sheet``).
"""
from __future__ import annotations

import json
import math
import re
import zlib

import kit
from kit import (AMBER, BLACK, CLEAR, COPPER, CYAN, GOLD, GREEN, GUNMETAL, RED, STEEL, VIOLET, Tex, mix, rgb,
                 shade)

M = kit.MOD
ATLAS = 64             # texture sheet size in texels; 1 texel == 1 model pixel
UVK = 16 / ATLAS       # model UV units per texel
FACES = kit.FACES
WHITE = [rgb('6f7378'), rgb('9a9ea4'), rgb('c6c9ce'), rgb('e2e4e7'), rgb('f4f5f7'), rgb('ffffff')]
OAK = [rgb('3b2913'), rgb('5c4020'), rgb('7d5a31'), rgb('a07943'), rgb('b8925a'), rgb('d4b07a')]
BLUE = [rgb('0b1e3d'), rgb('163a73'), rgb('2a62b8'), rgb('4b8ff0'), rgb('8cc0ff'), rgb('d6ebff')]
RAMPS = {'cyan': CYAN, 'violet': VIOLET, 'amber': AMBER, 'green': GREEN, 'red': RED, 'steel': STEEL,
         'gunmetal': GUNMETAL, 'copper': COPPER, 'gold': GOLD, 'white': WHITE, 'oak': OAK, 'blue': BLUE}

# Directional light baked into textures of models drawn by block entity renderers, which the game
# renders without face shading.
BAKED_LIGHT = {'up': 1.0, 'down': 0.66, 'north': 0.93, 'south': 0.76, 'east': 0.86, 'west': 0.7}


def seed_of(*parts) -> int:
    return zlib.crc32('|'.join(str(p) for p in parts).encode())


def ramp_from(hex_colour: str):
    """A six-step dark -> light ramp around one colour (index 3 is the colour itself)."""
    c = rgb(hex_colour)
    black, white = rgb('05060a'), rgb('ffffff')
    return [mix(c, black, 0.78), mix(c, black, 0.55), mix(c, black, 0.28), c, mix(c, white, 0.45), mix(c, white, 0.82)]


# =================================================================================================
# Painters: functions that paint a face-sized Tex. Specs are strings "name:arg:arg".
# =================================================================================================

PAINTERS = {}


def painter(name):
    def register(fn):
        PAINTERS[name] = fn
        return fn
    return register


def paint(spec: str, w: int, h: int) -> Tex:
    name, *args = spec.split(':')
    t = Tex(w, h, seed=seed_of(spec, w, h))
    PAINTERS[name](t, *args)
    return t


def _gradient(t: Tex, top=1.07, bottom=0.9):
    if t.h < 3:
        return
    for y in range(t.h):
        f = top + (bottom - top) * y / (t.h - 1)
        for x in range(t.w):
            t.set(x, y, shade(t.get(x, y), f))


def plate(t: Tex, ramp, base=2, hi=None, lo=None, brushed=True, grad=True, spec_px=True, noise=0.3):
    """The workhorse: a brushed/noisy plate with a light top-left and dark bottom-right bevel."""
    w, h = t.w, t.h
    if brushed:
        t.brushed(ramp, base=base, vertical=h > w * 1.5)
    else:
        t.noise(ramp, base=base, chance=noise)
    if grad:
        _gradient(t)
    hi = ramp[min(len(ramp) - 1, base + 2)] if hi is None else hi
    lo = ramp[max(0, base - 2)] if lo is None else lo
    if w >= 2 and h >= 2:
        t.hline(0, w - 1, 0, hi)
        t.vline(0, 0, h - 1, hi)
        t.hline(0, w - 1, h - 1, lo)
        t.vline(w - 1, 0, h - 1, lo)
        t.set(w - 1, 0, mix(hi, lo, 0.5))
        t.set(0, h - 1, mix(hi, lo, 0.5))
        if spec_px:
            t.set(0, 0, mix(hi, (255, 255, 255, 255), 0.35))
    elif h == 1 and w >= 2:
        for x in range(w):
            t.set(x, 0, mix(ramp[base], hi, 0.6 if x < w // 2 else 0.2))
        t.set(w - 1, 0, lo)
    elif w == 1 and h >= 2:
        for y in range(h):
            t.set(0, y, mix(ramp[base], hi, 0.6 if y < h // 2 else 0.1))
        t.set(0, h - 1, lo)
    else:
        t.set(0, 0, ramp[base + 1 if base + 1 < len(ramp) else base])
    return t


def groove(t: Tex, x0, y0, x1, y1, dark=GUNMETAL[0], light=GUNMETAL[4]):
    """A 1px engraved rectangle: dark line with a light line below/right of it (reads as cut-in)."""
    t.hline(x0, x1, y0, dark)
    t.vline(x0, y0, y1, dark)
    t.hline(x0 + 1, x1 + 1, y1 + 1, light)
    t.vline(x1 + 1, y0 + 1, y1 + 1, light)
    t.hline(x0, x1, y1, mix(dark, light, 0.4))
    t.vline(x1, y0, y1, mix(dark, light, 0.4))


def rivet(t: Tex, x, y):
    t.set(x, y, STEEL[4])
    t.set(x + 1, y, STEEL[2])
    t.set(x, y + 1, STEEL[2])
    t.set(x + 1, y + 1, GUNMETAL[0])


def dot_screw(t: Tex, x, y):
    t.set(x, y, STEEL[3])


@painter('casing')
def p_casing(t):
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])


@painter('casing_dark')
def p_casing_dark(t):
    plate(t, GUNMETAL, base=1, hi=GUNMETAL[3], lo=BLACK)


@painter('steel')
def p_steel(t):
    plate(t, STEEL, base=2, hi=STEEL[4], lo=STEEL[0])


@painter('steel_hi')
def p_steel_hi(t):
    plate(t, STEEL, base=3, hi=STEEL[5], lo=STEEL[1])


@painter('steel_dark')
def p_steel_dark(t):
    plate(t, STEEL, base=1, hi=STEEL[3], lo=GUNMETAL[1])


@painter('bracket')
def p_bracket(t):
    """Steel corner bracket with screw heads in its corners."""
    plate(t, STEEL, base=2, hi=STEEL[5], lo=STEEL[0])
    if t.w >= 4 and t.h >= 4:
        for x, y in ((1, 1), (t.w - 3, t.h - 3)):
            rivet(t, x, y)
    elif t.w >= 3 and t.h >= 3:
        dot_screw(t, t.w // 2, t.h // 2)


@painter('pillar')
def p_pillar(t):
    """Gunmetal pillar with a steel edge highlight and notches every few pixels."""
    plate(t, GUNMETAL, base=3, hi=STEEL[2], lo=GUNMETAL[0])
    long_v = t.h >= t.w
    n = t.h if long_v else t.w
    for i in range(3, n - 2, 4):
        if long_v:
            t.hline(1, t.w - 2, i, GUNMETAL[1])
            t.hline(1, t.w - 2, i + 1, GUNMETAL[4])
        else:
            t.vline(i, 1, t.h - 2, GUNMETAL[1])
            t.vline(i + 1, 1, t.h - 2, GUNMETAL[4])


@painter('recess')
def p_recess(t):
    t.noise(GUNMETAL, base=0, spread=1, chance=0.25)
    for x in range(t.w):
        t.set(x, 0, BLACK)
    for y in range(t.h):
        t.set(0, y, BLACK)
    if t.w >= 3 and t.h >= 3:
        t.hline(1, t.w - 1, t.h - 1, GUNMETAL[2])
        t.vline(t.w - 1, 1, t.h - 1, GUNMETAL[2])


@painter('black')
def p_black(t):
    t.noise([BLACK, BLACK, GUNMETAL[0], GUNMETAL[1]], base=1, chance=0.2)


@painter('glow')
def p_glow(t, ramp='cyan'):
    r = RAMPS[ramp]
    w, h = t.w, t.h
    t.fill(r[3])
    if w >= 3 and h >= 3:
        t.rect(1, 1, w - 2, h - 2, r[4])
        if h > w:
            t.vline(w // 2, 1, h - 2, r[5])
        else:
            t.hline(1, w - 2, h // 2, r[5])
    elif w >= 2 and h >= 2:
        t.rect(0, 0, w - 1, h - 1, r[4])
        t.set(0, 0, r[5])
    else:
        for i in range(max(w, h)):
            t.set(min(i, w - 1), min(i, h - 1), r[4] if i % 3 else r[5])


@painter('dim')
def p_dim(t, ramp='cyan'):
    """A glow strip while the device is off: dark tinted glass."""
    r = RAMPS[ramp]
    w, h = t.w, t.h
    t.fill(r[0])
    if w >= 3 and h >= 3:
        t.rect(1, 1, w - 2, h - 2, mix(r[0], r[1], 0.6))
        if h > w:
            t.vline(1, 1, h - 2, r[1])
        else:
            t.hline(1, w - 2, 1, r[1])
    else:
        for i in range(0, max(w, h), 3):
            t.set(min(i, w - 1), min(i, h - 1), r[1])


@painter('white')
def p_white(t):
    """Light, softly shaded surface for tinted faces (the tint supplies the colour)."""
    plate(t, WHITE, base=3, hi=WHITE[5], lo=WHITE[1], brushed=False, noise=0.15)


@painter('lamp')
def p_lamp(t):
    """Flat bright surface for tinted lamps and LEDs."""
    t.fill(WHITE[4])
    if t.w >= 2 and t.h >= 2:
        t.set(0, 0, WHITE[5])
        t.hline(0, t.w - 1, t.h - 1, WHITE[3])
        t.vline(t.w - 1, 0, t.h - 1, WHITE[3])


@painter('glass')
def p_glass(t, ramp='cyan'):
    """Clear, faintly tinted glass with two reflection streaks in the upper-left corner."""
    r = RAMPS[ramp]
    w, h = t.w, t.h
    t.fill((r[1][0], r[1][1], r[1][2], 34))

    def a(c, alpha):
        return c[0], c[1], c[2], alpha
    for x in range(w):
        for y in range(h):
            d = x + y
            if d in (3, 4) or d == 7:
                t.set(x, y, a(r[5], 70 if d != 7 else 48))
    for x in range(w):
        t.set(x, 0, a(r[4], 105))
        t.set(x, h - 1, a(r[1], 110))
    for y in range(h):
        t.set(0, y, a(r[4], 105))
        t.set(w - 1, y, a(r[1], 110))
    if w >= 4 and h >= 4:
        t.set(1, 1, (255, 255, 255, 170))


@painter('panel')
def p_panel(t, ramp='gunmetal'):
    """Casing plate with an engraved inner panel and corner screws (scales with the face)."""
    r = RAMPS[ramp]
    plate(t, r, base=2, hi=r[4], lo=r[0])
    w, h = t.w, t.h
    if w >= 8 and h >= 8:
        groove(t, 2, 2, w - 4, h - 4, dark=r[0], light=r[4])
        for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            t.set(x, y, STEEL[3])
    elif w >= 4 and h >= 4:
        for x, y in ((1, 1), (w - 2, h - 2)):
            t.set(x, y, STEEL[3])


@painter('hazard')
def p_hazard(t):
    for y in range(t.h):
        for x in range(t.w):
            t.set(x, y, AMBER[3] if ((x + y) // 2) % 2 == 0 else GUNMETAL[0])


@painter('copper')
def p_copper(t):
    plate(t, COPPER, base=2, hi=COPPER[4], lo=COPPER[0])


@painter('coil')
def p_coil(t):
    """Copper windings: stripes across the long axis."""
    vertical = t.h > t.w
    n = t.h if vertical else t.w
    for i in range(n):
        tone = COPPER[3] if i % 2 == 0 else COPPER[1]
        if vertical:
            t.hline(0, t.w - 1, i, tone)
        else:
            t.vline(i, 0, t.h - 1, tone)
    for i in range(0, n, 2):
        if vertical:
            t.set(0, i, COPPER[4])
        else:
            t.set(i, 0, COPPER[4])


@painter('wood')
def p_wood(t):
    """Oak planks running along the long axis, with grain and nail heads."""
    w, h = t.w, t.h
    along_x = w >= h
    t.noise(OAK, base=3, chance=0.25)
    span = h if along_x else w
    for i in range(span):
        if i % 4 == 3:
            if along_x:
                t.hline(0, w - 1, i, OAK[1])
            else:
                t.vline(i, 0, h - 1, OAK[1])
    rng = t.rng
    for _ in range(max(1, w * h // 12)):
        x, y = rng.randrange(w), rng.randrange(h)
        if t.get(x, y) != OAK[1]:
            t.set(x, y, OAK[2] if rng.random() < 0.6 else OAK[4])
    if w >= 2 and h >= 2:
        t.hline(0, w - 1, 0, OAK[4])
        t.vline(0, 0, h - 1, OAK[4])
        t.hline(0, w - 1, h - 1, OAK[0])
        t.vline(w - 1, 0, h - 1, OAK[0])


@painter('rubber')
def p_rubber(t):
    plate(t, GUNMETAL, base=1, hi=GUNMETAL[3], lo=BLACK, brushed=False, noise=0.2)
    vertical = t.h > t.w
    n = t.h if vertical else t.w
    for i in range(1, n - 1, 2):
        if vertical:
            t.hline(1, t.w - 2, i, BLACK)
        else:
            t.vline(i, 1, t.h - 2, BLACK)


@painter('gem')
def p_gem(t, ramp='violet'):
    """Faceted crystal surface: bright upper-left facet, darker lower-right, sparkle pixels."""
    r = RAMPS[ramp]
    w, h = t.w, t.h
    for y in range(h):
        for x in range(w):
            k = (x / max(1, w - 1) + y / max(1, h - 1)) / 2
            t.set(x, y, r[4] if k < 0.3 else r[3] if k < 0.62 else r[2])
    if w >= 3 and h >= 3:
        t.line(0, h - 1, w - 1, 0, mix(r[3], r[5], 0.4))
        t.set(1, 1, r[5])
    t.set(0, 0, r[5])


# -- larger, single-purpose faces --------------------------------------------------------------------

@painter('cap_top')
def p_cap_top(t):
    """Top plate of a cap: bevelled border with a rivet in each corner."""
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    w, h = t.w, t.h
    for x, y in ((1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)):
        rivet(t, x, y)


@painter('band')
def p_band(t):
    """Side band of a slab: steel trim line along the top with bolts along it."""
    plate(t, GUNMETAL, base=2, hi=STEEL[3], lo=GUNMETAL[0])
    w, h = t.w, t.h
    if h >= 3:
        t.hline(1, w - 2, 1, GUNMETAL[4])
    for x in range(2, w - 1, 4):
        if h >= 2:
            t.set(x, h // 2 + (0 if h > 2 else -1), STEEL[3])


@painter('port')
def p_port(t, ramp='cyan'):
    """A round-ish socket: steel ring, dark bore, coloured ring inside."""
    r = RAMPS[ramp]
    w, h = t.w, t.h
    t.fill(CLEAR)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    rad = min(w, h) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy) / rad
            if d <= 1.08:
                if d > 0.78:
                    t.set(x, y, STEEL[4] if (x < cx and y < cy) else STEEL[2] if (x < cx or y < cy) else STEEL[0])
                elif d > 0.55:
                    t.set(x, y, r[2])
                else:
                    t.set(x, y, BLACK if d < 0.32 else GUNMETAL[0])
    for y in range(h):
        for x in range(w):
            if t.get(x, y)[3] == 0:
                t.set(x, y, GUNMETAL[2])


# =================================================================================================
# Atlas + model builder
# =================================================================================================

class Region:
    def __init__(self, spec, w, h, light):
        self.spec, self.w, self.h, self.light = spec, w, h, light
        self.page = self.x = self.y = None


class Atlas:
    """Collects painted face regions for one block (or item) and packs them into 64x64 sheets."""

    def __init__(self, folder: str):
        self.folder = folder  # e.g. 'block/drive'
        self.regions: dict = {}
        self.pages = 0

    def region(self, spec, w, h, light=1.0):
        tw = max(1, math.ceil(w - 1e-6))
        th = max(1, math.ceil(h - 1e-6))
        key = (spec, tw, th, round(light, 3))
        if key not in self.regions:
            self.regions[key] = Region(spec, tw, th, light)
        return self.regions[key]

    def page_name(self, index):
        return f'{self.folder}/parts' + (f'_{index}' if index else '')

    def pack(self):
        page, x, y, shelf = 0, 0, 0, 0
        for r in sorted(self.regions.values(), key=lambda r: (-r.h, -r.w, r.spec, r.light)):
            if r.w > ATLAS or r.h > ATLAS:
                raise ValueError(f'{r.spec} {r.w}x{r.h} is larger than a sheet')
            if x + r.w > ATLAS:
                x, y, shelf = 0, y + shelf, 0
            if y + r.h > ATLAS:
                page, x, y, shelf = page + 1, 0, 0, 0
            r.page, r.x, r.y = page, x, y
            x += r.w
            shelf = max(shelf, r.h)
        self.pages = page + 1

    def save(self):
        folder = kit.ASSETS / 'textures' / self.folder
        for stale in folder.glob('parts*.png'):
            stale.unlink()
        # Unused space is opaque gunmetal so mipmaps of solid models do not bleed black at a distance.
        sheets = [Tex(ATLAS, fill=GUNMETAL[1]) for _ in range(self.pages)]
        for r in self.regions.values():
            t = paint(r.spec, r.w, r.h)
            if r.light != 1.0:
                for yy in range(t.h):
                    for xx in range(t.w):
                        t.set(xx, yy, shade(t.get(xx, yy), r.light))
            sheets[r.page].img.paste(t.img, (r.x, r.y))  # replace, keeping transparent pixels transparent
        for i, sheet in enumerate(sheets):
            sheet.save(self.page_name(i))


def face_dims(face, f, t):
    dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
    if face in ('north', 'south'):
        return dx, dy
    if face in ('east', 'west'):
        return dz, dy
    return dx, dz


class Shape:
    """A model whose box faces are painted into an Atlas. Call Atlas.pack() before save()."""

    def __init__(self, atlas: Atlas, parent='block/block', render_type=None, textures=None, bake_light=False,
                 particle='block/network/casing', ao=True):
        self.atlas = atlas
        self.parent = parent
        self.render_type = render_type
        self.textures = dict(textures or {})
        if particle:
            self.textures.setdefault('particle', particle)
        self.bake_light = bake_light
        self.ao = ao
        self.elements = []
        self.display = None

    def box(self, f, t, mat='casing', faces=None, emissive=False, tint=None, rot=None, shade=None, skip=(),
            uvrot=None, light=None, cull=True):
        """f/t: corners in pixels. mat: painter spec for every face; faces: per-face overrides, where a
        value is a painter spec, None (no face) or ('#var', [u0, v0, u1, v1]) for a named texture.
        tint: tint index for every face or {face: index}. rot: (angle, axis[, origin[, rescale]]).
        Emissive elements are unshaded unless shade=True."""
        f = [round(v, 4) for v in f]
        t = [round(v, 4) for v in t]
        el = {'from': f, 'to': t}
        if rot:
            angle, axis = rot[0], rot[1]
            origin = rot[2] if len(rot) > 2 and rot[2] is not None else [8, 8, 8]
            el['rotation'] = {'angle': angle, 'axis': axis, 'origin': list(origin)}
            if len(rot) > 3 and rot[3]:
                el['rotation']['rescale'] = True
        if shade is None:
            shade = not emissive
        if not shade:
            el['shade'] = False
        el['faces'] = {}
        for face in FACES:
            if face in skip:
                continue
            spec = faces[face] if faces and face in faces else mat
            if spec is None:
                continue
            entry = {}
            if isinstance(spec, tuple):
                entry['_tex'] = spec
            else:
                w, h = face_dims(face, f, t)
                if w <= 0 or h <= 0:
                    continue
                lt = light if light is not None else (BAKED_LIGHT[face] if self.bake_light else 1.0)
                entry['_region'] = (self.atlas.region(spec, w, h, lt), w, h)
            if uvrot and face in uvrot:
                entry['rotation'] = uvrot[face]
            ti = tint.get(face) if isinstance(tint, dict) else tint
            if ti is not None:
                entry['tintindex'] = ti
            if cull and not rot:
                boundary = {'north': f[2] == 0, 'south': t[2] == 16, 'west': f[0] == 0, 'east': t[0] == 16,
                            'down': f[1] == 0, 'up': t[1] == 16}
                if boundary[face]:
                    entry['cullface'] = face
            el['faces'][face] = entry
        if emissive:
            el['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        self.elements.append(el)
        return self

    def include(self, other: 'Shape', offset=(0, 0, 0)):
        """Copies another shape's elements (same atlas), translated by ``offset`` pixels."""
        for el in other.elements:
            copy = json.loads(json.dumps({k: v for k, v in el.items() if k != 'faces'}))
            copy['from'] = [round(a + b, 4) for a, b in zip(el['from'], offset)]
            copy['to'] = [round(a + b, 4) for a, b in zip(el['to'], offset)]
            if 'rotation' in copy:
                copy['rotation']['origin'] = [round(a + b, 4) for a, b in zip(copy['rotation']['origin'], offset)]
            copy['faces'] = {face: {k: v for k, v in entry.items() if k != 'cullface'} for face, entry in el['faces'].items()}
            self.elements.append(copy)
        for key, value in other.textures.items():
            if key != 'particle':
                self.textures.setdefault(key, value)
        return self

    def to_model(self) -> kit.Model:
        model = kit.Model(self.parent, dict(self.textures), self.render_type, self.ao)
        used_pages = set()
        out = []
        for el in self.elements:
            copy = {k: v for k, v in el.items() if k != 'faces'}
            faces = {}
            for face, entry in el['faces'].items():
                fe = {}
                if '_tex' in entry:
                    spec = entry['_tex']
                    fe['uv'] = [round(v, 4) for v in spec[1]]
                    fe['texture'] = spec[0]
                elif '_region' in entry:
                    region, w, h = entry['_region']
                    if region.page is None:
                        raise RuntimeError(f'{self.atlas.folder}: pack the atlas before saving')
                    fe['uv'] = [round(region.x * UVK, 4), round(region.y * UVK, 4),
                                round((region.x + w) * UVK, 4), round((region.y + h) * UVK, 4)]
                    fe['texture'] = f'#p{region.page}'
                    used_pages.add(region.page)
                else:  # already finalised (copied from an included shape)
                    fe['uv'] = entry['uv']
                    fe['texture'] = entry['texture']
                for key in ('rotation', 'tintindex', 'cullface'):
                    if key in entry:
                        fe[key] = entry[key]
                faces[face] = fe
            copy['faces'] = faces
            out.append(copy)
        for page in sorted(used_pages):
            model.textures[f'p{page}'] = self.atlas.page_name(page)
        model.elements = out
        model.display = self.display
        return model

    def finalised(self) -> 'Shape':
        """A copy whose faces carry concrete UVs (for include() after packing)."""
        model = self.to_model()
        s = Shape(self.atlas, self.parent, self.render_type, model.textures, self.bake_light, None, self.ao)
        s.elements = model.elements
        return s

    def save(self, rel: str):
        data = self.to_model().to_json()
        # kit.Model qualifies texture ids; keep '#refs' untouched
        for key, value in list(data['textures'].items()):
            if value.startswith(f'{M}:#'):
                data['textures'][key] = value[len(M) + 1:]
        return kit.write_json(kit.ASSETS / 'models' / (rel + '.json'), data)


def rotate_box_y(f, t, turns):
    """Rotates a box clockwise (seen from above) by 90-degree turns about the block centre: north -> east."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    for _ in range(turns % 4):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return [x0, y0, z0], [x1, y1, z1]


FACE_TURN = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}


def turn_faces(spec: dict | None, turns: int):
    if spec is None:
        return None
    out = dict(spec)
    for _ in range(turns % 4):
        out = {FACE_TURN[k]: v for k, v in out.items()}
    return out


def blockstate_variants(name: str, prop: str, models: dict):
    kit.write_json(kit.ASSETS / 'blockstates' / (name + '.json'),
                   {'variants': {f'{prop}={value}': {'model': f'{M}:{model}'} for value, model in models.items()}})


def putter(s: Shape, turns: int):
    """box() for a shape authored facing north, rotated clockwise by ``turns`` quarter turns."""
    def put(f, t, mat='casing', **kw):
        f2, t2 = rotate_box_y(f, t, turns)
        kw['faces'] = turn_faces(kw.get('faces'), turns)
        kw['skip'] = tuple(turn_faces({k: 1 for k in kw.get('skip', ())}, turns) or ())
        if isinstance(kw.get('tint'), dict):
            kw['tint'] = turn_faces(kw['tint'], turns)
        return s.box(f2, t2, mat, **kw)
    return put


# =================================================================================================
# Nexus Core
# =================================================================================================

@painter('crystal')
def p_crystal(t, ramp='violet', alpha='205'):
    r = RAMPS[ramp]
    a = int(alpha)
    w, h = t.w, t.h
    for y in range(h):
        for x in range(w):
            k = y / max(1, h - 1)
            c = mix(r[5], r[4], k / 0.45) if k < 0.45 else mix(r[4], r[3], (k - 0.45) / 0.55)
            if x == 0:
                c = mix(c, (255, 255, 255, 255), 0.45)
            elif x == w - 1 and w > 2:
                c = mix(c, r[3], 0.5)
            t.set(x, y, (c[0], c[1], c[2], a))
    if w >= 2 and h >= 3:
        sx, sy = t.rng.randrange(w), t.rng.randrange(h)
        t.set(sx, sy, (255, 248, 255, min(255, a + 50)))


@painter('core_floor')
def p_core_floor(t):
    """Inside face of a cap: dark emitter plate with an engraved ring."""
    plate(t, GUNMETAL, base=1, hi=GUNMETAL[3], lo=BLACK)
    if t.w >= 8:
        groove(t, 1, 1, t.w - 3, t.h - 3, dark=BLACK, light=GUNMETAL[3])


@painter('core_well')
def p_core_well(t):
    t.noise(GUNMETAL, base=0, chance=0.3)
    groove(t, 0, 0, t.w - 2, t.h - 2, dark=BLACK, light=GUNMETAL[2])


@painter('sill')
def p_sill(t):
    plate(t, STEEL, base=2, hi=STEEL[5], lo=STEEL[0])
    if t.w >= 6 and t.h >= 2:
        for x in range(2, t.w - 1, 3):
            t.set(x, t.h - 1, GUNMETAL[1])


def ring4(s: Shape, x0, x1, y0, y1, w, mat, **kw):
    """A square ring of four bars in the XZ plane between x0..x1 (same for z), bars ``w`` wide."""
    s.box([x0, y0, x0], [x1, y1, x0 + w], mat, **kw)
    s.box([x0, y0, x1 - w], [x1, y1, x1], mat, **kw)
    s.box([x0, y0, x0 + w], [x0 + w, y1, x1 - w], mat, **kw)
    s.box([x1 - w, y0, x0 + w], [x1, y1, x1 - w], mat, **kw)


def core_frame(s: Shape, active: bool):
    glow = 'glow:cyan' if active else 'dim:cyan'
    em = active
    # bottom slab, plinth and emitter ring
    s.box([0, 0, 0], [16, 2, 16], 'band', faces={'down': 'panel', 'up': 'casing'})
    s.box([2, 2, 2], [14, 3, 14], 'steel', faces={'up': 'core_floor', 'down': None})
    ring4(s, 4.5, 11.5, 3, 3.5, 1, glow, emissive=em, skip=('down',))
    s.box([6, 3, 6], [10, 3.25, 10], 'bracket', skip=('down',))
    # upper plinth and emitter ring, facing down at the crystal
    s.box([2, 13, 2], [14, 14, 14], 'steel', faces={'down': 'core_floor', 'up': None})
    ring4(s, 4.5, 11.5, 12.5, 13, 1, glow, emissive=em, skip=('up',))
    s.box([6, 12.75, 6], [10, 13, 10], 'bracket', skip=('up',))
    # top slab: a frame around a recessed well with a glowing ring and a violet crown gem
    s.box([0, 14, 0], [16, 16, 4], 'band', faces={'up': 'cap_top', 'down': 'casing'})
    s.box([0, 14, 12], [16, 16, 16], 'band', faces={'up': 'cap_top', 'down': 'casing'})
    s.box([0, 14, 4], [4, 16, 12], 'band', faces={'up': 'casing', 'down': 'casing', 'north': None, 'south': None})
    s.box([12, 14, 4], [16, 16, 12], 'band', faces={'up': 'casing', 'down': 'casing', 'north': None, 'south': None})
    s.box([4, 14, 4], [12, 15, 12], 'core_well', faces={'north': None, 'south': None, 'east': None, 'west': None, 'down': None})
    ring4(s, 4.5, 11.5, 15, 15.5, 1, glow, emissive=em, skip=('down',))
    s.box([6.5, 15, 6.5], [9.5, 15.75, 9.5], 'gem:violet', emissive=em, skip=('down',))
    # slender corner rods between chunky steel collars: thin enough that the crystal stays visible
    # past the nearest corner in the 3/4 view
    for x, z in ((0, 0), (14.5, 0), (0, 14.5), (14.5, 14.5)):
        s.box([x, 4, z], [x + 1.5, 12, z + 1.5], 'pillar', skip=('up', 'down'))
        bx = 0 if x == 0 else 12.5
        bz = 0 if z == 0 else 12.5
        s.box([bx, 2, bz], [bx + 3.5, 4, bz + 3.5], 'bracket', skip=('down',))
        s.box([bx, 12, bz], [bx + 3.5, 14, bz + 3.5], 'bracket', skip=('up',))
    # window sills, lintels, edge lights and glass on each side
    for turns in range(4):
        put = putter(s, turns)
        put([3.5, 2, 0], [12.5, 3, 2], 'sill', skip=('down',))
        put([3.5, 13, 0], [12.5, 14, 2], 'sill', skip=('up',))
        put([1.5, 4, 0.25], [2, 12, 0.75], glow, emissive=em, skip=('up', 'down'))
        put([14, 4, 0.25], [14.5, 12, 0.75], glow, emissive=em, skip=('up', 'down'))
        put([1.5, 3, 0.75], [14.5, 13, 1.25], 'glass:cyan', skip=('east', 'west', 'up', 'down'))


class Scaled:
    """Wraps a Shape so boxes are scaled by ``k`` about ``centre`` (used for the item's enlarged crystal)."""

    def __init__(self, shape: Shape, k, centre=(8, 8, 8), offset=(0, 0, 0)):
        self.shape, self.c, self.o = shape, centre, offset
        self.k = k if isinstance(k, (tuple, list)) else (k, k, k)

    def _p(self, v):
        return [round(self.c[i] + (v[i] - self.c[i]) * self.k[i] + self.o[i], 4) for i in range(3)]

    def box(self, f, t, mat='casing', **kw):
        if kw.get('rot'):
            rot = list(kw['rot'])
            rot[2] = self._p(rot[2] if len(rot) > 2 and rot[2] is not None else [8, 8, 8])
            kw['rot'] = tuple(rot)
        return self.shape.box(self._p(f), self._p(t), mat, **kw)


def core_crystal(s: Shape, tint=0):
    em = dict(emissive=True, tint=tint)
    r45 = (45, 'y', [8, 8, 8])
    shell = 'crystal:violet:190'
    s.box([7.25, 6.25, 7.25], [8.75, 9.75, 8.75], 'glow:violet', rot=r45, **em)  # inner energy core
    s.box([6.25, 5.5, 6.25], [9.75, 10.5, 9.75], shell, rot=r45, **em)
    s.box([6.5, 6, 6.5], [9.5, 10, 9.5], 'crystal:violet:150', **em)
    s.box([6.75, 10.5, 6.75], [9.25, 11.5, 9.25], shell, rot=r45, **em)
    s.box([7.25, 11.5, 7.25], [8.75, 12.25, 8.75], 'crystal:violet:215', rot=r45, **em)
    s.box([7.6, 12.25, 7.6], [8.4, 12.75, 8.4], 'crystal:violet:235', rot=r45, **em)
    s.box([6.75, 4.5, 6.75], [9.25, 5.5, 9.25], shell, rot=r45, **em)
    s.box([7.25, 3.75, 7.25], [8.75, 4.5, 8.75], 'crystal:violet:215', rot=r45, **em)
    s.box([7.6, 3.25, 7.6], [8.4, 3.75, 8.4], 'crystal:violet:235', rot=r45, **em)
    # two satellite shards leaning out from the base
    s.box([9.6, 5.2, 7.4], [10.8, 8.4, 8.6], 'crystal:violet:215', rot=(-22.5, 'z', [9.6, 5.2, 8]), **em)
    s.box([5.2, 5.6, 7.4], [6.4, 8.2, 8.6], 'crystal:violet:215', rot=(22.5, 'z', [6.4, 5.6, 8]), **em)


def core_ring(s: Shape, tint=0, radius=5.5):
    em = dict(emissive=True, tint=tint)
    L, H, T = 2.35, 0.7, 0.55
    y0, y1 = 8 - H / 2, 8 + H / 2
    for side in (1, -1):
        zc = 8 + side * radius
        for a in (-45, -22.5, 0, 22.5, 45):
            s.box([8 - L / 2, y0, zc - T / 2], [8 + L / 2, y1, zc + T / 2], 'glow:cyan',
                  rot=(a, 'y', [8, 8, 8]) if a else None, **em)
    for side in (1, -1):
        xc = 8 + side * radius
        for a in (-22.5, 0, 22.5):
            s.box([xc - T / 2, y0, 8 - L / 2], [xc + T / 2, y1, 8 + L / 2], 'glow:cyan',
                  rot=(a, 'y', [8, 8, 8]) if a else None, **em)
    # four bright beads mark the spin
    for dx, dz in ((radius, 0), (-radius, 0), (0, radius), (0, -radius)):
        s.box([8 + dx - 0.6, 7.4, 8 + dz - 0.6], [8 + dx + 0.6, 8.6, 8 + dz + 0.6], 'glow:violet', **em)


def nexus_core():
    atlas = Atlas('block/nexus_core')
    rt = 'minecraft:translucent'
    idle = Shape(atlas, render_type=rt)
    core_frame(idle, False)
    on = Shape(atlas, render_type=rt)
    core_frame(on, True)
    crystal = Shape(atlas, parent=None, render_type=rt, bake_light=True, particle='block/nexus_core/crystal_particle')
    core_crystal(crystal)
    ring = Shape(atlas, parent=None, render_type=rt, bake_light=True, particle='block/nexus_core/crystal_particle')
    core_ring(ring)
    # the item has no renderer: bake the crystal and a level ring into the frame
    # In the inventory view the block centre lies behind the near top corner, so the item's crystal is
    # larger and sits a little lower to fill the windows.
    item = Shape(atlas, render_type=rt)
    core_frame(item, True)
    core_crystal(Scaled(item, (1.4, 1.0, 1.4), offset=(0, -0.2, 0)), tint=None)
    core_ring(Scaled(item, 1.0, offset=(0, -1.75, 0)), tint=None)
    atlas.pack()
    atlas.save()
    Tex(16, seed=7).noise(VIOLET, base=3, chance=0.4).save('block/nexus_core/crystal_particle')
    idle.save('block/nexus_core')
    on.save('block/nexus_core_active')
    crystal.save('block/nexus_core_crystal')
    ring.save('block/nexus_core_ring')
    item.save('item/nexus_core')
    blockstate_variants('nexus_core', 'active', {'false': 'block/nexus_core', 'true': 'block/nexus_core_active'})


# =================================================================================================
# Nexus Cable
# =================================================================================================

@painter('flange')
def p_flange(t):
    """Cable coupling face: steel ring around a dark socket."""
    plate(t, STEEL, base=2, hi=STEEL[5], lo=STEEL[0])
    if t.w >= 5 and t.h >= 5:
        t.rect(1, 1, t.w - 2, t.h - 2, GUNMETAL[1])
        t.rect(2, 2, t.w - 3, t.h - 3, BLACK)
        t.set(1, 1, STEEL[3])


# Material sets for the two cable families (same geometry, same tinted emissive light strips).
CABLE_STYLES = {
    'nexus': {'body': 'casing_dark', 'bracket': 'bracket', 'rail': 'steel', 'flange': 'flange'},
    'inventory': {'body': 'oakwood', 'bracket': 'copper_bracket', 'rail': 'copper', 'flange': 'flange_copper'},
}


@painter('oakwood')
def p_oakwood(t):
    """Oak with grain running along the long axis and darkened edges (inventory cable body)."""
    vertical = t.h > t.w
    t.noise(OAK, base=2, chance=0.3)
    n = t.w if vertical else t.h
    for i in range(n):
        if i % 3 == 1:
            for j in range(t.h if vertical else t.w):
                x, y = (i, j) if vertical else (j, i)
                if t.rng.random() < 0.7:
                    t.set(x, y, OAK[1])
    if t.w >= 2 and t.h >= 2:
        t.hline(0, t.w - 1, 0, OAK[3])
        t.vline(0, 0, t.h - 1, OAK[3])
        t.hline(0, t.w - 1, t.h - 1, OAK[0])
        t.vline(t.w - 1, 0, t.h - 1, OAK[0])


@painter('copper_bracket')
def p_copper_bracket(t):
    plate(t, COPPER, base=2, hi=COPPER[5], lo=COPPER[0])
    if t.w >= 3 and t.h >= 3:
        t.set(t.w // 2, t.h // 2, COPPER[4])


@painter('flange_copper')
def p_flange_copper(t):
    plate(t, COPPER, base=2, hi=COPPER[5], lo=COPPER[0])
    if t.w >= 5 and t.h >= 5:
        t.rect(1, 1, t.w - 2, t.h - 2, OAK[0])
        t.rect(2, 2, t.w - 3, t.h - 3, BLACK)
        t.set(1, 1, COPPER[3])


def cable_core(s: Shape, style='nexus'):
    m = CABLE_STYLES[style]
    s.box([5.5, 5.5, 5.5], [10.5, 10.5, 10.5], m['body'])
    for x in (5, 9.5):
        for y in (5, 9.5):
            for z in (5, 9.5):
                s.box([x, y, z], [x + 1.5, y + 1.5, z + 1.5], m['bracket'])
    lamp = dict(emissive=True, tint=0)
    s.box([5.25, 7, 7], [10.75, 9, 9], 'white', faces={'north': None, 'south': None, 'up': None, 'down': None}, **lamp)
    s.box([7, 5.25, 7], [9, 10.75, 9], 'white', faces={'north': None, 'south': None, 'east': None, 'west': None}, **lamp)
    s.box([7, 7, 5.25], [9, 9, 10.75], 'white', faces={'east': None, 'west': None, 'up': None, 'down': None}, **lamp)


def cable_arm(s: Shape, turns=0, style='nexus'):
    m = CABLE_STYLES[style]
    put = putter(s, turns)
    put([6, 6, 0], [10, 10, 5.5], m['body'], skip=('north', 'south'))
    for x in (5.5, 9.5):
        for y in (5.5, 9.5):
            put([x, y, 1], [x + 1, y + 1, 5.5], m['rail'], skip=('south',))
    lamp = dict(emissive=True, tint=0, skip=('north', 'south'))
    put([7.25, 9.75, 1], [8.75, 10.25, 5.5], 'white', **lamp)
    put([7.25, 5.75, 1], [8.75, 6.25, 5.5], 'white', **lamp)
    put([5.75, 7.25, 1], [6.25, 8.75, 5.5], 'white', **lamp)
    put([9.75, 7.25, 1], [10.25, 8.75, 5.5], 'white', **lamp)
    put([5.5, 5.5, 0], [10.5, 10.5, 1], m['bracket'], faces={'north': m['flange']})


def cable_block(name: str, style: str):
    """Core + arm models, the item (core with east/west arms) and the multipart blockstate."""
    atlas = Atlas(f'block/{name}')
    particle = 'block/network/casing' if style == 'nexus' else 'block/inventory_cable/particle'
    core = Shape(atlas, particle=particle)
    cable_core(core, style)
    arm = Shape(atlas, particle=particle)
    cable_arm(arm, style=style)
    item = Shape(atlas, particle=particle)
    cable_core(item, style)
    cable_arm(item, 1, style)
    cable_arm(item, 3, style)
    atlas.pack()
    atlas.save()
    core.save(f'block/{name}_core')
    arm.save(f'block/{name}_arm')
    item.display = kit.Model().block_display().display
    item.display['gui'] = {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]}
    item.save(f'item/{name}')
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    parts = [{'apply': {'model': f'{M}:block/{name}_core'}}]
    for direction, r in rot.items():
        parts.append({'when': {direction: 'true'}, 'apply': {'model': f'{M}:block/{name}_arm', **r}})
    kit.blockstate_multipart(name, parts)


def nexus_cable():
    cable_block('nexus_cable', 'nexus')


def inventory_cable():
    t = Tex(16, seed=61)
    p_oakwood(t)
    t.save('block/inventory_cable/particle')
    cable_block('inventory_cable', 'inventory')


# =================================================================================================
# Shared device chassis pieces
# =================================================================================================

@painter('side')
def p_side(t, accent='cyan'):
    """Device side panel: engraved inner panel, vent slots, a steel ID plate and status pips."""
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    w, h = t.w, t.h
    groove(t, 2, 2, w - 4, h - 4, dark=GUNMETAL[0], light=GUNMETAL[4])
    t.brushed(GUNMETAL, x0=3, y0=3, x1=w - 4, y1=h - 4, base=2)
    # steel nameplate with an engraved rule
    nx0, nx1 = w // 2 - 3, w // 2 + 2
    t.rect(nx0, 4, nx1, 6, STEEL[2])
    t.hline(nx0, nx1, 4, STEEL[4])
    t.vline(nx0, 4, 6, STEEL[3])
    t.hline(nx0, nx1, 6, STEEL[0])
    t.vline(nx1, 4, 6, STEEL[1])
    t.hline(nx0 + 1, nx1 - 2, 5, STEEL[1])
    t.set(nx1 - 1, 5, RAMPS[accent][3])     # indicator lamp in the plate
    # vent slots
    for y in (h - 8, h - 6):
        t.hline(4, w - 6, y, BLACK)
        t.hline(4, w - 6, y + 1, GUNMETAL[3])
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        t.set(x, y, STEEL[3])


@painter('top')
def p_top(t):
    """Device top: framed grille with rivets."""
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    w, h = t.w, t.h
    t.rect(3, 3, w - 4, h - 4, GUNMETAL[1])
    for y in range(4, h - 4):
        for x in range(4, w - 4):
            if (x + y) % 2 == 0 and y % 2 == 0:
                t.set(x, y, BLACK)
            elif y % 2 == 0:
                t.set(x, y, GUNMETAL[0])
            else:
                t.set(x, y, GUNMETAL[2])
    t.hline(3, w - 4, 3, GUNMETAL[0])
    t.vline(3, 3, h - 4, GUNMETAL[0])
    t.hline(3, w - 4, h - 3, GUNMETAL[4])
    t.vline(w - 3, 3, h - 3, GUNMETAL[4])
    for x, y in ((1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)):
        rivet(t, x, y)


@painter('back')
def p_back(t):
    """Device back: panel with a cable socket in the middle."""
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    w, h = t.w, t.h
    groove(t, 1, 1, w - 3, h - 3, dark=GUNMETAL[0], light=GUNMETAL[3])
    cx, cy = w // 2, h // 2
    port = paint('port:cyan', 6, 6)
    t.paste(port, cx - 3, cy - 3)
    for x in (3, w - 5):
        t.vline(x, 4, h - 5, GUNMETAL[1])
        t.vline(x + 1, 4, h - 5, GUNMETAL[3])
    for x, y in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
        rivet(t, x, y)


def seam(s: Shape, active: bool, ramp='cyan'):
    """The glowing seam that rings every device between its front frame and its chassis (1px wide and
    only 0.5px deep so it stays visible at the 45 degree inventory angle)."""
    s.box([0.5, 0.5, 2], [15.5, 15.5, 3], f'glow:{ramp}' if active else f'dim:{ramp}', emissive=active,
          skip=('north', 'south'))


def chassis(s: Shape, side='side', top='top', back='back'):
    s.box([0, 0, 3], [16, 16, 16], 'casing', faces={'north': None, 'east': side, 'west': side, 'up': top,
                                                    'south': back})


# =================================================================================================
# Drive
# =================================================================================================

BAYS = [((8.5 if i % 2 == 0 else 2.0), 11.0 - 2.5 * (i // 2)) for i in range(10)]


@painter('bay_back')
def p_bay_back(t):
    """Back wall of the drive bays (12x12, viewer's top-left = block x 14, y 13): sockets per bay."""
    t.noise([BLACK, BLACK, GUNMETAL[0], GUNMETAL[1]], base=1, chance=0.2)
    for bx, by in BAYS:
        u = int(round(14 - (bx + 5.5)))
        v = int(round(13 - (by + 2)))
        t.hline(u, u + 5, v, BLACK)
        t.set(u + 1, v + 1, STEEL[1])
        t.set(u + 4, v + 1, STEEL[1])
        t.hline(u + 2, u + 3, v + 1, GUNMETAL[2])


@painter('cell_front')
def p_cell_front(t):
    plate(t, GUNMETAL, base=3, hi=STEEL[3], lo=GUNMETAL[0], brushed=False)
    if t.w >= 5:
        t.vline(t.w - 2, 0, t.h - 1, GUNMETAL[1])


@painter('tier')
def p_tier(t, hex_colour='9aa0a8'):
    r = ramp_from(hex_colour)
    plate(t, r, base=3, hi=r[4], lo=r[1], brushed=False, noise=0.15)


@painter('pillar_front')
def p_pillar_front(t):
    plate(t, STEEL, base=2, hi=STEEL[4], lo=STEEL[0])
    w, h = t.w, t.h
    for y in (1, h - 3):
        if w >= 2:
            rivet(t, 0 if w < 3 else (w - 2) // 2, y)
    for y in range(4, h - 4, 2):
        t.set(w // 2, y, STEEL[1])


def drive_cell(s, light=None, tint=0, stripe='white', led=None):
    """A storage cartridge authored at a bay origin (the bay's bottom-left-front corner is 0,0,0)."""
    s.box([0.25, 0.25, 0.4], [5.25, 1.75, 2.0], 'casing', faces={'north': 'cell_front', 'up': 'steel', 'south': None},
          light=light)
    s.box([0.55, 0.5, 0.3], [3.75, 1.5, 0.4], stripe, tint=tint, skip=('south',), light=light)
    if led:
        s.box([4.2, 0.6, 0.25], [5.0, 1.4, 0.45], led, emissive=True, skip=('south',))


def drive_model(s: Shape, active: bool):
    chassis(s)
    seam(s, active)
    s.box([2, 1, 2], [14, 13, 3], 'black', faces={'north': 'bay_back'}, skip=('south', 'east', 'west', 'up', 'down'))
    for x in (0, 14):
        s.box([x, 0, 0], [x + 2, 16, 2], 'casing', faces={'north': 'pillar_front', 'east' if x == 0 else 'west': 'casing_dark'})
    s.box([2, 15, 0], [14, 16, 2], 'casing', faces={'north': 'steel'})
    s.box([2, 13, 0], [14, 14, 2], 'casing', faces={'north': 'steel', 'down': 'casing_dark'})
    s.box([2, 14, 0], [3.5, 15, 2], 'steel', skip=('up', 'down'))
    s.box([12.5, 14, 0], [14, 15, 2], 'steel', skip=('up', 'down'))
    if active:
        s.box([3.5, 14, 0.5], [12.5, 15, 2], 'black', faces={'north': ('#status', [0, 0, 9, 1])}, emissive=True,
              skip=('south', 'east', 'west', 'up', 'down'))
    else:
        s.box([3.5, 14, 0.5], [12.5, 15, 2], 'black', faces={'north': 'dim:cyan'}, skip=('south', 'east', 'west', 'up', 'down'))
    s.box([2, 0, 0], [14, 1, 2], 'casing', faces={'north': 'steel', 'up': 'casing_dark'})
    s.box([7.5, 1, 0], [8.5, 13, 2], 'casing_dark', faces={'north': 'steel'})
    for y in (3, 5.5, 8, 10.5):
        s.box([2, y, 0], [14, y + 0.5, 2], 'casing_dark', faces={'north': 'steel_dark'})


def drive_status_texture():
    frames = []
    for f in range(12):
        t = Tex(16, seed=f)
        t.fill(CYAN[0])
        for x in range(9):
            t.set(x, 0, CYAN[1] if x % 3 == 2 else CYAN[2])
        head = f * 9 // 12 if f < 12 else 0
        for d, c in ((0, CYAN[5]), (1, CYAN[4]), (2, CYAN[3])):
            x = head - d
            if 0 <= x < 9:
                t.set(x, 0, c)
        frames.append(t)
    kit.save_animation('block/drive/status', frames, frametime=2, interpolate=False)


def drive():
    atlas = Atlas('block/drive')
    idle = Shape(atlas)
    drive_model(idle, False)
    on = Shape(atlas, textures={'status': 'block/drive/status'})
    drive_model(on, True)
    cell = Shape(atlas, parent=None, render_type='minecraft:cutout', bake_light=True)
    drive_cell(cell)
    led = Shape(atlas, parent=None, render_type='minecraft:cutout', particle='block/network/casing')
    led.box([4.2, 0.6, 0.25], [5.0, 1.4, 0.45], 'lamp', tint=0, emissive=True, skip=('south',))
    item = Shape(atlas, textures={'status': 'block/drive/status'})
    drive_model(item, True)
    fills = {0: ('f5c542', 'glow:green'), 1: ('4de8e0', 'glow:green'), 2: ('a46bff', 'glow:amber'),
             3: ('9aa0a8', 'glow:green'), 5: ('5be08a', 'glow:green'), 6: ('ff6bd5', 'glow:red')}
    cell_parts = {}
    for slot, (colour, led_mat) in fills.items():
        part = Shape(atlas, parent=None)
        drive_cell(part, stripe=f'tier:{colour}', tint=None, led=led_mat)
        cell_parts[slot] = part
    drive_status_texture()
    atlas.pack()
    atlas.save()
    for slot, part in cell_parts.items():
        bx, by = BAYS[slot]
        item.include(part.finalised(), (bx, by, 0))
    idle.save('block/drive')
    on.save('block/drive_active')
    cell.save('block/drive_cell')
    led.save('block/drive_cell_led')
    item.save('item/drive')
    kit.blockstate_horizontal('drive', extra={'active=false': 'block/drive', 'active=true': 'block/drive_active'})


# =================================================================================================
# Terminals
# =================================================================================================

ITEM_ICONS = [  # tiny 2x2 "items" on terminal screens: (top-left, top-right, bottom-left, bottom-right)
    (rgb('a3fff8'), rgb('4de8e0'), rgb('1fb5ad'), rgb('12706c')),   # diamond
    (rgb('ffe082'), rgb('f5c542'), rgb('d9a21c'), rgb('9c6a0e')),   # gold
    (rgb('ff6b6b'), rgb('b82a2a'), rgb('b82a2a'), rgb('701717')),   # redstone
    (rgb('a6f5c0'), rgb('22a34a'), rgb('22a34a'), rgb('15632d')),   # emerald
    (rgb('e3e6ea'), rgb('c4c9cf'), rgb('9aa0a8'), rgb('6b7078')),   # iron
    (rgb('8cc0ff'), rgb('2a62b8'), rgb('2a62b8'), rgb('163a73')),   # lapis
    (rgb('c9a6ff'), rgb('a46bff'), rgb('6a35a8'), rgb('3f1d63')),   # omnium
    (rgb('d4b07a'), rgb('a07943'), rgb('a07943'), rgb('5c4020')),   # planks
]
SCREEN_BG = rgb('051a22')


def icon(t: Tex, x, y, colours):
    t.set(x, y, colours[0])
    t.set(x + 1, y, colours[1])
    t.set(x, y + 1, colours[2])
    t.set(x + 1, y + 1, colours[3])


def screen_base(t: Tex, w=12, h=11):
    t.rect(0, 0, w - 1, h - 1, SCREEN_BG)
    for y in range(0, h, 2):
        t.hline(0, w - 1, y, mix(SCREEN_BG, CYAN[0], 0.6))
    t.outline(0, 0, w - 1, h - 1, CYAN[1])


def terminal_screen_texture():
    frames = []
    slots = [(1, 3), (4, 3), (7, 3), (1, 6), (4, 6), (7, 6)]
    for f in range(8):
        t = Tex(16, seed=100 + f)
        screen_base(t)
        t.hline(1, 6, 1, CYAN[1])                         # search field
        if f % 2 == 0:
            t.set(2 + (f // 2) % 4, 1, CYAN[4])            # typing cursor
        for i in range(2, 2 + (f // 2) % 4):
            t.set(i, 1, CYAN[3])
        t.set(8, 1, CYAN[3])                               # sort toggle
        t.set(9, 1, CYAN[2])
        for n, (x, y) in enumerate(slots):
            icon(t, x, y, ITEM_ICONS[(n + f // 4) % len(ITEM_ICONS)])
        sx, sy = slots[f % len(slots)]
        t.outline(sx - 1, sy - 1, sx + 2, sy + 2, CYAN[4])  # selection box hops along the grid
        icon(t, sx, sy, ITEM_ICONS[(f % len(slots) + f // 4) % len(ITEM_ICONS)])
        t.vline(10, 2, 8, CYAN[0])                          # scrollbar track + thumb
        thumb = 2 + (f % 4)
        t.vline(10, thumb, thumb + 2, CYAN[3])
        for x in range(1, 10):                              # capacity bar
            t.set(x, 9, CYAN[3] if x < 7 else CYAN[1])
        frames.append(t)
    kit.save_animation('block/terminal/screen', frames, frametime=5, interpolate=False)


def crafting_screen_texture():
    """A 3x3 crafting grid filling with planks, an arrow lighting up and a crafted workbench appearing."""
    frames = []
    cells = [(3 * c, 1 + 3 * r) for r in range(3) for c in range(3)]
    plank = ITEM_ICONS[7]
    table = (rgb('d4b07a'), rgb('a07943'), rgb('7d5a31'), rgb('3b2913'))
    order = [0, 1, 3, 4]
    slot = mix(SCREEN_BG, CYAN[1], 0.75)
    for f in range(10):
        t = Tex(16, seed=200 + f)
        t.rect(0, 0, 11, 10, SCREEN_BG)
        for y in range(0, 11, 2):
            t.hline(0, 11, y, mix(SCREEN_BG, CYAN[0], 0.6))
        for x, y in cells:
            t.rect(x, y, x + 1, y + 1, slot)
        filled = order[:min(f, 4)] if f < 9 else []
        for k in filled:
            icon(t, *cells[k], plank)
        crafted = f >= 5
        arrow = AMBER[3] if crafted else CYAN[1]
        for x, y in ((8, 3), (8, 4), (8, 5), (9, 4)):
            t.set(x, y, arrow)
        t.rect(10, 3, 11, 5, slot)
        t.hline(10, 11, 3, AMBER[2] if crafted else CYAN[1])
        if crafted:
            icon(t, 10, 4, table if f != 5 else (AMBER[5], AMBER[4], AMBER[4], AMBER[3]))
        for x in range(12):
            t.set(x, 10, AMBER[3] if x < min(12, f * 2 + 1) and f < 9 else CYAN[1])
        frames.append(t)
    kit.save_animation('block/crafting_terminal/screen', frames, frametime=6, interpolate=False)


@painter('screen_off')
def p_screen_off(t):
    t.fill(rgb('070b10'))
    for x in range(t.w):
        for y in range(t.h):
            if (x - y) in (1, 2) or (x - y) == 5:
                t.set(x, y, rgb('141c26'))
    t.outline(0, 0, t.w - 1, t.h - 1, GUNMETAL[1])


@painter('control')
def p_control(t):
    """Control strip under a screen, with a speaker grille at its right end."""
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    w, h = t.w, t.h
    for x in range(w - 6, w - 2):
        if x % 2 == 0:
            t.vline(x, 1, h - 2, BLACK)


@painter('craft_top')
def p_craft_top(t):
    """Workbench top: oak planks framed in steel with a 3x3 grid inlay."""
    p_wood(t)
    w, h = t.w, t.h
    t.outline(0, 0, w - 1, h - 1, STEEL[2])
    t.hline(0, w - 1, 0, STEEL[4])
    t.vline(0, 0, h - 1, STEEL[4])
    x0, y0 = (w - 10) // 2, (h - 10) // 2
    for i in range(4):
        t.hline(x0, x0 + 9, y0 + i * 3, OAK[0])
        t.vline(x0 + i * 3, y0, y0 + 9, OAK[0])
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        t.set(x, y, STEEL[3])


@painter('craft_side')
def p_craft_side(t):
    """Side panel with an oak inlay holding a saw and hammer, like a crafting table's side."""
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    w, h = t.w, t.h
    inlay = paint('wood', w - 4, h - 5)
    t.paste(inlay, 2, 3)
    t.outline(2, 3, w - 3, h - 3, GUNMETAL[0])
    # saw: wooden handle, steel blade with teeth on its right edge
    t.rect(4, 5, 6, 6, OAK[1])
    t.set(5, 5, OAK[0])
    for y in range(7, h - 4):
        t.set(4, y, STEEL[4])
        t.set(5, y, STEEL[2])
        if y % 2:
            t.set(6, y, STEEL[1])
    t.set(5, h - 4, STEEL[2])
    # hammer: steel head on a wooden haft
    hx = w - 7
    t.rect(hx - 1, 5, hx + 2, 6, STEEL[2])
    t.hline(hx - 1, hx + 2, 5, STEEL[4])
    t.set(hx - 1, 6, STEEL[1])
    for y in range(7, h - 4):
        t.set(hx, y, OAK[1])
        t.set(hx + 1, y, OAK[0])
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        t.set(x, y, STEEL[3])


def terminal_model(s: Shape, active: bool, crafting: bool):
    accent_corner = 'copper' if crafting else 'bracket'
    side = 'craft_side' if crafting else 'side'
    top = 'craft_top' if crafting else 'top'
    chassis(s, side=side, top=top)
    seam(s, active, 'amber' if crafting else 'cyan')
    # bezel (front at z 0.5) with corner brackets standing proud at z 0
    s.box([0, 3, 0.5], [2, 13, 2], 'casing', faces={'north': 'steel', 'east': 'casing_dark'})
    s.box([14, 3, 0.5], [16, 13, 2], 'casing', faces={'north': 'steel', 'west': 'casing_dark'})
    s.box([3, 14, 0.5], [13, 16, 2], 'casing', faces={'north': 'wood' if crafting else 'steel', 'down': 'casing_dark'})
    s.box([3, 0, 0.5], [13, 3, 2], 'casing', faces={'north': 'wood' if crafting else 'control', 'up': 'casing_dark'})
    for x, y in ((0, 0), (13, 0), (0, 13), (13, 13)):
        s.box([x, y, 0], [x + 3, y + 3, 2], accent_corner, skip=('south',))
    # inner bezel fillers between the brackets
    s.box([2, 3, 1], [3, 14, 2], 'casing_dark', skip=('south', 'up', 'down'))
    s.box([13, 3, 1], [14, 14, 2], 'casing_dark', skip=('south', 'up', 'down'))
    s.box([3, 13, 1], [13, 14, 2], 'casing_dark', skip=('south', 'east', 'west'))
    tex = '#screen'
    if active:
        s.box([2, 3, 1.5], [14, 14, 2], 'black', faces={'north': (tex, [0, 0, 12, 11])}, emissive=True,
              skip=('south', 'east', 'west', 'up', 'down'))
    else:
        s.box([2, 3, 1.5], [14, 14, 2], 'black', faces={'north': 'screen_off'}, skip=('south', 'east', 'west', 'up', 'down'))
    # status lamp and keys on the lower bezel
    lamp = 'glow:amber' if crafting else 'glow:green'
    s.box([10.5, 1, 0.25], [12, 2, 0.5], lamp if active else 'dim:green', emissive=active, skip=('south',))
    if not crafting:
        s.box([4, 1, 0.25], [5.5, 2, 0.5], 'steel_hi', skip=('south',))
        s.box([6, 1, 0.25], [7.5, 2, 0.5], 'steel', skip=('south',))
    else:
        s.box([4, 0.75, 0.25], [8, 2.25, 0.5], 'copper', skip=('south',))


def terminals():
    terminal_screen_texture()
    crafting_screen_texture()
    for name, crafting in (('terminal', False), ('crafting_terminal', True)):
        atlas = Atlas(f'block/{name}')
        textures = {'screen': f'block/{name}/screen'}
        idle = Shape(atlas, textures=textures)
        terminal_model(idle, False, crafting)
        on = Shape(atlas, textures=textures)
        terminal_model(on, True, crafting)
        atlas.pack()
        atlas.save()
        idle.save(f'block/{name}')
        on.save(f'block/{name}_active')
        kit.item_model(name, parent=f'block/{name}_active')
        kit.blockstate_horizontal(name, extra={'active=false': f'block/{name}', 'active=true': f'block/{name}_active'})


# =================================================================================================
# Storage Monitor
# =================================================================================================

@painter('monitor_screen')
def p_monitor_screen(t, on='1'):
    """Large, quiet display: near-black glass, faint scanlines and corner ticks (the BER draws on it)."""
    lit = on == '1'
    bg = rgb('06121a') if lit else rgb('06090d')
    t.fill(bg)
    for y in range(0, t.h, 2):
        t.hline(0, t.w - 1, y, mix(bg, CYAN[0], 0.2 if lit else 0.08))
    if lit:
        c = mix(CYAN[1], bg, 0.3)
        for x, y, dx, dy in ((1, 1, 1, 1), (t.w - 2, 1, -1, 1), (1, t.h - 2, 1, -1), (t.w - 2, t.h - 2, -1, -1)):
            t.set(x, y, c)
            t.set(x + dx, y, c)
            t.set(x, y + dy, c)
    for x in range(t.w):
        if (x * 7) % 5 == 0 and t.h > 4:
            t.set(x, 0, mix(bg, (255, 255, 255, 255), 0.06))
    t.set(1, 1 if not lit else 2, mix(bg, (255, 255, 255, 255), 0.12))


def storage_monitor_model(s: Shape, active: bool):
    """A flat screen at z = 1 inside a 1px steel bezel; the block entity renderer draws just in front of it."""
    chassis(s)
    seam(s, active)
    s.box([1, 1, 1], [15, 15, 2], 'black', faces={'north': f'monitor_screen:{1 if active else 0}'},
          skip=('south', 'east', 'west', 'up', 'down'))
    for f, t in (([0, 0, 0], [1, 16, 2]), ([15, 0, 0], [16, 16, 2]), ([1, 15, 0], [15, 16, 2]), ([1, 0, 0], [15, 1, 2])):
        s.box(f, t, 'steel', faces={'south': None})
    glow = 'glow:cyan' if active else 'dim:cyan'
    for f, t in (([1, 1, 0.5], [1.5, 15, 1]), ([14.5, 1, 0.5], [15, 15, 1]), ([1.5, 14.5, 0.5], [14.5, 15, 1]),
                 ([1.5, 1, 0.5], [14.5, 1.5, 1])):
        s.box(f, t, glow, emissive=active, skip=('south',))


def storage_monitor():
    atlas = Atlas('block/storage_monitor')
    idle = Shape(atlas)
    storage_monitor_model(idle, False)
    on = Shape(atlas)
    storage_monitor_model(on, True)
    atlas.pack()
    atlas.save()
    idle.save('block/storage_monitor')
    on.save('block/storage_monitor_active')
    kit.item_model('storage_monitor', parent='block/storage_monitor_active')
    kit.blockstate_horizontal('storage_monitor', extra={'active=false': 'block/storage_monitor',
                                                        'active=true': 'block/storage_monitor_active'})


# =================================================================================================
# Buses, Destructor and Constructor (pointing devices; models face north, z = 0 touches the target)
# =================================================================================================

@painter('chev')
def p_chev(t, ramp='cyan', direction='l'):
    """Glowing chevrons on a dark band. direction: the way they point in texture space (l/r/u/d)."""
    r = RAMPS[ramp]
    w, h = t.w, t.h
    t.fill(r[0])
    along_u = direction in 'lr'
    n_along, n_across = (w, h) if along_u else (h, w)
    for a in range(n_across):
        for b in range(n_along):
            centre = (n_across - 1) / 2
            dist = abs(a - centre)
            pos = b if direction in 'lu' else n_along - 1 - b
            phase = (pos - dist) % 4
            if phase < 1:
                c = r[5] if dist < 1 else r[4]
            elif phase < 2:
                c = r[2]
            else:
                c = r[0] if (a + b) % 3 else r[1]
            x, y = (b, a) if along_u else (a, b)
            t.set(x, y, c)


@painter('dots')
def p_dots(t, ramp='violet'):
    r = RAMPS[ramp]
    t.fill(r[0])
    for y in range(t.h):
        for x in range(t.w):
            if (x + 2 * y) % 4 == 0:
                t.set(x, y, r[4])
            elif (x + 2 * y) % 4 == 1:
                t.set(x, y, r[2])


@painter('glow_hazard')
def p_glow_hazard(t, ramp='amber'):
    r = RAMPS[ramp]
    for y in range(t.h):
        for x in range(t.w):
            t.set(x, y, r[3] if ((x + y) // 2) % 2 == 0 else rgb('1a0c06'))


@painter('plate_front')
def p_plate_front(t, style='steel'):
    if style == 'hazard':
        p_hazard(t)
        t.outline(0, 0, t.w - 1, t.h - 1, GUNMETAL[0])
        return
    plate(t, STEEL, base=2, hi=STEEL[4], lo=STEEL[0])
    if t.w >= 4 and t.h >= 4:
        rivet(t, 1, 1)


@painter('ring')
def p_ring(t, ramp='steel'):
    """A square-ish ring with a transparent middle (for cutout models)."""
    r = RAMPS[ramp]
    plate(t, r, base=2 if ramp in ('steel', 'gunmetal') else 3, brushed=False)
    if t.w >= 5 and t.h >= 5:
        t.rect(2, 2, t.w - 3, t.h - 3, CLEAR)
        t.hline(2, t.w - 3, 1, r[1])
        t.vline(1, 2, t.h - 3, r[1])


@painter('blade')
def p_blade(t):
    """Cutter blade: steel with serrated, red-hot edges."""
    plate(t, STEEL, base=3, hi=STEEL[5], lo=STEEL[1])
    for x in range(0, t.w, 2):
        t.set(x, 0, RED[3])
        if t.h > 1:
            t.set(x, t.h - 1, RED[2])


@painter('grid')
def p_grid(t, ramp='green'):
    r = RAMPS[ramp]
    t.fill(r[0])
    for y in range(t.h):
        for x in range(t.w):
            if x % 2 == 0 and y % 2 == 0:
                t.set(x, y, r[4] if (x + y) % 4 == 0 else r[3])
            elif x % 2 == 0 or y % 2 == 0:
                t.set(x, y, r[1])
    t.set(t.w // 2, t.h // 2, r[5])


def bus_frame(s: Shape, ramp: str, band: dict, front_z=0.0, plate_style='steel'):
    """Plate edges, body with an accent band, and a neck reaching the back face."""
    pf = f'plate_front:{plate_style}'
    for f, t in (([1, 1, front_z], [15, 4, 3]), ([1, 12, front_z], [15, 15, 3]),
                 ([1, 4, front_z], [4, 12, 3]), ([12, 4, front_z], [15, 12, 3])):
        s.box(f, t, 'casing', faces={'north': pf, 'south': 'bracket'})
    # body: steel collars around an accent band (emissive markings)
    s.box([3, 3, 3], [13, 13, 4], 'steel', skip=('north',))
    s.box([3.5, 3.5, 4], [12.5, 12.5, 7], 'black', faces=band, emissive=True, skip=('north', 'south'))
    s.box([3, 3, 7], [13, 13, 8], 'steel', faces={'south': 'panel'}, skip=('north',))
    # neck with steel rings and accent light strips, ending in a cable flange
    s.box([5.5, 5.5, 8], [10.5, 10.5, 15], 'casing_dark', skip=('north', 'south'))
    s.box([5, 5, 10], [11, 11, 11], 'steel', skip=('north',))
    for f, t in (([7.25, 10.25, 11], [8.75, 10.75, 15]), ([7.25, 5.25, 11], [8.75, 5.75, 15]),
                 ([5.25, 7.25, 11], [5.75, 8.75, 15]), ([10.25, 7.25, 11], [10.75, 8.75, 15])):
        s.box(f, t, f'glow:{ramp}', emissive=True, skip=('north', 'south'))
    s.box([5, 5, 15], [11, 11, 16], 'bracket', faces={'south': 'flange'})


def band_faces(ramp, toward_target: bool | None):
    """Accent band markings: chevrons pointing toward the target (-z) or the network (+z), or dots."""
    if toward_target is None:
        return {f: f'dots:{ramp}' for f in ('east', 'west', 'up', 'down')}
    if toward_target:
        return {'east': f'chev:{ramp}:r', 'west': f'chev:{ramp}:l', 'up': f'chev:{ramp}:u', 'down': f'chev:{ramp}:d'}
    return {'east': f'chev:{ramp}:l', 'west': f'chev:{ramp}:r', 'up': f'chev:{ramp}:d', 'down': f'chev:{ramp}:u'}


def import_bus(s: Shape):
    bus_frame(s, 'cyan', band_faces('cyan', False))
    # an intake funnel stepping inward to a glowing throat
    ring = [([4, 4], [12, 5.5]), ([4, 10.5], [12, 12]), ([4, 5.5], [5.5, 10.5]), ([10.5, 5.5], [12, 10.5])]
    for (x0, y0), (x1, y1) in ring:
        s.box([x0, y0, 0.75], [x1, y1, 3], 'steel', skip=('south',))
    ring2 = [([5.5, 5.5], [10.5, 6.5]), ([5.5, 9.5], [10.5, 10.5]), ([5.5, 6.5], [6.5, 9.5]), ([9.5, 6.5], [10.5, 9.5])]
    for (x0, y0), (x1, y1) in ring2:
        s.box([x0, y0, 1.75], [x1, y1, 3], 'steel_dark', skip=('south',))
    s.box([6.5, 6.5, 2.5], [9.5, 9.5, 3], 'black', faces={'north': 'grid:cyan'}, emissive=True, skip=('south',))


def export_bus(s: Shape):
    bus_frame(s, 'amber', band_faces('amber', True), front_z=1.0)
    # a nozzle standing proud of the plate, with a glowing bore
    s.box([4.5, 4.5, 1], [11.5, 11.5, 3], 'steel_dark', skip=('south',))
    s.box([5.5, 5.5, 0], [10.5, 10.5, 1], 'steel_hi', faces={'north': 'ring:steel'}, skip=('south',))
    s.box([6.5, 6.5, 0.5], [9.5, 9.5, 1], 'glow:amber', emissive=True, skip=('south',))


def storage_bus(s: Shape):
    bus_frame(s, 'violet', band_faces('violet', None))
    # a lens housing ringed with violet light
    s.box([4, 4, 1], [12, 12, 3], 'casing', skip=('south',))
    for f, t in (([4.5, 4.5, 0.5], [11.5, 5.25, 1]), ([4.5, 10.75, 0.5], [11.5, 11.5, 1]),
                 ([4.5, 5.25, 0.5], [5.25, 10.75, 1]), ([10.75, 5.25, 0.5], [11.5, 10.75, 1])):
        s.box(f, t, 'glow:violet', emissive=True, skip=('south',))
    s.box([6, 6, 0.25], [10, 10, 1], 'gem:violet', emissive=True, skip=('south',))


def destructor(s: Shape):
    bus_frame(s, 'red', {f: 'glow_hazard:amber' for f in ('east', 'west', 'up', 'down')}, plate_style='hazard')
    # a red-hot grinding head: crossed serrated blades on a hub
    s.box([4, 4, 1.25], [12, 12, 3], 'recess', skip=('south',))
    s.box([4.5, 4.5, 1], [11.5, 11.5, 1.25], 'glow:red', emissive=True, skip=('south',))
    for angle in (45, -45):
        s.box([3.6, 7.25, 0.25], [12.4, 8.75, 1], 'blade', rot=(angle, 'z', [8, 8, 0.5]), skip=('south',))
    s.box([6.75, 6.75, 0], [9.25, 9.25, 1], 'bracket', skip=('south',))


def constructor(s: Shape):
    bus_frame(s, 'green', band_faces('green', True), front_z=0.5)
    # a projector: emitter grid behind a framed cyan lens ring
    s.box([4, 4, 1], [12, 12, 3], 'casing', skip=('south',))
    s.box([4.75, 4.75, 0.75], [11.25, 11.25, 1], 'grid:green', emissive=True, skip=('south',))
    for f, t in (([4, 4, 0], [12, 4.75, 0.75]), ([4, 11.25, 0], [12, 12, 0.75]),
                 ([4, 4.75, 0], [4.75, 11.25, 0.75]), ([11.25, 4.75, 0], [12, 11.25, 0.75])):
        s.box(f, t, 'glow:cyan', emissive=True, skip=('south',))
    s.box([7.5, 4.75, 0.5], [8.5, 11.25, 0.75], 'steel', skip=('south',))
    s.box([4.75, 7.5, 0.5], [11.25, 8.5, 0.75], 'steel', skip=('south',))


def pointing_devices():
    for name, build in (('import_bus', import_bus), ('export_bus', export_bus), ('storage_bus', storage_bus),
                        ('destructor', destructor), ('constructor', constructor)):
        atlas = Atlas(f'block/{name}')
        s = Shape(atlas, render_type='minecraft:cutout')
        build(s)
        atlas.pack()
        atlas.save()
        s.save(f'block/{name}')
        kit.item_model(name, parent=f'block/{name}')
        kit.blockstate_facing(name)


# =================================================================================================
# Network Port
# =================================================================================================

@painter('hatch')
def p_hatch(t):
    """Recessed hatch plate between the frame beams: diagonal braces and bolts."""
    plate(t, GUNMETAL, base=1, hi=GUNMETAL[3], lo=BLACK)
    w, h = t.w, t.h
    groove(t, 2, 2, w - 4, h - 4, dark=BLACK, light=GUNMETAL[3])
    for i in range(3, min(w, h) - 3):
        if i < 5 or i > min(w, h) - 6:
            t.set(i, i, GUNMETAL[3])
            t.set(w - 1 - i, i, GUNMETAL[3])
    for x, y in ((3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)):
        rivet(t, x, y)


def port_model(s: Shape, active: bool):
    s.box([1, 1, 1], [15, 15, 15], 'hatch')
    beams = []
    for a in (0, 14):
        for b in (0, 14):
            beams.append(([0, a, b], [16, a + 2, b + 2]))      # along x
            beams.append(([a, 0, b], [a + 2, 16, b + 2]))      # along y
            beams.append(([a, b, 0], [a + 2, b + 2, 16]))      # along z
    for f, t in beams:
        s.box(f, t, 'pillar')
    glow = 'glow:cyan' if active else 'dim:cyan'
    sockets = {
        'north': ([5, 5, 0.5], [11, 11, 1], [7, 7, 0.75], [9, 9, 1]),
        'south': ([5, 5, 15], [11, 11, 15.5], [7, 7, 15], [9, 9, 15.25]),
        'west': ([0.5, 5, 5], [1, 11, 11], [0.75, 7, 7], [1, 9, 9]),
        'east': ([15, 5, 5], [15.5, 11, 11], [15, 7, 7], [15.25, 9, 9]),
        'up': ([5, 15, 5], [11, 15.5, 11], [7, 15, 7], [9, 15.25, 9]),
        'down': ([5, 0.5, 5], [11, 1, 11], [7, 0.75, 7], [9, 1, 9]),
    }
    for face, (f, t, gf, gt) in sockets.items():
        if face == 'north':
            continue
        s.box(f, t, 'steel', faces={face: 'ring:steel'})
        s.box(gf, gt, glow, emissive=active)
    # the front is a wide intake: hazard-framed iris around a bright core
    s.box([3, 3, 0.5], [13, 13, 1], 'steel_dark', faces={'north': 'glow_hazard:amber'}, skip=('south',),
          emissive=active)
    s.box([4.5, 4.5, 0.25], [11.5, 11.5, 1], 'steel', faces={'north': 'ring:steel'}, skip=('south',))
    s.box([6, 6, 0.5], [10, 10, 1], glow, faces={'north': 'grid:cyan' if active else 'dim:cyan'}, emissive=active,
          skip=('south',))


def network_port():
    atlas = Atlas('block/network_port')
    idle = Shape(atlas, render_type='minecraft:cutout')
    port_model(idle, False)
    on = Shape(atlas, render_type='minecraft:cutout')
    port_model(on, True)
    atlas.pack()
    atlas.save()
    idle.save('block/network_port')
    on.save('block/network_port_active')
    kit.item_model('network_port', parent='block/network_port_active')
    kit.blockstate_horizontal('network_port', extra={'active=false': 'block/network_port',
                                                     'active=true': 'block/network_port_active'})


# =================================================================================================
# Signal Emitter
# =================================================================================================

def emitter_model(s: Shape, powered: bool):
    s.box([3, 3, 14], [13, 13, 16], 'bracket', faces={'north': 'steel', 'south': 'flange'})
    s.box([3.5, 3.5, 12], [12.5, 12.5, 14], 'casing', faces={'north': 'panel'})
    s.box([5.5, 5.5, 6], [10.5, 10.5, 12], 'coil', skip=('north', 'south'))
    for z in (6, 8.5, 11):
        s.box([5, 5, z], [11, 11, z + 1], 'steel', faces={'north': 'bracket'})
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        s.box([x, y, 1], [x + 1, y + 1, 6], 'steel_hi', skip=('south',))
    lamp = 'glow:red' if powered else 'dim:red'
    s.box([6.5, 6.5, 1.5], [9.5, 9.5, 6], lamp, emissive=powered, skip=('south',))
    s.box([6, 6, 1], [10, 10, 1.5], 'steel', faces={'north': 'ring:steel'}, skip=('south',))
    s.box([7, 7, 1.25], [9, 9, 1.5], 'glow:red' if powered else 'dim:red', emissive=powered, skip=('south',))


def signal_emitter():
    atlas = Atlas('block/signal_emitter')
    off = Shape(atlas, render_type='minecraft:cutout')
    emitter_model(off, False)
    on = Shape(atlas, render_type='minecraft:cutout')
    emitter_model(on, True)
    atlas.pack()
    atlas.save()
    off.save('block/signal_emitter')
    on.save('block/signal_emitter_powered')
    kit.item_model('signal_emitter', parent='block/signal_emitter_powered')
    kit.blockstate_facing('signal_emitter', extra={'powered=false': 'block/signal_emitter',
                                                   'powered=true': 'block/signal_emitter_powered'})


# =================================================================================================
# Wireless Transmitter
# =================================================================================================

@painter('deck')
def p_deck(t):
    plate(t, STEEL, base=2, hi=STEEL[4], lo=STEEL[0])
    w, h = t.w, t.h
    for i in range(2, w - 2, 2):
        t.vline(i, 2, h - 3, STEEL[1])
    t.outline(1, 1, w - 2, h - 2, STEEL[0])


def transmitter_model(s: Shape, active: bool):
    glow = 'glow:cyan' if active else 'dim:cyan'
    s.box([1, 0, 1], [15, 2, 15], 'band', faces={'up': 'panel', 'down': 'casing'})
    s.box([2, 2, 2], [14, 3.25, 14], 'steel', faces={'up': 'deck'}, skip=('down',))
    s.box([4, 3.25, 4], [12, 4, 12], 'bracket', skip=('down',))
    for x, z in ((1.5, 1.5), (13.5, 1.5), (1.5, 13.5), (13.5, 13.5)):
        s.box([x, 2, z], [x + 1, 2.5, z + 1], glow, emissive=active, skip=('down',))
    s.box([6, 4, 6], [10, 6, 10], 'steel_dark', skip=('down',))
    s.box([7.25, 6, 7.25], [8.75, 13, 8.75], glow, emissive=active, skip=('down', 'up'))
    # three octagonal antenna rings, narrowing upwards, held by steel spokes
    for y, r in ((7, 2.7), (9.5, 2.35), (11.75, 2.0)):  # rotated segments stay inside the 5..11 mast box
        side = 2 * r * math.tan(math.radians(22.5)) + 0.3
        t2 = 0.5
        for zc in (8 + r, 8 - r):
            for a in (0, 45, -45):
                s.box([8 - side / 2, y, zc - t2 / 2], [8 + side / 2, y + 0.6, zc + t2 / 2], glow,
                      emissive=active, rot=(a, 'y', [8, 8, 8]) if a else None)
        for xc in (8 + r, 8 - r):
            s.box([xc - t2 / 2, y, 8 - side / 2], [xc + t2 / 2, y + 0.6, 8 + side / 2], glow, emissive=active)
        s.box([8 - r, y + 0.1, 7.75], [8 + r, y + 0.5, 8.25], 'steel', skip=('east', 'west'))
    s.box([6.5, 12.75, 6.5], [9.5, 13.5, 9.5], 'bracket')
    s.box([6.9, 13.5, 6.9], [9.1, 15.25, 9.1], 'gem:cyan' if active else 'steel_dark', emissive=active,
          rot=(45, 'y', [8, 8, 8]))
    s.box([7.5, 15.25, 7.5], [8.5, 16, 8.5], glow, emissive=active, rot=(45, 'y', [8, 8, 8]))


def wireless_transmitter():
    atlas = Atlas('block/wireless_transmitter')
    idle = Shape(atlas)
    transmitter_model(idle, False)
    on = Shape(atlas)
    transmitter_model(on, True)
    atlas.pack()
    atlas.save()
    idle.save('block/wireless_transmitter')
    on.save('block/wireless_transmitter_active')
    kit.item_model('wireless_transmitter', parent='block/wireless_transmitter_active')
    blockstate_variants('wireless_transmitter', 'active', {'false': 'block/wireless_transmitter',
                                                           'true': 'block/wireless_transmitter_active'})


# =================================================================================================
# Storage Hub (early game: oak and iron, copper ports, an amber status lamp)
# =================================================================================================

@painter('hub_planks')
def p_hub_planks(t):
    """Oak boards behind the iron bands, with a darker gap between boards and nail heads."""
    w, h = t.w, t.h
    t.noise(OAK, base=3, chance=0.3)
    for x in range(w):
        for y in range(h):
            if x % 4 == 3:
                t.set(x, y, OAK[1])
            elif t.rng.random() < 0.08:
                t.set(x, y, OAK[4])
    for x in range(1, w, 4):
        for y in (1, h - 2):
            t.set(x, y, IRON_RAMP[3])
    t.hline(0, w - 1, 0, OAK[1])
    t.vline(0, 0, h - 1, OAK[1])


@painter('iron_band')
def p_iron_band(t):
    """Forged (wrought) iron strap with rivets along its length."""
    wrought = [rgb('1f2226'), rgb('3a3f45'), rgb('555b63'), rgb('737a83'), rgb('959ca5'), rgb('b9c0c8')]
    plate(t, wrought, base=2, hi=wrought[4], lo=wrought[0])
    along_x = t.w >= t.h
    n = t.w if along_x else t.h
    for i in range(2, n - 1, 4):
        x, y = (i, 0) if along_x else (0, i)
        t.set(x, y, wrought[5])


@painter('lens')
def p_lens(t, ramp='amber', lit='1'):
    """A domed lamp lens: bright core, rim and a specular glint."""
    r = RAMPS[ramp]
    on = lit == '1'
    t.fill(r[3] if on else r[1])
    if t.w >= 3 and t.h >= 3:
        t.rect(1, 1, t.w - 2, t.h - 2, r[4] if on else r[2])
        t.set(1, 1, r[5] if on else r[3])
    t.set(0, 0, r[5] if on else r[2])


def hub_model(s: Shape, active: bool):
    glow = 'glow:amber' if active else 'dim:amber'
    s.box([1, 1, 1], [15, 15, 15], 'hub_planks')
    for a in (0, 14):
        for b in (0, 14):
            s.box([0, a, b], [16, a + 2, b + 2], 'iron_band')
            s.box([a, 0, b], [a + 2, 16, b + 2], 'iron_band')
            s.box([a, b, 0], [a + 2, b + 2, 16], 'iron_band')
    # a copper port on every side face and underneath, glowing amber while the network runs
    ports = {
        'north': ([5, 5, 0.5], [11, 11, 1], [6.5, 6.5, 0.75], [9.5, 9.5, 1]),
        'south': ([5, 5, 15], [11, 11, 15.5], [6.5, 6.5, 15], [9.5, 9.5, 15.25]),
        'west': ([0.5, 5, 5], [1, 11, 11], [0.75, 6.5, 6.5], [1, 9.5, 9.5]),
        'east': ([15, 5, 5], [15.5, 11, 11], [15, 6.5, 6.5], [15.25, 9.5, 9.5]),
        'down': ([5, 0.5, 5], [11, 1, 11], [6.5, 0.75, 6.5], [9.5, 1, 9.5]),
    }
    for face, (f, t, gf, gt) in ports.items():
        s.box(f, t, 'copper', faces={face: 'ring:copper'})
        s.box(gf, gt, glow, emissive=active)
    # the status lamp: a copper bezel and a domed amber lens on top
    s.box([5, 15, 5], [11, 15.5, 11], 'copper', faces={'up': 'ring:copper'}, skip=('down',))
    s.box([6, 15, 6], [10, 15.75, 10], 'copper_bracket', skip=('down',))
    s.box([6.75, 15.75, 6.75], [9.25, 16, 9.25], f'lens:amber:{1 if active else 0}', emissive=active,
          skip=('down',))


def storage_hub():
    atlas = Atlas('block/storage_hub')
    textures = {}
    idle = Shape(atlas, render_type='minecraft:cutout', textures=textures, particle='block/storage_hub/particle')
    hub_model(idle, False)
    on = Shape(atlas, render_type='minecraft:cutout', textures=textures, particle='block/storage_hub/particle')
    hub_model(on, True)
    atlas.pack()
    atlas.save()
    t = Tex(16, seed=62)
    p_hub_planks(t)
    t.save('block/storage_hub/particle')
    idle.save('block/storage_hub')
    on.save('block/storage_hub_active')
    kit.item_model('storage_hub', parent='block/storage_hub_active')
    blockstate_variants('storage_hub', 'active', {'false': 'block/storage_hub', 'true': 'block/storage_hub_active'})


# =================================================================================================
# Quantum Bridge (late game: a dimensional gateway in void steel, violet and singularity white)
# =================================================================================================

VOID = [rgb('08060c'), rgb('120d1a'), rgb('1c1428'), rgb('2a1f3b'), rgb('3d2e56'), rgb('5a4580')]
SINGULARITY = [rgb('4a3a66'), rgb('8a74b8'), rgb('c8b8f0'), rgb('e9d7ff'), rgb('f6efff'), rgb('ffffff')]
RAMPS['void'] = VOID
RAMPS['singularity'] = SINGULARITY


@painter('void')
def p_void(t):
    plate(t, VOID, base=2, hi=VOID[4], lo=VOID[0])


@painter('void_band')
def p_void_band(t):
    """Void-steel slab side with an engraved violet circuit line."""
    plate(t, VOID, base=2, hi=VOID[5], lo=VOID[0])
    if t.h >= 3:
        y = t.h // 2
        for x in range(1, t.w - 1):
            t.set(x, y, VIOLET[1] if x % 5 else VIOLET[3])


@painter('void_top')
def p_void_top(t):
    """Top of the bridge: an engraved octagon around the singularity crown, rivets in the corners."""
    plate(t, VOID, base=2, hi=VOID[4], lo=VOID[0])
    w, h = t.w, t.h
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            d = max(abs(x - cx), abs(y - cy), (abs(x - cx) + abs(y - cy)) / 1.4)
            if 5.6 <= d < 6.4:
                t.set(x, y, VIOLET[1])
    for x, y in ((1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)):
        rivet(t, x, y)


@painter('void_pillar')
def p_void_pillar(t):
    plate(t, VOID, base=3, hi=VIOLET[2], lo=VOID[0])
    for i in range(2, t.h - 1, 3):
        t.hline(1, t.w - 2, i, VOID[1])


@painter('portal_off')
def p_portal_off(t):
    """The gateway at rest: a dark, faintly coiled field."""
    w, h = t.w, t.h
    cx, cy = w / 2, h / 2
    for y in range(h):
        for x in range(w):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            v = math.sin(2 * a + r * 1.3)
            t.set(x, y, VOID[2] if v > 0.6 else VOID[1] if v > -0.2 else VOID[0])


def portal_texture():
    """Animated vortex: violet spiral arms around a white-hot singularity, with drifting sparks."""
    frames = []
    n = 16
    for f in range(n):
        t = Tex(16, seed=300 + f)
        phase = f / n * math.tau
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                r, a = math.hypot(dx, dy), math.atan2(dy, dx)
                v = math.sin(2 * a - r * 1.15 + phase)
                fall = max(0.0, 1 - r / 5.5)
                if r < 1.1:
                    c = SINGULARITY[5]
                elif r < 1.9:
                    c = SINGULARITY[3]
                elif v > 0.6:
                    c = mix(VIOLET[3], SINGULARITY[3], fall * fall)
                elif v > 0.0:
                    c = mix(VIOLET[1], VIOLET[3], fall)
                else:
                    c = mix(VOID[1], VIOLET[1], fall)
                t.set(x, y, c)
        for k in range(5):
            ang = phase * 0.5 + k * math.tau / 5
            rad = 2.5 + ((f + k * 3) % n) / n * 4.5
            sx, sy = int(8 + rad * math.cos(ang)), int(8 + rad * math.sin(ang))
            t.set(sx, sy, SINGULARITY[5])
        frames.append(t)
    kit.save_animation('block/quantum_bridge/portal', frames, frametime=2, interpolate=True)


def octagon_ring(s: Shape, plane: str, centre, depth, apothem, thickness, mat, **kw):
    """Eight bars forming an octagonal ring. plane 'xy' (faces north/south) or 'zy' (faces east/west);
    depth = (near, far) along the third axis; centre = (horizontal, vertical) of the ring."""
    side = 2 * apothem * math.tan(math.radians(22.5)) + thickness * 0.8
    h, v = centre
    d0, d1 = depth
    th = thickness / 2
    if plane == 'xy':
        def box(x0, y0, x1, y1, angle=0):
            s.box([x0, y0, d0], [x1, y1, d1], mat, rot=(angle, 'z', [h, v, (d0 + d1) / 2]) if angle else None, **kw)
    else:
        def box(x0, y0, x1, y1, angle=0):
            s.box([d0, y0, x0], [d1, y1, x1], mat, rot=(angle, 'x', [(d0 + d1) / 2, v, h]) if angle else None, **kw)
    for sign in (1, -1):
        yc = v + sign * apothem
        for angle in (0, 45, -45):
            box(h - side / 2, yc - th, h + side / 2, yc + th, angle)
        xc = h + sign * apothem
        box(xc - th, v - side / 2, xc + th, v + side / 2)


def bridge_model(s: Shape, active: bool):
    ring = 'glow:violet' if active else 'dim:violet'
    edge = 'glow:singularity' if active else 'dim:violet'
    s.box([0, 0, 0], [16, 3, 16], 'void_band', faces={'up': 'void', 'down': 'panel'})
    s.box([0, 13, 0], [16, 15.25, 16], 'void_band', faces={'up': 'void_top', 'down': 'void'})
    # a rim closes the crown recess at the sides (no see-through slit under a block placed on top)
    for f, t in (([0, 15.25, 0], [16, 16, 1.5]), ([0, 15.25, 14.5], [16, 16, 16]),
                 ([0, 15.25, 1.5], [1.5, 16, 14.5]), ([14.5, 15.25, 1.5], [16, 16, 14.5])):
        s.box(f, t, 'void_band', faces={'up': 'void'}, skip=('down',))
    s.box([2, 3, 2], [14, 13, 14], 'void', skip=('up', 'down'))
    for x, z in ((0, 0), (13, 0), (0, 13), (13, 13)):
        s.box([x, 3, z], [x + 3, 13, z + 3], 'void_pillar', skip=('up', 'down'))
    portal = ('#portal', [3, 3, 13, 13]) if active else 'portal_off'
    for turns in range(4):
        put = putter(s, turns)
        # the vortex pane, set back between the pillars, with white light along both edges
        put([3, 3, 1.5], [13, 13, 2], 'void', faces={'north': portal}, emissive=active,
            skip=('south', 'east', 'west', 'up', 'down'))
        put([3, 3.5, 0.75], [3.5, 12.5, 1.5], edge, emissive=active, skip=('up', 'down', 'south'))
        put([12.5, 3.5, 0.75], [13, 12.5, 1.5], edge, emissive=active, skip=('up', 'down', 'south'))
    # octagonal gateway rings standing in front of each pane
    for plane, depth in (('xy', (0.5, 1.5)), ('xy', (14.5, 15.5)), ('zy', (0.5, 1.5)), ('zy', (14.5, 15.5))):
        octagon_ring(s, plane, (8, 8), depth, 4.2, 0.8, ring, emissive=active)
    # the crown: a singularity held over the top in a violet ring
    for y, r in ((15.25, 2.6),):
        side = 2 * r * math.tan(math.radians(22.5)) + 0.3
        for zc in (8 + r, 8 - r):
            for a in (0, 45, -45):
                s.box([8 - side / 2, y, zc - 0.3], [8 + side / 2, y + 0.6, zc + 0.3], ring, emissive=active,
                      rot=(a, 'y', [8, 8, 8]) if a else None, skip=('down',))
        for xc in (8 + r, 8 - r):
            s.box([xc - 0.3, y, 8 - side / 2], [xc + 0.3, y + 0.6, 8 + side / 2], ring, emissive=active,
                  skip=('down',))
    s.box([7, 15.25, 7], [9, 16, 9], 'glow:singularity' if active else 'void', emissive=active,
          rot=(45, 'y', [8, 8, 8]), skip=('down',))


def quantum_bridge():
    portal_texture()
    atlas = Atlas('block/quantum_bridge')
    textures = {'portal': 'block/quantum_bridge/portal'}
    idle = Shape(atlas, textures=textures, particle='block/quantum_bridge/particle')
    bridge_model(idle, False)
    on = Shape(atlas, textures=textures, particle='block/quantum_bridge/particle')
    bridge_model(on, True)
    atlas.pack()
    atlas.save()
    t = Tex(16, seed=63)
    p_void(t)
    t.save('block/quantum_bridge/particle')
    idle.save('block/quantum_bridge')
    on.save('block/quantum_bridge_active')
    kit.item_model('quantum_bridge', parent='block/quantum_bridge_active')
    blockstate_variants('quantum_bridge', 'active', {'false': 'block/quantum_bridge', 'true': 'block/quantum_bridge_active'})


# =================================================================================================
# Block of Omnium
# =================================================================================================

def omnium_block():
    base = Tex(16, seed=41)
    for qx in (0, 8):
        for qy in (0, 8):
            sub = Tex(8, seed=seed_of('omq', qx, qy))
            plate(sub, VIOLET, base=1, hi=VIOLET[3], lo=BLACK, spec_px=False)
            sub.set(1, 1, VIOLET[4])
            sub.set(6, 6, VIOLET[0])
            base.paste(sub, qx, qy)
    base.save('block/omnium_block/base')
    frames = []
    vein = [(1, 3), (2, 3), (3, 4), (4, 4), (5, 5), (6, 5), (7, 6), (8, 6), (9, 6), (10, 7), (11, 8), (12, 8), (13, 9),
            (14, 10), (5, 6), (5, 7), (4, 8), (4, 9), (3, 10), (3, 11), (2, 12), (10, 6), (11, 5), (12, 4), (13, 3),
            (13, 2), (9, 7), (9, 8), (8, 9), (8, 10), (9, 11), (10, 12), (10, 13)]
    for f in range(8):
        t = Tex(16, seed=f)
        k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        for i, (x, y) in enumerate(vein):
            pulse = 0.5 + 0.5 * math.sin(f / 8 * math.tau - i * 0.35)
            t.set(x, y, mix(VIOLET[4], VIOLET[5], pulse))
        for x, y in ((1, 3), (14, 10), (2, 12), (13, 2), (10, 13)):
            t.set(x, y, mix(VIOLET[4], (255, 255, 255, 255), 0.3 * k))
        frames.append(t)
    kit.save_animation('block/omnium_block/veins', frames, frametime=3, interpolate=True)
    m = kit.Model(textures={'base': 'block/omnium_block/base', 'veins': 'block/omnium_block/veins',
                            'particle': 'block/omnium_block/base'}, render_type='minecraft:cutout')
    m.box([0, 0, 0], [16, 16, 16], 'base', cull=True)
    m.box([0, 0, 0], [16, 16, 16], 'veins', cull=True, emissive=True, shade=False)
    m.save('block/omnium_block')
    kit.item_model('omnium_block', parent='block/omnium_block')
    kit.blockstate_single('omnium_block')


# =================================================================================================
# Item sprites
# =================================================================================================

TIER_IDS = ['4k', '16k', '64k', '256k', '1m', '16m']
TIER_HEX = {'4k': '9aa0a8', '16k': 'f5c542', '64k': '4de8e0', '256k': '5be08a', '1m': 'a46bff', '16m': 'ff6bd5'}


def bevel_rect(t: Tex, x0, y0, x1, y1, ramp, base=2, hi=None, lo=None):
    t.rect(x0, y0, x1, y1, ramp[base])
    t.hline(x0, x1, y0, hi or ramp[min(5, base + 2)])
    t.vline(x0, y0, y1, hi or ramp[min(5, base + 2)])
    t.hline(x0, x1, y1, lo or ramp[max(0, base - 2)])
    t.vline(x1, y0, y1, lo or ramp[max(0, base - 2)])


def outline_alpha(t: Tex, colour=BLACK):
    """Adds a 1px dark outline around opaque pixels (sprite readability on any background)."""
    src = t.img.copy().load()
    for y in range(t.h):
        for x in range(t.w):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < t.w and 0 <= ny < t.h and src[nx, ny][3] > 0:
                        t.set(x, y, colour)
                        break


def cartridge_body(t: Tex, cap_ramp=None):
    """The shared cell shell (x 3..12, y 1..14). cap_ramp=None leaves the cap for the tint layer."""
    # handle
    t.hline(5, 10, 1, STEEL[4])
    t.set(5, 2, STEEL[2])
    t.set(10, 2, STEEL[1])
    t.hline(6, 9, 2, GUNMETAL[0])
    # body
    bevel_rect(t, 3, 3, 12, 14, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    t.vline(4, 5, 12, GUNMETAL[3])
    if cap_ramp:
        t.hline(3, 12, 3, cap_ramp[4])
        t.hline(3, 12, 4, cap_ramp[2])
        t.set(12, 3, cap_ramp[2])
    # contacts
    for x in range(4, 12):
        t.set(x, 13, GOLD[3] if x % 2 == 0 else GUNMETAL[0])
    t.hline(3, 12, 14, BLACK)
    # status LED
    t.set(11, 11, CYAN[4])
    t.set(11, 12, CYAN[2])


def tint_cap(t: Tex):
    t.hline(3, 12, 3, WHITE[5])
    t.hline(3, 12, 4, WHITE[2])
    t.set(3, 3, WHITE[5])
    t.set(12, 3, WHITE[3])
    t.set(12, 4, WHITE[1])


def split_layers(full: Tex, mask: Tex):
    """layer0 = tinted pixels (mask), layer1 = the rest."""
    body = full.copy()
    for y in range(16):
        for x in range(16):
            if mask.get(x, y)[3] > 0:
                body.set(x, y, CLEAR)
    return mask, body


def storage_cell_sprites():
    # tint layer: cap and label (grey shades; the item colour handler multiplies the tier colour in)
    tint = Tex(16)
    tint_cap(tint)
    tint.rect(5, 6, 10, 9, WHITE[3])
    tint.hline(5, 10, 6, WHITE[5])
    tint.vline(5, 6, 9, WHITE[4])
    tint.hline(6, 10, 9, WHITE[1])
    tint.vline(10, 7, 9, WHITE[2])
    tint.hline(6, 8, 8, WHITE[2])
    full = Tex(16)
    cartridge_body(full)
    for x in range(4, 10):                     # capacity gauge track
        full.set(x, 11, BLACK)
    full.paste(tint)
    _, body = split_layers(full, tint)
    tint.save('item/storage_cell/tint')
    body.save('item/storage_cell/body')
    # capacity glyph (layer2, untinted): a segmented gauge lit one segment per tier
    for n, tier in enumerate(TIER_IDS, 1):
        g = Tex(16)
        for i in range(n):
            g.set(4 + i, 11, CYAN[4] if i == n - 1 else CYAN[3])
        g.save(f'item/storage_cell/glyph_{tier}')
    g = Tex(16)
    for i, c in enumerate((RED[3], AMBER[3], GOLD[3], GREEN[3], CYAN[3], VIOLET[3])):
        g.set(4 + i, 11, c)
    g.save('item/storage_cell/glyph_creative')
    # singularity: the gauge overflows in white light and a star sits on the label
    g = Tex(16)
    for i in range(6):
        g.set(4 + i, 11, SINGULARITY[5] if i % 2 else SINGULARITY[3])
    for x, y, c in ((8, 6, 5), (7, 7, 4), (8, 7, 5), (9, 7, 4), (8, 8, 4)):
        g.set(x, y, SINGULARITY[c])
    g.save('item/storage_cell/glyph_singularity')
    for tier in TIER_IDS + ['singularity']:
        kit.item_model(f'storage_cell_{tier}', layers=['item/storage_cell/tint', 'item/storage_cell/body',
                                                        f'item/storage_cell/glyph_{tier}'])
    kit.item_model('creative_storage_cell', layers=['item/storage_cell/tint', 'item/storage_cell/body',
                                                     'item/storage_cell/glyph_creative'])
    # empty housing: steel cap, open dark slot where the matrix goes
    h = Tex(16)
    cartridge_body(h, cap_ramp=STEEL)
    h.rect(5, 6, 10, 10, BLACK)
    h.hline(5, 10, 6, GUNMETAL[0])
    h.hline(6, 9, 10, GUNMETAL[1])
    h.set(6, 8, GUNMETAL[1])
    h.set(9, 8, GUNMETAL[1])
    h.set(11, 11, GUNMETAL[1])
    h.set(11, 12, GUNMETAL[0])
    h.save('item/cell_housing')
    kit.item_model('cell_housing')


def fluid_window(t: Tex, liquid=True):
    """Glass window (x 5..10, y 6..11) showing liquid with a wave line and a bubble."""
    t.rect(5, 6, 10, 11, rgb('0c1a24'))
    t.outline(5, 6, 10, 11, STEEL[1])
    t.hline(5, 10, 6, STEEL[3])
    if liquid:
        t.rect(6, 8, 9, 10, BLUE[2])
        for x in range(6, 10):
            t.set(x, 8, BLUE[4] if x % 2 == 0 else BLUE[3])
        t.set(8, 9, BLUE[5])
        t.vline(9, 9, 10, BLUE[1])
        t.hline(6, 9, 10, BLUE[1])
    t.set(6, 7, (230, 245, 255, 255))


def fluid_cell_sprites():
    tint = Tex(16)
    tint_cap(tint)
    tint.hline(4, 11, 12, WHITE[3])   # tier band above the contacts
    tint.set(4, 12, WHITE[4])
    tint.set(11, 12, WHITE[1])
    full = Tex(16)
    cartridge_body(full)
    fluid_window(full)
    full.set(11, 11, CYAN[4])
    full.set(11, 12, CYAN[2])
    full.paste(tint)
    _, body = split_layers(full, tint)
    tint.save('item/fluid_cell/tint')
    body.save('item/fluid_cell/body')
    # glyph: a droplet on the window and tier pips down the right edge
    for n, tier in enumerate(TIER_IDS + ['creative'], 1):
        g = Tex(16)
        g.set(7, 7, BLUE[5])
        g.set(7, 8, (255, 255, 255, 255))
        if tier == 'creative':
            for y in range(5, 11):
                g.set(11, y, mix(RED[4], AMBER[4], (y - 5) / 5))
        else:
            for i in range(n):
                g.set(11, 10 - i, BLUE[4] if i % 2 == 0 else BLUE[3])
        g.save(f'item/fluid_cell/glyph_{tier}')
    g = Tex(16)
    g.set(7, 7, SINGULARITY[5])
    g.set(7, 8, (255, 255, 255, 255))
    g.set(8, 9, SINGULARITY[3])
    for i in range(6):
        g.set(11, 10 - i, SINGULARITY[5] if i % 2 else SINGULARITY[2])
    g.save('item/fluid_cell/glyph_singularity')
    for tier in TIER_IDS + ['singularity']:
        kit.item_model(f'fluid_cell_{tier}', layers=['item/fluid_cell/tint', 'item/fluid_cell/body',
                                                      f'item/fluid_cell/glyph_{tier}'])
    kit.item_model('creative_fluid_cell', layers=['item/fluid_cell/tint', 'item/fluid_cell/body',
                                                   'item/fluid_cell/glyph_creative'])
    h = Tex(16)
    cartridge_body(h, cap_ramp=BLUE)
    fluid_window(h, liquid=False)
    h.set(7, 8, BLUE[3])
    h.set(7, 9, BLUE[2])
    h.set(11, 11, GUNMETAL[1])
    h.set(11, 12, GUNMETAL[0])
    h.save('item/fluid_cell_housing')
    kit.item_model('fluid_cell_housing')


def matrix_sprites():
    for n, tier in enumerate(TIER_IDS, 1):
        r = ramp_from(TIER_HEX[tier])
        t = Tex(16, seed=n)
        # gold pins on both sides
        for y in range(4, 13, 2):
            t.set(1, y, GOLD[3])
            t.set(14, y, GOLD[2])
        bevel_rect(t, 2, 2, 13, 13, GUNMETAL, base=2, hi=STEEL[2], lo=BLACK)
        t.rect(3, 3, 12, 12, GUNMETAL[1])
        # lattice crystal
        for y in range(3, 13):
            for x in range(3, 13):
                d = abs(x - 7.5) + abs(y - 7.5)
                if d <= 4.6:
                    if (x - 7.5) + (y - 7.5) < -1:
                        c = r[4]
                    elif (x - 7.5) + (y - 7.5) < 1.5:
                        c = r[3]
                    else:
                        c = r[2]
                    if (x + y) % 3 == 0 and d < 4:
                        c = mix(c, r[1], 0.5)
                    if d > 3.8:
                        c = r[1] if (x - 7.5) + (y - 7.5) > 0 else r[3]
                    t.set(x, y, c)
        t.set(6, 5, r[5])
        t.set(5, 6, r[5])
        t.set(7, 7, (255, 255, 255, 255))
        for i in range(n):
            t.set(3 + i * 2 if n < 6 else 3 + i * 2, 13, r[4])
        t.save(f'item/storage_matrix_{tier}')
        kit.item_model(f'storage_matrix_{tier}')
    singularity_matrix_sprite()


def singularity_matrix_sprite():
    """A white-hot singularity: a black core inside a blazing accretion ring, on a void-steel chip."""
    t = Tex(16, seed=77)
    for y in range(4, 13, 2):
        t.set(1, y, SINGULARITY[4])
        t.set(14, y, SINGULARITY[2])
    bevel_rect(t, 2, 2, 13, 13, VOID, base=2, hi=VIOLET[2], lo=BLACK)
    t.rect(3, 3, 12, 12, VOID[0])
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx * 0.8, dy * 1.25)   # a tilted disk
            if 2.3 <= r < 4.6:
                k = (r - 2.3) / 2.3
                c = mix(SINGULARITY[5], VIOLET[3], k) if dx + dy < 1 else mix(SINGULARITY[3], VIOLET[2], k)
                t.set(x, y, c)
            elif r < 2.3:
                t.set(x, y, BLACK if r < 1.6 else VIOLET[1])
    t.set(5, 5, SINGULARITY[5])
    t.set(11, 4, VIOLET[4])
    t.set(4, 11, VIOLET[4])
    for x in range(3, 13, 2):
        t.set(x, 13, SINGULARITY[4])
    t.save('item/storage_matrix_singularity')
    kit.item_model('storage_matrix_singularity')


def link_card_sprite():
    """Quantum Link Card: a void keycard with a holographic stripe and two entangled rings."""
    t = Tex(16, seed=78)
    bevel_rect(t, 1, 3, 14, 12, VOID, base=2, hi=VIOLET[3], lo=BLACK)
    t.set(1, 3, CLEAR)
    t.set(14, 3, CLEAR)
    t.set(1, 12, CLEAR)
    t.set(14, 12, CLEAR)
    for x in range(2, 14):                       # holographic stripe
        t.set(x, 4, mix(VIOLET[4], CYAN[4], (x - 2) / 11))
        t.set(x, 5, mix(VIOLET[2], CYAN[2], (x - 2) / 11))
    for cx, colour in ((6, VIOLET[4]), (9, SINGULARITY[4])):   # two interlocked rings
        for x, y in ((cx - 1, 7), (cx, 7), (cx - 2, 8), (cx + 1, 8), (cx - 2, 9), (cx + 1, 9), (cx - 1, 10), (cx, 10)):
            t.set(x, y, colour)
    t.set(7, 8, SINGULARITY[5])
    t.set(8, 9, VIOLET[5])
    t.rect(11, 9, 12, 10, GOLD[3])               # contact chip
    t.set(11, 9, GOLD[4])
    t.save('item/quantum_link_card')
    kit.item_model('quantum_link_card')


def chip(t: Tex, x0, y0, x1, y1, pin_ramp, body=GUNMETAL):
    for x in range(x0 + 1, x1, 2):
        t.set(x, y0 - 1, pin_ramp[3])
        t.set(x, y1 + 1, pin_ramp[2])
    for y in range(y0 + 1, y1, 2):
        t.set(x0 - 1, y, pin_ramp[3])
        t.set(x1 + 1, y, pin_ramp[2])
    bevel_rect(t, x0, y0, x1, y1, body, base=1, hi=body[3], lo=BLACK)


def processor_sprites():
    # logic: small cyan die with traces
    t = Tex(16)
    chip(t, 3, 3, 12, 12, GOLD)
    t.rect(6, 6, 9, 9, CYAN[2])
    t.hline(6, 9, 6, CYAN[4])
    t.vline(6, 6, 9, CYAN[4])
    t.set(7, 7, CYAN[5])
    t.rect(8, 8, 9, 9, CYAN[1])
    for a, b in (((4, 7), (5, 7)), ((10, 8), (11, 8)), ((7, 4), (7, 5)), ((8, 10), (8, 11))):
        t.line(*a, *b, CYAN[1])
    t.save('item/logic_processor')
    # advanced: gold-framed teal crystal die, traces to every side
    t = Tex(16)
    chip(t, 2, 2, 13, 13, GOLD)
    t.outline(4, 4, 11, 11, GOLD[2])
    t.hline(4, 11, 4, GOLD[4])
    d = ramp_from('4de8e0')
    for y in range(5, 11):
        for x in range(5, 11):
            k = (x + y - 10) / 10
            t.set(x, y, d[4] if k < 0.25 else d[3] if k < 0.55 else d[2])
    t.set(6, 6, d[5])
    t.set(7, 6, (255, 255, 255, 255))
    for x in (3, 12):
        t.vline(x, 6, 9, GOLD[1])
    t.save('item/advanced_processor')
    # quantum: violet star core in a dark steel package with violet traces
    t = Tex(16)
    chip(t, 2, 2, 13, 13, STEEL, body=GUNMETAL)
    for i in range(3, 13):
        if i % 3 == 0:
            t.set(i, 3, VIOLET[2])
            t.set(3, i, VIOLET[2])
            t.set(i, 12, VIOLET[1])
            t.set(12, i, VIOLET[1])
    t.rect(5, 5, 10, 10, VIOLET[1])
    t.outline(5, 5, 10, 10, VIOLET[2])
    for x, y, c in ((7, 5, 3), (8, 5, 3), (7, 10, 3), (8, 10, 3), (5, 7, 3), (5, 8, 3), (10, 7, 3), (10, 8, 3),
                    (7, 6, 4), (8, 6, 4), (6, 7, 4), (9, 7, 4), (6, 8, 4), (9, 8, 4), (7, 9, 4), (8, 9, 4),
                    (7, 7, 5), (8, 7, 5), (7, 8, 5), (8, 8, 5)):
        t.set(x, y, VIOLET[c])
    t.set(7, 7, (255, 255, 255, 255))
    t.save('item/quantum_processor')
    for name in ('logic_processor', 'advanced_processor', 'quantum_processor'):
        kit.item_model(name)


def card(t: Tex, accent):
    """Upgrade card blank: dark PCB with steel border, gold edge contacts and an accent corner."""
    bevel_rect(t, 2, 2, 13, 13, GUNMETAL, base=2, hi=STEEL[2], lo=BLACK)
    t.rect(3, 3, 12, 11, rgb('16241f'))
    t.set(2, 2, CLEAR)
    t.set(13, 2, CLEAR)
    for x in range(4, 12):
        t.set(x, 12, GOLD[3] if x % 2 == 0 else GOLD[1])
    t.hline(3, 5, 3, accent[3])
    t.set(3, 4, accent[3])
    t.set(12, 10, accent[2])
    for x, y in ((5, 9), (6, 9), (10, 4), (10, 5)):
        t.set(x, y, rgb('2b4038'))


def upgrade_sprites():
    t = Tex(16)
    card(t, STEEL)
    for y in range(5, 10, 2):
        for x in range(5, 11, 2):
            t.set(x, y, rgb('2b4038'))
    t.save('item/upgrade_base')
    kit.item_model('upgrade_base')
    icons = {}
    # speed: double chevron
    t = Tex(16)
    card(t, CYAN)
    for ox in (4, 7):
        for i, (x, y) in enumerate(((ox, 5), (ox + 1, 6), (ox + 2, 7), (ox + 1, 8), (ox, 9))):
            t.set(x, y, CYAN[4] if i == 2 else CYAN[3])
    icons['speed_upgrade'] = t
    # stack: three stacked plates
    t = Tex(16)
    card(t, AMBER)
    for k, y in enumerate((9, 7, 5)):
        t.hline(5, 10, y, AMBER[3 if k < 2 else 4])
        t.hline(5, 10, y + 1, AMBER[1])
        t.set(5, y, AMBER[4])
    icons['stack_upgrade'] = t
    # fuzzy: approximately-equal waves
    t = Tex(16)
    card(t, VIOLET)
    for y0 in (5, 8):
        for x in range(4, 12):
            t.set(x, y0 + (1 if (x // 2) % 2 else 0), VIOLET[4] if y0 == 5 else VIOLET[3])
    icons['fuzzy_upgrade'] = t
    # range: antenna with signal arcs
    t = Tex(16)
    card(t, CYAN)
    t.vline(7, 6, 10, STEEL[4])
    t.set(7, 5, CYAN[5])
    for x, y in ((5, 4), (4, 5), (4, 6), (5, 7), (9, 4), (10, 5), (10, 6), (9, 7)):
        t.set(x, y, CYAN[3])
    t.hline(6, 8, 10, STEEL[2])
    icons['range_upgrade'] = t
    # redstone: a glowing redstone torch
    t = Tex(16)
    card(t, RED)
    t.vline(8, 7, 10, OAK[2])
    t.vline(7, 8, 10, OAK[1])
    t.rect(7, 5, 8, 6, RED[3])
    t.set(7, 5, RED[4])
    t.set(8, 4, RED[2])
    t.set(6, 6, RED[1])
    t.set(9, 5, RED[1])
    icons['redstone_upgrade'] = t
    for name, t in icons.items():
        t.save(f'item/{name}')
        kit.item_model(name)


def template_sprite():
    """Smithing-template plate (notched corners, dark rim) engraved with a glowing quantum orbit."""
    t = Tex(16, seed=5)
    stone = [rgb('120a1c'), rgb('1e1230'), rgb('2c1a45'), rgb('3f2763'), rgb('5a3a8a'), rgb('7b58b0')]
    t.noise(stone, x0=3, y0=1, x1=12, y1=14, base=2, chance=0.35)
    for x, y in ((3, 1), (12, 1), (3, 14), (12, 14), (3, 7), (12, 8)):
        t.set(x, y, CLEAR)
    t.hline(4, 11, 1, stone[4])
    t.vline(3, 2, 6, stone[4])
    t.vline(3, 8, 13, stone[4])
    t.hline(4, 11, 14, stone[0])
    t.vline(12, 2, 7, stone[1])
    t.vline(12, 9, 13, stone[1])
    orbit = [(5, 5), (6, 4), (7, 4), (8, 5), (9, 6), (10, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 9), (5, 8),
             (5, 7), (5, 6)]
    for x, y in orbit:
        t.set(x, y, VIOLET[3])
    for x, y in ((4, 9), (5, 10), (6, 11), (9, 4), (10, 5), (11, 6)):
        t.set(x, y, CYAN[3])
    t.rect(7, 6, 8, 8, VIOLET[4])
    t.set(7, 7, VIOLET[5])
    t.set(8, 7, (255, 255, 255, 255))
    outline_alpha(t, stone[0])
    t.save('item/quantum_upgrade_template')
    kit.item_model('quantum_upgrade_template')


def material_sprites():
    def pile(t, ramp, specks=None, seed=1):
        rows = {13: (2, 13), 12: (3, 12), 11: (3, 12), 10: (4, 11), 9: (5, 10), 8: (6, 9), 7: (7, 8)}
        rng = t.rng
        for y, (x0, x1) in rows.items():
            for x in range(x0, x1 + 1):
                k = (x - 7.5) / 6 + (y - 7) / 8
                c = ramp[4] if k < 0.05 else ramp[3] if k < 0.55 else ramp[2]
                if rng.random() < 0.25:
                    c = ramp[max(1, min(5, ramp.index(c) + rng.choice((-1, 1))))]
                if specks and rng.random() < 0.3:
                    c = specks[rng.choice((2, 3, 4))]
                t.set(x, y, c)
        t.hline(2, 13, 13, ramp[1])
        for x, y in ((1, 13), (14, 12), (12, 8), (3, 9)):
            t.set(x, y, ramp[2])
        outline_alpha(t, ramp[0])
    t = Tex(16, seed=11)
    pile(t, VIOLET)
    for x, y in ((6, 9), (9, 11), (4, 12), (11, 10)):
        t.set(x, y, VIOLET[5])
    t.set(7, 7, (255, 255, 255, 255))
    t.save('item/omnium_dust')
    t = Tex(16, seed=12)
    pile(t, IRON_RAMP, specks=VIOLET)
    t.save('item/omnium_blend')
    # ingot: lighter top face over a bevelled front face with a glowing core line
    t = Tex(16)
    for i, y in enumerate((5, 6)):
        t.hline(5 - i, 10 + i, y, VIOLET[5] if i == 0 else VIOLET[4])
    t.rect(3, 7, 12, 10, VIOLET[3])
    t.hline(3, 12, 11, VIOLET[1])
    t.vline(3, 7, 11, VIOLET[4])
    t.vline(12, 7, 11, VIOLET[1])
    t.hline(4, 11, 9, VIOLET[4])
    t.hline(5, 10, 9, VIOLET[5])
    t.set(4, 6, VIOLET[5])
    t.set(5, 5, (255, 255, 255, 255))
    outline_alpha(t, VIOLET[0])
    t.save('item/omnium_ingot')
    for name in ('omnium_dust', 'omnium_blend', 'omnium_ingot'):
        kit.item_model(name)


IRON_RAMP = [rgb('3b3f45'), rgb('6b7078'), rgb('9aa0a8'), rgb('c4c9cf'), rgb('e3e6ea'), rgb('ffffff')]


# =================================================================================================
# 3D items: wireless terminals and the OmniTool
# =================================================================================================

HANDHELD_DISPLAY = {
    'gui': {'rotation': [15, -30, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
}

TOOL_DISPLAY = {
    'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
}


@painter('keypad')
def p_keypad(t, accent='cyan'):
    r = RAMPS[accent]
    t.fill(GUNMETAL[1])
    for y in range(0, t.h, 2):
        for x in range(0, t.w, 2):
            t.set(x, y, STEEL[3] if (x + y) % 4 else STEEL[2])
    t.set(t.w - 2, t.h - 2, r[3])


def handheld_screen_texture(name, ramp):
    frames = []
    for f in range(8):
        t = Tex(16, seed=f)
        t.rect(0, 0, 5, 5, mix(SCREEN_BG, ramp[0], 0.5))
        t.hline(0, 5, 0, ramp[1])
        t.set(1 + f % 4, 0, ramp[4])
        for n, (x, y) in enumerate(((0, 2), (3, 2), (0, 4), (3, 4))):
            icon(t, x, y, ITEM_ICONS[(n + f // 2) % len(ITEM_ICONS)])
        frames.append(t)
    kit.save_animation(f'item/{name}/screen', frames, frametime=5, interpolate=False)


def wireless_terminal_model(s: Shape, quantum: bool):
    accent = 'violet' if quantum else 'cyan'
    s.box([4, 1, 7], [12, 13, 9], 'casing', faces={'south': 'casing', 'north': 'panel'})
    s.box([4.5, 5.5, 9], [11.5, 12.5, 9.25], 'steel_dark', faces={'south': 'steel'})
    s.box([5, 6, 9.25], [11, 12, 9.5], 'black', faces={'south': ('#screen', [0, 0, 6, 6])}, emissive=True,
          skip=('north',))
    s.box([5, 1.75, 9], [11, 4.75, 9.25], 'steel', faces={'south': f'keypad:{accent}'})
    s.box([3.5, 2, 7.25], [4, 11, 8.75], 'rubber', skip=('east',))
    s.box([12, 2, 7.25], [12.5, 11, 8.75], 'rubber', skip=('west',))
    s.box([4, 12.5, 6.75], [12, 13.5, 9.25], 'bracket')
    s.box([9.75, 13.5, 7.5], [10.75, 15, 8.5], 'steel_hi')
    if quantum:
        s.box([9.5, 15, 7.25], [11, 16.5, 8.75], 'gem:violet', emissive=True, rot=(45, 'y', [10.25, 15.75, 8]))
        for x0 in (4, 11.5):
            s.box([x0, 2, 9], [x0 + 0.5, 11, 9.25], 'glow:violet', emissive=True, skip=('north',))
        s.box([5, 0.5, 7.5], [11, 1, 8.5], 'glow:violet', emissive=True, skip=('up',))
    else:
        s.box([9.75, 15, 7.5], [10.75, 15.75, 8.5], 'glow:cyan', emissive=True)
    s.display = json.loads(json.dumps(HANDHELD_DISPLAY))


def omni_tool_model(s: Shape):
    r = (-45, 'z', [8, 8, 8])
    s.box([7, 0.5, 7.25], [9, 6, 8.75], 'rubber', rot=r)
    s.box([6.75, -0.25, 7], [9.25, 0.75, 9], 'bracket', rot=r)
    s.box([6.5, 6, 7], [9.5, 7, 9], 'bracket', rot=r)
    s.box([7, 7, 7.4], [9, 11.5, 8.6], 'steel_hi', rot=r)
    s.box([6.85, 8, 7.25], [9.15, 10, 8.75], 'gem:violet', emissive=True, rot=r)
    s.box([4.5, 11.5, 7], [11.5, 13.25, 9], 'steel', rot=r)
    s.box([4.5, 13.25, 7], [6.75, 16.75, 9], 'steel_hi', rot=r)
    s.box([9.25, 13.25, 7], [11.5, 16, 9], 'steel', rot=r)
    s.box([6.75, 13.25, 7.5], [9.25, 13.75, 8.5], 'glow:violet', emissive=True, rot=r)
    s.display = json.loads(json.dumps(TOOL_DISPLAY))


def handheld_items():
    for name, quantum in (('wireless_terminal', False), ('quantum_wireless_terminal', True)):
        handheld_screen_texture(name, VIOLET if quantum else CYAN)
        atlas = Atlas(f'item/{name}')
        s = Shape(atlas, parent=None, textures={'screen': f'item/{name}/screen'}, particle='block/network/casing')
        wireless_terminal_model(s, quantum)
        atlas.pack()
        atlas.save()
        s.save(f'item/{name}')
    atlas = Atlas('item/omni_tool')
    s = Shape(atlas, parent=None, particle='block/network/casing')
    omni_tool_model(s)
    atlas.pack()
    atlas.save()
    s.save('item/omni_tool')


# =================================================================================================
# Data: recipes, loot tables, tags, lang
# =================================================================================================

NETWORK_BLOCKS = ['nexus_core', 'nexus_cable', 'drive', 'terminal', 'crafting_terminal', 'import_bus', 'export_bus',
                  'storage_bus', 'network_port', 'signal_emitter', 'wireless_transmitter', 'omnium_block',
                  'destructor', 'constructor', 'storage_monitor', 'quantum_bridge']
EARLY_BLOCKS = ['storage_hub', 'inventory_cable']   # oak and iron: mined with an axe


def ov(name):
    return f'{M}:{name}'


def mc(name):
    return f'minecraft:{name}'


def shaped(name, pattern, key, count=1, result=None):
    return kit.shaped(name, pattern, key, result=result, count=count)


def shapeless(name, ingredients, count=1, result=None):
    return kit.shapeless(name, ingredients, result=result, count=count)


def recipes():
    # -- materials --------------------------------------------------------------------------------
    shapeless('omnium_dust', [mc('amethyst_shard'), mc('quartz'), mc('redstone'), mc('redstone')], count=4)
    shapeless('omnium_blend', [mc('iron_ingot'), ov('omnium_dust'), ov('omnium_dust')])
    kit.smelting('omnium_ingot', ov('omnium_blend'), ov('omnium_ingot'), xp=0.7)
    shaped('omnium_block', ['###', '###', '###'], {'#': ov('omnium_ingot')})
    shapeless('omnium_ingot_from_block', [ov('omnium_block')], count=9, result=ov('omnium_ingot'))
    # -- processors ---------------------------------------------------------------------------------
    shaped('logic_processor', [' R ', 'GOG', ' R '], {'R': mc('redstone'), 'G': mc('gold_ingot'), 'O': ov('omnium_ingot')})
    shaped('advanced_processor', [' D ', 'LOL', ' Q '],
           {'D': mc('diamond'), 'L': ov('logic_processor'), 'O': ov('omnium_ingot'), 'Q': mc('quartz')})
    shaped('quantum_processor', [' E ', 'ANA', ' P '],
           {'E': mc('echo_shard'), 'A': ov('advanced_processor'), 'N': mc('netherite_scrap'), 'P': mc('ender_pearl')})
    # -- upgrade cards, template, tool ---------------------------------------------------------------
    shaped('upgrade_base', ['GOG', 'ILI'], {'G': mc('gold_nugget'), 'O': ov('omnium_ingot'), 'I': mc('iron_ingot'),
                                            'L': ov('logic_processor')}, count=2)
    shapeless('speed_upgrade', [ov('upgrade_base'), mc('sugar'), mc('redstone'), ov('omnium_dust')])
    shapeless('stack_upgrade', [ov('upgrade_base'), mc('hopper'), ov('omnium_ingot')])
    shapeless('fuzzy_upgrade', [ov('upgrade_base'), mc('comparator'), '#minecraft:wool'])
    shapeless('range_upgrade', [ov('upgrade_base'), mc('ender_pearl'), mc('amethyst_shard')])
    shapeless('redstone_upgrade', [ov('upgrade_base'), mc('redstone_torch'), mc('redstone')])
    shaped('quantum_upgrade_template', ['DQD', 'OSO', 'DOD'],
           {'D': mc('diamond'), 'Q': ov('quantum_processor'), 'O': ov('omnium_ingot'), 'S': mc('echo_shard')})
    shaped('omni_tool', ['I I', ' O ', ' I '], {'I': mc('iron_ingot'), 'O': ov('omnium_ingot')})
    # -- storage ------------------------------------------------------------------------------------
    shaped('cell_housing', ['GRG', 'I I', 'III'], {'G': mc('glass_pane'), 'R': mc('redstone'), 'I': mc('iron_ingot')})
    shaped('fluid_cell_housing', ['GRG', 'IBI', 'III'],
           {'G': mc('glass'), 'R': mc('redstone'), 'B': mc('bucket'), 'I': mc('iron_ingot')})
    matrix_keys = {
        '4k': (['ROR', 'QLQ', 'ROR'], {'R': mc('redstone'), 'O': ov('omnium_dust'), 'Q': mc('quartz'), 'L': ov('logic_processor')}),
        '16k': (['GMG', 'MLM', 'GMG'], {'G': mc('gold_ingot'), 'M': ov('storage_matrix_4k'), 'L': ov('logic_processor')}),
        '64k': (['DMD', 'MAM', 'DMD'], {'D': mc('diamond'), 'M': ov('storage_matrix_16k'), 'A': ov('advanced_processor')}),
        '256k': (['OMO', 'MAM', 'OMO'], {'O': ov('omnium_ingot'), 'M': ov('storage_matrix_64k'), 'A': ov('advanced_processor')}),
        '1m': (['EME', 'MQM', 'EME'], {'E': mc('echo_shard'), 'M': ov('storage_matrix_256k'), 'Q': ov('quantum_processor')}),
        '16m': (['MNM', 'QSQ', 'MNM'], {'M': ov('storage_matrix_1m'), 'N': mc('netherite_ingot'), 'Q': ov('quantum_processor'),
                                        'S': mc('nether_star')}),
    }
    matrix_keys['singularity'] = (['MQM', 'QNQ', 'MQM'], {'M': ov('storage_matrix_16m'), 'Q': ov('quantum_processor'),
                                                         'N': mc('nether_star')})
    for tier, (pattern, key) in matrix_keys.items():
        shaped(f'storage_matrix_{tier}', pattern, key)
        shapeless(f'storage_cell_{tier}', [ov('cell_housing'), ov(f'storage_matrix_{tier}')])
        shapeless(f'fluid_cell_{tier}', [ov('fluid_cell_housing'), ov(f'storage_matrix_{tier}')])
    # -- early game: vanilla materials only, so a Storage Hub network works before any omnium -------------
    shaped('storage_hub', ['PRP', 'CIC', 'PRP'], {'P': '#minecraft:planks', 'R': mc('redstone'), 'C': mc('chest'),
                                                  'I': mc('iron_ingot')})
    shaped('inventory_cable', ['SSS', 'NRN', 'SSS'], {'S': '#minecraft:wooden_slabs', 'N': mc('iron_nugget'),
                                                      'R': mc('redstone')}, count=8)
    shaped('terminal', ['IGI', 'RCR', 'IRI'], {'I': mc('iron_ingot'), 'G': mc('glass'), 'R': mc('redstone'),
                                               'C': mc('chest')})
    shapeless('crafting_terminal', [ov('terminal'), mc('crafting_table'), mc('redstone')])
    # -- network blocks -------------------------------------------------------------------------------
    shaped('nexus_core', ['OAO', 'BEB', 'OAO'], {'O': ov('omnium_ingot'), 'A': ov('advanced_processor'),
                                                 'B': ov('omnium_block'), 'E': mc('ender_eye')})
    shaped('nexus_cable', ['NNN', 'DRD', 'NNN'], {'N': mc('iron_nugget'), 'D': ov('omnium_dust'), 'R': mc('redstone')},
           count=8)
    shaped('drive', ['IOI', 'CLC', 'IOI'], {'I': mc('iron_ingot'), 'O': ov('omnium_ingot'), 'C': mc('chest'),
                                            'L': ov('logic_processor')})
    # -- late game: quantum links ---------------------------------------------------------------------
    shaped('quantum_bridge', ['EQE', 'CBC', 'ESE'], {'E': mc('echo_shard'), 'Q': ov('quantum_processor'),
                                                     'C': mc('ender_chest'), 'B': ov('omnium_block'),
                                                     'S': mc('nether_star')})
    shapeless('quantum_link_card', [mc('ender_pearl'), ov('quantum_processor'), mc('paper'), ov('omnium_ingot')],
              count=2)
    shaped('storage_monitor', ['GGG', 'ILI', 'III'], {'G': mc('glass_pane'), 'I': mc('iron_ingot'),
                                                      'L': ov('logic_processor')})
    for name, part in (('import_bus', 'hopper'), ('export_bus', 'piston'), ('storage_bus', 'chest'),
                       ('destructor', 'diamond_pickaxe'), ('constructor', 'dispenser')):
        shaped(name, ['OXO', ' L '], {'O': ov('omnium_ingot'), 'X': mc(part), 'L': ov('logic_processor')})
    shaped('network_port', ['IHI', 'OLO', 'ICI'], {'I': mc('iron_ingot'), 'H': mc('hopper'), 'O': ov('omnium_ingot'),
                                                   'L': ov('logic_processor'), 'C': mc('chest')})
    shaped('signal_emitter', [' C ', 'OLO'], {'C': mc('comparator'), 'O': ov('omnium_ingot'), 'L': ov('logic_processor')})
    shaped('wireless_transmitter', [' E ', ' R ', 'OAO'], {'E': mc('ender_pearl'), 'R': mc('lightning_rod'),
                                                           'O': ov('omnium_ingot'), 'A': ov('advanced_processor')})
    shaped('wireless_terminal', [' E ', 'OTO', 'OAO'], {'E': mc('ender_pearl'), 'T': ov('crafting_terminal'),
                                                        'O': ov('omnium_ingot'), 'A': ov('advanced_processor')})
    kit.smithing('quantum_wireless_terminal', ov('quantum_upgrade_template'), ov('wireless_terminal'),
                 mc('nether_star'), ov('quantum_wireless_terminal'))


def loot_and_tags():
    for name in NETWORK_BLOCKS + EARLY_BLOCKS:
        kit.loot_self(name)
    # Tool tags are shared by every feature: merge into them instead of overwriting other entries.
    kit.merge_tag('block', 'mineable/pickaxe', [ov(n) for n in NETWORK_BLOCKS])
    kit.merge_tag('block', 'mineable/axe', [ov(n) for n in EARLY_BLOCKS])


def lang():
    names = {'fluid_cell_housing': 'Fluid Cell Housing', 'creative_fluid_cell': 'Creative Fluid Cell'}
    names.update({f'fluid_cell_{t}': f'{t.upper()} Fluid Cell' for t in TIER_IDS})
    path = kit.ROOT / 'tools' / 'lang' / 'network_art.json'
    kit.write_json(path, {f'item.{M}.{k}': v for k, v in names.items()})


def validate_recipe_ingredients():
    """Checks every minecraft: id used in our recipes against the vanilla jar (when it has been downloaded)."""
    import zipfile
    jars = sorted(kit.ROOT.glob('1.21.1/*/build/moddev/artifacts/*client-extra.jar'))
    if not jars:
        print('  (vanilla jar not found: build the project once to validate recipe ingredients)')
        return
    with zipfile.ZipFile(jars[0]) as jar:
        names = jar.namelist()
    items = {'minecraft:' + n.rsplit('/', 1)[1][:-5] for n in names if n.startswith('assets/minecraft/models/item/')}
    tags = {'minecraft:' + n[len('data/minecraft/tags/item/'):-5] for n in names if n.startswith('data/minecraft/tags/item/')}
    bad = []
    for path in sorted((kit.DATA / 'recipe').glob('*.json')):
        text = path.read_text(encoding='utf-8')
        for ref in set(re.findall(r'"(?:item|id|tag)": "(minecraft:[a-z0-9_/]+)"', text)):
            if ref not in items and ref not in tags:
                bad.append(f'{path.name}: {ref}')
    if bad:
        raise SystemExit('Unknown vanilla ids in recipes:\n  ' + '\n  '.join(bad))


# =================================================================================================
# main
# =================================================================================================

def shared_textures():
    t = Tex(16, seed=3)
    plate(t, GUNMETAL, base=2, hi=GUNMETAL[4], lo=GUNMETAL[0])
    t.save('block/network/casing')


def main():
    shared_textures()
    nexus_core()
    nexus_cable()
    storage_hub()
    inventory_cable()
    drive()
    terminals()
    storage_monitor()
    pointing_devices()
    network_port()
    signal_emitter()
    wireless_transmitter()
    quantum_bridge()
    omnium_block()
    storage_cell_sprites()
    fluid_cell_sprites()
    matrix_sprites()
    link_card_sprite()
    processor_sprites()
    upgrade_sprites()
    template_sprite()
    material_sprites()
    handheld_items()
    recipes()
    loot_and_tags()
    lang()
    validate_recipe_ingredients()
    print('network.py: assets and data written')


if __name__ == '__main__':
    main()
