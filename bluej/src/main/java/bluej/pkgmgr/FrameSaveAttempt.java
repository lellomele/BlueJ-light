/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.pkgmgr;

import bluej.utility.javafx.FXFuture;
import bluej.utility.javafx.FXPlatformRunnable;
import java.util.concurrent.CompletableFuture;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Stop waiting to close a window without cancelling or misreporting its real metadata write. */
@OnThread(Tag.FXPlatform)
final class FrameSaveAttempt
{
    @FunctionalInterface interface Timer { FXPlatformRunnable arm(FXPlatformRunnable expired); }

    static CompletableFuture<Boolean> await(CompletableFuture<Boolean> writing, Timer timer, FXPlatformRunnable pendingNotice)
    {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        FXPlatformRunnable cancel = timer.arm(() -> FXFuture.run(result, () -> {
            // A timeout refuses close. The writer keeps ownership of all accepted work.
            pendingNotice.run();
            result.complete(false);
        }));
        result.whenComplete((saved, failure) -> bluej.utility.javafx.JavaFXUtil.runNowOrLater(cancel));
        writing.whenComplete((saved, failure) -> FXFuture.run(result, () -> {
            if (failure != null) result.completeExceptionally(failure);
            else result.complete(Boolean.TRUE.equals(saved));
        }));
        return result;
    }
}
