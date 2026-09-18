package fr.mathildeuh.youneedme.modules.migration;

import fr.mathildeuh.youneedme.YouNeedMe;

/**
 * EssentialsC (the project YouNeedMe takes its architecture from) is itself a close fork of
 * EssentialsX, so per the brief its on-disk format is close to 1:1 with EssentialsX's - this
 * importer is nothing more than {@link EssentialsXImporter} pointed at {@code plugins/EssentialsC/}
 * instead of {@code plugins/Essentials/}.
 */
public final class EssentialsCImporter extends EssentialsXImporter {

    public EssentialsCImporter(YouNeedMe plugin) {
        super(plugin, "EssentialsC");
    }
}
