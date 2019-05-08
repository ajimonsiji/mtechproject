package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.NFQueue.Verdict;
import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.PackUtils;
import il.ac.technion.eyalzo.pack.PredOutChunks;
import il.ac.technion.eyalzo.pack.spoof.SpoofThread;
import il.ac.technion.eyalzo.pack.stamps.ChainItem;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.nio.ByteBuffer;

public class TcpConnRcv extends TcpConn
{
	/**
	 * Maximal number of chunks to add to outbox in one operation.
	 */
	protected static final int MAX_CHUNKS_PER_PRED = 100;
	/**
	 * Chain in to follow incoming chunks and use it for predictions.
	 */
	protected ChainItem chain;
	/**
	 * Search for the first anchor and not use the first "chunk" signature because it is usually useless.
	 */
	protected boolean searchFirstAnchor = true;
	/**
	 * Connection buffer for traffic from sender to receiver.
	 */
	protected ConnBuffer buffer;

	//
	// Prediction related
	//
	/**
	 * Receiver: Predictions sent and wait for approval or rejection. Key is the TCP sequence. Order is kept for cleanup
	 * reasons.
	 */
	protected PredOutChunks predSent = new PredOutChunks();
	/**
	 * Predictions to send with outgoing ACKs, always subsequent, waiting for ACK to leave so it can piggyback it. Empty
	 * if there are no predictions. Also contains the TCP sequence of the first prediction in the list.
	 */
	private PredOutChunks predOutbox = new PredOutChunks();

	//
	// Statistics
	//
	/**
	 * Incoming bytes that match existing chunk.
	 */
	long statBytesKnown;

	public TcpConnRcv(boolean synDirOut, long seq, int windowScaling)
	{
		super(synDirOut, seq, windowScaling);
	}

	/**
	 * Handle incoming or outgoing packet.
	 * <p>
	 * Add data to the internal buffer and look for the next anchor. Also check for connection closing.
	 * 
	 * @param dirOut
	 *            Direction of packet: out (true) or in (false).
	 * @param tcp
	 *            TCP payload data.
	 * @param rawIpPacket
	 *            Raw IP packet with IP header and TCP header.
	 * @return What to do with the packet. Normally accept, but it can also be drop for ACKs to ride, for example.
	 */
	public Verdict handlePacket(boolean dirOut, TCPPacket tcp, byte[] rawIpPacket)
	{
		//
		// PACK message ?
		//
		ByteBuffer packMessage = PackUtils.getPackMessage(tcp, rawIpPacket);

		// Don't touch outgoing packets with PACK messages as they must leave the machine intact and not even be counted
		if (dirOut && packMessage != null)
			return Verdict.NF_ACCEPT;

		this.lastPacketTime = System.currentTimeMillis();

		int tcpPayloadSize = tcp.getIPPacketLength() - tcp.getCombinedHeaderByteLength();

		//
		// Variables by direction
		//

		// TCP sequence of the local machine segment start and/or last anchor found. Initialized according to TCP seq (up) or TCP ACK (down) and then updated when anchors are found
		long localSeq;
		// Similar but for the remote machine
		long remoteSeq;

		// Save window size
		if (dirOut)
		{
			this.localLastWindow = tcp.getWindowSize();
			localSeq = tcp.getSequenceNumber();
			remoteSeq = tcp.getAckNumber();

			this.localSeqEnd = TcpUtils.tcpSequenceAdd(localSeq, tcpPayloadSize);
			this.remoteSeqEnd = remoteSeq;
		} else
		{
			this.remoteLastWindow = tcp.getWindowSize();
			remoteSeq = tcp.getSequenceNumber();
			localSeq = tcp.getAckNumber();

			this.localSeqEnd = localSeq;
			this.remoteSeqEnd = TcpUtils.tcpSequenceAdd(remoteSeq, tcpPayloadSize);
		}

		// SYN and SYN+ACK are already handled by the caller
		if (tcp.isSet(TCPPacket.MASK_SYN))
			return Verdict.NF_ACCEPT;

		// TCP sequence of data
		long relativeSeq = TcpUtils.tcpSequenceDiff(this.remoteSeqStart, remoteSeq);

		//
		// Close?
		//
		if (tcp.isSetAny(TCPPacket.MASK_FIN | TCPPacket.MASK_RST))
		{
			// Assume that there is no data after FIN

			// Remember the event
			addEventClose(dirOut, localSeq, remoteSeq, tcp.isSet(TCPPacket.MASK_FIN));

			// Show statistics by role: sender out bytes or receiver in bytes
			if (Main.debugLevel >= 4)
				System.out.println(String.format("   %,d: RCV %,d end %s %s %,d known %,d cache, %,d/%,d lost/got", this.serial,
						relativeSeq, tcp.flagsToString(), dirOut ? "out" : "in", statBytesKnown, statBytesPredAck, lostPacket, gotPacket));

			return Verdict.NF_ACCEPT;
		}

		boolean noTcpPayload = tcpPayloadSize == 0;

		//
		//  Incoming PACK ACK ?
		//
		if (!dirOut && PackUtils.hasPackAckCommand(packMessage))
		{
			// Try to parse incoming ACK
			Long ackLength = PackUtils.parsePackAckCommand(packMessage);
			// It has to be true
			if (ackLength != null)
			{
				// Check if actually sent such a prediction
				ChunkItem chunk = predSent.popChunk(remoteSeq);

				// Send the data internally to itself
				if (chunk != null)
				{
					// Statistics
					statBytesPredAck += ackLength;
					// Try to use the data internally, before it is being sent
					handleMatchedPackAck(remoteSeq, chunk);
					// Send the data to the TCP stack so the application gets it
					SpoofThread.sendAcknowledgedChunkIn(rawIpPacket, remoteSeq, chunk);
				}

				if (Main.debugLevel >= 4)
					System.out.println(String.format("      %,d: RCV %,d pack ACK for %,d bytes, %s", this.serial,
							relativeSeq, ackLength, chunk != null ? "match" : "no match !"));
			}
			// Quit here since it cannot be used for anything else
			return Verdict.NF_ACCEPT;
		}

		//
		// Send prediction opportunity ?
		// When the receiver is about to send an empty ACK and there are outgoing predictions waiting in the outbox
		//
		if (dirOut && noTcpPayload && tcp.isSet(TCPPacket.MASK_ACK)
				&& !tcp.isSetAny(TCPPacket.MASK_FIN | TCPPacket.MASK_RST) && chain != null && !predOutbox.isEmpty())
		{
			// Note: the outgoing ACK does not carry PACK messages as such packets are filtered earlier

			if (receiverSendPackPred(tcp, rawIpPacket))
				return Verdict.NF_DROP;
		}

		if (noTcpPayload)
			return Verdict.NF_ACCEPT;

		// Outgoing data is of no interest here, unless there are predictions waiting (see above)
		if (dirOut)
			return Verdict.NF_ACCEPT;

		// Incoming data
		handleReceiverIncomingData(tcp, rawIpPacket);

		return Verdict.NF_ACCEPT;
	}

