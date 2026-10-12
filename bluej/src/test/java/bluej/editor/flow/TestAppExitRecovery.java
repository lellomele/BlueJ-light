/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.GuiHandler;
import bluej.Main;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestAppExitRecovery extends FXTest
{
    @Test public void unsuccessfulCleanupDoesNotExitOrUnloadTheApplication() throws Exception
    {
        Field field = Main.class.getDeclaredField("guiHandler");
        field.setAccessible(true);
        GuiHandler previous = (GuiHandler)field.get(null);
        AtomicInteger calls = new AtomicInteger();
        GuiHandler refusing = (GuiHandler)Proxy.newProxyInstance(GuiHandler.class.getClassLoader(),
            new Class<?>[] {GuiHandler.class}, (object, method, args) -> {
                if (method.getName().equals("doExitCleanup")) { calls.incrementAndGet(); return false; }
                return null;
            });
        try {
            Main.setGuiHandler(refusing);
            fx_(Main::doQuit);
            assertEquals(1, calls.get());
            assertEquals(Integer.valueOf(42), fx(() -> 42));
        }
        finally { Main.setGuiHandler(previous); }
    }
}
