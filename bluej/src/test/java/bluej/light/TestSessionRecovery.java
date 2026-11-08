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

    @Test public void aSessionRecordedBeforeAnInterruptedRenameResolvesTheActualClass() throws Exception
    {
        Path root=folder.getRoot().toPath(),preferences=root.resolve("preferences"),source=root.resolve("Example.java"),destination=root.resolve("Renamed.java");
        Files.writeString(source,"class Renamed {}\n");LocalHistory history=new LocalHistory(preferences);
        history.preparePathChange(source,destination);Files.move(source,destination);
        var session=List.of(new SessionRecovery.Entry(root,List.of("Example")));
        assertEquals(List.of(new SessionRecovery.Entry(root,List.of("Renamed"))),SessionRecovery.resolveEntries(session,new LocalHistory(preferences)));
    }

    @Test public void reusedOldNameBelongsToTheNewClassAndInvalidNamesCannotEscapeThePackage() throws Exception
    {
        Path root=folder.getRoot().toPath(),preferences=root.resolve("preferences"),source=root.resolve("Example.java"),destination=root.resolve("Renamed.java");
        Files.writeString(source,"class Renamed {}\n");LocalHistory history=new LocalHistory(preferences);
        var change=history.preparePathChange(source,destination);Files.move(source,destination);history.commitPathChange(change);
        Files.writeString(source,"class Example {}\n");
        var session=List.of(new SessionRecovery.Entry(root,List.of("Example","../outside","Renamed","Renamed")));
        assertEquals(List.of(new SessionRecovery.Entry(root,List.of("Example","Renamed"))),SessionRecovery.resolveEntries(session,new LocalHistory(preferences)));
    }

    @Test public void interruptedPackageTransferRestoresTheClassInItsActualPackage() throws Exception
    {
        Path root=folder.getRoot().toPath(),child=Files.createDirectory(root.resolve("child")),preferences=root.resolve("preferences");Files.writeString(root.resolve("package.bluej"),"");Files.writeString(child.resolve("package.bluej"),"");
        Path source=root.resolve("Example.java"),destination=child.resolve("Example.java");Files.writeString(source,"package child; class Example {}\n");
        LocalHistory history=new LocalHistory(preferences);history.preparePathChange(source,destination,root);Files.move(source,destination);
        var session=List.of(new SessionRecovery.Entry(root,List.of("Example")));
        assertEquals(List.of(new SessionRecovery.Entry(root,List.of()),new SessionRecovery.Entry(child,List.of("Example"))),SessionRecovery.resolveEntries(session,new LocalHistory(preferences)));
    }
}
