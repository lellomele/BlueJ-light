/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Project-owned, bounded source writer. No UI object crosses into its worker. */
@OnThread(Tag.Any)
public final class SourceSaveService implements AutoCloseable
{
    public enum Outcome { SAVED, CONFLICT, CANCELLED, FAILED }

    /** Identity of one loaded editor/file generation, not of its current text. */
    public static final class Owner
    {
        private final String id = UUID.randomUUID().toString();
        private final AtomicBoolean retired = new AtomicBoolean();
        private Path path;
        private long lastAcceptedRevision = Long.MIN_VALUE;
        public String id() { return id; }
        public void retire() { retired.set(true); }
        public boolean isRetired() { return retired.get(); }
        private synchronized boolean reserve(Path source, long revision)
        {
            if (retired.get() || (path != null && !path.equals(source)) || revision < lastAcceptedRevision) return false;
            path = source; lastAcceptedRevision = revision; return true;
        }
    }

    public record Snapshot(Owner owner, long revision, Path source, String text, Charset charset,
                           String expectedDiskText, boolean checkDiskChanges, boolean recoveryCopy)
    {
        public Snapshot
        {
            Objects.requireNonNull(owner); Objects.requireNonNull(source); Objects.requireNonNull(text); Objects.requireNonNull(charset);
            source = source.toAbsolutePath().normalize();
        }
    }

    /** Missing disk metadata after a successful commit is represented by -1, not a false save failure. */
    public record Result(Outcome outcome, Snapshot snapshot, String previousText, String diskText,
                         long modifiedMillis, long byteLength, IOException failure)
    {
        public boolean saved() { return outcome == Outcome.SAVED; }
    }

    @FunctionalInterface interface Storage
    {
        void save(Snapshot snapshot, AtomicSourceSave.Replacement replacement) throws IOException;
    }

    private record Written(Owner owner, long revision, String text) { }
    private static final Map<Path, FileLock> fileLocks = new HashMap<>();
    private static final class FileLock
    {
        final ReentrantLock lock = new ReentrantLock();
        int users;
    }
    private static final class Conflict extends IOException
    {
        final String text;
        Conflict(String text) { super("Source changed on disk"); this.text = text; }
    }
    private static final class Retired extends IOException { Retired() { super("Source save was cancelled"); } }

    private final ThreadPoolExecutor writer;
    private final Storage storage;
    private final int capacity;
    private final Map<Path, Written> written = new HashMap<>(); // Accessed only by this service's worker.
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private boolean closing;
    private int pending;

    public SourceSaveService()
    {
        this(64, (snapshot, replacement) -> AtomicSourceSave.save(snapshot.source(), snapshot.text(), snapshot.charset(), "#", replacement));
    }

