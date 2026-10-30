"""Resource consistency checks for every OmniVault feature (standard library only).

Run from the repository root: python3 -m unittest discover -s tests

Registered ids are read from the Java sources (``ModRegistry.blockWithItem/block/item/hiddenItem`` calls,
including ids passed through small helper methods or local variables), so new features are covered
automatically once their content classes and assets are merged.
"""
from __future__ import annotations

import json
import re
import sys
import unittest
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / '1.21.1'
RESOURCES = PROJECT / 'common' / 'src' / 'main' / 'resources'
ASSETS = RESOURCES / 'assets' / 'omnivault'
DATA = RESOURCES / 'data'
NS = 'omnivault'


def resource_roots():
    return sorted(p for p in PROJECT.glob('*/src/*/resources') if p.is_dir())


def java_sources():
    return sorted(PROJECT.glob('*/src/main/java/**/*.java'))


def _first_argument(text: str, start: int) -> str:
    depth, i, in_string = 0, start, False
    while i < len(text):
        c = text[i]
        if in_string:
            if c == '\\':
                i += 2
                continue
            if c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c in '([{':
            depth += 1
        elif c in ')]}':
            if depth == 0:
                return text[start:i].strip()
            depth -= 1
        elif c == ',' and depth == 0:
            return text[start:i].strip()
        i += 1
    return text[start:].strip()


def _literals(expr: str):
    """(ids, prefixes) named by a Java string expression: "a", "a" + x, cond ? "a" : "b" + x ..."""
    ids, prefixes = set(), set()
    for m in re.finditer(r'"([a-z0-9_./]*)"(\s*\+)?', expr):
        (prefixes if m.group(2) else ids).add(m.group(1))
    return ids, prefixes


@lru_cache(maxsize=1)
def registrations():
    """{'blocks_with_items': set, 'blocks': set, 'items': set, 'prefixes': {kind: set}}"""
    found = {'blockWithItem': set(), 'block': set(), 'item': set()}
    prefixes = {'blockWithItem': set(), 'block': set(), 'item': set()}
    for path in java_sources():
        text = path.read_text(encoding='utf-8')
        for call in re.finditer(r'ModRegistry\.(blockWithItem|block|item|hiddenItem)\s*\(', text):
            kind = 'item' if call.group(1) == 'hiddenItem' else call.group(1)
            expr = _first_argument(text, call.end())
            if re.fullmatch(r'[A-Za-z_]\w*', expr):
                before = text[:call.start()]
                local = list(re.finditer(r'String\s+' + expr + r'\s*=\s*([^;]+);', before))
                param = list(re.finditer(r'(\w+)\s*\(\s*(?:final\s+)?String\s+' + expr + r'\b', before))
                if local and (not param or local[-1].start() > param[-1].start()):
                    ids, pre = _literals(local[-1].group(1))
                elif param:
                    helper = param[-1].group(1)
                    ids, pre = set(), set()
                    for use in re.finditer(r'\b' + helper + r'\s*\(', text):
                        # the declaration's own "(String name" has no literal, so it adds nothing
                        i2, p2 = _literals(_first_argument(text, use.end()))
                        ids |= i2
                        pre |= p2
                else:
                    ids, pre = set(), set()
            else:
                ids, pre = _literals(expr) if expr.startswith('"') else (set(), set())
            found[kind] |= ids
            prefixes[kind] |= pre
    return {'blocks_with_items': found['blockWithItem'], 'blocks': found['block'], 'items': found['item'],
            'prefixes': prefixes}


def all_item_ids():
    reg = registrations()
    return reg['items'] | reg['blocks_with_items']


def item_prefixes():
    reg = registrations()
    return reg['prefixes']['item'] | reg['prefixes']['blockWithItem']


def lang_ids():
    """Ids with a block/item name in the built lang file. Every registered id has one (the
    ResourceGameTests check that against the live registry), so this covers ids the Java scan can't
    see, such as names built in loops."""
    lang_file = ASSETS / 'lang' / 'en_us.json'
    if not lang_file.exists():
        return set()
    keys = json.loads(lang_file.read_text(encoding='utf-8'))
    return {k.split('.')[2] for k in keys if k.count('.') == 2 and k.split('.')[0] in ('item', 'block') and k.split('.')[1] == NS}


