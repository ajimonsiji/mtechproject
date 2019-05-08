package il.ac.technion.eyalzo.pack.conns;

import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Remote machine list by IP and port, for connections database.
 */
public class RemoteMachineList
{
	/**
	 * Remote machines are senders.
	 * <p>
	 * When true the remote list belongs to the receiver side, meaning that the machines are sender.
	 */
	private final boolean remoteSideSender;

	/**
	 * Machine list. Key is IP and port. Value is a machine that holds a list of TCP connections and an optional host
	 * name (if found in an HTTP session).
	 */
	private HashMap<InetSocketAddress, Machine> machines = new HashMap<InetSocketAddress, Machine>();

	public RemoteMachineList(boolean remoteSideSender)
	{
		this.remoteSideSender = remoteSideSender;
	}

	/**
	 * @param addr
	 *            IP and port of the remote machine.
	 * @return Machine record, from existing list or newly added.
	 */
	public Machine getMachineOrAddNew(InetSocketAddress addr)
	{
		synchronized (machines)
		{
			// Look for existing record
			Machine curMachine = machines.get(addr);

			// If does not exist then add a new record
			if (curMachine == null)
			{
				curMachine = new Machine(!remoteSideSender, addr);
				machines.put(addr, curMachine);
			}

			// Return the new or existing record
			return curMachine;
		}
	}

	/**
	 * @param addr
	 *            IP and port of the remote machine.
	 * @return Machine record or null if this machine is unknown.
	 */
	public Machine getMachine(InetSocketAddress addr)
	{
		synchronized (machines)
		{
			return machines.get(addr);
		}
	}

	/**
	 * @return Number of machines in the list. Note: it may be decreased by { {@link #cleanup(long)}.
	 */
	public int size()
	{
		synchronized (machines)
		{
			return machines.size();
		}
	}

	/**
	 * Handle connections timeout and remove inactive machines.
	 * <p>
	 * It locks the machine list and walks through the entire connection list per machine, so it should not be called
	 * too often.
	 */
	public void cleanup(long connTtlMillis)
	{
		long lastAllowedActionTime = System.currentTimeMillis() - connTtlMillis;

		synchronized (machines)
		{
			Iterator<Machine> it = machines.values().iterator();
			while (it.hasNext())
			{
				Machine curMachine = it.next();
				// Cleanup connections
				curMachine.cleanup(lastAllowedActionTime);
				// Remove server if nothing happened for too long
				if (curMachine.isInactive(lastAllowedActionTime))
				{
					it.remove();
				}
			}
		}
	}

	/**
	 * @return Duplicate of the full machine list.
	 */
	public LinkedList<Machine> getMachinesDup()
	{
		synchronized (machines)
		{
			return new LinkedList<Machine>(machines.values());
		}
	}

	/**
	 * @return Number of TCP connections still registered for all the machines together. Time consuming, so be careful.
	 */
	public int getConnectionsCount()
	{
		int result = 0;

		LinkedList<Machine> machinesDup = getMachinesDup();
		for (Machine curMachine : machinesDup)
		{
			result += curMachine.getConnsCount();
		}

		return result;
	}

	public DisplayTable webGuiMachineList(String machineDetailsLink)
	{
		DisplayTable table = new DisplayTable();

		table.addCol("Machine", "Remote machine IP address", true);
		table.addCol("Sender<br>host name", "Optional first host name mentioned in HTTP request", true);
		table.addCol("Conns", "Active connections", false);
		table.addCol("Known", "Bytes in known chunks (even if not predicted)", false);
		table.addCol("Pred<br>sent", "Bytes in PACK predictions", false);
		table.addCol("Pred<br>match", "Real bytes in matched predictions", false);

		List<Machine> machinesDup = getMachinesDup();

		for (Machine curMachine : machinesDup)
		{
			table.addRow(null);

			// Machine
			String addrString = curMachine.addr.toString().substring(1);
			table.addCell(curMachine.addr, machineDetailsLink + addrString);
			// Host name
			table.addCell(curMachine.hostName);
			// Connections
			table.addCell(curMachine.getConnsCount());
			table.addCell(curMachine.getStatBytesKnown());
			table.addCell(curMachine.getStatBytesPackPred());
			table.addCell(curMachine.getStatBytesPredMatch());
		}

		return table;
	}

