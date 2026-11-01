#!/usr/bin/env python3
"""Derives the Minecraft 1.20.1 resources from the 1.21.1 ones.

1.21.1 is where the art generators in tools/assets write (1.21.1/common/src/main/resources). This script
rewrites that tree for 1.20.1 into 1.20.1/common/src/main/resources, plus the few loader and GameTest
resources that differ, so later art or data changes are re-synced by running it again:

    python3 tools/port/resources_1_20_1.py          # rewrite the 1.20.1 resources
    python3 tools/port/resources_1_20_1.py --check  # exit 1 if they are out of date

What changes for 1.20.1:
  * data folders are plural: recipes/, loot_tables/, tags/blocks/, tags/items/ (GameTest structures/);
  * recipe results are {"item": ..., "count": ...}, and cooking results a plain item id;
  * the c: convention tags of 1.21 do not exist on 1.20.1 Forge, so recipes use omnivault: tags that
    list the vanilla items plus the Forge (forge:) and Fabric (c:) tags of that version;
  * blocks and items that 1.20.1 does not have are replaced (recipes) or made optional (tags);
  * loot tables copy block entity data with omnivault:copy_components (1.20.1 has no data components);
  * models use Forge's forge_data / forge:item_layers instead of NeoForge's neoforge_data / neoforge:item_layers;
  * pack.mcmeta has pack format 15.

The script only reads 1.21.1 and only writes the 1.20.1 targets below; files there that 1.21.1 no longer
produces are deleted.
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / '1.21.1' / 'common' / 'src' / 'main' / 'resources'
SOURCE_GAMETEST = ROOT / '1.21.1' / 'common' / 'src' / 'gametest' / 'resources'
TARGET = ROOT / '1.20.1' / 'common' / 'src' / 'main' / 'resources'
TARGET_GAMETEST = ROOT / '1.20.1' / 'common' / 'src' / 'gametest' / 'resources'
# Fabric reads the forge:item/default parent our layered item models use from here (Forge ships its own).
TARGET_FABRIC = ROOT / '1.20.1' / 'fabric' / 'src' / 'main' / 'resources' / 'assets' / 'forge'

PACK_FORMAT = 15

# 1.21 folder -> 1.20.1 folder, matched as leading path parts below data/<namespace>/.
DATA_FOLDERS = (
    (('recipe',), ('recipes',)),
    (('loot_table',), ('loot_tables',)),
    (('tags', 'block'), ('tags', 'blocks')),
    (('tags', 'item'), ('tags', 'items')),
    (('structure',), ('structures',)),
)

# Tags that only exist in 1.21's vanilla data and that 1.20.1 handles in code instead.
DROPPED = {
    # Dyeing is decided by DyeableLeatherItem on 1.20.1 (see DyeableBackpackItem).
    Path('data/minecraft/tags/items/dyeable.json'),
}

# Blocks and items added after 1.20.1, and what a 1.20.1 recipe uses instead.
RECIPE_SUBSTITUTES = {
    # The Crafter came with 1.21; the Assembler takes the crafting table it automates.
    'minecraft:crafter': 'minecraft:crafting_table',
}
# Tag entries for blocks that 1.20.1 does not have: made optional, so the tag still loads.
NEWER_THAN_1_20_1 = {'minecraft:crafter', 'minecraft:trial_spawner', 'minecraft:vault', 'minecraft:heavy_core'}

# 1.21 convention tags used by our recipes and tags, and what they hold on 1.20.1: the vanilla items, then
# the Forge and Fabric convention tags of that version (optional, so each loads on the other loader too).
CONVENTION_TAGS = {
    'ingots/iron': (['minecraft:iron_ingot'], ['forge:ingots/iron', 'c:iron_ingots']),
    'ingots/gold': (['minecraft:gold_ingot'], ['forge:ingots/gold', 'c:gold_ingots']),
    'ingots/copper': (['minecraft:copper_ingot'], ['forge:ingots/copper', 'c:copper_ingots']),
    'gems/diamond': (['minecraft:diamond'], ['forge:gems/diamond', 'c:diamonds']),
    'chests/wooden': (['minecraft:chest', 'minecraft:trapped_chest'], ['forge:chests/wooden', 'c:wooden_chests']),
    'barrels/wooden': (['minecraft:barrel'], ['forge:barrels/wooden', 'c:wooden_barrels']),
    'shulker_boxes': (['minecraft:' + color + 'shulker_box' for color in (
        '', 'white_', 'orange_', 'magenta_', 'light_blue_', 'yellow_', 'lime_', 'pink_', 'gray_', 'light_gray_',
        'cyan_', 'purple_', 'blue_', 'brown_', 'green_', 'red_', 'black_')], ['forge:shulker_boxes', 'c:shulker_boxes']),
}


def dump(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + '\n'


def convention(tag):
    """A 1.21 c: tag id as the omnivault: tag that stands in for it on 1.20.1, or None if it is not one."""
    if not tag.startswith('c:'):
        return None
    path = tag[2:]
    if path not in CONVENTION_TAGS:
        raise SystemExit(f'No 1.20.1 equivalent for convention tag #{tag}; add it to CONVENTION_TAGS')
    return 'omnivault:' + path


def data_path(relative):
    """The 1.20.1 location of a file under data/ (or None to drop it)."""
    parts = relative.parts
    if len(parts) < 3 or parts[0] != 'data':
        return relative
    head, rest = parts[:2], parts[2:]
    for old, new in DATA_FOLDERS:
        if tuple(rest[:len(old)]) == old:
            rest = new + tuple(rest[len(old):])
            break
    converted = Path(*head, *rest)
    return None if converted in DROPPED else converted


# -- recipes -------------------------------------------------------------------------------------------

def convert_ingredient(ingredient):
    if isinstance(ingredient, list):
        return [convert_ingredient(entry) for entry in ingredient]
    if not isinstance(ingredient, dict):
        return ingredient
    ingredient = dict(ingredient)
    if 'item' in ingredient:
        ingredient['item'] = RECIPE_SUBSTITUTES.get(ingredient['item'], ingredient['item'])
    if 'tag' in ingredient:
        ingredient['tag'] = convention(ingredient['tag']) or ingredient['tag']
    return ingredient


def convert_recipe(recipe, used_tags):
    recipe = dict(recipe)
    kind = recipe.get('type')
    for key in ('ingredient', 'base', 'addition', 'template'):
        if key in recipe:
            recipe[key] = convert_ingredient(recipe[key])
    if 'ingredients' in recipe:
        recipe['ingredients'] = [convert_ingredient(entry) for entry in recipe['ingredients']]
    if 'key' in recipe:
        recipe['key'] = {symbol: convert_ingredient(entry) for symbol, entry in recipe['key'].items()}
    result = recipe.get('result')
    if isinstance(result, dict):
        if set(result) - {'id', 'count'}:
            raise SystemExit(f'Recipe result with data components cannot be converted: {result}')
        item = result['id']
        if kind in ('minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking'):
            # 1.20.1 cooking recipes name their single result item as a string.
            if result.get('count', 1) != 1:
                raise SystemExit(f'1.20.1 cooking recipes make one item: {result}')
            recipe['result'] = item
        else:
            recipe['result'] = {'item': item, 'count': result.get('count', 1)}
    for ingredient in _ingredients(recipe):
        if isinstance(ingredient, dict) and ingredient.get('tag', '').startswith('omnivault:'):
            used_tags.add(('items', ingredient['tag']))
    return recipe


def _ingredients(recipe):
    for key in ('ingredient', 'base', 'addition', 'template'):
        yield from _flat(recipe.get(key))
    for entry in recipe.get('ingredients', []):
        yield from _flat(entry)
    for entry in recipe.get('key', {}).values():
        yield from _flat(entry)


def _flat(ingredient):
    if isinstance(ingredient, list):
        for entry in ingredient:
            yield from _flat(entry)
    elif ingredient is not None:
        yield ingredient


# -- loot tables -------------------------------------------------------------------------------------

def convert_loot(table):
    def walk(node):
        if isinstance(node, dict):
            node = {key: walk(value) for key, value in node.items()}
            if node.get('function') == 'minecraft:copy_components':
                # Our block entities' data lives in item NBT on 1.20.1; see CopyComponentsFunction.
                node['function'] = 'omnivault:copy_components'
            return node
        if isinstance(node, list):
            return [walk(value) for value in node]
        return node
    return walk(table)


# -- tags ----------------------------------------------------------------------------------------------

def convert_tag(tag, registry, used_tags):
    values = []
    for value in tag.get('values', []):
        entry = value if isinstance(value, dict) else {'id': value}
        ident = entry['id']
        required = entry.get('required', True)
        if ident.startswith('#'):
            stand_in = convention(ident[1:])
            if stand_in:
                # The stand-in always exists, so the reference can stay required.
                used_tags.add((registry, stand_in))
                values.append('#' + stand_in)
                continue
        if ident in NEWER_THAN_1_20_1:
            required = False
        values.append(ident if required else {'id': ident, 'required': False})
    converted = dict(tag)
    converted['values'] = values
    return converted


def convention_tag_files(used_tags):
    """The omnivault:<path> item or block tags that stand in for 1.21's c:<path> where we use them."""
    files = {}
    for registry, tag in sorted(used_tags):
        path = tag.split(':', 1)[1]
        if path not in CONVENTION_TAGS:
            continue
        entries, conventions = CONVENTION_TAGS[path]
        values = list(entries) + [{'id': '#' + name, 'required': False} for name in conventions]
        files[Path('data', 'omnivault', 'tags', registry, *path.split('/')).with_suffix('.json')] = {
            'replace': False, 'values': values}
    return files


