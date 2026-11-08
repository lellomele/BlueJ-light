/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.utility.javafx.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import threadchecker.OnThread;
import threadchecker.Tag;

/** A slow write remains owned by its writer while the user can keep editing or preserve a copy. */
@OnThread(Tag.FXPlatform)
final class CloseSaveAttempt
{
    enum Outcome { SAVED, RECOVERY_COPY, KEEP_EDITING }
    static final class PendingWrite extends IOException { PendingWrite(String message) { super(message); } }
    @FunctionalInterface interface Recovery { CompletableFuture<Boolean> offer(IOException failure); }
    @FunctionalInterface interface Timer { FXPlatformRunnable arm(FXPlatformRunnable expired); }
    private enum Phase { WRITING, DECIDING, FINISHED }
    private final CompletableFuture<Outcome> result = new CompletableFuture<>();
    private final FXPlatformSupplier<Boolean> current;
    private final Recovery recovery;
    private Phase phase = Phase.WRITING;
    private FXPlatformRunnable cancelTimer;

    static CompletableFuture<Outcome> start(CompletableFuture<Void> writing, FXPlatformSupplier<Boolean> current,
        Recovery recovery, Timer timer, String timeoutMessage)
    {
        CloseSaveAttempt attempt = new CloseSaveAttempt(current,recovery);
        attempt.cancelTimer = timer.arm(() -> attempt.decide(new PendingWrite(timeoutMessage)));
        attempt.result.whenComplete((ignored, error) -> JavaFXUtil.runNowOrLater(() -> {
            attempt.phase = Phase.FINISHED;
            if (attempt.cancelTimer != null) attempt.cancelTimer.run();
        }));
        writing.whenComplete((saved, failure) -> FXFuture.run(attempt.result, () -> {
            if (attempt.phase != Phase.WRITING) return;
            if (!current.get()) { attempt.result.complete(Outcome.KEEP_EDITING); return; }
            if (failure == null) attempt.result.complete(Outcome.SAVED);
            else attempt.decide(failure instanceof IOException io ? io : new IOException("Cannot save source",failure));
        }));
        return attempt.result;
    }
    private CloseSaveAttempt(FXPlatformSupplier<Boolean> current, Recovery recovery) { this.current=current;this.recovery=recovery; }
    private void decide(IOException failure)
    {
        if (phase != Phase.WRITING || result.isDone()) return;
        if (!current.get()) { result.complete(Outcome.KEEP_EDITING); return; }
        phase = Phase.DECIDING;
        if (cancelTimer != null) cancelTimer.run();
        try
        {
            recovery.offer(failure).whenComplete((copy, error) -> FXFuture.run(result, () -> {
                if (error != null) result.completeExceptionally(error);
                else result.complete(current.get() && Boolean.TRUE.equals(copy) ? Outcome.RECOVERY_COPY : Outcome.KEEP_EDITING);
            }));
        }
        catch (RuntimeException | LinkageError | AssertionError unavailable) { result.completeExceptionally(unavailable); }
    }
}
