package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.pack.conns.RemoteMachineList;

public class TimeoutThread extends Thread {
	private static final int LOOP_MILLIS = 10;
	private static final int MIN_TIMEOUT = LOOP_MILLIS + 5;
	private static int sndChunkTimeoutMillis = 1000;
	/**
	 * Receiver side remote machines, meaning that these are senders.
	 */
	private final RemoteMachineList remoteMachineListRcv;

	//
	// Statistics
	//

	public TimeoutThread(RemoteMachineList remoteMachineListRcv) {
		super("TimeoutThread");

		this.remoteMachineListRcv = remoteMachineListRcv;
	}

	@Override
	public void run() {
		while (true) {
			try {
				cleanup();
			} catch (Throwable t) {
				t.printStackTrace();
			}

			try {
				Thread.sleep(LOOP_MILLIS);
			} catch (InterruptedException e) {
			}
		}
	}

	private void cleanup() {
		remoteMachineListRcv.releaseTimeoutBuffers(sndChunkTimeoutMillis);
	}

	/**
	 * Set a new timeout if lower than current.
	 */
	public static void setSndChunkTimeoutMillis(int timeoutMillis) {
		sndChunkTimeoutMillis = Math.max(MIN_TIMEOUT, Math.min(
				sndChunkTimeoutMillis, timeoutMillis / 2 - LOOP_MILLIS));
	}
}
