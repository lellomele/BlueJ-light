/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Numbered tab stops, linked occurrences, defaults and a final $0 caret position. */
@OnThread(Tag.Any)
public final class SnippetTemplate
{
    @OnThread(Tag.Any) public record Stop(int index, int start, int end) {}
    @OnThread(Tag.Any) public record Expansion(String text, List<Stop> stops, int exit) {}
    private final String body;

    public SnippetTemplate(String body) { this.body = body; }

    public Expansion expand(String indent, String tab)
    {
        String source = body.replace("\t", tab).replace("\n", "\n" + indent);
        StringBuilder text = new StringBuilder();
        List<Stop> stops = new ArrayList<>();
        Map<Integer, String> defaults = new HashMap<>();
        int exit = -1;
        for (int pos = 0; pos < source.length();)
        {
            char c = source.charAt(pos++);
            if (c == '\\' && pos < source.length() && "$}\\".indexOf(source.charAt(pos)) >= 0)
            {
                text.append(source.charAt(pos++));
                continue;
            }
            if (c != '$' || pos == source.length()
                || (source.charAt(pos) != '{' && !Character.isDigit(source.charAt(pos))))
            {
                text.append(c);
                continue;
            }
            boolean braced = source.charAt(pos) == '{';
            if (braced) pos++;
            int numberStart = pos;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
            if (numberStart == pos) throw new IllegalArgumentException("Expected numbered tab stop");
            int number = Integer.parseInt(source.substring(numberStart, pos));
            String value = "";
            if (braced)
            {
                if (pos < source.length() && source.charAt(pos) == ':')
                {
                    pos++;
                    StringBuilder defaultText = new StringBuilder();
                    while (pos < source.length() && source.charAt(pos) != '}')
                    {
                        char next = source.charAt(pos++);
                        if (next == '\\' && pos < source.length() && "$}\\".indexOf(source.charAt(pos)) >= 0)
                            next = source.charAt(pos++);
                        defaultText.append(next);
                    }
                    value = defaultText.toString();
                }
                if (pos >= source.length() || source.charAt(pos++) != '}')
                    throw new IllegalArgumentException("Unclosed tab stop");
            }
            if (defaults.containsKey(number)) value = defaults.get(number);
            else defaults.put(number, value);
            int start = text.length();
            text.append(value);
            if (number == 0) exit = start;
            else stops.add(new Stop(number, start, text.length()));
        }
        return new Expansion(text.toString(), List.copyOf(stops), exit < 0 ? text.length() : exit);
    }

}
