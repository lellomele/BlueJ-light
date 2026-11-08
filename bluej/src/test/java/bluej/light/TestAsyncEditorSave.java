/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import bluej.editor.EditorWatcher;
import bluej.editor.EditorSaveBatch;
import bluej.editor.flow.*;
import bluej.parser.InitConfig;
import bluej.Config;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestAsyncEditorSave extends FXTest
{
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private static final String ORIGINAL="class Example {}\n";
    private Path path;
    private FlowEditor editor;
    private SourceSaveService service;
    private final AtomicReference<Gate> next=new AtomicReference<>();
    private final AtomicBoolean rejectAcknowledgement=new AtomicBoolean();
    private Gate active;

    private static final class Gate
    {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final boolean fail;
        Gate(boolean fail){this.fail=fail;}
    }

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);InitConfig.init();Config.loadFXFonts();
        path=folder.getRoot().toPath().resolve("Example.java");Files.writeString(path,ORIGINAL);
        service=new SourceSaveService(8,(snapshot,replacement)->{
            Gate gate=next.getAndSet(null);
            if(gate!=null) {
                gate.entered.countDown();
                try {if(!gate.release.await(10,TimeUnit.SECONDS))throw new IOException("Controlled storage wait expired");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException(ex);}
                if(gate.fail)throw new IOException("Controlled storage failure");
            }
            AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        });
        EditorWatcher watcher=(EditorWatcher)Proxy.newProxyInstance(EditorWatcher.class.getClassLoader(),new Class<?>[]{EditorWatcher.class},
            (proxy,method,args)->{
                if(method.getName().equals("saveEvent")&&rejectAcknowledgement.getAndSet(false))throw new AssertionError("Injected save observer failure");
                return method.getReturnType()==boolean.class?false:null;
            });
        editor=new FlowEditor(window->null,"Example",watcher,null,null,null,new ReadOnlyBooleanWrapper(true),false) {
            @Override protected SourceSaveService sourceSaveService(){return service;}
        };
        stage.setScene(new Scene(editor,800,600));stage.show();
        assertTrue(editor.showFile(path.toString(),StandardCharsets.UTF_8,false,null));
    }

    @After public void drain() throws Exception
    {
        if(active!=null)active.release.countDown();
        if(service!=null)service.closeAsync().get(10,TimeUnit.SECONDS);
    }

    private Gate arm(boolean fail){active=new Gate(fail);next.set(active);return active;}
    private void text(String value){fx_(() -> {var document=editor.getSourcePane().getDocument();document.replaceText(0,document.getLength(),value);});}
    private CompletableFuture<Void> save()
    {AtomicReference<CompletableFuture<Void>> result=new AtomicReference<>();fx_(() -> result.set(editor.saveAsync()));return result.get();}
    private void saved(CompletableFuture<Void> result) throws Exception {result.get(10,TimeUnit.SECONDS);}

    @Test public void editingAndUndoRedoContinueWhileAnOlderRevisionIsSaving() throws Exception
    {
        String first="class Example { int first; }\n",newer="class Example { int newer; }\n";
        text(first);Gate gate=arm(false);var pending=save();assertTrue(gate.entered.await(5,TimeUnit.SECONDS));
        fx_(() -> {
            assertFalse(pending.isDone());assertEquals(1,gate.release.getCount());
            var document=editor.getSourcePane().getDocument();document.replaceText(0,document.getLength(),newer);
            editor.getActions().getActionByName("undo").actionPerformed(false);assertEquals(first,document.getFullContent());
            editor.getActions().getActionByName("redo").actionPerformed(false);assertEquals(newer,document.getFullContent());
        });
        gate.release.countDown();saved(pending);
        assertEquals(first,Files.readString(path));
        fx_(() -> {assertEquals(newer,editor.getSourcePane().getDocument().getFullContent());assertTrue(editor.isModified());});
        saved(save());assertEquals(newer,Files.readString(path));fx_(() -> assertFalse(editor.isModified()));
    }

    @Test public void twoPendingRevisionsSettleInOrderAndOnlyTheLatestAcknowledgementMarksSaved() throws Exception
    {
        String first="class Example { int first; }\n",newer="class Example { int second; }\n";
        text(first);Gate gate=arm(false);var one=save();assertTrue(gate.entered.await(5,TimeUnit.SECONDS));
        text(newer);var two=save();assertFalse(one.isDone());assertFalse(two.isDone());
        gate.release.countDown();saved(one);saved(two);
        assertEquals(newer,Files.readString(path));fx_(() -> {assertFalse(editor.isModified());assertEquals(newer,editor.getSourcePane().getDocument().getFullContent());});
    }

    @Test public void lateFailurePreservesNewerUnsavedTextAndAllowsRetry() throws Exception
    {
        String first="class Example { int first; }\n",newer="class Example { int afterFailure; }\n";
        text(first);Gate gate=arm(true);var pending=save();assertTrue(gate.entered.await(5,TimeUnit.SECONDS));text(newer);
        gate.release.countDown();try{saved(pending);fail("Expected failed save");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}
        assertEquals(ORIGINAL,Files.readString(path));
        fx_(() -> {assertTrue(editor.isModified());assertEquals(newer,editor.getSourcePane().getDocument().getFullContent());});
        saved(save());assertEquals(newer,Files.readString(path));
    }

    @Test public void changingTheLoadedFileCancelsOldUiResultsAndPreventsTheOldCommit() throws Exception
    {
        Path other=folder.getRoot().toPath().resolve("Other.java");String otherText="class Other {}\n";Files.writeString(other,otherText);
        text("class Example { int pending; }\n");Gate gate=arm(false);var pending=save();assertTrue(gate.entered.await(5,TimeUnit.SECONDS));
        fx_(() -> assertTrue(editor.showFile(other.toString(),StandardCharsets.UTF_8,false,null)));
        gate.release.countDown();try{saved(pending);fail("Expected retired request");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}
        service.closeAsync().get(10,TimeUnit.SECONDS);
        assertEquals(ORIGINAL,Files.readString(path));assertEquals(otherText,Files.readString(other));
        fx_(() -> {assertEquals(otherText,editor.getSourcePane().getDocument().getFullContent());assertFalse(editor.isModified());});
    }

    @Test public void failingUiAcknowledgementSettlesTheRequestAndDoesNotPoisonTheNextSave() throws Exception
    {
        String first="class Example { int first; }\n",next="class Example { int next; }\n";
        text(first);rejectAcknowledgement.set(true);var pending=save();
        try{saved(pending);fail("Expected observer failure");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof AssertionError);}
        assertEquals(first,Files.readString(path));
        text(next);saved(save());assertEquals(next,Files.readString(path));fx_(() -> assertFalse(editor.isModified()));
    }

    @Test public void batchRevisitsAnEarlierEditorChangedWhileAnotherFileIsSaving() throws Exception
    {
        Path otherPath=folder.getRoot().toPath().resolve("Other.java");Files.writeString(otherPath,"class Other {}\n");
        AtomicReference<FlowEditor> otherRef=new AtomicReference<>();
        fx_(() -> {
            FlowEditor other=new FlowEditor(window->null,"Other",null,null,null,null,new ReadOnlyBooleanWrapper(true),false) {
                @Override protected SourceSaveService sourceSaveService(){return service;}
            };
            assertTrue(other.showFile(otherPath.toString(),StandardCharsets.UTF_8,false,null));
            var document=other.getSourcePane().getDocument();document.replaceText(0,document.getLength(),"class Other { int saved; }\n");
            otherRef.set(other);
        });
        String first="class Example { int first; }\n",newer="class Example { int latest; }\n";
        text(first);Gate one=arm(false);AtomicReference<CompletableFuture<Void>> batchRef=new AtomicReference<>();
        fx_(() -> batchRef.set(EditorSaveBatch.save(() -> java.util.List.of(editor,otherRef.get()),null)));
        assertTrue(one.entered.await(5,TimeUnit.SECONDS));Gate two=arm(false);one.release.countDown();
        assertTrue(two.entered.await(5,TimeUnit.SECONDS));text(newer);assertFalse(batchRef.get().isDone());
        two.release.countDown();saved(batchRef.get());
        assertEquals(newer,Files.readString(path));assertEquals("class Other { int saved; }\n",Files.readString(otherPath));
        fx_(() -> {assertFalse(editor.isModified());assertFalse(otherRef.get().isModified());});
    }

    @Test public void closeWaitsForTheLatestTextAndAnUndoInvalidatesItsPermission() throws Exception
    {
        String first="class Example { int first; }\n",latest="class Example { int latest; }\n";
        text(first);Gate gate=arm(false);AtomicReference<CompletableFuture<Boolean>> close=new AtomicReference<>();
        fx_(() -> close.set(editor.saveForCloseAsync()));assertTrue(gate.entered.await(5,TimeUnit.SECONDS));
        text(latest);fx_(() -> {assertFalse(close.get().isDone());assertFalse(editor.isPreparedForClose());});
        gate.release.countDown();assertTrue(close.get().get(10,TimeUnit.SECONDS));assertEquals(latest,Files.readString(path));
        fx_(() -> {
            assertTrue(editor.isPreparedForClose());editor.getActions().getActionByName("undo").actionPerformed(false);
            assertEquals(first,editor.getSourcePane().getDocument().getFullContent());assertFalse(editor.isPreparedForClose());
            editor.getActions().getActionByName("redo").actionPerformed(false);assertFalse(editor.isPreparedForClose());
        });
        saved(save());assertEquals(latest,Files.readString(path));
    }

    @Test public void verifiedRecoveryCopyCanCloseWhileTheOrdinarySourceWriterIsHeld() throws Exception
    {
        String latest="class Example { int emergency; }\n";
        text(latest);Gate gate=arm(false);var pending=save();assertTrue(gate.entered.await(5,TimeUnit.SECONDS));
        AtomicReference<CompletableFuture<Path>> backup=new AtomicReference<>();
        fx_(() -> {
            try
            {
                var method=FlowEditor.class.getDeclaredMethod("writeRecoveryCopyForCloseAsync");method.setAccessible(true);
                @SuppressWarnings("unchecked") CompletableFuture<Path> copy=(CompletableFuture<Path>)method.invoke(editor);backup.set(copy);
            }
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
        Path copy=backup.get().get(5,TimeUnit.SECONDS);assertEquals(latest,Files.readString(copy));
        AtomicReference<CompletableFuture<Boolean>> close=new AtomicReference<>();fx_(() -> close.set(editor.saveForCloseAsync()));
        assertTrue(close.get().get(5,TimeUnit.SECONDS));assertEquals(1,gate.release.getCount());
        fx_(() -> {assertTrue(editor.isPreparedForClose());editor.closePrepared();});
        gate.release.countDown();
        try{saved(pending);fail("Closed owner must settle the old request as cancelled");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}
        service.closeAsync().get(10,TimeUnit.SECONDS);
        assertEquals(ORIGINAL,Files.readString(path));assertEquals(latest,Files.readString(copy));
        fx_(() -> assertTrue(editor.saveForCompilationAsync(bluej.compiler.CompileType.ERROR_CHECK_ONLY).isCompletedExceptionally()));
        Files.delete(copy);
    }

    @Test public void saveAllStillSavesOtherFilesAfterOneSourceFails() throws Exception
    {
        Path otherPath=folder.getRoot().toPath().resolve("Other.java");Files.writeString(otherPath,"class Other {}\n");
        AtomicReference<FlowEditor> otherRef=new AtomicReference<>();
        fx_(() -> {
            FlowEditor other=new FlowEditor(window->null,"Other",null,null,null,null,new ReadOnlyBooleanWrapper(true),false) {
                @Override protected SourceSaveService sourceSaveService(){return service;}
            };
            assertTrue(other.showFile(otherPath.toString(),StandardCharsets.UTF_8,false,null));
            var document=other.getSourcePane().getDocument();document.replaceText(0,document.getLength(),"class Other { int saved; }\n");otherRef.set(other);
        });
        text("class Example { int blocked; }\n");Gate gate=arm(true);AtomicReference<CompletableFuture<Void>> batch=new AtomicReference<>();
        fx_(() -> batch.set(EditorSaveBatch.saveAll(() -> java.util.List.of(editor,otherRef.get()))));
        assertTrue(gate.entered.await(5,TimeUnit.SECONDS));gate.release.countDown();
        try{saved(batch.get());fail("Expected combined failure");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}
        assertEquals(ORIGINAL,Files.readString(path));assertEquals("class Other { int saved; }\n",Files.readString(otherPath));
        fx_(() -> {assertTrue(editor.isModified());assertFalse(otherRef.get().isModified());});
    }
}
