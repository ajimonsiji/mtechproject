package il.ac.technion.eyalzo.pack.stamps;

import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.files.FileItem;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Local chunk list for files and streams.
 */
public class ChainItem
{
	/**
	 * Chunks by their order in the file/stream.
	 */
	private LinkedList<ChunkItem> chunks = new LinkedList<ChunkItem>();
	private static int globalSerial = 1;
	private final int serial;

	public ChainItem()
	{
		this.serial = globalSerial++;
	}

	/**
	 * @param senderChainSerial
	 *            Optional. 1-based serial number of sender's chain, for clear
	 *            debug messages on match or sync. This number will actually be
	 *            a duplicate as the sender's chain already exists in the
	 *            system.
	 */
	public ChainItem(int senderChainSerial)
	{
		if (senderChainSerial >= 0)
			this.serial = senderChainSerial;
		else
			this.serial = globalSerial++;
	}

	/**
	 * Load file/stream chunks from buffer that was previously read from local
	 * backup file. Also adds the loaded chunks to the global list.
	 * 
	 * @param buffer
	 *            Buffer pointing to chain that is made of [2:len][4:stamp]
	 *            couples and ends with [2:len] where len is zero.
	 * @return Number of read chunks.
	 */
	public int loadChunks(ByteBuffer buffer, GlobalChunkList globalChunkList)
	{
		int result = 0;

		while (true)
		{
			int chunkLen = 0x0000ffff & buffer.getShort();

			// On chain end
			if (chunkLen == 0)
				return result;

			// Stamp
			int stampVal = buffer.getInt();

			// Add item to the global list or get it if already exists
			ChunkItem curChunk = globalChunkList.getChunkOrAddNew(stampVal,
					chunkLen);

			// Add to local chain
			synchronized (chunks)
			{
				// Point from the previous last to the new last
				if (!chunks.isEmpty())
				{
					ChunkItem lastChunk = chunks.getLast();
					lastChunk.setNextChunk(curChunk);
				}
				// Add the new last
				chunks.add(curChunk);
			}
		}
	}

	/**
	 * Write chunks to buffer that will later be written to backup file, per
	 * file or global stream chains backup.
	 * 
	 * @param buffer
	 *            Target buffer.
	 * @return False if there is not enough room in the buffer to save all the
	 *         chunks.
	 */
	public boolean saveChunks(ByteBuffer buffer)
	{
		synchronized (chunks)
		{
			// It needs 6 bytes per chunk plus null terminator
			if (buffer.remaining() < (chunks.size() * 6) + 2)
				return false;

			for (ChunkItem curChunk : chunks)
			{
				// Length
				buffer.putShort((short) curChunk.length);
				// Stamp
				buffer.putInt(curChunk.stamp);
			}

			// Chains separator
			buffer.putShort((short) 0);
		}

		return true;
	}

	/**
	 * 
	 * @return Number of chunks in that chain.
	 */
	public int size()
	{
		synchronized (chunks)
		{
			return chunks.size();
		}
	}

