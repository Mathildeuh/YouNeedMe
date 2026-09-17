package fr.mathildeuh.youneedme.scheduler;

/** Detects, once at startup, which scheduling model the running server actually supports. */
public final class ServerEnvironment {

    private static final boolean PAPER_API_PRESENT = classExists("io.papermc.paper.ServerBuildInfo");
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
            Object buildInfo = Class.forName("io.papermc.paper.ServerBuildInfo")
                    .getMethod("buildInfo")
                    .invoke(null);
            Object key = Class.forName("net.kyori.adventure.key.Key")
                    .getMethod("key", String.class, String.class)
                    .invoke(null, "papermc", "folia");
            Object isCompatible = buildInfo
                    .getClass()
                    .getMethod("isBrandCompatible", Class.forName("net.kyori.adventure.key.Key"))
                    .invoke(buildInfo, key);
            return Boolean.TRUE.equals(isCompatible);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Paper API present but predates ServerBuildInfo#isBrandCompatible, or this simply isn't Folia.
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
