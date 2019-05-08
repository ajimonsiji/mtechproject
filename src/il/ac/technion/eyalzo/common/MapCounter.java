package il.ac.technion.eyalzo.common;

import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Counter of items per key. Use it to count instances in list or map.
 */
public class MapCounter<K>
{
    protected Map<K, Long> mapCounter = Collections.synchronizedMap(new HashMap<K, Long>());

    /**
     * Add to counter of this key.
     * 
     * @param key Key.
     * @param toAdd How much to add to this key's counter.
     * @return Updated count, after add.
     */
    public synchronized long add(K key, long toAdd)
    {
        Long prevValue = mapCounter.get(key);

        //
        // If key is new put for the first time
        //
        if (prevValue == null)
        {
            mapCounter.put(key, toAdd);
            return toAdd;
        }

        //
        // Key already exists, to add to current
        //
        long newValue = prevValue + toAdd;
        mapCounter.put(key, newValue);
        return newValue;
    }

    /**
     * Add to counter of these keys.
     * 
     * @param keys Collection of keys to be handled with iterator.
     * @param toAdd How much to add to keys' counter.
     */
    public synchronized void addAll(Collection<K> keys, long toAdd)
    {
        Iterator<K> it = keys.iterator();
        while (it.hasNext())
        {
            K key = it.next();
            this.add(key, toAdd);
        }
    }

