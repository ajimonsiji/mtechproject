package il.ac.technion.eyalzo.pack.stamps;

import java.util.LinkedList;

public class ReceiverChainStore extends GlobalChunkList
{
	// Full recording of all the chunks by the order they have been received
	LinkedList<ChainItem> chains = new LinkedList<ChainItem>();

	// The last chain added is the active one, or null if no chains were added
	// or it was just reset
	ChainItem activeChain = null;

	public void closeChain()
	{
		this.activeChain = null;
	}

	public void addChunk(int stampVal, int chunkLen)
	{
		addChunk(stampVal, chunkLen, -1);
	}

	/**
	 * Add a chunk to the store.
	 * <p>
	 * If chunk is already known it may only register a new chain for it. If the chunk also references this chain
	 * already then nothing will happen.
	 * 
	 * @param senderChainSerial
	 *            Optional. 1-based serial number of sender's chain, for clear debug messages on match or sync. This
	 *            number will actually be a duplicate as the sender's chain already exists in the system.
	 */
	public void addChunk(int stampVal, int chunkLen, int senderChainSerial)
	{
		// Get the chain item or create a new one
		ChunkItem chunkItem = this.getChunkOrAddNew(stampVal, chunkLen);

		// If there is no active chain then create and add
		if (activeChain == null)
		{
			activeChain = new ChainItem(senderChainSerial);
			chains.add(activeChain);
		}

		// Add the chunk last, even if already exists in that chain or another
		activeChain.addChunk(chunkItem);

		// Save the chain in the chunk's list of chain
		chunkItem.addChain(activeChain);
	}

	/**
	 * To be called <b>before</b> adding the chunk with {@link #addChunk(int, int)}.
	 * 
	 * @return The last chain that was registered for this chunk, or null if this chunk is seen for the first time (or
	 *         does not have a reference by error/bug).
	 */
	public ChainItem getChainForChunk(int stampVal, int chunkLen)
	{
		ChunkItem chunkItem = this.getChunkItem(stampVal, chunkLen);
		if (chunkItem == null)
			return null;
		return chunkItem.getChainLast();
	}

	/**
	 * @return For debug only.
	 */
	public LinkedList<ChainItem> getChains()
	{
		return chains;
	}
}
