package il.ac.technion.eyalzo.pack.files;

import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Local disk directories and the files in them.
 */
public class DirList
{
	/**
	 * Directories and files in each.
	 */
	private HashMap<String, FileList> dirList = new HashMap<String, FileList>();

	/**
	 * 
	 * @param dirName
	 *            Directory name. Can end with a separator. Other then that it is taken as-is (case sensitive etc.).
	 * @return File list in the directory, or null if not found in the list (may still exist on disk).
	 */
	public FileList get(String dirName)
	{
		// Handle a file separator
		String fixedName = dirName.endsWith(File.separator) ? dirName.substring(0, dirName.length() - 1) : dirName;

		synchronized (dirList)
		{
			return dirList.get(fixedName);
		}
	}

	/**
	 * Add a new directory to the list and scan the directory for files, overriding existing one with the same name (if
	 * exists).
	 * 
	 * @param dirName
	 *            Directory name. May end with a path separator.
	 * @param minFileSize
	 *            Minimum file size to be scanned and indexed.
	 * @return The new file list representing this directory.
	 */
	public FileList addNew(String dirName, long minFileSize)
	{
		String fixedName = dirName.endsWith(File.separator) ? dirName.substring(0, dirName.length() - 1) : dirName;

		FileList result = new FileList(fixedName, minFileSize);
		synchronized (dirList)
		{
			dirList.put(fixedName, result);
		}
		return result;
	}

	/**
	 * @param hideEmpty
	 *            Hide directories with no large files with chains.
	 */
	public DisplayTable webGuiDirs(String dirDetailsLink, boolean hideEmpty)
	{
		DisplayTable table = new DisplayTable();

		table.addCol("Directory", "Directory full path", true);
		table.addCol("Direct<br>sub", "Sub directories, first level", false);
		table.addCol("Files", "Number of files that have stamps (min size)", false);
		table.addCol("Small<br>files", "Number of files, also those that are too small (min size)", false);
		table
				.addColNum("Load<br>time", "Time it took to load the persistent meta file", false, true, true, null,
						" ms");
		table.addColNum("Stamp<br>time", "Time it took to stamp files (total for multiple runs)", false, true, true,
				null, " ms");

		synchronized (dirList)
		{
			Iterator<Entry<String, FileList>> it = dirList.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, FileList> entry = it.next();
				String dirName = entry.getKey();
				FileList filelist = entry.getValue();
				int filesCount = filelist.size();

				if (hideEmpty && filesCount == 0)
					continue;

				table.addRow(filesCount == 0 ? null : "lightgreen");

				table.addCell(dirName, filesCount == 0 ? null : dirDetailsLink + dirName);
				table.addCell(filelist.getChildrenCount());
				table.addCell(filesCount);
				table.addCell(filelist.getStatFilesCountAll() - filesCount);
				table.addCell(filelist.getStatMetaLoadTimeMillis());
				table.addCell(filelist.getStatStampTime());
			}
		}

		return table;
	}

	public int getDirCount()
	{
		synchronized (dirList)
		{
			return dirList.size();
		}
	}

	public FileItem getFileItem(String dirName, String fileName)
	{
		synchronized (dirList)
		{
			FileList fileList = this.get(dirName);
			if (fileList == null)
				return null;

			return fileList.getFile(fileName);
		}
	}

	public FileItem getFileItem(int fileSerial)
	{
		synchronized (dirList)
		{
			for (FileList curFileList : dirList.values())
			{
				FileItem fileItem = curFileList.getFile(fileSerial);
				if (fileItem != null)
					return fileItem;
			}
		}

		return null;
	}

	public String getFileName(int fileSerial, boolean fullPath)
	{
		synchronized (dirList)
		{
			Iterator<Entry<String, FileList>> it = dirList.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, FileList> entry = it.next();
				String dirName = entry.getKey();
				FileList filelist = entry.getValue();
				String fileName = filelist.getFileName(fileSerial);
				if (fileName != null)
					return fullPath ? dirName + File.separatorChar + fileName : fileName;
			}
		}

		return null;
	}
}
