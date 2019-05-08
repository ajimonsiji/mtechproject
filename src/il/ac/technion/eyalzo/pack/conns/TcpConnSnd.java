package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.NFQueue.Verdict;
import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.PackUtils;
import il.ac.technion.eyalzo.pack.RabinUtils;
import il.ac.technion.eyalzo.pack.TimeoutThread;
import il.ac.technion.eyalzo.pack.pred.PredInChunk;
import il.ac.technion.eyalzo.pack.pred.PredInList;
import il.ac.technion.eyalzo.pack.spoof.SpoofThread;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Random;

public class TcpConnSnd extends TcpConn
{
	/**
	 * Incoming predictions, waiting for outgoing packets to match.
	 */
	protected PredInList predInbox = new PredInList();
	/**
	 * Buffer outgoing data when there is an overlapping chunk in the prediction inbox.
	 */
	private PredInChunk curPredBuffering;
	/**
	 * When buffering started, to better understand retransmissions.
	 */
	private long curPredBufferingStartTime;
	private byte[] rawIpPacketForSpoof;
	private Random randDebugSha1 = new Random();

	//
	// Statistics
	//
	/**
	 * Outgoing bytes that overlap ranges in inbox predictions.
	 */
	long statBytesPredOverlap;

	public TcpConnSnd(boolean synDirOut, long seq, int windowScaling)
	{
		super(synDirOut, seq, windowScaling);
	}

