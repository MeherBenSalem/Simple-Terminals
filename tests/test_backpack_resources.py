"""Resource consistency checks for the backpack feature (run from the repository root:
python3 -m unittest discover -s tests)."""
import json
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / '1.21.1' / 'common' / 'src' / 'main' / 'resources'
ASSETS = RESOURCES / 'assets' / 'omnivault'
DATA = RESOURCES / 'data' / 'omnivault'

TIERS = ['leather', 'copper', 'iron', 'gold', 'diamond', 'netherite']
UPGRADES = ['pickup', 'magnet', 'void', 'feeding', 'restock', 'compacting', 'crafting', 'everlasting', 'deposit']


def load(path: Path):
    return json.loads(path.read_text(encoding='utf-8'))


def model_path(ref: str) -> Path | None:
    namespace, _, path = ref.partition(':') if ':' in ref else ('minecraft', '', ref)
    return ASSETS / 'models' / (path + '.json') if namespace == 'omnivault' else None


def resolved_textures(ref: str) -> tuple[dict, list]:
    """Textures and elements of a model after following its omnivault parents."""
    path = model_path(ref)
    if path is None:
        return {}, []
    data = load(path)
    textures, elements = resolved_textures(data['parent']) if 'parent' in data else ({}, [])
    textures = {**textures, **data.get('textures', {})}
    return textures, data.get('elements', elements)


class BackpackResources(unittest.TestCase):
    lang = load(ASSETS / 'lang' / 'en_us.json')

    def test_json_parses(self):
        for folder in (ASSETS, DATA):
            for path in folder.rglob('*.json'):
                with self.subTest(path=path.relative_to(ROOT)):
                    load(path)

    def assert_model_complete(self, ref):
        textures, elements = resolved_textures(ref)
        self.assertIn('particle', textures, ref)
        used = {face['texture'][1:] for element in elements for face in element['faces'].values()}
        for variable in used | {'particle'}:
            value = textures.get(variable)
            self.assertIsNotNone(value, f'{ref} leaves #{variable} unset')
            namespace, _, path = value.partition(':')
            self.assertEqual('omnivault', namespace, value)
            png = ASSETS / 'textures' / (path + '.png')
            self.assertTrue(png.exists(), f'{ref} references missing {png.relative_to(ROOT)}')
            with Image.open(png) as image:
                width, height = image.size
            self.assertEqual(16, width, png)
            if height != 16:
                self.assertTrue(png.with_suffix('.png.mcmeta').exists(), f'{png} is animated without .mcmeta')

    def test_backpacks(self):
        for tier in TIERS:
            name = f'{tier}_backpack'
            with self.subTest(tier=tier):
                state = load(ASSETS / 'blockstates' / (name + '.json'))
                self.assertEqual(4, len(state['variants']))
                for variant in state['variants'].values():
                    self.assert_model_complete(variant['model'])
                item = load(ASSETS / 'models' / 'item' / (name + '.json'))
                self.assert_model_complete(item['parent'])
                self.assertIn(f'block.omnivault.{name}', self.lang)
                self.assertIn(f'tooltip.omnivault.backpack.tier.{tier}', self.lang)
                loot = load(DATA / 'loot_table' / 'blocks' / (name + '.json'))
                self.assertEqual('minecraft:dynamic', loot['pools'][0]['entries'][0]['type'])
                self.assertTrue((DATA / 'recipe' / (name + '.json')).exists(), f'no recipe for {name}')

    def test_model_has_detail(self):
        _, elements = resolved_textures('omnivault:block/iron_backpack')
        self.assertGreaterEqual(len(elements), 25)
        self.assertTrue(any('neoforge_data' in element for element in elements), 'no emissive omnium gem')

    def test_upgrades(self):
        for upgrade in UPGRADES:
            name = f'{upgrade}_upgrade'
            with self.subTest(upgrade=upgrade):
                model = load(ASSETS / 'models' / 'item' / (name + '.json'))
                layer = model['textures']['layer0'].partition(':')[2]
                self.assertTrue((ASSETS / 'textures' / (layer + '.png')).exists())
                self.assertIn(f'item.omnivault.{name}', self.lang)
                self.assertIn(f'item.omnivault.{name}.tooltip', self.lang)
                self.assertTrue((DATA / 'recipe' / (name + '.json')).exists(), f'no recipe for {name}')

    def test_tags(self):
        dyeable = load(RESOURCES / 'data' / 'minecraft' / 'tags' / 'item' / 'dyeable.json')
        self.assertIn('omnivault:leather_backpack', dyeable['values'])
        backpacks = load(DATA / 'tags' / 'item' / 'backpacks.json')
        self.assertEqual({f'omnivault:{t}_backpack' for t in TIERS}, set(backpacks['values']))


if __name__ == '__main__':
    unittest.main()
