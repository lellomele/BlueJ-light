/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.AssistContent;
import java.util.Arrays;
import java.util.List;
import threadchecker.OnThread;
import threadchecker.Tag;

public final class JavaKeywordCompletion extends AssistContent
{
    private static final List<JavaKeywordCompletion> ALL = Arrays.stream((
        "abstract assert boolean break byte case catch char class continue default do double else enum "
        + "extends final finally float for if implements import instanceof int interface long native new "
        + "package private protected public return short static strictfp super switch synchronized this "
        + "throw throws transient try void volatile while true false null record sealed non-sealed permits var yield when "
        + "open module requires transitive exports opens uses provides to with")
        .split(" ")).map(JavaKeywordCompletion::new).toList();
    private final String word;
    private JavaKeywordCompletion(String word) { this.word = word; }
    public static List<JavaKeywordCompletion> all() { return ALL; }
    @Override @OnThread(Tag.Any) public String getName() { return word; }
    @Override public List<ParamInfo> getParams() { return null; }
    @Override public String getType() { return null; }
    @Override public Access getAccessPermission() { return null; }
    @Override public String getDeclaringClass() { return null; }
    @Override public CompletionKind getKind() { return CompletionKind.KEYWORD; }
    @Override public String getJavadoc() { return null; }
}
