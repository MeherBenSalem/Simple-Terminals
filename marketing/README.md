# Marketing

Listing copy and artwork for CurseForge and Modrinth (the project formerly listed as Simple Terminals). Nothing here
ships in the mod jar except the logo, which is copied to `1.21.1/neoforge/src/main/resources/omnivault_logo.png`.

| Path | What it is |
|------|------------|
| `curseforge/description.md` | The project description (Markdown; also works on Modrinth). Images load from this repo's `main` branch. |
| `curseforge/summary.txt` | The one-line summary (222 characters; the limit is 250). |
| `images/banner.png` | 1800×600 header banner (the hero base capture, the seven cell tiers). |
| `images/icon.png` | 512×512 project icon (transparent corners): the Nexus Core in a machined frame. A 256×256 copy is the mod's `logoFile`. |
| `images/card-*.png` | 1600×900 feature cards used in the description and as gallery images. |
| `images/header-*.png`, `images/divider.png` | 1400 wide, transparent: section headers (120 high) and a divider (40 high). |
| `images/screenshots/*.jpg` | 1920×1080 real in-game captures (Minecraft 1.21.1, NeoForge), used by the cards and the gallery. |
| `source/render.cjs` | Renders every PNG above from the screenshots, the renders and the bundled fonts. |
| `source/renders.py` | Transparent renders of the mod's own models (`source/renders/`) and the two card backdrops. |
| `source/backdrop.jpg`, `source/backdrop-late.jpg` | Blurred card backgrounds (downscaled `base.jpg` and `late.jpg`). |
| `source/fonts/` | Barlow Condensed and Inter (WOFF2, from Fontsource), both under the SIL Open Font License 1.1 (licence files included). |

## Cards

| Card | Shows | Made from |
|------|-------|-----------|
| `card-progression` | Early, mid and late game side by side | `early.jpg`, `base.jpg`, `late.jpg` |
| `card-early` | Chests and barrels on Inventory Cable, the Storage Hub, the merged terminal | `early.jpg`, `early-terminal.jpg` |
| `card-network` | The hero base: drive walls, Storage Monitors, the Nexus Core, both terminals | `base.jpg` |
| `card-cells` | The seven item cell tiers and a loaded Cell Drive with its LEDs | renders |
| `card-automation` | Buses, Network Port, Destructor, Constructor, Signal Emitter, Monitor, Transmitter, upgrade cards | renders |
| `card-terminals` | Storage Terminal (with a tooltip), Crafting Terminal with a recipe, the fluid view | `terminal.jpg`, `crafting.jpg`, `terminal-fluids.jpg` |
| `card-autocrafting` | Assemblers at work, a five-step plan, Crafting Status with two jobs | `autocraft.jpg`, `request.jpg`, `crafting-status.jpg` |
| `card-vaults` | The six tiers with the Omnium Vault open, its 162-slot screen, the slot table | `vaults.jpg`, `vault-gui.jpg` |
| `card-utilities` | Linked Vault and Pouch, Trash Can, vault kit, Relocators, OmniTool, Drawer Key | renders |
| `card-drawers` | A drawer wall with a controller, compacting drawers and a locked drawer | `drawers.jpg` |
| `card-backpacks` | Six placed tiers and one worn (third person), the backpack screen with its Magnet tab, the nine upgrades | `backpacks.jpg`, `backpack-gui.jpg`, renders |
| `card-quantum` | A linked Quantum Bridge pair across a chasm, Singularity drives, Quantum Assemblers | `late.jpg`, `quantum-assembler.jpg`, a render |
| `card-config` | The `omnivault-common.toml` defaults (values from `docs/CONFIG.md`) | text |

## Regenerating the images

Needs Python 3 with Pillow and numpy, Node.js and Playwright with Chromium:

```bash
python3 marketing/source/renders.py                                      # model renders and backdrops
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs              # everything
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs card-cells   # one asset
python3 -c "from PIL import Image; Image.open('marketing/images/icon.png').resize((256, 256), Image.LANCZOS).save('1.21.1/neoforge/src/main/resources/omnivault_logo.png')"
```

