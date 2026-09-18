package fr.mathildeuh.youneedme.modules.scoreboard;

import fr.mathildeuh.youneedme.api.scoreboard.ScoreboardService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardServiceImpl implements ScoreboardService {

    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();
    private volatile boolean active;

    public ScoreboardServiceImpl(boolean active) {
        this.active = active;
    }

    @Override
    public boolean isEnabledFor(UUID player) {
        return !disabled.contains(player);
    }

    @Override
    public void setEnabledFor(UUID player, boolean enabled) {
        if (enabled) {
            disabled.remove(player);
        } else {
            disabled.add(player);
        }
    }

    @Override
    public void toggle(UUID player) {
        setEnabledFor(player, !isEnabledFor(player));
    }

    @Override
    public void reload() {
        // Line content is re-read from config by the renderer on its next tick; nothing to do here.
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
