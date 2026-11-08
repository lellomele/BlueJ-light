/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.utility.javafx;

import java.util.concurrent.CompletableFuture;
import threadchecker.OnThread;
import threadchecker.Tag;

/** A dispatched UI callback must settle its owning request even when the callback fails. */
@OnThread(Tag.Any)
public final class FXFuture
{
    private FXFuture() { }
    public static void run(CompletableFuture<?> result, FXPlatformRunnable action)
    {
        try
        {
            JavaFXUtil.runNowOrLater(() -> {
                if (result.isDone()) return;
                try { action.run(); }
                catch (RuntimeException | LinkageError | AssertionError failure) { result.completeExceptionally(failure); }
                catch (Error fatal) { result.completeExceptionally(fatal); throw fatal; }
            });
        }
        catch (RuntimeException unavailable) { result.completeExceptionally(unavailable); }
    }
}
