/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.parser.InitConfig;
import bluej.utility.javafx.FXPlatformSupplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestToStringSnippetEditor extends FXTest
{
    // The first line inherits the source indentation before the trigger.
    private static final String TEMPLATE = "@Override\n    public String toString() {\n"
        + "        return getClass().getSimpleName() + \"{\" + \"\" + \"}\";\n    }";
    private FlowEditor editor;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        Config.loadFXFonts();
        editor = new FlowEditor(null, "Example", null, null, null, () -> {}, new ReadOnlyBooleanWrapper(true), true) {
            @Override public boolean isOpen() { return stage.isShowing(); }
            @Override public Window getWindow() { return stage; }
        };
        stage.setScene(new Scene(editor, 900, 620));
        stage.show();
    }

    private void load(String source, String prefix)
    {
        fx_(() -> {
            FlowEditorPane pane = editor.getSourcePane();
            pane.getDocument().replaceText(0, pane.getDocument().getLength(), source);
            editor.enableParser(true);
            pane.positionCaret(source.lastIndexOf(prefix) + prefix.length());
            editor.getSourceDocument().flushReparseQueue();
            pane.requestFocus();
            editor.undoManager.forgetHistory();
        });
    }

    private String text() { return editor.getSourcePane().getDocument().getFullContent(); }

    private <T> T await(String description, FXPlatformSupplier<T> probe)
    {
        for (int i = 0; i < 400; i++) {
            T value = fx(probe);
            if (value != null) return value;
            sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    @Test public void controlSpaceInsertsTheLiteralTemplateWithOneEditableExpression()
    {
        String source = "public class Example {\n    tostr\n}\n";
        String inserted = source.replace("tostr", TEMPLATE);
        load(source, "tostr");
        fx_(() -> Event.fireEvent(editor.getSourcePane(),
            new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, true, false, false)));
        await("completion of tostr", () -> {
            if (text().equals(inserted)) return true;
            // A unique match may be accepted directly. Otherwise accept the highlighted match.
            Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(scene -> scene != null && scene.lookup(".suggestion-list") != null).findFirst()
                .ifPresent(scene -> Event.fireEvent(scene.getRoot(),
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)));
            return null;
        });
        fx_(() -> {
            FlowEditorPane pane = editor.getSourcePane();
            assertEquals("\"\"", pane.getSelectedText());
            String expression = "\"name=\" + getClass().getName()";
            String edited = inserted.replace("\"\"", expression);
            pane.replaceSelection(expression);
            Event.fireEvent(pane, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
            assertEquals(edited, text());
            assertEquals("", pane.getSelectedText());
            assertEquals(edited.lastIndexOf("\n}"), pane.getCaretPosition());
            editor.getActions().getActionByName("undo").actionPerformed(false);
            assertEquals(inserted, text());
            editor.getActions().getActionByName("undo").actionPerformed(false);
            assertEquals(source, text());
            editor.getActions().getActionByName("redo").actionPerformed(false);
            assertEquals(inserted, text());
            editor.getActions().getActionByName("redo").actionPerformed(false);
            assertEquals(edited, text());
        });
    }

    @Test public void catalogueInsertsTheSameTextAlongsideExistingFieldsAndMethods()
    {
        String source = """
            public class Example<T> {
                private final int[] data = {1, 2};
                private java.util.List<T> values;
                static class Nested { long timestamp; }
                @Override public String toString() { return "handwritten"; }
                tos
            }
            """;
        load(source, "tos");
        fx_(() -> {
            MenuItem browse = editor.getFXMenu().stream().flatMap(menu -> menu.getItems().stream())
                .filter(item -> "light-browse-snippets".equals(item.getId())).findFirst().orElseThrow();
            Platform.runLater(browse::fire);
        });
        DialogPane catalog = await("snippet catalogue", () -> Window.getWindows().stream()
            .filter(Window::isShowing).map(Window::getScene).filter(java.util.Objects::nonNull).map(Scene::getRoot)
            .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
            .filter(pane -> "light-snippet-browser".equals(pane.getId())).findFirst().orElse(null));
        fx_(() -> {
            ((TextField)catalog.lookup("#snippet-search")).setText("tostr");
            ListView<?> list = (ListView<?>)catalog.lookup("#snippet-list");
            assertEquals("tostr", ((SnippetCompletion)list.getSelectionModel().getSelectedItem()).getName());
            ButtonType insert = catalog.getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            ((Button)catalog.lookupButton(insert)).fire();
        });
        String inserted = source.replace("\n    tos\n", "\n    " + TEMPLATE + "\n");
        await("template insertion at cursor", () -> inserted.equals(text()) ? true : null);
        fx_(() -> assertEquals("\"\"", editor.getSourcePane().getSelectedText()));
    }
}
