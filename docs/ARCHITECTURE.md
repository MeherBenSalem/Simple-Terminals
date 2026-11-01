# OmniVault architecture

OmniVault is a [MultiLoader](https://github.com/jaredlll08/MultiLoader-Template) project. Each Minecraft
version is its own workspace with the same layout and the same features: **1.20.1** (Forge and Fabric),
**1.21.1**, **26.2** and **26.3** (NeoForge and Fabric). 1.21.1 is the reference: art and lang are generated
there, and the other workspaces derive their resources from it (see [Minecraft versions](#minecraft-versions)).
This page describes 1.21.1; the other versions differ only where their APIs do.

```
1.21.1/
├── common/     # all gameplay, menus, screens and renderers (vanilla APIs only)
│   └── src/gametest/  # in-world GameTests shared by both loaders, not shipped
├── fabric/     # Fabric glue: registries, Transfer API storages, payloads, events, mixins, client setup
│   └── src/gametest/  # Fabric test adapter, Fabric-only transfer tests, the fabric-gametest entrypoint
├── neoforge/   # NeoForge glue: registries, capabilities, payloads, events, client setup
│   └── src/gametest/  # NeoForge test adapter
tools/
├── assets/     # Python generators for textures, models, blockstates, recipes, loot tables
└── lang/       # per-feature lang fragments, merged into en_us.json
```

## Rules for `common`

`common` compiles against vanilla Minecraft only (NeoForm), so it must not touch loader classes or
anything vanilla keeps package-private. Everything loader-specific goes through a service:

| Need | Common API | NeoForge | Fabric |
| --- | --- | --- | --- |
| Registering blocks, items, block entities, menus, components | `registry.ModRegistry` (backed by `IRegistryHelper`) | `DeferredRegister` per registry | Vanilla registries; each entry registers the first time it is asked for, the rest at the end of init |
| Menus with extra open data | `ModRegistry.menu(...)`, `Services.PLATFORM.openMenu(...)` | `IMenuTypeExtension`, `ServerPlayer.openMenu(provider, buf)` | `ExtendedScreenHandlerType` carrying the buffer's bytes |
| Other mods' inventories and tanks | `Services.PLATFORM.findSlotAccess/findFluidAccess(level, pos, side)` | Item and fluid handler capabilities | `ItemStorage.SIDED` / `FluidStorage.SIDED` |
| Exposing our inventories, tanks, energy | `ModCapabilities.exposeItems/exposeFluids/exposeEnergy` | `RegisterCapabilitiesEvent` | Transfer API lookups; energy through Team Reborn Energy when installed |
| Network payloads | `OmniVaultCommon.packets(registrar -> ...)` with vanilla `CustomPacketPayload` records | `RegisterPayloadHandlersEvent` | `PayloadTypeRegistry` + `ServerPlayNetworking`/`ClientPlayNetworking` |
| Server tick | `OmniVaultCommon.onServerTick(...)` | `ServerTickEvent.Post` | `ServerTickEvents.END_SERVER_TICK` |
| Items that act before the block (`content.BlockUseOverride`) | implemented by the item | `PlayerInteractEvent.RightClickBlock` | mixin on `BlockStateBase#useItemOn` |
| Backpack pickup, creative drawer clicks | `backpack.BackpackHooks`, `drawer.DrawerInteraction` | `ItemEntityPickupEvent`, `LeftClickBlock` | mixin on `ItemEntity#playerTouch`, `AttackBlockCallback` |
| Client screens, renderers, colours, keys, standalone models, player layers | `OmniVaultClient.registration(r -> ...)` with `client.ClientRegistrar` | one forwarding registrar per NeoForge client event | registered directly during client init; `ModelLoadingPlugin` for models |
| Config | `config.OVConfig` values declared as static fields | `config/omnivault-common.toml` | the same file |

### Fabric transactions

Our inventories take a simulate flag, Fabric's take a transaction, and Fabric callers routinely test a
move, abort it and do it for real. The adapters in `fabric/transfer`:

- **Slotted inventories** (vaults, drawers, backpacks, the trash can): `SlotAccessItemStorage` applies each
  change at once and records how to undo it; an aborted transaction runs the undo steps backwards.
- **Network views** (Network Port, a Pattern Provider's return slot, the port's tanks): `NetworkItemStorage`
  and `FluidAccessFluidStorage` move whole amounts through the network inside the caller's transaction.
  While a `storage.ChangeJournal` records, the network notes how to undo changes to our own storage, leaves
  storage that joins the transaction (`joinsTransactions()`, another mod's) to roll back by itself, and
  hands items to waiting crafting jobs only when the outermost transaction commits.

## Features

Each feature owns a package, a content class with an `init()` called from `OmniVaultCommon.init()`,
an optional client class with an `init()` called from `OmniVaultClient.init()`, a lang fragment in
`tools/lang/<feature>.json`, an art generator in `tools/assets/<feature>.py`, and GameTests in
`common/src/gametest`.

| Package | What it is |
| --- | --- |
| `content` | Omnium materials, processors, upgrade cards, the OmniTool, the creative tab |
| `network` | The digital storage network: Nexus Core, cables, drives and cells, terminals, buses, wireless |
| `physical` | Tiered vaults, drawers, linked (ender) vaults and pouches, trash can, relocator |
| `backpack` | Tiered backpacks and their upgrades |
| `autocraft` | Patterns, assemblers and crafting jobs on the network |

## Minecraft versions

| Workspace | Loaders | Java | Resources |
| --- | --- | --- | --- |
| `1.20.1/` | `forge` (Forge 47.4), `fabric` | 17 (Gradle runs on 21) | `python3 tools/port/resources_1_20_1.py` |
| `1.21.1/` | `neoforge`, `fabric` | 21 | generated by `tools/assets/` |
| `26.2/` | `neoforge`, `fabric` | 25 | `python3 tools/port/resources_26.py 26.2` |
| `26.3/` | `neoforge`, `fabric` | 25 | `python3 tools/port/resources_26.py 26.3` |

The class and package names, services, registry ids, GameTests and loader seams are the same in every
workspace. After changing art, lang or data, regenerate 1.21.1 and re-run the converters (each has a
`--check` mode). They rewrite what changed between versions: plural data folders, recipe results, tags
and `forge_data` for 1.20.1; item model definitions, string ingredients, in-bounds UVs and vanilla
`light_emission` for glowing parts on 26.x. A change to Java code is made in each workspace.

What differs:

- **1.20.1** has no data components: `registry.ItemComponent` keeps the same component API and stores each
  value in the stack's NBT under its id. Payloads go through a common `packet` abstraction (a Forge
  `SimpleChannel`, a Fabric channel per payload), Forge capabilities are attached per side through
  `AttachCapabilitiesEvent`, and crafting lookups go through `util.CraftingGrid` because there is no
  `CraftingInput`. On Fabric, `EmissiveModels` also applies each model's `render_type`.
- **26.2 and 26.3** use `Identifier`, ids set in block and item properties, `ValueInput`/`ValueOutput`,
  server-only recipes, render-state block entity renderers and the extract-based GUI. On NeoForge the
  storage seams use its transfer API (`ResourceHandler`, transactions), with the same undo and
  commit-on-close adapters as Fabric. Saved data lives in `data/omnivault/`; in a world upgraded from 1.21.1,
  `util.LegacySavedData` reads the old `data/omnivault_*.dat` files once (running vanilla's item fixers on every
  stack) and saves them in the new place, leaving the old files as a backup. GameTests are registered through
  `RegisterGameTestsEvent` on NeoForge and Fabric's `@GameTest`. 26.3 adds SDL3 key codes, blocks without
  codecs and new loot table and model formats, which `resources_26.py` handles per version.

## Shared building blocks

- `storage.ItemKey`: an item type (item + components) usable as a map key.
- `storage.ItemStorage`: counted storage with `long` amounts; `storage.SlotAccessStorage` adapts any slotted inventory.
- `menu.OVMenu`: base menu with player-inventory slots and shift-click handling.
- `client.gui.GuiStyle`: the shared screen look (windows, wells, slots, buttons, scrollbars, small counts).
- `util.InventorySorting`: compact-and-sort with a ready-made `SortPayload`; menus implement `Sortable`.
- `util.Formatting`: `1.2K`-style counts.

## Art

Textures are 16×16 pixel art generated by `tools/assets/*.py` on top of `tools/assets/kit.py` (palette,
pixel helpers, a box-model builder, blockstate/recipe/loot writers). Models are multi-element 3D
models. Glowing parts carry NeoForge's per-element `neoforge_data` light values and are also marked
unshaded (only glowing parts are): NeoForge draws them full-bright from the data, and on Fabric
`EmissiveModels` gives every unshaded face of our models an emissive material.

Regenerate everything with:

```bash
for f in tools/assets/*.py; do case "$f" in */kit.py|*/render_models.py) ;; *) python3 "$f" ;; esac; done
```

Preview models offline with `tools/assets/render_models.py` (Pillow + numpy): for example
`python3 tools/assets/render_models.py block/drive --size 512 --out build/previews/drive.png`, or
`--sheet` for a labelled contact sheet of every model. It is also importable for marketing renders.

## Testing

```bash
cd 1.21.1                              # or any other workspace
./gradlew build                        # compile and package both loaders
./gradlew :neoforge:runGameTestServer  # GameTests on a NeoForge dedicated server (:forge: on 1.20.1)
./gradlew :fabric:runGametest          # the same GameTests plus Fabric transfer tests on Fabric
python3 -m unittest discover -s tests  # resource and release-guard checks (from the repository root)
```

The shared GameTests reach the loader through `gametest.TestPlatform` (inventories as other mods see
them, the left-click hook) and take their structure names from each loader's `gametest.Templates`.
NeoForge finds the classes through `@GameTestHolder`; Fabric lists them in its `fabric-gametest`
entrypoint and compiles against stand-ins for the two NeoForge annotations.
