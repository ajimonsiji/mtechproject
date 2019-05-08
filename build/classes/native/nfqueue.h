#ifndef NFQUEUE_H
#define NFQUEUE_H

extern "C" {
#include <netinet/in.h>
#include <linux/netfilter.h>  /* Defines verdicts (NF_ACCEPT, etc) */
#include <libnetfilter_queue/libnetfilter_queue.h>
}

/**
 * The queue readbuffer size.
 */
#define NFQ_PACKET_MAX_SIZE 4096


/**
 * Default netlink socket receive buffer size per packet. Each packet is 4K long
 * plus an additional overhead of 1K per packet. 
 */
#define NFQ_RECEIVE_BUFFER_SIZE_PER_PACKET (4096 + 1024)

/**
 * A NetFilter Queue class.
 */
class NFQueue
{
  private:

    /**
     * A flag indicating that the loop was terminated.
     */
    bool m_stopped;

    /**
     * The queue number this object is associated with.
     */
    u_int16_t m_queueNum;

    /**
     * The max queue length.
     */
    u_int32_t m_queueLen;

    /**
     * The copy mode to set in the next loop.
     */
    u_int8_t m_copyMode;

    /**
     * The copy range to set in the next loop.
     */
    u_int32_t m_copyRange;

    /**
     * The socket's receive buffer size
     */
    u_int32_t m_recvBuffSize;

    
    /**
     * The NFQueue callback function. Find the associated object, and
     * call it's packet handler method.
     */
    static int queueCallback(nfq_q_handle *queueHandle,
                             struct nfgenmsg *msg,
                             nfq_data *pkt,
                             void *queueObj);

  public:

    /**
     * Create a new NFQueue object, associated with the given queue.
     */
    NFQueue(u_int16_t queueNum, u_int32_t queueLen) :
        m_stopped(false),
        m_queueNum(queueNum),
        m_queueLen(queueLen),
        m_copyMode(NFQNL_COPY_PACKET),
        m_copyRange(NFQ_PACKET_MAX_SIZE),
        m_recvBuffSize (queueLen * NFQ_RECEIVE_BUFFER_SIZE_PER_PACKET)
    {
    }

    /**
     * The usual virtual destructor.
     */
    virtual ~NFQueue() {}

    /**
     * A handler for received packets.
     */
    virtual int onPacketReceived(nfq_q_handle *queueHandle,
                                 struct nfgenmsg *msg,
                                 nfq_data *pkt) = 0;

    /**
     * The Netfilter_Queue main loop: open the queue, then loop to dispatch
     * packets forever.
     *
     * @return  0 if all went well, a negative value indicating the
     * error otherwise.
     * 
     */
    int loop();

    /**
     * Break out of a running loop.
     */
    void breakLoop()
    {
        m_stopped = true;
    }

    /**
     * Set the packet copy mode for the NEXT call to loop().
     * @param mode The copy mode. Can be one of NFQNL_COPY_NONE,
     * NFQNL_COPY_META, or NFQNL_COPY_PACKET.
     * @param range For NFQNL_COPY_PACKET, this is tha maximum number of bytes
     * to copy.
     */
    void setCopyMode(u_int8_t mode, u_int32_t range)
    {
        m_copyMode = mode;
        m_copyRange = range;
    }


    /**
     * Update the netlink socket's receive buffer size
     * @param bufSize Size of buffer to allocate
     */
    void setRecvBufferSize(u_int32_t bufSize)
    {
        m_recvBuffSize = bufSize;
    }
    
};

#endif // NFQUEUE_H
