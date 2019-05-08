package il.ac.technion.eyalzo.common;

import il.ac.technion.eyalzo.pack.files.FileUtils;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reporter class is used to drop raw report data to file.
 * 
 */
public abstract class Reporter extends Thread {
	/**
	 * Default flush interval, set to 5 minutes.
	 */
	public static final int DEFAULT_AUTO_FLUSH_INTERVAL = 1 * 60 * 1000;
	public static final String DEFAULT_REPORTS_DIR = "reports";
	/**
	 * Time column format.
	 */
	public static final String TIME_FORMAT = "yyyy/MM/dd HH:mm:ss";

	public final static int COLUMN_TYPE_STRING = 0;
	public final static int COLUMN_TYPE_INT_ABS = 1;
	public final static int COLUMN_TYPE_INT_DIFF = 2;

	protected String fileNameRegexp;

	public static enum ColumnType {
		ctString, ctIntAbs, ctIntDiff
	}

	public enum StateHeaders {
		NONE, STARTED, CONTINUED, CHANGED
	}

	private static final String FILE_EXT = ".rep";
	private static final String LINE_SEPERATOR = "\r\n";
	private static final String COLUMN_SEPERATOR = "\t";
	private static final String REPLACE_COLUMN_SEPERATOR = " ";
	private static final String AUTO_TIME_FIELD_NAME = "Time";
	private static final String COLUMNS_HEADER = "#Columns:"
			+ REPLACE_COLUMN_SEPERATOR;
	private static final String STARTED_HEADER = "#Started:" + COLUMN_SEPERATOR;
	private static final String CONTINUED_HEADER = "Continued";
	private static final String CHANGED_HEADER = "#Changed:" + COLUMN_SEPERATOR;
	private static final String COMMENT_HEADER = "#Comment:" + COLUMN_SEPERATOR;

	private static final String applicationName = "pack";
	private String currentFileName = "";
	private FileOutputStream file;
	private final ArrayList<Column> columns = new ArrayList<Column>();
	private final ArrayList<ReporterChart> charts = new ArrayList<ReporterChart>();
	private final String filePrefix = "pack";
	private static SimpleDateFormat timeFormatter = new SimpleDateFormat(
			TIME_FORMAT);
	private static SimpleDateFormat fileNameFormatter = new SimpleDateFormat(
			"yyMMdd");
	protected StateHeaders headerStatus;
	/**
	 * Start time, when the "Started" header was written. Needed for the
	 * "Continued" header, to match with the real start time.
	 */
	private Date startTime = null;
	private Date firstFlush = null;
	Date lastFlush = null;

	/**
	 * Hold one column's properties
	 */
	public class ColumnProperties {
		String name;
		private String comment;
		ColumnType type;

		/**
		 * Create a ColumnProperties class instance
		 * 
		 * @param name
		 *            - can not be null
		 * @param type
		 * @param comment
		 *            - default is null
		 */
		public ColumnProperties(String name, ColumnType type, String comment) {
			if (name == null) {
				throw new IllegalArgumentException("Name must not be null");
			}
			this.name = name.replaceAll(COLUMN_SEPERATOR,
					REPLACE_COLUMN_SEPERATOR);
			this.type = type;
			this.comment = comment;
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("Name: " + name);
			sb.append(", Comment: " + comment);
			sb.append(", type: " + type);

			return sb.toString();
		}

		/**
		 * @return Returns the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return Returns the comment
		 */
		public String getComment() {
			return comment;
		}

		/**
		 * @return Returns the type (string / numeric / etc.)
		 */
		public ColumnType getType() {
			return type;
		}
	}

	/**
	 * @return The number of defined charts.
	 */
	public int getChartsCount() {
		return charts.size();
	}

	/**
	 * @param description
	 *            Chart description, to appear in links.
	 * @param series2
	 *            Optional.
	 * @param commonScale
	 *            Relevant only if two seria are used.
	 */
	public void addChart(String description, String series1, String series2,
			boolean commonScale) {
		ReporterChart chart = new ReporterChart(description, series1, series2,
				commonScale);
		charts.add(chart);
	}

	/**
	 * @param description
	 *            Chart description, to appear in links.
	 */
	public void addChart(String description, String series1) {
		ReporterChart chart = new ReporterChart(description, series1, null,
				false);
		charts.add(chart);
	}