	/**
	 * Add data to the internal buffer and look for the next anchor. Also check for connection closing.
	 * 
	 * @param tcpPayload
	 *            TCP payload data.
	 * @param tcpSeq
	 *            TCP sequence of the payload.
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
		long relativeSeq = TcpUtils.tcpSequenceDiff(this.localSeqStart, localSeq);

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
				System.out.println(String.format("   %,d: SND %,d end %s %s %,d overlap", this.serial, relativeSeq, tcp
						.flagsToString(), dirOut ? "out" : "in", statBytesPredOverlap));

			return Verdict.NF_ACCEPT;
		}

		boolean noTcpPayload = tcpPayloadSize == 0;

		Main.debugSha1 = 0;
		if (Main.debugSha1 > 0 && dirOut && !noTcpPayload && (Main.debugSha1 >= 100 || Main.debugSha1 > randDebugSha1.nextInt(100)))
		{
			RabinUtils.calculateSha1(rawIpPacket, tcp.getCombinedHeaderByteLength(), tcpPayloadSize);

			return Verdict.NF_ACCEPT;
		}

		//
		// Sender: Outgoing payload packets - possibly a match with incoming predictions
		//
		if (dirOut && !noTcpPayload && !predInbox.isEmpty())
			return handlePacketOutData(tcp, rawIpPacket, localSeq, remoteSeq, relativeSeq, tcpPayloadSize);

		//
		// Sender: Incoming PRED ?
		//
		if (!dirOut && PackUtils.hasPackPredCommand(packMessage))
		{
			int predInboxSizeBefore = predInbox.size();
			// Try to parse incoming OFFSET and (multiple) PRED and add the predictions to the inbox
			int predBytesLen = PackUtils.parsePredIn(localSeq, packMessage, predInbox);
			// Print even if the predictions were not added due to error
			packMessage.reset();
			if (predBytesLen == 0)
			{
				if (Main.debugLevel >= 4)
					System.out.println(String.format(
							"      %,d: SND %,d pack msg (%,d bytes) did not add predictions to inbox", this.serial,
							relativeSeq, packMessage.remaining()));
			} else
			{
				statBytesPackPred += predBytesLen;

				// Added chunks to inbox
				int predInboxSizeAdded = predInbox.size() - predInboxSizeBefore;

				long predSeqEndRel = TcpUtils.tcpSequenceDiff(this.localSeqStart, TcpUtils.tcpSequenceAdd(predInbox
						.getNextTcpSeq(), -1));
				long predsLen = predInbox.getPredsTotalLen(predInboxSizeAdded);
				long predSeqStartRel = predSeqEndRel - predsLen + 1;

				if (Main.debugLevel >= 5)
					System.out.println(String.format(
							"      %,d: SND %,d pack msg added %,d prediction bytes (-%,d) to inbox (size %,d)",
							this.serial, relativeSeq, predBytesLen, predSeqEndRel, predInbox.size()));

				addEventPredIn(localSeq, remoteSeq, TcpUtils.tcpSequenceDiff(this.localSeqStart, TcpUtils
						.tcpSequenceAdd(localSeqEnd, -1)), predInboxSizeAdded, predSeqStartRel, predSeqEndRel);
			}

			// Quit here since it cannot be used for anything else
			return Verdict.NF_ACCEPT;
		}

		return Verdict.NF_ACCEPT;
	}

	/**
	 * Handle sender's outgoing data to inspect the prediction inbox and see if the data overlaps a prediction.
	 */
	private synchronized Verdict handlePacketOutData(TCPPacket tcp, byte[] rawIpPacket, long localSeq, long remoteSeq,
			long relativeSeq, int tcpPayloadSize)
	{
		// Nothing to do if there are no predictions waiting in the inbox
		if (predInbox.isEmpty())
		{
			return Verdict.NF_ACCEPT;
		}

		LinkedList<PredInChunk> overlapPredsIn = predInbox.getPacketOverlaps(localSeq, tcpPayloadSize);

		// If there is no overlapping prediction, then there is nothing more to do here
		if (overlapPredsIn == null)
		{
			//				System.out.println(String.format("***   %,d: SND %,d out packet no overlap %s", this.serial,
			//						relativeSeq, predInbox.toString(this.localSeqStart)));
			return Verdict.NF_ACCEPT;
		}

		//
		// Calculate how many bytes in overlap and chunks
		//
		long overlapBytes = 0;
		long chunksTotalLen = 0;
		for (PredInChunk curChunk : overlapPredsIn)
		{
			chunksTotalLen += curChunk.getLength();
			overlapBytes += TcpUtils.tcpSequenceRangeOverlap(localSeq, tcpPayloadSize, curChunk.getTcpSeq(), curChunk
					.getLength());
		}

		PredInChunk chunk = overlapPredsIn.getFirst();

		// Start sequence of the first overlapping chunk
		long predSeqStartRel = TcpUtils.tcpSequenceDiff(localSeqStart, chunk.getTcpSeq());
		long predSeqEndRel = predSeqStartRel + chunksTotalLen - 1;

		addEventPredOverlap(localSeq, remoteSeq, overlapPredsIn.size(), predSeqStartRel, predSeqEndRel);

		//
		// Add the data to the buffer
		//
		int bufferedBytes = chunk.addOutData(rawIpPacket);
		Verdict verdict = Verdict.NF_ACCEPT;

		// Needed when first part of a packet overlaps a matched prediction, but another part belongs to another or none
		boolean dropFirstPart = false;

		// Check if it's a new chunk
		if (curPredBuffering == null || curPredBuffering != chunk)
		{
			// First time a packet belongs to this prediction is caught

			if (bufferedBytes > 0)
			{
				// The packet covers the beginning of the chunk (rare)

				if (Main.debugLevel >= 4)
					System.out.println(String.format(
							"      %,d: SND %,d packet overlap start (%,d-) send %,d and prepare buffer %,d",
							this.serial, relativeSeq, predSeqStartRel, tcpPayloadSize - bufferedBytes, chunk
									.getLength()));

				// Send first part and buffer the rest: [-----send-chunk-1----|-------buffer-chunk-2--------]
				SpoofThread.sendShorter(rawIpPacket, tcpPayloadSize - bufferedBytes);

				verdict = Verdict.NF_DROP;
			} else
			{
				// The packet does not cover the beginning of the chunk

				long missing = TcpUtils.tcpSequenceDiff(chunk.getTcpSeq(), localSeq);
				addEventSndPredSkip(localSeq, remoteSeq, (int) missing);

				// Remove that chunk as we can't buffer it
				predInbox.cleanupUntilChunk(chunk);

				if (Main.debugLevel >= 4)
					System.out
							.println(String
									.format(
											"      %,d: SND %,d packet overlaps %,d inbox chunks (%,d-) %,d bytes, missing %,d bytes so skip to next chunk",
											this.serial, relativeSeq, overlapPredsIn.size(), predSeqStartRel,
											overlapBytes, missing));
			}

			// Remember the current chunk, to continue with buffer or skip
			curPredBuffering = chunk;
			curPredBufferingStartTime = System.currentTimeMillis();

			// Save a raw IP packet just once, for safer and simpler spoofing of data (if needed)
			if (rawIpPacketForSpoof == null)
				rawIpPacketForSpoof = rawIpPacket;
		} else if (chunk.isOutBufReadyForSignature())
		{
			// If it was just completed

			// Always drop because even on mismatch the chunk retransmission will handle that packet too
			verdict = Verdict.NF_DROP;
			// Signal for the next adjacent chunk
			dropFirstPart = true;

			int sha1 = chunk.calculateSha1();
			boolean match = chunk.signatureMatch(sha1);

			addEventSndSign(localSeq, remoteSeq, match, sha1, (int) chunk.getLength());

			if (match)
			{
				// Send PACK acknowledgment
				sendPackAck(tcp, rawIpPacket, chunk.getTcpSeq(), chunk.getLength());

				// Release the buffer to eliminate a transmission of the buffered data
				curPredBuffering = null;

				if (Main.debugLevel >= 5)
					System.out.println(String.format("      %,d: SND %,d full buffer sign match %08x", this.serial,
							relativeSeq, 0xffffffffL & sha1));
			} else
			{
				// Send now
				SpoofThread.sendBufferedChunkOut(rawIpPacket, chunk);

				if (Main.debugLevel >= 5)
					System.out.println(String.format("      %,d: SND %,d full buffer sign no match %08x != %08x !!!",
							this.serial, relativeSeq, 0xffffffffL & sha1, chunk.getSignature()));
			}
		} else if (bufferedBytes > 0)
		{
			// The packet was buffered as part of a chunk
			verdict = Verdict.NF_DROP;

			//TODO new ! temp value
			//			SpoofThread.sendAck(rawIpPacket, 10000 >> this.remoteWindowScaling);

			// Just buffered, and not a complete chunk yet
			if (Main.debugLevel >= 6)
				System.out.println(String.format(
						"         %,d: SND %,d out overlap %,d chunks (%,d-), buffer %,d bytes", this.serial,
						relativeSeq, overlapPredsIn.size(), predSeqStartRel, bufferedBytes));
		} else
		{
			// This is a retransmission because the sender did not get an ACK for too long
			// It will usually be a packet that starts exactly where the buffered chunk starts

			// Don't buffer this chunk anymore
			SpoofThread.sendBufferedChunkOut(rawIpPacket, chunk);
			// Don't try again
			predInbox.cleanupUntilChunk(chunk);
			// Drop it because it overlaps this chunk
			verdict = Verdict.NF_DROP;
			// No handled chunk for now
			curPredBuffering = null;

			// Calculate how much time passed since started to buffer
			long timeoutMillis = System.currentTimeMillis() - curPredBufferingStartTime;
			TimeoutThread.setSndChunkTimeoutMillis((int)timeoutMillis);
			
			if (Main.debugLevel >= 3)
			{
				System.out.println(String.format("         %,d: SND %,d retrans %,d mSec release buffer %,d-%,d", this.serial,
						relativeSeq, timeoutMillis, TcpUtils.tcpSequenceDiff(this.localSeqStart, chunk.getTcpSeq()), TcpUtils
								.tcpSequenceDiff(this.localSeqStart, chunk.getTcpSeq())
								+ chunk.getOutBufFilledBytes() - 1));
			}
		}

		// If there is another chunk, it must be valid 
		if (overlapPredsIn.size() > 1)
		{
			// Drop was handled before, with the previous chunk processing

			// Get the second chunk from the prediction inbox
			chunk = overlapPredsIn.get(1);

			// Move to the second chunk from now on
			curPredBuffering = chunk;
			curPredBufferingStartTime = System.currentTimeMillis();

			// Add the relevant outgoing data to it
			bufferedBytes = chunk.addOutData(rawIpPacket);

			// Drop always
			verdict = Verdict.NF_DROP;

			// If the first chunk was not buffered, then need to release that packet for the previous chunk, but without the second chunk's bytes
			if (!dropFirstPart)
			{
				// Send first part and buffer the rest: [-----send-chunk-1----|-------buffer-chunk-2--------]
				SpoofThread.sendShorter(rawIpPacket, tcpPayloadSize - bufferedBytes);

				if (Main.debugLevel >= 5)
					System.out.println(String.format(
							"      %,d: SND %,d new buffer (%,d-%,d) for %08x, sent %,d of %,d bytes", this.serial,
							relativeSeq, TcpUtils.tcpSequenceDiff(this.localSeqStart, chunk.getTcpSeq()), TcpUtils
									.tcpSequenceDiff(this.localSeqStart, chunk.getTcpSeq())
									+ chunk.getLength() - 1, chunk.getSignature(), tcpPayloadSize - bufferedBytes,
							tcpPayloadSize));
			} else
			{
				if (Main.debugLevel >= 5)
					System.out.println(String.format("      %,d: SND %,d new buffer (%,d-%,d) for %08x, drop packet",
							this.serial, relativeSeq, TcpUtils.tcpSequenceDiff(this.localSeqStart, chunk.getTcpSeq()),
							TcpUtils.tcpSequenceDiff(this.localSeqStart, chunk.getTcpSeq()) + chunk.getLength() - 1,
							chunk.getSignature()));
			}
		} else if (dropFirstPart && bufferedBytes < tcpPayloadSize)
		{
			// Several bytes were buffered in previous chunk, and there is no prediction for the second part

			SpoofThread.sendSkipPart(rawIpPacket, tcpPayloadSize - bufferedBytes);

			if (Main.debugLevel >= 5)
				System.out.println(String.format("      %,d: SND %,d no overlap for second part (%,d-%,d)",
						this.serial, relativeSeq, relativeSeq + bufferedBytes, relativeSeq + tcpPayloadSize - 1));
		}

		// Statistics
		statBytesPredOverlap += overlapBytes;

		// Nothing more to do here
		return verdict;
	}

