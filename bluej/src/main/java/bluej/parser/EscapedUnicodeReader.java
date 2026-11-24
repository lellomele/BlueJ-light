/* BlueJ light modifications, Copyright (C) 2026 Raffaele Mele. Modified 2026-11-24. GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2012,2013,2015,2022  Michael Kolling and John Rosenberg 

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
package bluej.parser;

import bluej.parser.lexer.LineColPos;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * This is a Reader processes the stream from another reader, replacing unicode escape
 * sequences (backslash-'u'-XXXX where XXXX is a four digit hexadecimal number) with the
 * characters they represent.
 * 
 * This is suitable for a java pre-processor, before the lexer stage. It allows the lexer
 * to correctly recognize keywords, identifiers etc. which have embedded unicode escape
 * sequences.
 * 
 * @author Davin McCall
 */
@OnThread(Tag.Any)
public final class EscapedUnicodeReader extends Reader
{
    private Reader sourceReader;
    // The unread suffix is kept at the end so another push can prepend in place.
    private char[] pushedBack;
    private int remaining;

    private boolean charIsBuffered;
    private int bufferedChar;
    
    private LineColPos lineColPos = new LineColPos(1, 1, 0); // position within source stream
    
    public EscapedUnicodeReader(Reader source)
    {
        sourceReader = Objects.requireNonNull(source);
    }
    
    public void setLineColPos(LineColPos lineColPos)
    {
        this.lineColPos = lineColPos;
    }

    @Override
    public int read(char [] buffer, int off, int len) throws IOException
    {
        int numRead = 0;
        while (len > 0) {
            try {
                int r = getChar();
                if (r == -1)
                    break;
                buffer[off++] = (char) r;
                len--;
                numRead++;
            }
            catch (IOException ioe) {
                // If we got an exception, but successfully read some characters,
                // we should return those characters.
                if (numRead == 0) {
                    throw ioe;
                }
                break;
            }
        }

        // if we failed to read anything, it's due to end-of-stream
        if (numRead == 0 && len != 0)
            numRead = -1;

        return numRead;
    }

    @Override
    public void close() throws IOException
    {
        if (sourceReader != null) {
            sourceReader.close();
            sourceReader = null;
            pushedBack = null;
            remaining = 0;
            charIsBuffered = false;
        }
    }

    /**
     * Get a single character, which may be an escaped unicode character (\\uXXXX, with a
     * single leading backslash)
     */
    private int getChar() throws IOException
    {
        if (sourceReader == null)
            throw new IOException("Stream closed");
        int rchar;
        if (charIsBuffered) {
            charIsBuffered = false;
            if (bufferedChar != -1) {
                processChar((char) bufferedChar);
            }
            return bufferedChar;
        }
        rchar = readSourceChar();

        if (rchar == '\\') {
            // This could be the beginning of an escaped unicode sequence,
            // \\uXXXX (with only a single backslash)
            int nchar = readRawChar();

            if (nchar == 'u') {
                lineColPos = lineColPos.offsetSameLineBy(1);
                return readEscapedUnicodeSequence();
            }
            putBuffer(nchar);             
            return '\\';
        }

        return rchar;
    }

    private void putBuffer(int nchar)
    {
        bufferedChar = nchar;
        charIsBuffered = true;
    }

    private int readEscapedUnicodeSequence() throws IOException
    {
        // The Java Language Spec specifies that any number of 'u' characters may appear in sequence
        // as part of a unicode escape.
        int uc = readRawChar();
        while (uc == 'u') {
            processChar((char)uc);
            uc = readRawChar();
        }
        
        int val = Character.digit((char) uc, 16);
        if (val == -1) {
            putBuffer(uc);
            return 0xFFFF;
        }
        processChar((char)uc);
        
        int i = 0;
        do {
            val *= 0x10;
            uc = readRawChar();
            int digitVal = Character.digit((char) uc, 16);
            if (digitVal == -1) {
                putBuffer(uc);
                return 0xFFFF;
            }
            processChar((char)uc);
            val += digitVal;
            i++;
        } while (i < 3);
        
        return val;
    }

    private int readSourceChar() throws IOException
    {
        int rchar = readRawChar();
        if (rchar != -1) {
            processChar((char) rchar);
        }
        return rchar;
    }

    private int readRawChar() throws IOException
    {
        if (remaining == 0)
            return sourceReader.read();
        int index = pushedBack.length - remaining;
        remaining--;
        return pushedBack[index];
    }
    
    private void processChar(char ch)
    {
        if (ch == '\n') {
            lineColPos = new LineColPos(lineColPos.line() + 1, 1, lineColPos.position() + 1);
        }
        else {
            lineColPos = lineColPos.offsetSameLineBy(1);
        }
    }
        
    public LineColPos getLineColPos()
    {
        return lineColPos;
    }

    @Override
    public int read() throws IOException
    {
        return getChar();
    }

    /**
     * Push the given content back on to the front of the reader, and set the current position
     * to the given position.
     */
    public void pushBack(String content, LineColPos lineColPos) throws IOException
    {
        if (sourceReader == null)
            throw new IOException("Stream closed");
        if (!content.isEmpty()) {
            int required = Math.addExact(remaining, content.length());
            if (pushedBack == null || pushedBack.length < required) {
                char[] larger = new char[required];
                if (remaining != 0)
                    System.arraycopy(pushedBack, pushedBack.length - remaining, larger, required - remaining, remaining);
                pushedBack = larger;
            }
            content.getChars(0, content.length(), pushedBack, pushedBack.length - required);
            remaining = required;
        }
        this.lineColPos = lineColPos;
    }
}
