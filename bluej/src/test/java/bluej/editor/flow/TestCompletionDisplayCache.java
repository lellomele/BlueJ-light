/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.AssistContent;
import bluej.parser.AssistContentThreadSafe;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestCompletionDisplayCache
{
    private static AssistContentThreadSafe method(String... types)
    {
        List<AssistContent.ParamInfo> params = Arrays.stream(types)
            .map(t -> new AssistContent.ParamInfo(t, null, "_value_", () -> null)).toList();
        return new AssistContentThreadSafe(AssistContent.Access.PUBLIC, "Example", null,
            AssistContent.CompletionKind.METHOD, "method", null, params, null, "int", null);
    }

    @Test public void signaturesShareImmutableDisplayWithoutCachingResolvedMembers()
    {
        CompletionDisplayCache cache = new CompletionDisplayCache();
        Object loader = new Object(); cache.setContext(1, loader);
        String first = cache.parameters(method("java.util.List<java.lang.String>", "int[]"));
        assertEquals("(List<String>, int[])", first);
        assertSame(first, cache.parameters(method("java.util.List<java.lang.String>", "int[]")));
        assertEquals("(String)", cache.parameters(method("java.lang.String")));
        assertEquals("()", cache.parameters(method()));
        assertEquals("", cache.parameters(new AssistContentThreadSafe(AssistContent.Access.PUBLIC, null, null,
            AssistContent.CompletionKind.FIELD, "value", null, null, null, "int", null)));
    }

    @Test public void editUndoRedoAndClassLoaderChangesInvalidateTheCache()
    {
        CompletionDisplayCache cache = new CompletionDisplayCache(); Object loader = new Object();
        cache.setContext(1, loader); String first = cache.parameters(method("int"));
        cache.setContext(2, loader); String edit = cache.parameters(method("int"));
        assertNotSame(first, edit);
        cache.setContext(3, loader); String undo = cache.parameters(method("int"));
        assertNotSame(edit, undo);
        cache.setContext(4, loader); String redo = cache.parameters(method("int"));
        assertNotSame(undo, redo);
        cache.setContext(4, new Object()); assertNotSame(redo, cache.parameters(method("int")));
    }

    @Test public void changedExternalSignaturesCannotReuseOldTypes()
    {
        CompletionDisplayCache cache = new CompletionDisplayCache(); cache.setContext(1, new Object());
        assertEquals("(String)", cache.parameters(method("java.lang.String")));
        assertEquals("(List<Integer>)", cache.parameters(method("java.util.List<java.lang.Integer>")));
    }
}
