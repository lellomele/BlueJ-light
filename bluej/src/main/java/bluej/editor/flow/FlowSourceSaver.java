/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.light.*;
import bluej.utility.javafx.JavaFXUtil;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import threadchecker.OnThread;
import threadchecker.Tag;

/** UI-side lifetime and acknowledgement of source writes; the service owns all disk operations. */
@OnThread(Tag.FXPlatform)
final class FlowSourceSaver
{
    record State(Path source, String text, Charset charset, long revision, String diskText,
                 boolean modified, boolean checkDiskChanges) { }
    enum ConflictChoice { RELOAD, KEEP_LOCAL, CANCEL }

    interface View
    {
        State state();
        SourceSaveService service();
        LocalHistory history();
        void saved(SourceSaveService.Result result);
        void failed(IOException failure);
        CompletableFuture<ConflictChoice> decideConflict();
        void conflictFinished();
        void reloaded(SourceSaveService.DiskState state);
    }

    private static final class Attempt
    {
        final SourceSaveService.Snapshot snapshot;
        final CompletableFuture<SourceSaveService.Result> io;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        boolean legacy, handled;
        Attempt(SourceSaveService.Snapshot snapshot, CompletableFuture<SourceSaveService.Result> io)
        { this.snapshot = snapshot; this.io = io; }
    }

    private final View view;
    private SourceSaveService.Owner owner = new SourceSaveService.Owner();
    private Path source;
    private long appliedRevision = Long.MIN_VALUE;
    private final Map<Long, Attempt> attempts = new HashMap<>();
    private CompletableFuture<Void> conflict;

    FlowSourceSaver(View view) { this.view = view; }

    String ownerId() { return owner.id(); }
    boolean pending() { return !attempts.isEmpty(); }

    /** Changing/reloading the file retires requests, but never pretends an active disk write has stopped. */
    void reset()
    {
        owner.retire(); owner = new SourceSaveService.Owner(); source = null; appliedRevision = Long.MIN_VALUE;
        List<Attempt> old = List.copyOf(attempts.values()); attempts.clear();
        CompletableFuture<Void> oldConflict = conflict; conflict = null;
        old.forEach(attempt -> attempt.handled = true);
        JavaFXUtil.runAfterCurrent(() -> {
            old.forEach(attempt -> attempt.done.completeExceptionally(new IOException("Source editor changed")));
            if (oldConflict != null) oldConflict.completeExceptionally(new IOException("Source editor changed"));
        });
    }

    CompletableFuture<Void> saveAsync()
    {
        if (conflict != null) return conflict;
        State state = view.state();
        if (state.source() == null || (!state.modified() && attempts.isEmpty())) return CompletableFuture.completedFuture(null);
        return begin(state, false).done;
    }

    /** Legacy extension bridge only. Internal UI callers must use saveAsync. */
    void saveBlocking() throws IOException
    {
        if (conflict != null) throw new IOException("A source conflict decision is pending");
        State state = view.state();
        if (state.source() == null || (!state.modified() && attempts.isEmpty())) return;
        Attempt attempt = begin(state, true);
        attempt.legacy = true;
        try
        {
            SourceSaveService.Result result = attempt.io.get();
            if (!attempt.handled) finish(attempt, result);
            if (!result.saved()) throw result.failure();
            try { attempt.done.join(); }
            catch (CompletionException failure) { throw asIOException(failure); }
        }
        catch (InterruptedException interrupted)
        {
            attempt.legacy = false;
            Thread.currentThread().interrupt(); throw new IOException("Save interrupted", interrupted);
        }
        catch (ExecutionException failure) { IOException io = asIOException(failure); fail(attempt, io); throw io; }
    }

    private Attempt begin(State state, boolean legacy)
    {
        Path path = state.source().toAbsolutePath().normalize();
        if (source != null && !source.equals(path)) reset();
        source = path;
        Attempt existing = attempts.get(state.revision());
        if (existing != null) { if (legacy) existing.legacy = true; return existing; }
        SourceSaveService.Snapshot snapshot = new SourceSaveService.Snapshot(owner, state.revision(), path,
            state.text(), state.charset(), state.diskText(), state.checkDiskChanges(), false);
        LocalHistory history = view.history();
        CompletableFuture<SourceSaveService.Result> io = view.service().save(snapshot);
        Attempt attempt = new Attempt(snapshot, io); attempt.legacy = legacy;
        attempts.put(state.revision(), attempt);
        io.whenComplete((result, failure) -> {
            if (result != null && result.saved())
                HistoryTasks.submit(() -> history.acknowledgeSave(snapshot.source(), result.previousText(), snapshot.text(), snapshot.owner().id(), snapshot.revision()));
            dispatch(attempt, () -> {
                if (attempt.legacy || attempt.handled) return;
                if (failure != null) fail(attempt, asIOException(failure));
                else if (snapshot.owner() != owner) fail(attempt, new IOException("Source editor changed"));
                else if (result.outcome() == SourceSaveService.Outcome.CONFLICT) resolveConflict(attempt, result);
                else finish(attempt, result);
            });
        });
        return attempt;
    }

