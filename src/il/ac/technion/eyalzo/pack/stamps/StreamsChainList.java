package il.ac.technion.eyalzo.pack.stamps;

import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.files.FileUtils;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

public class StreamsChainList
{
	/**
	 * FIFO chain list.
	 */
	private LinkedList<ChainItem> chainList = new LinkedList<ChainItem>();
	/**
	 * Permanent stamps file.
	 */
	private static final String STAMPS_FILE_NAME = "pack.chains";
	/**
	 * Maximum file size for {@link #STAMPS_FILE_NAME}.
	 */
	private static final int MAX_STAMPS_FILE_SIZE = 1024 * 1024 * 2;

	//
	// Statistics
	//
	private static long statStampsLoaded;
	/**
	 * Number of loaded stamps with no ancestor in chain.
	 */
	private static long statChainsLoaded;

	public StreamsChainList(boolean restoreChains)
	{
		if (restoreChains)
			restoreChains();
	}

	public void addChain(ChainItem newChain)
	{
		synchronized (chainList)
		{
			chainList.addFirst(newChain);
		}
	}

	public ChainItem addChain()
	{
		synchronized (chainList)
		{
			ChainItem newChain = new ChainItem();
			chainList.addFirst(newChain);
			return newChain;
		}
	}

	/**
	 * Save chunks that are part of chains (2 chunks and up). Each chain is
	 * saved as [2:len] [4:stamp] for each chunk until a 2-bytes zero value
	 * separator (zero "len").
	 */
	public boolean backupChains()
	{
		File metaFile = new File(STAMPS_FILE_NAME);

		ByteBuffer buffer = ByteBuffer.allocate(MAX_STAMPS_FILE_SIZE);

		synchronized (chainList)
		{
			for (ChainItem curChain : chainList)
			{
				// Break when buffer is exhausted
				if (!curChain.saveChunks(buffer))
					break;
			}
		}

		//
		// Save the file
		//
		buffer.limit(buffer.position());
		buffer.position(0);
		// TODO delete in the write method itself?
		metaFile.delete();
		FileUtils.initDataFile(metaFile.getAbsolutePath(), buffer.remaining(),
				true);
		return FileUtils
				.writeBlock(metaFile.getAbsolutePath(), 0, buffer, null);
	}

	/**
	 * Load stamps and chains from permanent file.
	 * <p>
	 * Also modifies {@link #statChainsLoaded} and {@link #statStampsLoaded}.
	 */
	public void restoreChains()
	{
		File backupFile = new File(STAMPS_FILE_NAME);
		// File may not exist, like in first run for example
		if (!backupFile.exists())
			return;

		// Load from file
		ByteBuffer buffer = FileUtils.readBlock(backupFile.getAbsolutePath(),
				0L, (int) backupFile.length(), ByteOrder.BIG_ENDIAN, null);
		if (buffer == null)
			return;

		synchronized (chainList)
		{
			chainList.clear();
		}

		while (buffer.hasRemaining())
		{
			ChainItem curChain = new ChainItem();
			curChain.loadChunks(buffer, Main.chunks);
			synchronized (chainList)
			{
				chainList.add(curChain);
			}
		}

		// Statistics
		statChainsLoaded = chainList.size();

		statStampsLoaded = getChunksCount();

		System.out.println(String.format(
				"Loaded %,d chains with %,d stamps from file %s",
				statChainsLoaded, statStampsLoaded, STAMPS_FILE_NAME));
	}

	private int getChunksCount()
	{
		int result = 0;

		synchronized (chainList)
		{
			for (ChainItem curChain : chainList)
			{
				result += curChain.size();
			}
		}

		return result;
	}

	/**
	 * 
	 * @return Number of chains.
	 */
	public int size()
	{
		synchronized (chainList)
		{
			return chainList.size();
		}
	}

	public DisplayTable webGuiChains(String chainDetailsLink)
	{
		DisplayTable table = new DisplayTable();

		table.addCol("Serial", "Serial number of the chain (1-based)", false);
		table
				.addCol("First chunk",
						"Signature of the first chunk in the chain");
		table.addCol("Chunks", "Number of chunks", false);

		synchronized (chainList)
		{
			for (ChainItem curChain : chainList)
			{
				table.addRow(null);

				// Serial
				table.addCell(curChain.getSerial());
				// First chunk
				ChunkItem firstChunk = curChain.getChunks().getFirst();
				table.addCell(firstChunk.toHtmlString(), chainDetailsLink
						+ curChain.getSerial());
				// Chunks
				table.addCell(curChain.size());
			}
		}

		return table;
	}

	public ChainItem getChain(int chainSerial)
	{
		synchronized (chainList)
		{
			if (chainSerial < 0 || chainSerial >= chainList.size())
				return null;

			return chainList.get(chainSerial);
		}
	}
}
