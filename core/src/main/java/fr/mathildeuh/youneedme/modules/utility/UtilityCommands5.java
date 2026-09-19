package fr.mathildeuh.youneedme.modules.utility;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.YnmCommand;
import fr.mathildeuh.youneedme.lang.LanguageManager;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class PTimeCommand extends YnmCommand {

    PTimeCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.ptime", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(
                    sender,
                    "ptime.current",
                    Placeholder.unparsed("time", String.valueOf(player.getPlayerTime())));
            return;
        }
        if ("reset".equalsIgnoreCase(args[0])) {
            player.resetPlayerTime();
            send(sender, "ptime.reset");
            return;
        }
        Long ticks = parseTime(args[0]);
        if (ticks == null) {
            send(sender, "ptime.invalid");
            return;
        }
        boolean locked = args.length > 1 && "locked".equalsIgnoreCase(args[1]);
        player.setPlayerTime(ticks, !locked);
        send(
                sender,
                "ptime.set",
                Placeholder.unparsed("time", args[0]),
                Placeholder.unparsed("type", locked ? "locked" : "relative"));
    }

    private static Long parseTime(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            case "dawn" -> 23000L;
            case "dusk" -> 12000L;
            default -> {
                try {
                    long value = Long.parseLong(input);
                    yield value >= 0 && value <= 24000 ? value : null;
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? List.of("day", "night", "noon", "midnight", "dawn", "dusk", "reset")
                : List.of();
    }
}

final class PWeatherCommand extends YnmCommand {

    PWeatherCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.pweather", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            send(sender, "pweather.current_normal");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reset" -> {
                player.resetPlayerWeather();
                send(sender, "pweather.reset");
            }
            case "clear" -> {
                player.setPlayerWeather(WeatherType.CLEAR);
                send(sender, "pweather.set", Placeholder.unparsed("weather", "clear"));
            }
            case "rain", "storm" -> {
                player.setPlayerWeather(WeatherType.DOWNFALL);
                send(sender, "pweather.set", Placeholder.unparsed("weather", args[0]));
            }
            default -> send(sender, "pweather.invalid");
        }
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("clear", "rain", "storm", "reset") : List.of();
    }
}

final class LanguageCommand extends YnmCommand {

    LanguageCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.language", true);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        LanguageManager lang = plugin.lang();
        if (args.length == 0) {
            String override = lang.playerLocaleOverride(player.getUniqueId());
            send(
                    sender,
                    "language.current",
                    Placeholder.unparsed(
                            "language", override != null ? override : lang.resolveLocale(player)));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                send(sender, "language.list.header");
                send(
                        sender,
                        "language.list.available",
                        Placeholder.unparsed(
                                "languages",
                                String.join(
                                        ", ",
                                        LanguageManager.sortedLocales(lang.availableLocales()))));
            }
            case "reset" -> {
                lang.setPlayerLocaleOverride(player.getUniqueId(), null);
                services()
                        .storage
                        .playerProfiles()
                        .find(player.getUniqueId())
                        .thenAccept(
                                opt ->
                                        opt.ifPresent(
                                                p ->
                                                        services()
                                                                .storage
                                                                .playerProfiles()
                                                                .save(p.withLanguageCode(null))));
                send(
                        sender,
                        "language.reset.success",
                        Placeholder.unparsed("language", lang.resolveLocale(player)));
            }
            case "set" -> {
                String resolved = args.length < 2 ? null : lang.matchLocale(args[1]);
                if (resolved == null) {
                    send(
                            sender,
                            "language.error.not_found",
                            Placeholder.unparsed("lang", args.length > 1 ? args[1] : ""));
                    return;
                }
                applyLocale(player, resolved);
                send(sender, "language.set.success", Placeholder.unparsed("language", resolved));
            }
            default -> {
                String resolved = lang.matchLocale(args[0]);
                if (resolved != null) {
                    applyLocale(player, resolved);
                    send(
                            sender,
                            "language.set.success",
                            Placeholder.unparsed("language", resolved));
                } else {
                    send(
                            sender,
                            "language.error.invalid_lang",
                            Placeholder.unparsed("lang", args[0]));
                }
            }
        }
    }

    private void applyLocale(Player player, String localeCode) {
        plugin.lang().setPlayerLocaleOverride(player.getUniqueId(), localeCode);
        services()
                .storage
                .playerProfiles()
                .find(player.getUniqueId())
                .thenAccept(
                        opt ->
                                opt.ifPresent(
                                        p ->
                                                services()
                                                        .storage
                                                        .playerProfiles()
                                                        .save(p.withLanguageCode(localeCode))));
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(List.of("list", "reset", "set"));
            options.addAll(plugin.lang().availableLocales());
            return options;
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            return List.copyOf(plugin.lang().availableLocales());
        }
        return List.of();
    }
}

