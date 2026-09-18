# Random Teleport (RTP)

`/rtp` (alias `/wild`) teleports a player to a random, safe location within a configurable ring
around a center point. The candidate search runs off the main thread; only the final teleport
touches the world, and — on Folia — it's dispatched through the region that owns the destination
rather than a naive synchronous teleport.

A location is only accepted if the ground is solid, not lava/water/fire/cactus/magma, and there's
clear air above it.

## Configuration (`modules/rtp.yml`)

```yaml
enabled: true
center-x: 0
center-z: 0
min-radius: 100
max-radius: 5000
teleport-warmup-seconds: 3
cooldown-seconds: 0
```

`youneedme.rtp.bypass-cooldown` skips the cooldown for staff testing.
