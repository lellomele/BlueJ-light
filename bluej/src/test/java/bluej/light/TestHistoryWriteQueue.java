/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestHistoryWriteQueue
{
    private static void await(CountDownLatch latch) throws IOException
    {
        try { if (!latch.await(5,TimeUnit.SECONDS)) throw new IOException("Test writer was not released"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt();throw new IOException(failure); }
    }
    private static void failed(CompletableFuture<?> request) throws Exception
    {
        try { request.get(5,TimeUnit.SECONDS);fail("Expected a settled failure"); }
        catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); }
    }
    private static void stop(HistoryWriteQueue queue) throws Exception
    { queue.shutdown();assertTrue(queue.awaitTermination(5,TimeUnit.SECONDS));assertEquals(0,queue.stats().pending());assertEquals(0,queue.stats().retainedBytes()); }

    @Test public void thousandsOfSubmissionsAreBoundedAndEveryRequestSettles() throws Exception
    {
        HistoryWriteQueue queue = new HistoryWriteQueue(8,8000,failure -> {});
        CountDownLatch running = new CountDownLatch(1),release = new CountDownLatch(1);
        List<CompletableFuture<HistoryWriteQueue.Outcome>> accepted = new ArrayList<>(),rejected = new ArrayList<>();
        accepted.add(queue.submit(() -> {running.countDown();await(release);},null,1000,false));assertTrue(running.await(5,TimeUnit.SECONDS));
        try
        {
            for(int i=0;i<5000;i++)
            {
                var request = queue.submit(() -> {},null,1000,false);
                (request.isCompletedExceptionally() ? rejected : accepted).add(request);
                assertTrue(queue.stats().pending() <= 8);assertTrue(queue.stats().retainedBytes() <= 8000);
            }
            assertEquals(8,accepted.size());assertEquals(4993,rejected.size());
            for(var request : rejected) failed(request);
        }
        finally {release.countDown();}
        for(var request : accepted) assertEquals(HistoryWriteQueue.Outcome.WRITTEN,request.get(5,TimeUnit.SECONDS));stop(queue);
    }

    @Test public void replacingAQueuedDraftMovesItAfterSaveBarriersAndSettlesItsPredecessor() throws Exception
    {
        HistoryWriteQueue queue = new HistoryWriteQueue(4,10000,failure -> {});
        CountDownLatch running = new CountDownLatch(1),release = new CountDownLatch(1);
        queue.submit(() -> {running.countDown();await(release);},null,1000,false);assertTrue(running.await(5,TimeUnit.SECONDS));
        List<String> order = Collections.synchronizedList(new ArrayList<>());Path source = Path.of("Example.java");
        var old = queue.submit(() -> order.add("old"),source,2000,false);
        var save = queue.submit(() -> order.add("saved"),null,1000,false);
        var latest = queue.submit(() -> order.add("latest"),source,3000,false);
        assertEquals(HistoryWriteQueue.Outcome.SUPERSEDED,old.get(5,TimeUnit.SECONDS));assertEquals(3,queue.stats().pending());assertEquals(5000,queue.stats().retainedBytes());
        release.countDown();save.get(5,TimeUnit.SECONDS);latest.get(5,TimeUnit.SECONDS);stop(queue);assertEquals(List.of("saved","latest"),order);
    }

    @Test public void aTooLargeReplacementDoesNotDiscardTheAcceptedDraft() throws Exception
    {
        HistoryWriteQueue queue = new HistoryWriteQueue(3,3000,failure -> {});
        CountDownLatch running = new CountDownLatch(1),release = new CountDownLatch(1);
        queue.submit(() -> {running.countDown();await(release);},null,1000,false);assertTrue(running.await(5,TimeUnit.SECONDS));
        List<String> order = Collections.synchronizedList(new ArrayList<>());Path source = Path.of("Example.java");
        var old = queue.submit(() -> order.add("preserved"),source,1000,false);
        var rejected = queue.submit(() -> order.add("too large"),source,2500,false);failed(rejected);
        assertFalse(old.isDone());assertEquals(2000,queue.stats().retainedBytes());release.countDown();old.get(5,TimeUnit.SECONDS);stop(queue);assertEquals(List.of("preserved"),order);
    }

    @Test public void oneSmallTerminalBarrierCanFollowAFullQueue() throws Exception
    {
        HistoryWriteQueue queue = new HistoryWriteQueue(2,2000,failure -> {});
        CountDownLatch running = new CountDownLatch(1),release = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        queue.submit(() -> {running.countDown();await(release);order.add("draft");},null,1000,false);assertTrue(running.await(5,TimeUnit.SECONDS));
        queue.submit(() -> order.add("saved"),null,1000,false);
        var barrier = queue.submit(() -> order.add("session finished"),null,1024,true);
        failed(queue.submit(() -> {},null,1,true));failed(queue.submit(() -> {},null,1,false));
        assertEquals(3,queue.stats().pending());release.countDown();barrier.get(5,TimeUnit.SECONDS);stop(queue);assertEquals(List.of("draft","saved","session finished"),order);
    }

    @Test public void failuresAndReentrantCallbacksDoNotStopTheWriter() throws Exception
    {
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        HistoryWriteQueue queue = new HistoryWriteQueue(2,5000,failures::add);
        CountDownLatch running = new CountDownLatch(1),release = new CountDownLatch(1);
        var first = queue.submit(() -> {running.countDown();await(release);throw new IOException("Storage unavailable");},null,1000,false);
        assertTrue(running.await(5,TimeUnit.SECONDS));
        var retry = first.handle((outcome,failure) -> queue.submit(() -> {},null,1000,false)).thenCompose(request -> request);
        release.countDown();failed(first);assertEquals(HistoryWriteQueue.Outcome.WRITTEN,retry.get(5,TimeUnit.SECONDS));stop(queue);assertEquals(1,failures.size());
    }

    @Test public void shutdownRejectsNewJobsButDrainsAllAcceptedWork() throws Exception
    {
        HistoryWriteQueue queue = new HistoryWriteQueue(2,5000,failure -> {});
        CountDownLatch running = new CountDownLatch(1),release = new CountDownLatch(1);
        var first = queue.submit(() -> {running.countDown();await(release);},null,1000,false);assertTrue(running.await(5,TimeUnit.SECONDS));
        var second = queue.submit(() -> {},null,1000,false);queue.shutdown();failed(queue.submit(() -> {},null,1,false));assertFalse(queue.awaitTermination(1,TimeUnit.MILLISECONDS));
        release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);stop(queue);
    }
}
