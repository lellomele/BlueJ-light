/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.light;
import threadchecker.OnThread;
import threadchecker.Tag;

/** A deadline applies only to the current incremental UI parse, not javac or background tools. */
@OnThread(Tag.Any)
public final class ParserBudget
{
    private static final ThreadLocal<State> current = new ThreadLocal<>();
    private static final class State { final long deadline; int checks; State(long deadline) { this.deadline = deadline; } }
    public static final class Scope implements AutoCloseable
    {
        private final State previous;
        private Scope(State previous) { this.previous = previous; }
        @Override public void close() { if (previous == null) current.remove(); else current.set(previous); }
    }
    public static Scope start(long nanoseconds)
    {
        State previous = current.get(); long deadline = System.nanoTime() + nanoseconds;
        current.set(new State(previous == null ? deadline : Math.min(previous.deadline, deadline)));
        return new Scope(previous);
    }
    public static boolean active() { return current.get() != null; }
    public static void checkpoint()
    {
        State state = current.get();
        if (state != null && (++state.checks & 511) == 0 && System.nanoTime() - state.deadline >= 0)
            throw new IllegalStateException("Incremental parser time limit exceeded");
    }
}
