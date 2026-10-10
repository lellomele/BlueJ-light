/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.compiler;

import threadchecker.OnThread;
import threadchecker.Tag;

/** Compilation intent retained while a target or debugger is busy. */
@OnThread(Tag.Any)
public record CompileRequest(CompileReason reason, CompileType type)
{
    public CompileRequest merge(CompileRequest newer)
    {
        if (newer.type == CompileType.EXPLICIT_USER_COMPILE || type == CompileType.ERROR_CHECK_ONLY
            || (type != CompileType.EXPLICIT_USER_COMPILE && newer.type != CompileType.ERROR_CHECK_ONLY))
            return newer;
        return this;
    }
}
