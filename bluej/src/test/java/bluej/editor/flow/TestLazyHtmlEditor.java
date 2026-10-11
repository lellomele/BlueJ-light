/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.parser.InitConfig;
import java.lang.reflect.Field;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestLazyHtmlEditor extends FXTest
{
    private FlowEditor editor;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage); InitConfig.init(); Config.loadFXFonts();
        editor = new FlowEditor(null, "Example", null, null, null, () -> {}, new ReadOnlyBooleanWrapper(true), true);
        stage.setScene(new Scene(editor, 800, 600)); stage.show();
    }

    private Object field(String name)
    {
        try { Field f = FlowEditor.class.getDeclaredField(name); f.setAccessible(true); return f.get(editor); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test public void sourceEditingDoesNotCreateOrLayoutWebKit()
    {
        fx_(() -> {
            assertNull(field("htmlPane"));
            editor.getSourcePane().getDocument().replaceText(0, 0, "class Example { int answer() { return 42; } }\n");
            assertNull(field("htmlPane"));
            assertEquals(1, ((Pane)field("editorViews")).getChildren().size());
        });
    }

    @Test public void htmlViewIsCreatedOnceAndUnmanagedWhenHidden()
    {
        fx_(() -> {
            try {
                var method = FlowEditor.class.getDeclaredMethod("getHtmlPane"); method.setAccessible(true);
                WebView html = (WebView)method.invoke(editor);
                assertSame(html, method.invoke(editor));
                assertEquals(2, ((Pane)field("editorViews")).getChildren().size());
                assertFalse(html.isVisible()); assertFalse(html.isManaged());
                BooleanProperty viewing = (BooleanProperty)field("viewingHTML");
                viewing.set(true); assertTrue(html.isVisible()); assertTrue(html.isManaged());
                viewing.set(false); assertFalse(html.isVisible()); assertFalse(html.isManaged());
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }
}
