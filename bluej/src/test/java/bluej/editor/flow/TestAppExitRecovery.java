/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.GuiHandler;
import bluej.Main;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
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
                if (method.getName().equals("doExitCleanupAsync")) { calls.incrementAndGet(); return CompletableFuture.completedFuture(false); }
                return null;
            });
        try {
            Main.setGuiHandler(refusing);
            fx_(Main::doQuit);
            assertEquals(1, calls.get());
            assertEquals(Integer.valueOf(42), fx(() -> 42));
            fx_(Main::doQuit);assertEquals(2,calls.get());
        }
        finally { Main.setGuiHandler(previous); }
    }

    @Test public void pendingExitKeepsTheUiResponsiveAndCoalescesRepeatedCloseRequests() throws Exception
    {
        Field field=Main.class.getDeclaredField("guiHandler");field.setAccessible(true);
        GuiHandler previous=(GuiHandler)field.get(null);AtomicInteger calls=new AtomicInteger();
        CompletableFuture<Boolean> pending=new CompletableFuture<>();
        GuiHandler delayed=(GuiHandler)Proxy.newProxyInstance(GuiHandler.class.getClassLoader(),new Class<?>[]{GuiHandler.class},
            (object,method,args) -> {
                if(method.getName().equals("doExitCleanupAsync")){calls.incrementAndGet();return pending;}
                return null;
            });
        try
        {
            Main.setGuiHandler(delayed);fx_(() -> {Main.doQuit();Main.doQuit();});
            assertEquals(1,calls.get());assertEquals(Integer.valueOf(42),fx(() -> 42));
            pending.complete(false);fx_(() -> {});fx_(Main::doQuit);assertEquals(2,calls.get());
        }
        finally {pending.complete(false);fx_(() -> {});Main.setGuiHandler(previous);}
    }

    @Test public void synchronousCleanupFailureDoesNotPermanentlyDisableQuit() throws Exception
    {
        Field field=Main.class.getDeclaredField("guiHandler");field.setAccessible(true);
        GuiHandler previous=(GuiHandler)field.get(null);AtomicInteger calls=new AtomicInteger();
        GuiHandler faulty=(GuiHandler)Proxy.newProxyInstance(GuiHandler.class.getClassLoader(),new Class<?>[]{GuiHandler.class},
            (object,method,args) -> {
                if(method.getName().equals("doExitCleanupAsync"))
                {if(calls.incrementAndGet()==1)throw new IllegalStateException("Injected cleanup failure");return CompletableFuture.completedFuture(false);}
                return null;
            });
        try
        {
            Main.setGuiHandler(faulty);fx_(Main::doQuit);assertEquals(Integer.valueOf(42),fx(() -> 42));
            fx_(Main::doQuit);assertEquals(2,calls.get());
        }
        finally {Main.setGuiHandler(previous);}
    }
}
