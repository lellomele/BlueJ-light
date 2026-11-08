/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Persistent history identities. A rename never copies, replaces or deletes history payloads. */
@OnThread(Tag.Any)
final class HistoryPaths
{
    // Separate editors have separate LocalHistory objects; path-link transactions share this lock.
    private static final Object lock = new Object();
    private final Path root;
    // Old queued jobs in this editor retain their document identity after its path changes.
    private final Map<String, String> identities = new HashMap<>();

    record Move(String id, Path source, Path destination, String identity, String vacatedIdentity, Path projectRoot) { }

    HistoryPaths(Path root) { this.root = root; }
    private static String key(Path source) { return source.toAbsolutePath().normalize().toString(); }

    Path folder(Path source) throws IOException
    {
        synchronized (lock)
        {
            String path = key(source);
            String identity = identities.get(path);
            if (identity == null)
            {
                recoverMoves();
                Path link = root.resolve("path-links").resolve(LocalHistory.hash(path) + ".properties");
                identity = LocalHistory.hash(path);
                if (Files.isRegularFile(link))
                {
                    Properties values = read(link);
                    if (!path.equals(values.getProperty("source")) || !validIdentity(values.getProperty("identity")))
                        throw new IOException("Invalid local history path link");
                    identity = values.getProperty("identity");
                }
                identities.put(path, identity);
            }
            return root.resolve(identity);
        }
    }

    Move prepare(Path source, Path destination) throws IOException
    { return prepare(source,destination,null); }

    Move prepare(Path source, Path destination, Path projectRoot) throws IOException
    {
        synchronized (lock)
        {
            recoverMoves();
            Path from = source.toAbsolutePath().normalize(), to = destination.toAbsolutePath().normalize();
            Path scope = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
            if (!validPathChange(from,to,scope))
                throw new IOException("Invalid history path change");
            Move move = new Move(UUID.randomUUID().toString(), from, to, folder(from).getFileName().toString(), LocalHistory.hash(UUID.randomUUID().toString()),scope);
            write(journal(move), values(move, "prepared"));
            return move;
        }
    }

    void commit(Move move) throws IOException
    {
        synchronized (lock)
        {
            // Once the source moved, keep the destination reachable even if link publication fails.
            identities.put(key(move.destination()), move.identity());
            write(journal(move), values(move, "committed"));
            publish(move);
        }
    }

    void abort(Move move) throws IOException { synchronized (lock) { Files.deleteIfExists(journal(move)); } }

    private Path journal(Move move) { return root.resolve("path-transactions").resolve(move.id() + ".properties"); }

    private void publish(Move move) throws IOException
    {
        // A newly created class at the vacated name must have its own draft/history identity.
        link(move.source(), move.vacatedIdentity(),move.destination(),move.projectRoot());
        link(move.destination(), move.identity(),null,null);
        try { Files.deleteIfExists(journal(move)); }
        catch (IOException unavailable)
        {
            // A completed record cannot replay over a later rename if deletion is unavailable.
            write(journal(move), values(move, "complete"));
        }
    }

    private void link(Path source, String identity, Path movedTo, Path projectRoot) throws IOException
    {
        Properties values = new Properties(); values.setProperty("source", key(source)); values.setProperty("identity", identity);
        if (movedTo != null) values.setProperty("movedTo",key(movedTo));
        if (projectRoot != null) values.setProperty("projectRoot",key(projectRoot));
        write(root.resolve("path-links").resolve(LocalHistory.hash(key(source)) + ".properties"), values);
    }

    Path effectiveSource(Path source) throws IOException
    {
        synchronized (lock)
        {
            recoverMoves();
            Path current = source.toAbsolutePath().normalize(); Set<String> seen = new HashSet<>();
            for (int count = 0; count < 32; count++)
            {
                if (!seen.add(key(current))) throw new IOException("Cyclic history path links");
                Path link = root.resolve("path-links").resolve(LocalHistory.hash(key(current)) + ".properties");
                if (!Files.isRegularFile(link)) return current;
                Properties values = read(link);
                if (!key(current).equals(values.getProperty("source"))) throw new IOException("Invalid history path link");
                String redirect = values.getProperty("movedTo"); if (redirect == null) return current;
                Path next;
                try { next = Path.of(redirect).toAbsolutePath().normalize(); }
                catch (RuntimeException invalid) { throw new IOException("Invalid history path redirect",invalid); }
                Path scope = null;
                try { if (values.getProperty("projectRoot") != null) scope = Path.of(values.getProperty("projectRoot")); }
                catch (RuntimeException invalid) { throw new IOException("Invalid history project root",invalid); }
                if (!validPathChange(current,next,scope)) throw new IOException("History path redirect leaves its package or project");
                if (Files.exists(current))
                {
                    boolean changedCase = !key(current).equals(key(next)) && Files.exists(next) && Files.isSameFile(current,next)
                        && current.getFileName().toString().equalsIgnoreCase(next.getFileName().toString()) && exactEntry(next);
                    if (!changedCase) return current;
                }
                current = next;
            }
            throw new IOException("Too many history path redirects");
        }
    }

