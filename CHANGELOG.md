# Simple Terminals — Changelog

## 1.1.1

### Fixes
* Restored the Crafting Terminal survival recipe on **Minecraft 1.21.1** (Fabric, NeoForge) by migrating to the singular `recipe/` data directory and the current result `id` field.
* Restored the recipe on **Minecraft 26.2** (Fabric, NeoForge), including its direct-string ingredient format.
* Restored terminal block drops on **1.21.1** and **26.2** by migrating their block loot tables to the singular `loot_table/` data directory.
* Fixed **26.2** block/item initialization by assigning their registry IDs before construction, as required by that Minecraft version, and preserved the existing block-item translation key.
* Updated the shared **26.2** resource/data pack metadata to accept the current client and server pack formats.
* Preserved the existing recipe ingredients, layout, output ID and quantity; the **1.20.1** recipe is unchanged.

### Regression coverage
* Added native Minecraft recipe discovery, decoding, crafting-grid matching, crafted output and block-loot generation tests for all three supported versions, including rejection of the older broken resources.
* Added a source-backed native **26.2** registry-property construction regression.
* Added source-resource and packaged-loader-jar checks plus a non-publishing CI build/test workflow.
* Native recipe/loot tests use a stand-in item registered under the terminal's exact ID; client JEI display and Sophisticated Storage interactions require separate in-game verification.

## 1.1.0

### Compatibility
* Added **Minecraft 1.21.1** (Fabric, NeoForge).
* Added **Minecraft 26.2** (Fabric, NeoForge).
* Version-synced **1.20.1** jars to **1.1.0**.

### Licensing
* Project license switched to **Apache License 2.0**.

### Known limitations
* Item-handler / Sophisticated Storage integration remains **Forge/NeoForge-only** (Fabric opens vanilla `Container` / `MenuProvider` targets only).

## 1.0.0

* Initial wall-mounted crafting terminal for Minecraft 1.20.1 (Fabric, Forge).