	public synchronized void releaseCurrentBuffer()
	{
		if (Main.debugLevel >= 5)
		{
			long timeoutMillis = System.currentTimeMillis() - curPredBufferingStartTime;
			long seqStart = TcpUtils.tcpSequenceDiff(this.localSeqStart, curPredBuffering.getTcpSeq());
			long seqEnd = seqStart + curPredBuffering.getLength() - 1;
			System.out.println(String.format("         %,d: SND release after %,d mSec (%,d-%,d)", this.serial, timeoutMillis ,seqStart,
					seqEnd));
		}

		// Don't buffer this chunk anymore
		SpoofThread.sendBufferedChunkOut(rawIpPacketForSpoof, curPredBuffering);
		// Don't try again
		predInbox.cleanupUntilChunk(curPredBuffering);
		// No handled chunk for now
		curPredBuffering = null;
	}

	public DisplayTable webGuiDetails()
	{
		DisplayTable table = new DisplayTable();

		table.addField("Sent (by seq)", getStatBytesSent(), "Sent bytes by TCP sequence");

		table.addField("Received (by seq)", getStatBytesReceived(), "Received bytes by TCP sequence");

		table.addField("PACK pred", statBytesPredMatch, "Total bytes in received prediction PRED commands");

		table.addField("Overlap bytes", statBytesPredOverlap,
				"Outgoing bytes that overlap (by range) inbox predictions");

		return table;
	}

