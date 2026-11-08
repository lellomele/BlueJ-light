/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.utility;

import bluej.editor.flow.FXTest;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import threadchecker.OnThread;
import threadchecker.Tag;
import static org.junit.Assert.*;

@OnThread(Tag.Worker)
public class TestImportScannerHandoff extends FXTest
{
    private static final class Scanner extends ImportScanner
    {
        @OnThread(Tag.Any) final AtomicReference<Throwable> problem = new AtomicReference<>();
        @OnThread(Tag.Any) final ClassLoader expected = TestImportScannerHandoff.class.getClassLoader();

        Scanner() { super(null); }
        CompletableFuture<ClassLoader> request() { return requestProjectClassLoader(); }

        @Override @OnThread(Tag.FXPlatform)
        protected ClassLoader projectClassLoader()
        {
            Throwable failure = problem.get();
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
            return expected;
        }
    }

    @Test public void settlesClassLoaderCallbackFailuresAndAllowsNextRequest() throws Exception
    {
        Scanner scanner = new Scanner();
        for (Throwable failure : new Throwable[] {new IllegalStateException("unavailable"), new AssertionError("callback failed"), new LinkageError("unloadable")})
        {
            scanner.problem.set(failure);
            try { scanner.request().get(3, TimeUnit.SECONDS); fail("Expected failed handoff"); }
            catch (ExecutionException expected) { assertSame(failure, expected.getCause()); }
            scanner.problem.set(null);
            assertSame(scanner.expected, scanner.request().get(3, TimeUnit.SECONDS));
        }
    }

    @Test public void closingProjectSettlesRequestAsCancelled() throws Exception
    {
        Scanner scanner = new Scanner();
        scanner.problem.set(new CancellationException("project closed"));
        CompletableFuture<ClassLoader> request = scanner.request();
        try { request.get(3, TimeUnit.SECONDS); fail("Expected cancelled handoff"); }
        catch (CancellationException expected) { assertTrue(request.isDone()); }
        scanner.problem.set(null);
        assertSame(scanner.expected, scanner.request().get(3, TimeUnit.SECONDS));
    }
}
