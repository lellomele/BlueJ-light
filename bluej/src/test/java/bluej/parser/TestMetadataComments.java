/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-11. GPLv2 with Classpath Exception. */
package bluej.parser;

import bluej.JavaFXThreadingRule;
import bluej.parser.entity.ClassLoaderResolver;
import bluej.parser.lexer.LocatableToken;
import bluej.parser.symtab.ClassInfo;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestMetadataComments
{
    @Rule public JavaFXThreadingRule javafxRule = new JavaFXThreadingRule();
    @BeforeClass public static void init() { InitConfig.init(); }

    private static class Probe extends InfoParser
    {
        private final boolean legacy;
        Probe(String source, boolean legacy)
        {
            super(new StringReader(source), new ClassLoaderResolver(TestMetadataComments.class.getClassLoader()));
            this.legacy = legacy; targetPkg = "";
        }
        @Override public void gotComment(LocatableToken token)
        {
            if (legacy) comments(this).add(token); else super.gotComment(token);
        }
        ClassInfo read() { parseCU(); if (info != null) resolveComments(); return info; }
    }
    @SuppressWarnings("unchecked") private static List<LocatableToken> comments(EditorParser parser)
    {
        try { Field f = EditorParser.class.getDeclaredField("commentQueue"); f.setAccessible(true); return (List<LocatableToken>)f.get(parser); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void equivalent(String source)
    {
        Probe current = new Probe(source, false), legacy = new Probe(source, true);
        ClassInfo actual = current.read(), expected = legacy.read();
        assertTrue(comments(current).isEmpty());
        if (expected == null) { assertNull(actual); return; }
        assertNotNull(actual);
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getSuperclass(), actual.getSuperclass());
        assertEquals(expected.getImplements(), actual.getImplements());
        assertEquals(expected.getUsed(), actual.getUsed());
        assertEquals(expected.getTypeParameterTexts(), actual.getTypeParameterTexts());
        assertEquals(expected.getComments(), actual.getComments());
    }
    @Test public void javadocConstructorsOverloadsGenericsAndNestedClassesArePreserved()
    {
        String source = "import java.util.ArrayList; import java.io.Serializable;\n"
            + "/** Class documentation. */ public class Example<T extends Number> extends ArrayList<T> implements Serializable {\n"
            + " /** Creates an example. */ public Example() {}\n"
            + " /** Returns a value. @param value input */ public T answer(T value) { return value; }\n"
            + " /** Another overload. */ public int answer(int... values) { return values.length; }\n"
            + " /** Nested type. */ static class Nested { /** Nested value. */ int value() { return 42; } }\n}\n";
        equivalent(source);
        ClassInfo info = new Probe(source, false).read();
        assertTrue(info.getCommentsAsList().stream().anyMatch(c -> c.comment != null && c.comment.contains("Returns a value")));
    }
    @Test public void denseCommentsDoNotAccumulateInMetadataParser()
    {
        StringBuilder source = new StringBuilder("class Example {\n");
        for (int i = 0; i < 80; i++) source.append("/** Method documentation. */ int method").append(i).append("() { /* body */ return 42; } // end\n");
        source.append("}\n");
        equivalent(source.toString());
        Probe old = new Probe(source.toString(), true); old.read();
        assertTrue(comments(old).size() >= 80);
    }
    @Test public void commentsInStringsAndTextBlocksDoNotChangeMetadata()
    {
        equivalent("class Example { /** Value. */ String text = \"// not a comment\"; String block = \"\"\"\n/* text */\n\"\"\"; }\n");
    }
    @Test public void malformedAndIncompleteSourcesRetainTheExistingBehaviour()
    {
        for (String source : List.of("/** Before. */ class Broken { public answer() { return 1; } }", "class Broken { /** unfinished", "/** A */ class Broken { int m( { }"))
            equivalent(source);
    }
}
