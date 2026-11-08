/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestSourceRemoval
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Path source() throws IOException {Path file=folder.getRoot().toPath().resolve("Example.java");Files.writeString(file,"original\n");return file;}
    private Path recovery() {return folder.getRoot().toPath().resolve("recovered-sources");}
    private SourceSaveService.Snapshot buffer(Path file,String text)
    {
        var owner=new SourceSaveService.Owner();owner.retire();
        return new SourceSaveService.Snapshot(owner,2,file,text,StandardCharsets.UTF_8,"original\n",true,false);
    }
    private static void fails(CompletableFuture<?> request) throws Exception
    {try{request.get(5,TimeUnit.SECONDS);fail("Expected a safe refusal");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}}

    @Test public void sourceIsRemovedOnlyAfterAnExactVerifiedCopyExists() throws Exception
    {
        Path file=source();byte[] original=Files.readAllBytes(file);
        try(var service=new SourceSaveService())
        {
            var removed=service.removeSource(file,buffer(file,"original\n"),recovery()).get(5,TimeUnit.SECONDS);
            assertFalse(Files.exists(file));assertEquals(1,removed.recoveryCopies().size());assertArrayEquals(original,Files.readAllBytes(removed.recoveryCopies().getFirst()));
        }
    }
    @Test public void preservesBothUnsavedBufferAndPreviousDiskVersion() throws Exception
    {
        Path file=source();String latest="// ultima citt\u00e0\nclass Example { int latest; }\n";
        try(var service=new SourceSaveService())
        {
            var removed=service.removeSource(file,buffer(file,latest),recovery()).get(5,TimeUnit.SECONDS);
            Set<String> copies=new HashSet<>();for(Path copy:removed.recoveryCopies())copies.add(Files.readString(copy));
            assertEquals(Set.of("original\n",latest),copies);assertFalse(Files.exists(file));
        }
    }
    @Test public void missingDiskFileDoesNotDiscardItsRetainedBuffer() throws Exception
    {
        Path file=source();Files.delete(file);
        try(var service=new SourceSaveService())
        {
            var removed=service.removeSource(file,buffer(file,"retained text\n"),recovery()).get(5,TimeUnit.SECONDS);
            assertEquals(1,removed.recoveryCopies().size());assertEquals("retained text\n",Files.readString(removed.recoveryCopies().getFirst()));
        }
    }
    @Test public void changedExternalFileIsNotRemovedOrOverwritten() throws Exception
    {
        Path file=source();Files.writeString(file,"external\n");
        try(var service=new SourceSaveService())
        {fails(service.removeSource(file,buffer(file,"buffer\n"),recovery()));assertEquals("external\n",Files.readString(file));assertFalse(Files.exists(recovery()));}
    }
    @Test public void unavailableRecoveryStorageLeavesThePrimarySourceInPlace() throws Exception
    {
        Path file=source();Files.writeString(recovery(),"Unrelated user file");
        try(var service=new SourceSaveService())
        {fails(service.removeSource(file,buffer(file,"original\n"),recovery()));assertEquals("original\n",Files.readString(file));assertEquals("Unrelated user file",Files.readString(recovery()));}
    }
    @Test public void activeEditorIdentityCannotBeUsedForRemoval() throws Exception
    {
        Path file=source();var snapshot=new SourceSaveService.Snapshot(new SourceSaveService.Owner(),1,file,"original\n",StandardCharsets.UTF_8,"original\n",true,false);
        try(var service=new SourceSaveService()) {fails(service.removeSource(file,snapshot,recovery()));assertTrue(Files.exists(file));assertFalse(Files.exists(recovery()));}
    }
    @Test public void anOlderAcceptedWriteCannotResurrectARetiredSource() throws Exception
    {
        Path file=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);var owner=new SourceSaveService.Owner();
        var snapshot=new SourceSaveService.Snapshot(owner,1,file,"older pending\n",StandardCharsets.UTF_8,"original\n",true,false);
        try(var service=new SourceSaveService(8,(saved,replacement) -> {
            entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Test storage not released");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IOException(failure);}
            AtomicSourceSave.save(saved.source(),saved.text(),saved.charset(),"#",replacement);
        }))
        {
            var pending=service.save(snapshot);assertTrue(entered.await(5,TimeUnit.SECONDS));owner.retire();
            var removing=service.removeSource(file,buffer(file,"newest unsaved\n"),recovery());assertFalse(removing.isDone());release.countDown();
            assertEquals(SourceSaveService.Outcome.CANCELLED,pending.get(5,TimeUnit.SECONDS).outcome());var removed=removing.get(5,TimeUnit.SECONDS);
            assertFalse(Files.exists(file));assertEquals(2,removed.recoveryCopies().size());
            assertEquals(SourceSaveService.Outcome.CANCELLED,service.save(snapshot).get(5,TimeUnit.SECONDS).outcome());assertFalse(Files.exists(file));
        }
        finally{release.countDown();}
    }

    @Test public void aClosedSourceIsArchivedAsBytesWithoutAssumingUtf8() throws Exception
    {
        Path file=source();byte[] original="// citt\u00e0\r\nclass Example {}\r\n".getBytes(StandardCharsets.ISO_8859_1);Files.write(file,original);
        try(var service=new SourceSaveService())
        {
            var removed=service.removeSource(file,null,recovery()).get(5,TimeUnit.SECONDS);
            assertFalse(Files.exists(file));assertEquals(1,removed.recoveryCopies().size());assertArrayEquals(original,Files.readAllBytes(removed.recoveryCopies().getFirst()));
        }
    }
    @Test public void aNewClassAtTheDeletedNameHasAnIndependentHistoryIdentity() throws Exception
    {
        Path file=source(),preferences=folder.getRoot().toPath().resolve("preferences");LocalHistory oldHistory=new LocalHistory(preferences);
        oldHistory.draft(file,"old unsaved\n",LocalHistory.hash("original\n"),"old editor",2);
        try(var service=new SourceSaveService())
        {var removed=service.removeSource(file,buffer(file,"old unsaved\n"),recovery(),oldHistory).get(5,TimeUnit.SECONDS);assertNull(removed.historyWarning());}
        Files.writeString(file,"new unrelated class\n");
        assertTrue(new LocalHistory(preferences).recover(file,"new unrelated class\n").isEmpty());
        assertEquals("old unsaved\n",oldHistory.recover(file,"original\n").orElseThrow().text());
    }
}
