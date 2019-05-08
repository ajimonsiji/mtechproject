package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.NFQueue;
import il.ac.technion.eyalzo.NFQueue.CopyMode;
import il.ac.technion.eyalzo.NFQueue.Verdict;
import il.ac.technion.eyalzo.NFQueueException;
import il.ac.technion.eyalzo.NFQueueListener;
import il.ac.technion.eyalzo.common.LoggingUtil;
import il.ac.technion.eyalzo.net.TCPPacket;
import il.ac.technion.eyalzo.pack.conns.Machine;
import il.ac.technion.eyalzo.pack.conns.RemoteMachineList;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Call {@link #pauseCapture()} before {@link Thread#start()} if you don't want this one to capture on startup.
 */
public class CaptureThread extends Thread implements NFQueueListener
{
	private Logger log;
	private RemoteMachineList machineList;
	/**
	 * Maximum number of bytes ion captured packet. Also needed for connection processing buffer.
	 */
	public static final int MAX_PACKET_BYTES = 4096;
	private static int MAX_NFQUEUE_MSG_COUNT = 1000;
	private NFQueue nfQueue;
	private TCPPacket tcpPacket;
	/**
	 * 1-based serial number of capture threads.
	 */
	private static int count = 1;

	//
	// Statistics
	//
	private static long statErrorException;
	private static long statErrorRead;

	/**
	 * Number of packets. See {@link #dirOut} for direction.
	 */
	private long statPacket;
	/**
	 * Number of forwarded packets due to duplicate detection. Not counted anywhere else.
	 */
	private long statPacketsDupElim;
	/**
	 * Number of dropped packets.
	 */
	private long statPacketsDrop;

	//
	// Artificial packet loss
	//
	public static Random randPacketLoss;

	//
	// Statistics - Directions
	//
	/**
	 * Captured traffic belongs to senders.
	 */
	private final boolean sideSender;
	private final boolean dirOut;

	//
	// Statistics - Bytes
	//
	/**
	 * All the IP header and payload bytes contained in packets seen by the application, meaning the bytes sent to the
	 * application by iptables. That includes IP and TCP headers and also TCP retransmissions.
	 */
	private long statBytesRawIp;
	/**
	 * TCP payload bytes, includes retransmissions.
	 */
	private long statBytesTcpPayload;

	public CaptureThread(QueueNum queueNum, RemoteMachineList serverList)
	{
		// Set serial number in thread name for web-gui monitoring
		super(String.format("Capture%02d-%s", CaptureThread.count, queueNum.name()));

		this.sideSender = queueNum.sideSender;
		this.dirOut = queueNum.dirOut;
		this.machineList = serverList;

		CaptureThread.count++;

		log = Logger.getAnonymousLogger();
		log.setLevel(Level.ALL);

		tcpPacket = new TCPPacket(MAX_PACKET_BYTES);

		nfQueue = initNfqueue(this, queueNum.queueNum, MAX_NFQUEUE_MSG_COUNT, null);
	}

	@Override
	/**
	 * The main loop.
	 */
	public void run()
	{
		System.out.println("Start capture on queue " + nfQueue.getQueueNum() + " "
				+ (sideSender ? "sender" : "receiver") + "-" + (this.dirOut ? "out" : "in"));

		if (Main.lossRate > 0)
			randPacketLoss = new Random(System.currentTimeMillis());

		int retLoop = 0;

		while (true)
		{
			try
			{
				retLoop = nfQueue.loop();
			} catch (Throwable t)
			{
				try
				{
					log.log(Level.SEVERE, "Problem reading packets: ", t);
				} catch (Exception e)
				{
				}
			}

			log.log(Level.SEVERE, "Exiting capture loop, code {0}", retLoop);

			if (retLoop == -5)
			{
				log.log(Level.SEVERE,
						"Need to update the net.core.rmem_max configuration in /etc/sysctl.conf. Need at least "
								+ MAX_NFQUEUE_MSG_COUNT * (4096 + 1024));

				System.exit(0);
			}

			System.exit(1);
		}

	}

	@Override
	public void onPacketReceiveError(String errMsg)
	{
		statErrorRead++;
		System.err.println(this.getName() + ": onPacketReceiveError \"" + errMsg + "\"");
	}

	@Override
	public Verdict onPacketReceived(byte[] rawIpPacket, int ipPayloadLength)
	{
		Verdict verdict = Verdict.NF_ACCEPT;

		// Count packets here, for double check
		statPacket++;
		statBytesRawIp += ipPayloadLength;

		//		System.err.println(String.format("%s %s %,d", this.sideSender ? "SND"
		//				: "RCV", this.dirOut ? "=>" : "<=", statPacket));

		try
		{
			verdict = handleMessage(rawIpPacket);
			if (verdict == Verdict.NF_DROP)
				statPacketsDrop++;
		} catch (Exception e)
		{
			statErrorException++;
			if (log.isLoggable(Level.WARNING))
			{
				StringWriter sWriter = new StringWriter();
				e.printStackTrace(new PrintWriter(sWriter));
				LoggingUtil.log(log, Level.WARNING, "Java exception when handling message {0}: {1}", statPacket,
						sWriter.getBuffer().toString());
			}
		}

		return verdict;
	}

	public static NFQueue initNfqueue(NFQueueListener listener, short queueNum, int queueLen, Logger log)
	{
		NFQueue nfQueue = null;

		try
		{
			nfQueue = new NFQueue(queueNum, queueLen);
			nfQueue.setListener(listener);
			if (!nfQueue.setCopyMode(CopyMode.COPY_PACKET, 4096))
			{
				LoggingUtil.log(log, Level.SEVERE, "NFQueue error, failed to set copy mode on queue " + queueNum);
				System.exit(1);
			}
		} catch (UnsatisfiedLinkError e)
		{
			System.err.println("Library file is missing. Workaround:\n"
					+ "1. These two are installed (emerge): net-libs/libnfnetlink net-libs/libnetfilter_queue\n"
					+ "2. Make sure that java run is using  -Djava.library.path=/root/workspace/NFQueueJNI/dist:/root/workspace/rocksaw-1.0.1/lib\n" + "Error: " + e);
			System.exit(1);
		} catch (NFQueueException e)
		{
			LoggingUtil.log(log, Level.SEVERE, "NFQueue exception on queue {0}:\n{1}", queueNum, e);
			System.exit(1);
		}

		return nfQueue;
	}

	/**
	 * @param rawIpPacket
	 * @return Verdict if to drop or accept the packet.
	 * @throws UnknownHostException
	 */
	private Verdict handleMessage(byte[] rawIpPacket) throws UnknownHostException
	{
		// Do not capture altered packets
		if (dirOut && PackUtils.hasPack(rawIpPacket))
		{
			statPacketsDupElim++;
			return Verdict.NF_ACCEPT;
		}

		// Put the raw IP packet in a TCP structure for further analysis
		tcpPacket.setData(rawIpPacket, false);
		InetSocketAddress otherAddr;
		if (this.dirOut)
		{
			otherAddr = new InetSocketAddress(tcpPacket.getDestinationAsInetAddress(), tcpPacket.getDestinationPort());
		} else
		{
			otherAddr = new InetSocketAddress(tcpPacket.getSourceAsInetAddress(), tcpPacket.getSourcePort());
		}

		// Count the number of TCP payload bytes
		statBytesTcpPayload += tcpPacket.getTCPDataByteLength();

		// Find the remote machine or add new
		Machine machine = machineList.getMachineOrAddNew(otherAddr);
		return machine.processPacket(tcpPacket, rawIpPacket, dirOut);
	}

	/**
	 * @return Number of seen packets. Side and direction can be determined with other methods.
	 */
	public long getStatPackets()
	{
		return statPacket;
	}

	public long getStatBytesRawIp()
	{
		return this.statBytesRawIp;
	}

	/**
	 * @return Receiver side only. Incoming real bytes that match existing chunk.
	 */
	public long getStatBytesKnown()
	{
		// Relevant only to receiver-in
		if (sideSender || dirOut)
			return 0;

		return this.machineList.getStatBytesKnown();
	}

	/**
	 * Number of bytes in PACK command PRED.
	 * 
	 * @return Receiver: predictions sent out. Sender: predictions inserted to inbox.
	 */
	public long getStatBytesPredSent()
	{
		// Must check it first because statistics in the machine list has a direction
		if (sideSender && dirOut || !sideSender && !dirOut)
			return 0;

		return this.machineList.getStatBytesPackPred();
	}

	public long getStatBytesPredMatch()
	{
		// Relevant only to receiver-in
		if (sideSender || dirOut)
			return 0;

		return this.machineList.getStatBytesPredMatch();
	}

	public long getStatBytesPredAck()
	{
		// Relevant only to sender-out and receiver-in
		if (sideSender && !dirOut || !sideSender && dirOut)
			return 0;

		return this.machineList.getStatBytesPredAck();
	}

	/**
	 * @return Sender side only. Number of bytes in real data sent out while it overlaps ranges of predictions in the
	 *         inbox. 0 if receiver side.
	 */
	public long getStatBytesPredOverlap()
	{
		// Relevant only to sender-out
		if (!sideSender || !dirOut)
			return 0;

		return this.machineList.getStatBytesPredOverlap();
	}

	/**
	 * 
	 * @return Local machines side: true for sender and false for receiver.
	 */
	public boolean isSideSender()
	{
		return this.sideSender;
	}

	public boolean isDirOut()
	{
		return this.dirOut;
	}

	public long getStatPacketsDropped()
	{
		return this.statPacketsDrop;
	}

	public long getStatPacketsDupElim()
	{
		return this.statPacketsDupElim;
	}

	/**
	 * 
	 * @return TCP payload bytes, includes retransmissions.
	 */
	public long getStatBytesTcpPayload()
	{
		return this.statBytesTcpPayload;
	}
}
