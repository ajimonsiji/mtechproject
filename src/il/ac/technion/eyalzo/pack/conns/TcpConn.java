package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.NFQueue.Verdict;
import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.util.LinkedList;

public abstract class TcpConn
{
	/**
	 * Maximal number of characters to display in table for HTTP requests.
	 */
	private static final int MAX_DISPLAY_URL_CHARS = 50;
	/**
	 * When the connection attempt was sent.
	 */
	private long startTime;
	/**
	 * Initiation direction where true means that the local sent the SYN and the remote the SYN+ACK.
	 */
	private boolean synDirOut;
	/**
	 * Fully established when true meaning that SYN+ACK was received.
	 */
	private boolean established;
	/**
	 * Connections counter for logging and debug.
	 */
	private static long globalSerial = 1;
	/**
	 * Serial of this TCP connection (1 based).
	 * 
	 * @see #globalSerial
	 */
	protected final long serial;
	/**
	 * Last time a packet was captured for this connection.
	 */
	long lastPacketTime;
	long lostPacket = 0;
	long gotPacket = 0;

	/**
	 * Events during the connection time.
	 */
	protected LinkedList<TcpEvent> tcpEvents = new LinkedList<TcpEvent>();

	//
	// Windows
	//
	/**
	 * Number of bits to move to the left (1 less than the sent scaling).
	 */
	protected int localWindowScaling;
	/**
	 * Number of bits to move to the left (1 less than the sent scaling).
	 */
	protected int remoteWindowScaling;
	/**
	 * Without scaling.
	 */
	protected int localLastWindow;
	/**
	 * Without scaling.
	 */
	protected int remoteLastWindow;

	//
	// TCP sequence
	//
	/**
	 * TCP sequence of the remote machine when started the connection, meaning the first byte after the SYN.
	 */
	protected long remoteSeqStart;
	/**
	 * TCP sequence of the local machine when started the connection, meaning the first byte after the SYN.
	 */
	protected long localSeqStart;
	/**
	 * Current highest sequence seen + 1.
	 */
	protected long remoteSeqEnd;
	/**
	 * Current highest sequence seen + 1.
	 */
	protected long localSeqEnd;

	//
	// Statistics
	//
	/**
	 * Real bytes that match a prediction (receiver in or sender out).
	 */
	long statBytesPredMatch;
	/**
	 * Receiver: predictions sent out. Sender: predictions inserted to inbox.
	 */
	long statBytesPackPred;
	/**
	 * Acknowledged bytes, which is the last step in prediction on the sender side. Sender: sent virtual bytes.
	 * Receiver: received virtual bytes.
	 */
	long statBytesPredAck;

