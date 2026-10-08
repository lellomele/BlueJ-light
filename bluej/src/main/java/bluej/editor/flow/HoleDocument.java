/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 2019,2020,2022  Michael Kolling and John Rosenberg

 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 

 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 

 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 This file is subject to the Classpath exception as provided in the
 LICENSE.txt file that accompanied this code.
 */
package bluej.editor.flow;

import bluej.extensions2.editor.DocumentListener;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

public class HoleDocument implements Document
{
    // How much extra should we grow the array by when needed?
    private static final int GROWTH_MARGIN = 256;
    // Array which always contains, in order:
    //  content, up to holeStart (exclusive) -- may be empty if holeStart == 0
    //  a hole in the middle, from holeStart (inclusive) to holeEnd (exclusive) -- may be empty if holeStart == holeEnd
    //  more content, from holeEnd (inclusive) to end of array -- may be empty if holdEnd == content.length
    private char[] content;
    private int holeStart; // Index of first character in the hole.
    private int holeEnd; // Index of first character in array after the hole
    
    private final ArrayList<LineInformation> lineInformation = new ArrayList<>();
    private String cachedLongestLine;

    /**
     * We need to know all the positions so we can update them all.  But we don't want
     * to retain them and cause a memory leak.  Rather than having a deregistration system,
     * we just keep weak references and thus let them fall out of memory once the caller
     * of trackPosition no longer keeps track of them.
     */
    private final ArrayList<WeakReference<TrackedPosition>> trackedPositions = new ArrayList<>();
    private final List<DocumentListener> listeners = new ArrayList<>();
    private final List<bluej.utility.javafx.FXPlatformRunnable> afterEditListeners = new ArrayList<>();

    public HoleDocument()
    {
        content = new char[128];
        holeStart = 0;
        holeEnd = content.length;
        lineInformation.add(new LineInformation(0));
    }

    @Override
    public void replaceText(int startCharIncl, int endCharExcl, String text)
    {
        cachedLongestLine = null;
        int indexToInsertNewNewlines = getLineFromPosition(startCharIncl) + 1;
        int endLine = getLineFromPosition(endCharExcl) + 1;
        int linesRemoved = endLine - indexToInsertNewNewlines;
        lineInformation.subList(indexToInsertNewNewlines, endLine).clear();
        
        // Start by moving the hole to the modification location:
        if (holeStart < startCharIncl)
        {
            moveLineOffsets(holeStart, startCharIncl, -(holeEnd - holeStart));
            // Hole is too early, shuffle content backwards
            System.arraycopy(content, holeEnd, content, holeStart, startCharIncl - holeStart);
            holeEnd += (startCharIncl - holeStart);
            holeStart = startCharIncl;
        }
        else if (holeStart > startCharIncl)
        {
            moveLineOffsets(startCharIncl, holeStart, holeEnd - holeStart);
            // Hole is too late, shuffle content forwards
            int amountToMove = holeStart - startCharIncl;
            System.arraycopy(content, startCharIncl, content, holeEnd - amountToMove, amountToMove);
            holeEnd -= amountToMove;
            holeStart = startCharIncl;
        }

        // Now hole is at the right position.
        
        // Store content being replaced:
        String replaced = new String(content, holeEnd, endCharExcl - startCharIncl);

        // Remove existing content by deleting at end of hole
        holeEnd += (endCharExcl - startCharIncl);
        
        // Check if we have enough space for the new content:
        int additionAmount = text.length();
        if (holeEnd - holeStart < additionAmount)
        {
            // Hole not big enough, need to enlarge:
            int extraLength = Math.max(additionAmount - (holeEnd - holeStart),
                Math.max(GROWTH_MARGIN, content.length / 2));
            char[] newContent = new char[content.length + extraLength];
            System.arraycopy(content, 0, newContent, 0, holeStart);
            System.arraycopy(content, holeEnd, newContent, holeEnd + extraLength, content.length - holeEnd);
            for (int line = getLineFromPosition(holeStart) + 1; line < lineInformation.size(); line++)
                lineInformation.get(line).bufferOffset += extraLength;
            content = newContent;
            holeEnd += extraLength;
        }
        
        // Add new content by copying into hole
        System.arraycopy(text.toCharArray(), 0, content, holeStart, text.length());
        holeStart += text.length();

        for (Iterator<WeakReference<TrackedPosition>> iterator = trackedPositions.iterator(); iterator.hasNext(); )
        {
            WeakReference<TrackedPosition> trackedPositionRef = iterator.next();
            TrackedPosition trackedPosition = trackedPositionRef.get();
            if (trackedPosition == null)
            {
                iterator.remove();
            }
            else
            {
                trackedPosition.updateTrackedPosition(startCharIncl, endCharExcl, text.length());
            }
        }
        
        int linesAdded = 0;
        // Now add new positions for the added newlines:
        for (int i = 0; i < text.length(); i++)
        {
            if (text.charAt(i) == '\n')
            {
                lineInformation.add(indexToInsertNewNewlines, new LineInformation(i + 1 + startCharIncl));
                indexToInsertNewNewlines += 1;
                linesAdded += 1;
            }
        }

        // Take a copy in case one of the listeners removes themselves
        // (would lead to a concurrent modification exception if we iterated over the original list):
        List<DocumentListener> listenersCopy = new ArrayList<>(this.listeners);
        for (bluej.extensions2.editor.DocumentListener listener : listenersCopy)
        {
            listener.textReplaced(startCharIncl, replaced, text, linesRemoved, linesAdded);
        }
        if (!afterEditListeners.isEmpty())
            for (var listener : new ArrayList<>(afterEditListeners))
                listener.run();
    }

