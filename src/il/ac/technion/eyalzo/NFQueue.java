package il.ac.technion.eyalzo;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * A NetFilter Queue class. 
 * 
 * @see test.NFQTest
 */
public class NFQueue
{
	/**
	 * Location in /proc indicating if ethernet header is included in packet's payload
	 */
	private static final String PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD = "/proc/sys/net/netfilter/nfqueue-include-etherent-header-in-payload";

	private final int queueNum;

	//
	// Constants.
	//

	/**
	 * queue verdicts. Values for these are defined in netfilter.h.
	 */
	public enum Verdict
	{
		NF_DROP(0), NF_ACCEPT(1), NF_QUEUE(3), NF_REPEAT(4);

		private int m_value;

		private Verdict(int value)
		{
			m_value = value;
		}

		int getValue()
		{
			return m_value;
		}
	};

	/**
	 * The packet copy modes.
	 */
	public enum CopyMode
	{
		COPY_NONE, // = 0
		COPY_META, // = 1
		COPY_PACKET, // = 2
	};

	/**
	 * The native lib name.
	 */
	private final static String JNI_LIB_NAME = "nfqjni";

	/**
	 * The error message to send if loop has been entered twice concurrently.
	 */
	private static final String LOOP_TWICE_MSG = "NFQueue.loop() must be called concurrently by two threads";

	/**
	 * Version of the java code, will be matched against the compiled native version to verify compatibility.
	 */
	private static final int NFQ_JAVA_VERSION = 4;

	/**
	 * Indicates whether ethernet header is included in the packet's payload -1 is unknown, 0 not included, 1 included.
	 */
	private int isEthHdrIncludedInPayload = -1;

	//
	// Members.
	//

	/**
	 * The listener to queue events.
	 */
	private NFQueueListener m_listener = null;

	/**
	 * The buffer to put the packet payload in.
	 */
	private byte[] m_payloadBuf;

	/**
	 * A flag indicating that the JNI library was already loaded.
	 */
	private static boolean c_libLoaded = false;

	/**
	 * The pointer to the actual native NFQueue object, as a long.
	 */
	private long m_peer = 0;

	/**
	 * A flag indicating that we are currently inside a loop.
	 */
	private boolean m_isLooping = false;

	//
	// Operations.
	//

	/**
	 * Create a new NFQueue object.
	 * 
	 * @throw UnsatisfiedLinkError if the native lib could not be loaded.
	 */
	public NFQueue(int queueNum, int queueLen) throws UnsatisfiedLinkError
	{
		loadNativeLib();
		this.queueNum = queueNum;
		m_peer = newNativeQueue((short) queueNum, queueLen);
	}

	/**
	 * Create a new NFQueue object, associated with the main queue (queue 0).
	 * 
	 * @throw UnsatisfiedLinkError if the native lib could not be loaded.
	 */
	public NFQueue() throws UnsatisfiedLinkError
	{
		this((short) 0, 1024);
	}

	/**
	 * Destroy the native peer object. Since this object wraps a C++ object, it needs to be destroyed when no longer
	 * needed. No further calls to this object are allowed after this call.
	 */
	public void delete()
	{
		if (m_peer != 0)
		{
			deleteNativeQueue(m_peer);
			m_peer = 0;
		}
	}

	/**
	 * Finalize method: if the user didn't delete the peer, do it ourselves here. Don't rely on this! call delete() when
	 * done with the queue.
	 */
	@Override
	protected void finalize()
	{
		if (m_peer != 0)
		{
			delete();
		}
	}

	/**
	 * The Netfilter_Queue main loop: open the queue, then loop to dispatch packets forever.
	 * 
	 * @return 0 if all went well, a negative value indicating the error otherwise.
	 */
	public int loop() throws NFQueueException
	{
		synchronized (this)
		{
			if (m_isLooping)
			{
				if (m_listener != null)
				{
					m_listener.onPacketReceiveError(LOOP_TWICE_MSG);
				}
				return -1;
			}
			m_isLooping = true;
		}

		assertValid();

		// According to post I found in the Internet it is better to unbind first, to try to prevent "File exists" error that we get sometimes
		nativeBreakLoop(m_peer);

		int retCode = nativeLoop(m_peer);
		if (retCode != 0 && m_listener != null)
		{
			m_listener.onPacketReceiveError(getLastErrorMsg());
		}

		synchronized (this)
		{
			m_isLooping = false;
		}
		return retCode;
	}

	/**
	 * Break out of a running loop.
	 */
	public void breakLoop() throws NFQueueException
	{
		assertValid();
		nativeBreakLoop(m_peer);
	}

	/**
	 * Set the copy mode - whether to copy into userspace the entire packet, just the header, or nothing at all. Copying
	 * less would mean better performance.
	 * 
	 * @param copyPacket
	 *            The copy mode.
	 * @param numBytges
	 *            For COPY_PACKET mode, this is the maximum number of bytes to copy. This is also effected by the native
	 *            library's buffer size, which defaults to 4096 bytes. If you want higher values, you'll need to
	 *            recompile the library.
	 * @return true for success, false for failure. This method will fail if we are currently looping waiting for
	 *         events.
	 */
	public boolean setCopyMode(NFQueue.CopyMode copyPacket, int maxBytes)
			throws NFQueueException
	{
		assertValid();
		synchronized (this)
		{
			if (m_isLooping)
				return false;
			setCopyMode(m_peer, (byte) copyPacket.ordinal(), maxBytes);
			return true;
		}
	}

