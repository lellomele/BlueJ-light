/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.utility;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import threadchecker.OnThread;
import threadchecker.Tag;
import static org.junit.Assert.*;

@OnThread(Tag.Worker)
public class TestImportScannerResilience
{
    private static class FaultyScanner extends ImportScanner
    {
        final AtomicInteger attempts = new AtomicInteger();
        final boolean transientFailure;
        volatile boolean daemon;
        FaultyScanner(boolean transientFailure) { super(null); this.transientFailure = transientFailure; }
        @Override protected RootPackageInfo findAllTypes()
        {
            daemon = Thread.currentThread().isDaemon();
            int attempt = attempts.incrementAndGet();
            if (!transientFailure || attempt == 1) throw new IllegalStateException("simulated scan failure");
            return new RootPackageInfo();
        }
    }

    private static CompletableFuture<?> future(ImportScanner scanner) throws Exception
    {
        Field root = ImportScanner.class.getDeclaredField("root");
        root.setAccessible(true);
        return (CompletableFuture<?>)root.get(scanner);
    }

    private static void allowRetry(ImportScanner scanner) throws Exception
    {
        Field delay = ImportScanner.class.getDeclaredField("retryAfterNanos");
        delay.setAccessible(true);
        delay.setLong(scanner, 0);
    }

    private static void expectFailure(CompletableFuture<?> future) throws Exception
    {
        try { future.get(3, TimeUnit.SECONDS); fail("Missing scan failure"); }
        catch (ExecutionException ex) { assertTrue(ex.getCause() instanceof IllegalStateException); }
    }

    @Test public void completesFailedRequestsAndCanRecoverFromTransientFailures() throws Exception
    {
        FaultyScanner scanner = new FaultyScanner(true);
        scanner.startScanning();
        expectFailure(future(scanner));
        assertTrue(scanner.daemon);
        allowRetry(scanner);
        scanner.startScanning();
        assertNotNull(future(scanner).get(3, TimeUnit.SECONDS));
        assertEquals(2, scanner.attempts.get());
    }

    @Test public void limitsAutomaticRetriesInsteadOfStartingAnEndlessLoop() throws Exception
    {
        FaultyScanner scanner = new FaultyScanner(false);
        for (int attempt = 0; attempt < 3; attempt++) {
            allowRetry(scanner);
            scanner.startScanning();
            expectFailure(future(scanner));
        }
        CompletableFuture<?> last = future(scanner);
        allowRetry(scanner);
        scanner.startScanning();
        assertSame(last, future(scanner));
        assertEquals(3, scanner.attempts.get());
    }

    @Test public void preservesInterruptionWhenWaitingForAnIndex() throws Exception
    {
        ImportScanner scanner = new ImportScanner(null);
        Field root = ImportScanner.class.getDeclaredField("root");
        root.setAccessible(true);
        root.set(scanner, new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            assertTrue(scanner.getImportedTypes("java.lang.*").isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally { Thread.interrupted(); }
    }

    @Test public void savingUnusedCacheDoesNotStartAnUnnecessaryScan() throws Exception
    {
        FaultyScanner scanner = new FaultyScanner(false);
        scanner.saveCachedImports();
        assertNull(future(scanner));
        assertEquals(0, scanner.attempts.get());
    }
}
