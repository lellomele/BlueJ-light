/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.util.List;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class VisibleRanges
{
    /** Search results are ordered by starting offset and displayed on their first line. */
    public static List<int[]> between(List<int[]> sorted, int first, int last)
    { return sorted.subList(lowerBound(sorted, first), lowerBound(sorted, last)); }
    private static int lowerBound(List<int[]> sorted, int offset)
    {
        int low = 0, high = sorted.size();
        while (low < high)
        { int mid = (low + high) >>> 1; if (sorted.get(mid)[0] < offset) low = mid + 1; else high = mid; }
        return low;
    }
}
