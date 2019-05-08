package il.ac.technion.eyalzo.pack.conns;

/**
 * Sent bytes from sender overlap ranges in the predictions inbox.
 */
public class TcpEventPredOverlap extends TcpEventPred
{
	public TcpEventPredOverlap(long localSeq, long remoteSeq, int chunks, long predSeqStartRel,
			long predSeqEndRel)
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
		return "#aa55cc";
	}

	@Override
	public String getType()
	{
		return "Pred overlap";
	}
}
