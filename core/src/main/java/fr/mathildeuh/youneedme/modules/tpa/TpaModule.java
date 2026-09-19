package fr.mathildeuh.youneedme.modules.tpa;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory TPA request queue and per-player preferences - request state is not worth persisting.
 */
public final class TpaModule {

    public static TpaModule enable(YouNeedMe plugin) {
        TpaModule module = new TpaModule();
        CommandRegistrar.register(plugin, "tpa", new TpaCommand(plugin));
        CommandRegistrar.register(plugin, "tpahere", new TpaHereCommand(plugin));
        CommandRegistrar.register(plugin, "tpaccept", new TpAcceptCommand(plugin));
        CommandRegistrar.register(plugin, "tpdeny", new TpDenyCommand(plugin));
        CommandRegistrar.register(plugin, "tpcancel", new TpCancelCommand(plugin));
        CommandRegistrar.register(plugin, "tpaignore", new TpaIgnoreCommand(plugin));
        CommandRegistrar.register(plugin, "tpaqueue", new TpaQueueCommand(plugin));
        CommandRegistrar.register(plugin, "tpatoggle", new TpaToggleCommand(plugin));
        CommandRegistrar.register(plugin, "tphere", new TpHereCommand(plugin));
        CommandRegistrar.register(plugin, "tphereall", new TpHereAllCommand(plugin));
        CommandRegistrar.register(plugin, "tpoffline", new TpOfflineCommand(plugin));
        return module;
    }

    public enum Type {
        TPA,
        TPAHERE
    }

    public record Request(UUID from, UUID to, Type type, long expiresAt) {}

    private final Map<UUID, Request> incomingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Request>> outgoingBySender = new ConcurrentHashMap<>();
    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> ignoring = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastIncomingSender = new ConcurrentHashMap<>();

    public boolean isToggledOff(UUID player) {
        return toggledOff.contains(player);
    }

    public void setToggledOff(UUID player, boolean off) {
        if (off) {
            toggledOff.add(player);
        } else {
            toggledOff.remove(player);
        }
    }

    public boolean isIgnoring(UUID player, UUID other) {
        return ignoring.getOrDefault(player, Set.of()).contains(other);
    }

    public boolean toggleIgnore(UUID player, UUID other) {
        Set<UUID> set = ignoring.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());
        if (set.remove(other)) {
            return false;
        }
        set.add(other);
        return true;
    }

    public void addRequest(Request request) {
        incomingByTarget.put(request.to(), request);
        outgoingBySender
                .computeIfAbsent(request.from(), k -> new LinkedHashMap<>())
                .put(request.to(), request);
        lastIncomingSender.put(request.to(), request.from());
    }

    public java.util.Optional<Request> incoming(UUID target) {
        Request request = incomingByTarget.get(target);
        return request != null && request.expiresAt() > System.currentTimeMillis()
                ? java.util.Optional.of(request)
                : java.util.Optional.empty();
    }

    public java.util.Optional<UUID> lastIncomingSender(UUID target) {
        return java.util.Optional.ofNullable(lastIncomingSender.get(target));
    }

    public void clearIncoming(UUID target) {
        Request request = incomingByTarget.remove(target);
        if (request != null) {
            Map<UUID, Request> outgoing = outgoingBySender.get(request.from());
            if (outgoing != null) {
                outgoing.remove(target);
            }
        }
    }

    public java.util.Collection<Request> outgoing(UUID sender) {
        return outgoingBySender.getOrDefault(sender, Map.of()).values();
    }

    public java.util.Collection<Request> incomingAll(UUID target) {
        Request request = incomingByTarget.get(target);
        return request == null ? java.util.List.of() : java.util.List.of(request);
    }

    public void cancelOutgoing(UUID sender, UUID target) {
        Map<UUID, Request> outgoing = outgoingBySender.get(sender);
        if (outgoing != null) {
            outgoing.remove(target);
        }
        Request incoming = incomingByTarget.get(target);
        if (incoming != null && incoming.from().equals(sender)) {
            incomingByTarget.remove(target);
        }
    }

    public void forgetPlayer(UUID player) {
        incomingByTarget.remove(player);
        outgoingBySender.remove(player);
        toggledOff.remove(player);
        ignoring.remove(player);
        lastIncomingSender.remove(player);
    }
}
