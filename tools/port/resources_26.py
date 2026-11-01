#!/usr/bin/env python3
"""Derives the Minecraft 26.x resources from the 1.21.1 ones.

1.21.1 is where the art generators in tools/assets write (1.21.1/common/src/main/resources). This script
rewrites that tree for a 26.x workspace into <version>/common/src/main/resources, plus the GameTest
structures, so later art or data changes are re-synced by running it again:

    python3 tools/port/resources_26.py 26.2          # rewrite the 26.2 resources
    python3 tools/port/resources_26.py 26.2 --check  # exit 1 if they are out of date

What changes from 1.21.1:
  * every item gets an item model definition in assets/<mod>/items/ (since 1.21.4). Item colours, the
    Relocator's "full" override and the patterns' glowing layer move into those definitions: tints,
    a has_component condition and a composite with a full-bright overlay model;
  * glowing model elements use vanilla's per-element "light_emission" instead of NeoForge's
    neoforge_data light values, which both loaders draw full-bright; NeoForge's render_type, the
    neoforge:item_layers loader and its item/default parent are gone (the render layer now follows
    the texture's transparency);
  * face UVs that reach past the sprite's edge are moved back inside it (26.x rejects them);
  * recipe ingredients are plain strings: "minecraft:stick", "#minecraft:planks", or a list of them;
  * dyeable items are no longer a tag (minecraft:dyeable): each gets a minecraft:crafting_dye recipe;
  * the controls category for our keys is translated as key.category.omnivault.main;
  * pack.mcmeta declares min_format and max_format.

And from 26.3:
  * a face lit evenly ("shade": false) is shaded as if it faced up ("shade_direction_override": "up");
  * loot tables take one "condition" and one "modifier" where they took "conditions" and "functions"
    lists, and conditions and functions name their kind with "type".

The script only reads 1.21.1 and only writes the targets below (META-INF and any other hand-written
files next to them are left alone); files there that 1.21.1 no longer produces are deleted.
"""

import argparse
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / '1.21.1' / 'common' / 'src' / 'main' / 'resources'
SOURCE_GAMETEST = ROOT / '1.21.1' / 'common' / 'src' / 'gametest' / 'resources'
MOD = 'omnivault'

# Resource pack major.minor and data pack major.minor of each target (version.json's pack_version).
PACK_VERSIONS = {
    '26.2': ((88, 0), (107, 1)),
    '26.3': ((97, 1), (121, 0)),
}
# The first 26.x release with each format change after 26.2.
SHADE_DIRECTION_OVERRIDE = (26, 3)
LOOT_MODIFIERS = (26, 3)
# Cooking times 1.21.1 used when a recipe gave none (26.3 requires one).
COOKING_TIMES = {'minecraft:smelting': 200, 'minecraft:blasting': 100, 'minecraft:smoking': 100, 'minecraft:campfire_cooking': 600}

# Item models that 1.21.1 only reaches through an override; they are models, not items.
OVERRIDE_MODELS = {'relocator_full', 'netherite_relocator_full'}
# 1.21.1 item override predicates and the item model condition that replaces each.
PREDICATES = {
    # RelocatorItem.carried(stack) != null: the stack carries a picked-up block.
    f'{MOD}:full': {'property': 'minecraft:has_component', 'component': f'{MOD}:relocated_block'},
}

# The tint sources of item models, by tint index. 1.21.1 registered these as item colours in code.
WHITE = {'type': 'minecraft:constant', 'value': 0xFFFFFF}
CELL_TIER = {'type': f'{MOD}:cell_tier'}
LATCHES = [WHITE] + [{'type': f'{MOD}:linked_latch', 'latch': latch} for latch in range(3)]
ITEM_TINTS = {
    # Undyed light strips: CableBlock.CableColor.NONE and the Inventory Cable's amber.
    'nexus_cable': [{'type': 'minecraft:constant', 'value': 0x38E1FF}],
    'inventory_cable': [{'type': 'minecraft:constant', 'value': 0xF2B33D}],
    # DyedItemColor.LEATHER_COLOR until dyed.
    'leather_backpack': [{'type': 'minecraft:dye', 'default': -6265536}],
    'linked_pouch': LATCHES,
    'linked_vault': LATCHES,
}
CELL_ITEMS = ('storage_cell_', 'fluid_cell_', 'creative_storage_cell', 'creative_fluid_cell')


