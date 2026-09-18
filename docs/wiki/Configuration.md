# Configuration

Every setting is reloadable at runtime with `/ynm reload` — no restart required. Messages are
never hard-coded in the plugin's Java code; they all live in `lang/<locale>.json`, so changing
wording never requires waiting for a plugin update.

## `config.yml`

| Key | Purpose |
| --- | --- |
| `language.default` | Locale used when a player has no override and no matching client locale |
| `storage.type` | `sqlite` \| `mysql` \| `mariadb` \| `postgresql` \| `mongodb` \| `json` |
| `storage.<type>.*` | Connection details for the selected backend |
| `modules.<name>.enabled` | Turn a whole feature module on/off |
| `navigation.spawn` | Set automatically by `/setspawn` |
| `navigation.back-cooldown-seconds` | Cooldown between `/back` uses |
| `utility.spawnentity-max-amount` | Cap for `/spawnentity <entity> <amount>` |
| `utility.rename-max-length` | Max length for `/rename` |
| `rules` | Lines shown by `/rules`, MiniMessage-formatted |

## `modules/*.yml`

Each feature module has its own file so the config stays readable, rather than one giant file:

| File | Key settings |
| --- | --- |
| `economy.yml` | `starting-balance`, `minimum-balance`, `maximum-balance`, `currency.*` |
| `shop.yml` | `enabled` — the catalog itself is in `shop.yml` at the data folder root |
| `auctionhouse.yml` | `listing-duration-hours`, `listing-tax-rate`, `minimum-price`, `expiry-sweep-interval-seconds` |
| `homes.yml` | `default-limit`, `teleport-warmup-seconds`, `teleport-cooldown-seconds` |
| `warps.yml` | `teleport-warmup-seconds`, `cooldown-seconds` |
| `rtp.yml` | `enabled`, `center-x`/`center-z`, `min-radius`/`max-radius`, warmup/cooldown |
| `tpa.yml` | `request-expiry-seconds` |
| `moderation.yml` | reserved for future settings — permissions are declared in `plugin.yml` |
| `scoreboard.yml` | `enabled`, `refresh-interval-ticks`, `title`, `lines` (MiniMessage, `{player}`/`{online}`/`{max}`/`{server}` tokens, plus any `%papi_placeholder%` if PlaceholderAPI is installed) |
| `nickname.yml` | `max-length`, `blacklist` |
| `kits.yml` | reserved — kit definitions themselves are in `kits.yml` at the data folder root |
| `discord.yml` | `invite-url`, `webhook-url`, `notify.*` toggles per event type |
| `migration.yml` | reserved for future settings |
| `chat.yml` | `enabled`, `format` — only takes effect when LuckPerms is installed |

## `shop.yml` (data folder root)

```yaml
categories:
  <category-id>:
    display-name: "<green>Blocks"
    icon: GRASS_BLOCK       # any Material
    order: 0                # display order, lowest first
    permission: some.perm   # optional - omit for no restriction
    items:
      <item-id>:
        material: DIRT
        buy-price: 1.0      # omit to make the item unbuyable
        sell-price: 0.25    # omit to make the item unsellable
        stock: 10           # omit for unlimited stock
```

## `kits.yml` (data folder root)

```yaml
kits:
  <kit-id>:
    display-name: "<gold>Starter Kit"
    permission: youneedme.kit.starter   # optional
    cooldown-seconds: 0
    one-time: true
    max-claims: 5             # optional cap independent of one-time
    items:
      - { material: BREAD, amount: 16 }
```
