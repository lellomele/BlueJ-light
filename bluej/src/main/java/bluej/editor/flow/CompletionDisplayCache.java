/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.AssistContentThreadSafe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Caches only immutable signature text, never resolved members or parser nodes. */
final class CompletionDisplayCache
{
    private final Map<List<String>, String> signatures = new HashMap<>();
    private long revision = Long.MIN_VALUE;
    private Object classLoader;

    void setContext(long revision, Object classLoader)
    {
        if (this.revision != revision || this.classLoader != classLoader)
        {
            signatures.clear();
            this.revision = revision;
            this.classLoader = classLoader;
        }
    }

    String parameters(AssistContentThreadSafe candidate)
    {
        if (candidate.getParams() == null) return "";
        List<String> types = candidate.getParams().stream().map(p -> p.getQualifiedType()).toList();
        String cached = signatures.get(types);
        if (cached != null) return cached;
        if (signatures.size() >= 256) signatures.clear();
        String display = "(" + candidate.getParams().stream().map(p -> p.getUnqualifiedType())
            .collect(java.util.stream.Collectors.joining(", ")) + ")";
        signatures.put(types, display);
        return display;
    }
}
