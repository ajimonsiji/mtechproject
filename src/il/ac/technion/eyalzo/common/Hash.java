package il.ac.technion.eyalzo.common;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.Random;

public class Hash implements Comparable<Hash>
{
	protected byte[]	hashBuffer	= null;
	protected int		length		= 16;		// default length is 16 byte
	protected boolean	valid		= false;

	/**
	 * Initialize from hex-string representation. Remember that every byte is
	 * made of two hex characters.
	 * 
	 * @param hashString
	 *            - hex-string
	 * @param bytesLen
	 *            - length in bytes of the hash. This parameter is supplied for
	 *            cases when string might be too short.
	 */
	public Hash(String hashString, int bytesLen)
	{
		// make sure string length is legal
		int iStrLen = hashString.length();
		int iBytesLenString = iStrLen / 2;
		if (iStrLen <= 0 || (iStrLen % 2) > 0 || iBytesLenString > bytesLen)
			return;
		// set length in bytes
		length = bytesLen;
		hashBuffer = new byte[length];
		// string may be too small, so start with filling msb bytes
		for (int i = (bytesLen - iBytesLenString - 1); i >= 0; i--)
			hashBuffer[i] = (byte) 0;
		// continue with string's bytes
		for (int i = (bytesLen - iBytesLenString); i < bytesLen; i++)
		{
			int iPos = i - (bytesLen - iBytesLenString);
			hashBuffer[i] = (byte) ((Character.digit(hashString
					.charAt(iPos * 2), 16) << 4) + Character.digit(hashString
					.charAt(iPos * 2 + 1), 16));
		}

		valid = true;
	}

	/**
	 * Similar to Hash(String, int), but assumes that length in bytes is exactly
	 * half of the string length.
	 * 
	 * @param hashString
	 *            - see Hash(String, int)
	 */
	public Hash(String hashString)
	{
		this(hashString, hashString.length() / 2);
	}

	/**
	 * Initialize from hex-string representation. Remember that every byte is
	 * made of two hex characters.
	 * 
	 * @param hashString
	 *            - hex-string
	 * @param bytesLen
	 *            - length in bytes of the hash. This parameter is supplied for
	 *            cases when string might be too short.
	 */
	public Hash(byte[] userHashBytes)
	{
		length = userHashBytes.length;
		// make sure string length is legal
		if (length == 0)
			return;

		hashBuffer = userHashBytes;

		valid = true;
	}

	public Hash(byte[] userHashBytes, int offset, int count)
	{
		length = count;

		// make sure string length is legal
		if (length == 0 || userHashBytes.length < offset + count)
			return;

		hashBuffer = new byte[length];

		for (int i = 0; i < length; i++)
		{
			hashBuffer[i] = userHashBytes[offset + i];
		}

		valid = true;
	}

	/**
	 * Initialize by reading from ByteBuffer. It also means that the position of
	 * the buffer will move forward, by length bytes.
	 * 
	 * @param userHashBuffer
	 *            The given ByteBuffer to read from.
	 * @param length
	 *            Number of bytes to read from the buffer.
	 */
	public Hash(ByteBuffer userHashBuffer, int length)
	{
		// make sure string length is legal
		if (length == 0 || length > userHashBuffer.remaining())
			return;

		hashBuffer = new byte[length];
		userHashBuffer.get(hashBuffer);
		this.length = length;

		valid = true;
	}

	/**
	 * Initialize from int, which is usually a result of a call to
	 * <code>hashCode()</code>.
	 * 
	 * @param hashCode
	 *            Given 32-bit that will be converted to big-endian hash.
	 */
	public Hash(int hashCode)
	{
		hashBuffer = new byte[4];
		hashBuffer[0] = (byte) ((hashCode >> 24) & 0x000000FF);
		hashBuffer[1] = (byte) ((hashCode >> 16) & 0x000000FF);
		hashBuffer[2] = (byte) ((hashCode >> 8) & 0x000000FF);
		hashBuffer[3] = (byte) (hashCode & 0x000000FF);
		length = 4;
		valid = true;
	}

	/**
	 * Initialize an empty object.
	 */
	public Hash()
	{
	}

	/**
	 * Set length in bytes which is different from the default.
	 * 
	 * @param length
	 *            - length in bytes
	 */
	public void setLength(int length)
	{
		// if it's a new length then destroy the previous buffer
		if (this.length != length)
		{
			this.length = length;
			hashBuffer = null;
			valid = false;
		}
	}

