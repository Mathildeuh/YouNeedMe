package fr.mathildeuh.youneedme.modules.trade;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory trade request queue and active-session registry - nothing here is persisted. */
public final class TradeManager {

    record Request(UUID from, UUID to, long expiresAt) {}

    private final Map<UUID, Request> incomingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, TradeSession> activeByPlayer = new ConcurrentHashMap<>();

    void addRequest(Request request) {
        incomingByTarget.put(request.to(), request);
    }

    Optional<Request> incoming(UUID target) {
        Request request = incomingByTarget.get(target);
        return request != null && request.expiresAt() > System.currentTimeMillis()
                ? Optional.of(request)
                : Optional.empty();
    }

    void clearIncoming(UUID target) {
        incomingByTarget.remove(target);
    }

    public boolean isTrading(UUID player) {
        return activeByPlayer.containsKey(player);
    }

    Optional<TradeSession> activeSession(UUID player) {
        return Optional.ofNullable(activeByPlayer.get(player));
    }

    void startSession(TradeSession session) {
        activeByPlayer.put(session.playerA, session);
        activeByPlayer.put(session.playerB, session);
    }

    void endSession(TradeSession session) {
        activeByPlayer.remove(session.playerA, session);
        activeByPlayer.remove(session.playerB, session);
    }

    public void forgetIncomingRequest(UUID player) {
        incomingByTarget.remove(player);
    }
}