	/**
	 * @param chartIndex
	 *            0-based chart index.
	 * @return Null if there is no such chart.
	 */
	public String getChartName(int chartIndex) {
		if (chartIndex >= charts.size())
			return null;
		return charts.get(chartIndex).description;
	}

	/**
	 * @param chartIndex
	 *            0-based chart index.
	 * @return Null if there is no such chart.
	 */
	public String getChartSeries1(int chartIndex) {
		if (chartIndex >= charts.size())
			return null;
		return charts.get(chartIndex).series1;
	}

	/**
	 * @param chartIndex
	 *            0-based chart index.
	 * @return Null if there is no such chart or this chart has only one series.
	 */
	public String getChartSeries2(int chartIndex) {
		if (chartIndex >= charts.size())
			return null;
		return charts.get(chartIndex).series2;
	}

	/**
	 * @param chartIndex
	 *            0-based chart index.
	 * @return True if there is such chart and it should use a common scale.
	 */
	public boolean getChartCommonScale(int chartIndex) {
		if (chartIndex >= charts.size())
			return false;
		return charts.get(chartIndex).commonScale;
	}

	private class ReporterChart {
		String description;
		String series1;
		String series2;
		boolean commonScale;

		public ReporterChart(String description, String series1,
				String series2, boolean commonScale) {
			this.description = description;
			this.series1 = series1;
			this.series2 = series2;
			this.commonScale = commonScale;
		}
	}

	/**
	 * Inner class to hold one column name and value
	 */
	public class Column {
		private long prevValue = 0;
		private long curValue = 0;
		private String curStr;
		ColumnProperties properties;
		/**
		 * Last flushed, written to report, as string. Set by {@link #get(Date)}
		 * , that is called if and only if the current value is flushed.
		 */
		private String lastFlushedStr = "";

		public Column(ColumnProperties properties) {
			this.properties = properties;
		}

		public void set(String value) {
			if (properties.type != ColumnType.ctString) {
				throw new IllegalStateException(
						"can not set a String value in a numeric column");
			}

			if (value == null) {
				curStr = "";
			} else {
				curStr = value.replaceAll(COLUMN_SEPERATOR,
						REPLACE_COLUMN_SEPERATOR);
			}
		}

		public void set(long value) {
			if (properties.type == ColumnType.ctString) {
				throw new IllegalStateException(
						"can not set a numeric value in a String column");
			}
			curValue = value;
		}

		public long inc() {
			return inc(1);
		}

		public long inc(long value) {
			if (properties.type == ColumnType.ctString) {
				throw new IllegalStateException(
						"can not set a numeric value in a String column");
			}
			if (value < 0) {
				throw new IllegalArgumentException(
						"expected non-negative value, got " + value);
			}
			long retVal = curValue;

			curValue += value;
			if (curValue < retVal) {
				curValue = retVal;
			}

			return retVal;
		}

		public long dec() {
			return dec(1);
		}

		public long dec(long value) {
			if (properties.type == ColumnType.ctString) {
				throw new IllegalStateException(
						"can not set a numeric value in a String column");
			}
			if (value < 0) {
				throw new IllegalArgumentException(
						"expected non-negative value, got " + value);
			}
			long retVal = curValue;

			curValue -= value;
			if (curValue > retVal) {
				curValue = retVal;
			}

			return retVal;
		}

		public long change(long value) {
			if (value >= 0) {
				return inc(value);
			}
			return dec(-value);
		}

		/**
		 * @return The current value of the column as displayed in the reporter
		 */
		public String get() {
			long val;
			switch (properties.getType()) {
			case ctString:
				return curStr;

			case ctIntAbs:
				val = curValue;
				break;

			case ctIntDiff:
				val = curValue - prevValue;
				break;

			default:
				return "Unsupported type " + properties.type;
			}

			String retVal;

			retVal = Long.toString(val);

			return retVal;
		}

		/**
		 * @return The last value written to report, as a string. May be empty
		 *         but never null.
		 */
		public String getLastFlushedAsString() {
			return lastFlushedStr;
		}

		/**
		 * Get the string representation of the column value
		 * 
		 * @return
		 */
		public String get(Date now) {
			long val;
			switch (properties.getType()) {
			case ctString:
				lastFlushedStr = curStr;
				return curStr;

			case ctIntAbs:
				val = curValue;
				break;

			case ctIntDiff:
				val = curValue - prevValue;
				break;

			default:
				lastFlushedStr = "";
				return "Unsupported type " + properties.type;
			}

			//
			// We get here if we're either in ABS or DIFF column
			//
			prevValue = curValue;

			String retVal;
			retVal = Long.toString(val);

			lastFlushedStr = retVal;

			return retVal;
		}

