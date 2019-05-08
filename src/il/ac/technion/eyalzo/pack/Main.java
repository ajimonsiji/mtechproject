package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.pack.conns.RemoteMachineList;
import il.ac.technion.eyalzo.pack.files.DirList;
import il.ac.technion.eyalzo.pack.files.DiskScanThread;
import il.ac.technion.eyalzo.pack.spoof.SpoofThread;
import il.ac.technion.eyalzo.pack.stamps.GlobalChunkList;
import il.ac.technion.eyalzo.pack.stamps.StreamsChainList;
import il.ac.technion.eyalzo.webgui.WebGuiHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;

import com.sun.net.httpserver.HttpServer;

public class Main
{
	private static final long serialVersionUID = 1L;

	//
	// Data structures
	//

	/**
	 * Global chunk list.
	 */
	public static GlobalChunkList chunks;
	/**
	 * Global chain list.
	 */
	public static StreamsChainList chains;
	/**
	 * Global senders machine list as the basis for the global connection list.
	 */
	private RemoteMachineList remoteMachineListSnd;
	/**
	 * Global receivers machine list as the basis for the global connection list.
	 */
	private RemoteMachineList remoteMachineListRcv;

	public static DirList dirList = new DirList();
	public static LinkedList<CaptureThread> captureThreads = new LinkedList<CaptureThread>();
	public static DiskScanThread diskScan;
	public static PackReporter reporter;

	//
	// Debug
	//
	public static int debugLevel = 5;
	/**
	 * How many of each 100 packets to sign with sha1.
	 */
	public static int debugSha1 = 0;
	/**
	 * If greater than zero, this is the packet loss ratio (1 to ...).
	 */
	public static int lossRate = 0;
	private static final boolean DEBUG_ALL = false;
	private static final boolean DEBUG_DIRS = false;

	public Main(String deviceName, boolean noNetwork, boolean noDiskScan, boolean restoreChains) throws IOException
	{
		remoteMachineListSnd = new RemoteMachineList(true);
		remoteMachineListRcv = new RemoteMachineList(false);

		//
		// Web GUI
		//
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/", new WebGuiHandler(remoteMachineListSnd, remoteMachineListRcv));
		server.setExecutor(null); // creates a default executor
		server.start();

		//
		// Stamps
		//
		chunks = new GlobalChunkList();
		chains = new StreamsChainList(restoreChains);

		//
		// Threads
		//
		new CleanupThread(remoteMachineListSnd, remoteMachineListRcv).start();
		new TimeoutThread(remoteMachineListRcv).start();

		diskScan = new DiskScanThread(DEBUG_DIRS || DEBUG_ALL);
		if (!noDiskScan)
			diskScan.start();

		reporter = new PackReporter();
		reporter.start();

		//
		// Address
		//
		InetAddress myAddr = null;
		if (noNetwork)
		{
			System.err.println("Special debug mode \"No Network\"!");
		} else
		{
			myAddr = NetUtils.getNetworkAddress(deviceName);
			if (myAddr == null)
			{
				System.err.println("Failed to detect IPv4 address!");
				// System.exit(0);
			}
		}

		System.out.println("Local address: " + myAddr + " on " + deviceName);

		//
		// Spoof
		//
		if (deviceName != null)
		{
			SpoofThread.init(deviceName);
		}

		//
		// Capture
		//
		for (QueueNum curQueue : QueueNum.values())
		{
			// Machine list is reversed because it holds the list of the remote
			CaptureThread captureThread = new CaptureThread(curQueue, curQueue.sideSender ? remoteMachineListRcv
					: remoteMachineListSnd);
			captureThreads.add(captureThread);
			captureThread.start();
			
			// It usually prevents the "File exists" error that happens in fast machines
			try
			{
				Thread.sleep(100);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		while (true)
			try
			{
				Thread.sleep(10000);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws CaptureDeviceLookupException
	 */
	public static void main(final String[] args)
	{
		boolean noNetwork = false;
		boolean noDiskScan = false;
		boolean noRestoreChains = false;
		String deviceName = "lo";

		for (String curArg : args)
		{
			if ("nonetwork".equalsIgnoreCase(curArg))
				noNetwork = true;
			else if ("nodiskscan".equalsIgnoreCase(curArg))
				noDiskScan = true;
			else if ("norestorechains".equalsIgnoreCase(curArg))
				noRestoreChains = true;
			else if (curArg.startsWith("eth") || curArg.startsWith("wlan") || curArg.equals("lo"))
				deviceName = curArg;
			else if ("debugsha1".equalsIgnoreCase(curArg))
				debugSha1 = 100;
			else if (curArg.startsWith("loss="))
				lossRate = Integer.parseInt(curArg.split("=")[1]);
		}

		try
		{
			new Main(deviceName, noNetwork, noDiskScan, !noRestoreChains);
		} catch (IOException e)
		{
			e.printStackTrace();
			System.exit(-1);
		}

		System.out.println("Complete.");
	}
}
