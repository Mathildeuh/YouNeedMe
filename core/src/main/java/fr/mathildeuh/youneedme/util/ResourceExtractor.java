package fr.mathildeuh.youneedme.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Copies every resource under a classpath folder (e.g. the bundled {@code lang/} directory) out to
 * disk on first run, without overwriting files an admin already edited. {@code
 * JavaPlugin#saveResource} only handles a single known file name, hence this.
 */
public final class ResourceExtractor {

    private ResourceExtractor() {}

    public static void extractFolder(
            Class<?> anchor, String resourceFolder, Path targetDir, Logger logger) {
        try {
            Files.createDirectories(targetDir);
            URL url = anchor.getClassLoader().getResource(resourceFolder);
            if (url == null) {
                logger.warning(
                        "Bundled resource folder '"
                                + resourceFolder
                                + "' not found in the plugin jar.");
                return;
            }
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(resourceFolder + "/")) {
                        continue;
                    }
                    String relative = entry.getName().substring(resourceFolder.length() + 1);
                    Path destination = targetDir.resolve(relative);
                    if (Files.exists(destination)) {
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, destination);
                    }
                }
            }
        } catch (IOException | ClassCastException e) {
            logger.warning(
                    "Failed to extract bundled resources from '"
                            + resourceFolder
                            + "': "
                            + e.getMessage());
        }
    }
}
