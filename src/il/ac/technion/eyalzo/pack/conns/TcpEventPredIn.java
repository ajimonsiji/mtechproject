package il.ac.technion.eyalzo.pack.conns;

/**
 * Prediction received in a single packet. Sender side.
 */
public class TcpEventPredIn extends TcpEventPred
{
	long localSeqMaxSentRel;

	/**
	 * @param localSeqMaxSentRel
	 *            Maximal sequence number of already sent out data. It is not the regular local sequence which is the
	 *            acknowledge number in the incoming packet that carries the predictions.
	 * @param chunks
	 *            Number of received chunks.
	 * @param predSeqStartRel
	 *            TCP sequence of the prediction's start (relative offset to start).
	 * @param predSeqEndRel
	 *            TCP sequence of the prediction's end, inclusive (relative offset to start).
	 */
	public TcpEventPredIn(long localSeq, long remoteSeq, long localSeqMaxSentRel, int chunks, long predSeqStartRel,
			long predSeqEndRel)
	{
		super(false, localSeq, remoteSeq, chunks, predSeqStartRel, predSeqEndRel);

		this.localSeqMaxSentRel = localSeqMaxSentRel;
	}

	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getColor()
	{
		return "yellow";
	}

	@Override
	public String getType()
	{
		return "Pred in";
	}
}
