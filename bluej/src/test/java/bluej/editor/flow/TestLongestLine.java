/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestLongestLine
{
    @Test public void includesFinalLineAndReusesCachedResult()
    {
        HoleDocument document = new HoleDocument();
        assertEquals("", document.getLongestLine());
        document.replaceText(0, 0, "first\nlongest final line");
        assertEquals("longest final line", document.getLongestLine());
        assertSame(document.getLongestLine(), document.getLongestLine());
        document.replaceText(0, 5, "new longest line, before the gap");
        assertEquals("new longest line, before the gap", document.getLongestLine());
        document.replaceText(0, document.getLength(), "\n\n");
        assertEquals("", document.getLongestLine());
    }

    @Test public void invalidatesBeforeNotifyingListeners()
    {
        HoleDocument document = new HoleDocument();
        document.replaceText(0, 0, "old");
        document.getLongestLine();
        document.addListener(false, (start, oldText, newText, removed, added) ->
            assertEquals("new longest line", document.getLongestLine()));
        document.replaceText(0, 3, "new longest line");
    }

    @Test public void matchesReferenceAfterEditsAndUndo()
    {
        HoleDocument document = new HoleDocument();
        DocumentUndoStack undo = new DocumentUndoStack(document);
        Random random = new Random(552);
        String expected = "";
        for (int i = 0; i < 500; i++)
        {
            int start = random.nextInt(expected.length() + 1);
            int end = start + random.nextInt(expected.length() - start + 1);
            String replacement = switch (random.nextInt(4)) {
                case 0 -> "\n";
                case 1 -> "abc\nlast longer line";
                case 2 -> "";
                default -> "x".repeat(random.nextInt(50));
            };
            String before = expected;
            undo.compoundEdit(() -> document.replaceText(start, end, replacement));
            expected = expected.substring(0, start) + replacement + expected.substring(end);
            checkLongest(document, expected);
            if (i % 7 == 0 && !before.equals(expected))
            {
                undo.undo();
                checkLongest(document, before);
                undo.redo();
                checkLongest(document, expected);
            }
        }
    }

    private void checkLongest(HoleDocument document, String text)
    {
        String longest = "";
        for (String line : text.split("\n", -1))
            if (line.length() > longest.length()) longest = line;
        assertEquals(longest, document.getLongestLine());
        assertSame(document.getLongestLine(), document.getLongestLine());
    }
}
