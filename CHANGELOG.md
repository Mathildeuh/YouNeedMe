# Changelog

All notable changes to this project are documented in this file. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

Releases after this point are generated automatically from
[Conventional Commits](https://www.conventionalcommits.org/) by the `release.yml` workflow — this
initial entry was written by hand to seed the file.

## [1.0.0] - Unreleased

### Added

- Multi-module Gradle project (`api`, `core`, `expansions/expansion-api`, `expansions/expansion-placeholders`).
- Economy with a native Vault `Economy` provider, a pre-configured shop, and an auction house.
- Homes, warps, safe random teleport, and the full `/tpa` command family.
- Moderation (bans/mutes/kicks with durations and history), vanish, god mode, `/invsee`/`/endersee`.
- A YAML-configured sidebar scoreboard that yields to TAB when it's installed.
- Nicknames, with Bedrock-safe plain-text fallback when Floodgate is detected.
- Kits with per-kit cooldowns, permissions, and one-time/limited claims.
- LuckPerms integration: live-updated chat prefixes/suffixes.
- The official PlaceholderAPI expansion, plus an example third-party expansion.
- Discord notifications via DiscordSRV (when present) or a raw webhook.
- Dry-run-by-default migration importers from EssentialsX, CMI, and EssentialsC.
- A dynamic expansion loader (`plugins/YouNeedMe/expansions/`) with an isolated classloader per
  expansion and a versioned `expansion-api` module for third-party developers.
- SQLite (default), MySQL/MariaDB, PostgreSQL, MongoDB and JSON storage backends behind one
  `DataStorage` abstraction, all fully asynchronous.
- Folia support through a `SchedulerAdapter` used everywhere instead of `Bukkit.getScheduler()`.
- 28 bundled translations, reloadable at runtime, with per-player language override (`/language`).

[1.0.0]: https://github.com/Mathildeuh/YouNeedMe/releases/tag/v1.0.0
