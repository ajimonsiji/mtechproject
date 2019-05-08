package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.pack.conns.RemoteMachineList;

public class CleanupThread extends Thread
{
	private static final int LOOP_MILLIS = 1000 * 15;
	private static final int REMOTE_MACHINE_TTL_MILLIS = 1000 * 60 * 60;
	private final RemoteMachineList remoteMachineListSnd;
	private final RemoteMachineList remoteMachineListRcv;
	//
	// Statistics
	//
	/**
	 * Next time the loop will be executed.
	 */
	private long statNextLoopTime;
	/**
	 * How many import loops were performed so far.
	 */
	private int statLoopsCount;

	public CleanupThread(RemoteMachineList remoteMachineListSnd,
			RemoteMachineList remoteMachineListRcv)
	{
		super("CleanupThread");

		this.remoteMachineListSnd = remoteMachineListSnd;
		this.remoteMachineListRcv = remoteMachineListRcv;
	}

	@Override
	public void run()
	{
		boolean firstTime = true;

		while (true)
		{
			statLoopsCount++;
			statNextLoopTime = System.currentTimeMillis() + LOOP_MILLIS;

			if (firstTime)
			{
				firstTime = false;
			} else
			{
				try
				{
					cleanup();
				} catch (Throwable t)
				{
					t.printStackTrace();
				}
			}

			// Sleep must be outside the loop's try-catch, so exceptions will
			// not cause a busy-loop
			long timeToSleep = statNextLoopTime - System.currentTimeMillis();
			if (timeToSleep > 0)
			{
				try
				{
					Thread.sleep(timeToSleep);
				} catch (InterruptedException e)
				{
				}
			}
		}
	}

	private void cleanup()
	{
		remoteMachineListSnd.cleanup(REMOTE_MACHINE_TTL_MILLIS);
		remoteMachineListRcv.cleanup(REMOTE_MACHINE_TTL_MILLIS);
//		Main.chains.backupChains();
	}
}
