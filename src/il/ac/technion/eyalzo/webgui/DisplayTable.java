package il.ac.technion.eyalzo.webgui;

import il.ac.technion.eyalzo.common.Hash;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

/**
 * The table is built and filled with data, and then displayed with optional
 * filters and automatic paging.
 * <p>
 * The most common usage of this class will be inside a WebGui descendant.
 * Create an instance of this class for each table that should be shown
 * throughout the WebGui.
 * <p>
 * Tip: If the table size is expected to be large or creating the row from each
 * item is very expensive it's recommended to add a link to refresh the table,
 * and only refersh it when requested, not automatically upon any entrance to a
 * WebGui page.
 * <p>
 * Example, from within a {@link WebGuiHandler}, with two filters for min and max:
 * 
 * <pre>
 * DisplayTable table2 = new DisplayTable();
 * table2.addCol(&quot;Col1, int, asc&quot;, &quot;tip1&quot;, true);
 * table2.addCol(&quot;Col2, int, no sort&quot;, &quot;tip2&quot;);
 * table2.addCol(&quot;Col3, string, dec&quot;, &quot;tip3&quot;, false);
 * table2.addCol(&quot;Col4, link, asc&quot;, &quot;tip4&quot;, true);
 * 
 * for (int i = 1; i &lt;= 10; i++)
 * {
 * 	table2.addRow(&quot;yellow&quot;);
 * 	table2.addCell(i);
 * 	table2.addCell(11 - i);
 * 	table2.addCell(Integer.toString(i));
 * 	table2.addCell(i * 1000 + i, &quot;http://test.com/test.php?num=&quot; + i);
 * }
 * 
 * table2.filterMin(&quot;Min number&quot;, &quot;min&quot;, 0, 1);
 * table2.filterMax(&quot;Max number&quot;, &quot;max&quot;, 0, 10);
 * table2.printHTMLTable(this, 0);
 * </pre>
 * 
 * <p>
 * Another mode is the fields-mode, where the table has two columns with no
 * header. New rows are added with {@link #addField(String, Object, String)}
 * only. No need to add rows, columns or cells. Every field has a name, value
 * and optional tooltip. When table is ready, you can print it normally with
 * {@link #printHTMLTable(WebGui, int)}. For example:
 * 
 * <pre>
 * DisplayTable table = new DisplayTable();
 * table.addField(&quot;Hash&quot;, this.hash, &quot;File hash&quot;);
 * table.addField(&quot;File name&quot;, this.name, null);
 * </pre>
 * 
 * <p>
 * This class can be easily unit-tested. Just run its main, and open your
 * browser on http://localhost:4000
 */
public class DisplayTable
{
	public enum FilterType
	{
		MIN, MAX, EXACT
	}

	private static final String		DEFAULT_ROW_COLOR	= "#dddddd";

	private String					headerColor			= "cyan";
	private ArrayList<Column>		columns				= new ArrayList<Column>();
	private LinkedList<Row>			rows				= new LinkedList<Row>();

	//
	// Charts
	//
	private static final int		CHART_HEIGHT		= 500;
	private static final int		CHART_WIDTH			= 1000;
	private static final int		CHART_BARS			= 100;
	private static final int		CHART_AXIS_UNITS_X	= 10;
	private static final int		CHART_AXIS_UNITS_Y	= 10;
	private static final DateFormat	dateFormatChart		= DateFormat
																.getDateTimeInstance(
																		DateFormat.SHORT,
																		DateFormat.SHORT,
																		Locale.UK);

	/**
	 * Holds the last added row, for faster {@link #addCell(Object)}.
	 */
	private Row						lastRow				= null;
	private int						defaultSortColumn	= -1;
	private int						sortColumn			= -1;
	private boolean					sortAscending;
	private long					lastAccessed		= System
																.currentTimeMillis();
	private long					freshMillis			= 5 * 60 * 1000;

	// Increased each time we add a new cell, set to 0 each time we add a new
	// row
	private int						currCol				= -1;

	/**
	 * When true, table is designed for fields display in two columns, with
	 * tooltip for every row, unsortable.
	 */
	private boolean					fieldsMode			= false;
	/**
	 * Default date-time format for Date fields.
	 */
	private static final DateFormat	dateFormatDateTime	= DateFormat
																.getDateTimeInstance(
																		DateFormat.SHORT,
																		DateFormat.MEDIUM,
																		Locale.UK);
	/**
	 * Date format for text.
	 */
	private static SimpleDateFormat	dateFormatText		= new SimpleDateFormat(
																"yyyy-MM-dd HH:mm:ss");

	/**
	 * Internal class representing a column in a DisplayTable
	 */
	public class Column
	{
		protected String		name;
		protected String		toolTip;
		protected boolean		isNumeric;
		protected boolean		isLink;
		/**
		 * When true, column is sortable, so the column name has a link for
		 * sorting.
		 */
		private boolean			isSortable;
		/**
		 * When {@link #isSortable}, it tells if first sort will be in asc
		 * order.
		 */
		protected boolean		initialSortAsc;
		protected boolean		blankOnZero;
		protected boolean		commaSeparator;
		protected String		suffix;
		protected String		prefix;
		/**
		 * Date format in case it's a {@link java.util.Date} or long for
		 * date-time display.
		 */
		protected DateFormat	dateFormat;

		public boolean isSortable()
		{
			return isSortable;
		}

		public boolean isLink()
		{
			return isLink;
		}

		public boolean isNumeric()
		{
			return isNumeric;
		}

		public boolean isTime()
		{
			return dateFormat != null;
		}

		public String getName()
		{
			return name;
		}

		/**
		 * Constructor for DisplayTable's Column class, sortable.
		 * 
		 * @param name
		 *            Column name
		 * @param isNumeric
		 *            True if column's data is numeric. Needed for sorting and
		 *            filtering.
		 * @param isLink
		 *            True if column's data is link. Needed for sorting by
		 *            displayed text.
		 */
		public Column(String name, boolean isNumeric, boolean isLink)
		{
			this.name = name;
			this.isNumeric = isNumeric;
			this.isLink = isLink;
			this.isSortable = true;
			this.initialSortAsc = true;
			this.commaSeparator = true;
		}

		/**
		 * Constructor for DisplayTable's Column class, optional sortable.
		 * <p>
		 * Use this instead of {@link #Column(String, boolean, boolean)} if you
		 * need to create an unsortable column.
		 * 
		 * @param name
		 *            Column name
		 * @param toolTip
		 *            Optional tool-tip that pops-up when the mouse stays over
		 *            column name. If empty it is stored as null.
		 * @param isNumeric
		 *            True if column's data is numeric. Needed for sorting and
		 *            filtering.
		 * @param isLink
		 *            True if column's data is link. Needed for sorting by
		 *            displayed text.
		 * @param isSortable
		 *            True if column is sortable.
		 * @param initialSortAsc
		 *            When true, the first sort will always be in asc order.
		 * @param blankOnZero
		 *            When true and value is zero, nothing will be displayed.
		 * @param commaSeparator
		 *            When true, numbers will be disaplyed with comma-separator
		 *            every 3 digits.
		 * @param dateFormat
		 *            Date format in case it's a {@link java.util.Date} or long
		 *            for date-time display.
		 * @param prefix
		 *            Optional prefix for non-zero data. Add space after the
		 *            prefix if needed.
		 * @param suffix
		 *            Optional suffix for non-zero data. Add space before the
		 *            suffix if needed.
		 */
		public Column(String name, String toolTip, boolean isNumeric,
				boolean isLink, boolean isSortable, boolean initialSortAsc,
				boolean blankOnZero, boolean commaSeparator,
				DateFormat dateFormat, String prefix, String suffix)
		{
			this.name = name;
			if (toolTip != null && !toolTip.equals(""))
			{
				this.toolTip = toolTip;
			}
			this.isNumeric = isNumeric;
			this.isLink = isLink;
			this.isSortable = isSortable;
			this.initialSortAsc = initialSortAsc;
			this.blankOnZero = blankOnZero;
			this.commaSeparator = commaSeparator;
			this.dateFormat = dateFormat;
			this.prefix = prefix;
			this.suffix = suffix;
		}
	}

