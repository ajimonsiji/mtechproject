package il.ac.technion.eyalzo.pack.pred;

import il.ac.technion.eyalzo.pack.RabinUtils;
import il.ac.technion.eyalzo.pack.conns.TcpUtils;

/**
 * Incoming prediction item with a signature, hint, length, future content holder and a gap list to be filled when
 * outgoing packets are detected.
 */
public class PredInChunk
{
	/**
	 * TCP sequence of the first byte in the prediction's range.
	 */
	private long tcpSeq;
	private long signature;
	/**
	 * Signature bit-mask that is derived from the number of bytes (LSB) sent for the SHA-1 signature.
	 */
	private long signatureMask;
	@SuppressWarnings("unused")
	private byte hint;
	private int chunkLength;

	//
	// Buffering outgoing data
	//
	/**
	 * True if the first packet was not seen yet or the buffer was initialized already.
	 */
	private boolean outBufPossible = true;
	/**
	 * Outgoing buffer to save dropped packets that overlap with incoming prediction. Null at first and initialized only
	 * if the first packet covers the first chunk's byte.
	 */
	private byte[] outBuf;
	/**
	 * Exclusive end sequence of the outgoing buffer. Meaning that this is the expected TCP sequence of the next data to
	 * be captured on its way out.
	 */
	private long outBufExpectedSeq;

	/**
	 * @param tcpSeq
	 *            TCP sequence of the expected chunk.
	 * @param signature
	 *            Chunk's signature as provided by PRED command.
	 * @param signatureLength
	 *            Number of SHA-1 LSB bytes. Required so it can be compared with the content later.
	 * @param hint
	 *            Not in use.
	 * @param chunkLength
	 *            Chunks's length as provided by PRED command.
	 */
	public PredInChunk(long tcpSeq, int signature, int signatureLength, byte hint, int chunkLength)
	{
		this.tcpSeq = tcpSeq;
		this.signatureMask = -1L >>> (Long.SIZE - signatureLength * 8);
		this.signature = (signatureMask & signature);
		this.hint = hint;
		this.chunkLength = chunkLength;
	}

	/**
	 * 
	 * @param tcpSeqStart
	 *            Packet's TCP sequence.
	 * @param tcpPayloadSize
	 *            Packet's TCP payload size.
	 * @return True if the given packet overlaps with the predicted chunk, under the assumption that packets are never
	 *         bigger than chunks.
	 */
	boolean isPacketOverlap(long tcpSeqStart, int tcpPayloadSize)
	{
		// Sanity check
		if (tcpPayloadSize <= 0)
			return false;

		// Case 1: packet start overlaps the chunk
		if (TcpUtils.tcpSequenceInRange(tcpSeqStart, this.tcpSeq, chunkLength))
			return true;

		// Inclusive last TCP sequence
		long tcpSeqEnd = TcpUtils.tcpSequenceAdd(tcpSeqStart, tcpPayloadSize - 1);

		// Case 2: packet end overlaps
		if (TcpUtils.tcpSequenceInRange(tcpSeqEnd, this.tcpSeq, chunkLength))
			return true;

		// No need for more checks as packets cannot be bigger than chunks in real life

		return false;
	}

	@Override
	public String toString()
	{
		// TCP sequence, signature, chunk length 
		return String.format("(%,d / 0x%08x / %,d / %,d)", this.tcpSeq, this.signature, chunkLength,
				getOutBufFilledBytes());
	}

	/**
	 * @return TCP sequence of the first byte in the prediction's range.
	 */
	public long getTcpSeq()
	{
		return tcpSeq;
	}

	public long getLength()
	{
		return this.chunkLength;
	}

	/**
	 * @param startTcpSeq
	 *            Connection start TCP sequence for more readable print of TCP sequences as relative numbers.
	 * @return Relative TCP sequence, signature and length.
	 */
	public String toString(long startTcpSeq)
	{
		// TCP sequence, signature, chunk length 
		return String.format("(%,d / 0x%08x / %,d)", TcpUtils.tcpSequenceDiff(startTcpSeq, this.tcpSeq),
				this.signature, chunkLength);
	}

	public boolean signatureMatch(int signature)
	{
		return this.signature == (signatureMask & signature);
	}

	public long getSignature()
	{
		return this.signature;
	}

	/**
	 * @return Number of bytes added to the buffer. These are the bytes that should not be transmitted to the receiver.
	 *         Zero if this chunk cannot be buffered because some bytes were already missing.
	 */
	public int addOutData(byte[] rawIpPacket)
	{
		// If the first packet did not cover the first byte of the chunk, there is nothing to do with this prediction
		if (!outBufPossible)
			return 0;

		int payloadLen = TcpUtils.getTcpPaylodLen(rawIpPacket);
		if (payloadLen == 0)
			return 0;

		long packetSeq = TcpUtils.getTcpSeq(rawIpPacket);
		int skipBytes = 0;

		// If this is the first packet
		if (outBuf == null)
		{
			// Check if the first packet covers the beginning of the chunk
			long diff = TcpUtils.tcpSequenceDiff(packetSeq, this.tcpSeq);
			if (diff < 0 || diff >= payloadLen)
			{
				outBufPossible = false;
				return 0;
			}

			// Initialize the buffer
			this.outBuf = new byte[this.chunkLength];
			// How many bytes to skip from this packet
			skipBytes = (int) diff;
			// Initialize the expected sequence
			this.outBufExpectedSeq = this.tcpSeq;
		} else
		{
			// Verify that this is the expected data
			if (packetSeq != this.outBufExpectedSeq)
				return 0;
		}

		int lenToUse = Math.min(payloadLen - skipBytes, getSpaceLeft());

		// Copy from the raw packet to the out buffer
		int bufferOffset = getOutBufFilledBytes();
		int headersLen = TcpUtils.getCombinedHeadersLen(rawIpPacket);
		System.arraycopy(rawIpPacket, headersLen + skipBytes, outBuf, bufferOffset, lenToUse);

		this.outBufExpectedSeq = TcpUtils.tcpSequenceAdd(outBufExpectedSeq, lenToUse);

		return lenToUse;
	}

	/**
	 * 
	 * @return True if the out buffer was initialized and is full.
	 */
	public boolean isOutBufReadyForSignature()
	{
		return outBuf != null && getSpaceLeft() == 0;
	}

	private int getSpaceLeft()
	{
		if (outBuf == null)
			return 0;
		return this.chunkLength - getOutBufFilledBytes();
	}

	/**
	 * @return Number of bytes already filled in the out buffer.
	 */
	public int getOutBufFilledBytes()
	{
		if (outBuf == null)
			return 0;
		return (int) TcpUtils.tcpSequenceDiff(this.tcpSeq, this.outBufExpectedSeq);
	}

	public synchronized int calculateSha1()
	{
		if (!isOutBufReadyForSignature())
			return 0;

		return RabinUtils.calculateSha1(outBuf, 0, outBuf.length);
	}
	
	public byte[] getOutBuffer()
	{
		return this.outBuf;
	}
}
