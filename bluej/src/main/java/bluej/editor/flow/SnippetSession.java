/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import bluej.extensions2.editor.DocumentListener;
import bluej.utility.javafx.FXPlatformConsumer;
import bluej.utility.javafx.FXPlatformRunnable;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public final class SnippetSession implements DocumentListener
{
    @OnThread(Tag.FXPlatform)
    public interface Selection { void select(int start, int end); }

    private record Range(TrackedPosition start, TrackedPosition end) {}
    private final HoleDocument document;
    private final List<List<Range>> groups;
    private final TrackedPosition exit;
    private final Selection selection;
    private final FXPlatformConsumer<FXPlatformRunnable> appendUndo;
    private final FXPlatformRunnable afterEdit = this::mirror;
    private int current;
    private int previousStart;
    private int previousEnd;
    private boolean active = true;
    private boolean mirroring;
    private boolean pending;

    public SnippetSession(HoleDocument document, SnippetTemplate.Expansion expansion, int base,
        Selection selection, FXPlatformConsumer<FXPlatformRunnable> appendUndo)
    {
        this.document = document;
        this.selection = selection;
        this.appendUndo = appendUndo;
        TreeMap<Integer, List<Range>> grouped = new TreeMap<>();
        for (SnippetTemplate.Stop stop : expansion.stops())
            grouped.computeIfAbsent(stop.index(), ignored -> new ArrayList<>()).add(new Range(
                document.trackPosition(base + stop.start(), Document.Bias.BACK),
                document.trackPosition(base + stop.end(), Document.Bias.FORWARD)));
        groups = List.copyOf(grouped.values());
        exit = document.trackPosition(base + expansion.exit(), Document.Bias.FORWARD);
        document.addListener(false, this);
        document.addAfterEditListener(afterEdit);
        if (groups.isEmpty()) finish(); else selectCurrent();
    }

    public boolean isActive() { return active; }

    public boolean advance(boolean backwards)
    {
        if (!active) return false;
        if (backwards) current = Math.max(0, current - 1);
        else current++;
        if (current == groups.size()) finish(); else selectCurrent();
        return true;
    }

    private void selectCurrent()
    {
        Range range = groups.get(current).getFirst();
        previousStart = range.start().getPosition();
        previousEnd = range.end().getPosition();
        selection.select(previousStart, previousEnd);
    }

    private void finish()
    {
        int position = exit.getPosition();
        close();
        selection.select(position, position);
    }

    public void close()
    {
        active = false;
        document.removeListener(this);
        document.removeAfterEditListener(afterEdit);
    }

    @Override
    public void textReplaced(int start, String oldText, String text, int removedLines, int addedLines)
    {
        if (!active || mirroring) return;
        if (start < previousStart || start + oldText.length() > previousEnd) { close(); return; }
        pending = true;
    }

    private void mirror()
    {
        if (!active || mirroring || !pending) return;
        pending = false;
        mirroring = true;
        try
        {
            Range primary = groups.get(current).getFirst();
            String text = document.getContent(primary.start().getPosition(), primary.end().getPosition()).toString();
            List<Range> others = new ArrayList<>(groups.get(current).subList(1, groups.get(current).size()));
            others.sort(Comparator.comparingInt((Range range) -> range.start().getPosition()).reversed());
            appendUndo.accept(() -> {
                for (Range range : others)
                    document.replaceText(range.start().getPosition(), range.end().getPosition(), text);
            });
            previousStart = primary.start().getPosition();
            previousEnd = primary.end().getPosition();
        }
        finally { mirroring = false; }
    }
}
