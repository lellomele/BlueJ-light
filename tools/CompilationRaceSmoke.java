/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class CompilationRaceSmoke extends BootSmoke
{
    private static final Queue<Throwable> uiFailures = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger explicitFinishes = new AtomicInteger();
    private static final AtomicInteger automaticFinishes = new AtomicInteger();
    private static final AtomicInteger automaticFailures = new AtomicInteger();
    private static final Queue<String> compileFailures = new ConcurrentLinkedQueue<>();
    private static Object target, sourceEditor, document, project;
    private static Path sourceFile, classFile, blockerDirectory;

    public static void main(String[] args)
    {
        Thread verification = new Thread(() -> {
            Gate gate = null;
            try
            {
                await("package editor", 45, () -> {
                    try { editor = fx(BootSmoke::findEditor); }
                    catch (IllegalStateException notStarted) { return false; }
                    return editor != null;
                });
                loader = editor.getClass().getClassLoader();
                pkg = fx(() -> call(editor, "getPackage"));
                project = fx(() -> call(pkg, "getProject"));
                fx(() -> {
                    Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
                        uiFailures.add(error);
                        error.printStackTrace();
                    });
                    target = ((Collection<?>)call(pkg, "getVertices")).stream()
                        .filter(value -> value.toString().equals("School")).findFirst().orElseThrow();
                    target.getClass().getMethod("doubleClick", boolean.class).invoke(target, false);
                    sourceEditor = call(target, "getEditor");
                    document = call(call(sourceEditor, "getSourcePane"), "getDocument");
                    sourceFile = ((File)call(target, "getJavaSourceFile")).toPath();
                    classFile = ((File)call(target, "getClassFile")).toPath();
                    return null;
                });
                Path fixture = Path.of(args[0]).toAbsolutePath().normalize();
                blockerDirectory = Files.createTempDirectory(fixture.getParent(), "compilation-blocker-");
                Files.writeString(blockerDirectory.resolve("CompilationBlocker.java"),
                    "final class CompilationBlocker { int value() { return 1; } }\n", StandardCharsets.UTF_8);
                addObserver();
                await("initial and loaded compilations to finish", 60, CompilationRaceSmoke::idle);
                String original = fx(() -> (String)call(document, "getFullContent"));
                if (!original.contains("return 42;")) throw new AssertionError("Expected School.answer fixture");
                compileOnce();
                await("baseline explicit compilation", 30,
                    () -> explicitFinishes.get() > 0 && idle() && fx(() -> (Boolean)call(target, "isCompiled")));
                if (!Files.isRegularFile(classFile)) throw new AssertionError("Baseline School.class missing");
                String mode = System.getProperty("bluej.light.race.mode", "all");
                if (!mode.equals("all") && !mode.equals("manual") && !mode.equals("automatic"))
                    throw new AssertionError("Invalid race mode: " + mode);
                int requestedRounds = Integer.getInteger("bluej.light.race.rounds", 8);
                if (requestedRounds < 1 || requestedRounds > 100) throw new AssertionError("Invalid race round count");
                int manualRounds = mode.equals("automatic") ? 0 : requestedRounds;
                int automaticRounds = mode.equals("manual") ? 0 : 2;
                for (int round = 0; round < manualRounds; round++)
                {
                    await("compiler idle before round " + round, 30, CompilationRaceSmoke::idle);
                    byte[] previousClass = Files.readAllBytes(classFile);
                    int before = explicitFinishes.get();
                    gate = blockCompiler();
                    if (!gate.entered.await(15, TimeUnit.SECONDS)) throw new AssertionError("Compiler blocker did not enter");
                    String changed = original.replace("return 42;", "return " + (100 + round) + ";");
                    fx(() -> {
                        document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                            .invoke(document, 0, number(document, "getLength"), changed);
                        return null;
                    });
                    // The real edit schedules MODIFIED/ERROR_CHECK_ONLY; the gate keeps its start callback pending.
                    await("automatic target queued before compiler start", 15, () -> fx(() ->
                        (Boolean)call(target, "isQueued") && !editorFlag("compilationStarted")));
                    compileOnce();
                    fx(() -> null);
                    fx(() -> null);
                    boolean gapHeld = fx(() -> (Boolean)call(target, "isQueued") && !editorFlag("compilationStarted"));
                    if (!gapHeld) throw new AssertionError("Compiler-start gap was not held");
                    System.out.println("COMPILATION_RACE_GAP round=" + round + " queued=true started=false explicitRequestedOnce=true");
                    gate.release.countDown();
                    Gate finishedGate = gate;
                    await("explicit completion after held gap, round " + round, 30, () ->
                        explicitFinishes.get() > before && idle() && fx(() -> (Boolean)call(target, "isCompiled")));
                    if (finishedGate.failure.get() != null) throw new AssertionError("Compiler gate failed", finishedGate.failure.get());
                    if (explicitFinishes.get() != before + 1) throw new AssertionError("Unexpected duplicate explicit compilation");
                    byte[] updatedClass = Files.readAllBytes(classFile);
                    if (java.util.Arrays.equals(previousClass, updatedClass)) throw new AssertionError("School.class was not updated");
                    if (!changed.equals(Files.readString(sourceFile))) throw new AssertionError("Edited source was not saved");
                    checkFailures();
                    System.out.println("COMPILATION_RACE_ROUND_OK round=" + round + " classSHA256="
                        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(updatedClass)));
                    gate = null;
                }
                for (int round = 0; round < automaticRounds; round++)
                {
                    await("compiler idle before automatic round " + round, 30, CompilationRaceSmoke::idle);
                    int explicitBefore = explicitFinishes.get();
                    int automaticBefore = automaticFinishes.get();
                    int failedChecksBefore = automaticFailures.get();
                    gate = blockCompiler();
                    if (!gate.entered.await(15, TimeUnit.SECONDS)) throw new AssertionError("Compiler blocker did not enter");
                    String valid = original.replace("return 42;", "return " + (200 + round) + ";");
                    String invalid = valid.replace("int answer()", "void answer()");
                    if (valid.equals(invalid)) throw new AssertionError("Expected int School.answer fixture");
                    replace(valid);
                    await("first automatic target queued before compiler start", 15, () -> fx(() ->
                        (Boolean)call(target, "isQueued") && !editorFlag("compilationStarted")));
                    replace(invalid);
                    fx(() -> null);
                    await("second automatic timer scheduled", 10, () -> fx(() ->
                        ((Collection<?>)field(project, "scheduledTargets")).contains(target)));
                    await("second timer fired while first target remains queued", 10, () -> fx(() ->
                        !((Collection<?>)field(project, "scheduledTargets")).contains(target)
                            && (Boolean)call(target, "isQueued") && !editorFlag("compilationStarted")));
                    fx(() -> null);
                    boolean savedBeforeStart = round == 1;
                    if (savedBeforeStart)
                    {
                        fx(() -> { call(sourceEditor, "save"); return null; });
                        if (!invalid.equals(Files.readString(sourceFile)))
                            throw new AssertionError("Explicit save before compiler start did not update the source");
                    }
                    System.out.println("COMPILATION_RACE_AUTO_GAP round=" + round
                        + " queued=true started=false secondTimerFired=true explicitRequests=0 savedBeforeStart=" + savedBeforeStart);
                    gate.release.countDown();
                    Gate finishedGate = gate;
                    await("fresh automatic error check after held gap, round " + round, 30, () ->
                        automaticFailures.get() > failedChecksBefore && idle() && hasIncompatibleTypesDiagnostic()
                            && invalid.equals(Files.readString(sourceFile)));
                    if (finishedGate.failure.get() != null) throw new AssertionError("Compiler gate failed", finishedGate.failure.get());
                    if (explicitFinishes.get() != explicitBefore)
                        throw new AssertionError("Automatic checking triggered an unexpected explicit compilation");
                    int completedAutomatic = automaticFinishes.get() - automaticBefore;
                    int expectedAutomatic = savedBeforeStart ? 1 : 2;
                    if (completedAutomatic != expectedAutomatic)
                        throw new AssertionError("Expected " + expectedAutomatic + " automatic compilations, got " + completedAutomatic);
                    checkFailures();
                    System.out.println("COMPILATION_RACE_AUTO_ROUND_OK round=" + round
                        + " freshDiagnostic=true invalidSourceSaved=true explicitRequests=0 savedBeforeStart="
                        + savedBeforeStart + " automaticCompilations=" + completedAutomatic);
                    gate = null;
                    replace(original);
                    compileOnce();
                    await("repair after automatic round " + round, 30, () ->
                        explicitFinishes.get() > explicitBefore && idle() && fx(() -> (Boolean)call(target, "isCompiled")));
                    if (explicitFinishes.get() != explicitBefore + 1)
                        throw new AssertionError("Unexpected duplicate explicit repair compilation");
                }
                System.out.println("COMPILATION_RACE_OK manualRounds=" + manualRounds + " automaticRounds=" + automaticRounds
                    + " heldBeforeStart=true explicitEnd=true freshDiagnostics=" + (automaticRounds > 0)
                    + " classesUpdated=" + (manualRounds > 0) + " inputFreshdedupe=" + (automaticRounds > 0)
                    + " uiExceptions=0 retries=0");
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            }
            catch (Throwable error)
            {
                if (gate != null) gate.release.countDown();
                error.printStackTrace();
                System.exit(2);
            }
            finally
            {
                if (gate != null) gate.release.countDown();
            }
        }, "Compilation-start race verification");
        verification.setDaemon(true);
        verification.start();
        Boot.main(args[1].equals("@portable") ? new String[]{args[0]}
            : new String[]{"-bluej.userHome=" + args[1], args[0]});
    }

    private static void compileOnce() throws Exception
    {
        fx(() -> { call(sourceEditor, "compileOrShowNextError"); return null; });
    }

    private static void replace(String text) throws Exception
    {
        fx(() -> {
            document.getClass().getMethod("replaceText", int.class, int.class, String.class)
                .invoke(document, 0, number(document, "getLength"), text);
            return null;
        });
    }

    private static boolean hasIncompatibleTypesDiagnostic() throws Exception
    {
        return fx(() -> {
            for (Object diagnostic : (Collection<?>)call(sourceEditor, "getLightDiagnostics"))
            {
                String code = (String)call(diagnostic, "getCompilerCode");
                if (code != null && code.contains("prob.found.req")) return true;
            }
            return false;
        });
    }

    private static boolean idle() throws Exception
    {
        return fx(() -> {
            for (Object value : (Collection<?>)call(pkg, "getVertices"))
                if (value.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")
                    && (Boolean)call(value, "isQueued")) return false;
            return !editorFlag("compilationQueued") && !editorFlag("compilationStarted")
                && !editorFlag("requeueForCompilation")
                && ((Collection<?>)field(project, "scheduledTargets")).isEmpty()
                && ((Collection<?>)field(project, "scheduledPkgs")).isEmpty();
        });
    }

    private static boolean editorFlag(String name) throws Exception
    {
        return (Boolean)field(sourceEditor, name);
    }

    static Object field(Object object, String name) throws Exception
    {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void addObserver() throws Exception
    {
        Class<?> observerType = loader.loadClass("bluej.compiler.FXCompileObserver");
        Object observer = Proxy.newProxyInstance(loader, new Class<?>[]{observerType}, (proxy, method, values) -> {
            if ((method.getName().equals("startCompile") || method.getName().equals("endCompile")) && containsSchool(values[0]))
            {
                String type = values[2].toString();
                System.out.println("COMPILATION_RACE_EVENT " + method.getName() + " type=" + type
                    + " resultOrReason=" + values[1] + " sequence=" + values[3]);
                if (method.getName().equals("endCompile") && type.equals("EXPLICIT_USER_COMPILE"))
                {
                    if (Boolean.TRUE.equals(values[1])) explicitFinishes.incrementAndGet();
                    else compileFailures.add("Explicit compilation failed, sequence=" + values[3]);
                }
                else if (method.getName().equals("endCompile") && type.equals("ERROR_CHECK_ONLY"))
                {
                    automaticFinishes.incrementAndGet();
                    if (Boolean.FALSE.equals(values[1])) automaticFailures.incrementAndGet();
                }
            }
            return method.getReturnType() == boolean.class ? false : null;
        });
        fx(() -> { pkg.getClass().getMethod("addCompileObserver", observerType).invoke(pkg, observer); return null; });
    }

    private static boolean containsSchool(Object sources) throws Exception
    {
        for (int index = 0; index < Array.getLength(sources); index++)
        {
            File file = (File)call(Array.get(sources, index), "getJavaCompileInputFile");
            if (file.toPath().toAbsolutePath().normalize().equals(sourceFile.toAbsolutePath().normalize())) return true;
        }
        return false;
    }

    private static Gate blockCompiler() throws Exception
    {
        Gate gate = new Gate();
        Class<?> inputType = loader.loadClass("bluej.compiler.CompileInputFile");
        Class<?> observerType = loader.loadClass("bluej.compiler.CompileObserver");
        Class<?> reasonType = loader.loadClass("bluej.compiler.CompileReason");
        Class<?> compileType = loader.loadClass("bluej.compiler.CompileType");
        File source = blockerDirectory.resolve("CompilationBlocker.java").toFile();
        Object inputs = Array.newInstance(inputType, 1);
        Array.set(inputs, 0, inputType.getConstructor(File.class, File.class).newInstance(source, source));
        Object observer = Proxy.newProxyInstance(loader, new Class<?>[]{observerType}, (proxy, method, values) -> {
            if (method.getName().equals("startCompile"))
            {
                gate.entered.countDown();
                if (!gate.release.await(30, TimeUnit.SECONDS))
                {
                    IllegalStateException timeout = new IllegalStateException("Compiler gate timed out");
                    gate.failure.set(timeout);
                    throw timeout;
                }
            }
            else if (method.getName().equals("endCompile") && !Boolean.TRUE.equals(values[1]))
                gate.failure.compareAndSet(null, new AssertionError("Blocker source compilation failed"));
            return null;
        });
        Object queue = loader.loadClass("bluej.compiler.JobQueue").getMethod("getJobQueue").invoke(null);
        Object classLoader = fx(() -> call(project, "getClassLoader"));
        Object reason = Enum.valueOf((Class)reasonType, "USER");
        Object type = Enum.valueOf((Class)compileType, "INTERNAL_COMPILE");
        queue.getClass().getMethod("addJob", inputs.getClass(), observerType,
            loader.loadClass("bluej.classmgr.BPClassLoader"), File.class, boolean.class,
            java.nio.charset.Charset.class, reasonType, compileType)
            .invoke(queue, inputs, observer, classLoader, blockerDirectory.toFile(), false, StandardCharsets.UTF_8, reason, type);
        return gate;
    }

    private static void await(String description, int seconds, Callable<Boolean> condition) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.call())
        {
            checkFailures();
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Timed out waiting for " + description);
            if (Thread.interrupted()) throw new InterruptedException();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }

    private static void checkFailures()
    {
        if (!uiFailures.isEmpty()) throw new AssertionError("JavaFX exception", uiFailures.peek());
        if (!compileFailures.isEmpty()) throw new AssertionError(compileFailures.peek());
    }

    private static final class Gate
    {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
    }
}
