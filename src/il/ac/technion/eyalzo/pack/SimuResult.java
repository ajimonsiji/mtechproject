package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.common.MapCounter;
import il.ac.technion.eyalzo.webgui.DisplayTable;

import java.util.LinkedList;

public class SimuResult {
	/**
	 * True for snapshots by time or false for snapshots by bytes.
	 */
	private boolean snapshotByTime;
	/**
	 * Bytes or millis, according to {@link #snapshotByTime}.
	 */
	private long snapshotInterval;
	public int predictionLength;
	public int senderAttempts;
	/**
	 * Number of files (and therefore chains) that the sender transmitted.
	 */
	public int senderFiles;
	public long startTime;
	public long endTime;
	/**
	 * Number of chunks saved thanks to redundancy. Also partial.
	 */
	private int savedChunks;
	/**
	 * Number of bytes saved thanks to redundancy.
	 */
	private long savedBytes;
	/**
	 * Receiver did not have a prediction so it looked up a chain and did not
	 * find one.
	 */
	public int receiverChainLookupFail;
	/**
	 * Receiver did not have a prediction so it looked up a chain and found one.
	 */
	private int receiverChainLookupSuccess;
	/**
	 * Receiver did not send a prediction because a former chain just ended.
	 */
	public int receiverEndOfChain;
	/**
	 * Prediction sent and missed. Not necessarily a chain break because this
	 * could be a normal miss after another miss or no-prediction.
	 */
	public int receiverChunkMissed;
	/**
	 * Receiver sent a prediction that failed, got a single chunk and looked for
	 * a new chain immediately but failed to find such chain.
	 */
	public int receiverChainLookupFailImmed;
	/**
	 * Receiver sent a prediction that failed, got a single chunk and found a
	 * new chain immediately.
	 */
	private int receiverChainLookupSuccessImmed;
	/**
	 * How many of {@link #receiverChainLookupSuccessImmed} were found in the
	 * middle of a chain (not start).
	 */
	private int receiverChainLookupSuccessImmedMid;
	/**
	 * Bytes sent as real data.
	 */
	private long sentBytes;
	/**
	 * Complete sent chunks (not partial).
	 */
	private int sentChunks;
	/**
	 * Total number of chunks.
	 */
	private int chunkCount;
	/**
	 * Bytes the sender ran sha-1 on them but there was no match (does not
	 * consider the hints).
	 */
	public long senderSha1FailBytes;
	/**
	 * Bytes the sender ran sha-1 on them and matched with the prediction.
	 */
	public long senderSha1SuccessBytes;

	//
	// LBFS
	//
	private long lbfsSavedBytes;
	private long lbfsSentBytes;
	/**
	 * Time paid (2 x RTT) for each file and/or reaching the 1024+1 chunk.
	 */
	private int lbfsRttTime;
	private int lbfsChunkHahshesInHand;
	private static final int LBFS_CHUNK_HASHES_PER_RTT = 1024;

	//
	// Snapshots
	//
	private long speedDataBitspersec;
	private long speedPackBitspersec;
	private static final long SNAPSHOT_INTERVAL_MILLIS = 30000;
	private static final long SNAPSHOT_INTERVAL_BYTES = 100000000;
	/**
	 * Millis or bytes, according to {@link #snapshotByTime}.
	 */
	private long nextSnapshot;
	private LinkedList<SimuSnapshot> snapshotList = new LinkedList<SimuSnapshot>();

	//
	// Status and live
	//
	/**
	 * The cost of RTT in terms of prediction that gets lost because it is
	 * already on the way to the receiver. Evaluated by looking at TCP seq at
	 * the receiver when the lookup succeed, and then adding the bytes that
	 * could be in the pipe during RTT.
	 */
	private long rttSingleCostBytes;
	private long rttTotalCostBytes;
	/**
	 * Required for LBFS only.
	 */
	private long rttMillis;
	private long predicitionOffsetStart;

	//
	// Matching chains
	//
	/**
	 * Current matching chain length while matching chunks continue. On miss it
	 * is saved in {@link #matchingChains} and reset.
	 */
	private int curMatchingChain;
	/**
	 * Count of matching chains by their length, meaning that key is the length
	 * and count is the number of times such a match was found.
	 */
	private MapCounter<Integer> matchingChains = new MapCounter<Integer>();

