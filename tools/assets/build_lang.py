#!/usr/bin/env python3
"""Merge tools/lang/*.json fragments into the shipped en_us.json (sorted, duplicate keys rejected).

Usage: python3 tools/assets/build_lang.py [--check]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FRAGMENTS = ROOT / 'tools' / 'lang'
TARGET = ROOT / '1.21.1' / 'common' / 'src' / 'main' / 'resources' / 'assets' / 'omnivault' / 'lang' / 'en_us.json'


def merged():
    entries = {}
    for fragment in sorted(FRAGMENTS.glob('*.json')):
        for key, value in json.loads(fragment.read_text(encoding='utf-8')).items():
            if key in entries:
                raise SystemExit(f'Duplicate lang key {key} in {fragment.name}')
            entries[key] = value
    return json.dumps(dict(sorted(entries.items())), indent=2, ensure_ascii=False) + '\n'


def main():
    text = merged()
    if '--check' in sys.argv:
        if not TARGET.exists() or TARGET.read_text(encoding='utf-8') != text:
            raise SystemExit(f'{TARGET} is out of date; run python3 tools/assets/build_lang.py')
        return
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(text, encoding='utf-8')
    print(f'Wrote {TARGET.relative_to(ROOT)} ({text.count(chr(10)) - 2} keys)')


if __name__ == '__main__':
    main()
