/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Exercises the editor controls against an isolated project and the real BlueJ debugger VM. */
public class EditorRunSmoke extends BootSmoke {
    private static final String NO_MAIN = """
        public class Runner {
            public void main(int number) {}
            static class Nested { public static void main(String[] args) {} }
        }
        """;

    public static void main(String[] args) {
        try { startProject(args); }
        catch (Exception failure) { throw new IllegalStateException("Cannot start editor Run test", failure); }
    }

    private static void startProject(String[] args) throws Exception {
        Path folder = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        Path source = folder.resolve("Runner.java");
        Path calls = folder.resolve("calls.txt");
        Files.writeString(source, NO_MAIN);
        Files.writeString(folder.resolve("Helper.java"), "public class Helper { public int getValue() { return 7; } }");
        Files.writeString(folder.resolve("package.bluej"), """
            project.charset=UTF-8
            package.numTargets=2
            package.numDependencies=0
            target1.type=ClassTarget
            target1.name=Runner
            target1.x=60
            target1.y=60
            target1.width=90
            target1.height=50
            target2.type=ClassTarget
            target2.name=Helper
            target2.x=220
            target2.y=60
            target2.width=90
            target2.height=50
            """);
        Thread worker = new Thread(() -> {
            try {
                verify(source, calls, args[2]);
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(2);
            }
        }, "Editor Run verification");
        worker.setDaemon(true);
        worker.start();
        Boot.main(new String[]{"-bluej.userHome=" + args[1], folder.toString()});
    }

