package il.ac.technion.eyalzo.pack.conns;

public class TcpEventHttpResponse extends TcpEvent
{
	int		responseCode;
	/**
	 * Optional. Length of the HTTP header including the ending double newline.
	 * May be -1 if the header end was not detected (when in second packet etc).
	 */
	int		headerLen;
	long	contentLength;
	String	contentType;

	public TcpEventHttpResponse(boolean dirUp, long localSeq, long remoteSeq,
			int responseCode, int headerLen, long contentLength,
			String contentType)
	{
		super(dirUp, localSeq, remoteSeq);
		this.responseCode = responseCode;
		this.headerLen = headerLen;
		this.contentLength = contentLength;
		this.contentType = contentType;
	}

	@Override
	public String toString()
	{
		return String.format(
				"HTTP response %d, length %d (%s), header len %,d",
				responseCode, contentLength, contentType, headerLen);
	}

	@Override
	public String getColor()
	{
		return "#CCFF99";
	}

	@Override
	public String getType()
	{
		return "HTTP response";
	}
}
