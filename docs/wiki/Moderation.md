# Moderation

Bans, IP bans, mutes and kicks, with relative durations (`1d2h30m`, `45m`, or `perm`/`permanent`),
mandatory reasons, and full history per player. Punishments are enforced at the door — a banned
player (or a banned IP) is rejected at `AsyncPlayerPreLoginEvent`, not just kicked if already
online — and a muted player's chat is blocked with the reason shown back to them.

## Commands

| Command | Description |
| --- | --- |
| `/ban <player> [duration] [reason]` | Ban a player |
| `/ban-ip <player\|ip> [duration] [reason]` | Ban an IP (resolves an online player's IP automatically) |
| `/unban <player>` / `/unban-ip <ip>` | Revoke a ban |
| `/banlist [players\|ips] [page]` | List active bans |
| `/mute <player> [duration] [reason]` / `/unmute <player>` | Mute/unmute |
| `/kick <player> [reason]` | Kick a player |
| `/checkpunish <player>` | Show a player's active ban/mute status |
| `/smite <player>` | Strike a player with lightning |
| `/god [player]` | Toggle damage immunity |
| `/vanish` (`/v`) | Become invisible to players without `youneedme.vanish.see` |
| `/invsee <player>` / `/endersee <player>` | View another player's inventory/ender chest |
| `/sudo <player> <command\|message>` | Force a player to run a command or say something |
| `/clearinventory [player]` | Clear an inventory |

`youneedme.exempt.<action>` (`ban`, `mute`, `kick`, `sudo`) exempts a player from that specific
action even for staff who would otherwise be able to target them.

## Discord notifications

See [Discord](Discord.md) — bans/mutes/kicks can be relayed to a Discord channel automatically.

## For developers

`PunishmentIssuedEvent` fires before a punishment is persisted and can be cancelled;
`PunishmentRevokedEvent` fires after an unban/unmute.
