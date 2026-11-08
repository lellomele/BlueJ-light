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
        default CompletableFuture<Void> savedAsync(SourceSaveService.Result result)
        { saved(result); return CompletableFuture.completedFuture(null); }
        default CompletableFuture<Void> savedAsync(SourceSaveService.Result result, boolean interactive) { return savedAsync(result); }
        default void acknowledgementFailed(SourceSaveService.Result result, Throwable failure) { failed(asIOException(failure)); }
        void failed(IOException failure);
        CompletableFuture<ConflictChoice> decideConflict();
        void conflictFinished();
        default void cancelConflict() { conflictFinished(); }
        void reloaded(SourceSaveService.DiskState state);
        default CompletableFuture<Void> reloadedAsync(SourceSaveService.DiskState state)
        { reloaded(state);return CompletableFuture.completedFuture(null); }
        default CompletableFuture<Void> reloadedAsync(SourceSaveService.DiskState state, boolean interactive) { return reloadedAsync(state); }
    }

    private static final class Attempt
    {
        final SourceSaveService.Snapshot snapshot;
        final CompletableFuture<SourceSaveService.Result> io;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        boolean legacy, handled, interactive;
        Attempt(SourceSaveService.Snapshot snapshot, CompletableFuture<SourceSaveService.Result> io)
        { this.snapshot = snapshot; this.io = io; }
    }

    private final View view;
    private SourceSaveService.Owner owner = new SourceSaveService.Owner();
    private Path source;
    private long appliedRevision = Long.MIN_VALUE;
    private final Map<Long, Attempt> attempts = new HashMap<>();
    private final Map<CompletableFuture<Void>, Attempt> requests = new IdentityHashMap<>();
    private CompletableFuture<Void> conflict;
    private Attempt acknowledging;
    private long fileLifetime;

    FlowSourceSaver(View view) { this.view = view; }

    String ownerId() { return owner.id(); }
    long lifetime() { return fileLifetime; }
    SourceSaveService.Owner owner() { return owner; }
    boolean pending() { return requests.keySet().stream().anyMatch(request -> !request.isDone()); }

    /** Changing/reloading the file retires requests, but never pretends an active disk write has stopped. */
    void reset()
    {
        reset(null,true);
    }

    private void reset(Attempt retained, boolean newLifetime)
    {
        if (newLifetime) fileLifetime++;
        acknowledging = null;
        owner.retire(); owner = new SourceSaveService.Owner(); source = null; appliedRevision = Long.MIN_VALUE;
        List<CompletableFuture<Void>> old = requests.keySet().stream().filter(request -> retained == null || request != retained.done).toList();
        requests.values().stream().filter(Objects::nonNull).filter(attempt -> attempt != retained).forEach(attempt -> attempt.handled = true);
        requests.clear(); attempts.clear();
        if (retained != null) requests.put(retained.done,retained);
        boolean hadConflict = conflict != null && (retained == null || conflict != retained.done); conflict = null;
        JavaFXUtil.runAfterCurrent(() -> {
            old.forEach(request -> request.completeExceptionally(new IOException("Source editor changed")));
        });
        if (hadConflict) view.cancelConflict();
    }

    /** Adopt an inspected disk version; its path/model changes form part of this request too. */
    CompletableFuture<Void> reload(SourceSaveService.DiskState disk)
    {
        State current = view.state();
        Attempt attempt = new Attempt(new SourceSaveService.Snapshot(owner,current.revision(),current.source(),disk.text(),
            current.charset(),disk.text(),current.checkDiskChanges(),false),CompletableFuture.completedFuture(null));
        track(attempt.done,attempt);adoptReload(attempt,disk);return attempt.done;
    }

    private void adoptReload(Attempt attempt, SourceSaveService.DiskState disk)
    {
        reset(attempt,false);acknowledging = attempt;
        long lifetime = fileLifetime;
        SourceSaveService.Result result = new SourceSaveService.Result(SourceSaveService.Outcome.SAVED,attempt.snapshot,null,
            disk.text(),disk.modifiedMillis(),disk.byteLength(),null);
        try
        {
            Objects.requireNonNull(view.reloadedAsync(disk,attempt.interactive)).whenComplete((ignored, failure) -> JavaFXUtil.runNowOrLater(() ->
                finishAcknowledgement(attempt,result,lifetime,failure)));
        }
        catch (RuntimeException | LinkageError | AssertionError failure) { finishAcknowledgement(attempt,result,lifetime,failure); }
        catch (Error fatal) { attempt.done.completeExceptionally(fatal);throw fatal; }
    }

    /** An acknowledged move changes the writer identity, retaining requests for this same document. */
    void pathChanged()
    {
        owner.retire(); owner = new SourceSaveService.Owner(); source = null; appliedRevision = Long.MIN_VALUE;
    }

    CompletableFuture<Void> saveAsync() { return saveAsync(true); }

    /** Background error checks must never ask a modal question or reopen a cancelled decision. */
    CompletableFuture<Void> saveAsync(boolean interactive)
    {
        State state = view.state();
        Attempt current = requests.values().stream().filter(Objects::nonNull)
            .filter(attempt -> attempt.snapshot.owner() == owner && attempt.snapshot.revision() == state.revision() && !attempt.done.isDone()).findFirst().orElse(null);
        if (current != null) { current.interactive |= interactive; return current.done; }
        if (acknowledging != null && !acknowledging.done.isDone()) return afterConflict(acknowledging.done, interactive);
        if (conflict != null && conflict.isDone()) conflict = null;
        if (conflict != null)
        {
            Attempt sameRevision = attempts.get(state.revision());
            if (sameRevision != null) return sameRevision.done;
            return afterConflict(conflict, interactive);
        }
        if (state.source() == null) return CompletableFuture.completedFuture(null);
        if (!state.modified())
        {
            Attempt active = requests.values().stream().filter(Objects::nonNull).filter(attempt -> !attempt.done.isDone()).findFirst().orElse(null);
            return active == null ? CompletableFuture.completedFuture(null) : afterConflict(active.done, interactive);
        }
        return begin(state, false, interactive).done;
    }

    private CompletableFuture<Void> afterConflict(CompletableFuture<Void> barrier, boolean interactive)
    {
        CompletableFuture<Void> result = new CompletableFuture<>(); track(result, null);
        long expected = fileLifetime;
        barrier.whenComplete((ignored, failure) -> bluej.utility.javafx.FXFuture.run(result, () -> {
            if (failure != null || expected != fileLifetime)
            { result.completeExceptionally(failure != null ? failure : new IOException("Source editor changed")); return; }
            saveAsync(interactive).whenComplete((saved, error) -> bluej.utility.javafx.FXFuture.run(result, () -> {
                if (error == null) result.complete(null); else result.completeExceptionally(error);
            }));
        }));
        return result;
    }

    private void track(CompletableFuture<Void> request, Attempt attempt)
    {
        requests.put(request, attempt);
        request.whenComplete((ignored, failure) -> JavaFXUtil.runNowOrLater(() -> {
            requests.remove(request);
            if (attempt != null) attempts.remove(attempt.snapshot.revision(), attempt);
            if (conflict == request) conflict = null;
        }));
    }

    /** Legacy extension bridge only. Internal UI callers must use saveAsync. */
    void saveBlocking() throws IOException
    {
        if (acknowledging != null && !acknowledging.done.isDone()) throw new IOException("Source metadata or path update is pending");
        if (conflict != null && !conflict.isDone()) throw new IOException("A source conflict decision is pending");
        conflict = null;
        State state = view.state();
        if (state.source() == null || (!state.modified() && attempts.isEmpty())) return;
        Attempt attempt = begin(state, true, true);
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

    private Attempt begin(State state, boolean legacy, boolean interactive)
    {
        Path path = state.source().toAbsolutePath().normalize();
        if (source != null && !source.equals(path)) reset();
        source = path;
        Attempt existing = attempts.get(state.revision());
        if (existing != null) { if (legacy) existing.legacy = true; existing.interactive |= interactive; return existing; }
        SourceSaveService.Snapshot snapshot = new SourceSaveService.Snapshot(owner, state.revision(), path,
            state.text(), state.charset(), state.diskText(), state.checkDiskChanges(), false);
        LocalHistory history = view.history();
        CompletableFuture<SourceSaveService.Result> io = view.service().save(snapshot);
        Attempt attempt = new Attempt(snapshot, io); attempt.legacy = legacy; attempt.interactive = interactive;
        attempts.put(state.revision(), attempt);
        track(attempt.done, attempt);
        io.whenComplete((result, failure) -> {
            if (result != null && result.saved())
                HistoryTasks.submit(HistoryTasks.retainedTextBytes(snapshot.text(),snapshot.expectedDiskText(),result.diskText(),result.previousText()),
                    () -> history.acknowledgeSave(snapshot.source(), result.previousText(), snapshot.text(), snapshot.owner().id(), snapshot.revision()));
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
                long lifetime = fileLifetime;
                try
                {
                    if (attempt.legacy) view.saved(result);
                    else
                    {
                        acknowledging = attempt;
                        Objects.requireNonNull(view.savedAsync(result,attempt.interactive)).whenComplete((ignored, failure) -> JavaFXUtil.runNowOrLater(() ->
                            finishAcknowledgement(attempt, result, lifetime, failure)));
                        return;
                    }
                }
                catch (RuntimeException | LinkageError | AssertionError failure)
                {
                    finishAcknowledgement(attempt, result, lifetime, failure); return;
                }
                catch (Error fatal) { attempt.done.completeExceptionally(fatal); throw fatal; }
            }
            attempt.done.complete(null);
        }
        else fail(attempt, result.failure() != null ? result.failure() : new IOException("Source editor changed"));
    }

    private void finishAcknowledgement(Attempt attempt, SourceSaveService.Result result, long lifetime, Throwable failure)
    {
        attempt.handled = true;
        if (acknowledging == attempt) acknowledging = null;
        if (attempt.done.isDone()) return;
        if (lifetime != fileLifetime) { attempt.done.completeExceptionally(new IOException("Source editor changed")); return; }
        if (failure == null) { attempt.done.complete(null); return; }
        try
        {
            bluej.utility.Debug.reportError("Source-save acknowledgement failed", failure);
            view.acknowledgementFailed(result, failure);
        }
        finally { attempt.done.completeExceptionally(failure); }
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
        if (!attempt.interactive)
        {
            // Neither version has been accepted. Keep the original disk baseline and dirty buffer.
            attempt.handled = true;
            attempt.done.completeExceptionally(new IOException("Source changed on disk; save explicitly to resolve the conflict"));
            return;
        }
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
            view.conflictFinished();
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
                    adoptReload(attempt,disk);
                }));
            }
            else
            {
                // Keep local text, but recheck the disk version explicitly accepted by the user.
                State current = view.state();
                State retry = new State(current.source(), current.text(), current.charset(), current.revision(), result.diskText(), true, true);
                Attempt newer = begin(retry, false, true);
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
