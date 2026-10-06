# Native crafting-recipe and terminal-drop regression test

Run from the repository root with Python 3.9+ and a **JDK 25+**:

```sh
python3 tests/native_recipe/run.py --java "$JAVA_HOME/bin/java"
```

A single JDK 25 can run the vanilla implementations for all three supported
Minecraft versions. First run downloads approximately 180 MB from Mojang, plus
mappings. Downloads are pinned and SHA-1-verified against official metadata;
the bundled Minecraft jar and each library are additionally SHA-256-verified
against the bundle's manifest. `--cache-dir` retains downloads for offline
reruns; `--versions 1.21.1 26.2` selects versions.

The harness uses only vanilla bootstrap and native recipe/loot/resource classes. It
never launches a dedicated server, opens a listener, creates a world, logs in,
or accepts an EULA. Mojang jars and mappings are cached locally, not included
in this repository.

## Coverage

- The actual `RecipeManager` prefix/lister, `PathPackResources`,
  `MultiPackResourceManager`, and `FileToIdConverter` discover the recipe from
  the source resource pack. The legacy plural directory is visible in 1.20.1
  and ignored in 1.21.1 and 26.2.
- The native 1.20.1 JSON recipe serializer and 1.21.1/26.2 `Recipe.CODEC`
  deserialize the fixed JSON. Both newer versions reject the historical JSON
  independently of its obsolete directory.
- The native `ShapedRecipe.matches` accepts normal and mirrored patterns at
  both legal vertical offsets in a 3x3 grid. Missing/wrong ingredients, extra
  items, arbitrary rearrangement, a vertical flip, and a 2x2 grid are rejected.
- Native assembly returns the registered `simple_terminals:crafting_terminal`
  item with count 1 for every accepted grid.
- Native `LootDataType.TABLE` / `Registries.LOOT_TABLE` directory resolution,
  `FileToIdConverter`, and the resource manager discover the block loot table.
  The unchanged plural `loot_tables` path remains visible in 1.20.1; the
  historical plural path is ignored in 1.21.1 and 26.2, where the fixed singular
  `loot_table` path is discovered. The legacy loot fixture was copied from the
  repository's pre-fix `HEAD`, and its JSON schema is still accepted in all
  three versions.
- Native 1.20.1 `LootDataType.TABLE` deserialization and 1.21.1/26.2
  `LootTable.DIRECT_CODEC` decode the discovered table. Native
  `LootTable.getRandomItemsRaw` produces exactly one registered terminal item
  with count 1 in a validated `minecraft:block` context with no explosion.
  Unit-radius and zero-survival-chance explosion contexts also exercise the
  unchanged `survives_explosion` condition.
- Native 26.2 `PackMetadataSection` client/server codecs and `PackCompatibility`
  validate the shared metadata for resource format 88.0 and data format 107.1.
  Historical `pack_format: 64` is parsed but classified `TOO_OLD` for both.

## Scope

A plain test item is registered under the real output ID during the item
registry's bootstrap callback. This verifies recipe ID resolution and crafted
output without loading Fabric/Forge/NeoForge or the mod's block-item class.
For loot generation, the context uses a vanilla crafting-table block state,
an empty tool, and a zero origin. It supplies no level; the table's native raw
generation path does not need one. This checks the loot table and registered
item ID/count, not the actual terminal block's initialization or breaking a
block in a running game. It does not claim a running mod-loader integration
test or an in-game UI test. Version-specific mappings resolve reflective API
names; the recipe/loot parsing, resource discovery, matching, assembly, and
raw loot generation logic are all the original Mojang implementations, not
replicas in the test.

## Primary references

- [1.21 directory changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21)
- [1.21.2 ingredient format changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-2)
- [1.21.9 pack metadata changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9)
- [26.2 final pack versions](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)
- The pinned vanilla recipe and crafting-table loot files, plus `version.json`,
  inside each official Mojang server bundle provide direct version-specific
  schema and directory evidence. The newer loot JSON needs no schema change.