	public SimuResult(int predictionLength, int senderAttempts,
			long speedBitspersecond, long speedPackBitspersec, int rttMillis) {
		this(predictionLength, senderAttempts, speedBitspersecond,
				speedPackBitspersec, rttMillis, true, SNAPSHOT_INTERVAL_MILLIS);
	}

	/**
	 * @param snapshotByTime
	 *            True for snapshots by time or false for snapshots by bytes.
	 * @param snapshotInterval
	 *            Bytes or millis.
	 */
	public SimuResult(int predictionLength, int senderAttempts,
			long speedBitspersecond, long speedPackBitspersec, int rttMillis,
			boolean snapshotByTime, long snapshotInterval) {
		this.predictionLength = predictionLength;
		this.senderAttempts = senderAttempts;
		this.startTime = System.currentTimeMillis();
		this.speedPackBitspersec = speedPackBitspersec;
		this.speedDataBitspersec = speedBitspersecond;
		this.rttSingleCostBytes = (speedDataBitspersec <= 0 || rttMillis <= 0) ? 0
				: (speedDataBitspersec * rttMillis / 1000 / 8);
		this.rttMillis = rttMillis;
		this.snapshotByTime = snapshotByTime;
		this.snapshotInterval = snapshotInterval == 0 ? snapshotByTime ? SNAPSHOT_INTERVAL_MILLIS
				: SNAPSHOT_INTERVAL_BYTES
				: snapshotInterval;
		this.nextSnapshot = snapshotInterval;
	}

	public DisplayTable webGuiResults() {
		DisplayTable table = new DisplayTable();

		long totalBytes = this.sentBytes + this.savedBytes;
		table.addField("Sent bytes<br>Receiver SHA-1 bytes", this.sentBytes,
				"Bytes sent as real data");
		table.addField(null, String.format("%.2f %%", (float) this.sentBytes
				* 100 / totalBytes), null);
		table.addField("Saved bytes", this.savedBytes,
				"Number of bytes saved thanks to redundancy");
		table.addField(null, String.format("%.2f %%", (float) this.savedBytes
				* 100 / totalBytes), null);
		table.addField("Saved chunks", this.savedChunks,
				"Number of chunks saved thanks to redundancy");

		//
		// Chain lookup
		//
		int receiverChainLookup = receiverChainLookupFail
				+ receiverChainLookupSuccess;
		table.addField(null, null, null);
		table.addField("Total chunks", this.chunkCount,
				"Total number of chunks");
		table.addField("Chain lookup", receiverChainLookup,
				"Lookups in receiver chain store");
		table.addField("&nbsp;&nbsp;&nbsp;Lookup/chunks",
				String.format("%.2f %%", (float) receiverChainLookup * 100
						/ this.chunkCount), null);
		table.addField("&nbsp;&nbsp;&nbsp;Chain lookup fail",
				receiverChainLookupFail,
				"Lookups in receiver chain store that ended with no match");
		table
				.addField(
						"Miss Prediction",
						this.receiverChunkMissed,
						"Prediction sent and missed. Not necessarily a chain break because this could be a normal miss after another miss or no-prediction");
		table
				.addField(
						"&nbsp;&nbsp;&nbsp;Immed. lookup success",
						this.receiverChainLookupSuccessImmed,
						"Receiver sent a prediction that failed, got a single chunk and found a new chain immediately");
		table.addField(
				"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Immed. success mid.",
				this.receiverChainLookupSuccessImmed, "Not in chain start");
		table
				.addField(
						"&nbsp;&nbsp;&nbsp;Immed. lookup fail",
						this.receiverChainLookupFailImmed,
						"Receiver sent a prediction that failed, got a single chunk and looked for a new chain immediately but failed to find such chain");

		//
		// Work done
		//
		table.addField(null, null, null);
		long senderSha1 = this.senderSha1SuccessBytes
				+ this.senderSha1FailBytes / 256;
		table.addField("Sender sha-1 bytes", senderSha1,
				"Bytes signed with sha-1 by the sender");
		table.addField(null, String.format("%.2f %%", (float) senderSha1 * 100
				/ totalBytes), null);
		table.addField("Receiver sha-1 bytes", this.sentBytes,
				"Bytes signed with sha-1 by the receiver");
		table.addField(null, String.format("%.2f %%", (float) sentBytes * 100
				/ totalBytes), null);

		return table;
	}

