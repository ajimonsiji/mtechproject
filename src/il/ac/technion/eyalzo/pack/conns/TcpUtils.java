package il.ac.technion.eyalzo.pack.conns;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import il.ac.technion.eyalzo.net.IPPacket;
import il.ac.technion.eyalzo.net.TCPPacket;

public class TcpUtils {
	public static final long TCP_SEQ_RANGE = 0x100000000L;
	public static final long MAX_TCP_SEQ = TCP_SEQ_RANGE - 1;
	public static final long HALF_TCP_SEQ_RANGE = TCP_SEQ_RANGE / 2;
	/**
	 * Windows size does not have to be long as it belongs to sender.
	 */
	private static final short WINDOW_SIZE = 1460 * 2;
	/**
	 * TCP header length, minimal without options.
	 */
	public static final int MIN_TCP_HEADER_LEN_BYTES = 20;
	public static final int MAX_TCP_HEADER_LEN_BYTES = 60;
	public static final int IP_HEADER_LEN_BYTES = 20;
	/**
	 * Typical IP packet size, for PACK packets. Also allowed MSS (maximum TCP
	 * payload bytes per packet). To be used in SYN+ACK when PACK is enabled, to
	 * prevent packet loss due to large packets that do not fit into the
	 * Netfilter Queue buffer size (4KB). It was especially added for the lo
	 * interface that uses 16KB on default.
	 */
	public static final int PACKET_SIZE = 1500;
	/**
	 * Typical TCP payload size considering the packet size.
	 */
	public static final int TCP_PAYLOAD_SIZE = PACKET_SIZE
			- IP_HEADER_LEN_BYTES - MIN_TCP_HEADER_LEN_BYTES;
	/**
	 * Minimal IP and TCP headers length together.
	 */
	public static final int COMBINED_HEADERS_LEN = IP_HEADER_LEN_BYTES
			+ MIN_TCP_HEADER_LEN_BYTES;

	//
	// Packet offsets
	//
	public static final int OFFSET_TOTAL_LEN = 2;
	public static final int OFFSET_IP_CHECKSUM = 10;
	public static final int OFFSET_SRC_ADDR = 12;
	public static final int OFFSET_DST_ADDR = 16;
	public static final int OFFSET_SRC_PORT = IP_HEADER_LEN_BYTES + 0;
	public static final int OFFSET_DST_PORT = IP_HEADER_LEN_BYTES + 2;
	public static final int OFFSET_SEQ_NUM = IP_HEADER_LEN_BYTES + 4;
	public static final int OFFSET_ACK = IP_HEADER_LEN_BYTES + 8;
	public static final int OFFSET_TCP_DATA_OFFSET = IP_HEADER_LEN_BYTES + 12;
	public static final int OFFSET_TCP_FLAGS = IP_HEADER_LEN_BYTES + 13;
	public static final int OFFSET_WINDOW_SIZE = IP_HEADER_LEN_BYTES + 14;
	public static final int OFFSET_TCP_CHECKSUM = IP_HEADER_LEN_BYTES + 16;
	public static final int OFFSET_OPTIONS = IP_HEADER_LEN_BYTES + 20;

	//
	// TCP option kind
	//
	public static final int OPTION_EOL = 0;
	public static final int OPTION_NOP = 1;
	public static final int OPTION_MSS = 2;
	public static final int OPTION_SCALING = 3;
	public static final int OPTION_SACK = 4;

	//
	// TCP flags (6 right bits) from left to right: FIN, SYN, RST, PSH, ACK, URG
	//
	public static final byte FLAG_FIN = 0x01;
	public static final byte FLAG_SYN = 0x02;
	public static final byte FLAG_RST = 0x04;
	public static final byte FLAG_PSH = 0x08;
	public static final byte FLAG_ACK = 0x10;
	public static final byte FLAG_URG = 0x20;
	/**
	 * Special flag for PACK messages to bypass iptables forward to queue.
	 */
	public static final byte FLAG_PACK = FLAG_URG;

	/**
	 * @param tcpSequenceBase
	 *            Base TCP sequence.
	 * @param contentLength
	 *            Expected content length.
	 * @return True if one of the input numbers is illegal (negative or too
	 *         large), or their sum will cause an overflow in the 16-bit
	 *         sequence.
	 */
	public static boolean tcpSequenceOverflow(long tcpSequenceBase,
			long contentLength) {
		// Must be checked separately because it may be an illegal number
		if (tcpSequenceBase > MAX_TCP_SEQ || tcpSequenceBase < 0)
			return true;

		// Must be checked separately because it may be an illegal number
		if (contentLength > MAX_TCP_SEQ || contentLength < 0)
			return true;

		// Check for overflow
		return (tcpSequenceBase + contentLength) > MAX_TCP_SEQ;
	}

	/**
	 * @param tcpSequenceBase
	 *            Base TCP sequence.
	 * @param toAdd
	 *            Positive or negative number to add.
	 * @return Sum of the two modulo 16-bit.
	 */
	public static long tcpSequenceAdd(long tcpSequenceBase, long toAdd) {
		long result = tcpSequenceBase + toAdd;

		while (result < 0)
			result += TCP_SEQ_RANGE;

		return result % TCP_SEQ_RANGE;
	}

