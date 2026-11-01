#!/usr/bin/env python3
"""Fail-closed OmniVault release selection and packaged-jar checks.

Requires Python 3.11+ (stdlib only). This script never publishes or starts Minecraft.

    python3 scripts/release_guard.py --version 2.0.0 --manifest .release-manifest.json --notes .release-notes.md
    python3 scripts/release_guard.py --version 2.0.0 --minecraft 1.20.1,26.2 ...   # only these Minecraft versions
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

MOD_ID = 'omnivault'
TARGETS = (
    ('1.20.1', 'fabric'), ('1.20.1', 'forge'),
    ('1.21.1', 'fabric'), ('1.21.1', 'neoforge'),
    ('26.2', 'fabric'), ('26.2', 'neoforge'),
    ('26.3', 'fabric'), ('26.3', 'neoforge'),
)
METADATA = {'fabric': 'fabric.mod.json', 'forge': 'META-INF/mods.toml', 'neoforge': 'META-INF/neoforge.mods.toml'}
# The Minecraft dependency each loader's metadata must declare.
MINECRAFT_RANGES = {
    'forge': {'1.20.1': '[1.20.1,1.20.2)'},
    'neoforge': {'1.21.1': '[1.21.1, 1.22)', '26.2': '[26.2, 26.3)', '26.3': '[26.3, 26.4)'},
    'fabric': {'1.20.1': '1.20.1', '1.21.1': '~1.21.1', '26.2': '~26.2', '26.3': '~26.3'},
}
REQUIRED_CLASSES = (
    'com/nightbeam/omnivault/OmniVault.class',
    'com/nightbeam/omnivault/OmniVaultCommon.class',
    'com/nightbeam/omnivault/network/StorageNetwork.class',
)
LOADER_CLASSES = {
    'fabric': (
        'com/nightbeam/omnivault/fabric/OmniVaultFabric.class',
        'com/nightbeam/omnivault/fabric/client/OmniVaultFabricClient.class',
    ),
    'forge': ('com/nightbeam/omnivault/forge/OmniVaultForge.class',),
    'neoforge': ('com/nightbeam/omnivault/neoforge/OmniVaultNeoForge.class',),
}
REQUIRED_SERVICES = (
    'META-INF/services/com.nightbeam.omnivault.platform.services.IPlatformHelper',
    'META-INF/services/com.nightbeam.omnivault.platform.services.IRegistryHelper',
    'META-INF/services/com.nightbeam.omnivault.client.ClientPlatform',
)
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
    return f'{MOD_ID}-{minecraft}-{loader}-{version}.jar'


def minecraft_key(minecraft):
    return tuple(int(part) for part in minecraft.split('.'))


def layout(minecraft):
    """Where each Minecraft version keeps the resources the checks look for."""
    key = minecraft_key(minecraft)
    return {
        # 1.21 renamed the plural data folders.
        'recipes': 'recipes' if key < (1, 21) else 'recipe',
        'loot': 'loot_tables' if key < (1, 21) else 'loot_table',
        'obsolete': 'recipe|loot_table' if key < (1, 21) else 'recipes|loot_tables',
        # 1.21.4 moved item models behind item model definitions in assets/<mod>/items.
        'item_model': 'items/{}.json' if key >= (1, 21, 4) else 'models/item/{}.json',
        # From 26.1 Minecraft ships unobfuscated, so Fabric mixins need no refmap.
        'refmap': key < (26, 1),
    }


def select_targets(minecraft=None):
    """All release targets, or those for the given Minecraft versions."""
    if minecraft is None:
        return TARGETS
    known = dict.fromkeys(mc for mc, _ in TARGETS)
    unknown = [mc for mc in minecraft if mc not in known]
    require(minecraft and not unknown, 'Unknown Minecraft version: ' + ', '.join(unknown or ['(none)']))
    return tuple(target for target in TARGETS if target[0] in minecraft)


def candidate_jars(root, source='auto', targets=TARGETS):
    release = sorted((root / 'releases').rglob('*.jar'))
    if source == 'release' or (source == 'auto' and release):
        # Even a partial or stale prebuilt directory must fail, never fall back to a build.
        return release
    require(source in ('auto', 'build'), f'Unknown artifact source: {source}')
    candidates = []
    for minecraft in dict.fromkeys(mc for mc, _ in targets):
        for jar in (root / minecraft).glob('*/build/libs/*.jar'):
            if jar.parent.parent.parent.name not in ('common', 'buildSrc', 'build-logic'):
                candidates.append(jar)
    return sorted(candidates)


def select_artifacts(root, version, source='auto', minecraft=None):
    validate_version(version)
    targets = select_targets(minecraft)
    expected = {filename(mc, loader, version): (mc, loader) for mc, loader in targets}
    selected = {}
    for jar in candidate_jars(root, source, targets):
        if AUXILIARY.search(jar.name):
            continue
        require(jar.name in expected, f'Unexpected or stale distributable jar: {jar}')
        require(jar.name not in selected, f'Duplicate release target: {jar.name}')
        selected[jar.name] = jar
    missing = expected.keys() - selected.keys()
    require(not missing, 'Missing release jars: ' + ', '.join(sorted(missing)))
    artifacts = []
    for minecraft, loader in targets:
        jar = selected[filename(minecraft, loader, version)]
        if not jar.is_relative_to(root / 'releases'):
            require(jar.parent == root / minecraft / loader / 'build/libs',
                    f'Jar is in the wrong Minecraft/loader build directory: {jar}')
        artifacts.append({'path': str(jar), 'minecraft': minecraft, 'loader': loader})
    return artifacts


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


def validate_resources(jar, names, minecraft):
    """Every block in the lang file has a blockstate, an item model and a loot table; every JSON parses."""
    paths = layout(minecraft)
    item_model = f'assets/{MOD_ID}/' + paths['item_model']
    lang_name = f'assets/{MOD_ID}/lang/en_us.json'
    require(lang_name in names, 'Missing en_us.json')
    lang = json.loads(jar.read(lang_name))
    blocks = sorted(key.split('.')[2] for key in lang if re.fullmatch(rf'block\.{MOD_ID}\.[a-z0-9_]+', key))
    require(blocks, 'No blocks found in en_us.json')
    for block in blocks:
        for resource in (f'assets/{MOD_ID}/blockstates/{block}.json',
                         item_model.format(block),
                         f'data/{MOD_ID}/{paths["loot"]}/blocks/{block}.json'):
            require(resource in names, f'Block {block} is missing {resource}')
    items = sorted(key.split('.')[2] for key in lang if re.fullmatch(rf'item\.{MOD_ID}\.[a-z0-9_]+', key))
    for item in items:
        require(item_model.format(item) in names, f'Item {item} has no item model')
    recipes = [name for name in names if name.startswith(f'data/{MOD_ID}/{paths["recipes"]}/') and name.endswith('.json')]
    require(recipes, 'No recipes packaged')
    require(not any(re.match(rf'data/{MOD_ID}/({paths["obsolete"]})/', name) for name in names),
            'Data directories from another Minecraft version packaged')
    for name in names:
        if name.endswith('.json') or name.endswith('.mcmeta'):
            json.loads(jar.read(name))
    require(not any(name.startswith('com/nightbeam/omnivault/gametest/') for name in names), 'GameTest classes packaged')
    require(not any(re.match(rf'data/{MOD_ID}/structure/', name) for name in names), 'GameTest structures packaged')


def validate_fml_metadata(jar, loader, minecraft, version):
    """Forge's mods.toml and NeoForge's neoforge.mods.toml share one layout."""
    mod = tomllib.loads(jar.read(METADATA[loader]).decode())
    require(mod.get('modLoader') == 'javafml', 'Wrong FML mod loader')
    mods = mod.get('mods', [])
    require(len(mods) == 1 and mods[0].get('modId') == MOD_ID, 'Wrong FML mod identity')
    require(mods[0].get('version') == version, 'Wrong FML mod version')
    dependencies = mod.get('dependencies', {}).get(MOD_ID, [])
    by_id = {dependency.get('modId'): dependency for dependency in dependencies}
    require(len(by_id) == len(dependencies), 'Duplicate FML dependency')
    require(loader in by_id and by_id[loader].get('versionRange'), f'Missing {loader} dependency')
    require(by_id.get('minecraft', {}).get('versionRange') == MINECRAFT_RANGES[loader][minecraft],
            'Wrong FML Minecraft version range')


def class_file(name):
    return name.replace('.', '/') + '.class'


def validate_fabric_metadata(jar, names, minecraft, version):
    mod = json.loads(jar.read(METADATA['fabric']))
    require(mod.get('schemaVersion') == 1, 'Wrong fabric.mod.json schema')
    require(mod.get('id') == MOD_ID, 'Wrong Fabric mod identity')
    require(mod.get('version') == version, 'Wrong Fabric mod version')
    depends = mod.get('depends', {})
    require(depends.get('minecraft') == MINECRAFT_RANGES['fabric'][minecraft], 'Wrong Fabric Minecraft dependency')
    require('fabricloader' in depends and 'fabric-api' in depends, 'Missing Fabric Loader or Fabric API dependency')
    entrypoints = mod.get('entrypoints', {})
    require(entrypoints.get('main') and entrypoints.get('client'), 'Missing Fabric main or client entrypoint')
    require('fabric-gametest' not in entrypoints, 'GameTest entrypoint packaged')
    for kind in ('main', 'client'):
        for entry in entrypoints[kind]:
            target = entry if isinstance(entry, str) else entry.get('value', '')
            require(class_file(target) in names, f'Fabric {kind} entrypoint class missing: {target}')
    icon = mod.get('icon')
    require(isinstance(icon, str) and icon in names, 'Fabric mod icon missing')
    mixins = mod.get('mixins', [])
    require(mixins, 'No Fabric mixin configs')
    for config_name in mixins:
        config_name = config_name if isinstance(config_name, str) else config_name.get('config', '')
        require(config_name in names, f'Missing mixin config {config_name}')
        config = json.loads(jar.read(config_name))
        if layout(minecraft)['refmap']:
            refmap = config.get('refmap')
            require(isinstance(refmap, str) and refmap in names, f'Mixin config {config_name} has no packaged refmap')
        package = config.get('package', '').replace('.', '/')
        for mixin in config.get('mixins', []) + config.get('client', []) + config.get('server', []):
            require(f'{package}/{mixin}.class' in names, f'Mixin class missing: {mixin}')
    # The GameTests' stand-ins for (Neo)Forge annotations are test code only.
    require(not any(name.startswith(('net/neoforged/', 'net/minecraftforge/')) for name in names),
            'Forge or NeoForge test stand-ins packaged')


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
            if loader == 'fabric':
                validate_fabric_metadata(jar, names, minecraft, version)
            else:
                validate_fml_metadata(jar, loader, minecraft, version)
            manifest = manifest_fields(jar.read('META-INF/MANIFEST.MF').decode())
            require(manifest.get('Implementation-Version') == version, 'Wrong manifest mod version')
            require(manifest.get('Built-On-Minecraft') == minecraft, 'Wrong manifest Minecraft version')
            require(manifest.get('Implementation-Title') == loader, 'Wrong manifest loader')
            for resource in REQUIRED_CLASSES + LOADER_CLASSES[loader]:
                require(jar.read(resource).startswith(b'\xca\xfe\xba\xbe'), f'Missing compiled mod class: {resource}')
            for resource in REQUIRED_SERVICES:
                require(jar.read(resource).strip(), f'Empty service file: {resource}')
            validate_resources(jar, names, minecraft)
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
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--root', type=Path, default=Path('.'))
    parser.add_argument('--version', required=True)
    parser.add_argument('--source', choices=('auto', 'release', 'build'), default='auto')
    parser.add_argument('--minecraft', type=lambda value: [part.strip() for part in value.split(',') if part.strip()],
                        help='Comma-separated Minecraft versions to release (default: every target)')
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--notes', type=Path, required=True)
    args = parser.parse_args()
    try:
        artifacts = select_artifacts(args.root, args.version, args.source, args.minecraft)
        for artifact in artifacts:
            artifact['hashes'] = validate_jar(artifact, args.version)
        notes = release_notes((args.root / 'CHANGELOG.md').read_text(), args.version)
        args.manifest.write_text(json.dumps({'version': args.version, 'artifacts': artifacts}, indent=2) + '\n')
        args.notes.write_text(notes)
        print(f'Validated {len(artifacts)} release jar(s) for {args.version}:')
        for artifact in artifacts:
            print(artifact['path'] + ' ' + ' '.join(f'{algorithm}={digest}' for algorithm, digest in artifact['hashes'].items()))
    except (ReleaseError, OSError) as error:
        print(f'Release blocked: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
