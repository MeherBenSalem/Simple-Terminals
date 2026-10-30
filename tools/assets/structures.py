#!/usr/bin/env python3
"""Writes the GameTest structure templates (gzipped NBT) used by 1.21.1/neoforge/src/gametest.

Each template is an open test floor of polished andesite; tests place everything else in code.
Usage: python3 tools/assets/structures.py
"""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '1.21.1' / 'neoforge' / 'src' / 'gametest' / 'resources' / 'data' / 'omnivault' / 'structure'
DATA_VERSION = 3955  # Minecraft 1.21.1

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def _name(name):
    raw = name.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def _payload(tag_type, value):
    if tag_type == TAG_INT:
        return struct.pack('>i', value)
    if tag_type == TAG_STRING:
        return _name(value)
    if tag_type == TAG_LIST:
        element_type, items = value
        out = struct.pack('>bi', element_type if items else TAG_END, len(items))
        return out + b''.join(_payload(element_type, item) for item in items)
    if tag_type == TAG_COMPOUND:
        out = b''
        for key, (child_type, child) in value.items():
            out += struct.pack('>b', child_type) + _name(key) + _payload(child_type, child)
        return out + struct.pack('>b', TAG_END)
    raise ValueError(tag_type)


def floor_template(name, width, height, depth, block='minecraft:polished_andesite'):
    blocks = []
    for x in range(width):
        for z in range(depth):
            pos = (TAG_LIST, (TAG_INT, [x, 0, z]))
            blocks.append({'pos': pos, 'state': (TAG_INT, 0)})
    root = {
        'DataVersion': (TAG_INT, DATA_VERSION),
        'size': (TAG_LIST, (TAG_INT, [width, height, depth])),
        'palette': (TAG_LIST, (TAG_COMPOUND, [{'Name': (TAG_STRING, block)}])),
        'blocks': (TAG_LIST, (TAG_COMPOUND, blocks)),
        'entities': (TAG_LIST, (TAG_COMPOUND, [])),
    }
    data = struct.pack('>b', TAG_COMPOUND) + _name('') + _payload(TAG_COMPOUND, root)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f'{name}.nbt').write_bytes(gzip.compress(data, mtime=0))


if __name__ == '__main__':
    floor_template('floor_9x9', 9, 6, 9)
    floor_template('floor_17x17', 17, 8, 17)
    print('Wrote', ', '.join(p.name for p in sorted(OUT.glob('*.nbt'))))