	/**
	 * Fast and inaccurate check if the given TCP payload starts with "GET" or
	 * "POST".
	 * 
	 * @param data
	 *            Given TCP payload (may start with other headers).
	 * @param startOffset
	 *            Zero-based offset, where the TCP payload actually starts, in
	 *            case the data contains IP header etc.
	 * @return False if given data is too short, or it does not start with "GET"
	 *         or "POST".
	 */
	public static boolean isHttpRequestPrefix(byte[] data, int startOffset) {
		// Safety check
		if (startOffset + 4 > data.length)
			return false;

		// Look for "GET"
		if (data[startOffset] == 'G')
			return data[startOffset + 1] == 'E' && data[startOffset + 2] == 'T';

		// Look for "POST"
		if (data[startOffset] == 'P')
			return data[startOffset + 1] == 'O' && data[startOffset + 2] == 'S'
					&& data[startOffset + 3] == 'T';

		// Not "GET" nor "POST"
		return false;
	}

	/**
	 * Fast and inaccurate check if the given TCP payload starts with
	 * "HTTP/1.1 xxx".
	 * 
	 * @param data
	 *            Given TCP payload (may start with other headers).
	 * @param startOffset
	 *            Zero-based offset, where the TCP payload actually starts, in
	 *            case the data contains IP header etc.
	 * @return 0 if it's too short or not an HTTP response.
	 */
	public static int getHttpResponseCode(byte[] data, int startOffset) {
		// Safety check, for "HTTP/1.1 200"
		if (startOffset + 12 > data.length)
			return 0;

		// Look for "GET"
		boolean prefix = (data[startOffset] == 'H'
				&& data[startOffset + 1] == 'T' && data[startOffset + 2] == 'T'
				&& data[startOffset + 3] == 'P' && data[startOffset + 4] == '/'
				&& data[startOffset + 5] == '1' && data[startOffset + 6] == '.'
				&& data[startOffset + 7] == '1' && data[startOffset + 8] == ' ');

		if (!prefix)
			return 0;

		int result = (data[startOffset + 9] - '0') * 100
				+ (data[startOffset + 10] - '0') * 10
				+ (data[startOffset + 11] - '0');

		return result;
	}

	/**
	 * Fast and inaccurate check if the given TCP payload starts with "HEAD".
	 * 
	 * @param data
	 *            Given TCP payload (may start with other headers).
	 * @param startOffset
	 *            Zero-based offset, where the TCP payload actually starts, in
	 *            case the data contains IP header etc.
	 * @param skipFirstByte
	 *            Skip the first byte, for the case of first packet with one
	 *            byte only. For example, if a former packet started with 'H'.
	 * @return False if given data is too short, or it does not start with
	 *         "HEAD".
	 */
	public static boolean isHttpHeadRequestPrefix(byte[] data, int startOffset,
			boolean skipFirstByte) {
		// Safety check
		if (startOffset + (skipFirstByte ? 3 : 4) > data.length)
			return false;

		if (skipFirstByte)
			return data[startOffset] == 'E' && data[startOffset + 1] == 'A'
					&& data[startOffset + 2] == 'D';

		// Look for "HEAD"
		if (data[startOffset] == 'H')
			return data[startOffset + 1] == 'E' && data[startOffset + 2] == 'A'
					&& data[startOffset + 3] == 'D';

		// Not "HEAD"
		return false;
	}

	/**
	 * Fast and inaccurate check if the given TCP payload starts with "GET" or
	 * "POST".
	 * 
	 * @param data
	 *            Given TCP payload (may start with other headers).
	 * @param startOffset
	 *            Zero-based offset, where the TCP payload actually starts, in
	 *            case the data contains IP header etc.
	 * @return Null if there is a problem with the request, or the URL
	 *         otherwise, starting with a slash.
	 */
	public static String getUrlFromHttpPayload(byte[] data, int startOffset) {
		// Safety check
		if (data.length < 16)
			return null;

		// Where the URL is supposed to start, or a space if it's a POST request
		int urlStartOffset = startOffset + 4;
		byte c = data[urlStartOffset];
		// POST or HEAD ?
		if (c == ' ') {
			urlStartOffset++;
			c = data[urlStartOffset];
		}
		// It must start with a slash
		if (c != '/')
			return null;

		for (int urlEndOffset = urlStartOffset + 1; urlEndOffset < data.length; urlEndOffset++) {
			c = data[urlEndOffset];

			// Space ?
			if (c == ' ')
				return new String(data, urlStartOffset, urlEndOffset
						- urlStartOffset);

			// Forbidden character or a new-line ?
			if (c > 0 && c < ' ')
				return null;
		}

		return null;
	}

	/**
	 * Computes the difference between two TCP sequence numbers, assuming that
	 * difference cannot be higher than half the maximum sequence (32 bit).
	 * 
	 * @param small
	 *            The smaller sequence.
	 * @param big
	 *            The bigger sequence.
	 * @return Positive if the expected bigger is not smaller, or is over half
	 *         range bigger.
	 */
	public static long tcpSequenceDiff(long small, long big) {
		long diff = big - small;

		if (diff == 0)
			return 0;

		// Positive
		if (diff > 0) {
			if (diff < (MAX_TCP_SEQ / 2))
				return diff;

			return -(MAX_TCP_SEQ + 1 - big + small);
		}

		// Negative
		if (diff > ((-MAX_TCP_SEQ) / 2)) {
			return diff;
		}

		// Negative but should be reversed
		return MAX_TCP_SEQ - small + 1 + big;
	}

	/**
	 * 
	 * @param expectedSeq
	 *            Expected TCP sequence.
	 * @param tcpSeq
	 *            Actual TCP sequence.
	 * @param maxBackwardAllowed
	 *            Maximum allowed difference.
	 * @return True if the actual TCP sequence has a backward difference that is
	 *         larger than the allowed.
	 */
	public static boolean tcpSequenceBackward(long expectedSeq, long tcpSeq,
			long maxBackwardAllowed) {
		// Sanity, because if half range is allowed it cannot distinguish
		// backward and forward
		if (maxBackwardAllowed >= HALF_TCP_SEQ_RANGE || maxBackwardAllowed < 0)
			return false;

		long diff = tcpSequenceDiff(expectedSeq, tcpSeq);
		if (diff >= 0)
			return false;

		// Negative means backward
		return -diff > maxBackwardAllowed;
	}

