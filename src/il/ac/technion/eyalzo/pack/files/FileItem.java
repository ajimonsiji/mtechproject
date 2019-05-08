package il.ac.technion.eyalzo.pack.files;

import il.ac.technion.eyalzo.pack.RabinUtils;
import il.ac.technion.eyalzo.pack.stamps.ChainItem;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;
import il.ac.technion.eyalzo.pack.stamps.GlobalChunkList;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

public class FileItem implements Comparable<FileItem>
{
	private static int globalSerial = 1;
	/**
	 * File name (without path), for debug.
	 */
	private String name;
	/**
	 * True if file was not changed since the last meta save and meta was loaded
	 * successfully.
	 */
	private boolean loadedMetaChunks = false;
	/**
	 * 1-based serial.
	 */
	private int serial;
	/**
	 * File size, simply because it is found anyway during the directory scan.
	 */
	long fileSize;
	/**
	 * Last modified, simply because it is found anyway during the directory
	 * scan.
	 */
	long lastModified;
	/**
	 * Chain made of ordered chunks.
	 */
	ChainItem chain = new ChainItem();

	//
	// Statistics
	//
	/**
	 * Number of stamps already found elsewhere, according to
	 * {@link #addStamps(HashMap)}.
	 */
	private int statRedundantStamps;

	public static FileItem init(String fileName, long minFileSize)
	{
		if (fileName == null || fileName.isEmpty())
			return null;

		File file = new File(fileName);
		return init(file, minFileSize);
	}

	/**
	 * 
	 * @param file
	 *            File to build a structure for.
	 * @param minFileSize
	 *            Minimal file size, below it a null is returned.
	 * @return New file structure, or null if the file is too small or not a
	 *         real file.
	 */
	public static FileItem init(File file, long minFileSize)
	{
		if (file == null)
			return null;

		// Skip directories etc
		if (!file.isFile())
			return null;

		// Size
		if (file.length() < minFileSize)
			return null;

		//
		// Create the new instance
		//
		FileItem result = new FileItem();
		result.name = file.getName();
		result.fileSize = file.length();
		result.lastModified = file.lastModified();
		result.serial = globalSerial++;

		return result;
	}

	/**
	 * Load chunks from directory's meta file, and set a flag to remember it. To
	 * be called only if the file's time and size was not changed, comparing to
	 * the information found in the directory's meta.
	 * 
	 * @param buffer
	 *            Meta file buffer that already holds the bytes loaded from the
	 *            file, to be processed by this method.
	 */
	public void loadMetaChunks(ByteBuffer buffer,
			GlobalChunkList globalChunkList)
	{
		loadedMetaChunks = true;

		chain.loadChunks(buffer, globalChunkList);

		chain.addFileToChunks(this);
	}

	public boolean isLoadedMetaChunks()
	{
		return loadedMetaChunks;
	}

	public void addSaveLine(String name, ByteBuffer buffer)
	{
		// File name length must fit into 2 bytes
		if (name.length() > 0xefff)
			return;

		// 1-2: Name length
		byte[] nameBytes = name.getBytes();
		buffer.putShort((short) nameBytes.length);
		// 3-?: Name (relative)
		buffer.put(name.getBytes());
		// 8: File size
		buffer.putLong(fileSize);
		// 8: Last modified
		buffer.putLong(lastModified);
		// var: Chunks
		chain.saveChunks(buffer);
	}

	public ChainItem getChain()
	{
		return this.chain;
	}

	public int getChunkCount()
	{
		return chain.size();
	}

	/**
	 * @return Number of distinct chunks in chain. Cannot be greater than
	 *         {@link #size()}.
	 */
	public int getChunkCountDistinct()
	{
		return chain.getChunkCountDistinct();
	}

	public int getStatRedundantStamps()
	{
		return statRedundantStamps;
	}

