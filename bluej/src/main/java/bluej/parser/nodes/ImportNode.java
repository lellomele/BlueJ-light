/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-11-24. GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 2011  Michael Kolling and John Rosenberg 
 
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
package bluej.parser.nodes;

import bluej.parser.lexer.LocatableToken;

/**
 * A parsed node representing an import statement.
 * 
 * @author Davin McCall
 */
public class ImportNode extends InnerNode
{
    private final LocatableToken terminator;

    public ImportNode(JavaParentNode parent, LocatableToken terminator)
    {
        super(parent);
        this.terminator = terminator;
    }

    LocatableToken getTerminator()
    {
        return terminator;
    }
    
    @Override
    protected boolean marksOwnEnd()
    {
        return true;
    }
}
