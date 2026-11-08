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
        private boolean relocating;
        public String id() { return id; }
        public void retire() { retired.set(true); }
        public boolean isRetired() { return retired.get(); }
        private synchronized boolean reserve(Path source, long revision)
        {
            if (relocating || retired.get() || (path != null && !path.equals(source)) || revision < lastAcceptedRevision) return false;
            path = source; lastAcceptedRevision = revision; return true;
        }
        private synchronized boolean beginRelocation(Path source, long revision)
        {
            if (!reserve(source, revision)) return false;
            relocating = true; return true;
        }
        private synchronized void finishRelocation(boolean moved)
        {
            if (moved) retired.set(true);
            relocating = false;
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

    public record DiskState(Path source, String text, long modifiedMillis, long byteLength, boolean exists) { }

    public record Relocation(DiskState disk, IOException historyWarning) { }
    public record Removal(List<Path> recoveryCopies, IOException historyWarning)
    { public Removal { recoveryCopies = List.copyOf(recoveryCopies); } }

    /** Retire the editor before calling. Preserve verified copies before unlinking the primary source. */
    public CompletableFuture<Removal> removeSource(Path path, Snapshot buffer, Path recoveryDirectory)
    { return removeSource(path,buffer,recoveryDirectory,null); }

    public CompletableFuture<Removal> removeSource(Path path, Snapshot buffer, Path recoveryDirectory, LocalHistory history)
    { return removeFile(path,buffer,recoveryDirectory,history,true); }

    public CompletableFuture<Removal> removeTextFile(Path path, Snapshot buffer, Path recoveryDirectory, LocalHistory history)
    { return removeFile(path,buffer,recoveryDirectory,history,false); }

    private CompletableFuture<Removal> removeFile(Path path, Snapshot buffer, Path recoveryDirectory, LocalHistory history, boolean javaSource)
    {
        Path source = path.toAbsolutePath().normalize(), recovery = recoveryDirectory.toAbsolutePath().normalize();
        if (source.getParent() == null || source.getFileName() == null || (javaSource && !source.getFileName().toString().endsWith(".java")))
            return CompletableFuture.failedFuture(new IOException("File removal requires a regular source path"));
        if (buffer != null && !source.equals(buffer.source()))
            return CompletableFuture.failedFuture(new IOException("Removal buffer belongs to another source"));
        if (buffer != null && !buffer.owner().isRetired())
            return CompletableFuture.failedFuture(new IOException("The source editor must be retired before removing its file"));
        return submit(() -> {
            Path canonical = Files.exists(source) ? source.toRealPath() : source.getParent().toRealPath().resolve(source.getFileName());
            FileLock guard;
            synchronized (fileLocks) { guard = fileLocks.computeIfAbsent(canonical,key -> new FileLock());guard.users++; }
            guard.lock.lock();
            try
            {
                Charset charset = buffer == null ? java.nio.charset.StandardCharsets.UTF_8 : buffer.charset();
                boolean existed = Files.exists(source);
                String before = existed && buffer != null ? normalise(Files.readString(source,charset)) : null;
                if (buffer != null && buffer.checkDiskChanges() && before != null && !Objects.equals(before,normalise(buffer.expectedDiskText())))
                    throw new IOException("Source changed on disk before removal");
                List<Path> copies = new ArrayList<>();
                Path diskCopy = null;
                if (existed || buffer != null) Files.createDirectories(recovery);
                if (existed)
                {
                    Path copy = recovery.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID() + "-removed-" + source.getFileName());
                    Files.copy(source,copy);
                    try (var channel = java.nio.channels.FileChannel.open(copy,StandardOpenOption.WRITE)) { channel.force(true); }
                    if (Files.mismatch(source,copy) != -1) throw new IOException("Source removal recovery copy could not be verified");
                    copies.add(copy);diskCopy = copy;
                }
                if (buffer != null && !Objects.equals(before,normalise(buffer.text())))
                {
                    Path copy = recovery.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID() + "-removed-buffer-" + source.getFileName());
                    AtomicSourceSave.save(copy,buffer.text(),java.nio.charset.StandardCharsets.UTF_8,".backup");
                    if (!Files.readString(copy,java.nio.charset.StandardCharsets.UTF_8).equals(buffer.text()))
                        throw new IOException("Source buffer recovery copy could not be verified");
                    copies.add(copy);
                }
                if (diskCopy == null ? Files.exists(source) : !Files.exists(source) || Files.mismatch(source,diskCopy) != -1)
                    throw new IOException("Source changed while preserving it for removal");
                Files.deleteIfExists(source);written.remove(canonical);
                IOException warning = null;
                try { if (history != null) history.retirePath(source); }
                catch (IOException unavailable) { warning = unavailable; }
                return new Removal(copies,warning);
            }
            finally
            {
                guard.lock.unlock();
                synchronized (fileLocks) { if (--guard.users == 0) fileLocks.remove(canonical,guard); }
            }
        });
    }

    /** The history intent precedes the physical move; a crash can resolve its identity at the new name. */
    public CompletableFuture<Relocation> relocate(Snapshot saved, Path destination, LocalHistory history)
    { return relocateWithHistory(saved,destination,history,null); }

    /** Cross-package moves are confined to the same explicitly supplied project root. */
    public CompletableFuture<Relocation> movePackage(Snapshot saved, Path destination, Path projectRoot, LocalHistory history)
    {
        if (!saved.source().getFileName().equals(destination.getFileName()))
            return CompletableFuture.failedFuture(new IOException("A package move must retain the Java class filename"));
        return relocateWithHistory(saved,destination,history,Objects.requireNonNull(projectRoot).toAbsolutePath().normalize());
    }

    private CompletableFuture<Relocation> relocateWithHistory(Snapshot saved, Path destination, LocalHistory history, Path projectRoot)
    {
        Path target = destination.toAbsolutePath().normalize();
        return submit(() -> {
            HistoryPaths.Move change = history.preparePathChange(saved.source(), target, projectRoot);
            DiskState state;
            try { state = relocateNow(saved, target, projectRoot); }
            catch (IOException | RuntimeException failure)
            {
                try { history.abortPathChange(change); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            IOException warning = null;
            try { history.commitPathChange(change); } catch (IOException unavailable) { warning = unavailable; }
            // An already moved source must always be acknowledged at its actual destination.
            return new Relocation(state, warning);
        });
    }

    /** A path change consumes the old writer identity; the UI must create a fresh identity after acknowledgement. */
    public CompletableFuture<DiskState> relocate(Snapshot saved, Path destination)
    {
        Path target = destination.toAbsolutePath().normalize();
        return submit(() -> relocateNow(saved, target));
    }

    private DiskState relocateNow(Snapshot saved, Path target) throws IOException
    { return relocateNow(saved,target,null); }

    private DiskState relocateNow(Snapshot saved, Path target, Path projectRoot) throws IOException
    {
        Path source = saved.source();
        if (saved.recoveryCopy() || source.getFileName() == null || target.getFileName() == null
            || !source.getFileName().toString().endsWith(".java") || !target.getFileName().toString().endsWith(".java")
            || source.getParent() == null || target.getParent() == null)
            throw new IOException("Class relocation requires Java source paths");
        Path fromDirectory = source.getParent().toRealPath(), toDirectory = target.getParent().toRealPath();
        if (projectRoot == null ? !fromDirectory.equals(toDirectory)
            : !fromDirectory.startsWith(projectRoot.toRealPath()) || !toDirectory.startsWith(projectRoot.toRealPath()))
            throw new IOException("Class relocation leaves its package or project");
        if (!saved.owner().beginRelocation(source, saved.revision())) throw new IOException("Source identity changed before renaming");
        boolean moved = false;
        List<Map.Entry<Path, FileLock>> locks = new ArrayList<>();
        int acquired = 0;
        try
        {
            Path realSource = source.toRealPath();
            Path realTarget = Files.exists(target) ? target.toRealPath() : target.getParent().toRealPath().resolve(target.getFileName());
            List<Path> keys = java.util.stream.Stream.of(realSource, realTarget).distinct().sorted(Comparator.comparing(Path::toString)).toList();
            synchronized (fileLocks)
            {
                for (Path key : keys)
                {
                    FileLock guard = fileLocks.computeIfAbsent(key, ignored -> new FileLock()); guard.users++;
                    locks.add(Map.entry(key, guard));
                }
            }
            for (var entry : locks) { entry.getValue().lock.lock(); acquired++; }
            if (saved.owner().isRetired()) throw new IOException("Source editor changed before renaming");
            if (!Files.isRegularFile(source) || !Objects.equals(read(saved), normalise(saved.text())))
                throw new IOException("Source content changed before renaming");
            boolean sameSpelling = source.toString().equals(target.toString());
            boolean caseOnly = fromDirectory.equals(toDirectory) && !sameSpelling && source.getFileName().toString().equalsIgnoreCase(target.getFileName().toString())
                && Files.exists(target) && Files.isSameFile(source, target);
            if (Files.exists(target) && !sameSpelling && !caseOnly) throw new FileAlreadyExistsException(target.toString());
            if (saved.owner().isRetired()) throw new IOException("Source editor changed before renaming");
            // ATOMIC_MOVE is restricted to the same physical file: on Windows it otherwise permits replacement.
            if (caseOnly) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            else if (!sameSpelling) Files.move(source, target);
            moved = true;
            written.remove(realSource);
            written.remove(realTarget);
            long modified = -1, length = -1;
            try
            {
                BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class);
                modified = attributes.lastModifiedTime().toMillis(); length = attributes.size();
            }
            catch (IOException unavailable) { /* The move already committed. */ }
            return new DiskState(target, normalise(saved.text()), modified, length, true);
        }
        finally
        {
            try
            {
                for (int i = acquired - 1; i >= 0; i--) locks.get(i).getValue().lock.unlock();
            }
            finally
            {
                synchronized (fileLocks)
                {
                    for (var entry : locks) if (--entry.getValue().users == 0) fileLocks.remove(entry.getKey(), entry.getValue());
                }
                saved.owner().finishRelocation(moved);
            }
        }
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

    /** Content and metadata checks share the ordered worker; callers must not perform them on FX. */
    public CompletableFuture<DiskState> inspect(Path path, Charset charset)
    {
        Path source = path.toAbsolutePath().normalize();
        return submit(() -> {
            if (!Files.exists(source)) return new DiskState(source, null, -1, 0, false);
            String text = normalise(Files.readString(source, charset));
            BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
            return new DiskState(source, text, attributes.lastModifiedTime().toMillis(), attributes.size(), true);
        });
    }

    @FunctionalInterface public interface IOOperation<T> { @OnThread(Tag.Any) T run() throws IOException; }

    /** Ordered project-file operations; capture immutable data only, never controls or editor objects. */
    public <T> CompletableFuture<T> submit(IOOperation<T> operation)
    {
        Objects.requireNonNull(operation);
        CompletableFuture<T> result = new CompletableFuture<>();
        boolean rejected;
        synchronized (this)
        {
            rejected = closing || pending >= capacity;
            if (!rejected)
            {
                pending++;
                try
                {
                    writer.execute(() -> {
                        T state = null; Throwable failure = null;
                        try { state = operation.run(); }
                        catch (Throwable error) { failure = error; }
                        boolean finished;
                        synchronized (SourceSaveService.this) { pending--; finished = closing && pending == 0; }
                        if (failure == null) result.complete(state); else result.completeExceptionally(failure);
                        if (finished) drained.complete(null);
                        if (failure instanceof Error error) throw error;
                    });
                }
                catch (RejectedExecutionException failure) { pending--; rejected = true; }
            }
        }
        if (rejected) result.completeExceptionally(new IOException("Source writer is unavailable"));
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
