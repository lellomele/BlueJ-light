/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.light;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Local-only stack traces for an unresponsive UI, without source text or network activity. */
@OnThread(Tag.Any)
public final class UiStallMonitor
{
    private static volatile ScheduledExecutorService monitor;
    @OnThread(Tag.FXPlatform)
    public static synchronized void start(Path preferences)
    {
        if (monitor != null) return;
        Thread ui = Thread.currentThread();
        AtomicBoolean pending = new AtomicBoolean(), reported = new AtomicBoolean();
        AtomicLong queuedAt = new AtomicLong();
        monitor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread worker = new Thread(task, "BlueJ local UI diagnostics"); worker.setDaemon(true); return worker;
        });
        monitor.scheduleWithFixedDelay(new Runnable() {
            @Override @OnThread(value=Tag.Worker, ignoreParent=true)
            public void run()
            {
            if (pending.compareAndSet(false, true))
            {
                queuedAt.set(System.nanoTime()); reported.set(false);
                try { Platform.runLater(() -> pending.set(false)); }
                catch (IllegalStateException ex) { stop(); }
            }
            else if (System.nanoTime() - queuedAt.get() >= TimeUnit.SECONDS.toNanos(8) && reported.compareAndSet(false, true))
            {
                StringBuilder trace = new StringBuilder(Instant.now() + " UI heartbeat delayed more than 8 seconds\n");
                trace.append(ui.getName()).append(" ").append(ui.getState()).append('\n');
                for (StackTraceElement frame : ui.getStackTrace()) trace.append("  at ").append(frame).append('\n');
                var bean = java.lang.management.ManagementFactory.getThreadMXBean();
                long[] deadlocked = bean.findDeadlockedThreads();
                if (deadlocked != null)
                    for (var thread : bean.getThreadInfo(deadlocked, 64)) if (thread != null) trace.append(thread).append('\n');
                try
                {
                    Path file = preferences.resolve("bluej-ui-stall.log"); Files.createDirectories(preferences);
                    if (Files.isRegularFile(file) && Files.size(file) > 256 * 1024)
                        Files.move(file, preferences.resolve("bluej-ui-stall.log.1"), StandardCopyOption.REPLACE_EXISTING);
                    Files.writeString(file, trace + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                catch (java.io.IOException ignored) { }
            }
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    public static synchronized void stop()
    { if (monitor != null) { monitor.shutdownNow(); monitor = null; } }
}
