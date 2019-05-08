package il.ac.technion.eyalzo.pack.pred;

import il.ac.technion.eyalzo.pack.conns.TcpUtils;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Incoming prediction list, also called "Prediction Inbox".
 * <p>
 * Managed in an incremental TCP sequence mode where only predictions for future chunks can be added. Incoming
 * predictions are added, and outgoing packets are compared with the current list to see if they should be dropped
 * because the receiver already has the data.
 */
public class PredInList
{
	private LinkedList<PredInChunk> preds = new LinkedList<PredInChunk>();
	/**
	 * Local TCP sequence of the next expected prediction. New predictions with former sequence will not be added. That
	 * prevents duplicates or overlaps. When zero that rule is skipped.
	 */
	private long nextTcpSeq;

	/**
	 * @param tcpSeq
	 *            TCP sequence of the expected chunk.
	 * @param signature
	 *            Chunk's signature as provided by PRED command.
	 * @param signatureLength
	 *            Number of SHA-1 LSB bytes. Required so it can be compared with the content later.
	 * @param hint
	 *            Not in use.
	 * @param chunkLength
	 *            Chunks's length as provided by PRED command.
	 * @return True if the prediction was added or false if it was not because it points backwards relative to other
	 *         predictions in the inbox.
	 */
	public boolean addPredForward(long tcpSeq, int signature, int signatureLength, byte hint, int chunkLength)
	{
		synchronized (preds)
		{
			// Do not add predictions for chunks that are older than existing predictions
			if (!preds.isEmpty() && nextTcpSeq > 0 && TcpUtils.tcpSequenceBackward(nextTcpSeq, tcpSeq, 0))
				return false;

			// Create item
			PredInChunk predInItem = new PredInChunk(tcpSeq, signature, signatureLength, hint, chunkLength);
			preds.add(predInItem);

			// Update expected TCP sequence
			nextTcpSeq = TcpUtils.tcpSequenceAdd(tcpSeq, chunkLength);

			return true;
		}
	}

	/**
	 * Get the predictions that are relevant for the given TCP packet and cleanup old predictions.
	 * 
	 * @param tcpSeqStart
	 *            Packet's TCP sequence.
	 * @param tcpPayloadSize
	 *            Packet's TCP payload size.
	 * @return Null if no predictions are relevant for this packet. Usually returns a single chunk, but it can be up to
	 *         two since packets are never bigger than chunks.
	 */
	public LinkedList<PredInChunk> getPacketOverlaps(long tcpSeqStart, int tcpPayloadSize)
	{
		LinkedList<PredInChunk> result = null;

		synchronized (preds)
		{
			Iterator<PredInChunk> it = preds.iterator();
			while (it.hasNext())
			{
				PredInChunk curPred = it.next();
				if (curPred.isPacketOverlap(tcpSeqStart, tcpPayloadSize))
				{
					if (result == null)
						result = new LinkedList<PredInChunk>();

					result.add(curPred);
					continue;
				}

				// Check if there is need to continue or cleanup
				long diff = TcpUtils.tcpSequenceDiff(curPred.getTcpSeq(), tcpSeqStart);
				if (diff >= curPred.getLength())
					// Past prediction cleanup
					it.remove();
				else
					// Future prediction so there is no need to continue
					break;

			}
		}

		return result;
	}

	public boolean isEmpty()
	{
		synchronized (preds)
		{
			return preds.isEmpty();
		}
	}

	public int size()
	{
		synchronized (preds)
		{
			return preds.size();
		}
	}

	@Override
	public String toString()
	{
		return String.format("%,d: ", this.nextTcpSeq) + preds.toString();
	}

	/**
	 * 
	 * @param startTcpSeq
	 *            Connection start TCP sequence for more readable print of TCP sequences as relative numbers.
	 * @return Inbox content for each chunk: relative TCP sequence, signature and length.
	 */
	public String toString(long startTcpSeq)
	{
		StringBuffer buffer = new StringBuffer();

		synchronized (preds)
		{
			for (PredInChunk curChunk : preds)
			{
				buffer.append(curChunk.toString(startTcpSeq));
				buffer.append(' ');
			}
		}

		return buffer.toString();
	}

	public void clear()
	{
		synchronized (preds)
		{
			nextTcpSeq = 0;
			preds.clear();
		}
	}

	public long getNextTcpSeq()
	{
		return nextTcpSeq;
	}

	/**
	 * @param lastPredsToSum
	 *            Number of predictions to sum up (from end).
	 * @return Sum of the last predictions in the list. Zero on index error.
	 */
	public long getPredsTotalLen(int lastPredsToSum)
	{
		synchronized (preds)
		{
			int start = preds.size() - lastPredsToSum;
			if (start < 0)
				return 0;

			long result = 0;

			for (int i = preds.size() - 1; i >= start; i--)
				result += preds.get(i).getLength();

			return result;
		}
	}

	/**
	 * Remove all the predictions up to and include the given chunk.
	 */
	public void cleanupUntilChunk(PredInChunk chunk)
	{
		synchronized (preds)
		{
			Iterator<PredInChunk> it = preds.iterator();
			while (it.hasNext())
			{
				PredInChunk curPred = it.next();
				it.remove();
				if (curPred == chunk)
					return;
			}
		}
	}
}
