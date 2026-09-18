package fr.mathildeuh.youneedme.modules.migration;

import java.util.ArrayList;
import java.util.List;

/** What a migrator did (or would do, in dry-run) plus anything it couldn't make sense of. */
public final class MigrationReport {

    private int playersScanned;
    private int homesImported;
    private int warpsImported;
    private int balancesImported;
    private final List<String> unmigratable = new ArrayList<>();

    public void playerScanned() {
        playersScanned++;
    }

    public void homeImported() {
        homesImported++;
    }

    public void warpImported() {
        warpsImported++;
    }

    public void balanceImported() {
        balancesImported++;
    }

    public void skip(String entry, String reason) {
        unmigratable.add(entry + ": " + reason);
    }

    public List<String> unmigratable() {
        return List.copyOf(unmigratable);
    }

    public String summary() {
        return "Scanned %d player file(s): %d home(s), %d warp(s), %d balance(s) importable. %d entr%s could not be migrated (see console)."
                .formatted(
                        playersScanned,
                        homesImported,
                        warpsImported,
                        balancesImported,
                        unmigratable.size(),
                        unmigratable.size() == 1 ? "y" : "ies");
    }
}