	/**
	 * Empty constructor, caller will need to set columns and rows later
	 */
	public DisplayTable()
	{
	}

	/**
	 * Add a new sortable column.
	 * 
	 * @param name
	 *            Column name
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            column name. Use null for no tool-tip.
	 * @param initialSortAsc
	 *            When true, the first sort will always be in asc order.
	 * @return Index of the newly added column, 0-based.
	 */
	public int addCol(String name, String toolTip, boolean initialSortAsc)
	{
		// User mustn't add more columns after first row was added
		// if (rows.size() != 0)
		// throw new
		// IllegalAccessError("Can't add additional columns after first row was added");

		columns.add(new Column(name, toolTip, false, false, true,
				initialSortAsc, true, true, null, null, null));

		// If no other sortable column was added before, then set the default
		if (defaultSortColumn == -1)
		{
			defaultSortColumn = (columns.size() - 1);
		}

		return columns.size() - 1;
	}

	/**
	 * Add a complete field-row, and turn-on the fields mode for the entire
	 * table.
	 * 
	 * @param name
	 *            Field name, to be displayed in bold as the first cell in row.
	 * @param color
	 *            The color of the whole row
	 * @param value
	 *            Value to be displayed in the 2nd column.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            field name. Use null for no tool-tip.
	 * @param link
	 *            Optional link for the value field.
	 */
	public void addField(String name, String color, Object value,
			String toolTip, String link)
	{
		//
		// If fields-mode was not turned-on yet, then do it now
		//
		if (!fieldsMode)
		{
			fieldsMode = true;
			//
			// Build the "table" with fake columns
			//
			columns.add(new Column("Field", false, false));
			columns.add(new Column("Value", false, false));
			columns.add(new Column("Tooltip", false, false));
		}

		//
		// Add the row and cells
		//
		addRow(color);
		addCell(name, link, true, false);
		addCell(value);
		addCell(toolTip);
	}

	/**
	 * Add a complete field-row, and turn-on the fields mode for the entire
	 * table.
	 * 
	 * @param name
	 *            Field name, to be displayed in bold as the first cell in row.
	 * @param value
	 *            Value to be displayed in the 2nd column.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            field name. Use null for no tool-tip.
	 * @param link
	 *            Optional link for the value field.
	 */
	public void addField(String name, Object value, String toolTip, String link)
	{
		addField(name, "", value, toolTip, link);
	}

	/**
	 * Add a complete field-row, and turn-on the fields mode for the entire
	 * table.
	 * 
	 * @param name
	 *            Field name, to be displayed in bold as the first cell in row.
	 * @param value
	 *            Value to be displayed in the 2nd column.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            field name. Use null for no tool-tip.
	 */
	public void addField(String name, Object value, String toolTip)
	{
		addField(name, value, toolTip, null);
	}

	/**
	 * Add a complete field-row, and turn-on the fields mode for the entire
	 * table.
	 * 
	 * @param name
	 *            Field name, to be displayed in bold as the first cell in row.
	 * @param color
	 *            The color of the whole row
	 * @param value
	 *            Value to be displayed in the 2nd column.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            field name. Use null for no tool-tip.
	 * @param link
	 *            Optional link for the value field.
	 */
	public void addMarkedField(String name, String color, Object value,
			String toolTip, String link)
	{
		addField(name, "<span style='background-color:" + color + "';>" + value
				+ "</span>", toolTip, link);
	}

	/**
	 * Add a new sortable numeric column.
	 * 
	 * @param name
	 *            Column name.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            column name. Use null for no tool-tip.
	 * @param initialSortAsc
	 *            When true, the first sort will always be in asc order.
	 * @param blankOnZero
	 *            When true and value is zero, nothing will be displayed.
	 * @param commaSeparator
	 *            When true, numbers will be disaplyed with comma-separator
	 *            every 3 digits.
	 * @param prefix
	 *            Optional prefix for non-zero data. Add space after the prefix
	 *            if needed.
	 * @param suffix
	 *            Optional suffix for non-zero data. Add space before the suffix
	 *            if needed.
	 * @return Index of the newly added column, 0-based.
	 */
	public int addColNum(String name, String toolTip, boolean initialSortAsc,
			boolean blankOnZero, boolean commaSeparator, String prefix,
			String suffix)
	{
		// User mustn't add more columns after first row was added
		if (rows.size() != 0)
			throw new IllegalAccessError(
					"Can't add additional columns after first row was added");

		columns.add(new Column(name, toolTip, false, false, true,
				initialSortAsc, blankOnZero, commaSeparator, null, prefix,
				suffix));

		// If no other sortable column was added before, then set the default
		if (defaultSortColumn == -1)
		{
			defaultSortColumn = (columns.size() - 1);
		}

		return columns.size() - 1;
	}

	/**
	 * Add a new sortable numeric column.
	 * 
	 * @param name
	 *            Column name.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            column name. Use null for no tool-tip.
	 * @param initialSortAsc
	 *            When true, the first sort will always be in asc order.
	 * @param blankOnZero
	 *            When true and value is zero, nothing will be displayed.
	 * @param commaSeparator
	 *            When true, numbers will be disaplyed with comma-separator
	 *            every 3 digits.
	 * @return Index of the newly added column, 0-based.
	 */
	public int addColNum(String name, String toolTip, boolean initialSortAsc,
			boolean blankOnZero, boolean commaSeparator)
	{
		// User mustn't add more columns after first row was added
		if (rows.size() != 0)
			throw new IllegalAccessError(
					"Can't add additional columns after first row was added");

		columns.add(new Column(name, toolTip, false, false, true,
				initialSortAsc, blankOnZero, commaSeparator, null, null, null));

		// If no other sortable column was added before, then set the default
		if (defaultSortColumn == -1)
		{
			defaultSortColumn = (columns.size() - 1);
		}

		return columns.size() - 1;
	}

