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
        if (!active || cancelCheckpoint != null) return;
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
            Path file = file();
            List<Entry> snapshot = List.copyOf(entries.values());
            HistoryTasks.submitDraft(file, () -> write(file, snapshot));
        });
    }

    @OnThread(Tag.FXPlatform)
    public static void restoreEditors(List<Entry> entries)
    {
        for (Entry entry : entries)
            for (PkgMgrFrame frame : PkgMgrFrame.getAllFrames())
            {
                var pkg = frame.getPackage();
                if (pkg == null || !pkg.getPath().toPath().toAbsolutePath().normalize().equals(entry.packageDirectory())) continue;
                for (String name : entry.classes())
                    if (pkg.getTarget(name) instanceof ClassTarget ct)
                    {
                        try { ct.open(); }
                        catch (RuntimeException ex) { Debug.reportError("Cannot restore editor: " + name, ex); }
                    }
            }
    }

    @OnThread(Tag.FXPlatform)
    public static void finish()
    {
        if (!active) return;
        active = false;
        if (cancelCheckpoint != null) { cancelCheckpoint.run(); cancelCheckpoint = null; }
        Path file = file();
        HistoryTasks.submit(() -> Files.deleteIfExists(file));
    }
}