	/**
	 * 
	 * @param expectedSeq
	 *            Expected TCP sequence.
	 * @param tcpSeq
	 *            Actual TCP sequence.
	 * @param maxForwardAllowed
	 *            Maximum allowed difference.
	 * @return True if the actual TCP sequence has a forward difference that is
	 *         larger than the allowed.
	 */
	public static boolean tcpSequenceForward(long expectedSeq, long tcpSeq,
			long maxForwardAllowed) {
		// Sanity, because if half range is allowed it cannot distinguish
		// backward and forward
		if (maxForwardAllowed >= HALF_TCP_SEQ_RANGE || maxForwardAllowed < 0)
			return false;

		long diff = tcpSequenceDiff(expectedSeq, tcpSeq);
		if (diff <= 0)
			return false;

		// Positive means forward
		return diff > maxForwardAllowed;
	}

	/**
	 * Check if a given TCP sequence is within a range of a TCP sequence. This
	 * is an inclusive match meaning that a range with size 1 is matched only if
	 * the sequence numbers are identical.
	 * 
	 * @return True if the given TCP sequence is in the range (inclusive).
	 */
	public static boolean tcpSequenceInRange(long tcpSeq, long rangeSeqStart,
			long rangeLength) {
		// Sanity, because if half range is allowed it cannot distinguish
		// backward and forward
		if (rangeLength >= HALF_TCP_SEQ_RANGE || rangeLength < 0)
			return false;

		long diff = tcpSequenceDiff(rangeSeqStart, tcpSeq);

		return diff >= 0 && diff < rangeLength;
	}

	/**
	 * Calculate how many bytes overlap between two ranges.
	 * 
	 * @return Zero on range-length error or if there is no overlap.
	 */
	public static long tcpSequenceRangeOverlap(long seqStart1, long len1,
			long seqStart2, long len2) {
		// Make sure 1 do not start after 2
		if (seqStart1 > seqStart2)
			return tcpSequenceRangeOverlap(seqStart2, len2, seqStart1, len1);

		// Sanity, because if half range is allowed it cannot distinguish
		// backward and forward
		if (len1 >= HALF_TCP_SEQ_RANGE || len1 < 0
				|| len2 >= HALF_TCP_SEQ_RANGE || len2 < 0)
			return 0;

		long startDiff = tcpSequenceDiff(seqStart1, seqStart2);
		if (startDiff >= len1)
			return 0;

		long seqEnd1 = tcpSequenceAdd(seqStart1, len1 - 1);
		long seqEnd2 = tcpSequenceAdd(seqStart2, len2 - 1);
		long endDiff = tcpSequenceDiff(seqEnd1, seqEnd2);
		if (endDiff > 0)
			return len1 - startDiff;

		// Negative means that 1 contains 2
		return len2;
	}

	/**
	 * @param rawIpPacket
	 *            The entire IP packet, starting from the first byte of the IP
	 *            header.
	 * @return Window size scaling as number of bits to shift (exponent), or
	 *         zero if this option field was not found.
	 */
	public static int getTcpOptionWindowScaling(byte[] rawIpPacket) {
		return (int) getTcpOptionAsLong(rawIpPacket, OPTION_SCALING);
	}

	/**
	 * @param data
	 *            The entire IP packet, starting from the first byte of the IP
	 *            header.
	 * @return Maximum segment size (MSS), or zero if this option field was not
	 *         found.
	 */
	public static int getTcpOptionMss(byte[] rawIpPacket) {
		return (int) getTcpOptionAsLong(rawIpPacket, OPTION_MSS);
	}

	/**
	 * @param rawIpPacket
	 *            The entire IP packet, starting from the first byte of the IP
	 *            header.
	 * @param optionKind
	 *            According to TCP specifications. For example: 2=MSS, 4=SACK,
	 *            3=Scaling.
	 * @return Window size scaling as number of bits to shift (exponent), or
	 *         zero if this option field was not found.
	 */
	public static long getTcpOptionAsLong(byte[] rawIpPacket, int optionKind) {
		if (optionKind <= 1)
			return 0;

		// Total length of headers what may include options and padding
		int headersLen = IP_HEADER_LEN_BYTES
				+ getTcpHeaderBytesLen(rawIpPacket);

		// If too short, meaning that there are no options
		int offsetOptions = OFFSET_OPTIONS;
		if (headersLen <= offsetOptions)
			return 0;

		// Walk through all the options (last single byte is not interesting)
		for (int offset = offsetOptions; offset < (headersLen - 1);) {
			int curKind = rawIpPacket[offset];

			// End-of-list
			if (curKind == OPTION_EOL)
				return 0;

			// No operation
			if (curKind == OPTION_NOP) {
				offset++;
				continue;
			}

			//
			// Length
			//
			int curLen = rawIpPacket[offset + 1];

			// If there is an error in the length field
			if (curLen <= 0 || offset + curLen > headersLen)
				return 0;

			if (curKind != optionKind) {
				offset += curLen;
				continue;
			}

			// Found the kind, now verify that length is suitable for long value
			if (curLen > 9)
				return 0;

			//
			// Value loop
			//
			long result = 0;
			int maxOffset = offset + curLen;
			for (offset += 2; offset < maxOffset; offset++) {
				result <<= 8;
				result += 0x00ff & rawIpPacket[offset];
			}

			return result;
		}

		return 0;
	}