	/**
	 * Set the internal netlink socket's receive buffer size
	 * 
	 * @param bufSize
	 *            Number of bytes to allocate
	 * @return true for success, false for failure. This method will fail if we are currently looping waiting for
	 *         events.
	 */
	public boolean setRecvBufferSize(int bufSize) throws NFQueueException
	{
		assertValid();
		synchronized (this)
		{
			if (m_isLooping)
				return false;
			setRecvBufferSize(m_peer, bufSize);
			return true;
		}
	}

	/**
	 * A handler for received packets. Called from the native code. Should return the verdict for the packet.
	 * 
	 * @param packetHandle
	 *            A packet handle that can be used for getting the packet information itself, but that would mean
	 *            allocating a new buffer for every call to this method.
	 * @return a verdict value.
	 */
	private int onPacketReceived(long packetHandle)
	{
		Verdict verdict = Verdict.NF_DROP;
		if (m_listener != null)
		{
			if (m_payloadBuf == null)
			{
				m_payloadBuf = new byte[4096];
			}

			int bytesRead = getPacketPayload(packetHandle, m_payloadBuf);
			if (bytesRead >= 0)
			{
				try
				{
					verdict = m_listener.onPacketReceived(m_payloadBuf,
							bytesRead);
				} catch (Throwable t)
				{
					// Since the native code won't catch this, we should... Even
					// if we can't do too much with it.
					t.printStackTrace();
				}
			} else
			{
				try
				{
					m_listener.onPacketReceiveError(getLastErrorMsg());
				} catch (Throwable t)
				{
					// Again, not much we can do...
					t.printStackTrace();
				}
			}
		}
		return verdict.getValue();
	}

	/**
	 * Set the listener to queue events.
	 */
	public void setListener(NFQueueListener listener)
	{
		m_listener = listener;
	}

	/**
	 * Make sure that this object has a valid peer. Throw an exception otherwise.
	 */
	private void assertValid() throws NFQNoPeerException
	{
		if (m_peer == 0)
		{
			throw new NFQNoPeerException("Object has no peer. Deleted?");
		}
	}

	/**
	 * Load the native lib. The library will be loaded automatically by the constructor when it is needed. Still, it
	 * seemed like making this method public will allow more flexibility.
	 * 
	 * @throws UnsatisfiedLinkError
	 *             If the JNI library could not be loaded.
	 */
	public static void loadNativeLib() throws UnsatisfiedLinkError
	{
		if (!c_libLoaded)
		{
			System.loadLibrary(JNI_LIB_NAME);

			int nativeVersion = NFQueue.getNativeVersion();
			if (nativeVersion != NFQ_JAVA_VERSION)
			{
				System.err.println("Native version: " + nativeVersion
						+ " does not match Java code version: "
						+ NFQ_JAVA_VERSION);

				throw new UnsatisfiedLinkError(
						"found incorrect version of the " + JNI_LIB_NAME
								+ " so file (" + nativeVersion
								+ "), while expecting " + NFQ_JAVA_VERSION);
			}

			c_libLoaded = true;
		}
	}

	//
	// Calls to the actual peer methods.
	//

	private native long newNativeQueue(short queueNum, int queueLen);

	private native void deleteNativeQueue(long peer);

	private native int nativeLoop(long peer);

	private native void nativeBreakLoop(long peer);

	private native void setCopyMode(long peer, byte mode, int range);

	private native void setRecvBufferSize(long peer, int bufSize);

	private native static int getNativeVersion();

	//
	// Query methods for querying the packet info.
	//

	/**
	 * Get the payload for a received packet. That is, the actual packet data. The data is returned in a caller-supplied
	 * buffer, for optimization.
	 * 
	 * @param packetHandle
	 *            The packet handle.
	 * @param buffer
	 *            (output parameter) the buffer to store the data in.
	 * @return the size of the data actually returned, or -1 on failure.
	 */
	private native static int getPacketPayload(long packetHandle, byte[] buffer);

	/**
	 * Return an error string describing the last system error.
	 */
	private native static String getLastErrorMsg();

	/**
	 * Determines whether the packet's payload received from the kernel includes the ethernet header.
	 */
	public boolean isEthHdrIncludedInPayload()
	{
		if (isEthHdrIncludedInPayload != -1)
		{
			return isEthHdrIncludedInPayload == 1 ? true : false;
		}

		// Assume not supported to begin with
		isEthHdrIncludedInPayload = 0;

		// First time - read the /proc to determine if it is supported and enabled.
		FileInputStream in;
		try
		{
			in = new FileInputStream(PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD);
			if (in.read() == '1')
			{
				isEthHdrIncludedInPayload = 1;
			}
			in.close();
		} catch (FileNotFoundException e)
		{
			System.err.println("Failed to read from "
					+ PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD);
			// e.printStackTrace();
		} catch (IOException e)
		{
			System.err.println("Failed to read from "
					+ PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD);
			// e.printStackTrace();
		}

		return isEthHdrIncludedInPayload == 1 ? true : false;
	}

	/**
	 * Attempts to modify the system settings and include/exclude the ethernet header in the payload's packet.
	 * 
	 * @return the current setting after it has been modified.
	 */
	public boolean setIsEthHdrIncludedInPayload(boolean include)
	{
		// Reset existing setting
		isEthHdrIncludedInPayload = -1;

		// Open the /proc in order to update the system's settings
		FileOutputStream out;
		try
		{
			out = new FileOutputStream(PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD);
			out.write(include ? '1' : '0');
			out.close();
		} catch (FileNotFoundException e)
		{
			System.err.println("Failed to write to "
					+ PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD);
			// e.printStackTrace();
		} catch (IOException e)
		{
			System.err.println("Failed to write to "
					+ PROC_NFQ_INCLUDE_ETH_HDR_IN_PAYLOAD);
			// e.printStackTrace();
		}

		return isEthHdrIncludedInPayload();
	}

	public int getQueueNum()
	{
		return queueNum;
	}
};
