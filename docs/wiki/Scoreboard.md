# Scoreboard

A YAML-configured sidebar scoreboard, refreshed on a timer. **Automatically disabled** while the
TAB plugin is installed, so the two never fight over the sidebar slot.

## Commands

| Command | Description |
| --- | --- |
| `/scoreboard` (`/sb`) | Toggle the scoreboard for yourself |
| `/scoreboard reload` | Reload the title/lines from config (requires `youneedme.admin`) |

## Configuration (`modules/scoreboard.yml`)

```yaml
enabled: true
refresh-interval-ticks: 20
title: "<gold><bold>YouNeedMe"
lines:
  - ""
  - "<gray>Player: <white>{player}"
  - "<gray>Online: <white>{online}/{max}"
  - ""
  - "<yellow>play.example.com"
```

Built-in tokens: `{player}`, `{online}`, `{max}`, `{server}`. If PlaceholderAPI is installed, any
`%placeholder%` in a line is also resolved — including YouNeedMe's own
[official expansion](API#placeholderapi).
