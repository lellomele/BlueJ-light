/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.AssistContent;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestJavaKeywordCompletion extends FXTest
{
    @Test public void keywordsAreReusableAndInsertPlainTextWithoutParameters()
    {
        fx_(() -> {
            assertSame(JavaKeywordCompletion.all(), JavaKeywordCompletion.all());
            List<String> names = JavaKeywordCompletion.all().stream().map(AssistContent::getName).toList();
            assertEquals(names.size(), names.stream().distinct().count());
            assertTrue(names.containsAll(List.of("return", "public", "if", "instanceof", "record", "sealed", "var")));
            assertFalse(names.contains("goto"));
            assertFalse(names.contains("const"));
            for (var keyword : JavaKeywordCompletion.all()) {
                assertNull(keyword.getParams());
                assertEquals(AssistContent.CompletionKind.KEYWORD, keyword.getKind());
            }
        });
    }

    @Test public void ownMembersComeBeforeLocalsTypesSnippetsAndObjectMembers()
    {
        fx_(() -> {
            assertEquals(0, FlowEditor.completionPriority(candidate("zField", AssistContent.CompletionKind.FIELD, "a1"), "a1"));
            assertEquals(0, FlowEditor.completionPriority(candidate("zMethod", AssistContent.CompletionKind.METHOD, "p.a1"), "a1"));
            assertEquals(1, FlowEditor.completionPriority(candidate("aLocal", AssistContent.CompletionKind.LOCAL_VAR, null), "a1"));
            assertEquals(2, FlowEditor.completionPriority(candidate("aInherited", AssistContent.CompletionKind.FIELD, "Parent"), "a1"));
            assertEquals(3, FlowEditor.completionPriority(candidate("AType", AssistContent.CompletionKind.TYPE, null), "a1"));
            assertEquals(4, FlowEditor.completionPriority(JavaKeywordCompletion.all().getFirst(), "a1"));
            assertEquals(5, FlowEditor.completionPriority(new SnippetCompletion("aaa", "test", "test"), "a1"));
            assertEquals(6, FlowEditor.completionPriority(candidate("equals", AssistContent.CompletionKind.METHOD, "Object"), "a1"));
        });
    }

    private static AssistContent candidate(String name, AssistContent.CompletionKind kind, String owner)
    {
        return new AssistContent() {
            public String getName() { return name; }
            public List<ParamInfo> getParams() { return null; }
            public String getType() { return null; }
            public Access getAccessPermission() { return null; }
            public String getDeclaringClass() { return owner; }
            public CompletionKind getKind() { return kind; }
            public String getJavadoc() { return null; }
        };
    }
}
