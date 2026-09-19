# Changelog

All notable changes to this project are documented in this file. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

Releases after this point are generated automatically from
[Conventional Commits](https://www.conventionalcommits.org/) by the `release.yml` workflow — this
initial entry was written by hand to seed the file.

## [1.6.0](https://github.com/Mathildeuh/YouNeedMe/compare/v1.5.1...v1.6.0) (2026-09-19)


### Features

* player-run shops (chest + sign, ChestShop-style) ([3f6191f](https://github.com/Mathildeuh/YouNeedMe/commit/3f6191f12762f8f5c4b4c7e3131df13fd79d1a60))

## [1.5.1](https://github.com/Mathildeuh/YouNeedMe/compare/v1.5.0...v1.5.1) (2026-09-18)


### Bug Fixes

* stop Qodana workflow failing (and emailing) until QODANA_TOKEN is set up ([a7e0f86](https://github.com/Mathildeuh/YouNeedMe/commit/a7e0f86762bf610560bfd8fc85d7029dce42ce8e))

## [1.5.0](https://github.com/Mathildeuh/YouNeedMe/compare/v1.4.0...v1.5.0) (2026-09-18)


### Features

* real bidding on the auction house, not just fixed-price listings ([c74a971](https://github.com/Mathildeuh/YouNeedMe/commit/c74a9711d22082c812f9c7c2bd97b5ee8f115045))


### Bug Fixes

* chain the publish/javadoc jobs onto release-please instead of a separate tag-triggered workflow ([cb12b2b](https://github.com/Mathildeuh/YouNeedMe/commit/cb12b2b81156372e073647f9a76e198c092c4b14))
* replace two nonexistent GitHub Actions in the release workflow with real ones ([5218fee](https://github.com/Mathildeuh/YouNeedMe/commit/5218feed75d1165c15d0001493705054b26becd2))

## [1.4.0](https://github.com/Mathildeuh/YouNeedMe/compare/v1.3.3...v1.4.0) (2026-09-18)


### Features

* add /ynm panel, a one-screen staff dashboard GUI ([dd83ea9](https://github.com/Mathildeuh/YouNeedMe/commit/dd83ea9b5b9841db36c6b23a04bdff98ed6ea9f9))

## [1.3.3](https://github.com/Mathildeuh/YouNeedMe/compare/v1.3.2...v1.3.3) (2026-09-18)


### Bug Fixes

* remove environment block from javadoc job - an unresolvable environment reference can block the whole workflow run, not just that job ([95be936](https://github.com/Mathildeuh/YouNeedMe/commit/95be93624dae0d549af6fa72b5afb2f524336dc3))

## [1.3.2](https://github.com/Mathildeuh/YouNeedMe/compare/v1.3.1...v1.3.2) (2026-09-18)


### Bug Fixes

* click:run_command arguments never actually resolved a nested placeholder ([920eeee](https://github.com/Mathildeuh/YouNeedMe/commit/920eeee776ba34458a540bff436e92f70e8bc2da))

## [1.3.1](https://github.com/Mathildeuh/YouNeedMe/compare/v1.3.0...v1.3.1) (2026-09-18)


### Bug Fixes

* remove duplicate migration.status.managers key in hi_IN.json ([daa72f8](https://github.com/Mathildeuh/YouNeedMe/commit/daa72f8b600d935868f3560777e91cc30883bd61))

## [1.3.0](https://github.com/Mathildeuh/YouNeedMe/compare/v1.2.0...v1.3.0) (2026-09-18)


### Features

* in-game kit/shop editors and a fully GUI-driven auction house ([1688be3](https://github.com/Mathildeuh/YouNeedMe/commit/1688be3815004589fa8c406327ec800c895d50a4))

## [1.2.0](https://github.com/Mathildeuh/YouNeedMe/compare/v1.1.0...v1.2.0) (2026-09-18)


### Features

* full item metadata in configs, admin /home, hardened nicknames, dialog input ([ea106b0](https://github.com/Mathildeuh/YouNeedMe/commit/ea106b0d19bd7baad311584724e82813a0d13c1e))

## [1.1.0](https://github.com/Mathildeuh/YouNeedMe/compare/v1.0.0...v1.1.0) (2026-09-18)


### Features

* **core:** base project ([f6f5ba4](https://github.com/Mathildeuh/YouNeedMe/commit/f6f5ba48ad9b8594985d0e96a7a31e3793820be5))
* restructure into multi-module (api/core/expansions) + foundations ([219fbe5](https://github.com/Mathildeuh/YouNeedMe/commit/219fbe510691026f01dd73afb61ce88a87b29b77))
* third-party integrations, expansion loader, migration importers, docs, CI/CD, tests ([5dfed09](https://github.com/Mathildeuh/YouNeedMe/commit/5dfed09d50bf1a91ad3b6654ce92627da6a22582))
* wire every remaining module and add third-party integrations ([26f253c](https://github.com/Mathildeuh/YouNeedMe/commit/26f253c745561cefe61a71c08186d3ddbc346862))


### Bug Fixes

* checkstyleMain crashed - JavadocMethod properties don't exist on Checkstyle 14 ([7e17ce0](https://github.com/Mathildeuh/YouNeedMe/commit/7e17ce0a51986f1aced2b0ddccc47c31c58161e4))
* mark gradlew executable - CI failed with 'Permission denied' otherwise ([1a88079](https://github.com/Mathildeuh/YouNeedMe/commit/1a88079ab80d2bf8013b4c4a5d7725e0a97d67e2))

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
