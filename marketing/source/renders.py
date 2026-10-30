#!/usr/bin/env python3
"""Transparent studio renders of OmniVault's own models for the listing cards (marketing/source/renders/).

Uses the project's offline model renderer (tools/assets/render_models.py), which bakes the same model JSON, textures,
tints and full-bright elements the game uses. Block entity parts (the core's crystal and ring, drive cells, assembler
rings, vault lids) are placed the way their renderers place them. Run from the repository root:

  python3 marketing/source/renders.py            # everything
  python3 marketing/source/renders.py core cells # some
  python3 marketing/source/renders.py backdrops  # card backdrops, from the screenshots
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools' / 'assets'))
import render_models as rm  # noqa: E402

OUT = Path(__file__).resolve().parent / 'renders'

CELL_TIERS = ['4k', '16k', '64k', '256k', '1m', '16m', 'singularity']
VAULTS = ['copper', 'iron', 'gold', 'diamond', 'netherite', 'omnium']
BACKPACKS = ['leather', 'copper', 'iron', 'gold', 'diamond', 'netherite']
BACKPACK_UPGRADES = ['pickup', 'magnet', 'void', 'feeding', 'restock', 'compacting', 'crafting', 'everlasting', 'deposit']
NETWORK_UPGRADES = ['speed', 'stack', 'fuzzy', 'redstone', 'range']
DEVICES = ['import_bus', 'export_bus', 'storage_bus', 'network_port_active', 'destructor', 'constructor',
           'signal_emitter_powered', 'storage_monitor_active', 'wireless_transmitter_active']
ITEMS = ['omnium_ingot', 'omnium_dust', 'logic_processor', 'advanced_processor', 'quantum_processor', 'omni_tool',
         'wireless_terminal', 'quantum_wireless_terminal', 'quantum_link_card', 'relocator', 'netherite_relocator',
         'linked_pouch', 'drawer_key', 'blank_pattern', 'upgrade_base', 'copper_vault_kit', 'omnium_vault_kit']


def model(ref, size, **kw):
    return rm.render_scene([rm.Part(ref)], size=size, **kw)


def save(img, name):
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f'{name}.png', optimize=True)
    print('rendered', name, flush=True)


def assembler_scene(quantum=True, time=30.0):
    """The assembler body with its two spinning chamber rings (as AssemblerRenderer draws them while working)."""
    prefix = 'quantum_assembler' if quantum else 'assembler'
    parts = [rm.Part(f'block/{prefix}_working')]
    for ring in range(2):
        m = rm.rot_axis('y', (1 if ring == 0 else -1.6) * time * 6.0) @ rm.rot_axis('x', 20.0 if ring == 0 else -35.0)
        scale = 1.0 if ring == 0 else 0.72
        parts.append(rm.Part(f'block/{prefix}_ring', matrix=m * scale, render_type='translucent'))
    return parts


def vault_scene(tier, openness=0.0):
    """A vault body and its lid, swung open around the hinge (y 10px, z 15px) like LidRendering.hinge."""
    lid = rm.Part(f'block/{tier}_vault_lid', pivot=(8, 10, 15))
    if openness > 0:
        lid.matrix = rm.rot_axis('x', openness * 100.0)
    return [rm.Part(f'block/{tier}_vault'), lid]


def drive_scene():
    return rm.drive_scene(True, tiers=('16m', '1m', '256k', 'singularity', '64k', '1m', '16k', '256k', '4k', '64k'),
                          leds=(0x5BE08A, 0x5BE08A, 0xF2C14E, 0x5BE08A, 0x5BE08A, 0x5BE08A, 0xFF4A4A, 0x5BE08A, 0xF2C14E, 0x5BE08A))


def backdrops():
    """Small copies of two captures, blurred behind the cards by render.cjs."""
    from PIL import Image
    shots = ROOT / 'marketing' / 'images' / 'screenshots'
    for src, dst in (('base.jpg', 'backdrop.jpg'), ('late.jpg', 'backdrop-late.jpg')):
        Image.open(shots / src).convert('RGB').resize((640, 360), Image.LANCZOS).save(OUT.parent / dst, quality=85)
        print('wrote', dst, flush=True)


JOBS = {
    'backdrops': backdrops,
    'core': lambda: save(rm.render_scene(rm.core_scene(True), size=768, yaw=205, pitch=24, fit=True, margin=0.04, bloom=0.55), 'core'),
    'drive': lambda: save(rm.render_scene(drive_scene(), size=640, yaw=200, pitch=18, fit=True, margin=0.04, bloom=0.35), 'drive'),
    'hub': lambda: save(model('block/storage_hub_active', size=512, yaw=205, pitch=24, fit=True, margin=0.05, bloom=0.3), 'storage_hub'),
    'bridge': lambda: save(model('block/quantum_bridge_active', size=640, yaw=205, pitch=24, fit=True, margin=0.04, bloom=0.5), 'quantum_bridge'),
    'qassembler': lambda: save(rm.render_scene(assembler_scene(True), size=640, yaw=205, pitch=24, fit=True, margin=0.04, bloom=0.5), 'quantum_assembler'),
    'assembler': lambda: save(rm.render_scene(assembler_scene(False), size=512, yaw=205, pitch=24, fit=True, margin=0.04, bloom=0.4), 'assembler'),
    'terminal': lambda: save(model('block/terminal_active', size=512, yaw=205, pitch=20, fit=True, margin=0.04, bloom=0.3), 'terminal'),
    'crafting_terminal': lambda: save(model('block/crafting_terminal_active', size=512, yaw=205, pitch=20, fit=True, margin=0.04, bloom=0.3), 'crafting_terminal'),
    'pattern_terminal': lambda: save(model('block/pattern_terminal_on', size=512, yaw=205, pitch=20, fit=True, margin=0.04, bloom=0.3), 'pattern_terminal'),
    'provider': lambda: save(model('block/pattern_provider_on', size=512, yaw=205, pitch=20, fit=True, margin=0.04, bloom=0.3), 'pattern_provider'),
    'processor': lambda: save(model('block/crafting_processor_busy', size=512, yaw=205, pitch=20, fit=True, margin=0.04, bloom=0.3), 'crafting_processor'),
    'cells': lambda: [save(model(f'item/storage_cell_{t}', size=256), f'cell-{t}') for t in CELL_TIERS]
    + [save(model('item/creative_storage_cell', size=256), 'cell-creative')]
    + [save(model(f'item/fluid_cell_{t}', size=256), f'fluid-cell-{t}') for t in CELL_TIERS],
    'vaults': lambda: [save(rm.render_scene(vault_scene(t), size=384, fit=True, margin=0.05), f'vault-{t}') for t in VAULTS]
    + [save(rm.render_scene(vault_scene('omnium', 1.0), size=512, yaw=200, pitch=22, fit=True, margin=0.05, bloom=0.3), 'vault-omnium-open')],
    'backpacks': lambda: [save(model(f'item/{t}_backpack', size=384, fit=True, margin=0.05), f'backpack-{t}') for t in BACKPACKS],
    'upgrades': lambda: [save(model(f'item/{u}_upgrade', size=192), f'upgrade-{u}') for u in BACKPACK_UPGRADES + NETWORK_UPGRADES],
    'devices': lambda: [save(model(f'block/{d}', size=384, yaw=205, pitch=22, fit=True, margin=0.06, bloom=0.25), f'device-{d.split("_active")[0].split("_powered")[0]}') for d in DEVICES],
    'items': lambda: [save(model(f'item/{i}', size=192), f'item-{i}') for i in ITEMS],
    'drawers': lambda: [save(model(f'item/{d}', size=384, fit=True, margin=0.05), f'drawer-{d}') for d in
                        ('drawer_1x1', 'drawer_1x2', 'drawer_2x2', 'compacting_drawer', 'drawer_controller')],
    'linked': lambda: save(model('item/linked_vault', size=384, fit=True, margin=0.05), 'linked_vault'),
    'trash': lambda: save(model('item/trash_can', size=384, fit=True, margin=0.05), 'trash_can'),
}


def main(argv):
    names = argv or list(JOBS)
    for name in names:
        JOBS[name]()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
