# OmniVault (formerly Simple Terminals) — Changelog

## 2.0.4

### Fixed (every version)
* **See-through world beside Storage and Crafting Terminals.** The terminals' recessed screen bezel leaves small
  openings at the sides of the block, but the block told Minecraft it was a solid cube, so neighbouring blocks
  skipped drawing the faces that touch it. Looking past the bezel showed through into caves or the sky. Terminals
  now let their neighbours draw those faces, like the Nexus Core already does.

## 2.0.3

### Fixed (every version)
* **Duplication: breaking a terminal on a running network.** Breaking a Storage Terminal (or another device) while
  it was connected to a Nexus Core dropped the item *and* left the block in place. Devices write their state (active,
  working, powered) during network updates, and the update that followed the break wrote the old block back.
  A device now only changes its own state while it is still in the world.

## 2.0.2

### Fixed (Minecraft 26.3)
* Storage and Crafting Terminal clicks: left-click took half a stack, right-click did nothing, shift-click took one
  item instead of moving the stack, and inserting from the cursor put in one item instead of the stack.
* Drawer lock button, Trash and Storage screens, and the backpack buttons, scrollbar and filter slots responded to
  the wrong mouse buttons.

26.3 numbers mouse buttons differently (left 1, right 3); these screens now translate the button as Minecraft's own
inventory screens do. Other versions are unchanged apart from the version number.

## 2.0.1

OmniVault now runs on **Minecraft 1.20.1** (Forge, Fabric), **26.2** and **26.3** (NeoForge, Fabric) as well as
1.21.1. Every version and loader has the whole 2.0 feature set: the Storage Hub and Inventory Cable, the
channel-free storage network with cells up to Singularity, autocrafting, Quantum Bridges, vaults, drawers, linked
vaults and backpacks. The full list is in the
[2.0.0 notes](https://github.com/MeherBenSalem/Simple-Terminals/blob/main/CHANGELOG.md#200).

### New Minecraft versions
* **1.20.1** on Forge 47.4.28+ and on Fabric (Fabric API required), Java 17.
* **26.2** on NeoForge 26.2.0.93+ and on Fabric, and **26.3** on NeoForge 26.3.0.100+ and on Fabric (Fabric API
  required), Java 25.
* JEI support on every version; Team Reborn Energy for powered networks on Fabric, Forge Energy on Forge and NeoForge.
* Worlds that used Simple Terminals 1.1.1 on 1.20.1 or 26.2 keep their Crafting Terminals.
* A 1.21.1 world opened on 26.2 or 26.3 keeps its cell contents, backpacks and linked vaults.

### Fixes
* **Fabric 1.21.1:** see-through model parts render as on NeoForge instead of solid: the Nexus Core's and the
  Assemblers' glass shows the crystal and rings inside again, and the Export Bus and Storage Hub show their LEDs.

### Version notes
* **1.20.1:** the Assembler is crafted with a crafting table, since 1.20.1 has no crafter. A drawer picked with
  creative pick-block keeps its contents, but its tooltip does not list them.

## 2.0.0

Simple Terminals is now **OmniVault**, an all-in-one storage mod for **Minecraft 1.21.1 on NeoForge and Fabric**. Existing
Crafting Terminals carry over: the old `simple_terminals:crafting_terminal` ID now loads as OmniVault's Crafting
Terminal.

### Early game
* **Storage Hub** and **Inventory Cable**: every chest, barrel or other inventory they touch joins one network,
  from vanilla materials. Storage and Crafting Terminals now need only vanilla materials too.

### Storage network
* **Nexus Core**, dyeable **Nexus Cable** and no channel limits; optional energy (Forge Energy on NeoForge, Team
  Reborn Energy on Fabric).
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
* JEI's `+` button loads any recipe into the Pattern Encoding Terminal: crafting recipes as crafting patterns,
  furnace and other machine recipes as processing patterns.

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

### Fabric
* The same content on Fabric (Fabric API required), from the same code base.
* Inventories, drawers, the Network Port and Pattern Providers work with any mod through Fabric's Transfer API,
  including its transactions: aborted transfers change nothing, and the Network Port moves whole stacks of any size.
* JEI support, Team Reborn Energy for powered networks, and the same `config/omnivault-common.toml`.

### Removed for now
* The 1.20.1 (Fabric, Forge) and 26.2 (Fabric, NeoForge) builds of Simple Terminals. The code layout is ready for
  those ports to return.

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
