package il.ac.technion.eyalzo.pack.spoof;

import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.pack.PackUtils;
import il.ac.technion.eyalzo.pack.conns.TcpUtils;
import il.ac.technion.eyalzo.pack.pred.PredInChunk;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import com.savarese.rocksaw.net.RawSocket;

/**
 * Thread that sends spoofed packets from dedicated queue.
 */
public class SpoofThread {
	/**
	 * General buffer for packets generated on spot.
	 */
	private static byte[] interBuf = new byte[TcpUtils.PACKET_SIZE];

	// Assume that is being used by one thread as an atomic operation
	// private static ByteBuffer bufferRequest;

	private static TCPPacket packetForChecksum = new TCPPacket(
			TcpUtils.IP_HEADER_LEN_BYTES + TcpUtils.MIN_TCP_HEADER_LEN_BYTES);

	private static RawSocket socketOther;
	private static RawSocket socketSelf;

	//
	// Configuration
	//
	private static final int SEND_TIMEOUT = 5000;

	public static void init(String deviceName) throws IOException {
		socketOther = initSocket(deviceName);
		socketSelf = initSocket("lo");
	}

	private static RawSocket initSocket(String deviceName) throws IOException {
		if (!verifyDevice(deviceName)) {
			System.out
					.println("The devices for responses were not found by netstat command. "
							+ "Run \"netstat -i\" and compare to deviceIn and deviceOut");
			System.exit(1);
		}

		RawSocket curSocket = null;

		try {
			curSocket = new RawSocket();
		} catch (UnsatisfiedLinkError e) {
			System.out.println("Problems with library file librocksaw.so");
			e.printStackTrace();
			System.exit(1);
		}
		curSocket.open(RawSocket.PF_INET, RawSocket.getProtocolByName("tcp"));
		curSocket.setIPHeaderInclude(true);
		curSocket.bindDevice(deviceName);

		try {
			curSocket.setSendTimeout(SEND_TIMEOUT);
			curSocket.setReceiveTimeout(SEND_TIMEOUT);
		} catch (java.net.SocketException se) {
			System.err.println("Problems setting timeout " + SEND_TIMEOUT
					+ ": " + se.toString());
			curSocket.setUseSelectTimeout(true);
			curSocket.setSendTimeout(SEND_TIMEOUT);
			curSocket.setReceiveTimeout(SEND_TIMEOUT);
		}

		System.out.println("Initiated raw-sockets on device " + deviceName);

		return curSocket;
	}

