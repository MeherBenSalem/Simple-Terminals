# Contributing

Thanks for contributing to Simple Terminals.

## Before you start

- Open an issue for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, registry IDs) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Build the version root you changed from inside that folder (`1.20.1/`, `1.21.1/`, or `26.2/`).
4. Update documentation when behavior or release steps change.
5. Open a pull request with a clear summary and verification notes.

## Coding expectations

- Follow the existing patterns in the targeted version root. Version folders are intentionally isolated.
- Do not invent Minecraft, Fabric, Forge, or NeoForge APIs — verify against the exact version you target.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
