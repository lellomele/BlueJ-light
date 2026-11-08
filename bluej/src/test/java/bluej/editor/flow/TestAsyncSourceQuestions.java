/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.InitConfig;
import bluej.utility.DialogManager;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestAsyncSourceQuestions extends FXTest
{
    private Stage owner;
    @Override public void start(Stage stage) throws Exception {
        super.start(stage);InitConfig.init();owner=stage;stage.setScene(new Scene(new Pane(),500,300));stage.show();
    }
    private CompletableFuture<Integer> ask() {
        AtomicReference<CompletableFuture<Integer>> request=new AtomicReference<>();
        fx_(() -> request.set(DialogManager.askQuestionFXAsync(owner,"light-source-conflict")));return request.get();
    }
    private DialogPane dialog() {
        return Window.getWindows().stream().filter(Window::isShowing).filter(window -> window.getScene()!=null)
            .map(window -> window.getScene().getRoot()).filter(node -> node instanceof DialogPane).map(node -> (DialogPane)node).findFirst().orElseThrow();
    }
    @Test public void cancellingTheRequestHidesTheModalQuestion() throws Exception {
        var request=ask();fx_(() -> {assertEquals(3,dialog().getButtonTypes().size());});
        request.cancel(false);fx_(() -> {
            assertTrue(request.isCancelled());
            assertFalse(Window.getWindows().stream().filter(Window::isShowing).anyMatch(window -> window.getScene()!=null&&window.getScene().getRoot() instanceof DialogPane));
            assertTrue(owner.isShowing());
        });
    }
    @Test public void windowCloseMeansCancelAndNeverKeepLocal() throws Exception {
        var request=ask();fx_(() -> {
            DialogPane pane=dialog();ButtonType cancel=pane.getButtonTypes().get(2);
            assertTrue(cancel.getButtonData().isCancelButton());assertTrue(((Button)pane.lookupButton(cancel)).isDefaultButton());
            assertFalse(((Button)pane.lookupButton(pane.getButtonTypes().get(1))).isDefaultButton());
            Window window=pane.getScene().getWindow();Event.fireEvent(window,new WindowEvent(window,WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        assertEquals(Integer.valueOf(2),request.get(5,TimeUnit.SECONDS));assertEquals(Integer.valueOf(42),fx(() -> 42));
    }
    @Test public void keepingTheEditorVersionIsASeparateExplicitChoice() throws Exception {
        var request=ask();fx_(() -> {
            DialogPane pane=dialog();ButtonType keep=pane.getButtonTypes().get(1);
            assertFalse(keep.getButtonData().isCancelButton());((Button)pane.lookupButton(keep)).fire();
        });
        assertEquals(Integer.valueOf(1),request.get(5,TimeUnit.SECONDS));
    }
}