		/**
		 * Get a debug string representing the column object and its state
		 */
		@Override
		public String toString() {
			StringBuffer buf = new StringBuffer(100);
			buf.append("[Column - ");
			buf.append(properties.toString());
			switch (properties.getType()) {
			case ctString:
				buf.append(", String: ");
				buf.append(curStr);
				break;

			case ctIntAbs:
				buf.append(", Absolute: ");
				buf.append(curValue);
				break;

			case ctIntDiff:
				buf.append(", Diff: ");
				buf.append(curValue - prevValue);
				buf.append(" (cur: ");
				buf.append(curValue);
				buf.append(" prev: ");
				buf.append(prevValue);
				buf.append(")");
				break;
			}
			buf.append("]");
			return buf.toString();
		}

		/**
		 * Get the properties of this column
		 * 
		 * @return ColumnProperties properties
		 */
		public ColumnProperties getProperties() {
			return properties;
		}

		/**
		 * @return Current value, absolute or diff (by type), or zero if it is a
		 *         string.
		 */
		public long getValueLong() {
			switch (this.properties.type) {
			case ctString:
				return 0;
			case ctIntAbs:
				return this.curValue;
			case ctIntDiff:
				return this.curValue - this.prevValue;
			}

			return 0;
		}

		public void reset() {
			this.curStr = "";
			this.curValue = 0;
			this.prevValue = 0;
		}
	}

	protected Reporter() {
		super("Reporter");

		headerStatus = StateHeaders.STARTED;

		onInitColumns();

		// Set regular expression to match report files
		fileNameRegexp = applicationName + "-[0-9]{6}" + FILE_EXT;

		FileUtils.createNonExsistingDir(DEFAULT_REPORTS_DIR, null);
	}

	/**
	 * Override this to add your own columns.
	 */
	protected abstract void onInitColumns();

	/**
	 * Override this to update values right before the report file is written.
	 */
	protected abstract void onFlushData();

	/**
	 * @return Like {@link #getFileNameForDate(String, Date)}, but using default
	 *         prefix and current time.
	 */
	public String getFileName() {
		return getFileNameForDate(this.filePrefix, new Date());
	}

	/**
	 * Get the file name appropriate for current prefix and current date and
	 * time
	 * <p>
	 * Example usage:<br>
	 * String filename = Reporter.getFileNameForDate("my", new Date());
	 * 
	 * @param date
	 * @return
	 */
	public static String getFileNameForDate(String filePrefix, Date date) {
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(DEFAULT_REPORTS_DIR);
		stringBuffer.append(File.separator);
		stringBuffer.append(filePrefix);
		stringBuffer.append("-");
		stringBuffer.append(fileNameFormatter.format(date));
		stringBuffer.append(FILE_EXT);

		return stringBuffer.toString();
	}

	/**
	 * Appends the given column if it does not exist
	 * 
	 * @param columnName
	 */
	private void appendColumn(String columnName, ColumnProperties properties) {
		if (columnName == null) {
			throw new IllegalArgumentException("columnName can not be null");
		}

		synchronized (columns) {
			//
			// Check that this name does not exist yet
			//
			Column col = getColumnNoError(columnName);
			if (col != null) {
				throw new IllegalArgumentException("columnName already exists");
			}

			col = new Column(properties);
			columns.add(col);
		}

		if (headerStatus != StateHeaders.STARTED) {
			headerStatus = StateHeaders.CHANGED;
		}
	}

	private ColumnType convertColumnType(int columnType) {
		ColumnType ct;
		switch (columnType) {
		case COLUMN_TYPE_INT_ABS:
			ct = ColumnType.ctIntAbs;
			break;

		case COLUMN_TYPE_INT_DIFF:
			ct = ColumnType.ctIntDiff;
			break;

		case COLUMN_TYPE_STRING:
			ct = ColumnType.ctString;
			break;

		default:
			throw new IllegalArgumentException("Unknown type");
		}

		return ct;
	}

	public void addColumn(String columnName, int columnType,
			String columnComment) {
		ColumnProperties col = new ColumnProperties(columnName,
				convertColumnType(columnType), columnComment);
		appendColumn(columnName, col);
	}