	/**
	 * @param rawIpPacket
	 *            The entire IP packet, starting from the first byte of the IP
	 *            header.
	 * @param optionKind
	 *            According to TCP specifications. For example: 2=MSS, 4=SACK,
	 *            3=Scaling.
	 * @return True if the TCP option was found.
	 */
	public static boolean hasTcpOption(byte[] rawIpPacket, int optionKind) {
		if (optionKind <= 1)
			return false;

		// Total length of headers what may include options and padding
		int headersLen = IP_HEADER_LEN_BYTES
				+ getTcpHeaderBytesLen(rawIpPacket);

		// If too short, meaning that there are no options
		int offsetOptions = OFFSET_OPTIONS;
		if (headersLen <= offsetOptions)
			return false;

		// Walk through all the options (last single byte is not interesting)
		for (int offset = offsetOptions; offset < (headersLen - 1);) {
			int curKind = rawIpPacket[offset];

			// End-of-list
			if (curKind == OPTION_EOL)
				return false;

			// No operation
			if (curKind == OPTION_NOP) {
				offset++;
				continue;
			}

			//
			// Length
			//
			int curLen = rawIpPacket[offset + 1];

			// If there is an error in the length field
			if (curLen <= 0 || offset + curLen > headersLen)
				return false;

			if (curKind != optionKind) {
				offset += curLen;
				continue;
			}

			// Found the kind
			return true;
		}

		return false;
	}

	/**
	 * @param tcpPacket
	 *            TCP packet, already synchronized with the data parameter, to
	 *            provide length methods etc.
	 * @param rawIpPacket
	 *            The entire IP packet, starting from the first byte of the IP
	 *            header.
	 * @param optionKind
	 *            According to TCP specifications. For example: 2=MSS, 4=SACK,
	 *            3=Scaling.
	 * @return Null if that option was not found, or byte array with the content
	 *         excluding the kind and length.
	 */
	public static byte[] getTcpOptionAsBytes(TCPPacket tcpPacket,
			byte[] rawIpPacket, int optionKind) {
		if (optionKind <= 1)
			return null;

		int headersLen = tcpPacket.getCombinedHeaderByteLength();

		// If too short, meaning that there are no options
		int offsetOptions = OFFSET_OPTIONS;
		if (headersLen <= offsetOptions)
			return null;

		// Walk through all the options (last single byte is not interesting)
		for (int offset = offsetOptions; offset < (headersLen - 1);) {
			int curKind = rawIpPacket[offset];

			// End-of-list
			if (curKind == OPTION_EOL)
				return null;

			// No operation
			if (curKind == OPTION_NOP) {
				offset++;
				continue;
			}

			//
			// Length
			//
			int curLen = rawIpPacket[offset + 1];

			// If there is an error in the length field
			if (curLen <= 0 || offset + curLen > headersLen)
				return null;

			if (curKind != optionKind) {
				offset += curLen;
				continue;
			}

			byte[] result = new byte[curLen];
			System.arraycopy(rawIpPacket, offset + 2, result, 0, curLen);
			return result;
		}

		return null;
	}

	/**
	 * @param tcpPacket
	 *            TCP packet, already synchronized with the data parameter, to
	 *            provide length methods etc.
	 * @param rawIpPacket
	 *            The entire IP packet, starting from the first byte of the IP
	 *            header.
	 * @param optionKind
	 *            According to TCP specifications. For example: 2=MSS, 4=SACK,
	 *            3=Scaling.
	 * @return Null if that option was not found, or buffer wrapping the given
	 *         raw IP packet. The position excludes the TCP option kind and
	 *         length, and the limit covers the entire TCP header.
	 */
	public static ByteBuffer getTcpOptionAsByteBuffer(TCPPacket tcpPacket,
			byte[] rawIpPacket, int optionKind) {
		if (optionKind <= 1)
			return null;

		int headersLen = tcpPacket.getCombinedHeaderByteLength();

		// If too short, meaning that there are no options
		int offsetOptions = OFFSET_OPTIONS;
		if (headersLen <= offsetOptions)
			return null;

		// Walk through all the options (last single byte is not interesting)
		for (int offset = offsetOptions; offset < (headersLen - 1);) {
			int curKind = rawIpPacket[offset];

			// End-of-list
			if (curKind == OPTION_EOL)
				return null;

			// No operation
			if (curKind == OPTION_NOP) {
				offset++;
				continue;
			}

			//
			// Length
			//
			int curLen = rawIpPacket[offset + 1];

			// If there is an error in the length field
			if (curLen <= 0 || offset + curLen > headersLen)
				return null;

			if (curKind != optionKind) {
				offset += curLen;
				continue;
			}

			return ByteBuffer.wrap(rawIpPacket, offset + 2, curLen);
		}

		return null;
	}

