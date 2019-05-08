package il.ac.technion.eyalzo.pack.conns;

/**
 * Prediction sent (RCV side) or received (SND side) in a single packet.
 */
public abstract class TcpEventPred extends TcpEvent
{
	/**
	 * Number of chunks in the prediction.
	 */
	int chunks;
	/**
	 * TCP sequence of the prediction's start (relative offset to start).
	 */
	long predSeqStartRel;
	/**
	 * TCP sequence of the prediction's end, inclusive (relative offset to start).
	 */
	long predSeqEndRel;

	/**
	 * @param dirOut
	 *            True for receiver prediction or false for sender incoming prediction.
	 * @param chunks
	 *            Number of covered chunks.
	 * @param predSeqStartRel
	 *            TCP sequence of the prediction's start (relative offset to start).
	 * @param predSeqEndRel
	 *            TCP sequence of the prediction's end, inclusive (relative offset to start).
	 */
	public TcpEventPred(boolean dirOut, long localSeq, long remoteSeq, int chunks, long predSeqStartRel,
			long predSeqEndRel)
	{
		super(dirOut, localSeq, remoteSeq);
		this.chunks = chunks;
		this.predSeqStartRel = predSeqStartRel;
		this.predSeqEndRel = predSeqEndRel;
	}

	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getColor()
	{
		return "#4466CC";
	}
}
