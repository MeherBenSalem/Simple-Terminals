# Configuration

OmniVault writes one file, `config/omnivault-common.toml`, on first launch. Every value has a comment with its
default and allowed range; out-of-range values are clamped and invalid ones fall back to the default (a warning is
logged). The file is rewritten on load so new options always appear. Restart the game or server after editing.

The network's options are listed below. Each feature adds its own section to the same file (`[vaults]`,
`[drawers]`, `[backpacks]`, `[autocrafting]` and so on); open the file to see all of them with their comments.

## `[network]`

| Key | Default | Meaning |
| --- | --- | --- |
| `maxNodes` | 8192 | Most cables and devices one Nexus Core scans. |
| `requireEnergy` | false | Networks need FE delivered to the Nexus Core to run. |
| `coreEnergyBuffer` | 1000000 | FE the Nexus Core can buffer. |
| `coreEnergyUsage` | 8 | FE per tick used by the core itself (devices add their own). |

## `[cells]`

| Key | Default | Meaning |
| --- | --- | --- |
| `typeLimit` | 128 | Different items one storage cell can hold. |
| `fluidTypeLimit` | 32 | Different fluids one fluid cell can hold. |
| `singularityTypeLimit` | 1024 | Different items (or fluids) one Singularity cell can hold. |

## `[wireless]`

| Key | Default | Meaning |
| --- | --- | --- |
| `coreRange` | 32 | Blocks a wireless terminal reaches from the Nexus Core itself. |
| `transmitterRange` | 64 | Blocks a Wireless Transmitter reaches without upgrades. |
| `rangePerUpgrade` | 48 | Extra blocks per Range Upgrade. |

## `[buses]`

| Key | Default | Meaning |
| --- | --- | --- |
| `baseInterval` | 20 | Ticks between bus, Destructor and Constructor operations; each Speed Upgrade halves it. |
| `baseItemsPerOperation` | 8 | Items a bus moves per operation; each Stack Upgrade doubles it. Fluids move 125 mB per item. |