	/**
	 * @return Byte-buffer for IP packets (or even Ethernet frames) that carry
	 *         minimal TCP segment with RST. Allocates 2000 bytes, includes 20
	 *         bytes for IP header and 20 bytes for TCP header. Addresses and
	 *         ports are zero. No fragmentation. Large TTL (50).
	 */
	public static ByteBuffer initBufferReset() {
		ByteBuffer buffer = ByteBuffer.allocate(2000);

		//
		// IP
		//

		// Version (4) and IP header length in 32-bit words (5)
		buffer.put((byte) 0x45);

		// Type of service (not used)
		buffer.put((byte) 0);

		// Total length which is 20 IP header + 20 for TCP header
		buffer
				.putShort((short) (TcpUtils.IP_HEADER_LEN_BYTES + TcpUtils.MIN_TCP_HEADER_LEN_BYTES));

		// Identification
		buffer.putShort((short) 0x0);

		// Flags (don't fragment)
		buffer.put((byte) 0x40);

		// Fragmentation offset
		buffer.put((byte) 0);

		// TTL
		buffer.put((byte) 50);

		// Protocol
		buffer.put((byte) 6);

		// Checksum
		buffer.putShort((short) 0);

		// Source address
		byte[] emptyAddr = new byte[4];
		buffer.put(emptyAddr);

		// Destination address
		buffer.put(emptyAddr);

		//
		// TCP
		//

		// Source port
		buffer.putShort((short) 0);

		// Destination port
		buffer.putShort((short) 0);

		// Sequence number
		buffer.putInt((int) 0);

		// Ack number
		buffer.putInt((int) 0);

		// Data offset in 32-bit words (4 left bits)
		buffer.put((byte) ((5 << 4) & 0xf0));

		// TCP flags (6 right bits) from left to right: FIN, SYN, RST, PSH, ACK,
		// URG
		buffer.put((byte) 0x04);

		// Window size
		buffer.putShort((short) 0);

		// Checksum
		buffer.putShort((short) 0);

		// Urgent pointer
		buffer.putShort((short) 0);

		buffer.limit(TcpUtils.IP_HEADER_LEN_BYTES
				+ TcpUtils.MIN_TCP_HEADER_LEN_BYTES);

		return buffer;
	}

	/**
	 * @param includeEthHeader
	 *            If true then an Ethernet header will be added (14 bytes
	 *            prefix), with empty addresses and a flag for IP.
	 * @return Byte-buffer for packets that carry minimal TCP segment with
	 *         FIN+ACK. Allocates 2000 bytes, 20 bytes for IP header and 20
	 *         bytes for TCP header. Addresses and ports are zero. No
	 *         fragmentation. Large TTL (50).
	 */
	public static ByteBuffer initBufferFinAck() {
		ByteBuffer buffer = initBufferReset();

		// TCP flags (6 right bits) from left to right: FIN, SYN, RST, PSH, ACK,
		// URG
		buffer.position(TcpUtils.OFFSET_TCP_FLAGS);
		buffer.put((byte) (FLAG_FIN | FLAG_ACK));

		return buffer;
	}

	/**
	 * @param includeEthHeader
	 *            If true then an Ethernet header will be added (14 bytes
	 *            prefix), with empty addresses and a flag for IP.
	 * @return Byte-buffer for packets that carry minimal TCP segment with
	 *         SYN+ACK. Allocates 2000 bytes, 20 bytes for IP header and 20
	 *         bytes for TCP header. Addresses and ports are zero. No
	 *         fragmentation. Large TTL (50).
	 */
	public static ByteBuffer initBufferSynAck() {
		ByteBuffer buffer = initBufferReset();

		// TCP flags (6 right bits) from left to right: FIN, SYN, RST, PSH, ACK,
		// URG
		buffer.position(TcpUtils.OFFSET_TCP_FLAGS);
		buffer.put((byte) (FLAG_SYN | FLAG_ACK));
		// Window size
		buffer.position(TcpUtils.OFFSET_WINDOW_SIZE);
		buffer.putShort((short) WINDOW_SIZE);

		return buffer;
	}

	/**
	 * @return Byte-buffer for packets that carry minimal TCP segment with ACK.
	 *         Allocates 2000 bytes, 20 bytes for IP header and 20 bytes for TCP
	 *         header. Addresses and ports are zero. Large TTL (50).
	 */
	public static ByteBuffer initBufferAck() {
		ByteBuffer buffer = initBufferReset();

		// TCP flags (6 right bits) from left to right: FIN, SYN, RST, PSH, ACK,
		// URG
		buffer.position(TcpUtils.OFFSET_TCP_FLAGS);
		buffer.put(FLAG_ACK);
		// Window size
		buffer.position(TcpUtils.OFFSET_WINDOW_SIZE);
		buffer.putShort((short) WINDOW_SIZE);

		return buffer;
	}

	/**
	 * @return Byte-buffer for packets that carry minimal TCP segment with no
	 *         flags. Allocates 2000 bytes, 20 bytes for IP header and 20 bytes
	 *         for TCP header. Addresses and ports are zero. Large TTL (50).
	 */
	public static ByteBuffer initBufferNoFlags() {
		ByteBuffer buffer = initBufferAck();

		// TCP flags (6 right bits) from left to right: FIN, SYN, RST, PSH, ACK,
		// URG
		buffer.position(TcpUtils.OFFSET_TCP_FLAGS);
		buffer.put((byte) 0);

		return buffer;
	}

	/**
	 * Get IP header length in bytes. Can be from 20 (minimal IP header length)
	 * and up to 60 because it is expressed with 4 bits to be multiplied by 4
	 * (32-bit units).
	 * 
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 * @return Length of IP header in bytes, or zero on error.
	 */
	public static int getIpHeaderBytesLen(byte[] rawIpPacket) {
		// Minimum IHL is 5 (units of 32 bits)
		if (rawIpPacket.length < IP_HEADER_LEN_BYTES)
			return 0;

		int result = (rawIpPacket[0] & 0x0f) * 4;
		if (result < IP_HEADER_LEN_BYTES)
			return 0;

		return result;
	}

