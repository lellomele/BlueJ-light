/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import bluej.utility.Debug;
import threadchecker.OnThread;
import threadchecker.Tag;

/** One ordered writer prevents an older draft from overtaking a successful save. */
@OnThread(Tag.Any)
public final class HistoryTasks
{
    @FunctionalInterface public interface Job { @OnThread(Tag.Any) void run() throws IOException; }
    private static final Map<Path, Runnable> pendingDrafts = new HashMap<>();
    private static final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(), task -> {
        Thread thread = new Thread(task, "BlueJ local history"); thread.setDaemon(true); return thread;
    });
    static
    {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            writer.shutdown();
            try { writer.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }, "BlueJ history flush"));
    }
    public static void submit(Job job)
    {
        submitAsync(job);
    }

    public static java.util.concurrent.CompletableFuture<Void> submitAsync(Job job)
    {
        java.util.concurrent.CompletableFuture<Void> result = new java.util.concurrent.CompletableFuture<>();
        try
        {
            writer.execute(() -> {
                try { job.run(); result.complete(null); }
                catch (Throwable failure)
                {
                    Debug.reportError("Local history", failure); result.completeExceptionally(failure);
                    if (failure instanceof Error fatal) throw fatal;
                }
            });
        }
        catch (java.util.concurrent.RejectedExecutionException failure) { result.completeExceptionally(failure); Debug.reportError("History writer unavailable", failure); }
        return result;
    }

    /** Replace a queued draft at the tail, never across a saved-source/history barrier. */
    public static void submitDraft(Path source, Job job)
    {
        Path key = source.toAbsolutePath().normalize();
        synchronized (pendingDrafts)
        {
            Runnable previous = pendingDrafts.get(key);
            if (previous != null) writer.remove(previous);
            Runnable task = new Runnable() {
                @Override public void run()
                {
                    synchronized (pendingDrafts) { pendingDrafts.remove(key, this); }
                    HistoryTasks.run(job);
                }
            };
            pendingDrafts.put(key, task);
            enqueue(task);
        }
    }

    private static void enqueue(Runnable task)
    {
        try { writer.execute(task); }
        catch (java.util.concurrent.RejectedExecutionException ex) { Debug.message("History writer is shutting down"); }
    }

    private static void run(Job job)
    {
        try { job.run(); }
        catch (IOException | RuntimeException ex) { Debug.reportError("Local history", ex); }
    }
}
