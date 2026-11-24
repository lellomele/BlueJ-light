/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import javafx.geometry.Point2D;

/** Checks a real diagram imported from an old, already-saved project with zero recorded edges. */
public class CompiledDiagramSmoke extends BootSmoke {
    private static final List<Point2D> PASS_ROUTE = List.of(new Point2D(120, 80),
        new Point2D(175, 80), new Point2D(175, 175), new Point2D(285, 175));

    public static void main(String[] args) {
        try { run(args); }
        catch (Exception error) { throw new IllegalStateException("Could not start synthetic diagram test", error); }
    }

    private static void run(String[] args) throws Exception {
        Path folder = Files.createTempDirectory(Path.of(args[0]).toAbsolutePath().getParent(), "diagram-");
        Path pass = folder.resolve("Pass.java");
        Path client = folder.resolve("Client.java");
        Path binary = folder.resolve("Library.java");
        String original = "public class Client { void run() { new Pass(); new Pass(); new Library(); } }";
        Files.writeString(pass, "public class Pass {}");
        Files.writeString(client, original);
        Files.writeString(binary, "public class Library {}");
        compile(folder, client, pass, binary);
        Files.delete(binary);
        byte[] libraryClass = Files.readAllBytes(folder.resolve("Library.class"));
        Files.delete(folder.resolve("Library.class")); // Not yet distributed with this project.
        Path metadata = folder.resolve("package.bluej");
        Files.writeString(metadata, "project.charset=UTF-8\npackage.numTargets=2\npackage.numDependencies=0\n"
            + "target1.name=Client\ntarget1.type=ClassTarget\ntarget1.x=40\ntarget1.y=60\ntarget1.width=80\ntarget1.height=40\n"
            + "target2.name=Pass\ntarget2.type=ClassTarget\ntarget2.x=285\ntarget2.y=155\ntarget2.width=80\ntarget2.height=40\n");
        Thread worker = new Thread(() -> {
            try {
                for (int i = 0; i < 200 && editor == null; i++) {
                    try { editor = fx(BootSmoke::findEditor); } catch (IllegalStateException starting) { }
                    if (editor == null) Thread.sleep(100);
                }
                require(editor != null, "Project diagram did not open");
                loader = editor.getClass().getClassLoader();
                Object frame = fx(() -> ((Object[])loader.loadClass("bluej.pkgmgr.PkgMgrFrame")
                    .getMethod("getAllFrames").invoke(null))[0]);
                fx(() -> {
                    setRoute(checkUses(frame, Set.of("Pass")));
                    require((boolean)call(frame, "doSave"), "Could not save diagram");
                    return null;
                });
                close(frame);
                open(frame, folder);
                fx(() -> {
                    checkRoute(checkUses(frame, Set.of("Pass")));
                    for (String name : Set.of("Client", "Pass")) {
                        Object cache = call(target(frame, name), "getSourceInfo");
                        require(call(cache, "getInfoIfAvailable") == null, name + " unnecessarily reparsed on reopen");
                    }
                    return null;
                });

                close(frame);
                Properties legacy = new Properties();
                try (var input = Files.newInputStream(metadata)) { legacy.load(input); }
                require(legacy.keySet().removeIf(key -> key.toString().endsWith(".usesSourceStamp")), "Missing saved source stamps");
                try (var output = Files.newOutputStream(metadata)) { legacy.store(output, null); }
                open(frame, folder);
                fx(() -> {
                    checkRoute(checkUses(frame, Set.of("Pass")));
                    return null;
                });
                close(frame);
                Files.write(folder.resolve("Library.class"), libraryClass);
                open(frame, folder);
                fx(() -> {
                    // A new box changes the diagram geometry. Record a fresh route before
                    // checking that removing an unrelated edge leaves it intact.
                    setRoute(checkUses(frame, Set.of("Pass", "Library")));
                    require(!(boolean)call(target(frame,"Library"), "hasSourceCode"), "Expected a compiled-only target");
                    require((boolean)call(frame, "doSave"), "Could not save new target");
                    return null;
                });
                close(frame);
                Files.writeString(client, "public class Client { void run() { new Pass(); } }");
                compile(folder, client); // Existing targets, source changed and compiled outside the IDE.
                FileTime compiled = Files.getLastModifiedTime(folder.resolve("Pass.class"));
                Files.setLastModifiedTime(pass, FileTime.fromMillis(compiled.toMillis() + 2000));
                open(frame, folder);
                fx(() -> {
                    checkRoute(checkUses(frame, Set.of("Pass")));
                    require(!(boolean)call(target(frame, "Client"), "isCompiled"), "Stale dependency failed to invalidate Client");
                    System.out.println("COMPILED_DIAGRAM_OK legacy=true cachedZero=true legacyRoute=true newClass=true externalChange=true staleDependency=true");
                    loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);
                    return null;
                });
            } catch (Throwable error) { error.printStackTrace(); System.exit(2); }
        }, "Compiled diagram verification");
        worker.setDaemon(true);
        worker.start();
        Boot.main(new String[]{"-bluej.userHome=" + args[1], folder.toString()});
    }

    private static void compile(Path directory, Path... files) {
        var args = new java.util.ArrayList<>(java.util.List.of("-proc:none", "-classpath", directory.toString(), "-d", directory.toString()));
        for (Path file : files) args.add(file.toString());
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) == 0, "Synthetic project did not compile");
    }

    private static void close(Object frame) throws Exception {
        var closed = fx(() -> (CompletableFuture<?>)call(frame, "closePackageAsync"));
        require(Boolean.TRUE.equals(closed.get(20, TimeUnit.SECONDS)), "Could not close project");
    }

    private static void open(Object frame, Path folder) throws Exception {
        require(fx(() -> (boolean)frame.getClass().getMethod("openExerciseProject", String.class)
            .invoke(frame, folder.toString())), "Could not reopen project");
    }

    private static Object target(Object frame, String name) throws Exception {
        Object p = call(frame, "getPackage");
        return p.getClass().getMethod("getTarget", String.class).invoke(p, name);
    }

    private static void setRoute(Object arrow) throws Exception {
        arrow.getClass().getMethod("setLayoutRoute", List.class).invoke(arrow, PASS_ROUTE);
    }

    private static void checkRoute(Object arrow) throws Exception {
        require(PASS_ROUTE.equals(call(arrow, "getLayoutRoute")), "Saved Client to Pass route was lost");
    }

    private static Object checkUses(Object frame, Set<String> expected) throws Exception {
        Object projectDiagram = call(call(frame, "getPackage"), "getEditor");
        Collection<?> arrows = (Collection<?>)call(projectDiagram, "getVisibleEdges");
        Set<String> found = new HashSet<>();
        Object passArrow = null;
        for (Object arrow : arrows) {
            require(arrow.getClass().getSimpleName().equals("UsesDependency"), "Unexpected arrow type");
            require(call(call(arrow, "getFrom"), "getIdentifierName").equals("Client"), "Unexpected source");
            String destination = (String)call(call(arrow, "getTo"), "getIdentifierName");
            require(found.add(destination), "Duplicate arrow");
            if (destination.equals("Pass")) passArrow = arrow;
        }
        require(found.equals(expected), "Expected " + expected + ", found " + found);
        return passArrow;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
