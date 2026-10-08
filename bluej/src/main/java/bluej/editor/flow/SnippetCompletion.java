/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import java.util.List;
import bluej.parser.AssistContent;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public final class SnippetCompletion extends AssistContent
{
    @OnThread(Tag.Any) private final String name;
    private final String description;
    private final SnippetTemplate template;
    private final String context;

    public SnippetCompletion(String name, String description, String body)
    {
        this(name, description, body, "method");
    }

    public SnippetCompletion(String name, String description, String body, String context)
    {
        this.name = name;
        this.description = description;
        this.template = new SnippetTemplate(body);
        this.context = context;
    }

    @Override @OnThread(Tag.Any) public String getName() { return name; }
    @Override public List<ParamInfo> getParams() { return null; }
    @Override public String getType() { return null; }
    @Override public Access getAccessPermission() { return null; }
    @Override public String getDeclaringClass() { return null; }
    @Override public CompletionKind getKind() { return CompletionKind.LOCAL_VAR; }
    @Override public String getJavadoc() { return description; }
    public String getDescription() { return description; }
    public SnippetTemplate getTemplate() { return template; }
    public String getContext() { return context; }
}