final class UserCommand extends YnmCommand {

    UserCommand(YouNeedMe plugin) {
        super(plugin, "youneedme.admin", false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "command.usage.user");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String section = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "profile";
        services()
                .storage
                .playerProfiles()
                .find(target.getUniqueId())
                .thenAccept(
                        opt -> {
                            if (opt.isEmpty()) {
                                send(
                                        sender,
                                        "user.error.no_profile",
                                        Placeholder.unparsed(
                                                "player", String.valueOf(target.getName())));
                                return;
                            }
                            var profile = opt.get();
                            send(
                                    sender,
                                    "user.header",
                                    Placeholder.unparsed("player", profile.lastKnownUsername()));
                            if (section.equals("profile") || section.equals("all")) {
                                send(sender, "user.section.profile");
                                send(
                                        sender,
                                        "user.profile.uuid",
                                        Placeholder.unparsed("uuid", profile.uuid().toString()));
                                send(
                                        sender,
                                        "user.profile.username",
                                        Placeholder.unparsed(
                                                "username", profile.lastKnownUsername()));
                                send(
                                        sender,
                                        "user.profile.language",
                                        Placeholder.unparsed(
                                                "language",
                                                profile.languageCode() == null
                                                        ? "auto"
                                                        : profile.languageCode()));
                                send(
                                        sender,
                                        "user.profile.first_join",
                                        Placeholder.unparsed(
                                                "first_join",
                                                java.time.Instant.ofEpochMilli(
                                                                profile.firstJoinedAt())
                                                        .toString()));
                                send(
                                        sender,
                                        "user.profile.last_join",
                                        Placeholder.unparsed(
                                                "last_join",
                                                java.time.Instant.ofEpochMilli(profile.lastSeenAt())
                                                        .toString()));
                            }
                            if (section.equals("states") || section.equals("all")) {
                                send(sender, "user.section.states");
                                send(sender, "user.states.fly", Placeholder.unparsed("value", "-"));
                                send(
                                        sender,
                                        "user.states.vanished",
                                        Placeholder.unparsed(
                                                "value",
                                                String.valueOf(
                                                        services()
                                                                .vanished
                                                                .contains(target.getUniqueId()))));
                            }
                            if (section.equals("punishments") || section.equals("all")) {
                                send(sender, "user.section.punishments");
                                services()
                                        .moderation
                                        .activeBan(target.getUniqueId())
                                        .thenAccept(
                                                ban -> {
                                                    if (ban == null) {
                                                        send(sender, "user.punishments.not_banned");
                                                    } else {
                                                        Long expiresAt = ban.expiresAt();
                                                        send(
                                                                sender,
                                                                "user.punishments.banned",
                                                                Placeholder.unparsed(
                                                                        "banner",
                                                                        String.valueOf(
                                                                                ban.issuedBy())),
                                                                Placeholder.unparsed(
                                                                        "reason", ban.reason()),
                                                                Placeholder.unparsed(
                                                                        "time",
                                                                        java.time.Instant
                                                                                .ofEpochMilli(
                                                                                        ban
                                                                                                .issuedAt())
                                                                                .toString()),
                                                                Placeholder.unparsed(
                                                                        "expires",
                                                                        expiresAt == null
                                                                                ? "never"
                                                                                : java.time.Instant
                                                                                        .ofEpochMilli(
                                                                                                expiresAt)
                                                                                        .toString()));
                                                    }
                                                });
                            }
                            if (section.equals("locations") || section.equals("all")) {
                                send(sender, "user.section.locations");
                                if (profile.lastLocation() != null) {
                                    send(
                                            sender,
                                            "user.locations.back",
                                            Placeholder.unparsed(
                                                    "world", profile.lastLocation().worldName()),
                                            Placeholder.unparsed(
                                                    "x",
                                                    String.valueOf(
                                                            (int) profile.lastLocation().x())),
                                            Placeholder.unparsed(
                                                    "y",
                                                    String.valueOf(
                                                            (int) profile.lastLocation().y())),
                                            Placeholder.unparsed(
                                                    "z",
                                                    String.valueOf(
                                                            (int) profile.lastLocation().z())));
                                } else {
                                    send(sender, "user.locations.back_none");
                                }
                            }
                            send(sender, "user.footer");
                        });
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2) {
            return List.of("profile", "states", "punishments", "locations", "cooldowns", "all");
        }
        return List.of();
    }
}
