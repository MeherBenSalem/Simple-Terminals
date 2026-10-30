# OmniVault (formerly Simple Terminals) — Changelog

## 2.0.0

Simple Terminals is now **OmniVault**, an all-in-one storage mod for **Minecraft 1.21.1 on NeoForge**. Existing
Crafting Terminals carry over: the old `simple_terminals:crafting_terminal` ID now loads as OmniVault's Crafting
Terminal.

### Early game
* **Storage Hub** and **Inventory Cable**: every chest, barrel or other inventory they touch joins one network,
  from vanilla materials. Storage and Crafting Terminals now need only vanilla materials too.

### Storage network
* **Nexus Core**, dyeable **Nexus Cable** and no channel limits; optional Forge Energy.
* **Cell Drives** with item and fluid cells in 4K, 16K, 64K, 256K, 1M, 16M and **Singularity** (2,147,483,648
  items) tiers, plus creative cells.
* **Storage** and **Crafting Terminals**: search by name, `@mod` and `#tag`, four sort orders, a fluid view with
  bucket fill/empty, and JEI recipe transfer.
* **Import**, **Export** and **Storage Buses** for items and fluids, **Network Port**, **Destructor**,
  **Constructor**, **Signal Emitter** and **Storage Monitor**; Speed, Stack, Fuzzy, Redstone and Range Upgrades.
* **Wireless Terminal** with **Wireless Transmitters**, the **Quantum Wireless Terminal**, and **Quantum Bridges**
  that join network segments across any distance and between dimensions.

### Autocrafting
* Crafting and processing patterns, the **Pattern Encoding Terminal**, **Pattern Providers**, **Assemblers**,
  **Quantum Assemblers** and **Crafting Processors**, with a planner, reservations and a crafting status screen.

### Physical storage
* **Vaults** in six tiers (45–162 slots) with animated lids, sorting and in-place **upgrade kits** (also for vanilla
  chests and barrels).
* **Drawers** (1×1, 1×2, 2×2), **Compacting Drawer**, **Drawer Controller**, **Drawer Trim**, capacity and void
  upgrades and the **Drawer Key**.
* **Linked Vault** and **Linked Pouch** (colour frequencies, optional private mode), **Trash Can**, **Relocator** and
  **Netherite Relocator**.

### Backpacks
* Six tiers (27–120 slots), wearable and placeable, dyeable leather, key binding **B**.
* Pickup, Magnet, Void, Feeding, Restock, Compacting, Crafting, Everlasting and Deposit upgrades with filters.

### Removed for now
* The 1.20.1 (Fabric, Forge) and 26.2 (Fabric, NeoForge) builds and the 1.21.1 Fabric build of Simple Terminals.
  The code layout is ready for those ports to return.

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