    /**
     * Add counters from another map-counter.
     * 
     * @param other Another map-counter. map-counter, so a local copy better be given and not a live object.
     */
    public synchronized void addAll(MapCounter<K> other)
    {
        Iterator<Entry<K, Long>> it = other.entrySet().iterator();
        while (it.hasNext())
        {
            Entry<K, Long> entry = it.next();
            this.add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Add 1 to counter of this key.
     * 
     * @param key Key.
     * @return Updated count, after add.
     */
    public synchronized long inc(K key)
    {
        return this.add(key, 1L);
    }

    /**
     * Sub 1 from counter of this key.
     * 
     * @param key Key.
     * @return Updated count, after sub.
     */
    public synchronized long dec(K key)
    {
        return this.add(key, -1L);
    }

    /**
     * Add 1 to counter of these keys.
     * 
     * @param keys Collection of keys to be handled with iterator.
     */
    public synchronized void incAll(Collection<K> keys)
    {
        this.addAll(keys, 1L);
    }

    /**
     * @return The internal map with counter for every key. Must be synchronized for safe access.
     */
    public synchronized Map<K, Long> getMap()
    {
        return mapCounter;
    }

    /**
     * @return A duplicate of the internal map with counter for every key. No need to synchronize.
     */
    public synchronized Map<K, Long> getMapDup()
    {
        return new HashMap<K, Long>(mapCounter);
    }

    /**
     * @return Entry set, for iterator over the original internal map.
     */
    public synchronized Set<Entry<K, Long>> entrySet()
    {
        return mapCounter.entrySet();
    }

    /**
     * @return Key set, for iterator over the original internal map.
     */
    public synchronized Set<K> keySet()
    {
        return mapCounter.keySet();
    }

    /**
     * Gets the numeric value stored for this key, or zero if not found.
     * 
     * @param key The given key.
     * @return The numeric value stored for this key, or zero if not found.
     */
    public synchronized long get(K key)
    {
        Long value = mapCounter.get(key);
        if (value == null)
            return 0;
        return value.longValue();
    }

    /**
     * @return Sum of al counts.
     */
    public synchronized long getSum()
    {
        long sum = 0;

        for (long val : mapCounter.values())
        {
            sum += val;
        }

        return sum;
    }

    /**
     * @return Average count per key, by sum divided by number of keys.
     */
    public synchronized float getAverage()
    {
        if (mapCounter.isEmpty())
            return 0;

        return (float) (((double) (this.getSum())) / mapCounter.size());
    }

    /**
     * Default table for this map.
     * 
     * @return New display table for this map-counter. Default sort to counter column is the value column.
     */
    public synchronized DisplayTable webGuiTable(String keyName, String keyTip, String keyLink, String valueName,
            String valueTip)
    {
        DisplayTable table = new DisplayTable();

        //
        // Columns
        //
        table.addCol(keyName, keyTip, true);
        table.addCol(valueName, valueTip, false);
        // Set default sort to counter column
        table.setDefaultSortCol(1);

        Iterator<Entry<K, Long>> it = mapCounter.entrySet().iterator();
        while (it.hasNext())
        {
            Entry<K, Long> entry = it.next();
            K key = entry.getKey();

            table.addRow("");

            //
            // Key
            //
            if (keyLink != null && !keyLink.isEmpty() && key != null)
            {
                table.addCell(key, keyLink + key.toString());
            }
            else
            {
                table.addCell(key);
            }

            //
            // Value
            //
            table.addCell(entry.getValue().longValue());
        }

        return table;
    }

    /**
     * @return Number of keys in this map.
     */
    public int size()
    {
        return mapCounter.size();
    }

    @Override
    public synchronized String toString()
    {
        return toString("\n", "\t");
    }

    /**
     * @param keySeparator If not null, keys will be returned, and each line (except for the last) will end with this
     *        string.
     * @param valueSeparator If not null, values will be returned, and seperated from keys (or other values, if keys are not
     *        returned) with this string.
     */
    public synchronized String toString(String keySeparator, String valueSeparator)
    {
        StringBuffer buffer = new StringBuffer(1000);
        boolean first = true;

        Iterator<Entry<K, Long>> it = mapCounter.entrySet().iterator();
        while (it.hasNext())
        {
            Entry<K, Long> entry = it.next();

            if (keySeparator != null)
            {
                if (first)
                {
                    first = false;
                }
                else
                {
                    buffer.append(keySeparator);
                }
                buffer.append(entry.getKey().toString());
            }

            if (valueSeparator != null)
            {
                buffer.append(valueSeparator);

                buffer.append(entry.getValue().toString());
            }
        }

        return buffer.toString();
    }

    /**
     * Clear all items and their counters.
     */
    public synchronized void clear()
    {
        mapCounter.clear();
    }

    /**
     * @param minValue Keys with value below this will be removed.
     */
    public synchronized void cleanupMin(long minValue)
    {
        Iterator<Long> it = mapCounter.values().iterator();
        while (it.hasNext())
        {
            Long curValue = it.next();
            if (curValue < minValue)
            {
                it.remove();
            }
        }
    }

    /**
     * @param factor All the values will be divided by this number.
     */
    public synchronized void div(long factor)
    {
        Iterator<Entry<K, Long>> it = mapCounter.entrySet().iterator();
        while (it.hasNext())
        {
            Entry<K, Long> entry = it.next();

            entry.setValue(entry.getValue() / factor);
        }
    }

    public synchronized boolean isEmpty()
    {
        return mapCounter.isEmpty();
    }

    /**
     * @return The key with the highest value, or null if empty.
     */
    public synchronized K getMaxKey()
    {
        K result = null;
        long max = Long.MIN_VALUE;

        Iterator<Entry<K, Long>> it = mapCounter.entrySet().iterator();
        while (it.hasNext())
        {
            Entry<K, Long> entry = it.next();

            if (entry.getValue() > max)
            {
                result = entry.getKey();
                max = entry.getValue();
            }
        }

        return result;
    }

    /**
     * @return New map, sorted by the keys, in ascending order.
     */
    public synchronized SortedMap<K, Long> getSortedByKeyDup()
    {
        return new TreeMap<K, Long>(mapCounter);
    }

    /**
     * @return New map, sorted by the counters, in ascending order. In case of equality, order is random.
     */
    public synchronized SortedMap<K, Long> getSortedByCountDup()
    {
        TreeMap<K, Long> result = new TreeMap<K, Long>(new Comparator<K>()
        {
            public int compare(K o1, K o2)
            {
                Long val1 = mapCounter.get(o1);
                Long val2 = mapCounter.get(o2);
                if (val1 == null)
                {
                    if (val2 == null)
                        return 0;
                    return 1;
                }
                if (val2 == null)
                    return -1;
                return val1.compareTo(val2);
            }
        });

        result.putAll(mapCounter);

        return result;
    }

    /**
     * @param keyToClear Key to remove from the map.
     */
    public void clear(K keyToClear)
    {
        synchronized (mapCounter)
        {
            mapCounter.remove(keyToClear);
        }
    }
}
