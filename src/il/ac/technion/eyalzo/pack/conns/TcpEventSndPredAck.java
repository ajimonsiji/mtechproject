package il.ac.technion.eyalzo.pack.conns;

/**
 * Sender ACK for prediction.
 */
public class TcpEventSndPredAck extends TcpEvent
{
	long ackLength;

	public TcpEventSndPredAck(long localSeq, long remoteSeq, long ackLength)
	{
		super(true, localSeq, remoteSeq);

		this.ackLength = ackLength;
	}

	@Override
	public String getColor()
	{
		return "#00d010";
	}

	@Override
	public String getType()
	{
		return "Ack";
	}
}
