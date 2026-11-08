/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.utility;

import java.util.*;
import threadchecker.OnThread;
import threadchecker.Tag;

/** FIFO map for object-owned requests. Renaming an object must not lose its queued work. */
@OnThread(Tag.Any)
public final class OrderedIdentityMap<K,V> extends AbstractMap<K,V>
{
    private record Identity<K>(K value)
    {
        @Override public int hashCode() { return System.identityHashCode(value); }
        @Override public boolean equals(Object other) { return other instanceof Identity<?> identity && value == identity.value; }
    }
    private final LinkedHashMap<Identity<K>,V> entries = new LinkedHashMap<>();
    public OrderedIdentityMap() { }
    public OrderedIdentityMap(Map<? extends K,? extends V> source) { putAll(source); }
    @Override public V put(K key, V value) { return entries.put(new Identity<>(key),value); }
    @Override public V get(Object key) { return entries.get(new Identity<>(key)); }
    @Override public V remove(Object key) { return entries.remove(new Identity<>(key)); }
    @Override public boolean containsKey(Object key) { return entries.containsKey(new Identity<>(key)); }
    @Override public int size() { return entries.size(); }
    @Override public void clear() { entries.clear(); }
    @Override public Set<Entry<K,V>> entrySet()
    {
        return new AbstractSet<>()
        {
            @Override public int size() { return entries.size(); }
            @Override public void clear() { entries.clear(); }
            @Override public Iterator<Entry<K,V>> iterator()
            {
                Iterator<Entry<Identity<K>,V>> iterator = entries.entrySet().iterator();
                return new Iterator<>()
                {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public void remove() { iterator.remove(); }
                    @Override public Entry<K,V> next()
                    {
                        Entry<Identity<K>,V> entry = iterator.next();
                        return new SimpleEntry<>(entry.getKey().value(),entry.getValue())
                        {
                            @Override public V setValue(V value) { super.setValue(value); return entry.setValue(value); }
                        };
                    }
                };
            }
        };
    }
}
