/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestAtomicSourceSave
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Path source() throws IOException
    {
        Path file = folder.getRoot().toPath().resolve("Example.java");
        Files.writeString(file, "class Example { }\n");
        return file;
    }

    private void assertNoTemporaryFiles() throws IOException
    {
        try (var files = Files.list(folder.getRoot().toPath())) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test public void replacesCompleteSourceIncludingUnicode() throws Exception
    {
        Path file = source();
        String content = "class Example { String text = \"\u03B1\uD83D\uDE00\"; }\r\n";
        AtomicSourceSave.save(file, content, StandardCharsets.UTF_8, "#");
        assertEquals(content, Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling("Example.java#")));
        assertNoTemporaryFiles();
    }

    @Test public void encodingFailureLeavesOriginalAndExistingBackupUntouched() throws Exception
    {
        Path file = source();
        Path backup = file.resolveSibling("Example.java#");
        Files.writeString(backup, "older backup");
        try {
            AtomicSourceSave.save(file, "\u03B1", StandardCharsets.US_ASCII, "#");
            fail("Expected encoding failure");
        }
        catch (IOException expected) { }
        assertEquals("class Example { }\n", Files.readString(file));
        assertEquals("older backup", Files.readString(backup));
        assertNoTemporaryFiles();
    }

    @Test public void commitFailurePreservesSourceAndRecoveryCopy() throws Exception
    {
        Path file = source();
        try {
            AtomicSourceSave.save(file, "new source", StandardCharsets.UTF_8, "#", (temporary, target) -> {
                if (target.equals(file)) throw new IOException("simulated commit failure");
                AtomicSourceSave.replace(temporary, target);
            });
            fail("Expected commit failure");
        }
        catch (IOException expected) { }
        assertEquals("class Example { }\n", Files.readString(file));
        assertEquals("class Example { }\n", Files.readString(file.resolveSibling("Example.java#")));
        assertNoTemporaryFiles();
    }

    @Test public void backupFailureDoesNotOverwriteSource() throws Exception
    {
        Path file = source();
        try {
            AtomicSourceSave.save(file, "new source", StandardCharsets.UTF_8, "#", (temporary, target) -> {
                throw new IOException("simulated backup failure");
            });
            fail("Expected backup failure");
        }
        catch (IOException expected) { }
        assertEquals("class Example { }\n", Files.readString(file));
        assertNoTemporaryFiles();
    }

    @Test public void createsNewSourceWithoutDiscardingUnrelatedBackup() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve("New.java");
        Path backup = file.resolveSibling("New.java#");
        Files.writeString(backup, "recovery copy");
        AtomicSourceSave.save(file, "class New {}", StandardCharsets.UTF_8, "#");
        assertEquals("class New {}", Files.readString(file));
        assertEquals("recovery copy", Files.readString(backup));
        assertNoTemporaryFiles();
    }
}
