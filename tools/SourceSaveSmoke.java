/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;

public class SourceSaveSmoke extends BootSmoke {
    public static void main(String[] args) {
        Thread check = new Thread(() -> {
            try {
                for (int i = 0; i < 200 && editor == null; i++) {
                    try { editor = fx(BootSmoke::findEditor); } catch (IllegalStateException ex) { }
                    if (editor == null) Thread.sleep(100);
                }
                if (editor == null) throw new AssertionError("No diagram");
                loader = editor.getClass().getClassLoader();
                pkg = call(editor, "getPackage");
                Object project = call(pkg, "getProject");
                Object resolver = call(project, "getJavadocResolver");
                Path folder = ((java.io.File)call(pkg, "getPath")).toPath();
                var save = loader.loadClass("bluej.light.AtomicSourceSave").getMethod("save",
                    Path.class, String.class, java.nio.charset.Charset.class, String.class);
                for (int i = 0; i < 10; i++) {
                    for (String name : List.of("a1", "b1")) {
                        Path source = folder.resolve(name + ".java");
                        String text = Files.readString(source);
                        fx(() -> {
                            Object doc = resolver.getClass().getMethod("getJavadoc", String.class, String.class).invoke(resolver, null, name);
                            if (doc == null) throw new AssertionError("Fixture documentation not read: " + name);
                            save.invoke(null, source, text, StandardCharsets.UTF_8, "#");
                            return null;
                        });
                    }
                }
                System.out.println("SOURCE_SAVE_OK javadocThenAtomicSave=20 twoDependentClasses=true");
                verifyRecovery(folder, project);
                throw new AssertionError("Application did not exit after recovery");
            } catch (Throwable ex) { ex.printStackTrace(); System.exit(2); }
        }, "Source save verification");
        check.setDaemon(true);
        check.start();
        Boot.main(new String[]{"-bluej.userHome=" + args[1], args[0]});
    }

    static void verifyRecovery(Path folder, Object project) throws Exception {
        Object target = fx(() -> ((List<?>)call(pkg, "getClassTargets")).stream()
            .filter(t -> { try { return call(t, "getIdentifierName").equals("a1"); } catch (Exception ex) { throw new RuntimeException(ex); } })
            .findFirst().orElseThrow());
        Object sourceEditor = fx(() -> { Object value = call(target, "getEditor");
            value.getClass().getMethod("setEditorVisible", boolean.class, boolean.class).invoke(value, true, false); return value; });
        Object pane = fx(() -> call(sourceEditor, "getSourcePane"));
        Object document = fx(() -> call(pane, "getDocument"));
        String original = fx(() -> (String)call(document, "getFullContent"));
        var replace = document.getClass().getMethod("replaceText", int.class, int.class, String.class);
        String unsaved = original + "// emergency recovery\n";
        try (var heldOpen = new java.io.FileInputStream(folder.resolve("a1.java").toFile())) {
            fx(() -> { replace.invoke(document,0,((Number)call(document,"getLength")).intValue(),unsaved); return null; });
            var complete = new java.util.concurrent.CompletableFuture<Boolean>();
            Platform.runLater(() -> { try { complete.complete((Boolean)call(project,"prepareEditorsForClose")); }
                catch (Throwable ex) { complete.completeExceptionally(ex); } });
            DialogPane warning = dialog(null);
            fx(() -> {
                snapshot(warning, "save-recovery-dialog.png");
                ButtonType copy = warning.getButtonTypes().stream().filter(b -> !b.getButtonData().isCancelButton()).findFirst().orElseThrow();
                Platform.runLater(() -> ((Button)warning.lookupButton(copy)).fire()); return null;
            });
            DialogPane saved = dialog(warning);
            fx(() -> { snapshot(saved, "saved-recovery-copy.png"); ((Button)saved.lookupButton(ButtonType.OK)).fire(); return null; });
            if (!complete.get(10,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Project close preflight denied recovery");
            java.lang.reflect.Field f = sourceEditor.getClass().getDeclaredField("recoveryCloseRevision"); f.setAccessible(true);
            if (fx(() -> f.getLong(sourceEditor)) < 0) throw new AssertionError("Copy did not authorize close");
            Object config = loader.loadClass("bluej.Config").getMethod("getUserConfigDir").invoke(null);
            try (var files = Files.list(((java.io.File)config).toPath().resolve("recovered-sources"))) {
                Path copy = files.filter(Files::isRegularFile).findFirst().orElseThrow();
                if (!Files.readString(copy).equals(unsaved)) throw new AssertionError("Recovery copy not exact");
            }
            if (!Files.readString(folder.resolve("a1.java")).equals(original)) throw new AssertionError("Locked original changed");
            fx(() -> { if (!(Boolean)call(pkg,"closeAllEditors")) throw new AssertionError("Recovered editor could not close"); return null; });
            System.out.println("SAVE_CLOSE_RECOVERY_OK permanentWindowsReadHandle=true copyVerified=true originalPreserved=true projectAndEditorClose=true");
            fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            Thread.sleep(10000);
            throw new AssertionError("Quit failed with locked original");
        }
    }

    static DialogPane dialog(DialogPane previous) throws Exception {
        for (int i=0;i<200;i++) {
            DialogPane pane = fx(() -> {
                for (Window window : Window.getWindows())
                    if (window.isShowing() && window.getScene()!=null && window.getScene().getRoot() instanceof DialogPane d && d!=previous) return d;
                return null;
            });
            if (pane!=null) return pane;
            Thread.sleep(50);
        }
        throw new AssertionError("Missing recovery dialog");
    }
}
