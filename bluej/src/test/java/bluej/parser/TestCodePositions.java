/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-12. GPLv2 with Classpath Exception. */
package bluej.parser;

import bluej.JavaFXThreadingRule;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestCodePositions
{
    @Rule public JavaFXThreadingRule javafxRule = new JavaFXThreadingRule();
    @BeforeClass public static void init() { InitConfig.init(); }

    private static TestableDocument parse(String source)
    {
        TestableDocument document = new TestableDocument();
        document.enableParser(true);
        document.insertString(0, source);
        document.flushReparseQueue();
        return document;
    }

    @Test public void closedLiteralsAndCommentsAreNotCode()
    {
        String source = "class School { int answer() { /* note */ String text = \"hello\"; return 42; } }";
        var document = parse(source);
        assertFalse(document.isCodePosition(source.indexOf("note") + 2));
        assertFalse(document.isCodePosition(source.indexOf("hello") + 2));
        assertTrue(document.isCodePosition(source.indexOf("return") + 2));
    }

    @Test public void unterminatedStringIsNotAnInsertionPosition()
    {
        String source = "class School { String text = \"unfinished";
        assertFalse(parse(source).isCodePosition(source.indexOf("unfinished") + 3));
    }

    @Test public void unterminatedCharacterIsNotAnInsertionPosition()
    {
        String source = "class School { char value = 'unfinished";
        assertFalse(parse(source).isCodePosition(source.indexOf("unfinished") + 3));
    }

    @Test public void textBlockClosingLineDoesNotHideFollowingCode()
    {
        String source = "class School { String text = \"\"\"\nhello\n\"\"\"; int field; int answer() { return 42; } }";
        var document = parse(source);
        assertFalse(document.isCodePosition(source.indexOf("hello") + 2));
        assertTrue(document.isCodePosition(source.indexOf("field") + 2));
        assertTrue(document.isCodePosition(source.indexOf("return") + 2));
    }
}
