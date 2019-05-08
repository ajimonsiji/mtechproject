/**
 * Prediction list for outbox, sent-items and inbox.
 */
package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.pack.conns.TcpUtils;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class PredOutChunks
{
	/**
	 * TCP sequence of the next expected chunk.
	 */
	private long tcpSeqNext;
	/**
	 * All the chunks in ascending subsequent order. Key is the TCP sequence and value is the predicted chunk item.
	 */
	private LinkedHashMap<Long, ChunkItem> chunks = new LinkedHashMap<Long, ChunkItem>();

	public LinkedHashMap<Long, ChunkItem> getChunks()
	{
		return chunks;
	}

	public void init(long tcpSeq)
	{
		synchronized (chunks)
		{
			chunks.clear();
			this.tcpSeqNext = tcpSeq;
		}
	}

	/**
	 * Add chunk to the end of the prediction list, and update the TCP sequence of the next expected prediction.
	 * 
	 * @param predChunk
	 */
	public void addPredChunkSubsequent(ChunkItem predChunk)
	{
		synchronized (chunks)
		{
			chunks.put(tcpSeqNext, predChunk);
			tcpSeqNext = TcpUtils.tcpSequenceAdd(tcpSeqNext, predChunk.getLength());
		}
	}

	/**
	 * Conditional addition of chunk to the end of the prediction list, and update the TCP sequence of the next expected
	 * prediction.
	 * 
	 * @param tcpSeq
	 *            TCP sequence of the chunk to add.
	 * @param predChunk
	 *            The chunk itself.
	 * @return False if the chunk was not added because it is older than the last chunk in the list (by half of the
	 *         sequence range).
	 */
	public boolean addPredChunk(long tcpSeq, ChunkItem predChunk)
	{
		synchronized (chunks)
		{
			// It allows half of the sequence range
			if (tcpSeqNext > 0 && TcpUtils.tcpSequenceDiff(tcpSeqNext, tcpSeq) < 0)
				return false;

			chunks.put(tcpSeq, predChunk);
			tcpSeqNext = TcpUtils.tcpSequenceAdd(tcpSeq, predChunk.getLength());
		}

		return true;
	}

	public boolean isEmpty()
	{
		synchronized (chunks)
		{
			return chunks.isEmpty();
		}
	}

	/**
	 * @param tcpSeq
	 *            TCP sequence of any prediction.
	 * @return True if the prediction list contains a chunk in that offset.
	 */
	public boolean contains(long tcpSeq)
	{
		synchronized (chunks)
		{
			return chunks.containsKey(tcpSeq);
		}
	}

	/**
	 * @return Number of chunks in the prediction list.
	 */
	public int size()
	{
		synchronized (chunks)
		{
			return chunks.size();
		}
	}

	/**
	 * @param index
	 *            0-based index, can also be negative where -1 is the last item, -2 is the one before last, etc.
	 * @return TCP sequence of the requested chunk or 0 if something goes wrong.
	 */
	public long getTcpSeq(int index)
	{
		long result = 0;

		synchronized (chunks)
		{
			// Negative is index from end while -1 means last
			if (index < 0)
				index = size() + index;

			// Sanity
			if (index < 0)
				return 0;

			Iterator<Long> it = chunks.keySet().iterator();
			int count = 0;
			while (it.hasNext())
			{
				result = it.next();
				if (count == index)
					return result;

				count++;
			}
		}

		// It should not get here
		return 0;
	}

	/**
	 * 
	 * @return TCP sequence of the next expected chunk start, which is the byte after the last chunk in the list.
	 */
	public long getTcpSeqNext()
	{
		return this.tcpSeqNext;
	}

	/**
	 * @param count
	 *            How many chunks to sum from start (positive) or end (negative).
	 * @return 0 on index error or the total length of count chunks.
	 */
	public long getChunksLength(int count)
	{
		synchronized (chunks)
		{
			// Sanity
			if (count == 0 || count > chunks.size() || count < -chunks.size())
				return 0;

			long result = 0;

			// Positive
			if (count > 0)
			{
				for (ChunkItem curChunk : chunks.values())
				{
					result += curChunk.getLength();
					if (count-- == 0)
						return result;
				}
			}

			// Negative

			// How many to skip
			int skip = chunks.size() + count;
			for (ChunkItem curChunk : chunks.values())
			{
				if (skip > 0)
				{
					skip--;
					continue;
				}

				result += curChunk.getLength();
			}

			return result;
		}
	}

	public String toString(long seqStart, int from, int len)
	{
		StringBuffer buffer = new StringBuffer(len * 32);

		synchronized (chunks)
		{
			int skip = from >= 0 ? from : chunks.size() + from;

			Iterator<Entry<Long, ChunkItem>> it = chunks.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<Long, ChunkItem> entry = it.next();
				if (skip-- > 0)
					continue;

				if (len-- < 0)
					break;

				ChunkItem curChunk = entry.getValue();
				long relSeqStart = TcpUtils.tcpSequenceDiff(seqStart, entry.getKey());
				long relSeqEnd = relSeqStart + curChunk.getLength() - 1;

				buffer.append(String.format("(%,d-%,d) %08x ", relSeqStart, relSeqEnd, curChunk.getStamp()));
			}
		}

		return buffer.toString();
	}

	/**
	 * Get and remove the chunk with the given TCP sequence.
	 * 
	 * @return Null if the chunk is not found.
	 */
	public ChunkItem popChunk(long remoteSeq)
	{
		synchronized (chunks)
		{
			return chunks.remove(remoteSeq);
		}
	}
}
