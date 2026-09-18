# Kits

Kits with per-kit cooldowns, permissions, and one-time or capped claims. Ships with three example
kits (`starter`, `daily`, `vip`) in `kits.yml` — see [Configuration](Configuration.md#kitsyml-data-folder-root)
for the schema.

## Commands

| Command | Description |
| --- | --- |
| `/kit <name>` (or `/kit claim <name>`) | Claim a kit |
| `/kit cooldown <name>` | Check your remaining cooldown for a kit |
| `/kits` (or `/kits list`) | List every kit and whether you can currently claim it |
| `/kits reload` | Reload `kits.yml` |

Claiming checks, in order: permission, one-time/max-claims history, cooldown, then free inventory
space — a kit is never partially granted.

## For developers

`KitClaimEvent` fires (and can be cancelled) right before a kit's items are handed out.
Third-party expansions can register additional kits programmatically via `KitService#register`
without touching `kits.yml` at all — see [Expansions](Expansions.md).
