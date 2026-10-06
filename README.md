# Simple Terminals

Wall-mounted crafting terminals that open nearby inventories with a scrollable storage view, search, and a 3×3 crafting grid.

## Features

- Place a thin **Crafting Terminal** on a wall facing a chest or other inventory
- Opens the target menu when it provides a `MenuProvider`
- Otherwise opens a terminal UI with search, scrolling slots, and crafting
- MultiLoader builds for Fabric, Forge (1.20.1), and NeoForge (1.21.1 / 26.2)

## Supported versions

| Minecraft | Fabric | Forge | NeoForge |
|-----------|--------|-------|----------|
| 1.20.1    | Yes    | Yes   | —        |
| 1.21.1    | Yes    | —     | Yes      |
| 26.2      | Yes    | —     | Yes      |

**Limitation:** Capability-based item handlers and Sophisticated Storage controller integration work on **Forge/NeoForge** only. Fabric opens adjacent vanilla containers / menu providers.

## Requirements

- **JDK 21** (or 17) to run Gradle for `1.20.1/` and `1.21.1/`
- **JDK 25** for `26.2/`

## Installation

1. Install the matching loader for your Minecraft version.
2. Drop `simple-terminals-<minecraft>-<loader>-<version>.jar` into `mods/`.
3. Restart the game.

## Building

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
cd 1.20.1
.\gradlew.bat build
cd ..\1.21.1
.\gradlew.bat build

$env:JAVA_HOME = "C:\path\to\jdk-25"
cd ..\26.2
.\gradlew.bat build
```

## Testing

Run source-resource regressions with `python3 -m unittest discover -s tests -v`.
Run the native Minecraft recipe suite with a JDK 25+:
`python3 tests/native_recipe/run.py --java "$JAVA_HOME/bin/java"`.
Also run `python3 tests/native_registry_ids/run.py --java "$JAVA_HOME/bin/java"`
for the 26.2 registry-property regression.
See [the native test instructions](tests/native_recipe/README.md) for coverage,
verified download caching, and loader/UI integration limits.

After building, validate each distributable loader jar with
`python3 tests/test_terminal_recipe_resources.py --jar 1.21.1:path/to/loader.jar -v`
(substitute its Minecraft version; repeat `--jar` for multiple artifacts).
The non-publishing **Verify recipes and builds** workflow runs native tests,
builds all six supported loader targets, and validates their packaged survival resources.
The separate publish workflow remains tag/manual-triggered.

## Project layout

```
Simple-Terminals/
├── 1.20.1/   # Fabric + Forge
├── 1.21.1/   # Fabric + NeoForge
└── 26.2/     # Fabric + NeoForge
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

See [.github/SECURITY.md](.github/SECURITY.md).

## License

Licensed under the [Apache License 2.0](LICENSE).