	/**
	 * @param localSeqMaxSentRel
	 *            Maximal sequence number of already sent out data. It is not the regular local sequence which is the
	 *            acknowledge number in the incoming packet that carries the predictions.
	 * @param chunks
	 *            Number of received chunks.
	 * @param predSeqStartRel
	 *            TCP sequence of the prediction's start (relative offset to start).
	 * @param predSeqEndRel
	 *            TCP sequence of the prediction's end, inclusive (relative offset to start).
	 */
	public TcpEventPred addEventPredIn(long localSeq, long remoteSeq, long localSeqMaxSentRel, int chunks,
			long predSeqStartRel, long predSeqEndRel)
	{
		TcpEventPredIn event = new TcpEventPredIn(localSeq, remoteSeq, localSeqMaxSentRel, chunks, predSeqStartRel,
				predSeqEndRel);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	/**
	 * @param chunks
	 *            Number of covered chunks.
	 * @param predSeqStartRel
	 *            TCP sequence of the first overlapping chunk (relative offset to start).
	 * @param predSeqEndRel
	 *            TCP sequence of the last byte in the overlapping chunk, inclusive (relative offset to start).
	 */
	public TcpEventPred addEventPredOverlap(long localSeq, long remoteSeq, int chunks, long predSeqStartRel,
			long predSeqEndRel)
	{
		TcpEventPredOverlap event = new TcpEventPredOverlap(localSeq, remoteSeq, chunks, predSeqStartRel, predSeqEndRel);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	/**
	 * @param match
	 *            True if signed and matched the prediction.
	 */
	public TcpEventSndSign addEventSndSign(long localSeq, long remoteSeq, boolean match, int signature, int chunkLen)
	{
		TcpEventSndSign event = new TcpEventSndSign(localSeq, remoteSeq, match, signature, chunkLen);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	/**
	 * @param missing
	 *            Number of missing bytes (already sent) to be able to fill the buffer and sign.
	 */
	public TcpEventSndPredSkip addEventSndPredSkip(long localSeq, long remoteSeq, int missing)
	{
		TcpEventSndPredSkip event = new TcpEventSndPredSkip(localSeq, remoteSeq, missing);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	/**
	 * @param ackLength
	 *            Length of acknowledged block (usually a single chunk).
	 */
	private TcpEventSndPredAck addEventSndPredAck(long localSeq, long remoteSeq, long ackLength)
	{
		TcpEventSndPredAck event = new TcpEventSndPredAck(localSeq, remoteSeq, ackLength);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	public boolean isBufferingBefore(long timeMillis)
	{
		return curPredBuffering != null && curPredBufferingStartTime < timeMillis;
	}

	/**
	 * Use sender's outgoing data packet to generate a prediction-acknowledgment.
	 */
	private boolean sendPackAck(TCPPacket tcp, byte[] rawIpPacket, long seqStart, long ackLength)
	{
		ByteBuffer ackBuffer = PackUtils.generatePackAckPacket(ackLength);

		// It can't really fail
		if (ackBuffer == null)
			return false;

		statBytesPredAck += ackLength;

		SpoofThread.sendPackMsg(rawIpPacket, seqStart, ackBuffer);

		// Sequences are already updated with anchor position according to direction
		addEventSndPredAck(tcp.getSequenceNumber(), tcp.getAckNumber(), ackLength);

		return true;
	}
}
