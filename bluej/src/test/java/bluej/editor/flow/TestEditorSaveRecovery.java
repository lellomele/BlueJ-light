/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.editor.EditorWatcher;
import bluej.parser.InitConfig;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import threadchecker.OnThread;
import threadchecker.Tag;
import static org.junit.Assert.*;

public class TestEditorSaveRecovery extends FXTest
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private FaultyEditor editor;
    private Path source;
    private final AtomicInteger closed = new AtomicInteger();

    @OnThread(Tag.FXPlatform)
    private static final class FaultyEditor extends FlowEditor
    {
        boolean rejectSave = true;
        boolean recoverForClose;
        boolean rejectRecovery;
        Path recoveryCopy;
        FaultyEditor(EditorWatcher watcher)
        {
            super(window -> null, "Example", watcher, null, null, null,
                new ReadOnlyBooleanWrapper(true), false);
        }
        @Override public void save() throws IOException
        {
            if (rejectSave) throw new IOException("simulated save failure");
            super.save();
        }
        @Override protected boolean offerRecoveryForClose(IOException failure)
        {
            if (!recoverForClose) return false;
            try { recoveryCopy = writeRecoveryCopyForClose(); return true; }
            catch (IOException ex) { return false; }
        }
        @Override protected Path writeRecoveryCopyForClose() throws IOException
        {
            if (rejectRecovery) throw new IOException("simulated recovery failure");
            return super.writeRecoveryCopyForClose();
        }
    }

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        Config.loadFXFonts();
        source = folder.getRoot().toPath().resolve("Example.java");
        Files.writeString(source, "class Example {}\n");
        EditorWatcher watcher = (EditorWatcher)Proxy.newProxyInstance(EditorWatcher.class.getClassLoader(),
            new Class<?>[] { EditorWatcher.class }, (object, method, args) -> {
                if (method.getName().equals("closeEvent")) closed.incrementAndGet();
                return method.getReturnType() == boolean.class ? false : null;
            });
        editor = new FaultyEditor(watcher);
        stage.setScene(new Scene(editor, 800, 600));
        stage.show();
        assertTrue(editor.showFile(source.toString(), StandardCharsets.UTF_8, false, null));
    }

    @Test public void failedSaveDoesNotCloseOrDiscardEditableText() throws Exception
    {
        String changed = "class Example { int changed; }\n";
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            document.replaceText(0, document.getLength(), changed);
            assertTrue(editor.isModified());
            assertFalse(editor.saveForClose());
            editor.close();
            assertEquals(0, closed.get());
            assertEquals(changed, document.getFullContent());
            assertTrue(editor.isModified());
            editor.rejectSave = false;
            editor.close();
            assertFalse(editor.isModified());
            assertEquals(1, closed.get());
        });
        assertEquals(changed, Files.readString(source));
    }

    @Test public void recoveryCopyAllowsCloseButNeverBypassesOrdinarySave() throws Exception
    {
        String changed = "class Example { int recovered; }\n";
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            document.replaceText(0, document.getLength(), changed);
            editor.recoverForClose = true;
            assertTrue(editor.saveForClose());
            try { editor.save(); fail("Compilation must not use the stale original"); }
            catch (IOException expected) { }
            editor.close();
            assertEquals(1, closed.get());
        });
        assertEquals(changed, Files.readString(editor.recoveryCopy));
        assertEquals("class Example {}\n", Files.readString(source));
        Files.delete(editor.recoveryCopy);
    }

    @Test public void unavailableRecoveryStorageDoesNotAuthorizeClose() throws Exception
    {
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            document.replaceText(document.getLength(), document.getLength(), "// unsaved\n");
            String changed = document.getFullContent();
            editor.recoverForClose = true;
            editor.rejectRecovery = true;
            assertFalse(editor.saveForClose());
            editor.close();
            assertEquals(0, closed.get());
            assertEquals(changed, document.getFullContent());
        });
    }

    @Test public void deletedRecoveryCopyDoesNotAuthorizeClose() throws Exception
    {
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            document.replaceText(document.getLength(), document.getLength(), "// recovered\n");
            editor.recoverForClose = true;
            assertTrue(editor.saveForClose());
            editor.recoverForClose = false;
            try { Files.delete(editor.recoveryCopy); } catch (IOException ex) { throw new AssertionError(ex); }
            assertFalse(editor.saveForClose());
        });
    }

    @Test public void furtherEditingInvalidatesTheCloseRecoveryCopy() throws Exception
    {
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            document.replaceText(document.getLength(), document.getLength(), "// recovered\n");
            editor.recoverForClose = true;
            assertTrue(editor.saveForClose());
            editor.recoverForClose = false;
            document.replaceText(document.getLength(), document.getLength(), "// newer\n");
            assertFalse(editor.saveForClose());
        });
        Files.delete(editor.recoveryCopy);
    }

    @Test public void failedSaveRetainsUndoAndRedo() throws Exception
    {
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            String original = document.getFullContent();
            int position = original.indexOf("{}");
            document.replaceText(position + 1, position + 1, " int value; ");
            String changed = document.getFullContent();
            assertFalse(editor.saveForClose());
            editor.undoManager.undo();
            assertEquals(original, document.getFullContent());
            editor.undoManager.redo();
            assertEquals(changed, document.getFullContent());
            editor.rejectSave = false;
            assertTrue(editor.saveForClose());
        });
    }

    @Test public void continuousEditingProducesDraftsWithoutWaitingForIdle() throws Exception
    {
        for (int i = 0; i < 24; i++) {
            fx_(() -> {
                var document = editor.getSourcePane().getDocument();
                document.replaceText(document.getLength(), document.getLength(), "// editing\n");
            });
            sleep(100);
        }
        java.util.concurrent.CompletableFuture<Boolean> checkpoint = new java.util.concurrent.CompletableFuture<>();
        bluej.light.HistoryTasks.submit(() -> {
            var history = new bluej.light.LocalHistory(Config.getUserConfigDir().toPath());
            checkpoint.complete(history.recover(source, "class Example {}\n").isPresent());
        });
        assertTrue("Continuous input must not postpone all recovery checkpoints", checkpoint.get(5, java.util.concurrent.TimeUnit.SECONDS));
        fx_(() -> {
            editor.rejectSave = false;
            editor.close();
        });
    }

    @Test public void openingAndSavingPreservesTabsInsideLiterals() throws Exception
    {
        String text = "class Example {\n\tString text = \"a\tb\";\n\tchar value = '\t';\n}\n";
        Files.writeString(source, text.replace("\n", "\r\n"));
        fx_(() -> {
            assertTrue(editor.showFile(source.toString(), StandardCharsets.UTF_8, false, null));
            assertEquals(text, editor.getSourcePane().getDocument().getFullContent());
            var document = editor.getSourcePane().getDocument();
            document.replaceText(document.getLength(), document.getLength(), "// changed\n");
            editor.rejectSave = false;
            assertTrue(editor.saveForClose());
        });
        assertEquals(text + "// changed\n", Files.readString(source));
    }

    private boolean checkDisk(boolean verifyContent)
    {
        try
        {
            var method = FlowEditor.class.getDeclaredMethod("checkForChangeOnDisk", boolean.class);
            method.setAccessible(true);
            return (boolean)method.invoke(editor, verifyContent);
        }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test public void equalTimestampAndLengthStillDetectChangedContent() throws Exception
    {
        var stamp = Files.getLastModifiedTime(source);
        String external = "class Changed {}\n";
        assertEquals(Files.size(source), external.getBytes(StandardCharsets.UTF_8).length);
        Files.writeString(source, external);
        Files.setLastModifiedTime(source, stamp);
        fx_(() -> {
            assertTrue(checkDisk(true));
            assertEquals(external, editor.getSourcePane().getDocument().getFullContent());
            assertFalse(editor.isModified());
        });
    }

    @Test public void olderExternalFileReloadsWithoutMergingCrOnlyLines() throws Exception
    {
        String external = "class Changed {\r\tint value;\r}\r";
        Files.writeString(source, external);
        Files.setLastModifiedTime(source, java.nio.file.attribute.FileTime.fromMillis(1000));
        fx_(() -> {
            assertTrue(checkDisk(false));
            assertEquals(external.replace("\r", "\n"), editor.getSourcePane().getDocument().getFullContent());
        });
    }

    @Test public void unreadableDiskDoesNotDiscardBufferOrAllowOverwrite() throws Exception
    {
        Files.write(source, new byte[] {(byte)0xc3, (byte)0x28});
        fx_(() -> {
            String original = editor.getSourcePane().getDocument().getFullContent();
            assertFalse(checkDisk(true));
            assertEquals(original, editor.getSourcePane().getDocument().getFullContent());
        });
        assertArrayEquals(new byte[] {(byte)0xc3, (byte)0x28}, Files.readAllBytes(source));
    }

    @Test public void nestedSaveCannotProceedWhileConflictDialogIsActive() throws Exception
    {
        fx_(() -> {
            try
            {
                var field = FlowEditor.class.getDeclaredField("showingChangedOnDiskDialog");
                field.setAccessible(true);
                field.setBoolean(editor, true);
                try { assertFalse(checkDisk(true)); assertFalse(editor.saveForClose()); }
                finally { field.setBoolean(editor, false); }
            }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }

    @Test public void renamedSourceRetainsUndoAndSavesOnlyTheNewPath() throws Exception
    {
        Path renamed = source.resolveSibling("Renamed.java");
        Files.copy(source, renamed);
        fx_(() -> {
            var document = editor.getSourcePane().getDocument();
            String original = document.getFullContent();
            document.replaceText(document.getLength(), document.getLength(), "// rename probe\n");
            String changed = document.getFullContent();
            editor.changeName("Renamed", renamed.toString(), renamed.toString(), null);
            editor.undoManager.undo();
            assertEquals(original, document.getFullContent());
            editor.undoManager.redo();
            assertEquals(changed, document.getFullContent());
            editor.rejectSave = false;
            assertTrue(editor.saveForClose());
        });
        assertEquals("class Example {}\n", Files.readString(source));
        assertEquals("class Example {}\n// rename probe\n", Files.readString(renamed));
    }
}
