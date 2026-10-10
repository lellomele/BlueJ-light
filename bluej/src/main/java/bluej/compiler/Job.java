/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2011,2012,2016,2020,2022  Michael Kolling and John Rosenberg
 
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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import bluej.Config;
import bluej.utility.Debug;
import bluej.classmgr.BPClassLoader;

/**
 * A compiler "job". A list of filenames to compile + parameters.
 * Jobs are held in a queue by the CompilerThread, which compiles them
 * by running the job's "compile" method.
 *
 * @author  Michael Cahill
 */
record Job(CompileInputFile[] sources, Compiler compiler, CompileObserver observer, BPClassLoader bpClassLoader, File destDir,
           boolean internal, // true for compiling shell files,
                             // or user files if we want to suppress
                             // "unchecked" warnings, false otherwise
           List<String> userCompileOptions, Charset fileCharset, CompileType type, CompileReason reason)
{
    /**
     * Generator for unique ascending compilation identifiers.  It doesn't matter if it's shared between
     * packages or between projects, it just needs to be unique and ascending.  An individual user won't manage
     * 2 billion compilations in a single session, so integer is fine:
     */
    private static final AtomicInteger nextCompilationSequence = new AtomicInteger(1);

    /**
     * Compile this job
     */
    public void compile()
    {
        int compilationSequence = nextCompilationSequence.getAndIncrement();
        boolean successful = false;

        try {
            if(observer != null) {
                observer.startCompile(sources, reason, type, compilationSequence);
            }

            if(destDir != null) {
                compiler.setDestDir(destDir);
            }

            compiler.setClasspath(bpClassLoader.getClassPathAsFiles());

            compiler.setBootClassPath(null);

            File[] actualSourceFiles = new File[sources.length];
            for (int i = 0; i < sources.length; i++)
            {
                actualSourceFiles[i] = sources[i].getJavaCompileInputFile();
            }

            successful = compiler.compile(actualSourceFiles, observer, internal, userCompileOptions, fileCharset, type);
        }
        catch (java.util.concurrent.CancellationException cancelled) { }
        catch (Exception | LinkageError | AssertionError failure) {
            Debug.reportError(Config.getString("compileException"), failure);
        }
        finally {
            if (observer != null) {
                try { observer.endCompile(sources, successful, type, compilationSequence); }
                catch (RuntimeException | LinkageError | AssertionError failure) {
                    Debug.reportError("Compilation completion callback failed", failure);
                }
            }
        }
    }
}
