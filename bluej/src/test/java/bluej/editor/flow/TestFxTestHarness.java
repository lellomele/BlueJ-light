/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestFxTestHarness extends FXTest
{
    @Test
    public void propagatesAssertionsInsteadOfReportingTimeouts()
    {
        AssertionError expected = new AssertionError("original assertion");
        try {
            fx_(() -> { throw expected; });
            fail("Missing assertion");
        }
        catch (AssertionError actual) { assertSame(expected, actual); }
        assertEquals(Integer.valueOf(42), fx(() -> 42));
    }

    @Test
    public void propagatesSupplierExceptionsWithoutBlockingFollowingActions()
    {
        IllegalStateException expected = new IllegalStateException("original failure");
        try {
            fx(() -> { throw expected; });
            fail("Missing exception");
        }
        catch (IllegalStateException actual) { assertSame(expected, actual); }
        fx_(() -> { });
    }
}
