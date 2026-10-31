# Contributing

Thanks for contributing to OmniVault (formerly Simple Terminals).

## Before you start

- Open an issue for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, registry IDs) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Build and test in every Minecraft workspace you changed (`1.20.1/`, `1.21.1/`, `26.2/`, `26.3/`):
   `./gradlew build`, `./gradlew :neoforge:runGameTestServer` (`:forge:` on 1.20.1) and
   `./gradlew :fabric:runGametest`, plus `python3 -m unittest discover -s tests` from the repository root.
   Gameplay changes belong in every workspace.
4. Update documentation when behavior or release steps change.
5. Open a pull request with a clear summary and verification notes.

## Coding expectations

- Follow `docs/ARCHITECTURE.md`: gameplay goes in `common` (vanilla APIs only), loader glue in `fabric` and
  `neoforge`; anything a loader provides goes through a service with an implementation for each.
- Change art and data through the generators in `tools/assets/` and lang through `tools/lang/`, never the generated files;
  then refresh the other versions with `tools/port/resources_1_20_1.py` and `tools/port/resources_26.py <version>`.
- Add a GameTest for gameplay changes.
- Do not invent Minecraft, Fabric, Forge, or NeoForge APIs — verify against the exact version you target.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
