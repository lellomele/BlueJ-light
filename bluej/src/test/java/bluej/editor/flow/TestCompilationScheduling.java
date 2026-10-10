/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.compiler.CompileReason;
import bluej.compiler.CompileType;
import bluej.editor.EditorWatcher;
import bluej.parser.InitConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestCompilationScheduling extends FXTest
{
    private FlowEditor editor;
    private ControlledScheduler scheduler;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        Config.loadFXFonts();
        scheduler = new ControlledScheduler();
        EditorWatcher watcher = (EditorWatcher)Proxy.newProxyInstance(EditorWatcher.class.getClassLoader(),
            new Class<?>[]{EditorWatcher.class}, (object, method, arguments) -> {
                if (method.getName().equals("scheduleCompilation"))
                    scheduler.post(new Request((Boolean)arguments[0], (CompileReason)arguments[1], (CompileType)arguments[2]));
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        editor = new FlowEditor(window -> null, "Example", watcher, null, null, null,
            new ReadOnlyBooleanWrapper(true), true);
        stage.setScene(new Scene(editor, 900, 600));
        stage.show();
    }

    @Test public void explicitCompileSurvivesTheDeferredTargetQueuedBeforeStartGap()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.fireTimer();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("The user's compile must remain pending after error checking", result.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.current().type());
        assertEquals(2, result.accepted().size());
        assertEquals(CompileType.ERROR_CHECK_ONLY, result.accepted().getFirst().type());
    }

    @Test public void explicitCompileSurvivesTheLoadedTargetQueuedBeforeStartGap()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("An already queued load check cannot swallow an explicit compile", result.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.current().type());
        assertEquals(2, result.accepted().size());
    }

    @Test public void explicitStateFromAnEarlierCycleDoesNotSuppressTheNextRequest()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.start();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.finish(true, false);
            scheduler.start();
            scheduler.finish(true, true);
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("The previous explicit cycle must not suppress a new explicit request", result.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.current().type());
        assertEquals(List.of(CompileType.ERROR_CHECK_ONLY, CompileType.EXPLICIT_USER_COMPILE,
            CompileType.ERROR_CHECK_ONLY, CompileType.EXPLICIT_USER_COMPILE),
            result.accepted().stream().map(Request::type).toList());
    }

    @Test public void laterAutomaticRequestsDoNotDowngradePendingExplicitCompilation()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.start();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            replaceSource("class Example { int value = 1; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            replaceSource("class Example { int value = 2; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull(result.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.current().type());
        assertEquals(CompileReason.USER, result.current().reason());
        assertEquals(2, result.accepted().size());
    }

    @Test public void promotionBeforeTheDeferredTimerDoesNotCompileTwice()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, true);
            return scheduler.snapshot();
        });
        assertNull("A valid explicit result needs no second compile", result.current());
        assertNull("Promotion must cancel the old deferred target", result.deferred());
        assertEquals(1, result.accepted().size());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.accepted().getFirst().type());
    }

    @Test public void validKeptClassesMakePendingAutomaticCompilationRedundant()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.start();
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.finish(true, true);
            return scheduler.snapshot();
        });
        assertNull(result.current());
        assertNull(result.deferred());
        assertEquals(1, result.accepted().size());
    }

    @Test public void secondExplicitRequestSurvivesAStaleExplicitResultBeforeStart()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("A stale explicit result whose classes were discarded cannot satisfy the later request",
            result.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.current().type());
        assertEquals(CompileReason.USER, result.current().reason());
        assertEquals(2, result.accepted().size());
        assertTrue("The later explicit request must wait rather than be rejected by the queued target",
            result.dropped().isEmpty());
    }

    @Test public void automaticRequestSurvivesAStaleExplicitResultBeforeStart()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            replaceSource("class Example { int value = 2; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("Discarded stale classes cannot make the newer automatic error check redundant",
            result.current());
        assertEquals(CompileType.ERROR_CHECK_ONLY, result.current().type());
        assertEquals(CompileReason.MODIFIED, result.current().reason());
        assertEquals(2, result.accepted().size());
        assertTrue(result.dropped().isEmpty());
    }

    @Test public void explicitRequestDuringAnExternalCompilationWaitsForItsStaleResult()
    {
        Snapshot result = fx(() -> {
            scheduler.post(new Request(true, CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE));
            scheduler.dispatch();
            scheduler.start();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("An externally started compilation with discarded stale classes must preserve the editor request",
            result.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, result.current().type());
        assertEquals(CompileReason.USER, result.current().reason());
        assertEquals(2, result.accepted().size());
        assertTrue("An editor request during an external compilation must wait until the target is unqueued",
            result.dropped().isEmpty());
    }

    @Test public void automaticEditsSurviveTheDeferredTargetQueuedBeforeStartGap()
    {
        Snapshot result = fx(() -> {
            replaceSource("class Example { int value = 1; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            markSaved();
            scheduler.fireTimer();
            replaceSource("class Example { int value = 2; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.fireTimer();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("The queued target's stale result cannot swallow the newer dirty source check",
            result.current());
        assertEquals(CompileType.ERROR_CHECK_ONLY, result.current().type());
        assertEquals(CompileReason.MODIFIED, result.current().reason());
        assertEquals(2, result.accepted().size());
        assertEquals("The second timer fires while the old target remains queued", 1, result.dropped().size());
        assertEquals(CompileType.ERROR_CHECK_ONLY, result.dropped().getFirst().type());
        assertTrue(fx(editor::isModified));
    }

    @Test public void duplicateLoadedRequestsBeforeStartDoNotCompileAgain()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNull("Repeated load checks for the same saved revision need only one error-check result",
            result.current());
        assertNull(result.deferred());
        assertEquals(1, result.accepted().size());
        assertTrue(result.dropped().isEmpty());
    }

    @Test public void duplicateLoadedRequestsDuringCompilationDoNotCompileAgain()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.start();
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNull("A running load check already covers a duplicate request for its unchanged saved revision",
            result.current());
        assertNull(result.deferred());
        assertEquals(1, result.accepted().size());
        assertTrue(result.dropped().isEmpty());
    }

    @Test public void savedFreshDebouncedEditsDoNotRequireAnotherAutomaticCompilation()
    {
        Snapshot result = fx(() -> {
            replaceSource("class Example { int value = 1; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            replaceSource("class Example { int value = 2; }");
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            markSaved();
            scheduler.fireTimer();
            scheduler.start();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNull("The compiler starts from the latest saved debounced source, so its error check is sufficient",
            result.current());
        assertNull(result.deferred());
        assertEquals(1, result.accepted().size());
        assertTrue(result.dropped().isEmpty());
        assertFalse(fx(editor::isModified));
    }

    @Test public void aNewSavedRevisionDuringCompilationStillNeedsAutomaticChecking()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.start();
            replaceSource("class Example { int value = 2; }");
            markSaved();
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNotNull("A saved reload after compilation started is a new revision not covered by the stale result",
            result.current());
        assertEquals(CompileType.ERROR_CHECK_ONLY, result.current().type());
        assertEquals(CompileReason.MODIFIED, result.current().reason());
        assertEquals(2, result.accepted().size());
        assertTrue(result.dropped().isEmpty());
        assertFalse(fx(editor::isModified));
    }

    private void replaceSource(String source)
    {
        Document document = editor.getSourcePane().getDocument();
        document.replaceText(0, document.getLength(), source);
    }

    /** Models ClassTarget.ensureSaved before the compiler starts reading its input. */
    private void markSaved()
    {
        try
        {
            Field saveState = FlowEditor.class.getDeclaredField("saveState");
            saveState.setAccessible(true);
            ((StatusLabel)saveState.get(editor)).setState(StatusLabel.Status.SAVED);
        }
        catch (ReflectiveOperationException ex)
        {
            throw new AssertionError("Cannot model saving the compiler's source snapshot", ex);
        }
    }

    @Test public void cancellationReleasesPendingRequestsWithoutAnAutomaticRetry()
    {
        Snapshot result = fx(() -> {
            request(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            scheduler.dispatch();
            scheduler.fireTimer();
            request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            scheduler.dispatch();
            editor.compileCancelled();
            scheduler.finish(true, false);
            return scheduler.snapshot();
        });
        assertNull(result.current());
        Snapshot next = fx(() -> { request(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE); scheduler.dispatch(); return scheduler.snapshot(); });
        assertNotNull(next.current());
        assertEquals(CompileType.EXPLICIT_USER_COMPILE, next.current().type());
    }

    private void request(CompileReason reason, CompileType type)
    {
        editor.scheduleCompilation(reason, type);
    }

    private record Request(boolean immediate, CompileReason reason, CompileType type) { }
    private record Snapshot(List<Request> accepted, List<Request> dropped, Request current, Request deferred) { }

    /** Models Project.runLater, its timer, and Package.searchCompile's queued-target rejection. */
    private class ControlledScheduler
    {
        private final List<Request> posted = new ArrayList<>();
        private final List<Request> accepted = new ArrayList<>();
        private final List<Request> dropped = new ArrayList<>();
        private Request deferred;
        private Request current;
        private int sequence;

        void post(Request request)
        {
            posted.add(request);
        }

        void dispatch()
        {
            List<Request> work = List.copyOf(posted);
            posted.clear();
            for (Request request : work)
            {
                if (request.immediate())
                {
                    deferred = null;
                    enqueue(request);
                }
                else
                    deferred = request;
            }
        }

        void fireTimer()
        {
            Request request = deferred;
            deferred = null;
            enqueue(request);
        }

        private void enqueue(Request request)
        {
            if (current != null)
                dropped.add(request);
            else
            {
                current = request;
                accepted.add(request);
            }
        }

        void start()
        {
            editor.compileStarted(++sequence);
        }

        void finish(boolean successful, boolean classesKept)
        {
            editor.compileFinished(successful, classesKept);
            // ClassTarget is unqueued after notifying the editor; Project dispatch follows later.
            current = null;
            dispatch();
        }

        Snapshot snapshot()
        {
            return new Snapshot(List.copyOf(accepted), List.copyOf(dropped), current, deferred);
        }
    }
}