`renders.py` drives `tools/assets/render_models.py`, the project's offline model renderer, so regenerated models and
textures (see `tools/assets/`) show up in the next render. The supported Minecraft versions and loaders are at the top of
`render.cjs`; adding one there also needs a row in the compatibility table of `description.md`. The palette is the
mod's own (`client/gui/GuiStyle.java`): gunmetal panels, cyan `#38e1ff` and violet `#a46bff`.

## Retaking the screenshots

`tools/qa/marketing_shots.py` builds every scene with server commands and captures it from the QA client (see
`tools/qa/`): a 1920×1080 window under Xvfb at GUI scale 3 with a 60° field of view (`fov:-0.25`), software rendering,
on a 1.21.1 NeoForge dev server with `level-name=showcase` (superflat), `view-distance=10` and
`broadcast-rcon-to-ops=false`. The player is op'd and in creative, time and weather are frozen, and the HUD is hidden.

```bash
QA_LOGS=/tmp/omnivault-qa tools/qa/devrun.sh setup-server   # then edit level-name and view-distance
QA_LOGS=/tmp/omnivault-qa tools/qa/devrun.sh start-server
QA_LOGS=/tmp/omnivault-qa tools/qa/devrun.sh start-client
python3 tools/qa/marketing_shots.py stock                    # once per world
python3 tools/qa/marketing_shots.py                          # every scene, or name some
```

Nothing in the scenes is faked: networks are built block by block, and every count you see was moved there by the mod
itself. Import Buses empty drawers, chests and cauldrons into the cells; the amber and red drive LEDs come from cells
that were filled to exactly that level one at a time; the crafting plan, the jobs and the working assemblers are real
requests made through the terminal; the Quantum Bridges were entangled with a Quantum Link Card; the open Omnium Vault
lid is held open by a second, invisible player. Every screenshot is a single unedited frame.

| Screenshot | Scene |
|------------|-------|
| `base.jpg` | The hero base: a U of Cell Drives under Storage Monitors, the Nexus Core with a Wireless Transmitter, both terminals |
| `terminal.jpg`, `terminal-fluids.jpg` | The Storage Terminal over the base (with an item tooltip), and its fluid view |
| `crafting.jpg` | The Crafting Terminal with a piston recipe in its grid |
| `early.jpg`, `early-terminal.jpg` | A wooden storeroom: chests and barrels on Inventory Cable with a Storage Hub; the merged terminal |
| `pattern-terminal.jpg` | The Pattern Encoding Terminal with the Logic Processor recipe |
| `request.jpg` | A craft request for 16 Advanced Processors: five steps, from Omnium Dust up |
| `autocraft.jpg`, `crafting-status.jpg` | Assemblers working on a 1,536 sticky piston job; the Crafting Status screen |
| `vaults.jpg`, `vault-gui.jpg` | The six vault tiers with the Omnium Vault open; its 162-slot screen |
| `drawers.jpg` | A drawer wall: 1×1, 1×2 and 2×2 drawers, two compacting drawers, a controller and a locked drawer |
| `backpacks.jpg`, `backpack-gui.jpg` | Six backpacks around a campfire, a Diamond Backpack worn; the backpack screen with five upgrades |
| `late.jpg`, `quantum-assembler.jpg` | A Quantum Bridge pair joining a Singularity drive bank to Quantum Assemblers across a chasm |

## Wording

- Use the mod's own names. Don't put other mods' names, logos or art in listing images or titles, and don't claim
  compatibility with a specific mod that hasn't been tested: say "any mod that exposes NeoForge item or fluid handlers".
- Every number in the copy and on the cards comes from the code defaults and the docs (`docs/PROGRESSION.md`,
  `NETWORK.md`, `AUTOCRAFTING.md`, `STORAGE.md`, `BACKPACKS.md`, `CONFIG.md`). Update the copy when they change.
