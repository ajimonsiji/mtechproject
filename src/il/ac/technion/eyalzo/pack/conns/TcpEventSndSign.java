package il.ac.technion.eyalzo.pack.conns;

/**
 * Sender signed a delayed block (chunk in practice) on its way out.
 */
public class TcpEventSndSign extends TcpEvent
{
	boolean match;
	int signature;
	int chunkLen;

	/**
	 * @param match
	 *            True if signed and matched the prediction.
	 */
	public TcpEventSndSign(long localSeq, long remoteSeq, boolean match, int signature, int chunkLen)
	{
		super(true, localSeq, remoteSeq);

		this.match = match;
		this.signature = signature;
		this.chunkLen = chunkLen;
	}

	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getColor()
	{
		return match ? "lightgreen" : "#a00000";
	}

	@Override
	public String getType()
	{
		return "Sign check";
	}
}
