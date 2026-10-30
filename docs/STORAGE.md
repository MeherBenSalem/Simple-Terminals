# Physical storage

Blocks you can use without a network. Every one of them also works with hoppers, pipes and the network's
Storage Bus.

## Tiered vaults

| Vault | Slots | Grid | Notes |
| --- | --- | --- | --- |
| Copper Vault | 45 | 9 × 5 | |
| Iron Vault | 54 | 9 × 6 | |
| Gold Vault | 81 | 9 × 9 | |
| Diamond Vault | 108 | 12 × 9 | |
| Netherite Vault | 135 | 15 × 9 | Blast resistant |
| Omnium Vault | 162 | 18 × 9 | Blast resistant |

- The lid opens on its hinge while anyone is looking inside, with chest sounds.
- **Sort** compacts and orders the contents with one click.
- Comparators read how full a vault is. Breaking a vault spills its contents, like a chest.
- **Vault Upgrade Kits** upgrade a placed vault one tier in place, keeping its contents, name and facing.
  The Copper kit also upgrades a vanilla chest or barrel (configurable). Kits refuse while someone has the vault open.

## Linked Vault and Linked Pouch

A Linked Vault shares its 27 slots with every Linked Vault set to the same three colours, in any dimension.

- Right-click a latch on the lid with a dye to set that colour.
- Right-click with a diamond (consumed) or the OmniTool to make the frequency **private** to you; others then see a
  different inventory. The OmniTool switches it back.
- Sneak-right-click a Linked Vault with a **Linked Pouch** to bind the pouch, then right-click the pouch anywhere to open
  the same inventory.
- A broken Linked Vault keeps its colours. Hoppers and pipes reach the shared inventory, which makes it a
  cross-dimension item link.

## Trash Can

Anything put in its slot is destroyed; the last item can be taken back until something else replaces it. Pipes and
hoppers that feed it are voided too, and an allow/deny filter decides what automation may void.

## Relocator

Use it on any block with a block entity (a chest, a furnace, a vault, another mod's machine) to pick it up with
everything inside, then use it again to put it down somewhere else. A Relocator has 16 uses; the Netherite Relocator
never wears out. Blocks in the `omnivault:relocator_denylist` tag (spawners, end portals, beds, bedrock-like blocks,
command blocks...) are refused.

## Drawers

| Drawer | Compartments | Each holds (base) |
| --- | --- | --- |
| Drawer 1×1 | 1 | 32 stacks |
| Drawer 1×2 | 2 | 16 stacks |
| Drawer 2×2 | 4 | 8 stacks |
| Compacting Drawer | 3 linked tiers | 8 stacks of the most compressed form |

On the front:

- **Right-click** inserts the held stack; **right-click again quickly** inserts every matching item from your inventory.
- **Left-click** takes a stack, **sneak + left-click** one item. Creative players don't break a drawer by hitting its front.
- **Sneak-right-click** with an empty hand opens the upgrade screen.

Each compartment shows its item and count on the front. Breaking a drawer keeps its contents, upgrades and lock in
the item.

- **Upgrades**: Iron (×2), Gold (×4), Diamond (×8) and Netherite (×16) capacity upgrades add up; a **Void Upgrade**
  destroys overflow of items the drawer already holds.
- **Locking**: the **Drawer Key** or the OmniTool locks a drawer to its current items, so they stay even when empty
  (shown as a ghost).
- **Compacting Drawer**: put in an ingot and it also holds the nugget and block forms (or any item with matching
  3×3/2×2 and reverse recipes, plus quartz, glowstone, clay, snow and amethyst), stored as one amount.
- **Drawer Controller**: connects every drawer touching it (directly, through other drawers or through **Drawer
  Trim**) up to 12 blocks away. Insert into the controller and items go to the drawer that already holds or is locked to
  them, then to empty drawers. Hoppers and the network see all connected drawers through it. The Drawer Key on a
  controller locks or unlocks every drawer.

When a Storage Bus faces a compacting drawer, the network sees each tier (the same item counted as blocks, ingots and
nuggets), as with other drawer mods. Prefer facing a regular drawer or the controller.
