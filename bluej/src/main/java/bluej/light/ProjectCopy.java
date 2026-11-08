/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import threadchecker.OnThread;
import threadchecker.Tag;

/** An off-UI project copy. Existing destination files are never replaced or removed on failure. */
@OnThread(Tag.Any)
public final class ProjectCopy
{
    private record Entry(boolean directory, String contents) { }
    private ProjectCopy() { }

    public static void copy(Path source, Path destination) throws IOException
    { copy(source, destination, () -> {}); }

    public static void copy(Path source, Path destination, List<SourceSaveService.Snapshot> buffers) throws IOException
    { copy(source,destination,List.copyOf(buffers),() -> {}); }

    static void copy(Path source, Path destination, Runnable beforeCopy) throws IOException
    { copy(source,destination,List.of(),beforeCopy); }

    private static void copy(Path source, Path destination, List<SourceSaveService.Snapshot> buffers, Runnable beforeCopy) throws IOException
    {
        Path from = source.toRealPath(), to = canonicalDestination(destination);
        if (!Files.isDirectory(from) || to.startsWith(from) || from.startsWith(to))
            throw new IOException("The project copy must be outside the original project directory");
        verifyBuffers(from,from,buffers);
        Map<Path, Entry> original = inventory(from);
        if (Files.exists(to,LinkOption.NOFOLLOW_LINKS))
        {
            if (!Files.isDirectory(to,LinkOption.NOFOLLOW_LINKS)) throw new IOException("The destination is not a directory");
            try (var contents = Files.list(to))
            { if (contents.findAny().isPresent()) throw new IOException("The destination directory is not empty"); }
        }
        else Files.createDirectory(to);
        beforeCopy.run();
        for (var entry : original.entrySet())
        {
            Path target = to.resolve(entry.getKey()), input = from.resolve(entry.getKey());
            if (!target.getParent().toRealPath().startsWith(to)) throw new IOException("The destination directory changed during copying");
            if (entry.getValue().directory()) Files.createDirectory(target);
            else
            {
                Files.copy(input,target,LinkOption.NOFOLLOW_LINKS,StandardCopyOption.COPY_ATTRIBUTES);
                if (!entry.getValue().equals(describe(target))) throw new IOException("A source file changed while it was being copied: " + entry.getKey());
            }
        }
        if (!original.equals(inventory(from)) || !original.equals(inventory(to)))
            throw new IOException("Project files changed during copying; the original project remains open");
        verifyBuffers(from,to,buffers);
    }

    private static void verifyBuffers(Path source, Path copied, List<SourceSaveService.Snapshot> buffers) throws IOException
    {
        for (var buffer : buffers)
        {
            if (!buffer.source().startsWith(source)) continue;
            Path file = copied.resolve(source.relativize(buffer.source()));
            String text = Files.readString(file,buffer.charset()).replace("\r\n","\n").replace('\r','\n');
            if (!buffer.text().equals(text)) throw new IOException("A source file no longer matches its open editor: " + buffer.source().getFileName());
        }
    }

    private static Path canonicalDestination(Path destination) throws IOException
    {
        Path absolute = destination.toAbsolutePath().normalize();
        if (Files.exists(absolute,LinkOption.NOFOLLOW_LINKS))
        {
            if (Files.isSymbolicLink(absolute)) throw new IOException("The destination must not be a symbolic link");
            return absolute.toRealPath();
        }
        if (absolute.getParent() == null) throw new IOException("Invalid destination directory");
        return absolute.getParent().toRealPath().resolve(absolute.getFileName());
    }

    private static Map<Path, Entry> inventory(Path root) throws IOException
    {
        Map<Path, Entry> entries = new TreeMap<>();
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
            {
                if (!directory.equals(root)) entries.put(root.relativize(directory),new Entry(true,""));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
            { entries.put(root.relativize(file),describe(file));return FileVisitResult.CONTINUE; }
        });
        return entries;
    }

    private static Entry describe(Path file) throws IOException
    {
        if (Files.isSymbolicLink(file)) return new Entry(false,"link:" + Files.readSymbolicLink(file));
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsupported project file: " + file);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        byte[] buffer = new byte[64 * 1024];
        try (var input = Files.newInputStream(file))
        { for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer,0,read); }
        return new Entry(false,HexFormat.of().formatHex(digest.digest()));
    }
}
