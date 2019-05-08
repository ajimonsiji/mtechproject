package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.NFQueue.Verdict;
import il.ac.technion.eyalzo.common.ConversionUtils;
import il.ac.technion.eyalzo.common.VideoUtils;
import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.pack.CaptureThread;
import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.spoof.SpoofThread;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Machine
{
	/**
	 * Optional first host name found in an HTTP request.
	 */
	String hostName;
	/**
	 * If true the machine is part of the sender side remote list, meaning that it is a receiver machine.
	 */
	final boolean sideSender;
	/**
	 * Server IP address and port.
	 */
	InetSocketAddress addr;
	/**
	 * Live real connection, each for a different client IP+port. When a session is turned into upload from cache, the
	 * connection is removed from this list.
	 */
	private HashMap<InetSocketAddress, TcpConn> connections = new HashMap<InetSocketAddress, TcpConn>();

	//
	// Times
	//
	/**
	 * When this object was created.
	 */
	long statStartTime;
	/**
	 * Last action, request or response.
	 */
	long statLastActionTime;

	public Machine(boolean sideSender, InetSocketAddress serverAddr)
	{
		this.sideSender = sideSender;
		this.addr = serverAddr;
		this.statStartTime = System.currentTimeMillis();
		this.statLastActionTime = this.statStartTime;
	}

	@Override
	/**
	 * @return IP address as string
	 */
	public String toString()
	{
		return sideSender ? "SND " : "RCV " + this.addr.toString().substring(1);
	}

	/**
	 * Remove a connection by client IP and port, when received FIN or RST.
	 * 
	 * @param clientAddr
	 *            Client IP:port.
	 * @return True if the session was registered or false if not found in records.
	 */
	public boolean removeConnection(InetSocketAddress clientAddr)
	{
		synchronized (connections)
		{
			TcpConn conn = connections.remove(clientAddr);
			return (conn != null);
		}
	}

	/**
	 * Cleanup inactive connections.
	 * 
	 * @param lastAllowedAckTime
	 *            Connection that did not receive an ACK since then, should be closed.
	 * @return Number of removed connections due to timeout.
	 */
	@SuppressWarnings("incomplete-switch")
	int cleanup(long lastAllowedAckTime)
	{
		int result = 0;

		synchronized (connections)
		{
			Iterator<TcpConn> it = connections.values().iterator();
			while (it.hasNext())
			{
				TcpConn curConn = it.next();

				// Skip sessions that accepted an ack recently
				if (curConn.isAlive(lastAllowedAckTime))
					continue;

				// Remove inactive connection
				it.remove();

				result++;
			}
		}

		return result;
	}

	/**
	 * @param lastAllowedActionTime
	 *            Server that were not active since (request or response), and do not have large ended sessions, will be
	 *            removed.
	 * @return True if the server should be cleaned-up because it was not active for too long and it does not have
	 *         registered connections.
	 */
	boolean isInactive(long lastAllowedActionTime)
	{
		synchronized (connections)
		{
			return lastAllowedActionTime > this.statLastActionTime && connections.isEmpty();
		}
	}

	/**
	 * @return Number of live sessions.
	 */
	public int getConnsCount()
	{
		synchronized (connections)
		{
			return connections.size();
		}
	}

	public InetSocketAddress getAddress()
	{
		return this.addr;
	}

	public Verdict processPacket(TCPPacket tcp, byte[] rawIpPacket, boolean dirOut) throws UnknownHostException
	{
		Verdict result = Verdict.NF_ACCEPT;

		this.statLastActionTime = System.currentTimeMillis();

		// Local address, to locate the connection
		InetSocketAddress localAddr = new InetSocketAddress(dirOut ? tcp.getSourceAsInetAddress() : tcp
				.getDestinationAsInetAddress(), dirOut ? tcp.getSourcePort() : tcp.getDestinationPort());

		long remoteSeq = dirOut ? tcp.getAckNumber() : tcp.getSequenceNumber();
		long localSeq = dirOut ? tcp.getSequenceNumber() : tcp.getAckNumber();

		// System.out.println(String.format("%,d %s %s", remoteSeq, dirUp ? "^"
		// : ".", tcp.flagsToString()));

		//
		// Get or add the connection
		//
		TcpConn conn = null;
		synchronized (connections)
		{
			// Try to find that connection in the remote machine's connection
			// list
			conn = connections.get(localAddr);
			if (conn == null)
			{
				// It must be SYN
				if (!tcp.isSet(TCPPacket.MASK_SYN))
					return result;

				// Hijack for later spoof
				if (dirOut)
				{
					result = Verdict.NF_DROP;
					SpoofThread.sendPackPermitted(rawIpPacket);
				}

				if (tcp.isSet(TCPPacket.MASK_ACK))
					return result;

				int windowScaling = TcpUtils.getTcpOptionWindowScaling(rawIpPacket);

				//
				// Add new connection
				//

				// SYN consumes one sequence but it has no data in practice
				if (sideSender)
					conn = new TcpConnSnd(dirOut, tcp.getSequenceNumber(), windowScaling);
				else
					conn = new TcpConnRcv(dirOut, tcp.getSequenceNumber(), windowScaling);

				connections.put(localAddr, conn);

				// Only the local sequence has a meaning
				if (Main.debugLevel >= 2)
					System.out.println(String.format("%,d: %s. %s SYN. New. Port %,d. Init seq %,d", conn.getSerial(),
							(sideSender ? "SND" : "RCV"), (dirOut ? "Sent" : "Got"), localAddr.getPort(),
							dirOut ? localSeq : remoteSeq));

				return result;
			}
		}

		//
		// SYN+ACK that is required for sequence number and window scaling
		//
		if (tcp.isSet(TCPPacket.MASK_SYN | TCPPacket.MASK_ACK))
		{
			// If this is the first time the SYN+ACK is seen (on the right direction)
			if (!conn.isEstablished())
			{
				// Set window scaling and mark as established
				int windowScaling = TcpUtils.getTcpOptionWindowScaling(rawIpPacket);
				conn.synAck(dirOut, tcp.getSequenceNumber(), windowScaling);

				// If going out then hijack and set the PACK Permitted flag
				if (dirOut)
				{
					// Set the PACK permitted and set MSS if needed
					result = Verdict.NF_DROP;
					SpoofThread.sendPackPermitted(rawIpPacket);
				}

				if (Main.debugLevel >= 5)
					System.out.println(String.format("   %,d: %s. %s SYN+ACK. Ack seq %,d", conn.getSerial(),
							(sideSender ? "SND" : "RCV"), (dirOut ? "Sent" : "Got"), dirOut ? localSeq : remoteSeq));
			}
			return result;
		}

		// For non-established connection we must quit here as there are no allocated buffers yet
		if (!conn.isEstablished())
		{
			if (Main.debugLevel >= 5)
				System.out.println(String.format("%,d: %s. %s. Real seq %,d. Conn not established yet!", conn
						.getSerial(), (sideSender ? "SND" : "RCV"), (dirOut ? "Out" : "In"), (dirOut ? localSeq
						: remoteSeq)));
			return result;
		}

		// Artificial packet loss mechanism
		if(CaptureThread.randPacketLoss != null && CaptureThread.randPacketLoss.nextInt(Main.lossRate) == 0)
		{
			conn.lostPacket();
			return Verdict.NF_DROP;
		}
		
		conn.gotPacket();
		
		//
		// Handle the packet in the context of the connection
		//
		result = conn.handlePacket(dirOut, tcp, rawIpPacket);

		if (tcp.getTCPDataByteLength() <= 0)
			return result;

		//
		// Further processing for information only
		//

		// This is where the TCP payload starts
		int startOffset = tcp.getCombinedHeaderByteLength();

		// Look for HTTP response
		int httpResponseCode = TcpUtils.getHttpResponseCode(rawIpPacket, startOffset);

		// Is it HTTP response?
		if (httpResponseCode > 0)
		{
			int headerLen = VideoUtils.getHttpHeaderLenFromPayloadStart(rawIpPacket, startOffset);
			long contentLength = VideoUtils.getContentLengthFromPayload(rawIpPacket, startOffset,
					headerLen > 0 ? headerLen : rawIpPacket.length);
			String contentType = VideoUtils.getContentTypeFromPayload(rawIpPacket, startOffset,
					headerLen > 0 ? headerLen : rawIpPacket.length);

			TcpEventHttpResponse event = conn.addEventHttpResponse(dirOut, localSeq, remoteSeq, httpResponseCode,
					headerLen, contentLength, contentType);

			if (Main.debugLevel >= 3)
				System.out.println(String.format("   %,d: %,d %s", conn.getSerial(), conn.getRalativeSeq(remoteSeq),
						event));
			return result;
		}

		// Is it HTTP request?
		if (TcpUtils.isHttpRequestPrefix(rawIpPacket, startOffset))
		{
			String url = TcpUtils.getUrlFromHttpPayload(rawIpPacket, startOffset);
			if (url == null)
				return result;

			String hostName = VideoUtils.getHostFromPayload(rawIpPacket, startOffset, rawIpPacket.length);
			setHostName(hostName);

			TcpEventHttpRequest event = conn.addEventHttpRequest(dirOut, localSeq, remoteSeq, hostName, url);

			if (Main.debugLevel >= 4)
				System.out.println(String.format("   %,d: %s %,d %s %s", conn.getSerial(), sideSender ? "SND" : "RCV",
						conn.getRalativeSeq(remoteSeq), (dirOut ? "sent" : "got"), event));
		}

		return result;
	}

	TcpConn getConnBySerial(int serial)
	{
		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				if (curConn.getSerial() == serial)
					return curConn;
			}
		}

		return null;
	}

	private void setHostName(String hostName)
	{
		if (hostName == null || hostName.isEmpty())
			return;

		if (this.hostName != null && !hostName.isEmpty())
			return;

		this.hostName = hostName;
	}

	public DisplayTable webGuiDetails()
	{
		long now = System.currentTimeMillis();

		DisplayTable table = new DisplayTable();

		table.addField("Side", this.sideSender ? "Sender" : "Receiver", "Machine side, sender or receiver");
		table.addField("Host name", this.hostName, "First legal host name found in any HTTP request");
		table.addField("Start time", ConversionUtils.millisAsHumanReadable(now - this.statStartTime),
				"When the machine was first noticed (may also been active but cleanup bfore)");
		table.addField("Last packet", ConversionUtils.millisAsHumanReadable(now - this.statLastActionTime),
				"Last time a packet was captured");

		return table;
	}

	long getStatChainBytes()
	{
		long result = 0;

		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				result += curConn.statBytesPredMatch;
			}
		}

		return result;
	}

	/**
	 * @return Receiver side only. Incoming real bytes that match existing chunk.
	 */
	public long getStatBytesKnown()
	{
		if (sideSender)
			return 0;

		long result = 0;

		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				result += ((TcpConnRcv) curConn).statBytesKnown;
			}
		}

		return result;
	}

	/**
	 * @return Sender side only. Number of bytes in real data sent out while it overlaps ranges of predictions in the
	 *         inbox. 0 if receiver side.
	 */
	public long getStatBytesPredOverlap()
	{
		if (!sideSender)
			return 0;

		long result = 0;

		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				result += ((TcpConnSnd) curConn).statBytesPredOverlap;
			}
		}

		return result;
	}

	/**
	 * Number of bytes in PACK command PRED.
	 * 
	 * @return Receiver: predictions sent out. Sender: predictions inserted to inbox.
	 */
	public long getStatBytesPackPred()
	{
		long result = 0;

		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				result += curConn.statBytesPackPred;
			}
		}

		return result;
	}

	public long getStatBytesPredMatch()
	{
		long result = 0;

		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				result += curConn.statBytesPredMatch;
			}
		}

		return result;
	}

	public long getStatBytesPredAck()
	{
		long result = 0;

		synchronized (connections)
		{
			for (TcpConn curConn : connections.values())
			{
				result += curConn.statBytesPredAck;
			}
		}

		return result;
	}

	public boolean isSideSender()
	{
		return this.sideSender;
	}

	public Map<InetSocketAddress, TcpConn> getConnsDup()
	{
		synchronized (connections)
		{
			return new HashMap<InetSocketAddress, TcpConn>(connections);
		}
	}

	public int releaseTimeoutBuffers(long lastAllowedActionTime)
	{
		// Sanity check
		if (!sideSender)
			return 0;

		int result = 0;

		synchronized (connections)
		{
			Iterator<TcpConn> it = connections.values().iterator();
			while (it.hasNext())
			{
				TcpConnSnd curConn = (TcpConnSnd) it.next();

				// Skip connections that do not buffer or were recently active 
				if (!curConn.isBufferingBefore(lastAllowedActionTime))
					continue;

				// Release the buffer and data
				curConn.releaseCurrentBuffer();
			}
		}

		return result;
	}
}
