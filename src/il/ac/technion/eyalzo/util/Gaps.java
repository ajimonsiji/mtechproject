package il.ac.technion.eyalzo.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Gap list for predictions.
 * <p>
 * Gaps are 1-based blocks that we don't own for now, inclusive. Empty when
 * chunk is complete.
 * 
 * <pre>
 * Gaps gaps = new Gaps(1000);
 * gaps.fillGap(1, 1000);
 * </pre>
 */
public class Gaps
{
	/**
	 * Chunk size in bytes.
	 * <p>
	 * Saved here for boundary verification.
	 */
	protected long chunkSize;
	/**
	 * Gap list.
	 */
	protected List<LongRange> gapList = Collections
			.synchronizedList(new LinkedList<LongRange>());

	/**
	 * Initialize with one big gap from 1 to chunk-size.
	 * 
	 * @param chunkSize
	 *            Chunk size in bytes.
	 */
	public Gaps(long chunkSize)
	{
		this.chunkSize = chunkSize;

		// Safety check
		if (chunkSize <= 0)
		{
			return;
		}

		// Set one big gap for the entire chunk
		this.set(1, chunkSize);
	}

	/**
	 * @return True when gap list is empty, meaning the chunk is complete.
	 */
	public boolean isChunkComplete()
	{
		return gapList.isEmpty();
	}

	/**
	 * Set gap-list to have only one item, as specified here.
	 * <p>
	 * Can be used for 1-based gaps or 0-based, because after calling this
	 * method the gap list is reset to this range, and from now on it is up to
	 * the caller to decide how to use it. For example, if you have a 1000 bytes
	 * chunk, you can use it as 0-based:
	 * 
	 * <pre>
	 * gaps.set(0, 999);
	 * </pre>
	 * 
	 * Or as 1-based:
	 * 
	 * <pre>
	 * gaps.set(1, 1000);
	 * </pre>
	 * 
	 * @param start
	 *            Missing-range start, inclusive.
	 * @param end
	 *            Missing-range end, inclusive.
	 */
	public synchronized void set(long start, long end)
	{
		gapList.clear();
		gapList.add(new LongRange(start, end));
	}

	@Override
	/**
	 * Returns a string representing the gap list for save in chunk.
	 * <p>
	 * For example "0-120,450-899,"
	 * 
	 * @return Gap list as comma-separated list (with ending comma).
	 */
	public synchronized String toString()
	{
		// For empty gap-list return empty string.
		if (gapList.isEmpty())
		{
			return "";
		}

		StringBuffer result = new StringBuffer();

		int i = 0;
		for (LongRange curGap : gapList)
		{
			i++;
			result.append(curGap.start);
			result.append('-');
			result.append(curGap.end);

			// do not add comma for last gap
			if (i < gapList.size())
				result.append(',');
		}

		return result.toString();
	}

	/**
	 * Get the number of gaps in list.
	 * <p>
	 * The returned value is usually meaningless, because 1 gap might be bigger
	 * than 2 gaps, when counting the bytes. But it is provided anyway.
	 * 
	 * @return Number of gaps in list.
	 */
	public int getGapsCount()
	{
		return gapList.size();
	}

	/**
	 * @return The gap list, exposed
	 */
	public List<LongRange> getGapList()
	{
		return gapList;
	}

	/**
	 * How many bytes are still missing, meaning they are included in gaps.
	 * 
	 * @return Number of missing bytes.
	 */
	public synchronized long getMissingBytesCount()
	{
		long result = 0;

		for (LongRange curGap : gapList)
		{
			result += (curGap.end - curGap.start + 1);
		}

		return result;
	}

	/**
	 * @return Number of downloaded bytes according to gaps.
	 */
	public long getDownloaded()
	{
		return chunkSize - getMissingBytesCount();
	}

	/**
	 * Clear gap-list.
	 */
	public synchronized void clear()
	{
		gapList.clear();
	}

	/**
	 * @return The chunk size.
	 */
	public long getChunkSize()
	{
		return chunkSize;
	}

