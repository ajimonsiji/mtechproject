package il.ac.technion.eyalzo.pack.files;

import java.io.File;

import il.ac.technion.eyalzo.pack.Main;
import il.ac.technion.eyalzo.pack.RabinUtils;

public class DiskScanThread extends Thread {
	private boolean active = false;
	private static final int RUN_TIME_BEFORE_SUSPEND = 200;
	/**
	 * Suspend every {@link #RUN_TIME_BEFORE_SUSPEND} (approximately) to relief
	 * the CPU.
	 */
	private static final int SUSPEND_MILLIS = 200;
	public static final long MIN_FILE_SIZE = 10L * RabinUtils
			.getAverageChunkLen();
	/**
	 * Current directory.
	 */
	private String curDirName;
	private boolean debug;

	//
	// Initialization
	//
	/**
	 * Initialization directories.
	 */
	// private static final String INIT_DIRS[] = { "/bin", "/tmp", "/home",
	// "/root", "/usr/src/linux-headers-2.6.31-16" };
	// private static final String INIT_DIRS[] = {
	// "/home/eyalzo/.kde/share/apps/kmail/mail/.OE-Import.directory/eyal.imap.backup/cur",
	// "/home/eyalzo/Downloads/linux",
	// "/home/eyalzo/Downloads/israel.eml",
	// "/home/eyalzo/Downloads/israel.mbx",
	// "/home/eyalzo/.mozilla-thunderbird/75rv8n1a.default/ImapMail/imap.gmail.com",
	// "/home/eyalzo/.kde/share/apps/kmail/mail/MBOX-INBOX" };
	private static final String INIT_DIRS[] = {
			"/home/eyalzo/.cache",
			"/home/eyalzo/Downloads/linux",
			"/home/eyalzo/Downloads/Vitara DVR",
			"/home/eyalzo/Downloads/israel.mbx",
			"/home/eyalzo/Downloads/eyalzo.gmail",
			"/home/eyalzo/Downloads/linux.fast",
			"/home/eyalzo/.kde/share/apps/kmail/mail/.OE-Import.directory/eyal.imap.backup/cur",
			"/home/eyalzo/.mozilla-thunderbird/75rv8n1a.default/ImapMail/imap.gmail-1.com" };
	private int initDirsLeft;

	//
	// Statistics
	//
	/**
	 * Time spent stamping files.
	 */
	private long statStampsTime = 0;

	public DiskScanThread(boolean debug) {
		super("DiskScanThread");
		this.debug = debug;
	}

	@Override
	public void run() {
		initDirsLeft = INIT_DIRS.length;

		while (true) {
			//
			// Initialization process
			//
			if (!active && initDirsLeft > 0) {
				initDirsLeft--;
				curDirName = INIT_DIRS[initDirsLeft];
				active = true;
			}

			if (active && curDirName != null) {
				try {
					handleDir(curDirName, true, null);
				} catch (Throwable t) {
					t.printStackTrace();
				}

				// Pause
				active = false;
			}

			try {
				Thread.sleep(SUSPEND_MILLIS);
			} catch (InterruptedException e) {
			}
		}
	}

	private void handleDir(String dirName, boolean recursive, FileList parent) {
		if (debug)
			System.out.println("Dir " + dirName + ": Start...");

		//
		// Get existing or add new
		//
		FileList fileList = Main.dirList.get(dirName);
		if (fileList == null) {
			fileList = Main.dirList.addNew(dirName, MIN_FILE_SIZE);
			// If parent is valid then add new child to it
			if (parent != null) {
				parent.addChildren(fileList);
			}
		}

		// Load ready signatures from disk
		fileList.loadMeta(debug, Main.chunks);

		statStampsTime += fileList.calculateMissingStamps(
				RUN_TIME_BEFORE_SUSPEND, SUSPEND_MILLIS, Main.chunks, false);

		fileList.saveMeta();

		if (debug)
			System.out.println("Dir " + dirName + ": End.");

		if (recursive) {
			handleSubDir(dirName, fileList);
		}
	}

	private void handleSubDir(String dirName, FileList parent) {
		File dir = new File(dirName);
		File[] fileList = dir.listFiles();
		
		// In case the directory does not exist
		if(fileList == null)
			return;
		
		try {
			for (File curFile : fileList) {
				// Use only dirs
				if (!curFile.isDirectory())
					continue;

				handleDir(curFile.getPath(), true, parent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isActive() {
		return this.active;
	}

	/**
	 * Set the directory name and start the process.
	 * 
	 * @param dirName
	 *            Full path. May end with path separator.
	 * @return
	 */
	public boolean setDirName(String dirName) {
		if (active)
			return false;

		this.curDirName = dirName;

		active = true;

		return true;
	}

	/**
	 * @return Directory name or null if did not handle even one directory yet.
	 */
	public String getDirName() {
		return this.curDirName;
	}

	public String getStatusLine() {
		if (!active)
			return "Idle.";

		return "Scanning " + curDirName + " ...";
	}
}
