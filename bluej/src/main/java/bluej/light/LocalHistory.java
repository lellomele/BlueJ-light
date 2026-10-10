/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-09. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class LocalHistory
{
    public record Revision(Path file, Instant time, String kind) {}
    public record Draft(String text, String baseHash) {}
    private final Path root;
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public LocalHistory(Path preferences) { root = preferences.resolve("local-history"); }

    private Path folder(Path source) throws IOException
    {
        return root.resolve(hash(source.toAbsolutePath().normalize().toString()));
    }

    public static String hash(String value)
    {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static void atomicWrite(Path target, String text) throws IOException
    {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "history-", ".tmp");
        try
        {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { Files.deleteIfExists(temporary); }
    }

    public synchronized void snapshot(Path source, String text, String kind) throws IOException
    {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return;
        Path directory = folder(source);
        Files.createDirectories(directory);
        List<Revision> previous = list(source);
        if (!previous.isEmpty() && read(previous.getFirst()).equals(text)) return;
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        atomicWrite(directory.resolve(id + ".txt"), text);
        Properties metadata = new Properties();
        metadata.setProperty("time", Instant.now().toString());
        metadata.setProperty("kind", kind);
        metadata.setProperty("source", source.toAbsolutePath().toString());
        java.io.StringWriter writer = new java.io.StringWriter();
        metadata.store(writer, null);
        atomicWrite(directory.resolve(id + ".properties"), writer.toString());
        List<Revision> revisions = list(source);
        long retained = 0;
        for (int i = 0; i < revisions.size(); i++)
        {
            Path file = revisions.get(i).file();
            retained += Files.size(file);
            if (i < 50 && retained <= 20L * 1024 * 1024) continue;
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName().toString().replace(".txt", ".properties")));
        }
    }

    public synchronized List<Revision> list(Path source) throws IOException
    {
        Path directory = folder(source);
        if (!Files.isDirectory(directory)) return List.of();
        List<Revision> revisions = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.properties"))
        {
            for (Path file : files)
            {
                Properties values = new Properties();
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
                Path text = file.resolveSibling(file.getFileName().toString().replace(".properties", ".txt"));
                if (Files.isRegularFile(text))
                    try { revisions.add(new Revision(text, Instant.parse(values.getProperty("time")), values.getProperty("kind", "saved"))); }
                    catch (RuntimeException ignored) { }
            }
        }
        revisions.sort(Comparator.comparing(Revision::time).reversed());
        return List.copyOf(revisions);
    }

    public String read(Revision revision) throws IOException
    {
        if (Files.size(revision.file()) > MAX_BYTES) throw new IOException("History entry is too large");
        return Files.readString(revision.file(), StandardCharsets.UTF_8);
    }

    public synchronized void draft(Path source, String text, String diskHash) throws IOException
    {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return;
        Properties values = new Properties();
        values.setProperty("base", diskHash);
        values.setProperty("text", text);
        java.io.StringWriter writer = new java.io.StringWriter();
        values.store(writer, null);
        atomicWrite(folder(source).resolve("draft.data"), writer.toString());
    }

    public synchronized Optional<Draft> recover(Path source, String diskText) throws IOException
    {
        Path file = folder(source).resolve("draft.data");
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES * 4L) return Optional.empty();
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        String text = values.getProperty("text", "");
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return Optional.empty();
        return text.equals(diskText) ? Optional.empty() : Optional.of(new Draft(text, values.getProperty("base", "")));
    }

    public synchronized void clearDraft(Path source) throws IOException { Files.deleteIfExists(folder(source).resolve("draft.data")); }
}