	/**
	 * Update gap-list with the new data arrival.
	 * <p>
	 * This method handles all the posibble cases, although in real usage the
	 * received bytes usually split gaps and then cover them from left to right.
	 * We also update here the complete-bytes counter, because we might already
	 * have some of the recevied bytes. On return (>0), call
	 * PartsBitmap#updatePartsStatus(), to fix the parts-bitmap if needed.
	 * <p>
	 * Examples for the different cases are described below (some have two
	 * examples):
	 * 
	 * <pre>
	 *                             1. Left-full  2. Left-part  3. Right-part  4. Mid-part
	 *                       Gap:  ===    ===    =====         =====  ====    =======
	 *                       New:  #####  ###    ###             ###    ####    ###
	 * </pre>
	 * 
	 * If the given range is illegal, filling will not be performed and the
	 * returned value will be 0.
	 * 
	 * @param newStart
	 *            1-based start position, inclusive.
	 * @param newEnd
	 *            1-based end position, inclusive.
	 * @return 0 on range failure. Otherwise number of bytes removed from gaps,
	 *         which may differ from the given range if some of the range is not
	 *         in gaps.
	 */
	public synchronized long fillGap(long newStart, long newEnd)
	{
		// Verify positions
		if (newStart < 0 || newStart > newEnd || newEnd > chunkSize)
			return 0;

		// Check if there are any gaps left
		if (gapList.isEmpty())
		{
			return 0;
		}

		// Skip gaps that end before that range even starts
		int i = 0;
		while (i < gapList.size())
		{
			// Check if that gap might have anything to do with the received bytes
			if (gapList.get(i).end >= newStart)
			{
				break;
			}
			// Next gap
			i++;
		}

		// If we didn't find overlapping gap
		// This should not happen, because the parts-request was based on gaps we have, and the part-answer was matched
		// with pending-request before this call
		if (i >= gapList.size() || gapList.get(i).start > newEnd)
		{
			return 0;
		}

		long completedBytes = 0;

		// We found at least one overlapping gap
		// Currently received bytes will always overlap only one gap, but this was made for future use to fix all
		// related gaps
		while (i < gapList.size())
		{
			LongRange curGap = gapList.get(i);
			// That can happen only with the first found overlapping gap
			if (curGap.start >= newStart)
			{
				// 1. Left-full, so we remove that gap from list, and look for more gaps
				if (curGap.end <= newEnd)
				{
					completedBytes += (curGap.end - curGap.start + 1);
					gapList.remove(i);
					continue; // Don't advance the counter
				}
				// 2. Left-partial, so we cut the gap from left, and quit here
				if (curGap.start <= newEnd)
				{
					completedBytes += (newEnd - curGap.start + 1);
					curGap.start = newEnd + 1;
				}
				return completedBytes;
			}
			// That can happen only with the first found overlapping gap
			else if (curGap.start < newStart)
			{
				// 3. Right-partial, so we cut the gap from right, and look for more relevant gaps
				if (curGap.end <= newEnd)
				{
					completedBytes += (curGap.end - newStart + 1);
					curGap.end = newStart - 1;
				}
				// 4. Mid-part, so we have to split the current gap into two gaps and quit
				else
				{
					completedBytes += (newEnd - newStart + 1);
					// Split the gap, meaning add a new gap
					LongRange newGap = new LongRange(newEnd + 1, curGap.end);
					curGap.end = newStart - 1;
					gapList.add(i + 1, newGap);
					return completedBytes;
				}
			}
			// The gap starts after our range
			else if (curGap.start > newEnd)
			{
				return completedBytes;
			}

			i++;
		}
		return completedBytes;
	}

	/**
	 * @param checkStart
	 *            Inclusive range check start.
	 * @param checkEnd
	 *            Inclusive range check end.
	 * @return True if the given range check does not overlap even one of the
	 *         gaps.
	 */
	public synchronized boolean isFull(long checkStart, long checkEnd)
	{
		// Sanity check
		if (checkEnd < checkStart || checkEnd > this.chunkSize)
			return false;

		// Check if there are any gaps left
		if (gapList.isEmpty())
			return true;

		for (LongRange curRange : gapList)
		{
			if (curRange.start > checkEnd)
				return true;

			if (curRange.end >= checkStart)
				return false;
		}

		return true;
	}

	/**
	 * Fill all gaps by removing them.
	 */
	public void fillAll()
	{
		this.fillGap(1, this.chunkSize);
	}
}