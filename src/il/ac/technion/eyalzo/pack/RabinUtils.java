package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.pack.conns.TcpUtils;
import il.ac.technion.eyalzo.pack.files.FileUtils;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;
import il.ac.technion.eyalzo.pack.stamps.GlobalChunkList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;

public class RabinUtils
{
	public static final RabinHashFunction32 rhf = RabinHashFunction32.DEFAULT_HASH_FUNCTION;
	/**
	 * Number of bytes in a fingerprint.
	 */
	public final static int FINGERPRINT_BYTES_LEN = 48;
	/**
	 * Number of fingerprint bits to be compared with a single value. Average chunk size will then be 2^x.
	 */
	private final static int ANCHOR_BITS_NUM = 11;
	/**
	 * Average chunk length in random data.
	 */
	private final static int AVG_CHUNK_LEN = (1 << ANCHOR_BITS_NUM);
	/**
	 * The mask that isolates the rightmost bits of a fingerprint, to find anchors.
	 */
	public final static int ANCHOR_MASK = AVG_CHUNK_LEN - 1;
	/**
	 * Maximal length of a block to hash, in bytes. If no anchor is found within that length, than the hash is performed
	 * over this block and an artificial anchor is "added" right after it.
	 */
	public final static int MAX_CHUNK_LEN = 2 * AVG_CHUNK_LEN;
	/**
	 * Minimal length for a chunk. Anchors are not searched before that number of bytes is skipped since a previous
	 * anchor.
	 */
	public final static int MIN_CHUNK_LEN = Math.max(AVG_CHUNK_LEN / 4, TcpUtils.PACKET_SIZE
			- TcpUtils.COMBINED_HEADERS_LEN + 1);
	/**
	 * Default block size for file reads.
	 */
	private final static int BLOCK_SIZE = 100000 + MAX_CHUNK_LEN;
	private static MessageDigest md = null;

	//
	// Rolling Rabin
	//
	private static final long PRIME_BASE = 257L;
	private static final long PRIME_MOD = 1000000007L;

	//
	// Rolling PACK
	//
	//	private static final long ROL_PACK_MASK = 0x0105010301031580L;
	// The mask should not use the 7 bits from left and 7 from right
	// 13 bits = 8KB chunk
	//	private static final long ROL_PACK_MASK = 0x0000010301731580L;
	// 11 bits = 2KB chunk
	private static final long ROL_PACK_MASK = 0x0000010101331580L;
	private static final long ROL_PACK_ANCHOR = ROL_PACK_MASK;
	/**
	 * Number of bytes covered by the rolling hash in a single window.
	 */
	public static final int ROL_PACK_WINDOW_BYTES = 48;
	// We can express 48 bytes window with 55 bits
	private static final int ROL_PACK_BITS = Long.SIZE - 9;
	// Shift bits to move a byte all the way to the left
	private static final int ROL_PACK_SHIFT_BITS = ROL_PACK_BITS - Byte.SIZE;
	// Cleanup steps (57 bytes with 64-bit longs)
	private static final int ROL_PACK_CLEANUP_BITS = ROL_PACK_SHIFT_BITS + 1;
	private static long[] ROL_PACK_TABLE;

	/**
	 * SAMPLEBYTE (EndRE) fixed anchors for 32 bytes chunks (256 / 8).
	 */
	private static byte SAMPLEBYTE_ANCHORS_1 = 0;
	private static byte SAMPLEBYTE_ANCHORS_2 = 32;
	private static byte SAMPLEBYTE_ANCHORS_3 = 48;
	private static byte SAMPLEBYTE_ANCHORS_4 = 101;
	private static byte SAMPLEBYTE_ANCHORS_5 = 105;
	private static byte SAMPLEBYTE_ANCHORS_6 = 115;
	private static byte SAMPLEBYTE_ANCHORS_7 = 116;
	private static byte SAMPLEBYTE_ANCHORS_8 = (byte) 255;

