# API

The `api` module is a small, stable, `compileOnly`-only dependency with zero implementation
details — depend on it, never on `core`.

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.Mathildeuh.YouNeedMe:api:<version>")
}
```

JitPack builds the `api` module on demand from any tag or commit of this repository — the first
request for a given ref triggers the build.

## Getting a service

Every service is registered on Bukkit's `ServicesManager` (exactly like Vault or LuckPerms), and
`YouNeedMeAPI` is a thin static facade over the same lookups so you don't have to write the
boilerplate yourself:

```java
import fr.mathildeuh.youneedme.api.YouNeedMeAPI;

YouNeedMeAPI.economy().ifPresent(economy ->
        economy.balance(player.getUniqueId())
                .thenAccept(balance -> player.sendMessage("Balance: " + balance)));
```

`Optional` is empty when YouNeedMe (or the specific module backing that service) isn't loaded —
always handle that case rather than assuming presence.

## Available services

| Service | Accessor | Covers |
| --- | --- | --- |
| `EconomyService` | `YouNeedMeAPI.economy()` | Balances, transfers, currencies |
| `HomeService` | `YouNeedMeAPI.homes()` | Multi-homes |
| `WarpService` | `YouNeedMeAPI.warps()` | Warps |
| `KitService` | `YouNeedMeAPI.kits()` | Kits, including registering new ones programmatically |
| `AuctionHouseService` | `YouNeedMeAPI.auctionHouse()` | Auction house listings |
| `ShopService` | `YouNeedMeAPI.shop()` | Shop categories/items, buy/sell |
| `ModerationService` | `YouNeedMeAPI.moderation()` | Bans/mutes/kicks and history |
| `ScoreboardService` | `YouNeedMeAPI.scoreboard()` | Per-player scoreboard toggle |
| `NicknameService` | `YouNeedMeAPI.nicknames()` | Nickname read/write, real-name resolution |
| `DataStorage` | `YouNeedMeAPI.storage()` | Direct repository access (advanced use only) |
| `SchedulerAdapter` | `YouNeedMeAPI.scheduler()` | Folia-safe scheduling, reusable in your own plugin |

Every method returns a `CompletableFuture` (storage-backed services) or is synchronous where no I/O
is involved (e.g. `KitService#kits()`), and every mutating method is safe to call from any thread.

## Events

All under `fr.mathildeuh.youneedme.api.event`, standard Bukkit events (listen with
`@EventHandler` as usual):

| Event | Fired | Cancellable |
| --- | --- | --- |
| `HomeSetEvent` | Before a home is created/overwritten | Yes |
| `WarpUseEvent` | Before a warp teleport starts (after cost/permission checks) | Yes |
| `KitClaimEvent` | Before a kit's items are handed out | Yes |
| `AuctionListEvent` | Before an item is listed on the auction house (price adjustable) | Yes |
| `EconomyTransactionEvent` | Before any balance change (deposit/withdraw/set) | Yes |
| `PunishmentIssuedEvent` | Before a ban/mute/warn/kick is persisted | Yes |
| `PunishmentRevokedEvent` | After an unban/unmute | No |

## PlaceholderAPI

The official expansion (`%youneedme_*%`) registers itself automatically when PlaceholderAPI is
installed:

| Placeholder | Value |
| --- | --- |
| `%youneedme_balance%` | Raw balance |
| `%youneedme_balance_formatted%` | Balance with currency symbol |
| `%youneedme_homes_count%` | Number of homes set |
| `%youneedme_language%` | Resolved locale |
| `%youneedme_nickname%` | Nickname, or username if none set |
| `%youneedme_afk%` | `true`/`false` |
| `%youneedme_vanished%` | `true`/`false` |
| `%youneedme_playtime%` | Formatted total playtime |
| `%youneedme_group%` | LuckPerms primary group (empty if LuckPerms isn't installed) |

## Javadoc

Published to GitHub Pages on every tagged release: <https://mathildeuh.github.io/YouNeedMe/>.

See also [Expansions](Expansions.md) for a lifecycle-managed alternative to a standalone plugin.
