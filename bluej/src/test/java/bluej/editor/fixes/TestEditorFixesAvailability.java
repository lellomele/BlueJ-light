/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.fixes;

import bluej.parser.AssistContentThreadSafe;
import bluej.parser.AssistContent.CompletionKind;
import bluej.pkgmgr.target.role.Kind;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import threadchecker.OnThread;
import threadchecker.Tag;
import static org.junit.Assert.*;

@OnThread(Tag.Worker)
public class TestEditorFixesAvailability
{
    @Test(timeout=2000) public void pendingProjectInformationDoesNotDelayAvailableImports()
    {
        CompletableFuture<ProjectImportInformation> information = new CompletableFuture<>();
        EditorFixesManager manager = new EditorFixesManager(information);
        CompletableFuture<List<AssistContentThreadSafe>> pending = new CompletableFuture<>();
        AssistContentThreadSafe known = new AssistContentThreadSafe(null, null, null, CompletionKind.TYPE,
            "ReadyType", "example", null, List.of(), null, Kind.CLASS_NON_FINAL);
        manager.getImportedTypesFutureList().add(pending);
        manager.getImportedTypesFutureList().add(CompletableFuture.completedFuture(List.of(known)));
        assertEquals(List.of(known), manager.getAllImportedTypes().toList());
        assertTrue(manager.getImportSuggestions().isEmpty());
        assertTrue(manager.getJavaLangImports().isEmpty());
        assertFalse(information.isDone());
        assertFalse(pending.isDone());
        pending.complete(List.of(known));
        assertEquals(2, manager.getAllImportedTypes().count());
    }

    @Test(timeout=2000) public void failedProjectInformationSettlesWaitingScan() throws Exception
    {
        CompletableFuture<ProjectImportInformation> information = new CompletableFuture<>();
        EditorFixesManager manager = new EditorFixesManager(information);
        Future<List<AssistContentThreadSafe>> scan = manager.scanImports("example.*");
        assertFalse(scan.isDone());
        information.completeExceptionally(new AssertionError("initialization failed"));
        assertTrue(scan.get(1, TimeUnit.SECONDS).isEmpty());
        assertTrue(manager.getAllImportedTypes().toList().isEmpty());
        assertTrue(manager.getImportSuggestions().isEmpty());
    }

    @Test(timeout=2000) public void cancelledProjectInformationDoesNotDelayLaterScans() throws Exception
    {
        CompletableFuture<ProjectImportInformation> information = new CompletableFuture<>();
        EditorFixesManager manager = new EditorFixesManager(information);
        information.cancel(false);
        assertTrue(manager.scanImports("example.*").get(1, TimeUnit.SECONDS).isEmpty());
        assertTrue(manager.getJavaLangImports().isEmpty());
        assertTrue(manager.getImportSuggestions().isEmpty());
    }
}