	/**
	 * Set an absolute value into the specified column
	 * 
	 * @param columnName
	 * @param value
	 */
	public void set(String columnName, long value) {
		Column col = getColumn(columnName);
		col.set(value);
	}

	/**
	 * Set a string value into the specified column
	 * 
	 * @param columnName
	 * @param value
	 */
	public void set(String columnName, String value) {
		Column col = getColumn(columnName);
		col.set(value);
	}

	/**
	 * Increase the value in the specified column by 1
	 * 
	 * @param columnName
	 * @return previous value
	 */
	public long inc(String columnName) {
		return inc(columnName, 1);
	}

	/**
	 * Increase the value in the specified column by the specified value
	 * 
	 * @param columnName
	 * @param value
	 * @return previous value
	 */
	public long inc(String columnName, long value) {
		Column col = getColumn(columnName);
		return col.inc(value);
	}

	/**
	 * Get current value of a column that holds numeric values.
	 * 
	 * @param columnName
	 * @return Current value, absolute or diff (by type), or zero if column does
	 *         not exist or it is a string.
	 */
	public long getValueLong(String columnName) {
		Column col;
		try {
			col = getColumn(columnName);
		} catch (RuntimeException e) {
			return 0;
		}

		return col.getValueLong();
	}

	/**
	 * Decrease the value in the specified column by 1
	 * 
	 * @param columnName
	 * @return previous value
	 */
	public long dec(String columnName) {
		return dec(columnName, 1);
	}

	/**
	 * Decrease the value in the specified column by the specified value
	 * 
	 * @param columnName
	 * @param value
	 * @return previous value
	 */
	public long dec(String columnName, long value) {
		Column col = getColumn(columnName);
		return col.dec(value);
	}

	public long change(String columnName, long value) {
		Column col = getColumn(columnName);
		return col.change(value);
	}

	/**
	 * Get the specified column from the list of columns<br>
	 * Throws IllegalArgumentException if the column does not exist
	 * 
	 * @param columnName
	 *            Column name, case insensitive.
	 * @return the column
	 */
	public Column getColumn(String columnName) {
		if (columnName == null) {
			throw new IllegalArgumentException("columnName can not be null");
		}

		Column col = getColumnNoError(columnName);

		if (col == null) {
			throw new IllegalArgumentException("No such column: " + columnName);
		}

		return col;
	}

	/**
	 * Get the specified column from the list of columns or null if it doesn't
	 * exist.
	 * 
	 * @param columnName
	 *            Column name, case insensitive.
	 * @return The column object, or null if no such column was found.
	 */
	protected Column getColumnNoError(String columnName) {
		if (columnName == null) {
			return null;
		}

		String colNameReplaced = columnName.replaceAll(COLUMN_SEPERATOR,
				REPLACE_COLUMN_SEPERATOR);

		synchronized (columns) {
			for (int i = 0; i < columns.size(); ++i) {
				Column col = columns.get(i);
				if (colNameReplaced.equalsIgnoreCase(col.getProperties()
						.getName()))
					return col;
			}
		}

		return null;
	}

	/**
	 * Get the 0-based index of the given column name.
	 * 
	 * @return 0-based index, or -1 if not found or name is null.
	 */
	protected int getColumnIndex(String columnName) {
		if (columnName == null)
			return -1;

		String colNameReplaced = columnName.replaceAll(COLUMN_SEPERATOR,
				REPLACE_COLUMN_SEPERATOR);

		synchronized (columns) {
			for (int i = 0; i < columns.size(); ++i) {
				Column col = columns.get(i);
				if (colNameReplaced.equals(col.getProperties().getName())) {
					return i;
				}
			}
		}

		return -1;
	}

	/**
	 * @param columnName
	 *            Column name, case insensitive.
	 * @return The last value written to report, as a string. May be empty but
	 *         never null, even if column is unknown or there is a problem with
	 *         its definition.
	 */
	public String getColumnLastFlushedAsString(String columnName) {
		// Find the column
		Column col = getColumnNoError(columnName);
		if (col == null)
			return "";

		// Return the value
		return col.getLastFlushedAsString();
	}

