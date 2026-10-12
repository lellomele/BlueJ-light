/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.parser;

import org.junit.Test;
import threadchecker.OnThread;
import threadchecker.Tag;
import static org.junit.Assert.*;

@OnThread(Tag.FXPlatform)
public class TestLocalCompletion
{
    @Test
    public void supportsJavaIdentifiersIncludingSupplementaryUnicode()
    {
        for (String name : new String[] { "value", "$value", "_value", "\u03B1", "\uD801\uDC00value" }) {
            AssistContent content = LocalCompletion.getCompletion("String", name, false);
            assertNotNull(name, content);
            assertEquals(name, content.getName());
            assertEquals(AssistContent.CompletionKind.LOCAL_VAR, content.getKind());
        }
    }

    @Test
    public void rejectsIncompleteAndReservedNames()
    {
        for (String name : new String[] { null, "", "_", "class", "null", "1value", "value.", "a b" })
            assertNull(LocalCompletion.getCompletion("String", name, false));
    }

    @Test
    public void retainsFormalParameterKindAndIncompleteTypeText()
    {
        AssistContent content = LocalCompletion.getCompletion("List<", "items", true);
        assertEquals("List<", content.getType());
        assertEquals(AssistContent.CompletionKind.FORMAL_PARAM, content.getKind());
    }
}
