package fr.mathildeuh.youneedme.api.scoreboard;

import java.util.UUID;

/**
 * The built-in, YAML-configured scoreboard. When the TAB plugin is present and its scoreboard
 * feature is enabled, YouNeedMe defers to it automatically and this service becomes a no-op to
 * avoid a display conflict - see {@code core}'s TAB integration.
 */
public interface ScoreboardService {

    boolean isEnabledFor(UUID player);

    void setEnabledFor(UUID player, boolean enabled);

    void toggle(UUID player);

    void reload();

    /** Whether YouNeedMe is actively rendering its own scoreboard right now (false while TAB owns the display). */
    boolean isActive();
}
