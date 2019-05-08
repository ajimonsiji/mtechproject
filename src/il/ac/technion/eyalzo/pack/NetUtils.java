package il.ac.technion.eyalzo.pack;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetUtils
{
	public static String getAllHostAddressesForFilter()
	{
		String result = "(";
		Collection<String> addresses = getAllHostAddresses();
		for (String curAddr : addresses)
		{
			if (result.length() > 1)
			{
				result += " or ";
			}
			result += curAddr;
		}
		result += ")";

		return result;
	}

	/**
	 * Gets all host addresses for the local machine.
	 * 
	 * @param log
	 *            the logger to write messages to, or null if not needed
	 * @return list of addresses as Strings, or null if an error occured
	 */
	public static ArrayList<String> getAllHostAddress(Logger log)
	{
		ArrayList<String> networkAddressList = new ArrayList<String>();
		try
		{
			Enumeration<NetworkInterface> interfaceEnum = NetworkInterface.getNetworkInterfaces();
			while (interfaceEnum.hasMoreElements())
			{
				NetworkInterface netface = interfaceEnum.nextElement();

				Enumeration<InetAddress> addressEnum = netface.getInetAddresses();
				while (addressEnum.hasMoreElements())
				{
					InetAddress address = addressEnum.nextElement();
					networkAddressList.add(address.getHostAddress());
				}
			}
			return networkAddressList;
		} catch (Exception e)
		{
			if (log != null)
			{
				log.log(Level.SEVERE, "An excpetion occured during enumaration:", e);
			}
		}
		return null;
	}

	/**
	 * Same as calling getAllHostAddress(null).
	 * 
	 * @return List of addresses, or null if an error occurred.
	 * @see #getAllHostAddress(Logger)
	 */
	public static ArrayList<String> getAllHostAddresses()
	{
		return getAllHostAddress(null);
	}

	/**
	 * @param addr
	 *            Given IP address.
	 * @param log
	 *            Optional.
	 * @return Name of device that has the given IP address, or null if no such device was found.
	 */
	public static String getDeviceForAddress(InetAddress addr, Logger log)
	{
		try
		{
			Enumeration<NetworkInterface> interfaceEnum = NetworkInterface.getNetworkInterfaces();
			while (interfaceEnum.hasMoreElements())
			{
				NetworkInterface netface = interfaceEnum.nextElement();
				// if (!"eth0".equals(netface.getName()))
				// continue;

				Enumeration<InetAddress> addresses = netface.getInetAddresses();

				while (addresses.hasMoreElements())
				{
					InetAddress curAddr = addresses.nextElement();
					if (curAddr == null || !curAddr.equals(addr))
						continue;

					return netface.getName();
				}
			}
		} catch (Exception e)
		{
			if (log != null)
			{
				log.log(Level.SEVERE, "An excpetion occured during enumaration:", e);
			}
		}
		return null;
	}

	/**
	 * Gets the first IP of the local machine (the first one for eth0)
	 * 
	 * @param log
	 *            the logger to write messages to, or null if not needed
	 * @return the ip address as InetAddress, or null if an error occurred
	 */
	public static InetAddress getNetworkAddress(Logger log)
	{
		try
		{
			Enumeration<NetworkInterface> interfaceEnum = NetworkInterface.getNetworkInterfaces();
			while (interfaceEnum.hasMoreElements())
			{
				NetworkInterface netface = interfaceEnum.nextElement();
				// if (!"eth0".equals(netface.getName()))
				// continue;

				Enumeration<InetAddress> addresses = netface.getInetAddresses();

				while (addresses.hasMoreElements())
				{
					InetAddress curAddr = addresses.nextElement();
					if (curAddr == null)
						continue;

					// Skip IPv6
					if (curAddr.getAddress().length != 4)
						continue;

					// Skip 127. addresses
					int firstByte = 0x00ff & (int) curAddr.getAddress()[0];
					if (firstByte == 127)
						continue;

					return curAddr;
				}
			}
		} catch (Exception e)
		{
			if (log != null)
			{
				log.log(Level.SEVERE, "An excpetion occured during enumaration:", e);
			}
		}
		return null;
	}

	/**
	 * Gets the first IP of the given device name.
	 * 
	 * @return The IP address as InetAddress, or null if an error occurred.
	 */
	public static InetAddress getNetworkAddress(String deviceName)
	{
		if (deviceName == null || deviceName.equals(""))
			return null;

		try
		{
			Enumeration<NetworkInterface> interfaceEnum = NetworkInterface.getNetworkInterfaces();
			while (interfaceEnum.hasMoreElements())
			{
				NetworkInterface netface = interfaceEnum.nextElement();
				if (!deviceName.equalsIgnoreCase(netface.getName()))
					continue;

				Enumeration<InetAddress> addresses = netface.getInetAddresses();

				while (addresses.hasMoreElements())
				{
					InetAddress curAddr = addresses.nextElement();
					if (curAddr == null)
						continue;

					// Skip IPv6
					if (curAddr.getAddress().length != 4)
						continue;

					return curAddr;
				}
			}
		} catch (Exception e)
		{
		}
		return null;
	}

	/**
	 * Same as calling getNetworkAddress(null)
	 * 
	 * @return the ip address as InetAddress, or null if an error occured
	 * @see #getAllHostAddress(Logger)
	 */
	public static InetAddress getNetworkAddress()
	{
		return getNetworkAddress((Logger) null);
	}

	/**
	 * Receives byte array and represtents it as int
	 * 
	 * @param ip
	 * @param offset
	 * @param bigEndian
	 * @return
	 */
	public static int ipAsInt(byte[] ip, int offset, boolean bigEndian)
	{
		int returnValue = 0;
		int position = 0;
		for (int i = 0; i < 4; i++)
		{
			position = (bigEndian) ? i : (4 - i - 1);
			position += offset;
			returnValue = (returnValue << 8) | (int) (ip[position] & 0xFF);
		}
		return returnValue;
	}

	/**
	 * @return -1 on error or a non-negative number when IP is legal.
	 */
	public static long ipAsLong(byte[] ip)
	{
		if (ip.length != 4)
			return -1L;

		return (ip[3] & 0x000000ff) | ((ip[2] & 0x000000ff) << 8) | ((ip[1] & 0x000000ff) << 16)
				| ((ip[0] & 0x000000ff) << 24);
	}

	/**
	 * @return -1 on error or a non-negative number when IP is legal.
	 */
	public static long ipAsLong(InetAddress address)
	{
		if (address == null)
			return -1L;

		return address.hashCode() & 0xffffffffL;
	}

	/**
	 * @return -1 on error or a non-negative number when IP is legal.
	 */
	public static long ipAsLong(InetSocketAddress socketAddress)
	{
		if (socketAddress == null)
			return -1L;

		InetAddress address = socketAddress.getAddress();
		if (address == null)
			return -1L;

		return address.hashCode() & 0xffffffffL;
	}

	/**
	 * Convert IP or host name to long.
	 * 
	 * @param ipStr
	 *            Dotted-decimal string representing an IPv4 address.
	 * @return -1 on error or a non-negative number when IP is legal.
	 */
	public static long ipAsLongResolve(String ipStr)
	{
		try
		{
			InetAddress address = InetAddress.getByName(ipStr);
			return address.hashCode() & 0xffffffffL;
		} catch (UnknownHostException e)
		{
			return -1L;
		}
	}

	/**
	 * @param ip
	 *            IPv4 dotted-decimal address.
	 * @return -1 on error or big-endian long.
	 */
	public static long ipAsLong(String ip)
	{
		return ipAsLong(ip, true, false);
	}

	/**
	 * @param ip
	 *            IPv4 dotted-decimal address.
	 * @param bigEndian
	 *            Big-endian (TCP/IP network order) or little-endian.
	 * @param completeParts
	 *            If true and the address has less than 4 parts, these are completed with ".0". This is needed when
	 *            dealing with masks.
	 * @return -1 on error or big-endian long.
	 */
	public static long ipAsLong(String ip, boolean bigEndian, boolean completeParts)
	{
		long returnValue = 0;
		String[] ipStrs = ip.split("\\.");

		if (completeParts)
		{
			if (ipStrs.length != 4)
			{
				if (ipStrs.length < 1 || ipStrs.length > 4)
					return -1;
				for (int i = ipStrs.length; i < 4; i++)
				{
					ip += ".0";
				}
				ipStrs = ip.split("\\.");
			}
		} else if (ipStrs.length != 4)
		{
			return -1;
		}

		int position = 0;
		for (int i = 0; i < 4; i++)
		{
			position = (bigEndian) ? i : (4 - i - 1);
			int curVal;
			try
			{
				curVal = Integer.parseInt(ipStrs[position]);
			} catch (NumberFormatException e)
			{
				return -1;
			}
			if (curVal < 0 || curVal > 255)
				return -1;
			returnValue = (returnValue << 8) | curVal;
		}
		return returnValue;
	}

	/**
	 * @deprecated The usage as int for storing IPs has two drawbacks: negative values cannot be easily sorted, and
	 *             there is no free value for ilegal IPs found during parsing. Therefor you better use
	 *             {@link #ipAsLong(String)} when possible.
	 */
	@Deprecated
	public static int ipAsInt(String ip, boolean bigEndian)
	{
		int returnValue = 0;
		String[] ipStrs = ip.split("\\.");
		if (ipStrs.length != 4)
		{
			return -1;
		}

		int position = 0;
		for (int i = 0; i < 4; i++)
		{
			position = (bigEndian) ? i : (4 - i - 1);
			returnValue = (returnValue << 8) | (int) (Integer.parseInt(ipStrs[position]) & 0xFF);
		}
		return returnValue;
	}

	/**
	 * @param ip
	 *            Given IP address in full dotted-decimal notation.
	 * @return Null if string is illegal, or byte array (4 bytes) with the IP address in big-endian (network order).
	 */
	public static byte[] ipAsByteArray(String ip)
	{
		String[] ipStrs = ip.split("\\.");

		// Make sure that we have 4 parts
		if (ipStrs.length != 4)
		{
			return null;
		}

		byte[] array = new byte[4];

		try
		{
			array[0] = (byte) (Integer.parseInt(ipStrs[0]) & 0xFF);
			array[1] = (byte) (Integer.parseInt(ipStrs[1]) & 0xFF);
			array[2] = (byte) (Integer.parseInt(ipStrs[2]) & 0xFF);
			array[3] = (byte) (Integer.parseInt(ipStrs[3]) & 0xFF);
		} catch (NumberFormatException e)
		{
			return null;
		}

		return array;
	}

	/**
	 * @param ip
	 *            Given IP address in full dotted-decimal notation.
	 * @return Null if string is illegal, or new {@link InetAddress} with IP address and not host name.
	 * @see #ipAsByteArray(String)
	 */
	public static InetAddress ipAsInetAddress(String ip)
	{
		byte[] array = ipAsByteArray(ip);

		if (array == null)
			return null;

		InetAddress address = null;
		try
		{
			address = InetAddress.getByAddress(null, array);
		} catch (UnknownHostException e)
		{
			// This cannot happen, because the array will always be 4 bytes long
			return null;
		}

		return address;
	}

	/**
	 * @param ip
	 *            Given IP address as long.
	 * @return New {@link InetAddress} with IP address and not host name.
	 */
	public static InetAddress ipAsInetAddress(long ip)
	{
		byte[] array = new byte[4];

		array[0] = (byte) ((ip & 0xff000000) >> 24);
		array[1] = (byte) ((ip & 0x00ff0000) >> 16);
		array[2] = (byte) ((ip & 0x0000ff00) >> 8);
		array[3] = (byte) ((ip & 0x000000ff));

		InetAddress address = null;
		try
		{
			address = InetAddress.getByAddress(null, array);
		} catch (UnknownHostException e)
		{
			// This cannot happen, because the array will always be 4 bytes long
			return null;
		}

		return address;
	}

	/**
	 * @param ip
	 *            Given IP address as long.
	 * @param bigEndian
	 *            When false, this is a network byte order which is the natural representation.
	 * @return New {@link InetAddress} with IP address and not host name.
	 */
	public static InetAddress ipAsInetAddress(long ip, boolean bigEndian)
	{
		if (!bigEndian)
			return ipAsInetAddress(ip);

		byte[] array = new byte[4];

		array[3] = (byte) ((ip & 0xff000000) >> 24);
		array[2] = (byte) ((ip & 0x00ff0000) >> 16);
		array[1] = (byte) ((ip & 0x0000ff00) >> 8);
		array[0] = (byte) ((ip & 0x000000ff));

		InetAddress address = null;
		try
		{
			address = InetAddress.getByAddress(null, array);
		} catch (UnknownHostException e)
		{
			// This cannot happen, because the array will always be 4 bytes long
			return null;
		}

		return address;
	}

	/**
	 * Converts IP and port as string into InetSocketAddress, without doing any resolving.
	 * <p>
	 * Unlike the normal {@link InetSocketAddress}, this method can parse a string without resolving the address. It
	 * means that the returned address is reolved, but does not require a DNS query for that.
	 * 
	 * @param ip
	 *            Given IP address in full dotted-decimal notation.
	 * @return Null if string is illegal, or new {@link InetAddress} with IP address and not host name.
	 * @see #ipAsByteArray(String)
	 */
	public static InetSocketAddress ipAndPortAsInetSocketAddress(String ipAndPort)
	{
		// Look for the ip-port separator
		int sepIndex = ipAndPort.indexOf(':');
		if (sepIndex < 7)
			return null;

		//
		// IP address
		//
		InetAddress address = ipAsInetAddress(ipAndPort.substring(0, sepIndex));
		if (address == null)
			return null;

		//
		// Port
		//
		int port = -1;
		try
		{
			port = Integer.parseInt(ipAndPort.substring(sepIndex + 1));
		} catch (NumberFormatException e)
		{
			return null;
		}

		InetSocketAddress result;
		try
		{
			result = new InetSocketAddress(address, port);
		} catch (IllegalArgumentException e)
		{
			// Usually bad port number
			return null;
		}

		return result;
	}

	/**
	 * Translate the int ip to ip String (BIG_ENDIAN)
	 * 
	 * @param ip
	 * @param bigEndian
	 *            - if true IP is read RTL
	 * @return the ip str
	 */
	public static String ipAsStr(long ip, boolean bigEndian)
	{
		byte[] ipArr = ipAsByteArr(ip, bigEndian);
		StringBuffer stringBuffer = new StringBuffer();

		for (int i = 0; i < ipArr.length; i++)
		{
			stringBuffer.append(ipArr[i] & 0xFF);
			if (i != 3)
			{
				stringBuffer.append(".");
			}
		}
		return stringBuffer.toString();
	}

	/**
	 * Translate the int ip to byte[]
	 * 
	 * @param ip
	 * @param bigEndian
	 *            - if true IP is read RTL (largest index is the MSB)
	 * @return the ip as byte[]
	 */
	public static byte[] ipAsByteArr(long ip, boolean bigEndian)
	{
		int position = 0;
		byte[] ipArr = new byte[4];
		for (int i = 0; i < 4; i++)
		{
			position = (bigEndian) ? (4 - i - 1) : i;
			ipArr[position] = (byte) (ip & 0xFF);
			ip = ip >> 8;
		}
		return ipArr;
	}

	/**
	 * Translate the int ip to bytes and put in given array
	 * 
	 * @param targetarray
	 *            Byte array to bytes of int into
	 * @param offset
	 *            Offset into byte array
	 * @param ip
	 * @param bigEndian
	 *            - if true IP is read RTL (largest index is the MSB)
	 */
	public static void ipInByteArr(byte[] targetarray, int offset, long ip, boolean bigEndian)
	{
		int position = 0;
		if (targetarray.length < offset + 4)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		for (int i = 0; i < 4; i++)
		{
			position = (bigEndian) ? (4 - i - 1) : i;
			targetarray[position + offset] = (byte) (ip & 0xFF);
			ip = ip >> 8;
		}
	}

	/**
	 * Verify the given string as a legal IP address (one that conforms to the format a.b.c.d where 0 <= a,b,c,d <= 255)
	 * 
	 * @param ipStr
	 *            - string to check
	 * @return true if valid
	 */
	public static boolean isValidIP(String ipStr)
	{
		String[] addressParts = ipStr.split("\\.");
		if (addressParts.length != 4)
		{
			return false;
		}

		for (String part : addressParts)
		{
			int num;
			try
			{
				num = Integer.parseInt(part);
			} catch (NumberFormatException e)
			{
				return false;
			}

			if (num < 0 || num > 255)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Checks whether the range represented by startIP-endIP is a valid ip range
	 * 
	 * @param startIP
	 *            - the lower ip in the range
	 * @param endIP
	 *            - the upper ip in the range
	 * @return true if valid
	 */
	public static boolean isValidIPRange(String startIP, String endIP)
	{
		long start = NetUtils.ipAsLong(startIP);
		if (start < 0)
			return false;

		long end = NetUtils.ipAsLong(endIP);
		if (end < 0)
			return false;

		return (start <= end);
	}

	/**
	 * Convert IP and mask to first IP in range.
	 * 
	 * @param ip
	 *            IP address as numeric value (long for later compare in range).
	 * @param maskBits
	 *            Number of fixed bits in range (0-32).
	 * @return First IP in range, or 0 if mask is illegal.
	 */
	public static long ipMaskToFirstIp(long ip, int maskBits)
	{
		if (maskBits < 0 || maskBits > 32)
			return 0;

		return ip & (0xffffffffL << (32 - maskBits));
	}

	/**
	 * @return How many mask bits are fixed in the range from this range's start. The returned mask might be smaller
	 *         than the range, but never bigger.
	 */
	public static int ipRangeNetmaskBits(long ipStart, long ipEnd)
	{
		// How many IPs are in this range
		long rangeIpsCount = ipEnd - ipStart + 1;

		// Error in given addresses
		if (rangeIpsCount <= 0)
			return 0;

		//
		// Determine the bigest possible included netmask
		//
		long testingBit = 1L;
		int count = 0;
		while (((ipStart & testingBit) == 0) && ((ipStart | testingBit) <= ipEnd))
		{
			testingBit = (testingBit << 1) | 1L;
			count++;
		}

		return 32 - count;
	}

	/**
	 * @param addr
	 *            Null or valid IP address.
	 * @return -1 if null or a positive number that represents the IP.
	 */
	public static long inetAddressAsLong(InetAddress addr)
	{
		return (addr == null) ? -1 : addr.hashCode() & 0xffffffffL;
	}

	public static class IpMaskPair implements Comparable<IpMaskPair>
	{
		public long ip;
		public int maskBits;

		public IpMaskPair(long ip, int maskBits)
		{
			this.ip = ip;
			this.maskBits = maskBits;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof IpMaskPair))
				return false;

			IpMaskPair other = (IpMaskPair) o;
			return ip == other.ip && maskBits == other.maskBits;
		}

		public int compareTo(IpMaskPair other)
		{
			return (ip != other.ip) ? sgn(ip - other.ip) : (maskBits - other.maskBits);
		}

		private int sgn(long value)
		{
			if (value < 0)
				return -1;
			else if (value > 0)
				return 1;
			else
				return 0;
		}
	};

	/**
	 * Return the requested bit of the input.
	 * 
	 * @param input
	 * @param bitNum
	 * @return
	 */
	private static long bit(long input, int bitNum)
	{
		return input & (0x80000000L >>> bitNum);
	}

	/**
	 * Either add an entry to the collection of ip mask pairs or split the range to 2 ranges and invoke this method on
	 * each of them.
	 * 
	 * @param start
	 * @param end
	 * @param ret
	 * @param maskBits
	 */
	private static void rangeToIpMaskPairs(long start, long end, ArrayList<IpMaskPair> ret, int maskBits)
	{
		if (start == end)
		{
			ret.add(new IpMaskPair(start, 32));
			return;
		}

		while (maskBits < 32 && bit(start, maskBits) == bit(end, maskBits))
			maskBits++;

		if (start == ipMaskToFirstIp(end, maskBits) && end == ipMaskToLastIp(start, maskBits))
		{
			ret.add(new IpMaskPair(start, maskBits));
		} else
		{
			rangeToIpMaskPairs(start, ipMaskToLastIp(start, maskBits + 1), ret, maskBits);
			rangeToIpMaskPairs(ipMaskToFirstIp(end, maskBits + 1), end, ret, maskBits);
		}
	}

	/**
	 * @param ip
	 *            Base IP as long.
	 * @param maskBits
	 *            Number of mask bits (1-32).
	 * @return List of long numbers representing all the IP addresses in the range (inclusive).
	 */
	public static Collection<Long> ipMaskToList(long ip, int maskBits)
	{
		LinkedList<Long> result = new LinkedList<Long>();

		long start = ipMaskToFirstIp(ip, maskBits);
		long end = ipMaskToLastIp(ip, maskBits);

		for (long curAddr = start; curAddr <= end; curAddr++)
		{
			result.add(curAddr);
		}

		return result;
	}

	/**
	 * Convert ip range represented by [start , end ] pair to collection of [ ip , maskBits]
	 * 
	 * @param start
	 * @param end
	 * @return List<IpMaskPair>
	 */
	public static List<IpMaskPair> rangeToIpMaskPairs(long start, long end)
	{
		ArrayList<IpMaskPair> ret = new ArrayList<IpMaskPair>();
		start &= 0xffffffffl;
		end &= 0xffffffffl;
		if (start < end)
			rangeToIpMaskPairs(start, end, ret, 0);
		else
			rangeToIpMaskPairs(end, start, ret, 0);
		return ret;
	}

	/**
	 * Convert IP and mask to last IP in range.
	 * 
	 * @param ip
	 *            IP address as numeric value (long for later compare in range).
	 * @param maskBits
	 *            Number of fixed bits in range (0-32).
	 * @return Last IP in range, or 0 if mask is illegal.
	 */
	public static long ipMaskToLastIp(long ip, int maskBits)
	{
		if (maskBits < 0 || maskBits > 32)
			return 0;

		return (ip & (0xffffffffL << (32 - maskBits))) | (0xffffffffL >>> maskBits);
	}

	/**
	 * For a better cidr result (without a lot of /32) changing ip addresses that start with 1 to 0 and end with 254 to
	 * 255 10.43.21.1 -> 10.43.21.0 10.43.21.254 -> 10.43.21.255
	 * 
	 * @param IP
	 *            address as long
	 * @return the new ip address - if somethint is wrong return the old ip
	 */
	public static long computeSimpleIp(long ip)
	{
		String ipStr = NetUtils.ipAsStr(ip, true);
		String[] ipArray = ipStr.split("\\.");

		if ("1".equals(ipArray[3]))
		{
			return (ip - 1);
		} else if ("254".equals(ipArray[3]))
		{
			return (ip + 1);
		}
		return ip;
	}

	/**
	 * Convert byteArr address into cidr - if the range can not be represented in one cidr it will be represented in
	 * list of cidr If the last byte equal to 1 or 254 it will be changed to 0 and 255 respectively
	 * 
	 * @param start
	 *            ip IP address as Byte arrays.
	 * @param start
	 *            ip IP address as byte array.
	 * @return List of cidr, or null if the long addresses are illegal.
	 */
	public static List<String> getCidrFromByteArr(long start, long end)
	{
		long newStart = computeSimpleIp(start);
		long newEnd = computeSimpleIp(end);

		return (getCidrFromLong(newStart, newEnd));
	}

	/**
	 * Taking a given range and divide it to a smaller group of range For a better result (without a lot of /32) please
	 * use computeSimpleIp
	 * 
	 * @param start
	 *            ip IP address as long.
	 * @param end
	 *            ip IP address as long.
	 * @param the
	 *            number of addresses to divide to
	 * @return List of cidr, or null if the long addresses are illegal
	 */
	public static List<String> divideRange(long start, long end, int numSubAddresses, boolean bigEndian)
	{
		List<String> listAddr = new ArrayList<String>();
		// how many adresses in one sub range
		long numIP = (end - start) / numSubAddresses;
		long subStart = start;
		long subEnd = start - 1;
		for (int i = 0; i < numSubAddresses; i++)
		{
			subStart = subEnd + 1;
			if (i != (numSubAddresses - 1))
			{
				subEnd = subStart + numIP;
			} else
			{
				subEnd = end;
			}
			List<String> subAddr = getCidrFromByteArr(subStart, subEnd);
			listAddr.addAll(subAddr);
		}
		return listAddr;
	}

	/**
	 * Taking a given range and divide it to a smaller group of range. The parameter dicided which range will be
	 * returned(every two or every tree)
	 * 
	 * @param start
	 *            ip IP address as long.
	 * @param end
	 *            ip IP address as long.
	 * @param the
	 *            number of addresses to divide to
	 * @param every
	 *            'modulo' range should be returned
	 * @param the
	 *            first sub range that will be in new list
	 * @return List of cidr, or null if the long addresses are illegal
	 */
	public static List<String> portionRanges(long start, long end, int numSubAddresses, int modulo, boolean bigEndian)
	{
		List<String> listSubAddr = new ArrayList<String>();
		List<String> listAddr = divideRange(start, end, numSubAddresses, bigEndian);
		int i = 0;

		for (String tmp : listAddr)
		{
			if (i % modulo == 0)
			{
				listSubAddr.add(tmp);
			}
			i++;
		}
		return listSubAddr;
	}

	/**
	 * Convert long address into cidr - if the range can not be represented in one cidr it will be represented in list
	 * of cidr
	 * 
	 * @param start
	 *            ip IP address as numeric value (long for later compare in range).
	 * @param start
	 *            ip IP address as numeric value (long for later compare in range).
	 * @return List of cidr, or null if the long addresses are illegal.
	 */
	public static List<String> getCidrFromLong(long start, long end)
	{
		start &= 0xFFFFFFFFl;
		end &= 0xFFFFFFFFl;

		if (start > end)
		{
			return null;
		}
		//
		// Produce list
		//
		List<String> ans = new ArrayList<String>();
		//
		// This loop is needed because some ranges need more than one netmask
		// for full coverage
		//
		long curStart = start;
		while (true)
		{
			int fixedMaskBits = NetUtils.ipRangeNetmaskBits(curStart, end);
			if (fixedMaskBits <= 0)
			{
				ans.add(NetUtils.ipAsStr(curStart, true));
				break;
			}

			ans.add(NetUtils.ipAsStr(curStart, true) + "/" + fixedMaskBits);

			//
			// If mask does not cover the range
			//
			long lastInMask = NetUtils.ipMaskToLastIp(curStart, fixedMaskBits);
			if (lastInMask >= end)
				break;

			curStart = lastInMask + 1;
		}
		return ans;
	}

	/**
	 * Convert bits mask into String (e.g.: 255.255.255.0)
	 * 
	 * @param bits
	 *            mask in bits
	 * @return Mask in String format.
	 */
	public static String getMaskFromBits(int maskBits)
	{
		String ret = "";

		int octetCount = 1;
		while (maskBits > 0)
		{
			if (maskBits > 8)
			{
				ret += "255.";
				maskBits -= 8;
			} else
			{
				int lastOctet = 0;
				for (; maskBits > 0; maskBits--)
					lastOctet += Math.pow(2, (8 - maskBits));
				ret += lastOctet;
				break;
			}
			octetCount++;
		}

		for (; octetCount < 4; octetCount++)
			ret += ".0";

		return ret;
	}
}