	/**
	 * Write current values to file, side effect: reset diff values
	 * 
	 * @throws IOException
	 */
	public void flush(boolean flushData) throws IOException {
		if (flushData) {
			onFlushData();
		}

		StringBuffer stringBuffer = new StringBuffer();
		Date now = new Date();
		if (firstFlush == null) {
			firstFlush = now;
		}

		//
		// Replace file, if needed (also write headers)
		// ISSUE: consider opening and closing the file every time, and not
		// keeping it opened
		//
		String fileName = getFileNameForDate(filePrefix, now);
		if (!fileName.equals(currentFileName)) {
			// TODO: Check if we haven't passed the file limit

			if (file != null) {
				file.close();
				headerStatus = StateHeaders.CONTINUED;
			}
			currentFileName = fileName;
			file = new FileOutputStream(currentFileName, true);
		}

		//
		// The accumulation of data and flushing are potentially long operations
		// To avoid long locks we copy the columns and work on the copy
		//
		ArrayList<Column> columnsCopy = new ArrayList<Column>();
		synchronized (columns) {
			columnsCopy.addAll(columns);
		}

		if (headerStatus != StateHeaders.NONE) {
			appendHeader(columnsCopy, stringBuffer, now);
			headerStatus = StateHeaders.NONE;
		}

		if (flushData) {
			appendData(columnsCopy, stringBuffer, now);
		}

		//
		// Write the string to the file
		//
		byte[] toWrite = stringBuffer.toString().getBytes();
		file.write(toWrite);
		lastFlush = now;
	}

	/**
	 * @param headerStatus
	 *            The headerStatus to set.
	 */
	public void setHeaderStatus(StateHeaders headerStatus) {
		this.headerStatus = headerStatus;
	}

	/**
	 * Adds the headers of the report, one line for the column headers, another
	 * with state and date <br>
	 * Note: We assume that headers are needed if this function is called
	 * 
	 * @param stringBuffer
	 *            to append the headers to
	 * @param now
	 *            current date and time
	 */
	private void appendHeader(ArrayList<Column> columns,
			StringBuffer stringBuffer, Date now) {
		stringBuffer.append(COLUMNS_HEADER);

		//
		// Add time field automatically
		//
		stringBuffer.append(AUTO_TIME_FIELD_NAME);

		if (columns.size() != 0) {
			stringBuffer.append(COLUMN_SEPERATOR);
		}

		//
		// Add the rest of the fields
		//
		for (int i = 0; i < columns.size(); ++i) {
			Column col = columns.get(i);
			stringBuffer.append(col.getProperties().getName());

			if (i < columns.size() - 1) {
				stringBuffer.append(COLUMN_SEPERATOR);
			}
		}

		stringBuffer.append(LINE_SEPERATOR);

		//
		// Add columns descriptions and comments
		//
		for (int i = 0; i < columns.size(); ++i) {
			Column col = columns.get(i);
			stringBuffer.append(COMMENT_HEADER);
			stringBuffer.append(col.getProperties().getName());
			stringBuffer.append(" - ");
			switch (col.getProperties().getType()) {
			case ctString:
				stringBuffer.append("string");
				break;

			case ctIntAbs:
				stringBuffer.append("abs");
				break;

			case ctIntDiff:
				stringBuffer.append("diff");
				break;
			}

			if (col.getProperties().getComment() != null
					&& !("".equals(col.getProperties().getComment()))) {
				stringBuffer.append(", " + col.getProperties().getComment());
			}

			stringBuffer.append(LINE_SEPERATOR);
		}

		switch (headerStatus) {
		case STARTED:
		case CONTINUED:
			stringBuffer.append(STARTED_HEADER);
			break;

		case CHANGED:
			stringBuffer.append(CHANGED_HEADER);
			break;

		default:
			throw new IllegalStateException("Unexpected headerStatus: "
					+ headerStatus);
		}

		if (headerStatus == StateHeaders.CONTINUED) {
			// The first flush was written here and not the start time written
			// in the original header
			stringBuffer.append(timeFormatter
					.format(startTime == null ? firstFlush : startTime));
			stringBuffer.append(COLUMN_SEPERATOR);
			stringBuffer.append(CONTINUED_HEADER);
		} else {
			startTime = new Date(now.getTime());
			stringBuffer.append(timeFormatter.format(startTime));
		}
		stringBuffer.append(LINE_SEPERATOR);
	}