def is_registered_item(name: str) -> bool:
    return name in all_item_ids() or name in lang_ids() or any(name.startswith(p) for p in item_prefixes())


def load(path: Path):
    return json.loads(path.read_text(encoding='utf-8'))


def split_id(ref: str, default='minecraft'):
    return ref.split(':', 1) if ':' in ref else (default, ref)


def model_path(ref: str):
    ns, path = split_id(ref)
    return RESOURCES / 'assets' / ns / 'models' / (path + '.json') if ns == NS else None


def texture_path(ref: str):
    ns, path = split_id(ref)
    return RESOURCES / 'assets' / ns / 'textures' / (path + '.png') if ns == NS else None


class ResourceTests(unittest.TestCase):
    def test_registrations_found(self):
        reg = registrations()
        self.assertTrue(reg['blocks_with_items'], 'no ModRegistry.blockWithItem calls found')
        self.assertTrue(reg['items'], 'no ModRegistry.item calls found')

    def test_every_json_parses(self):
        failures = []
        for root in resource_roots():
            for path in list(root.rglob('*.json')) + list(root.rglob('*.mcmeta')):
                try:
                    load(path)
                except (ValueError, UnicodeDecodeError) as exc:
                    failures.append(f'{path.relative_to(ROOT)}: {exc}')
        self.assertFalse(failures, '\n'.join(failures))

    def test_model_textures_and_parents_resolve(self):
        failures = []
        # Models other models inherit from may leave texture variables for their children to fill.
        templates = set()
        for path in (ASSETS / 'models').rglob('*.json'):
            parent = load(path).get('parent', '')
            if parent.startswith(f'{NS}:'):
                templates.add(parent.split(':', 1)[1])
        for path in sorted((ASSETS / 'models').rglob('*.json')):
            name = path.relative_to(ASSETS / 'models').with_suffix('').as_posix()
            chain, current, seen = [], f'{NS}:{name}', set()
            while current:
                file = model_path(current)
                if file is None:  # vanilla parent: not checked here
                    break
                if current in seen or not file.exists():
                    failures.append(f'{name}: parent {current} missing or cyclic')
                    break
                seen.add(current)
                data = load(file)
                chain.append(data)
                parent = data.get('parent')
                current = (parent if ':' in parent or parent.startswith('builtin/') else f'minecraft:{parent}') if parent else None
            textures = {}
            for data in reversed(chain):
                textures.update(data.get('textures', {}))
            for key, value in textures.items():
                if not value.startswith('#'):
                    png = texture_path(value)
                    if png is not None and not png.exists():
                        failures.append(f'{name}: texture {key} -> {value} has no PNG')
            elements = next((d['elements'] for d in chain if 'elements' in d), [])
            for i, element in enumerate(elements):
                for face, spec in element.get('faces', {}).items():
                    ref, hops = spec.get('texture', ''), 0
                    while ref.startswith('#') and hops < 16:
                        ref = textures.get(ref[1:], '')
                        hops += 1
                    if not ref:
                        if name not in templates:
                            failures.append(f'{name}: element {i} {face} texture {spec.get("texture")} does not resolve')
                    elif texture_path(ref) is not None and not texture_path(ref).exists():
                        failures.append(f'{name}: element {i} {face} texture {ref} has no PNG')
        self.assertFalse(failures, '\n'.join(failures))

    def test_animations_have_whole_frames(self):
        failures = []
        for meta in sorted((ASSETS / 'textures').rglob('*.png.mcmeta')):
            png = meta.with_suffix('')
            if not png.exists():
                failures.append(f'{meta.name}: no texture')
                continue
            raw = png.read_bytes()
            width, height = int.from_bytes(raw[16:20], 'big'), int.from_bytes(raw[20:24], 'big')
            if height % width:
                failures.append(f'{png.relative_to(ASSETS)}: {width}x{height} is not a stack of square frames')
        self.assertFalse(failures, '\n'.join(failures))

    def test_blockstate_models_exist(self):
        failures = []
        for path in sorted((ASSETS / 'blockstates').glob('*.json')):
            data = load(path)
            applies = []
            for variant in data.get('variants', {}).values():
                applies += variant if isinstance(variant, list) else [variant]
            for part in data.get('multipart', []):
                apply = part.get('apply', [])
                applies += apply if isinstance(apply, list) else [apply]
            if not applies:
                failures.append(f'{path.name}: no models')
            for apply in applies:
                file = model_path(apply['model'])
                if file is not None and not file.exists():
                    failures.append(f'{path.name}: model {apply["model"]} missing')
        self.assertFalse(failures, '\n'.join(failures))

    def test_registered_blocks_have_blockstate_item_model_and_loot(self):
        reg = registrations()
        failures = []
        for name in sorted(reg['blocks_with_items']):
            if not (ASSETS / 'blockstates' / f'{name}.json').exists():
                failures.append(f'{name}: no blockstate')
            if not (ASSETS / 'models' / 'item' / f'{name}.json').exists():
                failures.append(f'{name}: no item model')
            if not (DATA / NS / 'loot_table' / 'blocks' / f'{name}.json').exists():
                failures.append(f'{name}: no loot table')
        for name in sorted(reg['blocks']):
            if not (ASSETS / 'blockstates' / f'{name}.json').exists():
                failures.append(f'{name}: no blockstate')
        self.assertFalse(failures, '\n'.join(failures))

    def test_registered_items_have_models(self):
        reg = registrations()
        models = {p.relative_to(ASSETS / 'models' / 'item').with_suffix('').as_posix()
                  for p in (ASSETS / 'models' / 'item').rglob('*.json')} if (ASSETS / 'models' / 'item').exists() else set()
        failures = [f'{name}: no item model' for name in sorted(reg['items']) if name not in models]
        for prefix in sorted(item_prefixes()):
            if not any(m.startswith(prefix) for m in models):
                failures.append(f'{prefix}*: no item model for this dynamically named item family')
        self.assertFalse(failures, '\n'.join(failures))

    def test_registered_ids_have_lang(self):
        reg = registrations()
        lang = load(ASSETS / 'lang' / 'en_us.json')
        failures = []
        for name in sorted(reg['blocks_with_items'] | reg['blocks']):
            if f'block.{NS}.{name}' not in lang:
                failures.append(f'block.{NS}.{name}')
        for name in sorted(reg['items']):
            if f'item.{NS}.{name}' not in lang:
                failures.append(f'item.{NS}.{name}')
        for prefix in sorted(item_prefixes()):
            if not any(k.startswith((f'item.{NS}.{prefix}', f'block.{NS}.{prefix}')) for k in lang):
                failures.append(f'{prefix}*: no lang entries for this dynamically named item family')
        self.assertFalse(failures, 'missing en_us entries:\n' + '\n'.join(failures))

    def test_lang_file_is_built_from_fragments(self):
        sys.path.insert(0, str(ROOT / 'tools' / 'assets'))
        try:
            import build_lang
        finally:
            sys.path.pop(0)
        self.assertEqual((ASSETS / 'lang' / 'en_us.json').read_text(encoding='utf-8'), build_lang.merged(),
                         'en_us.json is stale: run python3 tools/assets/build_lang.py')

    def test_data_references_registered_items(self):
        """omnivault ids in recipes and loot tables must be registered, unless the file is guarded by a
        neoforge:item_exists condition for that id (content from a feature not merged yet)."""
        failures = []
        for folder in ('recipe', 'loot_table'):
            for path in sorted((DATA / NS / folder).rglob('*.json')) if (DATA / NS / folder).exists() else []:
                data = load(path)
                guarded = {c.get('item') for c in data.get('neoforge:conditions', [])
                           if c.get('type') == 'neoforge:item_exists'}
                refs = set()

                def walk(node):
                    if isinstance(node, dict):
                        # Dynamic loot entries name a block-entity drop, not an item.
                        if node.get('type') != 'minecraft:dynamic':
                            for key in ('item', 'id', 'name'):
                                value = node.get(key)
                                if isinstance(value, str) and value.startswith(NS + ':'):
                                    refs.add(value.split(':', 1)[1])
                        for key, value in node.items():
                            if key != 'neoforge:conditions':
                                walk(value)
                    elif isinstance(node, list):
                        for value in node:
                            walk(value)

                walk(data)
                for ref in refs:
                    if f'{NS}:{ref}' not in guarded and not is_registered_item(ref):
                        failures.append(f'{path.relative_to(DATA)}: {NS}:{ref} is not registered')
        self.assertFalse(failures, '\n'.join(failures))


if __name__ == '__main__':
    unittest.main()
