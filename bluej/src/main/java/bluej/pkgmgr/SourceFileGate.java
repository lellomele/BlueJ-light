/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.pkgmgr;

import bluej.utility.javafx.FXPlatformSupplier;
import bluej.utility.javafx.JavaFXUtil;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import threadchecker.OnThread;
import threadchecker.Tag;

/** File/model changes wait for existing compilers; new compilers wait for those changes. FX-owned. */
@OnThread(Tag.FXPlatform)
final class SourceFileGate
{
    private final FXPlatformSupplier<Boolean> compiling;
    private final Set<CompletableFuture<Void>> changes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<CompletableFuture<Void>> idle = new ArrayList<>();
    SourceFileGate(FXPlatformSupplier<Boolean> compiling) { this.compiling = compiling; }
    boolean pending() { return !changes.isEmpty(); }
    CompletableFuture<Void> settled() { return CompletableFuture.allOf(changes.toArray(CompletableFuture[]::new)); }
    CompletableFuture<Void> begin(CompletableFuture<Void> change)
    {
        if (change.isDone()) throw new IllegalArgumentException("File change already finished");
        changes.add(change);
        change.whenComplete((ignored, failure) -> JavaFXUtil.runNowOrLater(() -> changes.remove(change)));
        if (!compiling.get()) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> result = new CompletableFuture<>(); idle.add(result); return result;
    }
    void compilationChanged()
    {
        if (compiling.get()) return;
        List<CompletableFuture<Void>> ready = List.copyOf(idle); idle.clear();
        ready.forEach(request -> request.complete(null));
    }
}
