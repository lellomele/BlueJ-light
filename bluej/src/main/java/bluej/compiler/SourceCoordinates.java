/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.compiler;

import java.util.ArrayList;
import java.util.Arrays;

/** Physical UTF-16 source coordinates, independent of javac's expanded tab columns. */
final class SourceCoordinates
{
    record Position(long line, long column) { }
    private final int[] lineStarts;
    private final int length;

    SourceCoordinates(CharSequence source)
    {
        length = source.length();
        ArrayList<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < length; i++)
        {
            char value = source.charAt(i);
            if (value == '\r')
            {
                if (i + 1 < length && source.charAt(i + 1) == '\n') i++;
                starts.add(i + 1);
            }
            else if (value == '\n') starts.add(i + 1);
        }
        lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    Position at(long offset)
    {
        int position = (int)Math.max(0, Math.min(offset, length));
        int index = Arrays.binarySearch(lineStarts, position);
        if (index < 0) index = -index - 2;
        return new Position(index + 1L, position - lineStarts[index] + 1L);
    }
}