	/**
	 * Add a new sortable numeric column, with blank-on-zero and
	 * comma-separator.
	 * 
	 * @param name
	 *            Column name.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            column name. Use null for no tool-tip.
	 * @param initialSortAsc
	 *            When true, the first sort will always be in asc order.
	 * @return Index of the newly added column, 0-based.
	 */
	public int addColNum(String name, String toolTip, boolean initialSortAsc)
	{
		// User mustn't add more columns after first row was added
		if (rows.size() != 0)
			throw new IllegalAccessError(
					"Can't add additional columns after first row was added");

		columns.add(new Column(name, toolTip, true, false, true,
				initialSortAsc, true, true, null, null, null));

		// If no other sortable column was added before, then set the default
		if (defaultSortColumn == -1)
		{
			defaultSortColumn = (columns.size() - 1);
		}

		return columns.size() - 1;
	}

	/**
	 * Add a new sortable date-time column, based on {@link java.util.Date} or
	 * long.
	 * 
	 * @param name
	 *            Column name.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            column name. Use null for no tool-tip.
	 * @param initialSortAsc
	 *            When true, the first sort will always be in asc order.
	 * @return Index of the newly added column, 0-based.
	 */
	public int addColTime(String name, String toolTip, boolean initialSortAsc,
			boolean showDate, boolean showTime, boolean showSeconds)
	{
		// User mustn't add more columns after first row was added
		if (rows.size() != 0)
			throw new IllegalAccessError(
					"Can't add additional columns after first row was added");

		DateFormat dateFormat = null;

		// Set time style
		int timeStyle = showSeconds ? DateFormat.MEDIUM : DateFormat.SHORT;

		if (showDate)
		{
			if (showTime)
			{
				dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT,
						timeStyle, Locale.UK);
			}
			else
			{
				dateFormat = DateFormat.getDateInstance(DateFormat.SHORT,
						Locale.UK);
			}
		}
		else if (showTime)
		{
			dateFormat = DateFormat.getTimeInstance(timeStyle, Locale.UK);
		}

		columns.add(new Column(name, toolTip, false, false, true,
				initialSortAsc, true, true, dateFormat, null, null));

		// If no other sortable column was added before, then set the default
		if (defaultSortColumn == -1)
		{
			defaultSortColumn = (columns.size() - 1);
		}

