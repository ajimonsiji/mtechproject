package il.ac.technion.eyalzo.pack.conns;

/**
 * Prediction sent in a single packet. Receiver side.
 */
public class TcpEventPredSent extends TcpEventPred
{
	/**
	 * @param chunks
	 *            Number of sent chunks.
	 * @param predSeqStartRel
	 *            TCP sequence of the prediction's start (relative offset to start).
	 * @param predSeqEndRel
	 *            TCP sequence of the prediction's end, inclusive (relative offset to start).
	 */
	public TcpEventPredSent(long localSeq, long remoteSeq, int chunks, long predSeqStartRel, long predSeqEndRel)
	{
		super(true, localSeq, remoteSeq, chunks, predSeqStartRel, predSeqEndRel);
	}

	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getColor()
	{
		return "pink";
	}

	@Override
	public String getType()
	{
		return "Pred sent";
	}	
}
