/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.compiler;

import bluej.classmgr.BPClassLoader;
import bluej.parser.InitConfig;
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public class TestCompilerJobs
{
    private Job job(Compiler compiler, CompileObserver observer)
    {
        InitConfig.init();
        return new Job(new CompileInputFile[0], compiler, observer,
            new BPClassLoader(new URL[0], getClass().getClassLoader()), null, false, List.of(),
            StandardCharsets.UTF_8, CompileType.ERROR_CHECK_ONLY, CompileReason.MODIFIED);
    }

    private Compiler compiler(Runnable action)
    {
        return new Compiler() {
            @Override public boolean compile(File[] sources, CompileObserver observer, boolean internal,
                List<String> options, Charset charset, CompileType type) { action.run(); return true; }
        };
    }

    @Test public void endCallbackIsNotRepeatedWhenItThrows()
    {
        AtomicInteger ends = new AtomicInteger();
        job(compiler(() -> {}), new Observer() {
            @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) {
                ends.incrementAndGet(); throw new IllegalStateException("synthetic end failure");
            }
        }).compile();
        assertEquals(1, ends.get());
    }

    @Test public void compilerAssertionStillCompletesWithFailure()
    {
        AtomicInteger ends = new AtomicInteger();
        job(compiler(() -> { throw new AssertionError("synthetic compiler failure"); }), new Observer() {
            @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) {
                assertFalse(success); ends.incrementAndGet();
            }
        }).compile();
        assertEquals(1, ends.get());
    }

    @Test public void failedStartDoesNotInvokeCompilerButEndsOnce()
    {
        AtomicInteger calls = new AtomicInteger(), ends = new AtomicInteger();
        job(compiler(calls::incrementAndGet), new Observer() {
            @Override public void startCompile(CompileInputFile[] sources, CompileReason reason, CompileType type, int sequence) {
                throw new IllegalStateException("synthetic start failure");
            }
            @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) {
                assertFalse(success); ends.incrementAndGet();
            }
        }).compile();
        assertEquals(0, calls.get()); assertEquals(1, ends.get());
    }

    @Test public void remainingJobsRunAfterACompilerFailure() throws Exception
    {
        CountDownLatch finished = new CountDownLatch(1);
        CompilerThread worker = new CompilerThread();
        worker.setDaemon(true);
        worker.addJob(job(compiler(() -> { throw new AssertionError("synthetic failure"); }), new Observer()));
        worker.addJob(job(compiler(() -> {}), new Observer() {
            @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) {
                if (success) finished.countDown();
            }
        }));
        worker.start();
        assertTrue("The second job must finish", finished.await(5, TimeUnit.SECONDS));
        assertTrue(worker.isAlive());
    }

    @Test public void manualIntentCannotBeDowngradedByOtherRequests()
    {
        CompileRequest manual = new CompileRequest(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
        for (CompileType type : CompileType.values()) {
            CompileRequest merged = manual.merge(new CompileRequest(CompileReason.MODIFIED, type));
            assertEquals(CompileType.EXPLICIT_USER_COMPILE, merged.type());
        }
        for (CompileType type : CompileType.values())
            assertEquals(manual, new CompileRequest(CompileReason.LOADED, type).merge(manual));
    }

    @Test public void separateTargetsRetainTheirOwnIntent()
    {
        var pending = new java.util.LinkedHashMap<String, CompileRequest>();
        pending.merge("A", new CompileRequest(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE), CompileRequest::merge);
        pending.merge("B", new CompileRequest(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY), CompileRequest::merge);
        pending.merge("A", new CompileRequest(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY), CompileRequest::merge);
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, pending.get("A").type());
        assertEquals(CompileType.ERROR_CHECK_ONLY, pending.get("B").type());
    }

    private static class Observer implements CompileObserver
    {
        @Override public void startCompile(CompileInputFile[] sources, CompileReason reason, CompileType type, int sequence) { }
        @Override public void compilerMessage(Diagnostic diagnostic, CompileType type) { }
        @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) { }
    }
}
