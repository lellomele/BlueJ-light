/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.pkgmgr;
import bluej.editor.flow.FXTest;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestSourceFileGate extends FXTest
{
    @Test public void existingCompilerFinishesBeforeMoveAndNewCompilersWaitForItsModelAcknowledgement() throws Exception
    {
        fx_(() -> {
            boolean[] compiling={true};SourceFileGate gate=new SourceFileGate(() -> compiling[0]);CompletableFuture<Void> operation=new CompletableFuture<>();
            var idle=gate.begin(operation);var settled=gate.settled();assertTrue(gate.pending());assertFalse(idle.isDone());assertFalse(settled.isDone());
            gate.compilationChanged();assertFalse(idle.isDone());compiling[0]=false;gate.compilationChanged();
            assertTrue(idle.isDone());assertFalse(settled.isDone());assertTrue(gate.pending());
            operation.complete(null);assertFalse(gate.pending());assertTrue(settled.isDone());
        });
    }
    @Test public void multipleChangesRetainTheirIndividualOutcomesAndAllowRetry() throws Exception
    {
        fx_(() -> {
            SourceFileGate gate=new SourceFileGate(() -> false);CompletableFuture<Void> first=new CompletableFuture<>(),second=new CompletableFuture<>();
            assertTrue(gate.begin(first).isDone());gate.begin(second);var waiting=gate.settled();
            first.completeExceptionally(new IOException("Move rejected"));assertTrue(gate.pending());assertFalse(waiting.isDone());
            second.complete(null);assertTrue(waiting.isCompletedExceptionally());assertFalse(gate.pending());
            CompletableFuture<Void> retry=new CompletableFuture<>();gate.begin(retry);var next=gate.settled();retry.complete(null);assertFalse(next.isCompletedExceptionally());assertTrue(next.isDone());
        });
    }
    @Test public void completionFromWorkerIsAppliedOnTheUiAndReentrantChangesRemainTracked() throws Exception
    {
        AtomicReference<CompletableFuture<Void>> operation=new AtomicReference<>(),replacement=new AtomicReference<>();AtomicReference<SourceFileGate> source=new AtomicReference<>();
        fx_(() -> {
            SourceFileGate gate=new SourceFileGate(() -> false);source.set(gate);operation.set(new CompletableFuture<>());replacement.set(new CompletableFuture<>());
            gate.begin(operation.get());operation.get().whenComplete((ignored,error) -> javafx.application.Platform.runLater(() -> gate.begin(replacement.get())));
        });
        operation.get().complete(null);fx_(() -> {});fx_(() -> {assertTrue(source.get().pending());replacement.get().complete(null);assertFalse(source.get().pending());});
    }
}
