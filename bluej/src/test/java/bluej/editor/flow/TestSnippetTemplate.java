/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestSnippetTemplate
{
    @Test public void expandsLinkedFieldsAndIndentation()
    {
        var result = new SnippetTemplate("for (${1:i}; ${1} < ${2:limit}; ${1}++) {\n\t$0\n}").expand("    ", "    ");
        assertEquals("for (i; i < limit; i++) {\n        \n    }", result.text());
        assertEquals(4, result.stops().size());
        assertEquals(3, result.stops().stream().filter(stop -> stop.index() == 1).count());
        assertEquals(result.text().indexOf("\n") + 9, result.exit());
    }

    @Test public void supportsEmptyFieldsAndLiteralDollars()
    {
        var result = new SnippetTemplate("${1:} \\$ ${2:a\\}b} $0").expand("", "    ");
        assertEquals(" $ a}b ", result.text());
        assertEquals(0, result.stops().getFirst().end());
        assertEquals(result.text().length(), result.exit());
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsUnclosedField()
    {
        new SnippetTemplate("${1:name").expand("", "    ");
    }
}
