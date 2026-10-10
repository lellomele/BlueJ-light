/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public class TestLightTools
{
    private static String workerClasspath() throws Exception
    {
        Path libraries = Path.of("build/resources/main/lib").toAbsolutePath();
        try (var files = Files.list(libraries))
        { return files.filter(file -> file.toString().endsWith(".jar")).map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)); }
    }
    @Test public void historyRetainsFiftyVersionsAndDeduplicates() throws Exception
    {
        Path home = Files.createTempDirectory("history-test"); Path source = home.resolve("Example.java"); LocalHistory history = new LocalHistory(home);
        for (int i = 0; i < 55; i++) history.snapshot(source, "class Example { int n = " + i + "; }", "saved");
        assertEquals(50, history.list(source).size()); String latest = history.read(history.list(source).getFirst());
        history.snapshot(source, latest, "saved"); assertEquals(50, history.list(source).size()); assertTrue(latest.contains("54"));
    }
    @Test public void visibleSearchRangesUseInclusiveStartAndExclusiveEnd()
    {
        List<int[]> ranges = List.of(new int[]{0, 4}, new int[]{20, 24}, new int[]{20, 25}, new int[]{1000, 1004});
        assertEquals(2, VisibleRanges.between(ranges, 20, 1000).size());
        assertTrue(VisibleRanges.between(ranges, 21, 1000).isEmpty());
        assertEquals(1, VisibleRanges.between(ranges, 1000, 1005).size());
        assertTrue(VisibleRanges.between(List.of(), 0, 10).isEmpty());
    }
    @Test public void draftPreservesUnicodeAndConflictBaseWithoutChangingSource() throws Exception
    {
        Path home = Files.createTempDirectory("draft-test"); Path source = home.resolve("Example.java"); Files.writeString(source, "original");
        LocalHistory history = new LocalHistory(home); String text = "// caff\u00e8\nclass Example {}";
        history.draft(source, text, LocalHistory.hash("original")); LocalHistory.Draft draft = history.recover(source, "external change").orElseThrow();
        assertEquals(text, draft.text()); assertNotEquals(LocalHistory.hash("external change"), draft.baseHash());
        assertEquals("original", Files.readString(source)); assertTrue(history.recover(source, text).isEmpty());
        history.clearDraft(source); assertTrue(history.recover(source, "original").isEmpty());
    }
    @Test public void outlineUsesSyntaxNotCommentsAndFindsNestedConstructors() throws Exception
    {
        var entries = MethodOutline.parse("class Outer { // void fake() {}\n Outer() {} String text = \"void bogus() {}\";\n int add(int n) { return n; } class Inner { void nested() {} } }");
        assertEquals(3, entries.size()); assertTrue(entries.stream().anyMatch(e -> e.signature().equals("Outer()")));
        assertTrue(entries.stream().anyMatch(e -> e.owner().contains("Inner") && e.signature().startsWith("nested(")));
        assertTrue(entries.stream().allMatch(e -> e.offset() >= 0 && e.line() > 0));
    }
    @Test public void outlineAcceptsIncompleteCode() { assertFalse(MethodOutline.parse("class X { void first() {} void second() {").isEmpty()); }
    @Test public void diagnosticCodeIsIndependentOfCompilerLanguage()
    {
        assertEquals("Nomi e visibilita", ErrorExplanations.explain("compiler.err.cant.resolve.location", Locale.ITALIAN).concept());
        assertEquals(2, ErrorExplanations.explain("compiler.err.cant.resolve.location", Locale.ENGLISH).hints().size());
        assertTrue(ErrorExplanations.explain("unrecognized", Locale.ENGLISH).hints().getFirst().contains("original"));
    }
    @Test public void exerciseRoundTripPreservesInstructionsAndExcludesBuildArtifacts() throws Exception
    {
        Path parent = Files.createTempDirectory("exercise-test"); Path project = Files.createDirectory(parent.resolve("source"));
        Files.writeString(project.resolve("Answer.java"), "class Answer {}"); Files.writeString(project.resolve("Answer.class"), "not a class");
        Files.createDirectories(project.resolve(".git")); Files.writeString(project.resolve(".git/config"), "private");
        Properties values = new Properties(); values.setProperty("title.en", "Loops"); values.setProperty("title.it", "Cicli");
        values.setProperty("instructions.en", "Write a loop."); values.setProperty("hint.1.it", "Controlla la condizione"); values.setProperty("tests", "example.LoopTest");
        ExercisePack pack = new ExercisePack(values); Path zip = parent.resolve("exercise.zip"); pack.export(project, zip);
        Path imported = ExercisePack.importPack(zip, parent, "student"); ExercisePack read = ExercisePack.load(imported);
        assertEquals("Cicli", read.text("title", Locale.ITALIAN)); assertEquals("Loops", read.text("title", Locale.FRENCH));
        assertEquals(List.of("example.LoopTest"), read.tests()); assertTrue(Files.exists(imported.resolve("Answer.java")));
        assertFalse(Files.exists(imported.resolve("Answer.class"))); assertFalse(Files.exists(imported.resolve(".git")));
        assertTrue(Files.exists(imported.resolve("package.bluej")));
        assertThrows(java.io.IOException.class, () -> ExercisePack.importPack(zip, parent, "student"));
    }
    @Test public void rejectsZipSlipAndInvalidClasses() throws Exception
    {
        Path parent = Files.createTempDirectory("zip-slip-test"); Path zip = parent.resolve("bad.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("../escape.java")); out.write("class Escape {}".getBytes(StandardCharsets.UTF_8)); }
        assertThrows(java.io.IOException.class, () -> ExercisePack.importPack(zip, parent, "unsafe"));
        assertFalse(Files.exists(parent.resolve("escape.java"))); assertFalse(Files.exists(parent.resolve("unsafe")));
        Properties values = new Properties(); values.setProperty("tests", "Class;malicious"); assertThrows(java.io.IOException.class, () -> new ExercisePack(values));
    }
    @Test public void exerciseRunnerCollectsExpectedActualAndNeverWritesClassFilesInProject() throws Exception
    {
        Path project = Files.createTempDirectory("feedback-test");
        Files.writeString(project.resolve("AnswerTest.java"), "import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; public class AnswerTest { @Test void pass() { assertEquals(2, 2); } @Test void fail() { assertEquals(4, 3, \"Check sum\"); } }");
        var report = new ExerciseRunner().run(project, List.of("AnswerTest"), workerClasspath());
        assertEquals(2, report.results().size()); var failed = report.results().stream().filter(r -> r.status().equals("FAILED")).findFirst().orElseThrow();
        assertEquals("4", failed.expected()); assertEquals("3", failed.actual()); assertFalse(Files.exists(project.resolve("AnswerTest.class")));
        Path csv = project.resolve("report.csv"); ExerciseRunner.exportCsv(report, csv); assertTrue(Files.readString(csv).contains("expected,actual"));
    }
    @Test public void exerciseRunnerReportsCompilationFailures() throws Exception
    {
        Path project = Files.createTempDirectory("compile-feedback-test"); Files.writeString(project.resolve("Wrong.java"), "class Wrong { int x = ; }");
        var report = new ExerciseRunner().run(project, List.of("Wrong"), workerClasspath());
        assertEquals("COMPILATION_FAILED", report.results().getFirst().status());
    }
    @Test public void exerciseRunnerCanBeCancelledBeforeStarting() throws Exception
    {
        ExerciseRunner runner = new ExerciseRunner(); runner.cancel(); Path project = Files.createTempDirectory("cancel-test");
        Files.writeString(project.resolve("Example.java"), "class Example {}");
        assertThrows(java.io.IOException.class, () -> runner.run(project, List.of("Example"), System.getProperty("java.class.path")));
    }
}
