/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestProjectTypeIndex extends FXTest
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static Field field(String name) throws Exception {
        Field field = ProjectTypeIndex.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void awaitScan(ProjectTypeIndex index) throws Exception {
        Field pending = field("pending");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(() -> {
            try { return pending.get(index) != null; }
            catch (IllegalAccessException ex) { throw new RuntimeException(ex); }
        })) {
            assertTrue("Index scan did not finish", System.nanoTime() < deadline);
            Thread.sleep(10);
        }
    }

    private static final class WorkerBlock implements AutoCloseable {
        final CountDownLatch release = new CountDownLatch(1);
        final Future<?> task;
        WorkerBlock() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            ExecutorService worker = (ExecutorService)field("WORKER").get(null);
            task = worker.submit(() -> {
                started.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            assertTrue("Worker did not start", started.await(5, TimeUnit.SECONDS));
        }
        public void close() throws Exception {
            release.countDown();
            task.get(5, TimeUnit.SECONDS);
        }
    }

    @Test public void cachedSnapshotsDoNotWaitForRefreshIncludingEmptyProjects() throws Exception {
        for (boolean populated : List.of(false, true)) {
            var root = folder.newFolder().toPath();
            if (populated) Files.writeString(root.resolve("Before.java"), "public class Before {}\n");
            ProjectTypeIndex index = fx(() -> new ProjectTypeIndex(root, StandardCharsets.UTF_8));
            fx_(() -> index.whenReady(() -> { }));
            awaitScan(index);
            AtomicInteger calls = new AtomicInteger();
            Field lastScan = field("lastScan"), pending = field("pending"), waiting = field("waiting");
            try (WorkerBlock block = new WorkerBlock()) {
                Files.writeString(root.resolve("After.java"), "public class After {}\n");
                fx_(() -> {
                    try {
                        lastScan.setLong(index, 0);
                        index.whenReady(calls::incrementAndGet);
                        index.whenReady(calls::incrementAndGet);
                        assertEquals("Cached callback must run synchronously", 2, calls.get());
                        assertNotNull("Refresh must still be scheduled", pending.get(index));
                        assertTrue(((List<?>)waiting.get(index)).isEmpty());
                        assertFalse(index.snapshot().stream().anyMatch(t -> t.name().equals("After")));
                    } catch (IllegalAccessException ex) { throw new RuntimeException(ex); }
                });
            }
            awaitScan(index);
            fx_(() -> {
                assertTrue(index.snapshot().stream().anyMatch(t -> t.name().equals("After")));
                assertEquals("Refresh must not call cached listeners again", 2, calls.get());
                index.close();
            });
        }
    }

    @Test public void coldStartHasBoundedWaitAndDeliversOnlyOnce() throws Exception {
        var root = folder.newFolder().toPath();
        Files.writeString(root.resolve("Example.java"), "class Example {}\n");
        ProjectTypeIndex index = fx(() -> new ProjectTypeIndex(root, StandardCharsets.UTF_8));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch callback = new CountDownLatch(1);
        try (WorkerBlock block = new WorkerBlock()) {
            fx_(() -> {
                index.whenReady(() -> { calls.incrementAndGet(); callback.countDown(); });
                assertEquals("Cold start should wait for the first snapshot", 0, calls.get());
            });
            assertTrue("Cold callback must not wait indefinitely", callback.await(3, TimeUnit.SECONDS));
            fx_(() -> assertTrue(index.snapshot().isEmpty()));
        }
        awaitScan(index);
        fx_(() -> {
            assertEquals(1, calls.get());
            assertEquals("Example", index.snapshot().get(0).name());
            index.close();
        });
    }

    @Test public void closeDuringColdScanDoesNotRepublishTypesOrLoseCallback() throws Exception {
        var root = folder.newFolder().toPath();
        Files.writeString(root.resolve("Example.java"), "class Example {}\n");
        ProjectTypeIndex index = fx(() -> new ProjectTypeIndex(root, StandardCharsets.UTF_8));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch callback = new CountDownLatch(1);
        try (WorkerBlock block = new WorkerBlock()) {
            fx_(() -> {
                index.whenReady(() -> { calls.incrementAndGet(); callback.countDown(); });
                index.close();
            });
        }
        awaitScan(index);
        assertTrue(callback.await(3, TimeUnit.SECONDS));
        fx_(() -> {
            assertTrue(index.snapshot().isEmpty());
            assertEquals(1, calls.get());
            index.whenReady(calls::incrementAndGet);
            assertEquals(2, calls.get());
        });
    }
}