    private static void verify(Path source, Path calls, String language) throws Exception {
        waitUntil("project opens", () -> {
            try { editor = fx(BootSmoke::findEditor); }
            catch (IllegalStateException starting) { return false; }
            return editor != null;
        });
        loader = editor.getClass().getClassLoader();
        pkg = fx(() -> call(editor, "getPackage"));
        Object target = fx(() -> pkg.getClass().getMethod("getTarget", String.class).invoke(pkg, "Runner"));
        Node javaEditor = fx(() -> {
            target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
            return (Node)call(target, "getEditor");
        });
        Object document = fx(() -> call(call(javaEditor, "getSourcePane"), "getDocument"));
        Object syntax = fx(() -> call(javaEditor, "getSourceDocument"));
        EditorCompletionSmoke.parsed(syntax);
        SplitMenuButton run = fx(() -> (SplitMenuButton)javaEditor.lookup("#editor-run"));
        check(run != null, "Editor did not contain #editor-run");
        waitUntil("button disabled for nested main and overload", () -> fx(() -> run.isVisible() && run.isManaged() && run.isDisabled()));
        System.out.println("EDITOR_RUN_NO_MAIN visible=true disabled=true");

        String working = """
            import java.nio.file.*;
            public class Runner {
                public static void main(String[] args) throws Exception {
                    Files.writeString(Path.of("%s"), "old:" + String.join("|", args) + "\\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
            """.formatted(calls.toString().replace('\\', '/'));
        fx(() -> { replace(document, working); return null; });
        waitUntil("button appears for new main", () -> fx(() -> run.isVisible() && run.isManaged() && !run.isDisabled()));
        boolean stableLayout = verifyTabs(javaEditor);
        waitUntil("debugger ready", () -> fx(() -> (boolean)call(pkg, "isDebuggerIdle")));
        String edited = working.replace("\"old:", "\"edited:");
        fx(() -> {
            check(run.getText().equals(language.equals("italian") ? "Esegui" : "Run"), "Untranslated button: " + run.getText());
            Stage stage = (Stage)run.getScene().getWindow();
            stage.toFront();
            stage.requestFocus();
            snapshot(javaEditor, "editor-run.png");
            return null;
        });
        editAndRun(document, edited, source, run, true);
        waitUntil("edited main called with an empty array", () -> entries(calls).equals(List.of("edited:")));
        check(Files.readString(source).equals(edited), "Direct Run did not save the edited revision");
        Path bytecode = source.resolveSibling("Runner.class");
        check(Files.exists(bytecode), "No previous class file exists for the regression test");
        var compiledAt = Files.getLastModifiedTime(bytecode);
        System.out.println("EDITOR_RUN_DIRECT nativeMouse=true savedLatest=true argsLength=0 calls=" + entries(calls));

        Object debugger = fx(() -> call(call(pkg, "getProject"), "getDebugger"));
        waitUntil("debugger idle after direct call", () -> fx(() -> number(debugger, "getStatus") == 2 && !run.isDisabled()));
        fx(() -> {
            Stage stage = (Stage)run.getScene().getWindow();
            stage.toFront();
            stage.requestFocus();
            mouseClick(run.lookup(".arrow-button"));
            return null;
        });
        waitUntil("arguments menu opens", () -> fx(run::isShowing));
        fx(() -> {
            MenuItem item = run.getItems().stream().filter(i -> "editor-run-with-arguments".equals(i.getId())).findFirst().orElseThrow();
            String text = item.getText().toLowerCase(java.util.Locale.ROOT);
            check(text.contains(language.equals("italian") ? "argoment" : "argument"), "Untranslated menu: " + text);
            Node menuNode = item.getStyleableNode();
            check(menuNode != null && menuNode.localToScreen(menuNode.getBoundsInLocal()) != null, "Menu item not on screen");
            mouseClick(menuNode);
            return null;
        });
        waitUntil("argument dialog ready", () -> fx(() -> argumentDialog() != null));
        fx(() -> {
            DialogPane dialog = argumentDialog();
            ComboBox<?> input = (ComboBox<?>)dialog.lookup(".combo-box");
            input.getEditor().setText("{\"one\", \"two words\"}");
            snapshot(dialog, "editor-run-arguments.png");
            fireDialogButton(dialog, ButtonType.OK);
            return null;
        });
        List<String> previous = List.of("edited:", "edited:one|two words");
        waitUntil("argument values received by main", () -> entries(calls).equals(previous));
        waitUntil("debugger idle after argument call", () -> fx(() -> number(debugger, "getStatus") == 2 && !run.isDisabled()));
        System.out.println("EDITOR_RUN_ARGUMENTS nativeMenu=true calls=" + previous);
        var afterUnchangedRun = Files.getLastModifiedTime(bytecode);
        System.out.println("EDITOR_RUN_BYTECODE unchanged=" + compiledAt.equals(afterUnchangedRun) + " first=" + compiledAt + " second=" + afterUnchangedRun);
        verifyCancelledDialogs(document, edited, run, calls, previous);

        String broken = edited.replace("Files.writeString(", "missingMethod(); Files.writeString(");
        editAndRun(document, broken, source, run, false);
        waitUntil("javac reports error in new revision", () -> Files.readString(source).equals(broken)
            && fx(() -> call(target, "getState").toString().equals("HAS_ERROR") && !(boolean)call(target, "isQueued")));
        Thread.sleep(2000);
        check(entries(calls).equals(previous), "Broken source reran the old bytecode: " + entries(calls));
        System.out.println("EDITOR_RUN_ERROR previousClass=true diagnostics=HAS_ERROR calls=" + entries(calls));

        String fixed = edited.replace("\"edited:", "\"fixed:");
        editAndRun(document, fixed, source, run, false);
        waitUntil("corrected revision executed once", () -> entries(calls).equals(List.of("edited:", "edited:one|two words", "fixed:")));
        waitUntil("corrected launch finished", () -> fx(() -> number(debugger, "getStatus") == 2 && !run.isDisabled()));
        String external = fixed.replace("\"fixed:", "\"external:");
        List<String> beforeExternal = entries(calls);
        fx(() -> {
            check(!(boolean)call(javaEditor, "isModified"), "Editor was not clean before external edit");
            Files.writeString(source, external);
            run.fire();
            return null;
        });
        waitUntil("external modification loaded", () -> fx(() -> external.equals(call(document, "getFullContent")) && !run.isDisabled()));
        check(beforeExternal.equals(entries(calls)), "Old bytecode executed after an external edit");
        fx(() -> { run.fire(); return null; });
        waitUntil("reloaded main executed", () -> entries(calls).equals(List.of("edited:", "edited:one|two words", "fixed:", "external:")));
        waitUntil("external launch finished", () -> fx(() -> number(debugger, "getStatus") == 2 && !run.isDisabled()));
        System.out.println("EDITOR_RUN_EXTERNAL firstReloadOnly=true secondExecuted=true");
        fx(() -> {
            Object undoManager = field(javaEditor, "undoManager");
            var forget = undoManager.getClass().getMethod("forgetHistory");
            forget.setAccessible(true);
            forget.invoke(undoManager);
            replace(document, NO_MAIN);
            return null;
        });
        waitUntil("button disabled after removing main", () -> fx(() -> run.isVisible() && run.isManaged() && run.isDisabled()));
        fx(() -> {
            Object actions = call(javaEditor, "getActions");
            Object undo = actions.getClass().getMethod("getActionByName", String.class).invoke(actions, "undo");
            loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed", boolean.class).invoke(undo, false);
            return null;
        });
        waitUntil("undo restores main and enables its button", () -> fx(() -> external.equals(call(document, "getFullContent")) && run.isVisible() && run.isManaged() && !run.isDisabled()));
        verifyDocumentation(javaEditor, run);
        check(stableLayout, "Toolbar positions differ across editor tabs; see EDITOR_RUN_TAB");
        check(compiledAt.equals(afterUnchangedRun), "Running the unmodified program rewrote Runner.class");
        System.out.println("EDITOR_RUN_OK language=" + language + " corrected=true disabledAfterRemoval=true undo=true calls=" + entries(calls));
    }