	public DisplayTable webGuiDetails()
	{
		DisplayTable table = new DisplayTable();

		table.addField("Serial", this.serial,
				"Internal serial for simpler display");
		table.addField("Size", this.fileSize, "File size");
		int chunkCount = this.getChunkCount();
		table.addField("Chunks", chunkCount, "Number of chunks");
		table
				.addField("Overlap files", getOverlapFiles().size(),
						"Number of files that have at least one chunk that this file has");
		table.addField("Last modified", new Date(this.lastModified),
				"Last modified time");

		return table;
	}

	/**
	 * 
	 * @param fromSerial
	 *            1-based inclusive.
	 * @param toSerial
	 *            1-based inclusive.
	 */
	public DisplayTable webGuiChunks(int fromSerial, int toSerial,
			String chunkDetailsLink, String paramLen)
	{
		return chain.webGuiChunks(fromSerial, toSerial, chunkDetailsLink,
				paramLen, this);
	}

	public int getSerial()
	{
		return serial;
	}

	public long getFileSize()
	{
		return fileSize;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	/**
	 * Calculate the file's chunks, add them to the global list, and link them
	 * while overriding former chains. It does not touch the back link of the
	 * first chunk and not the forward link of the last chunk.
	 * 
	 * @param fullPath
	 *            File full path.
	 */
	public void calculateStamps(String fullPath, GlobalChunkList globalChunkList)
	{
		LinkedList<ChunkItem> calcChunks = RabinUtils.calcFileChunks(fileSize,
				globalChunkList, fullPath);

		// Chunk were already added to the global list

		// Make sure there was no file or stamping error
		if (calcChunks == null || calcChunks.isEmpty())
			return;

		chain.setChunks(calcChunks);

		addFileToChunks();
	}

	private void addFileToChunks()
	{
		chain.addFileToChunks(this);
	}

	/**
	 * @return List of other files that have at least one chunk that this chain
	 *         has. Does not include this file. May be empty but never null.
	 */
	public Collection<FileItem> getOverlapFiles()
	{
		Collection<FileItem> result = chain.getChunksFiles();

		result.remove(this);

		return result;
	}

	public int getOverlapChunksCount(ChainItem otherChain)
	{
		return chain.getOverlapChunksCount(otherChain);
	}

	public int getOverlapChunksCount(FileItem otherFileItem)
	{
		return chain.getOverlapChunksCount(otherFileItem.chain);
	}

	public String webGuiOverlapChunksVisual(
			LinkedList<ChunkItem> otherChainChunks, int bytesPerPixel)
	{
		return chain.webGuiOverlapChunksVisual(otherChainChunks, bytesPerPixel);
	}

	public DisplayTable webGuiChainFiles(String fileDetailsLink,
			boolean withVisual)
	{
		return chain.webGuiChainFiles(fileDetailsLink, withVisual);
	}

	public String getName()
	{
		return name;
	}

	public boolean hasChunk(ChunkItem curChunk)
	{
		return chain.hasChunk(curChunk);
	}

	@Override
	public int compareTo(FileItem o)
	{
		return ((Integer) this.serial).compareTo(o.serial);
	}

	@Override
	public String toString()
	{
		return String.format("%s %,d", this.name, this.fileSize);
	}

	/**
	 * Find the overlapping areas between two files.
	 * <p>
	 * For each item from this file's chain it looks for a match on the other.
	 * If there is no match, it looks for the chunk on the other file's chain
	 * from the beginning. If there is a match it tries to find more and
	 * increase the current series counter.
	 * 
	 * @return List of overlapping series lengths. May be empty but never null.
	 */
	public LinkedList<Integer> getOverlapChunksSeries(FileItem otherFile)
	{
		return this.chain.getOverlapChunksSeries(otherFile.chain);
	}

	/**
	 * @return The internal chunk list (need to synchronize).
	 */
	public LinkedList<ChunkItem> getChunks()
	{
		return chain.getChunks();
	}
}
