/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.util.concurrent.Executors;
import bluej.utility.Debug;
import threadchecker.OnThread;
import threadchecker.Tag;

/** One ordered writer prevents an older draft from overtaking a successful save. */
@OnThread(Tag.Any)
public final class HistoryTasks
{
    @FunctionalInterface public interface Job { @OnThread(Tag.Any) void run() throws IOException; }
    private static final java.util.concurrent.ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
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
        writer.execute(() -> { try { job.run(); } catch (IOException ex) { Debug.reportError("Local history", ex); } });
    }
}