    public void addAfterEditListener(bluej.utility.javafx.FXPlatformRunnable listener) { afterEditListeners.add(listener); }
    public void removeAfterEditListener(bluej.utility.javafx.FXPlatformRunnable listener) { afterEditListeners.remove(listener); }

    // Line starts use physical buffer offsets: nearby edits only change the gap, not every line.
    private void moveLineOffsets(int fromExclusive, int toInclusive, int delta)
    {
        if (delta == 0)
            return;
        int first = getLineFromPosition(fromExclusive) + 1;
        int last = getLineFromPosition(toInclusive);
        for (int line = first; line <= last; line++)
            lineInformation.get(line).bufferOffset += delta;
    }

    private int logicalLineStart(int index)
    {
        int physical = lineInformation.get(index).bufferOffset;
        return physical <= holeStart ? physical : physical - (holeEnd - holeStart);
    }

    @Override
    public String getFullContent()
    {
        return new String(content, 0, holeStart) + new String(content, holeEnd, content.length - holeEnd);
    }

    @Override
    public int getLength()
    {
        return content.length - (holeEnd - holeStart);
    }

    @Override
    public int getLineFromPosition(int position)
    {
        int index = Collections.binarySearch(getLineStartPositions(), position);
        if (index >= 0)
        {
            // Line begins there exactly:
            return index;
        }
        else
        {
            return -2-index;
        }
    }

    /**
     * A read-only list of the current line start positions in the document.
     */
    private List<Integer> getLineStartPositions()
    {
        // No need to copy when we are read-only, just make a dummy list object: 
        return new AbstractListRandomAccess<Integer>() {
            @Override
            public int size()
            {
                return lineInformation.size();
            }

            @Override
            public Integer get(int index)
            {
                if (index == 0)
                {
                    return 0;
                }
                else
                {
                    return logicalLineStart(index);
                }
            }
        };
    }

    @Override
    public int getColumnFromPosition(int position)
    {
        int lineStartIndex = getLineFromPosition(position);
        if (lineStartIndex <= 0)
        {
            // First line:
            return position;
        }
        else
        {
            return position - logicalLineStart(lineStartIndex);
        }
    }

    @Override
    public TrackedPosition trackPosition(int position, Bias bias)
    {
        TrackedPosition trackedPosition = new TrackedPosition(this, position, bias);
        trackedPositions.add(new WeakReference<>(trackedPosition));
        return trackedPosition;
    }
    
    public List<CharSequence> getLines()
    {
        List<Integer> lineStarts = getLineStartPositions();
        
        return new AbstractListRandomAccess<CharSequence>()
        {
            @Override
            public CharSequence get(int lineIndex)
            {
                int startChar = lineStarts.get(lineIndex);
                int endChar = lineIndex + 1 >= lineStarts.size() ? getLength() : (lineStarts.get(lineIndex + 1) - 1);
                return subSequence(startChar, endChar);
            }

            @Override
            public int size()
            {
                return lineStarts.size();
            }
        };
    }

