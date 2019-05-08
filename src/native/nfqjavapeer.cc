#include "nfqjavapeer.h"
#include <iostream>
#include <iomanip>
#include <csignal>
#include <time.h>
#include <assert.h>
#include <errno.h>
#include <string.h>

#include <netinet/in.h>

#include "nfqueue.h"

using namespace std;


#ifndef MIN
#  define MIN(a,b)  ((a) < (b) ? (a) : (b))
#endif

/**
 * An NFQueue java peer object.
 */
class NFQJavaPeer :
    public NFQueue
{
  private:

    /**
     * While inside the event loop, this is the java environment for callback
     * purposes. NULL when not in the loop.
     */
    JNIEnv *m_javaEnv;

    /**
     * While inside the event loop, this is the associated java object.
     * NULL when not in the loop.
     */
    jobject m_javaObject;

    /**
     * While inside the event loop, this is the method ID of the java callback
     * method. NULL when not in the loop.
     */
    jmethodID m_javaCallbackMethod;

  public:
    /**
     * Create an NFQueue java peer object.
     */
    NFQJavaPeer(u_int16_t queueNum, u_int32_t queueLen) :
        NFQueue(queueNum, queueLen),
        m_javaEnv(NULL),
        m_javaObject(NULL),
        m_javaCallbackMethod(NULL)
    {
    }
    
    /**
     * The usual virtual destructor.
     */
    virtual ~NFQJavaPeer() {}

    /**
     * Handle a received packet by forwarding it to the java handler method.
     */
    virtual int onPacketReceived(nfq_q_handle *queueHandle,
                                 struct nfgenmsg *msg,
                                 nfq_data *pkt);

    /**
     * execute the queue event loop, keeping a reference to the java environment
     * and object, so that the java callback can be called.
     */
    int loop(JNIEnv *env, jobject obj);
};

/**
 * execute the queue event loop, keeping a reference to the java environment
 * and object, so that the java callback can be called.
 */
int NFQJavaPeer::loop(JNIEnv *javaEnv, jobject javaObject)
{
    m_javaEnv = javaEnv;
    m_javaObject = javaObject;

    jclass javaClass = javaEnv->GetObjectClass(javaObject);
    assert(javaClass);
    if (javaClass)
    {
        m_javaCallbackMethod =
            javaEnv->GetMethodID(javaClass, "onPacketReceived", "(J)I");
        assert(m_javaCallbackMethod);
    }

    int retCode = NFQueue::loop();

    m_javaCallbackMethod = NULL;
    m_javaObject = NULL;
    m_javaEnv = NULL;

    return retCode;
}

/**
 * Handle a received packet by forwarding it to the java handler method.
 */
int NFQJavaPeer::onPacketReceived(nfq_q_handle *queueHandle,
                                  struct nfgenmsg *msg,
                                  nfq_data *pkt)
{
    
    uint32_t id = 0;
    nfqnl_msg_packet_hdr *header;
    header = nfq_get_msg_packet_hdr(pkt);
    if (!header)
        return (-1);
    
    id = ntohl(header->packet_id);

    assert(m_javaEnv && m_javaObject && m_javaCallbackMethod);

    u_int32_t verdict = NF_DROP;
    if (m_javaEnv && m_javaObject && m_javaCallbackMethod)
    {
        jint retCode =
            m_javaEnv->CallIntMethod(m_javaObject, m_javaCallbackMethod, pkt);
        verdict = (u_int32_t)retCode;
    }

    return nfq_set_verdict(queueHandle, id, verdict, 0, NULL);
}



//
// The JNI peer methods. Forward the calls to the corresponding peer object.
//

/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    newNativeQueue
 * Signature: (S)J
 */
JNIEXPORT jlong JNICALL Java_il_ac_technion_eyalzo_NFQueue_newNativeQueue
  (JNIEnv *env, jobject obj, jshort queueNum, jint queueLen)
{
    NFQJavaPeer* peer = new NFQJavaPeer(queueNum, queueLen);
    return (jlong)peer;
}

/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    deleteNativeQueue
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_il_ac_technion_eyalzo_NFQueue_deleteNativeQueue
    (JNIEnv *env, jobject obj, jlong peerAsLong)
{
    NFQJavaPeer* peer = (NFQJavaPeer*)peerAsLong;
    assert(peer);
    delete peer;
}

/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    nativeBreakLoop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_il_ac_technion_eyalzo_NFQueue_nativeBreakLoop
    (JNIEnv *env, jobject obj, jlong peerAsLong)
{
    NFQJavaPeer* peer = (NFQJavaPeer*)peerAsLong;
    assert(peer);
    peer->breakLoop();
}

/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    nativeLoop
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_il_ac_technion_eyalzo_NFQueue_nativeLoop
  (JNIEnv *env, jobject obj, jlong peerAsLong)
{
    NFQJavaPeer* peer = (NFQJavaPeer*)peerAsLong;
    assert(peer);
    return peer->loop(env, obj);
}


/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    setCopyMode
 * Signature: (JBI)V
 */
JNIEXPORT void JNICALL Java_il_ac_technion_eyalzo_NFQueue_setCopyMode
    (JNIEnv *env, jobject obj, jlong peerAsLong, jbyte mode, jint range)
{
    NFQJavaPeer* peer = (NFQJavaPeer*)peerAsLong;
    assert(peer);
    peer->setCopyMode(mode, range);
}


/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    setRecvBufferSize
 * Signature: (JBI)V
 */
JNIEXPORT void JNICALL Java_il_ac_technion_eyalzo_NFQueue_setRecvBufferSize
    (JNIEnv *env, jobject obj, jlong peerAsLong, jint bufSize)
{
    NFQJavaPeer* peer = (NFQJavaPeer*)peerAsLong;
    assert(peer);
    peer->setRecvBufferSize(bufSize);
}

//
// Some static helper methods. These are not forwarded to the class object.
// Instead, they are handled here. Basically, these are getter methods for
// packet data.
//

/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    getPacketPayload
 * Signature: (J[B)I
 *
 * Get the payload of the packet identified by the given packet handle. Return
 * the number of actual bytes read, and the packet content in the given buffer.
 * in case of error, return a negative value.
 */
JNIEXPORT jint JNICALL Java_il_ac_technion_eyalzo_NFQueue_getPacketPayload
  (JNIEnv *env, jclass cls, jlong pktHandleAsJLong, jbyteArray buffer)
{
    unsigned char* payloadPtr = NULL;

    struct nfq_data* pktHandle = (struct nfq_data*)pktHandleAsJLong;
    assert (pktHandle);
    int retCode = -1;
    if ((pktHandle != NULL) &&
        ((retCode = nfq_get_payload(pktHandle, &payloadPtr)) >= 0))
    {
        jsize javaBufSize = env->GetArrayLength(buffer);
        jsize bytesToCopy = MIN(javaBufSize, retCode);
        env->SetByteArrayRegion(buffer, 0, bytesToCopy,
                                (const jbyte*)payloadPtr);
    }
    return retCode;
}

/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    getLastErrorMsg
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_il_ac_technion_eyalzo_NFQueue_getLastErrorMsg
  (JNIEnv *env, jclass cls)
{
    char* lastErrorMsg = strerror(errno);
    return env->NewStringUTF(lastErrorMsg);
}


/*
 * Class:     il_ac_technion_eyalzo_NFQueue
 * Method:    getNativeVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_il_ac_technion_eyalzo_NFQueue_getNativeVersion
  (JNIEnv *, jclass)
{
    //Return the version of this native.
    return il_ac_technion_eyalzo_NFQueue_NFQ_JAVA_VERSION;
    
}

