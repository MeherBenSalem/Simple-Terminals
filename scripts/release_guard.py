#!/usr/bin/env python3
"""Fail-closed Simple Terminals release selection and packaged-resource checks.

Requires Python 3.11+ (stdlib only). This script never publishes or starts Minecraft.
"""

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

TARGETS = (
    ('1.20.1', 'fabric'), ('1.20.1', 'forge'),
    ('1.21.1', 'fabric'), ('1.21.1', 'neoforge'),
    ('26.2', 'fabric'), ('26.2', 'neoforge'),
)
METADATA = {
    'fabric': 'fabric.mod.json',
    'forge': 'META-INF/mods.toml',
    'neoforge': 'META-INF/neoforge.mods.toml',
}
MINECRAFT_RANGES = {
    '1.20.1': '[1.20.1, 1.22)',
    '1.21.1': '[1.21.1, 1.22)',
    '26.2': '[26.2, 26.3)',
}
INGREDIENTS = {
    'R': 'minecraft:redstone_torch', 'D': 'minecraft:diamond',
    'T': 'minecraft:crafting_table', 'C': 'minecraft:chest',
}
AUXILIARY = re.compile(r'-(?:sources|javadoc|dev|dev-shadow)\.jar$')
VERSION = re.compile(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?')


class ReleaseError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def validate_version(version):
    require(VERSION.fullmatch(version) is not None, f'Invalid release version: {version!r}')


def filename(minecraft, loader, version):
    return f'simple-terminals-{minecraft}-{loader}-{version}.jar'


def candidate_jars(root, source='auto'):
    release = sorted((root / 'releases').rglob('*.jar'))
    if source == 'release' or (source == 'auto' and release):
        # Even a partial/stale prebuilt directory must fail, never fall back to a build.
        return release
    require(source in ('auto', 'build'), f'Unknown artifact source: {source}')
    candidates = []
    for minecraft in dict.fromkeys(mc for mc, _ in TARGETS):
        for jar in (root / minecraft).glob('*/build/libs/*.jar'):
            if jar.parent.parent.parent.name not in ('common', 'buildSrc', 'build-logic'):
                candidates.append(jar)
    return sorted(candidates)


def select_artifacts(root, version, source='auto'):
    validate_version(version)
    expected = {filename(mc, loader, version): (mc, loader) for mc, loader in TARGETS}
    selected = {}
    for jar in candidate_jars(root, source):
        if AUXILIARY.search(jar.name):
            continue
        require(jar.name in expected, f'Unexpected or stale distributable jar: {jar}')
        require(jar.name not in selected, f'Duplicate release target: {jar.name}')
        selected[jar.name] = jar
    missing = expected.keys() - selected.keys()
    require(not missing, 'Missing release jars: ' + ', '.join(sorted(missing)))
    artifacts = []
    for minecraft, loader in TARGETS:
        jar = selected[filename(minecraft, loader, version)]
        if not jar.is_relative_to(root / 'releases'):
            require(jar.parent == root / minecraft / loader / 'build/libs',
                    f'Jar is in the wrong Minecraft/loader build directory: {jar}')
        artifacts.append({'path': str(jar), 'minecraft': minecraft, 'loader': loader})
    return artifacts


def expected_recipe(minecraft):
    key = INGREDIENTS if minecraft == '26.2' else {
        symbol: {'item': item} for symbol, item in INGREDIENTS.items()
    }
    result_field = 'item' if minecraft == '1.20.1' else 'id'
    return {
        'type': 'minecraft:crafting_shaped', 'pattern': [' R ', 'DTC', '   '],
        'key': key, 'result': {result_field: 'simple_terminals:crafting_terminal', 'count': 1},
    }


def expected_loot():
    return {'type': 'minecraft:block', 'pools': [{
        'rolls': 1.0, 'bonus_rolls': 0.0,
        'entries': [{'type': 'minecraft:item', 'name': 'simple_terminals:crafting_terminal'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}],
    }]}


def manifest_fields(text):
    unfolded = re.sub(r'\r?\n ', '', text)
    fields = {}
    for line in unfolded.splitlines():
        if not line:
            break  # Only the main manifest section describes the mod jar.
        key, separator, value = line.partition(': ')
        require(separator and key not in fields, 'Invalid or duplicate main manifest field')
        fields[key] = value
    return fields


def validate_jar(artifact, version):
    minecraft, loader = artifact['minecraft'], artifact['loader']
    path = Path(artifact['path'])
    try:
        # Validate and hash the same byte snapshot, even if the file later changes.
        payload = path.read_bytes()
        with zipfile.ZipFile(io.BytesIO(payload)) as jar:
            names = jar.namelist()
            require(len(names) == len(set(names)), 'Duplicate entries inside jar')
            metadata = [name for name in METADATA.values() if name in names]
            require(metadata == [METADATA[loader]], f'Wrong loader metadata for {loader}')
            for directory, matcher, value in (
                ('recipes' if minecraft == '1.20.1' else 'recipe',
                 r'data/[^/]+/(?:recipe|recipes)/crafting_terminal\.json', expected_recipe(minecraft)),
                ('loot_tables' if minecraft == '1.20.1' else 'loot_table',
                 r'data/[^/]+/(?:loot_table|loot_tables)/blocks/crafting_terminal\.json', expected_loot()),
            ):
                middle = 'blocks/' if directory.startswith('loot') else ''
                resource = f'data/simple_terminals/{directory}/{middle}crafting_terminal.json'
                require([name for name in names if re.fullmatch(matcher, name)] == [resource],
                        f'Wrong, missing, or obsolete terminal resource location: {resource}')
                actual = json.loads(jar.read(resource))
                require(actual == value, f'Wrong terminal resource schema/content: {resource}')
                if directory.startswith('recipe'):
                    require(type(actual['result']['count']) is int, 'Recipe count must be an integer')
                else:
                    pool = actual['pools'][0]
                    require(all(type(pool[field]) in (int, float) for field in ('rolls', 'bonus_rolls')),
                            'Loot rolls must be numeric, not boolean')
            if minecraft == '26.2':
                pack = json.loads(jar.read('pack.mcmeta'))['pack']
                require('pack_format' not in pack, '26.2 pack must use min_format/max_format')
                bounds = [pack.get('min_format'), pack.get('max_format')]
                require(all(isinstance(bound, list) and len(bound) == 2 and
                            all(type(component) is int and component >= 0 for component in bound)
                            for bound in bounds), 'Invalid 26.2 pack format bounds')
                minimum, maximum = map(tuple, bounds)
                require(minimum <= (88, 0) and maximum >= (107, 1),
                        '26.2 pack excludes resource 88.0 or data 107.1')
            if loader == 'fabric':
                mod = json.loads(jar.read(METADATA[loader]))
                require(mod.get('schemaVersion') == 1 and mod.get('id') == 'simple_terminals',
                        'Wrong Fabric mod identity')
                require(mod.get('version') == version, 'Wrong Fabric mod version')
                require(mod.get('depends', {}).get('minecraft') == minecraft, 'Wrong Fabric Minecraft version')
                require('fabricloader' in mod.get('depends', {}), 'Missing Fabric loader dependency')
                require(mod.get('entrypoints', {}).get('main') == ['com.nightbeam.simpleterminals.SimpleTerminals'],
                        'Wrong Fabric entrypoint')
            else:
                mod = tomllib.loads(jar.read(METADATA[loader]).decode())
                require(mod.get('modLoader') == 'javafml', 'Wrong FML mod loader')
                mods = mod.get('mods', [])
                require(len(mods) == 1 and mods[0].get('modId') == 'simple_terminals', 'Wrong FML mod identity')
                require(mods[0].get('version') == version, 'Wrong FML mod version')
                dependencies = mod.get('dependencies', {}).get('simple_terminals', [])
                by_id = {dependency.get('modId'): dependency for dependency in dependencies}
                require(len(by_id) == len(dependencies), 'Duplicate FML dependency')
                require(loader in by_id and by_id[loader].get('versionRange'), f'Missing {loader} dependency')
                require(by_id.get('minecraft', {}).get('versionRange') == MINECRAFT_RANGES[minecraft],
                        'Wrong FML Minecraft version range')
            manifest = manifest_fields(jar.read('META-INF/MANIFEST.MF').decode())
            require(manifest.get('Implementation-Version') == version, 'Wrong manifest mod version')
            require(manifest.get('Built-On-Minecraft') == minecraft, 'Wrong manifest Minecraft version')
            require(manifest.get('Implementation-Title') == loader, 'Wrong manifest loader')
            for class_name in ('SimpleTerminals', 'block/CraftingTerminalBlock', 'registry/ModBlocks', 'registry/ModItems'):
                resource = f'com/nightbeam/simpleterminals/{class_name}.class'
                require(jar.read(resource).startswith(b'\xca\xfe\xba\xbe'), f'Missing compiled mod class: {resource}')
        return {algorithm: hashlib.new(algorithm, payload).hexdigest()
                for algorithm in ('sha256', 'sha1', 'sha512')}
    except (KeyError, TypeError, AttributeError, UnicodeDecodeError, json.JSONDecodeError,
            tomllib.TOMLDecodeError, zipfile.BadZipFile, OSError, ReleaseError) as error:
        raise ReleaseError(f'{path}: {error}') from error


def release_notes(changelog, version):
    validate_version(version)
    lines = changelog.splitlines()
    headings = [index for index, line in enumerate(lines) if re.fullmatch(r'##\s+' + re.escape(version) + r'\s*', line)]
    require(len(headings) == 1, f'Expected exactly one CHANGELOG.md section for {version}')
    start = headings[0] + 1
    end = next((index for index in range(start, len(lines)) if re.match(r'##\s+', lines[index])), len(lines))
    notes = '\n'.join(lines[start:end]).strip()
    require(notes, f'Empty CHANGELOG.md section for {version}')
    return notes + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('.'))
    parser.add_argument('--version', required=True)
    parser.add_argument('--source', choices=('auto', 'release', 'build'), default='auto')
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--notes', type=Path, required=True)
    args = parser.parse_args()
    try:
        artifacts = select_artifacts(args.root, args.version, args.source)
        for artifact in artifacts:
            artifact['hashes'] = validate_jar(artifact, args.version)
        notes = release_notes((args.root / 'CHANGELOG.md').read_text(), args.version)
        args.manifest.write_text(json.dumps({'version': args.version, 'artifacts': artifacts}, indent=2) + '\n')
        args.notes.write_text(notes)
        print(f'Validated all {len(artifacts)} release jars for {args.version}:')
        for artifact in artifacts:
            print(artifact['path'] + ' ' + ' '.join(f'{algorithm}={digest}' for algorithm, digest in artifact['hashes'].items()))
    except (ReleaseError, OSError) as error:
        print(f'Release blocked: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
