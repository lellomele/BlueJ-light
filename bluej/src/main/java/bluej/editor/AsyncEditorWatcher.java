/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor;

import java.util.concurrent.CompletableFuture;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Optional acknowledgement of model/path work following the physical source write. */
@OnThread(Tag.FXPlatform)
public interface AsyncEditorWatcher extends EditorWatcher
{
    CompletableFuture<Void> saveEventAsync(Editor editor);
}