		return columns.size() - 1;
	}

	/**
	 * Add a new unsortable column.
	 * 
	 * @param name
	 *            Column name.
	 * @param toolTip
	 *            Optional tool-tip to be displayed when mouse stays over the
	 *            column name. Use null for no tool-tip.
	 * @return Index of the newly added column, 0-based.
	 */
	public int addCol(String name, String toolTip)
	{
		// User mustn't add more columns after first row was added
		if (rows.size() != 0)
			throw new IllegalAccessError(
					"Can't add additional columns after first row was added");

		columns.add(new Column(name, toolTip, false, false, false, false, true,
				true, null, null, null));
		return columns.size() - 1;
	}

	/**
	 * Add a new row, without its data cells yet.
	 * <p>
	 * Note: Only 16 color names are supported by the W3C HTML 4.0 standard
	 * (aqua, black, blue, fuchsia, gray, green, lime, maroon, navy, olive,
	 * purple, red, silver, teal, white, and yellow). For all other colors you
	 * should use the Color HEX value.
	 * 
	 * @param color
	 *            Html color name or code. Can be null or empty for automatic
	 *            on-off colors.
	 * @return Row index, 0-based.
	 */
	public int addRow(String color)
	{
		// User must first add columns, then add rows
		if (columns.size() == 0)
			throw new IllegalAccessError("Must set columns before adding a row");

		currCol = 0;

		lastRow = new Row(color, columns.size());
		rows.add(lastRow);

		lastAccessed = System.currentTimeMillis();

		return rows.size() - 1;
	}

	/**
	 * Create cell with default properties.
	 * <p>
	 * No link, not bold and not small.
	 */
	public void addCell(Object data)
	{
		// Trying to add a new cell without adding a row first
		if (currCol == -1)
			throw new ArrayIndexOutOfBoundsException(
					"Must add a new row before adding a cell");

		// Trying to add a new cell without adding a new row (i.e. too many
		// cells in the same row)
		if (currCol >= columns.size())
			throw new ArrayIndexOutOfBoundsException("Table can only contain "
					+ columns.size() + " columns per row");

		lastRow.addCell(new Cell(data, null, null, false, false, null, false,
				columns.get(currCol).commaSeparator));

		currCol++;
	}

	/**
	 * @param data
	 *            Data to be displayed.
	 * @param link
	 *            Optional link.
	 * @param bold
	 *            True if should display in bold.
	 * @param small
	 *            True if should display in small text size.
	 */
	public void addCell(Object data, String link, boolean bold, boolean small)
	{
		lastRow.addCell(new Cell(data, link, null, bold, small, null, false,
				columns.get(currCol).commaSeparator));
		currCol++;
	}

	/**
	 * @param data
	 *            Data to be displayed.
	 * @param link
	 *            Optional link.
	 * @param bold
	 *            True if should display in bold.
	 * @param small
	 *            True if should display in small text size.
	 * @param linkNewWindow
	 *            If link will open a new window (traget=new).
	 */
	public void addCell(Object data, String link, boolean bold, boolean small,
			boolean linkNewWindow)
	{
		lastRow.addCell(new Cell(data, link, null, bold, small, null,
				linkNewWindow, columns.get(currCol).commaSeparator));
		currCol++;
	}

	/**
	 * Create cell with link.
	 * <p>
	 * Not bold and not small.
	 */
	public void addCell(Object data, String link)
	{
		lastRow.addCell(new Cell(data, link, null, false, false, null, false,
				columns.get(currCol).commaSeparator));
		currCol++;
	}

	public void addCell(Object data, String link, String tooltip)
	{
		lastRow.addCell(new Cell(data, link, tooltip, false, false, null,
				false, columns.get(currCol).commaSeparator));
		currCol++;
	}

	public void addMarkedCell(Object data, String color, String link)
	{
		lastRow.addCell(new Cell(data, link, null, false, false, color, false,
				columns.get(currCol).commaSeparator));
		currCol++;
	}

	public void addMarkedCell(Object data, String color, String link,
			String tooltip)
	{
		lastRow.addCell(new Cell(data, link, tooltip, false, false, color,
				false, columns.get(currCol).commaSeparator));
		currCol++;
	}

	/**
	 * Get number of columns
	 * 
	 * @return number of columns
	 */
	public int getColumnsCount()
	{
		if (columns == null)
			return 0;
		return columns.size();
	}

	/**
	 * Sort the table rows
	 * 
	 * @param columnIndex
	 *            - the column to sort by
	 * @param isAscending
	 *            - ascending or descending
	 */
	public void sort(int columnIndex, boolean isAscending)
	{
		if (columnIndex < 0 || columnIndex >= getColumnsCount())
			return;

		sortColumn = columnIndex;
		sortAscending = isAscending;

		Column col = columns.get(columnIndex);
		Collections.sort(rows, new MyComp(columnIndex, isAscending, col
				.isNumeric(), col.isLink()));
	}

	/**
	 * 
	 * @return int Get the current sorted column index
	 */
	public int getSortColumnId()
	{
		return sortColumn;
	}

	/**
	 * @return int Get the default sort column index of this table
	 */
	public int getDefaultSortColumnId()
	{
		return defaultSortColumn;
	}

	/**
	 * @param webGui
	 */
	public void printHtmlFields(WebContext webGui)
	{
		// Make sure it's fields mode
		if (!fieldsMode)
			return;

		webGui.appendTableStart();
		//
		// Show rows
		//
		for (Row row : rows)
		{
			// if row color is empty, use the default color (gray)
			webGui.appendTableRowStart("".equals(row.color) ? DEFAULT_ROW_COLOR
					: row.color);

			// First column is the field name as string
			// The 3rd column is actually a tooltip
			Object toolTip = row.getCell(2);
			if (toolTip == null)
			{
				webGui.appendTableCell("<b>" + row.getCell(0) + "</b>");
			}
			else
			{
				webGui.appendTableCell("<b title=\"" + toolTip.toString()
						+ "\">" + row.getCell(0) + "</b>");
			}

			Cell cell = row.getCell(1);
			Column col = columns.get(1);

			// Date-time column can contain only Date
			if (cell.data instanceof Date)
			{
				Date displayTime = (Date) cell.data;
				if (displayTime.getTime() == 0)
				{
					webGui.appendTableCell("");
					continue;
				}
				webGui.appendTableCell(dateFormatDateTime.format(displayTime));
				continue;
			}

			// Numeric columns may be displayed in a non-default style
			if (cell.data instanceof Number)
			{
				long longVal = ((Number) cell.data).longValue();
				if (longVal == 0)
				{
					if (col.blankOnZero)
					{
						webGui.appendTableCell("");
						continue;
					}
				}
			}

			webGui.appendTableCell(cell.toString());
		}
		webGui.appendTableEnd();
	}

	/**
	 * Print the paged table to a WebGui
	 * 
	 * @param webGui
	 * @param maxRowPerPage
	 */
	public void printHTMLTable(WebContext webGui)
	{
		printHTMLTable(webGui, this.headerColor, false);
	}

	/**
	 * @param alsoLong
	 *            If true, will try to look for numeric column if time column is
	 *            not found.
	 * @return 0-based index of the first time column, or -1 if no such column
	 *         exists.
	 */
	private int getTimeColIndexFirst(boolean alsoNumeric)
	{
		for (int i = 0; i < columns.size(); i++)
		{
			Column curCol = columns.get(i);
			if (curCol.isTime())
				return i;
		}

		if (!alsoNumeric)
			return -1;

		for (int i = 0; i < columns.size(); i++)
		{
			Column curCol = columns.get(i);
			if (curCol.isNumeric)
				return i;
		}

		return -1;
	}

	/**
	 * @param colIndex
	 *            0-based column index.
	 * @return List of numbers based on the given column index. If column index
	 *         is wrong, the returned list will be empty. Normally the length of
	 *         the list will be equal to the number of rows, even if some or all
	 *         cells do not contain numeric values.
	 */
	private LinkedList<Long> getSeries(int colIndex)
	{
		LinkedList<Long> series = new LinkedList<Long>();

		for (Row curRow : rows)
		{
			Object data = curRow.getCellData(colIndex);
			long value;
			// Since the chart is time-based, we must not skip any column
			if (data == null)
			{
				value = 0;
			}
			else if (data instanceof Long)
			{
				value = (Long) data;
			}
			else if (data instanceof Integer)
			{
				value = (Integer) data;
			}
			else if (data instanceof Date)
			{
				value = ((Date) data).getTime();
			}
			else
			{
				value = 0;
			}
			series.add(value);
		}

		return series;
	}

	/**
	 * @param series
	 *            Series of numbers without nulls.
	 * @return Maximum positive value found in the list, or 0 if no values were
	 *         found.
	 */
	static long getSeriesMax(List<Long> series)
	{
		if (series == null)
			return 0;

		long result = 0;

		for (Long curValue : series)
		{
			if (curValue > result)
			{
				result = curValue;
			}
		}

		return result;
	}

	/**
	 * Adjust the given series to contain up to the given number of elements, by
	 * gathering elements in groups and saving the average value in a
	 * representative element.
	 * 
	 * @param series
	 *            Series to be adjusted, without null elements.
	 * @param maxElem
	 *            Maximum number of elements to leave in the series.
	 */
	static void adjustSeriesElements(LinkedList<Long> series, int maxElem)
	{
		if (series == null || series.size() <= maxElem)
			return;

		float factor = ((float) series.size()) / maxElem;
		float coverPos = 0;
		int originalPos = 0;

		ListIterator<Long> it = series.listIterator();
		while (it.hasNext())
		{
			Long curValue = it.next();

			coverPos += factor;
			originalPos++;

			Long sum = curValue;
			int count = 1;
			while (Math.round(coverPos - originalPos) >= 1 && it.hasNext())
			{
				sum += it.next();
				it.remove();
				originalPos++;
				count++;
			}

			// Update the average if needed
			if (count > 1)
			{
				it.previous();
				it.set(sum / count);
				it.next();
			}
		}
	}

	/**
	 * Print HTML bar chart based on a given column of numeric values.
	 * <p>
	 * Series is sorted by the first time column found in the table.
	 * 
	 * @param seriesColIndex
	 *            0-based column index to display.
	 */
	public void printHtmlChart(WebContext context, int seriesColIndex)
	{
		printHtmlChart(context, seriesColIndex, -1, false);
	}

	/**
	 * Print HTML chart based on a given column of numeric values.
	 * <p>
	 * Series is sorted by the first time column found in the table. If no time
	 * column is found, it tries to use the first numeric column.
	 * 
	 * @param seriesColIndex1
	 *            0-based column index to display in blue.
	 * @param seriesColIndex2
	 *            0-based column index to display in red. Can be negative if
	 *            only one series should be displayed.
	 * @param commonScale
	 *            If true, both seria will be using the same Y-axis scale.
	 *            Otherwise each will have a separate scale, according to its
	 *            maximum value.
	 */
	public void printHtmlChart(WebContext context, int seriesColIndex1,
			int seriesColIndex2, boolean commonScale)
	{
		if (seriesColIndex1 >= columns.size()
				|| seriesColIndex2 >= columns.size() || seriesColIndex1 < 0)
			return;

		//
		// Sort by the first time column
		//
		int timeColIndex = getTimeColIndexFirst(true);
		if (timeColIndex < 0)
			return;
		sort(timeColIndex, true);

		// Produce a series of long numbers out of the series column
		LinkedList<Long> timeSeries = getSeries(timeColIndex);
		LinkedList<Long> series1 = getSeries(seriesColIndex1);
		LinkedList<Long> series2 = seriesColIndex2 < 0 ? null
				: getSeries(seriesColIndex2);

		// Reduce number of elements to look better in chart
		adjustSeriesElements(timeSeries, CHART_AXIS_UNITS_X);
		adjustSeriesElements(series1, CHART_BARS);
		adjustSeriesElements(series2, CHART_BARS);

		// Get the maximum value
		long maxValue1 = getSeriesMax(series1);
		long maxValue2 = getSeriesMax(series2);

		//
		// Prepare title with series name
		//
		Column col = columns.get(seriesColIndex1);
		context.appendString("<h1 align=center><font color=blue>");
		context.appendString(col.getName().replace("<br>", " "));
		if (series2 != null)
		{
			col = columns.get(seriesColIndex2);
			context.appendString("</font>&nbsp;/&nbsp;<font color=red>");
			context.appendString(col.getName().replace("<br>", " "));
		}
		context.appendString("</font></h1>");

		// Print the chart
		printHtmlChart(context, timeSeries, series1, commonScale ? Math.max(
				maxValue1, maxValue2) : maxValue1, series2, commonScale ? 0
				: maxValue2, "blue", "red");
	}

	/**
	 * @param maxValue2
	 *            Positive number if Y-axis should be drawn for the second
	 *            series too.
	 */
	private static void printHtmlChart(WebContext context,
			List<Long> timeSeries, List<Long> series1, long maxValue1,
			List<Long> series2, long maxValue2, String color1, String color2)
	{
		// Safety check
		if (series1 == null || series1.isEmpty())
			return;

		//
		// Y axis
		//
		context
				.appendString("<table bgColor=#ffffff cellSpacing=0 cellPadding=0 border=0>"
						+ "<tr>");

		printHtmlChartAxisValues(context, maxValue1, color1);

		context.appendString("<td>");

		//
		// Chart area
		//
		long barWidth = Math.max(2, CHART_WIDTH / series1.size());
		context
				.appendString("<table bgColor=#f0ffff height=\""
						+ CHART_HEIGHT
						+ "\" width=\""
						+ (barWidth * series1.size() + 4)
						+ "\" cellSpacing=0 cellPadding=0 border=1><tr><td valign=bottom>");
		if (series2 != null)
		{
			barWidth /= 2;
		}

		Iterator<Long> it = (series2 == null ? null : series2.iterator());
		for (Long curValue : series1)
		{
			printHtmlChartBar(context, curValue, maxValue1, barWidth, color1);
			if (it != null && it.hasNext())
			{
				curValue = it.next();
				printHtmlChartBar(context, curValue, maxValue2 <= 0 ? maxValue1
						: maxValue2, barWidth, color2);
			}
		}

		context.appendString("</td></tr></table>");
		if (series2 != null && maxValue2 > 0)
		{
			printHtmlChartAxisValues(context, maxValue2, color2);
		}
		context.appendString("</tr><tr><td></td>");
		printHtmlChartAxisTime(context, timeSeries);
		context.appendString("</tr></table>");
	}

	private static void printHtmlChartAxisValues(WebContext context,
			long maxValue, String color)
	{
		if (maxValue <= 0)
			return;
		context
				.appendString("<td height=100%><table height=\"100%\" cellSpacing=0 cellPadding=0 border=0>");
		for (long i = CHART_AXIS_UNITS_Y; i >= 1; i--)
		{
			context
					.appendString("<tr><td valign=top height=\"10%\"><font color="
							+ color
							+ ">"
							+ String.format("%,d", maxValue * i / 10)
							+ "</font></td></tr>");
		}
		context.appendString("</table></td>");
	}

	private static void printHtmlChartAxisTime(WebContext context,
			List<Long> timeSeries)
	{
		int width = 100 / timeSeries.size();
		context
				.appendString("<td><table width=\"100%\" cellSpacing=0 cellPadding=0 border=0><tr>");
		for (Long timeNum : timeSeries)
		{
			Date curValue = null;
			// Low number probably means that it's a Long and not really a date
			if (timeNum > 1000000)
			{
				curValue = new Date(timeNum);
			}
			context.appendString("<td align=center valign=top width=");
			context.appendString(Integer.toString(width));
			context.appendString("%>");
			// Low number probably means that it's a Long and not really a date
			if (curValue == null)
			{
				context.appendString(timeNum.toString());
			}
			else
			{
				context.appendString(dateFormatChart.format(curValue).replace(
						" ", "<br>"));
			}
			context.appendString("</td>");
		}
		context.appendString("</tr></table></td>");
	}

	private static void printHtmlChartBar(WebContext context, long curValue,
			long maxValue, long barWidth, String color)
	{
		long barHeight = maxValue == 0 ? 0 : curValue * 100 / maxValue;
		context
				.appendString("<table height=100% cellSpacing=0 cellPadding=0 border=0 align=left>"
						+ "<tr"
						+ (barHeight <= 0 ? " height=100%" : "")
						+ "><td width=1></td><td width="
						+ (barWidth - 1)
						+ "></td></tr>");

		if (curValue > 0)
		{
			context.appendString("<tr><td></td><td height=");
			context.appendString(Long.toString(barHeight));
			context.appendString("% bgColor=");
			context.appendString(color);
			context.appendString(" title=");
			context.appendString(Long.toString(curValue));
			context.appendString("></td></tr>");
		}
		context.appendString("</table>");
	}

	/**
	 * Print the paged table to a WebGui.
	 * <p>
	 * Note: Only 16 color names are supported by the W3C HTML 4.0 standard
	 * (aqua, black, blue, fuchsia, gray, green, lime, maroon, navy, olive,
	 * purple, red, silver, teal, white, and yellow). For all other colors you
	 * should use the Color HEX value.
	 * 
	 * @param webGui
	 * @param headerColor
	 *            Optional color of header row, to override
	 *            {@link #setHeaderColor(String)}. Use null or empty string to
	 *            not override.
	 * @param showLineSerialNumber
	 *            When true, every line has a 1-based serial number on the first
	 *            column.
	 * @param showLastModified
	 *            If to show the time when last row was added to table, which
	 *            helps to determine how old is the table and add a
	 *            manual-refresh link. Use null if this display should be used
	 *            only when table is marked as cached.
	 */
	public void printHTMLTable(WebContext webGui, String headerColor,
			boolean showLineSerialNumber)
	{
		if (webGui == null)
			throw new InvalidParameterException("webGui can not be null");

		// In fields mode use the appropriate print method
		if (fieldsMode)
		{
			printHtmlFields(webGui);
			return;
		}

		if (headerColor == null || headerColor.equals(""))
		{
			headerColor = this.headerColor;
		}

		StringBuffer buffer = new StringBuffer(1000);

		//
		// Handle sorting
		//
		webGuiSort(webGui);

		//
		// Show headers
		//
		webGui.appendTableStart();

		webGui.appendTableRowStart(headerColor);

		// Optional serial number column
		if (showLineSerialNumber)
		{
			webGui
					.appendTableCol("&nbsp;&nbsp;&nbsp;",
							"Automatic serial number that is generated when table is displayed");
		}

		boolean isTextMode = webGui.isTextMode();
		for (int i = 0; i < getColumnsCount(); ++i)
		{
			Column col = columns.get(i);

			if (!isTextMode && col.isSortable)
			{
				//
				// Build sort links
				//
				String linkSort = webGui.getUrl();
				linkSort += linkSort.contains("?") ? "&" : "?";
				linkSort += "sortcol=" + i;
				linkSort += "&sortasc="
						+ ((i == sortColumn) ? Boolean.toString(!sortAscending)
								: Boolean.toString(col.initialSortAsc));

				buffer.setLength(0);
				buffer.append("<a href='");
				buffer.append(linkSort);
				buffer.append("' style='text-decoration: none; color: black'>");

				buffer.append(col.name);
				if (i == sortColumn)
				{
					buffer.append("</a>&nbsp;<font color='black'>");
					buffer.append(sortAscending ? "&darr;" : "&uarr;");
					buffer.append("</font>");
				}
				else
				{
					buffer.append("</a>");
				}

				webGui.appendTableCol(buffer.toString(), col.toolTip);
			}
			else
			{
				webGui.appendTableCol(col.name, col.toolTip);
			}
		}

		// webGui.setParam("sortcol", sortColumn);
		// webGui.setParam("sortasc", Boolean.toString(sortAscending));

		// TODO: Why doesn't webGui have appendRowEnd?
		// maybe it should remember internally the start and end on new start or
		// on table end

		//
		// Preparation of indexes, due to filtering
		//
		int i = -1;

		//
		// Show rows
		//
		for (Row row : rows)
		{
			++i;

			String color = onRowColor(i, row);
			if (color == null)
			{
				color = "";
			}
			webGui.appendString("\n");
			webGui.appendTableRowStart(color);

			// Optional serial number
			if (showLineSerialNumber)
			{
				webGui.appendTableCell(i + 1);
			}

			for (int j = 0; j < row.getLength(); j++)
			{
				Cell cell = row.getCell(j);
				Column col = columns.get(j);

				if (cell.data instanceof Date)
				{
					webGui.appendTableCell(cell.toString());
					continue;
				}

				// Date-time column can contain Date or long
				if (cell.data instanceof Long && col.dateFormat != null)
				{
					long longVal = ((Long) cell.data).longValue();
					if (longVal == 0)
					{
						webGui.appendTableCell("");
						continue;
					}
					Date displayTime = new Date(longVal);
					webGui.appendTableCell(cell.toString(col.dateFormat
							.format(displayTime)));
					continue;
				}

				// Numeric columns may be displayed in a non-default style
				if (cell.data instanceof Number)
				{
					long longVal = ((Number) cell.data).longValue();
					if (longVal == 0)
					{
						if (col.blankOnZero)
						{
							webGui.appendTableCell("");
							continue;
						}
					}
				}

				//
				// Handle the optional prefix and/or suffix
				//
				String cellString = cell.toString();
				// If the string is empty, don't show prefix and/or suffix
				if (cellString == null || cellString.equals(""))
				{
					webGui.appendTableCell("");
				}
				else
				{
					if (col.prefix == null)
					{
						if (col.suffix == null)
						{
							webGui.appendTableCell(cellString);
						}
						else
						{
							webGui.appendTableCell(cellString + col.suffix);
						}
					}
					else if (col.suffix == null)
					{
						webGui.appendTableCell(col.prefix + cellString);
					}
					else
					{
						webGui.appendTableCell(col.prefix + cellString
								+ col.suffix);
					}
				}
			}
		}
		webGui.appendTableEnd();
	}

	/**
	 * Handle sorting according to web gui parameters
	 * 
	 * @param webGui
	 */
	private void webGuiSort(WebContext webGui)
	{
		sortColumn = webGui.getParamAsInt("sortcol", -1);
		if (sortColumn == -1)
			sortColumn = defaultSortColumn;
		if (sortColumn < 0 || sortColumn >= columns.size())
			return;
		Column col = columns.get(sortColumn);
		// Don't sort unsortable columns. This can happen only when manually
		// playing with parameters
		if (!col.isSortable)
			return;
		sortAscending = webGui.getParamAsBool("sortasc", col.initialSortAsc);
		sort(sortColumn, sortAscending);
	}

	/**
	 * Set the default sort column by its zero-based column index.
	 * 
	 * @param zeroBasedSortCol
	 */
	public void setDefaultSortCol(int zeroBasedSortCol)
	{
		defaultSortColumn = zeroBasedSortCol;
	}

	/**
	 * Set the last added column as the default sort column.
	 */
	public void setLastColAsDefaultSortCol()
	{
		defaultSortColumn = (columns.size() - 1);
	}

	/**
	 * Set the color for the header.
	 * <p>
	 * Note: Only 16 color names are supported by the W3C HTML 4.0 standard
	 * (aqua, black, blue, fuchsia, gray, green, lime, maroon, navy, olive,
	 * purple, red, silver, teal, white, and yellow). For all other colors you
	 * should use the Color HEX value.
	 * 
	 * @param color
	 *            - as accepted in HTML
	 */
	public void setHeaderColor(String color)
	{
		if (color == null)
			color = "";
		headerColor = color;
	}

	/**
	 * Get last time a row was added to the table
	 * 
	 * @return the time (as reported in System.currentMilliseconds
	 */
	public long getLastAccessed()
	{
		return lastAccessed;
	}

	/**
	 * @return True if too much time passed since this table was built. Default
	 *         is 5 minutes.
	 */
	public boolean shouldRefresh(WebContext context)
	{
		// Handle manual refresh
		if (context != null
				&& context.getParamAsBool("refreshtable", false) == true)
			return true;

		return ((System.currentTimeMillis() - getLastAccessed()) > freshMillis);
	}

	/**
	 * Get the color for a specific row
	 * <p>
	 * By default this return alternately "grey" and "lightgrey"<br>
	 * Method should be overriden by descndants in order to supply other
	 * coloring, for example based on the row's contents
	 * 
	 * @param rowIndex
	 * @param row
	 * @return the color, to be used in HTML
	 */
	protected String onRowColor(int rowIndex, Row row)
	{
		if (row.color == null || row.color.equals(""))
			return (rowIndex % 2 == 1) ? "#eeeeee" : "#dddddd";
		return row.color;
	}

	/**
	 * Comparator of rows according to specific column
	 * <p>
	 * The comparator supports ascending and descending sorts, and numeric and
	 * string sorts<br>
	 * If a numeric sort is requested the column mus contain nothing but numbers
	 * or a NumberFormatException will be thrown
	 */
	private class MyComp implements Comparator<Row>
	{
		private int		columnIndex;
		private boolean	isAscending;
		private boolean	numeric;
		private boolean	link;

		public MyComp(int columnIndex, boolean isAscending, boolean numeric,
				boolean link)
		{
			this.columnIndex = columnIndex;
			this.isAscending = isAscending;
			this.numeric = numeric;
			this.link = link;
		}

		private int intCompare(int val1, int val2)
		{
			if (val1 > val2)
				return 1;
			if (val2 > val1)
				return -1;
			return 0;
		}

		private int inetAddressCompare(InetAddress addr1, InetAddress addr2)
		{
			if (addr1 == null)
				return addr2 == null ? 0 : -1;
			if (addr2 == null)
				return 1;
			for (int i = 0; i < 4; i++)
			{
				int byte1 = addr1.getAddress()[i] & 0x000000ff;
				int byte2 = addr2.getAddress()[i] & 0x000000ff;
				int curCompare = ((Integer) byte1).compareTo((Integer) byte2);
				if (curCompare != 0)
					return curCompare;
			}
			return 0;
		}

		private int inetSocketAddressCompare(InetSocketAddress addr1,
				InetSocketAddress addr2)
		{
			int addrComp = inetAddressCompare(addr1.getAddress(), addr2
					.getAddress());
			if (addrComp != 0)
				return addrComp;
			return ((Integer) addr1.getPort()).compareTo(addr2.getPort());
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public int compare(Row row1, Row row2)
		{
			Object val1 = row1.getCellData(columnIndex);
			Object val2 = row2.getCellData(columnIndex);

			//
			// Testing values for null values
			//
			if (val1 == null && val2 == null)
				return 0;
			if (val1 == null)
				return isAscending ? -1 : 1;
			if (val2 == null)
				return isAscending ? 1 : -1;

			//
			// If values are not of the same class or not comparable, it's an
			// error, so compare their class names
			//
			if (val1.getClass() != val2.getClass())
			{
				return isAscending ? val1.getClass().getName().compareTo(
						val2.getClass().getName()) : val2.getClass().getName()
						.compareTo(val1.getClass().getName());
			}

			//
			// Handle the complicated case of strings that may contain numbers
			// and/or links
			//
			if (val1 instanceof String)
			{
				String str1 = (String) val1;
				String str2 = (String) val2;

				if (link)
				{
					int pos1 = str1.indexOf('>');
					int pos2 = str1.lastIndexOf('<');
					if (pos1 > -1 && pos2 > -1)
					{
						str1 = str1.substring(pos1 + 1, pos2);
					}

					pos1 = str2.indexOf('>');
					pos2 = str2.lastIndexOf('<');
					if (pos1 > -1 && pos2 > -1)
					{
						str2 = str2.substring(pos1 + 1, pos2);
					}
				}

				if (numeric)
				{
					int num1 = Integer.parseInt(str1);
					int num2 = Integer.parseInt(str2);

					return (isAscending ? intCompare(num1, num2) : intCompare(
							num2, num1));
				}

				return (isAscending ? str1.compareTo(str2) : str2
						.compareTo(str1));
			}

			//
			// InetSocketAddress
			//
			if (val1 instanceof InetSocketAddress)
			{
				return isAscending ? inetSocketAddressCompare(
						(InetSocketAddress) val1, (InetSocketAddress) val2)
						: inetSocketAddressCompare((InetSocketAddress) val2,
								(InetSocketAddress) val1);
			}

			//
			// InetAddress
			//
			if (val1 instanceof InetAddress)
			{
				return isAscending ? inetAddressCompare((InetAddress) val1,
						(InetAddress) val2) : inetAddressCompare(
						(InetAddress) val2, (InetAddress) val1);
			}

			//
			// If class does not support native sort, use the hash-code
			//
			if (!(val1 instanceof Comparable))
			{
				return ((Integer) val1.hashCode()).compareTo(val2.hashCode());
			}

			return (isAscending ? ((Comparable) val1)
					.compareTo((Comparable) val2) : ((Comparable) val2)
					.compareTo((Comparable) val1));
		}
	}

	/**
	 * Row class that holds all the cells and their data along with some row
	 * properties.
	 */
	protected class Row
	{
		ArrayList<Cell>	cells;
		private String	color;

		/**
		 * @param data
		 */
		public void addCell(Cell cell)
		{
			cells.add(cell);
		}

		/**
		 * @param color
		 *            Html color name or code.
		 * @param expectedCellsCount
		 *            Optional number of expected cells, for better performance.
		 *            Zero is allowed, but not recommended, because even random
		 *            number like 2 or 3 is better.
		 */
		Row(String color, int expectedCellsCount)
		{
			if (expectedCellsCount > 0)
			{
				this.cells = new ArrayList<Cell>(expectedCellsCount);
			}
			else
			{
				this.cells = new ArrayList<Cell>();
			}
			this.color = color;
		}

		public int getLength()
		{
			if (cells == null)
			{
				return 0;
			}
			return cells.size();
		}

		/**
		 * @return Content of the cell, or null if no such cell exists in this
		 *         row.
		 */
		Object getCellData(int columnIndex)
		{
			if (cells == null || columnIndex < 0 || columnIndex >= cells.size())
				return null;

			return cells.get(columnIndex).data;
		}

		private Cell getCell(int columnIndex)
		{
			if (cells == null || columnIndex < 0 || columnIndex >= cells.size())
			{
				return null;
			}
			return cells.get(columnIndex);
		}

		public ArrayList<Cell> getCells()
		{
			return cells;
		}
	}

	private class Cell
	{
		protected Object	data;
		protected String	link;
		protected String	tooltip;
		protected boolean	bold;
		protected boolean	small;
		protected String	color;
		protected boolean	linkNewWindow;
		protected boolean	commaSeperated;

		/**
		 * @param data
		 *            Data to be displayed.
		 * @param link
		 *            Optional link.
		 * @param title
		 *            Optional text to display when mouse hovers
		 * @param bold
		 *            True if should display in bold.
		 * @param small
		 *            True if should display in small text size.
		 */
		public Cell(Object data, String link, String tooltip, boolean bold,
				boolean small, String color, boolean linkNewWindow,
				boolean commaSeperated)
		{
			this.data = data;
			this.link = link;
			this.tooltip = tooltip;
			this.bold = bold;
			this.small = small;
			this.color = color;
			this.linkNewWindow = linkNewWindow;
			this.commaSeperated = commaSeperated;
		}

		String getDataString()
		{
			if (data == null)
				return "";

			if (data instanceof Number)
			{
				long dataLong = ((Number) data).longValue();

				if (commaSeperated)
				{
					return String.format("%,d", dataLong);
				}
				return Long.toString(dataLong);
			}

			if (data instanceof Hash)
			{
				// Hash has a short display format if it is too-long
				Hash hashVal = (Hash) data;
				return hashVal.toString(2);
			}

			if (data instanceof Date)
			{
				Date displayTime = (Date) data;
				if (displayTime.getTime() == 0)
					return "";

				return dateFormatDateTime.format(displayTime);
			}

			if (data instanceof List<?>)
			{
				return convertToTable((List<?>) data);
			}

			return data.toString();
		}

		private String convertToTable(List<?> list)
		{
			StringBuffer buff = new StringBuffer();

			if (list.size() > 0)
			{
				buff
						.append("<table rules='rows' style='font-size:8pt; border-color: #FFFFFF; border-style:solid; border-width:0px;' width='%100'>");
				for (Object item : list)
				{
					if (item instanceof Date)
					{
						buff.append("<tr><td>");
						Date displayTime = (Date) item;
						if (displayTime.getTime() != 0)
							buff.append(dateFormatDateTime.format(displayTime));

						buff.append("</td></tr>");
					}
					else
					{
						buff
								.append("<tr><td>" + item.toString()
										+ "</td></tr>");
					}
				}
				buff.append("</table>");
			}

			return buff.toString();
		}

		private String wrapWithToolTip(boolean withAnchor, String wrappedText)
		{
			return tooltip != null ? ((withAnchor ? "<a " : "") + "title = '"
					+ tooltip + "'>" + (wrappedText != null ? wrappedText : "") + (withAnchor ? "</a>"
					: ""))
					: (wrappedText != null ? wrappedText : "");
		}

		private String addToolTip()
		{
			return wrapWithToolTip(false, null);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString()
		{
			if (link == null)
			{
				if (color != null)
				{
					return "<span style='background-color:" + color + "';>"
							+ wrapWithToolTip(true, getDataString())
							+ "</span>";
				}

				if (bold)
				{
					if (small)
						return "<b><small>"
								+ wrapWithToolTip(true, getDataString())
								+ "</small></b>";
					return "<b>" + wrapWithToolTip(true, getDataString())
							+ "</b>";
				}
				if (small)
					return "<small>" + wrapWithToolTip(true, getDataString())
							+ "</small>";

				return wrapWithToolTip(true, getDataString());
			}
			return "<a " + (linkNewWindow ? "target=new " : "") + "href='"
					+ link + "' " + addToolTip() + ">" + (bold ? "<b>" : "")
					+ (small ? "<small>" : "") + getDataString()
					+ (small ? "</small>" : "") + (bold ? "</b>" : "") + "</a>";
		}

		public String toString(String dataString)
		{
			if (link == null)
			{
				if (bold)
				{
					if (small)
						return "<b><small>" + wrapWithToolTip(true, dataString)
								+ "</small></b>";
					return "<b>" + dataString + "</b>";
				}
				if (small)
					return "<small>" + wrapWithToolTip(true, dataString)
							+ "</small>";
				return dataString;
			}
			return "<a " + (linkNewWindow ? "target=new " : "") + "href='"
					+ link + "'" + addToolTip() + ">" + (bold ? "<b>" : "")
					+ (small ? "<small>" : "") + dataString
					+ (small ? "</small>" : "") + (bold ? "</b>" : "") + "</a>";
		}
	}

	public int getRowsCount()
	{
		return rows.size();
	}

	/**
	 * @return Full table content as simple text, to be printed to console.
	 *         Mainly for debug.
	 */
	@Override
	public String toString()
	{
		StringBuffer buffer = new StringBuffer(columns.size() * rows.size()
				* 10);

		for (Column curCol : columns)
		{
			buffer.append(curCol.name);
			buffer.append('\t');
		}

		buffer.deleteCharAt(buffer.length() - 1);
		buffer.append('\n');

		//
		// Rows
		//
		for (Row curRow : rows)
		{
			for (Cell curCell : curRow.cells)
			{
				if (curCell.data instanceof Date)
				{
					// Date should be in format that is easily understood by
					// Excel and Google docs
					buffer.append(dateFormatText.format((Date) (curCell.data)));
				}
				else if (curCell.data instanceof Number)
				{
					// Google docs don't accept comma-separated as numeric
					// values
					long dataLong = ((Number) curCell.data).longValue();
					buffer.append(Long.toString(dataLong));
				}
				else
				{
					buffer.append(curCell.getDataString());
				}
				buffer.append('\t');
			}

			buffer.deleteCharAt(buffer.length() - 1);
			buffer.append('\n');
		}

		return buffer.toString();
	}

	/**
	 * Get data by specific key.
	 * <p>
	 * For example, a table with two columns: string and long. You can call
	 * getCell(0, "Einat", 1) to get the number associated with the key "Einat".
	 * 
	 * @param keyColIndex
	 *            0-based column index of the key column.
	 * @param key
	 *            The key to match with the key column.
	 * @param retColIndex
	 *            0-based column index of the returned data.
	 * @return Data held in the row and column specified, or null if no such
	 *         row/column exists.
	 */
	public Object getCell(int keyColIndex, Object key, int retColIndex)
	{
		//
		// Rows
		//
		for (Row curRow : rows)
		{
			Cell keyCell = curRow.cells.get(keyColIndex);
			if (keyCell.data.equals(key))
			{
				if (retColIndex < curRow.cells.size())
					return curRow.cells.get(retColIndex).data;

				return null;
			}
		}

		return null;
	}

	/**
	 * Get data by row and column.
	 * 
	 * @param colIndex
	 *            0-based, up to ({@link #getColumnsCount()} -1).
	 * @param rowIndex
	 *            0-based, up to ({@link #getRowsCount()} -1).
	 * @return Data held in the row and column specified, or null if no such
	 *         row/column exists.
	 */
	public Object getCell(int colIndex, int rowIndex)
	{
		Row curRow = rows.get(rowIndex);
		if (curRow == null)
			return null;

		Cell keyCell = curRow.cells.get(colIndex);
		if (keyCell == null)
			return null;

		return keyCell.data;
	}

	/**
	 * Add the next cell in a specific row, or add new cell if this cell does
	 * not exist but is the next one to add.
	 * 
	 * @param keyColIndex
	 *            0-based column index of the key column.
	 * @param key
	 *            The key to match with the key column.
	 * @param dataColIndex
	 *            0-based column index of the data to set.
	 * @param data
	 *            The new data to set. Can be null.
	 * @return False if no such row/column exists.
	 */
	public boolean setCell(int keyColIndex, Object key, int dataColIndex,
			Object data, String link)
	{
		//
		// Rows
		//
		for (Row curRow : rows)
		{
			Cell keyCell = curRow.cells.get(keyColIndex);
			if (keyCell.data.equals(key))
			{
				// If this is a new cell
				if (dataColIndex == curRow.cells.size())
				{
					curRow.addCell(new Cell(data, link, null, false, false,
							null, false, columns.get(currCol).commaSeparator));
					return true;
				}

				// If this cell already exists
				if (dataColIndex < curRow.cells.size())
				{
					Cell cell = curRow.cells.get(dataColIndex);
					cell.data = data;
					cell.link = link;
					return true;
				}

				return false;
			}
		}

		return false;
	}

	/**
	 * Add the next cell in a specific row, or add new cell if this cell does
	 * not exist but is the next one to add.
	 * 
	 * @param colIndex
	 *            0-based, up to ({@link #getColumnsCount()} -1).
	 * @param rowIndex
	 *            0-based, up to ({@link #getRowsCount()} -1).
	 * @param data
	 *            The new data to set. Can be null.
	 * @return False if no such row/column exists.
	 */
	public boolean setCell(int rowIndex, int colIndex, Object data, String link)
	{
		Row curRow = rows.get(rowIndex);
		if (curRow == null)
			return false;

		Cell keyCell = curRow.cells.get(colIndex);
		if (keyCell == null)
			return false;

		// If this is a new cell
		if (colIndex == curRow.cells.size())
		{
			curRow.addCell(new Cell(data, link, null, false, false, null,
					false, columns.get(currCol).commaSeparator));
			return true;
		}

		// If this cell already exists
		if (colIndex < curRow.cells.size())
		{
			Cell cell = curRow.cells.get(colIndex);
			cell.data = data;
			cell.link = link;
			return true;
		}

		return false;
	}

	/**
	 * Clear everything: columns, rows and filters.
	 */
	public void clear()
	{
		columns.clear();
		rows.clear();
	}

	/**
	 * @param colIndex
	 *            0-based column index.
	 * @return Name of column, or null if no such column exists.
	 */
	public String getColName(int colIndex)
	{
		if (colIndex >= columns.size())
			return null;
		return columns.get(colIndex).name;
	}

	/**
	 * @return 0-based column index, or -1 if no such column exists.
	 */
	public int getColIndex(String colName)
	{
		if (colName == null || colName.equals(""))
			return -1;

		for (int i = 0; i < columns.size(); i++)
		{
			Column curColumn = columns.get(i);
			if (colName.equals(curColumn.name))
				return i;
		}

		return -1;
	}
}