    // Only valid while document content doesn't change!
    private CharSequence subSequence(int startChar, int endChar)
    {
        return new CharSequence()
        {
            @Override
            public int length()
            {
                return endChar - startChar;
            }

            @Override
            public char charAt(int index)
            {
                if (index + startChar < holeStart)
                    return content[index + startChar];
                else
                    return content[index + startChar + holeEnd - holeStart];
            }

            @Override
            public CharSequence subSequence(int start, int end)
            {
                return HoleDocument.this.subSequence(startChar + start, startChar + end);
            }

            @Override
            public String toString()
            {
                String beforeHole = startChar < holeStart ? new String(content, startChar, Math.min(endChar, holeStart) - startChar) : "";
                String afterHole = endChar > holeStart ? new String(content, Math.max(startChar + holeEnd - holeStart, holeEnd), endChar + holeEnd - holeStart - Math.max(startChar + holeEnd - holeStart, holeEnd)) : "";
                return beforeHole + afterHole;
            }

            @Override
            public int hashCode()
            {
                return toString().hashCode();
            }

            @Override
            public boolean equals(Object obj)
            {
                if (obj instanceof CharSequence)
                {
                    CharSequence cs = (CharSequence) obj;
                    if (length() != cs.length())
                        return false;
                    for (int i = 0; i < length(); i++)
                    {
                        if (charAt(i) != cs.charAt(i))
                            return false;
                    }
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public CharSequence getContent(int startCharIncl, int endCharExcl)
    {
        return subSequence(startCharIncl, endCharExcl);
    }

    @Override
    public int getLineStart(int lineNumber)
    {
        return getLineStartPositions().get(lineNumber);
    }

    @Override
    public int getLineEnd(int lineNumber)
    {
        if (lineNumber + 1 < lineInformation.size())
        {
            return getLineStartPositions().get(lineNumber + 1) - 1;
        }
        else
        {
            return getLength();
        }
    }

    @Override
    public int getLineCount()
    {
        return lineInformation.size();
    }

    @Override
    public void addListener(boolean atStart, DocumentListener listener)
    {
        if (atStart)
            listeners.add(0, listener);
        else
            listeners.add(listener);
    }
    
    public void removeListener(DocumentListener listener)
    {
        // Remove all by reference equality:
        // (Since this is exposed to extensions, it's possible they might implement
        // .equals() themselves but we want to ignore that).
        listeners.removeIf(l -> l == listener);
    }

    public boolean hasLineAttribute(int lineIndex, Object attributeKey)
    {
        if (lineIndex >= 0 && lineIndex < lineInformation.size())
        {
            return lineInformation.get(lineIndex).lineAttributes.containsKey(attributeKey);
        }
        else
        {
            return false;
        }
    }
    
    public void addLineAttribute(int lineIndex, Object key, Object value)
    {
        if (lineIndex >= 0 && lineIndex < lineInformation.size())
        {
            lineInformation.get(lineIndex).lineAttributes.put(key, value);
        }
    }
    
    public void removeLineAttributeThroughout(Object key)
    {
        for (LineInformation information : lineInformation)
        {
            information.lineAttributes.remove(key);
        }
    }

    @Override
    public Reader makeReader(int startPos, int endPos)
    {
        return new HoleReader(startPos, endPos);
    }

    /**
     * Gets the content of the longest line in the document, as measured by number of chars.
     */
    public String getLongestLine()
    {
        if (cachedLongestLine != null)
            return cachedLongestLine;
        int longestIndex = 0;
        int longestLength = 0;
        for (int line = 0; line < lineInformation.size(); line++)
        {
            int start = logicalLineStart(line);
            int end = line + 1 < lineInformation.size() ? logicalLineStart(line + 1) - 1 : getLength();
            int length = end - start;
            if (length > longestLength)
            {
                longestLength = length;
                longestIndex = line;
            }
        }
        cachedLongestLine = getLines().get(longestIndex).toString();
        return cachedLongestLine;
    }

    private static class LineInformation
    {
        private int bufferOffset;
        private final HashMap<Object, Object> lineAttributes = new HashMap<>();

        public LineInformation(int bufferOffset)
        {
            this.bufferOffset = bufferOffset;
        }
    }
    
    // Adapted from StringReader
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    private class HoleReader extends Reader
    {
        private int next;
        private int mark = 0;
        private final int end; 
        
        private HoleReader(int start, int end)
        {
            this.next = start;
            this.end = end;
        }

        public int read()
        {
            if (next >= end)
            {
                return -1;
            }
            else
            {
                if (next < holeStart)
                {
                    return content[next++];
                }
                else
                {
                    return content[next++ + (holeEnd - holeStart)];
                }
            }
        }
        
        public int read(char cbuf[], int off, int len)
        {
            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0))
            {
                throw new IndexOutOfBoundsException();
            }
            else if (len == 0)
            {
                return 0;
            }
            else if (next >= end)
            {
                return -1;
            }
            
            int total = Math.min(end - next, len);
            int toCopy = total;
            if (next < holeStart)
            {
                // Copy before the hole:
                System.arraycopy(content, next, cbuf, off, Math.min(toCopy, holeStart - next));
                off += holeStart - next;
                toCopy -= (holeStart - next);
            }
            if (toCopy > 0)
            {
                // Copy after hole:
                System.arraycopy(content, next + (holeEnd - holeStart) + Math.max(0, holeStart - next), cbuf, off, toCopy);
            }
            next += total;
            return total;
        }

        public long skip(long ns)
        {
            if (next >= end)
            {
                return 0;
            }
            // Bound skip by beginning and end of the source
            long n = Math.min(end - next, ns);
            n = Math.max(-next, n);
            next += n;
            return n;
        }
        
        public boolean ready()
        {
            return true;
        }
        
        public boolean markSupported()
        {
            return true;
        }
        
        public void mark(int readAheadLimit)
        {
            if (readAheadLimit < 0)
            {
                throw new IllegalArgumentException("Read-ahead limit < 0");
            }
            mark = next;
        }
        
        public void reset()
        {
            next = mark;
        }
        
        public void close()
        {
        }
    }

    // Anonymous inner classes can't extend and implements so we introduce this helper:
    // Important to implement empty RandomAccess interface for use of Collections.binarySearch()
    private static abstract class AbstractListRandomAccess<T> extends AbstractList<T> implements RandomAccess
    {
    }

}
