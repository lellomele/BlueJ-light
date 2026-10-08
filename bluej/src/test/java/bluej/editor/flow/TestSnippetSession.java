/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestSnippetSession extends FXTest
{
    @Test public void mirrorsAreOneUndoAndRedo()
    {
        fx_(() -> {
            var expansion = new SnippetTemplate("${1:i} + ${1} < ${2:limit}; $0").expand("", "    ");
            HoleDocument document = new HoleDocument();
            document.replaceText(0, 0, expansion.text());
            DocumentUndoStack undo = new DocumentUndoStack(document);
            int[] selection = new int[2];
            SnippetSession session = new SnippetSession(document, expansion, 0,
                (start, end) -> { selection[0] = start; selection[1] = end; }, undo::appendToLastEdit);
            document.replaceText(selection[0], selection[1], "counter");
            assertEquals("counter + counter < limit; ", document.getFullContent());
            assertEquals(1, undo.canUndoCount());
            session.advance(false);
            assertEquals("limit", document.getContent(selection[0], selection[1]).toString());
            session.advance(true);
            assertEquals("counter", document.getContent(selection[0], selection[1]).toString());
            session.close();
            undo.undo();
            assertEquals(expansion.text(), document.getFullContent());
            undo.redo();
            assertEquals("counter + counter < limit; ", document.getFullContent());
        });
    }

    @Test public void finalStopAndOutsideEditEndTheSession()
    {
        fx_(() -> {
            HoleDocument document = new HoleDocument();
            var expansion = new SnippetTemplate("x(${1:value})$0;").expand("", "    ");
            document.replaceText(0, 0, expansion.text());
            DocumentUndoStack undo = new DocumentUndoStack(document);
            int[] selection = new int[2];
            SnippetSession session = new SnippetSession(document, expansion, 0,
                (start, end) -> { selection[0] = start; selection[1] = end; }, undo::appendToLastEdit);
            session.advance(false);
            assertFalse(session.isActive());
            assertEquals(document.getLength() - 1, selection[0]);
            SnippetSession another = new SnippetSession(document, expansion, 0, (start, end) -> {}, undo::appendToLastEdit);
            document.replaceText(0, 0, "outside");
            assertFalse(another.isActive());
        });
    }
}
