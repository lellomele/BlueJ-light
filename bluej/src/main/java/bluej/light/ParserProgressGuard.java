/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.light;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class ParserProgressGuard
{
    private Object node;
    private int position = -1, size = -1, nodeSize = -1, repeats;
    public boolean progress(int position, int size, Object node, int nodeSize)
    {
        if (this.position == position && this.size == size && this.node == node && this.nodeSize == nodeSize) repeats++;
        else { this.position = position; this.size = size; this.node = node; this.nodeSize = nodeSize; repeats = 0; }
        return repeats < 128;
    }
    public void reset() { node = null; position = size = nodeSize = -1; repeats = 0; }
}
