"""Shared art toolkit for OmniVault: palette, 16px pixel-art primitives and model/blockstate writers.

Every feature's generator (tools/assets/<feature>.py) builds on this so the whole mod shares one look:
dark gunmetal casings with bevelled steel trim, recessed details, and glowing cyan/violet "omnium"
energy rendered full-bright through NeoForge's per-element light data.

Requires Pillow. Run feature scripts from the repository root, e.g. python3 tools/assets/network.py
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / '1.21.1' / 'common' / 'src' / 'main' / 'resources'
ASSETS = RESOURCES / 'assets' / 'omnivault'
DATA = RESOURCES / 'data' / 'omnivault'
MOD = 'omnivault'

# ---------------------------------------------------------------------------------------------
# Palette. Ramps run dark -> light; index 2 is the base tone.
# ---------------------------------------------------------------------------------------------


def rgb(hex_color: str, alpha: int = 255):
    hex_color = hex_color.lstrip('#')
    return (int(hex_color[0:2], 16), int(hex_color[2:4], 16), int(hex_color[4:6], 16), alpha)


GUNMETAL = [rgb('0d1015'), rgb('171b22'), rgb('222831'), rgb('2f3642'), rgb('3e4757'), rgb('525d70')]
STEEL = [rgb('3a414c'), rgb('59626f'), rgb('7d8794'), rgb('a3acb8'), rgb('c8cfd8'), rgb('e9edf2')]
CYAN = [rgb('0a3340'), rgb('0f5d73'), rgb('1aa3c2'), rgb('38e1ff'), rgb('8ef3ff'), rgb('e0fdff')]
VIOLET = [rgb('241038'), rgb('3f1d63'), rgb('6a35a8'), rgb('a46bff'), rgb('c9a6ff'), rgb('f0e6ff')]
AMBER = [rgb('3d2405'), rgb('7a4a0c'), rgb('c27a16'), rgb('f2b33d'), rgb('ffd77a'), rgb('fff3d6')]
GREEN = [rgb('0c3318'), rgb('15632d'), rgb('22a34a'), rgb('5be08a'), rgb('a6f5c0'), rgb('e8fff0')]
RED = [rgb('3a0c0c'), rgb('701717'), rgb('b82a2a'), rgb('ff6b6b'), rgb('ffb0b0'), rgb('fff0f0')]
BLACK = rgb('07090d')
CLEAR = (0, 0, 0, 0)

# Tier ramps for cells, vaults, backpacks and drawers.
COPPER = [rgb('4a2412'), rgb('7a3d1f'), rgb('b0602f'), rgb('d9874e'), rgb('f0b07e'), rgb('ffe0c4')]
IRON = [rgb('3b3f45'), rgb('6b7078'), rgb('9aa0a8'), rgb('c4c9cf'), rgb('e3e6ea'), rgb('ffffff')]
GOLD = [rgb('5c3b06'), rgb('9c6a0e'), rgb('d9a21c'), rgb('f5c542'), rgb('ffe082'), rgb('fff6cf')]
DIAMOND = [rgb('0b3b3a'), rgb('12706c'), rgb('1fb5ad'), rgb('4de8e0'), rgb('a3fff8'), rgb('e6fffd')]
NETHERITE = [rgb('17121a'), rgb('2a2230'), rgb('3d3343'), rgb('554a5c'), rgb('766a7e'), rgb('a093a8')]
OMNIUM = VIOLET
TIERS = {'copper': COPPER, 'iron': IRON, 'gold': GOLD, 'diamond': DIAMOND, 'netherite': NETHERITE, 'omnium': OMNIUM}


def mix(a, b, t: float):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(color, factor: float):
    return (max(0, min(255, int(color[0] * factor))), max(0, min(255, int(color[1] * factor))),
            max(0, min(255, int(color[2] * factor))), color[3])


# ---------------------------------------------------------------------------------------------
# Pixel canvas
# ---------------------------------------------------------------------------------------------


class Tex:
    """A small RGBA canvas with pixel-art helpers. Coordinates are inclusive pixel indices."""

    def __init__(self, width: int = 16, height: int | None = None, fill=CLEAR, seed: int = 0):
        self.w = width
        self.h = height or width
        self.img = Image.new('RGBA', (self.w, self.h), fill)
        self.px = self.img.load()
        self.rng = random.Random(seed)

    # -- basic ops ------------------------------------------------------------------------------
    def set(self, x: int, y: int, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = color

    def get(self, x: int, y: int):
        return self.px[x, y]

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)
        return self

    def outline(self, x0, y0, x1, y1, color):
        for x in range(x0, x1 + 1):
            self.set(x, y0, color)
            self.set(x, y1, color)
        for y in range(y0, y1 + 1):
            self.set(x0, y, color)
            self.set(x1, y, color)
        return self

    def hline(self, x0, x1, y, color):
        return self.rect(x0, y, x1, y, color)

    def vline(self, x, y0, y1, color):
        return self.rect(x, y0, x, y1, color)

    def line(self, x0, y0, x1, y1, color):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            self.set(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), color)
        return self

    def fill(self, color):
        return self.rect(0, 0, self.w - 1, self.h - 1, color)

    # -- surfaces -------------------------------------------------------------------------------
    def noise(self, ramp, x0=0, y0=0, x1=None, y1=None, base=2, spread=1, chance=0.35):
        """Fills an area with the ramp's base tone and sprinkles neighbouring tones for texture."""
        x1 = self.w - 1 if x1 is None else x1
        y1 = self.h - 1 if y1 is None else y1
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                idx = base
                if self.rng.random() < chance:
                    idx = max(0, min(len(ramp) - 1, base + self.rng.choice([-spread, spread])))
                self.set(x, y, ramp[idx])
        return self

    def brushed(self, ramp, x0=0, y0=0, x1=None, y1=None, base=2, vertical=False):
        """Brushed-metal streaks."""
        x1 = self.w - 1 if x1 is None else x1
        y1 = self.h - 1 if y1 is None else y1
        lines = range(x0, x1 + 1) if vertical else range(y0, y1 + 1)
        for i in lines:
            tone = ramp[max(0, min(len(ramp) - 1, base + self.rng.choice([0, 0, 0, 1, -1])))]
            if vertical:
                self.rect(i, y0, i, y1, tone)
            else:
                self.rect(x0, i, x1, i, tone)
        for _ in range(((x1 - x0 + 1) * (y1 - y0 + 1)) // 10):
            x = self.rng.randint(x0, x1)
            y = self.rng.randint(y0, y1)
            self.set(x, y, mix(self.get(x, y), ramp[min(len(ramp) - 1, base + 1)], 0.5))
        return self

    def bevel(self, x0, y0, x1, y1, ramp, base=2, depth=1, inset=False):
        """A raised (or inset) plate: light top-left edge, dark bottom-right edge."""
        self.rect(x0, y0, x1, y1, ramp[base])
        light = ramp[min(len(ramp) - 1, base + 2)]
        dark = ramp[max(0, base - 2)]
        if inset:
            light, dark = dark, light
        for d in range(depth):
            self.hline(x0 + d, x1 - d, y0 + d, light)
            self.vline(x0 + d, y0 + d, y1 - d, light)
            self.hline(x0 + d, x1 - d, y1 - d, dark)
            self.vline(x1 - d, y0 + d, y1 - d, dark)
        return self

    def frame(self, ramp, width=1, base=3):
        """Bevelled border around the whole canvas (the classic machine-casing edge)."""
        for d in range(width):
            self.hline(d, self.w - 1 - d, d, ramp[min(len(ramp) - 1, base + 1)])
            self.vline(d, d, self.h - 1 - d, ramp[min(len(ramp) - 1, base + 1)])
            self.hline(d, self.w - 1 - d, self.h - 1 - d, ramp[max(0, base - 2)])
            self.vline(self.w - 1 - d, d, self.h - 1 - d, ramp[max(0, base - 2)])
        return self

    def screw(self, x, y, ramp=STEEL):
        self.set(x, y, ramp[3])
        self.set(x + 1, y, ramp[1])
        self.set(x, y + 1, ramp[1])
        self.set(x + 1, y + 1, ramp[0])
        return self

    def vents(self, x0, y0, x1, y1, ramp=GUNMETAL, spacing=2):
        for y in range(y0, y1 + 1, spacing):
            self.hline(x0, x1, y, ramp[0])
            if y + 1 <= y1:
                self.hline(x0, x1, y + 1, ramp[3])
        return self

    def glow_line(self, x0, y0, x1, y1, ramp=CYAN):
        self.line(x0, y0, x1, y1, ramp[3])
        return self

    def led(self, x, y, ramp=GREEN):
        self.set(x, y, ramp[4])
        return self

    def gradient_v(self, x0, y0, x1, y1, top, bottom):
        for y in range(y0, y1 + 1):
            t = 0 if y1 == y0 else (y - y0) / (y1 - y0)
            self.hline(x0, x1, y, mix(top, bottom, t))
        return self

    def radial(self, cx, cy, radius, inner, outer):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius
                if d <= 1:
                    self.set(x, y, mix(inner, outer, d))
        return self

    def paste(self, other: 'Tex', x=0, y=0):
        self.img.alpha_composite(other.img, (x, y))
        return self

    def copy(self) -> 'Tex':
        t = Tex(self.w, self.h)
        t.img = self.img.copy()
        t.px = t.img.load()
        return t

    def rotated(self, quarter_turns: int) -> 'Tex':
        t = Tex(self.w, self.h)
        t.img = self.img.rotate(-90 * quarter_turns)
        t.px = t.img.load()
        return t

    def flipped(self) -> 'Tex':
        t = Tex(self.w, self.h)
        t.img = self.img.transpose(Image.FLIP_LEFT_RIGHT)
        t.px = t.img.load()
        return t

    def save(self, rel: str):
        """Saves to assets/omnivault/textures/<rel>.png"""
        path = ASSETS / 'textures' / (rel + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)
        return path


def save_animation(rel: str, frames: list[Tex], frametime: int = 2, interpolate: bool = True):
    """Stacks frames vertically and writes the .mcmeta next to the texture."""
    w, h = frames[0].w, frames[0].h
    sheet = Image.new('RGBA', (w, h * len(frames)), CLEAR)
    for i, frame in enumerate(frames):
        sheet.paste(frame.img, (0, i * h))
    path = ASSETS / 'textures' / (rel + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    meta = {'animation': {'frametime': frametime, 'interpolate': interpolate}}
    path.with_suffix('.png.mcmeta').write_text(json.dumps(meta, indent=2) + '\n')
    return path


# ---------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------

FACES = ('north', 'east', 'south', 'west', 'up', 'down')


def auto_uv(face: str, f, t):
    """Vanilla's default UV for a face of an element spanning f..t (what Blockbench calls auto UV)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face == 'north':
        return [16 - x1, 16 - y1, 16 - x0, 16 - y0]
    if face == 'south':
        return [x0, 16 - y1, x1, 16 - y0]
    if face == 'west':
        return [z0, 16 - y1, z1, 16 - y0]
    if face == 'east':
        return [16 - z1, 16 - y1, 16 - z0, 16 - y0]
    if face == 'up':
        return [x0, z0, x1, z1]
    return [x0, 16 - z1, x1, 16 - z0]  # down


class Model:
    """A Minecraft block/item model made of boxes, with optional full-bright (emissive) elements."""

    def __init__(self, parent: str | None = 'block/block', textures: dict | None = None,
                 render_type: str | None = None, ambient_occlusion: bool = True):
        self.parent = parent
        self.textures = dict(textures or {})
        self.render_type = render_type
        self.ao = ambient_occlusion
        self.elements = []
        self.display = None

    def box(self, f, t, tex=None, faces=None, uv=None, rotation=None, emissive=False, shade=True,
            tint=None, cull=False, name=None, skip=()):
        """Adds an element.

        tex: texture variable (without #) used for every face unless ``faces`` overrides it.
        faces: {face: texvar | (texvar, [u0,v0,u1,v1]) | (texvar, uv, rotation) | None} per face.
        uv: {face: [u0,v0,u1,v1]} overrides for faces using ``tex``.
        rotation: (angle, axis, origin) with angle in {-45,-22.5,0,22.5,45}.
        emissive: renders the element full-bright on NeoForge.
        cull: adds cullface for faces lying on the block boundary.
        skip: faces to omit.
        """
        element = {'from': list(f), 'to': list(t)}
        if name:
            element['name'] = name
        if rotation:
            angle, axis, origin = rotation
            element['rotation'] = {'angle': angle, 'axis': axis, 'origin': list(origin)}
        if not shade:
            element['shade'] = False
        out_faces = {}
        for face in FACES:
            if face in skip:
                continue
            spec = (faces or {}).get(face, tex) if faces is not None and face in faces else tex
            if spec is None:
                continue
            face_rot = None
            if isinstance(spec, tuple):
                if len(spec) == 3:
                    texvar, face_uv, face_rot = spec
                else:
                    texvar, face_uv = spec
            else:
                texvar = spec
                face_uv = (uv or {}).get(face) or auto_uv(face, f, t)
            entry = {'uv': [round(v, 4) for v in face_uv], 'texture': '#' + texvar}
            if face_rot:
                entry['rotation'] = face_rot
            if tint is not None:
                entry['tintindex'] = tint
            if cull:
                boundary = {'north': f[2] == 0, 'south': t[2] == 16, 'west': f[0] == 0, 'east': t[0] == 16,
                            'down': f[1] == 0, 'up': t[1] == 16}
                if boundary[face]:
                    entry['cullface'] = face
            out_faces[face] = entry
        element['faces'] = out_faces
        if emissive:
            element['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        self.elements.append(element)
        return self

    def block_display(self):
        """The vanilla block item transforms."""
        self.display = {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        }
        return self

    def to_json(self):
        data = {}
        if self.parent:
            data['parent'] = self.parent
        if self.render_type:
            data['render_type'] = self.render_type
        if not self.ao:
            data['ambientocclusion'] = False
        data['textures'] = {k: (v if ':' in v else f'{MOD}:{v}') for k, v in self.textures.items()}
        if self.elements:
            data['elements'] = self.elements
        if self.display:
            data['display'] = self.display
        return data

    def save(self, rel: str):
        """Saves to assets/omnivault/models/<rel>.json"""
        return write_json(ASSETS / 'models' / (rel + '.json'), self.to_json())


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    return path


def item_model(name: str, parent: str | None = None, layers: list[str] | None = None):
    """Item model: inherits a block model (``parent``) or is a flat generated sprite (``layers``)."""
    if parent:
        data = {'parent': parent if ':' in parent else f'{MOD}:{parent}'}
    else:
        data = {'parent': 'minecraft:item/generated',
                'textures': {f'layer{i}': (l if ':' in l else f'{MOD}:{l}') for i, l in enumerate(layers or [f'item/{name}'])}}
    return write_json(ASSETS / 'models' / 'item' / (name + '.json'), data)


def blockstate_single(name: str, model: str | None = None):
    return write_json(ASSETS / 'blockstates' / (name + '.json'),
                      {'variants': {'': {'model': f'{MOD}:{model or "block/" + name}'}}})


def blockstate_horizontal(name: str, model: str | None = None, extra: dict[str, dict[str, str]] | None = None):
    """Variants for HORIZONTAL_FACING (model faces north). ``extra`` maps other property values to models,
    e.g. {'active=true': 'block/x_on', 'active=false': 'block/x'}."""
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    variants = {}
    combos = list((extra or {'': model or f'block/{name}'}).items())
    for facing, y in rot.items():
        for prop, mdl in combos:
            key = f'facing={facing}' + (',' + prop if prop else '')
            v = {'model': f'{MOD}:{mdl}'}
            if y:
                v['y'] = y
            variants[key] = v
    return write_json(ASSETS / 'blockstates' / (name + '.json'), {'variants': variants})


def blockstate_facing(name: str, model: str | None = None, extra: dict[str, str] | None = None):
    """Variants for FACING (all six directions); the model must face north."""
    rot = {'north': (0, 0), 'east': (0, 90), 'south': (0, 180), 'west': (0, 270), 'up': (270, 0), 'down': (90, 0)}
    variants = {}
    combos = list((extra or {'': model or f'block/{name}'}).items())
    for facing, (x, y) in rot.items():
        for prop, mdl in combos:
            key = f'facing={facing}' + (',' + prop if prop else '')
            v = {'model': f'{MOD}:{mdl}'}
            if x:
                v['x'] = x
            if y:
                v['y'] = y
            variants[key] = v
    return write_json(ASSETS / 'blockstates' / (name + '.json'), {'variants': variants})


def blockstate_multipart(name: str, parts: list[dict]):
    return write_json(ASSETS / 'blockstates' / (name + '.json'), {'multipart': parts})


# ---------------------------------------------------------------------------------------------
# Data helpers
# ---------------------------------------------------------------------------------------------


def loot_self(name: str):
    """Block drops itself (explosion-safe), copying component data the block entity saved."""
    return write_json(DATA / 'loot_table' / 'blocks' / (name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1, 'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:{name}', 'functions': [
                {'function': 'minecraft:copy_components', 'source': 'block_entity',
                 'include': ['minecraft:custom_name']}]}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
        }],
    })


def shaped(name: str, pattern: list[str], key: dict[str, str], result: str | None = None, count: int = 1):
    """Shaped crafting recipe. Key values starting with '#' are tags."""
    return write_json(DATA / 'recipe' / (name + '.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: ({'tag': v[1:]} if v.startswith('#') else {'item': v}) for k, v in key.items()},
        'result': {'id': result or f'{MOD}:{name}', 'count': count},
    })


def shapeless(name: str, ingredients: list[str], result: str | None = None, count: int = 1):
    return write_json(DATA / 'recipe' / (name + '.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [({'tag': v[1:]} if v.startswith('#') else {'item': v}) for v in ingredients],
        'result': {'id': result or f'{MOD}:{name}', 'count': count},
    })


def smelting(name: str, ingredient: str, result: str, xp: float = 0.5, blasting: bool = True):
    write_json(DATA / 'recipe' / (name + '_smelting.json'), {
        'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': {'item': ingredient},
        'result': {'id': result}, 'experience': xp, 'cookingtime': 200})
    if blasting:
        write_json(DATA / 'recipe' / (name + '_blasting.json'), {
            'type': 'minecraft:blasting', 'category': 'misc', 'ingredient': {'item': ingredient},
            'result': {'id': result}, 'experience': xp, 'cookingtime': 100})


def smithing(name: str, template: str, base: str, addition: str, result: str):
    """Smithing transform; the result keeps the base item's data components."""
    return write_json(DATA / 'recipe' / (name + '.json'), {
        'type': 'minecraft:smithing_transform', 'template': {'item': template}, 'base': {'item': base},
        'addition': {'item': addition}, 'result': {'id': result, 'count': 1}})


def tag(registry: str, name: str, values: list[str], namespace: str = MOD):
    """registry: 'block', 'item'... written under data/<namespace>/tags/<registry>/<name>.json"""
    base = RESOURCES / 'data' / namespace / 'tags' / registry
    return write_json(base / (name + '.json'), {'replace': False, 'values': values})


def merge_tag(registry: str, name: str, values: list, namespace: str = 'minecraft'):
    """Adds entries to a tag file several features write (e.g. minecraft:mineable/pickaxe).

    Keeps every existing entry, drops duplicates (a required entry wins over an optional one for the same id) and
    sorts by id, so the result is the same whichever generator runs first.
    """
    path = RESOURCES / 'data' / namespace / 'tags' / registry / (name + '.json')
    existing = json.loads(path.read_text(encoding='utf-8')).get('values', []) if path.exists() else []
    by_id = {}
    for value in existing + list(values):
        key = value['id'] if isinstance(value, dict) else value
        if key not in by_id or isinstance(by_id[key], dict) and not isinstance(value, dict):
            by_id[key] = value
    return write_json(path, {'replace': False, 'values': [by_id[k] for k in sorted(by_id)]})
