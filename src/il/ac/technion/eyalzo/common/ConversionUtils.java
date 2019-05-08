package il.ac.technion.eyalzo.common;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;

public class ConversionUtils
{
	private static char[]	hexChar	= { '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	/**
	 * Convert a byte array to hexadecimal string representation
	 * <p>
	 * Each byte is converted to two characters<br>
	 * For example the byte 0x00 will be converted to the string "00" Note: if a
	 * delimiter is set to other than null, it will be appended after every byte
	 * representation
	 * 
	 * @param b
	 *            the array to convert
	 * @param delimiter
	 *            appendable character for display purpose
	 * @return string representation of the array
	 */
	public static String toHexString(byte[] b, Character delimiter)
	{
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++)
		{
			sb.append(hexChar[(b[i] & 0xf0) >>> 4]); // look up high nibble char
			sb.append(hexChar[b[i] & 0x0f]); // look up low nibble char

			//
			// if there is a delimiter and this is not the last byte, append it
			//
			if (delimiter != null && i != b.length - 1)
			{
				sb.append(delimiter);
			}
		}
		return sb.toString();
	}

	/**
	 * Convert a ByteBuffer to a hexadecimal string representation
	 * <p>
	 * 
	 * @param b
	 *            the ByteBuffer to convert
	 * @return string representation of the ByteBuffer
	 */
	public static String toHexString(ByteBuffer b)
	{
		ByteBuffer byteBuffer = b.duplicate().order(b.order());

		StringBuffer buffer = new StringBuffer();
		while (byteBuffer.hasRemaining())
		{
			buffer.append(Integer.toHexString(byteBuffer.get() & 0xFF) + "-");
		}

		return buffer.toString();
	}

	public static String byteArrAsStr(byte[] data)
	{
		StringBuffer buffer = new StringBuffer();
		for (byte b : data)
		{
			buffer.append(Integer.toHexString(b & 0xFF) + "-");
		}
		return buffer.toString();
	}

	/**
	 * Convert a hexadecimal text string to a byte array.
	 * 
	 * @param sHexString
	 *            Hexadecimal <code>String</code>
	 * @return <code>Byte</code> array
	 */
	public static byte[] hexStringToBytes(String sHexString)
	{
		byte[] output = new byte[sHexString.length() / 2];
		int j = 0;

		for (int i = 0; i < sHexString.length(); i = i + 2)
		{
			output[j] = (byte) (Byte.parseByte(sHexString.substring(i, i + 1),
					16) << 4);
			output[j] = (byte) (output[j] | (Byte.parseByte(sHexString
					.substring(i + 1, i + 2), 16)));
			j++;
		}
		return output;
	}

	/**
	 * Converts URL to InetSocketAddress
	 * <p>
	 * Taking into consideration that the port might be missing and using the
	 * default port instead
	 * 
	 * @param url
	 *            The URL we want to convert
	 * @return InetSocketAddress representing the URL
	 */
	public static InetSocketAddress convertURL(URL url)
	{
		int port = url.getPort();
		if (port == -1)
		{
			port = url.getDefaultPort();
		}
		return new InetSocketAddress(url.getHost(), port);
	}

	/**
	 * @param millis
	 *            Given period/interval in millis.
	 * @return Human-readable representation of the given period.
	 */
	public static String millisAsHumanReadable(long millis)
	{
		// Up to 10 seconds
		if (millis < 10 * 1000)
			return String.format("%,d msec", millis);
		// Up to 10 minutes
		if (millis < 60 * 10 * 1000)
			return String.format("%,d sec", millis / 1000);
		// Up to 1 hour
		if (millis < 60 * 60 * 1000)
			return String.format("%,d min", millis / 1000 / 60);
		// Up to 24 hours
		long minutes = millis / 1000 / 60;
		long hours = minutes / 60;
		if (millis < 24 * 60 * 60 * 1000)
			return String.format("%d:%02d hours", hours, minutes % 60);
		// More than a day
		return String.format("%d days %d:%02d hours", hours / 24, hours % 24,
				minutes % 60);
	}

	/**
	 * @param bytes
	 *            Given number of bytes.
	 * @return Human-readable representation of the given number of bytes, with
	 *         comma separator and KB/MB/GB extension.
	 */
	public static String bytesAsHumanReadable(long bytes)
	{
		// Up to 10K
		if (bytes < 10000L)
			return String.format("%,d B", bytes);
		// Up to 10M
		if (bytes < 10000000L)
			return String.format("%,d KB", bytes / 1024);
		// Up to 10G
		if (bytes < 10000000000L)
			return String.format("%,d MB", bytes / 1024 / 1024);
		// Up to 10T
		if (bytes < 10000000000000L)
			return String.format("%,d GB", bytes / 1024 / 1024 / 1024);
		// More than a 10T
		return String.format("%,d TB", bytes / 1024 / 1024 / 1024 / 1024);
	}
}
