package fr.mathildeuh.youneedme.modules.rtp;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.command.CommandRegistrar;

public final class RtpModule {

    private RtpModule() {}

    public static void enable(YouNeedMe plugin) {
        CommandRegistrar.register(plugin, "rtp", new RtpCommand(plugin));
    }
}
