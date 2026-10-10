/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class ExercisePack
{
    public static final String MANIFEST = "exercise.properties";
    private static final long MAX_TOTAL = 50L * 1024 * 1024;
    private final Properties values;
    public ExercisePack(Properties values) throws IOException
    {
        this.values = new Properties(); this.values.putAll(values);
        if (!values.getProperty("format", "1").equals("1")) throw new IOException("Unsupported exercise format");
        for (String name : tests())
            if (!name.matches("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)*")) throw new IOException("Invalid test class: " + name);
    }
    public static ExercisePack load(Path project) throws IOException
    {
        Path file = project.resolve(MANIFEST);
        if (Files.size(file) > 128 * 1024) throw new IOException("Exercise description is too large");
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        return new ExercisePack(values);
    }
    public String text(String key, Locale locale)
    {
        String chosen = values.getProperty(key + "." + locale.getLanguage(), "");
        return chosen.isBlank() ? values.getProperty(key + ".en", "") : chosen;
    }
    public List<String> tests()
    { return Arrays.stream(values.getProperty("tests", "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList(); }
    public List<String> hints(Locale locale)
    {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= 20; i++) { String hint = text("hint." + i, locale); if (!hint.isBlank()) result.add(hint); }
        return List.copyOf(result);
    }
    public String serialize() throws IOException
    {
        StringWriter writer = new StringWriter(); values.store(writer, "BlueJ light exercise format 1");
        if (writer.toString().getBytes(StandardCharsets.UTF_8).length > 128 * 1024) throw new IOException("Exercise description is too large");
        return writer.toString();
    }
    public void save(Path project) throws IOException
    { Files.writeString(project.resolve(MANIFEST), serialize(), StandardCharsets.UTF_8); }

    private static boolean include(Path relative)
    {
        for (Path component : relative)
            if (component.toString().startsWith(".") || Set.of("build", "node_modules", "local-history", "data").contains(component.toString())) return false;
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("package.bluej") || name.endsWith(".java") || name.endsWith(".txt") || name.endsWith(".md")
            || name.endsWith(".html") || name.endsWith(".csv") || name.endsWith(".json") || name.endsWith(".xml")
            || name.endsWith(".properties") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif");
    }
    public void export(Path project, Path zip) throws IOException
    {
        Path root = project.toRealPath();
        if (zip.toAbsolutePath().normalize().startsWith(root)) throw new IOException("Save the exercise archive outside the project");
        Path temporary = Files.createTempFile(zip.toAbsolutePath().getParent(), "exercise-", ".zip");
        try
        {
            long total = 0; int count = 1;
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary)); var paths = Files.walk(root))
            {
                out.putNextEntry(new ZipEntry(MANIFEST)); out.write(serialize().getBytes(StandardCharsets.UTF_8)); out.closeEntry();
                for (Path file : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList())
                {
                    Path relative = root.relativize(file);
                    if (!include(relative) || relative.toString().equals(MANIFEST)) continue;
                    if (!file.toRealPath().startsWith(root)) throw new IOException("External symbolic link in project");
                    long size = Files.size(file); total += size;
                    if (size > LocalHistory.MAX_BYTES || total > MAX_TOTAL || ++count > 2000) throw new IOException("Exercise archive size limit exceeded");
                    out.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    Files.copy(file, out); out.closeEntry();
                }
            }
            Files.move(temporary, zip, StandardCopyOption.REPLACE_EXISTING);
        }
        finally { Files.deleteIfExists(temporary); }
    }

    public static Path importPack(Path archive, Path parent, String folder) throws IOException
    {
        if (!folder.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")) throw new IOException("Invalid project folder name");
        Path destination = parent.toRealPath().resolve(folder);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Project folder already exists");
        Path staging = Files.createTempDirectory(parent.toRealPath(), ".exercise-");
        try
        {
            long total = 0; int count = 0;
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(archive)))
            {
                ZipEntry entry; byte[] buffer = new byte[8192]; Set<Path> entries = new HashSet<>();
                while ((entry = in.getNextEntry()) != null)
                {
                    if (++count > 2000 || entry.getName().contains("\\") || entry.getName().contains(":")) throw new IOException("Invalid archive entry");
                    Path target = staging.resolve(entry.getName()).normalize();
                    if (!target.startsWith(staging) || target.equals(staging) || !entries.add(target)) throw new IOException("Unsafe archive path");
                    if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                    if (!include(staging.relativize(target))) throw new IOException("Unsupported archive file: " + entry.getName());
                    Files.createDirectories(target.getParent());
                    long size = 0;
                    try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))
                    {
                        int read;
                        while ((read = in.read(buffer)) != -1)
                        {
                            size += read; total += read;
                            if (size > LocalHistory.MAX_BYTES || total > MAX_TOTAL) throw new IOException("Exercise archive size limit exceeded");
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
            load(staging);
            if (!Files.exists(staging.resolve("package.bluej"))) Files.writeString(staging.resolve("package.bluej"), "package.numTargets=0\n");
            Files.move(staging, destination);
            return destination;
        }
        finally
        {
            if (Files.exists(staging)) try (var paths = Files.walk(staging))
            { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
}
