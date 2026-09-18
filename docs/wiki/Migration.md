# Migration

`/migration <source> [--apply]` imports homes, warps and balances from another Essentials-style
plugin. **Dry-run by default** — run it without `--apply` first to see a report of what would be
imported before writing anything. Applying only ever adds rows that don't already exist in
YouNeedMe; it never overwrites or deletes existing YouNeedMe data.

## Supported sources

| Source | Reads from | Notes |
| --- | --- | --- |
| `essentialsx` | `plugins/Essentials/userdata/<uuid>.yml`, `plugins/Essentials/warps/<name>.yml` | The most common source |
| `cmi` | `plugins/CMI/Users/<uuid>.yml`, `plugins/CMI/warps.yml` | YAML-backed CMI installs only — a SQL-backed CMI install should export to YAML first |
| `essentialsc` | `plugins/EssentialsC/...` | Same reader as EssentialsX, since EssentialsC's on-disk format is close to 1:1 with it |

## Example

```
/migration essentialsx
# review the dry-run report...
/migration essentialsx --apply
```

Entries that couldn't be parsed (missing location data, non-UUID filenames, etc.) are logged in the
report rather than failing the whole migration silently.
