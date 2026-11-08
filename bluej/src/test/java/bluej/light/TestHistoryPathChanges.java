/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestHistoryPathChanges
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path preferences, source, destination;
    private LocalHistory history;
    @Before public void start() throws Exception
    {
        Path root = temporary.getRoot().toPath(); preferences = root.resolve("preferences");
        source = root.resolve("Example.java"); destination = root.resolve("Renamed.java");
        Files.writeString(source, "saved\n"); history = new LocalHistory(preferences);
        history.snapshot(source, "earlier\n", "saved");
        history.draft(source, "newer unsaved\n", LocalHistory.hash("saved\n"), "editor", 2);
    }
    private SourceSaveService.Snapshot snapshot()
    { return new SourceSaveService.Snapshot(new SourceSaveService.Owner(),1,source,"saved\n",StandardCharsets.UTF_8,"saved\n",true,false); }
    private void move() throws Exception
    {
        try (SourceSaveService service = new SourceSaveService())
        { assertNull(service.relocate(snapshot(),destination,history).get(5,TimeUnit.SECONDS).historyWarning()); }
    }
    @Test public void renamedSourceKeepsHistoryAndNewerDraftAcrossRestart() throws Exception
    {
        Path revision = history.list(source).getFirst().file(); move();
        LocalHistory restarted = new LocalHistory(preferences);
        assertEquals("newer unsaved\n",restarted.recover(destination,"saved\n").orElseThrow().text());
        assertEquals(revision,restarted.list(destination).getFirst().file());
        assertTrue(restarted.list(source).isEmpty()); assertTrue(restarted.recover(source,"").isEmpty());
    }
    @Test public void lateOldPathJobsStayWithTheirEditorWhileReusedNameGetsSeparateHistory() throws Exception
    {
        move(); history.draft(source,"late editor checkpoint\n",LocalHistory.hash("saved\n"),"editor",3);
        Files.writeString(source,"new unrelated class\n");
        LocalHistory unrelated = new LocalHistory(preferences);
        unrelated.draft(source,"unrelated unsaved\n",LocalHistory.hash("new unrelated class\n"),"other",1);
        LocalHistory restarted = new LocalHistory(preferences);
        assertEquals("late editor checkpoint\n",restarted.recover(destination,"saved\n").orElseThrow().text());
        assertEquals("unrelated unsaved\n",restarted.recover(source,"new unrelated class\n").orElseThrow().text());
    }
    @Test public void restartAfterPhysicalMoveBeforeAcknowledgementRecoversTheHistoryIdentity() throws Exception
    {
        history.preparePathChange(source,destination); Files.move(source,destination);
        LocalHistory restarted = new LocalHistory(preferences);
        assertEquals("newer unsaved\n",restarted.recover(destination,"saved\n").orElseThrow().text());
        assertEquals("earlier\n",restarted.read(restarted.list(destination).getFirst()));
        assertTrue(restarted.recover(source,"").isEmpty());
    }
    @Test public void intentBeforeAMoveDoesNotPublishHistoryForANonexistentDestination() throws Exception
    {
        history.preparePathChange(source,destination);
        LocalHistory restarted = new LocalHistory(preferences);
        assertTrue(restarted.recover(destination,"").isEmpty());
        assertEquals("newer unsaved\n",restarted.recover(source,"saved\n").orElseThrow().text());
    }
    @Test public void failedMovePreservesBothSourcesAndTheirSeparateDrafts() throws Exception
    {
        Files.writeString(destination,"occupied\n");
        LocalHistory other = new LocalHistory(preferences); other.draft(destination,"unrelated draft\n",LocalHistory.hash("occupied\n"));
        try (SourceSaveService service = new SourceSaveService())
        {
            try { service.relocate(snapshot(),destination,history).get(5,TimeUnit.SECONDS); fail("Expected occupied destination"); }
            catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); }
        }
        LocalHistory restarted = new LocalHistory(preferences);
        assertEquals("occupied\n",Files.readString(destination)); assertEquals("saved\n",Files.readString(source));
        assertEquals("newer unsaved\n",restarted.recover(source,"saved\n").orElseThrow().text());
        assertEquals("unrelated draft\n",restarted.recover(destination,"occupied\n").orElseThrow().text());
    }
    @Test public void repeatedMovesAndReturnToAnOldNameKeepOneDocumentHistory() throws Exception
    {
        Path original = source; move();
        source = destination; destination = source.resolveSibling("Third.java"); move();
        source = destination; destination = original; move();
        LocalHistory restarted = new LocalHistory(preferences);
        assertEquals("newer unsaved\n",restarted.recover(original,"saved\n").orElseThrow().text());
        assertTrue(restarted.recover(source,"").isEmpty());
        assertEquals(1,restarted.list(original).size());
    }
    @Test public void caseOnlyMoveAndRestartKeepTheDraft() throws Exception
    {
        destination = source.resolveSibling("EXAMPLE.java"); move();
        assertEquals("newer unsaved\n",new LocalHistory(preferences).recover(destination,"saved\n").orElseThrow().text());
        try (var files = Files.list(destination.getParent()))
        { assertTrue(files.anyMatch(file -> file.getFileName().toString().equals("EXAMPLE.java"))); }
    }
    @Test public void damagedUnrelatedTransactionDoesNotDisableHistory() throws Exception
    {
        Path transactions = preferences.resolve("local-history/path-transactions"); Files.createDirectories(transactions);
        Files.writeString(transactions.resolve("broken.properties"),"id=\\u0x00");
        LocalHistory restarted = new LocalHistory(preferences);
        assertEquals("newer unsaved\n",restarted.recover(source,"saved\n").orElseThrow().text());
        move(); assertEquals("saved\n",Files.readString(destination));
    }
}
