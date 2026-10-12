/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.compiler;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestSourceCoordinates
{
    @Test public void tabsKeepPhysicalCharacterColumns()
    {
        String text = "class Example {\n\tint result = missing;\n}\n";
        SourceCoordinates positions = new SourceCoordinates(text);
        int offset = text.indexOf("missing");
        assertEquals(new SourceCoordinates.Position(2, 15), positions.at(offset));
        assertEquals(new SourceCoordinates.Position(2, 22), positions.at(offset + 7));
    }

    @Test public void supportsLfCrLfAndCrLineEndings()
    {
        for (String newline : new String[] {"\n", "\r\n", "\r"}) {
            String text = "first" + newline + "second" + newline;
            SourceCoordinates positions = new SourceCoordinates(text);
            assertEquals(new SourceCoordinates.Position(2, 1), positions.at(text.indexOf("second")));
            assertEquals(new SourceCoordinates.Position(3, 1), positions.at(text.length()));
        }
    }

    @Test public void clampsUnavailableOffsetsAndUsesUtf16Columns()
    {
        SourceCoordinates positions = new SourceCoordinates("\uD83D\uDE00x");
        assertEquals(new SourceCoordinates.Position(1, 3), positions.at(2));
        assertEquals(new SourceCoordinates.Position(1, 1), positions.at(-1));
        assertEquals(new SourceCoordinates.Position(1, 4), positions.at(Long.MAX_VALUE));
        assertEquals(new SourceCoordinates.Position(1, 1), new SourceCoordinates("").at(0));
    }
}
