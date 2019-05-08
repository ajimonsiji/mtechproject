package il.ac.technion.eyalzo.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class VideoUtils
{
	//
	// Constants
	//
	private static final String		HTTP_RESPONSE_HOST					= "\nHost: ";
	private static final byte[]		HTTP_RESPONSE_HOST_BYTES			= HTTP_RESPONSE_HOST
																				.getBytes();
	private static final int		HTTP_RESPONSE_HOST_LEN				= HTTP_RESPONSE_HOST
																				.length();
	private static final String		HTTP_RESPONSE_REFERER				= "\nReferer: http://";
	private static final int		HTTP_RESPONSE_REFERER_LEN			= HTTP_RESPONSE_REFERER
																				.length();
	private static final String		HTTP_COMMAND_GET_PREFIX				= "GET ";
	private static final int		HTTP_COMMAND_GET_PREFIX_LEN			= HTTP_COMMAND_GET_PREFIX
																				.length();
	private static final String		HTTP_COMMAND_POST_PREFIX			= "POST ";
	private static final int		HTTP_COMMAND_POST_PREFIX_LEN		= HTTP_COMMAND_POST_PREFIX
																				.length();
	private static final String		HTTP_COMMAND_SUFFIX					= " HTTP/1.";
	private static final int		HTTP_COMMAND_SUFFIX_LEN				= HTTP_COMMAND_SUFFIX
																				.length() + 1;
	private static final String		HTTP_RESPONSE_CONTENT_LENGTH		= "\nContent-Length: ";
	private static final byte[]		HTTP_RESPONSE_CONTENT_LENGTH_BYTES	= HTTP_RESPONSE_CONTENT_LENGTH
																				.getBytes();
	private static final int		HTTP_RESPONSE_CONTENT_LENGTH_LEN	= HTTP_RESPONSE_CONTENT_LENGTH
																				.length();
	private static final String		HTTP_RESPONSE_CONTENT_RANGE			= "\nContent-Range: ";
	private static final byte[]		HTTP_RESPONSE_CONTENT_RANGE_BYTES	= HTTP_RESPONSE_CONTENT_RANGE
																				.getBytes();
	private static final int		HTTP_RESPONSE_CONTENT_RANGE_LEN		= HTTP_RESPONSE_CONTENT_RANGE
																				.length();
	private static final String		HTTP_RESPONSE_CONTENT_TYPE			= "\nContent-Type: ";
	private static final byte[]		HTTP_RESPONSE_CONTENT_TYPE_BYTES	= HTTP_RESPONSE_CONTENT_TYPE
																				.getBytes();
	private static final int		HTTP_RESPONSE_CONTENT_TYPE_LEN		= HTTP_RESPONSE_CONTENT_TYPE
																				.length();
	private static final String		HTTP_RESPONSE_ATTACH_FILE1			= "\nContent-Disposition: Attachment; filename=";
	private static final byte[]		HTTP_RESPONSE_ATTACH_FILE1_BYTES	= HTTP_RESPONSE_ATTACH_FILE1
																				.getBytes();
	private static final String		HTTP_RESPONSE_ATTACH_FILE2			= "\nContent-Disposition: attachment; filename=";
	private static final byte[]		HTTP_RESPONSE_ATTACH_FILE2_BYTES	= HTTP_RESPONSE_ATTACH_FILE2
																				.getBytes();
	private static final String		HTTP_RESPONSE_ETAG					= "\nETag: ";
	private static final byte[]		HTTP_RESPONSE_ETAG_BYTES			= HTTP_RESPONSE_ETAG
																				.getBytes();
	private static final byte[]		NEWLINE_BYTES						= "\r"
																				.getBytes();
	private static final byte[]		SLASHN_BYTES						= "\n"
																				.getBytes();
	public static final byte[]		QUOTE_BYTES							= "\""
																				.getBytes();
	private static final Pattern	hostPattern							= Pattern
																				.compile("(([a-z0-9][a-z0-9_-]*)(\\.[a-z0-9][a-z0-9_-]*)+)");
	private static final byte[]		DOUBLE_NEW_LINE_BYTES				= "\r\n\r\n"
																				.getBytes();

	/**
	 * Get the URL part from a TCP payload in HTTP. The first line is expected
	 * to start with "GET" or "POST" and end with "HTTP/1.1" or "HTTP/1.0". The
	 * returned URL may differ from the original, as it may be decoded,
	 * especially when some of the characters are expressed as "%xy" like "%20"
	 * for space etc.
	 * 
	 * @param payload
	 *            The full TCP payload, expected to be in an HTTP format.
	 * @param decode
	 *            True if the URL should be decoded to UTF-8.
	 * @return Full URL from a GET/POST method (optionally decoded to UTF-8), or
	 *         null if no such thing was detected.
	 */
	public static String getUrlFromPayload(String payload, boolean decode)
	{
		if (payload == null)
			return null;

		int httpCommandPrefixLen = 0;
		if (payload.startsWith(HTTP_COMMAND_GET_PREFIX))
		{
			httpCommandPrefixLen = HTTP_COMMAND_GET_PREFIX_LEN;
		}
		else if (payload.startsWith(HTTP_COMMAND_POST_PREFIX))
		{
			httpCommandPrefixLen = HTTP_COMMAND_POST_PREFIX_LEN;
		}
		else
		{
			return null;
		}

		// New line must appear right after "GET ... HTTP/1.x" (x is 0 or 1)
		int newLinePos = payload.indexOf('\r', httpCommandPrefixLen);
		if (newLinePos < 14)
			return null;

		// Make sure the line ends correctly
		int spacePos = newLinePos - HTTP_COMMAND_SUFFIX_LEN;
		if (payload.indexOf(HTTP_COMMAND_SUFFIX, spacePos) != spacePos)
			return null;

		String subStr = payload.substring(httpCommandPrefixLen, spacePos);

		if (decode)
		{
			try
			{
				String result = URLDecoder.decode(subStr, "UTF-8");
				return result;
			}
			// May catch UnsupportedEncodingException and
			// IllegalArgumentException
			catch (Exception e)
			{
			}
		}

		return subStr;
	}

	/**
	 * @return The decoded URL by UTF-8, or the original if something went
	 *         wrong.
	 */
	public static String decodeUrl(String original)
	{
		try
		{
			String result = URLDecoder.decode(original, "UTF-8");
			return result;
		}
		// May catch UnsupportedEncodingException and IllegalArgumentException
		catch (Exception e)
		{
		}

		return original;
	}

	/**
	 * Get the host name from a TCP payload carrying an HTTP header.
	 * <p>
	 * The host name must follow the pattern. It may fail if the TCP payload is
	 * not complete and/or some kind of encoding was used.
	 * 
	 * @return Host name, or null if something went wrong.
	 */
	public static String getHostFromPayload(String payload)
	{
		if (payload == null)
			return null;

		String hostName = null;

		//
		// Get the position where the host name is written
		//
		int hostPos = payload.indexOf(HTTP_RESPONSE_HOST);
		if (hostPos < 0)
			return null;
		hostPos += HTTP_RESPONSE_HOST_LEN;

		//
		// Make sure the host line ends with a new-line, and it contains a
		// minimal number of characters
		//
		int newLinePos = payload.indexOf('\r', hostPos);
		if (newLinePos < (hostPos + 4))
			return null;

		//
		// Isolate the host name
		//
		hostName = payload.substring(hostPos, newLinePos);

		//
		// Remove port number if mentioned
		//
		int portIndex = hostName.indexOf(':');
		if (portIndex > 0)
		{
			hostName = hostName.substring(0, portIndex);
		}

		// Make sure it matches the host name rules
		if (!hostPattern.matcher(hostName).matches())
			return null;

		return hostName;
	}

	/**
	 * @return Length of HTTP header assuming that the header start at the given
	 *         offset, or -1 if the header end was not found.
	 */
	public static int getHttpHeaderLenFromPayloadStart(byte[] data,
			int startOffset)
	{
		// Needed for all the fields we search for in the header
		int newlineOffset = VideoUtils.indexOfBytesInBytes(data,
				DOUBLE_NEW_LINE_BYTES, startOffset, data.length);
		return newlineOffset == -1 ? -1 : newlineOffset
				+ DOUBLE_NEW_LINE_BYTES.length - startOffset;
	}

	/**
	 * Get the content length from a TCP payload carrying an HTTP response.
	 * 
	 * @return Host name, or null if something went wrong.
	 */
	public static long getContentLengthFromPayload(String payload)
	{
		if (payload == null)
			return 0;

		//
		// Get the position where the content length is written
		//
		int fieldPos = payload.indexOf(HTTP_RESPONSE_CONTENT_LENGTH);
		if (fieldPos < 0)
			return 0;
		fieldPos += HTTP_RESPONSE_CONTENT_LENGTH_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = payload.indexOf('\r', fieldPos);
		if (newLinePos < (fieldPos + 1))
			return 0;

		//
		// Isolate the number
		//
		String numString = payload.substring(fieldPos, newLinePos);
		try
		{
			long num = Long.parseLong(numString);
			return num;
		} catch (Exception e)
		{
		}

		return 0;
	}

	/**
	 * Get the content length from a TCP payload carrying an HTTP response.
	 * 
	 * @param payload
	 *            HTTP content payload
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return Content length, or zero if anything went wrong.
	 */
	public static long getContentLengthFromPayload(byte[] payload,
			int startOffset, int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload,
				HTTP_RESPONSE_CONTENT_LENGTH_BYTES, startOffset, endOffset);
		if (fieldPos < 0)
			return 0;

		fieldPos += HTTP_RESPONSE_CONTENT_LENGTH_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 1))
			return 0;

		long result = bytesToLong(payload, fieldPos, newLinePos);
		if (result < 0)
			return 0;

		return result;
	}

	/**
	 * 
	 * Purpose: Get the content range from value from the TCP payload carrying
	 * an HTTP response.
	 * 
	 * @param payload
	 *            HTTP content payload
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return from range, or zero if anything went wrong.
	 */
	public static long getContentRangeFromFromPayload(byte[] payload,
			int startOffset, int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload,
				HTTP_RESPONSE_CONTENT_RANGE_BYTES, startOffset, endOffset);
		if (fieldPos < 0)
			return 0;

		fieldPos += HTTP_RESPONSE_CONTENT_RANGE_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 1))
			return 0;

		// reset the position to the dash char (-)
		int dashPos = indexOfBytesInBytes(payload, "-".getBytes(), fieldPos,
				newLinePos);

		int spacePos = dashPos;
		while (payload[spacePos] != ' ')
			spacePos--;

		long result = bytesToLong(payload, spacePos + 1, dashPos);
		if (result < 0)
			return 0;

		return result;
	}

	/**
	 * 
	 * Purpose: Get the content range to value from the TCP payload carrying an
	 * HTTP response.
	 * 
	 * @param payload
	 *            HTTP content payload
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return to range or total range if not exists, or zero if anything went
	 *         wrong.
	 */
	public static long getContentRangeToFromPayload(byte[] payload,
			int startOffset, int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload,
				HTTP_RESPONSE_CONTENT_RANGE_BYTES, startOffset, endOffset);
		if (fieldPos < 0)
			return 0;

		fieldPos += HTTP_RESPONSE_CONTENT_RANGE_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 1))
			return 0;

		int dashPos = indexOfBytesInBytes(payload, "-".getBytes(), fieldPos,
				newLinePos);
		if (dashPos < 0)
			return 0;

		int slashPos = indexOfBytesInBytes(payload, "/".getBytes(), fieldPos,
				newLinePos);
		if (slashPos < 0)
			return 0;

		long result;

		// try to get the to, if slash and dash are one after the other, the to
		// is equal to the total
		// set it accordingly (this is an impossible scenario in incoming
		// response but can happen in outgoing if controlled)
		if (dashPos == slashPos - 1)
		{
			result = bytesToLong(payload, slashPos + 1, newLinePos);
		}
		else
		{
			result = bytesToLong(payload, dashPos + 1, slashPos);
		}

		if (result < 0)
			return 0;

		return result;
	}

	/**
	 * 
	 * Purpose: Get the content total range length from value from the TCP
	 * payload carrying an HTTP response.
	 * 
	 * @param payload
	 *            HTTP content payload
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return total range length, or zero if anything went wrong.
	 */
	public static long getContentRangeTotalFromPayload(byte[] payload,
			int startOffset, int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload,
				HTTP_RESPONSE_CONTENT_RANGE_BYTES, startOffset, endOffset);
		if (fieldPos < 0)
			return 0;

		fieldPos += HTTP_RESPONSE_CONTENT_RANGE_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 1))
			return 0;

		int slashPos = indexOfBytesInBytes(payload, "/".getBytes(), fieldPos,
				newLinePos);
		if (slashPos < 0)
			return 0;

		long result = bytesToLong(payload, slashPos + 1, newLinePos);
		if (result < 0)
			return 0;

		return result;
	}

	/**
	 * Get the host name ("Host:" field) from a TCP payload carrying an HTTP
	 * response.
	 * 
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return Host name, or null if something went wrong.
	 */
	public static String getHostFromPayload(byte[] payload, int startOffset,
			int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload, HTTP_RESPONSE_HOST_BYTES,
				startOffset, endOffset);
		if (fieldPos < 0)
			return null;

		fieldPos += HTTP_RESPONSE_HOST_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 1))
			return null;

		String result = new String(payload, fieldPos, newLinePos - fieldPos);
		return result;
	}

	/**
	 * 
	 * Purpose: Extract the value of the header from the packet payload (up to
	 * the next new line \r)
	 * 
	 * Notes: This should be called in all the other 'specific' header string
	 * extractions
	 * 
	 * @param payload
	 * @param header
	 * @param startOffset
	 * @param endOffset
	 * @return
	 */
	public static String getHeaderValueFromPayload(byte[] payload,
			byte[] header, int startOffset, int endOffset)
	{
		int fieldPos = indexOfBytesInBytes(payload, header, startOffset,
				endOffset);
		if (fieldPos < 0)
			return null;

		fieldPos += header.length;

		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 1))
			return null;

		String result = new String(payload, fieldPos, newLinePos - fieldPos);
		return result;
	}

	/**
	 * Get the content-type from a TCP payload carrying an HTTP response.
	 * 
	 * @return Content-type or null if not found.
	 */
	public static String getContentTypeFromPayload(byte[] payload,
			int startOffset, int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload,
				HTTP_RESPONSE_CONTENT_TYPE_BYTES, startOffset, endOffset);
		if (fieldPos < 0)
			return null;

		fieldPos += HTTP_RESPONSE_CONTENT_TYPE_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 3))
			return null;

		String result = new String(payload, fieldPos, newLinePos - fieldPos);
		return result;
	}

	/**
	 * Get the attachment file name from a TCP payload carrying an HTTP
	 * response.
	 * 
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return File name or null if not found.
	 */
	public static String getAttachmentFileNameFromPayload(byte[] payload,
			int startOffset, int endOffset)
	{
		String result = getValueFromPayload(HTTP_RESPONSE_ATTACH_FILE1_BYTES,
				payload, startOffset, endOffset);

		if (result == null)
			return getValueFromPayload(HTTP_RESPONSE_ATTACH_FILE2_BYTES,
					payload, startOffset, endOffset);

		return result;
	}

	/**
	 * Get the ETag value from a TCP payload carrying an HTTP response.
	 * 
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return Etag value or null if not found.
	 */
	public static String getEtagFromPayload(byte[] payload, int startOffset,
			int endOffset)
	{
		return getValueFromPayload(HTTP_RESPONSE_ETAG_BYTES, payload,
				startOffset, endOffset);
	}

	/**
	 * @param startOffset
	 *            0-based inclusive.
	 * @param endOffset
	 *            0-based exclusive. Should point to the first byte that should
	 *            not be scanned. The byte before should contain the last
	 *            newline to scan, meaning that is should point to the second
	 *            new line (\r\n).
	 * @return Value if field was found, or null if not. If the value is
	 *         surounded with " or ', they are removed.
	 */
	private static String getValueFromPayload(byte[] fieldName, byte[] payload,
			int startOffset, int endOffset)
	{
		//
		// Get the position where the content length is written
		//
		int fieldPos = indexOfBytesInBytes(payload, fieldName, startOffset,
				endOffset);
		if (fieldPos < 0)
			return null;

		fieldPos += fieldName.length;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = indexOfBytesInBytes(payload, NEWLINE_BYTES, fieldPos,
				endOffset);
		if (newLinePos < (fieldPos + 3))
			return null;

		// If the name is in quote marks, then remove them
		if (payload[fieldPos] == '"' && payload[newLinePos - 1] == '"'
				|| payload[fieldPos] == '\'' && payload[newLinePos - 1] == '\'')
		{
			fieldPos++;
			newLinePos--;
		}

		String result = new String(payload, fieldPos, newLinePos - fieldPos);
		return result;
	}

	/**
	 * Get the content-type from a TCP payload carrying an HTTP response.
	 * 
	 * @return Content-type or null if not found.
	 */
	public static String getContentTypeFromPayload(String payload)
	{
		if (payload == null)
			return null;

		//
		// Get the position where the content length is written
		//
		int fieldPos = payload.indexOf(HTTP_RESPONSE_CONTENT_TYPE);
		if (fieldPos < 0)
			return null;
		fieldPos += HTTP_RESPONSE_CONTENT_TYPE_LEN;

		//
		// Make sure the line ends with a new-line, and it contains a minimal
		// number of characters
		//
		int newLinePos = payload.indexOf('\r', fieldPos);
		if (newLinePos < (fieldPos + 1))
			return null;

		return payload.substring(fieldPos, newLinePos);
	}

	/**
	 * Get the referer host name from a TCP payload carrying an HTTP header.
	 * <p>
	 * The host name must follow the pattern. It may fail if the TCP payload is
	 * not complete and/or some kind of encoding was used.
	 * 
	 * @return Host name, or null if something went wrong.
	 */
	public static String getRefererHostFromPayload(String payload)
	{
		if (payload == null)
			return null;

		String hostName = null;

		//
		// Get the position where the host name is written
		//
		int hostPos = payload.indexOf(HTTP_RESPONSE_REFERER);
		if (hostPos < 0)
			return null;
		hostPos += HTTP_RESPONSE_REFERER_LEN;

		//
		// Make sure the referer line has a path
		//
		int slashPos = payload.indexOf('/', hostPos);
		if (slashPos < (hostPos + 4))
			return null;

		//
		// Isolate the host name
		//
		hostName = payload.substring(hostPos, slashPos);

		//
		// Remove port number if mentioned
		//
		int portIndex = hostName.indexOf(':');
		if (portIndex > 0)
		{
			hostName = hostName.substring(0, portIndex);
		}

		// Make sure it matches the host name rules
		if (!hostPattern.matcher(hostName).matches())
			return null;

		return hostName;
	}

	/**
	 * @param withHttpHeader
	 *            If the payload contains an HTTP header that needs to be
	 *            skipped before the hash is calculated.
	 * @return Hash code of the first content bytes, or -1 if the packet
	 *         contains all the header but only it, or zero if it does not
	 *         contain enough content or the header may continue on the next
	 *         packet.
	 */
	public static long getStampFromPayload(byte[] payload, int stampBytes,
			boolean withHttpHeader)
	{
		int pos = 0;

		if (withHttpHeader)
		{
			int limit = payload.length - stampBytes - 4;
			for (int i = 30; i < limit; i++)
			{
				if (payload[i] == '\r' && payload[i + 1] == '\n'
						&& payload[i + 2] == '\r' && payload[i + 3] == '\n')
				{
					pos = i + 4;
					break;
				}
			}

			// Did not find the end
			if (pos == 0)
			{
				// If the packet ends with the header-end
				if (payload[payload.length - 4] == '\r'
						&& payload[payload.length - 3] == '\n'
						&& payload[payload.length - 2] == '\r'
						&& payload[payload.length - 1] == '\n')
					return -1;

				return 0;
			}
		}
		else
		{
			// Make sure we have the minimum number of bytes in payload
			if (stampBytes > payload.length)
				return 0;
		}

		int stamp = Arrays.hashCode(Arrays.copyOfRange(payload, pos, pos
				+ stampBytes));
		return 0x00000000ffffffffL & stamp;
	}

	/**
	 * @param hostname
	 *            Host name.
	 * @return Domain of the host name, or null if there is any problem with the
	 *         host name. If the last part starts with a number, it assumes that
	 *         this is an IP, and returns the entire hostname as domain.
	 */
	public static String getDomainFromHostname(String hostname)
	{
		return getDomainFromHostname(hostname, true);
	}

	/**
	 * @param hostname
	 *            Host name. Can contain a port number.
	 * @param allowNumeric
	 *            False if host name that ends with a digit is not considered as
	 *            domain.
	 * @return Domain of the host name, or null if there is any problem with the
	 *         host name. If the last part starts with a number, it assumes that
	 *         this is an IP, and returns the entire host name as domain only if
	 *         it is allowed according to the given parameters.
	 */
	public static String getDomainFromHostname(String hostname,
			boolean allowNumeric)
	{
		if (hostname == null)
			return null;

		String split[] = hostname.split("\\.");
		// Minimum is two parts
		if (split.length < 2)
			return null;

		//
		// Check for top-level-domains
		//

		// Remove port number if specified
		String lastPart = split[split.length - 1].split("\\:")[0];
		String beforeLastPart = split[split.length - 2];

		// Two-parts only
		if (split.length == 2)
			return beforeLastPart + "." + lastPart;

		// Top-level with no option for three-parts
		if (lastPart.equals("com") || lastPart.equals("net")
				|| lastPart.equals("info") || lastPart.equals("biz")
				|| lastPart.equals("name") || lastPart.equals("org")
				|| lastPart.equals("pro") || lastPart.equals("gov")
				|| lastPart.equals("edu") || lastPart.equals("tv")
				|| lastPart.equals("pl"))
			return beforeLastPart + "." + lastPart;

		// Countries with optional two-parts
		if (lastPart.equals("ru") || lastPart.equals("fr")
				|| lastPart.equals("cn") || lastPart.equals("br")
				|| lastPart.equals("hu"))
		{
			if (!beforeLastPart.equals("com") && !beforeLastPart.equals("gov")
					&& !beforeLastPart.equals("edu")
					&& !beforeLastPart.equals("net"))
				return beforeLastPart + "." + lastPart;
		}

		// Numeric IP? does not have to be accurate, so it is a quick check
		if (Character.isDigit(lastPart.charAt(0)))
			return allowNumeric ? hostname : null;

		return split[split.length - 3] + "." + split[split.length - 2] + "."
				+ lastPart;
	}

	/**
	 * Search for byte array in another byte array.
	 * 
	 * @param data
	 *            Payload bytes, with or without a header. In case of a header,
	 *            use start offset parameter.
	 * @param startOffset
	 *            0-based start offset. Should be zero if the payload start at
	 *            the beginning of the given array, or a positive number that
	 *            points to the payload start in the given array.
	 * @return HTTP response code (2xx to 5xx), or -1 if this can not be a legal
	 *         HTTP response.
	 */
	public static int getHttpResponseCode(byte[] data, int startOffset)
	{
		// Verify that the given parameters make sense
		if (data == null || (data.length - startOffset) < 12)
			return -1;

		// Verify start with "HTTP/x.x " and then a space after 3 digits
		if (data[startOffset] != 'H' || data[startOffset + 1] != 'T'
				|| data[startOffset + 2] != 'T' || data[startOffset + 3] != 'P'
				|| data[startOffset + 4] != '/' || data[startOffset + 6] != '.'
				|| data[startOffset + 8] != ' '
				|| data[startOffset + 12] != ' ')
			return -1;

		long result = bytesToLong(data, startOffset + 9, startOffset + 12);
		if (result < 200 || result >= 600)
			return -1;

		return (int) result;
	}

	/**
	 * Search for byte array in another byte array.
	 * 
	 * @param data
	 *            Payload bytes, with or without a header. In case of a header,
	 *            use start offset parameter.
	 * @param searchBytes
	 *            The bytes to search for in the payload.
	 * @param startOffset
	 *            0-based start offset. Should be zero if the payload start at
	 *            the beginning of the given array, or a positive number that
	 *            points to the payload start in the given array.
	 * @param endOffset
	 *            0-based end offset, exclusive. The total length of the payload
	 *            is (end - start).
	 * @return 0-based offset of the search, or -1 if not found.
	 */
	public static int indexOfBytesInBytes(byte[] data, byte[] searchBytes,
			int startOffset, int endOffset)
	{
		// Verify that the given parameters make sense
		if (data == null || searchBytes == null || searchBytes.length <= 0
				|| data.length < endOffset
				|| (endOffset - startOffset) < searchBytes.length)
			return -1;

		//
		// Search
		//
		int lastPossibleParamPos = endOffset - searchBytes.length;
		boolean matched = false;
		for (int curOffset = startOffset; curOffset <= lastPossibleParamPos
				&& !matched; curOffset++)
		{
			//
			// Loop to compare all the bytes
			//
			for (int i = 0; i < searchBytes.length; i++)
			{
				if (data[i + curOffset] != searchBytes[i])
				{
					matched = false;
					break;
				}
				matched = true;
			}

			// The loop was ended, because of a full match or no match
			if (matched)
				return curOffset;
		}

		return -1;
	}

	/**
	 * Isolate a number in byte array, and return its value.
	 * 
	 * @param data
	 *            Payload bytes, with or without a header. In case of a header,
	 *            use start offset parameter.
	 * @param startOffset
	 *            0-based start offset. Should be zero if the payload start at
	 *            the beginning of the given array, or a positive number that
	 *            points to the payload start in the given array.
	 * @param 0-based end offset, exclusive. The total length of the payload is
	 *        (end - start).
	 * @return Parsed decimal number, or -1 on error.
	 */
	static long bytesToLong(byte[] data, int startOffset, int endOffset)
	{
		// Verify that the given parameters make sense
		if (data == null || data.length < endOffset || endOffset <= startOffset)
			return -1;

		long result = 0;

		for (int i = startOffset; i < endOffset; i++)
		{
			byte curByte = data[i];

			if (curByte == '\r' || curByte == '\n')
				return result;

			if (curByte == ' ')
			{
				// Space before the numbers is allowed, so skip it
				if (result == 0)
					continue;

				// Space after numbers means that this is the end
				return result;
			}

			// Accept only numbers
			if (curByte < '0' || curByte > '9')
				return -1;

			// Next digit
			result = result * 10 + (curByte - '0');
		}

		return result;
	}

	/**
	 * @return First line of textual response.
	 */
	public static String readUrlText(String urlString,
			int connectTimeoutMillis, int readTimeoutMillis, Logger log)
	{
		//
		// Build the full URL
		//
		URL url;
		try
		{
			url = new URL(urlString);
		} catch (MalformedURLException e)
		{
			LoggingUtil.log(log, Level.WARNING, "Malformed URL exception: {0}",
					e);
			return null;
		}

		LoggingUtil.log(log, Level.FINER, "Sending {0}", url);

		//
		// Connect and send the request
		//
		HttpURLConnection connection = sendHttpRequest(url,
				connectTimeoutMillis, readTimeoutMillis, log);
		if (connection == null)
		{
			LoggingUtil.log(log, Level.FINE, "Failed to connect {0}", url
					.getHost());
			return null;
		}

		//
		// Open input stream and read
		//
		BufferedReader reader;
		try
		{
			InputStreamReader in = new InputStreamReader(connection
					.getInputStream());
			reader = new BufferedReader(in);
		} catch (Exception e)
		{
			if (e instanceof java.net.SocketTimeoutException)
			{
				LoggingUtil.log(log, Level.INFO,
						"Command {0}: Read timeout after {1} mSec", urlString,
						readTimeoutMillis);
			}
			else
			{
				LoggingUtil.log(log, Level.INFO,
						"Command {0}: Failed to get input stream:\n{1}",
						urlString, e);
			}
			return null;
		}

		//
		// Read the line
		//
		String line;
		try
		{
			line = reader.readLine();
		} catch (IOException e)
		{
			return null;
		}

		//
		// Close the input
		//
		try
		{
			reader.close();
		} catch (IOException e)
		{
		}

		return line;
	}

	/**
	 * @return All the lines of a textual response.
	 */
	public static LinkedList<String> readUrlTextLines(String urlString,
			int connectTimeoutMillis, int readTimeoutMillis, Logger log)
	{
		//
		// Build the full URL
		//
		URL url;
		try
		{
			url = new URL(urlString);
		} catch (MalformedURLException e)
		{
			LoggingUtil.log(log, Level.WARNING, "Malformed URL exception: {0}",
					e);
			return null;
		}

		LoggingUtil.log(log, Level.FINER, "Sending {0}", url);

		//
		// Connect and send the request
		//
		HttpURLConnection connection = sendHttpRequest(url,
				connectTimeoutMillis, readTimeoutMillis, log);
		if (connection == null)
		{
			LoggingUtil.log(log, Level.FINE, "Failed to connect {0}", url
					.getHost());
			return null;
		}

		//
		// Open input stream and read
		//
		BufferedReader reader;
		LinkedList<String> result;
		try
		{
			InputStreamReader in = new InputStreamReader(connection
					.getInputStream());
			reader = new BufferedReader(in);
			result = new LinkedList<String>();
		} catch (Exception e)
		{
			if (e instanceof java.net.SocketTimeoutException)
			{
				LoggingUtil.log(log, Level.INFO,
						"Command {0}: Read timeout after {1} mSec", urlString,
						readTimeoutMillis);
			}
			else
			{
				LoggingUtil.log(log, Level.INFO,
						"Command {0}: Failed to get input stream:\n{1}",
						urlString, e);
			}
			return null;
		}

		//
		// Read the lines
		//
		while (true)
		{
			String line;
			try
			{
				line = reader.readLine();
			} catch (IOException e)
			{
				break;
			}
			if (line == null)
				break;
			result.add(line);
		}

		//
		// Close the input
		//
		try
		{
			reader.close();
		} catch (IOException e)
		{
		}

		return result;
	}

	/**
	 * Send HTTP GET request.
	 * <p>
	 * Opens an HTTP connection with timeouts both for connect and for read and
	 * sends a request.
	 * 
	 * @param urlFile
	 *            The file part of the URL.
	 * @param readTimeoutMillis
	 *            Read-timeout to set, in milli-seconds.
	 * @return Null if failed, or the connection in order to be able to read
	 *         from it.
	 */
	private static HttpURLConnection sendHttpRequest(URL url,
			int connectTimeoutMillis, int readTimeoutMillis, Logger log)
	{
		//
		// Prepare the connection
		//
		HttpURLConnection connection;
		try
		{
			connection = (HttpURLConnection) url.openConnection();
		} catch (IOException e)
		{
			LoggingUtil.log(log, Level.WARNING,
					"Failed to prepare connection URL {0}:\n{1}", url, e);
			return null;
		}

		connection.setConnectTimeout(connectTimeoutMillis);
		connection.setReadTimeout(readTimeoutMillis);
		connection.setUseCaches(false);
		connection.addRequestProperty("Connection", "close");

		//
		// Connect
		//
		try
		{
			connection.connect();
		} catch (SocketTimeoutException e1)
		{
			LoggingUtil.log(log, Level.WARNING,
					"Failed to connect. Timeout after {0} mSec. URL {1}",
					connectTimeoutMillis, url);
			return null;
		} catch (IOException e)
		{
			LoggingUtil.log(log, Level.WARNING,
					"Failed to connect. URL {0}:\n{1}", url, e);
			return null;
		}

		return connection;
	}

	/**
	 * Search for a parameter in text, written as key=value or key="value", and
	 * return its value.
	 * 
	 * @param text
	 *            Text to scan.
	 * @param paramName
	 *            Name of parameter, case sensitive.
	 * @param defaultResult
	 *            Default result to return on error.
	 * @return Value if found and parsed to valid number, or default if any kind
	 *         of problem occurred.
	 */
	public static long paramAsLong(String text, String paramName,
			long defaultResult)
	{
		String value = paramAsString(text, paramName);
		if (value == null)
			return defaultResult;

		long result;
		try
		{
			result = Long.parseLong(value);
		} catch (NumberFormatException e)
		{
			return defaultResult;
		}

		return result;
	}

	/**
	 * Search for a parameter in text, written as key=value or key="value", and
	 * return its value.
	 * 
	 * @param text
	 *            Text to scan.
	 * @param paramName
	 *            Name of parameter, case sensitive.
	 * @return Value if found, or null if any kind of problem occurred.
	 */
	public static String paramAsString(String text, String paramName)
	{
		int paramPos = text.indexOf(paramName + "=");
		if (paramPos < 0)
			return null;

		int valuePos = paramPos + paramName.length() + 1;
		if (valuePos >= text.length())
			return null;

		boolean withSurr = text.charAt(valuePos) == '"';
		if (withSurr)
			valuePos++;

		//
		// Position of the last included char
		//
		int endPos;
		if (withSurr)
		{
			endPos = text.indexOf('"', valuePos);
			if (endPos < 0)
				return null;
		}
		else
		{
			endPos = text.indexOf(' ', valuePos);
			if (endPos < 0)
			{
				endPos = text.length();
			}
		}

		return text.substring(valuePos, endPos);
	}

	public static String paramAsString(byte[] text, int length,
			byte[] paramName, int startOffset)
	{
		int paramPos = VideoUtils.indexOfBytesInBytes(text, paramName,
				startOffset, text.length);
		if (paramPos < 0)
			return null;

		int valuePos = paramPos + paramName.length;
		if (valuePos >= length)
			return null;

		boolean withSurr = text[valuePos] == '"';
		if (withSurr)
			valuePos++;

		//
		// Position of the last included char
		//
		int endPos;
		if (withSurr)
		{
			endPos = indexOfBytesInBytes(text, QUOTE_BYTES, valuePos, length);
			if (endPos < 0)
				return null;
		}
		else
		{
			endPos = indexOfBytesInBytes(text, NEWLINE_BYTES, valuePos, length);
			if (endPos < 0)
			{
				endPos = indexOfBytesInBytes(text, SLASHN_BYTES, valuePos,
						length);
				if (endPos < 0)
				{
					endPos = length;
				}
			}
		}

		return new String(text, valuePos, endPos - valuePos);
	}
}
