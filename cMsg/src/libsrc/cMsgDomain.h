/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 * Description:
 *      Header for cMsg routines
 *
 *----------------------------------------------------------------------------*/
 
/**
 * @file
 * This is the header file for the cMsg domain implementation of cMsg.
 */
 
#ifndef __cMsgDomain_h
#define __cMsgDomain_h

#ifndef VXWORKS
#include <inttypes.h>
#endif
#include <signal.h>

#include "cMsgPrivate.h"
#include "rwlock.h"

#ifdef	__cplusplus
extern "C" {
#endif

/** Maximum number of subscriptions per client connection. */
#define CMSG_MAX_SUBSCRIBE 40
/** Maximum number of simultaneous subscribeAndGets per client connection. */
#define CMSG_MAX_SUBSCRIBE_AND_GET 20
/** Maximum number of simultaneous sendAndGets per client connection. */
#define CMSG_MAX_SEND_AND_GET 20
/** Maximum number of callbacks per subscription. */
#define CMSG_MAX_CALLBACK 20

/**
 * This structure is used to synchronize threads waiting to failover (are
 * calling send or subscribe or something) and the thread which detects
 * the need to failover (keepalive thread).
 */
typedef struct countDownLatch_t {
  int count;   /**< Number of calls to "countDown" before releasing callers of "await". */
  int waiters; /**< Number of current waiters (callers of "await"). */
  pthread_mutex_t mutex;  /**< Mutex used to change count. */
  pthread_cond_t  countCond;   /**< Condition variable used for callers of "await" to wait. */
  pthread_cond_t  notifyCond;  /**< Condition variable used for caller of "countDown" to wait. */
} countDownLatch;

/**
 * This structure is used to store monitoring data for a single connection to server.
 */
typedef struct monitorData_t {
  int subAndGets;           /**< Number of subscribeAndGets currently active. */
  int sendAndGets;          /**< Number of sendAndGets currently active. */
  uint64_t numTcpSends;     /**< Number of tcp sends done. */
  uint64_t numUdpSends;     /**< Number of udp sends done. */
  uint64_t numSyncSends;    /**< Number of syncSends done. */
  uint64_t numSubAndGets;   /**< Number of subscribeAndGets done. */
  uint64_t numSendAndGets;  /**< Number of sendAndGets done. */
  uint64_t numSubscribes;   /**< Number of subscribes done. */
  uint64_t numUnsubscribes; /**< Number of unsubscribes done. */
} monitorData;


/** This structure represents a single subscription's callback. */
typedef struct subscribeCbInfo_t {
  int               active;   /**< Boolean telling if this callback is active. */
  int               messages; /**< Number of messages in list. */
  int               threads;  /**< Number of supplemental threads to run callback if
                               *   config allows parallelizing (mustSerialize = 0). */
  int               quit;     /**< Boolean telling thread to end. */
  uint64_t          msgCount; /**< Number of messages passed to callback. */
  void             *userArg;  /**< User argument to be passed to the callback. */
  cMsgCallbackFunc *callback; /**< Callback function (or C++ callback class instance) to be called. */
  cMsgMessage_t    *head;     /**< Head of linked list of messages given to callback. */
  cMsgMessage_t    *tail;     /**< Tail of linked list of messages given to callback. */
  subscribeConfig   config;   /**< Subscription configuration info. */
  pthread_t         thread;   /**< Thread running callback. */
  pthread_cond_t    cond;     /**< Condition variable callback thread is waiting on. */
  pthread_mutex_t   mutex;    /**< Mutex callback thread is waiting on. */
} subscribeCbInfo;


/**
 * This structure represents a subscription of a certain subject and type.
 */
typedef struct subscribeInfo_t {
  int  id;             /**< Unique id # corresponding to a unique subject/type pair. */
  int  active;         /**< Boolean telling if this subject/type has an active callback. */
  int  numCallbacks;   /**< Current number of active callbacks. */
  char *subject;       /**< Subject of subscription. */
  char *type;          /**< Type of subscription. */
  char *subjectRegexp; /**< Subject of subscription made into regular expression. */
  char *typeRegexp;    /**< Type of subscription made into regular expression. */
  subscribeCbInfo cbInfo[CMSG_MAX_CALLBACK]; /**< Array of callbacks. */
} subInfo;


/**
 * This structure represents a sendAndGet or subscribeAndGet
 * of a certain subject and type.
 */
typedef struct getInfo_t {
  int  id;       /**< Unique id # corresponding to a unique subject/type pair. */
  int  active;   /**< Boolean telling if this subject/type has an active callback. */
  int  error;    /**< Error code when client woken up with error condition. */
  int  msgIn;    /**< Boolean telling if a message has arrived. (1-y, 0-n) */
  int  quit;     /**< Boolean commanding sendAndGet to end. */
  char *subject; /**< Subject of sendAndGet. */
  char *type;    /**< Type of sendAndGet. */
  cMsgMessage_t *msg;    /**< Message to be passed to the caller. */
  pthread_cond_t  cond;  /**< Condition variable sendAndGet thread is waiting on. */
  pthread_mutex_t mutex; /**< Mutex sendAndGet thread is waiting on. */
} getInfo;


/**
 * This structure contains the components of a given UDL broken down
 * into its consituent parts.
 */
typedef struct parsedUDL_t {
  int   nameServerPort; /**< port of name server. */
  int   valid;          /**< 1 if valid UDL for the cMsg domain, else 0. */
  int   mustBroadcast;  /**< 1 if UDL specifies broadcasting to find server, else 0. */
  int   timeout;        /**< time in seconds to wait for a broadcast response. */
  char *udl;            /**< whole UDL for name server */
  char *udlRemainder;   /**< domain specific part of the UDL. */
  char *subdomain;      /**< subdomain name. */
  char *subRemainder;   /**< subdomain specific part of the UDL. */
  char *password;       /**< password of name server. */
  char *nameServerHost; /**< host of name server. */
} parsedUDL;


/**
 * This structure contains all information concerning a single client
 * connection to this domain.
 */
typedef struct cMsgDomainInfo_t {  
  
  int receiveState;    /**< Boolean telling if messages are being delivered to
                            callbacks (1) or if they are being igmored (0). */
  int gotConnection;   /**< Boolean telling if connection to cMsg server is good. */
  
  int sendSocket;      /**< File descriptor for TCP socket to send messages/requests on. */
  int sendUdpSocket;   /**< File descriptor for UDP socket to send messages on. */
  int receiveSocket;   /**< File descriptor for TCP socket to receive request responses on. */
  int listenSocket;    /**< File descriptor for socket this program listens on for TCP connections. */
  int keepAliveSocket; /**< File descriptor for socket to tell if server is still alive or not. */

  int sendPort;        /**< Port to send messages to. */
  int sendUdpPort;     /**< Port to send messages to with UDP protocol. */
  int listenPort;      /**< Port this program listens on for this domain's TCP connections. */
  
  /* subdomain handler attributes */
  int hasSend;            /**< Does this subdomain implement a send function? (1-y, 0-n) */
  int hasSyncSend;        /**< Does this subdomain implement a syncSend function? (1-y, 0-n) */
  int hasSubscribeAndGet; /**< Does this subdomain implement a subscribeAndGet function? (1-y, 0-n) */
  int hasSendAndGet;      /**< Does this subdomain implement a sendAndGet function? (1-y, 0-n) */
  int hasSubscribe;       /**< Does this subdomain implement a subscribe function? (1-y, 0-n) */
  int hasUnsubscribe;     /**< Does this subdomain implement a unsubscribe function? (1-y, 0-n) */
  int hasShutdown;        /**< Does this subdomain implement a shutdowm function? (1-y, 0-n) */

  char *myHost;       /**< This hostname. */
  char *sendHost;     /**< Host to send messages to. */
  char *serverHost;   /**< Host cMsg name server lives on. */

  char *name;         /**< Name of this user. */
  char *udl;          /**< semicolon separated list of UDLs of cMsg name servers. */
  char *description;  /**< User description. */
  char *password;     /**< User password. */
  
  /** Array of parsedUDL structures for failover purposes obtained from parsing udl. */  
  parsedUDL *failovers;
  int failoverSize;          /**< Size of the failover array. */
  int failoverIndex;         /**< Index into the failover array for the UDL currently being used. */
  int implementFailovers;    /**< Boolean telling if failovers are being used. */
  int resubscribeComplete;   /**< Boolean telling if resubscribe is complete in failover process. */
  int killClientThread;      /**< Boolean telling if client thread receiving messages should be killed. */
  countDownLatch syncLatch;  /**< Latch used to synchronize the failover. */
  
  char *msgBuffer;           /**< Buffer used in socket communication to server. */
  int   msgBufferSize;       /**< Size of buffer (in bytes) used in socket communication to server. */

  pthread_t pendThread;      /**< Listening thread. */
  pthread_t keepAliveThread; /**< Thread sending keep alives to server. */
  pthread_t clientThread[2]; /**< Threads from server connecting to client (created by pendThread). */
  
  /**
   * Read/write lock to prevent connect or disconnect from being
   * run simultaneously with any other function.
   */
  rwLock_t connectLock;
  pthread_mutex_t socketMutex;    /**< Mutex to ensure thread-safety of socket use. */
  pthread_mutex_t syncSendMutex;  /**< Mutex to ensure thread-safety of syncSends. */
  pthread_mutex_t subscribeMutex; /**< Mutex to ensure thread-safety of (un)subscribes. */
  pthread_cond_t  subscribeCond;  /**< Condition variable used for waiting on clogged callback cue. */

  /*  rc domain stuff  */
  int             rcConnectAbort;    /**< Flag used to abort rc client connection to RC Broadcast server. */
  int             rcConnectComplete; /**< Has a special TCP message been sent from RC
                                          server to indicate that connection is conplete?
                                          (1-y, 0-n) */
  pthread_mutex_t rcConnectMutex;    /**< Mutex used for rc domain connect. */
  pthread_cond_t  rcConnectCond;     /**< Condition variable used for rc domain connect. */
  /* ***************** */
  
  /** Data from monitoring client connection. */
  monitorData monData;
  
  /** Array of structures - each of which contain a subscription. */
  subInfo subscribeInfo[CMSG_MAX_SUBSCRIBE];
  
  /** Array of structures - each of which contain a subscribeAndGet. */
  getInfo subscribeAndGetInfo[CMSG_MAX_SUBSCRIBE_AND_GET];
  
  /** Array of structures - each of which contain a sendAndGet. */
  getInfo sendAndGetInfo[CMSG_MAX_SEND_AND_GET];
  
  /** Shutdown handler function. */
  cMsgShutdownHandler *shutdownHandler;
  
  /** Shutdown handler user argument. */
  void *shutdownUserArg;
  
  /** Store signal mask for restoration after disconnect. */
  sigset_t originalMask;
  
  /** Boolean telling if original mask is being stored. */
  int maskStored;
 
} cMsgDomainInfo;


/** This structure (pointer) is passed as an argument to a callback. */
typedef struct cbArg_t {
  uintptr_t domainId;  /**< Domain identifier. */
  int subIndex;        /**< Index into domain structure's subscription array. */
  int cbIndex;         /**< Index into subscription structure's callback array. */
  cMsgDomainInfo *domain;  /**< Pointer to element of domain structure array. */
} cbArg;


/**
 * This structure passes relevant info to each thread spawned by 
 * another thread. Not all the structure's elements are used in
 * each circumstance, but all are combined into 1 struct for
 * convenience.
 */
typedef struct cMsgThreadInfo_t {
  int isRunning;  /**< Boolean to indicate client listening thread is running. (1-y, 0-n) */
  int connfd;     /**< Socket connection's file descriptor. */
  int listenFd;   /**< Listening socket file descriptor. */
  int thd0started;/**< Boolean to indicate client msg receiving thread is running. (1-y, 0-n) */
  int thd1started;/**< Boolean to indicate client keepalive receiving thread is running. (1-y, 0-n) */
  int blocking;   /**< Block in accept (CMSG_BLOCKING) or
                      not (CMSG_NONBLOCKING)? */
  cMsgDomainInfo *domain;  /**< Pointer to element of domain structure array. */
  char *domainType;        /**< String containing domain name (e.g. cmsg, rc, file). */
} cMsgThreadInfo;


/* prototypes */

/* string matching */
char *cMsgStringEscape(const char *s);
int   cMsgStringMatches(char *regexp, const char *s);
int   cMsgRegexpMatches(char *regexp, const char *s);

/* mutexes and read/write locks */
void  cMsgMutexLock(pthread_mutex_t *mutex);
void  cMsgMutexUnlock(pthread_mutex_t *mutex);
void  cMsgConnectReadLock(cMsgDomainInfo *domain);
void  cMsgConnectReadUnlock(cMsgDomainInfo *domain);
void  cMsgConnectWriteLock(cMsgDomainInfo *domain);
void  cMsgConnectWriteUnlock(cMsgDomainInfo *domain);
void  cMsgSocketMutexLock(cMsgDomainInfo *domain);
void  cMsgSocketMutexUnlock(cMsgDomainInfo *domain);
void  cMsgSyncSendMutexLock(cMsgDomainInfo *domain);
void  cMsgSyncSendMutexUnlock(cMsgDomainInfo *domain);
void  cMsgSubscribeMutexLock(cMsgDomainInfo *domain);
void  cMsgSubscribeMutexUnlock(cMsgDomainInfo *domain);
void  cMsgCountDownLatchFree(countDownLatch *latch); 
void  cMsgCountDownLatchInit(countDownLatch *latch, int count);
void  cMsgLatchReset(countDownLatch *latch, int count, const struct timespec *timeout);
int   cMsgLatchCountDown(countDownLatch *latch, const struct timespec *timeout);
int   cMsgLatchAwait(countDownLatch *latch, const struct timespec *timeout);

/* threads */
void *cMsgClientListeningThread(void *arg);
void *cMsgCallbackThread(void *arg);
void *cMsgSupplementalThread(void *arg);

/* initialization and freeing */
void  cMsgDomainInit(cMsgDomainInfo *domain);
void  cMsgDomainClear(cMsgDomainInfo *domain);
void  cMsgDomainFree(cMsgDomainInfo *domain);

/* misc */
int   cMsgCheckString(const char *s);
int   cMsgGetAbsoluteTime(const struct timespec *deltaTime, struct timespec *absTime);
int   sun_setconcurrency(int newLevel);
int   sun_getconcurrency(void);

/* signals */
void  cMsgBlockSignals(cMsgDomainInfo *domain);
void  cMsgRestoreSignals(cMsgDomainInfo *domain);


#ifdef	__cplusplus
}
#endif

#endif
