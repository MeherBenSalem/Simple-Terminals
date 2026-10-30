# Autocrafting

Teach the network recipes and it crafts what you ask for, sub-ingredients included.

## The parts

| Block / item | Role |
| --- | --- |
| Blank Pattern | Encoded in the Pattern Encoding Terminal. |
| Crafting Pattern | A crafting-table recipe (3×3). Remainders such as empty buckets go back to the network. |
| Processing Pattern | Up to 9 inputs → up to 3 outputs, for furnaces and machines from any mod. |
| Pattern Encoding Terminal | A terminal with a pattern editor: set a 3×3 grid (and outputs in processing mode), put in a blank pattern, press Encode. Put an encoded pattern back in to edit it. With JEI, the `+` button on any recipe fills the editor: crafting recipes in crafting mode, furnace and machine recipes in processing mode. |
| Pattern Provider | Holds 9 patterns. Pushes processing inputs into the block it faces; the results come back through an Import Bus, a pipe, or by inserting them into the provider. |
| Assembler | Carries out crafting-pattern steps. Faster with Speed Upgrades (up to 4). |
| Quantum Assembler | Late game: sixteen times the crafts per cycle of an Assembler, before Speed Upgrades. |
| Crafting Processor | Each one lets the network run one crafting job at a time. No processor, no autocrafting. Right-click it for the Crafting Status screen. |

## Requesting a craft

1. Craftable items show **Craft** in every terminal (and the wireless terminal). Middle-click or Ctrl + click one.
2. Enter an amount and press **Calculate**. The plan lists what it will use from storage, what it will craft and anything
   missing.
3. **Start** reserves the stored ingredients (nothing else can take them) and runs the job. Cancel from Crafting Status
   to get every reserved item back.

The planner uses stored items first, crafts only the shortfall, batches repeated crafts, picks the first pattern whose
whole sub-tree can work when several patterns make the same item, and stops runaway loops (such as ingots ↔ nuggets).

## Processing example: smelting

1. Encode a processing pattern: 1 Raw Iron → 1 Iron Ingot (or press `+` on JEI's furnace recipe).
2. Put it in a Pattern Provider facing (or above) a fuelled furnace.
3. Get the ingots back into the network: an Import Bus on the furnace's output side, or a hopper/pipe into the
   provider.
4. Request iron ingots from any terminal.

## Limits

Patterns match items exactly (no tag substitution yet). Any matching item that enters the network while a job waits
for a machine counts towards that job. A job's size is unlimited; its count is one per Crafting Processor.
