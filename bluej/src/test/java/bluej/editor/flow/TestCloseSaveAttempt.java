/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestCloseSaveAttempt extends FXTest
{
    private final class Clock implements CloseSaveAttempt.Timer
    {
        bluej.utility.javafx.FXPlatformRunnable expired;boolean cancelled;
        @Override public bluej.utility.javafx.FXPlatformRunnable arm(bluej.utility.javafx.FXPlatformRunnable expired) {this.expired=expired;return () -> cancelled=true;}
    }
    @Test public void timeoutAllowsARecoveryCopyWithoutCancellingTheActualWrite()
    {
        fx_(() -> {
            Clock clock=new Clock();CompletableFuture<Void> write=new CompletableFuture<>();CompletableFuture<Boolean> choice=new CompletableFuture<>();
            var close=CloseSaveAttempt.start(write,() -> true,error -> {assertTrue(error instanceof CloseSaveAttempt.PendingWrite);return choice;},clock,"Still saving");
            clock.expired.run();assertFalse(close.isDone());assertFalse(write.isDone());choice.complete(true);
            assertEquals(CloseSaveAttempt.Outcome.RECOVERY_COPY,close.join());assertFalse(write.isDone());assertTrue(clock.cancelled);
            write.completeExceptionally(new IOException("Late write failure"));assertEquals(CloseSaveAttempt.Outcome.RECOVERY_COPY,close.join());
        });
    }
    @Test public void successfulSaveDuringTheQuestionDoesNotOverrideKeepEditing()
    {
        fx_(() -> {
            Clock clock=new Clock();CompletableFuture<Void> write=new CompletableFuture<>();CompletableFuture<Boolean> choice=new CompletableFuture<>();
            var close=CloseSaveAttempt.start(write,() -> true,error -> choice,clock,"Still saving");clock.expired.run();write.complete(null);
            assertFalse(close.isDone());choice.complete(false);assertEquals(CloseSaveAttempt.Outcome.KEEP_EDITING,close.join());
        });
    }
    @Test public void changedDocumentRejectsAnOldTimerAndNeverOffersACopyForTheNewFile()
    {
        fx_(() -> {
            Clock clock=new Clock();CompletableFuture<Void> write=new CompletableFuture<>();boolean[] current={true};
            var close=CloseSaveAttempt.start(write,() -> current[0],error -> {fail("Obsolete close offered recovery");return null;},clock,"Still saving");
            current[0]=false;clock.expired.run();assertEquals(CloseSaveAttempt.Outcome.KEEP_EDITING,close.join());assertFalse(write.isDone());
        });
    }
    @Test public void fastSaveCancelsTheTimerAndRecoveryFailureStillSettlesClose()
    {
        fx_(() -> {
            Clock clock=new Clock();var success=CloseSaveAttempt.start(CompletableFuture.completedFuture(null),() -> true,error -> {fail();return null;},clock,"Still saving");
            assertEquals(CloseSaveAttempt.Outcome.SAVED,success.join());assertTrue(clock.cancelled);clock.expired.run();
            var failed=CloseSaveAttempt.start(CompletableFuture.failedFuture(new IOException("Disk failure")),() -> true,error -> CompletableFuture.failedFuture(new IOException("Copy failed")),new Clock(),"Still saving");
            assertTrue(failed.isCompletedExceptionally());
        });
    }
}