    SourceSaveService(int capacity, Storage storage)
    {
        if (capacity < 1) throw new IllegalArgumentException("Positive save capacity required");
        this.capacity = capacity; this.storage = Objects.requireNonNull(storage);
        writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            Thread thread = new Thread(task, "BlueJ source saves"); thread.setDaemon(true); return thread;
        });
    }

    public CompletableFuture<Result> save(Snapshot snapshot)
    {
        Objects.requireNonNull(snapshot);
        CompletableFuture<Result> result = new CompletableFuture<>();
        Result rejected = null;
        synchronized (this)
        {
            if (closing) rejected = failed(snapshot, "Source writer is closed");
            else if (pending >= capacity) rejected = failed(snapshot, "Too many saves are pending. Please try again.");
            else if (!snapshot.owner().reserve(snapshot.source(), snapshot.revision()))
                rejected = cancelled(snapshot);
            else
            {
                pending++;
                try { writer.execute(() -> execute(snapshot, result)); }
                catch (RejectedExecutionException ex) { pending--; rejected = failed(snapshot, "Source writer is unavailable"); }
            }
        }
        // A consumer may reenter the service; never notify while holding its monitor.
        if (rejected != null) result.complete(rejected);
        return result;
    }

    private void execute(Snapshot snapshot, CompletableFuture<Result> completion)
    {
        Result result;
        Throwable fatal = null;
        try { result = write(snapshot); }
        catch (Throwable error)
        {
            IOException failure = error instanceof IOException io ? io : new IOException("Cannot save source", error);
            result = new Result(Outcome.FAILED, snapshot, null, null, -1, -1, failure);
            if (error instanceof Error) fatal = error;
        }
        boolean finished;
        synchronized (this) { pending--; finished = closing && pending == 0; }
        completion.complete(result);
        if (finished) drained.complete(null);
        if (fatal instanceof Error error) throw error;
    }

    private Result write(Snapshot snapshot) throws IOException
    {
        if (snapshot.owner().isRetired()) return cancelled(snapshot);
        if (snapshot.recoveryCopy()) Files.createDirectories(snapshot.source().getParent());
        Path canonical = Files.exists(snapshot.source()) ? snapshot.source().toRealPath()
            : snapshot.source().getParent().toRealPath().resolve(snapshot.source().getFileName());
        FileLock guard;
        synchronized (fileLocks) { guard = fileLocks.computeIfAbsent(canonical, key -> new FileLock()); guard.users++; }
        guard.lock.lock();
        try
        {
            if (snapshot.owner().isRetired()) return cancelled(snapshot);
            String before = read(snapshot);
            Written previous = written.get(canonical);
            boolean ownPreviousWrite = previous != null && previous.owner() == snapshot.owner()
                && previous.revision() <= snapshot.revision() && Objects.equals(before, normalise(previous.text()));
            if (snapshot.checkDiskChanges() && !Objects.equals(before, normalise(snapshot.expectedDiskText())) && !ownPreviousWrite)
                return conflict(snapshot, before);
            try
            {
                storage.save(snapshot, (temporary, target) -> {
                    if (target.equals(canonical) || target.equals(snapshot.source()))
                    {
                        if (snapshot.owner().isRetired()) throw new Retired();
                        // A slow force/copy leaves time for an external edit after the initial check.
                        if (snapshot.checkDiskChanges())
                        {
                            String current = read(snapshot);
                            if (!Objects.equals(current, before)) throw new Conflict(current);
                        }
                    }
                    AtomicSourceSave.replace(temporary, target);
                });
            }
            catch (Conflict conflict) { return conflict(snapshot, conflict.text); }
            catch (Retired retired) { return cancelled(snapshot); }
            written.put(canonical, new Written(snapshot.owner(), snapshot.revision(), snapshot.text()));
            if (snapshot.recoveryCopy() && !Files.readString(snapshot.source(), snapshot.charset()).equals(snapshot.text()))
                throw new IOException("Recovery copy verification failed");
            long modified = -1, length = -1;
            try
            {
                BasicFileAttributes attributes = Files.readAttributes(snapshot.source(), BasicFileAttributes.class);
                modified = attributes.lastModifiedTime().toMillis(); length = attributes.size();
            }
            catch (IOException unavailable) { /* The source commit already succeeded. */ }
            return new Result(Outcome.SAVED, snapshot, before, normalise(snapshot.text()), modified, length, null);
        }
        finally
        {
            guard.lock.unlock();
            synchronized (fileLocks) { if (--guard.users == 0) fileLocks.remove(canonical, guard); }
        }
    }

    private static String read(Snapshot snapshot) throws IOException
    {
        return Files.exists(snapshot.source()) ? normalise(Files.readString(snapshot.source(), snapshot.charset())) : null;
    }

    private static String normalise(String text) { return text == null ? null : text.replace("\r\n", "\n").replace("\r", "\n"); }
    private static Result failed(Snapshot snapshot, String message)
    { return new Result(Outcome.FAILED, snapshot, null, null, -1, -1, new IOException(message)); }
    private static Result cancelled(Snapshot snapshot)
    { return new Result(Outcome.CANCELLED, snapshot, null, null, -1, -1, new IOException("Source save was cancelled")); }
    private static Result conflict(Snapshot snapshot, String disk)
    { return new Result(Outcome.CONFLICT, snapshot, null, disk, -1, -1, new IOException("Source changed on disk")); }

    public synchronized int pendingCount() { return pending; }

    /** Refuse new requests, finish accepted writes, and acknowledge actual drain without blocking the caller. */
    public CompletableFuture<Void> closeAsync()
    {
        boolean finished;
        synchronized (this) { closing = true; writer.shutdown(); finished = pending == 0; }
        if (finished) drained.complete(null);
        return drained;
    }

    @Override public void close() { closeAsync(); }
}
