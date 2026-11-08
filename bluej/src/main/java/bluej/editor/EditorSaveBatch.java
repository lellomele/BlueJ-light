/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor;

import bluej.compiler.CompileType;
import bluej.utility.javafx.JavaFXUtil;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import threadchecker.OnThread;
import threadchecker.Tag;

/** A stable save boundary across editors whose text/dependencies can change while disk work runs. */
@OnThread(Tag.FXPlatform)
public final class EditorSaveBatch
{
    @FunctionalInterface public interface Editors { List<Editor> get(); }
    private EditorSaveBatch() { }

    public static boolean needsSave(Editors editors)
    {
        return editors.get().stream().anyMatch(editor -> editor.isModified() || editor.hasPendingSave());
    }

    public static CompletableFuture<Void> save(Editors editors, CompileType type)
    {
        return save(editors, type, false);
    }

    /** Save other writable files even when one file fails, then report the combined failure. */
    public static CompletableFuture<Void> saveAll(Editors editors)
    {
        return save(editors, null, true);
    }

    private static CompletableFuture<Void> save(Editors editors, CompileType type, boolean continueAfterFailure)
    {
        CompletableFuture<Void> result = new CompletableFuture<>();
        pass(editors, type, continueAfterFailure, 0, result);
        return result;
    }

    private static void pass(Editors editors, CompileType type, boolean continueAfterFailure, int round, CompletableFuture<Void> result)
    {
        if (result.isDone()) return;
        try
        {
            List<Editor> dirty = editors.get().stream().filter(editor -> editor.isModified() || editor.hasPendingSave()).distinct().toList();
            if (dirty.isEmpty()) { result.complete(null); return; }
            if (round >= 32) { result.completeExceptionally(new IOException("Sources kept changing while preparing the operation")); return; }
            next(editors, dirty, 0, type, continueAfterFailure, round, new ArrayList<>(), result);
        }
        catch (RuntimeException | LinkageError | AssertionError failure) { result.completeExceptionally(failure); }
    }

    private static void next(Editors editors, List<Editor> dirty, int index, CompileType type, boolean continueAfterFailure,
        int round, List<Throwable> failures, CompletableFuture<Void> result)
    {
        if (result.isDone()) return;
        if (index == dirty.size())
        {
            if (!failures.isEmpty())
            {
                IOException combined = new IOException("Cannot save all sources", failures.get(0));
                failures.stream().skip(1).forEach(combined::addSuppressed); result.completeExceptionally(combined);
            }
            else JavaFXUtil.runAfterCurrent(() -> pass(editors, type, continueAfterFailure, round + 1, result));
            return;
        }
        try
        {
            Editor editor = dirty.get(index);
            CompletableFuture<Void> save = type == null ? editor.saveAsync() : editor.saveForCompilationAsync(type);
            save.whenComplete((ignored, failure) -> JavaFXUtil.runNowOrLater(() -> {
                if (failure != null && !continueAfterFailure) result.completeExceptionally(failure);
                else
                {
                    if (failure != null) failures.add(failure);
                    next(editors, dirty, index + 1, type, continueAfterFailure, round, failures, result);
                }
            }));
        }
        catch (RuntimeException | LinkageError | AssertionError failure)
        {
            if (!continueAfterFailure) result.completeExceptionally(failure);
            else { failures.add(failure); next(editors, dirty, index + 1, type, true, round, failures, result); }
        }
    }
}
