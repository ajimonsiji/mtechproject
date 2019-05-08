package il.ac.technion.eyalzo.pack.conns;

public class TcpEventHttpRequest extends TcpEvent
{
	String	url;
	String	hostName;

	public TcpEventHttpRequest(boolean dirUp, long localSeq, long remoteSeq,
			String hostName, String url)
	{
		super(dirUp, localSeq, remoteSeq);

		this.hostName = hostName;
		this.url = url;
	}

	@Override
	public String toString()
	{
		if (hostName == null)
		{
			if (url == null)
				return "(none)";

			return "HTTP request " + url;
		}

		return "HTTP request " + hostName + url;
	}

	@Override
	public String getColor()
	{

		return "#CC9900";
	}

	@Override
	public String getType()
	{
		return "HTTP request";
	}
}
