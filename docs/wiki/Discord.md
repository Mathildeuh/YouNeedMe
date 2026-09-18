# Discord

Relays bans/mutes/kicks and (optionally) joins/quits to Discord. When **DiscordSRV** is installed,
messages go through it (to its configured main channel) automatically; otherwise, a plain webhook
is used if one is configured. DiscordSRV always takes priority when both are available.

## Commands

| Command | Description |
| --- | --- |
| `/discord` | Shows the configured Discord invite link |

## Configuration (`modules/discord.yml`)

```yaml
invite-url: ""
webhook-url: ""

notify:
  bans: true
  mutes: true
  kicks: true
  warns: false
  joins: false
  quits: false
```

Each notification category can be toggled independently. With DiscordSRV installed, no
`webhook-url` is needed at all.
