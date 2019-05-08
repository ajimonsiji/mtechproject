package il.ac.technion.eyalzo.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

/**
 * StringUtils File - contains general static utility functions for string handling <BR>
 * <BR>
 * identText - returns a string with appeneded white spaces at asked length<BR>
 * getWordAt - returns the i-th word from a string
 */
public class StringUtils
{
	public static SimpleDateFormat dateFormatDateTime = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	/**
	 * This method receives a variable size string, and returns a new string with desired length <BR>
	 * if s.length < len the function returns the string with appeneded white spaces if s.length > len function will
	 * trunc the string to desired length
	 * 
	 * @param s
	 *            - string to ident
	 * @param len
	 *            - desired length
	 * @return the string at desired length
	 */
	public static String identText(String s, int len)
	{
		StringBuffer tmp = new StringBuffer(s);
		for (int i = s.length(); i < len; ++i)
		{
			tmp.append(" ");
		}
		return tmp.toString().substring(0, len);
	}

	/**
	 * This method receives a variable size string, and returns a new string with desired length <BR>
	 * if s.length < len the function returns the string with appeneded white spaces if s.length > len function will
	 * trunc the string to desired length, if asked to (i.e. if trunc = true), otherwise - return the original string
	 * 
	 * @param s
	 *            - string to ident
	 * @param len
	 *            - desired length
	 * @param trunc
	 *            - do we truncate the string if longer than desired length or not
	 * @return the string at desired length
	 */
	public static String identText(String s, int len, boolean trunc)
	{
		if (!trunc && s.length() >= len)
		{
			return s;
		}
		return identText(s, len);
	}

	/**
	 * getLastWord: Returns the right part of a *char* seperated String (anything after last *char*)
	 * 
	 * @param s
	 *            - space delimited string containing a path
	 * @param c
	 *            - delimiter
	 * @return Right part of the string
	 */
	public static String getLastWord(String s, char c)
	{
		if (s == null || s.equals(""))
			return "";
		int index = s.lastIndexOf(c);
		if (index == -1)
		{
			return s;
		}
		return s.substring(index + 1);
	}

	/**
	 * getLastWord: Returns the right part of a space seperated String (anything after last space)
	 * 
	 * @param s
	 *            - space delimited string containing a path
	 * @return Right part of the string
	 */
	public static String getLastWord(String s)
	{
		return getLastWord(s, ' ');
	}

	/**
	 * deleteExtraBlanks: Converts all places with >= 2 blanks to 1 blank space
	 * 
	 * @param s
	 *            - string to work on
	 * @return Parameter string without any sequence of 2 spaces or more
	 */
	public static String deleteExtraBlanks(String s)
	{
		if (s == null)
			return null;
		//
		// While we still have double blanks, replace them with one blank
		// (Can be done more efficiently by scanning the String, but will take more code lines,
		// and will not occur much anyway, so speed doesn't matter)
		//
		while (s.indexOf("  ") != -1 && !s.trim().equals(""))
		{
			s = s.replaceAll("  ", " ");
		}

		return s;
	}

	/**
	 * getWordAt: Returns the a certain word at a *char* seperated String according to a param index
	 * 
	 * @param s
	 *            - space delimited string containing a path
	 * @param c
	 *            - delimiter
	 * @param index
	 *            - index of the word we wish to receive, 1 being the most left word
	 * @param fromStart
	 *            - do we want the index-th word from the beginning or the end
	 * @return The word at the given index (null if index invalid)
	 */
	public static String getWordAt(String s, char c, int index, boolean fromStart)
	{
		if (s == null || s.equals(""))
			return null;

		String[] tmp = s.split(String.valueOf(c));
		if (index < 1 || index > tmp.length)
			return null;

		if (!fromStart)
			index = tmp.length - index + 1;

		return tmp[index - 1];
	}

	public static String createLink(String command, String text, String param)
	{
		return "<a href='/" + command + "?key=" + param + "'>" + text + "</a>";
	}

	public static String createLink(String command, String text, String paramName, String paramVal)
	{
		return "<a href='/" + command + "?" + paramName + "=" + paramVal + "'>" + text + "</a>";
	}