	/**
	 * Returns byte-array representation of the hash.
	 * 
	 * @return The internal buffer or null if hash is invalid.
	 */
	public byte[] toByteArray()
	{
		return hashBuffer;
	}

	/**
	 * Converts hash to integer (signed 32-bit).
	 * 
	 * @return Zero if hash is not valid or hash length is less than 32-bit.
	 */
	@Override
	public int hashCode()
	{
		if (!valid || hashBuffer.length < 4)
			return 0;
		return (hashBuffer[0] << 24 | hashBuffer[1] << 16 | hashBuffer[2] << 8 | hashBuffer[3]);
	}

	/**
	 * @return Returns the valid.
	 */
	public boolean isValid()
	{
		return valid;
	}

	@Override
	/**
	 * @return Upper-case, zero-padded, hex representation of that hash.
	 */
	public String toString()
	{
		String result = "";

		if (!valid)
			return result;

		for (int i = 0; i < length; i++)
		{
			int curByte = hashBuffer[i] & 0x00ff;
			if (curByte < 16)
			{
				result += "0" + Integer.toHexString(curByte).toUpperCase();
			}
			else
			{
				result += Integer.toHexString(curByte).toUpperCase();
			}
		}

		return result;
	}

	/**
	 * @param prefixBytes
	 *            How many bytes should be displayed before and after the "..".
	 * @return Like {@link #toString()} but in short, made of prefix and suffix.
	 *         For example ABCDEF with max-length of 4 will return "A..F".
	 */
	public String toString(int prefixBytes)
	{
		if (!valid || prefixBytes < 1 || prefixBytes >= (length - 1))
			return toString();

		String result = "";

		//
		// Left characters
		//
		for (int i = 0; i < prefixBytes; i++)
		{
			int curByte = hashBuffer[i] & 0x00ff;
			if (curByte < 16)
			{
				result += "0" + Integer.toHexString(curByte).toUpperCase();
			}
			else
			{
				result += Integer.toHexString(curByte).toUpperCase();
			}
		}

		//
		// Right characters
		//
		result += "..";
		for (int i = length - prefixBytes; i < length; i++)
		{
			int curByte = hashBuffer[i] & 0x00ff;
			if (curByte < 16)
			{
				result += "0" + Integer.toHexString(curByte).toUpperCase();
			}
			else
			{
				result += Integer.toHexString(curByte).toUpperCase();
			}
		}

		return result;
	}

	@Override
	public boolean equals(Object arg0)
	{
		Hash otherHash = (Hash) arg0;
		if (!valid || !otherHash.valid || length != otherHash.length)
			return false;

		for (int i = 0; i < length; i++)
		{
			if (hashBuffer[i] != otherHash.hashBuffer[i])
				return false;
		}

		return true;
	}

	static public Hash generateRandomHash(int length)
	{
		byte[] hash = new byte[length];

		Random rand = new Random(System.currentTimeMillis());

		// Random bytes
		rand.nextBytes(hash);

		return new Hash(hash);
	}

	/**
	 * @return Returns the length.
	 */
	public int getLength()
	{
		return length;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(T)
	 */
	public int compareTo(Hash o)
	{
		if (!valid)
			return -1;

		if (!o.valid)
			return 1;

		// This "extra if" saves one comparison on the common case
		if (length != o.length)
		{
			if (length > o.length)
				return 1;

			if (length < o.length)
				return -1;
		}

		for (int i = 0; i < length; i++)
		{
			int myByte = hashBuffer[i] & 0xff;
			int otherByte = o.hashBuffer[i] & 0xff;
			if (myByte > otherByte)
				return 1;
			if (myByte < otherByte)
				return -1;
		}

		return 0;
	}

	/**
	 * Get the URL encoded format of the hash.
	 * 
	 * @return Returns the encoded hash or empty string if failed to encode.
	 *         Cannot be null.
	 */
	public String getUrlEncoded()
	{
		try
		{
			return URLEncoder.encode(new String(this.getBytes(), "ISO-8859-1"),
					"ISO-8859-1").replaceAll("\\+", "%20");
		} catch (UnsupportedEncodingException e)
		{
			return "";
		}
	}

	/**
	 * Returns byte-array representation of the hash. Same as toByteArray().
	 * 
	 * @return The internal buffer or null if hash is invalid.
	 */
	public byte[] getBytes()
	{
		return hashBuffer;
	}
}