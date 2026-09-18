# YouNeedMe

[![CI](https://github.com/Mathildeuh/YouNeedMe/actions/workflows/ci.yml/badge.svg)](https://github.com/Mathildeuh/YouNeedMe/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Mathildeuh/YouNeedMe?label=release)](https://github.com/Mathildeuh/YouNeedMe/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Paper API](https://img.shields.io/badge/paper--api-26.2%2F26.3-orange)](https://papermc.io)
[![Modrinth](https://img.shields.io/badge/modrinth-YouNeedMe-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/youneedme)

**YouNeedMe** is an all-in-one, Essentials-style administration and utility plugin for
Paper/Spigot/Purpur/**Folia** servers — homes, warps, kits, an economy with a native Vault
provider, a shop, an auction house, moderation, a configurable scoreboard, nicknames, Discord
notifications and more, in a single modern, actively maintained plugin.

It's directly inspired by [EssentialsC](https://github.com/GodlyCow203/EssentialsC)'s architecture
(a public `api` module, an `expansions/` system, `commands.yml`-style configurability) — rewritten
from scratch, not forked, and licensed the same way (Apache-2.0) for the same reason: server owners
and expansion authors should be able to build on it without friction.

## Features

| Category | What's included |
| --- | --- |
| Economy & Commerce | Internal economy (native Vault provider), a pre-configured shop with categorized items, an auction house with anti-duplication protection |
| Navigation | Multi-homes, public/categorized warps, safe random teleport (`/rtp`), the full `/tpa` family |
| Administration | Bans/mutes/kicks with durations and history, vanish, god mode, `/invsee`/`/endersee`, a configurable scoreboard, nicknames |
| Kits | Per-kit cooldowns, permissions, one-time/limited claims |
| Integrations | Vault, LuckPerms (live-updated chat prefixes/suffixes), PlaceholderAPI, DiscordSRV/webhooks, Floodgate, TAB (auto-yields its scoreboard) |
| Storage | SQLite (default), MySQL/MariaDB, PostgreSQL, MongoDB or flat JSON — swap with one config key |
| Folia | Every scheduled task goes through a `SchedulerAdapter`; no plugin code ever touches `Bukkit.getScheduler()` directly |
| Expansions | A versioned `expansion-api` module and a dynamic loader (`plugins/YouNeedMe/expansions/`) for third-party add-ons, isolated so a broken expansion can't take the server down |
| Migration | Dry-run-by-default importers from EssentialsX, CMI and EssentialsC |

See the [wiki](https://github.com/Mathildeuh/YouNeedMe/wiki) for the full command/permission
reference and configuration guide.

## Requirements

- Java 21+
- Paper (recommended), Spigot, Purpur, Folia, or a compatible fork (Leaf, Leaves) — Minecraft 26.2,
  with 26.3 supported as soon as it's stable.

## Installation

1. Download the latest `YouNeedMe-<version>.jar` from
   [Releases](https://github.com/Mathildeuh/YouNeedMe/releases/latest) or
   [Modrinth](https://modrinth.com/plugin/youneedme).
2. Drop it into your server's `plugins/` folder and restart.
3. Edit `plugins/YouNeedMe/config.yml` and `plugins/YouNeedMe/modules/*.yml` to taste, then
   `/ynm reload`.

By default YouNeedMe runs on an embedded SQLite database with no further setup required. For a
multi-server network, set `storage.type` to `mysql`, `mariadb` or `postgresql` in `config.yml`.

## Building from source

```bash
git clone https://github.com/Mathildeuh/YouNeedMe.git
cd YouNeedMe
./gradlew build
```

The finished plugin jar is written to `build/libs/YouNeedMe-<version>.jar`.

## Developing against the API

Third-party plugins and expansions should `compileOnly` against the `api` module rather than the
`core` implementation:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.Mathildeuh.YouNeedMe:api:<version>")
}
```

```java
YouNeedMeAPI.economy().ifPresent(economy ->
        economy.balance(player.getUniqueId())
                .thenAccept(balance -> player.sendMessage("Balance: " + balance)));
```

See the [API wiki page](https://github.com/Mathildeuh/YouNeedMe/wiki/API) for the full service
list and event catalog, and the [Expansions wiki page](https://github.com/Mathildeuh/YouNeedMe/wiki/Expansions)
for writing a loadable expansion instead of a standalone plugin.

## Contributing

Issues and pull requests are welcome. Before opening a PR:

- Run `./gradlew spotlessApply` (formatting is enforced, not just suggested).
- Run `./gradlew build` and make sure it passes.
- Keep the `api` module free of implementation details — it's what third parties compile against.

### Local test server

`docker-compose.yml` at the repository root spins up Paper + MariaDB + Redis with the freshly built
plugin jar mounted in:

```bash
./gradlew build
docker compose up -d
```

See the comments in `docker-compose.yml` for pointing `config.yml` at the MariaDB service.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
