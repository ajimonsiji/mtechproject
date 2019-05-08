#include <errno.h>
#include <assert.h>
#include <stdio.h>
#include <sys/socket.h>
#include <stdlib.h>
#include "nfqueue.h"

/**
 * The Netlink socket timeout. This determines how long
 * until we detect a call to breakLoop(). Higher values
 * will require more CPU. Value is in uSeconds.
 */
#define SOCKET_TIMEOUT 250000

/**
 * The NFQueue callback function. Find the associated object, and
 * call it's packet handler method.
 */
int NFQueue::queueCallback(nfq_q_handle *queueHandle,
                      struct nfgenmsg *msg,
                      nfq_data *pkt,
                      void *queueObj)
{
    NFQueue* pQueue = (NFQueue*)queueObj;
    assert(pQueue != NULL);
    if (pQueue != NULL)
    {
        return pQueue->onPacketReceived(queueHandle, msg, pkt);
    }
    else
        return -1;
}

/**
 * The Netfilter_Queue main loop: open the queue, then loop to dispatch
 * packets forever.
 *
 * @return  0 if all went well, a negative value indicating the
 * error otherwise.
 * 
 */
int NFQueue::loop()
{
    m_stopped = false;

    struct nfq_handle *nfqHandle;
    if (!(nfqHandle = nfq_open()))
    {
        return -1;
    }

    // Unbind and rebind the handler, then create the queue. Not sure
    // why we need to unbind, but I'm getting a 'File exists' error
    // otherwise. I got the tip from
    // http://gicl.cs.drexel.edu/people/tjkopena/wiki/pmwiki.php?n=SWAT.NetfilterQueueNotes
    struct nfq_q_handle *queueHandle;

    //Amir: unbind ignoring the result. Done based on comment:
    //http://www.spinics.net/lists/netfilter/msg42063.html
    nfq_unbind_pf(nfqHandle, AF_INET);

    
    if ((nfq_bind_pf(nfqHandle, AF_INET) < 0) ||
        (!(queueHandle =
           nfq_create_queue(nfqHandle, m_queueNum, &queueCallback, this))))
    {
        nfq_close(nfqHandle);
        return -2;
    }

    u_int32_t realRange = (m_copyRange < NFQ_PACKET_MAX_SIZE) ?
        m_copyRange : NFQ_PACKET_MAX_SIZE;

    if (nfq_set_mode(queueHandle, m_copyMode, realRange) < 0)
    {
        nfq_destroy_queue(queueHandle);
        nfq_close(nfqHandle);
        return -3;
    }

    //Eyal: use the queue length, after it was fixed in new kernel
    u_int32_t queueLen = 1024 * 10;
    if (nfq_set_queue_maxlen(queueHandle, queueLen) < 0)
    {
        nfq_destroy_queue(queueHandle);
        nfq_close(nfqHandle);
        return -7;
    }

    struct nfnl_handle *netlinkHandle;
    int netlinkSocket;
    char buf[NFQ_PACKET_MAX_SIZE];

    netlinkHandle = nfq_nfnlh(nfqHandle);
    netlinkSocket = nfnl_fd(netlinkHandle);

    struct timeval tv;
    tv.tv_sec = SOCKET_TIMEOUT / 1000000;
    tv.tv_usec = SOCKET_TIMEOUT % 1000000;
    setsockopt(netlinkSocket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    //Update the socket's receive buffer size
//    printf("Attempting to update socket receive buffer size %d\n", m_recvBuffSize);
    setsockopt(netlinkSocket, SOL_SOCKET, SO_RCVBUF, (char*)&m_recvBuffSize, sizeof(m_recvBuffSize));    

    //Verify allocated size
    u_int32_t allocatedBuff;
    socklen_t size = sizeof(allocatedBuff);
    getsockopt(netlinkSocket, SOL_SOCKET, SO_RCVBUF, (char*)&allocatedBuff, &size);    
//    printf("Allocated socket receive buffer size %d\n", allocatedBuff);

    //Verify that we have enough memory, otherwise exit. 
    if(allocatedBuff < m_recvBuffSize)
    {
        printf("!!! Error unable to allocate sufficient buffer size !!! \n");
        printf("!!! Need to update the net.core.rmem_max configuration in /etc/sysctl.conf !!!\n");
        return -5;
    }
    
    int rcvCount;
    while (!m_stopped)
    {
        rcvCount = recv(netlinkSocket, buf, sizeof(buf), 0);
        if (rcvCount > 0)
        {
            nfq_handle_packet(nfqHandle, buf, rcvCount);
        }
        else if (errno != EAGAIN)
        {
            printf("Error while reading netlink socket: %d\n", errno);
            break;
        }
    }

    if(m_stopped)
    {
        //Only attempt to cleanup queue when exiting due to an application
        //request otherwise the call will block indefintley due to a bug
        // in netfilter queue. 
        nfq_destroy_queue(queueHandle);
        nfq_close(nfqHandle);

        return 0;
    }
    else
    {
        //unexpected end of loop - exit with an error code
        return -6;
    }
    
}
