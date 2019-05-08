package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.net.TCPPacket;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Duplicates eliminator remembers packets by some of their fields to prevent multiple captures of the same or altered
 * packet. It is especially needed when packets are being dropped and then transmitted again with a change over the raw
 * socket.
 */
public class DupElimOut
{
	/**
	 * The maximal number of recent packets to save here.
	 */
	private static final int MAX_PACKETS = 50;
	/**
	 * Limited-size list of recent packets by their internal hash.
	 */
	private LinkedHashSet<Long> recentPackets = new LinkedHashSet<Long>();
	private final boolean sideSender;

	public DupElimOut(boolean sideSender, boolean dirOut)
	{
		this.sideSender = sideSender;
	}

	/**
	 * 
	 * @param tcpPacket
	 *            Received packet to be registered and also searched for in the list.
	 * @return True if that packet was already seen before (recently).
	 */
	public synchronized boolean exists(TCPPacket tcpPacket)
	{
		long tcpSeq = sideSender ? tcpPacket.getSequenceNumber() : tcpPacket.getAckNumber();
		// Add TCP payload length to the hash, in case an empty packet was sent before
		long hash = tcpSeq | (tcpPacket.getTCPDataByteLength() << 32);

		if (recentPackets.contains(hash))
		{
			//			System.err.println(sideSender + " ***" + hash + ": " + tcpPacket);
			return true;
		}

		//		System.err.println(sideSender + " " + hash + ": " + tcpPacket);

		recentPackets.add(hash);

		if (recentPackets.size() > MAX_PACKETS)
		{
			// Remove the first item
			Iterator<Long> it = recentPackets.iterator();
			it.next();
			it.remove();
		}

		return false;
	}
}
