#!/usr/bin/env python3
"""Offline renderer for OmniVault block and item models (Pillow + numpy software rasteriser).

Renders any model JSON under assets/omnivault/models the way Minecraft bakes it: parent chains
(vanilla parents are read from the Minecraft jar in the Gradle caches when available, with built-in
fallbacks), element rotations with rescale, per-face UVs and UV rotation, texture variables, the first
(or any) animation frame, tint indices, NeoForge emissive elements (full-bright, unshaded), directional
face shading, solid / cutout / translucent render types and extruded ``item/generated`` sprites.

The default camera is the inventory (GUI) view: the model's ``display.gui`` transform with an
orthographic projection, supersampled and downscaled onto a transparent background.

Command line (run from the repository root)::

    python3 tools/assets/render_models.py block/drive --size 512 --out build/previews/drive.png
    python3 tools/assets/render_models.py item/storage_cell_16k --tint 0=f5c542 --out build/previews/cell.png
    python3 tools/assets/render_models.py block/nexus_core --yaw 200 --pitch 25 --bloom 0.6 --out build/previews/core.png
    python3 tools/assets/render_models.py --sheet --out build/previews/sheet.png

As a library (marketing scenes)::

    import sys; sys.path.insert(0, 'tools/assets')
    import render_models as rm
    img = rm.render_model('block/terminal_active', size=1024, yaw=200, pitch=20, bloom=0.5)
    scene = rm.render_scene([rm.Part('block/drive_active'),
                             rm.Part('block/drive_cell', translate=(8.5, 11, 0), tints={0: 0xF5C542})], size=1024)

Model names may be given as ``block/x``, ``omnivault:block/x`` or ``minecraft:block/stone``.
"""
from __future__ import annotations

import argparse
import io
import json
import math
import re
import sys
import zipfile
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / '1.21.1' / 'common' / 'src' / 'main' / 'resources'
ASSETS = RESOURCES / 'assets'
MOD = 'omnivault'

DEFAULT_TINT = 0x38E1FF  # tint index 0 without a preset: the default (undyed) cable glow
# Tint presets for the contact sheet and CLI, matched against the model path (first match wins).
TIER_COLOURS = {'4k': 0x9AA0A8, '16k': 0xF5C542, '64k': 0x4DE8E0, '256k': 0x5BE08A, '1m': 0xA46BFF,
                '16m': 0xFF6BD5, 'creative': 0xFF4D4D}
TINT_PRESETS = [
    (r'(storage|fluid)_cell_(4k|16k|64k|256k|1m|16m)$', lambda m: {0: TIER_COLOURS[m.group(2)]}),
    (r'creative_(storage|fluid)_cell$', lambda m: {0: TIER_COLOURS['creative']}),
    (r'drive_cell_led$', lambda m: {0: 0x5BE08A}),
    (r'drive_cell$', lambda m: {0: TIER_COLOURS['16k']}),
    (r'nexus_core_(crystal|ring)$', lambda m: {0: 0xFFFFFF}),
]
# Standalone models drawn by block entity renderers through ModelBlockRenderer.renderModel, which
# uses putBulkData with brightness 1: no directional face shading in game, so none here either.
UNSHADED_MODELS = r'(drive_cell|drive_cell_led|nexus_core_crystal|nexus_core_ring)$'

FACE_VERTS = {  # net.minecraft.client.renderer.FaceInfo: (x, y, z) picking from (min, max) per axis
    'down': ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    'up': ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}

