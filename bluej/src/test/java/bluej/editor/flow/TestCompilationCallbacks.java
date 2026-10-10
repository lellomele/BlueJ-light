/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.compiler.*;
import bluej.parser.InitConfig;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestCompilationCallbacks extends FXTest
{
    @Override public void start(Stage stage) throws Exception { super.start(stage); InitConfig.init(); }

    @Test public void callbackOnUiRunsDirectlyInsteadOfWaitingForItself()
    {
        AtomicInteger starts = new AtomicInteger(), ends = new AtomicInteger();
        EventqueueCompileObserverAdapter adapter = new EventqueueCompileObserverAdapter(new Observer() {
            @Override public void startCompile(CompileInputFile[] sources, CompileReason reason, CompileType type, int sequence) { starts.incrementAndGet(); }
            @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) { ends.incrementAndGet(); }
        });
        fx_(() -> { adapter.startCompile(new CompileInputFile[0], CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE, 1);
            adapter.endCompile(new CompileInputFile[0], true, CompileType.EXPLICIT_USER_COMPILE, 1); });
        assertEquals(1, starts.get()); assertEquals(1, ends.get());
    }

    @Test public void nestedUiCallbackDoesNotTakeTheWorkersMonitor() throws Exception
    {
        AtomicInteger nested = new AtomicInteger();
        EventqueueCompileObserverAdapter[] adapter = new EventqueueCompileObserverAdapter[1];
        adapter[0] = new EventqueueCompileObserverAdapter(new Observer() {
            @Override public void startCompile(CompileInputFile[] sources, CompileReason reason, CompileType type, int sequence) {
                adapter[0].compilerMessage(null, type);
            }
            @Override public boolean compilerMessage(Diagnostic diagnostic, CompileType type) { nested.incrementAndGet(); return false; }
        });
        CompletableFuture<Void> done = CompletableFuture.runAsync(() -> adapter[0].startCompile(
            new CompileInputFile[0], CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE, 2));
        done.get(5, TimeUnit.SECONDS);
        assertEquals(1, nested.get());
    }

    @Test public void queueWaitOnUiFailsFast()
    {
        JobQueue queue = JobQueue.getJobQueue();
        boolean rejected = fx(() -> {
            try { queue.waitForEmptyQueue(); return false; }
            catch (IllegalStateException expected) { return true; }
        });
        assertTrue(rejected);
    }

    private static class Observer implements FXCompileObserver
    {
        @Override public void startCompile(CompileInputFile[] sources, CompileReason reason, CompileType type, int sequence) { }
        @Override public boolean compilerMessage(Diagnostic diagnostic, CompileType type) { return false; }
        @Override public void endCompile(CompileInputFile[] sources, boolean success, CompileType type, int sequence) { }
    }
}
