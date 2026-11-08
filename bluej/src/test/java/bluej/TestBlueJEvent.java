/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej;
import bluej.editor.flow.FXTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestBlueJEvent extends FXTest
{
    @Test public void aFailingListenerCannotLoseAnotherEditorsDocumentationResult()
    {
        List<Integer> received=new ArrayList<>();
        BlueJEventListener good=(event,arg,project) -> received.add(event),bad=(event,arg,project) -> {throw new AssertionError("Listener failure");};
        fx_(() -> {
            BlueJEvent.addListener(good);BlueJEvent.addListener(bad);
            try {BlueJEvent.raiseEvent(BlueJEvent.DOCU_GENERATED,"Example.html");assertEquals(List.of(BlueJEvent.DOCU_GENERATED),received);}
            finally {BlueJEvent.removeListener(bad);BlueJEvent.removeListener(good);}
        });
    }
    @Test public void registrationDuringNotificationStartsWithTheNextEvent()
    {
        List<String> received=new ArrayList<>();BlueJEventListener added=(event,arg,project) -> received.add("added");
        BlueJEventListener first=(event,arg,project) -> {received.add("first");BlueJEvent.addListener(added);};
        fx_(() -> {
            BlueJEvent.addListener(first);
            try {BlueJEvent.raiseEvent(BlueJEvent.DOCU_GENERATED,null);assertEquals(List.of("first"),received);BlueJEvent.raiseEvent(BlueJEvent.DOCU_GENERATED,null);assertEquals(List.of("first","added","first"),received);}
            finally {BlueJEvent.removeListener(first);BlueJEvent.removeListener(added);}
        });
    }
}
