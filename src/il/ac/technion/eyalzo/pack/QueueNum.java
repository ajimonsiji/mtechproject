package il.ac.technion.eyalzo.pack;

public enum QueueNum
{
	SenderIn(1, true, false), SenderOut(2, true, true), ReceiverIn(3, false, false), ReceiverOut(4, false, true);

	public final short queueNum;
	public final boolean dirOut;
	public final boolean sideSender;

	/**
	 * @param sideSender
	 *            Remote machines are senders.
	 */
	QueueNum(int queueNum, boolean sideSender, boolean dirOut)
	{
		this.queueNum = (short) queueNum;
		this.sideSender = sideSender;
		this.dirOut = dirOut;
	}
}