	@SuppressWarnings("unused")
	private static void initRabinRolling()
	{
		if (ROL_PACK_TABLE != null)
			return;

		ROL_PACK_TABLE = new long[(int) Math.pow(2, Byte.SIZE)];

		for (int i = 0; i < ROL_PACK_TABLE.length; i++)
		{
			ROL_PACK_TABLE[i] = (0x00ffL & i) << ROL_PACK_SHIFT_BITS;
		}
	}

	private static void initDigest()
	{
		if (md != null)
			return;

		try
		{
			md = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Build chunk list from all the file's bytes. That includes that chunk that starts from the file's beginning, after
	 * a minimal-chunk skip. The last chunk may not be added if too short.
	 * 
	 * @return Null if failed to read the entire file or even one block.
	 */
	public synchronized static LinkedList<ChunkItem> calcFileChunks(long totalSize, GlobalChunkList globalStampList,
			String fileName)
	{
		if (totalSize <= 0)
			return null;

		LinkedList<ChunkItem> result = new LinkedList<ChunkItem>();

		// The first block always starts with the file
		long prevAnchorOffset = 0;
		long curAnchorOffset;

		//
		// Read the file in large blocks, to find anchors
		//
		long bufferOffset = 0;
		while (true)
		{
			// How many bytes to read from file into the buffer
			boolean lastLoop = (bufferOffset + BLOCK_SIZE) >= totalSize;
			int readBytes = lastLoop ? (int) (totalSize - bufferOffset) : BLOCK_SIZE;

			// Read the file
			ByteBuffer buffer = FileUtils.readBlock(fileName, bufferOffset, readBytes, ByteOrder.BIG_ENDIAN, null);

			// Read error?
			if (buffer == null)
				return null;

			// Where is the last buffer offset where it should still look for
			// anchors
			long lastBufferAnchorOffset = buffer.capacity() - 1 - FINGERPRINT_BYTES_LEN;

			// Offset where to stop in buffer because reached maximal block size
			int anchorOffsetByMaxBlockSize = (int) (bufferOffset - prevAnchorOffset) + MAX_CHUNK_LEN;
			for (int i = MIN_CHUNK_LEN; i <= lastBufferAnchorOffset; i++)
			{
				buffer.limit(i + FINGERPRINT_BYTES_LEN);
				buffer.position(i);
				int curHash = rhf.hash(buffer);
				boolean reachedMaxSize = (i == anchorOffsetByMaxBlockSize);
				// Check for anchor (if the rightmost bits are all 1)
				if ((curHash & ANCHOR_MASK) == ANCHOR_MASK || reachedMaxSize)
				{
					// The anchor offset
					curAnchorOffset = bufferOffset + i;
					int blockLen = (int) (curAnchorOffset - prevAnchorOffset);

					int sha1 = calculateSha1(buffer, (int) (prevAnchorOffset - bufferOffset), blockLen);
					// System.out.println(String.format("%,d: 0x%08X",
					// prevAnchorOffset, sha1));

					// Add to result
					ChunkItem stampItem = globalStampList.getChunkOrAddNew(sha1, blockLen);
					result.add(stampItem);
					prevAnchorOffset = curAnchorOffset;

					// When to stop because reached block max size
					anchorOffsetByMaxBlockSize = i + MAX_CHUNK_LEN;

					// Skip the minimal block size
					i += MIN_CHUNK_LEN - 1;
				}
			}

			// Handle the last and break
			if (lastLoop && !result.contains(prevAnchorOffset))
			{
				int length = (int) (totalSize - prevAnchorOffset);

				// Skip the last chunk if too short
				if (length < MIN_CHUNK_LEN)
					break;

				int sha1 = calculateSha1(buffer, (int) (prevAnchorOffset - bufferOffset), length);
				// System.out.println(String.format("%,d: 0x%08X",
				// prevAnchorOffset, sha1));

				// Add to result
				ChunkItem stampItem = globalStampList.getChunkOrAddNew(sha1, length);
				result.add(stampItem);

				break;
			}

			// Next offset, to overlap with the last found anchor
			bufferOffset = prevAnchorOffset;
		}

		return result;
	}

	/**
	 * @param buffer
	 *            Buffer that holds the data to hash. No matter where the position and limits are.
	 * @param offset
	 *            Offset in buffer's byte array (after the internal offset).
	 * @param len
	 *            How many bytes to put in the hash.
	 * @return Hash result.
	 */
	public synchronized static int calculateSha1(ByteBuffer buffer, int offset, int len)
	{
		initDigest();

		// Do SHA-1
		byte[] sha1Array;
		md.update(buffer.array(), buffer.arrayOffset() + offset, len);
		sha1Array = md.digest();

		int sha1 = (int) ((0x00ffL & sha1Array[0]) | (0x00ffL & sha1Array[1]) << 8 | (0x00ffL & sha1Array[2]) << 16 | (0x00ffL & sha1Array[3]) << 24);

		return sha1;
	}

	/**
	 * @param buffer
	 *            Buffer that holds the data to hash.
	 * @param offset
	 *            Offset in buffer's byte array .
	 * @param len
	 *            How many bytes to put in the hash.
	 * @return Hash result.
	 */
	public synchronized static int calculateSha1(byte[] buffer, int offset, int len)
	{
		initDigest();

		// Do SHA-1
		byte[] sha1Array;
		md.update(buffer, offset, len);
		sha1Array = md.digest();

		int sha1 = (int) ((0x00ffL & sha1Array[0]) | (0x00ffL & sha1Array[1]) << 8 | (0x00ffL & sha1Array[2]) << 16 | (0x00ffL & sha1Array[3]) << 24);

		return sha1;
	}

	/**
	 * @param data
	 *            Byte array of the data.
	 * @param offset
	 *            Where to check for anchor.
	 * @return True if the position contains an anchor.
	 */
	public static boolean isAnchor(byte[] data, int offset)
	{
		//TODO make it a rolling hash
		int curHash = rhf.hash(data, offset, FINGERPRINT_BYTES_LEN, 0);
		return (curHash & RabinUtils.ANCHOR_MASK) == RabinUtils.ANCHOR_MASK;
	}

	public static int calcRabinFingerprint(byte[] data, int offset)
	{
		return rhf.hash(data, offset, FINGERPRINT_BYTES_LEN, 0);
	}

	public static int getAverageChunkLen()
	{
		return AVG_CHUNK_LEN;
	}

	static long rabinHash(byte[] s)
	{
		long ret = 0;

		for (int i = 0; i < s.length; i++)
		{
			ret = ret * PRIME_BASE + s[i];
			ret %= PRIME_MOD; //don't overflow
		}

		return ret;
	}

	static int rabinSearch(byte[] needle, byte[] haystack)
	{
		//I'm using long longs to avoid overflow
		long hash1 = rabinHash(needle);
		long hash2 = 0;

		//you could use exponentiation by squaring for extra speed
		long power = 1;
		for (int i = 0; i < needle.length; i++)
			power = (power * PRIME_BASE) % PRIME_MOD;

		for (int i = 0; i < haystack.length; i++)
		{
			//add the last letter
			hash2 = hash2 * PRIME_BASE + haystack[i];
			hash2 %= PRIME_MOD;

			//remove the first character, if needed
			if (i >= needle.length)
			{
				hash2 -= power * haystack[i - needle.length] % PRIME_MOD;
				if (hash2 < 0) //negative can be made positive with mod
					hash2 += PRIME_MOD;
			}

			//match?
			if (i >= (needle.length - 1) && hash1 == hash2)
				return i - (needle.length - 1);
		}

		return -1;
	}

	static int rabinRollingAnchorCount(byte[] buffer)
	{
		int result = 0;

		long hash = 0;

		for (int i = 0; i < ROL_PACK_CLEANUP_BITS; i++)
		{
			hash = (hash << 1) ^ (0x00ffL & buffer[i]);
		}

		// Anchors
		for (int i = ROL_PACK_CLEANUP_BITS; i < buffer.length; i++)
		{
			// Check for anchor
			if ((hash & ROL_PACK_MASK) == ROL_PACK_ANCHOR)
				result++;

			//	Next hash
			//			hash = ((hash ^ ((0x00ffL & buffer[i - ROL_PACK_CLEANUP_BITS]) << ROL_PACK_SHIFT_BITS)) << 1)
			//			^ (0x00ffL & buffer[i]);
			hash = (hash << 1) ^ (0x00ffL & buffer[i]);
		}

		return result;
	}

	static int samplebyteAnchorCount(byte[] buffer)
	{
		int result = 0;

		// Anchors
		for (int i = 0; i < buffer.length; i++)
		{
			byte c = buffer[i];

			// Check for anchor
			if (c == SAMPLEBYTE_ANCHORS_1 || c == SAMPLEBYTE_ANCHORS_2 || c == SAMPLEBYTE_ANCHORS_3
					|| c == SAMPLEBYTE_ANCHORS_4 || c == SAMPLEBYTE_ANCHORS_5 || c == SAMPLEBYTE_ANCHORS_6
					|| c == SAMPLEBYTE_ANCHORS_7 || c == SAMPLEBYTE_ANCHORS_8)
			{
				result++;
			}
		}

		return result;
	}

	/**
	 * @return Anchor offset (zero based) or -1 if not found.
	 */
	static int rabinRollingNextAnchor(byte[] buffer, int offset)
	{
		return rabinRollingNextAnchor(buffer, offset, buffer.length - 1);
	}

	/**
	 * Return the offset of the next anchor.
	 * 
	 * @param offset
	 *            Offset of the last byte in the first window. If possible, the window will start before this offset.
	 * @param endOffset
	 *            Inclusive offset of the last byte in the examined window.
	 * 
	 * @return Zero-based offset of the last byte of the 48-byte anchor or -1 if not found.
	 */
	public static int rabinRollingNextAnchor(byte[] buffer, int offset, int endOffset)
	{
		long hash = 0;

		// Where to start, since we need to move back for warm-up
		int start = Math.max(0, offset - ROL_PACK_CLEANUP_BITS + 1);

		for (int i = start; i < start + ROL_PACK_CLEANUP_BITS; i++)
		{
			hash = (hash << 1) ^ (0x00ffL & buffer[i]);
		}

		// Now we have the first valid hash ready for use

		//		System.out.println(String.format("%6d: %64s", offset, Long.toBinaryString(hash)));

		// Start shifting
		for (int i = start + ROL_PACK_CLEANUP_BITS;; i++)
		{
			// Check for anchor
			if ((hash & ROL_PACK_MASK) == ROL_PACK_ANCHOR)
				return i - 1;

			if (i > endOffset)
				return -1;

			hash = ((hash ^ ((0x00ffL & buffer[i - ROL_PACK_CLEANUP_BITS]) << ROL_PACK_SHIFT_BITS)) << 1)
					^ (0x00ffL & buffer[i]);
		}
	}

	/**
	 * @return Value at the given offset
	 */
	static long rabinRollingValueAt(byte[] buffer, int startOffset, int valueOffset)
	{
		long hash = 0;

		// Where to start
		int start = Math.max(0, startOffset - ROL_PACK_CLEANUP_BITS + 1);

		for (int i = start; i < start + ROL_PACK_CLEANUP_BITS; i++)
		{
			hash = (hash << 1) ^ (0x00ffL & buffer[i]);
		}

		for (int i = start + ROL_PACK_CLEANUP_BITS; i <= valueOffset; i++)
		{
			hash = ((hash ^ ((0x00ffL & buffer[i - ROL_PACK_CLEANUP_BITS]) << ROL_PACK_SHIFT_BITS)) << 1)
					^ (0x00ffL & buffer[i]);
		}

		//		System.out.println(String.format("%6d: %64s", startOffset, Long.toBinaryString(hash)));

		return hash;
	}
}
