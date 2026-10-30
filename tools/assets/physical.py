#!/usr/bin/env python3
"""Art and data for physical storage: tiered vaults + kits, the Linked Vault and Pouch, the Trash Can
and the Relocator. Writes textures, block/item models, blockstates, loot tables, tags and recipes.

Models face north. Vault lids are separate standalone models hinged at y=10px, z=15px (see
physical/client/LidRendering.java); item models combine body and closed lid.

Usage (from the repository root): python3 tools/assets/physical.py
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kit  # noqa: E402
from kit import (AMBER, BLACK, CLEAR, CYAN, DIAMOND, GUNMETAL, NETHERITE, STEEL, TIERS, VIOLET,  # noqa: E402
                 Model, Tex, mix, rgb, shade)

TIER_ORDER = ['copper', 'iron', 'gold', 'diamond', 'netherite', 'omnium']
# Accent glow per tier: cyan energy for most metals (on omnium's violet it is the mod's signature
# pairing), molten amber for netherite.
TIER_GLOW = {'copper': 'cyan', 'iron': 'cyan', 'gold': 'cyan', 'diamond': 'cyan', 'netherite': 'amber', 'omnium': 'cyan'}
GLOWS = {'cyan': CYAN, 'violet': VIOLET, 'amber': AMBER, 'teal': DIAMOND}
TEAL = [rgb('06302c'), rgb('0b5a52'), rgb('15917f'), rgb('2fd3b5'), rgb('8bf5df'), rgb('e3fff8')]


def seed_of(name: str) -> int:
    return sum((i + 1) * ord(c) for i, c in enumerate(name))


def ramp_at(ramp, index: int):
    return ramp[max(0, min(len(ramp) - 1, index))]


# =============================================================================================
# Texture helpers (local to this feature; kit.py stays untouched)
# =============================================================================================


def hammered(tex: Tex, ramp, x0=0, y0=0, x1=15, y1=15, base=2, speckle=0.18):
    """Hand-worked metal: soft diagonal light falloff, tonal noise and a few bright glints."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = ((x - x0) + (y - y0)) / max(1, (x1 - x0) + (y1 - y0))
            c = mix(ramp_at(ramp, base + 1), ramp_at(ramp, base), min(1.0, t * 1.4))
            r = tex.rng.random()
            if r < speckle:
                c = mix(c, ramp_at(ramp, base - 1), 0.55)
            elif r < speckle * 1.6:
                c = mix(c, ramp_at(ramp, base + 2), 0.35)
            tex.set(x, y, c)
    for _ in range(max(1, (x1 - x0 + 1) * (y1 - y0 + 1) // 40)):
        tex.set(tex.rng.randint(x0, x1), tex.rng.randint(y0, y1), ramp_at(ramp, base + 3))
    return tex


def brushed_h(tex: Tex, ramp, x0=0, y0=0, x1=15, y1=15, base=2):
    """Horizontal brushed metal with streak variation and a top-down sheen."""
    for y in range(y0, y1 + 1):
        row = tex.rng.choice([0, 0, 0, 1, -1])
        sheen = 1.0 - 0.18 * ((y - y0) / max(1, y1 - y0))
        for x in range(x0, x1 + 1):
            c = ramp_at(ramp, base + row)
            if tex.rng.random() < 0.12:
                c = mix(c, ramp_at(ramp, base + row + 1), 0.5)
            tex.set(x, y, shade(c, sheen))
    return tex


def rivet(tex: Tex, x, y, ramp):
    """A 2x2 dome rivet: bright cap, shaded rim."""
    tex.set(x, y, ramp_at(ramp, 5))
    tex.set(x + 1, y, ramp_at(ramp, 3))
    tex.set(x, y + 1, ramp_at(ramp, 3))
    tex.set(x + 1, y + 1, ramp_at(ramp, 1))


def pin(tex: Tex, x, y, ramp):
    """A 1px rivet with a drop shadow."""
    tex.set(x, y, ramp_at(ramp, 4))
    tex.set(x + 1, y + 1, ramp_at(ramp, 0))


def inset_frame(tex: Tex, x0, y0, x1, y1, ramp, base=2):
    """A recessed rectangle edge: shadow on top/left, catch-light on bottom/right."""
    tex.hline(x0, x1, y0, ramp_at(ramp, base - 2))
    tex.vline(x0, y0, y1, ramp_at(ramp, base - 2))
    tex.hline(x0, x1, y1, ramp_at(ramp, base + 2))
    tex.vline(x1, y0, y1, ramp_at(ramp, base + 2))


def raised_frame(tex: Tex, x0, y0, x1, y1, ramp, base=2):
    tex.hline(x0, x1, y0, ramp_at(ramp, base + 2))
    tex.vline(x0, y0, y1, ramp_at(ramp, base + 2))
    tex.hline(x0, x1, y1, ramp_at(ramp, base - 2))
    tex.vline(x1, y0, y1, ramp_at(ramp, base - 2))


def tread_plate(tex: Tex, ramp, x0, y0, x1, y1, base=2):
    """Diamond plate: alternating diagonal bumps, each lit on its upper-left and shaded below."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            tex.set(x, y, mix(ramp_at(ramp, base), ramp_at(ramp, base + 1), 0.15 * ((x - x0 + y1 - y) / max(1, x1 - x0 + y1 - y0))))
    for row, y in enumerate(range(y0 + 1, y1 - 1, 2)):
        for col, x in enumerate(range(x0 + 1 + (row % 2) * 2, x1 - 1, 4)):
            hi = mix(ramp_at(ramp, base + 1), ramp_at(ramp, base + 2), 0.5)
            lo = mix(ramp_at(ramp, base), ramp_at(ramp, base - 1), 0.7)
            if (row + col) % 2 == 0:  # "/" bump
                tex.set(x + 1, y, hi)
                tex.set(x, y + 1, ramp_at(ramp, base + 1))
                tex.set(x + 1, y + 1, lo)
            else:  # "\" bump
                tex.set(x, y, hi)
                tex.set(x + 1, y + 1, ramp_at(ramp, base + 1))
                tex.set(x, y + 1, lo)


def glow_tex(name: str, ramp, seed: int):
    """Energy strip: bright core, hot sparkles, darker rim rows."""
    t = Tex(seed=seed)
    for y in range(16):
        for x in range(16):
            band = abs(((y % 4) + 0.5) - 2) / 2  # 0 in the middle of each 4px band
            c = mix(ramp[4], ramp[3], band)
            if t.rng.random() < 0.10:
                c = ramp[5]
            elif t.rng.random() < 0.08:
                c = ramp[2]
            t.set(x, y, c)
    t.save(f'block/physical/glow_{name}')


def emblem_tex(name: str, ramp, seed: int):
    """A cut gem seen from above: radial core with facet highlights."""
    t = Tex(seed=seed)
    t.fill(ramp[1])
    t.radial(8, 8, 9.0, ramp[4], ramp[1])
    for i in range(16):
        t.set(i, i, mix(t.get(i, i), ramp[4], 0.3))
        t.set(15 - i, i, mix(t.get(15 - i, i), ramp[1], 0.35))
    t.rect(7, 7, 8, 8, ramp[3])
    t.set(7, 7, ramp[5])
    t.set(6, 7, ramp[4])
    t.set(7, 6, ramp[4])
    t.save(f'block/physical/emblem_{name}')


# =============================================================================================
# Shared textures
# =============================================================================================


def shared_textures():
    # Casing: fine gunmetal grain for plinths, lock plates and badge backs.
    t = Tex(seed=11)
    hammered(t, GUNMETAL, base=2, speckle=0.22)
    t.save('block/physical/casing')

    # Hardware: brushed steel for handles and hinge barrels.
    t = Tex(seed=12)
    brushed_h(t, STEEL, base=2)
    for x in range(0, 16, 4):
        t.vline(x, 0, 15, mix(t.get(x, 0), STEEL[0], 0.35))
    t.save('block/physical/hardware')

    # Interior: the storage bay seen when the lid is open (and the underside of the lid).
    t = Tex(seed=13)
    t.fill(GUNMETAL[0])
    t.noise(GUNMETAL, base=1, spread=1, chance=0.25)
    for v in range(2, 15, 4):
        t.hline(2, 13, v, mix(GUNMETAL[1], GUNMETAL[2], 0.6))
        t.vline(v, 2, 13, mix(GUNMETAL[1], GUNMETAL[2], 0.6))
    raised_frame(t, 2, 2, 13, 13, GUNMETAL, base=2)
    inset_frame(t, 3, 3, 12, 12, GUNMETAL, base=1)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        t.set(x, y, CYAN[2])
    t.outline(0, 0, 15, 15, GUNMETAL[3])
    t.save('block/physical/interior')

    # Lock plate: dark steel; the 3x3 face at u 6..8, v 8..10 carries a keyhole.
    t = Tex(seed=14)
    hammered(t, GUNMETAL, base=3, speckle=0.2)
    t.hline(6, 8, 8, GUNMETAL[5])
    t.set(6, 9, GUNMETAL[4])
    t.set(7, 9, BLACK)
    t.set(8, 9, GUNMETAL[2])
    t.set(6, 10, GUNMETAL[3])
    t.set(7, 10, GUNMETAL[0])
    t.set(8, 10, GUNMETAL[1])
    t.save('block/physical/lock')

    for name, ramp in GLOWS.items():
        glow_tex(name, ramp, seed_of(name))
        emblem_tex(name, ramp, seed_of(name) + 1)


# =============================================================================================
# Tiered vault textures
# =============================================================================================


def vault_textures(tier: str):
    ramp = TIERS[tier]
    seed = seed_of(tier)
    casing = NETHERITE if tier == 'netherite' else GUNMETAL

    # Trim: the tier's metal for posts, bands, caps, rails and the latch.
    t = Tex(seed=seed)
    hammered(t, ramp, base=2, speckle=0.12)
    if tier == 'copper':
        for _ in range(6):  # a little verdigris in the seams
            t.set(t.rng.randint(0, 15), t.rng.randint(0, 15), mix(DIAMOND[1], ramp[1], 0.45))
    elif tier == 'omnium':
        for _ in range(4):
            t.set(t.rng.randint(0, 15), t.rng.randint(0, 15), CYAN[4])
    elif tier == 'netherite':
        for _ in range(4):
            t.set(t.rng.randint(0, 15), t.rng.randint(0, 15), mix(AMBER[2], ramp[3], 0.5))
    t.save(f'block/physical/vault/{tier}_trim')

    def recess(t: Tex):
        """Shadowed casing seen in the gaps around raised panels, plus the lid strip (v 3..4)."""
        t.noise(casing, base=1, spread=1, chance=0.25)
        brushed_h(t, casing, 0, 3, 15, 4, base=2)
        t.hline(0, 15, 4, mix(casing[2], ramp[2], 0.6))
        for x in range(1, 16, 4):
            t.set(x, 3, mix(casing[3], ramp[3], 0.3))

    def panel_frame(t: Tex):
        """Tier-metal frame of a raised 8x5 panel at u 4..11, v 8..12, with corner rivets."""
        t.hline(4, 11, 8, ramp[4])
        t.vline(4, 8, 12, ramp[3])
        t.vline(11, 8, 12, ramp[1])
        t.hline(4, 11, 12, ramp[1])
        t.set(11, 12, ramp[0])
        t.set(4, 8, ramp[5])
        t.set(11, 8, ramp[3])
        t.set(4, 12, ramp[2])

    # Front: the vault door (raised panel) with a dark field and a tier slat behind the lock.
    t = Tex(seed=seed + 1)
    recess(t)
    t.rect(5, 9, 10, 11, casing[1])
    t.hline(5, 10, 9, casing[0])
    t.hline(5, 10, 10, mix(ramp[2], casing[2], 0.45))
    t.hline(5, 10, 11, mix(casing[2], ramp[1], 0.3))
    t.set(5, 10, ramp[3])
    t.set(10, 10, ramp[1])
    panel_frame(t)
    t.save(f'block/physical/vault/{tier}_front')

    # Side/back: a raised panel with two vent slits (the handle crosses it).
    t = Tex(seed=seed + 2)
    recess(t)
    t.rect(5, 9, 10, 11, casing[2])
    t.hline(5, 10, 9, BLACK)
    t.hline(5, 10, 11, BLACK)
    t.hline(6, 9, 10, mix(casing[3], ramp[2], 0.35))
    panel_frame(t)
    t.save(f'block/physical/vault/{tier}_side')

    # Lid top: dark gap ring (2 and 13) around a tier diamond plate (3..12).
    t = Tex(seed=seed + 3)
    t.fill(casing[1])
    tread_plate(t, ramp, 3, 3, 12, 12, base=2)
    raised_frame(t, 3, 3, 12, 12, ramp, base=2)
    t.outline(2, 2, 13, 13, mix(casing[0], casing[1], 0.5))
    t.save(f'block/physical/vault/{tier}_lid')


# =============================================================================================
# Vault models
# =============================================================================================


def vault_body(m: Model, tier: str, glow: str):
    """The static part: plinth, recessed core, corner posts, bands, raised panels, lock, handles, hinges."""
    m.box((1.5, 0, 1.5), (14.5, 1, 14.5), 'casing', name='plinth')
    m.box((2, 1, 2), (14, 10, 14), faces={'north': 'front', 'south': 'side', 'west': 'side', 'east': 'side',
                                          'up': 'interior', 'down': 'casing'}, name='core')
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box((x0, 0.5, z0), (x0 + 2, 10, z0 + 2), 'trim', name='post')
    for y0, y1 in ((1, 2), (8.5, 10)):
        m.box((3, y0, 1), (13, y1, 2), 'trim', skip=('south',), name='band_front')
        m.box((3, y0, 14), (13, y1, 15), 'trim', skip=('north',), name='band_back')
        m.box((1, y0, 3), (2, y1, 13), 'trim', skip=('east',), name='band_west')
        m.box((14, y0, 3), (15, y1, 13), 'trim', skip=('west',), name='band_east')
    # Raised panels: the door on the front, plain panels on the other three sides.
    m.box((4, 3, 1.5), (12, 8, 2), faces={'north': 'front', 'west': 'trim', 'east': 'trim', 'up': 'trim', 'down': 'trim'}, name='door')
    m.box((4, 3, 14), (12, 8, 14.5), faces={'south': 'side', 'west': 'trim', 'east': 'trim', 'up': 'trim', 'down': 'trim'}, name='panel_back')
    m.box((1.5, 3, 4), (2, 8, 12), faces={'west': 'side', 'north': 'trim', 'south': 'trim', 'up': 'trim', 'down': 'trim'}, name='panel_west')
    m.box((14, 3, 4), (14.5, 8, 12), faces={'east': 'side', 'north': 'trim', 'south': 'trim', 'up': 'trim', 'down': 'trim'}, name='panel_east')
    m.box((6.5, 4, 1), (9.5, 7, 1.5), faces={'north': ('lock', [6, 8, 9, 11]), 'west': 'trim', 'east': 'trim', 'up': 'trim', 'down': 'trim'},
          name='lock_plate')
    m.box((4, 2.25, 1.75), (12, 2.75, 2), 'glow', emissive=True, skip=('south',), name='glow_strip')
    # Side handles: a bar on two mounts.
    for bar, mount in (((0.5, 1), (0.5, 2)), ((15, 15.5), (14, 15.5))):
        m.box((bar[0], 5.5, 6), (bar[1], 6.25, 10), 'hardware', name='handle')
        m.box((mount[0], 5, 5), (mount[1], 6.75, 6), 'hardware', name='handle_mount')
        m.box((mount[0], 5, 10), (mount[1], 6.75, 11), 'hardware', name='handle_mount')
    for x0 in (3.5, 10.5):
        m.box((x0, 9, 14.5), (x0 + 2, 11.25, 15.75), 'hardware', name='hinge')  # tops clear the lid band (no z-fight)
    if tier in ('netherite', 'omnium'):
        # Glowing seams down the front posts.
        for x in (1.75, 13.75):
            m.box((x, 1.5, 0.9), (x + 0.5, 9.5, 1), 'glow', emissive=True, skip=('south',), name='seam')


def vault_lid(m: Model, tier: str):
    """The hinged part. Everything here swings with the lid."""
    m.box((2, 10, 2), (14, 13, 14), faces={'north': 'front', 'south': 'side', 'west': 'side', 'east': 'side',
                                           'up': 'lid', 'down': 'interior'}, name='lid_core')
    m.box((3, 13, 3), (13, 13.5, 13), faces={'up': 'lid', 'north': 'trim', 'south': 'trim', 'west': 'trim', 'east': 'trim'},
          name='lid_plate')
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box((x0, 10, z0), (x0 + 2, 13.75, z0 + 2), 'trim', name='lid_cap')
    m.box((3, 10, 1), (13, 11, 2), 'trim', skip=('south',), name='lid_band_front')
    m.box((3, 10, 14), (13, 11, 15), 'trim', skip=('north',), name='lid_band_back')
    m.box((1, 10, 3), (2, 11, 13), 'trim', skip=('east',), name='lid_band_west')
    m.box((14, 10, 3), (15, 11, 13), 'trim', skip=('west',), name='lid_band_east')
    # Rails along the plate's left and right edges.
    for x0 in (3, 12):
        m.box((x0, 13.5, 3), (x0 + 1, 13.75, 13), 'trim', skip=('down',), name='rail')
    # Diamond emblem: a gunmetal plate and a glowing gem, both turned 45 degrees.
    turn = (45, 'y', (8, 13.5, 8))
    m.box((6, 13.5, 6), (10, 13.9, 10), 'casing', rotation=turn, skip=('down',), name='emblem_plate')
    m.box((6.75, 13.9, 6.75), (9.25, 14.15, 9.25), 'emblem', rotation=turn, emissive=True, skip=('down',), name='emblem_gem')
    # Front latch with a glowing key light.
    m.box((7, 6.5, 0.5), (9, 11.5, 1.25), 'trim', name='latch')
    m.box((7.5, 7.25, 0.4), (8.5, 8.75, 0.5), 'glow', emissive=True, skip=('south',), name='latch_light')
    if tier in ('diamond', 'netherite', 'omnium'):
        # Gem studs on the lid corners.
        for x0, z0 in ((1.5, 1.5), (13.5, 1.5), (1.5, 13.5), (13.5, 13.5)):
            m.box((x0, 13.75, z0), (x0 + 1, 14.25, z0 + 1), 'emblem', emissive=tier != 'diamond', skip=('down',), name='stud')


def vault_textures_map(tier: str, glow: str):
    return {
        'particle': f'block/physical/vault/{tier}_trim',
        'trim': f'block/physical/vault/{tier}_trim',
        'front': f'block/physical/vault/{tier}_front',
        'side': f'block/physical/vault/{tier}_side',
        'lid': f'block/physical/vault/{tier}_lid',
        'interior': 'block/physical/interior',
        'casing': 'block/physical/casing',
        'hardware': 'block/physical/hardware',
        'lock': 'block/physical/lock',
        'glow': f'block/physical/glow_{glow}',
        'emblem': f'block/physical/emblem_{glow}',
    }


def vault_models(tier: str):
    glow = TIER_GLOW[tier]
    vault_textures(tier)
    name = f'{tier}_vault'
    textures = vault_textures_map(tier, glow)

    body = Model(textures=textures, render_type='minecraft:cutout')
    vault_body(body, tier, glow)
    body.save(f'block/{name}')

    lid = Model(parent=None, textures=textures, render_type='minecraft:cutout')
    vault_lid(lid, tier)
    lid.save(f'block/{name}_lid')

    item = Model(textures=textures, render_type='minecraft:cutout')
    vault_body(item, tier, glow)
    vault_lid(item, tier)
    item.block_display()
    item.save(f'item/{name}')

    kit.blockstate_horizontal(name)
    kit.loot_self(name)


# =============================================================================================
# Linked Vault
# =============================================================================================


def linked_textures():
    seed = 701
    obsidian = [rgb('07060b'), rgb('100c18'), rgb('1a1426'), rgb('271e38'), rgb('3a2c52'), rgb('564277')]

    t = Tex(seed=seed)
    hammered(t, obsidian, base=2, speckle=0.3)
    for _ in range(10):
        t.set(t.rng.randint(0, 15), t.rng.randint(0, 15), VIOLET[2])
    t.save('block/physical/linked/casing')

    # Trim: dark steel with violet sheen.
    t = Tex(seed=seed + 1)
    hammered(t, [mix(a, b, 0.25) for a, b in zip(GUNMETAL, VIOLET)], base=3, speckle=0.2)
    t.save('block/physical/linked/trim')

    def obsidian_panel(t: Tex, front: bool):
        hammered(t, obsidian, base=2, speckle=0.3)
        t.rect(0, 3, 15, 5, obsidian[1])
        t.hline(0, 15, 4, VIOLET[1])
        t.rect(3, 8, 12, 13, obsidian[1])
        inset_frame(t, 3, 8, 12, 13, obsidian, base=2)
        if not front:
            # Runic channels in the side panel.
            for x in (5, 8, 11):
                t.vline(x, 9, 12, VIOLET[1])
                t.set(x, 9 + (x % 3), VIOLET[3])
        for _ in range(6):
            t.set(t.rng.randint(4, 11), t.rng.randint(9, 12), mix(obsidian[3], VIOLET[2], 0.5))

    t = Tex(seed=seed + 2)
    obsidian_panel(t, True)
    t.save('block/physical/linked/front')
    t = Tex(seed=seed + 3)
    obsidian_panel(t, False)
    t.save('block/physical/linked/side')

    # Lid top: obsidian with an inlaid violet circuit ring around the latch bay.
    t = Tex(seed=seed + 4)
    t.fill(obsidian[0])
    hammered(t, obsidian, 3, 3, 12, 12, base=2, speckle=0.3)
    raised_frame(t, 3, 3, 12, 12, obsidian, base=3)
    t.outline(5, 9, 10, 11, VIOLET[2])
    t.hline(6, 9, 10, VIOLET[1])
    t.vline(4, 5, 10, VIOLET[1])
    t.vline(11, 5, 10, VIOLET[1])
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        pin(t, x, y, STEEL)
    t.save('block/physical/linked/lid')

    # Latch button: near-white with bevel shading so the dye tint reads clearly.
    t = Tex(seed=seed + 5)
    t.fill(rgb('d8dde4'))
    t.noise([rgb('b9c0ca'), rgb('cfd5dd'), rgb('e3e7ec'), rgb('f2f4f7'), rgb('ffffff'), rgb('ffffff')], base=2, chance=0.25)
    for i in range(16):
        t.set(i, 0, rgb('ffffff'))
        t.set(0, i, rgb('ffffff'))
        t.set(i, 15, rgb('8e97a4'))
        t.set(15, i, rgb('8e97a4'))
    t.save('block/physical/linked/latch')

    # Padlock plate for private mode.
    t = Tex(seed=seed + 6)
    hammered(t, AMBER, base=2, speckle=0.2)
    t.rect(6, 10, 9, 12, AMBER[4])
    t.outline(6, 10, 9, 12, AMBER[1])
    t.set(7, 11, BLACK)
    t.set(8, 11, BLACK)
    t.save('block/physical/linked/padlock')


def linked_body(m: Model):
    m.box((1.5, 0, 1.5), (14.5, 1, 14.5), 'casing', name='plinth')
    m.box((2, 1, 2), (14, 10, 14), faces={'north': 'front', 'south': 'side', 'west': 'side', 'east': 'side',
                                          'up': 'interior', 'down': 'casing'}, name='core')
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box((x0, 0.5, z0), (x0 + 2, 10, z0 + 2), 'trim', name='post')
    for x in (1.75, 13.75):
        m.box((x, 1.5, 0.9), (x + 0.5, 9.5, 1), 'glow', emissive=True, skip=('south',), name='seam')
    for z in (1.75, 13.75):
        m.box((0.9, 1.5, z), (1, 9.5, z + 0.5), 'glow', emissive=True, skip=('east',), name='seam_side')
        m.box((15, 1.5, z), (15.1, 9.5, z + 0.5), 'glow', emissive=True, skip=('west',), name='seam_side')
    for y0, y1 in ((1, 2), (8.5, 10)):
        m.box((3, y0, 1), (13, y1, 2), 'trim', skip=('south',), name='band_front')
        m.box((3, y0, 14), (13, y1, 15), 'trim', skip=('north',), name='band_back')
        m.box((1, y0, 3), (2, y1, 13), 'trim', skip=('east',), name='band_west')
        m.box((14, y0, 3), (15, y1, 13), 'trim', skip=('west',), name='band_east')
    # The "eye": a dark iris plate, a ring of trim and a glowing teal pupil turned 45 degrees.
    m.box((5.5, 3, 1.5), (10.5, 8, 2), 'casing', skip=('south',), name='eye_plate')
    m.box((6.5, 4, 1.25), (9.5, 7, 1.5), 'trim', skip=('south',), name='eye_ring')
    m.box((7.1, 4.6, 1.0), (8.9, 6.4, 1.25), 'eye', emissive=True, rotation=(45, 'z', (8, 5.5, 1.1)), skip=('south',), name='eye')
    for x0 in (3.5, 10.5):
        m.box((x0, 9, 14.5), (x0 + 2, 11.25, 15.75), 'hardware', name='hinge')  # tops clear the lid band (no z-fight)


def linked_lid(m: Model, private: bool):
    m.box((2, 10, 2), (14, 13, 14), faces={'north': 'front', 'south': 'side', 'west': 'side', 'east': 'side',
                                           'up': 'lid', 'down': 'interior'}, name='lid_core')
    m.box((3, 13, 3), (13, 14, 13), faces={'up': 'lid', 'north': 'trim', 'south': 'trim', 'west': 'trim', 'east': 'trim'},
          name='lid_plate')
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box((x0, 10, z0), (x0 + 2, 14, z0 + 2), 'trim', name='lid_cap')
        m.box((x0 + 0.5, 14, z0 + 0.5), (x0 + 1.5, 14.25, z0 + 1.5), 'glow', emissive=True, skip=('down',), name='cap_light')
    m.box((3, 10, 1), (13, 11, 2), 'trim', skip=('south',), name='lid_band_front')
    m.box((3, 10, 14), (13, 11, 15), 'trim', skip=('north',), name='lid_band_back')
    m.box((1, 10, 3), (2, 11, 13), 'trim', skip=('east',), name='lid_band_west')
    m.box((14, 10, 3), (15, 11, 13), 'trim', skip=('west',), name='lid_band_east')
    # Latch bay rail behind the three latches.
    m.box((3.5, 14, 7), (12.5, 14.5, 8), 'trim', skip=('down',), name='latch_rail')
    # Mode lens on the lid front: teal when public, amber padlock when private.
    m.box((6, 10.5, 1.25), (10, 13, 2), 'padlock' if private else 'casing', skip=('south',), name='badge')
    m.box((7, 11, 1.0), (9, 12.5, 1.25), 'lens', emissive=True, skip=('south',), name='lens')


def linked_latch(m: Model, center_x: float, tint: int):
    half = 1
    m.box((center_x - half - 0.5, 14, 3.5), (center_x + half + 0.5, 14.25, 7.5), 'trim', skip=('down',), name='latch_base')
    m.box((center_x - half, 14.25, 4), (center_x + half, 15, 7), 'latch', tint=tint, skip=('down',), name='latch')


def linked_models():
    linked_textures()
    textures = {
        'particle': 'block/physical/linked/casing',
        'casing': 'block/physical/linked/casing',
        'trim': 'block/physical/linked/trim',
        'front': 'block/physical/linked/front',
        'side': 'block/physical/linked/side',
        'lid': 'block/physical/linked/lid',
        'interior': 'block/physical/interior',
        'hardware': 'block/physical/hardware',
        'glow': 'block/physical/glow_violet',
        'eye': 'block/physical/emblem_teal',
        'latch': 'block/physical/linked/latch',
        'padlock': 'block/physical/linked/padlock',
        'lens': 'block/physical/emblem_teal',
    }
    body = Model(textures=textures, render_type='minecraft:cutout')
    linked_body(body)
    body.save('block/linked_vault')

    lid = Model(parent=None, textures=textures, render_type='minecraft:cutout')
    linked_lid(lid, private=False)
    lid.save('block/linked_vault_lid')

    private_textures = dict(textures, lens='block/physical/emblem_amber')
    lid = Model(parent=None, textures=private_textures, render_type='minecraft:cutout')
    linked_lid(lid, private=True)
    lid.save('block/linked_vault_lid_private')

    latch = Model(parent=None, textures=textures, render_type='minecraft:cutout')
    linked_latch(latch, 8, 0)
    latch.save('block/linked_vault_latch')

    item = Model(textures=textures, render_type='minecraft:cutout')
    linked_body(item)
    linked_lid(item, private=False)
    for i in range(3):
        linked_latch(item, 11 - 3 * i, i + 1)
    item.block_display()
    item.save('item/linked_vault')

    kit.blockstate_horizontal('linked_vault')
    kit.write_json(kit.DATA / 'loot_table' / 'blocks' / 'linked_vault.json', {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1, 'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': 'omnivault:linked_vault', 'functions': [
                {'function': 'minecraft:copy_components', 'source': 'block_entity',
                 'include': ['minecraft:custom_name', 'omnivault:linked_frequency']}]}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
        }],
    })


# =============================================================================================
# Trash Can
# =============================================================================================


def trash_textures():
    seed = 801
    t = Tex(seed=seed)
    brushed_h(t, GUNMETAL, base=2)
    # Vertical ribs.
    for x in range(1, 16, 3):
        t.vline(x, 0, 15, GUNMETAL[4])
        t.vline(x + 1, 0, 15, GUNMETAL[1])
    # Vent slots near the top of each side (v 5..7 is y 9..11, under the hazard band region).
    t.rect(5, 11, 10, 13, GUNMETAL[0])
    for x in range(5, 11, 2):
        t.vline(x, 11, 13, BLACK)
        t.vline(x + 1, 11, 13, GUNMETAL[3])
    t.save('block/physical/trash/side')

    t = Tex(seed=seed + 1)
    for y in range(16):
        for x in range(16):
            stripe = ((x + y) // 3) % 2 == 0
            t.set(x, y, AMBER[3] if stripe else rgb('1b1d22'))
    for x in range(16):
        t.set(x, 0, mix(t.get(x, 0), BLACK, 0.4))
    t.save('block/physical/trash/hazard')

    t = Tex(seed=seed + 2)
    hammered(t, STEEL, base=2, speckle=0.2)
    t.save('block/physical/trash/trim')

    t = Tex(seed=seed + 3)
    hammered(t, GUNMETAL, base=3, speckle=0.2)
    t.outline(3, 3, 12, 12, GUNMETAL[1])
    inset_frame(t, 5, 5, 10, 10, GUNMETAL, base=2)
    t.save('block/physical/trash/lid')

    # The void: an animated swirling singularity (interpolated, 16 frames).
    frames = []
    for f in range(16):
        fr = Tex(seed=seed + 10 + f)
        phase = f / 16 * math.tau
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                swirl = math.sin(a * 3 + r * 0.9 - phase * 2)
                v = max(0.0, 1.0 - r / 9.5)
                level = 0.15 + 0.55 * v + 0.25 * swirl * (1 - v * 0.6)
                idx = max(0, min(5, int(level * 6)))
                c = VIOLET[idx]
                if r < 1.6:
                    c = BLACK
                fr.set(x, y, c)
        frames.append(fr)
    kit.save_animation('block/physical/trash/void', frames, frametime=2, interpolate=True)


def trash_model(m: Model):
    m.box((2, 0, 2), (14, 1, 14), 'casing', name='base')
    m.box((6, 0, 1), (10, 0.75, 2), 'trim', name='pedal')
    m.box((3, 1, 3), (13, 11, 13), faces={'north': 'side', 'south': 'side', 'west': 'side', 'east': 'side', 'down': 'casing'},
          name='body')
    for x0, z0 in ((2, 2), (12, 2), (2, 12), (12, 12)):
        m.box((x0, 1, z0), (x0 + 2, 11.5, z0 + 2), 'trim', name='post')
    m.box((4, 8, 2.5), (12, 9.5, 3), 'hazard', skip=('south',), name='hazard_front')
    m.box((4, 8, 13), (12, 9.5, 13.5), 'hazard', skip=('north',), name='hazard_back')
    m.box((2.5, 8, 4), (3, 9.5, 12), 'hazard', skip=('east',), name='hazard_west')
    m.box((13, 8, 4), (13.5, 9.5, 12), 'hazard', skip=('west',), name='hazard_east')
    m.box((2.5, 11, 2.5), (13.5, 12, 13.5), faces={'north': 'trim', 'south': 'trim', 'west': 'trim', 'east': 'trim',
                                                   'up': 'lid', 'down': 'trim'}, name='rim')
    m.box((3, 12, 3), (13, 13, 5), 'lid', name='lid_north')
    m.box((3, 12, 11), (13, 13, 13), 'lid', name='lid_south')
    m.box((3, 12, 5), (5, 13, 11), 'lid', name='lid_west')
    m.box((11, 12, 5), (13, 13, 11), 'lid', name='lid_east')
    m.box((5, 12.05, 5), (11, 12.1, 11), faces={'up': ('void', [0, 0, 16, 16])}, emissive=True, name='void')
    m.box((5, 13, 7.5), (6, 13.75, 8.5), 'trim', name='handle_post')
    m.box((10, 13, 7.5), (11, 13.75, 8.5), 'trim', name='handle_post')
    m.box((5, 13.75, 7.5), (11, 14.5, 8.5), 'hardware', name='handle')
    m.box((5, 3, 2.5), (11, 6.5, 3), 'casing', skip=('south',), name='status_panel')
    m.box((6, 4.25, 2.25), (10, 5.25, 2.5), 'glow', emissive=True, skip=('south',), name='status_light')


def trash_models():
    trash_textures()
    textures = {
        'particle': 'block/physical/trash/trim',
        'side': 'block/physical/trash/side',
        'hazard': 'block/physical/trash/hazard',
        'trim': 'block/physical/trash/trim',
        'lid': 'block/physical/trash/lid',
        'void': 'block/physical/trash/void',
        'casing': 'block/physical/casing',
        'hardware': 'block/physical/hardware',
        'glow': 'block/physical/glow_violet',
    }
    m = Model(textures=textures, render_type='minecraft:cutout')
    trash_model(m)
    m.save('block/trash_can')
    kit.item_model('trash_can', parent='block/trash_can')
    kit.blockstate_horizontal('trash_can')
    kit.loot_self('trash_can')


# =============================================================================================
# Item sprites
# =============================================================================================


def kit_sprite(tier: str):
    """Upgrade kit: a tier-metal reinforcement bracket around a glowing upgrade chevron."""
    ramp = TIERS[tier]
    glow = GLOWS[TIER_GLOW[tier]]
    t = Tex(seed=seed_of(tier) + 50)
    # Outer bracket (rounded square) in tier metal.
    for y in range(2, 14):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (2, 13))
            if corner:
                continue
            t.set(x, y, ramp[2])
    hammered(t, ramp, 3, 3, 12, 12, base=2, speckle=0.15)
    t.hline(3, 12, 2, ramp[4])
    t.vline(2, 3, 12, ramp[4])
    t.hline(3, 12, 13, ramp[0])
    t.vline(13, 3, 12, ramp[0])
    t.set(3, 3, ramp[5])
    # Dark inner well.
    t.rect(5, 5, 10, 10, GUNMETAL[0])
    inset_frame(t, 4, 4, 11, 11, ramp, base=2)
    # Upgrade glyph: two stacked rank chevrons, the upper one brighter.
    for top, (hi, mid, lo) in ((5, (glow[5], glow[4], glow[3])), (8, (glow[3], glow[3], glow[2]))):
        t.hline(7, 8, top, hi)
        t.set(6, top + 1, mid)
        t.set(9, top + 1, mid)
        t.set(7, top + 1, lo)
        t.set(8, top + 1, lo)
        t.set(5, top + 2, lo)
        t.set(10, top + 2, lo)
        t.set(6, top + 2, mid)
        t.set(9, top + 2, mid)
    # Corner rivets.
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        t.set(x, y, STEEL[4])
        t.set(x + 1, y + 1, ramp[0])
    # Outline for readability on any background.
    out = Tex(16)
    for y in range(16):
        for x in range(16):
            if t.get(x, y)[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and t.get(nx, ny)[3] > 0:
                        out.set(x, y, shade(ramp[0], 0.6))
                        break
    out.paste(t)
    out.save(f'item/{tier}_vault_kit')
    kit.item_model(f'{tier}_vault_kit')


def outline_sprite(t: Tex, color):
    out = Tex(t.w)
    for y in range(t.h):
        for x in range(t.w):
            if t.get(x, y)[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < t.w and 0 <= ny < t.h and t.get(nx, ny)[3] > 0:
                        out.set(x, y, color)
                        break
    out.paste(t)
    return out


def pouch_sprites():
    leather = [rgb('120c1c'), rgb('22183a'), rgb('33255a'), rgb('47367a'), rgb('5f4b9c'), rgb('8a74c4')]
    t = Tex(seed=901)
    # Bag body: rounded sack shape.
    rows = {4: (5, 10), 5: (4, 11), 6: (3, 12), 7: (2, 13), 8: (2, 13), 9: (2, 13), 10: (2, 13), 11: (2, 13), 12: (3, 12), 13: (4, 11)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            t.set(x, y, leather[2])
    hammered(t, leather, 2, 6, 13, 13, base=2, speckle=0.15)
    for y, (x0, x1) in rows.items():
        t.set(x0, y, leather[3])
        t.set(x1, y, leather[1])
    t.hline(4, 11, 13, leather[0])
    # Neck and drawstring.
    t.rect(6, 2, 9, 4, leather[3])
    t.hline(6, 9, 2, leather[4])
    t.hline(5, 10, 4, STEEL[2])
    t.set(5, 5, STEEL[3])
    t.set(4, 6, STEEL[1])
    # Flap across the top of the body with a metal rim.
    t.rect(3, 6, 12, 8, leather[1])
    t.hline(3, 12, 6, leather[3])
    t.hline(3, 12, 9, GUNMETAL[1])
    # Ender eye emblem on the body.
    t.rect(7, 10, 8, 11, TEAL[3])
    t.set(7, 10, TEAL[5])
    t.set(8, 11, TEAL[1])
    t.set(6, 10, TEAL[1])
    t.set(9, 11, TEAL[1])
    out = outline_sprite(t, rgb('07050c'))
    out.save('item/linked_pouch')
    # Three latch buttons on the flap, as separate tintable layers (left to right).
    for i, x in enumerate((4, 7, 10)):
        layer = Tex(16)
        layer.set(x, 7, rgb('ffffff'))
        layer.set(x + 1, 7, rgb('e6e9ee'))
        layer.set(x, 8, rgb('c7ccd4'))
        layer.set(x + 1, 8, rgb('9aa2ae'))
        layer.save(f'item/linked_pouch_latch_{i}')
    kit.item_model('linked_pouch', layers=['item/linked_pouch', 'item/linked_pouch_latch_0', 'item/linked_pouch_latch_1',
                                            'item/linked_pouch_latch_2'])


RELOCATOR_GRID = [
    # A gravity clamp held diagonally: grip bottom-left, two jaws top-right with the load between them.
    '................',
    '..........t.....',
    '........hjt.....',
    '........hj......',
    '........hj......',
    '........hj....t.',
    '........hhhhhhj.',
    '.......cjjjjjjs.',
    '......bbs.......',
    '.....bbs........',
    '....bbs.........',
    '...gGs..........',
    '..gGs...........',
    '.gGs............',
    '.pg.............',
    '................',
]


def relocator_sprite(name: str, body, energy, full: bool):
    """A gravity clamp: grip bottom-left, shaft, two jaws top-right with energy (or a crate) between."""
    palette = {
        't': body[4], 'h': body[3], 'j': body[2], 's': body[0], 'b': body[2],
        'c': STEEL[4], 'g': GUNMETAL[2], 'G': GUNMETAL[4], 'p': STEEL[3],
    }
    t = Tex(seed=seed_of(name))
    for y, row in enumerate(RELOCATOR_GRID):
        for x, ch in enumerate(row):
            if ch in palette:
                t.set(x, y, palette[ch])
    # Shaft highlight runs along its upper edge.
    for x, y in ((5, 9), (6, 8), (4, 10)):
        t.set(x, y, body[3])
    if full:
        crate = [rgb('3a2814'), rgb('5c3f1f'), rgb('86602f'), rgb('a97c40'), rgb('c99c5c'), rgb('e8c58f')]
        t.rect(10, 3, 12, 5, crate[2])
        t.hline(10, 12, 3, crate[4])
        t.vline(10, 3, 5, crate[3])
        t.set(12, 5, crate[0])
        t.set(11, 4, crate[1])
        for x, y in ((13, 4), (11, 2), (13, 2)):
            t.set(x, y, energy[4])
    else:
        for x, y, i in ((11, 4, 4), (12, 3, 3), (10, 5, 2), (12, 5, 3), (13, 3, 2)):
            t.set(x, y, energy[i])
    out = outline_sprite(t, shade(body[0], 0.5))
    suffix = '_full' if full else ''
    out.save(f'item/{name}{suffix}')


def relocator_items():
    for name, body, energy in (('relocator', STEEL, CYAN), ('netherite_relocator', NETHERITE, VIOLET)):
        relocator_sprite(name, body, energy, False)
        relocator_sprite(name, body, energy, True)
        kit.item_model(f'{name}_full')
        data = {
            'parent': 'minecraft:item/handheld',
            'textures': {'layer0': f'omnivault:item/{name}'},
            'overrides': [{'predicate': {'omnivault:full': 1}, 'model': f'omnivault:item/{name}_full'}],
        }
        kit.write_json(kit.ASSETS / 'models' / 'item' / f'{name}.json', data)
        full = {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'omnivault:item/{name}_full'}}
        kit.write_json(kit.ASSETS / 'models' / 'item' / f'{name}_full.json', full)


# =============================================================================================
# Data: tags and recipes
# =============================================================================================


def merge_tag(registry: str, name: str, values: list, namespace: str):
    """Writes a tag, keeping entries other features already put in the same file."""
    kit.merge_tag(registry, name, values, namespace)


def tags():
    vaults = [f'omnivault:{t}_vault' for t in TIER_ORDER]
    merge_tag('block', 'mineable/pickaxe', vaults + ['omnivault:linked_vault', 'omnivault:trash_can'], 'minecraft')
    merge_tag('block', 'needs_stone_tool', ['omnivault:iron_vault', 'omnivault:gold_vault'], 'minecraft')
    merge_tag('block', 'needs_iron_tool', ['omnivault:diamond_vault', 'omnivault:linked_vault'], 'minecraft')
    merge_tag('block', 'needs_diamond_tool', ['omnivault:netherite_vault', 'omnivault:omnium_vault'], 'minecraft')
    kit.write_json(kit.DATA / 'tags' / 'block' / 'relocator_denylist.json', {'replace': False, 'values': [
        '#minecraft:beds',
        'minecraft:barrier', 'minecraft:bedrock', 'minecraft:chain_command_block', 'minecraft:command_block',
        'minecraft:end_gateway', 'minecraft:end_portal', 'minecraft:end_portal_frame', 'minecraft:jigsaw',
        'minecraft:light', 'minecraft:moving_piston', 'minecraft:piston_head', 'minecraft:reinforced_deepslate',
        'minecraft:repeating_command_block', 'minecraft:spawner', 'minecraft:structure_block',
        'minecraft:trial_spawner', 'minecraft:vault',
    ]})
    kit.write_json(kit.DATA / 'tags' / 'block' / 'vault_upgradeable.json', {'replace': False, 'values': [
        'minecraft:chest', 'minecraft:trapped_chest', 'minecraft:barrel',
        {'id': '#c:chests/wooden', 'required': False},
        {'id': '#c:barrels/wooden', 'required': False},
    ]})


def recipes():
    ingots = {'copper': 'minecraft:copper_ingot', 'iron': 'minecraft:iron_ingot', 'gold': 'minecraft:gold_ingot'}
    kit.shaped('copper_vault', ['CCC', 'CXC', 'CCC'], {'C': ingots['copper'], 'X': 'minecraft:chest'})
    kit.shaped('iron_vault', ['III', 'IVI', 'III'], {'I': ingots['iron'], 'V': 'omnivault:copper_vault'})
    kit.shaped('gold_vault', ['GGG', 'GVG', 'GGG'], {'G': ingots['gold'], 'V': 'omnivault:iron_vault'})
    kit.shaped('diamond_vault', ['gDg', 'DVD', 'gDg'], {'g': 'minecraft:glass', 'D': 'minecraft:diamond', 'V': 'omnivault:gold_vault'})
    kit.smithing('netherite_vault', 'minecraft:netherite_upgrade_smithing_template', 'omnivault:diamond_vault',
                 'minecraft:netherite_ingot', 'omnivault:netherite_vault')
    kit.shaped('omnium_vault', ['OAO', 'OVO', 'OOO'], {'O': 'omnivault:omnium_ingot', 'A': 'omnivault:advanced_processor',
                                                       'V': 'omnivault:netherite_vault'})

    kit.shaped('copper_vault_kit', ['CCC', 'CPC', 'CCC'], {'C': ingots['copper'], 'P': '#minecraft:planks'})
    kit.shaped('iron_vault_kit', ['III', 'ICI', 'III'], {'I': ingots['iron'], 'C': ingots['copper']})
    kit.shaped('gold_vault_kit', ['GGG', 'GIG', 'GGG'], {'G': ingots['gold'], 'I': ingots['iron']})
    kit.shaped('diamond_vault_kit', ['gDg', 'DGD', 'gDg'], {'g': 'minecraft:glass', 'D': 'minecraft:diamond', 'G': ingots['gold']})
    kit.shaped('netherite_vault_kit', ['OOO', 'ONO', 'OOO'], {'O': 'minecraft:obsidian', 'N': 'minecraft:netherite_ingot'})
    kit.shaped('omnium_vault_kit', ['OAO', 'ODO', 'OOO'], {'O': 'omnivault:omnium_ingot', 'A': 'omnivault:advanced_processor',
                                                           'D': 'minecraft:diamond'})

    kit.shaped('linked_vault', ['BWB', 'OEO', 'BLB'], {'B': 'minecraft:blaze_rod', 'W': '#minecraft:wool', 'O': 'minecraft:obsidian',
                                                       'E': 'minecraft:ender_chest', 'L': 'omnivault:logic_processor'})
    kit.shaped('linked_pouch', ['LWL', 'LEL', 'LPL'], {'L': 'minecraft:leather', 'W': '#minecraft:wool',
                                                       'E': 'minecraft:ender_pearl', 'P': 'omnivault:logic_processor'})
    kit.shaped('trash_can', ['III', 'CLC', 'CCC'], {'I': ingots['iron'], 'C': 'minecraft:cobblestone', 'L': 'minecraft:lava_bucket'})
    kit.shaped('relocator', ['SIS', 'ILI', 'SIS'], {'S': 'minecraft:slime_ball', 'I': ingots['iron'], 'L': 'omnivault:logic_processor'})
    kit.smithing('netherite_relocator', 'minecraft:netherite_upgrade_smithing_template', 'omnivault:relocator',
                 'minecraft:netherite_ingot', 'omnivault:netherite_relocator')


def main():
    shared_textures()
    for tier in TIER_ORDER:
        vault_models(tier)
        kit_sprite(tier)
    linked_models()
    pouch_sprites()
    trash_models()
    relocator_items()
    tags()
    recipes()
    print('physical: wrote vaults, kits, linked vault + pouch, trash can, relocators, tags and recipes')


if __name__ == '__main__':
    main()
