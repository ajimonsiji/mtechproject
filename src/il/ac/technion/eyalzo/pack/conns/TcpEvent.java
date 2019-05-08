package il.ac.technion.eyalzo.pack.conns;

public abstract class TcpEvent
{
	protected long time;
	/**
	 * True if packet was sent from me. False if it was sent to me.
	 */
	protected boolean dirUp;
	protected long localSeq;
	protected long remoteSeq;

	public TcpEvent(boolean dirUp, long localSeq, long remoteSeq)
	{
		super();
		this.time = System.currentTimeMillis();
		this.dirUp = dirUp;
		this.localSeq = localSeq;
		this.remoteSeq = remoteSeq;
	}

	/**
	 * @return Name of type for HTML, for display in event table.
	 */
	public abstract String getColor();

	/**
	 * @return Name of type, for display in event table.
	 */
	public abstract String getType();
}
