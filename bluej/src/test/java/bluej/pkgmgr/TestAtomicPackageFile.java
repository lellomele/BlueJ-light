/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.pkgmgr;

import java.nio.file.Files;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestAtomicPackageFile
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void roundTripsUnicodeAndProjectPositionsWithoutCreatingLegacyFiles() throws Exception
    {
        BlueJPackageFile file = new BlueJPackageFile(folder.getRoot());
        Properties values = new Properties();
        values.setProperty("project.description", "\u03B1\uD83D\uDE00");
        values.setProperty("target1.x", "123");
        file.save(values);
        Properties loaded = new Properties();
        file.load(loaded);
        assertEquals(values, loaded);
        assertFalse(Files.exists(folder.getRoot().toPath().resolve("bluej.pkg")));
    }

    @Test public void keepsExistingMetadataOnFailure() throws Exception
    {
        BlueJPackageFile file = new BlueJPackageFile(folder.getRoot());
        Properties old = new Properties(); old.setProperty("target1.name", "Original");
        file.save(old);
        Files.createDirectory(folder.getRoot().toPath().resolve("bluej.pkg"));
        Properties changed = new Properties(); changed.setProperty("target1.name", "Changed");
        try { file.save(changed); fail("Expected metadata save failure"); }
        catch (java.io.IOException expected) { }
        Properties loaded = new Properties(); file.load(loaded);
        assertEquals(old, loaded);
    }
}
