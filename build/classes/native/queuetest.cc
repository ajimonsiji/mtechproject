#include <iostream>
#include <iomanip>
#include <csignal>
#include <time.h>
#include <assert.h>

#include <netinet/in.h>

#include "nfqueue.h"

using namespace std;

/**
 * An NFQueue test.
 */
class NFQueueTest :
    public NFQueue
{
  public:
    /**
     * Create an NFQueue test object.
     */
    NFQueueTest(u_int16_t queueNum) :
        NFQueue(queueNum, 1024)
    {
    }
    
    /**
     * The usual virtual destructor.
     */
    virtual ~NFQueueTest() {}

    /**
     * Handle a received packet
     */
    virtual int onPacketReceived(nfq_q_handle *queueHandle,
                                 struct nfgenmsg *msg,
                                 nfq_data *pkt);
};

/**
 * A handler for received packets.
 */
int NFQueueTest::onPacketReceived(nfq_q_handle *queueHandle,
                                  struct nfgenmsg *msg,
                                  nfq_data *pkt)
{
    uint32_t id = 0;
    nfqnl_msg_packet_hdr *header;
        
    cout << "pkt recvd: ";
        
    if ((header = nfq_get_msg_packet_hdr(pkt))) {
        id = ntohl(header->packet_id);
        cout << "id " << id << "; hw_protocol " << setfill('0') << setw(4) <<
            hex << ntohs(header->hw_protocol) << "; hook " << ('0'+header->hook)
             << " ; ";
    }
    
    // The HW address is only fetchable at certain hook points
    nfqnl_msg_packet_hw *macAddr = nfq_get_packet_hw(pkt);
    if (macAddr) {
        cout << "mac len " << ntohs(macAddr->hw_addrlen) << " addr ";
        for (int i = 0; i < 8; i++) {
            cout << setfill('0') << setw(2) << hex << macAddr->hw_addr;
        }
        // end if macAddr
    } else {
        cout << "no MAC addr";
    }
        
    timeval tv;
    if (!nfq_get_timestamp(pkt, &tv)) {
        cout << "; tstamp " << tv.tv_sec << "." << tv.tv_usec;
    } else {
        cout << "; no tstamp";
    }
        
    cout << "; mark " << nfq_get_nfmark(pkt);
    
    // Note that you can also get the physical devices
    cout << "; indev " << nfq_get_indev(pkt);
    cout << "; outdev " << nfq_get_outdev(pkt);
        
    cout << endl;
    
    // Print the payload; in copy meta mode, only headers will be included;
    // in copy packet mode, whole packet will be returned.
    unsigned char *pktData;
    int len = nfq_get_payload(pkt, &pktData);
    if (len) {
        cout << "data[" << len << "]: '";
        for (int i = 0; i < len; i++) {
            if (isprint(pktData[i]))
                cout << pktData[i];
            else cout << " ";
        }
        cout << "'" << endl;
        // end data found
    }
    
    // For this program we'll always accept the packet...
    return nfq_set_verdict(queueHandle, id, NF_ACCEPT, 0, NULL);
}

NFQueueTest queue(0);

/**
 * SIGTERM signal handler.
 */
void onSigTerm(int sig)
{
    cout << "term signal cought, stopping...\n";
    queue.breakLoop();
}

int main(int argc, char **argv)
{
    signal(SIGTERM, onSigTerm);
    signal(SIGINT, onSigTerm);
    int retCode = queue.loop();
    cout << "nfqLoop returned" << retCode << '\n';
}
