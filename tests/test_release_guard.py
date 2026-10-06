"""Synthetic six-artifact release guard regressions; no upload/network calls."""

import importlib.util
import hashlib
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/release_guard.py'
spec = importlib.util.spec_from_file_location('release_guard', SCRIPT)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
VERSION = '1.1.1'


def resources(minecraft, loader):
    directory = 'recipes' if minecraft == '1.20.1' else 'recipe'
    loot_directory = 'loot_tables' if minecraft == '1.20.1' else 'loot_table'
    files = {
        f'data/simple_terminals/{directory}/crafting_terminal.json': json.dumps(guard.expected_recipe(minecraft)),
        f'data/simple_terminals/{loot_directory}/blocks/crafting_terminal.json': json.dumps(guard.expected_loot()),
        'META-INF/MANIFEST.MF': (
            'Manifest-Version: 1.0\n'
            f'Implementation-Version: {VERSION}\n'
            f'Implementation-Title: {loader}\n'
            f'Built-On-Minecraft: {minecraft}\n\n'
        ),
    }
    if minecraft == '26.2':
        files['pack.mcmeta'] = json.dumps({'pack': {'min_format': [88, 0], 'max_format': [107, 1]}})
    if loader == 'fabric':
        files[guard.METADATA[loader]] = json.dumps({
            'schemaVersion': 1, 'id': 'simple_terminals', 'version': VERSION,
            'depends': {'minecraft': minecraft, 'fabricloader': '>=0.16.9'},
            'entrypoints': {'main': ['com.nightbeam.simpleterminals.SimpleTerminals']},
        })
    else:
        files[guard.METADATA[loader]] = (
            'modLoader = "javafml"\n[[mods]]\nmodId = "simple_terminals"\n'
            f'version = "{VERSION}"\n'
            f'[[dependencies.simple_terminals]]\nmodId = "{loader}"\nversionRange = "[4,)"\n'
            '[[dependencies.simple_terminals]]\nmodId = "minecraft"\n'
            f'versionRange = "{guard.MINECRAFT_RANGES[minecraft]}"\n'
        )
    for name in ('SimpleTerminals', 'block/CraftingTerminalBlock', 'registry/ModBlocks', 'registry/ModItems'):
        files[f'com/nightbeam/simpleterminals/{name}.class'] = b'\xca\xfe\xba\xbe\x00\x00\x00\x41'
    return files