	/**
	 * 
	 * @param fromSerial
	 *            1-based inclusive.
	 * @param toSerial
	 *            1-based inclusive.
	 */
	public DisplayTable webGuiChunks(int fromSerial, int toSerial,
			String chunkDetailsLink, String paramLen, FileItem thisFile)
	{
		// Create list
		LinkedList<FileItem> overlapFiles = new LinkedList<FileItem>(thisFile
				.getOverlapFiles());
		// Remove this
		overlapFiles.remove(thisFile);
		// Sort by serial number
		Collections.sort(overlapFiles);

		DisplayTable table = new DisplayTable();

		table.addCol("Serial", "Chunk's serial number in the file", true);
		table.addCol("Signature", "The signature itself", true);
		table.addCol("Length", "Chunk length", false);
		table.addCol("More<br>files", "Times appear in other files", false);
		for (FileItem curFile : overlapFiles)
		{
			table.addCol("<font color=white>File<br>" + curFile.getSerial()
					+ "</font>", curFile.getName());
		}

		int serial = 0;
		// Expected chunk by chain
		ChunkItem expectedChunk = null;

		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				serial++;

				// Range - from
				if (serial < fromSerial)
					continue;
				// Range - to
				if (serial > toSerial)
					break;

				boolean isBreak = expectedChunk != null
						&& expectedChunk != curChunk;
				table.addRow(isBreak ? "yellow" : null);

				table.addCell(serial);
				// Signature
				table.addCell(curChunk.toHtmlString(), chunkDetailsLink
						+ curChunk.getStamp() + "&" + paramLen + "="
						+ curChunk.getLength());
				table.addCell(curChunk.getLength());
				// More files
				table.addCell(curChunk.getFilesCount() - 1);
				for (FileItem curFile : overlapFiles)
				{
					table.addCell(curFile.hasChunk(curChunk) ? "+" : null);
				}

				// Next by chain
				expectedChunk = curChunk.getNextChunk();
			}
		}

		return table;
	}

	public void setChunks(LinkedList<ChunkItem> calcChunks)
	{
		synchronized (chunks)
		{
			chunks.clear();
			chunks.addAll(calcChunks);
		}
	}

	/**
	 * @return List of other files that have at least one chunk that this chain
	 *         has. May be empty but never null.
	 */
	public Collection<FileItem> getChunksFiles()
	{
		HashSet<FileItem> result = new HashSet<FileItem>();

		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				Collection<FileItem> curFiles = curChunk.getFiles();
				if (curFiles == null)
					continue;

				result.addAll(curFiles);
			}
		}

		return result;
	}

	/**
	 * 
	 * @param otherChainChunks
	 *            Stamp list to compare with.
	 * @return Number of file stamps that are also found in the given list.
	 */
	public String webGuiOverlapChunksVisual(
			LinkedList<ChunkItem> otherChainChunks, int bytesPerPixel)
	{
		StringBuffer buffer = new StringBuffer(chunks.size() * 50);

		buffer.append("<table width=100% border=0 cellSpacing=0 cellPadding=0>"
				+ "<tr align=center bgcolor=white>");

		synchronized (chunks)
		{
			boolean light = true;

			for (ChunkItem curStamp : chunks)
			{
				int pixels = Math.max(1, curStamp.getLength() / bytesPerPixel);

				int chunkIndex = otherChainChunks.indexOf(curStamp);

				buffer.append("<td bgcolor=");
				// If found in chain
				if (chunkIndex >= 0)
				{
					buffer.append(light ? "#0000ff" : "#000088");
				} else
				{
					buffer.append(light ? "lightgray" : "gray");
				}
				buffer.append(" width=");
				buffer.append(pixels);
				buffer.append(" height=15><font size=1 color=white>");
				if (chunkIndex >= 0)
				{
					buffer.append(chunkIndex + 1);
				}
				buffer.append("</code></font>");

				light = light ? false : true;
			}
		}

		buffer.append("</tr></table>");

		return buffer.toString();
	}

	public int getOverlapChunksCount(ChainItem otherChain)
	{
		int result = 0;

		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				if (otherChain.contains(curChunk))
				{
					result++;
				}
			}
		}

		return result;
	}

	/**
	 * Find the overlapping areas between two chains.
	 * <p>
	 * For each item from this chain it looks for a match on the other. If there
	 * is no match, it looks for the chunk on the other chain from the
	 * beginning. If there is a match it tries to find more and increase the
	 * current series counter.
	 * 
	 * @return List of overlapping series lengths. May be empty but never null.
	 */
	public LinkedList<Integer> getOverlapChunksSeries(ChainItem otherChain)
	{
		// Prepare the place for the result
		LinkedList<Integer> result = new LinkedList<Integer>();

		// The other chain index for fast search on match
		int otherIndex = 0;
		int curSeries = 0;

		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				if (otherIndex >= 0)
				{
					ChunkItem otherChunk = otherChain.getChunk(otherIndex);

					// Chain match?
					if (otherChunk != null && otherChunk.equals(curChunk))
					{
						curSeries++;
						otherIndex++;
						continue;
					}
				}

				// Look for the chunk in the other chain, from the beginning
				otherIndex = otherChain.indexOf(curChunk);
				// This chunk does not exist on the other
				if (otherIndex == -1)
					continue;

				// Match with a new series

				// Save the last series
				if (curSeries > 0)
				{
					result.add(curSeries);
				}

				curSeries = 1;
				otherIndex++;
			}
		}

		// Save the last series
		if (curSeries > 0)
		{
			result.add(curSeries);
		}

		return result;
	}

	private boolean contains(ChunkItem curChunk)
	{
		synchronized (chunks)
		{
			return chunks.contains(curChunk);
		}
	}

	/**
	 * @return Index of the given chunk if found in the chain (0-based), or -1
	 *         if not found.
	 */
	private int indexOf(ChunkItem chunkItem)
	{
		synchronized (chunks)
		{
			return chunks.indexOf(chunkItem);
		}
	}

	public DisplayTable webGuiChainFiles(String fileDetailsLink,
			boolean withVisual)
	{
		// Chain files
		Collection<FileItem> chainFiles = this.getChunksFiles();

		long maxFileSize = 0;
		for (FileItem curFile : chainFiles)
		{
			maxFileSize = Math.max(maxFileSize, curFile.getFileSize());
		}

		int bytesPerPixel = (int) (maxFileSize / 2000);

		DisplayTable table = new DisplayTable();

		table.addCol("File #", "Internal sort representation", true);
		table.setLastColAsDefaultSortCol();
		table.addCol("Name", "File name in directory", true);
		table.addColNum("Size", "File size", false);
		table.addCol("Chunks", "Number of chunks", false);
		table.addCol("Overlap<br>chunks",
				"Number of chunks that are also found in this chain", false);
		table.addColTime("Last<br>modified", "Last modified time", false, true,
				true, false);
		if (withVisual)
		{
			table.addCol("Visual", "Visual representation of the chunks");
		}

		for (FileItem curFile : chainFiles)
		{
			int fileSerial = curFile.getSerial();
			String fileName = Main.dirList.getFileName(fileSerial, true);
			int stampsCount = curFile.getChunkCount();

			table.addRow(null);

			table.addCell(fileSerial);
			// Name
			table.addCell(fileName, fileDetailsLink + fileSerial);
			// Size
			table.addCell(curFile.getFileSize());
			// Chunks
			table.addCell(stampsCount);
			// Overlap chunks
			int sharedStamps = curFile.getOverlapChunksCount(this);
			table.addCell(sharedStamps);
			// Last modified
			table.addCell(curFile.getLastModified());
			// Visual
			if (withVisual)
			{
				table.addCell(curFile.webGuiOverlapChunksVisual(this.chunks,
						bytesPerPixel));
			}
		}

		return table;
	}

	public void addFileToChunks(FileItem file)
	{
		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				curChunk.addFile(file);
			}
		}
	}

	public boolean hasChunk(ChunkItem curChunk)
	{
		synchronized (chunks)
		{
			return chunks.contains(curChunk);
		}
	}

	public void addChunk(ChunkItem newChunk)
	{
		synchronized (chunks)
		{
			// Point from the previous last to the new last
			if (!chunks.isEmpty())
			{
				ChunkItem lastChunk = chunks.getLast();
				lastChunk.setNextChunk(newChunk);
			}
			// Add the new last
			chunks.add(newChunk);
		}
	}

	public ChunkItem getChainLastChunk()
	{
		synchronized (chunks)
		{
			if (chunks.isEmpty())
				return null;

			return chunks.getLast();
		}
	}

	/**
	 * 
	 * @param index
	 *            0-based index of chunk.
	 * @return Null if index is not in range, or the chunk matching the given
	 *         index.
	 */
	private ChunkItem getChunk(int index)
	{
		synchronized (chunks)
		{
			if (index >= chunks.size())
				return null;

			return chunks.get(index);
		}
	}

	/**
	 * @return Number of distinct chunks in chain. Cannot be greater than
	 *         {@link #size()}.
	 */
	public int getChunkCountDistinct()
	{
		synchronized (chunks)
		{
			HashSet<ChunkItem> chunksSet = new HashSet<ChunkItem>(chunks);
			return chunksSet.size();
		}
	}

	/**
	 * @return The internal chunk list (need to synchronize).
	 */
	public LinkedList<ChunkItem> getChunks()
	{
		return chunks;
	}

	/**
	 * @return 0-based index of first instance of such a chunk in the chain, or
	 *         -1 if not found.
	 */
	public int indexOf(int stamp, int length)
	{
		int index = 0;
		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				if (curChunk.getLength() == length
						&& curChunk.getStamp() == stamp)
					return index;

				index++;
			}
		}

		return -1;
	}

	/**
	 * @param startOffset
	 *            0-based offset for search start.
	 * @param searchLength
	 *            Number of chunks to compare.
	 * @return -1 for no match or 0-based offset of the chunk if found in the
	 *         given range.
	 */
	public int indexOf(int stampVal, int chunkLength, int startOffset,
			int searchLength)
	{
		// Sanity check
		if (startOffset >= this.size() || searchLength <= 0)
			return -1;

		int index = 0;
		int maxIndex = startOffset + searchLength - 1;
		synchronized (chunks)
		{
			for (ChunkItem curChunk : chunks)
			{
				if (index < startOffset)
				{
					index++;
					continue;
				}

				// Match?
				if (curChunk.stamp == stampVal
						&& curChunk.length == chunkLength)
					return index;

				// Next index
				index++;

				// End of search?
				if (index > maxIndex)
					return -1;
			}
		}

		return -1;
	}

	@Override
	public String toString()
	{
		synchronized (chunks)
		{
			if (chunks.isEmpty())
				return serial + ": (empty)";

			StringBuffer buffer = new StringBuffer(chunks.size() * 9);

			buffer.append(serial);
			buffer.append(":");

			for (ChunkItem curChunk : chunks)
			{
				buffer.append(" ");
				buffer.append(curChunk.toString());
			}

			return buffer.toString();
		}
	}

	/**
	 * @return 1-based chain serial for debug.
	 */
	public int getSerial()
	{
		return serial;
	}
}
