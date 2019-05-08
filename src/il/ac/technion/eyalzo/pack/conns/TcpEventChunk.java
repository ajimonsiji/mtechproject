package il.ac.technion.eyalzo.pack.conns;

/**
 * Anchor was detected and it completes a chunk with a previously detected anchor.
 */
public class TcpEventChunk extends TcpEvent
{
	/**
	 * Stamp of the chunk that ends before this anchor.
	 */
	long signature;
	/**
	 * Length of chunk that end in this sequence.
	 */
	int chunkLen;
	/**
	 * Window size of sender when chunk was detected
	 */
	int receiverWindowSize;
	/**
	 * True if chunk was also found in file.
	 */
	boolean chunkInFile;
	/**
	 * True if matched the expected next chunk by a chain.
	 */
	boolean matchExpected;

	/**
	 * @param stamp
	 *            Optional stamp of the chunk that ends before this anchor. May be null when this is an anchor that was
	 *            during during search mode.
	 */
	public TcpEventChunk(long localSeq, long remoteSeq, long stamp, int chunkLen, int receiverWindowSize,
			boolean chunkInFile, boolean matchExpected)
	{
		super(false, localSeq, remoteSeq);

		this.signature = stamp;
		this.chunkLen = chunkLen;
		this.receiverWindowSize = receiverWindowSize;
		this.chunkInFile = chunkInFile;
		this.matchExpected = matchExpected;
	}

	@Override
	public String toString()
	{
		return String.format("%08x %,d bytes %,d window", this.signature, this.chunkLen, this.receiverWindowSize);
	}

	@Override
	public String getColor()
	{
		return "#00FFFF";
	}

	@Override
	public String getType()
	{
		return "Chunk";
	}
}