def dump(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + '\n'


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


class Output:
    """Collects every file to write so the result can be compared (--check) or written in one go."""

    def __init__(self):
        self.files = {}

    def text(self, path, content):
        self.files[path] = content.encode('utf-8')

    def json(self, path, data):
        self.text(path, dump(data))

    def copy(self, path, source):
        self.files[path] = source.read_bytes()


# -- models -----------------------------------------------------------------------------------------


def version_key(version):
    return tuple(int(part) for part in version.split('.'))


def convert_model(model, version):
    """A 1.21.1 block or item model as 26.x reads it; returns (model, glowing item layers)."""
    model = dict(model)
    model.pop('render_type', None)
    glowing = []
    if model.get('loader') == 'neoforge:item_layers':
        layers = model.pop('neoforge_data', {}).get('layers', {})
        glowing = sorted(int(index) for index, data in layers.items() if data.get('block_light', 0) > 0)
        del model['loader']
    if model.get('parent') == 'neoforge:item/default':
        model['parent'] = 'minecraft:item/generated'
    model.pop('overrides', None)
    if 'elements' in model:
        model['elements'] = [convert_element(element, version) for element in model['elements']]
    return model, glowing


def convert_element(element, version):
    element = dict(element)
    if version_key(version) >= SHADE_DIRECTION_OVERRIDE and element.pop('shade', True) is False:
        element['shade_direction_override'] = 'up'
    data = element.pop('neoforge_data', None)
    if data:
        light = max(data.get('block_light', 0), data.get('sky_light', 0))
        if light > 0:
            element['light_emission'] = light
    if 'faces' in element:
        element['faces'] = {side: convert_face(face) for side, face in element['faces'].items()}
    return element


def convert_face(face):
    """26.x reads a face's texture pixels to pick its render layer and rejects UVs outside the sprite.

    The generated models give the thin edges of inset parts UVs just past 0 or 16, which used to sample a
    neighbouring sprite in the atlas; slide them back inside the sprite, keeping their size and direction.
    """
    uv = face.get('uv')
    if not uv or all(0 <= value <= 16 for value in uv):
        return face
    uv = list(uv)
    for axis in (0, 1):
        low, high = sorted((uv[axis], uv[axis + 2]))
        shift = -low if low < 0 else 16 - high if high > 16 else 0
        uv[axis] = min(max(uv[axis] + shift, 0), 16)
        uv[axis + 2] = min(max(uv[axis + 2] + shift, 0), 16)
    return dict(face, uv=uv)


def glow_model(texture, version):
    """A flat, full-bright overlay for one layer of a generated item model, just outside its two faces."""
    return {
        'parent': 'minecraft:item/generated',
        'textures': {'glow': texture, 'particle': texture},
        'elements': [{
            'from': [0, 0, 7.45],
            'to': [16, 16, 8.55],
            **({'shade_direction_override': 'up'} if version_key(version) >= SHADE_DIRECTION_OVERRIDE else {'shade': False}),
            'light_emission': 15,
            'faces': {
                'north': {'uv': [16, 0, 0, 16], 'texture': '#glow'},
                'south': {'uv': [0, 0, 16, 16], 'texture': '#glow'},
            },
        }],
    }


def item_tints(name):
    if name.startswith(CELL_ITEMS) and not name.endswith('_housing'):
        return [CELL_TIER]
    return ITEM_TINTS.get(name)


def plain_model(name, tints=None):
    model = {'type': 'minecraft:model', 'model': f'{MOD}:item/{name}'}
    if tints:
        model['tints'] = tints
    return model


def item_definition(name, source_model, glowing):
    tints = item_tints(name)
    model = plain_model(name, tints)
    for override in source_model.get('overrides', []):
        predicate = override['predicate']
        if len(predicate) != 1 or next(iter(predicate.values())) != 1:
            raise SystemExit(f'item/{name}: only single on/off override predicates are converted, not {predicate}')
        key = next(iter(predicate))
        if key not in PREDICATES:
            raise SystemExit(f'item/{name}: no item model condition known for predicate {key}')
        on_true = override['model'].split(':', 1)[1].removeprefix('item/')
        model = {'type': 'minecraft:condition', **PREDICATES[key], 'on_true': plain_model(on_true, tints), 'on_false': model}
    if glowing:
        model = {'type': 'minecraft:composite',
                 'models': [model] + [plain_model(f'{name}_glow_{layer}') for layer in glowing]}
    return {'model': model}


def convert_assets(output, target, version):
    assets = SOURCE / 'assets' / MOD
    out = target / 'assets' / MOD
    for path in sorted((assets / 'textures').rglob('*')):
        if path.is_file():
            output.copy(out / path.relative_to(assets), path)
    for path in sorted((assets / 'blockstates').glob('*.json')):
        output.json(out / 'blockstates' / path.name, load(path))
    for path in sorted((assets / 'models' / 'block').rglob('*.json')):
        model, _ = convert_model(load(path), version)
        output.json(out / path.relative_to(assets), model)
    lang = load(assets / 'lang' / 'en_us.json')
    names = {key.split('.')[2] for key in lang if key.count('.') == 2 and key.split('.')[:2] in (['item', MOD], ['block', MOD])}
    for path in sorted((assets / 'models' / 'item').glob('*.json')):
        name = path.stem
        source = load(path)
        model, glowing = convert_model(source, version)
        if glowing:
            # The glowing layers become overlays; the base model keeps the layers below them.
            textures = model['textures']
            for layer in glowing:
                output.json(out / 'models' / 'item' / f'{name}_glow_{layer}.json', glow_model(textures.pop(f'layer{layer}'), version))
        output.json(out / 'models' / 'item' / path.name, model)
        if name in OVERRIDE_MODELS:
            continue
        if name not in names:
            raise SystemExit(f'item/{name} has an item model but no name in en_us.json')
        output.json(out / 'items' / path.name, item_definition(name, source, glowing))
    missing = sorted(name for name in names if not (assets / 'models' / 'item' / f'{name}.json').exists())
    if missing:
        raise SystemExit('No item model for ' + ', '.join(missing))
    # 26.x names the controls category after its id: KeyMapping.Category(omnivault:main).
    if 'key.categories.omnivault' in lang:
        lang[f'key.category.{MOD}.main'] = lang.pop('key.categories.omnivault')
    output.json(out / 'lang' / 'en_us.json', dict(sorted(lang.items())))


# -- data -------------------------------------------------------------------------------------------


def ingredient(value):
    if isinstance(value, list):
        return [ingredient(entry) for entry in value]
    if isinstance(value, dict) and set(value) == {'item'}:
        return value['item']
    if isinstance(value, dict) and set(value) == {'tag'}:
        return '#' + value['tag']
    raise SystemExit(f'Unsupported ingredient {value!r}')


def convert_recipe(recipe):
    recipe = dict(recipe)
    if 'key' in recipe:
        recipe['key'] = {symbol: ingredient(value) for symbol, value in recipe['key'].items()}
    if 'ingredients' in recipe:
        recipe['ingredients'] = [ingredient(value) for value in recipe['ingredients']]
    for field in ('ingredient', 'template', 'base', 'addition'):
        if field in recipe:
            recipe[field] = ingredient(recipe[field])
    if recipe['type'] in COOKING_TIMES:
        recipe.setdefault('cookingtime', COOKING_TIMES[recipe['type']])
    return recipe


def loot_condition(condition):
    condition = {'type' if key == 'condition' else key: value for key, value in condition.items()}
    return convert_loot(condition)


def loot_function(function):
    function = {'type' if key == 'function' else key: value for key, value in function.items()}
    return convert_loot(function)


def convert_loot(value):
    """A 1.21.1 loot table part with lists of conditions and functions folded into one of each."""
    if isinstance(value, list):
        return [convert_loot(entry) for entry in value]
    if not isinstance(value, dict):
        return value
    converted = {}
    for key, entry in value.items():
        if key == 'conditions':
            terms = [loot_condition(condition) for condition in entry]
            converted['condition'] = terms[0] if len(terms) == 1 else {'type': 'minecraft:all_of', 'terms': terms}
        elif key == 'functions':
            functions = [loot_function(function) for function in entry]
            # A list is read as one sequence of functions.
            converted['modifier'] = functions[0] if len(functions) == 1 else functions
        elif key in ('rolls', 'bonus_rolls') and isinstance(entry, float) and entry.is_integer():
            converted[key] = int(entry)
        else:
            converted[key] = convert_loot(entry)
    return converted


def dye_recipe(item):
    return {
        'type': 'minecraft:crafting_dye',
        'dye': '#minecraft:dyes',
        'group': 'dyed_' + item.split(':', 1)[1],
        'result': {'id': item},
        'target': item,
    }


def convert_data(output, target, version):
    data = SOURCE / 'data'
    for path in sorted(data.rglob('*.json')):
        relative = path.relative_to(data)
        content = load(path)
        if relative == Path('minecraft/tags/item/dyeable.json'):
            for item in content['values']:
                namespace, name = item.split(':', 1)
                output.json(target / 'data' / namespace / 'recipe' / f'{name}_dyed.json', dye_recipe(item))
            continue
        if relative.parts[1] == 'recipe':
            content = convert_recipe(content)
        elif relative.parts[1] == 'loot_table' and version_key(version) >= LOOT_MODIFIERS:
            content = convert_loot(content)
        output.json(target / 'data' / relative, content)


def convert_gametest(output, target_gametest):
    for path in sorted(SOURCE_GAMETEST.rglob('*')):
        if path.is_file():
            output.copy(target_gametest / path.relative_to(SOURCE_GAMETEST), path)


def pack_mcmeta(version):
    (resource, data) = PACK_VERSIONS[version]
    # One pack.mcmeta serves the mod's assets and data, so it spans both formats.
    return {'pack': {'description': '${mod_name}', 'min_format': list(resource), 'max_format': list(data)}}


# -- driver -----------------------------------------------------------------------------------------


def build(version):
    workspace = ROOT / version
    target = workspace / 'common' / 'src' / 'main' / 'resources'
    target_gametest = workspace / 'common' / 'src' / 'gametest' / 'resources'
    output = Output()
    convert_assets(output, target, version)
    convert_data(output, target, version)
    output.json(target / 'pack.mcmeta', pack_mcmeta(version))
    output.copy(target / f'{MOD}_logo.png', SOURCE / f'{MOD}_logo.png')
    convert_gametest(output, target_gametest)
    owned = [target / 'assets' / MOD, target / 'data', target_gametest]
    return output.files, owned


def existing(owned):
    files = {}
    for root in owned:
        if root.exists():
            for path in root.rglob('*'):
                if path.is_file():
                    files[path] = path.read_bytes()
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('version', choices=sorted(PACK_VERSIONS), help='the 26.x workspace to write')
    parser.add_argument('--check', action='store_true', help='only report whether the target is up to date')
    args = parser.parse_args()

    files, owned = build(args.version)
    current = existing(owned)
    for path in [path for path in files if path.name in ('pack.mcmeta', f'{MOD}_logo.png')]:
        if path.exists():
            current[path] = path.read_bytes()
    stale = sorted(path for path in current if path not in files)
    changed = sorted(path for path, content in files.items() if current.get(path) != content)
    if args.check:
        for path in changed + stale:
            print(path.relative_to(ROOT))
        if changed or stale:
            print(f'{len(changed)} file(s) to write and {len(stale)} to delete; run tools/port/resources_26.py {args.version}')
            return 1
        print(f'{args.version} resources are up to date')
        return 0
    for path in stale:
        path.unlink()
    for path in changed:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(files[path])
    for root in owned:
        for directory in sorted((d for d in root.rglob('*') if d.is_dir()), reverse=True) if root.exists() else ():
            if not any(directory.iterdir()):
                directory.rmdir()
    print(f'{args.version}: wrote {len(changed)} file(s), deleted {len(stale)}, {len(files)} in total')
    return 0


if __name__ == '__main__':
    sys.exit(main())
