/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class AtomicSourceSave
{
    private AtomicSourceSave() { }

    @FunctionalInterface
    interface Replacement { void replace(Path temporary, Path target) throws IOException; }

    public static void save(Path source, String text, Charset charset, String backupSuffix) throws IOException
    {
        save(source, text, charset, backupSuffix, AtomicSourceSave::replace);
    }

    static void save(Path source, String text, Charset charset, String backupSuffix, Replacement replacement) throws IOException
    {
        // Encode before touching the files: unmappable characters must never silently become '?'.
        ByteBuffer bytes = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        Path target = source.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) target = target.toRealPath();
        if (Files.exists(target) && (!Files.isRegularFile(target) || !Files.isWritable(target)))
            throw new IOException("Source file is not writable: " + source);
        Path parent = target.getParent();
        Path backup = source.toAbsolutePath().normalize().resolveSibling(source.getFileName() + backupSuffix);
        Path temporary = Files.createTempFile(parent, ".bluej-save-", ".tmp");
        Path backupTemporary = null;
        boolean committed = false;
        boolean backupCreated = false;
        try
        {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
            {
                while (bytes.hasRemaining()) output.write(bytes);
                output.force(true);
            }
            if (Files.exists(target))
            {
                PosixFileAttributeView permissions = Files.getFileAttributeView(target, PosixFileAttributeView.class);
                if (permissions != null) Files.setPosixFilePermissions(temporary, permissions.readAttributes().permissions());
                backupTemporary = Files.createTempFile(backup.getParent(), ".bluej-backup-", ".tmp");
                Files.copy(target, backupTemporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                try (FileChannel saved = FileChannel.open(backupTemporary, StandardOpenOption.WRITE)) { saved.force(true); }
                replacement.replace(backupTemporary, backup);
                backupCreated = true;
            }
            replacement.replace(temporary, target);
            committed = true;
        }
        finally
        {
            deleteTemporary(temporary);
            if (backupTemporary != null) deleteTemporary(backupTemporary);
            // A failed commit retains the previous source and its durable recovery copy.
            if (committed && backupCreated)
            {
                try { Files.deleteIfExists(backup); }
                catch (IOException ignored) { /* The source is saved; a stale backup is harmless. */ }
            }
        }
    }

    private static void deleteTemporary(Path file)
    {
        try { Files.deleteIfExists(file); }
        catch (IOException ex) { bluej.utility.Debug.reportError("Cannot remove temporary save file: " + file, ex); }
    }

    static void replace(Path temporary, Path target) throws IOException
    {
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex)
        {
            // Recovery copy remains available if this filesystem cannot replace atomically.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
