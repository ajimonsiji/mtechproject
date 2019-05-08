package il.ac.technion.eyalzo.net;

import il.ac.technion.eyalzo.common.OctetConverter;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IPPacket
{

    /** Offset into byte array of the type of service header value. */
    public static final int     OFFSET_TYPE_OF_SERVICE     = 1;

    /** Offset into byte array of total packet length header value. */
    public static final int     OFFSET_TOTAL_LENGTH        = 2;

    /** Offset into byte array of the identification header value. */
    public static final int     OFFSET_IDENTIFICATION      = 4;

    /** Offset into byte array of the flags header value. */
    public static final int     OFFSET_FLAGS               = 6;

    /** Offset into byte array of source address header value. */
    public static final int     OFFSET_SOURCE_ADDRESS      = 12;

    /** Number of bytes in source address. */
    public static final int     LENGTH_SOURCE_ADDRESS      = 4;

    /** Offset into byte array of destination address header value. */
    public static final int     OFFSET_DESTINATION_ADDRESS = 16;

    /** Number of bytes in destination address. */
    public static final int     LENGTH_DESTINATION_ADDRESS = 4;

    /** Offset into byte array of time to live header value. */
    public static final int     OFFSET_TTL                 = 8;

    /** Offset into byte array of protocol number header value. */
    public static final int     OFFSET_PROTOCOL            = 9;

    /** Offset into byte array of header checksum header value. */
    public static final int     OFFSET_IP_CHECKSUM         = 10;

    /** Protocol constant for IPv4. */
    public static final int     PROTOCOL_IP                = 0;

    /** Protocol constant for ICMP. */
    public static final int     PROTOCOL_ICMP              = 1;

    /** Protocol constant for TCP. */
    public static final int     PROTOCOL_TCP               = 6;

    /** Protocol constant for UDP. */
    public static final int     PROTOCOL_UDP               = 17;

    /** Size in bytes of the ethernet header */
    public static final int     ETH_HDR_LEN                = 14;

    /** Size in bytes of the mac address */
    private static final int    MAC_LEN                    = 6;

    /** Offset of ether type in Ethernet frame */
    private static final int    OFFSET_ETHER_TYPE          = 12;

    /** Offset of vlan tag in ethernet frame */
    private static final int    OFFSET_VLAN_TAG            = OFFSET_ETHER_TYPE + 2;

    /** Ethernet frame type which contains a vlan */
    private static final byte[] ETHER_TYPE_VLAN            = {(byte) 0x81, (byte) 0x00};

    /** Size of vlan tag in ethernet frame */
    private static final int    VLAN_TAG_LEN               = 4;

    /** Raw packet data. */
    protected byte[]            _data_;

    /** The byte offset into the raw packet where the IP packet begins. */
    private int                 __offset;

    /**
     * Creates a new IPPacket of a given size.
     * 
     * @param size The number of bytes in the packet.
     */
    public IPPacket(int size)
    {
        setData(new byte[size]);
    }

    /**
     * Creates a new IPPacket of a given size.
     * 
     * @param size The number of bytes in the packet.
     * @param isEthHdrIcluded Inidcates whether the raw data includes the ethernet header otherwise the first byte is the
     *        beginning of the IP header.
     */
    public IPPacket(int size, boolean isEthHdrIcluded)
    {
        setData(new byte[size], isEthHdrIcluded);
    }

    /**
     * @return The size of the packet.
     */
    public int size()
    {
        return _data_.length;
    }

    /**
     * Sets the raw packet byte array. Although this method would appear to violate object-oriented principles, it is
     * necessary to implement efficient packet processing. You don't necessarily want to allocate a new IPPacket and data
     * buffer every time a packet arrives and you need to be able to wrap packets from APIs that supply them as byte arrays.
     * 
     * @param data The raw packet byte array to wrap.
     */
    public void setData(byte[] data)
    {
        _data_ = data;
        this.__offset = 0;
    }

    /**
     * @see IPPacket.setData(byte[])
     * @param data
     * @param isEthHdrIcluded Inidcates whether the raw data includes the ethernet header otherwise the first byte is the
     *        beginning of the IP header.
     */
    public void setData(byte[] data, boolean isEthHdrIcluded)
    {
        setData(data);

        if (isEthHdrIcluded)
        {
            __offset = ETH_HDR_LEN;

            if (data[OFFSET_ETHER_TYPE] == ETHER_TYPE_VLAN[0] && data[OFFSET_ETHER_TYPE + 1] == ETHER_TYPE_VLAN[1])
            {
                // Ethernet frame contains a vlan tag, i.e. additional 4 bytes
                __offset += VLAN_TAG_LEN;
            }
        }
        else
        {
            __offset = 0;
        }
    }

    /**
     * Copies the raw packet data into a byte array. If the array is too small to hold the data, the data is truncated.
     * 
     * @param data The raw packet byte array to wrap.
     */
    public void getData(byte[] data)
    {
        System.arraycopy(_data_, 0, data, 0, data.length);
    }

    /**
     * Copies the contents of an IPPacket to the calling instance. If the two packets are of different lengths, a new byte
     * array is allocated equal to the length of the packet parameter.
     * 
     * @param packet The packet to copy from.
     */
    public final void copy(IPPacket packet)
    {
        if (_data_.length != packet.size())
            setData(new byte[packet.size()]);
        System.arraycopy(packet._data_, 0, _data_, 0, _data_.length);
    }

    /**
     * Sets the IP version header value.
     * 
     * @param version A 4-bit unsigned integer.
     */
    public final void setIPVersion(int version)
    {
        _data_[__offset + 0] &= 0x0f;
        _data_[__offset + 0] |= ((version << 4) & 0xf0);
    }

    /**
     * Returns the IP version header value.
     * 
     * @return The IP version header value.
     */
    public final int getIPVersion()
    {
        return ((_data_[__offset + 0] & 0xf0) >> 4);
    }

    /**
     * Sets the IP header length field. At most, this can be a four-bit value. The high order bits beyond the fourth bit
     * will be ignored.
     * 
     * @param length The length of the IP header in 32-bit words.
     */
    public void setIPHeaderLength(int length)
    {
        // Clear low order bits and then set
        _data_[__offset + 0] &= 0xf0;
        _data_[__offset + 0] |= (length & 0x0f);
    }

    /**
     * @return The length of the IP header in 32-bit words.
     */
    public final int getIPHeaderLength()
    {
        return (_data_[__offset + 0] & 0x0f);
    }

    /**
     * @return The length of the IP header in bytes.
     */
    public final int getIPHeaderByteLength()
    {
        return getIPHeaderLength() << 2;
    }

    /**
     * Sets the IP type of service header value. You have to set the individual service bits yourself. Convenience methods
     * for setting the service bit fields directly may be added in a future version.
     * 
     * @param service An 8-bit unsigned integer.
     */
    public final void setTypeOfService(int service)
    {
        _data_[__offset + OFFSET_TYPE_OF_SERVICE] = (byte) (service & 0xff);
    }

    /**
     * Returns the IP type of service header value.
     * 
     * @return The IP type of service header value.
     */
    public final int getTypeOfService()
    {
        return (_data_[__offset + OFFSET_TYPE_OF_SERVICE] & 0xff);
    }

    /**
     * Sets the IP packet total length header value.
     * 
     * @param length The total IP packet length in bytes.
     */
    public final void setIPPacketLength(int length)
    {
        _data_[__offset + OFFSET_TOTAL_LENGTH] = (byte) ((length >> 8) & 0xff);
        _data_[__offset + OFFSET_TOTAL_LENGTH + 1] = (byte) (length & 0xff);
    }

    /**
     * @return The IP packet total length header value.
     */
    public final int getIPPacketLength()
    {
        return (((_data_[__offset + OFFSET_TOTAL_LENGTH] & 0xff) << 8) | (_data_[__offset + OFFSET_TOTAL_LENGTH + 1] & 0xff));
    }

    /**
     * Sets the IP identification header value.
     * 
     * @param id A 16-bit unsigned integer.
     */
    public void setIdentification(int id)
    {
        _data_[__offset + OFFSET_IDENTIFICATION] = (byte) ((id >> 8) & 0xff);
        _data_[__offset + OFFSET_IDENTIFICATION + 1] = (byte) (id & 0xff);
    }

    /**
     * Returns the IP identification header value.
     * 
     * @return The IP identification header value.
     */
    public final int getIdentification()
    {
        return (((_data_[__offset + OFFSET_IDENTIFICATION] & 0xff) << 8) | (_data_[__offset + OFFSET_IDENTIFICATION + 1] & 0xff));
    }

    /**
     * Sets the IP flags header value. You have to set the individual flag bits yourself. Convenience methods for setting
     * the flag bit fields directly may be added in a future version.
     * 
     * @param flags A 3-bit unsigned integer.
     */
    public final void setIPFlags(int flags)
    {
        _data_[__offset + OFFSET_FLAGS] &= 0x1f;
        _data_[__offset + OFFSET_FLAGS] |= ((flags << 5) & 0xe0);
    }

    /**
     * Returns the IP flags header value.
     * 
     * @return The IP flags header value.
     */
    public final int getIPFlags()
    {
        return ((_data_[__offset + OFFSET_FLAGS] & 0xe0) >> 5);
    }

    /**
     * Sets the fragment offset header value. The offset specifies a number of octets (i.e., bytes).
     * 
     * @param offset A 13-bit unsigned integer.
     */
    public void setFragmentOffset(int offset)
    {
        _data_[__offset + OFFSET_FLAGS] &= 0xe0;
        _data_[__offset + OFFSET_FLAGS] |= ((offset >> 8) & 0x1f);
        _data_[__offset + OFFSET_FLAGS + 1] = (byte) (offset & 0xff);
    }

    /**
     * Returns the fragment offset header value.
     * 
     * @return The fragment offset header value.
     */
    public final int getFragmentOffset()
    {
        return (((_data_[__offset + OFFSET_FLAGS] & 0x1f) << 8) | (_data_[__offset + OFFSET_FLAGS + 1] & 0xff));
    }

    /**
     * Sets the protocol number.
     * 
     * @param protocol The protocol number.
     */
    public final void setProtocol(int protocol)
    {
        _data_[__offset + OFFSET_PROTOCOL] = (byte) protocol;
    }

    /**
     * @return The protocol number.
     */
    public final int getProtocol()
    {
        return _data_[__offset + OFFSET_PROTOCOL];
    }

    /**
     * Sets the time to live value in seconds.
     * 
     * @param ttl The time to live value in seconds.
     */
    public final void setTTL(int ttl)
    {
        _data_[__offset + OFFSET_TTL] = (byte) ttl;
    }

    /**
     * @return The time to live value in seconds.
     */
    public final int getTTL()
    {
        return _data_[__offset + OFFSET_TTL];
    }

    /**
     * Calculates checksums assuming the checksum is a 16-bit header field. This method is generalized to work for IP, ICMP,
     * UDP, and TCP packets given the proper parameters.
     */
    protected int _computeChecksum_(int startOffset, int checksumOffset, int length, int virtualHeaderTotal,
            boolean update)
    {
        int total = 0;
        int i = startOffset;
        int imax = checksumOffset;

        while (i < imax)
            total += (((_data_[i++] & 0xff) << 8) | (_data_[i++] & 0xff));

        // Skip existing checksum.
        i = checksumOffset + 2;

        imax = length - (length % 2);

        while (i < imax)
            total += (((_data_[i++] & 0xff) << 8) | (_data_[i++] & 0xff));

        if (i < length)
            total += ((_data_[i] & 0xff) << 8);

        total += virtualHeaderTotal;

        // Fold to 16 bits
        while ((total & 0xffff0000) != 0)
            total = (total & 0xffff) + (total >>> 16);

        total = (~total & 0xffff);

        if (update)
        {
            _data_[checksumOffset] = (byte) (total >> 8);
            _data_[checksumOffset + 1] = (byte) (total & 0xff);
        }

        return total;
    }

    /**
     * Computes the IP checksum, optionally updating the IP checksum header.
     * 
     * @param update Specifies whether or not to update the IP checksum header after computing the checksum. A value of true
     *        indicates the header should be updated, a value of false indicates it should not be updated.
     * @return The computed IP checksum.
     */
    public final int computeIPChecksum(boolean update)
    {
        return _computeChecksum_(__offset, __offset + OFFSET_IP_CHECKSUM, __offset + getIPHeaderByteLength(), 0, update);
    }

    /**
     * Same as <code>computeIPChecksum(true);</code>
     * 
     * @return The computed IP checksum value.
     */
    public final int computeIPChecksum()
    {
        return computeIPChecksum(true);
    }

    /**
     * @return The IP checksum header value.
     */
    public final int getIPChecksum()
    {
        return (((_data_[__offset + OFFSET_IP_CHECKSUM] & 0xff) << 8) | (_data_[__offset + OFFSET_IP_CHECKSUM + 1] & 0xff));
    }

    /**
     * Retrieves the source IP address into a byte array. The array should be {@link #LENGTH_SOURCE_ADDRESS} bytes long.
     * 
     * @param address The array in which to store the address.
     */
    public final void getSource(byte[] address)
    {
        System.arraycopy(_data_, __offset + OFFSET_SOURCE_ADDRESS, address, 0, (address.length < LENGTH_SOURCE_ADDRESS
                ? address.length : LENGTH_SOURCE_ADDRESS));
    }

    /**
     * Retrieves the destionation IP address into a byte array. The array should be {@link #LENGTH_DESTINATION_ADDRESS}
     * bytes long.
     * 
     * @param address The array in which to store the address.
     */
    public final void getDestination(byte[] address)
    {
        System.arraycopy(_data_, __offset + OFFSET_DESTINATION_ADDRESS, address, 0,
                         (address.length < LENGTH_DESTINATION_ADDRESS ? address.length : LENGTH_DESTINATION_ADDRESS));
    }

    /**
     * Retrieves the source IP address as a string into a StringBuffer.
     * 
     * @param buffer The StringBuffer in which to store the address.
     */
    public final void getSource(StringBuffer buffer)
    {
        OctetConverter.octetsToString(buffer, _data_, __offset + OFFSET_SOURCE_ADDRESS);
    }

    /**
     * Retrieves the destination IP address as a string into a StringBuffer.
     * 
     * @param buffer The StringBuffer in which to store the address.
     */
    public final void getDestination(StringBuffer buffer)
    {
        OctetConverter.octetsToString(buffer, _data_, __offset + OFFSET_DESTINATION_ADDRESS);
    }

    /**
     * Sets the source IP address using a word representation.
     * 
     * @param src The source IP address as a 32-bit word.
     */
    public final void setSourceAsWord(int src)
    {
        OctetConverter.intToOctets(src, _data_, __offset + OFFSET_SOURCE_ADDRESS);
    }

    /**
     * Sets the destination IP address using a word representation.
     * 
     * @param dest The source IP address as a 32-bit word.
     */
    public final void setDestinationAsWord(int dest)
    {
        OctetConverter.intToOctets(dest, _data_, __offset + OFFSET_DESTINATION_ADDRESS);
    }

    /**
     * @return The source IP address as a 32-bit word.
     */
    public final int getSourceAsWord()
    {
        return OctetConverter.octetsToInt(_data_, __offset + OFFSET_SOURCE_ADDRESS);
    }

    /**
     * @return The destination IP address as a 32-bit word.
     */
    public final int getDestinationAsWord()
    {
        return OctetConverter.octetsToInt(_data_, __offset + OFFSET_DESTINATION_ADDRESS);
    }

    /**
     * @return The source IP address as a java.net.InetAddress instance.
     */
    public final InetAddress getSourceAsInetAddress() throws UnknownHostException
    {
        StringBuffer buf = new StringBuffer();
        getSource(buf);
        return InetAddress.getByName(buf.toString());
        // This works only with JDK 1.4 and up
        /*
         * byte[] octets = new byte[4]; getSource(octets); return InetAddress.getByAddress(octets);
         */
    }

    /**
     * @return The destination IP address as a java.net.InetAddress instance.
     */
    public final InetAddress getDestinationAsInetAddress() throws UnknownHostException
    {
        StringBuffer buf = new StringBuffer();
        getDestination(buf);
        return InetAddress.getByName(buf.toString());
        // This works only with JDK 1.4 and up
        /*
         * byte[] octets = new byte[4]; getDestination(octets); return InetAddress.getByAddress(octets);
         */
    }

    /**
     * Get the offset of the ip header from the start of the raw packet data. Will be non zero in case the raw data includes
     * additional data like the ethernet header.
     * 
     * @return
     */
    public int getIPHdrOffset()
    {
        return __offset;
    }

    /**
     * Indicates whether the raw data from which this packet is constructed contains also the Ethernet layer.
     * 
     * @return True if Ethernet data is included in packet (actually a frame).
     */
    public boolean isEthHdrIncluded()
    {
        return __offset > 0;
    }

    /**
     * @return Zero if Ethernet data is not included in packet, or {@link #ETH_HDR_LEN} if it does.
     */
    public final int getEthHeaderByteLength()
    {
        return __offset > 0 ? ETH_HDR_LEN : 0;
    }

    /**
     * Return the source mac address if the ethernet header was included in the raw packet.
     * 
     * @return mac address or null if not available
     */
    public byte[] getSourceMacAddress()
    {
        if (!isEthHdrIncluded())
        {
            return null;
        }

        byte[] srcMac = new byte[6];
        System.arraycopy(_data_, MAC_LEN, srcMac, 0, MAC_LEN);
        return srcMac;
    }

    /**
     * Return the destination mac address if the ethernet header was included in the raw packet.
     * 
     * @return mac address or null if not available
     */
    public byte[] getDestinationMacAddress()
    {
        if (!isEthHdrIncluded())
        {
            return null;
        }

        byte[] dstMac = new byte[6];
        System.arraycopy(_data_, 0, dstMac, 0, MAC_LEN);
        return dstMac;
    }

    /**
     * Return the vlan tag if the ethernet header was included in the raw packet and the ethernet frame is tagged with a
     * vlan
     * 
     * @return vlan tag or null if not available
     */
    public byte[] getVlanTag()
    {
        if (!isEthHdrIncluded())
        {
            return null;
        }

        if (_data_[OFFSET_ETHER_TYPE] != ETHER_TYPE_VLAN[0] || _data_[OFFSET_ETHER_TYPE + 1] != ETHER_TYPE_VLAN[1])
        {
            return null;
        }

        byte[] vlan = new byte[VLAN_TAG_LEN];
        System.arraycopy(_data_, OFFSET_VLAN_TAG, vlan, 0, VLAN_TAG_LEN);

        return vlan;
    }
}