	/**
	 * getLeft: Returns the left part of a space separated String (anything before last space)
	 * 
	 * @param s
	 *            - space delimited string containing a path
	 * @return Right part of the string
	 */
	public static String getLeft(String s)
	{
		return getLeft(s, ' ');
	}

	/**
	 * getLeft: Returns the left part of a *char* separated String (anything before last space)
	 * 
	 * @param s
	 *            - space delimited string containing a path
	 * @param c
	 *            - delimiter
	 * @return Right part of the string
	 */
	public static String getLeft(String s, char c)
	{
		if (s == null || s.equals(""))
			return "";
		int index = s.lastIndexOf(c);
		if (index == -1)
		{
			return "";
		}
		return s.substring(0, index);
	}

	/**
	 * getLeftFileName: Returns the left part of a char seperated String (anything before last space) or the string
	 * itself incase the delimiter doesn't exist.
	 * 
	 * @param s
	 *            - space delimited string containing a path
	 * @param c
	 *            - delimiter
	 * @return Left part of the string or the String itself ( if the delimiter doesn't exist)
	 */
	public static String getLeftFileName(String s, char c)
	{
		if (s == null || s.equals(""))
			return "";
		int index = s.lastIndexOf(c);
		if (index == -1)
		{
			return s;
		}
		return s.substring(0, index);
	}

	public static String createDateFormat(long time)
	{
		Date date = new Date(time);
		return dateFormatDateTime.format(date);
	}

