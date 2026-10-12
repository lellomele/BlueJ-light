/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.*;
import java.util.*;

public class JvmRecoverySmoke extends AdvancedSmoke
{
    public static void main(String[] args)
    {
        Path fixture = Path.of(args[0]);
        try
        {
            Files.writeString(fixture.resolve("CrashProbe.java"),
                "public class CrashProbe { public CrashProbe() { Runtime.getRuntime().halt(92); } }\n");
            Files.writeString(fixture.resolve("FailureProbe.java"),
                "public class FailureProbe { public FailureProbe() { throw new IllegalStateException(\"expected test failure\"); } }\n");
        }
        catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
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
                pkg = call(editor, "getPackage");
                Class<?> reason = loader.loadClass("bluej.compiler.CompileReason");
                Class<?> type = loader.loadClass("bluej.compiler.CompileType");
                Class<?> observerType = loader.loadClass("bluej.compiler.FXCompileObserver");
                Object observer = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] { observerType },
                    (proxy, method, values) -> method.getReturnType() == boolean.class ? false : null);
                Object user = Enum.valueOf((Class)reason, "USER");
                Object explicit = Enum.valueOf((Class)type, "EXPLICIT_USER_COMPILE");
                fx(() -> { pkg.getClass().getMethod("compile", observerType, reason, type)
                    .invoke(pkg, observer, user, explicit); return null; });
                boolean compiled = false;
                for (int i = 0; i < 300 && !compiled; i++)
                {
                    compiled = fx(() -> {
                        for (Object target : (Collection<?>)call(pkg, "getVertices"))
                            if (target.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")
                                && !(Boolean)call(target, "isCompiled")) return false;
                        return true;
                    });
                    if (!compiled) Thread.sleep(100);
                }
                if (!compiled) throw new AssertionError("Compilation failed");
                Object target = fx(() -> pkg.getClass().getMethod("getTarget", String.class).invoke(pkg, "School"));
                Object javaEditor = fx(() -> {
                    target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                    return call(target, "getEditor");
                });
                Object document = fx(() -> call(call(javaEditor, "getSourcePane"), "getDocument"));
                String original = fx(() -> (String)call(document, "getFullContent"));
                Object project = fx(() -> call(pkg, "getProject"));
                Object debugger = call(project, "getDebugger");
                Class<?> debuggerType = loader.loadClass("bluej.debugger.Debugger");
                int normal = debuggerType.getField("NORMAL_EXIT").getInt(null);
                int exception = debuggerType.getField("EXCEPTION").getInt(null);
                int terminated = debuggerType.getField("TERMINATED_BY_USER_SYSTEM_EXIT").getInt(null);
                instantiate(debugger, "School", normal);
                instantiate(debugger, "FailureProbe", exception);
                instantiate(debugger, "School", normal);
                Object previous;
                synchronized (debugger) { previous = field(debugger, "vmRef"); }
                instantiate(debugger, "CrashProbe", terminated);
                boolean restarted = false;
                for (int i = 0; i < 300 && !restarted; i++)
                {
                    synchronized (debugger)
                    {
                        Object current = field(debugger, "vmRef");
                        restarted = current != null && current != previous;
                    }
                    if (!restarted) Thread.sleep(100);
                }
                if (!restarted) throw new AssertionError("User JVM did not restart");
                instantiate(debugger, "School", normal);
                fx(() -> {
                    int end = number(document, "getLength");
                    document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                        .invoke(document, end, end, "// after JVM crash\n");
                    String changed = (String)call(document, "getFullContent");
                    action(javaEditor, "undo");
                    if (!original.equals(call(document, "getFullContent"))) throw new AssertionError("Undo after JVM crash failed");
                    action(javaEditor, "redo");
                    if (!changed.equals(call(document, "getFullContent"))) throw new AssertionError("Redo after JVM crash failed");
                    action(javaEditor, "undo");
                    call(javaEditor, "save");
                    return null;
                });
                if (!original.equals(Files.readString(fixture.resolve("School.java"))))
                    throw new AssertionError("User JVM crash changed source");
                verifySaveFailure(javaEditor, document, fixture.resolve("School.java"), original);
                System.out.println("JVM_RECOVERY_OK exception=true restarted=true newExecution=true sourcePreserved=true undo=true redo=true saveFailure=true tabMove=true");
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            }
            catch (Throwable ex) { ex.printStackTrace(); System.exit(2); }
        }, "User JVM recovery verification");
        verify.setDaemon(true);
        verify.start();
        Boot.main(new String[] { "-bluej.userHome=" + args[1], args[0] });
    }

    private static void instantiate(Object debugger, String name, int expected) throws Exception
    {
        Object result = debugger.getClass().getMethod("instantiateClass", String.class).invoke(debugger, name);
        if (number(result, "getExitStatus") != expected) throw new AssertionError("Unexpected execution result for " + name);
    }

    private static void verifySaveFailure(Object javaEditor, Object document, Path file, String original) throws Exception
    {
        Files.setAttribute(file, "dos:readonly", true);
        try
        {
            fx(() -> {
                int end = number(document, "getLength");
                document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                    .invoke(document, end, end, "// read-only save probe\n");
                String changed = (String)call(document, "getFullContent");
                try { call(javaEditor, "save"); throw new AssertionError("Read-only source was overwritten"); }
                catch (java.lang.reflect.InvocationTargetException ex)
                {
                    if (!(ex.getCause() instanceof java.io.IOException)) throw ex;
                }
                call(javaEditor, "close");
                if (!(Boolean)call(javaEditor, "isOpen") || !(Boolean)call(javaEditor, "isModified"))
                    throw new AssertionError("Failed save closed the editor");
                Object tab = field(javaEditor, "fxTab");
                Object parent = call(tab, "getParent");
                javaEditor.getClass().getMethod("setEditorVisible", boolean.class, boolean.class)
                    .invoke(javaEditor, true, true);
                if (call(tab, "getParent") == parent || !(Boolean)call(javaEditor, "isOpen"))
                    throw new AssertionError("Moving unsaved tab failed");
                if (!changed.equals(call(document, "getFullContent"))) throw new AssertionError("Move lost unsaved text");
                action(javaEditor, "undo");
                if (!original.equals(call(document, "getFullContent"))) throw new AssertionError("Failed save lost undo");
                action(javaEditor, "redo");
                if (!changed.equals(call(document, "getFullContent"))) throw new AssertionError("Failed save lost redo");
                return null;
            });
            if (!original.equals(Files.readString(file))) throw new AssertionError("Failed save modified original source");
        }
        finally { Files.setAttribute(file, "dos:readonly", false); }
        fx(() -> {
            action(javaEditor, "undo");
            call(javaEditor, "save");
            if (!original.equals(call(document, "getFullContent"))) throw new AssertionError("Save retry lost source");
            return null;
        });
    }

    private static void action(Object javaEditor, String name) throws Exception
    {
        Object actions = call(javaEditor, "getActions");
        Object command = actions.getClass().getMethod("getActionByName", String.class).invoke(actions, name);
        loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction")
            .getMethod("actionPerformed", boolean.class).invoke(command, false);
    }
}
