/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Entry point used only in a child JVM, never inside the IDE process. */
@OnThread(Tag.Any)
public final class ExerciseTestWorker
{
    public static void main(String[] args) throws Exception
    {
        Properties report = new Properties(); int[] index = {0};
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(Arrays.stream(args).skip(1).map(DiscoverySelectors::selectClass).toList())
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(new TestExecutionListener() {
            private synchronized void record(TestIdentifier id, String status, Throwable failure, String reason)
            {
                int i = index[0]++; String prefix = "test." + i + ".";
                report.setProperty(prefix + "name", trim(id.getDisplayName())); report.setProperty(prefix + "status", status);
                report.setProperty(prefix + "message", failure == null ? trim(reason) : trim(failure.toString()));
                Throwable current = failure;
                for (int depth = 0; current != null && depth < 6; depth++, current = current.getCause())
                {
                    if (current instanceof org.opentest4j.AssertionFailedError assertion)
                    {
                        if (assertion.isExpectedDefined()) report.setProperty(prefix + "expected", trim(assertion.getExpected().getStringRepresentation()));
                        if (assertion.isActualDefined()) report.setProperty(prefix + "actual", trim(assertion.getActual().getStringRepresentation()));
                        break;
                    }
                    if (current instanceof org.junit.ComparisonFailure comparison)
                    { report.setProperty(prefix + "expected", trim(comparison.getExpected())); report.setProperty(prefix + "actual", trim(comparison.getActual())); break; }
                }
                if (failure != null)
                {
                    List<String> lines = new ArrayList<>();
                    for (StackTraceElement frame : failure.getStackTrace())
                        if (frame.getFileName() != null && !frame.getClassName().startsWith("org.junit.") && !frame.getClassName().startsWith("org.opentest4j.")
                            && !frame.getClassName().startsWith("java.") && !frame.getClassName().startsWith("jdk.") && !frame.getClassName().startsWith("bluej.light."))
                        { lines.add(frame.toString()); if (lines.size() == 5) break; }
                    report.setProperty(prefix + "location", String.join("\n", lines));
                }
            }
            @Override public void executionFinished(TestIdentifier id, TestExecutionResult result)
            {
                if (id.isTest() || result.getStatus() != TestExecutionResult.Status.SUCCESSFUL)
                    record(id, result.getStatus().name(), result.getThrowable().orElse(null), "");
            }
            @Override public void executionSkipped(TestIdentifier id, String reason)
            { record(id, "SKIPPED", null, reason); }
        });
        launcher.execute(request);
        report.setProperty("count", Integer.toString(index[0]));
        try (var writer = Files.newBufferedWriter(Path.of(args[0]), StandardCharsets.UTF_8)) { report.store(writer, "BlueJ light test feedback"); }
    }
    private static String trim(String value) { return value == null ? "" : value.substring(0, Math.min(4000, value.length())); }
}
