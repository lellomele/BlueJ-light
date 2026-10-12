/*
 This file is part of the BlueJ program.
 Copyright (C) 2016,2019  Michael Kolling and John Rosenberg

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
package bluej.compiler;

import java.io.File;
import java.io.Serializable;

/**
 * Information about a file to be fed to the compiler as input.
 */
public class CompileInputFile implements Serializable
{
    private final File actualJavaFileForCompiler;
    private final File sourceFileToRecord;

    /**
     *
     * @param actualJavaFileForCompiler The Java source file that the compiler will read.
     * @param sourceFileToRecord The same Java source file, retained for compiler diagnostics.
     */
    public CompileInputFile(File actualJavaFileForCompiler, File sourceFileToRecord)
    {
        this.actualJavaFileForCompiler = actualJavaFileForCompiler;
        this.sourceFileToRecord = sourceFileToRecord;
    }

    /**
     * The Java source file that gets fed to the compiler.
     */
    public File getJavaCompileInputFile()
    {
        return actualJavaFileForCompiler;
    }

    /**
     * The source file being edited by the user.
     */
    public File getUserSourceFile()
    {
        return sourceFileToRecord;
    }

    public boolean isValid()
    {
        return (actualJavaFileForCompiler !=null && sourceFileToRecord != null);
    }
}