	/**
	 * When received PACK ACK it can handle it now directly by using the chunk's signature and anchor.
	 * 
	 * @param tcpSeq
	 *            TCP sequence of the first byte of the chunk.
	 * @param chunk
	 *            The approved chunk itself.
	 */
	private void handleMatchedPackAck(long tcpSeq, ChunkItem chunk)
	{
		synchronized (buffer)
		{
			// Normally there is no need to actually copy it
			if (!buffer.handleMatchedPackAck(tcpSeq, chunk))
			{
				buffer.addData(chunk.getContent(), 0, chunk.getLength(), tcpSeq);

				return;
			}

			// Count that stream
			chunk.incStatStreamCount();

			// The chain being built now from the stream

			// Remember the last real signature
			if (chain == null)
			{
				chain = Main.chains.addChain();
			}

			// Add the new chunk to the chain at the end
			chain.addChunk(chunk);

			// Try to add predictions
			// We now have remoteSeq pointing to the prediction seq, because this is where the last anchor was found
			long chunkEndSeq = buffer.getSeqFromBufferOffset(buffer.getNextChunkStartOffset());
			addPredictionsToOutbox(chunkEndSeq, chunk, MAX_CHUNKS_PER_PRED);
		}
	}

	private void handleReceiverIncomingData(TCPPacket tcp, byte[] rawIpPacket)
	{
		//
		// Incoming data
		//
		if (buffer == null)
			buffer = new ConnBuffer(serial, remoteSeqStart);

		synchronized (buffer)
		{
			buffer.addData(rawIpPacket, tcp.getCombinedHeaderByteLength(), tcp.getTCPDataByteLength(), tcp
					.getSequenceNumber());

			// Loop through all the anchors (usually there will be no more than one)
			while (true)
			{
				// Look for anchor
				if (!buffer.findNextAnchor())
					// Usually not found and break here
					return;

				int chunkLen = buffer.getChunkLen();

				// First anchor?
				if (chunkLen == 0)
				{
					long nextChunkStartRelSeq = buffer.getConnOffsetFromBufferOffset(buffer.getNextChunkStartOffset());

					if (Main.debugLevel >= 3)
						System.out.println(String.format("      %,d: RCV %,d chain start", this.serial,
								nextChunkStartRelSeq));

					// Sequences are already updated with anchor position according to direction
					addEventFirstAnchor(false, tcp.getAckNumber(), nextChunkStartRelSeq);

					return;
				}

				// The first anchor was already found before, so now it's a chunk for sure

				// Calculate signature on the buffer up to the anchor (exclusive)
				int sha1 = buffer.calculateSha1();

				// Add signature to main list and update statistics counter
				ChunkItem curChunk = Main.chunks.getChunkOrAddNew(sha1, chunkLen);
				// Count that stream
				curChunk.incStatStreamCount();

				// If the chunk is not part of a file already, then save the content in the chunk's buffer
				if (curChunk.getFilesCount() == 0)
					curChunk.setContent(buffer.getChunkDup());

				// Expected chunk by its own memory
				ChunkItem expectedChunk = null;

				// The chain being built now from the stream

				// Remember the last real signature
				if (chain == null)
				{
					chain = Main.chains.addChain();
				}

				// Remember who was last before we add the new one
				ChunkItem lastChunk = chain.getChainLastChunk();
				if (lastChunk != null)
				{
					// Check what was the prediction based on the previous chunk
					// and its memory
					expectedChunk = lastChunk.getNextChunk();
				}

				// Add the new chunk to the chain at the end
				chain.addChunk(curChunk);

				boolean chunkInFile = curChunk.getFilesCount() > 0;
				boolean matchedExpected = expectedChunk != null && expectedChunk == curChunk;

				// Add event
				int receiverWindowSize = (this.localLastWindow << this.localWindowScaling);
				long chunkEndSeq = buffer.getSeqFromBufferOffset(buffer.getNextChunkStartOffset());
				long chunkStartRelSeq = buffer.getConnOffsetFromBufferOffset(buffer.getCurChunkStartOffset());

				// Sequences are already updated with anchor position according to direction
				addEventChunk(tcp.getAckNumber(), chunkStartRelSeq, sha1, chunkLen, receiverWindowSize, chunkInFile,
						matchedExpected);

				boolean isNewChunk = curChunk.getFilesCount() == 0 && curChunk.getStatStreamCount() == 1;
				if (Main.debugLevel >= 4)
					System.out.println(String.format("      %,d: RCV chunk (%,10d-%,10d) sign %08x %,6d bytes - %s",
							this.serial, chunkStartRelSeq, chunkStartRelSeq + chunkLen - 1, sha1, chunkLen,
							matchedExpected ? "predicted" : chunkInFile ? "file" : isNewChunk ? "new" : "known"));

				// Try to add predictions
				// We now have remoteSeq pointing to the prediction seq, because this is where the last anchor was found
				addPredictionsToOutbox(chunkEndSeq, curChunk, MAX_CHUNKS_PER_PRED);

				// If match chunk from former chain then add the bytes to
				// statistics
				if (matchedExpected)
				{
					// Add to cached bytes count
					statBytesPredMatch += chunkLen;
				}

				if (!isNewChunk)
				{
					statBytesKnown += chunkLen;
				}

				// Shift data if needed
				buffer.shiftDataByAnchor();
			}
		}
	}

