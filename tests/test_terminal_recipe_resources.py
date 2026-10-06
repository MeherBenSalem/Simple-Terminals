"""Resource/packaging regressions; native crafting is tested separately.

Run: python3 -m unittest discover -s tests -v
Validate loader jars too: python3 tests/test_terminal_recipe_resources.py --jar 1.21.1:path/to.jar
Override source root for a baseline check: SIMPLE_TERMINALS_ROOT=/path/to/checkout
"""
import argparse
import json
import os
from pathlib import Path
import unittest
import zipfile

ROOT = Path(os.environ.get('SIMPLE_TERMINALS_ROOT', Path(__file__).resolve().parents[1]))
VERSIONS = ('1.20.1', '1.21.1', '26.2')
INGREDIENTS = {
    'R': 'minecraft:redstone_torch',
    'D': 'minecraft:diamond',
    'T': 'minecraft:crafting_table',
    'C': 'minecraft:chest',
}
JARS = []


def recipe_path(version):
    directory = 'recipes' if version == '1.20.1' else 'recipe'
    return f'data/simple_terminals/{directory}/crafting_terminal.json'


def loot_path(version):
    directory = 'loot_tables' if version == '1.20.1' else 'loot_table'
    return f'data/simple_terminals/{directory}/blocks/crafting_terminal.json'


def assert_loot(test, resource):
    test.assertEqual(resource['type'], 'minecraft:block')
    test.assertEqual(resource['pools'], [{
        'rolls': 1.0,
        'bonus_rolls': 0.0,
        'entries': [{'type': 'minecraft:item', 'name': 'simple_terminals:crafting_terminal'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}],
    }])


def assert_recipe(test, version, resource):
    test.assertEqual(resource['type'], 'minecraft:crafting_shaped')
    test.assertEqual(resource['pattern'], [' R ', 'DTC', '   '])
    expected_key = INGREDIENTS if version == '26.2' else {
        key: {'item': value} for key, value in INGREDIENTS.items()
    }
    test.assertEqual(resource['key'], expected_key)
    result_key = 'item' if version == '1.20.1' else 'id'
    test.assertEqual(resource['result'], {result_key: 'simple_terminals:crafting_terminal', 'count': 1})


class RecipeResources(unittest.TestCase):
    def test_consistent_patch_version(self):
        for version in VERSIONS:
            with self.subTest(version=version):
                properties = (ROOT / version / 'gradle.properties').read_text().splitlines()
                self.assertEqual([line for line in properties if line.startswith('version=')], ['version=1.1.1'])

    def test_recipe_discovery_locations(self):
        for version in VERSIONS:
            with self.subTest(version=version):
                resources = ROOT / version / 'common/src/main/resources'
                self.assertTrue((resources / recipe_path(version)).is_file(), 'Recipe must be discoverable by Minecraft')
                obsolete = 'recipe' if version == '1.20.1' else 'recipes'
                self.assertFalse((resources / f'data/simple_terminals/{obsolete}/crafting_terminal.json').exists())

    def test_version_specific_serializers(self):
        for version in VERSIONS:
            with self.subTest(version=version):
                path = ROOT / version / 'common/src/main/resources' / recipe_path(version)
                assert_recipe(self, version, json.loads(path.read_text()))

    def test_block_loot_discovery_and_content(self):
        for version in VERSIONS:
            with self.subTest(version=version):
                resources = ROOT / version / 'common/src/main/resources'
                path = resources / loot_path(version)
                self.assertTrue(path.is_file(), 'Block loot must be discoverable by Minecraft')
                assert_loot(self, json.loads(path.read_text()))
                obsolete = 'loot_table' if version == '1.20.1' else 'loot_tables'
                self.assertFalse((resources / f'data/simple_terminals/{obsolete}/blocks/crafting_terminal.json').exists())

    def test_modern_pack_accepts_data_and_resources(self):
        pack = json.loads((ROOT / '26.2/common/src/main/resources/pack.mcmeta').read_text())['pack']
        # Official 26.2 version.json: resource 88.0 and data 107.1.
        self.assertNotIn('pack_format', pack)
        minimum, maximum = tuple(pack['min_format']), tuple(pack['max_format'])
        for current in ((88, 0), (107, 1)):
            self.assertLessEqual(minimum, current)
            self.assertGreaterEqual(maximum, current)

    def test_loader_jars(self):
        if not JARS:
            self.skipTest("No built loader jars supplied; packaging was not tested")
        for version, path in JARS:
            with self.subTest(version=version, jar=path):
                with zipfile.ZipFile(path) as jar:
                    expected = recipe_path(version)
                    self.assertEqual(jar.namelist().count(expected), 1)
                    assert_recipe(self, version, json.loads(jar.read(expected)))
                    obsolete = 'recipe' if version == '1.20.1' else 'recipes'
                    self.assertNotIn(f'data/simple_terminals/{obsolete}/crafting_terminal.json', jar.namelist())
                    expected_loot = loot_path(version)
                    self.assertEqual(jar.namelist().count(expected_loot), 1)
                    assert_loot(self, json.loads(jar.read(expected_loot)))
                    old_loot = 'loot_table' if version == '1.20.1' else 'loot_tables'
                    self.assertNotIn(f'data/simple_terminals/{old_loot}/blocks/crafting_terminal.json', jar.namelist())
                    if version == '26.2':
                        pack = json.loads(jar.read('pack.mcmeta'))['pack']
                        self.assertNotIn('pack_format', pack)
                        self.assertLessEqual(tuple(pack['min_format']), (88, 0))
                        self.assertGreaterEqual(tuple(pack['max_format']), (107, 1))
                    # Check the artifact is actually a loader jar, not a test fixture.
                    self.assertTrue(any(name in jar.namelist() for name in (
                        'fabric.mod.json', 'META-INF/mods.toml', 'META-INF/neoforge.mods.toml')))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', action='append', default=[], metavar='VERSION:PATH')
    args, rest = parser.parse_known_args()
    for argument in args.jar:
        version, path = argument.split(':', 1)
        if version not in VERSIONS:
            parser.error(f'Unsupported Minecraft version: {version}')
        JARS.append((version, path))
    unittest.main(argv=[__file__, *rest])
