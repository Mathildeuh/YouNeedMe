# Shop

A server shop with categorized items, backed by a real chest GUI. Ships with a fully pre-filled
`shop.yml` (blocks, ores, farming, food, tools, and a permission-gated "black market" category) so
a new server has something usable immediately, rather than an empty template to fill in by hand.

## Commands

| Command | Description |
| --- | --- |
| `/shop [category]` | Open the shop GUI, or jump straight to a category |
| `/shop reload` | Reload `shop.yml` without restarting |
| `/sell` | Open the shop GUI for selling |
| `/quicksell [hand\|inventory]` | Instantly sell your hand item (or entire inventory) at its configured sell price |
| `/worth [inventory]` | Check what your held item (or whole inventory) would sell for |

## GUI

In a category view: **left-click** buys one (shift-click buys 64), **right-click** sells one from
your hand. Categories with a `permission` key are hidden from players who lack it.

## Configuration

See [Configuration](Configuration.md#shopyml-data-folder-root) for the `shop.yml` schema — each
item supports an optional `stock` (omit for unlimited), and either `buy-price` or `sell-price` can
be omitted to make an item one-directional.
