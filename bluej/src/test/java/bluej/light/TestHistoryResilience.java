/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestHistoryResilience
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void corruptMetadataDoesNotPreventNewSnapshots() throws Exception
    {
        LocalHistory history = new LocalHistory(folder.getRoot().toPath());
        Path source = folder.getRoot().toPath().resolve("Example.java");
        history.snapshot(source, "original", "saved");
        Path directory = history.list(source).getFirst().file().getParent();
        Files.writeString(directory.resolve("broken.properties"), "time=\\u0x00");
        Files.writeString(directory.resolve("oversized.properties"), "x".repeat(70 * 1024));
        history.snapshot(source, "changed", "saved");
        assertEquals(2, history.list(source).size());
        assertEquals("changed", history.read(history.list(source).getFirst()));
    }

    @Test public void damagedLatestTextDoesNotBlockSavingTheNextRevision() throws Exception
    {
        LocalHistory history = new LocalHistory(folder.getRoot().toPath());
        Path source = folder.getRoot().toPath().resolve("Example.java");
        history.snapshot(source, "original", "saved");
        Files.write(history.list(source).getFirst().file(), new byte[] {(byte)0xFF});
        history.snapshot(source, "new valid text", "saved");
        assertEquals("new valid text", history.read(history.list(source).getFirst()));
    }

    @Test public void coalescedDraftsStayAfterSavedSourceBarriers() throws Exception
    {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> complete = new CompletableFuture<>();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        Path source = folder.getRoot().toPath().resolve("Example.java");
        HistoryTasks.submit(() -> {
            running.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        assertTrue(running.await(5, TimeUnit.SECONDS));
        try {
            HistoryTasks.submitDraft(source, () -> order.add("obsolete draft"));
            HistoryTasks.submit(() -> order.add("saved source"));
            HistoryTasks.submitDraft(source, () -> order.add("latest draft"));
            HistoryTasks.submit(() -> complete.complete(null));
        }
        finally { release.countDown(); }
        complete.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("saved source", "latest draft"), order);
    }

    @Test public void unexpectedFailureDoesNotKillTheHistoryWriter() throws Exception
    {
        CompletableFuture<Thread> before = new CompletableFuture<>();
        CompletableFuture<Thread> after = new CompletableFuture<>();
        HistoryTasks.submit(() -> before.complete(Thread.currentThread()));
        HistoryTasks.submit(() -> { throw new IllegalArgumentException("simulated damaged history"); });
        HistoryTasks.submit(() -> after.complete(Thread.currentThread()));
        assertSame(before.get(5, TimeUnit.SECONDS), after.get(5, TimeUnit.SECONDS));
    }
}
