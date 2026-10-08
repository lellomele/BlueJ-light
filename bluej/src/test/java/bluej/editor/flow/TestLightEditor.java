/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import bluej.Config;
import bluej.editor.EditorWatcher;
import bluej.parser.InitConfig;
import bluej.pkgmgr.AboutDialogTemplate;
import bluej.prefmgr.PrefMgr;
import java.io.File;
import java.lang.reflect.Proxy;
import javax.imageio.ImageIO;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestLightEditor extends FXTest
{
    private FlowEditor editor;
    private Stage stage;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        Config.loadFXFonts();
        this.stage = stage;
        EditorWatcher watcher = (EditorWatcher)Proxy.newProxyInstance(EditorWatcher.class.getClassLoader(),
            new Class<?>[] {EditorWatcher.class}, (object, method, args) -> {
                if (method.getName().equals("breakpointToggleEvent")) return args[1];
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        editor = new FlowEditor(window -> null, "Example", watcher, null, null, null,
            new ReadOnlyBooleanWrapper(true), true);
        stage.setScene(new Scene(editor, 960, 640));
        stage.setTitle("BlueJ light 5.5.2");
        stage.show();
    }

    @Test public void snippetWorksThroughTheCompletionAndKeyboard()
    {
        String source = "public class Example {\n    void work() {\n        fori\n    }\n}";
        fx_(() -> {
            PrefMgr.setFlag(PrefMgr.AUTOMATIC_COMPLETION, false);
            editor.getSourcePane().getDocument().replaceText(0, 0, source);
            editor.enableParser(true);
            editor.getSourcePane().positionCaret(source.indexOf("fori") + 4);
            editor.getSourcePane().requestFocus();
        });
        sleep(300);
        fx_(() -> editor.createContentAssist());
        sleep(300);
        push(KeyCode.ENTER);
        sleep(150);
        String actual = fx(() -> editor.getSourcePane().getDocument().getFullContent());
        String context = fx(() -> "position=" + editor.getSourcePane().getCaretPosition()
            + " visible=" + editor.getSourcePane().isVisible() + " showing=" + stage.isShowing()
            + " code=" + ((JavaSyntaxView)editor.getSourceDocument()).isCodePosition(editor.getSourcePane().getCaretPosition())
            + " expression=" + editor.getSourceDocument().getParser().getExpressionType(editor.getSourcePane().getCaretPosition(), editor.getSourceDocument())
            + " bounds=" + editor.getSourcePane().getCaretBoundsOnScreen(source.indexOf("fori"))
            + " scope=" + editor.getSourceDocument().getParser().getContainingMethodOrClassNode(editor.getSourcePane().getCaretPosition())
            + " snippets=" + new SnippetCatalog().completions(true).stream().map(SnippetCompletion::getName).toList()
            + " library=" + Config.getBlueJLibDir());
        assertTrue(context + "\n" + actual, actual.contains("for (int i = 0; i < limit; i++)"));
        write("index");
        assertTrue(fx(() -> editor.getSourcePane().getDocument().getFullContent().contains("int index = 0; index < limit; index++")));
        push(KeyCode.TAB);
        assertEquals("limit", fx(() -> editor.getSourcePane().getSelectedText()));
        write("10");
        push(KeyCode.TAB);
        assertEquals("", fx(() -> editor.getSourcePane().getSelectedText()));
        fx_(() -> snapshot("editor-snippet.png"));
    }

    @Test public void largeDocumentRemainsVirtualised()
    {
        String source = "public class Example {\n    int value;\n    void work() {\n" + "        value++;\n".repeat(20000) + "    }\n}";
        fx_(() -> {
            editor.getSourcePane().getDocument().replaceText(0, 0, source);
            editor.enableParser(true);
            editor.getSourcePane().positionCaret(source.length() / 2);
        });
        sleep(1500);
        int visibleLines = fx(() -> stage.getScene().getRoot().lookupAll(".text-line").size());
        assertTrue(visibleLines > 0 && visibleLines < 100);
        fx_(() -> snapshot("editor-large-document.png"));
    }

    @Test public void aboutContainsVersionAndCopyright()
    {
        fx_(() -> {
            Image logo = Config.getFixedImageAsFXImage("bluej-icon-256.png");
            AboutDialogTemplate about = new AboutDialogTemplate(stage, "5.5.2-light", "https://www.bluej.org/",
                logo, new String[0], new String[0]);
            about.show();
            String labels = about.getDialogPane().lookupAll(".label").stream().filter(Label.class::isInstance)
                .map(node -> ((Label)node).getText()).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(labels.contains("5.5.2-light"));
            assertTrue(labels.contains("\u00a9 2026 - Prof. Ing. Raffaele Mele"));
            try
            {
                File folder = new File("build/light-screenshots");
                folder.mkdirs();
                ImageIO.write(SwingFXUtils.fromFXImage(about.getDialogPane().snapshot(null, null), null),
                    "png", new File(folder, "about.png"));
            }
            catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            about.close();
        });
    }

    @Test public void scopeCacheSurvivesJumpsEditsResizeAndFontChanges()
    {
        String source = "public class Example {\n" +
            "    void work() {\n        int value = 1;\n        if (value > 0) {\n            value++;\n        }\n    }\n".repeat(180) + "}\n";
        fx_(() -> {
            editor.getSourcePane().getDocument().replaceText(0, 0, source);
            editor.enableParser(true);
            editor.getSourceDocument().flushReparseQueue();
        });
        sleep(500);
        int oldSize = fx(() -> PrefMgr.getEditorFontSize().get());
        try
        {
            for (int line : new int[] {1000, 5, 600, 0})
            {
                fx_(() -> {
                    editor.getSourcePane().scrollTo(line);
                    stage.getScene().getRoot().applyCss();
                    stage.getScene().getRoot().layout();
                });
                sleep(250);
                fx_(() -> checkScopeCache());
            }
            fx_(() -> {
                editor.getSourcePane().scrollTo(600);
                int position = editor.getSourcePane().getDocument().getLineStart(602);
                editor.getSourcePane().getDocument().replaceText(position, position, "        // inserted line\n");
                editor.getSourceDocument().flushReparseQueue();
                stage.setWidth(780);
                stage.setHeight(520);
                PrefMgr.setEditorFontSize(oldSize + 2);
            });
            sleep(600);
            fx_(() -> {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                checkScopeCache();
                assertTrue(editor.getSourcePane().getDocument().getFullContent().contains("// inserted line"));
                snapshot("editor-scrolling-5.5.2.png");
            });
        }
        finally { fx_(() -> PrefMgr.setEditorFontSize(oldSize)); }
    }

    @Test public void searchErrorUnderlinesAndBreakpointsSurviveScrollJumps()
    {
        String source = "public class Example {\n" + java.util.stream.IntStream.range(0, 180)
            .mapToObj(i -> "    void work" + i + "() {\n        int value = 1;\n        if (value > 0) {\n            value++;\n        }\n    }\n")
            .collect(java.util.stream.Collectors.joining()) + "}\n";
        int position = source.indexOf("value", source.length() / 2);
        int line = fx(() -> {
            FlowEditorPane pane = editor.getSourcePane();
            pane.getDocument().replaceText(0, 0, source);
            editor.enableParser(true);
            editor.getSourceDocument().flushReparseQueue();
            return pane.getDocument().getLineFromPosition(position);
        });
        sleep(300);
        fx_(() -> {
            FlowEditorPane pane = editor.getSourcePane();
            pane.positionCaret(position);
            editor.toggleBreakpoint();
            editor.doFind("value", false).highlightAll();
            pane.setErrorQuery(() -> java.util.List.of(new javafx.scene.control.IndexRange(position, position + 5)));
        });
        for (int destination : new int[] {0, 1100, line})
        {
            fx_(() -> {
                editor.getSourcePane().scrollTo(destination);
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
            });
            sleep(250);
        }
        assertTrue(fx(() -> editor.getBreakpointLines().contains(line)));
        fx_(() -> snapshot("editor-annotations-552.png"));
        assertTrue(fx(() -> stage.getScene().getRoot().lookupAll(".flow-find-result").stream()
            .map(javafx.scene.shape.Path.class::cast).anyMatch(path -> path.isVisible() && !path.getElements().isEmpty())));
        assertTrue(fx(() -> stage.getScene().getRoot().lookupAll(".text-line").stream().anyMatch(node -> {
                javafx.scene.shape.Path underline = (javafx.scene.shape.Path)privateField(node, "errorUnderlineShape");
                return underline.isVisible() && !underline.getElements().isEmpty();
            })));
        assertTrue(fx(() -> stage.getScene().getRoot().lookupAll(".margin-and-text-line").stream()
                .anyMatch(node -> ((javafx.scene.Node)privateField(node, "breakpointIcon")).getOpacity() == 1.0)));
    }

    private Object privateField(Object target, String name)
    {
        try
        {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private void checkScopeCache()
    {
        try
        {
            java.lang.reflect.Field live = JavaSyntaxView.class.getDeclaredField("scopeBackgrounds");
            live.setAccessible(true);
            Object cache = live.get(editor.getSourceDocument());
            java.lang.reflect.Field source = cache.getClass().getDeclaredField("sourceInfo");
            source.setAccessible(true);
            java.util.Map<?, ?> entries = (java.util.Map<?, ?>)source.get(cache);
            int[] range = editor.getSourcePane().getLineRangeVisible();
            assertFalse("Visible scopes should remain present", entries.isEmpty());
            for (Object line : entries.keySet())
                assertTrue("Off-screen scope " + line, (Integer)line >= range[0] && (Integer)line <= range[1]);
            assertTrue(entries.size() <= range[1] - range[0] + 1);
        }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test public void snippetCatalogCanBeSearchedAndFiltered()
    {
        fx_(() -> {
            SnippetCatalog catalog = new SnippetCatalog();
            assertTrue(catalog.allCompletions().size() >= 17);
            SnippetBrowserDialog browser = new SnippetBrowserDialog(stage, catalog, true, true, false);
            browser.show();
            javafx.scene.control.TextField query = (javafx.scene.control.TextField)browser.getDialogPane().lookup("#snippet-search");
            javafx.scene.control.ListView<?> list = (javafx.scene.control.ListView<?>)browser.getDialogPane().lookup("#snippet-list");
            query.setText("fori");
            assertEquals(1, list.getItems().size());
            assertEquals("fori", ((SnippetCompletion)list.getItems().get(0)).getName());
            assertTrue(((javafx.scene.control.TextArea)browser.getDialogPane().lookup("#snippet-preview")).getText().contains("i < limit"));
            query.setText("System.out");
            assertEquals(1, list.getItems().size());
            assertEquals("sout", ((SnippetCompletion)list.getItems().get(0)).getName());
            query.setText("main");
            assertEquals(1, list.getItems().size());
            javafx.scene.control.Button insert = (javafx.scene.control.Button)browser.getDialogPane()
                .lookupButton(browser.getDialogPane().getButtonTypes().get(0));
            assertTrue(insert.isDisabled());
            browser.close();
        });
    }

    private void snapshot(String name)
    {
        try
        {
            File folder = new File("build/light-screenshots");
            folder.mkdirs();
            ImageIO.write(SwingFXUtils.fromFXImage(stage.getScene().snapshot(null), null), "png", new File(folder, name));
        }
        catch (java.io.IOException ex) { throw new RuntimeException(ex); }
    }
}
