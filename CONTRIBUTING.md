# Contributing

Thanks for contributing to OmniVault (formerly Simple Terminals).

## Before you start

- Open an issue for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, registry IDs) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Build and test from `1.21.1/`: `./gradlew build` and `./gradlew :neoforge:runGameTestServer`, plus
   `python3 -m unittest discover -s tests` from the repository root.
4. Update documentation when behavior or release steps change.
5. Open a pull request with a clear summary and verification notes.

## Coding expectations

- Follow `docs/ARCHITECTURE.md`: gameplay goes in `common` (vanilla APIs only), loader glue in `neoforge`.
- Change art and data through the generators in `tools/assets/` and lang through `tools/lang/`, never the generated files.
- Add a GameTest for gameplay changes.
- Do not invent Minecraft, Fabric, Forge, or NeoForge APIs — verify against the exact version you target.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
