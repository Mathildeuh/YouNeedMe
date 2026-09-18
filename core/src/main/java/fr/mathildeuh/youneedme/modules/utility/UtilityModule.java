package fr.mathildeuh.youneedme.modules.utility;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;
import org.bukkit.GameMode;

public final class UtilityModule {

    private UtilityModule() {}

    public static void enable(YouNeedMe plugin) {
        CommandRegistrar.register(plugin, "afk", new AfkCommand(plugin));
        CommandRegistrar.register(plugin, "afklist", new AfkListCommand(plugin));
        CommandRegistrar.register(plugin, "heal", new HealCommand(plugin));
        CommandRegistrar.register(plugin, "feed", new FeedCommand(plugin));
        CommandRegistrar.register(plugin, "fly", new FlyCommand(plugin));
        CommandRegistrar.register(plugin, "speed", new SpeedCommand(plugin));

        CommandRegistrar.register(plugin, "gamemode", new GameModeCommand(plugin));
        CommandRegistrar.register(plugin, "gm", new GameModeCommand(plugin));
        CommandRegistrar.register(
                plugin,
                "gma",
                new GameModeCommand(plugin, GameMode.ADVENTURE, "youneedme.gamemode.adventure"));
        CommandRegistrar.register(
                plugin,
                "gmc",
                new GameModeCommand(plugin, GameMode.CREATIVE, "youneedme.gamemode.creative"));
        CommandRegistrar.register(
                plugin,
                "gms",
                new GameModeCommand(plugin, GameMode.SURVIVAL, "youneedme.gamemode.survival"));
        CommandRegistrar.register(
                plugin,
                "gmsp",
                new GameModeCommand(plugin, GameMode.SPECTATOR, "youneedme.gamemode.spectator"));
        CommandRegistrar.register(plugin, "hat", new HatCommand(plugin));
        CommandRegistrar.register(plugin, "rename", new RenameCommand(plugin));
        CommandRegistrar.register(plugin, "repair", new RepairCommand(plugin));
        CommandRegistrar.register(plugin, "enchant", new EnchantCommand(plugin));
        CommandRegistrar.register(plugin, "unenchant", new UnenchantCommand(plugin));

        CommandRegistrar.register(plugin, "craftingtable", new CraftingTableCommand(plugin));
        CommandRegistrar.register(plugin, "anvil", new AnvilCommand(plugin));
        CommandRegistrar.register(plugin, "stonecutter", new StonecutterCommand(plugin));
        CommandRegistrar.register(plugin, "enderchest", new EnderChestCommand(plugin));
        CommandRegistrar.register(plugin, "trash", new TrashCommand(plugin));
        CommandRegistrar.register(plugin, "itemid", new ItemIdCommand(plugin));
        CommandRegistrar.register(plugin, "spawnentity", new SpawnEntityCommand(plugin));

        CommandRegistrar.register(plugin, "ping", new PingCommand(plugin));
        CommandRegistrar.register(plugin, "playtime", new PlaytimeCommand(plugin));
        CommandRegistrar.register(plugin, "uptime", new UptimeCommand(plugin));
        CommandRegistrar.register(plugin, "broadcast", new BroadcastCommand(plugin));
        CommandRegistrar.register(plugin, "msg", new MsgCommand(plugin));
        CommandRegistrar.register(plugin, "reply", new ReplyCommand(plugin));
        CommandRegistrar.register(plugin, "ignore", new IgnoreCommand(plugin));
        CommandRegistrar.register(plugin, "playerlist", new PlayerListCommand(plugin));
        CommandRegistrar.register(plugin, "seen", new SeenCommand(plugin));
        CommandRegistrar.register(plugin, "rules", new RulesCommand(plugin));
        CommandRegistrar.register(plugin, "suicide", new SuicideCommand(plugin));

        CommandRegistrar.register(plugin, "ptime", new PTimeCommand(plugin));
        CommandRegistrar.register(plugin, "pweather", new PWeatherCommand(plugin));
        CommandRegistrar.register(plugin, "language", new LanguageCommand(plugin));
        CommandRegistrar.register(plugin, "user", new UserCommand(plugin));
    }
}
