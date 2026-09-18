# Warps

Public, categorizable warps with per-warp permission and an optional Vault cost.

## Commands

| Command | Description |
| --- | --- |
| `/warp [name]` | Teleport to a warp |
| `/warps` | List every warp visible to you, grouped by category |
| `/setwarp <name>` | Create a warp at your location |
| `/delwarp <name>` | Delete a warp |
| `/warpadmin setperm <name> <perm\|none>` | Restrict/unrestrict a warp |
| `/warpadmin setcost <name> <amount>` | Set a Vault cost to use the warp |
| `/warpadmin setdesc <name> <text>` | Set a description |
| `/warpadmin setcategory <name> <category>` | Group the warp under a category |
| `/warpadmin hide` / `unhide` | Hide a warp from `/warps` (still directly usable if permitted) |
| `/warpadmin move <name>` | Move a warp to your current location |
| `/warpadmin info <name>` | Show every property of a warp |
| `/warpadmin reload` | Reload the warp cache from storage |

## Configuration (`modules/warps.yml`)

```yaml
teleport-warmup-seconds: 3
cooldown-seconds: 0
```

## For developers

`WarpUseEvent` fires after cost/permission checks pass, right before the teleport warmup starts,
and can be cancelled.
