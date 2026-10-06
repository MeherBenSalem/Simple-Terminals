#!/usr/bin/env python3
"""Test actual 26.2 common registry property sources with verified native vanilla code.

Requires Python 3.9+ and a JDK 25+. Reuses the native_recipe artifact cache and
download verification. Does not start a server, world, listener, or mod loader.
"""

import argparse
import importlib.util
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TEST_DIR = Path(__file__).resolve().parent
COMMON = ROOT / "26.2/common/src/main/java/com/nightbeam/simpleterminals"
MODES = ("without-block-id", "without-item-id", "fixed")
ID_ASSIGNMENT = re.compile(
    r'\.setId\(ResourceKey\.create\(Registries\.(BLOCK|ITEM),\s*'
    r'Identifier\.fromNamespaceAndPath\(Constants\.MOD_ID,\s*"crafting_terminal"\)\)\)'
)


def native_recipe_runner():
    spec = importlib.util.spec_from_file_location(
        "native_recipe_runner", ROOT / "tests/native_recipe/run.py"
    )
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    return runner


def executable(command):
    command = os.path.expanduser(command)
    resolved = shutil.which(command)
    if resolved is None:
        raise RuntimeError(f"Executable not found: {command}")
    return str(Path(resolved).resolve())


def write_sources(destination, mode):
    sources = []
    for name in ("ModBlocks", "ModItems"):
        text = (COMMON / "registry" / f"{name}.java").read_text()
        expected_kind = "BLOCK" if name == "ModBlocks" else "ITEM"
        assignments = list(ID_ASSIGNMENT.finditer(text))
        if len(assignments) != 1 or assignments[0].group(1) != expected_kind:
            raise AssertionError(f"Expected exactly one terminal {expected_kind} setId in {name}")
        if (name == "ModBlocks" and mode == "without-block-id") or (
            name == "ModItems" and mode == "without-item-id"
        ):
            # Remove only the new property ID, reproducing the original missing-ID condition.
            text = ID_ASSIGNMENT.sub("", text)
        path = destination / f"{name}.java"
        path.write_text(text)
        sources.append(path)
    constants = destination / "Constants.java"
    shutil.copyfile(COMMON / "Constants.java", constants)
    sources.extend([
        constants,
        TEST_DIR / "fixtures/CraftingTerminalBlock.java",
        TEST_DIR / "NativeRegistryIdsSmokeTest.java",
    ])
    return sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cache-dir", type=Path,
        default=Path(tempfile.gettempdir()) / "simple-terminals-native-recipes",
        help="Shared verified Mojang download cache; keep it for offline reruns",
    )
    parser.add_argument(
        "--java", default=os.environ.get("JAVA25", "java"),
        help="JDK 25+ java executable (or set JAVA25)",
    )
    parser.add_argument("--javac", help="Optional javac override; defaults to beside java")
    args = parser.parse_args()
    java = executable(args.java)
    javac = executable(args.javac or str(Path(java).with_name("javac")))
    runner = native_recipe_runner()
    jar, libraries, _ = runner.prepare(
        args.cache_dir.expanduser().resolve(), "26.2", runner.VERSIONS["26.2"]
    )
    native_classpath = os.pathsep.join([str(jar), str(libraries / "*")])

    with tempfile.TemporaryDirectory(prefix="simple-terminals-registry-ids-") as temporary:
        temporary = Path(temporary)
        for mode in MODES:
            directory = temporary / mode
            directory.mkdir()
            classes = directory / "classes"
            classes.mkdir()
            sources = write_sources(directory, mode)
            subprocess.run([
                javac, "-cp", native_classpath, "-d", str(classes),
                *map(str, sources),
            ], check=True)
            print(f"\nTesting native Minecraft 26.2 registry IDs: {mode}", flush=True)
            subprocess.run([
                java, "-XX:-UsePerfData", "-cp",
                os.pathsep.join([str(classes), native_classpath]),
                "NativeRegistryIdsSmokeTest", mode,
            ], check=True, cwd=directory)
    print("\nAll native 26.2 registry ID tests passed.", flush=True)


if __name__ == "__main__":
    main()
