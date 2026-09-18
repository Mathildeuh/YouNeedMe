# Auction House

Player-to-player item listings with a configurable duration, listing tax, and a click-to-buy GUI.
Expired listings are swept periodically and can be reclaimed by their seller.

## Commands (`/ah`)

| Subcommand | Description |
| --- | --- |
| `/ah sell <price>` | List the item in your hand |
| `/ah cancel <id>` | Cancel one of your active listings, returning the item |
| `/ah expired` | Reclaim every expired listing you're owed items for |
| `/ah listings` | List your own active listings |
| `/ah page [n]` / `/ah [n]` | Browse active listings in the GUI |

Clicking a listing in the GUI purchases it (funds move atomically, item is duplication-protected by
a per-listing mutex so concurrent purchase attempts can't both succeed).

## Configuration (`modules/auctionhouse.yml`)

```yaml
listing-duration-hours: 72
listing-tax-rate: 0.0          # fraction of the price taken as tax on listing
minimum-price: 1
expiry-sweep-interval-seconds: 300
```

## For developers

`AuctionListEvent` fires before a listing is created and lets a listener adjust the final price or
cancel the listing entirely.
