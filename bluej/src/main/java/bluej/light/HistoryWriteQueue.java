/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import threadchecker.OnThread;
import threadchecker.Tag;

/** A bounded FIFO; text retained by an accepted job is counted until the job finishes. */
@OnThread(Tag.Any)
final class HistoryWriteQueue
{
    enum Outcome { WRITTEN, SUPERSEDED }
    record Stats(int pending, long retainedBytes, long rejected, long superseded) { }
    private static final class Item
    {
        final HistoryTasks.Job job;
        final Path key;
        final long bytes;
        final boolean terminal;
        final CompletableFuture<Outcome> completion = new CompletableFuture<>();
        Item(HistoryTasks.Job job, Path key, long bytes, boolean terminal)
        { this.job = job; this.key = key; this.bytes = bytes; this.terminal = terminal; }
    }

    private final int capacity;
    private final long byteCapacity;
    private final Consumer<Throwable> failureHandler;
    private final ArrayDeque<Item> waiting = new ArrayDeque<>();
    private final Map<Path, Item> drafts = new HashMap<>();
    private Thread worker;
    private boolean closing, terminalPending;
    private int pending;
    private long retainedBytes, rejected, superseded;

    HistoryWriteQueue(int capacity, long byteCapacity, Consumer<Throwable> failureHandler)
    {
        if (capacity < 1 || byteCapacity < 1) throw new IllegalArgumentException("Positive history limits required");
        this.capacity = capacity; this.byteCapacity = byteCapacity; this.failureHandler = Objects.requireNonNull(failureHandler);
    }

    CompletableFuture<Outcome> submit(HistoryTasks.Job job, Path draft, long bytes, boolean terminal)
    {
        Objects.requireNonNull(job);
        if (bytes < 0 || (terminal && (draft != null || bytes > 4096))) throw new IllegalArgumentException("Invalid retained payload");
        Path key = draft == null ? null : draft.toAbsolutePath().normalize();
        Item item = new Item(job, key, bytes, terminal), replaced = null;
        IOException failure = null;
        synchronized (this)
        {
            Item previous = key == null ? null : drafts.get(key);
            int replacedCount = previous == null ? 0 : 1;
            long replacedBytes = previous == null ? 0 : previous.bytes;
            if (closing) failure = new IOException("History writer is closed");
            else if (terminal ? terminalPending : pending - (terminalPending ? 1 : 0) - replacedCount >= capacity
                || retainedBytes - replacedBytes + bytes > byteCapacity)
                failure = new IOException("Local history queue is full; the source buffer is retained");
            if (failure != null) rejected++;
            else
            {
                if (previous != null)
                {
                    waiting.remove(previous); pending--; retainedBytes -= previous.bytes;
                    replaced = previous; superseded++;
                }
                waiting.addLast(item); pending++; retainedBytes += bytes;
                if (key != null) drafts.put(key, item);
                if (terminal) terminalPending = true;
                if (worker == null) startWorker();
                notifyAll();
            }
        }
        // Completing a request can reenter this queue. Do not do it under its monitor.
        if (replaced != null) replaced.completion.complete(Outcome.SUPERSEDED);
        if (failure != null) { item.completion.completeExceptionally(failure); report(failure); }
        return item.completion;
    }

    private void startWorker()
    {
        worker = new Thread(this::drain, "BlueJ local history"); worker.setDaemon(true); worker.start();
    }

    private void drain()
    {
        try
        {
            while (true)
            {
                Item item;
                synchronized (this)
                {
                    while (waiting.isEmpty() && !closing)
                        try { wait(); } catch (InterruptedException interrupted) { /* Accepted jobs must settle. */ }
                    if (waiting.isEmpty()) return;
                    item = waiting.removeFirst();
                    if (item.key != null) drafts.remove(item.key, item);
                }
                Throwable failure = null;
                try { item.job.run(); } catch (Throwable unavailable) { failure = unavailable; }
                synchronized (this)
                {
                    pending--; retainedBytes -= item.bytes;
                    if (item.terminal) terminalPending = false;
                    notifyAll();
                }
                if (failure == null) item.completion.complete(Outcome.WRITTEN);
                else { item.completion.completeExceptionally(failure); report(failure); }
                if (failure instanceof Error fatal) throw fatal;
            }
        }
        finally
        {
            synchronized (this)
            {
                worker = null;
                if (!waiting.isEmpty()) startWorker();
                notifyAll();
            }
        }
    }

    private void report(Throwable failure)
    { try { failureHandler.accept(failure); } catch (RuntimeException ignored) { } }

    synchronized Stats stats() { return new Stats(pending, retainedBytes, rejected, superseded); }
    synchronized void shutdown() { closing = true; notifyAll(); }

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
    {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (this)
        {
            while (worker != null)
            {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            return true;
        }
    }
}