	/**
	 * New file starts now. For LBFS only.
	 */
	public void addFile() {
		lbfsChunkHahshesInHand = LBFS_CHUNK_HASHES_PER_RTT;
		lbfsRttTime += rttMillis;
	}

	/**
	 * @param length
	 *            Length of chunk in bytes.
	 * @param lbfsChunkKnown
	 *            True the chunk is known so LBFS could make a use of it.
	 */
	public void addSentChunk(int length, boolean lbfsChunkKnown) {
		this.chunkCount++;

		closeMatchingChain();

		// Update the counter
		this.sentBytes += length;
		this.sentChunks++;

		// LBFS
		if (lbfsChunkKnown)
			lbfsSavedBytes += length;
		else
			lbfsSentBytes += length;

		lbfsChunkHahshesInHand--;
		if (lbfsChunkHahshesInHand == 0) {
			lbfsRttTime += rttMillis;
			lbfsChunkHahshesInHand = LBFS_CHUNK_HASHES_PER_RTT;
		}

		handleSnapshot();
	}

	public void addSavedChunk(int length) {
		this.chunkCount++;

		curMatchingChain++;

		// Sender's relative TCP sequence before this data
		long tcpSeqBefore = this.savedBytes + this.sentBytes;

		// LBFS
		lbfsSavedBytes += length;
		lbfsChunkHahshesInHand--;
		if (lbfsChunkHahshesInHand == 0) {
			lbfsRttTime += rttMillis;
			lbfsChunkHahshesInHand = LBFS_CHUNK_HASHES_PER_RTT;
		}

		// Check if RTT cost has any effect here
		if (tcpSeqBefore < this.predicitionOffsetStart) {
			long costLeft = this.predicitionOffsetStart - tcpSeqBefore;
			if (costLeft >= length) {
				// The entire chunk is already on wire
				this.sentBytes += length;
				this.sentChunks++;
				this.rttTotalCostBytes += length;
				handleSnapshot();
				return;
			}

			// Partial waste
			this.savedChunks++;
			this.savedBytes += (length - costLeft);
			this.rttTotalCostBytes += costLeft;
			handleSnapshot();
			return;
		}

		// Update the counter
		this.savedBytes += length;

		this.savedChunks++;

		handleSnapshot();
	}

	private void handleSnapshot() {
		// Check if there is a need for time statistics
		if (speedDataBitspersec <= 0)
			return;

		// Calculate time
		long timeDataMillis = this.sentBytes * 8 * 1000 / speedDataBitspersec;
		long timePackMillis = this.savedBytes * 8 * 1000 / speedPackBitspersec;
		long timeMillis = timeDataMillis + timePackMillis;

		// Check if it is time for a snapshot
		if (this.snapshotByTime) {
			if (timeMillis < nextSnapshot)
				return;
		} else {
			if (this.sentBytes + this.savedBytes < nextSnapshot)
				return;
		}

		nextSnapshot += this.snapshotInterval;

		SimuSnapshot snapshot = new SimuSnapshot();
		snapshot.timeMillis = timeMillis;
		snapshot.sentBytes = this.sentBytes;
		snapshot.savedBytes = this.savedBytes;
		snapshot.rttCostBytes = this.rttTotalCostBytes;
		snapshot.savedChunks = this.savedChunks;
		snapshot.sentChunks = this.sentChunks;
		this.snapshotList.add(snapshot);

		//
		// LBFS
		//
		if (!snapshotByTime) {
			timeDataMillis = this.lbfsSentBytes * 8 * 1000
					/ speedDataBitspersec;
			timePackMillis = this.lbfsSavedBytes * 8 * 1000
					/ speedPackBitspersec;
			timeMillis = lbfsRttTime + timeDataMillis + timePackMillis;
			snapshot.lbfsTimeMillis = timeMillis;
		}
	}

