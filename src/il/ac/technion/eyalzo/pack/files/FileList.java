package il.ac.technion.eyalzo.pack.files;

import il.ac.technion.eyalzo.pack.RabinUtils;
import il.ac.technion.eyalzo.pack.SimuResult;
import il.ac.technion.eyalzo.pack.stamps.ChainItem;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;
import il.ac.technion.eyalzo.pack.stamps.GlobalChunkList;
import il.ac.technion.eyalzo.pack.stamps.ReceiverChainStore;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

public class FileList {
	/**
	 * Full path directory name, ending with a slash.
	 */
	private String dirName;
	/**
	 * Optional parent directory.
	 */
	private LinkedList<FileList> children = new LinkedList<FileList>();
	/**
	 * Backup file name (not a full path).
	 */
	private final static String META_FILE = ".pack";

	/**
	 * File list.
	 */
	private HashMap<String, FileItem> fileList = new HashMap<String, FileItem>();

	//
	// Statistics
	//
	/**
	 * Number of files, includes those that were not stamped (probably due to
	 * size)
	 */
	private int statFilesInDir;
	/**
	 * Number of bytes in all files, includes those that were not stamped
	 * (probably due to size).
	 */
	private long statTotalSizeAll;
	/**
	 * Number of bytes in files with chunks.
	 */
	private long statTotalSizeWithChunks;
	/**
	 * How much time it took to load meta data from directory's persistent file.
	 */
	private long statMetaLoadTimeMillis;
	/**
	 * Time spent stamping. Total time that includes also multiple runs.
	 */
	private long statStampTime;

	/**
	 * @param dirName
	 *            Full path. May end with path separator.
	 * @param minFileSize
	 *            Minimal file size to even consider for stamping.
	 */
	public FileList(String dirName, long minFileSize) {
		this.dirName = dirName.endsWith(File.separator) ? dirName.substring(0,
				dirName.length() - 1) : dirName;

		//
		// Load file list by scanning the directory.
		//
		initFileList(minFileSize);
	}

	public void addChildren(FileList fileList) {
		synchronized (fileList) {
			this.children.add(fileList);
		}
	}

