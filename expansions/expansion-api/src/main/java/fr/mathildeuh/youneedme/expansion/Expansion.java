package fr.mathildeuh.youneedme.expansion;

/**
 * Entry point for a third-party YouNeedMe expansion jar dropped in {@code
 * plugins/YouNeedMe/expansions/}. Mirrors {@code JavaPlugin}'s own lifecycle on purpose so it feels
 * familiar: {@link #onLoad} happens once the jar is discovered and its class loaded, {@link
 * #onEnable} once every expansion has finished loading, {@link #onDisable} on server shutdown or
 * {@code /ynm reload}.
 *
 * <p>An exception thrown from any of these three methods is caught and logged by the
 * ExpansionManager and disables only that expansion - it can never take the core plugin down.
 */
@FunctionalInterface
public interface Expansion {

    void onLoad(ExpansionContext context);

    default void onEnable() {}

    default void onDisable() {}
}
