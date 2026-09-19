YouNeedMe is an all-in-one, Essentials-style administration and utility plugin for
**Paper / Spigot / Purpur / Folia** servers. One plugin, one config folder, no stack of five
separate essentials/economy/shop/AH plugins fighting each other over `/home`.

It's actively developed, ships bug fixes and features on every release, and is built from the
ground up around Folia support — every scheduled task goes through a scheduler abstraction, so
nothing breaks on a regionised server.

## Highlights

- **Homes, warps & navigation** — multi-homes with per-permission limits, public/categorized
  warps with cost and cooldown support, safe random teleport (`/rtp`), and the full `/tpa` family
  (`/tpa`, `/tpahere`, `/tpaccept`, queueing, ignore lists).
- **Economy & commerce** — a native Vault economy provider (any plugin that speaks Vault works
  out of the box), a pre-configured, fully in-game-editable shop with categorized items, and an
  auction house with real bidding (not just fixed-price listings) and anti-duplication protection.
- **Moderation** — bans, IP bans, mutes and kicks with durations, reasons and full history,
  vanish (with optional cross-server sync over Redis), god mode, `/invsee`/`/endersee`, and a
  one-screen staff dashboard (`/ynm panel`) with online count, top balances, active
  bans/mutes and server uptime at a glance.
- **Kits** — per-kit cooldowns, permissions, one-time or limited claims, and an in-game editor —
  no hand-editing YAML to add a kit.
- **Quality of life** — a configurable scoreboard, nicknames with LuckPerms-aware chat
  formatting, AFK tracking, playtime, and full localization (ships with multiple languages,
  per-player language selection via `/language`).
- **Integrations** — Vault, LuckPerms, PlaceholderAPI, DiscordSRV/webhooks, Floodgate, and TAB
  (auto-yields its scoreboard instead of fighting it).
- **Your choice of storage** — SQLite (zero-setup default), MySQL/MariaDB, PostgreSQL, MongoDB,
  or flat JSON. Swap backends with a single config key; a small network runs happily on SQLite,
  a bigger one points every node at the same MySQL/MariaDB/PostgreSQL database.
- **Built for extension** — a versioned, documented `api` module and a dynamic expansion loader
  (drop a jar in `plugins/YouNeedMe/expansions/`) so third-party add-ons are isolated and can't
  take the whole server down if something goes wrong.
- **Migration tools** — dry-run-by-default importers for EssentialsX, CMI and EssentialsC, with
  an explicit `--apply` step when you are ready to migrate data.

## Requirements

- Java 21 or newer.
- Paper is recommended; Spigot, Purpur, Folia and compatible forks such as Leaf and Leaves are
  supported.
- Minecraft 26.2 and newer; Folia is supported through the same scheduler abstraction used by
  the rest of the plugin.

## Why YouNeedMe over a stack of separate plugins

Running Essentials + an economy plugin + a shop plugin + an AH plugin + a kits plugin means five
different config styles, five sets of permissions, and five plugins that all *sort of* agree on
what a "home" or a "balance" is. YouNeedMe is one coherent data model and one permission
namespace (`youneedme.*`) from the start, with in-game GUI editors for the parts server owners
usually end up hand-editing YAML for (shop, kits, auction house).

## Installation

1. Download the latest `YouNeedMe.jar` from [GitHub Releases](https://github.com/Mathildeuh/YouNeedMe/releases/latest),
   [Modrinth](https://modrinth.com/plugin/youneedme) or
   [Hangar](https://hangar.papermc.io/Mathildeuh/youneedme).
2. Drop the jar into your server's `plugins/` folder and restart.
3. Edit `plugins/YouNeedMe/config.yml` and `plugins/YouNeedMe/modules/*.yml` to taste, then run
   `/ynm reload`.

No database setup required to get started — YouNeedMe runs on an embedded SQLite database by
default. For a multi-server network, point `storage.type` at `mysql`, `mariadb`, `postgresql` or
`mongodb` in `config.yml` and every node shares the same data.

## Links

- [Source & issue tracker](https://github.com/Mathildeuh/YouNeedMe)
- [Wiki — full command/permission reference & configuration guide](https://github.com/Mathildeuh/YouNeedMe/wiki)
- [API docs (Javadoc)](https://mathildeuh.github.io/YouNeedMe/) for expansion authors
- [Modrinth project](https://modrinth.com/plugin/youneedme)
- [Hangar project](https://hangar.papermc.io/Mathildeuh/youneedme)

Licensed under Apache-2.0 — build on it, fork it, or ship your own expansion against its `api`
module without friction.
