package il.ac.technion.eyalzo;
/**
 * A listener to queue events.
 */
public interface NFQueueListener
{
    /**
     * A notification that a packet was received.
     * 
     * @param payload The packet payload data.
     * @param payloadLength The payload actual length. the payload buffer is 
     * reused between calls. So, this is the length of the actual data. 
     * @return This method should return a verdict for the packet.
     */
    public NFQueue.Verdict onPacketReceived(byte[] payload, int payloadLength);

    /**
     * A notification that an error occurred when receiving a packet.
     * @param errMsg An error message describing the error.
    */
    public void onPacketReceiveError(String errMsg);
}
