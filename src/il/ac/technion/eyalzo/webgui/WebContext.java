package il.ac.technion.eyalzo.webgui;

import il.ac.technion.eyalzo.common.Hash;
import il.ac.technion.eyalzo.common.StringUtils;
import il.ac.technion.eyalzo.pack.NetUtils;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.sun.net.httpserver.HttpExchange;

public class WebContext
{
	/**
	 * Date and time formatters.
	 */
	protected static final DateFormat dateFormatTimeMin = DateFormat
			.getTimeInstance(DateFormat.SHORT, Locale.UK);
	protected static final DateFormat dateFormatDate = DateFormat
			.getDateInstance(DateFormat.SHORT, Locale.UK);

	public static final String HTTP_1_1_403_FORBIDDEN_HEADER = "HTTP/1.1 403 Forbidden\r\n";
	public static final String HTTP_1_1_200_OK_HEADER = "HTTP/1.1 200 OK\r\n";
	/**
	 * Large output buffer to hold the returned page/file.
	 */
	public StringBuffer outputBuffer;
	/**
	 * The full-path of the URL with the query-string, which is the part right
	 * after the slash that follows the host name.
	 */
	protected URI uri;
	/**
	 * The command part of the URL, which is part of the {@link #fullPath}, up
	 * to the ? sign.
	 */
	public String command;
	/**
	 * Map that holds all query-string parameters, to be fetched with one of the
	 * relevant methods.
	 */
	private Map<String, String> paramMap = new HashMap<String, String>();

	/**
	 * Parameter's key indicating that the output should be in simple text
	 * rather then in formatted html. This mode enables external application to
	 * obtain/parse data more easily.
	 */
	private static final String TEXT_MODE = "textmode";

	/**
	 * Parameter's key indicating that the column separator when using text
	 * mode.
	 */
	private static final String TEXT_SEPARATOR = "separator";

