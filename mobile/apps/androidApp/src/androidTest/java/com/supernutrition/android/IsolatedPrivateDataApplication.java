package com.supernutrition.android;

import com.supernutrition.core.config.PrivateDocumentId;

import java.io.File;
import java.util.UUID;

/** Test-only substitution: production factories and adapters use a separate no-backup subtree. */
public final class IsolatedPrivateDataApplication extends BootstrapApplication {
    private final String fixtureName = "instrumentation-" + UUID.randomUUID();
    private File fixtureDirectory;

    @Override
    public synchronized File getNoBackupFilesDir() {
        if (fixtureDirectory == null) {
            File directory = new File(super.getNoBackupFilesDir(), fixtureName);
            if (!directory.mkdir()) throw new IllegalStateException("Could not create private-data test fixture");
            fixtureDirectory = directory;
        }
        return fixtureDirectory;
    }

    synchronized void cleanUpPrivateDocuments() {
        if (fixtureDirectory == null) return;
        for (PrivateDocumentId id : PrivateDocumentId.values()) {
            new File(fixtureDirectory, id.getFileName()).delete();
            new File(fixtureDirectory, id.getFileName() + ".bak").delete();
            new File(fixtureDirectory, id.getFileName() + ".new").delete();
        }
        // Never traverse or delete another test's unexpected files or the user's no-backup parent.
        fixtureDirectory.delete();
    }
}
