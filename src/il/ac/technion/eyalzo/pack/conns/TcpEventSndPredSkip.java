package il.ac.technion.eyalzo.pack.conns;

public class TcpEventSndPredSkip extends TcpEvent
{
	int missing;

	public TcpEventSndPredSkip(long localSeq, long remoteSeq, int missing)
	{
		super(true, localSeq, remoteSeq);

		this.missing = missing;
	}

	@Override
	public String getColor()
	{
		return "#900000";
	}

	@Override
	public String getType()
	{
		return "Sign missing";
	}
}
