/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.control.*;

public class CrashRecoverySmoke extends AdvancedSmoke
{
    public static void main(String[] args)
    {
        Thread verify = new Thread(() -> {
            try
            {
                for (int i = 0; i < 300 && editor == null; i++)
                {
                    try { editor = fx(BootSmoke::findEditor); }
                    catch (IllegalStateException ignored) { }
                    if (editor == null) Thread.sleep(100);
                }
                if (editor == null) throw new AssertionError("No project opened");
                loader = editor.getClass().getClassLoader();
                pkg = call(editor, "getPackage");
                Object target = fx(() -> ((Collection<?>)call(pkg, "getVertices")).stream()
                    .filter(t -> t.toString().equals("School")).findFirst().orElseThrow());
                Path file = ((java.io.File)fx(() -> call(target, "getJavaSourceFile"))).toPath();
                Path work = Path.of(args[1]);
                Path originalFile = work.resolve("original.java.txt");
                Path expectedFile = work.resolve("checkpoint.java.txt");
                Object javaEditor;
                if (args[2].equals("checkpoint"))
                    javaEditor = fx(() -> {
                        target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                        return call(target, "getEditor");
                    });
                else
                {
                    javaEditor = fx(() -> call(target, "getEditorIfOpen"));
                    if (javaEditor == null || !fx(() -> (Boolean)call(javaEditor, "isOpen")))
                        throw new AssertionError("Session did not reopen the Java class automatically");
                }
                Object document = fx(() -> call(call(javaEditor, "getSourcePane"), "getDocument"));
                if (args[2].equals("checkpoint"))
                {
                    Thread.sleep(2000);
                    String original = Files.readString(file);
                    Files.writeString(originalFile, original);
                    Path preferences = ((java.io.File)fx(() -> loader.loadClass("bluej.Config")
                        .getMethod("getUserConfigDir").invoke(null))).toPath();
                    Class<?> historyClass = loader.loadClass("bluej.light.LocalHistory");
                    Object history = historyClass.getConstructor(Path.class).newInstance(preferences);
                    for (int round = 0; round < 200; round++)
                    {
                        final int index = round;
                        String current = fx(() -> {
                            int end = number(document, "getLength");
                            document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                                .invoke(document, end, end, "// crash-checkpoint-" + index + "\n");
                            return (String)call(document, "getFullContent");
                        });
                        Thread.sleep(100);
                        Optional<?> draft = (Optional<?>)historyClass.getMethod("recover", Path.class, String.class)
                            .invoke(history, file, original);
                        if (draft.isPresent() && current.equals(call(draft.get(), "text")))
                        {
                            if (!original.equals(Files.readString(file)))
                                throw new AssertionError("Fixture was saved before crash checkpoint");
                            String session = Files.readString(preferences.resolve("recovery-session.properties"));
                            if (!session.contains("School")) throw new AssertionError("Open class absent from session journal");
                            Files.writeString(expectedFile, current);
                            System.out.println("CRASH_CHECKPOINT_OK continuousEditing=true unsaved=true session=true");
                            System.out.flush();
                            Runtime.getRuntime().halt(91);
                        }
                    }
                    throw new AssertionError("No durable checkpoint during continuous editing");
                }
                String original = Files.readString(originalFile);
                String expected = Files.readString(expectedFile);
                DialogPane recovery = dialog("light-recovery-dialog");
                fx(() -> {
                    ButtonType restore = recovery.getButtonTypes().stream()
                        .filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                    ((Button)recovery.lookupButton(restore)).fire();
                    return null;
                });
                fx(() -> {
                    if (!expected.equals(call(document, "getFullContent"))) throw new AssertionError("Incomplete draft restore");
                    if (!original.equals(Files.readString(file))) throw new AssertionError("Restore overwrote original immediately");
                    action(javaEditor, "undo");
                    if (!original.equals(call(document, "getFullContent"))) throw new AssertionError("Restore undo failed");
                    action(javaEditor, "redo");
                    if (!expected.equals(call(document, "getFullContent"))) throw new AssertionError("Restore redo failed");
                    return null;
                });
                System.out.println("CRASH_RECOVERY_OK automaticProject=true automaticClass=true draft=true originalPreserved=true undo=true redo=true");
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            }
            catch (Throwable ex) { ex.printStackTrace(); System.exit(2); }
        }, "Crash recovery verification");
        verify.setDaemon(true);
        verify.start();
        Boot.main(args[2].equals("checkpoint")
            ? new String[] { "-bluej.userHome=" + args[1], args[0] }
            : new String[] { "-bluej.userHome=" + args[1] });
    }

    private static void action(Object javaEditor, String name) throws Exception
    {
        Object actions = call(javaEditor, "getActions");
        Object command = actions.getClass().getMethod("getActionByName", String.class).invoke(actions, name);
        loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction")
            .getMethod("actionPerformed", boolean.class).invoke(command, false);
    }
}
