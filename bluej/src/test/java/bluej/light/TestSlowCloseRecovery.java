/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;
import bluej.Config;
import bluej.editor.flow.*;
import bluej.parser.InitConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestSlowCloseRecovery extends FXTest
{
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),offered=new CountDownLatch(1);
    private final CompletableFuture<Boolean> choice=new CompletableFuture<>();
    private SourceSaveService service;
    private Path source;
    private RecoverableEditor editor;
    private final String original="class Example {}\n";
    private final class RecoverableEditor extends FlowEditor
    {
        RecoverableEditor(){super(window -> null,"Example",null,null,null,null,new ReadOnlyBooleanWrapper(true),false);}
        @Override protected SourceSaveService sourceSaveService(){return service;}
        @Override protected javafx.util.Duration closeSaveWarningDelay(){return javafx.util.Duration.millis(50);}
        @Override protected CompletableFuture<Boolean> offerRecoveryForCloseAsync(IOException failure){offered.countDown();return choice;}
        CompletableFuture<Path> preserve(){return writeRecoveryCopyForCloseAsync();}
    }
    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);InitConfig.init();Config.loadFXFonts();source=temporary.getRoot().toPath().resolve("Example.java");Files.writeString(source,original);
        service=new SourceSaveService(8,(snapshot,replacement) -> {
            entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Storage hold expired");}
            catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IOException(failure);}
            AtomicSourceSave.save(snapshot.source(),snapshot.text(),snapshot.charset(),"#",replacement);
        });
        editor=new RecoverableEditor();stage.setScene(new Scene(editor,800,600));stage.show();assertTrue(editor.showFile(source.toString(),StandardCharsets.UTF_8,false,null));
    }
    @After public void drain() throws Exception {release.countDown();if(service!=null)service.closeAsync().get(10,TimeUnit.SECONDS);}
    private void edit(String text){var doc=editor.getSourcePane().getDocument();doc.replaceText(0,doc.getLength(),text);}
    private CompletableFuture<Boolean> closeRequest(){AtomicReference<CompletableFuture<Boolean>> request=new AtomicReference<>();fx_(() -> request.set(editor.saveForCloseAsync()));return request.get();}
    @Test public void keepEditingSettlesCloseBeforeTheHeldWriteAndLatestTextCanBeSavedLater() throws Exception
    {
        String first="class Example { int first; }\n",latest="class Example { int latest; }\n";fx_(() -> edit(first));var close=closeRequest();
        assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(offered.await(5,TimeUnit.SECONDS));
        fx_(() -> {edit(latest);editor.getActions().getActionByName("undo").actionPerformed(false);assertEquals(first,editor.getSourcePane().getDocument().getFullContent());editor.getActions().getActionByName("redo").actionPerformed(false);choice.complete(false);});
        assertFalse(close.get(5,TimeUnit.SECONDS));assertEquals(1,release.getCount());assertEquals(original,Files.readString(source));
        release.countDown();AtomicReference<CompletableFuture<Void>> saved=new AtomicReference<>();fx_(() -> saved.set(editor.saveAsync()));saved.get().get(10,TimeUnit.SECONDS);
        assertEquals(latest,Files.readString(source));fx_(() -> assertFalse(editor.isModified()));
    }
    @Test public void copyChoiceIsReachableBeforeWriteCompletionAndClosingRetiresThatWrite() throws Exception
    {
        String latest="class Example { String text = \"\u03a9\"; }\n";fx_(() -> edit(latest));var close=closeRequest();
        assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(offered.await(5,TimeUnit.SECONDS));AtomicReference<CompletableFuture<Path>> copy=new AtomicReference<>();fx_(() -> copy.set(editor.preserve()));
        Path recovered=copy.get().get(5,TimeUnit.SECONDS);assertEquals(latest,Files.readString(recovered));fx_(() -> choice.complete(true));
        assertTrue(close.get(5,TimeUnit.SECONDS));assertEquals(1,release.getCount());fx_(() -> {assertTrue(editor.isPreparedForClose());editor.closePrepared();});
        release.countDown();service.closeAsync().get(10,TimeUnit.SECONDS);assertEquals(original,Files.readString(source));assertEquals(latest,Files.readString(recovered));Files.delete(recovered);
    }
}
