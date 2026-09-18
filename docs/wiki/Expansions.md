# Expansions

An expansion is a jar dropped in `plugins/YouNeedMe/expansions/` — a lighter-weight alternative to
a standalone plugin for add-ons that only need YouNeedMe's data. Each expansion runs in its own
classloader with an isolated lifecycle: if one throws during `onLoad`/`onEnable`/`onDisable`, it's
logged and disabled, and the core plugin (and every *other* expansion) keeps running.

## Project setup

```kotlin
dependencies {
    compileOnly("com.github.Mathildeuh.YouNeedMe:expansion-api:<version>")
    compileOnly("com.github.Mathildeuh.YouNeedMe:api:<version>")
    compileOnly("io.papermc.paper:paper-api:<paper-version>")
}
```

Both are `compileOnly`: at runtime, YouNeedMe's own classloader (the expansion's parent) already
has both on it, so nothing needs to be shaded into your expansion's jar.

## `expansion.yml`

Placed at the root of your expansion's jar (a resource file), analogous to `plugin.yml`:

```yaml
id: my-expansion
version: 1.0.0
main: com.example.myexpansion.MyExpansion
api-version: "1.0"
authors: [ YourName ]
description: What this expansion adds.
```

## Implementing `Expansion`

```java
public final class MyExpansion implements Expansion {

    private ExpansionContext context;

    @Override
    public void onLoad(ExpansionContext context) {
        this.context = context; // context.dataFolder(), context.logger(), context.hostPlugin()
    }

    @Override
    public void onEnable() {
        // Register listeners/commands against context.hostPlugin() exactly like a normal plugin,
        // and reach YouNeedMe's services through YouNeedMeAPI exactly like a standalone plugin would.
        YouNeedMeAPI.economy().ifPresent(economy -> { /* ... */ });
    }

    @Override
    public void onDisable() {
        // Unregister anything you registered in onEnable, if needed.
    }
}
```

Lifecycle order across every expansion: every jar's `onLoad` runs first, then every jar's
`onEnable` — so by the time your `onEnable` runs, every other expansion has already finished
loading (though not necessarily enabling).

## Example: `expansion-placeholders`

The repository ships an official example expansion
([`expansions/expansion-placeholders`](../../expansions/expansion-placeholders)) that registers a
couple of bonus PlaceholderAPI placeholders (`%youneedme_extended_warps_count%`,
`%youneedme_extended_kits_total%`) purely through `YouNeedMeAPI` and the `Expansion` lifecycle — a
working reference alongside this page, not a feature server owners need to configure.
