# The OmniVault storage network

Everything here runs on one rule: **one root per network, any number of devices, no channels.** The root is a
cheap **Storage Hub** early on (plain inventories only) or a **Nexus Core** for everything. See
[the progression guide](PROGRESSION.md) for the whole path.

## Starting with a Storage Hub

Before you have any Omnium: place a **Storage Hub**, run **Inventory Cable** past your chests and add a
**Storage Terminal**. Every inventory touching the hub or an inventory cable (chests, barrels, drawers, vaults,
other mods' storage) appears in the terminal as one storage; double chests count once. Hub networks don't use
storage cells, autocrafting or wireless terminals; replace the hub with a Nexus Core when you want those, and
everything else keeps working. Inventory Cable works on Nexus networks too.

## Getting started

1. Craft **Omnium**: 1 amethyst shard + 1 nether quartz + 2 redstone → 4 Omnium Dust. Mix 1 iron ingot with
   2 dust into Omnium Blend and smelt (or blast) it into an **Omnium Ingot**.
2. Place a **Nexus Core**. It is the network's heart: a reactor cage with a floating omnium crystal that spins
   while the network is online.
3. Run **Nexus Cable** from the core to your devices. Cables connect to anything on the network by themselves.
4. Add a **Cell Drive** and put **Storage Cells** in it, then a **Storage Terminal** or **Crafting Terminal** to
   use everything.

Devices with a front face (drives, terminals) don't carry the network through that face; connect them from
the sides, top, bottom or back.

## Devices

| Device | What it does |
| --- | --- |
| Nexus Core | Runs the network. Right-click for the overview: online state, devices, items stored vs. capacity, energy. Two cores on one network put it in conflict until one is removed. |
| Storage Hub | Early-game root (see above). Right-click for the same overview as the core. |
| Inventory Cable | A cable that also adds every inventory it touches to the network. Dyeable like Nexus Cable. |
| Quantum Bridge | Entangle two bridges with a Quantum Link Card (use it on one, then the other) and the network continues through the pair across any distance and between dimensions, while both are loaded. Right-click shows the link; the OmniTool breaks it. |
| Nexus Cable | Carries the network. Dye it to keep parallel networks apart: two different colours never connect; uncoloured cable connects to any colour. A water bucket or sponge washes the dye off. Waterloggable. |
| Cell Drive | Ten bays for item and fluid cells. Each bay's LED shows the cell: green with room to spare, amber when nearly full, red when full, dark while the network is offline. Has a priority. |
| Storage Terminal | The whole network in a searchable, sortable grid. |
| Crafting Terminal | A storage terminal with a 3×3 crafting grid that refills itself from the network after every craft. |
| Import Bus | Pulls items (and fluids) that pass its filter out of the inventory it faces. |
| Export Bus | Pushes the items listed in its filter (and fluids listed as filled buckets) into the inventory it faces. |
| Storage Bus | Makes the inventory or tank it faces part of the network: chests, barrels, drawers, other mods' storage, even another network's Network Port. Has a filter, a priority and an access mode (insert and extract, insert only, extract only). |
| Network Port | The whole network as an ordinary inventory and tank, for hoppers and pipes from any mod. |
| Destructor | Mines the block it faces into the network and picks up items in front of it. Never breaks containers or unbreakable blocks, and only mines when every drop fits. |
| Constructor | Places blocks listed in its filter from the network into the space it faces. |
| Signal Emitter | Gives a redstone signal while the network holds at least (or fewer than) a set number of an item. With no item set it counts everything. |
| Storage Monitor | Shows one item and its count on its screen. Right-click with the item to store it; right-click empty-handed to take a stack (sneak: one). |
| Wireless Transmitter | Extends wireless range. Right-click it with Range Upgrades (up to four). |

## Storage cells

Cells are a Cell Housing plus a Storage Matrix (shapeless). Take an empty cell apart by sneak-using it.

| Tier | Items | Fluid cell |
| --- | --- | --- |
| 4K | 4,096 | 1,024 buckets |
| 16K | 16,384 | 4,096 buckets |
| 64K | 65,536 | 16,384 buckets |
| 256K | 262,144 | 65,536 buckets |
| 1M | 1,048,576 | 262,144 buckets |
| 16M | 16,777,216 | 4,194,304 buckets |
| Singularity | 2,147,483,648 | 536,870,912 buckets |
| Creative | practically unlimited | practically unlimited |

An item cell holds up to 128 different items and a fluid cell up to 32 fluids; Singularity cells hold 1,024 (all
configurable). A cell's
contents live in the world save, not on the item, so even a full 16M cell never bloats packets or chunk data. Its
tooltip and fill bar show how full it is.

## Terminals

- **Search** by name; `@mod` filters by mod, `#tag` by item tag; combine terms with spaces.
- **Sort** by count, name, mod or registry order, ascending or descending; show everything, stored items only or
  craftable items only.
- **Left-click** takes a stack, **right-click** half a stack, **shift + right-click** one item, **shift + left-click**
  sends a stack to your inventory. Click with an item on the cursor to store it (right-click stores one).
  **Shift-click** anything in your inventory to store it.
- **Fluids**: switch the grid to fluids with the button on the left. Click with an empty bucket to fill it, with
  a full bucket to empty it.
- **Craftable** items show "Craft"; middle-click or Ctrl + click asks for an amount (see Autocrafting).
- **JEI**: in a crafting terminal, JEI's `+` button moves a recipe into the grid, taking ingredients from the
  network first and your inventory second, and marks anything missing. In a Pattern Encoding Terminal it loads any recipe into
  the pattern editor instead (see [Autocrafting](AUTOCRAFTING.md)).

## Wireless

Sneak-use a **Wireless Terminal** on a Nexus Core to link it. It then opens a crafting terminal anywhere within
32 blocks of the core or 64 blocks (+48 per Range Upgrade) of a Wireless Transmitter on the network. Press
**G** to open the first wireless terminal in your inventory without holding it.

Upgrade it at a smithing table with a **Quantum Upgrade Template** and a nether star to make a **Quantum Wireless
Terminal**: any distance, any dimension, as long as the core's chunk is loaded. The link carries over.

## Upgrade cards

| Card | Effect |
| --- | --- |
| Speed | Buses, Destructors and Constructors act twice as often per card (up to 4). |
| Stack | Buses move twice as many items per operation per card (up to 4). |
| Fuzzy | Filters ignore damage, enchantments and other item data. |
| Redstone | The device only runs while it receives a redstone signal. |
| Range | Adds wireless range to a Wireless Transmitter. |

## OmniTool

Right-click turns an OmniVault block; while you hold the tool, blocks with a screen turn instead of opening.
Sneak-right-click dismantles the block into its item. On a Quantum Bridge it breaks the link, and drawers and
Linked Vaults have their own uses for it (see [Storage](STORAGE.md)).

## Priorities

Storage with a higher priority fills first and empties last. Drives and storage buses have one (−999 to 999;
hold shift for steps of 10). New items prefer storage that already holds them.

## Energy

Off by default. Set `requireEnergy = true` under `[network]` in `config/omnivault-common.toml` and the Nexus Core
accepts Forge Energy (FE) from any mod's cables; each device uses a little per tick and the network goes offline
when the core runs dry.