	/**
	 * Add the data for the current report line
	 * 
	 * @param stringBuffer
	 *            to append the headers to
	 * @param now
	 *            current date and time
	 */
	private void appendData(ArrayList<Column> columns,
			StringBuffer stringBuffer, Date now) {
		//
		// Add time field automatically
		//
		stringBuffer.append(timeFormatter.format(now));

		if (columns.size() != 0) {
			stringBuffer.append(COLUMN_SEPERATOR);
		}

		for (int i = 0; i < columns.size(); ++i) {
			Column col = columns.get(i);

			stringBuffer.append(col.get(now));

			if (i < columns.size() - 1) {
				stringBuffer.append(COLUMN_SEPERATOR);
			}
		}
		stringBuffer.append(LINE_SEPERATOR);
	}

	/**
	 * Restart the reporter by removing currently added columns and opening a
	 * new file <br>
	 * Also flushes current data in order to avoid data loss
	 */
	public void reset() {
		reset(true);
	}

	public void reset(boolean shouldFlush) {
		try {
			flush(shouldFlush);
		} catch (IOException e) {
			e.printStackTrace();
		}
		columns.clear();
		charts.clear();
	}

	/**
	 * @return List of all columns names, ordered as they were added, without
	 *         the first column (time).
	 */
	public List<String> getColumnsNames() {
		ArrayList<String> columnsNames = new ArrayList<String>(columns.size());
		for (Column curColumn : columns) {
			columnsNames.add(curColumn.properties.name);
		}
		return columnsNames;
	}

	/**
	 * Purpose: Return the name of this class as the report name Note: This
	 * method can be overriden in later classes if different name is required
	 * 
	 * @return
	 */
	protected String getReportName() {
		return getClass().getSimpleName();
	}

