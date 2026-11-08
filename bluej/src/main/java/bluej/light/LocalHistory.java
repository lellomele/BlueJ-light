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
    private final HistoryPaths paths;
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public LocalHistory(Path preferences) { paths = new HistoryPaths(preferences.resolve("local-history")); }

    private Path folder(Path source) throws IOException
    {
        return paths.folder(source);
    }

    synchronized HistoryPaths.Move preparePathChange(Path source, Path destination) throws IOException { return paths.prepare(source, destination); }
    synchronized HistoryPaths.Move preparePathChange(Path source, Path destination, Path projectRoot) throws IOException { return paths.prepare(source, destination, projectRoot); }
    synchronized void commitPathChange(HistoryPaths.Move move) throws IOException { paths.commit(move); }
    synchronized void abortPathChange(HistoryPaths.Move move) throws IOException { paths.abort(move); }
    public synchronized Path effectiveSource(Path source) throws IOException { return paths.effectiveSource(source); }
    synchronized void retirePath(Path source) throws IOException { paths.retire(source); }

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
        if (!previous.isEmpty())
        {
            try { if (read(previous.getFirst()).equals(text)) return; }
            catch (IOException ex) { bluej.utility.Debug.message("Ignoring unreadable previous history entry"); }
        }
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
                try
                {
                    if (Files.size(file) > 64 * 1024) continue;
                    Properties values = new Properties();
                    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
                    Path text = file.resolveSibling(file.getFileName().toString().replace(".properties", ".txt"));
                    if (Files.isRegularFile(text) && Files.size(text) <= MAX_BYTES)
                        revisions.add(new Revision(text, Instant.parse(values.getProperty("time")), values.getProperty("kind", "saved")));
                }
                catch (IOException | RuntimeException ex)
                {
                    bluej.utility.Debug.message("Ignoring invalid local history entry: " + file.getFileName());
                }
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
        draft(source, text, diskHash, "", -1);
    }

    /** A revision belongs to one loaded editor generation; it is not comparable across owners. */
    public synchronized void draft(Path source, String text, String diskHash, String owner, long revision) throws IOException
    {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return;
        Properties values = new Properties();
        values.setProperty("base", diskHash);
        values.setProperty("text", text);
        values.setProperty("owner", owner);
        values.setProperty("revision", Long.toString(revision));
        java.io.StringWriter writer = new java.io.StringWriter();
        values.store(writer, null);
        atomicWrite(folder(source).resolve("draft.data"), writer.toString());
    }

    /** A late acknowledgement may retire an older draft, but must preserve newer unsaved text. */
    public synchronized void acknowledgeSave(Path source, String before, String saved, String owner, long revision) throws IOException
    {
        if (before != null) snapshot(source, before, "before save");
        snapshot(source, saved, "saved");
        Path file = folder(source).resolve("draft.data");
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES * 4L) return;
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        String text = values.getProperty("text", "");
        boolean sameOwner = !owner.isEmpty() && owner.equals(values.getProperty("owner", ""));
        long draftRevision;
        try { draftRevision = Long.parseLong(values.getProperty("revision", "-1")); }
        catch (NumberFormatException invalid) { draftRevision = -1; }
        if (text.equals(saved) || (sameOwner && draftRevision >= 0 && draftRevision <= revision))
        {
            Files.deleteIfExists(file);
        }
        else if (sameOwner && draftRevision > revision)
        {
            // The new draft remains unsaved, now relative to our own completed disk version.
            values.setProperty("base", hash(saved));
            java.io.StringWriter writer = new java.io.StringWriter();
            values.store(writer, null);
            atomicWrite(file, writer.toString());
        }
        // Unknown/legacy or other-editor drafts are never deleted merely by a revision comparison.
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