	/**
	 * 
	 * @param connDetailsLink
	 *            Link to connection details, ending with connection serial parameter and the equals sign.
	 * @return Table with all the connections with all the remote machines.
	 */
	public DisplayTable webGuiConnList(String connDetailsLink)
	{
		DisplayTable table = new DisplayTable();

		table.addCol("Serial", "Connection local serial by start time", false);
		table.addColNum("Last", "Last time a packet was seen on this connection", true, false, true, null, " mSec");
		table.addColNum(remoteSideSender ? "Receiver<br>port" : "Sender<br>port", "Local port number", true, false,
				false);
		table.addCol("Other (" + (remoteSideSender ? "sender" : "receiver") + ")", "Remote machine", true);
		table.addCol("Sender<br>host name", "Optional first host name mentioned in HTTP request", true);
		table.addCol("Events", "Events as HTTP requests and responses", true);
		// If receiver-in
		if (remoteSideSender)
		{
			table.addCol("Received<br>by seq", "Received bytes by TCP sequence", false);
			table.addColNum("Speed<br>by seq", "Received bps by TCP sequence", false, true, true, null, " bps");
			table.addCol("Known", "Real bytes received and matched cached local chunks", false);
			table.addCol("PACK PRED", "Total bytes in PRED commands sent to sender", false);
			table.addCol("Virtual<br>in", "Total bytes in PRED ACK commands received", false);
		} else
		{
			table.addCol("Sent<br>by seq", "Sent bytes by TCP sequence", false);
			table.addColNum("Speed<br>by seq", "Sent bps by TCP sequence", false, true, true, null, " bps");
			table.addCol("PACK<br>PRED", "Total bytes in PRED commands received from receiver", false);
			table.addCol("PRED<br>overlap", "Sent real bytes that overlap ranges in predictions from the receiver",
					false);
			table.addCol("Virtual<br>out", "Total bytes in PRED ACK commands sent", false);
		}

		long now = System.currentTimeMillis();

		List<Machine> machinesDup = getMachinesDup();

		for (Machine curMachine : machinesDup)
		{
			Map<InetSocketAddress, TcpConn> connsDup = curMachine.getConnsDup();
			Iterator<Entry<InetSocketAddress, TcpConn>> it = connsDup.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<InetSocketAddress, TcpConn> entry = it.next();

				InetSocketAddress addr = entry.getKey();
				TcpConn conn = entry.getValue();

				table.addRow(null);
				// Serial
				table.addCell(conn.getSerial(), connDetailsLink + conn.getSerial());
				// Last action
				table.addCell(now - conn.lastPacketTime);
				// Local port
				table.addCell(addr.getPort());
				// Remote
				table.addCell(curMachine.addr);
				// Host name
				table.addCell(curMachine.hostName);
				// Events
				table.addCell(conn.getEventsCount());
				// If receiver
				if (remoteSideSender)
				{
					// Received
					table.addCell(conn.getStatBytesReceived());
					// Received speed
					table.addCell(conn.getStatBitsPerSecReceived());
					// Known
					table.addCell(((TcpConnRcv) conn).statBytesKnown);
					// PACK pred
					table.addCell(conn.statBytesPackPred);
				} else
				{
					// Sent
					table.addCell(conn.getStatBytesSent());
					// Sent speed
					table.addCell(conn.getStatBitsPerSecSent());
					// PACK pred
					table.addCell(conn.statBytesPackPred);
					// Pred overlap
					table.addCell(((TcpConnSnd) conn).statBytesPredOverlap);
				}
				// PACK ACK
				table.addCell(conn.statBytesPredAck);
			}
		}

		return table;
	}

	/**
	 * @param serial
	 *            Internal serial number of the connection.
	 * @return Connection record or null if not found. Time consuming and list lock, so be careful.
	 */
	public TcpConn getConnBySerial(int serial)
	{
		synchronized (machines)
		{
			for (Machine curMachine : machines.values())
			{
				TcpConn conn = curMachine.getConnBySerial(serial);
				if (conn != null)
					return conn;
			}
		}
		return null;
	}

	/**
	 * @return Receiver side only. Incoming real bytes that match existing chunk.
	 */
	public long getStatBytesKnown()
	{
		long result = 0;

		synchronized (machines)
		{
			for (Machine curMachine : machines.values())
				result += curMachine.getStatBytesKnown();
		}

		return result;
	}

	/**
	 * @return Sender side only. Number of bytes in real data sent out while it overlaps ranges of predictions in the
	 *         inbox. 0 if receiver side.
	 */
	public long getStatBytesPredOverlap()
	{
		long result = 0;

		synchronized (machines)
		{
			for (Machine curMachine : machines.values())
				result += curMachine.getStatBytesPredOverlap();
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

		synchronized (machines)
		{
			for (Machine curMachine : machines.values())
				result += curMachine.getStatBytesPackPred();
		}

		return result;
	}

	public long getStatBytesPredMatch()
	{
		long result = 0;

		synchronized (machines)
		{
			for (Machine curMachine : machines.values())
				result += curMachine.getStatBytesPredMatch();
		}

		return result;
	}

	public long getStatBytesPredAck()
	{
		long result = 0;

		synchronized (machines)
		{
			for (Machine curMachine : machines.values())
				result += curMachine.getStatBytesPredAck();
		}

		return result;
	}

	public void releaseTimeoutBuffers(int sndChunkBufferTimeoutMillis)
	{
		long lastAllowedActionTime = System.currentTimeMillis() - sndChunkBufferTimeoutMillis;

		synchronized (machines)
		{
			Iterator<Machine> it = machines.values().iterator();
			while (it.hasNext())
			{
				Machine curMachine = it.next();
				// Cleanup connections
				curMachine.releaseTimeoutBuffers(lastAllowedActionTime);
			}
		}
	}
}
