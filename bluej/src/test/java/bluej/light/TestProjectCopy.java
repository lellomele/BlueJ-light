/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestProjectCopy
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path source() throws IOException
    {
        Path root = temporary.newFolder("original").toPath();
        Files.writeString(root.resolve("Example.java"),"public class Example {}\n");
        Files.createDirectory(root.resolve("empty"));Files.createDirectory(root.resolve("child"));
        Files.write(root.resolve("child/data.bin"),new byte[]{0,1,-1,2});Files.writeString(root.resolve("package.bluej"),"target1.name=Example\n");
        return root;
    }
    private Path destination() { return temporary.getRoot().toPath().resolve("copy"); }
    @FunctionalInterface private interface Operation { void run() throws IOException; }
    private void fails(Operation operation) throws IOException
    { try { operation.run();fail("Expected a safe copy refusal"); } catch (IOException expected) { } }

    @Test public void copiesSourcesBinaryResourcesAndEmptyPackagesWithoutChangingTheOriginal() throws Exception
    {
        Path original = source(), copied = destination();ProjectCopy.copy(original,copied);
        assertEquals(Files.readString(original.resolve("Example.java")),Files.readString(copied.resolve("Example.java")));
        assertArrayEquals(Files.readAllBytes(original.resolve("child/data.bin")),Files.readAllBytes(copied.resolve("child/data.bin")));
        assertTrue(Files.isDirectory(copied.resolve("empty")));assertEquals("target1.name=Example\n",Files.readString(copied.resolve("package.bluej")));
    }
    @Test public void refusesNonemptyDestinationsWithoutReplacingUserFiles() throws Exception
    {
        Path original = source(), copied = temporary.newFolder("copy").toPath();Path unrelated = copied.resolve("Example.java");Files.writeString(unrelated,"User data");
        fails(() -> ProjectCopy.copy(original,copied));assertEquals("User data",Files.readString(unrelated));assertFalse(Files.exists(copied.resolve("child")));
    }
    @Test public void refusesSelfAndNestedCopiesBeforeCreatingAnOutputDirectory() throws Exception
    {
        Path original = source();fails(() -> ProjectCopy.copy(original,original));Path nested = original.resolve("nested");
        fails(() -> ProjectCopy.copy(original,nested));assertFalse(Files.exists(nested));fails(() -> ProjectCopy.copy(original,original.getParent()));
    }
    @Test public void detectsChangesDuringCopyAndKeepsBothTheOriginalAndPartialCopy() throws Exception
    {
        Path original = source(),copied = destination();
        fails(() -> ProjectCopy.copy(original,copied,() -> {
            try { Files.writeString(original.resolve("Example.java"),"public class Example { int latest; }\n"); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }));
        assertEquals("public class Example { int latest; }\n",Files.readString(original.resolve("Example.java")));assertTrue(Files.isDirectory(copied));
    }
    @Test public void detectsFilesAddedAfterTheInitialInventory() throws Exception
    {
        Path original = source(),copied = destination();
        fails(() -> ProjectCopy.copy(original,copied,() -> {
            try { Files.writeString(original.resolve("New.java"),"class New {}\n"); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }));assertTrue(Files.exists(original.resolve("New.java")));assertFalse(Files.exists(copied.resolve("New.java")));
    }
    @Test public void aConcurrentDestinationFileIsNeverOverwrittenOrDeleted() throws Exception
    {
        Path original = source(),copied = destination();
        fails(() -> ProjectCopy.copy(original,copied,() -> {
            try { Files.writeString(copied.resolve("Example.java"),"Concurrent user file"); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }));assertEquals("Concurrent user file",Files.readString(copied.resolve("Example.java")));assertTrue(Files.exists(original.resolve("Example.java")));
    }
    @Test public void aCleanOpenBufferMissingFromDiskCannotBeSilentlyOmitted() throws Exception
    {
        Path original = source(),file = original.resolve("Example.java");String text = Files.readString(file);
        var snapshot = new SourceSaveService.Snapshot(new SourceSaveService.Owner(),1,file,text,StandardCharsets.UTF_8,text,true,false);Files.delete(file);
        fails(() -> ProjectCopy.copy(original,destination(),List.of(snapshot)));assertFalse(Files.exists(destination()));assertEquals("public class Example {}\n",snapshot.text());
    }
    @Test public void verifiesOpenBuffersUsingTheirCharsetAndNormalizedLineEndings() throws Exception
    {
        Path original = source(),file = original.resolve("Example.java");String text = "// citt\u00e0\npublic class Example {}\n";
        Files.writeString(file,text.replace("\n","\r\n"),StandardCharsets.ISO_8859_1);
        var snapshot = new SourceSaveService.Snapshot(new SourceSaveService.Owner(),1,file,text,StandardCharsets.ISO_8859_1,text,true,false);
        ProjectCopy.copy(original,destination(),List.of(snapshot));assertArrayEquals(Files.readAllBytes(file),Files.readAllBytes(destination().resolve("Example.java")));
    }
}
