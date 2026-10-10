/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class ExerciseRunner
{
    public record Result(String name, String status, String message, String expected, String actual, String location) {}
    public record Report(Instant time, List<Result> results, String log) {}
    private volatile Process active;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel()
    {
        cancelled.set(true); Process process = active;
        if (process != null) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
    }
    public Report run(Path project, List<String> tests, String libraries) throws IOException, InterruptedException
    {
        if (tests.isEmpty()) throw new IOException("No test classes configured");
        Path work = Files.createTempDirectory("bluej-exercise-");
        StringBuilder log = new StringBuilder();
        try
        {
            List<Path> sources;
            try (var files = Files.walk(project))
            {
                sources = files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.toString().endsWith(".java"))
                    .filter(p -> java.util.stream.StreamSupport.stream(project.relativize(p).spliterator(), false).noneMatch(part -> part.toString().startsWith(".")))
                    .sorted().limit(1001).toList();
            }
            if (sources.isEmpty() || sources.size() > 1000) throw new IOException("Exercise must contain 1 to 1000 Java source files");
            long total = 0; for (Path file : sources) total += Files.size(file);
            if (total > 20 * 1024 * 1024) throw new IOException("Exercise source size limit exceeded");
            Path classes = Files.createDirectory(work.resolve("classes"));
            String classpath = classes + File.pathSeparator + libraries;
            Properties settings = new Properties(); Path projectSettings = project.resolve("package.bluej");
            if (Files.isRegularFile(projectSettings) && Files.size(projectSettings) < 1024 * 1024)
                try (var in = Files.newInputStream(projectSettings)) { settings.load(in); }
            String charset;
            try { charset = java.nio.charset.Charset.forName(settings.getProperty("project.charset", "UTF-8")).name(); }
            catch (IllegalArgumentException ex) { throw new IOException("Unsupported project encoding", ex); }
            StringBuilder arguments = new StringBuilder("-proc:none\n-encoding\n" + charset + "\n-d\n" + quote(classes.toString()) + "\n-classpath\n" + quote(classpath) + "\n");
            for (Path source : sources) arguments.append(quote(source.toAbsolutePath().toString())).append('\n');
            Path argfile = work.resolve("compile.args"); Files.writeString(argfile, arguments, StandardCharsets.UTF_8);
            String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
            Path bin = Path.of(System.getProperty("java.home"), "bin");
            int compiled = execute(List.of(bin.resolve("javac" + suffix).toString(), "-J-Xmx256m", "-J-Dfile.encoding=UTF-8", "@" + argfile), project, log);
            if (compiled != 0) return new Report(Instant.now(), List.of(new Result("javac", "COMPILATION_FAILED", log.toString(), "", "", "")), log.toString());
            Path report = work.resolve("report.properties");
            List<String> command = new ArrayList<>(List.of(bin.resolve("java" + suffix).toString(), "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", classpath,
                ExerciseTestWorker.class.getName(), report.toString()));
            command.addAll(tests);
            int exit = execute(command, project, log);
            if (exit != 0 || !Files.isRegularFile(report) || Files.size(report) > 1024 * 1024)
                throw new IOException("Test process did not produce a valid report (exit " + exit + ")\n" + log);
            Properties values = new Properties(); try (var reader = Files.newBufferedReader(report, StandardCharsets.UTF_8)) { values.load(reader); }
            int count;
            try { count = Integer.parseInt(values.getProperty("count", "-1")); }
            catch (NumberFormatException ex) { throw new IOException("Invalid test count", ex); }
            if (count < 0 || count > 2000) throw new IOException("Invalid test count");
            List<Result> results = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                String prefix = "test." + i + ".";
                results.add(new Result(values.getProperty(prefix + "name", ""), values.getProperty(prefix + "status", "UNKNOWN"),
                    values.getProperty(prefix + "message", ""), values.getProperty(prefix + "expected", ""), values.getProperty(prefix + "actual", ""), values.getProperty(prefix + "location", "")));
            }
            return new Report(Instant.now(), List.copyOf(results), log.toString());
        }
        finally
        {
            active = null;
            try (var files = Files.walk(work)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    private int execute(List<String> command, Path cwd, StringBuilder log) throws IOException, InterruptedException
    {
        if (cancelled.get()) throw new IOException("Cancelled");
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start(); active = process;
        if (cancelled.get()) cancel();
        AtomicBoolean tooMuchOutput = new AtomicBoolean();
        Thread drain = new Thread(() -> {
            try (InputStream in = process.getInputStream())
            {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int n;
                while ((n = in.read(chunk)) != -1)
                {
                    if (bytes.size() + n > 1024 * 1024) { tooMuchOutput.set(true); process.destroyForcibly(); break; }
                    bytes.write(chunk, 0, n);
                }
                synchronized (log) { log.append(bytes.toString(StandardCharsets.UTF_8)); }
            }
            catch (IOException ignored) { }
        }, "BlueJ exercise output");
        drain.setDaemon(true); drain.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished || cancelled.get() || tooMuchOutput.get())
        {
            process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
            drain.join(2000);
            throw new IOException(cancelled.get() ? "Cancelled" : !finished ? "Time limit exceeded (30 seconds)" : "Output size limit exceeded");
        }
        // Also terminate leftover children after a normally completed run.
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        drain.join(2000); active = null; return process.exitValue();
    }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    public static void exportCsv(Report report, Path file) throws IOException
    {
        try (var out = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
        {
            out.write("test,status,expected,actual,message,location\n");
            for (Result result : report.results())
            {
                String[] columns = {result.name(), result.status(), result.expected(), result.actual(), result.message(), result.location()};
                out.write(Arrays.stream(columns).map(ExerciseRunner::csvCell).collect(java.util.stream.Collectors.joining(",")) + "\n");
            }
        }
    }
    private static String csvCell(String value)
    {
        String checked = value.stripLeading();
        if (!checked.isEmpty() && "=+-@".indexOf(checked.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
