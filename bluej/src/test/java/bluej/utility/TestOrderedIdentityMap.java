/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.utility;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestOrderedIdentityMap
{
    private static final class Name
    {
        String value;
        Name(String value) { this.value=value; }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public boolean equals(Object other) { return other instanceof Name name && value.equals(name.value); }
    }
    @Test public void renamingAQueuedObjectKeepsLookupRemovalAndOrder()
    {
        Name first=new Name("a1"),second=new Name("b1");Map<Name,Integer> map=new OrderedIdentityMap<>();
        map.put(first,1);map.put(second,2);first.value="Renamed";
        assertEquals(Integer.valueOf(1),map.get(first));assertEquals(List.of(first,second),new ArrayList<>(map.keySet()));
        Map<Name,Integer> detached=new OrderedIdentityMap<>(map);first.value="AnotherRename";
        assertEquals(Integer.valueOf(1),detached.remove(first));assertEquals(Integer.valueOf(1),map.remove(first));
        assertEquals(1,map.size());assertEquals(Integer.valueOf(2),map.get(second));
    }
    @Test public void equalNamesRemainSeparateRequestsAndNullRepresentsTheWholePackage()
    {
        Name one=new Name("Same"),two=new Name("Same");var map=new OrderedIdentityMap<Name,String>();
        map.put(one,"one");map.put(null,"package");map.put(two,"two");
        assertEquals(3,map.size());assertEquals("one",map.get(one));assertEquals("two",map.get(two));assertEquals("package",map.get(null));
        assertNull(map.get(new Name("Same")));assertTrue(map.containsKey(null));
        assertEquals(Arrays.asList(one,null,two),new ArrayList<>(map.keySet()));
    }
    @Test public void iteratorAndMapDefaultsMutateTheBackingQueue()
    {
        Object first=new Object(),second=new Object();var map=new OrderedIdentityMap<Object,Integer>();
        map.put(first,1);map.put(second,2);var iterator=map.entrySet().iterator();
        assertEquals(Integer.valueOf(1),iterator.next().setValue(3));assertEquals(Integer.valueOf(3),map.get(first));
        iterator.remove();assertFalse(map.containsKey(first));assertEquals(Integer.valueOf(5),map.merge(second,3,Integer::sum));
        map.entrySet().clear();assertTrue(map.isEmpty());
    }
}
