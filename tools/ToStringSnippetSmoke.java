/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javax.tools.ToolProvider;

/** Verifies palette and keyboard insertion, the selected placeholder, and both compiled results. */
public class ToStringSnippetSmoke extends BootSmoke {
    private static final String ORIGINAL = """
        public class Exhibit {
            private String name = "Lab";
            private int[] points = {3, 5};

        }
        """;
    private static final String DEFAULT_RETURN = "return getClass().getSimpleName() + \"{\" + \"\" + \"}\";";

    public static void main(String[] args) {
        Path folder = Path.of(args[0]);
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("Exhibit.java"), ORIGINAL);
            Files.writeString(folder.resolve("package.bluej"), "project.charset=UTF-8\npackage.numTargets=1\n"
                + "package.numDependencies=0\ntarget1.type=ClassTarget\ntarget1.name=Exhibit\n");
        } catch (Exception e) { throw new IllegalStateException(e); }
        Thread verifier = new Thread(() -> {
            try {
                for (int i = 0; i < 300 && editor == null; i++) {
                    try { editor = fx(BootSmoke::findEditor); } catch (IllegalStateException starting) { }
                    if (editor == null) Thread.sleep(100);
                }
                if (editor == null) throw new AssertionError("Project did not open");
                loader = editor.getClass().getClassLoader();
                Object javaEditor = fx(() -> {
                    pkg = call(editor, "getPackage");
                    Object target = pkg.getClass().getMethod("getTarget", String.class).invoke(pkg, "Exhibit");
                    target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                    return call(target, "getEditor");
                });
                verify(javaEditor, folder);
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(2); }
        }, "Static toString snippet verification");
        verifier.setDaemon(true);
        verifier.start();
        Boot.main(new String[]{"-bluej.userHome=" + args[1], folder.toString()});
    }

    private static void verify(Object javaEditor, Path folder) throws Exception {
        Node pane = fx(() -> (Node)call(javaEditor, "getSourcePane"));
        Object document = fx(() -> call(pane, "getDocument"));
        Object syntax = fx(() -> call(javaEditor, "getSourceDocument"));
        int position = ORIGINAL.lastIndexOf("\n}");
        EditorCompletionSmoke.parsed(syntax);
        fx(() -> { pane.getClass().getMethod("positionCaret", int.class).invoke(pane, position); return null; });
        AdvancedSmoke.editorCommand(javaEditor, "light.snippets.browse");
        DialogPane browser = AdvancedSmoke.dialog("light-snippet-browser");
        fx(() -> {
            ((TextField)browser.lookup("#snippet-search")).setText("tostr");
            ListView<?> list = (ListView<?>)browser.lookup("#snippet-list");
            if (list.getItems().size() != 1 || !"tostr".equals(call(list.getItems().getFirst(), "getName")))
                throw new AssertionError("Palette did not list tostr for Exhibit");
            var insert = browser.getButtonTypes().stream().filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            Button control = (Button)browser.lookupButton(insert);
            if (control.isDisabled()) throw new AssertionError("Insertion disabled inside Exhibit");
            control.fire();
            return null;
        });
        String expanded = waitForExpansion(document);
        fx(() -> {
            check("\"\"".equals(call(pane, "getSelectedText")), "The empty string placeholder was not selected");
            call(javaEditor, "save");
            snapshot((Node)javaEditor, "static-tostr.png");
            return null;
        });
        compileAndCall(folder, "Exhibit{}");

        fx(() -> { shortcut(pane, KeyCode.Z); return null; });
        check(ORIGINAL.equals(fx(() -> call(document, "getFullContent"))), "Ctrl+Z did not remove the whole snippet");
        fx(() -> { shortcut(pane, KeyCode.Y); return null; });
        check(expanded.equals(fx(() -> call(document, "getFullContent"))), "Ctrl+Y did not restore the snippet");

        fx(() -> {
            shortcut(pane, KeyCode.Z);
            check(ORIGINAL.equals(call(document, "getFullContent")), "Ctrl+Z did not restore the original source");
            document.getClass().getMethod("replaceText", int.class, int.class, String.class).invoke(document, position, position, "tostr");
            pane.getClass().getMethod("positionCaret", int.class).invoke(pane, position + "tostr".length());
            return null;
        });
        EditorCompletionSmoke.parsed(syntax);
        fx(() -> { pane.requestFocus(); shortcut(pane, KeyCode.SPACE); return null; });
        boolean selected = false;
        for (int i = 0; i < 150; i++) {
            if (fx(() -> ((String)call(document, "getFullContent")).contains(DEFAULT_RETURN))) {
                selected = true; // The IDE auto-accepts an unambiguous completion.
                break;
            }
            Object popup = fx(() -> field(javaEditor, "activeCompletion"));
            if (popup != null && fx(() -> (boolean)call(popup, "isShowing"))) {
                fx(() -> {
                    List<?> choices = (List<?>)field(popup, "choices");
                    for (int n = 0; n < choices.size(); n++) {
                        Object choice = choices.get(n);
                        if ("tostr".equals(choice.getClass().getField("choice").get(choice))) {
                            popup.getClass().getMethod("setHighlighted", int.class, boolean.class).invoke(popup, n, true);
                            return null;
                        }
                    }
                    throw new AssertionError("Ctrl+Space did not offer tostr");
                });
                EditorCompletionSmoke.key(popup, KeyCode.ENTER);
                selected = true;
                break;
            }
            Thread.sleep(50);
        }
        check(selected && expanded.equals(waitForExpansion(document)), "Ctrl+Space and palette expansions differ");
        fx(() -> {
            check("\"\"".equals(call(pane, "getSelectedText")), "Ctrl+Space did not select the placeholder");
            pane.getClass().getMethod("replaceSelection", String.class).invoke(pane, "\"name=\" + name");
            call(javaEditor, "save");
            return null;
        });
        compileAndCall(folder, "Exhibit{name=Lab}");
        System.out.println("TOSTR_STATIC_OK palette=true ctrlSpace=true selectedPlaceholder=true undoRedo=true");
    }

    private static void shortcut(Node pane, KeyCode key) {
        Event.fireEvent(pane, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, true, false, false));
    }

    private static String waitForExpansion(Object document) throws Exception {
        String current = "";
        for (int i = 0; i < 120; i++) {
            current = fx(() -> (String)call(document, "getFullContent"));
            if (current.contains(DEFAULT_RETURN)) return current;
            Thread.sleep(50);
        }
        throw new AssertionError("Static snippet was not inserted: " + current);
    }

    private static void compileAndCall(Path folder, String expected) throws Exception {
        Path classes = Files.createTempDirectory(folder.getParent(), "compiled-");
        int code = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", classes.toString(),
            folder.resolve("Exhibit.java").toString());
        check(code == 0, "Expanded snippet failed to compile");
        try (var isolated = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            String value = isolated.loadClass("Exhibit").getConstructor().newInstance().toString();
            check(value.equals(expected), "Expected " + expected + ", got " + value);
            System.out.println("TOSTR_COMPILED output=" + value);
        }
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