    private static void editAndRun(Object document, String code, Path source, SplitMenuButton run, boolean mouse) throws Exception {
        fx(() -> {
            replace(document, code);
            check(!Files.readString(source).equals(code), "Fresh revision already on disk immediately after edit");
            return null;
        });
        waitUntil("Run available for edited main", () -> fx(() -> {
            if (run.isDisabled()) return false;
            System.out.println("EDITOR_RUN_ACTIVATE unsavedAtClick=" + !Files.readString(source).equals(code));
            if (mouse) mouseClick(run.lookup(".label")); else run.fire();
            return true;
        }));
    }

    private static void verifyCancelledDialogs(Object document, String original, SplitMenuButton run, Path calls, List<String> previous) throws Exception {
        for (boolean changeRevision : List.of(false, true)) {
            fx(() -> {
                check(!run.isDisabled(), "Arguments command still unavailable");
                run.getItems().stream().filter(i -> "editor-run-with-arguments".equals(i.getId())).findFirst().orElseThrow().fire();
                return null;
            });
            waitUntil("arguments dialog active", () -> fx(() -> argumentDialog() != null && run.isDisabled()));
            fx(() -> {
                DialogPane dialog = argumentDialog();
                if (changeRevision) replace(document, original + "// edit while arguments are open\n");
                ButtonType button = changeRevision ? ButtonType.OK : ButtonType.CANCEL;
                fireDialogButton(dialog, button);
                return null;
            });
            waitUntil("arguments dialog dismissed and Run ready", () -> fx(() -> argumentDialog() == null && !run.isDisabled()));
            check(entries(calls).equals(previous), "Cancelled or outdated dialog invoked main");
        }
        System.out.println("EDITOR_RUN_CANCEL explicit=true changedSource=true extraCalls=0");
    }