BUILTIN_MODELS = {  # used only when no Minecraft jar is found
    'minecraft:block/block': {'gui_light': 'side', 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]}}},
    'minecraft:block/cube': {'parent': 'block/block', 'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
        f: {'texture': '#' + f, 'cullface': f} for f in FACE_VERTS}}]},
    'minecraft:block/cube_all': {'parent': 'block/cube', 'textures': {
        'particle': '#all', **{f: '#all' for f in FACE_VERTS}}},
    'minecraft:item/generated': {'parent': 'builtin/generated', 'gui_light': 'front', 'display': {
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [1, 1, 1]}}},
    'minecraft:item/handheld': {'parent': 'item/generated', 'display': {
        'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]}}},
}


# ---------------------------------------------------------------------------------------------------
# Resource access
# ---------------------------------------------------------------------------------------------------

def _split(ref: str, default_ns: str = MOD):
    ref = ref.strip()
    if ':' in ref:
        ns, path = ref.split(':', 1)
    else:
        ns, path = default_ns, ref
    return ns, path


@lru_cache(maxsize=1)
def vanilla_jar():
    """The Minecraft client resources jar from the Gradle caches, if the project has been built once."""
    candidates = sorted(ROOT.glob('1.21.1/*/build/moddev/artifacts/*client-extra.jar'))
    candidates += sorted(Path.home().glob('.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar'))
    for path in candidates:
        try:
            return zipfile.ZipFile(path)
        except (OSError, zipfile.BadZipFile):
            continue
    return None


def read_resource(ns: str, kind: str, path: str, ext: str):
    """Bytes of assets/<ns>/<kind>/<path><ext> from the mod resources or the vanilla jar, else None."""
    local = ASSETS / ns / kind / (path + ext)
    if local.exists():
        return local.read_bytes()
    jar = vanilla_jar()
    if jar is not None:
        try:
            return jar.read(f'assets/{ns}/{kind}/{path}{ext}')
        except KeyError:
            return None
    return None


@lru_cache(maxsize=None)
def load_json_model(ref: str):
    ns, path = _split(ref, 'minecraft' if ref.startswith(('builtin/',)) else MOD)
    key = f'{ns}:{path}'
    raw = read_resource(ns, 'models', path, '.json')
    if raw is not None:
        return json.loads(raw)
    if key in BUILTIN_MODELS:
        return BUILTIN_MODELS[key]
    raise FileNotFoundError(f'Model not found: {key}')


def _missing_texture():
    img = np.zeros((16, 16, 4), dtype=np.float32)
    img[..., 3] = 1
    img[:8, :8, 0] = img[:8, :8, 2] = 1
    img[8:, 8:, 0] = img[8:, 8:, 2] = 1
    return img


@lru_cache(maxsize=None)
def load_texture(ref: str, frame: int = 0):
    """Texture as float RGBA array (H, W, 4) in 0..1; animated textures yield the requested frame.
    Unqualified ids are vanilla ones (``block/dirt`` is ``minecraft:block/dirt``), as in the game."""
    ns, path = _split(ref, 'minecraft')
    raw = read_resource(ns, 'textures', path, '.png')
    if raw is None:
        return _missing_texture()
    img = Image.open(io.BytesIO(raw)).convert('RGBA')
    arr = np.asarray(img, dtype=np.float32) / 255.0
    meta_raw = read_resource(ns, 'textures', path, '.png.mcmeta')
    if meta_raw is not None:
        anim = json.loads(meta_raw).get('animation')
        if anim is not None:
            w = anim.get('width', img.width if 'height' not in anim else img.width)
            h = anim.get('height', w)
            per_row = max(1, img.width // w)
            count = max(1, (img.width // w) * (img.height // h))
            frames = anim.get('frames') or list(range(count))
            entry = frames[frame % len(frames)]
            index = entry['index'] if isinstance(entry, dict) else int(entry)
            fx, fy = (index % per_row) * w, (index // per_row) * h
            arr = arr[fy:fy + h, fx:fx + w]
    return arr


def animation_length(ref: str) -> int:
    ns, path = _split(ref, 'minecraft')
    meta_raw = read_resource(ns, 'textures', path, '.png.mcmeta')
    if meta_raw is None:
        return 1
    anim = json.loads(meta_raw).get('animation') or {}
    if anim.get('frames'):
        return len(anim['frames'])
    raw = read_resource(ns, 'textures', path, '.png')
    img = Image.open(io.BytesIO(raw))
    w = anim.get('width', img.width)
    h = anim.get('height', w)
    return max(1, (img.width // w) * (img.height // h))


# ---------------------------------------------------------------------------------------------------
# Model resolution
# ---------------------------------------------------------------------------------------------------

@dataclass
class ResolvedModel:
    name: str
    textures: dict
    elements: list | None
    display: dict
    render_type: str | None
    gui_light: str
    generated: bool
    chain: list = field(default_factory=list)

    def texture(self, ref: str) -> str:
        seen = set()
        while ref.startswith('#'):
            key = ref[1:]
            if key in seen or key not in self.textures:
                return 'minecraft:missingno'
            seen.add(key)
            ref = self.textures[key]
        return ref


def resolve_model(ref: str) -> ResolvedModel:
    chain = []
    current = ref if ':' in ref else f'{MOD}:{ref}'
    while current:
        if current in ('builtin/generated', 'minecraft:builtin/generated'):
            chain.append(('builtin/generated', {}))
            break
        if current in ('builtin/entity', 'minecraft:builtin/entity'):
            chain.append(('builtin/entity', {}))
            break
        data = load_json_model(current)
        chain.append((current, data))
        parent = data.get('parent')
        if parent and ':' not in parent and not parent.startswith('builtin/'):
            parent = f'minecraft:{parent}'  # unqualified ids are vanilla ones, as in ResourceLocation
        current = parent or None
    textures, display = {}, {}
    elements, render_type, gui_light = None, None, None
    for name, data in reversed(chain):
        textures.update(data.get('textures', {}))
        for key, value in data.get('display', {}).items():
            display[key] = value
    for name, data in chain:
        if elements is None and 'elements' in data:
            elements = data['elements']
        if render_type is None and 'render_type' in data:
            render_type = data['render_type']
        if gui_light is None and 'gui_light' in data:
            gui_light = data['gui_light']
    generated = any(name == 'builtin/generated' for name, _ in chain) and elements is None
    return ResolvedModel(ref, textures, elements, display, render_type, gui_light or 'side', generated,
                         [name for name, _ in chain])


def default_render_type(model: ResolvedModel) -> str:
    rt = (model.render_type or '').split(':')[-1]
    if rt in ('solid', 'cutout', 'cutout_mipped', 'translucent'):
        return 'cutout' if rt == 'cutout_mipped' else rt
    # Items that are not block models render on the translucent item sheet.
    is_block = any(n.split(':')[-1].startswith('block/') for n in model.chain)
    return 'solid' if is_block else 'translucent'


def count_elements(ref: str) -> int:
    m = resolve_model(ref)
    return len(m.elements or [])


# ---------------------------------------------------------------------------------------------------
# Geometry
# ---------------------------------------------------------------------------------------------------

def rot_axis(axis: str, degrees: float):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def rot_xyz(rx, ry, rz):
    """JOML Quaternionf.rotationXYZ: applied to a vector as Rx * Ry * Rz."""
    return rot_axis('x', rx) @ rot_axis('y', ry) @ rot_axis('z', rz)


@dataclass
class Quad:
    verts: np.ndarray       # (4, 3) in model pixels (0..16)
    uvs: np.ndarray         # (4, 2) in 0..16 units
    texture: str
    tint: int
    shade: bool
    emissive: bool
    normal: np.ndarray      # (3,) in model space
    implicit_tint: bool = False  # item/generated layer index: only tinted by an explicit colour


def element_quads(model: ResolvedModel, elements: list) -> list[Quad]:
    quads = []
    rescale22 = 1.0 / math.cos(math.radians(22.5)) - 1.0
    rescale45 = 1.0 / math.cos(math.radians(45)) - 1.0
    for el in elements:
        f = np.array(el['from'], dtype=np.float64)
        t = np.array(el['to'], dtype=np.float64)
        lo, hi = np.minimum(f, t), np.maximum(f, t)
        rot = el.get('rotation')
        R = scale = origin = None
        if rot and rot.get('angle', 0):
            axis = rot['axis']
            R = rot_axis(axis, float(rot['angle']))
            origin = np.array(rot.get('origin', [8, 8, 8]), dtype=np.float64)
            scale = np.ones(3)
            if rot.get('rescale'):
                k = rescale22 if abs(float(rot['angle'])) == 22.5 else rescale45
                scale = np.array([0.0 if axis == 'x' else k, 0.0 if axis == 'y' else k, 0.0 if axis == 'z' else k]) + 1.0
        nd = el.get('neoforge_data') or {}
        emissive = (nd.get('block_light', 0) >= 15) or (el.get('emissivity', 0) >= 15)
        shade = el.get('shade', True)
        for face, spec in el.get('faces', {}).items():
            corners = FACE_VERTS[face]
            verts = np.array([[hi[i] if c[i] else lo[i] for i in range(3)] for c in corners], dtype=np.float64)
            if R is not None:
                verts = ((verts - origin) @ R.T) * scale + origin
            uv = spec.get('uv') or _auto_uv(face, lo, hi)
            r = int(spec.get('rotation', 0)) // 90
            uvs = []
            for i in range(4):
                j = (i + r) % 4
                u = uv[0] if j in (0, 1) else uv[2]
                v = uv[1] if j in (0, 3) else uv[3]
                uvs.append((u, v))
            n = np.cross(verts[1] - verts[0], verts[2] - verts[1])
            length = np.linalg.norm(n)
            if length < 1e-9:
                n = np.cross(verts[2] - verts[0], verts[3] - verts[1])
                length = np.linalg.norm(n)
                if length < 1e-9:
                    continue
            quads.append(Quad(verts, np.array(uvs, dtype=np.float64), model.texture(spec.get('texture', '#missing')),
                              int(spec.get('tintindex', -1)), shade, emissive, n / length))
    return quads


def _auto_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
            'west': [z0, 16 - y1, z1, 16 - y0], 'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
            'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0]}[face]


def generated_quads(model: ResolvedModel, frame: int = 0) -> list[Quad]:
    """item/generated: each layer becomes a 1px-thick extruded sprite (like ItemModelGenerator)."""
    quads = []
    i = 0
    while f'layer{i}' in model.textures:
        ref = model.texture(f'#layer{i}')
        tex = load_texture(ref, frame)
        h, w = tex.shape[:2]
        sx, sy = 16.0 / w, 16.0 / h
        z0, z1 = 7.5, 8.5
        full = np.array([[0, 16, z1], [0, 0, z1], [16, 0, z1], [16, 16, z1]], dtype=np.float64)
        quads.append(Quad(full, np.array([[0, 0], [0, 16], [16, 16], [16, 0]], dtype=np.float64), ref, i, True, False,
                          np.array([0, 0, 1.0])))
        back = np.array([[16, 16, z0], [16, 0, z0], [0, 0, z0], [0, 16, z0]], dtype=np.float64)
        quads.append(Quad(back, np.array([[16, 0], [16, 16], [0, 16], [0, 0]], dtype=np.float64), ref, i, True, False,
                          np.array([0, 0, -1.0])))
        alpha = tex[..., 3] > 0.004
        for py in range(h):
            for px in range(w):
                if not alpha[py, px]:
                    continue
                uv = np.array([[px * sx + 0.01, py * sy + 0.01]] * 4) + np.array([[0, 0], [0, 1], [1, 1], [1, 0]]) * [sx - 0.02, sy - 0.02]
                x0, x1 = px * sx, (px + 1) * sx
                y1, y0 = 16 - py * sy, 16 - (py + 1) * sy
                if px == 0 or not alpha[py, px - 1]:
                    quads.append(Quad(np.array([[x0, y1, z0], [x0, y0, z0], [x0, y0, z1], [x0, y1, z1]]), uv, ref, i, True, False, np.array([-1.0, 0, 0])))
                if px == w - 1 or not alpha[py, px + 1]:
                    quads.append(Quad(np.array([[x1, y1, z1], [x1, y0, z1], [x1, y0, z0], [x1, y1, z0]]), uv, ref, i, True, False, np.array([1.0, 0, 0])))
                if py == 0 or not alpha[py - 1, px]:
                    quads.append(Quad(np.array([[x0, y1, z0], [x0, y1, z1], [x1, y1, z1], [x1, y1, z0]]), uv, ref, i, True, False, np.array([0, 1.0, 0])))
                if py == h - 1 or not alpha[py + 1, px]:
                    quads.append(Quad(np.array([[x0, y0, z1], [x0, y0, z0], [x1, y0, z0], [x1, y0, z1]]), uv, ref, i, True, False, np.array([0, -1.0, 0])))
        i += 1
    for q in quads:
        q.implicit_tint = True
    return quads


def model_quads(ref: str, frame: int = 0):
    model = resolve_model(ref)
    if model.generated:
        return model, generated_quads(model, frame)
    return model, element_quads(model, model.elements or [])


# ---------------------------------------------------------------------------------------------------
# Rasteriser
# ---------------------------------------------------------------------------------------------------

def _shade(normal: np.ndarray) -> float:
    """Vanilla/NeoForge directional face shading: up 1.0, down 0.5, north/south 0.8, east/west 0.6."""
    nx, ny, nz = normal
    return float(min(1.0, nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (1.0 if ny > 0 else 0.5)))


def _rgb(colour: int):
    return np.array([(colour >> 16 & 255) / 255.0, (colour >> 8 & 255) / 255.0, (colour & 255) / 255.0])


@dataclass
class Part:
    """One model placed in a scene. ``matrix`` (3x3) rotates about ``pivot`` (pixels), then ``translate`` moves it."""
    model: str
    translate: tuple = (0, 0, 0)
    matrix: np.ndarray | None = None
    pivot: tuple = (8, 8, 8)
    tints: dict | None = None
    render_type: str | None = None
    brightness: float = 1.0
    shade: bool | None = None  # None: automatic (off for block entity renderer models)


class Canvas:
    def __init__(self, size: int):
        self.size = size
        self.color = np.zeros((size, size, 3), dtype=np.float32)   # premultiplied
        self.alpha = np.zeros((size, size), dtype=np.float32)
        self.depth = np.full((size, size), -np.inf, dtype=np.float32)
        self.glow = np.zeros((size, size, 3), dtype=np.float32)

    def triangle(self, p, uv, tex, colour, mode, clamp_uv, emissive, translucent_pass):
        """p: (3,3) screen x, y, depth; uv: (3,2) texel coords. Barycentric scanline over the bbox."""
        size = self.size
        xmin = max(int(math.floor(p[:, 0].min())), 0)
        xmax = min(int(math.ceil(p[:, 0].max())), size - 1)
        ymin = max(int(math.floor(p[:, 1].min())), 0)
        ymax = min(int(math.ceil(p[:, 1].max())), size - 1)
        if xmin > xmax or ymin > ymax:
            return
        (x0, y0, _), (x1, y1, _), (x2, y2, _) = p
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-12:
            return
        xs = np.arange(xmin, xmax + 1, dtype=np.float64) + 0.5
        ys = np.arange(ymin, ymax + 1, dtype=np.float64) + 0.5
        X, Y = np.meshgrid(xs, ys)
        w0 = ((x1 - X) * (y2 - Y) - (x2 - X) * (y1 - Y)) / area
        w1 = ((x2 - X) * (y0 - Y) - (x0 - X) * (y2 - Y)) / area
        w2 = 1.0 - w0 - w1
        eps = -1e-7
        inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
        if not inside.any():
            return
        z = w0 * p[0, 2] + w1 * p[1, 2] + w2 * p[2, 2]
        region_depth = self.depth[ymin:ymax + 1, xmin:xmax + 1]
        inside &= z >= region_depth - 1e-6
        if not inside.any():
            return
        u = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
        v = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
        th, tw = tex.shape[:2]
        (u_lo, u_hi), (v_lo, v_hi) = clamp_uv
        tx = np.clip(np.floor(np.clip(u, u_lo, u_hi)), 0, tw - 1).astype(np.int32)
        ty = np.clip(np.floor(np.clip(v, v_lo, v_hi)), 0, th - 1).astype(np.int32)
        texel = tex[ty, tx]
        a = texel[..., 3]
        rgb = texel[..., :3] * colour
        if mode == 'solid':
            a = np.ones_like(a)
        elif mode == 'cutout':
            inside &= a >= 0.1
            a = np.ones_like(a)
        else:
            if translucent_pass:
                inside &= (a > 0.004) & (a < 0.996)
            else:
                inside &= a >= 0.996
                a = np.ones_like(a)
        if not inside.any():
            return
        sl = (slice(ymin, ymax + 1), slice(xmin, xmax + 1))
        if translucent_pass:
            am = np.where(inside, a, 0.0)[..., None]
            self.color[sl] = rgb * am + self.color[sl] * (1 - am)
            self.alpha[sl] = am[..., 0] + self.alpha[sl] * (1 - am[..., 0])
            if emissive:
                self.glow[sl] = rgb * am + self.glow[sl] * (1 - am)
            else:
                self.glow[sl] = self.glow[sl] * (1 - am)
        else:
            m = inside
            self.color[sl][m] = rgb[m]
            self.alpha[sl][m] = 1.0
            self.depth[sl][m] = z[m]
            self.glow[sl][m] = rgb[m] if emissive else 0.0


BLOCK_GUI = {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}


def _display_transform(model: ResolvedModel, context: str):
    d = model.display.get(context)
    if d is None and context == 'gui' and model.name.split(':')[-1].startswith('block/'):
        d = BLOCK_GUI  # standalone block models (drawn by renderers) have no display: use the block view
    d = d or {}
    rot = d.get('rotation', [0, 0, 0])
    trans = np.array(d.get('translation', [0, 0, 0]), dtype=np.float64) / 16.0
    scale = np.array(d.get('scale', [1, 1, 1]), dtype=np.float64)
    return rot_xyz(*rot), np.clip(trans, -5, 5), np.clip(scale, -4, 4)


def render_scene(parts: list, size: int = 256, ss: int = 4, yaw: float | None = None, pitch: float | None = None,
                 scale: float | None = None, display: str = 'gui', fit: bool = False, margin: float = 0.06,
                 frame: int = 0, bloom: float = 0.0, background=None) -> Image.Image:
    """Renders models together. The camera comes from the first part's ``display.<display>`` transform
    unless yaw/pitch/scale are given (yaw/pitch in degrees, applied like a display rotation [pitch, yaw, 0])."""
    first = resolve_model(parts[0].model)
    d_rot, d_trans, d_scale = _display_transform(first, display)
    if yaw is not None or pitch is not None:
        cur = (first.display.get(display) or BLOCK_GUI).get('rotation', [30, 225, 0])
        d_rot = rot_xyz(pitch if pitch is not None else cur[0], yaw if yaw is not None else cur[1], 0)
        d_trans = np.zeros(3)
    if scale is not None:
        d_scale = np.array([scale] * 3)
    S = size * ss

    prepared = []  # (screen verts (4,3), quad, tex, colour, mode, view normal)
    for part in parts:
        model, quads = model_quads(part.model, frame)
        mode = part.render_type or default_render_type(model)
        tints = dict(part.tints or {})
        for pattern, preset in TINT_PRESETS:
            m = re.search(pattern, part.model)
            if m:
                for k, v in preset(m).items():
                    tints.setdefault(k, v)
                break
        shaded = part.shade if part.shade is not None else not re.search(UNSHADED_MODELS, part.model)
        M = part.matrix if part.matrix is not None else np.eye(3)
        pivot = np.array(part.pivot, dtype=np.float64)
        offset = np.array(part.translate, dtype=np.float64)
        for q in quads:
            verts = (q.verts - pivot) @ M.T + pivot + offset
            normal = M @ q.normal
            p = verts / 16.0 - 0.5
            p = (p * d_scale) @ d_rot.T + d_trans
            n_view = d_rot @ (normal * np.sign(d_scale))
            if n_view[2] <= 0:  # back-face culling: Minecraft culls quads facing away from the camera
                continue
            tex = load_texture(q.texture, frame)
            bright = 1.0 if (q.emissive or not q.shade or not shaded) else _shade(normal)
            if model.gui_light == 'front' and display == 'gui' and not q.emissive:
                bright = 0.55 + 0.45 * max(0.0, float(n_view[2]))
            colour = np.ones(3) * bright * part.brightness
            if q.tint in tints:
                colour = colour * _rgb(tints[q.tint])
            elif q.tint == 0 and not q.implicit_tint:
                colour = colour * _rgb(DEFAULT_TINT)
            prepared.append((p, q, tex, colour, mode))

    if not prepared:
        return Image.new('RGBA', (size, size), (0, 0, 0, 0))
    allp = np.concatenate([p for p, *_ in prepared])
    if fit:
        lo, hi = allp[:, :2].min(axis=0), allp[:, :2].max(axis=0)
        extent = max(hi[0] - lo[0], hi[1] - lo[1], 1e-6)
        k = (1 - 2 * margin) / extent
        centre = (lo + hi) / 2
    else:
        k, centre = 1.0, np.zeros(2)

    canvas = Canvas(S)
    screen = []
    for p, q, tex, colour, mode in prepared:
        sp = np.empty_like(p)
        sp[:, 0] = (0.5 + (p[:, 0] - centre[0]) * k) * S
        sp[:, 1] = (0.5 - (p[:, 1] - centre[1]) * k) * S
        sp[:, 2] = p[:, 2]
        th, tw = tex.shape[:2]
        uv = q.uvs.copy()
        uv[:, 0] *= tw / 16.0
        uv[:, 1] *= th / 16.0
        e = 1e-3
        clamp = ((uv[:, 0].min() + e, uv[:, 0].max() - e), (uv[:, 1].min() + e, uv[:, 1].max() - e))
        screen.append((sp, uv, tex, colour, mode, clamp, q.emissive))

    for sp, uv, tex, colour, mode, clamp, em in screen:
        for tri in ((0, 1, 2), (0, 2, 3)):
            canvas.triangle(sp[list(tri)], uv[list(tri)], tex, colour, mode, clamp, em, False)
    translucent = [s for s in screen if s[4] == 'translucent']
    translucent.sort(key=lambda s: s[0][:, 2].mean())
    for sp, uv, tex, colour, mode, clamp, em in translucent:
        for tri in ((0, 1, 2), (0, 2, 3)):
            canvas.triangle(sp[list(tri)], uv[list(tri)], tex, colour, mode, clamp, em, True)

    color = canvas.color.reshape(size, ss, size, ss, 3).mean(axis=(1, 3))
    alpha = canvas.alpha.reshape(size, ss, size, ss).mean(axis=(1, 3))
    if bloom > 0:
        glow = canvas.glow.reshape(size, ss, size, ss, 3).mean(axis=(1, 3))
        glow_img = Image.fromarray(np.clip(glow * 255, 0, 255).astype(np.uint8), 'RGB')
        radius = max(1.0, size * 0.025)
        blurred = np.zeros_like(glow)
        for r, w in ((radius, 0.6), (radius * 3, 0.4)):
            blurred += np.asarray(glow_img.filter(ImageFilter.GaussianBlur(r)), dtype=np.float32) / 255.0 * w
        add = blurred * bloom
        glow_alpha = np.clip(add.max(axis=2), 0, 1)
        color = color + add * (1 - alpha[..., None]) + add * alpha[..., None] * 0.6
        alpha = np.clip(alpha + glow_alpha * (1 - alpha), 0, 1)
    out = np.zeros((size, size, 4), dtype=np.float32)
    safe = np.where(alpha > 1e-6, alpha, 1.0)
    out[..., :3] = np.clip(color / safe[..., None], 0, 1)
    out[..., 3] = alpha
    img = Image.fromarray((out * 255 + 0.5).astype(np.uint8), 'RGBA')
    if background is not None:
        bg = Image.new('RGBA', img.size, background)
        bg.alpha_composite(img)
        img = bg
    return img


def render_model(ref: str, size: int = 256, ss: int = 4, yaw: float | None = None, pitch: float | None = None,
                 scale: float | None = None, tints: dict | None = None, display: str = 'gui', fit: bool = False,
                 frame: int = 0, bloom: float = 0.0, render_type: str | None = None, background=None) -> Image.Image:
    """Renders one model to a PIL RGBA image (transparent background unless ``background`` is given)."""
    return render_scene([Part(ref, tints=tints, render_type=render_type)], size=size, ss=ss, yaw=yaw, pitch=pitch,
                        scale=scale, display=display, fit=fit, frame=frame, bloom=bloom, background=background)


# ---------------------------------------------------------------------------------------------------
# Composite previews of block entity renderer output (keep in sync with DriveRenderer / CoreRenderer)
# ---------------------------------------------------------------------------------------------------

def drive_scene(active: bool = True, tiers=('4k', '16k', '64k', '256k', '1m', '16m', 'creative', '64k', None, '16k'),
                leds=(0x5BE08A, 0x5BE08A, 0xF2C14E, 0x5BE08A, 0xFF4A4A, 0x5BE08A, 0x5BE08A, 0xF2C14E, None, 0x5BE08A)):
    parts = [Part('block/drive_active' if active else 'block/drive')]
    for slot, tier in enumerate(tiers):
        if tier is None:
            continue
        x = 8.5 if slot % 2 == 0 else 2.0
        y = 11.0 - 2.5 * (slot // 2)
        parts.append(Part('block/drive_cell', translate=(x, y, 0), tints={0: TIER_COLOURS[tier]}, render_type='cutout'))
        parts.append(Part('block/drive_cell_led', translate=(x, y, 0), tints={0: leds[slot] if active else 0x30343A},
                          render_type='cutout'))
    return parts


def core_scene(active: bool = True, time: float = 40.0):
    tint = 0xFFFFFF if active else 0x737373
    crystal = rot_axis('y', time * 2.0)
    ring = rot_axis('y', -time * 3.0) @ rot_axis('x', 18.0)
    return [Part('block/nexus_core_active' if active else 'block/nexus_core'),
            Part('block/nexus_core_crystal', matrix=crystal, tints={0: tint}, render_type='translucent'),
            Part('block/nexus_core_ring', matrix=ring, tints={0: tint}, render_type='translucent')]


def blockstate_rotation(x: int = 0, y: int = 0):
    """3x3 matrix for a blockstate variant rotation (x/y in degrees, clockwise like the game)."""
    return rot_axis('y', -y) @ rot_axis('x', -x)


def place(parts, offset):
    """Moves scene parts by a block offset (in blocks)."""
    for part in parts:
        part.translate = tuple(np.array(part.translate, dtype=float) + np.array(offset, dtype=float) * 16)
    return parts


def cable_parts(*directions):
    rot = {'north': (0, 0), 'east': (0, 90), 'south': (0, 180), 'west': (0, 270), 'up': (270, 0), 'down': (90, 0)}
    parts = [Part('block/nexus_cable_core')]
    for d in directions:
        parts.append(Part('block/nexus_cable_arm', matrix=blockstate_rotation(*rot[d])))
    return parts


def showcase_scene():
    """A small network: core, cable run, a loaded drive and both terminals (an example for marketing)."""
    parts = []
    parts += place(core_scene(True), (0, 0, 0))
    parts += place(cable_parts('west', 'east', 'south'), (1, 0, 0))
    parts += place(drive_scene(True), (2, 0, 0))
    parts += place([Part('block/terminal_active')], (3, 0, 0))
    parts += place(cable_parts('north', 'up'), (1, 0, 1))
    parts += place([Part('block/crafting_terminal_active')], (1, 1, 1))
    parts += place([Part('block/wireless_transmitter_active')], (0, 1, 0))
    return parts


SCENES = {
    'drive_loaded': lambda: drive_scene(True),
    'drive_offline': lambda: drive_scene(False),
    'nexus_core_running': lambda: core_scene(True),
    'nexus_core_idle': lambda: core_scene(False),
    'showcase': showcase_scene,
}


# ---------------------------------------------------------------------------------------------------
# Contact sheet
# ---------------------------------------------------------------------------------------------------

def _font(size: int):
    for name in ('DejaVuSans.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'Arial.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    try:
        return ImageFont.load_default(size)
    except TypeError:
        return ImageFont.load_default()


def all_models(kind: str):
    base = ASSETS / MOD / 'models' / kind
    return sorted(f'{kind}/{p.relative_to(base).with_suffix("").as_posix()}' for p in base.rglob('*.json'))


def contact_sheet(cell: int = 176, columns: int = 8, ss: int = 3, bloom: float = 0.0, fit: bool = True,
                  include_scenes: bool = True) -> Image.Image:
    sections = []
    if include_scenes:
        sections.append(('Block entity scenes', [(f'scene:{k}', k) for k in SCENES]))
    sections.append(('Block models', [(m, m) for m in all_models('block')]))
    sections.append(('Item models', [(m, m) for m in all_models('item')]))
    label_h = 30
    header_h = 34
    font = _font(12)
    small = _font(10)
    head_font = _font(18)
    rows_total = sum(math.ceil(len(items) / columns) for _, items in sections)
    width = columns * cell
    height = rows_total * (cell + label_h) + len(sections) * header_h + 12
    sheet = Image.new('RGBA', (width, height), (20, 23, 28, 255))
    draw = ImageDraw.Draw(sheet)
    y = 6
    for title, items in sections:
        draw.text((10, y + 6), title, fill=(200, 210, 222, 255), font=head_font)
        y += header_h
        for i, (ref, label) in enumerate(items):
            col, row = i % columns, i // columns
            x0, y0 = col * cell, y + row * (cell + label_h)
            shade = (27, 31, 38, 255) if (col + row) % 2 == 0 else (24, 27, 33, 255)
            draw.rectangle([x0, y0, x0 + cell - 1, y0 + cell + label_h - 1], fill=shade)
            try:
                if ref.startswith('scene:'):
                    img = render_scene(SCENES[ref[6:]](), size=cell, ss=ss, fit=fit, bloom=bloom)
                    count = None
                else:
                    img = render_model(ref, size=cell, ss=ss, fit=fit, bloom=bloom)
                    count = count_elements(ref)
                sheet.alpha_composite(img, (x0, y0))
            except Exception as exc:  # keep going; show the error on the sheet
                draw.text((x0 + 6, y0 + cell // 2), f'error: {exc}'[:40], fill=(255, 110, 110, 255), font=small)
                count = None
            name = label.split('/', 1)[-1]
            draw.text((x0 + 6, y0 + cell + 2), name[:26], fill=(225, 230, 238, 255), font=font)
            sub = label.split('/', 1)[0] if '/' in label else 'scene'
            if count is not None:
                sub += f' · {count} elements' if count else ' · generated'
            draw.text((x0 + 6, y0 + cell + 16), sub, fill=(130, 140, 155, 255), font=small)
        y += math.ceil(len(items) / columns) * (cell + label_h)
    return sheet


# ---------------------------------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------------------------------

def _parse_tints(values):
    tints = {}
    for value in values or []:
        index, colour = value.split('=')
        tints[int(index)] = int(colour.lstrip('#'), 16)
    return tints


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('model', nargs='?', help='model to render, e.g. block/drive, item/omni_tool, or scene:drive_loaded')
    parser.add_argument('--out', help='output PNG (default build/previews/<model>.png)')
    parser.add_argument('--size', type=int, default=256)
    parser.add_argument('--ss', type=int, default=4, help='supersampling factor')
    parser.add_argument('--yaw', type=float)
    parser.add_argument('--pitch', type=float)
    parser.add_argument('--scale', type=float)
    parser.add_argument('--display', default='gui', help='display context for the camera (gui, fixed, ground...)')
    parser.add_argument('--fit', action='store_true', help='frame the model to fill the image')
    parser.add_argument('--frame', type=int, default=0, help='animation frame index')
    parser.add_argument('--bloom', type=float, default=0.0, help='glow halo around emissive faces (0 = off)')
    parser.add_argument('--tint', action='append', help='tint override INDEX=RRGGBB (repeatable)')
    parser.add_argument('--sheet', action='store_true', help='render a labelled contact sheet of every model')
    parser.add_argument('--cell', type=int, default=176, help='contact sheet cell size')
    parser.add_argument('--columns', type=int, default=8)
    args = parser.parse_args(argv)

    if args.sheet:
        out = Path(args.out or ROOT / 'build' / 'previews' / 'sheet.png')
        out.parent.mkdir(parents=True, exist_ok=True)
        contact_sheet(cell=args.cell, columns=args.columns, ss=min(args.ss, 3), bloom=args.bloom).save(out)
        print(f'Wrote {out}')
        return 0
    if not args.model:
        parser.print_help()
        return 0
    out = Path(args.out or ROOT / 'build' / 'previews' / (args.model.replace(':', '_').replace('/', '_') + '.png'))
    out.parent.mkdir(parents=True, exist_ok=True)
    if args.model.startswith('scene:'):
        img = render_scene(SCENES[args.model[6:]](), size=args.size, ss=args.ss, yaw=args.yaw, pitch=args.pitch,
                           scale=args.scale, display=args.display, fit=args.fit, frame=args.frame, bloom=args.bloom)
    else:
        img = render_model(args.model, size=args.size, ss=args.ss, yaw=args.yaw, pitch=args.pitch, scale=args.scale,
                           tints=_parse_tints(args.tint), display=args.display, fit=args.fit, frame=args.frame,
                           bloom=args.bloom)
    img.save(out)
    print(f'Wrote {out}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
