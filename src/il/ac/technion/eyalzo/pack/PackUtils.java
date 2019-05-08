/**
 * 
 */
package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.pack.conns.TcpUtils;
import il.ac.technion.eyalzo.pack.pred.PredInList;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * All the PACK commands are wrapped in one TCP option that starts with {@link #OPTION_PACK_MESSAGE} and length (like
 * other generic TCP options). Each command start with one byte that contains 4-bit command code and 4-bit length (bytes
 * after that first byte).
 * <p>
 * Each TCP option (except for 2 special commands) starts with 2 bytes for kind and length (bytes, includes these first
 * 2 bytes):
 * 
 * <pre>
 * -------------------------------------
 * | kind   | len    |        |        |
 * -------------------------------------
 * </pre>
 * 
 * PACK Permitted TCP option (supporting versions 1 and 2):
 * 
 * <pre>
 * -------------------------------------
 * | 29     | 4      | 1      | 2      |
 * -------------------------------------
 * </pre>
 * 
 * PACK commands are wrapped as one TCP option kind {@link #OPTION_PACK_MESSAGE} . Each PACK command has 1-byte prefix
 * that is made of 2 4-bit units: command code and length (bytes, without this first byte)
 * 
 * <pre>
 * -------------------------------------
 * | cmd+len|        |        |        |
 * -------------------------------------
 * </pre>
 * 
 * 
 * OFFSET command (negative offset of 100 bytes):
 * 
 * <pre>
 * -------------------------------------
 * | 30     | 4      | 0x31   | 100    |
 * -------------------------------------
 * </pre>
 * 
 * PRED command:
 * 
 * <pre>
 * ------------------------------------+--------------------------+--------+--------+---------
 * | 30     | 10     | 0x17   | chunk len       | hint   | 4-bytes signature (LSB)           |
 * ------------------------------------+--------------------------+--------+--------+---------
 * 
 * ------------------------------------+--------------------------+--------+--------+--------+--------+--------+--------+---------
 * | 30     | 14     | 0x1b   | chunk len       | hint   | 8-bytes signature (SHA-1 LSB)                                         |
 * ------------------------------------+--------------------------+--------+--------+--------+--------+--------+--------+---------
 * </pre>
 * 
 * ACK command, from TCP sequence:
 * 
 * <pre>
 * ------------------------------------+--------+---------
 * | 30     | 10     | 0x93   | ACK len                  |
 * ------------------------------------+--------+---------
 * </pre>
 */
public class PackUtils
{
	private static final int OPTION_PACK_PERMITTED = 29;
	public static final int OPTION_PACK_MESSAGE = 30;

	//
	// PACK commands
	//
	static final int PACK_CMD_OFFSET_POS = 0;
	static final int PACK_CMD_OFFSET_NEG = 3;
	static final int PACK_CMD_PRED = 1;
	/**
	 * Sender acknowledge to former PRED(s).
	 */
	static final int PACK_CMD_ACK = 9;

	public static void setPackPermitted(byte[] rawIpPacket)
	{
		ByteBuffer buffer = ByteBuffer.allocate(1);
		// Supported PACK version 1
		buffer.put((byte) 1);
		buffer.flip();

		TcpUtils.setTcpOption(rawIpPacket, OPTION_PACK_PERMITTED, buffer);
	}

	/**
	 * Write predictions into the given IP packet and remove used predictions.
	 * 
	 * @param rawIpPacket
	 *            TCP outgoing ACK with IP and TCP headers, to carry the predictions in the extended TCP options field.
	 *            TCP sequence of the first prediction
	 * @param predOutbox
	 *            Prediction chunk list and TCP sequence of the first chunk.
	 * @param predSent
	 *            When the buffer is valid it contains all the chunks that are included in the prediction.
	 * @return Null or buffer ready to be written in the TCP options field but without the option kind and length. Never
	 *         return an empty buffer.
	 */
	public static ByteBuffer generatePackPredictionsPacketFromOutbox(byte[] rawIpPacket, PredOutChunks predOutbox,
			PredOutChunks predSent)
	{
		// Sanity
		if (predOutbox == null || predOutbox.isEmpty())
			return null;

		// Need the ACK for the offset
		long ack = TcpUtils.getTcpAck(rawIpPacket);
		if (ack == 0)
			return null;

		//
		// Calculate space left for the new commands
		//
		int ipTotalLen = TcpUtils.getIpTotalLen(rawIpPacket);
		int ipHeaderLen = TcpUtils.getIpHeaderBytesLen(rawIpPacket);
		int tcpHedaerLen = TcpUtils.getTcpHeaderBytesLen(rawIpPacket);
		// Make sure the packet has only IP and TCP headers
		if (ipTotalLen != (ipHeaderLen + tcpHedaerLen))
			return null;

		int spaceLeft = TcpUtils.MAX_TCP_HEADER_LEN_BYTES - tcpHedaerLen;
		// At least 2 for option kind and length, 2 for offset, 4 for prediction 
		if (spaceLeft < 8)
			return null;

		// Get a new buffer, with PACK code, space for length and position ready for writes and capacity by space left
		ByteBuffer packOptionBuffer = initPackOption(spaceLeft);

		//		System.err.println(String.format("More %,d bytes for PACK commands",
		//				spaceLeft));

		//
		// Walk through the predictions
		//
		boolean addedOffset = false;
		boolean addedChunk = false;

		synchronized (predOutbox)
		{
			Iterator<Entry<Long, ChunkItem>> it = predOutbox.getChunks().entrySet().iterator();
			while (it.hasNext())
			{
				Entry<Long, ChunkItem> entry = it.next();
				// Get from chunk although it must be subsequent
				long curPredSeq = entry.getKey();
				ChunkItem curPredChunk = entry.getValue();

				if (!addedOffset)
				{
					// In most cases the diff is negative because the packet with the anchor was not ACKed yet
					long diff = TcpUtils.tcpSequenceDiff(ack, curPredSeq);

					// Try to write the diff command
					if (!appendOffsetCommand(packOptionBuffer, diff))
						break;

					addedOffset = true;
				}

				// Write the prediction command
				if (!appendPredCommand(packOptionBuffer, curPredChunk.getLength(), curPredChunk.getStamp(), 4))
					break;

				// Now the command was written and position was updated

				addedChunk = true;

				//			System.err.println(curPredSeq + " / " + predOutbox.size() + " / "
				//					+ predSent.size() + " / " + packOptionBuffer.remaining()); 

				it.remove();

				// If the TCP sequence is backward, then quit after the item was removed from outbox
				if (!predSent.addPredChunk(curPredSeq, curPredChunk))
					return null;
			}
		}

		// Do not return empty buffer
		if (packOptionBuffer.position() == 0 || !addedOffset || !addedChunk)
			return null;

		// Set limit to the end of the written content
		packOptionBuffer.limit(packOptionBuffer.position());
		packOptionBuffer.position(0);

		return packOptionBuffer;
	}

	/**
	 * Generate a TCP option with PACK ACK command.
	 * 
	 * @return Null or buffer ready to be written in the TCP options field but without the option kind and length. Never
	 *         return an empty buffer.
	 */
	public static ByteBuffer generatePackAckPacket(long ackLength)
	{
		// Sanity
		if (ackLength <= 0)
			return null;

		int spaceLeft = TcpUtils.MAX_TCP_HEADER_LEN_BYTES - TcpUtils.MIN_TCP_HEADER_LEN_BYTES;

		// Get a new buffer, with PACK code, space for length and position ready for writes and capacity by space left
		ByteBuffer packOptionBuffer = initPackOption(spaceLeft);

		// Try to write the command
		if (!appendAckCommand(packOptionBuffer, ackLength))
			return null;

		// Set limit to the end of the written content
		packOptionBuffer.limit(packOptionBuffer.position());
		packOptionBuffer.position(0);

		return packOptionBuffer;
	}

	/**
	 * @param spaceLeft
	 *            Space left for PACK message, including TCP option kind and length and padding for the 4-byte
	 *            alignment.
	 * @return Prepared buffer with enough space for the maximal space left. Capacity is the number of bytes left for
	 *         TCP options.
	 */
	private static ByteBuffer initPackOption(int spaceLeft)
	{
		// Padding for 4-byte alignment
		spaceLeft -= (spaceLeft % 4);
		// Leave room for TCP option kind and length
		spaceLeft -= 2;

		// Maximal space for TCP options is 40, and we also need 2 for kind and length
		ByteBuffer result = ByteBuffer.allocate(spaceLeft).order(ByteOrder.BIG_ENDIAN);

		return result;
	}

	/**
	 * Write an OFFSET command into the buffer.
	 * 
	 * @param buffer
	 *            Target buffer.
	 * @param offset
	 *            Can be zero.
	 * @return True if written successfully or false for illegal argument or lack of space.
	 */
	static boolean appendOffsetCommand(ByteBuffer buffer, long offset)
	{
		// Command by sign
		int packCommand = offset > 0 ? PACK_CMD_OFFSET_POS : PACK_CMD_OFFSET_NEG;
		int packLen;
		offset = Math.abs(offset);

		//
		// Calculate length
		//
		if (offset <= 0x00ff)
			packLen = 1;
		else if (offset <= 0xffff)
			packLen = 2;
		else if (offset <= 0xffffff)
			packLen = 3;
		else
			return false;

		// Check that we have enough space left
		if (buffer.remaining() < (packLen + 1))
			return false;

		// Write command
		buffer.put((byte) ((packCommand << 4) | packLen));
		for (int i = packLen - 1; i >= 0; i--)
		{
			buffer.put((byte) ((offset >> (8 * i)) & 0xff));
		}

		return true;
	}

	/**
	 * Write an ACK command into the buffer.
	 * 
	 * @param buffer
	 *            Target buffer.
	 * @param ackLength
	 *            Length of acknowledged sequence. Can be zero, although it does not make any sense.
	 * @return True if written successfully or false for illegal argument or lack of space.
	 */
	static boolean appendAckCommand(ByteBuffer buffer, long ackLength)
	{
		// Sanity
		if (ackLength < 0)
			return false;

		// Command by sign
		int packCommand = PACK_CMD_ACK;
		int packLen;
		ackLength = Math.abs(ackLength);

		//
		// Calculate length
		//
		if (ackLength <= 0x00ffL)
			packLen = 1;
		else if (ackLength <= 0xffffL)
			packLen = 2;
		else if (ackLength <= 0xffffffL)
			packLen = 3;
		else if (ackLength <= 0xffffffffL)
			packLen = 4;
		else if (ackLength <= 0xffffffffffL)
			packLen = 5;
		else if (ackLength <= 0xffffffffffffL)
			packLen = 6;
		else if (ackLength <= 0xffffffffffffffL)
			packLen = 7;
		else
			return false;

		// Check that we have enough space left
		if (buffer.remaining() < (packLen + 1))
			return false;

		// Write command
		buffer.put((byte) ((packCommand << 4) | packLen));
		for (int i = packLen - 1; i >= 0; i--)
		{
			buffer.put((byte) ((ackLength >> (8 * i)) & 0xff));
		}

		return true;
	}

	/**
	 * 
	 * @param buffer
	 *            Target buffer to be written from the current position.
	 * @return False for illegal arguments or lack of space.
	 */
	static boolean appendPredCommand(ByteBuffer buffer, int chunkLen, long signature, int signatureLen)
	{
		// Sanity check
		if (signatureLen < 1 || chunkLen > 0xffff || chunkLen <= 0)
			return false;

		// Can't be more than 8 bytes because long is 64-bit
		if (signatureLen > (Long.SIZE / 8))
			return false;

		// 2 for chunk length, 1 for hint and the rest is for signature
		int packLen = 2 + 1 + signatureLen;
		if (packLen + 1 > buffer.remaining())
			return false;

		// Write command
		buffer.put((byte) ((PACK_CMD_PRED << 4) | packLen));

		// Chunk length
		buffer.putShort((short) chunkLen);

		//TODO hint...
		buffer.put((byte) 0);

		for (int i = signatureLen - 1; i >= 0; i--)
		{
			buffer.put((byte) ((signature >> (8 * i)) & 0xff));
		}

		return true;
	}

	/**
	 * Parse incoming PRED commands and add them to the predictions inbox only if they have relevant TCP sequence
	 * numbers.
	 * 
	 * @param tcpAckSeq
	 *            TCP ACK sequence of the packet that carries this TCP option.
	 * @param packOptionBytes
	 *            TCP option bytes with PACK message, excluding the TCP option kind and length. Position usually
	 *            changes, but the mark holds the position before the call.
	 * @param predInbox
	 *            Prediction inbox.
	 * @return Total bytes in PRED commands found and used (future predictions). Commands with old TCP sequence are not
	 *         used.
	 */
	public static int parsePredIn(long tcpAckSeq, ByteBuffer packOptionBytes, PredInList predInbox)
	{
		// Save position for return
		packOptionBytes.mark();

		Long offset = parsePackOffsetCommand(packOptionBytes);
		if (offset == null)
			return 0;

		// Hold a TCP sequence of the next PRED
		long curTcpSeq = TcpUtils.tcpSequenceAdd(tcpAckSeq, offset);

		// Now we have offset in hand, and buffer position points to the next command
		int result = 0;

		while (true)
		{
			// PRED command needs at least 5 bytes
			if (packOptionBytes.remaining() < 4)
				return result;

			// Command and length
			byte packCommand = packOptionBytes.get();
			byte packLength = (byte) (packCommand & 0x0f);
			packCommand = (byte) (packCommand >> 4);

			// Sanity check
			if (packLength < 0 || packLength > packOptionBytes.remaining())
				return result;

			// If not PRED command then just skip the command's bytes
			if (packCommand != PACK_CMD_PRED)
			{
				// Skip the length bytes
				while (packLength-- > 0)
					packOptionBytes.get();

				continue;
			}

			// 2-bytes chunk length
			int chunkLength = packOptionBytes.getShort() & 0x0000ffff;

			// 1-byte hint (not in use)
			byte hint = packOptionBytes.get();

			// The rest of the bytes are for the signature
			int signatureLength = packLength - 3;
			long signature = readBufferLong(packOptionBytes, signatureLength);

			if (predInbox.addPredForward(curTcpSeq, (int) signature, signatureLength, hint, chunkLength))
			{
				result += chunkLength;
			}

			// Next TCP sequence
			curTcpSeq = TcpUtils.tcpSequenceAdd(curTcpSeq, chunkLength);
		}
	}

	/**
	 * Get offset PACK command.
	 * 
	 * @param packMessage
	 *            Buffer with PACK message, excluding the TCP option kind and length. Search starts at position. If
	 *            found, the position will point to the next command after the offset.
	 * @return Null if offset was not found or bad format. Negative or positive if offset was found.
	 */
	private static Long parsePackOffsetCommand(ByteBuffer packMessage)
	{
		while (true)
		{
			// If not found
			if (packMessage.remaining() < 1)
				return null;

			// Command and length
			byte packCommand = packMessage.get();
			byte packLength = (byte) (packCommand & 0x0f);
			packCommand = (byte) (packCommand >> 4);

			// Sanity check
			if (packLength < 0 || packLength > packMessage.remaining())
				return null;

			// If length error, or not offset command then just skip the command's bytes
			if (packLength == 0 || packLength > 4 || packCommand != PACK_CMD_OFFSET_NEG
					&& packCommand != PACK_CMD_OFFSET_POS)
			{
				// Skip the length bytes
				while (packLength-- > 0)
					packMessage.get();

				continue;
			}

			// Here we have a legal positive or negative offset
			long result = readBufferLong(packMessage, packLength);

			// Return positive or negative, according to command
			return packCommand == PACK_CMD_OFFSET_POS ? result : -result;
		}
	}

	/**
	 * Get ACK command value, which is the length of the acknowledged sequence.
	 * 
	 * @param packMessage
	 *            Buffer with PACK message, excluding the TCP option kind and length. Search starts at position. If
	 *            found, the position will point to the next command after the offset.
	 * @return Null if command was not found or bad format. Positive value if length was found.
	 */
	public static Long parsePackAckCommand(ByteBuffer packMessage)
	{
		while (true)
		{
			// If not found
			if (packMessage.remaining() < 2)
				return null;

			// Command and length
			byte packCommand = packMessage.get();
			byte packLength = (byte) (packCommand & 0x0f);
			packCommand = (byte) ((0x00ff & packCommand) >> 4);

			// Sanity check
			if (packLength < 1 || packLength > packMessage.remaining())
				return null;

			// If length error, or not ACK command then just skip the command's bytes
			if (packCommand != PACK_CMD_ACK)
			{
				// Skip the length bytes
				while (packLength-- > 0)
					packMessage.get();

				continue;
			}

			// Here we have a legal positive or negative offset
			long result = readBufferLong(packMessage, packLength);

			// Return positive or negative, according to command
			return result;
		}
	}

	/**
	 * Check if an offset command is found at the pack message buffer.
	 * 
	 * @param packMessage
	 *            Buffer with PACK message, excluding the TCP option kind and length. Search starts at position that is
	 *            restored at the end.
	 * @return True if the command was found.
	 */
	public static boolean hasPackOffsetCommand(ByteBuffer packMessage)
	{
		return hasPackCommand(packMessage, PACK_CMD_OFFSET_NEG) || hasPackCommand(packMessage, PACK_CMD_OFFSET_POS);
	}

	/**
	 * Check if a prediction command is found at the pack message buffer.
	 * 
	 * @param packMessage
	 *            Buffer with PACK message, excluding the TCP option kind and length. Search starts at position that is
	 *            restored at the end.
	 * @return True if the command was found.
	 */
	public static boolean hasPackPredCommand(ByteBuffer packMessage)
	{
		return hasPackCommand(packMessage, PACK_CMD_PRED);
	}

	/**
	 * Check if an acknowledge command is found at the pack message buffer.
	 * 
	 * @param packMessage
	 *            Buffer with PACK message, excluding the TCP option kind and length. Search starts at position that is
	 *            restored at the end.
	 * @return True if the command was found.
	 */
	public static boolean hasPackAckCommand(ByteBuffer packMessage)
	{
		return hasPackCommand(packMessage, PACK_CMD_ACK);
	}

	/**
	 * Check if a specific command is found at the pack message buffer.
	 * 
	 * @param packMessage
	 *            Buffer with PACK message, excluding the TCP option kind and length. Search starts at position that is
	 *            restored at the end.
	 * @return True if the command was found. Can be null.
	 */
	public static boolean hasPackCommand(ByteBuffer packMessage, int packCommandSearch)
	{
		if (packMessage == null)
			return false;

		int startPos = packMessage.position();

		while (true)
		{
			// If not found
			if (packMessage.remaining() < 1)
			{
				packMessage.position(startPos);
				return false;
			}

			// Command and length
			byte packCommandFound = packMessage.get();
			byte packLength = (byte) (packCommandFound & 0x0f);
			packCommandFound = (byte) ((0x00ff & packCommandFound) >> 4);

			// Sanity check
			if (packLength < 0 || packLength > packMessage.remaining())
			{
				packMessage.position(startPos);
				return false;
			}

			if (packCommandFound == packCommandSearch)
			{
				packMessage.position(startPos);
				return true;
			}

			// Skip the irrelevant command
			while (--packLength >= 0)
				packMessage.get();
		}
	}

	/**
	 * Read and return a long number from buffer position.
	 * 
	 * @param buffer
	 *            Buffer to read from position.
	 * @param length
	 *            Number of bytes to read (up to 8).
	 * @return Null on error in parameters or if there are not enough remaining bytes.
	 */
	static Long readBufferLong(ByteBuffer buffer, int length)
	{
		// Sanity check
		if (buffer == null || length > buffer.remaining() || length < 0 || length > (Long.SIZE / 8))
			return null;

		long result = 0;

		// Build the final result
		while (length-- > 0)
		{
			int i = (buffer.get() & 0x00ff);
			result = ((result << 8) | (i & 0x00ff));
		}

		return result;
	}

	public static ByteBuffer getPackMessage(TCPPacket tcp, byte[] rawIpPacket)
	{
		return TcpUtils.getTcpOptionAsByteBuffer(tcp, rawIpPacket, PackUtils.OPTION_PACK_MESSAGE);
	}

	/**
	 * @return True if found a TCP option related to PACK.
	 */
	public static boolean hasPack(byte[] rawIpPacket)
	{
		return TcpUtils.hasTcpOption(rawIpPacket, OPTION_PACK_MESSAGE)
				|| TcpUtils.hasTcpOption(rawIpPacket, OPTION_PACK_PERMITTED);
	}
}
