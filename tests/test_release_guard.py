"""Release guard behaviour on synthetic jars. Run: python3 -m unittest discover -s tests -v"""
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('release_guard', ROOT / 'scripts' / 'release_guard.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

VERSION = '2.0.0'
TOML = '''modLoader = "javafml"
loaderVersion = "[4,)"
license = "Apache-2.0"
[[mods]]
modId = "omnivault"
version = "2.0.0"
displayName = "OmniVault"
[[dependencies.omnivault]]
modId = "neoforge"
type = "required"
versionRange = "[21.1.80,)"
[[dependencies.omnivault]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1, 1.22)"
'''
MANIFEST = 'Manifest-Version: 1.0\nImplementation-Version: 2.0.0\nBuilt-On-Minecraft: 1.21.1\nImplementation-Title: neoforge\n\n'


def entries(**overrides):
    files = {
        'META-INF/neoforge.mods.toml': TOML,
        'META-INF/MANIFEST.MF': MANIFEST,
        'assets/omnivault/lang/en_us.json': json.dumps({'block.omnivault.drive': 'Cell Drive', 'item.omnivault.omni_tool': 'OmniTool'}),
        'assets/omnivault/blockstates/drive.json': '{"variants": {}}',
        'assets/omnivault/models/item/drive.json': '{}',
        'assets/omnivault/models/item/omni_tool.json': '{}',
        'data/omnivault/loot_table/blocks/drive.json': '{}',
        'data/omnivault/recipe/drive.json': '{}',
        'pack.mcmeta': '{"pack": {"pack_format": 34}}',
    }
    for name in guard.REQUIRED_CLASSES:
        files[name] = b'\xca\xfe\xba\xbe'
    for name in guard.REQUIRED_SERVICES:
        files[name] = 'com.example.Impl\n'
    files.update(overrides)
    return {name: value for name, value in files.items() if value is not None}


def write_jar(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as jar:
        for name, value in files.items():
            jar.writestr(name, value)
    return path


class ReleaseGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.libs = self.root / '1.21.1' / 'neoforge' / 'build' / 'libs'

    def tearDown(self):
        self.tmp.cleanup()

    def build(self, **overrides):
        return write_jar(self.libs / guard.filename('1.21.1', 'neoforge', VERSION), entries(**overrides))

    def validate(self):
        artifacts = guard.select_artifacts(self.root, VERSION, 'build')
        return [guard.validate_jar(artifact, VERSION) for artifact in artifacts]

    def test_valid_jar_passes_and_is_hashed(self):
        self.build()
        write_jar(self.libs / 'omnivault-1.21.1-neoforge-2.0.0-sources.jar', {'x': 'y'})
        hashes = self.validate()
        self.assertEqual(len(hashes), 1)
        self.assertEqual(set(hashes[0]), {'sha256', 'sha1', 'sha512'})

    def test_missing_jar_blocks(self):
        with self.assertRaisesRegex(guard.ReleaseError, 'Missing release jars'):
            self.validate()

    def test_stale_jar_blocks(self):
        self.build()
        write_jar(self.libs / 'omnivault-1.21.1-neoforge-1.9.0.jar', entries())
        with self.assertRaisesRegex(guard.ReleaseError, 'stale'):
            self.validate()

    def test_wrong_mod_id_blocks(self):
        self.build(**{'META-INF/neoforge.mods.toml': TOML.replace('modId = "omnivault"', 'modId = "simple_terminals"')})
        with self.assertRaisesRegex(guard.ReleaseError, 'identity'):
            self.validate()

    def test_wrong_version_blocks(self):
        self.build(**{'META-INF/neoforge.mods.toml': TOML.replace('version = "2.0.0"', 'version = "2.0.1"')})
        with self.assertRaisesRegex(guard.ReleaseError, 'version'):
            self.validate()

    def test_block_without_loot_table_blocks(self):
        self.build(**{'data/omnivault/loot_table/blocks/drive.json': None})
        with self.assertRaisesRegex(guard.ReleaseError, 'loot_table'):
            self.validate()

    def test_item_without_model_blocks(self):
        self.build(**{'assets/omnivault/models/item/omni_tool.json': None})
        with self.assertRaisesRegex(guard.ReleaseError, 'item model'):
            self.validate()

    def test_gametests_must_not_ship(self):
        self.build(**{'com/nightbeam/omnivault/gametest/NetworkGameTests.class': b'\xca\xfe\xba\xbe'})
        with self.assertRaisesRegex(guard.ReleaseError, 'GameTest'):
            self.validate()

    def test_invalid_json_blocks(self):
        self.build(**{'data/omnivault/recipe/drive.json': '{not json'})
        with self.assertRaises(guard.ReleaseError):
            self.validate()

    def test_release_notes_are_version_specific(self):
        changelog = '# Changelog\n\n## 2.0.0\n\n* New\n\n## 1.1.1\n\n* Old\n'
        self.assertEqual(guard.release_notes(changelog, '2.0.0'), '* New\n')
        with self.assertRaises(guard.ReleaseError):
            guard.release_notes(changelog, '3.0.0')


if __name__ == '__main__':
    unittest.main()