	/**
	 * @return True if the device is up and running.
	 */
	private static boolean verifyDevice(String deviceName) {
		boolean result = false;

		// Interface list, numeric (no name resolving)
		String dfCommand = "netstat -in";
		BufferedReader inputStream = null;
		Process process = null;
		try {
			process = Runtime.getRuntime().exec(
					new String[] { "/bin/sh", "-c", dfCommand });
			inputStream = new BufferedReader(new InputStreamReader(process
					.getInputStream()));
			while (true) {
				String inputLine = inputStream.readLine();
				if (inputLine == null)
					break;

				// Iface is up to 5 characters
				if (inputLine.length() < 5)
					continue;

				// Remove trailing spaces
				String ifaceName = inputLine.substring(0, 5).trim();

				// In
				if (deviceName.startsWith(ifaceName)) {
					result = true;
					break;
				}
			}
		} catch (IOException e) {
			return true;
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					System.err.println("inputStream can not be closed: "
							+ e.toString());
				}
			}
			if (process != null) {
				process.destroy();
			}
		}

		return result;
	}

	/**
	 * Send to receiver all the bytes that were buffered.
	 * 
	 * @param rawIpPacket
	 *            Raw IP packet just as source for several IP and TCP header
	 *            fields. Other fields will be overridden in the copy.
	 * @param chunk
	 *            Chunk to send, probably because of a mismatch.
	 */
	public static void sendBufferedChunkOut(byte[] rawIpPacket,
			PredInChunk chunk) {
		sendBuffer(socketOther, rawIpPacket, chunk.getOutBuffer(), chunk
				.getTcpSeq(), chunk.getOutBufFilledBytes());
	}

	/**
	 * Receiver sends to itself the bytes of an acknowledged prediction.
	 * 
	 * @param rawIpPacket
	 *            Raw IP packet just as source for several IP and TCP header
	 *            fields. Other fields will be overridden in the copy.
	 * @param chunk
	 *            Chunk to send, probably because of a mismatch.
	 */
	public static void sendAcknowledgedChunkIn(byte[] rawIpPacket, long tcpSeq,
			ChunkItem chunk) {
		sendBuffer(socketSelf, rawIpPacket, chunk.getContent(), tcpSeq, chunk
				.getLength());
	}

	/**
	 * Send to receiver all the bytes that were buffered.
	 * 
	 * @param rawIpPacket
	 *            Raw IP packet just as source for several IP and TCP header
	 *            fields. Other fields will be overridden in the copy.
	 * @param chunk
	 *            Chunk to send, probably because of a mismatch.
	 */
	private static void sendBuffer(RawSocket socket, byte[] rawIpPacket,
			byte[] chunkData, long tcpSeq, int len) {
		synchronized (interBuf) {
			System.arraycopy(rawIpPacket, 0, interBuf, 0,
					TcpUtils.COMBINED_HEADERS_LEN);

			// Remove options, to leave more space for data
			TcpUtils.resetTcpOptions(interBuf);

			// Remove flags and ACK to bypass iptables (PSH for self packets,
			// for easier debug and bypass)
			TcpUtils.setTcpFlagsOveride(interBuf,
					socket == socketSelf ? TcpUtils.FLAG_PSH : 0);
			TcpUtils.setTcpAck(interBuf, 0);

			byte[] addrBytes = TcpUtils.getIpDestinationAddr(interBuf);
			int nextOffset = 0;
			InetAddress addr = null;

			try {
				addr = InetAddress.getByAddress(addrBytes);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}

			while (true) {
				// Send and get next offset
				nextOffset = sendChunkPart(socket, addr, chunkData, len,
						nextOffset, tcpSeq);

				// Zero when finish or error
				if (nextOffset == 0)
					return;
			}
		}
	}

	/**
	 * Send a ready segment with IP and TCP headers over a raw socket.
	 * <p>
	 * Destination address is taken from the ready buffer itself. Also computes
	 * the checksum.
	 * 
	 * @param socket
	 *            The raw socket which is common to all traffic over a specific
	 *            NIC.
	 * @param sendBuffer
	 *            Buffer ready with IP and TCP headers only. Checksum does not
	 *            have to be correct. Length is according to IP total length in
	 *            the IP header.
	 * @return True if nothing went wrong, although it does not mean that the
	 *         data was sent or reached the destination.
	 */
	private static boolean sendSegment(RawSocket socket, byte[] sendBuffer) {
		// System.out.println("===== Spoof: " + bufferLen);

		// Get the destination address from the IP header part in the buffer
		byte[] addr = TcpUtils.getIpDestinationAddr(sendBuffer);
		InetAddress dstIp = null;
		try {
			dstIp = InetAddress.getByAddress(addr);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		//
		// Update the TCP checksum
		//
		synchronized (packetForChecksum) {
			packetForChecksum.setData(sendBuffer, false);
			packetForChecksum.computeTCPChecksum(true);
		}

		// Get packet length from the IP header
		int packetLen = TcpUtils.getIpTotalLen(sendBuffer);

		synchronized (socket) {
			try {
				socket.write(dstIp, sendBuffer, 0, packetLen);
			} catch (IllegalArgumentException ae) {
				System.err.println("Server reset illegal argument: "
						+ ae.toString());
				return false;
			} catch (InterruptedIOException te) {
				System.err.println("Server reset timeout to "
						+ dstIp.toString() + ": " + te.toString());
				return false;
			} catch (IOException ioe) {
				System.err.println("Server reset I/O error to "
						+ dstIp.toString() + ": " + ioe.toString());
				return false;
			}
		}

		return true;
	}

	/**
	 * @return Next offset to use.
	 */
	private static int sendChunkPart(RawSocket socket, InetAddress addr,
			byte[] chunkBuffer, int chunkBufferedBytes, int offset,
			long chunkSeq) {
		// How many bytes to put in this packet
		int length = Math.min(TcpUtils.TCP_PAYLOAD_SIZE, chunkBufferedBytes
				- offset);
		if (length <= 0)
			return 0;

		synchronized (interBuf) {
			// Fill the buffer, which also changes the buffer position
			System.arraycopy(chunkBuffer, offset, interBuf,
					TcpUtils.COMBINED_HEADERS_LEN, length);

			// Total length which is 20 IP header + 20 for TCP header, and data
			TcpUtils.setIpTotalLen(interBuf, length
					+ TcpUtils.COMBINED_HEADERS_LEN);

			// Sequence number
			TcpUtils.setTcpSeq(interBuf, TcpUtils.tcpSequenceAdd(chunkSeq,
					offset));

			//
			// Update the TCP checksum
			//
			synchronized (packetForChecksum) {
				packetForChecksum.setData(interBuf);
				packetForChecksum.computeTCPChecksum(true);
			}

			try {
				socket.write(addr, interBuf, 0, length
						+ TcpUtils.COMBINED_HEADERS_LEN);
			} catch (Exception ae) {
				ae.printStackTrace();
				return 0;
			}

			return offset + length;
		}
	}

	/**
	 * Send the same packet but with less bytes, to skip the second part of the
	 * payload.
	 */
	public synchronized static boolean sendShorter(byte[] rawIpPacket,
			int tcpPayloadLen) {
		// Get header length from the original packet
		int headersLen = TcpUtils.getCombinedHeadersLen(rawIpPacket);
		int ipTotalLen = headersLen + tcpPayloadLen;
		if (headersLen != 40)
			System.err.println(headersLen);

		// Set length in the new allocated buffer
		TcpUtils.setIpTotalLen(rawIpPacket, ipTotalLen);

		// Remove flags and ACK to bypass iptables
		TcpUtils.setTcpFlagsOveride(rawIpPacket, 0);
		TcpUtils.setTcpAck(rawIpPacket, 0);

		return sendSegment(socketOther, rawIpPacket);
	}

	/**
	 * Send ACK to this data buffered data packet. It is sent to the
	 * "self socket" on device "lo".
	 */
	public synchronized static boolean sendAck(byte[] rawIpPacketSender,
			int windowSize) {
		TcpUtils.swapAddresses(rawIpPacketSender);
		TcpUtils.swapSeqAndAck(rawIpPacketSender);
		TcpUtils.setWindowSize(rawIpPacketSender, windowSize);

		// Set length in the new allocated buffer
		TcpUtils
				.setIpTotalLen(rawIpPacketSender, TcpUtils.COMBINED_HEADERS_LEN);

		return sendSegment(socketSelf, rawIpPacketSender);
	}

	/**
	 * Send the same packet but without some of the first part payload bytes.
	 */
	public synchronized static boolean sendSkipPart(byte[] rawIpPacket,
			int newPayloadLen) {
		// Get header length from the original packet
		int headersLen = TcpUtils.getCombinedHeadersLen(rawIpPacket);
		if (headersLen != 40)
			System.err.println(headersLen);

		synchronized (interBuf) {
			// Copy headers
			System.arraycopy(rawIpPacket, 0, interBuf, 0, headersLen);

			// Original total length
			int orgPayloadLen = TcpUtils.getIpTotalLen(rawIpPacket)
					- headersLen;

			// How many bytes to remove from the first part
			int bytesToRemove = orgPayloadLen - newPayloadLen;

			// Fix sequence
			long orgSeq = TcpUtils.getTcpSeq(rawIpPacket);
			TcpUtils.setTcpSeq(interBuf, TcpUtils.tcpSequenceAdd(orgSeq,
					bytesToRemove));

			System.arraycopy(rawIpPacket, headersLen + bytesToRemove, interBuf,
					headersLen, newPayloadLen);

			// Set length in the new allocated buffer
			TcpUtils.setIpTotalLen(interBuf, newPayloadLen + headersLen);

			// Remove flags and ACK to bypass iptables
			TcpUtils.setTcpFlagsOveride(interBuf, 0);
			TcpUtils.setTcpAck(interBuf, 0);

			return sendSegment(socketOther, interBuf);
		}
	}

	public synchronized static boolean sendPackMsg(byte[] rawIpPacket,
			ByteBuffer optionDataBuffer) {
		return sendPackMsg(rawIpPacket, -1, optionDataBuffer);
	}

	public synchronized static boolean sendPackMsg(byte[] rawIpPacket,
			long tcpSeq, ByteBuffer optionDataBuffer) {
		// Get header length from the original packet
		int headersLen = TcpUtils.getCombinedHeadersLen(rawIpPacket);

		synchronized (interBuf) {
			// Copy headers
			System.arraycopy(rawIpPacket, 0, interBuf, 0, headersLen);

			TcpUtils.setTcpOption(interBuf, PackUtils.OPTION_PACK_MESSAGE,
					optionDataBuffer);

			if (tcpSeq >= 0)
				TcpUtils.setTcpSeq(interBuf, tcpSeq);

			return sendSegment(socketOther, interBuf);
		}
	}

	public synchronized static boolean sendPackPermitted(byte[] rawIpPacket) {
		// Get header length from the original packet
		int headersLen = TcpUtils.getCombinedHeadersLen(rawIpPacket);

		synchronized (interBuf) {
			// Copy headers
			System.arraycopy(rawIpPacket, 0, interBuf, 0, headersLen);

			// Set MSS
			TcpUtils.setTcpOptionMss(interBuf, TcpUtils.TCP_PAYLOAD_SIZE);

			// Remove SACK Permitted
			TcpUtils.removeTcpOption(interBuf, TcpUtils.OPTION_SACK);

			// Set TCP option for "PACK permitted"
			PackUtils.setPackPermitted(interBuf);

			return sendSegment(socketOther, interBuf);
		}
	}
}
