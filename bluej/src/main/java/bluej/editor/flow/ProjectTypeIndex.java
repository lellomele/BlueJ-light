/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.lexer.JavaLexer;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import javax.lang.model.SourceVersion;
import javafx.util.Duration;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Filename/header metadata only: no editors, class loading or source writes. */
@OnThread(Tag.FXPlatform)
public final class ProjectTypeIndex
{
    @OnThread(Tag.Any) public record Type(Path source, String pkg, String name, boolean publicType) {
        public String qualifiedName() { return pkg.isEmpty() ? name : pkg + "." + name; }
    }
    @OnThread(Tag.Any) private record Cached(long modified, long size, Type type) { }
    @OnThread(Tag.Any) private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BlueJ project type names"); thread.setDaemon(true); return thread;
    });
    @OnThread(Tag.Any) private final Path root;
    @OnThread(Tag.Any) private final Charset charset;
    @OnThread(Tag.Any) private volatile List<Type> types = List.of();
    @OnThread(Tag.Any) private volatile boolean closed;
    @OnThread(Tag.Any) private Map<Path, Cached> headers = Map.of();
    private CompletableFuture<List<Type>> pending;
    private final List<FXPlatformRunnable> waiting = new ArrayList<>();
    private long lastScan;
    private boolean snapshotReady;

    public ProjectTypeIndex(Path root, Charset charset) { this.root = root; this.charset = charset; }
    public List<Type> snapshot() { return types; }
    public void close() { closed = true; types = List.of(); }

    public void whenReady(FXPlatformRunnable callback)
    {
        if (closed) { callback.run(); return; }
        if (pending == null && (lastScan == 0 || System.nanoTime() - lastScan > TimeUnit.SECONDS.toNanos(5))) {
            lastScan = System.nanoTime();
            pending = CompletableFuture.supplyAsync(this::scan, WORKER);
            CompletableFuture<List<Type>> scan = pending;
            scan.whenComplete((result, failure) -> JavaFXUtil.runNowOrLater(() -> {
                if (!closed && failure == null) {
                    types = result;
                    snapshotReady = true;
                }
                if (pending == scan) pending = null;
                for (FXPlatformRunnable listener : List.copyOf(waiting)) listener.run();
            }));
        }
        // A refresh must not delay completion when a previous snapshot is usable.
        if (pending == null || snapshotReady) { callback.run(); return; }
        boolean[] delivered = {false};
        FXPlatformRunnable[] once = {null}, cancelTimeout = {() -> { }};
        once[0] = () -> {
            if (!delivered[0]) {
                delivered[0] = true; waiting.remove(once[0]); cancelTimeout[0].run(); callback.run();
            }
        };
        waiting.add(once[0]);
        cancelTimeout[0] = JavaFXUtil.runAfter(Duration.millis(150), once[0]);
    }

    @OnThread(Tag.Worker)
    private List<Type> scan()
    {
        Map<Path, Cached> refreshed = new HashMap<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            Files.walkFileTree(root, Set.of(), 32, new SimpleFileVisitor<>() {
                @Override @OnThread(value=Tag.Worker, ignoreParent=true)
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (closed || System.nanoTime() > deadline || refreshed.size() >= 10000) return FileVisitResult.TERMINATE;
                    if (!dir.equals(root) && (!validName(dir.getFileName().toString())
                        || !(Files.isRegularFile(dir.resolve("package.bluej")) || Files.isRegularFile(dir.resolve("bluej.pkg")))))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override @OnThread(value=Tag.Worker, ignoreParent=true)
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (closed || System.nanoTime() > deadline || refreshed.size() >= 10000) return FileVisitResult.TERMINATE;
                    String filename = file.getFileName().toString();
                    if (!attrs.isRegularFile() || !filename.endsWith(".java")) return FileVisitResult.CONTINUE;
                    String name = filename.substring(0, filename.length() - 5);
                    if (!validName(name)) return FileVisitResult.CONTINUE;
                    Cached old = headers.get(file);
                    if (old != null && old.modified() == attrs.lastModifiedTime().toMillis() && old.size() == attrs.size())
                        refreshed.put(file, old);
                    else {
                        String pkg = root.relativize(file.getParent()).toString().replace(File.separatorChar, '.');
                        Type type = new Type(file, pkg, name, publicHeader(file, name, charset));
                        refreshed.put(file, new Cached(attrs.lastModifiedTime().toMillis(), attrs.size(), type));
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override @OnThread(value=Tag.Worker, ignoreParent=true)
                public FileVisitResult visitFileFailed(Path file, IOException error) { return FileVisitResult.CONTINUE; }
            });
        } catch (IOException | SecurityException ex) {
            bluej.utility.Debug.message("Project type-name scan incomplete: " + ex.getClass().getSimpleName());
        }
        headers = refreshed;
        return refreshed.values().stream().map(Cached::type).sorted(Comparator.comparing(Type::qualifiedName)).toList();
    }

    @OnThread(Tag.Any) static boolean validName(String name) {
        return name != null && SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name)
            && !Set.of("true", "false", "null").contains(name);
    }

    @OnThread(Tag.Worker)
    static boolean publicHeader(Path file, String name, Charset charset)
    {
        try (Reader source = Files.newBufferedReader(file, charset)) {
            Reader limited = new FilterReader(source) {
                int remaining = 65536;
                public int read() throws IOException { return remaining-- > 0 ? super.read() : -1; }
                public int read(char[] b, int off, int len) throws IOException {
                    if (remaining <= 0) return -1;
                    int n = super.read(b, off, Math.min(len, remaining));
                    if (n > 0) remaining -= n;
                    return n;
                }
            };
            JavaLexer lexer = new JavaLexer(limited);
            int depth = 0;
            boolean publicType = false;
            String previous = "";
            for (LocatableToken token = lexer.nextToken(); token.getType() != JavaTokenTypes.EOF; token = lexer.nextToken()) {
                String text = token.getText();
                if (depth == 0 && "public".equals(text)) publicType = true;
                if (depth == 0 && !".".equals(previous) && Set.of("class", "interface", "enum", "record").contains(text)) {
                    LocatableToken identifier = lexer.nextToken();
                    if (name.equals(identifier.getText())) return publicType;
                }
                if ("{".equals(text)) depth++;
                if ("}".equals(text)) { depth--; if (depth <= 0) { depth = 0; publicType = false; } }
                if (depth == 0 && ";".equals(text)) publicType = false;
                previous = text;
            }
        } catch (IOException | RuntimeException ex) { }
        return false;
    }
}