    private static void verifyDocumentation(Node javaEditor, SplitMenuButton run) throws Exception {
        Path html = Path.of((String)fx(() -> field(javaEditor, "docFilename")));
        Files.createDirectories(html.getParent());
        Files.writeString(html, "<html><body>Runner documentation</body></html>");
        Files.setLastModifiedTime(html, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 60000));
        var show = javaEditor.getClass().getMethod("showInterface", boolean.class);
        fx(() -> { show.invoke(javaEditor, true); return null; });
        waitUntil("documentation disables Run", () -> fx(() -> run.isVisible() && run.isManaged() && run.isDisabled()
            && field(javaEditor, "htmlPane") instanceof Node htmlPane && htmlPane.isVisible()));
        fx(() -> { show.invoke(javaEditor, false); return null; });
        waitUntil("source enables Run", () -> fx(() -> run.isVisible() && run.isManaged() && !run.isDisabled()));
        System.out.println("EDITOR_RUN_DOCUMENTATION disabled=true restoredOnSource=true");
    }

    private static boolean verifyTabs(Node runner) throws Exception {
        Node helper = fx(() -> {
            Object target = pkg.getClass().getMethod("getTarget", String.class).invoke(pkg, "Helper");
            target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
            return (Node)call(target, "getEditor");
        });
        TabPane tabs = fx(() -> (TabPane)helper.getScene().getRoot().lookup(".tab-pane"));
        Tab runnerTab = fx(() -> tabs.getTabs().stream().filter(tab -> tab.getContent() == runner).findFirst().orElseThrow());
        Tab helperTab = fx(() -> tabs.getTabs().stream().filter(tab -> tab.getContent() == helper).findFirst().orElseThrow());
        check(fx(() -> tabs.getSelectionModel().getSelectedItem() == helperTab), "Opening Helper did not activate its tab");
        Bounds[] fixed = new Bounds[2];
        boolean stable = true;
        for (Tab tab : List.of(runnerTab, helperTab, runnerTab)) {
            boolean expected = tab == runnerTab;
            fx(() -> { tabs.getSelectionModel().select(tab); return null; });
            // Wait for the new tab's content to receive its first layout pulse.
            Thread.sleep(300);
            fx(() -> {
                var root = tabs.getScene().getRoot();
                root.applyCss();
                root.layout();
                Node current = tabs.getSelectionModel().getSelectedItem().getContent();
                Node b = current.lookup("#editor-run");
                System.out.println("EDITOR_RUN_TAB selected=" + (current == tab.getContent()) + " expectEnabled=" + expected
                    + " visible=" + b.isVisible() + " managed=" + b.isManaged() + " disabled=" + b.isDisabled()
                    + " rendered=" + root.lookupAll("#editor-run").stream().filter(EditorRunSmoke::visibleInTree).count()
                    + " runBounds=" + b.localToScreen(b.getBoundsInLocal())
                    + " undoBounds=" + current.lookup(".toolbar-undo-button").localToScreen(current.lookup(".toolbar-undo-button").getBoundsInLocal()));
                return null;
            });
            waitUntil("active tab toolbar visible=" + expected, () -> fx(() -> {
                Window window = tabs.getScene().getWindow();
                long rendered = tabs.getScene().getRoot().lookupAll("#editor-run").stream().filter(EditorRunSmoke::visibleInTree).count();
                Node current = tabs.getSelectionModel().getSelectedItem().getContent();
                Node button = current.lookup("#editor-run");
                if (current != tab.getContent() || !window.isShowing() || button == null || !button.isManaged()
                    || !button.isVisible() || button.isDisabled() == expected || rendered != 1) return false;
                return true;
            }));
            Bounds[] bounds = fx(() -> {
                Node current = tab.getContent();
                Node button = current.lookup("#editor-run");
                Node undo = current.lookup(".toolbar-undo-button");
                return new Bounds[] {button.localToScreen(button.getLayoutBounds()), undo.localToScreen(undo.getLayoutBounds())};
            });
            for (int i = 0; i < bounds.length; i++) {
                if (fixed[i] == null) fixed[i] = bounds[i];
                if (Math.abs(fixed[i].getMinX() - bounds[i].getMinX()) > 1 || Math.abs(fixed[i].getMinY() - bounds[i].getMinY()) > 1) stable = false;
            }
            if (!expected) fx(() -> { snapshot(helper, "editor-run-other-tab.png"); return null; });
        }
        System.out.println("EDITOR_RUN_TABS sameWindow=true selected=Runner/Helper/Runner renderedButtons=1/1/1 enabled=true/false/true runX="
            + fixed[0].getMinX() + " undoX=" + fixed[1].getMinX() + " stable=" + stable);
        Node readme = fx(() -> {
            Object target = call(pkg, "getReadmeTarget");
            target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
            return (Node)call(target, "getEditor");
        });
        waitUntil("README offers a disabled Run control", () -> fx(() -> {
            Node button = readme.lookup("#editor-run");
            return tabs.getSelectionModel().getSelectedItem().getContent() == readme && button != null
                && visibleInTree(button) && button.isManaged() && button.isDisabled();
        }));
        fx(() -> { tabs.getSelectionModel().select(runnerTab); return null; });
        System.out.println("EDITOR_RUN_README visible=true disabled=true");
        return stable;
    }

    private static boolean visibleInTree(Node node) {
        for (Node parent = node; parent != null; parent = parent.getParent()) {
            if (!parent.isVisible()) return false;
        }
        return true;
    }

    private static void mouseClick(Node node) {
        check(node != null, "Control missing from JavaFX skin");
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        check(bounds != null && bounds.getWidth() > 0 && bounds.getHeight() > 0, "Control is off screen");
        Robot mouse = new Robot();
        mouse.mouseMove(bounds.getCenterX(), bounds.getCenterY());
        mouse.mouseClick(MouseButton.PRIMARY);
    }

    private static DialogPane argumentDialog() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane pane
                && pane.lookup(".combo-box") instanceof ComboBox<?> box && box.isEditable()
                && pane.getButtonTypes().contains(ButtonType.OK)) return pane;
        }
        return null;
    }

    private static void fireDialogButton(DialogPane dialog, ButtonType type) {
        Node node = dialog.lookupButton(type);
        Button control = node instanceof Button button ? button : (Button)node.lookup(".button");
        check(control != null && !control.isDisabled(), "Dialog button unavailable: " + type);
        Platform.runLater(control::fire);
    }

    private static void replace(Object document, String text) throws Exception {
        document.getClass().getMethod("replaceText", int.class, int.class, String.class)
            .invoke(document, 0, number(document, "getLength"), text);
    }

    private static List<String> entries(Path calls) throws Exception {
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    private static void waitUntil(String name, Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (condition.call()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out: " + name);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