	/**
	 * Cleanup the prediction list and try to add several predictions from this point forward.
	 * <p>
	 * The prediction may be later used by outgoing ACKs.
	 * 
	 * @param tcpSeq
	 *            TCP sequence of the first prediction, meaning the point after the given chunk.
	 * @param curChunk
	 *            Current chunk, not the one to predict.
	 * @param maxPredicitions
	 *            Maximal number of predictions to add.
	 */
	private void addPredictionsToOutbox(long tcpSeq, ChunkItem curChunk, int maxPredicitions)
	{
		synchronized (predOutbox)
		{
			// Check if there is at least one prediction
			ChunkItem predChunk = curChunk.getNextChunk();
			if (predChunk == null)
				return;

			// Create empty prediction list with TCP sequence
			predOutbox.init(tcpSeq);
			// Count
			int count = 0;
			// True if already added something, just for safety because it does not suppose to happen
			boolean added = false;

			while (predChunk != null && count < maxPredicitions)
			{
				// Do not add predictions that were already sent
				if (predSent.contains(tcpSeq))
				{
					if (added)
						return;
				} else
				{
					added = true;
					// Subsequent so just add
					predOutbox.addPredChunk(tcpSeq, predChunk);
					//				System.err.println("outbox " + predOutbox.size() + ": " + (tcpSeq - this.remoteSeqStart));
				}

				// Count success cases
				count++;
				// Update the TCP sequence so we can later make sure that we don't send predictions that were already sent before
				tcpSeq = TcpUtils.tcpSequenceAdd(tcpSeq, predChunk.getLength());
				// Next prediction (if)
				predChunk = predChunk.getNextChunk();
			}
		}
	}