	/**
	 * @return Zero on offset error or long value made of 4 bytes.
	 */
	private static long get4BytesAsLong(byte[] rawIpPacket, int offset) {
		// Minimum is offset plus 4 bytes for the value
		if (rawIpPacket.length < offset + 4)
			return 0;

		long result = ((rawIpPacket[offset] & 0x00ffL) << 24)
				+ ((rawIpPacket[offset + 1] & 0x00ffL) << 16)
				+ ((rawIpPacket[offset + 2] & 0x00ffL) << 8)
				+ (rawIpPacket[offset + 3] & 0x00ffL);

		return result;
	}

	/**
	 * @return Zero on offset error or long value made of 4 bytes.
	 */
	private static int get2BytesAsInt(byte[] rawIpPacket, int offset) {
		// Minimum is offset plus 2 bytes for the value
		if (rawIpPacket.length < offset + 2)
			return 0;

		int result = ((rawIpPacket[offset] & 0x00ff) << 8)
				+ (rawIpPacket[offset + 1] & 0x00ff);

		return result;
	}

	private static void set4Bytes(byte[] rawIpPacket, int offset, long value) {
		// Minimum is offset plus 4 bytes for the value
		if (rawIpPacket.length < offset + 4)
			return;

		rawIpPacket[offset] = (byte) ((value >> 24) & 0x00ffL);
		rawIpPacket[offset + 1] = (byte) ((value >> 16) & 0x00ffL);
		rawIpPacket[offset + 2] = (byte) ((value >> 8) & 0x00ffL);
		rawIpPacket[offset + 3] = (byte) (value & 0x00ffL);
	}

