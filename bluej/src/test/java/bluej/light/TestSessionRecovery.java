/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestSessionRecovery
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void roundTripUnicodeAndSeparatePackages() throws Exception
    {
        Path root = folder.getRoot().toPath();
        Path child = Files.createDirectory(root.resolve("project-\u03b1"));
        Path file = root.resolve("session.properties");
        var entries = List.of(new SessionRecovery.Entry(root, List.of("Example", "Other")),
            new SessionRecovery.Entry(child, List.of("\u0394")));
        SessionRecovery.write(file, entries);
        assertEquals(entries, SessionRecovery.read(file));
        SessionRecovery.write(file, List.of());
        assertEquals(List.of(), SessionRecovery.read(file));
    }

    @Test public void missingPackagesAreNotRecreated() throws Exception
    {
        Path root = folder.getRoot().toPath();
        Path file = root.resolve("session.properties");
        SessionRecovery.write(file, List.of(new SessionRecovery.Entry(root.resolve("missing"), List.of("Example"))));
        assertTrue(SessionRecovery.read(file).isEmpty());
        assertFalse(Files.exists(root.resolve("missing")));
    }

    @Test public void invalidAndOversizedSessionsAreRejected() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve("session.properties");
        Files.writeString(file, "packages=999999999\n");
        try { SessionRecovery.read(file); fail("Invalid count accepted"); }
        catch (IOException expected) { }
        Files.writeString(file, "x".repeat(1024 * 1024 + 1));
        try { SessionRecovery.read(file); fail("Oversized session accepted"); }
        catch (IOException expected) { }
    }
}
