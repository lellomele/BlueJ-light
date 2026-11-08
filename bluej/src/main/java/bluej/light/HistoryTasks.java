/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import bluej.utility.Debug;
import threadchecker.OnThread;
import threadchecker.Tag;

/** One bounded ordered writer prevents old drafts overtaking saves or exhausting memory on slow storage. */
@OnThread(Tag.Any)
public final class HistoryTasks
{
    @FunctionalInterface public interface Job { @OnThread(Tag.Any) void run() throws IOException; }
    private static final HistoryWriteQueue writer = new HistoryWriteQueue(128, 32L * 1024 * 1024, HistoryTasks::report);
    private static long lastWarning;
    static
    {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            writer.shutdown();
            try { writer.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }, "BlueJ history flush"));
    }
    private HistoryTasks() { }

    /** Conservative estimate of retained UTF-16 text, including a small per-job allowance. */
    public static long retainedTextBytes(String... text)
    {
        long bytes = 1024;
        for (String value : text) if (value != null) bytes += 2L * value.length();
        return bytes;
    }

    public static void submit(Job job) { submitAsync(1024, job); }
    public static void submit(long bytes, Job job) { submitAsync(bytes, job); }
    public static CompletableFuture<Void> submitAsync(Job job) { return submitAsync(1024, job); }
    public static CompletableFuture<Void> submitAsync(long bytes, Job job)
    { return writer.submit(job, null, bytes, false).thenApply(outcome -> null); }

    /** One small reserved slot permits session shutdown to follow all already accepted history jobs. */
    public static CompletableFuture<Void> finishAsync(Job job)
    { return writer.submit(job, null, 1024, true).thenApply(outcome -> null); }

    /** Replace only a queued draft; place its replacement after every intervening saved-source barrier. */
    public static void submitDraft(Path source, Job job) { submitDraft(source, 1024, job); }
    public static void submitDraft(Path source, long bytes, Job job)
    { writer.submit(job, source, bytes, false); }

    private static synchronized void report(Throwable failure)
    {
        long now = System.nanoTime();
        if (lastWarning != 0 && now - lastWarning < TimeUnit.SECONDS.toNanos(10)) return;
        lastWarning = now; Debug.reportError("Local history unavailable; editor text is retained", failure);
    }
}
