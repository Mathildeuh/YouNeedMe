package fr.mathildeuh.youneedme.expansions;

import fr.mathildeuh.youneedme.YouNeedMe;
import fr.mathildeuh.youneedme.expansion.Expansion;
import fr.mathildeuh.youneedme.expansion.ExpansionContext;
import fr.mathildeuh.youneedme.expansion.ExpansionDescription;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Discovers, loads and manages third-party expansion jars dropped in {@code
 * plugins/YouNeedMe/expansions/}. Every expansion runs in its own {@link URLClassLoader} (parented
 * to YouNeedMe's own, so it can see the {@code api} and {@code expansion-api} modules) and every
 * lifecycle call is individually try/caught - a broken expansion is disabled and logged, never
 * brings the core plugin down.
 */
public final class ExpansionManager {

    private record Loaded(
            ExpansionDescription description, Expansion instance, URLClassLoader classLoader) {}

    private final YouNeedMe plugin;
    private final List<Loaded> loaded = new ArrayList<>();

    public ExpansionManager(YouNeedMe plugin) {
        this.plugin = plugin;
    }

    public void loadAndEnableAll() {
        File expansionsDir = new File(plugin.getDataFolder(), "expansions");
        boolean justCreated = expansionsDir.mkdirs();
        File[] jars =
                justCreated ? null : expansionsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return;
        }
        for (File jar : jars) {
            load(jar);
        }
        for (Loaded expansion : loaded) {
            safely(expansion.description().id(), "onEnable", expansion.instance()::onEnable);
        }
    }

    private void load(File jar) {
        ExpansionDescription description;
        try (JarFile jarFile = new JarFile(jar)) {
            var entry = jarFile.getEntry("expansion.yml");
            if (entry == null) {
                plugin.getLogger()
                        .warning("Skipping " + jar.getName() + ": no expansion.yml found.");
                return;
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                var yaml =
                        YamlConfiguration.loadConfiguration(
                                new InputStreamReader(in, StandardCharsets.UTF_8));
                description =
                        new ExpansionDescription(
                                yaml.getString("id", jar.getName()),
                                yaml.getString("version", "unknown"),
                                yaml.getString("main", ""),
                                yaml.getString("api-version", "1.0"),
                                yaml.getStringList("authors"),
                                yaml.getString("description", ""));
            }
        } catch (IOException e) {
            plugin.getLogger()
                    .log(Level.WARNING, "Could not read " + jar.getName() + "'s expansion.yml", e);
            return;
        }
        if (description.main().isBlank()) {
            plugin.getLogger()
                    .warning("Skipping " + jar.getName() + ": expansion.yml has no 'main' class.");
            return;
        }

        URLClassLoader classLoader; // NOPMD - lifetime is tied to the expansion, closed via
        // closeQuietly() below on failure or on disable, not within this method
        Expansion instance;
        try {
            classLoader =
                    new URLClassLoader(
                            new URL[] {jar.toURI().toURL()}, getClass().getClassLoader());
            Class<?> mainClass = Class.forName(description.main(), true, classLoader);
            instance = (Expansion) mainClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | IOException e) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Failed to load expansion '"
                                    + description.id()
                                    + "' from "
                                    + jar.getName(),
                            e);
            return;
        }

        File dataFolder =
                new File(new File(plugin.getDataFolder(), "expansions/data"), description.id());
        if (!dataFolder.mkdirs() && !dataFolder.isDirectory()) {
            plugin.getLogger()
                    .warning(
                            "Could not create data folder for expansion '"
                                    + description.id()
                                    + "' at "
                                    + dataFolder);
        }
        Logger expansionLogger =
                Logger.getLogger(plugin.getLogger().getName() + "." + description.id());
        ExpansionContext context =
                new ExpansionContext() {
                    @Override
                    public ExpansionDescription description() {
                        return description;
                    }

                    @Override
                    public File dataFolder() {
                        return dataFolder;
                    }

                    @Override
                    public Logger logger() {
                        return expansionLogger;
                    }

                    @Override
                    public org.bukkit.plugin.Plugin hostPlugin() {
                        return plugin;
                    }
                };

        Expansion finalInstance = instance;
        boolean ok = safely(description.id(), "onLoad", () -> finalInstance.onLoad(context));
        if (ok) {
            loaded.add(new Loaded(description, instance, classLoader));
            plugin.getLogger()
                    .info(
                            "Loaded expansion '"
                                    + description.id()
                                    + "' v"
                                    + description.version()
                                    + ".");
        } else {
            closeQuietly(classLoader);
        }
    }

    public void disableAll() {
        for (int i = loaded.size() - 1; i >= 0; i--) {
            Loaded expansion = loaded.get(i);
            safely(expansion.description().id(), "onDisable", expansion.instance()::onDisable);
            closeQuietly(expansion.classLoader());
        }
        loaded.clear();
    }

    private boolean safely(String expansionId, String phase, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Expansion '"
                                    + expansionId
                                    + "' threw during "
                                    + phase
                                    + " - disabling it.",
                            t);
            return false;
        }
    }

    private void closeQuietly(URLClassLoader classLoader) {
        try {
            classLoader.close();
        } catch (IOException ignored) {
            // ignored - best-effort cleanup only.
        }
    }
}