	/**
	 * @param synSeq
	 *            TCP sequence of the SYN, which is 1 less than the first payload byte to arrive later. To be called
	 *            when a SYN is received (not SYN+ACK). At that stage the window scaling is very important because it
	 *            will not appear again anywhere for this direction.
	 */
	public TcpConn(boolean synDirOut, long synSeq, int windowScaling)
	{
		this.startTime = System.currentTimeMillis();
		this.synDirOut = synDirOut;
		this.lastPacketTime = this.startTime;
		this.serial = globalSerial;
		globalSerial++;

		int fixedWindowScaling = windowScaling <= 0 ? 0 : windowScaling;

		// Initiate one side by direction
		long dataStartSeq = TcpUtils.tcpSequenceAdd(synSeq, 1);
		if (synDirOut)
		{
			// Start sequence is one after SYN
			this.localSeqStart = dataStartSeq;
			this.localWindowScaling = fixedWindowScaling;
		} else
		{
			// Start sequence is one after SYN
			this.remoteSeqStart = dataStartSeq;
			this.remoteWindowScaling = fixedWindowScaling;
		}
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
	public abstract Verdict handlePacket(boolean dirOut, TCPPacket tcp, byte[] rawIpPacket);

	/**
	 * Caught SYN+ACK. No need for direction as we assume that it is no the side who sent the SYN that initiated the
	 * connection.
	 * 
	 * @param seq
	 *            TCP sequence of the side that sent the SYN+ACK meaning not the initiator of the connection.
	 * @param windowScaling
	 *            Window scaling factor (not bits) of the side that sent the SYN+ACK meaning not the initiator of the
	 *            connection.
	 */
	public void synAck(boolean dirOut, long seq, int windowScaling)
	{
		// Make sure this is the right direction (against malicious users)
		if (dirOut == synDirOut)
			return;

		int fixedWindowScaling = windowScaling <= 0 ? 0 : windowScaling;

		// Initiate one side by direction of SYN that is opposite to this one
		if (synDirOut)
		{
			// Start sequence is one after SYN
			this.remoteSeqStart = TcpUtils.tcpSequenceAdd(seq, 1);
			this.remoteWindowScaling = fixedWindowScaling;
		} else
		{
			// Start sequence is one after SYN
			this.localSeqStart = TcpUtils.tcpSequenceAdd(seq, 1);
			this.localWindowScaling = fixedWindowScaling;
		}

		this.established = true;
	}

	/**
	 * @return Serial number of this TCP connection.
	 */
	public long getSerial()
	{
		return this.serial;
	}

	/**
	 * @param lastAllowedAckTime
	 *            Connection that did not capture packets since this time should be removed.
	 * @return True if the connection was recently active, or false if it seems dead.
	 */
	public synchronized boolean isAlive(long lastAllowedAckTime)
	{
		return this.lastPacketTime >= lastAllowedAckTime;
	}

	/**
	 * @return Last time a client packet time was saved here.
	 * @see Session#getLastDataTime()
	 */
	public long getLastPacketTime()
	{
		return this.lastPacketTime;
	}

	@Override
	public String toString()
	{
		return Long.toString(this.serial);
	}

	public long getRalativeSeq(long seqAbs)
	{
		return TcpUtils.tcpSequenceDiff(this.remoteSeqStart, seqAbs);
	}

	public TcpEventHttpResponse addEventHttpResponse(boolean dirUp, long localSeq, long remoteSeq, int responseCode,
			int headerLen, long contentLength, String contentType)
	{
		TcpEventHttpResponse event = new TcpEventHttpResponse(dirUp, localSeq, remoteSeq, responseCode, headerLen,
				contentLength, contentType);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	public TcpEventHttpRequest addEventHttpRequest(boolean dirUp, long localSeq, long remoteSeq, String hostName,
			String url)
	{
		TcpEventHttpRequest event = new TcpEventHttpRequest(dirUp, localSeq, remoteSeq, hostName, url);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	public TcpEventFirstAnchor addEventFirstAnchor(boolean dirUp, long localSeq, long remoteSeq)
	{
		TcpEventFirstAnchor event = new TcpEventFirstAnchor(dirUp, localSeq, remoteSeq);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	public TcpEventClose addEventClose(boolean dirUp, long localSeq, long remoteSeq, boolean isFin)
	{
		TcpEventClose event = new TcpEventClose(dirUp, localSeq, remoteSeq, isFin);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	public TcpEventChunk addEventChunk(long localSeq, long remoteSeq, long signature, int chunkLen,
			int receiverWindowSize, boolean chunkInFile, boolean matchExpected)
	{
		TcpEventChunk event = new TcpEventChunk(localSeq, remoteSeq, signature, chunkLen, receiverWindowSize,
				chunkInFile, matchExpected);
		synchronized (tcpEvents)
		{
			tcpEvents.add(event);
		}

		return event;
	}

	/**
	 * 
	 * @return Total number of connections detected so far.
	 */
	public static long getConnectionCount()
	{
		return globalSerial - 1;
	}

	public int getEventsCount()
	{
		synchronized (tcpEvents)
		{
			return tcpEvents.size();
		}
	}

	/**
	 * @param cmdChunkDetails
	 *            Link to the chunk details page, ready to get a hex signature.
	 * @param paramLen
	 *            Length parameter to go along with the chunk details link.
	 */
	public DisplayTable webGuiEvents(boolean showHttp, boolean showChunks, String cmdChunkDetails, String paramLen)
	{
		DisplayTable table = new DisplayTable();

		table.addColNum("Time", "Time relative to start", true, false, true, null, " mSec");
		table.setLastColAsDefaultSortCol();
		table.addCol("Dir", "Direction where \"^\" means up (from this machine)", false);
		table.addColNum("Local<br>seq", "TCP sequence of the local machine relative to connection start", true, false,
				true);
		table.addColNum("Remote<br>seq", "TCP sequence of the remote machine relative to connection start", true,
				false, true);
		table.addCol("Type", "Type of event", true);
		table.addCol("Val 1", "Pred: chunks / HTTP req: host / HTTP res: code / Chunk: sign", true);
		table.addCol("Val 2", "Pred: start seq / HTTP req: URL / HTTP res: content-len / Chunk: match?", true);
		table.addCol("Val 3", "Pred: end seq inc / HTTP res: header len / Chunk: back len", true);

		synchronized (tcpEvents)
		{
			for (TcpEvent curEvent : tcpEvents)
			{
				//
				// Filter
				//
				if (!showChunks)
				{
					if (!(curEvent instanceof TcpEventHttpRequest || curEvent instanceof TcpEventHttpResponse))
						continue;
				}

				if (!showHttp)
				{
					if (!(curEvent instanceof TcpEventChunk || curEvent instanceof TcpEventFirstAnchor
							|| curEvent instanceof TcpEventSndSign || curEvent instanceof TcpEventSndPredSkip || curEvent instanceof TcpEventPredIn))
						continue;
				}

				table.addRow(curEvent.getColor());

				// Time
				table.addCell(curEvent.time - this.startTime);
				// Direction
				table.addCell(curEvent.dirUp ? "^" : null);
				// Local sequence where absolute zero means that there was no
				// ACK
				table.addCell(curEvent.localSeq == 0 ? null : TcpUtils.tcpSequenceDiff(this.localSeqStart,
						curEvent.localSeq));
				// Remote sequence where absolute zero means that there was no
				// ACK
				table.addCell(curEvent.remoteSeq == 0 ? null : TcpUtils.tcpSequenceDiff(this.remoteSeqStart,
						curEvent.remoteSeq));
				// Type
				table.addCell(curEvent.getType());

				//
				// Per type
				//
				if (curEvent instanceof TcpEventHttpRequest)
				{
					// Properties
					TcpEventHttpRequest request = (TcpEventHttpRequest) curEvent;
					// Val 1 - host name
					table.addCell(request.hostName);
					// Val 2 - URL
					String displayUrl = request.url;
					if (displayUrl.length() > MAX_DISPLAY_URL_CHARS)
					{
						displayUrl = displayUrl.substring(0, MAX_DISPLAY_URL_CHARS) + "...";
					}
					String link = (request.hostName == null || request.hostName.equals("")) ? null : "http://"
							+ request.hostName + request.url;
					table.addCell(displayUrl, link);
				} else if (curEvent instanceof TcpEventHttpResponse)
				{
					// Properties
					TcpEventHttpResponse response = (TcpEventHttpResponse) curEvent;
					table.addCell(response.responseCode);
					table.addCell(response.contentLength);
					table.addCell(response.headerLen);
				} else if (curEvent instanceof TcpEventChunk)
				{
					// Properties
					TcpEventChunk chunk = (TcpEventChunk) curEvent;
					table.addCell(String.format("<code>%08x</code>", 0xffffffffL & chunk.signature), cmdChunkDetails
							+ chunk.signature + "&" + paramLen + "=" + chunk.chunkLen);
					// table.addCell(chunk.chunkLen);
					table.addCell(chunk.matchExpected ? (chunk.chunkInFile ? "match+file" : "match")
							: (chunk.chunkInFile ? "file" : null));
					table.addCell(chunk.chunkLen);
				} else if (curEvent instanceof TcpEventSndSign)
				{
					TcpEventSndSign sign = (TcpEventSndSign) curEvent;
					// Match?
					table.addCell(sign.match ? "match" : "mismatch");
					// Signature
					table.addCell(String.format("<code>%08x</code>", 0xffffffffL & sign.signature));
					// Chunk length
					table.addCell(sign.chunkLen);
				} else if (curEvent instanceof TcpEventSndPredSkip)
				{
					TcpEventSndPredSkip skip = (TcpEventSndPredSkip) curEvent;
					table.addCell(skip.missing);
				} else if (curEvent instanceof TcpEventPredSent)
				{
					// Properties
					TcpEventPred pred = (TcpEventPred) curEvent;
					table.addCell(pred.chunks);
					// Prediction start, relative TCP sequence
					table.addCell(pred.predSeqStartRel);
					// Prediction end, relative TCP sequence
					table.addCell(pred.predSeqEndRel);
				} else if (curEvent instanceof TcpEventPredOverlap)
				{
					// Properties
					TcpEventPred pred = (TcpEventPred) curEvent;
					table.addCell(pred.chunks);
					// Prediction start, relative TCP sequence
					table.addCell(pred.predSeqStartRel);
					// Prediction end, relative TCP sequence
					table.addCell(pred.predSeqEndRel);
				} else if (curEvent instanceof TcpEventPredIn)
				{
					// Properties
					TcpEventPredIn pred = (TcpEventPredIn) curEvent;
					table.addCell(pred.localSeqMaxSentRel);
					// Prediction start, relative TCP sequence
					table.addCell(pred.predSeqStartRel);
					// Prediction end, relative TCP sequence
					table.addCell(pred.predSeqEndRel);
				} else if (curEvent instanceof TcpEventClose)
				{
					// Properties
					table.addCell(curEvent.toString());
				}
			}
		}

		return table;
	}

	public abstract DisplayTable webGuiDetails();

	/**
	 * 
	 * @return True if the SYN+ACK was detected already.
	 */
	public boolean isEstablished()
	{
		return this.established;
	}

	public long getStatBytesReceived()
	{
		return TcpUtils.tcpSequenceDiff(remoteSeqStart, remoteSeqEnd);
	}

	public long getStatBytesSent()
	{
		return TcpUtils.tcpSequenceDiff(localSeqStart, localSeqEnd);
	}

	public long getStatBitsPerSecSent()
	{
		long bits = this.getStatBytesSent() * 8;
		if (bits <= 0)
			return 0;

		long millis = this.lastPacketTime - this.startTime;
		if (millis <= 0)
			return 0;

		return bits * 1000 / millis;
	}

	public long getStatBitsPerSecReceived()
	{
		long bits = this.getStatBytesReceived() * 8;
		if (bits <= 0)
			return 0;

		long millis = this.lastPacketTime - this.startTime;
		if (millis <= 0)
			return 0;

		return bits * 1000 / millis;
	}

	public void lostPacket()
	{
		this.lostPacket++;
	}

	public void gotPacket()
	{
		this.gotPacket++;
	}
}
