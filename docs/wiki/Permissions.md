# Permissions

Generated from `plugin.yml`. Like EssentialsX, most permission nodes have **no explicit default**,
which Bukkit resolves to *op-only* — this is deliberate: as an administration/utility plugin,
YouNeedMe expects server owners to grant the nodes they want (via LuckPerms or similar) to their
`default`/member group rather than opening everything by default. Nodes with an explicit default
are marked below; everything else is op-only until granted.

## Command permissions

| Permission | Command(s) | Default |
| --- | --- | --- |
| `youneedme.afk` | `/afk` | op-only |
| `youneedme.afklist` | `/afklist` | op-only |
| `youneedme.ah.use` | `/ah` | op-only |
| `youneedme.anvil` | `/anvil` | op-only |
| `youneedme.back` | `/back` | op-only |
| `youneedme.balance` | `/balance` | op-only |
| `youneedme.baltop` | `/baltop` | op-only |
| `youneedme.ban` | `/ban` | **op** |
| `youneedme.banip` | `/ban-ip` | **op** |
| `youneedme.banlist` | `/banlist` | **op** |
| `youneedme.broadcast` | `/broadcast` | **op** |
| `youneedme.checkpunish` | `/checkpunish` | **op** |
| `youneedme.clearinventory` | `/clearinventory` | **op** |
| `youneedme.craftingtable` | `/craftingtable` | op-only |
| `youneedme.dback` | `/dback` | op-only |
| `youneedme.delhome` | `/delhome` | op-only |
| `youneedme.delwarp` | `/delwarp` | **op** |
| `youneedme.discord` | `/discord` | op-only |
| `youneedme.eco.admin` | `/eco` | **op** |
| `youneedme.enchant` | `/enchant` | **op** |
| `youneedme.enderchest` | `/enderchest` | op-only |
| `youneedme.endersee` | `/endersee` | **op** |
| `youneedme.admin` | `/ynm`, `/user`, `/mysql` | **op** (see below — includes many children) |
| `youneedme.feed` | `/feed` | op-only |
| `youneedme.fly` | `/fly` | **op** |
| `youneedme.gamemode` | `/gamemode`, `/gm` | **op** |
| `youneedme.gamemode.adventure` | `/gma` | **op** |
| `youneedme.gamemode.creative` | `/gmc` | **op** |
| `youneedme.gamemode.survival` | `/gms` | **op** |
| `youneedme.gamemode.spectator` | `/gmsp` | **op** |
| `youneedme.god` | `/god` | **op** |
| `youneedme.hat` | `/hat` | op-only |
| `youneedme.heal` | `/heal` | op-only |
| `youneedme.home` | `/home` | op-only |
| `youneedme.homes` | `/homes` | op-only |
| `youneedme.ignore` | `/ignore` | op-only |
| `youneedme.invsee` | `/invsee` | **op** |
| `youneedme.itemid` | `/itemid` | **true (everyone)** |
| `youneedme.kick` | `/kick` | **op** |
| `youneedme.kit` | `/kit` | op-only |
| `youneedme.kits.list` | `/kits` | op-only |
| `youneedme.language` | `/language` | op-only |
| `youneedme.migration` | `/migration` | **op** |
| `youneedme.msg` | `/msg` | op-only |
| `youneedme.mute` | `/mute` | **op** |
| `youneedme.nick` | `/nick` (self) | op-only |
| `youneedme.nick.others` | `/nick <player>` | **op** |
| `youneedme.pay` | `/pay` | op-only |
| `youneedme.ping` | `/ping` | op-only |
| `youneedme.playerlist` | `/playerlist` | op-only |
| `youneedme.playtime` | `/playtime` | op-only |
| `youneedme.ptime` | `/ptime` | op-only |
| `youneedme.pweather` | `/pweather` | op-only |
| `youneedme.quicksell.hand` | `/quicksell` | op-only |
| `youneedme.realname` | `/realname` | op-only |
| `youneedme.rename` | `/rename` | op-only |
| `youneedme.repair` | `/repair` | op-only |
| `youneedme.reply` | `/reply` | op-only |
| `youneedme.rtp` | `/rtp` | op-only |
| `youneedme.rtp.bypass-cooldown` | (modifier) | op-only |
| `youneedme.rules` | `/rules` | op-only |
| `youneedme.scoreboard.toggle` | `/scoreboard` | op-only |
| `youneedme.seen` | `/seen` | op-only |
| `youneedme.sell` | `/sell` | op-only |
| `youneedme.sethome` | `/sethome` | op-only |
| `youneedme.setspawn` | `/setspawn` | **op** |
| `youneedme.spawn.others` | `/spawn <player>` | **op** |
| `youneedme.setwarp` | `/setwarp` | **op** |
| `youneedme.shop` | `/shop` | op-only |
| `youneedme.shop.blackmarket` | example restricted shop category | op-only |
| `youneedme.smite` | `/smite` | **op** |
| `youneedme.spawn` | `/spawn` | op-only |
| `youneedme.spawnentity` | `/spawnentity` | **op** |
| `youneedme.spawnentity.<entity>` / `.* ` | which entities `/spawnentity` allows | op-only |
| `youneedme.speed` | `/speed` | **op** |
| `youneedme.stonecutter` | `/stonecutter` | op-only |
| `youneedme.sudo` | `/sudo` | **op** |
| `youneedme.suicide` | `/suicide` | op-only |
| `youneedme.top` | `/top` | op-only |
| `youneedme.tpa` / `.tpahere` / `.tpaccept` / `.tpaignore` / `.tpaqueue` / `.tpatoggle` / `.tpcancel` / `.tpdeny` | the `/tpa*` family | op-only |
| `youneedme.tphere` / `.tphereall` / `.tpoffline` | admin-style teleport commands | op-only |
| `youneedme.trash` | `/trash` | op-only |
| `youneedme.unban` | `/unban` | **op** |
| `youneedme.unbanip` | `/unban-ip` | **op** |
| `youneedme.unenchant` | `/unenchant` | **op** |
| `youneedme.unmute` | `/unmute` | **op** |
| `youneedme.uptime` | `/uptime` | op-only |
| `youneedme.vanish` | `/vanish` | **op** |
| `youneedme.vanish.see` | who can still see a vanished player | **op** |
| `youneedme.warp` | `/warp` | op-only |
| `youneedme.warpadmin` | `/warpadmin` | **op** |
| `youneedme.warps` | `/warps` | op-only |
| `youneedme.worth.hand` | `/worth` | op-only |

## Modifier / non-command permissions

| Permission | Effect |
| --- | --- |
| `youneedme.homes.limit.<n>` | Raises a player's home limit to `<n>` (highest matching node wins) |
| `youneedme.homes.limit.unlimited` | Unlimited homes (default: `false`) |
| `youneedme.exempt.<action>` | Exempts a player from that moderation action (`ban`, `mute`, `kick`, `sudo`) (default: `false`) |

## `youneedme.admin`

A single node (default: **op**) that grants every moderation/admin permission as a child:
`eco.admin`, `warpadmin`, `ban`, `banip`, `banlist`, `unban`, `unbanip`, `mute`, `unmute`, `kick`,
`checkpunish`, `smite`, `vanish`, `invsee`, `endersee`, `sudo`, `clearinventory`, `setwarp`,
`delwarp`, `setspawn`, `kits.list`, `migration`. Granting `youneedme.admin` alone gives full
administrative access without listing each node individually.