	/**
	 * Returns a date format by the given format param.
	 * <p>
	 * If an error exists, the method will return null. <br>
	 * <u>Pay attention</u> - this method creats a SimpleDateFormat instance and is therefor not recommended if you use
	 * this repeatedly
	 * 
	 * @param time
	 *            - The time as long
	 * @param format
	 *            - The date format
	 * @return - The formated date or null if an error occurred
	 */
	public static String createDateFormat(long time, String format)
	{
		try
		{
			SimpleDateFormat tempDateFormatDateTime = new SimpleDateFormat(format);
			Date date = new Date(time);
			return tempDateFormatDateTime.format(date);
		} catch (RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * @param strings
	 *            a colection of Strings
	 * @return the length of the longest String in the collection
	 */
	public static int getMaxLength(Collection<String> strings)
	{
		int len = 0;
		for (String string : strings)
		{
			if (string.length() > len)
			{
				len = string.length();
			}
		}
		return len;
	}

	/**
	 * Purpose: This method takes the 'fullPath' as supplied, goes over the text and removes any indication to param
	 * arg.
	 * 
	 * @param fullPath
	 * @return new fullPath without the param in it
	 */
	public static String removeOldParam(String param, String fullPath)
	{
		String newFullPath = "";

		int i;
		if ((i = fullPath.indexOf("?" + param + "=")) > 0 || (i = fullPath.indexOf("&" + param + "=")) > 0)
		{
			int j;
			newFullPath = fullPath.substring(0, i)
					+ ((j = fullPath.indexOf("&", i + 1)) > 0 ? fullPath.substring(j) : "");
		} else
		{
			newFullPath = fullPath;
		}

		//
		// fix ? or & positions
		//
		if (newFullPath.indexOf("&") > 0)
		{
			newFullPath = newFullPath.replaceAll("\\?", "&");
			newFullPath = newFullPath.replaceFirst("\\&", "?");
		}

		return newFullPath;
	}

	/**
	 * Purpose: This method takes the 'fullPath' as supplied, goes over the text and removes any indication to
	 * 'oldactions'
	 * 
	 * @param fullPath
	 * @return new fullPath without oldactions in it
	 */
	protected static String removeOldAction(String fullPath)
	{
		return removeOldParam("oldaction", fullPath);
	}

	/**
	 * Purpose: Adds a parameter to a URL depending on if it is the first parameter or additional
	 * 
	 * Note: Passing null as value will append the word "null", in order to pass an empty value, one should send an
	 * empty string ("").
	 * 
	 * @param url
	 * @param param
	 * @param value
	 * @return
	 */
	public static String addURLParam(String url, String param, Object value)
	{
		// first remove any old action
		url = removeOldAction(url);

		// now, remove any parameters as requested (might be the action that was removed before)
		url = removeOldParam(param, url);

		// check if this is not the first parameter of this url
		if (url.contains("?"))
		{
			return url + "&" + param + "=" + String.valueOf(value);
		}

		return url + "?" + param + "=" + String.valueOf(value);
	}

	public static String addURLParams(String url, String[] params, Object... values)
	{
		String result = url;

		int i = 0;
		for (String param : params)
		{
			String value = "";
			if (values.length > i)
			{
				value = String.valueOf(values[i]);
			}

			result = addURLParam(result, param, value);
			i++;
		}

		return result;
	}

	/**
	 * @param str
	 *            - a string ref
	 * @return - true, if str is not null and not empty
	 */
	public static boolean isStrNotEmpty(String str)
	{
		return str != null && str.length() != 0;
	}

	/**
	 * 
	 * Purpose: Check if the text from the offset is same as the compared text
	 * 
	 * @param charArray
	 * @param text
	 *            the compared text
	 * @param offset
	 *            the position in the char buffer
	 * @return boolean true if found
	 */
	public static boolean isStringInArray(char[] charArray, String text, int offset)
	{
		for (int i = 0; i < text.length(); i++)
		{
			if (charArray[offset + i] != text.charAt(i))
				return false;
		}

		return true;
	}

	/**
	 * 
	 * Purpose: Find the position of the text in the char buffer
	 * 
	 * 
	 * @param text
	 * @param buffer
	 * @return int
	 */
	public static int getPosOf(String text, CharBuffer buffer)
	{
		char[] messageBytes = buffer.array();

		for (int i = 0; i < (buffer.position() - text.length()); i++)
		{
			if (isStringInArray(messageBytes, text, i))
			{
				return (i + text.length());
			}
		}

		return -1;
	}

	public static String reverseStr(String source)
	{
		int len = source.length();
		StringBuffer dest = new StringBuffer(len);

		for (int i = (len - 1); i >= 0; i--)
		{
			dest.append(source.charAt(i));
		}

		return dest.toString();
	}

	/**
	 * 
	 * 
	 * @param object
	 * @return String Get the name from the object, i.e. the last string after the last dot
	 */
	public static String extractNameFromClass(Object object)
	{
		if (object != null)
		{
			String name = object.getClass().toString();
			name = name.substring(name.lastIndexOf(".") + 1);

			return name;
		}
		return "";
	}

	/**
	 * 
	 * Purpose: Replace the thread name with the class name of the thread (the string after the last dot)
	 * 
	 * @param thread
	 */
	public static void setThreadName(Thread thread)
	{
		if (thread != null)
		{
			thread.setName(extractNameFromClass(thread));
		}
	}

	/**
	 * 
	 * Purpose: Extract the stream output and return it as a new string
	 * 
	 * @param stream
	 * @return String the streamed output as string
	 * @throws IOException
	 */
	public static String streamToString(InputStream stream, int maxSize) throws IOException
	{
		String result = null;
		if (stream != null)
		{
			BufferedReader bufferedReader = null;
			try
			{
				bufferedReader = new BufferedReader(new InputStreamReader(stream));
				StringBuilder stringBuilder = new StringBuilder();
				String line = null;

				while ((line = bufferedReader.readLine()) != null
						&& (maxSize == -1 || stringBuilder.length() <= maxSize))
				{
					stringBuilder.append(line + "\n");
				}

				if (maxSize == -1)
				{
					result = stringBuilder.toString();
				} else
				{
					result = stringBuilder.substring(1, maxSize);
				}
			} finally
			{
				bufferedReader.close();
			}
		}

		return result;
	}

	/**
	 * 
	 * Purpose: Extract the stream output and return it as a new string
	 * 
	 * @param stream
	 * @return String the streamed output as string
	 * @throws IOException
	 */
	public static String streamToString(InputStream stream) throws IOException
	{
		return streamToString(stream, -1);
	}
}