	/**
	 * @param minFileSize
	 *            Minimal file size to even consider for stamping.
	 */
	private void initFileList(long minFileSize) {
		// Clear current
		this.fileList.clear();
		statFilesInDir = 0;
		statTotalSizeWithChunks = 0;
		statTotalSizeAll = 0;

		File dir = new File(dirName);
		File[] fileList = dir.listFiles();
		
		// If directory does not exist
		if(fileList == null)
			return;
		
		try {
			for (File curFile : fileList) {
				// Skip the meta file itself
				if (curFile.getName().equals(META_FILE))
					continue;

				statTotalSizeAll += curFile.length();

				FileItem fileitem = FileItem.init(curFile, minFileSize);

				// Use only existing files beyond the minimal size
				if (fileitem == null)
					continue;

				// Count every file, even if too small
				statFilesInDir++;

				this.fileList.put(curFile.getName(), fileitem);

				statTotalSizeWithChunks += curFile.length();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadMeta(boolean debug, GlobalChunkList globalChunkList) {
		// TODO faster handling of file that do not exist on disk

		statMetaLoadTimeMillis = 0;

		File backupFile = new File(dirName, META_FILE);
		// File may not exist, like in first run for example
		if (!backupFile.exists())
			return;
		// Are there any files to save?
		if (fileList.isEmpty())
			return;

		// Load from file
		ByteBuffer buffer = FileUtils.readBlock(backupFile.getAbsolutePath(),
				0L, (int) backupFile.length(), ByteOrder.BIG_ENDIAN, null);
		if (buffer == null)
			return;

		long beforeLoadTime = System.currentTimeMillis();

		while (buffer.hasRemaining()) {
			//
			// File name
			//
			int stringLen = buffer.getShort();
			byte[] nameBytes = new byte[stringLen];
			buffer.get(nameBytes);
			String fileName = new String(nameBytes);

			// Continue even if file does not exist on disk
			FileItem fileItem = this.getFile(fileName);

			// 8: File size
			long fileSize = buffer.getLong();
			// 8: Last modified
			long lastModified = buffer.getLong();

			// Variable: chunks

			//
			// Make sure the file is still in the disk with the same file size
			// and modification time
			//
			File curFile = new File(dirName, fileName);
			boolean toLoad = true;
			if (fileItem == null) {
				toLoad = false;
				if (debug)
					System.out.println("   " + fileName + ": No longer exists");
			} else if (curFile.length() != fileSize) {
				toLoad = false;
				if (debug)
					System.out.println("   " + fileName + ": Size change");
			} else if (curFile.lastModified() != lastModified) {
				toLoad = false;
				if (debug)
					System.out.println("   " + fileName + ": Time change");
			}

			// Still need to load chunks?
			if (!toLoad) {
				// Need to read from the buffer for the next file
				while (true) {
					int len = 0x0000ffff & buffer.getShort();
					if (len == 0)
						break;
					buffer.getInt();
				}
				continue;
			}

			// Called only when the file was not changed, comparing to the meta
			fileItem.loadMetaChunks(buffer, globalChunkList);

			if (debug && fileItem.getChunkCount() > 0) {
				System.out.println(String.format("   %s: %,d chunks", fileName,
						fileItem.getChunkCount()));
			}
		}

		statMetaLoadTimeMillis = System.currentTimeMillis() - beforeLoadTime;
	}

	/**
	 * 
	 * @param runMillisBeforeSuspend
	 *            How much time to run before suspending.
	 * @param suspendMillis
	 *            Suspend time to relief the CPU load.
	 * @return Time spent (mSec) in calculations meaning the overall time minus
	 *         the suspension time.
	 */
	public long calculateMissingStamps(long runMillisBeforeSuspend,
			long suspendMillis, GlobalChunkList globalChunkList, boolean debug) {
		long result = 0;
		long before = System.currentTimeMillis();

		Iterator<Entry<String, FileItem>> it = fileList.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, FileItem> entry = it.next();
			FileItem curFile = entry.getValue();

			// Skip files that were not changed and had meta
			if (curFile.isLoadedMetaChunks())
				continue;

			String fileName = entry.getKey();

			if (debug)
				System.out.print("    " + fileName + " ... ");

			curFile.calculateStamps(dirName + File.separator + fileName,
					globalChunkList);

			if (debug)
				System.out.println(String.format("%,d chunks", curFile
						.getChunkCount()));

			long timePassed = System.currentTimeMillis() - before;
			if (timePassed >= runMillisBeforeSuspend) {
				result += timePassed;

				// Delay between calculations
				try {
					Thread.sleep(suspendMillis);
				} catch (InterruptedException e) {
				}

				before = System.currentTimeMillis();
			}
		}

		result += (System.currentTimeMillis() - before);

		statStampTime += result;

		return result;
	}

	public long getStatStampTime() {
		return statStampTime;
	}

	public boolean saveMeta() {
		File metaFile = new File(dirName, META_FILE);
		// Are there any files to save?
		if (fileList.isEmpty())
			return false;

		// TODO flexible size
		ByteBuffer buffer = ByteBuffer.allocate(4000000);

		Iterator<Entry<String, FileItem>> it = fileList.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, FileItem> entry = it.next();
			String fileName = entry.getKey();
			FileItem curFile = entry.getValue();

			curFile.addSaveLine(fileName, buffer);
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

	public int size() {
		return fileList.size();
	}

	/**
	 * 
	 * @return Number of files in directory, includes files that are too small
	 *         to stamp.
	 */
	public int getStatFilesCountAll() {
		return this.statFilesInDir;
	}

	public long getStatTotalSizeAll() {
		return statTotalSizeAll;
	}

	public long getStatTotalSizeWithChunks() {
		return statTotalSizeWithChunks;
	}

	public long getStatMetaLoadTimeMillis() {
		return this.statMetaLoadTimeMillis;
	}

	public DisplayTable webGuiFileList(String fileDetailsLink) {
		long avgChunk = RabinUtils.getAverageChunkLen();
		long lowChunk = avgChunk / 2;
		long highChunk = avgChunk * 2;

		DisplayTable table = new DisplayTable();

		table.addCol("Name", "File name in directory", true);
		table.addColNum("Size", "File size", false, true, true, null, " KB");
		table.addCol("Chunks", "Number of stamps (chunks)", false);
		table.addCol("Avg.<br>chunk", "Average chunk size", false);
		table.addCol("Overlap<br>files",
				"More files that have at least one chunk that this file has",
				false);
		table.addColTime("Last<br>modified", "Last modified time", false, true,
				true, false);

		Iterator<Entry<String, FileItem>> it = fileList.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, FileItem> entry = it.next();
			String fileName = entry.getKey();
			FileItem curFile = entry.getValue();
			int chunkCount = curFile.getChunkCount();
			long curAvgChunk = chunkCount == 0 ? 0 : curFile.fileSize
					/ chunkCount;

			table
					.addRow(curAvgChunk > highChunk || curAvgChunk < lowChunk ? "yellow"
							: null);

			table.addCell(fileName, fileDetailsLink + dirName + File.separator
					+ fileName);
			table.addCell(curFile.fileSize / 1024);
			// Chunks
			table.addCell(chunkCount);
			// Avg. chunk
			table.addCell(curAvgChunk);
			// Overlap files
			table.addCell(curFile.getOverlapFiles().size());
			// Last modified
			table.addCell(curFile.lastModified);
		}

		return table;
	}

	/**
	 * @param fileName
	 *            May be full path or only the file itself.
	 */
	public FileItem getFile(String fileName) {
		//
		// Get file name, after the path
		//
		String fixedName;
		int lastSlash = fileName.lastIndexOf(File.separatorChar);
		fixedName = lastSlash >= 0 ? fileName.substring(lastSlash + 1)
				: fileName;

		synchronized (fileList) {
			return fileList.get(fixedName);
		}
	}

	public FileItem getFile(int fileSerial) {
		synchronized (fileList) {
			for (FileItem curFile : fileList.values()) {
				if (curFile.getSerial() == fileSerial)
					return curFile;
			}
		}

		return null;
	}

	public String getFileName(int fileSerial) {
		synchronized (fileList) {
			Iterator<Entry<String, FileItem>> it = fileList.entrySet()
					.iterator();
			while (it.hasNext()) {
				Entry<String, FileItem> entry = it.next();
				String fileName = entry.getKey();
				FileItem curFile = entry.getValue();
				if (curFile.getSerial() == fileSerial)
					return fileName;
			}
		}

		return null;
	}

	public int getChildrenCount() {
		return this.children.size();
	}

	public DisplayTable webGuiStatistics() {
		DisplayTable table = new DisplayTable();

		//
		// Files size
		//
		table.addField("Files size, all", statTotalSizeAll,
				"Total size of all the files, includes small files");
		table.addField("Files size, chunked files", String.format(
				"%,d (%,d %%)", statTotalSizeWithChunks,
				(statTotalSizeWithChunks * 100 / statTotalSizeAll)),
				"Total size of all the chunks, without small files");

		//
		// Chunks
		//
		table.addField(null, null, null);
		int chunkCount = this.getChunkCount();
		HashSet<ChunkItem> chunksDistinct = this.getChunksDistinct();
		int chunkCountDistinct = chunksDistinct.size();
		table.addField("Chunks", chunkCount,
				"Number of chunks in all the files, may contain duplicates");
		table.addField("Chunks, distinct", String.format("%,d (%,d%%)",
				chunkCountDistinct, chunkCountDistinct * 100 / chunkCount),
				"Number of chunks in all the files, may contain duplicates");

		// Avg chunk
		table.addField("Avg. chunk size", statTotalSizeWithChunks / chunkCount,
				"Size of the largest chunk");

		// Avg chunk distinct
		long statDistinctChunksBytes = this.getStatDistinctChunksBytes();
		table.addField("Avg. chunk size, distinct", statDistinctChunksBytes
				/ chunkCountDistinct, "Size of the largest chunk");

		// Max chunk
		ChunkItem maxChunkBySize = Collections.max(chunksDistinct);
		table.addField("Max chunk size", maxChunkBySize.getLength(),
				"Size of the largest chunk");

		// Min chunk
		ChunkItem minChunkBySize = Collections.min(chunksDistinct);
		table.addField("Min chunk size", minChunkBySize.getLength(),
				"Size of the smallest chunk");

		//
		// Redundancy
		//
		table.addField(null, null, null);
		long statRedundantBytes = statTotalSizeWithChunks
				- statDistinctChunksBytes;
		table.addField("Redundant bytes", String.format("%,d (%,d%%)",
				statRedundantBytes,
				(statRedundantBytes * 100 / statTotalSizeAll)),
				"Number of bytes that could be saved with deduplication");

		return table;
	}

	private HashSet<ChunkItem> getChunksDistinct() {
		HashSet<ChunkItem> result = new HashSet<ChunkItem>();

		synchronized (fileList) {
			for (FileItem curFile : fileList.values()) {
				result.addAll(curFile.getChunks());
			}
		}

		return result;
	}

	/**
	 * 
	 * @return Number of chunks in all the (chunked) files together. That
	 *         includes duplicates (redundant).
	 */
	private int getChunkCount() {
		int result = 0;

		synchronized (fileList) {
			for (FileItem curFile : fileList.values()) {
				result += curFile.getChunkCount();
			}
		}

		return result;
	}

	private long getStatDistinctChunksBytes() {
		HashSet<ChunkItem> distinctChunks = this.getChunksDistinct();

		long result = 0;

		for (ChunkItem curChunk : distinctChunks) {
			result += curChunk.getLength();
		}

		return result;
	}

	/**
	 * @return File list, sorted by last modified time (ascending).
	 */
	private LinkedList<FileItem> getFileListSortedByTime() {
		LinkedList<FileItem> filesSorted;
		synchronized (fileList) {
			filesSorted = new LinkedList<FileItem>(fileList.values());
		}
		Collections.sort(filesSorted, new Comparator<FileItem>() {

			@Override
			public int compare(FileItem o1, FileItem o2) {
				return ((Long) o1.lastModified).compareTo(o2.lastModified);
			}
		});
		return filesSorted;
	}

	/**
	 * @return Chain list, sorted by files' last modified time (ascending).
	 */
	private LinkedList<ChainItem> getChainListSortedByFileTime() {
		LinkedList<ChainItem> chainsSorted = new LinkedList<ChainItem>();

		LinkedList<FileItem> filesSorted = this.getFileListSortedByTime();

		for (FileItem curFile : filesSorted) {
			chainsSorted.add(curFile.getChain());
		}
		return chainsSorted;
	}

	/**
	 * @param receiverPredicitionChunks
	 *            Max number of chunks in receiver prediction.
	 * @param senderSyncMaxAttempts
	 *            Max match attempts on miss, meaning the number of chunks that
	 *            the sender will compare with the prediction before it returns
	 *            to idle.
	 * @param snapshotByTime
	 *            True for snapshots by time or false for snapshots by bytes.
	 * @param snapshotInterval
	 *            Bytes or millis.
	 * @return Dedicated structure with many results related to this simulation.
	 *         Among them the number of bytes not sent thanks to matched
	 *         prediction.
	 */
	public SimuResult simulate(int receiverPredicitionChunks,
			int senderSyncMaxAttempts, long speedDataBitspersec,
			long speedPackBitspersec, int rttMillis, boolean debug,
			boolean snapshotByTime, long snapshotInterval) {
		// Sort the chain list by file modification time
		LinkedList<ChainItem> chainsSorted = this
				.getChainListSortedByFileTime();

		return simulate(chainsSorted, receiverPredicitionChunks,
				senderSyncMaxAttempts, speedDataBitspersec,
				speedPackBitspersec, rttMillis, debug, snapshotByTime,
				snapshotInterval);
	}

	static SimuResult simulate(LinkedList<ChainItem> chainsSorted,
			int receiverPredicitionChunks, int senderSyncMaxAttempts,
			boolean debug) {
		return simulate(chainsSorted, receiverPredicitionChunks,
				senderSyncMaxAttempts, 0, 0, 0, debug, true, 0);
	}

	/**
	 * @param receiverPredicitionChunks
	 *            Max number of chunks in receiver prediction.
	 * @param senderSyncMaxAttempts
	 *            Max match attempts on miss, meaning the number of chunks that
	 *            the sender will compare with the prediction before it returns
	 *            to idle.
	 * @return Dedicated structure with many results related to this simulation.
	 *         Among them the number of bytes not sent thanks to matched
	 *         prediction.
	 */
	static SimuResult simulate(LinkedList<ChainItem> chainsSorted,
			int receiverPredicitionChunks, int senderSyncMaxAttempts,
			long speedDataBitspersec, long speedPackBitspersec, int rttMillis,
			boolean debug, boolean snapshotByTime, long snapshotInterval) {
		SimuResult result = new SimuResult(receiverPredicitionChunks,
				senderSyncMaxAttempts, speedDataBitspersec,
				speedPackBitspersec, rttMillis, snapshotByTime,
				snapshotInterval);

		if (debug)
			System.out
					.println(String
							.format(
									"\r\n\r\nSimulate (receiver_predicition k=%,d   sender_attempts r=%,d)"
											+ "\r\n=======================================================",
									receiverPredicitionChunks,
									senderSyncMaxAttempts));

		// Build a new receiver chunk store for this simulation
		ReceiverChainStore receiverChainStore = new ReceiverChainStore();

		// Chain used by receiver to predict, and partially seen by the sender
		// as prediction
		ChainItem receiverChain = null;
		// Where the future prediction starts, according to the sender's view
		int searchStartOffset = -1;
		// Current number of chunks that the sender tried to match after a miss
		int senderResyncAttempts = 0;

		// Loop through files/chains
		result.senderFiles = chainsSorted.size();
		for (ChainItem curChain : chainsSorted) {
			if (debug)
				System.out.print(String.format("Chain %,d (%,d):", curChain
						.getSerial(), curChain.size()));
			
			result.addFile();

			// Loop through chunks
			for (ChunkItem curChunk : curChain.getChunks()) {
				// If the receiver have no idea what is coming next
				if (receiverChain == null) {
					// Look for the chain
					receiverChain = receiverChainStore.getChainForChunk(
							curChunk.getStamp(), curChunk.getLength());

					// Remember the chunk as sent, but for LBFS remember if
					// chunk is known
					result.addSentChunk(curChunk.getLength(),
							receiverChain != null);

					// Find the chunk in the chain
					if (receiverChain == null) {
						result.receiverChainLookupFail++;
						if (debug)
							System.out.print(String.format(" %s", curChunk));
					} else {
						result.addReceiverChainLookupSuccess(false, false);
						// Now we have a first match in hand
						senderResyncAttempts = 0;
						// Find the chunk in the chain
						searchStartOffset = receiverChain.indexOf(curChunk
								.getStamp(), curChunk.getLength());
						// It must be valid!
						if (searchStartOffset >= 0) {
							// Point to the next chunk in the chain
							searchStartOffset++;
						}

						if (debug)
							System.out.print(String.format(" (sync %,d-%,d)%s",
									receiverChain.getSerial(),
									searchStartOffset, curChunk));
					}

					receiverChainStore.addChunk(curChunk.getStamp(), curChunk
							.getLength(), curChain.getSerial());

					continue;
				}

				// The receiver already had a chain in hand

				// Get the match offset
				int curReceiverPredictionChunks = receiverPredicitionChunks;
				int matchOffset = receiverChain.indexOf(curChunk.getStamp(),
						curChunk.getLength(), searchStartOffset,
						curReceiverPredictionChunks);

				// If match the next expected (or more if sender can resync)
				if (matchOffset >= 0) {
					searchStartOffset = matchOffset + 1;

					// Add to result the number of saved bytes
					result.addSavedChunk(curChunk.getLength());
					result.senderSha1SuccessBytes += curChunk.getLength();

					// Must appear before the reset
					if (debug)
						System.out.print(String.format(" (match %,d-%,d)%s",
								receiverChain.getSerial(), (matchOffset + 1),
								curChunk));

					// End of chain? then reset
					if (searchStartOffset >= receiverChain.size()) {
						result.receiverEndOfChain++;
						receiverChain = null;
					}
					receiverChainStore.addChunk(curChunk.getStamp(), curChunk
							.getLength(), curChain.getSerial());
					continue;
				}

				result.senderSha1FailBytes += curChunk.getLength();

				// Break or further attempt - need to resync

				// First miss (break)?
				if (senderResyncAttempts == 0) {
					result.receiverChunkMissed++;
				}

				// Sender transmit data, and receiver try to find another chain
				ChainItem resyncChain = receiverChainStore.getChainForChunk(
						curChunk.getStamp(), curChunk.getLength());
				if (resyncChain == null) {
					result.receiverChainLookupFail++;
					result.receiverChainLookupFailImmed++;
				} else {
					receiverChain = resyncChain;
					// Now we have a first match in hand
					senderResyncAttempts = 0;
					// Find the chunk in the chain
					searchStartOffset = receiverChain.indexOf(curChunk
							.getStamp(), curChunk.getLength());
					// It must be valid!
					if (searchStartOffset >= 0) {
						// Point to the next chunk in the chain
						searchStartOffset++;
					}
					result.addSentChunk(curChunk.getLength(), true);
					result.addReceiverChainLookupSuccess(true,
							searchStartOffset > 0);
					receiverChainStore.addChunk(curChunk.getStamp(), curChunk
							.getLength(), curChain.getSerial());
					if (debug)
						System.out.print(String.format(" (resync %,d-%,d)%s",
								receiverChain.getSerial(), searchStartOffset,
								curChunk));
					continue;
				}

				senderResyncAttempts++;

				// If have to give up
				if (senderResyncAttempts >= senderSyncMaxAttempts) {
					receiverChain = null;
				}

				result.addSentChunk(curChunk.getLength(), false);
				receiverChainStore.addChunk(curChunk.getStamp(), curChunk
						.getLength(), curChain.getSerial());

				if (debug) {
					if (senderResyncAttempts == 1) {
						System.out.print(String
								.format(" (break)%s", (curChunk)));
					} else {
						System.out.print(String.format(" (attempt %,d)%s",
								senderResyncAttempts, curChunk));
					}
				}
			}

			receiverChainStore.closeChain();

			if (debug)
				System.out.println();
		}

		result.closeMatchingChain();

		if (debug)
			System.out.print(String
					.format("Result=%,d", result.getSavedBytes()));

		return result;
	}
}
