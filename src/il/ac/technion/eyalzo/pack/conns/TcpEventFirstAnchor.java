package il.ac.technion.eyalzo.pack.conns;

public class TcpEventFirstAnchor extends TcpEvent
{
	public TcpEventFirstAnchor(boolean dirUp, long localSeq, long remoteSeq)
	{
		super(dirUp, localSeq, remoteSeq);
	}

	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getColor()
	{
		return "#0066CC";
	}

	@Override
	public String getType()
	{
		return "First anchor";
	}
}
