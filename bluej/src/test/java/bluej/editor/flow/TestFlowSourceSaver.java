/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.light.*;
import bluej.parser.InitConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestFlowSourceSaver extends FXTest
{
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private SourceSaveService service;
    private View view;
    private FlowSourceSaver saver;
    private Gate active;
    private static final class Gate {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        void waitHere() throws IOException {
            entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Storage hold expired");}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}
        }
    }
    private final class View implements FlowSourceSaver.View {
        Path path;String text="local\n",disk="baseline\n";long revision=1;boolean modified=true;
        int decisions,cancelled,reloads,failures;
        CompletableFuture<FlowSourceSaver.ConflictChoice> choice;
        final LocalHistory history=new LocalHistory(folder.getRoot().toPath().resolve("history"));
        @Override public FlowSourceSaver.State state(){return new FlowSourceSaver.State(path,text,StandardCharsets.UTF_8,revision,disk,modified,true);}
        @Override public SourceSaveService service(){return service;}
        @Override public LocalHistory history(){return history;}
        @Override public void saved(SourceSaveService.Result result){disk=result.diskText();if(revision==result.snapshot().revision())modified=false;}
        @Override public void failed(IOException error){failures++;}
        @Override public CompletableFuture<FlowSourceSaver.ConflictChoice> decideConflict(){decisions++;choice=new CompletableFuture<>();return choice;}
        @Override public void conflictFinished(){}
        @Override public void cancelConflict(){cancelled++;if(choice!=null)choice.cancel(false);}
        @Override public void reloaded(SourceSaveService.DiskState state){reloads++;text=state.text();disk=text;revision++;modified=false;}
    }
    @Override public void start(Stage stage) throws Exception {
        super.start(stage);InitConfig.init();stage.setScene(new Scene(new Pane(),500,300));stage.show();
        service=new SourceSaveService();
        view=new View();view.path=folder.getRoot().toPath().resolve("Example.java");Files.writeString(view.path,"external\n");
        saver=new FlowSourceSaver(view);
    }
    @After public void drain() throws Exception {
        if(saver!=null)fx_(saver::reset);
        if(active!=null)active.release.countDown();if(service!=null)service.closeAsync().get(10,TimeUnit.SECONDS);
        HistoryTasks.submitAsync(() -> {}).get(5,TimeUnit.SECONDS);
    }
    private CompletableFuture<Void> save(){AtomicReference<CompletableFuture<Void>> request=new AtomicReference<>();fx_(() -> request.set(saver.saveAsync()));return request.get();}
    private void decisionReady() throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<limit){if(fx(() -> view.choice!=null))return;Thread.sleep(10);}fail("Conflict decision missing");
    }
    private Gate holdInspection() throws Exception {
        active=new Gate();Gate gate=active;service.submit(() -> {gate.waitHere();return null;});assertTrue(gate.entered.await(5,TimeUnit.SECONDS));return gate;
    }
    private void failed(CompletableFuture<Void> request) throws Exception {
        try{request.get(5,TimeUnit.SECONDS);fail("Expected explicit cancellation/failure");}
        catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}
    }
    @Test public void resetCancelsTheDecisionAndSettlesItsRequest() throws Exception {
        var request=save();decisionReady();fx_(saver::reset);failed(request);
        fx_(() -> {assertEquals(1,view.cancelled);assertFalse(saver.pending());assertEquals(0,view.failures);});
        assertEquals("external\n",Files.readString(view.path));
    }
    @Test public void resetSettlesReloadWhileTheInspectionWorkerIsStillHeld() throws Exception {
        var request=save();decisionReady();Gate gate=holdInspection();
        fx_(() -> {view.choice.complete(FlowSourceSaver.ConflictChoice.RELOAD);assertTrue(saver.pending());assertSame(request,saver.saveAsync());saver.reset();});
        failed(request);assertEquals(1,gate.release.getCount());
        fx_(() -> {view.text="another source\n";view.revision++;assertFalse(saver.pending());});
        gate.release.countDown();service.closeAsync().get(5,TimeUnit.SECONDS);fx_(() -> {});
        fx_(() -> {assertEquals("another source\n",view.text);assertEquals(0,view.reloads);assertEquals(0,view.failures);});
        assertEquals("external\n",Files.readString(view.path));
    }
    @Test public void editsDuringReloadAreRetainedAndRequireANewConflictDecision() throws Exception {
        var request=save();decisionReady();Gate gate=holdInspection();
        fx_(() -> {view.choice.complete(FlowSourceSaver.ConflictChoice.RELOAD);view.text="newer\n";view.revision++;});
        gate.release.countDown();failed(request);
        fx_(() -> {assertEquals("newer\n",view.text);assertTrue(view.modified);assertEquals(0,view.reloads);view.choice=null;});
        var retry=save();decisionReady();fx_(() -> {assertEquals(2,view.decisions);view.choice.complete(FlowSourceSaver.ConflictChoice.KEEP_LOCAL);});
        retry.get(5,TimeUnit.SECONDS);assertEquals("newer\n",Files.readString(view.path));
    }
    @Test public void aNewerSaveDuringKeepLocalWaitsForAndPersistsItsOwnRevision() throws Exception {
        var older=save();decisionReady();holdInspection();
        fx_(() -> view.choice.complete(FlowSourceSaver.ConflictChoice.KEEP_LOCAL));
        fx_(() -> {view.text="newest\n";view.revision++;});var newer=save();assertFalse(newer.isDone());
        fx_(() -> assertTrue(saver.pending()));active.release.countDown();older.get(5,TimeUnit.SECONDS);newer.get(5,TimeUnit.SECONDS);
        assertEquals("newest\n",Files.readString(view.path));fx_(() -> {assertFalse(view.modified);assertFalse(saver.pending());assertEquals(1,view.decisions);});
    }
}
