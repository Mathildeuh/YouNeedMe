package fr.mathildeuh.youneedme.modules.trade;

import java.util.UUID;
import org.bukkit.inventory.Inventory;

/**
 * Mutable state for one active trade - not persisted, a trade never needs to survive a restart.
 * Items live directly in {@link #inventory}'s slots (the GUI itself is the escrow: once an item is
 * dragged into a player's slot range it has already left their personal inventory); only the money
 * and XP levels offered need tracking here, since those are withdrawn from the offering player the
 * moment they're set rather than only at the final exchange.
 */
final class TradeSession {

    final UUID playerA;
    final UUID playerB;
    final Inventory inventory;

    double moneyA;
    double moneyB;
    int xpLevelsA;
    int xpLevelsB;
    boolean readyA;
    boolean readyB;

    TradeSession(UUID playerA, UUID playerB, Inventory inventory) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.inventory = inventory;
    }

    boolean isPlayerA(UUID player) {
        return player.equals(playerA);
    }

    boolean involves(UUID player) {
        return player.equals(playerA) || player.equals(playerB);
    }

    void resetReady() {
        readyA = false;
        readyB = false;
    }
}