	@Override
	public void run() {
		// ISSUE: What should we do on exception?

		try {
			flush(false);
		} catch (IOException e) {
			e.printStackTrace();
		}

		while (!isInterrupted()) {
			try {
				//
				// Calculate when we should do our next flush
				//
				long residue;
				long nextFlushTime;
				long nextFlushInterval;
				long curTime = System.currentTimeMillis();
				long orgTime = System.currentTimeMillis();

				do {
					try {
						//
						// the autoFlushInterval may change so we need to
						// calculate the next flush according to the original
						// time
						// 
						residue = orgTime % DEFAULT_AUTO_FLUSH_INTERVAL;
						nextFlushTime = orgTime + DEFAULT_AUTO_FLUSH_INTERVAL
								- residue;
						nextFlushInterval = nextFlushTime - curTime;

						// sleep until next flush
						sleep(Math.min(60000, nextFlushInterval));
						curTime = System.currentTimeMillis();
					} catch (InterruptedException e) {
						System.out.println("Thread interrupt");
						break;
					}
				} while (curTime < nextFlushTime);

				try {
					flush(true);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		System.out.println("Stopped the Thread");

	}

	/**
	 * Used by WebGUI.
	 */
	@Override
	public String toString() {
		StringBuffer result = new StringBuffer(1000);

		//
		// Flush times
		//
		result.append("File name: ");
		result.append(this.getFileName());
		result.append("\nFirst flush: ");
		result.append(firstFlush == null ? "(null)" : StringUtils
				.createDateFormat(firstFlush.getTime()));
		result.append("\nLast flush: ");
		result.append(lastFlush == null ? "(null)" : StringUtils
				.createDateFormat(lastFlush.getTime()));
		result.append("\nStart time: ");
		result.append(startTime == null ? "(null)" : StringUtils
				.createDateFormat(startTime.getTime()));

		for (Column col : columns) {
			result.append("\n");
			result.append(col.toString());
		}
		result.append("\n");

		return result.toString();
	}

	/**
	 * @param filePrefix
	 *            File prefix for report files, includes full path and file-name
	 *            prefix with the application name.
	 * @return Empty list on error or if directory is empty. Sorted list (by
	 *         name) of files if at least one was found in the given directory.
	 */
	List<File> getReportFiles() {
		LinkedList<File> result = new LinkedList<File>();

		int lastSep = filePrefix.lastIndexOf(File.separator);
		if (lastSep < 0)
			return result;
		String dirName = filePrefix.substring(0, lastSep);
		File dir = new File(dirName);
		if (!dir.isDirectory())
			return result;

		// Create filter to match only files with fileName length 40
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String fileName) {
				return fileName.matches(fileNameRegexp);
			}
		};

		File[] filesInDir = dir.listFiles(filter);

		// It can be null when there is an I/O error (too many open files and/or
		// sockets etc)
		if (filesInDir == null || filesInDir.length == 0)
			return result;

		for (File f : filesInDir) {
			if (!f.isFile())
				continue;

			if (!f.exists())
				continue;

			result.add(f);
		}

		// Sort by file name (asc)
		Collections.sort(result, new Comparator<File>() {
			public int compare(File o1, File o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});

		return result;
	}

	/**
	 * @return The latest start time found in any of the report files. Null if
	 *         something gets wrong.
	 */
	public Date getLastStartTime() {
		//
		// Get all the start dates
		//
		List<File> files = getReportFiles();
		if (files == null || files.isEmpty())
			return null;

		// List is already sorted, so the last file is the latest
		MapCounter<Date> curTimes = loadStartTimes(files.get(files.size() - 1));

		if (curTimes == null)
			return null;

		Date result = null;
		for (Date curDate : curTimes.getMap().keySet()) {
			if (result == null || result.before(curDate)) {
				result = curDate;
			}
		}

		return result;
	}

	public DisplayTable webGuiReports(String reportLink, String textModeLink,
			String chartsLink) {
		//
		// Get all the start dates
		//
		List<File> files = getReportFiles();
		MapCounter<Date> dates = new MapCounter<Date>();
		for (File curFile : files) {
			MapCounter<Date> curTimes = loadStartTimes(curFile);
			dates.addAll(curTimes);
		}

		DisplayTable table = new DisplayTable();

		table.addColTime("Start", "Start time of the report", false, true,
				true, false);
		table.addColNum("Lines",
				"Number of lines saved in a raw after this time", false);
		table.addCol("Text mode<br>all lines",
				"Click to view all lines in text mode, for Excel import");
		table.addCol("Charts", "Click to view related charts");

		Iterator<Entry<Date, Long>> it = dates.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Date, Long> entry = it.next();

			table.addRow(null);

			long dateAsLong = entry.getKey().getTime();
			table.addCell(dateAsLong, reportLink + dateAsLong);
			table.addCell(entry.getValue());
			table.addCell("Text mode", textModeLink + dateAsLong);
			table.addCell("Charts", chartsLink + dateAsLong);
		}

		return table;
	}

	public String getCurrentFileName() {
		return this.currentFileName;
	}

	/**
	 * Reads the passed report file, and generates an accounting of how many
	 * report lines were inserted in each of the sessions appearing in the file.
	 * A report session starts with a commented "#started" line. Several such
	 * sessions may exist in a single report file, since report files are
	 * generated one-per-day, and the reporting process can reset several times
	 * during that 24 hr period.
	 * 
	 * @param file
	 * @return A mapping of start date -> number of report lines appearing after
	 *         that date
	 */
	public static MapCounter<Date> loadStartTimes(File file) {
		MapCounter<Date> result = new MapCounter<Date>();

		//
		// Open the file for read
		//
		BufferedReader in;
		try {
			in = new BufferedReader(new FileReader(file));
		} catch (Exception e) {
			return result;
		}

		String str;
		int lineCount = 0;
		Date lastDate = null;
		// The readLine() calls may throw IOException
		try {
			while ((str = in.readLine()) != null) {
				// Header fields always start with a #
				if (!str.startsWith("#")) {
					lineCount++;
					continue;
				}

				if (!str.startsWith(STARTED_HEADER)
						&& !str.startsWith(CHANGED_HEADER))
					continue;

				//
				// Save the counter to the last date
				//
				if (lastDate != null) {
					result.add(lastDate, lineCount);
				}
				lineCount = 0;

				// Now we face the "started" field of the header
				String split[] = str.split("\t");
				if (split.length < 2)
					continue;

				//
				// Parse the start date and save it for the next time
				//
				lastDate = timeFormatter.parse(split[1]);
			}
		} catch (Exception e) {
		}

		if (lastDate != null) {
			result.add(lastDate, lineCount);
		}

		return result;
	}

	/**
	 * @return The application name given to the constructor.
	 */
	public String getApplicationName() {
		return applicationName;
	}

	/**
	 * @param startDate
	 *            The date to look for in the comments header.
	 * @return Null if there is any problem with the given date.
	 */
	public DisplayTable webGuiReportLines(Date startDate, Logger log) {
		DisplayTable table = new DisplayTable();

		boolean first = true;
		Date fileDate = new Date(startDate.getTime());

		while (true) {
			//
			// Check if the current file exists
			//
			String fileName = getFileNameForDate(filePrefix, fileDate);
			File file = new File(fileName);
			if (!file.exists()) {
				if (first) {
					LoggingUtil.log(log, Level.WARNING,
							"File not found {0} for start date {1}", fileName,
							startDate);
					return null;
				}

				return table;
			}

			//
			// Open the file for read
			//
			BufferedReader in;
			try {
				in = new BufferedReader(new FileReader(file));
			} catch (Exception e) {
				LoggingUtil.log(log, Level.WARNING,
						"Exception when open file {0} for start date {1}: {2}",
						fileName, startDate, e);
				return null;
			}

			boolean result = webGuiReportLinesStartFile(first ? table : null,
					startDate, in, log);
			if (!result)
				return table;

			try {
				result = webGuiReportLinesReadData(in, table);
			} catch (Exception e) {
				LoggingUtil
						.log(
								log,
								Level.WARNING,
								"Exception when reading lines from file {0} for start date {1}: {2}",
								fileName, startDate, e);
				return table;
			}

			if (!result)
				break;

			fileDate.setTime(fileDate.getTime() + 1000L * 60 * 60 * 24);
			first = false;
		}

		return table;
	}

	/**
	 * @return True if to continue with the next file.
	 */
	private boolean webGuiReportLinesReadData(BufferedReader in,
			DisplayTable table) throws Exception {
		// Now the reader is positioned on the line right after the time field
		// of the header
		String str;
		// The readLine() calls may throw IOException
		while ((str = in.readLine()) != null) {
			// In case of a comment, quit here because this means that the data
			// lines were ended
			if (str.startsWith("#"))
				return false;

			String split[] = str.split("\t");

			// Number of data fields must be equal to table columns count
			if (split.length != table.getColumnsCount())
				return false;

			table.addRow(null);

			// Time is first
			Date date = timeFormatter.parse(split[0]);
			table.addCell(date);

			//
			// Rest of the columns
			//
			for (int i = 1; i < split.length; i++) {
				long curData = Long.parseLong(split[i]);
				table.addCell(curData);
			}
		}

		return true;
	}

	/**
	 * @param table
	 *            Optional. If given, the columns are added according to the
	 *            definition found in the header.
	 * @param startDate
	 *            The date to look for in the comments header.
	 * @param in
	 *            Input reader that is given when its position is set to start,
	 *            and returned when position is at the first data line after the
	 *            header.
	 * @return False if there is any problem with the given date.
	 */
	private boolean webGuiReportLinesStartFile(DisplayTable table,
			Date startDate, BufferedReader in, Logger log) {
		if (table != null) {
			table.addColTime(AUTO_TIME_FIELD_NAME,
					"Time when line was written, in local zime zone", false,
					true, true, false);
		}

		String str;
		// The readLine() calls may throw IOException
		try {
			while ((str = in.readLine()) != null) {
				// The "started" line is always after the comment lines
				if (str.startsWith(STARTED_HEADER)
						|| str.startsWith(CHANGED_HEADER)) {
					String split[] = str.split("\t");
					if (split.length < 2) {
						LoggingUtil
								.log(
										log,
										Level.WARNING,
										"Start time line has {0} parts instead of 2 or 3: {1}",
										split.length, str);
						return false;
					}

					// Parse the start date and compare
					Date date = timeFormatter.parse(split[1]);
					if (date.equals(startDate))
						break;

					if (table != null) {
						// Reset columns, and look for another block
						table.clear();
						table
								.addColTime(
										AUTO_TIME_FIELD_NAME,
										"Time when line was written, in local zime zone",
										false, true, true, false);
					}

					continue;
				}

				if (!str.startsWith(COMMENT_HEADER))
					continue;

				if (table == null)
					continue;

				// All columns have a comment line like this:
				// #Comment:\tEd2k_Int - abs, Number of distinct Ed2k internal
				// IPs
				String split[] = str.split("\t");
				if (split.length != 2)
					continue;

				split = split[1].split(" - ");
				if (split.length != 2)
					continue;

				// Add the found column
				table.addColNum(split[0], split[1], true);
			}

		} catch (Exception e) {
			LoggingUtil.log(log, Level.WARNING,
					"Exception on init for start date {0}: {1}", startDate, e);
		}

		return true;
	}
}
