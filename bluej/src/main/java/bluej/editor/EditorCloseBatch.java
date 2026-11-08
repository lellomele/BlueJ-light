/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor;

import bluej.utility.javafx.JavaFXUtil;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Prepare every buffer before removing any window; recheck edits made during other saves. */
@OnThread(Tag.FXPlatform)
public final class EditorCloseBatch
{
    private EditorCloseBatch() { }

    public static CompletableFuture<Boolean> prepare(EditorSaveBatch.Editors editors)
    {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        pass(editors, 0, result); return result;
    }

    private static void pass(EditorSaveBatch.Editors editors, int round, CompletableFuture<Boolean> result)
    {
        if (result.isDone()) return;
        try
        {
            if (round >= 32) { result.completeExceptionally(new IOException("Sources kept changing during close")); return; }
            List<Editor> pending = editors.get().stream().distinct()
                .filter(editor -> round == 0 || !editor.isPreparedForClose()).toList();
            next(editors, pending, 0, round, result);
        }
        catch (RuntimeException | LinkageError | AssertionError failure) { result.completeExceptionally(failure); }
    }

    private static void next(EditorSaveBatch.Editors editors, List<Editor> pending, int index, int round, CompletableFuture<Boolean> result)
    {
        if (result.isDone()) return;
        try
        {
            if (index == pending.size())
            {
                if (editors.get().stream().allMatch(Editor::isPreparedForClose)) result.complete(true);
                else JavaFXUtil.runAfterCurrent(() -> pass(editors, round + 1, result));
                return;
            }
            pending.get(index).saveForCloseAsync().whenComplete((ready, failure) -> JavaFXUtil.runNowOrLater(() -> {
                if (failure != null) result.completeExceptionally(failure);
                else if (!Boolean.TRUE.equals(ready)) result.complete(false);
                else next(editors, pending, index + 1, round, result);
            }));
        }
        catch (RuntimeException | LinkageError | AssertionError failure) { result.completeExceptionally(failure); }
    }
}
