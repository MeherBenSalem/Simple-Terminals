# Native 26.2 registry-ID smoke test

Run with Python 3.9+ and JDK 25+:

```sh
python3 tests/native_registry_ids/run.py --java "$JAVA_HOME/bin/java"
```

The runner reuses `tests/native_recipe/run.py` to verify and prepare the official
Minecraft 26.2 server distribution. It shares the default
`/tmp/simple-terminals-native-recipes` cache; `--cache-dir` changes that location.
The test invokes vanilla bootstrap registration callbacks before registry freeze.
Expected-failure JVMs stop after the specific constructor exception; only the
fixed-source case completes registry bootstrap and freeze.
It never starts a dedicated server, network listener, world, GUI, or mod loader,
and does not accept an EULA.

The checked-in 26.2 `ModBlocks`, `ModItems`, and `Constants` sources are compiled
against native vanilla classes. A minimal `CraftingTerminalBlock` subclass of
vanilla `Block` isolates their property construction from unrelated gameplay.
Three fresh JVMs test:

- Removing the block `setId` from the actual registry source reproduces
  `NullPointerException: Block id not set`.
- Removing only the item `setId` reproduces
  `NullPointerException: Item id not set` during native item bootstrap.
- The unmodified fixed sources construct and register both objects successfully.
  Assertions verify the terminal block and item registry IDs, the item-to-block
  reference, matching block/item description IDs, and the default block loot ID
  `simple_terminals:blocks/crafting_terminal`.

These are focused native construction regressions, not full Fabric/NeoForge
loader startup tests or gameplay tests. The harness does not compile or exercise
the full terminal block, menu, client, or loader-specific registration code.
