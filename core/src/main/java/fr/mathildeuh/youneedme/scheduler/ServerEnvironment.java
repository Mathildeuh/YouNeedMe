package fr.mathildeuh.youneedme.scheduler;

import net.kyori.adventure.key.Key;

/** Detects, once at startup, which scheduling model the running server actually supports. */
public final class ServerEnvironment {

    private static final boolean PAPER_API_PRESENT =
            classExists("io.papermc.paper.ServerBuildInfo");
    private static final boolean FOLIA = PAPER_API_PRESENT && detectFolia();

    private ServerEnvironment() {}

    public static boolean isPaper() {
        return PAPER_API_PRESENT;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Object buildInfo =
                    Class.forName("io.papermc.paper.ServerBuildInfo")
                            .getMethod("buildInfo")
                            .invoke(null);
            Key key = Key.key("papermc", "folia");
            Object isCompatible =
                    buildInfo
                            .getClass()
                            .getMethod("isBrandCompatible", Key.class)
                            .invoke(buildInfo, key);
            return Boolean.TRUE.equals(isCompatible);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Paper API present but predates ServerBuildInfo#isBrandCompatible, or this simply
            // isn't Folia.
            return classExists("io.papermc.paper.threadedregions.RegionizedServer");
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