	/**
	 * Use receiver's outgoing empty ACK to piggyback predictions. The predictions are moved from predictions-outbox to
	 * predictions-sent-items.
	 * 
	 * @param tcp
	 *            Same as the raw packet so it is here just to save processing.
	 * @param rawIpPacket
	 *            Outgoing empty TCP packet with ACK.
	 * @return True if the original ACK should be dropped because the altered ACK was added to the spoof list. On that
	 *         case the sent-items contain all the chunks that are included in the prediction.
	 */
	private boolean receiverSendPackPred(TCPPacket tcp, byte[] rawIpPacket)
	{
		synchronized (predOutbox)
		{
			// Build buffer and cleanup prediction list
			int predSentSizeBefore = predSent.size();
			//			System.out.println(predOutbox.toString(this.remoteSeqStart, 0, predOutbox.size()));
			ByteBuffer predBuffer = PackUtils
					.generatePackPredictionsPacketFromOutbox(rawIpPacket, predOutbox, predSent);

			// It may fail due to lack of space or other reason
			if (predBuffer == null)
				return false;

			// Now the sent-items contains the new predictions as last chunks
			int predSentNewCount = predSent.size() - predSentSizeBefore;

			// For print only
			long relativeAck = TcpUtils.tcpSequenceDiff(this.remoteSeqStart, tcp.getAckNumber());

			// Total length of chunks in the prediction
			long predLength = predSent.getChunksLength(-predSentNewCount);
			long predSeqStartRel = TcpUtils.tcpSequenceDiff(this.remoteSeqStart, TcpUtils.tcpSequenceAdd(predSent
					.getTcpSeqNext(), -predLength));
			long predSeqEndRel = TcpUtils.tcpSequenceDiff(this.remoteSeqStart, TcpUtils.tcpSequenceAdd(predSent
					.getTcpSeqNext(), -1));

			if (Main.debugLevel >= 4)
			{
				System.out.println(String.format("      %,d: RCV %,d sent %,d preds: %s", this.serial, relativeAck,
						predSentNewCount, predSent.toString(this.remoteSeqStart, -predSentNewCount, predSentNewCount)));
			}
			//		System.out.println(String.format(
			//				"      %,d: RCV %,d ACK carries %,d chunks %,d-%,d (%,d TCP option bytes)", this.serial,
			//				relativeAck, predSentNewCount, predSeqStartRel, predSeqEndRel, predBuffer.remaining() + 2));

			statBytesPackPred += predLength;

			SpoofThread.sendPackMsg(rawIpPacket, predBuffer);

			// Sequences are already updated with anchor position according to direction
			addEventPredSent(tcp.getSequenceNumber(), tcp.getAckNumber(), predSentNewCount, predSeqStartRel,
					predSeqEndRel);

			return true;
		}
	}

	public DisplayTable webGuiDetails()
	{
		DisplayTable table = new DisplayTable();

		table.addField("Side", "Receiver",
				"Receiver side connection deals mainly with incoming traffic and generates outgoing predictions");

		table.addField("Sent (by seq)", getStatBytesSent(), "Sent bytes by TCP sequence");

		table.addField("Received (by seq)", getStatBytesReceived(), "Received bytes by TCP sequence");

		table.addField("Known bytes", statBytesKnown, "Received real data that has a local match with cache");

		table.addField("PACK pred", statBytesPredMatch, "Total bytes in predictions sent out");

		table.addField("Pred match", statBytesPredMatch, "Sent predictions that also matched  later incoming data");

		table.addField("Sent predictions", predSent.size(),
				"Total number of chunks in predictions sent to the other side");

		return table;
	}

	/**
	 * @param chunks
	 *            Number of sent chunks.
	 * @param predSeqStartRel
	 *            TCP sequence of the prediction's start (relative offset to start).
	 * @param predSeqEndRel
	 *            TCP sequence of the prediction's end, inclusive (relative offset to start).
	 */
	public TcpEventPred addEventPredSent(long localSeq, long remoteSeq, int chunks, long predSeqStartRel,
			long predSeqEndRel)
	{
		TcpEventPredSent event = new TcpEventPredSent(localSeq, remoteSeq, chunks, predSeqStartRel, predSeqEndRel);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}
}