    private void finish(Attempt attempt, SourceSaveService.Result result)
    {
        if (attempt.handled) return;
        if (result.saved() && attempt.snapshot.owner() == owner)
        {
            attempt.handled = true; attempts.remove(attempt.snapshot.revision(), attempt);
            if (attempt.snapshot.revision() >= appliedRevision)
            {
                appliedRevision = attempt.snapshot.revision();
                try { view.saved(result); }
                catch (RuntimeException | LinkageError | AssertionError failure)
                {
                    bluej.utility.Debug.reportError("Source-save acknowledgement failed", failure);
                    attempt.done.completeExceptionally(failure); return;
                }
                catch (Error fatal) { attempt.done.completeExceptionally(fatal); throw fatal; }
            }
            attempt.done.complete(null);
        }
        else fail(attempt, result.failure() != null ? result.failure() : new IOException("Source editor changed"));
    }

    private void fail(Attempt attempt, IOException failure)
    {
        if (attempt.handled) return;
        attempt.handled = true; attempts.remove(attempt.snapshot.revision(), attempt);
        try { view.failed(failure); }
        finally { attempt.done.completeExceptionally(failure); }
    }

    private void resolveConflict(Attempt attempt, SourceSaveService.Result result)
    {
        conflict = attempt.done;
        CompletableFuture<ConflictChoice> decision;
        try { decision = Objects.requireNonNull(view.decideConflict()); }
        catch (RuntimeException failure)
        {
            conflict = null;
            try { view.conflictFinished(); } finally { fail(attempt, asIOException(failure)); }
            return;
        }
        decision.whenComplete((choice, error) -> dispatch(attempt, () -> {
            view.conflictFinished(); conflict = null;
            if (attempt.snapshot.owner() != owner || error != null || choice == null || choice == ConflictChoice.CANCEL)
            { fail(attempt, new IOException("Source save cancelled")); return; }
            attempts.remove(attempt.snapshot.revision(), attempt);
            if (choice == ConflictChoice.RELOAD)
            {
                SourceSaveService service = view.service();
                service.inspect(attempt.snapshot.source(), attempt.snapshot.charset()).whenComplete((disk, failure) -> dispatch(attempt, () -> {
                    State current = view.state();
                    if (failure != null || disk == null || !disk.exists() || attempt.snapshot.owner() != owner
                        || current.revision() != attempt.snapshot.revision())
                    { fail(attempt, new IOException("Cannot reload changed source", failure)); return; }
                    try { view.reloaded(disk); attempt.handled = true; attempt.done.complete(null); }
                    catch (RuntimeException unavailable) { attempt.done.completeExceptionally(unavailable); }
                }));
            }
            else
            {
                // Keep local text, but recheck the disk version explicitly accepted by the user.
                State current = view.state();
                State retry = new State(current.source(), current.text(), current.charset(), current.revision(), result.diskText(), true, true);
                Attempt newer = begin(retry, false);
                newer.done.whenComplete((ignored, failure) -> JavaFXUtil.runNowOrLater(() -> {
                    attempt.handled = true;
                    if (failure == null) attempt.done.complete(null); else attempt.done.completeExceptionally(failure);
                }));
            }
        }));
    }

    private void dispatch(Attempt attempt, bluej.utility.javafx.FXPlatformRunnable action)
    {
        JavaFXUtil.runNowOrLater(() -> {
            if (attempt.done.isDone()) return;
            try { action.run(); }
            catch (RuntimeException | LinkageError | AssertionError failure)
            {
                conflict = null;
                try { view.conflictFinished(); } finally { fail(attempt, asIOException(failure)); }
            }
            catch (Error fatal)
            {
                attempt.handled = true; attempts.remove(attempt.snapshot.revision(), attempt);
                attempt.done.completeExceptionally(fatal); throw fatal;
            }
        });
    }

    private static IOException asIOException(Throwable failure)
    {
        while ((failure instanceof ExecutionException || failure instanceof CompletionException) && failure.getCause() != null)
            failure = failure.getCause();
        return failure instanceof IOException io ? io : new IOException("Cannot save source", failure);
    }
}
