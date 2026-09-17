package fr.mathildeuh.youneedme.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * Base for every YouNeedMe event tied to a single online player. Subclasses still declare their
 * own static {@link org.bukkit.event.HandlerList} and {@code getHandlers()}/{@code
 * getHandlerList()} pair - that boilerplate is a Bukkit requirement per concrete event type and
 * cannot be inherited away, only the {@link #getPlayer()} accessor is shared here.
 */
public abstract class YnmPlayerEvent extends Event {

    private final Player player;

    protected YnmPlayerEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
