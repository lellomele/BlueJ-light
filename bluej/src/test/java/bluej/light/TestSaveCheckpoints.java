/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestSaveCheckpoints
{
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private Path source() { return folder.getRoot().toPath().resolve("Example.java"); }
    private LocalHistory history() { return new LocalHistory(folder.getRoot().toPath().resolve("preferences")); }

    @Test public void oldSaveAcknowledgementPreservesAndRebasesNewerDraft() throws Exception
    {
        LocalHistory history=history();Path source=source();
        history.draft(source,"revision two",LocalHistory.hash("original"),"editor",2);
        history.acknowledgeSave(source,"original","revision one","editor",1);
        var draft=history.recover(source,"revision one").orElseThrow();
        assertEquals("revision two",draft.text());assertEquals(LocalHistory.hash("revision one"),draft.baseHash());
        history.acknowledgeSave(source,"revision one","revision two","editor",2);
        assertTrue(history.recover(source,"revision two").isEmpty());
        assertTrue(history.recover(source,"different text").isEmpty());
    }

    @Test public void latestAcknowledgementRetiresOlderDraftWithoutRestoringStaleText() throws Exception
    {
        LocalHistory history=history();Path source=source();
        history.draft(source,"revision one",LocalHistory.hash("original"),"editor",1);
        history.acknowledgeSave(source,"original","revision two","editor",2);
        assertTrue(history.recover(source,"revision two").isEmpty());
        assertTrue(history.recover(source,"other").isEmpty());
        assertEquals("revision two",history.read(history.list(source).getFirst()));
    }

    @Test public void sameRevisionNumberFromAnotherEditorCannotEraseItsDraft() throws Exception
    {
        LocalHistory history=history();Path source=source();
        history.draft(source,"other editor",LocalHistory.hash("original"),"other",1);
        history.acknowledgeSave(source,"original","saved","this",100);
        assertEquals("other editor",history.recover(source,"saved").orElseThrow().text());
    }

    @Test public void unversionedDraftRemainsReadableAndIsOnlyRemovedWhenItsTextWasSaved() throws Exception
    {
        LocalHistory history=history();Path source=source();
        history.draft(source,"legacy unsaved",LocalHistory.hash("original"));
        history.acknowledgeSave(source,"original","different save","editor",9);
        assertEquals("legacy unsaved",history.recover(source,"different save").orElseThrow().text());
        history.acknowledgeSave(source,"different save","legacy unsaved","editor",10);
        assertTrue(history.recover(source,"other").isEmpty());
    }
}
