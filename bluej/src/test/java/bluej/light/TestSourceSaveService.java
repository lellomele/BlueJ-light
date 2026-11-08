/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestSourceSaveService
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Path source() throws IOException
    { Path file=folder.getRoot().toPath().resolve("Example.java");Files.writeString(file,"original\n");return file; }
    private SourceSaveService.Snapshot snapshot(SourceSaveService.Owner owner,long revision,Path path,String text,String expected)
    { return new SourceSaveService.Snapshot(owner,revision,path,text,StandardCharsets.UTF_8,expected,true,false); }
    private SourceSaveService.Result await(CompletableFuture<SourceSaveService.Result> future) throws Exception
    { return future.get(5,TimeUnit.SECONDS); }
    private void waitFor(CountDownLatch latch) throws IOException
    { try {if(!latch.await(5,TimeUnit.SECONDS))throw new IOException("Test storage wait expired");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException(ex);} }

    @Test public void queuedRevisionsRemainOrderedWithoutFalseExternalConflict() throws Exception
    {
        Path path=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();AtomicReference<String> thread=new AtomicReference<>();
        try(SourceSaveService service=new SourceSaveService(8,(snapshot,replacement)->{
            thread.set(Thread.currentThread().getName());
            if(calls.getAndIncrement()==0){entered.countDown();waitFor(release);}
            AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var owner=new SourceSaveService.Owner();
            var first=service.save(snapshot(owner,1,path,"first α\n","original\n"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var second=service.save(snapshot(owner,2,path,"second €\n","original\n"));
            assertFalse(first.isDone());assertFalse(second.isDone());assertEquals("original\n",Files.readString(path));
            release.countDown();assertTrue(await(first).saved());assertTrue(await(second).saved());
            assertEquals("second €\n",Files.readString(path));assertEquals("BlueJ source saves",thread.get());
        } finally {release.countDown();}
    }

    @Test public void externalChangeDuringSlowWriteIsPreservedAtCommitBoundary() throws Exception
    {
        Path path=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(SourceSaveService service=new SourceSaveService(8,(snapshot,replacement)->{
            entered.countDown();waitFor(release);
            AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var future=service.save(snapshot(new SourceSaveService.Owner(),1,path,"editor\n","original\n"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));Files.writeString(path,"external\n");release.countDown();
            var result=await(future);assertEquals(SourceSaveService.Outcome.CONFLICT,result.outcome());
            assertEquals("external\n",result.diskText());assertEquals("external\n",Files.readString(path));
            assertEquals("external\n",Files.readString(path.resolveSibling("Example.java#")));
        } finally {release.countDown();}
    }

    @Test public void staleSnapshotCannotOverwriteANewerAcceptedRevision() throws Exception
    {
        Path path=source();var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService()) {
            assertTrue(await(service.save(snapshot(owner,5,path,"newer\n","original\n"))).saved());
            assertEquals(SourceSaveService.Outcome.CANCELLED,await(service.save(snapshot(owner,4,path,"older\n","original\n"))).outcome());
            assertEquals("newer\n",Files.readString(path));
        }
    }

    @Test public void retiringOwnerDuringWritePreventsItsLaterCommit() throws Exception
    {
        Path path=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService(8,(snapshot,replacement)->{
            entered.countDown();waitFor(release);AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var future=service.save(snapshot(owner,1,path,"retired\n","original\n"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));owner.retire();release.countDown();
            assertEquals(SourceSaveService.Outcome.CANCELLED,await(future).outcome());assertEquals("original\n",Files.readString(path));
        } finally {release.countDown();}
    }

    @Test public void boundedWriterRejectsExplicitlyAndCloseAcknowledgesActualDrain() throws Exception
    {
        Path path=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService(1,(snapshot,replacement)->{
            entered.countDown();waitFor(release);AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var accepted=service.save(snapshot(owner,1,path,"accepted\n","original\n"));assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals(SourceSaveService.Outcome.FAILED,await(service.save(snapshot(owner,2,path,"rejected\n","original\n"))).outcome());
            var drained=service.closeAsync();assertFalse(drained.isDone());
            assertEquals(SourceSaveService.Outcome.FAILED,await(service.save(snapshot(owner,3,path,"closed\n","original\n"))).outcome());
            release.countDown();assertTrue(await(accepted).saved());drained.get(5,TimeUnit.SECONDS);assertEquals(0,service.pendingCount());
        } finally {release.countDown();}
    }

    @Test public void failedWriteDoesNotPoisonLaterRequestsOrTheirObservers() throws Exception
    {
        Path path=source();AtomicInteger calls=new AtomicInteger();var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService(8,(snapshot,replacement)->{
            if(calls.getAndIncrement()==0)throw new IOException("injected");
            AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var failed=service.save(snapshot(owner,1,path,"first\n","original\n"));
            var badObserver=failed.thenAccept(result->{throw new IllegalStateException("observer failure");});
            assertEquals(SourceSaveService.Outcome.FAILED,await(failed).outcome());
            assertTrue(await(service.save(snapshot(owner,2,path,"retry\n","original\n"))).saved());
            assertTrue(badObserver.isCompletedExceptionally());assertEquals("retry\n",Files.readString(path));
        }
    }

    @Test public void secondEditorIsNotMistakenForTheFirstEditorsOwnWrite() throws Exception
    {
        Path path=source();
        try(SourceSaveService service=new SourceSaveService()) {
            assertTrue(await(service.save(snapshot(new SourceSaveService.Owner(),1,path,"other editor\n","original\n"))).saved());
            assertEquals(SourceSaveService.Outcome.CONFLICT,await(service.save(snapshot(new SourceSaveService.Owner(),1,path,"overwrite\n","original\n"))).outcome());
            assertEquals("other editor\n",Files.readString(path));
        }
    }

    @Test public void newRecoveryCopyIsCreatedAndVerifiedOnWorker() throws Exception
    {
        Path path=folder.getRoot().toPath().resolve("recovery/sub/copy.java");
        try(SourceSaveService service=new SourceSaveService()) {
            var copy=new SourceSaveService.Snapshot(new SourceSaveService.Owner(),1,path,"copy α €\n",StandardCharsets.UTF_8,null,true,true);
            var result=await(service.save(copy));assertTrue(result.saved());assertEquals("copy α €\n",Files.readString(path));
            assertTrue(result.byteLength()>0);assertTrue(result.modifiedMillis()>0);
        }
    }

    @Test public void differentProjectWritersCannotCommitOverEachOthersUncheckedVersion() throws Exception
    {
        Path path=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(SourceSaveService first=new SourceSaveService(8,(snapshot,replacement)->{
            entered.countDown();waitFor(release);AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        });SourceSaveService second=new SourceSaveService()) {
            var one=first.save(snapshot(new SourceSaveService.Owner(),1,path,"first project\n","original\n"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var two=second.save(snapshot(new SourceSaveService.Owner(),1,path,"second project\n","original\n"));
            assertFalse(two.isDone());release.countDown();assertTrue(await(one).saved());
            assertEquals(SourceSaveService.Outcome.CONFLICT,await(two).outcome());assertEquals("first project\n",Files.readString(path));
        } finally {release.countDown();}
    }

    @Test public void inspectionFollowsPendingWriteAndReportsTheCommittedVersion() throws Exception
    {
        Path path=source();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(SourceSaveService service=new SourceSaveService(8,(snapshot,replacement)->{
            entered.countDown();waitFor(release);AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var write=service.save(snapshot(new SourceSaveService.Owner(),1,path,"written α\n","original\n"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));var inspect=service.inspect(path,StandardCharsets.UTF_8);
            assertFalse(inspect.isDone());release.countDown();assertTrue(await(write).saved());
            var state=inspect.get(5,TimeUnit.SECONDS);assertTrue(state.exists());assertEquals("written α\n",state.text());
            assertTrue(state.byteLength()>0);assertTrue(state.modifiedMillis()>0);
        } finally {release.countDown();}
    }

    private void relocationFails(CompletableFuture<?> result) throws Exception
    {
        try{result.get(5,TimeUnit.SECONDS);fail("Expected rejected relocation");}
        catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}
    }

    @Test public void renamingConsumesTheOldIdentityAndPreventsRecreatingItsSource() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling("Renamed.java");var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService()) {
            var moved=service.relocate(snapshot(owner,1,path,"original\n","original\n"),renamed).get(5,TimeUnit.SECONDS);
            assertEquals(renamed,moved.source());assertEquals("original\n",Files.readString(renamed));assertFalse(Files.exists(path));assertTrue(owner.isRetired());
            assertEquals(SourceSaveService.Outcome.CANCELLED,await(service.save(snapshot(owner,2,path,"obsolete\n","original\n"))).outcome());
            assertFalse(Files.exists(path));
            assertTrue(await(service.save(snapshot(new SourceSaveService.Owner(),2,renamed,"new identity\n","original\n"))).saved());
            assertEquals("new identity\n",Files.readString(renamed));
        }
    }

    @Test public void renameNeverReplacesAnExistingSourceAndAFailedMoveDoesNotRetireTheEditor() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling("Occupied.java");Files.writeString(renamed,"unrelated source\n");var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService()) {
            relocationFails(service.relocate(snapshot(owner,1,path,"original\n","original\n"),renamed));
            assertEquals("unrelated source\n",Files.readString(renamed));assertEquals("original\n",Files.readString(path));assertFalse(owner.isRetired());
            assertTrue(await(service.save(snapshot(owner,2,path,"retry\n","original\n"))).saved());assertEquals("retry\n",Files.readString(path));
        }
    }

    @Test public void renameRejectsChangedDiskTextWithoutMovingIt() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling("Renamed.java");Files.writeString(path,"external version\n");var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService()) {
            relocationFails(service.relocate(snapshot(owner,1,path,"original\n","original\n"),renamed));
            assertEquals("external version\n",Files.readString(path));assertFalse(Files.exists(renamed));assertFalse(owner.isRetired());
        }
    }

    @Test public void retiredEditorCannotRenameItsOldFile() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling("Renamed.java");var owner=new SourceSaveService.Owner();owner.retire();
        try(SourceSaveService service=new SourceSaveService()) {
            relocationFails(service.relocate(snapshot(owner,1,path,"original\n","original\n"),renamed));
            assertEquals("original\n",Files.readString(path));assertFalse(Files.exists(renamed));
        }
    }

    @Test public void renameWaitsForItsSourceWriteAndUsesTheCommittedRevision() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling("Renamed.java");var owner=new SourceSaveService.Owner();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(SourceSaveService service=new SourceSaveService(8,(snapshot,replacement)-> {
            entered.countDown();waitFor(release);AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        })) {
            var write=service.save(snapshot(owner,1,path,"saved before rename\n","original\n"));assertTrue(entered.await(5,TimeUnit.SECONDS));
            var move=service.relocate(snapshot(owner,1,path,"saved before rename\n","original\n"),renamed);assertFalse(move.isDone());
            release.countDown();assertTrue(await(write).saved());move.get(5,TimeUnit.SECONDS);
            assertEquals("saved before rename\n",Files.readString(renamed));assertFalse(Files.exists(path));
        } finally {release.countDown();}
    }

    @Test public void aNewerAcceptedRevisionMakesAnOlderRenameObsolete() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling("Renamed.java");var owner=new SourceSaveService.Owner();
        try(SourceSaveService service=new SourceSaveService()) {
            assertTrue(await(service.save(snapshot(owner,2,path,"newer\n","original\n"))).saved());
            relocationFails(service.relocate(snapshot(owner,1,path,"original\n","original\n"),renamed));
            assertEquals("newer\n",Files.readString(path));assertFalse(Files.exists(renamed));assertFalse(owner.isRetired());
        }
    }

    @Test public void caseOnlyRenamePreservesTheSourceAndUpdatesItsDirectoryEntry() throws Exception
    {
        Path path=source(),renamed=path.resolveSibling(path.getFileName().toString().toUpperCase(java.util.Locale.ROOT).replace(".JAVA",".java"));
        assertNotEquals(path.getFileName().toString(),renamed.getFileName().toString());
        try(SourceSaveService service=new SourceSaveService()) {
            service.relocate(snapshot(new SourceSaveService.Owner(),1,path,"original\n","original\n"),renamed).get(5,TimeUnit.SECONDS);
            assertEquals("original\n",Files.readString(renamed));
            try(var files=Files.list(path.getParent())){assertTrue(files.anyMatch(file -> file.getFileName().toString().equals(renamed.getFileName().toString())));}
        }
    }
}
