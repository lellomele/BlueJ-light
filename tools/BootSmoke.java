/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
import bluej.Boot;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.event.Event;
import javafx.stage.Window;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public class BootSmoke {
    static Object editor;
    static ClassLoader loader;
    static Object pkg;

    public static void main(String[] args) {
        startVerification();
        Boot.main(args[1].equals("@portable") ? new String[]{args[0]}
            : new String[]{"-bluej.userHome=" + args[1], args[0]});
    }

    static void startVerification() {
        Thread verify = new Thread(() -> {
            try {
                for (int i = 0; i < 300 && editor == null; i++) {
                    try { editor = fx(BootSmoke::findEditor); } catch (IllegalStateException ex) { }
                    if (editor == null) Thread.sleep(100);
                }
                if (editor == null) throw new AssertionError("No package editor opened");
                loader = editor.getClass().getClassLoader();
                pkg = call(editor, "getPackage");
                Thread.sleep(2000);
                verifySnippetHelp();
                fx(() -> {
                    System.out.println("PACKAGE_PATH " + call(pkg, "getPath"));
                    System.out.println("CLASS_TARGETS " + call(pkg, "getClassTargets"));
                    for (Object target : (java.util.Collection<?>)call(pkg,"getVertices"))
                        if (target.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget"))
                            System.out.println("BEFORE " + target + " state=" + call(target,"getState") + " queued=" + call(target,"isQueued") + " source=" + call(target,"getSourceType"));
                    return null;
                });
                Class<?> reason = loader.loadClass("bluej.compiler.CompileReason");
                Class<?> type = loader.loadClass("bluej.compiler.CompileType");
                Object user = Enum.valueOf((Class)reason, "USER");
                Object explicit = Enum.valueOf((Class)type, "EXPLICIT_USER_COMPILE");
                Class<?> observerType = loader.loadClass("bluej.compiler.FXCompileObserver");
                Object observer = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{observerType},
                    (proxy, method, values) -> {
                        System.out.println("COMPILE_EVENT " + method.getName() + " " + java.util.Arrays.toString(values));
                        if (method.getName().equals("compilerMessage"))
                            System.out.println(values[0].getClass().getMethod("getMessage").invoke(values[0]));
                        return method.getReturnType() == boolean.class ? false : null;
                    });
                for (int i = 0; i < 600; i++) {
                    boolean queued = fx(() -> {
                        for (Object target : (java.util.Collection<?>)call(pkg, "getVertices"))
                            if (target.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")
                                && (Boolean)call(target, "isQueued")) return true;
                        return false;
                    });
                    if (!queued) break;
                    if (i == 599) {
                        Thread.getAllStackTraces().forEach((thread, trace) -> {
                            System.out.println("THREAD " + thread.getName());
                            for (StackTraceElement element : trace) System.out.println("    " + element);
                        });
                        throw new AssertionError("Initial compilation queue did not become idle");
                    }
                    Thread.sleep(100);
                }
                fx(() -> { pkg.getClass().getMethod("compile", observerType, reason, type).invoke(pkg, observer, user, explicit); return null; });
                List<?> vertices = fx(() -> new ArrayList<>((java.util.Collection<?>)call(pkg, "getVertices")));
                for (int i = 0; i < 200; i++) {
                    boolean compiled = fx(() -> {
                        for (Object target : vertices)
                            if (target.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")
                                && !(Boolean)call(target, "isCompiled")) return false;
                        return true;
                    });
                    if (compiled) break;
                    if (i == 199) {
                        fx(() -> { for (Object target : vertices) if (target.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")) System.out.println("TARGET_STATE " + target + " " + call(target,"getState")); return null; });
                        throw new AssertionError("Compilation did not finish");
                    }
                    Thread.sleep(100);
                }
                fx(() -> {
                    MenuItem arrange = findArrangeMenu();
                    if (arrange == null || !arrange.isVisible()) throw new AssertionError("Layout menu missing");
                    arrange.fire();
                    return null;
                });
                for (int i = 0; i < 200 && fx(() -> (Boolean)call(editor, "isArranging")); i++) Thread.sleep(100);
                if (fx(() -> (Boolean)call(editor, "isArranging"))) throw new AssertionError("Layout did not finish");
                if (!fx(() -> (Boolean)call(editor, "canUndoArrangement"))) throw new AssertionError("No layout undo available");
                fx(() -> { assertNoOverlap(vertices); snapshot((Node)editor, "diagram.png"); return null; });
                fx(() -> { call(editor, "undoArrangement"); call(editor, "redoArrangement"); assertNoOverlap(vertices); return null; });
                fx(() -> {
                    Node root = ((Node)editor).getScene().getRoot();
                    Button button = (Button)root.lookup("#light-arrange-diagram");
                    if (button == null || !button.isVisible() || button.isDisabled()
                            || button.getBoundsInParent().getHeight() < 10)
                        throw new AssertionError("Visible layout button missing");
                    call(editor, "undoArrangement");
                    button.fire();
                    return null;
                });
                for (int i = 0; i < 200 && fx(() -> (Boolean)call(editor, "isArranging")); i++) Thread.sleep(100);
                fx(() -> { assertNoOverlap(vertices); return null; });
                Object target = vertices.stream().filter(v -> v.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")
                    && v.toString().equals("School")).findFirst().orElseThrow();
                Object javaEditor = fx(() -> {
                    target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                    return call(target, "getEditor");
                });
                Thread.sleep(1200);
                verifySnippetBrowser(javaEditor);
                fx(() -> {
                    if (!(javaEditor instanceof Node node) || node.getScene() == null || !node.getScene().getWindow().isShowing())
                        throw new AssertionError("Double click did not show Java editor");
                    Object pane = call(javaEditor, "getSourcePane");
                    Object document = call(pane, "getDocument");
                    String original = (String)call(document, "getFullContent");
                    if (!original.contains("class ")) throw new AssertionError("Class source not loaded");
                    String marker = "// BlueJ light packaged-editor verification\n";
                    Method replace = document.getClass().getMethod("replaceText", int.class, int.class, String.class);
                    replace.invoke(document, 0, 0, marker);
                    call(javaEditor, "save");
                    File sourceFile = (File)call(target, "getJavaSourceFile");
                    if (!java.nio.file.Files.readString(sourceFile.toPath()).startsWith(marker))
                        throw new AssertionError("Java edit not saved");
                    replace.invoke(document, 0, marker.length(), "");
                    call(javaEditor, "save");
                    snapshot(node, "packaged-editor.png");
                    return null;
                });
                Object frame = fx(() -> {
                    Object frames = loader.loadClass("bluej.pkgmgr.PkgMgrFrame").getMethod("getAllFrames").invoke(null);
                    return java.lang.reflect.Array.get(frames, 0);
                });
                for (int i = 0; i < 200 && fx(() -> (Boolean)call(target, "isQueued")); i++) Thread.sleep(100);
                Thread.sleep(1000);
                fx(() -> { javaEditor.getClass().getMethod("scheduleCompilation", reason, type).invoke(javaEditor, user, explicit); return null; });
                for (int i = 0; !fx(() -> (Boolean)call(target, "isCompiled")); i++) {
                    if (i == 199) throw new AssertionError("Compilation after editor save did not finish");
                    Thread.sleep(100);
                }
                fx(() -> { call(frame, "doSave"); return null; });
                fx(() -> { snapshot((Node)editor, "diagram.png"); return null; });
                AdvancedSmoke.verify(javaEditor, frame, vertices);
                System.out.println("SMOKE_OK version=" + Boot.BLUEJ_VERSION + " compile=true layoutMenu=true layoutButton=true undo=true redo=true editorDoubleClick=true editorEdit=true editorSave=true editorCompile=true save=true snippetHelp=true snippetSearch=true snippetInsert=true noTeam=true");
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            } catch (Throwable ex) { ex.printStackTrace(); System.exit(2); }
        }, "Light verification");
        verify.setDaemon(true);
        verify.start();
    }

    static MenuItem findArrangeMenu() {
        return findMenuItem(((Node)editor).getScene().getRoot(), "light-arrange-diagram-menu");
    }

    static MenuItem findMenuItem(Node root, String id) {
        for (Node node : root.lookupAll(".menu-bar"))
            if (node instanceof MenuBar bar)
                for (var menu : bar.getMenus())
                    for (MenuItem item : menu.getItems())
                        if (id.equals(item.getId())) return item;
        return null;
    }

    static DialogPane waitForBrowser() throws Exception {
        for (int i = 0; i < 100; i++) {
            DialogPane pane = fx(() -> {
                for (Window window : Window.getWindows())
                    if (window.isShowing() && window.getScene() != null) {
                        Node node = window.getScene().getRoot().lookup("#light-snippet-browser");
                        if (node instanceof DialogPane dialog) return dialog;
                    }
                return null;
            });
            if (pane != null) return pane;
            Thread.sleep(100);
        }
        throw new AssertionError("Snippet browser did not open");
    }

    static void verifySnippetHelp() throws Exception {
        fx(() -> {
            Node root = ((Node)editor).getScene().getRoot();
            if (!root.lookupAll(".pmf-tools-team").isEmpty()) throw new AssertionError("Team panel remains");
            for (Node node : root.lookupAll(".menu-bar")) if (node instanceof MenuBar bar)
                for (var menu : bar.getMenus()) for (MenuItem item : menu.getItems())
                    if (item.getText() != null && item.getText().toLowerCase().contains("team"))
                        throw new AssertionError("Team menu remains");
            MenuItem help = findMenuItem(root, "light-snippets-help");
            if (help == null) throw new AssertionError("Snippet help missing");
            Platform.runLater(help::fire);
            return null;
        });
        DialogPane browser = waitForBrowser();
        fx(() -> {
            ListView<?> list = (ListView<?>)browser.lookup("#snippet-list");
            if (list.getItems().size() != 17) throw new AssertionError("Built-in snippet list is incomplete: " + list.getItems().size());
            TextField query = (TextField)browser.lookup("#snippet-search");
            query.setText("for");
            if (list.getItems().size() != 2) throw new AssertionError("Snippet filtering failed");
            query.setText("fori");
            query.setText("");
            return null;
        });
        Thread.sleep(300);
        fx(() -> {
            snapshot(browser, "snippet-help.png");
            ((Button)browser.lookupButton(ButtonType.CLOSE)).fire();
            return null;
        });
    }

    static void verifySnippetBrowser(Object javaEditor) throws Exception {
        Object sourcePane = fx(() -> call(javaEditor, "getSourcePane"));
        Object document = fx(() -> call(sourcePane, "getDocument"));
        String original = fx(() -> (String)call(document, "getFullContent"));
        int position = original.indexOf("return 42;");
        if (position < 0) throw new AssertionError("Expected School source");
        fx(() -> {
            sourcePane.getClass().getMethod("positionCaret", int.class).invoke(sourcePane, position);
            MenuItem browse = findMenuItem(((Node)javaEditor).getScene().getRoot(), "light-browse-snippets");
            if (browse == null || browse.getAccelerator() == null) throw new AssertionError("Snippet shortcut missing");
            Platform.runLater(browse::fire);
            return null;
        });
        DialogPane browser = waitForBrowser();
        fx(() -> {
            ((TextField)browser.lookup("#snippet-search")).setText("fori");
            ListView<?> list = (ListView<?>)browser.lookup("#snippet-list");
            if (list.getItems().size() != 1) throw new AssertionError("Snippet search failed");
            return null;
        });
        Thread.sleep(300);
        fx(() -> {
            snapshot(browser, "snippet-browser.png");
            Event.fireEvent(browser, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
        Thread.sleep(150);
        fx(() -> {
            if (!((String)call(document, "getFullContent")).contains("for (int i = 0; i < limit; i++)"))
                throw new AssertionError("Palette did not insert snippet");
            Object actions = call(javaEditor, "getActions");
            Object undo = actions.getClass().getMethod("getActionByName", String.class).invoke(actions, "undo");
            loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed", boolean.class).invoke(undo, false);
            if (!original.equals(call(document, "getFullContent"))) throw new AssertionError("Snippet insertion is not a single undo");
            Method replace = document.getClass().getMethod("replaceText", int.class, int.class, String.class);
            replace.invoke(document, position, position, "for");
            sourcePane.getClass().getMethod("positionCaret", int.class).invoke(sourcePane, position + 3);
            Method assist = javaEditor.getClass().getDeclaredMethod("createContentAssist");
            assist.setAccessible(true);
            assist.invoke(javaEditor);
            return null;
        });
        Thread.sleep(1200);
        fx(() -> {
            Node popup = null;
            for (Window window : Window.getWindows()) if (window.isShowing() && window.getScene() != null) {
                Node candidate = window.getScene().getRoot().lookup(".suggestion-top-level");
                if (candidate != null) { popup = candidate; break; }
            }
            if (popup == null) throw new AssertionError("Completion popup missing");
            snapshot(popup, "completion-popup.png");
            Event.fireEvent(popup, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, true, true, false, false));
            return null;
        });
        DialogPane fromPopup = waitForBrowser();
        fx(() -> {
            if (((ListView<?>)fromPopup.lookup("#snippet-list")).getItems().size() != 17)
                throw new AssertionError("Palette shortcut from completion failed");
            ((Button)fromPopup.lookupButton(ButtonType.CLOSE)).fire();
            document.getClass().getMethod("replaceText", int.class, int.class, String.class).invoke(document,
                0, ((Number)call(document,"getLength")).intValue(), original);
            call(javaEditor, "save");
            return null;
        });
    }

    static Object call(Object target, String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    static int number(Object target, String name) throws Exception { return ((Number)call(target, name)).intValue(); }
    static Object findEditor() {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window.getScene() == null) continue;
            Node result = find(window.getScene().getRoot());
            if (result != null) return result;
        }
        return null;
    }
    static Node find(Node node) {
        if (node.getClass().getName().equals("bluej.pkgmgr.PackageEditor")) return node;
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            Node result = find(child); if (result != null) return result;
        }
        return null;
    }
    static <T> T fx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(work.call()); } catch (Throwable ex) { result.completeExceptionally(ex); } });
        return result.get(10, TimeUnit.SECONDS);
    }
    static void assertNoOverlap(List<?> vertices) throws Exception {
        for (int i = 0; i < vertices.size(); i++) for (int j = i + 1; j < vertices.size(); j++) {
            Object a = vertices.get(i), b = vertices.get(j);
            int ax = number(a,"getX"), ay = number(a,"getY"), bx = number(b,"getX"), by = number(b,"getY");
            if (ax < bx + number(b,"getWidth") && ax + number(a,"getWidth") > bx
                && ay < by + number(b,"getHeight") && ay + number(a,"getHeight") > by)
                throw new AssertionError("Overlapping targets: " + a + ", " + b);
        }
    }
    static void snapshot(Node node, String name) throws Exception {
        File folder = new File("bench/smoke-output"); folder.mkdirs();
        ImageIO.write(SwingFXUtils.fromFXImage(node.getScene().snapshot(null), null), "png", new File(folder,name));
    }
}
