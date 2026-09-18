# Homes

Multi-homes per player, with a limit controlled by permission nodes rather than a single flat
number — `youneedme.homes.limit.<n>` grants a home limit of `<n>` (the highest matching node wins),
and `youneedme.homes.limit.unlimited` removes the cap entirely.

## Commands

| Command | Description |
| --- | --- |
| `/sethome [name]` | Set a home (defaults to `home` if no name is given) |
| `/delhome <name>` | Delete a home (asks for confirmation by repeating the command) |
| `/home [name]` | Teleport to a home, after a configurable warmup |
| `/homes` | List your homes with their coordinates |
| `/back` | Return to your location before the last teleport |
| `/dback` | Return to your last death location |
| `/top` | Teleport to the highest block at your current X/Z |

## Configuration (`modules/homes.yml`)

```yaml
default-limit: 3
teleport-warmup-seconds: 3
teleport-cooldown-seconds: 0
```

Teleport warmups are cancelled by moving, taking damage, or logging off, matching player
expectations from EssentialsX-style plugins.

## For developers

`HomeSetEvent` fires before a home is created or overwritten and can be cancelled.
