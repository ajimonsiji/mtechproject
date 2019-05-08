package il.ac.technion.eyalzo.webgui;

import il.ac.technion.eyalzo.pack.CaptureThread;
import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.SimuResult;
import il.ac.technion.eyalzo.pack.conns.Machine;
import il.ac.technion.eyalzo.pack.conns.RemoteMachineList;
import il.ac.technion.eyalzo.pack.conns.TcpConn;
import il.ac.technion.eyalzo.pack.files.FileItem;
import il.ac.technion.eyalzo.pack.files.FileList;
import il.ac.technion.eyalzo.pack.stamps.ChainItem;
import il.ac.technion.eyalzo.pack.stamps.ChunkItem;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.Thread.State;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.util.LinkedList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class WebGuiHandler implements HttpHandler
{
	private RemoteMachineList remoteMachineListSnd;
	private RemoteMachineList remoteMachineListRcv;
	private static int CHUNKS_PER_FILE = 300;

	//
	// Commands
	//
	private static final String CMD_MACHINE_LIST = "machines";
	private static final String CMD_MACHINE_DETAILS = "machine";
	private static final String CMD_CHAIN_LIST = "chains";
	private static final String CMD_CHAIN_DETAILS = "chain";
	private static final String CMD_CHUNK_DETAILS = "chunk";
	private static final String CMD_CONN_LIST = "conns";
	private static final String CMD_CONN_DETAILS = "conn";
	private static final String CMD_REPORTER = "reporter";
	private static final String CMD_DIR_LIST = "dirs";
	private static final String CMD_DIR_DETAILS = "dir";
	private static final String CMD_DIR_FILES = "dirfiles";
	private static final String CMD_DIR_STAT = "dirstat";
	private static final String CMD_DIR_SIMU = "dirsimu";
	private static final String CMD_DIR_SIMU_RTT = "dirsimurtt";
	private static final String CMD_DIR_SIMU_SMART = "dirsimusmart";
	private static final String CMD_DIR_SIMU_CHAINS = "dirsimuchains";
	private static final String CMD_FILE_DETAILS = "file";
	private static final String CMD_THREADS = "threads";

	//
	// Colors
	//
	private static final String COLOR_MACHINE = "yellow";
	private static final String COLOR_DIR = "aqua";
	private static final String COLOR_CONN = "magenta";
	private static final String COLOR_EVENT = "lightblue";
	private static final String COLOR_FILE = "green";
	private static final String COLOR_CHAIN = "pink";
	private static final String COLOR_STAMP = "purple";
	private static final String COLOR_SYSTEM = "grey";

	//
	// Parameters
	//
	private static final String PARAM_MACHINE = "machine";
	private static final String PARAM_SIGNATURE = "sign";
	private static final String PARAM_SERIAL = "ser";
	private static final String PARAM_LEN = "len";
	private static final String PARAM_CONN = "conn";
	private static final String PARAM_SHOW_HTTP = "showhttp";
	private static final String PARAM_SHOW_CHUNKS = "showchunks";
	private static final String PARAM_DIR = "dir";
	private static final String PARAM_FILE_NAME = "fn";
	private static final String PARAM_FILE_SERIAL = "fs";
	private static final String PARAM_RTT = "rtt";
	private static final String PARAM_SPEED_REG = "spdreg";
	private static final String PARAM_SPEED_PACK = "spdpack";
	private static final String PARAM_SNAPSHOT_BYTIME = "snpbytime";
	private static final String PARAM_SNAPSHOT_INTERVAL = "snpintrvl";

	public WebGuiHandler(RemoteMachineList remoteMachineListSnd, RemoteMachineList remoteMachineListRcv)
	{
		super();

		this.remoteMachineListSnd = remoteMachineListSnd;
		this.remoteMachineListRcv = remoteMachineListRcv;
	}

	@Override
	public void handle(HttpExchange t) throws IOException
	{
		try
		{
			// InputStream is = t.getRequestBody();
			// URI requestUri = t.getRequestURI();

			WebContext webGui = new WebContext(t);

			//
			// Commands
			//
			String command = webGui.command.length() >= 1 ? webGui.command.substring(1) : webGui.command;
			if (command == null || command.equals(""))
				handleMain(webGui);
			else if (command.equals(CMD_MACHINE_LIST))
				handleMachines(webGui);
			else if (command.equals(CMD_MACHINE_DETAILS))
				handleMachineDetails(webGui);
			else if (command.equals(CMD_CHAIN_LIST))
				handleChains(webGui);
			else if (command.equals(CMD_CHAIN_DETAILS))
				handleChainDetails(webGui);
			else if (command.equals(CMD_CHUNK_DETAILS))
				handleChunkDetails(webGui);
			else if (command.equals(CMD_CONN_LIST))
				handleConns(webGui);
			else if (command.equals(CMD_CONN_DETAILS))
				handleConnDetails(webGui);
			else if (command.equals(CMD_REPORTER))
				handleReporter(webGui);
			else if (command.equals(CMD_DIR_LIST))
				handleDirList(webGui);
			else if (command.equals(CMD_DIR_DETAILS))
				handleDirDetails(webGui);
			else if (command.equals(CMD_DIR_FILES))
				handleDirFiles(webGui);
			else if (command.equals(CMD_DIR_STAT))
				handleDirStats(webGui);
			else if (command.equals(CMD_DIR_SIMU))
				handleDirSimu(webGui);
			else if (command.equals(CMD_DIR_SIMU_RTT))
				handleDirSimuRtt(webGui);
			else if (command.equals(CMD_DIR_SIMU_SMART))
				handleDirSimuSmart(webGui);
			else if (command.equals(CMD_DIR_SIMU_CHAINS))
				handleDirSimuChains(webGui);
			else if (command.equals(CMD_FILE_DETAILS))
				handleFileDetails(webGui);
			else if (command.equals(CMD_THREADS))
				handleThreads(webGui);
			else
				handleError(webGui);

			//
			// Build and send the response
			//
			byte[] response = webGui.outputBuffer.toString().getBytes();

			t.sendResponseHeaders(200, response.length);
			OutputStream os = t.getResponseBody();
			os.write(response);
			os.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void handleMachines(WebContext webGui)
	{
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Machines");
		webGui.appendNavbarEnd();

		webGui.appendHeaderMinor("Senders");
		DisplayTable table = remoteMachineListSnd.webGuiMachineList(CMD_MACHINE_DETAILS + "?" + PARAM_MACHINE + "=");
		table.printHTMLTable(webGui, COLOR_MACHINE, false);

		webGui.appendHeaderMinor("Receivers");
		table = remoteMachineListRcv.webGuiMachineList(CMD_MACHINE_DETAILS + "?" + PARAM_MACHINE + "=");
		table.printHTMLTable(webGui, COLOR_MACHINE, false);
	}

	private void handleChains(WebContext webGui)
	{
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Chains");
		webGui.appendNavbarEnd();

		DisplayTable table = Main.chains.webGuiChains(CMD_CHAIN_DETAILS + "?" + PARAM_SERIAL + "=");
		table.printHTMLTable(webGui, COLOR_CHAIN, false);
	}

	private void handleChainDetails(WebContext webGui)
	{
		int chainSerial = webGui.getParamAsInt(PARAM_SERIAL);
		if (chainSerial == 0)
			return;

		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Chains", CMD_CHAIN_LIST);
		webGui.appendNavbarItem(Integer.toString(chainSerial));
		webGui.appendNavbarEnd();

		//
		// Find the chain in the global list
		//
		ChainItem chainItem = Main.chains.getChain(chainSerial - 1);
		if (chainItem == null)
		{
			webGui.appendString("Chain not found!");
			return;
		}

		webGui.appendField("First chunk", chainItem.getChunks().getFirst().toHtmlString());
	}

	private void handleChunkDetails(WebContext webGui)
	{
		int signature = webGui.getParamAsInt(PARAM_SIGNATURE);
		if (signature == 0)
			return;

		int chunkLen = webGui.getParamAsInt(PARAM_LEN);
		if (chunkLen <= 0)
			return;

		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Chunks");
		webGui.appendNavbarItem(String.format("<code>%08x</code>", signature));
		webGui.appendNavbarEnd();

		//
		// Find the chunk in the global list
		//
		ChunkItem chunk = Main.chunks.getChunkItem(signature, chunkLen);
		if (chunk == null)
		{
			webGui.appendString("Chunk not found!");
			return;
		}

		//
		// Details
		//
		webGui.appendField("Length", chunk.getLength());
		// Next
		ChunkItem nextChunk = chunk.getNextChunk();
		if (nextChunk == null)
			webGui.appendField("Next", "(none)");
		else
			webGui.appendField("Next", "<a href=" + CMD_CHUNK_DETAILS + "?" + PARAM_SIGNATURE + "="
					+ nextChunk.getStamp() + "&" + PARAM_LEN + "=" + nextChunk.getLength() + ">"
					+ nextChunk.toHtmlString() + "</a>");
		webGui.appendField("Files", chunk.getFilesCount(), "number of files that have this chunk");
		// Stream count
		webGui.appendField("In stream", chunk.getStatStreamCount(), "number of times appeared in streams");
	}

	private void handleDirList(WebContext webGui)
	{
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs");
		webGui.appendNavbarEnd();

		webGui.appendField("Status", Main.diskScan.getStatusLine());

		DisplayTable table = Main.dirList.webGuiDirs(CMD_DIR_DETAILS + "?" + PARAM_DIR + "=", true);
		table.printHTMLTable(webGui, COLOR_DIR, false);
	}

	private void handleDirDetails(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName);
		webGui.appendNavbarEnd();

		//
		// Menu
		//
		webGui.appendMenuItem(CMD_DIR_FILES + "?" + PARAM_DIR + "=" + dirName, "Files", fileList.size());
		webGui.appendField("Files (all)", fileList.getStatFilesCountAll());
		webGui.appendMenuItem(CMD_DIR_SIMU + "?" + PARAM_DIR + "=" + dirName + "&" + PARAM_SPEED_REG + "=&"
				+ PARAM_SPEED_PACK + "=", "Simulation");
		webGui.appendMenuItem(CMD_DIR_SIMU_CHAINS + "?" + PARAM_DIR + "=" + dirName, "Simulation Chains");
		webGui.appendMenuItem(CMD_DIR_SIMU_RTT + "?" + PARAM_DIR + "=" + dirName + "&" + PARAM_RTT + "=100&"
				+ PARAM_SNAPSHOT_BYTIME + "=0&" + PARAM_SNAPSHOT_INTERVAL + "=20000000&" + PARAM_SPEED_REG
				+ "=5000000&" + PARAM_SPEED_PACK + "=50000000", "Simulation RTT, by Size Axis");
		webGui.appendMenuItem(CMD_DIR_SIMU_RTT + "?" + PARAM_DIR + "=" + dirName + "&" + PARAM_RTT + "=100&"
				+ PARAM_SNAPSHOT_BYTIME + "=1&" + PARAM_SNAPSHOT_INTERVAL + "=10000", "Simulation RTT, by Time Axis");
		webGui.appendMenuItem(CMD_DIR_SIMU_SMART + "?" + PARAM_DIR + "=" + dirName, "Simulation Smart");
		webGui.appendMenuItem(CMD_DIR_STAT + "?" + PARAM_DIR + "=" + dirName, "Statistics");
	}

	private void handleDirFiles(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem("Files");
		webGui.appendNavbarEnd();

		//
		// File list
		//
		DisplayTable table = fileList.webGuiFileList(CMD_FILE_DETAILS + "?" + PARAM_FILE_NAME + "=");
		table.printHTMLTable(webGui, COLOR_FILE, true);
	}

	private void handleDirStats(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem("Statistics");
		webGui.appendNavbarEnd();

		//
		// Statistics
		//
		DisplayTable table = fileList.webGuiStatistics();
		table.printHtmlFields(webGui);
	}

	private void handleDirSimu(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem("Simulation");
		webGui.appendNavbarEnd();

		//
		// Simulation
		//

		long statTotalSizeAll = fileList.getStatTotalSizeAll();

		long speedDataBitspersec = webGui.getParamAsLong(PARAM_SPEED_REG, 5L * 1000 * 1000);
		long speedPackBitspersec = webGui.getParamAsLong(PARAM_SPEED_PACK, 10 * speedDataBitspersec);
		boolean snapshotByTime = webGui.getParamAsBool(PARAM_SNAPSHOT_BYTIME, true);
		long snapshotInterval = webGui.getParamAsLong(PARAM_SNAPSHOT_INTERVAL, 0);
		SimuResult simuResult = fileList.simulate(1, 1, speedDataBitspersec, speedPackBitspersec, 0, false,
				snapshotByTime, snapshotInterval);

		DisplayTable table = simuResult.webGuiResults();

		table.addField("Total from list", statTotalSizeAll, "temp");

		table.printHTMLTable(webGui, COLOR_DIR, false);

		//
		// Snapshots
		//
		webGui.appendField("Data speed (bps)", speedDataBitspersec);
		webGui.appendField("PACK speed (bps)", speedPackBitspersec);
		webGui.appendField("RTT (mSec)", 0);

		table = simuResult.webGuiSnapshots();
		table.printHTMLTable(webGui, COLOR_DIR, true);
	}

	private void handleDirSimuRtt(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		int rttMillis = webGui.getParamAsInt(PARAM_RTT, 100);

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem("Simulation RTT");
		webGui.appendNavbarEnd();

		//
		// Simulation
		//

		long statTotalSizeAll = fileList.getStatTotalSizeAll();

		int speedDataBitspersec = webGui.getParamAsInt(PARAM_SPEED_REG, 1 * 1000 * 1000);
		int speedPackBitspersec = webGui.getParamAsInt(PARAM_SPEED_PACK, 10 * speedDataBitspersec);
		boolean snapshotByTime = webGui.getParamAsBool(PARAM_SNAPSHOT_BYTIME, true);
		long snapshotInterval = webGui.getParamAsLong(PARAM_SNAPSHOT_INTERVAL, 0);
		// TODO change rtt and repeat
		SimuResult simuResult = fileList.simulate(1, 1, speedDataBitspersec, speedPackBitspersec, rttMillis, false,
				snapshotByTime, snapshotInterval);

		DisplayTable table = simuResult.webGuiResults();

		table.addField("Total from list", statTotalSizeAll, "temp");

		table.printHTMLTable(webGui, COLOR_DIR, false);

		//
		// Snapshots
		//
		webGui.appendField("Data speed (bps)", speedDataBitspersec);
		webGui.appendField("PACK speed (bps)", speedPackBitspersec);
		webGui.appendField("RTT (mSec)", rttMillis);

		table = simuResult.webGuiSnapshots();
		table.printHTMLTable(webGui, COLOR_DIR, true);
	}

	private void handleDirSimuSmart(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem("Simulation", CMD_DIR_SIMU + "?" + PARAM_SPEED_REG + "=&" + PARAM_SPEED_PACK + "=");
		webGui.appendNavbarItem("Smart");
		webGui.appendNavbarEnd();

		//
		// Simulation
		//

		long statTotalSizeAll = fileList.getStatTotalSizeAll();

		// Receiver prediction
		LinkedList<Integer> kList = new LinkedList<Integer>();
		for (int k = 1; k <= 5; k += 4)
			// TODO
			kList.add(k);

		// Sender attempts
		LinkedList<Integer> rList = new LinkedList<Integer>();
		for (int r = 1; r <= 10; r += 9)
			// TODO
			rList.add(r);

		DisplayTable table = new DisplayTable();

		table.addCol("r", "Sender's number of chunk attempts to match with receiver's prediction");
		for (Integer kCur : kList)
		{
			table.addCol("k=" + kCur, "Receiver's number of chunks in each predicitive ACK");
			table.addCol("k=" + kCur + " %", "Percents of total files size (includes small files)");
		}

		//
		// Simulation
		//
		for (Integer rCur : rList)
		{
			table.addRow(null);
			table.addCell(rCur);
			for (Integer kCur : kList)
			{
				SimuResult simuResult = fileList.simulate(kCur, rCur, 0, 0, 0, false, true, 0);
				long simuSavedBytes = simuResult.getSavedBytes();
				float percents = (float) simuSavedBytes * 100 / statTotalSizeAll;
				table.addCell(simuSavedBytes);
				table.addCell(String.format("%.2f", percents));
			}
		}

		table.printHTMLTable(webGui, COLOR_DIR, false);
	}

	private void handleDirSimuChains(WebContext webGui)
	{
		String dirName = webGui.getParamAsString(PARAM_DIR);
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null)
			return;

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem("Simulation", CMD_DIR_SIMU + "?" + PARAM_SPEED_REG + "=&" + PARAM_SPEED_PACK + "=");
		webGui.appendNavbarItem("Chains");
		webGui.appendNavbarEnd();

		SimuResult simuResult = fileList.simulate(1, 1, 0, 0, 0, false, true, 0);
		DisplayTable table = simuResult.webGuiMatchingChains();
		table.setDefaultSortCol(0);
		table.printHTMLTable(webGui, COLOR_DIR, false);
	}

	private void handleFileDetails(WebContext webGui)
	{
		String fileName = webGui.getParamAsString(PARAM_FILE_NAME);
		if (fileName.isEmpty())
		{
			int fileSerial = webGui.getParamAsInt(PARAM_FILE_SERIAL);
			if (fileSerial == 0)
				return;

			fileName = Main.dirList.getFileName(fileSerial, true);
			if (fileName == null)
				return;
		}

		int pathPos = fileName.lastIndexOf(File.separatorChar);
		if (pathPos < 0)
			return;

		String dirName = fileName.substring(0, pathPos);
		fileName = fileName.substring(pathPos + 1);

		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Dirs", CMD_DIR_LIST);
		webGui.appendNavbarItem(dirName, CMD_DIR_DETAILS + "?" + PARAM_DIR + "=" + dirName);
		webGui.appendNavbarItem(fileName);
		webGui.appendNavbarEnd();

		//
		// Details
		//
		FileItem fileItem = Main.dirList.getFileItem(dirName, fileName);
		if (fileItem == null)
			return;
		DisplayTable table = fileItem.webGuiDetails();
		table.printHtmlFields(webGui);

		//
		// Overlap files
		//
		webGui.appendHeaderMinor("Overlap Files");
		table = fileItem.webGuiChainFiles(CMD_FILE_DETAILS + "?" + PARAM_FILE_SERIAL + "=", false);
		table.printHTMLTable(webGui, COLOR_FILE, false);

		//
		// Chunks
		//
		int chunkCount = fileItem.getChunkCount();
		webGui.appendHeaderMinor(chunkCount > CHUNKS_PER_FILE ? "Chunks (up to " + CHUNKS_PER_FILE + ")" : "Chunks");
		table = fileItem.webGuiChunks(1, CHUNKS_PER_FILE, CMD_CHUNK_DETAILS + "?" + PARAM_SIGNATURE + "=", PARAM_LEN);
		table.printHTMLTable(webGui, COLOR_STAMP, false);
	}

	private void handleReporter(WebContext webGui)
	{
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Reporter");
		webGui.appendNavbarEnd();

		webGui.appendString("<pre>\n");
		webGui.appendString(Main.reporter.toString());
		webGui.appendString("\n</pre>");
	}

	private void handleConns(WebContext webGui)
	{
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Conns");
		webGui.appendNavbarEnd();

		webGui.appendHeaderMinor("Receivers In");
		DisplayTable table = remoteMachineListSnd.webGuiConnList(CMD_CONN_DETAILS + "?" + PARAM_CONN + "=");
		table.printHTMLTable(webGui, COLOR_CONN, false);

		webGui.appendHeaderMinor("Senders Out");
		table = remoteMachineListRcv.webGuiConnList(CMD_CONN_DETAILS + "?" + PARAM_CONN + "=");
		table.printHTMLTable(webGui, COLOR_CONN, false);
	}

	private void handleConnDetails(WebContext webGui)
	{
		//
		// Connection
		//
		int serial = webGui.getParamAsInt(PARAM_CONN);
		TcpConn conn = remoteMachineListSnd.getConnBySerial(serial);
		if (conn == null)
			//TODO separate snd and rcv
			conn = remoteMachineListRcv.getConnBySerial(serial);

		if (conn == null)
			return;

		//
		// Flags
		//
		boolean showHttp = webGui.getParamAsBool(PARAM_SHOW_HTTP, true);
		boolean showChunks = webGui.getParamAsBool(PARAM_SHOW_CHUNKS, true);

		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Conns", CMD_CONN_LIST);
		if (showChunks && showHttp)
		{
			webGui.appendNavbarItem(Integer.toString(serial));
		} else
		{
			webGui.appendNavbarItem(Integer.toString(serial), CMD_CONN_DETAILS + "?" + PARAM_CONN + "=" + serial);
			webGui.appendNavbarItem(showChunks ? "Chunks" : "HTTP");
		}
		webGui.appendNavbarEnd();

		// Details
		DisplayTable table = conn.webGuiDetails();
		table.printHtmlFields(webGui);

		//
		// Filter
		//
		webGui.setParam(PARAM_SHOW_CHUNKS, false);
		String httpLink = webGui.setParam(PARAM_SHOW_HTTP, true);

		webGui.setParam(PARAM_SHOW_CHUNKS, true);
		String chunksLink = webGui.setParam(PARAM_SHOW_HTTP, false);

		webGui.appendMenuItem("Filter:", showChunks ? httpLink : null, "HTTP", showHttp ? chunksLink : null, "Chunks");

		//
		// Events
		//
		table = conn.webGuiEvents(showHttp, showChunks, CMD_CHUNK_DETAILS + "?" + PARAM_SIGNATURE + "=", PARAM_LEN);
		table.printHTMLTable(webGui, COLOR_EVENT, false);
	}

	private void handleMachineDetails(WebContext webGui)
	{
		InetSocketAddress addr = webGui.getParamAsAddress(PARAM_MACHINE);
		if (addr == null)
			return;

		//TODO separate snd and rcv
		Machine machine = remoteMachineListSnd.getMachine(addr);
		if (machine == null)
			machine = remoteMachineListRcv.getMachine(addr);

		if (machine == null)
			return;

		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Machines", CMD_MACHINE_LIST);
		webGui.appendNavbarItem(machine.toString());
		webGui.appendNavbarEnd();

		// Details
		DisplayTable table = machine.webGuiDetails();
		table.printHtmlFields(webGui);
	}

	private void handleThreads(WebContext webGui)
	{
		//
		// Navbar
		//
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("System");
		webGui.appendNavbarItem("Threads");
		webGui.appendNavbarEnd();

		//
		// Get the root
		//
		ThreadGroup rootThreadGroup = null;
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		ThreadGroup ptg;
		while ((ptg = tg.getParent()) != null)
			tg = ptg;
		rootThreadGroup = tg;

		webGui.appendField("Root", rootThreadGroup.getName());
		webGui.appendField("Active count", rootThreadGroup.activeCount());

		// Get all thread IDs
		final ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
		long[] threadIds = thbean.getAllThreadIds();

		//
		// Thread table
		//
		DisplayTable table = new DisplayTable();

		table.addCol("ID", "Internal thread ID as used by Java", true);
		table.addCol("Name", "Internal thread name as used by Java", true);
		table.addColNum("CPU", "CPU time in nano seconds", true, true, true, null, "ms");
		table.addCol("State", "Thread state");
		table.addCol("Stack trace", "Stack trace");

		for (long i : threadIds)
		{
			ThreadInfo curThreadInfo = thbean.getThreadInfo(i, 20);
			State curState = curThreadInfo.getThreadState();

			table.addRow(curState.equals(State.RUNNABLE) ? "lightgreen" : null);

			// ID
			table.addCell(i);
			// Name
			table.addCell(curThreadInfo.getThreadName());
			// CPU
			table.addCell(thbean.getThreadCpuTime(i) / 1000000);
			// State
			table.addCell(curState);
			// Stack trace
			StringBuffer buffer = new StringBuffer("<pre><small>");
			for (StackTraceElement curElement : curThreadInfo.getStackTrace())
			{
				buffer.append(curElement.toString());
				buffer.append("\n");
			}
			buffer.append("</small></pre>");
			table.addCell(buffer.toString());
		}

		table.printHTMLTable(webGui, COLOR_SYSTEM, false);
	}

	public void handleMain(WebContext webGui)
	{
		webGui.appendNavbarStart("Main", null);
		webGui.appendNavbarEnd();

		appendSummaryTable(webGui);

		// Machines
		webGui.appendMenuItem(CMD_MACHINE_LIST, "Machines (snd / rcv)", remoteMachineListSnd.size() + " / "
				+ remoteMachineListRcv.size());

		// Connections
		webGui.appendMenuItem(CMD_CONN_LIST, "Conns (snd / rcv)", remoteMachineListSnd.getConnectionsCount() + " / "
				+ remoteMachineListRcv.getConnectionsCount());

		webGui.appendMenuItem(CMD_CHAIN_LIST, "Chains", Main.chains.size());
		webGui.appendMenuItem(CMD_DIR_LIST, "Local Dirs", Main.dirList.getDirCount());
		webGui.appendMenuItem(CMD_REPORTER, "Reporter");
		webGui.appendMenuItem(CMD_THREADS, "Threads");
	}

	public void appendSummaryTable(WebContext webGui)
	{
		DisplayTable table = new DisplayTable();

		// Header
		table.addCol("Side", "Sender or Receiver", true);
		table.addCol("Direction", "In or Out", true);
		table.addCol("Packets", "Total number of packets, includes dropped, errors, duplicates, etc.", false);
		table.addCol("Drops", "Dropped packets usually for altering", false);
		table.addCol("Dup", "Duplicate elimination (skip processing of altered)", false);
		table.addCol("Raw IP", "Raw IP bytes, with retransmissions", false);
		table.addCol("TCP Payload", "TCP payload bytes (no out retransmissions)", false);
		table.addCol("Known", "Bytes in known chunks (even if not predicted)", false);
		table.addCol("PACK PRED",
				"Bytes in PACK PRED commands. Receiver: predictions sent out. Sender: predictions inserted to inbox.",
				false);
		table.addCol("Pred<br>overlap", "Real bytes sent while overlapping ranges in received PRED", false);
		table.addCol("Pred<br>match", "Real bytes in matched predictions", false);
		table.addCol("Pred<br>ACK", "Real bytes in ACKs (sender-out and receiver-in)", false);

		// Header
		for (CaptureThread curThread : Main.captureThreads)
		{
			table.addRow(null);

			//Side
			table.addCell(curThread.isSideSender() ? "SND" : "RCV");
			// Direction
			table.addCell(curThread.isDirOut() ? "Out" : "In");
			// Packets
			table.addCell(curThread.getStatPackets());
			// Drops
			table.addCell(curThread.getStatPacketsDropped());
			// Dup
			table.addCell(curThread.getStatPacketsDupElim());
			// Raw IP
			table.addCell(curThread.getStatBytesRawIp());
			// TCP payload
			table.addCell(curThread.getStatBytesTcpPayload());
			// Known
			table.addCell(curThread.getStatBytesKnown());
			// Pack pred
			table.addCell(curThread.getStatBytesPredSent());
			// Pred overlap
			table.addCell(curThread.getStatBytesPredOverlap());
			// Pred match
			table.addCell(curThread.getStatBytesPredMatch());
			// Pred ACK
			table.addCell(curThread.getStatBytesPredAck());
		}

		table.printHTMLTable(webGui, "lightblue", false);
	}

	public void handleError(WebContext webGui)
	{
		webGui.appendNavbarStart();
		webGui.appendNavbarItem("Error");
		webGui.appendNavbarEnd();

		webGui.appendField("Command", webGui.uri.toString());
	}
}
