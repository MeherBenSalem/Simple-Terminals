#!/usr/bin/env python3
"""Download verified Mojang distributions and test native recipe/loot code without Gradle.

Requires Python 3.9+ and a JDK 25+ (to execute Minecraft 26.2). No dedicated
server, network listener, world, mod loader, login, or EULA acceptance is used.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
TEST_DIR = Path(__file__).resolve().parent
VERSIONS = {
    "1.20.1": {
        "metadata_sha1": "c0a00f47b3dae01d83e21be9a646c9232379d9ab",
        "server_sha1": "84194a2f286ef7c14ed7ce0090dba59902951553",
        "recipe_directory": "recipes",
    },
    "1.21.1": {
        "metadata_sha1": "22a1966494dfa4eeb5ee778c8e6ed5b774839582",
        "server_sha1": "59353fb40c36d304f2035d51e7d6e6baa98dc05c",
        "recipe_directory": "recipe",
    },
    "26.2": {
        "metadata_sha1": "c7868781b30aaf24be0dac894c94a34e5d6df10d",
        "server_sha1": "823e2250d24b3ddac457a60c92a6a941943fcd6a",
        "recipe_directory": "recipe",
    },
}


def sha1(path):
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url, destination, expected_sha1):
    if destination.exists() and sha1(destination) == expected_sha1:
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + ".partial")
    print(f"Downloading {destination.name} from Mojang", flush=True)
    try:
        with urllib.request.urlopen(url, timeout=120) as response:
            with partial.open("wb") as output:
                shutil.copyfileobj(response, output)
        actual_sha1 = sha1(partial)
        if actual_sha1 != expected_sha1:
            raise RuntimeError(
                f"SHA-1 mismatch for {destination.name}: "
                f"expected {expected_sha1}, received {actual_sha1}"
            )
        partial.replace(destination)
    finally:
        if partial.exists():
            partial.unlink()


def prepare(cache, version, config):
    version_cache = cache / version
    version_cache.mkdir(parents=True, exist_ok=True)
    metadata_path = version_cache / "metadata.json"
    metadata_sha1 = config["metadata_sha1"]
    download(
        f"https://piston-meta.mojang.com/v1/packages/{metadata_sha1}/{version}.json",
        metadata_path,
        metadata_sha1,
    )
    metadata = json.loads(metadata_path.read_text())
    server = metadata["downloads"]["server"]
    if server["sha1"] != config["server_sha1"]:
        raise RuntimeError(f"Unexpected server artifact for {version}")
    outer_jar = version_cache / "server-bundle.jar"
    download(server["url"], outer_jar, server["sha1"])
    inner_jar = version_cache / "minecraft-server.jar"
    library_dir = version_cache / "libraries"
    library_dir.mkdir(exist_ok=True)
    with zipfile.ZipFile(outer_jar) as archive:
        version_entries = archive.read("META-INF/versions.list").decode().splitlines()
        selected = [entry.split("\t") for entry in version_entries if entry.split("\t")[1] == version]
        if len(selected) != 1:
            raise RuntimeError(f"Unexpected bundled version list for {version}")
        expected_hash, _, inner_path = selected[0]
        inner_bytes = archive.read(f"META-INF/versions/{inner_path}")
        if hashlib.sha256(inner_bytes).hexdigest() != expected_hash:
            raise RuntimeError(f"Bundled server SHA-256 mismatch for {version}")
        inner_jar.write_bytes(inner_bytes)
        for entry in archive.read("META-INF/libraries.list").decode().splitlines():
            expected_hash, _, library_path = entry.split("\t")
            library_bytes = archive.read(f"META-INF/libraries/{library_path}")
            if hashlib.sha256(library_bytes).hexdigest() != expected_hash:
                raise RuntimeError(f"Bundled library SHA-256 mismatch: {library_path}")
            (library_dir / Path(library_path).name).write_bytes(library_bytes)
    mapping_arg = "-"
    if "server_mappings" in metadata["downloads"]:
        mappings = metadata["downloads"]["server_mappings"]
        mapping_path = version_cache / "server-mappings.txt"
        download(mappings["url"], mapping_path, mappings["sha1"])
        mapping_arg = str(mapping_path)
    return inner_jar, library_dir, mapping_arg


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cache-dir", type=Path,
        default=Path(tempfile.gettempdir()) / "simple-terminals-native-recipes",
        help="Download cache; keep it for offline reruns",
    )
    parser.add_argument(
        "--java", default=os.environ.get("JAVA25", "java"),
        help="JDK 25+ java executable (or set JAVA25)",
    )
    parser.add_argument(
        "--versions", nargs="+", choices=VERSIONS, default=list(VERSIONS),
    )
    args = parser.parse_args()
    args.cache_dir = args.cache_dir.expanduser().resolve()
    args.java = os.path.expanduser(args.java)
    if os.path.dirname(args.java):
        args.java = str(Path(args.java).resolve())
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    for version in args.versions:
        config = VERSIONS[version]
        inner_jar, library_dir, mappings = prepare(args.cache_dir, version, config)
        recipe_path = (
            ROOT / version / "common/src/main/resources/data/simple_terminals"
            / config["recipe_directory"] / "crafting_terminal.json"
        )
        command = [
            args.java, "-XX:-UsePerfData", "-cp",
            os.pathsep.join([str(inner_jar), str(library_dir / "*")]),
            str(TEST_DIR / "NativeRecipeSmokeTest.java"), version, mappings,
            str(recipe_path),
            str(TEST_DIR / "fixtures/legacy_crafting_terminal.json"),
        ]
        if version == "26.2":
            command.extend([
                str(ROOT / "26.2/common/src/main/resources/pack.mcmeta"),
                str(TEST_DIR / "fixtures/pack_format_64.mcmeta"),
            ])
        else:
            command.extend(["-", "-"])
        command.extend([
            str(ROOT / version / "common/src/main/resources"),
            str(TEST_DIR / "fixtures/legacy_crafting_terminal_loot.json"),
        ])
        print(f"\nTesting native Minecraft {version}", flush=True)
        subprocess.run(command, check=True, cwd=args.cache_dir)
    print("\nAll selected native recipe and loot tests passed.", flush=True)


if __name__ == "__main__":
    main()
