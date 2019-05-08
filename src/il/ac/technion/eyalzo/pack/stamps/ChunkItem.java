package il.ac.technion.eyalzo.pack.stamps;

import il.ac.technion.eyalzo.pack.files.FileItem;
import il.ac.technion.eyalzo.webgui.WebContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class ChunkItem implements Comparable<ChunkItem>
{
	/**
	 * The chunk's stamp.
	 */
	int stamp;
	/**
	 * The chunk's length.
	 */
	int length;
	/**
	 * The chunk's content, that is needed when the chunk is not part of a file
	 * and we wish to use it locally for prediction. Null if content was not
	 * saved here due to lack of space, reference to file, etc.
	 */
	private byte[] chunkContent;
	/**
	 * Local disk files that have this chunk.
	 */
	private HashSet<FileItem> files = new HashSet<FileItem>();
	/**
	 * Local chains that have this chunk, ordered by adding time.
	 */
	private LinkedList<ChainItem> chains = new LinkedList<ChainItem>();
	/**
	 * Number of times found in traffic stream since start.
	 */
	private int statStreamCount;

	/**
	 * Next chunk according to the last chain that was processed with this chunk
	 * in it.
	 */
	private ChunkItem nextChunk;

	public ChunkItem getNextChunk()
	{
		return nextChunk;
	}

	public void setNextChunk(ChunkItem nextChunk)
	{
		this.nextChunk = nextChunk;
	}

	/**
	 * When the length and stamp are known.
	 */
	public ChunkItem(int stamp, int length)
	{
		this.stamp = stamp;
		this.length = length;
	}

	public void addFile(FileItem fileItem)
	{
		synchronized (files)
		{
			files.add(fileItem);
		}
	}

	@Override
	public String toString()
	{
		// For debug do not print length if 1
		if (this.length == 1)
			return String.format("%08x", this.stamp);

		return String.format("%08x/%,d", this.stamp, this.length);
	}

	public int getFilesCount()
	{
		synchronized (files)
		{
			return files.size();
		}
	}

	public static String toString(int stamp)
	{
		return String.format("%08x", stamp);
	}

	public String toHtmlString()
	{
		return String.format("<code>%08x</code>", stamp);
	}

	public int getStamp()
	{
		return stamp;
	}

	public int getLength()
	{
		return length;
	}

	public Collection<FileItem> getFiles()
	{
		return files;
	}

	public int getStatStreamCount()
	{
		return statStreamCount;
	}

	public void incStatStreamCount()
	{
		statStreamCount++;
	}

	/**
	 * @return Long value for storage in a data structure where the unique key
	 *         is long.
	 **/
	public long getLongForHashCode()
	{
		return (((long) stamp) << 32) | length;
	}

	/**
	 * 
	 * @param stamp
	 *            Chunk's 32 bits stamp.
	 * @param length
	 *            Chunk's length in bytes.
	 * @return Long value for storage in a data structure where the unique key
	 *         is long.
	 */
	public static long getLongForHashCode(int stamp, int length)
	{
		return (((long) stamp) << 32) | length;
	}

	@Override
	public int compareTo(ChunkItem o)
	{
		return ((Integer) this.length).compareTo(o.length);
	}

	/**
	 * Save a reference to that chain for later retrieval by receiver.
	 * <p>
	 * Preserves order in a way that guarantees that the last added is the last
	 * on the list.
	 */
	public void addChain(ChainItem chainItem)
	{
		synchronized (chains)
		{
			// For the rare case when a chunk is already found in that chain so
			// it may register the chain again
			chains.remove(chainItem);
			chains.add(chainItem);
		}
	}

	/**
	 * 
	 * @return Null if there are no chains for this chunk (because the reference
	 *         is voluntary), or the last added (or "added" again).
	 */
	public ChainItem getChainLast()
	{
		synchronized (chains)
		{
			return chains.getLast();
		}
	}

	public void setContent(byte[] chunkContent)
	{
		this.chunkContent = chunkContent;
	}

	/**
	 * 
	 * @return Null if the chunk's content was not saved.
	 */
	public byte[] getContent()
	{
		return this.chunkContent;
	}

	public void webGuiDetails(WebContext webGui)
	{
		webGui.appendField("Length", this.length);
	}
}
