package il.ac.technion.eyalzo.common;

public final class OctetConverter {
	private OctetConverter() {
	}

	public static final int octetsToInt(byte[] octets, int offset) {
		return (((octets[offset] & 0xff) << 24)
				| ((octets[offset + 1] & 0xff) << 16)
				| ((octets[offset + 2] & 0xff) << 8) | (octets[offset + 3] & 0xff));
	}

	public static final int octetsToInt(byte[] octets) {
		return octetsToInt(octets, 0);
	}

	public static final long octetsToLong(byte[] octets, int offset) {
		return (((octets[offset] & 0xffffL) << 56)
				| ((octets[offset + 1] & 0xffL) << 48)
				| ((octets[offset + 2] & 0xffL) << 40)
				| ((octets[offset + 3] & 0xffL) << 32)
				| ((octets[offset + 4] & 0xffL) << 24)
				| ((octets[offset + 5] & 0xffL) << 16)
				| ((octets[offset + 6] & 0xffL) << 8) | (octets[offset + 7] & 0xffL));
	}

	public static final long octetsToLong(byte[] octets) {
		return octetsToLong(octets, 0);
	}

	public static final void octetsToString(StringBuffer buffer, byte[] octets,
			int offset) {
		buffer.append(octets[offset++] & 0xff);
		buffer.append(".");
		buffer.append(octets[offset++] & 0xff);
		buffer.append(".");
		buffer.append(octets[offset++] & 0xff);
		buffer.append(".");
		buffer.append(octets[offset++] & 0xff);
	}

	public static final void octetsToString(StringBuffer buffer, byte[] octets) {
		octetsToString(buffer, octets, 0);
	}

	public static final void intToString(StringBuffer buffer, int address) {
		buffer.append(0xff & (address >>> 24));
		buffer.append(".");
		buffer.append(0xff & (address >>> 16));
		buffer.append(".");
		buffer.append(0xff & (address >>> 8));
		buffer.append(".");
		buffer.append(0xff & address);
	}

	public static final void intToOctets(int address, byte[] octets, int offset) {
		octets[offset] = (byte) (0xff & (address >>> 24));
		octets[offset + 1] = (byte) (0xff & (address >>> 16));
		octets[offset + 2] = (byte) (0xff & (address >>> 8));
		octets[offset + 3] = (byte) (0xff & address);
	}

	public static final void intToOctets(int address, byte[] octets) {
		intToOctets(address, octets, 0);
	}

	public static final void longToOctets(long address, byte[] octets,
			int offset) {
		octets[offset] = (byte) (0xffL & (address >>> 56));
		octets[offset + 1] = (byte) (0xffL & (address >>> 48));
		octets[offset + 2] = (byte) (0xffL & (address >>> 40));
		octets[offset + 3] = (byte) (0xffL & (address >>> 32));
		octets[offset + 4] = (byte) (0xffL & (address >>> 24));
		octets[offset + 5] = (byte) (0xffL & (address >>> 16));
		octets[offset + 6] = (byte) (0xffL & (address >>> 8));
		octets[offset + 7] = (byte) (0xffL & address);
	}

	public static final void longToOctets(long address, byte[] octets) {
		longToOctets(address, octets, 0);
	}
}
