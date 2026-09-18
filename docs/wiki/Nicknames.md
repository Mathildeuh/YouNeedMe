# Nicknames

Colored (MiniMessage) nicknames, with real-name lookup for staff and a Bedrock-safe fallback.

## Commands

| Command | Description |
| --- | --- |
| `/nick <nickname\|off>` | Set or clear your own nickname |
| `/nick <player> <nickname\|off>` | Set another player's nickname (`youneedme.nick.others`) |
| `/realname <nickname>` | Resolve a nickname back to the real account name |

## Configuration (`modules/nickname.yml`)

```yaml
max-length: 16
blacklist:
  - admin
  - moderator
  - owner
```

The length check strips MiniMessage tags before counting, so `<red>Steve</red>` counts as 5
characters, not 21.

## Floodgate (Bedrock) behavior

If Floodgate is installed and the *target* of `/nick` is a Bedrock player, any MiniMessage tags in
the requested nickname are stripped automatically — Bedrock clients render `<red>Name</red>` as
literal text rather than a color, so a colored nickname would otherwise show up broken to every
Bedrock player who sees it (including the one who set it).