# -- models --------------------------------------------------------------------------------------------

def convert_model(model):
    def walk(node):
        if isinstance(node, dict):
            converted = {}
            for key, value in node.items():
                key = 'forge_data' if key == 'neoforge_data' else key
                converted[key] = walk(value)
            for key in ('parent', 'loader'):
                if isinstance(converted.get(key), str) and converted[key].startswith('neoforge:'):
                    converted[key] = 'forge:' + converted[key][len('neoforge:'):]
            return converted
        if isinstance(node, list):
            return [walk(value) for value in node]
        return node
    return walk(model)


# -- tree ----------------------------------------------------------------------------------------------

def build():
    """Every 1.20.1 file as {target root: {relative path: bytes}}."""
    common, gametest, fabric = {}, {}, {}
    used_tags = set()
    tags = {}
    for source in sorted(p for p in SOURCE.rglob('*') if p.is_file()):
        relative = source.relative_to(SOURCE)
        target = data_path(relative)
        if target is None:
            continue
        if relative.suffix != '.json' and relative.name != 'pack.mcmeta':
            common[target] = source.read_bytes()
            continue
        if relative.name == 'pack.mcmeta':
            pack = json.loads(source.read_text())
            pack['pack']['pack_format'] = PACK_FORMAT
            pack['pack'].pop('supported_formats', None)
            common[target] = dump(pack).encode()
            continue
        data = json.loads(source.read_text())
        parts = target.parts
        if parts[0] == 'data' and parts[2] == 'recipes':
            data = convert_recipe(data, used_tags)
        elif parts[0] == 'data' and parts[2] == 'loot_tables':
            data = convert_loot(data)
        elif parts[0] == 'data' and parts[2] == 'tags':
            tags[target] = data
            continue
        elif parts[0] == 'assets' and len(parts) > 2 and parts[2] == 'models':
            data = convert_model(data)
        common[target] = dump(data).encode()
    for target, data in tags.items():
        common[target] = dump(convert_tag(data, target.parts[3], used_tags)).encode()
    for target, data in convention_tag_files(used_tags).items():
        if target in common:
            raise SystemExit(f'{target} is generated as a convention stand-in but also exists in 1.21.1')
        common[target] = dump(data).encode()

    for source in sorted(p for p in SOURCE_GAMETEST.rglob('*') if p.is_file()):
        gametest[data_path(source.relative_to(SOURCE_GAMETEST))] = source.read_bytes()

    fabric[Path('models', 'item', 'default.json')] = dump({'parent': 'minecraft:item/generated'}).encode()
    return {TARGET: common, TARGET_GAMETEST: gametest, TARGET_FABRIC: fabric}


def sync(check):
    stale = []
    for root, files in build().items():
        existing = {p.relative_to(root) for p in root.rglob('*') if p.is_file()} if root.exists() else set()
        for relative, content in files.items():
            path = root / relative
            if path.exists() and path.read_bytes() == content:
                continue
            stale.append(path)
            if not check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
        for relative in sorted(existing - files.keys()):
            stale.append(root / relative)
            if not check:
                (root / relative).unlink()
        if not check and root.exists():
            for directory in sorted((p for p in root.rglob('*') if p.is_dir()), key=lambda p: len(p.parts), reverse=True):
                if not any(directory.iterdir()):
                    directory.rmdir()
    return stale


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='report out-of-date files instead of writing them')
    args = parser.parse_args()
    if not SOURCE.is_dir():
        print(f'Missing {SOURCE}', file=sys.stderr)
        return 2
    stale = sync(args.check)
    for path in stale:
        print(('out of date: ' if args.check else 'wrote: ') + str(path.relative_to(ROOT)))
    if args.check and stale:
        return 1
    print(f'{len(stale)} file(s) {"out of date" if args.check else "updated"}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