	public long getSavedBytes() {
		return this.savedBytes;
	}

	public DisplayTable webGuiSnapshots() {
		DisplayTable table = new DisplayTable();

		if (snapshotByTime) {
			table.addCol("Time (mSec)", "Time since start", true);
		} else {
			table.addCol("Bytes", "Total bytes since start", true);
			table.addCol("Time<br>Diff (mSec)",
					"Time passed since previous snapshot", true);
			table.addCol("LBFS Time<br>diff (mSec)",
					"LBFS time passed since previous snapshot", true);
		}
		table.addCol("Sent (bps)", "Bytes sent on the wire", false);
		table.addCol("PACK (bps)", "Bytes saved with PACK", false);
		table.addCol("RTT Cost (bps)",
				"Bytes sent because of RTT and bytes on the wire", false);
		table.addCol("Sent chunks", "Complete chunks sent on the wire", false);
		table.addCol("PACK chunks", "Chunks sent with PACK. Also partial",
				false);

		SimuSnapshot prevSnapshot = null;
		for (SimuSnapshot curSnapshot : snapshotList) {
			table.addRow(null);
			long intervalMillis = curSnapshot.timeMillis
					- (prevSnapshot == null ? 0 : prevSnapshot.timeMillis);
			if (snapshotByTime) {
				table.addCell(curSnapshot.timeMillis);
			} else {
				table.addCell(curSnapshot.sentBytes + curSnapshot.savedBytes);
				table
						.addCell((curSnapshot.timeMillis - (prevSnapshot == null ? 0
								: prevSnapshot.timeMillis)));
				table
						.addCell((curSnapshot.lbfsTimeMillis - (prevSnapshot == null ? 0
								: prevSnapshot.lbfsTimeMillis)));
			}
			table.addCell((curSnapshot.sentBytes - (prevSnapshot == null ? 0
					: prevSnapshot.sentBytes))
					* 8 * 1000 / intervalMillis);
			table.addCell((curSnapshot.savedBytes - (prevSnapshot == null ? 0
					: prevSnapshot.savedBytes))
					* 8 * 1000 / intervalMillis);
			table.addCell((curSnapshot.rttCostBytes - (prevSnapshot == null ? 0
					: prevSnapshot.rttCostBytes))
					* 8 * 1000 / intervalMillis);
			table.addCell(curSnapshot.sentChunks
					- (prevSnapshot == null ? 0 : prevSnapshot.sentChunks));
			table.addCell(curSnapshot.savedChunks
					- (prevSnapshot == null ? 0 : prevSnapshot.savedChunks));

			prevSnapshot = curSnapshot;
		}

		return table;
	}

	private class SimuSnapshot {
		public long timeMillis;
		/**
		 * When snapshot by bytes this number is the time passed in LBFS.
		 */
		public long lbfsTimeMillis;
		public long sentBytes;
		public long savedBytes;
		public int sentChunks;
		public int savedChunks;
		public long rttCostBytes;
	}

	/**
	 * Must be called after the current chunk that will cause the successful
	 * lookup is already transmitted. Then the cost of RTT is calculated later
	 * by considering these values.
	 * 
	 * @param immeidate
	 * @param immediateMiddle
	 */
	public void addReceiverChainLookupSuccess(boolean immeidate,
			boolean immediateMiddle) {
		closeMatchingChain();
		curMatchingChain++;

		this.predicitionOffsetStart = this.sentBytes + this.savedBytes
				+ rttSingleCostBytes;
		this.receiverChainLookupSuccess++;
		if (immeidate) {
			this.receiverChainLookupSuccessImmed++;
			if (immediateMiddle)
				this.receiverChainLookupSuccessImmedMid++;
		}
	}

	public void closeMatchingChain() {
		if (curMatchingChain == 0)
			return;

		// Save the last matching chain length
		matchingChains.inc(curMatchingChain);
		// Reset
		curMatchingChain = 0;
	}

	public DisplayTable webGuiMatchingChains() {
		return matchingChains.webGuiTable("Chain length",
				"Length of matched chain on the receiver side", null, "Times",
				"Times this kind of length was matched");
	}
}
