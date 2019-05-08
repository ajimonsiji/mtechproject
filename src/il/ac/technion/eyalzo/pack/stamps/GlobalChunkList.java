package il.ac.technion.eyalzo.pack.stamps;

import java.util.HashMap;

public class GlobalChunkList
{
	/**
	 * Key is stamp+length, and the value is the chunk itself where pointers to files are held.
	 */
	private HashMap<Long, ChunkItem> chunks = new HashMap<Long, ChunkItem>();

	//
	// Statistics
	//
	/**
	 * Completely new.
	 */
	protected static long statChunksUnknown;
	/**
	 * Known stamps meaning not {@link #statChunksUnknown}.
	 */
	protected static long statChunksKnown;

	public GlobalChunkList()
	{
		// TODO revive
		// restoreChains();
	}

	public int getChunksCount()
	{
		synchronized (chunks)
		{
			return chunks.size();
		}
	}

	public long getStatChunksUnknown()
	{
		return statChunksUnknown;
	}

	public long getStatChunksKnown()
	{
		return statChunksKnown;
	}

	public ChunkItem getChunkItem(int stampVal, int chunkLen)
	{
		return chunks.get(ChunkItem.getLongForHashCode(stampVal, chunkLen));
	}

	/**
	 * @return Existing stamp with the same value or a newly added one.
	 */
	public ChunkItem getChunkOrAddNew(int stampVal, int chunkLen)
	{
		synchronized (chunks)
		{
			long keyValue = ChunkItem.getLongForHashCode(stampVal, chunkLen);
			ChunkItem stampItem = chunks.get(keyValue);
			// Need to create?
			if (stampItem == null)
			{
				stampItem = new ChunkItem(stampVal, chunkLen);
				chunks.put(keyValue, stampItem);
				statChunksUnknown++;
			} else
			{
				statChunksKnown++;
			}

			return stampItem;
		}
	}
}