	public WebContext(HttpExchange http)
	{
		this.uri = http.getRequestURI();
		generateCommandAndParamsMap();
		this.outputBuffer = new StringBuffer(10000);
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return URL-decoded param value, or "" if not found.
	 */
	public String getParamAsString(String paramName)
	{
		String result = paramMap.get(paramName.toLowerCase());
		if (result == null)
			return "";
		return result;
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Param value as it was sent by the remote agent, without
	 *         URL-decoding, or "" if not found.
	 */
	public String getParamAsStringOriginal(String paramName)
	{
		String result = paramMap.get(paramName.toLowerCase() + "_encoded");
		if (result == null)
			return "";
		return result;
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Param value as it was sent by the remote agent, without
	 *         URL-decoding, or "" if not found.
	 */
	public byte[] getParamAsByteArray(String paramName)
	{
		String valueOriginal = paramMap.get(paramName.toLowerCase()
				+ "_encoded");
		if (valueOriginal == null)
			return null;
		try
		{
			String valueUrlDecoded = URLDecoder.decode(valueOriginal,
					"ISO-8859-1");
			byte[] result = valueUrlDecoded.getBytes("ISO-8859-1");
			return result;
		} catch (UnsupportedEncodingException e)
		{
		}
		return null;
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @param defaultResult
	 *            In case the given param was not found.
	 * @return URL-decoded param value if exists (even if empty), or default if
	 *         not found.
	 */
	public String getParamAsString(String paramName, String defaultResult)
	{
		String result = paramMap.get(paramName.toLowerCase());
		if (result == null)
			return defaultResult;
		return result;
	}

	/**
	 * @param paramName
	 * @return boolean true if param exists in command
	 */
	public boolean isParamExists(String paramName)
	{
		return paramMap.containsKey(paramName);
	}

	/**
	 * Get parameter that may contain & symbol, so it is assumed to be last
	 * parameter and the entire string after it is returned.
	 * 
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Param value, until the end of the line, or "" if not found.
	 */
	public String getParamAsUrl(String paramName)
	{
		// Url parameter must be last, because it may be parsed to several
		// parameters
		String query = uri.getQuery();
		String searchString = paramName + "=";
		int index = query.indexOf(searchString);
		if (index < 1 || query.charAt(index - 1) != '?'
				&& query.charAt(index - 1) != '&')
			return "";

		return query.substring(index + searchString.length());
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Null if nothing was found.
	 */
	public String[] getParamAsStrings(String paramName)
	{
		String entireString = paramMap.get(paramName.toLowerCase());
		if (entireString == null)
			return null;

		String[] parts = entireString.split(",");

		return parts;
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Null if nothing was found, or list with at least one address. All
	 *         addresses are resolved, but not with reverse DNS but only parsed.
	 */
	public List<InetSocketAddress> getParamAsInetSocketAddressList(
			String paramName)
	{
		String[] addrArray = getParamAsStrings(paramName);
		if (addrArray == null)
			return null;

		List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(
				addrArray.length);
		for (String curAddrStr : addrArray)
		{
			InetSocketAddress curAddr = NetUtils
					.ipAndPortAsInetSocketAddress(curAddrStr);
			if (curAddr != null)
			{
				addresses.add(curAddr);
			}
		}

		return addresses;
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Null if parameter was not found or its format does not match the
	 *         IP pattern.
	 */
	public InetAddress getParamAsIpAddress(String paramName)
	{
		String addressString = paramMap.get(paramName.toLowerCase());
		if (addressString == null)
			return null;

		// Remove leading slash if needed
		if (addressString.startsWith("/"))
		{
			addressString = addressString.substring(1);
		}

		// Remove port number if exists
		int portIndex = addressString.indexOf(':');
		if (portIndex > 0)
		{
			addressString = addressString.substring(0, portIndex);
		}

		return NetUtils.ipAsInetAddress(addressString);
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return True if found the param with value "1", "true" or "yes"
	 *         (case-insensitive).
	 */
	public boolean getParamAsBool(String paramName)
	{
		String result = paramMap.get(paramName.toLowerCase());
		if (result == null)
			return false;
		return result.equals("1") || result.equalsIgnoreCase("yes")
				|| result.equalsIgnoreCase("true") || result.equals("on");
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @param defaultVal
	 *            What to return when param was not set.
	 * @return True if found the param with value "1", "true" or "yes"
	 *         (case-insensitive). See
	 * @param defaultVal
	 *            .
	 */
	public boolean getParamAsBool(String paramName, boolean defaultVal)
	{
		String result = paramMap.get(paramName.toLowerCase());
		if (result == null)
			return defaultVal;
		return result.equals("1") || result.equalsIgnoreCase("yes")
				|| result.equalsIgnoreCase("true") || result.equals("on");
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Null if parameter was not found or its format does not match the
	 *         "IP:port" pattern.
	 */
	public InetSocketAddress getParamAsAddress(String paramName)
	{
		String addressString = paramMap.get(paramName.toLowerCase());
		if (addressString == null)
			return null;

		int sepIndex = addressString.indexOf(':');
		if (sepIndex == -1)
			return null;

		int port;
		try
		{
			port = Integer.parseInt(addressString.substring(sepIndex + 1));
			if (addressString.startsWith("/"))
				return new InetSocketAddress(addressString.substring(1,
						sepIndex), port);

			return new InetSocketAddress(addressString.substring(0, sepIndex),
					port);
		} catch (Exception e)
		{
			return null;
		}
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Param value, or 0 if not found.
	 */
	public int getParamAsInt(String paramName)
	{
		return getParamAsInt(paramName, 0);
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Param value, or given default if not found.
	 */
	public int getParamAsInt(String paramName, int defaultValue)
	{
		String valString = paramMap.get(paramName.toLowerCase());
		if (valString == null)
			return defaultValue;

		try
		{
			return Integer.parseInt(valString);
		} catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Param value, or 0 if not found.
	 */
	public long getParamAsLong(String paramName)
	{
		return getParamAsLong(paramName, 0L);
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @param defaultValue
	 *            Default value to return if no value was found.
	 * @return Param value, or given default if not found or parsing error
	 *         occurred.
	 */
	public long getParamAsLong(String paramName, long defaultValue)
	{
		String valString = paramMap.get(paramName.toLowerCase());
		if (valString == null)
			return defaultValue;

		long result;
		try
		{
			result = Long.parseLong(valString);
		} catch (NumberFormatException e)
		{
			result = defaultValue;
		}
		return result;
	}

	/**
	 * @param paramName
	 *            Name of parameter to get.
	 * @return Valid hash or null.
	 */
	public Hash getParamAsHash(String paramName)
	{
		String valString = paramMap.get(paramName.toLowerCase());
		if (valString == null)
			return null;

		Hash fileHash = new Hash(valString);
		if (!fileHash.isValid())
		{
			return null;
		}
		return fileHash;
	}

	/**
	 * Separate the command part, and query parameters as map of strings.
	 */
	void generateCommandAndParamsMap()
	{
		paramMap.clear();

		// Isolate the command
		command = uri.getPath();

		// Cut what is found before the first param
		String query = uri.getQuery();
		if (query == null)
			return;

		String paramsArray[] = query == null ? null : query.split("[&]");

		for (String curCouple : paramsArray)
		{
			int i = curCouple.indexOf('=');
			if (i == -1)
				continue;
			// Try to url-decode
			String paramNameDecoded = curCouple.substring(0, i).toLowerCase();
			String valueEncoded = curCouple.substring(i + 1);

			paramMap.put(paramNameDecoded, valueEncoded);
		}
	}

	/**
	 * Append a string to the output.
	 * 
	 * @param htmlText
	 *            String to append.
	 */
	public void appendString(String htmlText)
	{
		outputBuffer.append(htmlText);
	}

	/**
	 * Append a paragraph to the output.
	 * <p>
	 * It only adds the &lt;p&gt; element to the text.
	 * 
	 * @param htmlText
	 *            String to append.
	 */
	public void appendParagraph(String htmlText)
	{
		outputBuffer.append("<p>");
		outputBuffer.append(htmlText);
		outputBuffer.append("</p>");
	}

	/**
	 * Append a string to the output.
	 * 
	 * @param htmlText
	 *            String to append.
	 */
	protected void appendInteger(int num)
	{
		outputBuffer.append(Integer.toString(num));
	}

	public String setParam(String paramName, boolean paramValue)
	{
		return setParam(paramName, paramValue ? "1" : "0");
	}

	public String setParam(String paramName, String paramValue)
	{
		String path = this.uri.getPath();
		String query = "";
		boolean found = false;

		for (Iterator<Entry<String, String>> it = paramMap.entrySet()
				.iterator(); it.hasNext();)
		{
			Entry<String, String> entry = it.next();
			String curName = entry.getKey();
			String curValue = entry.getValue();

			query += curName;
			query += "=";
			if (paramName.equals(curName))
			{
				found = true;
				query += paramValue;
			} else
			{
				query += curValue;
			}
			query += "&";
		}

		if (!found)
		{
			query += paramName;
			query += "=";
			query += paramValue;
		}

		// Set for later use of set or get
		paramMap.put(paramName, paramValue);

		return path + "?" + query;
	}

	/**
	 * Write HTML header to buffer, with optional automatic refresh.
	 * 
	 * @param refreshSeconds
	 *            Automatic refresh, or now refresh if zero.
	 */
	public void appendHtmlHeader(String pageSubTitle, int refreshSeconds,
			boolean mainMenuLink)
	{
		outputBuffer.append("<html><head>");

		//
		// If there is an action param, then remove it and refresh
		//
		int i;
		String query = uri.getQuery();
		if ((i = query.indexOf("action=")) == 0
				|| (i = query.indexOf("&action=")) >= 0)
		{
			outputBuffer.append("<meta http-equiv='refresh' content='0;");
			String fixedFullPath = query.substring(0, i + 1) + "old"
					+ query.substring(i + 1);
			outputBuffer.append(fixedFullPath);
			outputBuffer.append("'></head></html>");
			return;
		}

		if (refreshSeconds > 0)
		{
			outputBuffer.append("<meta http-equiv='refresh' content='");
			outputBuffer.append(Integer.toString(refreshSeconds));
			outputBuffer.append("'>\n");
		}

		//
		// Page title
		//
		outputBuffer.append("<title>");
		if (pageSubTitle != null && pageSubTitle.length() > 0)
		{
			outputBuffer.append(pageSubTitle);
		}
		outputBuffer.append("</title></head><body>\n");

		//
		// Optional link to main menu
		//
		if (mainMenuLink)
		{
			outputBuffer.append("<p><a href='/'><b>Main menu</b></a></p>\n");
		}
	}

	public void putHtmlFooter()
	{
		outputBuffer.append("</body></html>");
	}

	/**
	 * Append major header to output text.
	 * 
	 * @param header
	 */
	public void appendHeaderMajor(String header)
	{
		outputBuffer.append("<h1>");
		outputBuffer.append(header);
		outputBuffer.append("</h1>\n");
	}

	/**
	 * Append minor header to output text.
	 * 
	 * @param header
	 */
	public void appendHeaderMinor(String header)
	{
		outputBuffer.append("<h2>");
		outputBuffer.append(header);
		outputBuffer.append("</h2>\n");
	}

	public void appendTableStart()
	{
		if (isTextMode())
		{
			outputBuffer.append("<pre>");
			outputBuffer.append("~Table Start~");
			return;
		}

		outputBuffer.append("<table border=\"0\" style=\"font-size:70%\">");

	}

	public void appendTableRowStart()
	{
		if (isTextMode())
		{
			outputBuffer.append("\n");
			return;
		}

		outputBuffer.append("<tr>");
	}

	/**
	 * @param color
	 *            Html color, like "#5D5D5D" or "red".
	 */
	public void appendTableRowStart(String color)
	{
		if (isTextMode())
		{
			outputBuffer.append("\n");
			return;
		}

		outputBuffer.append("<tr bgcolor=\"" + color + "\">");
	}

	public void appendTableCell(Object cellContent)
	{
		if (isTextMode())
		{
			outputBuffer.append(cellContent.toString() + getTextSeparator());
			return;
		}

		if (cellContent == null)
		{
			outputBuffer.append("<td>&nbsp;</td>");
			return;
		}
		outputBuffer.append("<td>");
		outputBuffer.append(cellContent.toString());
		outputBuffer.append("</td>");
	}

	/**
	 * Table cell that is part of the first row that functions as a header.
	 * 
	 * @param colName
	 *            Column name.
	 * @param toolTip
	 *            Optional tool-tip to display when cursor stays on the column
	 *            name. Make sure this tool tip already follows the HTML rules,
	 *            like encoding of " and &.
	 */
	public void appendTableCol(String colName, String toolTip)
	{
		if (isTextMode())
		{
			outputBuffer.append(colName.toString().trim());
			outputBuffer.append(getTextSeparator());
			return;
		}

		if (toolTip != null && !toolTip.equals(""))
		{
			outputBuffer.append("<td><b title=\"");
			outputBuffer.append(toolTip);
			outputBuffer.append("\">");
		} else
		{
			outputBuffer.append("<td><b>");
		}
		outputBuffer.append(colName.toString());
		outputBuffer.append("</b></td>");
	}

	/**
	 * Append table-cell with a link.
	 * 
	 * @param cellContent
	 *            What to display.
	 * @param linkUrl
	 *            The link URL.
	 */
	public void appendTableCellLink(Object cellContent, String linkUrl)
	{
		if (isTextMode())
		{
			outputBuffer.append(linkUrl);
			return;
		}

		outputBuffer.append("<td><a href=\"");
		outputBuffer.append(linkUrl);
		outputBuffer.append("\">");
		outputBuffer.append(cellContent.toString());
		outputBuffer.append("</a></td>");
	}

	/**
	 * Append table-cell with a link, as action-button, bold with "...".
	 * 
	 * @param cellContent
	 *            What to display.
	 * @param linkUrl
	 *            The link URL.
	 */
	public void appendTableCellLinkAction(Object cellContent, String linkUrl)
	{
		if (isTextMode())
		{
			outputBuffer.append(linkUrl);
			return;
		}

		outputBuffer.append("<td><a href=\"");
		outputBuffer.append(linkUrl);
		outputBuffer.append("\"><b>");
		outputBuffer.append(cellContent.toString());
		outputBuffer.append("...</b></a></td>");
	}

	/**
	 * Append table cell for numeric value to display with a comma-separator.
	 * 
	 * @param num
	 *            Number to display.
	 */
	public void appendTableCellNum(int num)
	{
		if (isTextMode())
		{
			outputBuffer.append(String.format("%d", num));
			return;
		}

		outputBuffer.append("<td>");
		outputBuffer.append(String.format("%,d", num));
		outputBuffer.append("</td>");
	}

	/**
	 * Append table cell for numeric value to display with a comma-separator,
	 * leave blank when zero.
	 * 
	 * @param num
	 *            Number to display.
	 * @param zeroBlank
	 *            When true and number is zero, leave a blank cell instead of
	 *            writing "0".
	 * @param minToEmphasize
	 *            When 0 or number is smaller than this, do nothing special.
	 *            Otherwise, write as bold.
	 */
	public void appendTableCellNum(int num, boolean zeroBlank,
			int minToEmphasize)
	{
		if (isTextMode())
		{
			outputBuffer.append(String.format("%d", num));
			return;
		}

		//
		// If zero and asked to leave blank on zero then leave a blank cell
		//
		if (zeroBlank && num == 0)
		{
			outputBuffer.append("<td>&nbsp;</td>");
			return;
		}

		//
		// Emphasize if asked to
		//
		if (minToEmphasize != 0 && num >= minToEmphasize)
		{
			outputBuffer.append("<td><b>");
			outputBuffer.append(String.format("%,d", num));
			outputBuffer.append("</b></td>");
			return;
		}

		//
		// Normal display
		//
		outputBuffer.append("<td>");
		outputBuffer.append(String.format("%,d", num));
		outputBuffer.append("</td>");
	}

	/**
	 * Append table cell for numeric value to display with a comma-separator.
	 * 
	 * @param num
	 *            Number to display.
	 */
	public void appendTableCellNum(long num)
	{
		if (isTextMode())
		{
			outputBuffer.append(String.format("%d", num));
			return;
		}
		outputBuffer.append("<td>");
		outputBuffer.append(String.format("%,d", num));
		outputBuffer.append("</td>");
	}

	/**
	 * Append table cell for numeric value to display with a comma-separator,
	 * leave blank when zero.
	 * 
	 * @param num
	 *            Number to display.
	 * @param zeroBlank
	 *            When true and number is zero, leave a blank cell instead of
	 *            writing "0".
	 * @param minToEmphasize
	 *            When 0 or number is smaller than this, do nothing special.
	 *            Otherwise, write as bold.
	 */
	public void appendTableCellNum(long num, boolean zeroBlank,
			long minToEmphasize)
	{
		if (isTextMode())
		{
			outputBuffer.append(String.format("%d", num));
			return;
		}

		//
		// If zero and asked to leave blank on zero then leave a blank cell
		//
		if (zeroBlank && num == 0)
		{
			outputBuffer.append("<td>&nbsp;</td>");
			return;
		}

		//
		// Emphasize if asked to
		//
		if (minToEmphasize != 0 && num >= minToEmphasize)
		{
			outputBuffer.append("<td><b>");
			outputBuffer.append(String.format("%,d", num));
			outputBuffer.append("</b></td>");
			return;
		}

		//
		// Normal display
		//
		outputBuffer.append("<td>");
		outputBuffer.append(String.format("%,d", num));
		outputBuffer.append("</td>");
	}

	/**
	 * Display time in "hh:mm" or "hh:mm:ss" format.
	 * 
	 * @param millis
	 *            Milli-seconds in {@link System.currentTimeMillis()} format.
	 * @param displaySeconds
	 *            False for "hh:mm" or true for "hh:mm:ss" format.
	 */
	public void appendTableCellTime(long millis, boolean displaySeconds)
	{
		appendTableCellTime(millis, displaySeconds, false);
	}

	/**
	 * Display time in "hh:mm" or "hh:mm:ss" format, with optional bold display.
	 * 
	 * @param millis
	 *            Milli-seconds in {@link System.currentTimeMillis()} format.
	 * @param displaySeconds
	 *            False for "hh:mm" or true for "hh:mm:ss" format.
	 * @param bold
	 *            When true and time is not zero, it will display the output
	 *            time in bold.
	 */
	public void appendTableCellTime(long millis, boolean displaySeconds,
			boolean bold)
	{
		boolean isTextMode = isTextMode();
		if (!isTextMode)
		{
			outputBuffer.append("<td>");
		}

		if (millis == 0)
		{
			outputBuffer.append("-");
		} else
		{
			if (bold && !isTextMode)
			{
				outputBuffer.append("<b>");
			}

			// The time
			if (displaySeconds)
			{
				outputBuffer.append(StringUtils.createDateFormat(millis));
			} else
			{
				outputBuffer.append(dateFormatTimeMin.format(new Date(millis)));
			}

			if (bold && !isTextMode)
			{
				outputBuffer.append("</b>");
			}

		}

		if (!isTextMode)
		{
			outputBuffer.append("</td>");
		}
	}

	public void appendTableCellDate(long millis)
	{
		boolean isTextMode = isTextMode();
		if (!isTextMode)
		{
			outputBuffer.append("<td>");
		}
		if (millis == 0)
		{
			outputBuffer.append("-");
		} else
		{
			outputBuffer.append(dateFormatDate.format(new Date(millis)));
		}

		if (!isTextMode)
		{
			outputBuffer.append("</td>");
		}
	}

	public void appendTableCellDateTime(long millis)
	{
		boolean isTextMode = isTextMode();
		if (!isTextMode)
		{
			outputBuffer.append("<td>");
		}

		if (millis == 0)
		{
			outputBuffer.append("-");
		} else
		{
			outputBuffer.append(StringUtils.createDateFormat(millis));
		}
		if (!isTextMode)
		{
			outputBuffer.append("</td>");
		}
	}

	public void appendTableEnd()
	{
		if (isTextMode())
		{
			outputBuffer.append("\n~Table End~\n");
			outputBuffer.append("</pre>");
			return;
		}
		outputBuffer.append("</table>");
	}

	public void appendField(String title, Object value, String comment)
	{
		outputBuffer.append("<p><b>");
		outputBuffer.append(title);
		outputBuffer.append(":</b> ");
		outputBuffer.append(value.toString());
		outputBuffer.append(" <small>(");
		outputBuffer.append(comment);
		outputBuffer.append(")</small>");
		outputBuffer.append("</p>");
	}

	public void appendField(String title, Object value)
	{
		outputBuffer.append("<p><b>");
		outputBuffer.append(title);
		outputBuffer.append(":</b> ");
		if (value == null)
		{
			outputBuffer.append("(null)");
		} else if (value instanceof Integer)
		{
			outputBuffer.append(String.format("%,d", (Integer) value));
		} else if (value instanceof Long)
		{
			outputBuffer.append(String.format("%,d", (Long) value));
		} else
		{
			outputBuffer.append(value.toString());
		}
		outputBuffer.append("</p>");
	}

	public void appendFieldDate(String counterTitle, long millis, String comment)
	{
		if (millis == 0)
		{
			appendField(counterTitle, "-", comment);
		} else
		{
			appendField(counterTitle, dateFormatDate.format(new Date(millis)),
					comment);
		}
	}

	public void appendFieldDate(String counterTitle, long millis)
	{
		if (millis == 0)
		{
			appendField(counterTitle, "-");
		} else
		{
			appendField(counterTitle, dateFormatDate.format(new Date(millis)));
		}
	}

	public void appendFieldDateTime(String counterTitle, long millis,
			String comment)
	{
		if (millis == 0)
		{
			appendField(counterTitle, "-", comment);
		} else
		{
			appendField(counterTitle, StringUtils.createDateFormat(millis));
		}
	}

	public void appendFieldDateTime(String counterTitle, long millis)
	{
		if (millis == 0)
		{
			appendField(counterTitle, "-");
		} else
		{
			appendField(counterTitle, StringUtils.createDateFormat(millis));
		}
	}

	/**
	 * Display field of time in "hh:mm:ss" format, with a comment.
	 * 
	 * @param counterTitle
	 *            Field prefix as "Title: ".
	 * @param millis
	 *            Milli-seconds since 1970.
	 * @param comment
	 *            Comment to display after the time. If not needed, use
	 *            {@link #appendFieldTime(String, long)} instead.
	 */
	public void appendFieldTime(String counterTitle, long millis, String comment)
	{
		if (millis == 0)
		{
			appendField(counterTitle, "-", comment);
		} else
		{
			appendField(counterTitle, StringUtils.createDateFormat(millis),
					comment);
		}
	}

	/**
	 * Display field of time in "hh:mm:ss" format.
	 * 
	 * @param counterTitle
	 *            Field prefix as "Title: ".
	 * @param millis
	 *            Milli-seconds since 1970.
	 */
	public void appendFieldTime(String counterTitle, long millis)
	{
		if (millis == 0)
		{
			appendField(counterTitle, "-");
		} else
		{
			appendField(counterTitle, StringUtils.createDateFormat(millis));
		}
	}

	public void appendMenuItem(String linkUrl, String linkDescription,
			String freeText)
	{
		outputBuffer.append("<p><a href=\"");
		outputBuffer.append(linkUrl);
		outputBuffer.append("\"><b>");
		outputBuffer.append(linkDescription);
		outputBuffer.append("</b></a>");
		if (freeText != null && freeText.length() > 0)
		{
			outputBuffer.append(" - ");
			outputBuffer.append(freeText);
		}
		outputBuffer.append("</p>\n");
	}

	public void appendMenuItem(String linkUrl, String linkDescription,
			int counterToDisplay)
	{
		outputBuffer.append("<p><a href=\"");
		outputBuffer.append(linkUrl);
		outputBuffer.append("\"><b>");
		outputBuffer.append(linkDescription);
		outputBuffer.append("</b></a>");
		outputBuffer.append(": <b>");
		outputBuffer.append(String.format("%,d", counterToDisplay));
		outputBuffer.append("</b></p>\n");
	}

	public void appendMenuItem(String linkUrl, String linkDescription)
	{
		outputBuffer.append("<p><a href=\"");
		outputBuffer.append(linkUrl);
		outputBuffer.append("\"><b>");
		outputBuffer.append(linkDescription);
		outputBuffer.append("</b></a>");
		outputBuffer.append("</p>\n");
	}

	/**
	 * Menu item with one description and two links.
	 */
	public void appendMenuItem(String description, String linkUrl1,
			String linkDescription1, String linkUrl2, String linkDescription2)
	{
		outputBuffer.append("<p><b>");
		outputBuffer.append(description);
		outputBuffer.append("&nbsp;&nbsp;");
		if (linkUrl1 != null)
		{
			outputBuffer.append("<a href=\"");
			outputBuffer.append(linkUrl1);
			outputBuffer.append("\">");
		}
		outputBuffer.append(linkDescription1);
		if (linkUrl1 != null)
		{
			outputBuffer.append("</a>");
		}
		outputBuffer.append("&nbsp;/&nbsp;");
		if (linkUrl2 != null)
		{
			outputBuffer.append("<a href=\"");
			outputBuffer.append(linkUrl2);
			outputBuffer.append("\">");
		}
		outputBuffer.append(linkDescription2);
		if (linkUrl2 != null)
		{
			outputBuffer.append("</a>");
		}
		outputBuffer.append("</b>");
		outputBuffer.append("</p>\n");
	}

	/**
	 * Menu item with one description and three links.
	 */
	public void appendMenuItem(String description, String linkUrl1,
			String linkDescription1, String linkUrl2, String linkDescription2,
			String linkUrl3, String linkDescription3)
	{
		outputBuffer.append("<p><b>");
		outputBuffer.append(description);
		outputBuffer.append("&nbsp;&nbsp;<a href=\"");
		outputBuffer.append(linkUrl1);
		outputBuffer.append("\">");
		outputBuffer.append(linkDescription1);
		outputBuffer.append("</a>");
		outputBuffer.append("&nbsp;/&nbsp;<a href=\"");
		outputBuffer.append(linkUrl2);
		outputBuffer.append("\">");
		outputBuffer.append(linkDescription2);
		outputBuffer.append("</a>");
		outputBuffer.append("&nbsp;/&nbsp;<a href=\"");
		outputBuffer.append(linkUrl3);
		outputBuffer.append("\">");
		outputBuffer.append(linkDescription3);
		outputBuffer.append("</a></b>");
		outputBuffer.append("</p>\n");
	}

	/**
	 * First method to call when creating a nav bar.
	 * 
	 * @param description
	 * @param link
	 * @see #appendNavbarItem(String, String)
	 * @see #appendNavbarEnd()
	 */
	public void appendNavbarStart(String description, String link)
	{
		outputBuffer.append("<table border=0><tr><td>");
		if (link != null)
		{
			outputBuffer.append("<a href=\"");
			outputBuffer.append(link);
			outputBuffer.append("\">");
		}
		outputBuffer.append("<b>");
		outputBuffer.append(description);
		outputBuffer.append("</b></a></td>");
	}

	/**
	 * First method to call when creating a nav bar, with title "Main" and link
	 * to home-page.
	 * 
	 * @see #appendNavbarStart(String, String)
	 */
	public void appendNavbarStart()
	{
		this.appendNavbarStart("Main", "/");
	}

	/**
	 * To call after {@link #appendNavbarStart(String, String)}
	 * 
	 * @param description
	 * @param link
	 *            If null, then no link is displayed.
	 * @see #appendNavbarEnd()
	 */
	public void appendNavbarItem(String description, String link)
	{
		if (link == null)
		{
			appendNavbarItem(description);
			return;
		}

		outputBuffer.append("<td>&gt;</td><td><a href=\"");
		outputBuffer.append(link);
		outputBuffer.append("\"><b>");
		outputBuffer.append(description.replace(" ", "&nbsp;"));
		outputBuffer.append("</b></a></td>");
	}

	/**
	 * To call after {@link #appendNavbarStart(String, String)}
	 * <p>
	 * To be used when there is no link for this item, like when it's the last
	 * item.
	 * 
	 * @param description
	 * @see #appendNavbarEnd()
	 */
	public void appendNavbarItem(String description)
	{
		outputBuffer.append("<td>&gt;</td><td><b>");
		outputBuffer.append(description.replace(" ", "&nbsp;"));
		outputBuffer.append("</b></td>");
	}

	/**
	 * To call after the last {@link #appendNavbarItem(String, String)}.
	 * 
	 * @see #appendNavbarItem(String, String)
	 */
	public void appendNavbarHelp(String helpString)
	{
		outputBuffer
				.append("<script type=text/javascript language=javascript>\n");
		outputBuffer.append("    function toggletable() {\n");
		outputBuffer
				.append("        var temp = document.getElementById('helptable');\n");
		outputBuffer
				.append("        if (temp.style.visibility == 'visible') {\n");
		outputBuffer
				.append("            temp.style.visibility = 'collapse';\n");
		outputBuffer.append("        } else {\n");
		outputBuffer.append("            temp.style.visibility = 'visible';\n");
		outputBuffer.append("        }\n");
		outputBuffer.append("    }\n");
		outputBuffer.append("</script>\n");
		outputBuffer
				.append("<td width=100%></td><td align=right bgcolor=#ffffcc><b><a href=\"#\" onclick=\"toggletable()\">&nbsp;Help&nbsp;</a></b></td></table>\n");
		outputBuffer
				.append("<table id=\"helptable\" width=100% border=0 cellpadding=5 style=\"visibility: collapse;\"><tr><td bgcolor=#ffffcc>");
		outputBuffer.append(helpString);
		outputBuffer.append("</td>");
	}

	/**
	 * To call after the last {@link #appendNavbarItem(String, String)} or
	 * {@link #appendNavbarHelp()}.
	 * 
	 * @see #appendNavbarStart(String, String)
	 */
	public void appendNavbarEnd()
	{
		outputBuffer.append("</tr></table>");
	}

	public void appendLink(String text, String link, boolean linkNewWindow)
	{
		appendString(makeHtmlLink(text, link, linkNewWindow));
	}

	private static String makeHtmlLink(String text, String link,
			boolean linkNewWindow)
	{
		return "<a " + (linkNewWindow ? "target=new" : "") + " href=\"" + link
				+ "\">" + text + "</a>";
	}

	private static String makeHtmlLink(String text, String link)
	{
		return "<a href=\"" + link + "\">" + text + "</a>";
	}

	private static String makeHtmlLink(String text, String link,
			String paramName, String paramValue)
	{
		return makeHtmlLink(text, link + "?" + paramName + "=" + paramValue);
	}

	/**
	 * Michal: This method receives a text and link and appends it to webGUI
	 * 
	 * @param text
	 *            - the text you want displayed
	 * @param link
	 *            - where the text would link to
	 */
	public void appendLink(String text, String link)
	{
		appendLink(text, link, false);
	}

	/**
	 * Michal: This method receives a text and link + param and appends it to
	 * webGUI
	 * 
	 * @param text
	 *            - the text you want displayed
	 * @param link
	 *            - where the text would link to
	 * @param paramName
	 *            - name of parameter (will later be used in getParam)
	 * @param paramValue
	 *            - value of param
	 */
	public void appendLink(String text, String link, String paramName,
			String paramValue)
	{
		appendString(makeHtmlLink(text, link, paramName, paramValue));
	}

	/**
	 * Append HTTP header with optional content type, optional content length
	 * and list of extra headers
	 * 
	 * @param contentType
	 *            Content type to write in the header. If null or empty then
	 *            this header is not written at all.
	 * @param contentLength
	 *            Content length to write in the header. If zero or negative
	 *            then this header is not written at all.
	 * @param extraHeaders
	 *            Any additional information we want to send. (send as pairs of
	 *            Key/Value)
	 */
	public void appendHttpHeader(String contentType, int contentLength,
			Map<String, String> extraHeaders)
	{
		appendString(HTTP_1_1_200_OK_HEADER);

		//
		// Content-type
		//
		if (contentType != null && !contentType.equals(""))
		{
			appendString("Content-Type: " + contentType + "\r\n");
		}

		//
		// Content length
		//
		if (contentLength > 0)
		{
			appendString("Content-Length: " + contentLength);
			appendString("\r\n");
		}

		if (extraHeaders != null)
		{
			for (Iterator<Entry<String, String>> it = extraHeaders.entrySet()
					.iterator(); it.hasNext();)
			{
				Entry<String, String> entry = it.next();
				appendString(entry.getKey() + ": " + entry.getValue());
			}
		}

		appendString("\r\n");
	}

	/**
	 * Append HTTP header with optional content type and optional content
	 * length.
	 * 
	 * @param contentType
	 *            Content type to write in the header. If null or empty then
	 *            this header is not written at all.
	 * @param contentLength
	 *            Content length to write in the header. If zero or negative
	 *            then this header is not written at all.
	 */
	public void appendHttpHeader(String contentType, int contentLength)
	{
		appendHttpHeader(contentType, contentLength, null);
	}

	public void appendIndexItem(String colorId, String colorName, String desc)
	{
		appendString("<span style='background-color:" + colorId + "';>"
				+ colorName + "\t\t" + "</span> - " + desc + "<br>");
	}

	/**
	 * Determines if the generated output should be in simple text rather then
	 * formatted html.
	 * 
	 * @return true if text mode otherwise false.
	 */
	public boolean isTextMode()
	{
		return getParamAsBool(TEXT_MODE);
	}

	/**
	 * Gets the separator used in text mode to separate columns
	 * 
	 * @return the separator passed in the parameters or the default ',' in case
	 *         the paramater is missing.
	 */
	public String getTextSeparator()
	{
		return getParamAsString(TEXT_SEPARATOR, ",");
	}

	public String getUrl()
	{
		return this.uri.toString();
	}
}
