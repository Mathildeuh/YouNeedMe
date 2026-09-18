# Installation

## Requirements

- Java 21 or newer.
- Paper (recommended), Spigot, Purpur, Folia, or a compatible fork (Leaf, Leaves) — Minecraft 26.2,
  with 26.3 supported as soon as it's stable.

## Steps

1. Download `YouNeedMe-<version>.jar` from
   [Releases](https://github.com/Mathildeuh/YouNeedMe/releases/latest) or
   [Modrinth](https://modrinth.com/plugin/youneedme).
2. Place it in your server's `plugins/` folder.
3. Start (or restart) the server. On first boot, YouNeedMe creates:
   - `plugins/YouNeedMe/config.yml` — main configuration (storage backend, language).
   - `plugins/YouNeedMe/modules/*.yml` — one file per feature module.
   - `plugins/YouNeedMe/shop.yml` — the shop catalog, pre-filled with example categories/items.
   - `plugins/YouNeedMe/kits.yml` — kit definitions, pre-filled with example kits.
   - `plugins/YouNeedMe/lang/*.json` — bundled translations.
4. Edit whichever files you need, then run `/ynm reload` (no restart required for most settings).

## Storage backend

YouNeedMe ships with SQLite by default — zero configuration, single-server. For a network running
multiple servers against shared data, set `storage.type` in `config.yml` to `mysql`, `mariadb`,
`postgresql`, or `mongodb` and fill in the matching connection block. A `json` backend is also
available for very small servers or debugging.

Every storage operation is fully asynchronous; switching backends never requires code changes in
any command or service.

## Soft-depend integrations

None of the following are required — YouNeedMe detects and uses them automatically if present, and
degrades gracefully if not:

| Plugin | What it enables |
| --- | --- |
| Vault | YouNeedMe registers itself as the Vault `Economy` provider |
| LuckPerms | Chat prefixes/suffixes, live-updated on permission changes |
| PlaceholderAPI | The official `%youneedme_*%` placeholder expansion |
| DiscordSRV | Moderation/join/quit notifications relayed through it instead of a raw webhook |
| Floodgate | Bedrock players get plain-text (non-MiniMessage) nicknames |
| TAB | YouNeedMe's own sidebar scoreboard yields to TAB's, avoiding a display conflict |

## Docker test environment

See [`docker-compose.yml`](../../docker-compose.yml) at the repository root for a ready-to-run
Paper + MariaDB + Redis stack for local testing/contribution.
