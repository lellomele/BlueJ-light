/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.pkgmgr;

import bluej.editor.flow.FXTest;
import bluej.utility.javafx.FXPlatformRunnable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestFrameSaveAttempt extends FXTest
{
    private final class Clock implements FrameSaveAttempt.Timer
    {
        FXPlatformRunnable expired;
        boolean cancelled;
        @Override public FXPlatformRunnable arm(FXPlatformRunnable callback) { expired = callback; return () -> cancelled = true; }
    }

    @Test public void stalledMetadataRefusesCloseButKeepsTheActualWriteOwnedAndRetryable()
    {
        fx_(() -> {
            Clock clock = new Clock();
            CompletableFuture<Boolean> actual = new CompletableFuture<>();
            int[] notices = {0};
            var close = FrameSaveAttempt.await(actual, clock, () -> notices[0]++);
            clock.expired.run();
            assertFalse(close.join());
            assertFalse(actual.isDone());
            assertEquals(1, notices[0]);
            assertTrue(clock.cancelled);
            actual.complete(true);
            assertFalse("A late save must not close windows after a timeout", close.join());
            assertTrue(FrameSaveAttempt.await(actual, new Clock(), () -> fail()).join());
        });
    }

    @Test public void successfulMetadataCancelsTimeoutAndFailureIsNotReportedAsSuccess()
    {
        fx_(() -> {
            Clock clock = new Clock();
            var saved = FrameSaveAttempt.await(CompletableFuture.completedFuture(true), clock, () -> fail());
            assertTrue(saved.join());
            assertTrue(clock.cancelled);
            clock.expired.run();
            assertTrue(saved.join());
            assertFalse(FrameSaveAttempt.await(CompletableFuture.completedFuture(false), new Clock(), () -> fail()).join());
            assertTrue(FrameSaveAttempt.await(CompletableFuture.failedFuture(new IOException("metadata failed")), new Clock(), () -> fail()).isCompletedExceptionally());
        });
    }

    @Test public void failingPendingNoticeStillSettlesCloseWithoutCancellingIo()
    {
        fx_(() -> {
            Clock clock = new Clock();
            CompletableFuture<Boolean> actual = new CompletableFuture<>();
            var close = FrameSaveAttempt.await(actual, clock, () -> { throw new AssertionError("window unavailable"); });
            clock.expired.run();
            assertTrue(close.isCompletedExceptionally());
            assertFalse(actual.isDone());
        });
    }
}
