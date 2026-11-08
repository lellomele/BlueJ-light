/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import bluej.Config;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.target.ClassTarget;
import bluej.utility.Debug;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class SessionRecovery
{
    private static final int MAX_PACKAGES = 200;
    private static final int MAX_CLASSES = 1000;
    @OnThread(Tag.FXPlatform) private static boolean active;
    @OnThread(Tag.FXPlatform) private static FXPlatformRunnable cancelCheckpoint;
    @OnThread(Tag.FXPlatform) private static long restoreGeneration;
    @OnThread(Tag.FXPlatform) private static boolean restoringEditors;

    public record Entry(Path packageDirectory, List<String> classes)
    {
        public Entry { classes = List.copyOf(classes); }
    }

    private SessionRecovery() { }

    public static Path file()
    {
        return Config.getUserConfigDir().toPath().resolve("recovery-session.properties");
    }

    public static List<Entry> read(Path file) throws IOException
    {
        if (!Files.exists(file)) return List.of();
        if (Files.size(file) > 1024 * 1024) throw new IOException("Recovery session exceeds size limit");
        Properties properties = new Properties();
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(input); }
        try
        {
            int count = count(properties, "packages", MAX_PACKAGES);
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                String key = "package." + i;
                Path directory = Path.of(properties.getProperty(key + ".path", "")).toAbsolutePath().normalize();
                if (properties.getProperty(key + ".path") == null || !Files.isDirectory(directory)) continue;
                int classes = count(properties, key + ".classes", MAX_CLASSES);
                List<String> names = new ArrayList<>();
                for (int j = 0; j < classes; j++)
                {
                    String name = properties.getProperty(key + ".class." + j);
                    if (name != null && !name.isBlank()) names.add(name);
                }
                entries.add(new Entry(directory, names));
            }
            return List.copyOf(entries);
        }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid recovery session", ex); }
    }

    private static int count(Properties properties, String key, int maximum)
    {
        int count = Integer.parseInt(properties.getProperty(key, "0"));
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid session count: " + key);
        return count;
    }

    static void write(Path file, List<Entry> entries) throws IOException
    {
        Properties properties = new Properties();
        int count = Math.min(entries.size(), MAX_PACKAGES);
        properties.setProperty("packages", Integer.toString(count));
        for (int i = 0; i < count; i++)
        {
            Entry entry = entries.get(i);
            String key = "package." + i;
            properties.setProperty(key + ".path", entry.packageDirectory().toAbsolutePath().normalize().toString());
            int classes = Math.min(entry.classes().size(), MAX_CLASSES);
            properties.setProperty(key + ".classes", Integer.toString(classes));
            for (int j = 0; j < classes; j++) properties.setProperty(key + ".class." + j, entry.classes().get(j));
        }
        StringWriter text = new StringWriter();
        properties.store(text, "BlueJ light local session recovery");
        AtomicSourceSave.save(file, text.toString(), StandardCharsets.UTF_8, ".backup");
    }

    @OnThread(Tag.FXPlatform)
    public static void begin()
    {
        active = true;
        changed();
    }

    @OnThread(Tag.FXPlatform)
    public static void changed()
    {
        if (!active || restoringEditors || cancelCheckpoint != null) return;
        cancelCheckpoint = JavaFXUtil.runAfter(javafx.util.Duration.millis(250), () -> {
            cancelCheckpoint = null;
            Map<Path, Entry> entries = new LinkedHashMap<>();
            for (PkgMgrFrame frame : PkgMgrFrame.getAllFrames())
            {
                var pkg = frame.getPackage();
                if (pkg == null) continue;
                List<String> classes = new ArrayList<>();
                for (var target : pkg.getVertices())
                    if (target instanceof ClassTarget ct && ct.getEditorIfOpen() != null && ct.getEditorIfOpen().isOpen())
                        classes.add(ct.getIdentifierName());
                Path path = pkg.getPath().toPath().toAbsolutePath().normalize();
                entries.put(path, new Entry(path, classes));
            }
            // Source tabs may belong to a package whose diagram has no open window (or to an orphan target).
            for (bluej.pkgmgr.Project project : bluej.pkgmgr.Project.getProjects())
                for (bluej.editor.Editor editor : project.getConstructedEditors())
                    if (editor instanceof bluej.editor.flow.FlowEditor flow && editor.isOpen())
                    {
                        Path source = flow.getSourcePath();
                        if (source == null || !source.startsWith(project.getProjectDir().toPath().toAbsolutePath().normalize())
                            || !source.getFileName().toString().endsWith(".java")) continue;
                        Entry previous = entries.get(source.getParent());Set<String> names = new LinkedHashSet<>();
                        if (previous != null) names.addAll(previous.classes());
                        String fileName = source.getFileName().toString();names.add(fileName.substring(0,fileName.length() - 5));
                        entries.put(source.getParent(),new Entry(source.getParent(),List.copyOf(names)));
                    }
            Path file = file();
            List<Entry> snapshot = List.copyOf(entries.values());
            HistoryTasks.submitDraft(file, retainedBytes(snapshot), () -> write(file, snapshot));
        });
    }

    @OnThread(Tag.FXPlatform)
    public static void restoreEditors(List<Entry> entries)
    {
        if (entries.isEmpty()) return;
        long generation = ++restoreGeneration;
        restoringEditors = true;
        Path preferences = Config.getUserConfigDir().toPath(); List<Entry> captured = List.copyOf(entries);
        HistoryTasks.submitAsync(retainedBytes(captured), () -> {
            List<Entry> resolved = resolveEntries(captured,new LocalHistory(preferences));
            JavaFXUtil.runNowOrLater(() -> {
                if (generation != restoreGeneration) return;
                try { restoreResolvedEditors(resolved); }
                finally { restoringEditors = false;changed(); }
            });
        }).whenComplete((ignored, failure) -> {
            if (failure != null) JavaFXUtil.runNowOrLater(() -> {
                if (generation != restoreGeneration) return;
                Debug.reportError("Cannot restore editor session",failure);restoringEditors = false;changed();
            });
        });
    }

    /** Resolve a name journalled before a crash midway through its physical rename. No UI work here. */
    static List<Entry> resolveEntries(List<Entry> entries, LocalHistory history)
    {
        Map<Path, Set<String>> result = new LinkedHashMap<>();
        for (Entry entry : entries)
        {
            Set<String> classes = result.computeIfAbsent(entry.packageDirectory(),ignored -> new LinkedHashSet<>());
            for (String name : entry.classes())
            {
                if (name == null || name.isBlank() || !Character.isJavaIdentifierStart(name.codePointAt(0))
                    || !name.codePoints().allMatch(Character::isJavaIdentifierPart)) continue;
                Path source = entry.packageDirectory().resolve(name + ".java");
                try
                {
                    Path actual = history.effectiveSource(source);
                    String file = actual.getFileName().toString();
                    result.computeIfAbsent(actual.getParent(),ignored -> new LinkedHashSet<>()).add(file.endsWith(".java") ? file.substring(0,file.length() - 5) : name);
                }
                catch (IOException | RuntimeException unavailable) { Debug.reportError("Cannot resolve recovered class: " + name,unavailable);classes.add(name); }
            }
        }
        return result.entrySet().stream().map(entry -> new Entry(entry.getKey(),List.copyOf(entry.getValue()))).toList();
    }

    @OnThread(Tag.FXPlatform)
    private static void restoreResolvedEditors(List<Entry> entries)
    {
        for (Entry entry : entries)
        {
            bluej.pkgmgr.Package restoredPackage = null;
            for (PkgMgrFrame frame : PkgMgrFrame.getAllFrames())
            {
                var pkg = frame.getPackage();
                if (pkg == null || !pkg.getPath().toPath().toAbsolutePath().normalize().equals(entry.packageDirectory())) continue;
                restoredPackage = pkg;break;
            }
            if (restoredPackage == null && !entry.classes().isEmpty())
                for (bluej.pkgmgr.Project project : bluej.pkgmgr.Project.getProjects())
                {
                    Path projectRoot = project.getProjectDir().toPath().toAbsolutePath().normalize();
                    if (!entry.packageDirectory().startsWith(projectRoot)) continue;
                    String name = projectRoot.relativize(entry.packageDirectory()).toString().replace(java.io.File.separatorChar,'.');
                    try
                    {
                        restoredPackage = project.getPackage(name);
                        if (restoredPackage != null) { PkgMgrFrame frame = PkgMgrFrame.createFrame(restoredPackage,null);frame.setVisible(true); }
                    }
                    catch (RuntimeException unavailable) { Debug.reportError("Cannot restore package diagram",unavailable); }
                    break;
                }
            if (restoredPackage != null) for (String name : entry.classes())
                if (restoredPackage.getTarget(name) instanceof ClassTarget ct)
                    try { ct.open(); } catch (RuntimeException unavailable) { Debug.reportError("Cannot restore editor: " + name,unavailable); }
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void finish()
    {
        finishAsync();
    }

    @OnThread(Tag.FXPlatform)
    public static java.util.concurrent.CompletableFuture<Void> finishAsync()
    {
        restoreGeneration++;
        restoringEditors = false;
        if (!active) return java.util.concurrent.CompletableFuture.completedFuture(null);
        active = false;
        if (cancelCheckpoint != null) { cancelCheckpoint.run(); cancelCheckpoint = null; }
        Path file = file();
        return HistoryTasks.finishAsync(() -> Files.deleteIfExists(file));
    }

    private static long retainedBytes(List<Entry> entries)
    {
        long bytes = 4096;
        for (Entry entry : entries)
        {
            bytes += 128 + 2L * entry.packageDirectory().toString().length();
            for (String name : entry.classes()) bytes += 64 + 2L * name.length();
        }
        return bytes;
    }
}
