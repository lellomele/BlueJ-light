/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import java.io.IOException;
import threadchecker.OnThread;
import threadchecker.Tag;

/** An expected user decision, not an internal failure to log with a stack trace during editing. */
@OnThread(Tag.Any)
public final class SourceChangeRequired extends IOException
{
    public SourceChangeRequired(String message) { super(message); }
    public SourceChangeRequired(String message, Throwable cause) { super(message,cause); }
}