    private static boolean exactEntry(Path path) throws IOException
    {
        try (var files = Files.list(path.getParent()))
        { return files.anyMatch(entry -> entry.getFileName().toString().equals(path.getFileName().toString())); }
    }

    private void recoverMoves() throws IOException
    {
        Path directory = root.resolve("path-transactions");
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.properties"))
        {
            int count = 0;
            for (Path file : files)
            {
                if (++count > 1024) throw new IOException("Too many unfinished history path changes");
                Properties values;
                Move move;
                try
                {
                    values = read(file);
                    if ("complete".equals(values.getProperty("phase"))) continue;
                    move = decode(values);
                    if (!journal(move).equals(file)) throw new IOException("Invalid history transaction filename");
                }
                catch (IOException invalid)
                {
                    bluej.utility.Debug.reportError("Ignoring damaged history path metadata: " + file.getFileName(), invalid);
                    continue;
                }
                if ("committed".equals(values.getProperty("phase")) || moved(move))
                {
                    write(file, values(move, "committed"));
                    publish(move);
                }
            }
        }
    }

    private boolean moved(Move move) throws IOException
    {
        if (!Files.isRegularFile(move.destination())) return false;
        if (!Files.exists(move.source())) return true;
        if (!move.source().getFileName().toString().equalsIgnoreCase(move.destination().getFileName().toString())
            || !Files.isSameFile(move.source(), move.destination())) return false;
        // On Windows both case spellings exist logically; inspect the actual directory entry.
        return exactEntry(move.destination());
    }

    private static Move decode(Properties values) throws IOException
    {
        try
        {
            String id = values.getProperty("id"), identity = values.getProperty("identity"), vacated = values.getProperty("vacated");
            if (!UUID.fromString(id).toString().equals(id) || !validIdentity(identity) || !validIdentity(vacated))
                throw new IllegalArgumentException();
            Path source = Path.of(values.getProperty("source")), destination = Path.of(values.getProperty("destination"));
            Path scope = values.getProperty("projectRoot") == null ? null : Path.of(values.getProperty("projectRoot"));
            if (!validPathChange(source,destination,scope))
                throw new IllegalArgumentException();
            return new Move(id, source, destination, identity, vacated,scope);
        }
        catch (RuntimeException invalid) { throw new IOException("Invalid local history path transaction", invalid); }
    }

    private static boolean validIdentity(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean validPathChange(Path source, Path destination, Path projectRoot) throws IOException
    {
        if (!source.isAbsolute() || !destination.isAbsolute() || source.getParent() == null || destination.getParent() == null
            || !source.normalize().equals(source) || !destination.normalize().equals(destination) || key(source).equals(key(destination))
            || !source.getFileName().toString().endsWith(".java") || !destination.getFileName().toString().endsWith(".java")) return false;
        if (projectRoot == null) return Objects.equals(source.getParent(),destination.getParent());
        if (!projectRoot.isAbsolute() || !projectRoot.normalize().equals(projectRoot)
            || !(Files.isRegularFile(projectRoot.resolve("package.bluej")) || Files.isRegularFile(projectRoot.resolve("bluej.pkg")))) return false;
        Path realRoot = projectRoot.toRealPath();
        return source.getParent().toRealPath().startsWith(realRoot) && destination.getParent().toRealPath().startsWith(realRoot);
    }
    private static Properties values(Move move, String phase)
    {
        Properties values = new Properties();
        values.setProperty("id", move.id()); values.setProperty("source", key(move.source())); values.setProperty("destination", key(move.destination()));
        values.setProperty("identity", move.identity()); values.setProperty("vacated", move.vacatedIdentity()); values.setProperty("phase", phase);
        if (move.projectRoot() != null) values.setProperty("projectRoot",key(move.projectRoot()));
        return values;
    }
    private static Properties read(Path file) throws IOException
    {
        if (Files.size(file) > 64 * 1024) throw new IOException("History path metadata exceeds size limit");
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid history path metadata", invalid); }
        return values;
    }
    private static void write(Path file, Properties values) throws IOException
    {
        Files.createDirectories(file.getParent());
        StringWriter text = new StringWriter(); values.store(text, "BlueJ light history path identity");
        AtomicSourceSave.save(file, text.toString(), StandardCharsets.UTF_8, ".backup");
    }
}
