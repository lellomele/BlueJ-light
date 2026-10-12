/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-12. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class SnippetContextSmoke extends AdvancedSmoke
{
    private record Case(String name, String text, int position, boolean insertion) { }

    public static void main(String[] args)
    {
        Thread verify = new Thread(() -> {
            try
            {
                for (int i = 0; i < 300 && editor == null; i++)
                {
                    try { editor = fx(BootSmoke::findEditor); } catch (IllegalStateException ignored) { }
                    if (editor == null) Thread.sleep(100);
                }
                if (editor == null) throw new AssertionError("No project opened");
                loader = editor.getClass().getClassLoader();
                Queue<Throwable> uiFailures = new java.util.concurrent.ConcurrentLinkedQueue<>();
                fx(() -> {
                    Thread thread = Thread.currentThread();
                    var previous = thread.getUncaughtExceptionHandler();
                    thread.setUncaughtExceptionHandler((t, ex) -> {
                        uiFailures.add(ex);
                        if (previous != null) previous.uncaughtException(t, ex);
                    });
                    return null;
                });
                pkg = call(editor, "getPackage");
                Object target = fx(() -> pkg.getClass().getMethod("getTarget", String.class).invoke(pkg, "School"));
                Object javaEditor = fx(() -> {
                    target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                    return call(target, "getEditor");
                });
                Object pane = fx(() -> call(javaEditor, "getSourcePane"));
                Object document = fx(() -> call(pane, "getDocument"));
                String original = fx(() -> (String)call(document, "getFullContent"));
                // The unmodified first-open caret is essential: previous smoke checks moved it into a method.
                browse(javaEditor, false, true);
                String valid = "/* outside */\nimport java.util.List;\n"
                    + "public class School {\n /* field comment */ int field;\n"
                    + " int answer() { /* method comment */ String text = \"hello\"; if (field > 0) { return 42; } return 0; }\n"
                    + " class Nested { int value() { return 1; } }\n}\n// after class\n";
                String compatible = "public class School { int answer() { return 42; } }\n";
                List<Case> cases = List.of(
                    new Case("comment-before-class", valid, 3, false),
                    new Case("unfinished-package", "package /* typing */ ;\npublic class School {}", 0, false),
                    new Case("import", valid, valid.indexOf("java.util"), false),
                    new Case("class-header", compatible, 0, false),
                    new Case("field-comment", valid, valid.indexOf("field comment"), false),
                    new Case("method-comment", valid, valid.indexOf("method comment"), false),
                    new Case("string-literal", valid, valid.indexOf("hello") + 2, false),
                    new Case("method-body", compatible, compatible.indexOf("return"), true),
                    new Case("after-class", valid, valid.indexOf("after class"), false),
                    new Case("end-of-file", compatible, compatible.length(), false),
                    new Case("empty", "", 0, false),
                    new Case("comment-only", "// no class yet\n", 2, false),
                    new Case("unfinished-class", "public class ", 0, false),
                    new Case("missing-return-type", "public class School { answer() { return 42; } }", 0, false),
                    new Case("unclosed-comment", "/* unfinished", 3, false),
                    new Case("whitespace-only", " \t\n\n", 2, false),
                    new Case("unclosed-string", "public class School { String text = \"unfinished", 40, false),
                    new Case("interface-header", "public interface School { default int answer() { return 42; } }", 0, false),
                    new Case("record-header", "public record School(int value) { int answer() { return value; } }", 0, false),
                    new Case("unicode-comment", "// \u03b1 \uD83D\uDE00\npublic class School {}", 3, false),
                    new Case("lambda-method", "public class School { void run() { Runnable task = () -> {}; } }", 34, true),
                    new Case("text-block", "public class School { String text = \"\"\"\nhello\n\"\"\"; }", 41, false));
                int queries = 0;
                for (Case test : cases)
                {
                    queries += fx(() -> {
                        document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                            .invoke(document, 0, number(document, "getLength"), test.text());
                        Object parser = call(javaEditor, "getParsedNode");
                        if (parser != null)
                            for (int position = 0; position <= test.text().length(); position++)
                            {
                                Object scope = parser.getClass().getMethod("getContainingMethodOrClassNode", int.class)
                                    .invoke(parser, position);
                                if (scope != null && call(scope, "getName") == null)
                                    throw new AssertionError("Unnamed enclosing scope at " + test.name() + ":" + position);
                                parser.getClass().getMethod("isCurrentlyInControlStatement", int.class).invoke(parser, position);
                            }
                        pane.getClass().getMethod("positionCaret", int.class).invoke(pane, test.position());
                        return (test.text().length() + 1) * 2;
                    });
                    browse(javaEditor, test.insertion(), true);
                    long request = fx(() -> ((Number)field(javaEditor, "completionRequest")).longValue());
                    fx(() -> {
                        ((Node)pane).requestFocus();
                        Event.fireEvent((Node)pane,
                            new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, true, false, false));
                        return null;
                    });
                    Thread.sleep(250);
                    fx(() -> {
                        if (((Number)field(javaEditor, "completionRequest")).longValue() <= request)
                            throw new AssertionError("Completion shortcut did not run at " + test.name());
                        Object popup = field(javaEditor, "activeCompletion");
                        if (popup != null)
                        {
                            var dismiss = popup.getClass().getDeclaredMethod("dismiss");
                            dismiss.setAccessible(true);
                            dismiss.invoke(popup);
                        }
                        return null;
                    });
                    if (!uiFailures.isEmpty()) throw new AssertionError("UI callback failure", uiFailures.peek());
                    System.out.println("SNIPPET_CONTEXT_CASE " + test.name());
                }
                fx(() -> {
                    document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                        .invoke(document, 0, number(document, "getLength"), compatible);
                    pane.getClass().getMethod("positionCaret", int.class).invoke(pane, compatible.indexOf("return"));
                    return null;
                });
                for (int round = 0; round < 3; round++) browse(javaEditor, true, false);
                insertAndUndo(javaEditor, document, compatible);
                fx(() -> {
                    document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                        .invoke(document, 0, number(document, "getLength"), original);
                    call(javaEditor, "save");
                    return null;
                });
                Path source = ((java.io.File)fx(() -> call(target, "getJavaSourceFile"))).toPath();
                if (!original.equals(Files.readString(source))) throw new AssertionError("Context checks lost original source");
                fx(() -> {
                    javaEditor.getClass().getMethod("setEditorVisible", boolean.class, boolean.class).invoke(javaEditor, true, true);
                    pane.getClass().getMethod("positionCaret", int.class).invoke(pane, 0);
                    return null;
                });
                browse(javaEditor, false, true);
                fx(() -> { call(javaEditor, "close"); return null; });
                Object reopened = fx(() -> {
                    target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                    Object result = call(target, "getEditor");
                    Object sourcePane = call(result, "getSourcePane");
                    sourcePane.getClass().getMethod("positionCaret", int.class).invoke(sourcePane, 0);
                    return result;
                });
                browse(reopened, false, true);
                if (!uiFailures.isEmpty()) throw new AssertionError("UI callback failure", uiFailures.peek());
                System.out.println("SNIPPET_CONTEXT_OK cases=" + cases.size() + " queries=" + queries
                    + " coldOpen=true menu=true keyboard=true completionContexts=true repeated=true insertionUndoRedo=true moved=true reopened=true uiExceptions=0 sourcePreserved=true");
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            }
            catch (Throwable ex)
            {
                ex.printStackTrace();
                Thread.getAllStackTraces().forEach((thread, trace) -> {
                    if (thread.getName().equals("JavaFX Application Thread"))
                        for (StackTraceElement location : trace) System.out.println("FX_AT " + location);
                });
                System.exit(2);
            }
        }, "Snippet context verification");
        verify.setDaemon(true);
        verify.start();
        Boot.main(args[1].equals("@portable") ? new String[] { args[0] }
            : new String[] { "-bluej.userHome=" + args[1], args[0] });
    }

    private static void browse(Object javaEditor, boolean insertion, boolean menu) throws Exception
    {
        fx(() -> {
            if (menu)
            {
                List<Menu> menus = (List<Menu>)call(javaEditor, "getFXMenu");
                MenuItem command = byText(menus, label("light.snippets.browse"));
                if (command == null) throw new AssertionError("Missing snippet menu");
                Platform.runLater(command::fire);
            }
            else
            {
                Node pane = (Node)call(javaEditor, "getSourcePane");
                pane.requestFocus();
                Platform.runLater(() -> Event.fireEvent(pane,
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, true, true, false, false)));
            }
            return null;
        });
        DialogPane browser = dialog("light-snippet-browser");
        fx(() -> {
            ListView<?> list = (ListView<?>)browser.lookup("#snippet-list");
            if (list.getItems().isEmpty()) throw new AssertionError("Empty snippet catalogue");
            ButtonType insertType = browser.getButtonTypes().stream()
                .filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            Button insert = (Button)browser.lookupButton(insertType);
            if (!insertion && !insert.isDisabled()) throw new AssertionError("Insertion allowed outside Java code");
            if (insertion && insert.isDisabled()) throw new AssertionError("Insertion unavailable in a method");
            ((TextField)browser.lookup("#snippet-search")).setText("fori");
            if (list.getItems().isEmpty()) throw new AssertionError("Search stopped responding");
            ((Button)browser.lookupButton(ButtonType.CLOSE)).fire();
            return null;
        });
        fx(() -> null);
    }

    private static void insertAndUndo(Object javaEditor, Object document, String original) throws Exception
    {
        editorCommand(javaEditor, "light.snippets.browse");
        DialogPane browser = dialog("light-snippet-browser");
        fx(() -> {
            ((TextField)browser.lookup("#snippet-search")).setText("fori");
            ButtonType insert = browser.getButtonTypes().stream()
                .filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            ((Button)browser.lookupButton(insert)).fire();
            return null;
        });
        fx(() -> {
            String inserted = (String)call(document, "getFullContent");
            if (inserted.equals(original)) throw new AssertionError("Snippet was not inserted");
            Object actions = call(javaEditor, "getActions");
            var perform = loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction")
                .getMethod("actionPerformed", boolean.class);
            perform.invoke(actions.getClass().getMethod("getActionByName", String.class).invoke(actions, "undo"), false);
            if (!original.equals(call(document, "getFullContent"))) throw new AssertionError("Snippet undo failed");
            perform.invoke(actions.getClass().getMethod("getActionByName", String.class).invoke(actions, "redo"), false);
            if (!inserted.equals(call(document, "getFullContent"))) throw new AssertionError("Snippet redo failed");
            perform.invoke(actions.getClass().getMethod("getActionByName", String.class).invoke(actions, "undo"), false);
            return null;
        });
    }
}
