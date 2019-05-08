package il.ac.technion.eyalzo.pack.conns;

public class TcpEventClose extends TcpEvent
{
	// True for FIN or false for RST
	boolean	isFin;

	public TcpEventClose(boolean dirUp, long localSeq, long remoteSeq,
			boolean isFin)
	{
		super(dirUp, localSeq, remoteSeq);
	}

	@Override
	public String toString()
	{
		return isFin ? "FIN" : "RST";
	}

	@Override
	public String getColor()
	{
		return "#dd0000";
	}

	@Override
	public String getType()
	{
		return "Close";
	}
}