def write_jar(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as jar:
        for name, value in files.items():
            jar.writestr(name, value)


class ReleaseGuardTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.changelog = '# Changelog\n\n## 1.1.1\n\n### Fixes\nCurrent fix.\n\n## 1.1.0\n\nOld fix.\n'
        (self.root / 'CHANGELOG.md').write_text(self.changelog)

    def six_jars(self, source='release'):
        artifacts = []
        for minecraft, loader in guard.TARGETS:
            parent = self.root / ('releases' if source == 'release' else f'{minecraft}/{loader}/build/libs')
            path = parent / guard.filename(minecraft, loader, VERSION)
            write_jar(path, resources(minecraft, loader))
            artifacts.append({'path': str(path), 'minecraft': minecraft, 'loader': loader})
        return artifacts

    def assert_invalid(self, artifact, files):
        write_jar(Path(artifact['path']), files)
        with self.assertRaises(guard.ReleaseError):
            guard.validate_jar(artifact, VERSION)

    def test_valid_six_prebuilt_jars(self):
        expected = self.six_jars()
        artifacts = guard.select_artifacts(self.root, VERSION)
        self.assertEqual(artifacts, expected)
        for artifact in artifacts:
            guard.validate_jar(artifact, VERSION)

    def test_valid_six_built_jars(self):
        expected = self.six_jars('build')
        self.assertEqual(guard.select_artifacts(self.root, VERSION), expected)

    def test_missing_each_target_is_rejected(self):
        for target in guard.TARGETS:
            with self.subTest(target=target):
                artifacts = self.six_jars()
                Path(next(a['path'] for a in artifacts if (a['minecraft'], a['loader']) == target)).unlink()
                with self.assertRaisesRegex(guard.ReleaseError, 'Missing'):
                    guard.select_artifacts(self.root, VERSION)

    def test_stale_unexpected_wrong_loader_and_common_jars_are_rejected(self):
        self.six_jars()
        for name in ('simple-terminals-1.20.1-fabric-1.1.0.jar', 'unknown.jar',
                     'simple-terminals-26.2-forge-1.1.1.jar', 'simple-terminals-26.2-common-1.1.1.jar'):
            with self.subTest(name=name):
                path = self.root / 'releases' / name
                path.write_bytes(b'wrong')
                with self.assertRaisesRegex(guard.ReleaseError, 'Unexpected or stale'):
                    guard.select_artifacts(self.root, VERSION)
                path.unlink()

    def test_duplicate_target_is_rejected(self):
        artifacts = self.six_jars()
        duplicate = self.root / 'releases/duplicate' / Path(artifacts[0]['path']).name
        duplicate.parent.mkdir()
        duplicate.write_bytes(Path(artifacts[0]['path']).read_bytes())
        with self.assertRaisesRegex(guard.ReleaseError, 'Duplicate'):
            guard.select_artifacts(self.root, VERSION)

    def test_build_directory_must_match_minecraft_and_loader(self):
        artifacts = self.six_jars('build')
        old = Path(artifacts[0]['path'])
        new = self.root / '26.2/fabric/build/libs' / old.name
        old.rename(new)
        with self.assertRaisesRegex(guard.ReleaseError, 'wrong Minecraft/loader build directory'):
            guard.select_artifacts(self.root, VERSION)

    def test_auxiliary_build_outputs_are_not_distributable(self):
        expected = self.six_jars('build')
        for minecraft, loader in guard.TARGETS:
            parent = self.root / minecraft / loader / 'build/libs'
            for suffix in ('sources', 'javadoc', 'dev', 'dev-shadow'):
                (parent / f'simple-terminals-{minecraft}-{loader}-{VERSION}-{suffix}.jar').write_bytes(b'auxiliary')
            common = self.root / minecraft / 'common/build/libs/common.jar'
            common.parent.mkdir(parents=True, exist_ok=True)
            common.write_bytes(b'common')
        self.assertEqual(guard.select_artifacts(self.root, VERSION), expected)

    def test_partial_or_stale_prebuilt_set_never_falls_back_to_valid_build(self):
        self.six_jars('build')
        release = self.root / 'releases'
        release.mkdir()
        for name in ('simple-terminals-1.20.1-fabric-1.1.0.jar', 'only-sources.jar'):
            with self.subTest(name=name):
                path = release / name
                path.write_bytes(b'wrong')
                with self.assertRaises(guard.ReleaseError):
                    guard.select_artifacts(self.root, VERSION)
                path.unlink()

    def test_invalid_version_is_rejected(self):
        for version in ('', 'v1.1.1', '1.1', '1.1.1\nmalicious', '../1.1.1', '$(echo bad)'):
            with self.subTest(version=version), self.assertRaises(guard.ReleaseError):
                guard.select_artifacts(self.root, version)

    def test_packaged_resources_reject_wrong_directory_namespace_schema_and_duplicates(self):
        for artifact in self.six_jars():
            minecraft, loader = artifact['minecraft'], artifact['loader']
            baseline = resources(minecraft, loader)
            for kind in ('recipe', 'loot'):
                resource = next(n for n in baseline if n.startswith('data/') and
                                ('/blocks/' in n) == (kind == 'loot'))
                current = resource.split('/')[2]
                old = {'recipes': 'recipe', 'recipe': 'recipes', 'loot_tables': 'loot_table', 'loot_table': 'loot_tables'}[current]
                for fault in ('missing', 'old_directory', 'duplicate_old', 'wrong_namespace', 'duplicate_namespace', 'wrong_content', 'invalid_json'):
                    with self.subTest(minecraft=minecraft, loader=loader, kind=kind, fault=fault):
                        files = baseline.copy()
                        if fault == 'missing':
                            del files[resource]
                        elif fault == 'old_directory':
                            files[resource.replace('/' + current + '/', '/' + old + '/')] = files.pop(resource)
                        elif fault == 'duplicate_old':
                            files[resource.replace('/' + current + '/', '/' + old + '/')] = files[resource]
                        elif fault == 'wrong_namespace':
                            files[resource.replace('/simple_terminals/', '/wrong/')] = files.pop(resource)
                        elif fault == 'duplicate_namespace':
                            files[resource.replace('/simple_terminals/', '/wrong/')] = files[resource]
                        elif fault == 'invalid_json':
                            files[resource] = '{'
                        else:
                            value = json.loads(files[resource])
                            if kind == 'recipe':
                                value['result'] = {'item': 'simple_terminals:crafting_terminal', 'count': 2}
                            else:
                                value['pools'][0]['entries'][0]['name'] = 'minecraft:air'
                            files[resource] = json.dumps(value)
                        self.assert_invalid(artifact, files)

    def test_version_specific_recipe_serializers_are_enforced(self):
        for artifact in self.six_jars():
            minecraft, loader = artifact['minecraft'], artifact['loader']
            for fault in ('ingredient', 'result', 'pattern'):
                with self.subTest(minecraft=minecraft, loader=loader, fault=fault):
                    files = resources(minecraft, loader)
                    resource = next(n for n in files if n.startswith('data/') and '/blocks/' not in n)
                    recipe = json.loads(files[resource])
                    if fault == 'ingredient':
                        recipe['key']['R'] = {'item': guard.INGREDIENTS['R']} if minecraft == '26.2' else guard.INGREDIENTS['R']
                    elif fault == 'result':
                        recipe['result'] = {'id' if minecraft == '1.20.1' else 'item': 'simple_terminals:crafting_terminal', 'count': 1}
                    else:
                        recipe['pattern'] = ['RDC']
                    files[resource] = json.dumps(recipe)
                    self.assert_invalid(artifact, files)

    def test_boolean_recipe_count_and_loot_rolls_are_rejected(self):
        artifact = self.six_jars()[0]
        for kind in ('recipe', 'loot'):
            files = resources(artifact['minecraft'], artifact['loader'])
            resource = next(n for n in files if n.startswith('data/') and
                            ('/blocks/' in n) == (kind == 'loot'))
            value = json.loads(files[resource])
            if kind == 'recipe':
                value['result']['count'] = True
            else:
                value['pools'][0]['rolls'] = True
            files[resource] = json.dumps(value)
            self.assert_invalid(artifact, files)

    def test_modern_pack_metadata_is_required_and_bounds_are_checked(self):
        for artifact in self.six_jars():
            if artifact['minecraft'] != '26.2':
                continue
            for pack in (None, {'pack_format': 64}, {'min_format': [89, 0], 'max_format': [107, 1]},
                         {'min_format': [88, 0], 'max_format': [107, 0]},
                         {'min_format': 88, 'max_format': 107}, {'min_format': [False, 0], 'max_format': [107, 1]}):
                with self.subTest(loader=artifact['loader'], pack=pack):
                    files = resources('26.2', artifact['loader'])
                    if pack is None:
                        del files['pack.mcmeta']
                    else:
                        files['pack.mcmeta'] = json.dumps({'pack': pack})
                    self.assert_invalid(artifact, files)

    def test_wrong_missing_extra_and_unexpanded_loader_metadata_is_rejected(self):
        for artifact in self.six_jars():
            minecraft, loader = artifact['minecraft'], artifact['loader']
            for fault in ('missing', 'wrong_loader', 'extra_loader', 'version', 'unexpanded', 'mod_id', 'minecraft', 'dependency', 'entrypoint_or_loader', 'invalid'):
                with self.subTest(minecraft=minecraft, loader=loader, fault=fault):
                    files = resources(minecraft, loader)
                    metadata = guard.METADATA[loader]
                    if fault == 'missing':
                        del files[metadata]
                    elif fault in ('wrong_loader', 'extra_loader'):
                        other = next(name for name in guard.METADATA.values() if name != metadata)
                        files[other] = files[metadata]
                        if fault == 'wrong_loader':
                            del files[metadata]
                    elif loader == 'fabric':
                        value = json.loads(files[metadata])
                        if fault in ('version', 'unexpanded'):
                            value['version'] = '1.1.0' if fault == 'version' else '${version}'
                        elif fault == 'mod_id':
                            value['id'] = 'wrong'
                        elif fault == 'minecraft':
                            value['depends']['minecraft'] = '1.19.4'
                        elif fault == 'dependency':
                            del value['depends']['fabricloader']
                        elif fault == 'entrypoint_or_loader':
                            value['entrypoints']['main'] = ['wrong.Main']
                        files[metadata] = '{' if fault == 'invalid' else json.dumps(value)
                    else:
                        text = files[metadata]
                        if fault in ('version', 'unexpanded'):
                            text = text.replace(f'version = "{VERSION}"', 'version = "1.1.0"' if fault == 'version' else 'version = "${version}"')
                        elif fault == 'mod_id':
                            text = text.replace('modId = "simple_terminals"', 'modId = "wrong"')
                        elif fault == 'minecraft':
                            text = text.replace(guard.MINECRAFT_RANGES[minecraft], '[1.19, 1.20)')
                        elif fault == 'dependency':
                            text = text.replace(f'modId = "{loader}"', 'modId = "wrong"')
                        elif fault == 'entrypoint_or_loader':
                            text = text.replace('modLoader = "javafml"', 'modLoader = "wrong"')
                        files[metadata] = '[' if fault == 'invalid' else text
                    self.assert_invalid(artifact, files)

    def test_manifest_version_minecraft_loader_and_classes_are_checked(self):
        for artifact in self.six_jars():
            minecraft, loader = artifact['minecraft'], artifact['loader']
            for fault in ('version', 'minecraft', 'loader', 'missing_manifest', 'missing_class', 'invalid_class'):
                with self.subTest(minecraft=minecraft, loader=loader, fault=fault):
                    files = resources(minecraft, loader)
                    manifest = 'META-INF/MANIFEST.MF'
                    class_file = 'com/nightbeam/simpleterminals/SimpleTerminals.class'
                    if fault == 'missing_manifest':
                        del files[manifest]
                    elif fault == 'missing_class':
                        del files[class_file]
                    elif fault == 'invalid_class':
                        files[class_file] = b'not a class'
                    else:
                        fields = {'version': ('Implementation-Version', VERSION), 'minecraft': ('Built-On-Minecraft', minecraft), 'loader': ('Implementation-Title', loader)}
                        key, original = fields[fault]
                        files[manifest] = files[manifest].replace(f'{key}: {original}', f'{key}: wrong')
                    self.assert_invalid(artifact, files)

    def test_duplicate_zip_entries_and_invalid_zip_are_rejected(self):
        artifact = self.six_jars()[0]
        path = Path(artifact['path'])
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(path, 'a') as jar:
                jar.writestr('fabric.mod.json', '{}')
        with self.assertRaisesRegex(guard.ReleaseError, 'Duplicate entries'):
            guard.validate_jar(artifact, VERSION)
        path.write_bytes(b'not a zip')
        with self.assertRaises(guard.ReleaseError):
            guard.validate_jar(artifact, VERSION)

    def test_only_requested_changelog_section_is_used(self):
        self.assertEqual(guard.release_notes(self.changelog, VERSION), '### Fixes\nCurrent fix.\n')
        self.assertEqual(guard.release_notes(self.changelog, '1.1.0'), 'Old fix.\n')
        self.assertNotIn('Old fix', guard.release_notes(self.changelog, VERSION))

    def test_absent_empty_and_duplicate_changelog_sections_are_rejected(self):
        for text in ('## 1.1.0\nOld', '## 1.1.1\n\n## 1.1.0\nOld', '## 1.1.1\nOne\n## 1.1.1\nTwo'):
            with self.subTest(text=text), self.assertRaises(guard.ReleaseError):
                guard.release_notes(text, VERSION)

    @unittest.skipUnless(shutil.which('node'), 'Node is needed to exercise the publish destination guard')
    def test_upload_refuses_unverified_project_ids_before_file_or_network_access(self):
        workflow = (SCRIPT.parents[1] / '.github/workflows/publish.yml').read_text()
        script = workflow.split("node <<'NODE'\n", 1)[1].rsplit('\n          NODE', 1)[0]
        for modrinth, curseforge in (('wrong', '1528758'), ('Ss3e4qjk', 'wrong'), ('', '')):
            with self.subTest(modrinth=modrinth, curseforge=curseforge):
                # Missing manifest paths make accidental progress past the destination check detectable.
                env = {'PATH': os.environ['PATH'], 'MODRINTH_ID': modrinth, 'CURSEFORGE_ID': curseforge}
                completed = subprocess.run(['node'], input=script, env=env, capture_output=True, text=True)
                self.assertNotEqual(completed.returncode, 0)
                self.assertIn('Configured publish destination', completed.stderr)
                self.assertNotIn('ERR_INVALID_ARG_TYPE', completed.stderr)

    @unittest.skipUnless(shutil.which('node'), 'Node is needed to exercise the upload hash preflight')
    def test_upload_rejects_changed_bytes_in_any_jar_before_api_requests(self):
        artifacts = self.six_jars()
        for artifact in artifacts:
            artifact['hashes'] = guard.validate_jar(artifact, VERSION)
        manifest, notes = self.root / 'manifest.json', self.root / 'notes.md'
        manifest.write_text(json.dumps({'version': VERSION, 'artifacts': artifacts}))
        notes.write_text('Current fix.\n')
        workflow = (SCRIPT.parents[1] / '.github/workflows/publish.yml').read_text()
        script = workflow.split("node <<'NODE'\n", 1)[1].rsplit('\n          NODE', 1)[0]
        # Any accidental API request fails loudly; no network can be used by this test.
        script = "global.fetch = () => { throw new Error('Unexpected API request'); };\n" + script
        env = {'PATH': os.environ['PATH'], 'MODRINTH_ID': 'Ss3e4qjk', 'CURSEFORGE_ID': '1528758',
               'VERSION': VERSION, 'PLATFORMS': 'both', 'RELEASE_MANIFEST': str(manifest),
               'RELEASE_NOTES': str(notes)}
        for artifact in artifacts:
            path = Path(artifact['path'])
            original = path.read_bytes()
            path.write_bytes(original + b'changed after guard')
            with self.subTest(jar=path.name):
                completed = subprocess.run(['node'], input=script, env=env, capture_output=True, text=True)
                self.assertNotEqual(completed.returncode, 0)
                self.assertIn('Artifact hash mismatch: ' + path.name, completed.stderr)
                self.assertNotIn('Unexpected API request', completed.stderr)
            path.write_bytes(original)

    def test_cli_writes_only_validated_manifest_and_current_notes(self):
        artifacts = self.six_jars()
        manifest, notes = self.root / 'manifest.json', self.root / 'notes.md'
        command = [sys.executable, str(SCRIPT), '--root', str(self.root), '--version', VERSION,
                   '--manifest', str(manifest), '--notes', str(notes)]
        completed = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        for artifact in artifacts:
            payload = Path(artifact['path']).read_bytes()
            artifact['hashes'] = {algorithm: hashlib.new(algorithm, payload).hexdigest()
                                  for algorithm in ('sha256', 'sha1', 'sha512')}
        self.assertEqual(json.loads(manifest.read_text()), {'version': VERSION, 'artifacts': artifacts})
        self.assertIn('sha256=', completed.stdout)
        self.assertIn('sha1=', completed.stdout)
        self.assertIn('sha512=', completed.stdout)
        self.assertEqual(notes.read_text(), '### Fixes\nCurrent fix.\n')
        manifest.unlink()
        notes.unlink()
        Path(artifacts[0]['path']).unlink()
        completed = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(completed.returncode, 0)
        self.assertFalse(manifest.exists())
        self.assertFalse(notes.exists())


if __name__ == '__main__':
    unittest.main()