	private static void set2Bytes(byte[] rawIpPacket, int offset, long value) {
		// Minimum is offset plus 2 bytes for the value
		if (rawIpPacket.length < offset + 2)
			return;

		rawIpPacket[offset] = (byte) ((value >> 8) & 0x00ffL);
		rawIpPacket[offset + 1] = (byte) (value & 0x00ffL);
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 * @return TCP ACK.
	 */
	public static long getTcpAck(byte[] rawIpPacket) {
		return get4BytesAsLong(rawIpPacket, OFFSET_ACK);
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 * @return Destination port.
	 */
	public static int getTcpDestinationPort(byte[] rawIpPacket) {
		return get2BytesAsInt(rawIpPacket, OFFSET_DST_PORT);
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 * @return TCP sequence.
	 */
	public static long getTcpSeq(byte[] rawIpPacket) {
		return get4BytesAsLong(rawIpPacket, OFFSET_SEQ_NUM);
	}

	/**
	 * 
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 * @return Length of TCP header in bytes, or zero on error.
	 */
	public static int getTcpHeaderBytesLen(byte[] rawIpPacket) {
		int result = ((rawIpPacket[OFFSET_TCP_DATA_OFFSET] & 0xf0) >> 4) * 4;
		if (result < MIN_TCP_HEADER_LEN_BYTES)
			return 0;

		return result;
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 * @return IP address as 4 bytes, directly from the IP header.
	 */
	public static byte[] getIpDestinationAddr(byte[] rawIpPacket) {
		byte[] result = new byte[IPPacket.LENGTH_DESTINATION_ADDRESS];
		System.arraycopy(rawIpPacket, IPPacket.OFFSET_DESTINATION_ADDRESS,
				result, 0, IPPacket.LENGTH_DESTINATION_ADDRESS);
		return result;
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 */
	public static boolean setIpDestinationAddr(byte[] rawIpPacket, byte[] addr) {
		if (addr.length != IPPacket.LENGTH_DESTINATION_ADDRESS)
			return false;

		System.arraycopy(addr, IPPacket.OFFSET_DESTINATION_ADDRESS,
				rawIpPacket, IPPacket.LENGTH_DESTINATION_ADDRESS, addr.length);

		return true;
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 */
	public static boolean setIpSourceAddr(byte[] rawIpPacket, byte[] addr) {
		if (addr.length != IPPacket.LENGTH_SOURCE_ADDRESS)
			return false;

		System.arraycopy(addr, IPPacket.OFFSET_SOURCE_ADDRESS, rawIpPacket,
				IPPacket.LENGTH_SOURCE_ADDRESS, addr.length);

		return true;
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 */
	public static void setTcpSourcePort(byte[] rawIpPacket, int port) {
		set2Bytes(rawIpPacket, OFFSET_SRC_PORT, port);
	}

	/**
	 * @param rawIpPacket
	 *            Raw IP packet, starting from the first bit.
	 */
	public static void setTcpDestinationPort(byte[] rawIpPacket, int port) {
		set2Bytes(rawIpPacket, OFFSET_DST_PORT, port);
	}

	/**
	 * Set TCP option. If the same option is found then it tries to override
	 * (same size) or set NOP (different size).
	 * 
	 * @param rawIpPacket
	 *            IP packet with IP and TCP headers. It must have enough room
	 *            for the extra bytes and no TCP payload.
	 * @param optionData
	 *            To be used from position to limit. Can be null or empty if no
	 *            data needs to be written.
	 */
	public static boolean setTcpOption(byte[] rawIpPacket, int optionKind,
			ByteBuffer optionData) {
		// Total length of headers what may include options and padding
		int headersLen = IP_HEADER_LEN_BYTES
				+ getTcpHeaderBytesLen(rawIpPacket);

		// If too short, meaning that there are no options
		int offsetOptions = OFFSET_OPTIONS;
		if (headersLen < offsetOptions)
			return false;

		int optionDataLen = optionData == null ? 0 : optionData.remaining();

		// Walk through all the options
		int nextOptionOffset = offsetOptions;
		for (int offset = offsetOptions; offset < headersLen;) {
			int curKind = rawIpPacket[offset];

			// End-of-list
			if (curKind == OPTION_EOL) {
				nextOptionOffset = offset;
				break;
			}

			// No operation
			if (curKind == OPTION_NOP) {
				offset++;
				continue;
			}

			//
			// Length
			//
			int curLen = rawIpPacket[offset + 1];

			// If there is an error in the length field
			if (curLen <= 0 || offset + curLen > headersLen)
				return false;

			// Same option, so try to override
			if (curKind == optionKind) {
				// Same size ?
				if (optionData == null && curLen == 2 || optionData != null
						&& curLen == optionData.remaining() + 2) {
					if (optionDataLen > 0) {
						optionData.get(rawIpPacket, offset + 2, optionDataLen);
					}
					// Quit here because there is nothing more to update
					return true;
				}

				// Different size, so erase by writing NOP instead
				for (int i = 0; i < curLen; i++)
					rawIpPacket[offset + i] = OPTION_NOP;
			}

			offset += curLen;
			nextOptionOffset = offset;
		}

		//
		// Set the new option
		//
		rawIpPacket[nextOptionOffset] = (byte) optionKind;
		rawIpPacket[nextOptionOffset + 1] = (byte) (optionDataLen + 2);
		if (optionDataLen > 0) {
			optionData.get(rawIpPacket, nextOptionOffset + 2, optionDataLen);
		}

		// Point to the next option
		nextOptionOffset += 2 + optionDataLen;

		//
		// Padding
		//
		int rem = nextOptionOffset % 4;
		if (rem != 0) {
			for (int i = 3 - rem; i >= 0; i--) {
				rawIpPacket[nextOptionOffset + i] = OPTION_NOP;
			}
		}

		//
		// Set TCP header length
		//
		int tcpHeaderLen = nextOptionOffset + (rem == 0 ? 0 : 4 - rem)
				- IP_HEADER_LEN_BYTES;
		TcpUtils.setTcpHeaderLen(rawIpPacket, tcpHeaderLen);
		TcpUtils.setIpTotalLen(rawIpPacket, IP_HEADER_LEN_BYTES + tcpHeaderLen);

		return true;
	}

	/**
	 * Remove TCP option by replacing it with NOP.
	 * 
	 * @param rawIpPacket
	 *            IP packet with IP and TCP headers. It must have enough room
	 *            for the extra bytes and no TCP payload.
	 */
	public static boolean removeTcpOption(byte[] rawIpPacket, int optionKind) {
		// Total length of headers what may include options and padding
		int headersLen = IP_HEADER_LEN_BYTES
				+ getTcpHeaderBytesLen(rawIpPacket);

		// If too short, meaning that there are no options
		int offsetOptions = OFFSET_OPTIONS;
		if (headersLen < offsetOptions)
			return false;

		// Walk through all the options
		for (int offset = offsetOptions; offset < headersLen;) {
			int curKind = rawIpPacket[offset];

			// End-of-list
			if (curKind == OPTION_EOL) {
				break;
			}

			// No operation
			if (curKind == OPTION_NOP) {
				offset++;
				continue;
			}

			//
			// Length
			//
			int curLen = rawIpPacket[offset + 1];

			// If there is an error in the length field
			if (curLen <= 0 || offset + curLen > headersLen)
				return false;

			// Same option, so try to override
			if (curKind == optionKind) {
				// Erase by writing NOP instead
				for (int i = 0; i < curLen; i++)
					rawIpPacket[offset + i] = OPTION_NOP;
			}

			offset += curLen;
		}

		return true;
	}

	private static void setTcpHeaderLen(byte[] rawIpPacket, int tcpHeaderLen) {
		// One byte
		int curVal = rawIpPacket[OFFSET_TCP_DATA_OFFSET] & 0xff;
		int newVal = ((tcpHeaderLen / 4) << 4) | (curVal & 0x0f);
		rawIpPacket[OFFSET_TCP_DATA_OFFSET] = (byte) newVal;
	}

	public static void setIpTotalLen(byte[] rawIpPacket, int ipTotalLen) {
		set2Bytes(rawIpPacket, OFFSET_TOTAL_LEN, ipTotalLen);
	}

	public static int getIpTotalLen(byte[] rawIpPacket) {
		return ((rawIpPacket[IPPacket.OFFSET_TOTAL_LENGTH] & 0x00ff) << 8)
				+ (rawIpPacket[IPPacket.OFFSET_TOTAL_LENGTH + 1] & 0x00ff);
	}

	/**
	 * @return Zero on error.
	 */
	public static int getTcpPaylodLen(byte[] rawIpPacket) {
		int result = getIpTotalLen(rawIpPacket)
				- getIpHeaderBytesLen(rawIpPacket)
				- getTcpHeaderBytesLen(rawIpPacket);
		if (result < 0
				|| result > (rawIpPacket.length - IP_HEADER_LEN_BYTES - MIN_TCP_HEADER_LEN_BYTES))
			return 0;
		return result;
	}

	/**
	 * Set the MSS (maximum TCP payload bytes per packet). To be used in SYN+ACK
	 * when PACK is enabled, to prevent packet loss due to large packets that do
	 * not fit into the Netfilter Queue buffer size (4KB). It was especially
	 * added for the lo interface that uses 16KB on default.
	 */
	public static void setTcpOptionMss(byte[] rawIpPacket, int mss) {
		long currentMss = getTcpOptionAsLong(rawIpPacket, OPTION_MSS);
		if (currentMss > 0 && currentMss <= mss)
			return;

		ByteBuffer optionData = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
		optionData.putShort((short) mss);
		optionData.flip();

		TcpUtils.setTcpOption(rawIpPacket, OPTION_MSS, optionData);
	}

	/**
	 * @param buffer
	 *            Target buffer with enough space and proper limit. The position
	 *            is not changed.
	 */
	public void setAddressesAndSeq(ByteBuffer buffer, InetAddress srcIp,
			int srcPort, InetAddress dstIp, int dstPort, int srcSequence,
			int dstAck) {
		int prevPos = buffer.position();

		// Source address
		buffer.position(TcpUtils.OFFSET_SRC_ADDR);
		buffer.put(srcIp.getAddress());

		// Destination address
		buffer.put(dstIp.getAddress());

		// Source port
		buffer.position(TcpUtils.OFFSET_SRC_PORT);
		buffer.putShort((short) srcPort);

		// Destination port
		buffer.putShort((short) dstPort);

		// Sequence number
		buffer.putInt(srcSequence);

		// Ack
		buffer.putInt(dstAck);

		buffer.position(prevPos);
	}

	/**
	 * @param buffer
	 *            Target buffer with enough space and proper limit. The position
	 *            is not changed.
	 */
	public static void setAddresses(ByteBuffer buffer, byte[] srcIp,
			int srcPort, byte[] dstIp, int dstPort) {
		int prevPos = buffer.position();

		// Source address
		buffer.position(TcpUtils.OFFSET_SRC_ADDR);
		buffer.put(srcIp);

		// Destination address
		buffer.put(dstIp);

		// Source port
		buffer.position(TcpUtils.OFFSET_SRC_PORT);
		buffer.putShort((short) srcPort);

		// Destination port
		buffer.putShort((short) dstPort);

		buffer.position(prevPos);
	}

	/**
	 * 
	 * @param buffer
	 *            Target buffer with enough space and proper limit. The position
	 *            is not changed. Assumes big-endian buffer.
	 */
	public static void setAddresses(ByteBuffer buffer, int srcIp, int srcPort,
			int dstIp, int dstPort) {
		int prevPos = buffer.position();

		// Source address
		buffer.position(TcpUtils.OFFSET_SRC_ADDR);
		buffer.putInt(srcIp);

		// Destination address
		buffer.putInt(dstIp);

		// Source port
		buffer.position(TcpUtils.OFFSET_SRC_PORT);
		buffer.putShort((short) srcPort);

		// Destination port
		buffer.putShort((short) dstPort);

		buffer.position(prevPos);
	}

	public static int getCombinedHeadersLen(byte[] rawIpPacket) {
		return getIpHeaderBytesLen(rawIpPacket)
				+ getTcpHeaderBytesLen(rawIpPacket);
	}

	public static void setTcpFlagsOveride(byte[] rawIpPacket, int flags) {
		rawIpPacket[OFFSET_TCP_FLAGS] = (byte) (0x3f & flags);
	}

	public static void setTcpFlagsMask(byte[] rawIpPacket, int flags) {
		rawIpPacket[OFFSET_TCP_FLAGS] = (byte) (rawIpPacket[OFFSET_TCP_FLAGS] | (0x3f & flags));
	}

	/**
	 * Remove all TCP options by setting the header length to the minimum.
	 * 
	 * @param rawIpPacket
	 */
	public static void resetTcpOptions(byte[] rawIpPacket) {
		setTcpHeaderLen(rawIpPacket, MIN_TCP_HEADER_LEN_BYTES);
	}

	public static void setTcpAck(byte[] rawIpPacket, long tcpAck) {
		set4Bytes(rawIpPacket, OFFSET_ACK, tcpAck);
	}

	public static void setTcpSeq(byte[] rawIpPacket, long tcpSeq) {
		set4Bytes(rawIpPacket, OFFSET_SEQ_NUM, tcpSeq);
	}

	/**
	 * Swap source and destination IPs and ports in place.
	 */
	public static void swapAddresses(byte[] rawIpPacket) {
		// IP addresses
		for (int i = 0; i < 4; i++) {
			byte t = rawIpPacket[OFFSET_DST_ADDR + i];
			rawIpPacket[OFFSET_DST_ADDR + i] = rawIpPacket[OFFSET_SRC_ADDR + i];
			rawIpPacket[OFFSET_SRC_ADDR + i] = t;
		}

		// TCP ports
		for (int i = 0; i < 2; i++) {
			byte t = rawIpPacket[OFFSET_DST_PORT + i];
			rawIpPacket[OFFSET_DST_PORT + i] = rawIpPacket[OFFSET_SRC_PORT + i];
			rawIpPacket[OFFSET_SRC_PORT + i] = t;
		}
	}

	/**
	 * @param windowSize
	 *            No scaling.
	 */
	public static void setWindowSize(byte[] rawIpPacket, int windowSize) {
		set2Bytes(rawIpPacket, OFFSET_WINDOW_SIZE, windowSize);
	}

	/**
	 * Takes a data packet and swaps the sequence and acknowledgment numbers in
	 * place. The acknowledgment takes into account the payload size. The
	 * operation must be completed with a shortening of the packet to remove the
	 * data.
	 * 
	 * @param rawIpPacketSender
	 *            IP packet with IP and TCP headers that carries data from
	 *            sender. It is also the target meaning that changes are
	 *            performed in place.
	 */
	public static void swapSeqAndAck(byte[] rawIpPacketSender) {
		// Current values
		int payloadLen = getTcpPaylodLen(rawIpPacketSender);
		long curSeq = getTcpSeq(rawIpPacketSender);
		long curAck = getTcpAck(rawIpPacketSender);

		// New values
		long ack = tcpSequenceAdd(curSeq, payloadLen);
		long seq = curAck;

		// Set the new values
		setTcpAck(rawIpPacketSender, ack);
		setTcpSeq(rawIpPacketSender, seq);
	}
}
