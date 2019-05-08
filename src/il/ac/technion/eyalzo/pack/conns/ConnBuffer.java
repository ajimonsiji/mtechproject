package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.RabinUtils;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;

import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Receiver buffer per connection that accumulates incoming packets until it is possible to sign and shift.
 * <p>
 * 
 * When packet arrive call {@link #addData(byte[], int, long)} and then {@link #findNextAnchor()} right after it to
 * check if a <b>new</b> anchor was found now thanks to that new data. If a number other than -1 is returned it means
 * that such an anchor was found. With the new anchor call {@link #calculateSha1()} and then
 * {@link #shiftDataByAnchor()}.
 */
public class ConnBuffer
{
	/**
	 * Internal buffer size. Should suffice to hold forward data that is data received after a missing TCP data.
	 */
	private static int DATA_CAPACITY = RabinUtils.MAX_CHUNK_LEN * 4;
	/**
	 * Data itself.
	 */
	protected byte[] internalBuffer;
	/**
	 * Connection serial number, for debug prints.
	 */
	protected long connSerial;
	/**
	 * TCP sequence of the first byte of the connection.
	 */
	private long connStartSeq;
	/**
	 * TCP sequence of the first byte of {@link #internalBuffer}.
	 */
	protected long seqBuffer;
	/**
	 * Current length of {@link #internalBuffer}.
	 */
	protected int dataLen;
	/**
	 * Forward data, after missing packet(s). Key is the offset and value is the size.
	 */
	protected TreeMap<Integer, Integer> forwards = new TreeMap<Integer, Integer>();

	//
	// Anchor
	//
	/**
	 * Where to start searching for anchor next time.
	 */
	protected int anchorSearchOffset;
	/**
	 * Buffer-offset of the byte after the last found anchor. If {@link #chunkStartOffset} is not -1 it points to the
	 * byte after the last found chunk. Starts with -1 so the first anchor will not be considered as chunk.
	 */
	protected int chunkEndOffset = -1;
	/**
	 * Offset of the first byte in the last found chunk
	 */
	protected int chunkStartOffset = -1;

	/**
	 * 
	 * @param connSerial
	 *            Connection's serial number starting at 1 for debug messages only.
	 * @param connStartSeq
	 *            TCP sequence of the first byte in the payload of this connection (one after the SYN).
	 */
	public ConnBuffer(long connSerial, long connStartSeq)
	{
		this.connSerial = connSerial;
		this.connStartSeq = connStartSeq;
	}

	/**
	 * Add data to the internal buffer, from current offset until the source and/or internal buffer are exhausted.
	 * 
	 * @param data
	 *            Source data which is a TCP packet.
	 * @param offset
	 *            Offset where the TCP packet starts in the given data (raw IP).
	 * @param len
	 *            Number of bytes to add.
	 */
	public synchronized void addData(byte[] data, int dataOffset, int len, long tcpSeq)
	{
		// Initialize for the first time and save TCP sequence
		if (this.internalBuffer == null)
		{
			// Sanity check, in case the connection was initialized with a higher sequence (usually during tests)
			long diff = TcpUtils.tcpSequenceDiff(this.connStartSeq, tcpSeq);
			if (diff < 0)
				return;
			this.internalBuffer = new byte[DATA_CAPACITY];
			this.seqBuffer = tcpSeq;
		}

		// No data
		if (len <= 0)
			return;

		// Find the offset in buffer of the given data
		int bufferOffset = (int) TcpUtils.tcpSequenceDiff(this.seqBuffer, tcpSeq);

		// If the offset has a history sequence, then assume a duplicate and quit
		if (bufferOffset < dataLen)
		{
			// TODO handle partial overlaps
			if (Main.debugLevel >= 5)
				System.out.println(String.format("      %,d: RCV %,d duplicate", connSerial,
						getConnOffsetFromTcpSeq(tcpSeq)));
			return;
		}

		// Overflow
		if (bufferOffset + len > this.internalBuffer.length)
		{
			byte[] tempBuffer = new byte[bufferOffset + len + 2 * RabinUtils.MAX_CHUNK_LEN];
			System.arraycopy(internalBuffer, 0, tempBuffer, 0, dataLen);
			internalBuffer = tempBuffer;
		}

		// Copy data to the internal buffer
		System.arraycopy(data, dataOffset, this.internalBuffer, bufferOffset, len);

		// Forward data?
		if (bufferOffset > dataLen)
		{
			//
			// Try to merge with former forward
			//

			// Get a key-value (offset-length) with the greatest offset that is less or equal to the new forward
			Entry<Integer, Integer> entry = forwards.floorEntry(bufferOffset);
			if (entry != null)
			{
				int foundOffset = entry.getKey();
				int foundLen = entry.getValue();
				if (foundOffset + foundLen >= bufferOffset)
					forwards.put(foundOffset, len + (bufferOffset - foundOffset));
				else
					forwards.put(bufferOffset, len);
			} else
				// Remember for future the forward data
				forwards.put(bufferOffset, len);

			// Debug print
			if (Main.debugLevel >= 5)
				System.out.println(String.format("      %,d: RCV %,d forward %,d diff %,d", connSerial,
						getConnOffsetFromTcpSeq(tcpSeq), this.forwards.size(), (bufferOffset - dataLen)));
			return;
		}

		// Data offset is exactly what TCP expected now
		dataLen += len;

		handleForwards();
	}

	/**
	 * Walk through the forward list and see if any or all can be now used thanks to new data that may have closed the
	 * gap.
	 */
	private void handleForwards()
	{
		Iterator<Entry<Integer, Integer>> it = this.forwards.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<Integer, Integer> entry = it.next();
			int forwardBufferOffset = entry.getKey();

			// Still points to forward data
			if (forwardBufferOffset > dataLen)
				return;

			// Debug print
			if (Main.debugLevel >= 5)
				System.out.println(String.format("      %,d: RCV fixed %,d forwards %,d", this.connSerial,
						getConnOffsetFromBufferOffset(forwardBufferOffset), this.forwards.size()));

			int forwardLength = entry.getValue();
			int nextDataLen = forwardBufferOffset + forwardLength;

			// Jump to the next considering the rare option that all forward
			// item overlaps with past data
			dataLen = Math.max(dataLen, nextDataLen);

			it.remove();
		}
	}

	/**
	 * @param bufferOffset
	 *            Offset in current buffer.
	 * @return Offset relative to the connection's start that is based on the sequence of the first
	 *         {@link #addData(byte[], int, long)}.
	 */
	public synchronized long getConnOffsetFromBufferOffset(int bufferOffset)
	{
		return TcpUtils.tcpSequenceDiff(this.connStartSeq, TcpUtils.tcpSequenceAdd(seqBuffer, bufferOffset));
	}

	/**
	 * @param bufferOffset
	 *            Offset in current buffer.
	 * @return TCP sequence of the given byte by offset.
	 */
	public synchronized long getSeqFromBufferOffset(int bufferOffset)
	{
		return TcpUtils.tcpSequenceAdd(seqBuffer, bufferOffset);
	}

	/**
	 * @return Offset relative to the connection's start that is based on the sequence of the first
	 *         {@link #addData(byte[], int, long)}.
	 */
	private synchronized long getConnOffsetFromTcpSeq(long tcpSeq)
	{
		return TcpUtils.tcpSequenceDiff(this.connStartSeq, tcpSeq);
	}

	public boolean isChunkReady()
	{
		return chunkStartOffset >= 0 && chunkEndOffset > chunkStartOffset;
	}

	public synchronized int calculateSha1()
	{
		// If there is no data or no anchor
		if (!isChunkReady())
			return 0;

		return RabinUtils.calculateSha1(internalBuffer, chunkStartOffset, chunkEndOffset - chunkStartOffset);
	}

	/**
	 * 
	 * @return Length of data stored in the internal buffer, up to the last byte even if stored as forward data after
	 *         gaps.
	 */
	public synchronized int getLenWithForwards()
	{
		int result = dataLen;

		// TODO smarter forwards so the last item will be enough
		Iterator<Entry<Integer, Integer>> it = this.forwards.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<Integer, Integer> entry = it.next();
			int forwardBufferOffset = entry.getKey();
			int forwardLength = entry.getValue();

			result = Math.max(result, forwardBufferOffset + forwardLength);
		}

		return result;
	}

	protected synchronized void shiftForwards(int shift)
	{
		if (forwards.isEmpty())
			return;

		TreeMap<Integer, Integer> newForwards = new TreeMap<Integer, Integer>();
		Iterator<Entry<Integer, Integer>> it = this.forwards.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<Integer, Integer> entry = it.next();
			int forwardBufferOffset = entry.getKey();
			int forwardLength = entry.getValue();

			newForwards.put(forwardBufferOffset - shift, forwardLength);
		}

		forwards = newForwards;
	}

	synchronized int getDataLen()
	{
		return this.dataLen;
	}

	synchronized void printData(int offset, int len)
	{
		int endOffset = offset + len;
		int newLine = 0;
		for (int i = offset; i < endOffset; i++)
		{
			System.out.print(String.format("%02x ", internalBuffer[i]));
			newLine++;
			if (newLine == 16)
			{
				System.out.println();
				newLine = 0;
			} else if (newLine == 8)
			{
				System.out.print(" ");
			}
		}

		// Newline if the last line did not have a newline
		if (len % 16 != 0)
			System.out.println();
	}

	/**
	 * @param anchors
	 *            Optional target collection of anchors, for statistics about anchor values.
	 * @return True if anchor was found. If it is not the first, then chunk markers are ready.
	 */
	public synchronized boolean findNextAnchor()
	{
		int lastOffsetForAnchor = Math.min(dataLen - 1, chunkEndOffset + RabinUtils.MAX_CHUNK_LEN);
		// Very common because of the minimum chunk size
		if (anchorSearchOffset > lastOffsetForAnchor)
			return false;

		// Look for the next anchor
		int find = RabinUtils.rabinRollingNextAnchor(internalBuffer, anchorSearchOffset, lastOffsetForAnchor);

		// If not found but chunk is too big
		if (find == -1)
		{
			// If the maximal chunk does not have an anchor
			if (lastOffsetForAnchor == chunkEndOffset + RabinUtils.MAX_CHUNK_LEN)
				find = lastOffsetForAnchor;
			else
			{
				// Just remember not to calculate again the same bytes next time
				anchorSearchOffset = lastOffsetForAnchor + 1;
				return false;
			}
		}

		// Next time start at the point that ensures large enough chunks
		anchorSearchOffset = find + RabinUtils.MIN_CHUNK_LEN;

		chunkStartOffset = chunkEndOffset;
		// End offset is exclusive while the anchor function returns the inclusive last byte of the anchor
		chunkEndOffset = find + 1;

		return true;
	}

	/**
	 * @return Newly allocated buffer with the content of the last chunk that was detected. Null if no such chunk was
	 *         detected yet.
	 */
	public synchronized byte[] getChunkDup()
	{
		// If there is no data or no anchor
		if (!isChunkReady())
			return null;

		int size = chunkEndOffset - chunkStartOffset;
		byte[] result = new byte[size];
		System.arraycopy(internalBuffer, chunkStartOffset, result, 0, size);

		return result;
	}

	public synchronized int getNextChunkStartOffset()
	{
		return chunkEndOffset;
	}

	public synchronized int getCurChunkStartOffset()
	{
		return chunkStartOffset;
	}

	/**
	 * Shift data in the internal buffer so it will start after the last found anchor.
	 * 
	 * @return True if shift was performed. False if there is nothing to shift.
	 */
	public synchronized boolean shiftDataByAnchor()
	{
		// There is no chunk in the current data window
		if (!isChunkReady())
			return false;

		// There is not much data in the window, so spare the shift
		if (dataLen <= DATA_CAPACITY / 4)
			return false;

		//
		// Shift left by copy
		//
		int dataLenWithForwards = this.getLenWithForwards();
		int copyLen = dataLenWithForwards - chunkEndOffset;
		byte[] temp = new byte[copyLen];
		// It can't be negative
		if (copyLen > 0)
		{
			System.arraycopy(internalBuffer, chunkEndOffset, temp, 0, copyLen);
			System.arraycopy(temp, 0, internalBuffer, 0, copyLen);
			//		System.arraycopy(internalBuffer, chunkEndOffset, internalBuffer, 0, copyLen);
		}

		// Update buffer variables
		dataLen -= chunkEndOffset;
		seqBuffer = TcpUtils.tcpSequenceAdd(seqBuffer, chunkEndOffset);

		// Next search just needs to be shifted
		anchorSearchOffset -= chunkEndOffset;

		// Update forwards
		shiftForwards(chunkEndOffset);

		// Reset anchor variables
		chunkStartOffset = 0;
		chunkEndOffset = 0;

		return true;
	}

	@Override
	public String toString()
	{
		String result = String.format("data len=%d,chunk start=%d,chunk enc (exc)=%d,forwards=%d", this.dataLen,
				this.chunkStartOffset, this.chunkEndOffset, this.forwards.size());
		return result;
	}

	/**
	 * @param chunkOffset
	 *            Offset of the chunk in the connection.
	 * @param chunk
	 *            The ACKed chunk.
	 * @return True if this chunk was the expected one, so anchors and buffer were updated accordingly and there is no
	 *         need to do anything with the data itself.
	 */
	public synchronized boolean handleMatchedPackAck(long tcpSeq, ChunkItem ackChunk)
	{
		// The byte after the ACKed chunk
		int ackChunkStartOffset = (int) TcpUtils.tcpSequenceDiff(seqBuffer, tcpSeq);
		int ackChunkEndOffset = ackChunkStartOffset + ackChunk.getLength();
		// Search points to any point after the previous search (at least min-chunk after previous chunk)
		if (ackChunkStartOffset > anchorSearchOffset || ackChunkEndOffset < anchorSearchOffset)
			// We can only handle cases when searching for anchor that the ACKed chunk covers
			return false;

		//		System.out.println(String.format("*** %,d", getConnOffsetFromBufferOffset(ackChunkStartOffset)));

		this.chunkStartOffset = ackChunkStartOffset;
		this.chunkEndOffset = ackChunkEndOffset;

		// Next search should start from the byte after this chunk
		anchorSearchOffset = chunkEndOffset + RabinUtils.MIN_CHUNK_LEN;

		// We don't have real data there, but it is needed for the shift
		dataLen = Math.max(dataLen, ackChunkEndOffset);

		shiftDataByAnchor();

		return true;
	}

	public int getChunkLen()
	{
		if (!isChunkReady())
			return 0;

		return chunkEndOffset - chunkStartOffset;
	}
}
