/*----------------------------------------------------------------------------*
 *                                                                            *
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 7-Apr-2006, Jefferson Lab                                    *
 *                                                                            *
 *    Authors: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 *
 * Description:
 *
 *  Implements the rc or runcontrol domain used by CODA components  
 *  (but not the server). The runcontrol server side uses 2 domains
 *  to communicate with CODA components: rcTcp and rcUdp domains.
 *
 *----------------------------------------------------------------------------*/

/**
 * @file
 * This file contains the rc domain implementation of the cMsg user API.
 * This a messaging system programmed by the Data Acquisition Group at Jefferson
 * Lab. The rc domain allows CODA components to communicate with runcontrol.
 * The server which communicates with this cMsg user must use 2 domains, the 
 * rcTcp and rcUdp domains. The rc domain relies heavily on the cMsg domain
 * code which it uses as a library. It mainly relies on that code to implement
 * subscriptions/callbacks.
 */  
 

#ifdef VXWORKS
#include <vxWorks.h>
#include <sysLib.h>
#include <sockLib.h>
#include <hostLib.h>
#else
#include <strings.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <ctype.h>

#include "cMsgPrivate.h"
#include "cMsg.h"
#include "cMsgNetwork.h"
#include "rwlock.h"
#include "regex.h"
#include "cMsgDomain.h"



/**
 * Structure for arg to be passed to receiver/broadcast threads.
 * Allows data to flow back and forth with these threads.
 */
typedef struct thdArg_t {
    int sockfd;
    socklen_t len;
    unsigned short port;
    struct sockaddr_in addr;
    struct sockaddr_in *paddr;
    int   bufferLen;
    char *expid;
    char *buffer;
} thdArg;

/* built-in limits */
/** Number of seconds to wait for cMsgClientListeningThread threads to start. */
#define WAIT_FOR_THREADS 10

/* global variables */
/** Function in cMsgServer.c which implements the network listening thread of a client. */
void *rcClientListeningThread(void *arg);

/* local variables */
/** Pthread mutex to protect one-time initialization and the local generation of unique numbers. */
static pthread_mutex_t generalMutex = PTHREAD_MUTEX_INITIALIZER;

/** Id number which uniquely defines a subject/type pair. */
static int subjectTypeId = 1;

/** Size of buffer in bytes for sending messages. */
static int initialMsgBufferSize = 1500;

/**
 * Read/write lock to prevent connect or disconnect from being
 * run simultaneously with any other function.
 */
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond   = PTHREAD_COND_INITIALIZER;


/* Local prototypes */
static void  staticMutexLock(void);
static void  staticMutexUnlock(void);
static void *receiverThd(void *arg);
static void *broadcastThd(void *arg);
static void  defaultShutdownHandler(void *userArg);
static int   parseUDL(const char *UDLR, char **host,
                      unsigned short *port, char **expid,
                      int  *broadcastTO, int *connectTO, char **junk);
                      
/* Prototypes of the 14 functions which implement the standard tasks in cMsg. */
int   cmsg_rc_connect           (const char *myUDL, const char *myName,
                                 const char *myDescription,
                                 const char *UDLremainder, void **domainId);
int   cmsg_rc_send              (void *domainId, const void *msg);
int   cmsg_rc_syncSend          (void *domainId, const void *msg, const struct timespec *timeout, int *response);
int   cmsg_rc_flush             (void *domainId, const struct timespec *timeout);
int   cmsg_rc_subscribe         (void *domainId, const char *subject, const char *type,
                                 cMsgCallbackFunc *callback, void *userArg,
                                 cMsgSubscribeConfig *config, void **handle);
int   cmsg_rc_unsubscribe       (void *domainId, void *handle);
int   cmsg_rc_subscribeAndGet   (void *domainId, const char *subject, const char *type,
                                 const struct timespec *timeout, void **replyMsg);
int   cmsg_rc_sendAndGet        (void *domainId, const void *sendMsg,
                                 const struct timespec *timeout, void **replyMsg);
int   cmsg_rc_monitor           (void *domainId, const char *command, void **replyMsg);
int   cmsg_rc_start             (void *domainId);
int   cmsg_rc_stop              (void *domainId);
int   cmsg_rc_disconnect        (void **domainId);
int   cmsg_rc_shutdownClients   (void *domainId, const char *client, int flag);
int   cmsg_rc_shutdownServers   (void *domainId, const char *server, int flag);
int   cmsg_rc_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg);

/** List of the functions which implement the standard cMsg tasks in the cMsg domain. */
static domainFunctions functions = {cmsg_rc_connect, cmsg_rc_send,
                                    cmsg_rc_syncSend, cmsg_rc_flush,
                                    cmsg_rc_subscribe, cmsg_rc_unsubscribe,
                                    cmsg_rc_subscribeAndGet, cmsg_rc_sendAndGet,
                                    cmsg_rc_monitor, cmsg_rc_start,
                                    cmsg_rc_stop, cmsg_rc_disconnect,
                                    cmsg_rc_shutdownClients, cmsg_rc_shutdownServers,
                                    cmsg_rc_setShutdownHandler};

/* CC domain type */
domainTypeInfo rcDomainTypeInfo = {
  "rc",
  &functions
};

/*-------------------------------------------------------------------*/


/**
 * This routine is called once to connect to an RC domain. It is called
 * by the user through top-level cMsg API, "cMsgConnect()".
 * The argument "myUDL" is the Universal Domain Locator used to uniquely
 * identify the RC server to connect to.
 * It has the form:<p>
 *       <b>cMsg:rc://host:port/</b><p>
 * where the first "cMsg:" is optional. Both the host and port are also
 * optional. If a host is NOT specified, a broadcast on the local subnet
 * is used to locate the server. If the port is omitted, a default port
 * (RC_BROADCAST_PORT) is used.
 *
 * If successful, this routine fills the argument "domainId", which identifies
 * the connection uniquely and is required as an argument by many other routines.
 *
 * @param myUDL the Universal Domain Locator used to uniquely identify the rc
 *        server to connect to
 * @param myName name of this client
 * @param myDescription description of this client
 * @param UDLremainder partially parsed (initial cMsg:rc:// stripped off)
 *                     UDL which gets passed down from the API level (cMsgConnect())
 * @param domainId pointer to integer which gets filled with a unique id referring
 *        to this connection.
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if the EXPID is not defined
 * @returns CMSG_BAD_FORMAT if the UDL is malformed
 * @returns CMSG_ABORT if RC Broadcast server aborts connection attempt
 * @returns CMSG_OUT_OF_RANGE if the port specified in the UDL is out-of-range
 * @returns CMSG_OUT_OF_MEMORY if the allocating memory for domain id or
 *                             message buffer failed
 * @returns CMSG_SOCKET_ERROR if the listening thread finds all the ports it tries
 *                            to listen on are busy, or socket options could not be set.
 *                            If udp socket to server could not be created or connect failed.
 * @returns CMSG_NETWORK_ERROR if no connection to the rc server can be made,
 *                             or a communication error with server occurs.
 */   
int cmsg_rc_connect(const char *myUDL, const char *myName, const char *myDescription,
                    const char *UDLremainder, void **domainId) {
  
    unsigned short serverPort;
    char  *serverHost, *expid=NULL, buffer[1024];
    int    err, status, len, expidLen, nameLen;
    int    i, outGoing[4], broadcastTO=0, connectTO=0;
    char   temp[CMSG_MAXHOSTNAMELEN];
    char  *portEnvVariable=NULL;
    unsigned short startingPort;
    cMsgDomainInfo *domain;
    cMsgThreadInfo *threadArg;
    int    hz, num_try, try_max;
    struct timespec waitForThread;
    
    pthread_t rThread, bThread;
    thdArg    rArg,    bArg;
    
    struct timespec wait, time;
    struct sockaddr_in servaddr, addr;
    int    gotResponse=0;
    const int on=1, size=CMSG_BIGSOCKBUFSIZE; /* bytes */
        
       
    /* clear array */
    memset((void *)buffer, 0, 1024);
    
    /* parse the UDLRemainder to get the host and port but ignore everything else */
    err = parseUDL(UDLremainder, &serverHost, &serverPort,
                   &expid, &broadcastTO, &connectTO, NULL);
    if (err != CMSG_OK) {
        return(err);
    }
    
    /*
     * The EXPID is obtained from the UDL, but if it's not defined there
     * then use the environmental variable EXPID 's value.
     */
    if (expid == NULL) {
        expid = getenv("EXPID");
        if (expid != NULL) {
            expid = (char *)strdup(expid);
        }
        else {
            /* if expid not defined anywhere, return error */
            return(CMSG_ERROR);
        }
    }

    /* allocate struct to hold connection info */
    domain = (cMsgDomainInfo *) calloc(1, sizeof(cMsgDomainInfo));
    if (domain == NULL) {
        free(serverHost);
        free(expid);
        return(CMSG_OUT_OF_MEMORY);  
    }
    cMsgDomainInit(domain);  

    /* allocate memory for message-sending buffer */
    domain->msgBuffer     = (char *) malloc(initialMsgBufferSize);
    domain->msgBufferSize = initialMsgBufferSize;
    if (domain->msgBuffer == NULL) {
        cMsgDomainFree(domain);
        free(domain);
        free(serverHost);
        free(expid);
        return(CMSG_OUT_OF_MEMORY);
    }

    /* store our host's name */
    gethostname(temp, CMSG_MAXHOSTNAMELEN);
    domain->myHost = (char *) strdup(temp);

    /* store names, can be changed until server connection established */
    domain->name        = (char *) strdup(myName);
    domain->udl         = (char *) strdup(myUDL);
    domain->description = (char *) strdup(myDescription);

    /*--------------------------------------------------------------------------
     * First find a port on which to receive incoming messages.
     * Do this by trying to open a listening socket at a given
     * port number. If that doesn't work add one to port number
     * and try again.
     * 
     * But before that, define a port number from which to start looking.
     * If CMSG_PORT is defined, it's the starting port number.
     * If CMSG_PORT is NOT defind, start at RC_CLIENT_LISTENING_PORT (6543).
     *-------------------------------------------------------------------------*/
    
    /* pick starting port number */
    if ( (portEnvVariable = getenv("CMSG_RC_CLIENT_PORT")) == NULL ) {
        startingPort = RC_CLIENT_LISTENING_PORT;
        if (cMsgDebug >= CMSG_DEBUG_WARN) {
            fprintf(stderr, "cmsg_rc_connectImpl: cannot find CMSG_PORT env variable, first try port %hu\n", startingPort);
        }
    }
    else {
        i = atoi(portEnvVariable);
        if (i < 1025 || i > 65535) {
            startingPort = RC_CLIENT_LISTENING_PORT;
            if (cMsgDebug >= CMSG_DEBUG_WARN) {
                fprintf(stderr, "cmsg_rc_connect: CMSG_PORT contains a bad port #, first try port %hu\n", startingPort);
            }
        }
        else {
            startingPort = i;
        }
    }
     
    /* get listening port and socket for this application */
    if ( (err = cMsgGetListeningSocket(CMSG_BLOCKING,
                                       startingPort,
                                       &domain->listenPort,
                                       &domain->listenSocket)) != CMSG_OK) {
        cMsgDomainFree(domain);
        free(domain);
        free(serverHost);
        free(expid);
        return(err);
    }
    
/*printf("connect: create listening socket on port %d\n", domain->listenPort );*/

    /* launch pend thread and start listening on receive socket */
    threadArg = (cMsgThreadInfo *) malloc(sizeof(cMsgThreadInfo));
    if (threadArg == NULL) {
        cMsgDomainFree(domain);
        free(domain);
        free(serverHost);
        free(expid);
        return(CMSG_OUT_OF_MEMORY);  
    }
    threadArg->isRunning   = 0;
    threadArg->thd0started = 0;
    threadArg->thd1started = 0;
    threadArg->listenFd    = domain->listenSocket;
    threadArg->blocking    = CMSG_NONBLOCKING;
    threadArg->domain      = domain;
    threadArg->domainType  = strdup("rc");
    
    /* Block SIGPIPE for this and all spawned threads. */
    cMsgBlockSignals(domain);

/*printf("connect: start pend thread\n");*/
    status = pthread_create(&domain->pendThread, NULL,
                            cMsgClientListeningThread, (void *) threadArg);
    if (status != 0) {
        cmsg_err_abort(status, "Creating TCP message listening thread");
    }

    /*
     * Wait for flag to indicate thread is actually running before
     * continuing on. This thread must be running before we talk to
     * the rc server since the server tries to communicate with
     * the listening thread.
     */

  #ifdef VXWORKS
    hz = sysClkRateGet();
  #else
    /* get system clock rate - probably 100 Hz */
    hz = 100;
    hz = (int) sysconf(_SC_CLK_TCK);
  #endif
    /* wait up to WAIT_FOR_THREADS seconds for a thread to start */
    try_max = hz * WAIT_FOR_THREADS;
    num_try = 0;
    waitForThread.tv_sec  = 0;
    waitForThread.tv_nsec = 1000000000/hz;

    while((threadArg->isRunning != 1) && (num_try++ < try_max)) {
        nanosleep(&waitForThread, NULL);
    }
    if (num_try > try_max) {
        if (cMsgDebug >= CMSG_DEBUG_ERROR) {
            fprintf(stderr, "cmsg_rc_connect, cannot start listening thread\n");
        }
        exit(-1);
    }

    /* Mem allocated for the argument passed to listening thread is 
     * now freed in the pthread cancellation cleanup handler.in
     * cMsgDomainListenThread.c
     */
    /*free(threadArg);*/

    if (cMsgDebug >= CMSG_DEBUG_INFO) {
        fprintf(stderr, "cmsg_rc_connect: created listening thread\n");
    }

    /*-------------------------------------------------------
     * Talk to runcontrol broadcast server
     *-------------------------------------------------------*/
    
    memset((void *)&servaddr, 0, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_port   = htons(serverPort);
    
    /* create UDP socket */
    domain->sendSocket = socket(AF_INET, SOCK_DGRAM, 0);
    if (domain->sendSocket < 0) {
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        free(serverHost);
        free(expid);
        return(CMSG_SOCKET_ERROR);
    }

    /* turn broadcasting on */
    err = setsockopt(domain->sendSocket, SOL_SOCKET, SO_BROADCAST, (char*) &on, sizeof(on));
    if (err < 0) {
        close(domain->sendSocket);
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        free(serverHost);
        free(expid);
        return(CMSG_SOCKET_ERROR);
    }

    if ( (err = cMsgStringToNumericIPaddr(serverHost, &servaddr)) != CMSG_OK ) {
        close(domain->sendSocket);
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        free(serverHost);
        free(expid);
        return(err);
    }
    
    /*
     * We send 4 items explicitly:
     *   1) Type of broadcast (rc or cMsg domain),
     *   1) TCP listening port of this client,
     *   2) name of this client, &
     *   2) EXPID (experiment id string)
     * The host we're sending from gets sent for free
     * as does the UDP port we're sending from.
     */

/*printf("Sending info (listening tcp port = %d, expid = %s) to server on port = %hu on host %s\n",
        ((int) domain->listenPort), expid, serverPort, serverHost);*/
    
    nameLen  = strlen(myName);
    expidLen = strlen(expid);
    
    /* type of broadcast */
    outGoing[0] = htonl(RC_DOMAIN_BROADCAST);
    /* tcp port */
    outGoing[1] = htonl((int) domain->listenPort);
    /* length of "myName" string */
    outGoing[2] = htonl(nameLen);
    /* length of "expid" string */
    outGoing[3] = htonl(expidLen);

    /* copy data into a single buffer */
    memcpy(buffer, (void *)outGoing, sizeof(outGoing));
    len = sizeof(outGoing);
    memcpy(buffer+len, (const void *)myName, nameLen);
    len += nameLen;
    memcpy(buffer+len, (const void *)expid, expidLen);
    len += expidLen;
        
    free(serverHost);
    
    /* create and start a thread which will receive any responses to our broadcast */
    memset((void *)&rArg.addr, 0, sizeof(rArg.addr));
    rArg.len             = (socklen_t) sizeof(rArg.addr);
    rArg.port            = serverPort;
    rArg.expid           = expid;
    rArg.sockfd          = domain->sendSocket;
    rArg.addr.sin_family = AF_INET;
    
    status = pthread_create(&rThread, NULL, receiverThd, (void *)(&rArg));
    if (status != 0) {
        cmsg_err_abort(status, "Creating broadcast response receiving thread");
    }
    
    /* create and start a thread which will broadcast every second */
    bArg.len       = (socklen_t) sizeof(servaddr);
    bArg.sockfd    = domain->sendSocket;
    bArg.paddr     = &servaddr;
    bArg.buffer    = buffer;
    bArg.bufferLen = len;
    
    status = pthread_create(&bThread, NULL, broadcastThd, (void *)(&bArg));
    if (status != 0) {
        cmsg_err_abort(status, "Creating broadcast sending thread");
    }
    
    /* Wait for a response. If broadcastTO is given in the UDL, use that.
     * The default wait or the wait if broadcastTO is set to 0, is forever.
     * Round things to the nearest second since we're only broadcasting a
     * message every second anyway.
     */    
    if (broadcastTO > 0) {
        wait.tv_sec  = broadcastTO;
        wait.tv_nsec = 0;
        cMsgGetAbsoluteTime(&wait, &time);
        
        status = pthread_mutex_lock(&mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_mutex_lock");
        }
 
/*printf("Wait %d seconds for broadcast to be answered\n", broadcastTO);*/
        status = pthread_cond_timedwait(&cond, &mutex, &time);
        if (status == ETIMEDOUT) {
          /* stop receiving thread */
          pthread_cancel(rThread);
        }
        else if (status != 0) {
            cmsg_err_abort(status, "pthread_cond_timedwait");
        }
        else {
            gotResponse = 1;
        }
        
        status = pthread_mutex_unlock(&mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_mutex_lock");
        }
    }
    else {
        status = pthread_mutex_lock(&mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_mutex_lock");
        }
 
/* printf("Wait forever for broadcast to be answered\n"); */ 
        status = pthread_cond_wait(&cond, &mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_cond_timedwait");
        }
        gotResponse = 1;
        
        status = pthread_mutex_unlock(&mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_mutex_lock");
        }
    }
    
    /* stop broadcasting thread */
    pthread_cancel(bThread);
    free(expid);
    
    if (!gotResponse) {
/* printf("Got no response\n"); */ 
        close(domain->sendSocket);
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(CMSG_NETWORK_ERROR);
    }
    
/* printf("Got a response, now wait for connect to finish\n"); */ 

    /* Wait for a special message to come in to the TCP listening thread.
     * The message will contain the host and UDP port of the destination
     * of our subsequent sends. If connectTO is given in the UDL, use that.
     * Otherwise, wait for a maximum of 30 seconds before timing out of this
     * connect call.
     */
     
    if (connectTO > 0) {
        wait.tv_sec  = connectTO;
        wait.tv_nsec = 0;
    }
        
    cMsgMutexLock(&domain->rcConnectMutex);
    
    if (connectTO > 0) {
/*printf("Wait for connect to finish in %d seconds\n", connectTO);*/
        status = cMsgLatchAwait(&domain->syncLatch, &wait);
    }
    else {
/*printf("Wait FOREVER for connect to finish\n");*/
        status = cMsgLatchAwait(&domain->syncLatch, NULL);
    }

    if (domain->rcConnectAbort) {
/*printf("Told to abort connect by RC Broadcast server\n");*/
        close(domain->sendSocket);
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(CMSG_ABORT);
    }
    
    if (status < 1 || !domain->rcConnectComplete) {
/*printf("Wait timeout or rcConnectComplete is not 1\n");*/
        close(domain->sendSocket);
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(CMSG_TIMEOUT);
    }
        
    close(domain->sendSocket);

    cMsgMutexUnlock(&domain->rcConnectMutex);

    /* create TCP sending socket and store */
    if ( (err = cMsgTcpConnect(domain->sendHost,
                               (unsigned short) domain->sendPort,
                               CMSG_BIGSOCKBUFSIZE, 0, &domain->sendSocket)) != CMSG_OK) {
        cMsgRestoreSignals(domain);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(err);
     }

    /*
     * Create a new UDP "connection". This means all subsequent sends are to
     * be done with the "send" and not the "sendto" function. The benefit is 
     * that the udp socket does not have to connect and disconnect for each
     * message sent.
     */
    memset((void *)&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port   = htons(domain->sendUdpPort);
    
    /* create new UDP socket for sends */
    if (domain->sendUdpSocket > -1) {
        close(domain->sendUdpSocket); /* close old UDP socket */
    }
    domain->sendUdpSocket = socket(AF_INET, SOCK_DGRAM, 0);

    if (domain->sendUdpSocket < 0) {
        cMsgRestoreSignals(domain);
        close(domain->sendSocket);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(CMSG_SOCKET_ERROR);
    }

    /* set send buffer size */
    err = setsockopt(domain->sendUdpSocket, SOL_SOCKET, SO_SNDBUF, (char*) &size, sizeof(size));
    if (err < 0) {
        cMsgRestoreSignals(domain);
        close(domain->sendSocket);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(CMSG_SOCKET_ERROR);
    }

    if ( (err = cMsgStringToNumericIPaddr(domain->sendHost, &addr)) != CMSG_OK ) {
        cMsgRestoreSignals(domain);
        close(domain->sendUdpSocket);
        close(domain->sendSocket);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(err);
    }

/*printf("try UDP connection to port = %hu\n", ntohs(addr.sin_port));*/
    err = connect(domain->sendUdpSocket, (SA *)&addr, sizeof(addr));
    if (err < 0) {
        cMsgRestoreSignals(domain);
        close(domain->sendUdpSocket);
        close(domain->sendSocket);
        pthread_cancel(domain->pendThread);
        cMsgDomainFree(domain);
        free(domain);
        return(CMSG_SOCKET_ERROR);
    }
   
    /* return id */
    *domainId = (void *) domain;
        
    /* install default shutdown handler (exits program) */
    cmsg_rc_setShutdownHandler((void *)domain, defaultShutdownHandler, NULL);

    domain->gotConnection = 1;
    
    return(CMSG_OK);
}

/*-------------------------------------------------------------------*/

/**
 * This routine starts a thread to receive a return UDP packet from
 * the server due to our initial uni/broadcast.
 */
static void *receiverThd(void *arg) {

    thdArg *threadArg = (thdArg *) arg;
    int  ints[4], code, port, len1, len2;
    char buf[1024], *pbuf, *tmp, *host=NULL, *expid=NULL;
    ssize_t len;
    
    /* release resources when done */
    pthread_detach(pthread_self());
    
    while (1) {
        /* zero buffer */
        memset((void *)buf,0,1024);

        /* ignore error as it will be caught later */   
        len = recvfrom(threadArg->sockfd, (void *)buf, 1024, 0,
                       (SA *) &threadArg->addr, &(threadArg->len));
        
        /* server is sending:
         *         -> 0xc0da
         *         -> port server is listening on
         *         -> length of server's host name
         *         -> length of server's expid
         *         -> server's host name
         *         -> server's expid
         */
        if (len < 18) continue;
        
        pbuf = buf;
        memcpy(ints, pbuf, sizeof(ints));
        pbuf += sizeof(ints);

        code = ntohl(ints[0]);
        port = ntohl(ints[1]);
        len1 = ntohl(ints[2]);
        len2 = ntohl(ints[3]);
        
        if (len1 > 0 && len1 < 1025-16) {
          if ( (tmp = (char *) malloc(len1+1)) == NULL) {
            continue;    
          }
          /* read host string into memory */
          memcpy(tmp, pbuf, len1);
          /* add null terminator to string */
          tmp[len1] = 0;
          /* store string */
          host = tmp;
          /* go to next string */
          pbuf += len1;
/*printf("host = %s\n", host);*/
        }
        else {
            len1 = 0;
        }
        
        if (len2 > 0 && len2 < 1025-16-len1) {
          if ( (tmp = (char *) malloc(len2+1)) == NULL) {
            continue;    
          }
          memcpy(tmp, pbuf, len2);
          tmp[len2] = 0;
          expid = tmp;
          pbuf += len2;
/*printf("expid = %s\n", expid);*/
        }
        else {
            len2 = 0;
        }
/*
printf("Broadcast response from: %s, on port %hu, listening port = %d, host = %s, expid = %s\n",
                inet_ntoa(threadArg->addr.sin_addr),
                ntohs(threadArg->addr.sin_port),
                port, host, expid);
*/                 
        /* make sure the response is from the RCBroadcastServer */
        if (code != 0xc0da) {
            printf("receiverThd: got bogus secret code response to broadcast\n");
            continue;
        }
        else if (port != threadArg->port) {
            printf("receiverThd: got bogus port response to broadcast\n");
            continue;
        }
        else if (len2 > 0) {
            if (strcmp(expid, threadArg->expid) != 0) {
                printf("receiverThd: got bogus expid response to broadcast\n");
                continue;
            }
        }
        
        /* Tell main thread we are done. */
        pthread_cond_signal(&cond);
        break;
    }
    
    pthread_exit(NULL);
    return NULL;
}


/*-------------------------------------------------------------------*/

/**
 * This routine starts a thread to broadcast a UDP packet to the server
 * every second.
 */
static void *broadcastThd(void *arg) {

    thdArg *threadArg = (thdArg *) arg;
    struct timespec wait = {0, 100000000}; /* 0.1 sec */
    
    /* release resources when done */
    pthread_detach(pthread_self());
    
    /* A slight delay here will help the main thread (calling connect)
     * to be already waiting for a response from the server when we
     * broadcast to the server here (prompting that response). This
     * will help insure no responses will be lost.
     */
    nanosleep(&wait, NULL);
    
    while (1) {

/*printf("Send broadcast to RC Broadcast server\n");*/
      sendto(threadArg->sockfd, (void *)threadArg->buffer, threadArg->bufferLen, 0,
             (SA *) threadArg->paddr, threadArg->len);
      
      
      sleep(1);
    }
    
    pthread_exit(NULL);
    return NULL;
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends a msg to the specified rc server. It is called
 * by the user through cMsgSend() given the appropriate UDL. It is asynchronous
 * and should rarely block. It will only block if the udp socket buffer has
 * reached it limit which is not likely. In this domain cMsgFlush() does nothing
 * and does not need to be called for the message to be sent immediately.<p>
 *
 * This routine only sends the following messge fields:
 * <ol>
 *   <li>cMsg version</li>
 *   <li>user int</li>
 *   <li>info</li>
 *   <li>sender time</li>
 *   <li>user time</li>
 *   <li>sender</li>
 *   <li>subject</li>
 *   <li>type</li>
 *   <li>text</li>
 *   <li>byte array</li>
 * </ol>
 * @param domainId id of the domain connection
 * @param vmsg pointer to a message structure
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id or message argument is null
 * @returns CMSG_LIMIT_EXCEEDED if the message text field is > 1500 bytes (1 packet)
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_rc_send(void *domainId, const void *vmsg) {
  
  cMsgMessage_t *msg = (cMsgMessage_t *) vmsg;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  int err=CMSG_OK, len, lenSender, lenSubject, lenType, lenText, lenByteArray;
  int fd, highInt, lowInt, msgType, getResponse, outGoing[15];
  ssize_t sendLen;
  uint64_t llTime;
  struct timespec now;

  
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
      
  /* check args */
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  if ( (cMsgCheckString(msg->subject) != CMSG_OK ) ||
       (cMsgCheckString(msg->type)    != CMSG_OK )    ) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  if (msg->text == NULL) {
    lenText = 0;
  }
  else {
    lenText = strlen(msg->text);
  }

  if (!msg->context.udpSend) {
    fd = domain->sendSocket;  
  }
  else {
    fd = domain->sendUdpSocket;
  }

  cMsgGetGetResponse(vmsg, &getResponse);
  msgType = CMSG_SUBSCRIBE_RESPONSE;
  if (getResponse) {
/*printf("Sending a GET response with senderToken = %d\n",msg->senderToken);*/
      msgType = CMSG_GET_RESPONSE;
  }

  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(msgType);
  /* reserved for future use */
  outGoing[2] = htonl(CMSG_VERSION_MAJOR);
  /* user int */
  outGoing[3] = htonl(msg->userInt);
  /* bit info */
  outGoing[4] = htonl(msg->info);
  /* senderToken */
  outGoing[5] = htonl(msg->senderToken);

  /* time message sent (right now) */
  clock_gettime(CLOCK_REALTIME, &now);
  /* convert to milliseconds */
  llTime  = ((uint64_t)now.tv_sec * 1000) +
            ((uint64_t)now.tv_nsec/1000000);
  highInt = (int) ((llTime >> 32) & 0x00000000FFFFFFFF);
  lowInt  = (int) (llTime & 0x00000000FFFFFFFF);
  outGoing[6] = htonl(highInt);
  outGoing[7] = htonl(lowInt);

  /* user time */
  llTime  = ((uint64_t)msg->userTime.tv_sec * 1000) +
            ((uint64_t)msg->userTime.tv_nsec/1000000);
  highInt = (int) ((llTime >> 32) & 0x00000000FFFFFFFF);
  lowInt  = (int) (llTime & 0x00000000FFFFFFFF);
  outGoing[8] = htonl(highInt);
  outGoing[9] = htonl(lowInt);

  /* length of "sender" string */
  lenSender    = strlen(domain->name);
  outGoing[10] = htonl(lenSender);

  /* length of "subject" string */
  lenSubject   = strlen(msg->subject);
  outGoing[11] = htonl(lenSubject);

  /* length of "type" string */
  lenType      = strlen(msg->type);
  outGoing[12] = htonl(lenType);

  /* length of "text" string */
  outGoing[13] = htonl(lenText);

  /* length of byte array */
  lenByteArray = msg->byteArrayLength;
  outGoing[14] = htonl(lenByteArray);

  /* total length of message (minus first int) is first item sent */
  len = sizeof(outGoing) + lenSubject + lenType +
        lenSender + lenText + lenByteArray;
  outGoing[0] = htonl((int) (len - sizeof(int)));

  if (len > BIGGEST_UDP_PACKET_SIZE) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_rc_send: packet size too big\n");
    }
    return(CMSG_LIMIT_EXCEEDED);
  }

  /* Cannot run this while connecting/disconnecting */
  cMsgConnectReadLock(domain);

  if (domain->gotConnection != 1) {
    cMsgConnectReadUnlock(domain);
    return(CMSG_LOST_CONNECTION);
  }

  /* Make send socket communications thread-safe. That
   * includes protecting the one buffer being used.
   */
  cMsgSocketMutexLock(domain);

  /* allocate more memory for message-sending buffer if necessary */
  if (domain->msgBufferSize < len) {
    free(domain->msgBuffer);
    domain->msgBufferSize = len + 1024; /* give us 1kB extra */
    domain->msgBuffer = (char *) malloc(domain->msgBufferSize);
    if (domain->msgBuffer == NULL) {
      cMsgSocketMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      return(CMSG_OUT_OF_MEMORY);
    }
  }

  /* copy data into a single static buffer */
  memcpy(domain->msgBuffer, (void *)outGoing, sizeof(outGoing));
  len = sizeof(outGoing);
  memcpy(domain->msgBuffer+len, (void *)domain->name, lenSender);
  len += lenSender;
  memcpy(domain->msgBuffer+len, (void *)msg->subject, lenSubject);
  len += lenSubject;
  memcpy(domain->msgBuffer+len, (void *)msg->type, lenType);
  len += lenType;
  memcpy(domain->msgBuffer+len, (void *)msg->text, lenText);
  len += lenText;
  memcpy(domain->msgBuffer+len, (void *)&((msg->byteArray)[msg->byteArrayOffset]), lenByteArray);
  len += lenByteArray;   

  if (!msg->context.udpSend) {
/*printf("cmsg_rc_send: TCP, fd = %d\n", fd);*/
   /* send data over TCP socket */
    sendLen = cMsgTcpWrite(fd, (void *) domain->msgBuffer, len);
  }
  else {
/*printf("cmsg_rc_send: UDP, fd = %d\n", fd);*/
    /* send data over UDP socket */
    sendLen = send(fd, (void *) domain->msgBuffer, len, 0);      
  }
  if (sendLen != len) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_rc_send: write failure\n");
    }
    err = CMSG_NETWORK_ERROR;
  }

  /* done protecting communications */
  cMsgSocketMutexUnlock(domain);
  cMsgConnectReadUnlock(domain);
  
  return(err);
}


/*-------------------------------------------------------------------*/


/** syncSend is not implemented in the rc domain. */
int cmsg_rc_syncSend(void *domainId, const void *vmsg, const struct timespec *timeout, int *response) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


/** subscribeAndGet is not implemented in the rc domain. */
int cmsg_rc_subscribeAndGet(void *domainId, const char *subject, const char *type,
                                 const struct timespec *timeout, void **replyMsg) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


/** sendAndGet is not implemented in the rc domain. */
int cmsg_rc_sendAndGet(void *domainId, const void *sendMsg, const struct timespec *timeout,
                            void **replyMsg) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


/** flush does nothing in the rc domain. */
int cmsg_rc_flush(void *domainId, const struct timespec *timeout) {  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine subscribes to messages of the given subject and type.
 * When a message is received, the given callback is passed the message
 * pointer and the userArg pointer and then is executed. A configuration
 * structure is given to determine the behavior of the callback.
 * This routine is called by the user through cMsgSubscribe() given the
 * appropriate UDL. Only 1 subscription for a specific combination of
 * subject, type, callback and userArg is allowed.
 *
 * @param domainId id of the domain connection
 * @param subject subject of messages subscribed to
 * @param type type of messages subscribed to
 * @param callback pointer to callback to be executed on receipt of message
 * @param userArg user-specified pointer to be passed to the callback
 * @param config pointer to callback configuration structure
 * @param handle pointer to handle (void pointer) to be used for unsubscribing
 *               from this subscription
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id, subject, type, or callback are null
 * @returns CMSG_OUT_OF_MEMORY if all available subscription memory has been used
 * @returns CMSG_ALREADY_EXISTS if an identical subscription already exists
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_rc_subscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                      void *userArg, cMsgSubscribeConfig *config, void **handle) {

    int i, j, iok=0, jok=0, uniqueId, status, err=CMSG_OK;
    cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
    subscribeConfig *sConfig = (subscribeConfig *) config;
    cbArg *cbarg;
    pthread_attr_t threadAttribute;

    /* check args */  
    if (domain == NULL) {
      return(CMSG_BAD_ARGUMENT);
    }
  
    if ( (cMsgCheckString(subject) != CMSG_OK ) ||
         (cMsgCheckString(type)    != CMSG_OK ) ||
         (callback == NULL)                    ) {
      return(CMSG_BAD_ARGUMENT);
    }
    
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      return(CMSG_LOST_CONNECTION);
    }

    /* use default configuration if none given */
    if (config == NULL) {
      sConfig = (subscribeConfig *) cMsgSubscribeConfigCreate();
    }

    /* make sure subscribe and unsubscribe are not run at the same time */
    cMsgSubscribeMutexLock(domain);

    /* add to callback list if subscription to same subject/type exists */
    iok = jok = 0;
    for (i=0; i<CMSG_MAX_SUBSCRIBE; i++) {
      if (domain->subscribeInfo[i].active == 0) {
        continue;
      }

      if ((strcmp(domain->subscribeInfo[i].subject, subject) == 0) && 
          (strcmp(domain->subscribeInfo[i].type, type) == 0) ) {

        iok = 1;
        jok = 0;

        /* scan through callbacks looking for duplicates */ 
        for (j=0; j<CMSG_MAX_CALLBACK; j++) {
	  if (domain->subscribeInfo[i].cbInfo[j].active == 0) {
            continue;
          }

          if ( (domain->subscribeInfo[i].cbInfo[j].callback == callback) &&
               (domain->subscribeInfo[i].cbInfo[j].userArg  ==  userArg))  {

            cMsgSubscribeMutexUnlock(domain);
            cMsgConnectReadUnlock(domain);
            return(CMSG_ALREADY_EXISTS);
          }
        }

        /* scan through callbacks looking for empty space */ 
        for (j=0; j<CMSG_MAX_CALLBACK; j++) {
	  if (domain->subscribeInfo[i].cbInfo[j].active == 0) {

            domain->subscribeInfo[i].cbInfo[j].active   = 1;
	    domain->subscribeInfo[i].cbInfo[j].callback = callback;
	    domain->subscribeInfo[i].cbInfo[j].userArg  = userArg;
            domain->subscribeInfo[i].cbInfo[j].head     = NULL;
            domain->subscribeInfo[i].cbInfo[j].tail     = NULL;
            domain->subscribeInfo[i].cbInfo[j].quit     = 0;
            domain->subscribeInfo[i].cbInfo[j].messages = 0;
            domain->subscribeInfo[i].cbInfo[j].config   = *sConfig;
            
            domain->subscribeInfo[i].numCallbacks++;
            
            cbarg = (cbArg *) malloc(sizeof(cbArg));
            if (cbarg == NULL) {
              cMsgSubscribeMutexUnlock(domain);
              cMsgConnectReadUnlock(domain);
              return(CMSG_OUT_OF_MEMORY);  
            }                        
            cbarg->domainId = (uintptr_t) domainId;
            cbarg->subIndex = i;
            cbarg->cbIndex  = j;
            cbarg->domain   = domain;
            
            if (handle != NULL) {
              *handle = (void *)cbarg;
            }

            /* init thread attributes */
            pthread_attr_init(&threadAttribute);
            
            /* if stack size of this thread is set, include in attribute */
            if (domain->subscribeInfo[i].cbInfo[j].config.stackSize > 0) {
              pthread_attr_setstacksize(&threadAttribute,
                     domain->subscribeInfo[i].cbInfo[j].config.stackSize);
            }

            /* start callback thread now */
            status = pthread_create(&domain->subscribeInfo[i].cbInfo[j].thread,
                                    &threadAttribute, cMsgCallbackThread, (void *)cbarg);
            if (status != 0) {
              cmsg_err_abort(status, "Creating callback thread");
            }

            /* release allocated memory */
            pthread_attr_destroy(&threadAttribute);
            if (config == NULL) {
              cMsgSubscribeConfigDestroy((cMsgSubscribeConfig *) sConfig);
            }

	    jok = 1;
            break;
	  }
        }
        break;
      }
    }

    if ((iok == 1) && (jok == 0)) {
      cMsgSubscribeMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      return(CMSG_OUT_OF_MEMORY);
    }
    if ((iok == 1) && (jok == 1)) {
      cMsgSubscribeMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      return(CMSG_OK);
    }

    /* no match, make new entry */
    iok = 0;
    for (i=0; i<CMSG_MAX_SUBSCRIBE; i++) {
    
      if (domain->subscribeInfo[i].active != 0) {
        continue;
      }

      domain->subscribeInfo[i].active  = 1;
      domain->subscribeInfo[i].subject = (char *) strdup(subject);
      domain->subscribeInfo[i].type    = (char *) strdup(type);
      domain->subscribeInfo[i].subjectRegexp = cMsgStringEscape(subject);
      domain->subscribeInfo[i].typeRegexp    = cMsgStringEscape(type);
      domain->subscribeInfo[i].cbInfo[0].active   = 1;
      domain->subscribeInfo[i].cbInfo[0].callback = callback;
      domain->subscribeInfo[i].cbInfo[0].userArg  = userArg;
      domain->subscribeInfo[i].cbInfo[0].head     = NULL;
      domain->subscribeInfo[i].cbInfo[0].tail     = NULL;
      domain->subscribeInfo[i].cbInfo[0].quit     = 0;
      domain->subscribeInfo[i].cbInfo[0].messages = 0;
      domain->subscribeInfo[i].cbInfo[0].config   = *sConfig;

      domain->subscribeInfo[i].numCallbacks++;

      cbarg = (cbArg *) malloc(sizeof(cbArg));
      if (cbarg == NULL) {
        cMsgSubscribeMutexUnlock(domain);
        cMsgConnectReadUnlock(domain);
        return(CMSG_OUT_OF_MEMORY);  
      }                        
      cbarg->domainId = (uintptr_t) domainId;
      cbarg->subIndex = i;
      cbarg->cbIndex  = 0;
      cbarg->domain   = domain;
      
      if (handle != NULL) {
        *handle = (void *)cbarg;
      }

      /* init thread attributes */
      pthread_attr_init(&threadAttribute);

      /* if stack size of this thread is set, include in attribute */
      if (domain->subscribeInfo[i].cbInfo[0].config.stackSize > 0) {
        pthread_attr_setstacksize(&threadAttribute,
               domain->subscribeInfo[i].cbInfo[0].config.stackSize);
      }

      /* start callback thread now */
      status = pthread_create(&domain->subscribeInfo[i].cbInfo[0].thread,
                              &threadAttribute, cMsgCallbackThread, (void *)cbarg);
      if (status != 0) {
        cmsg_err_abort(status, "Creating callback thread");
      }

      /* release allocated memory */
      pthread_attr_destroy(&threadAttribute);
      if (config == NULL) {
        cMsgSubscribeConfigDestroy((cMsgSubscribeConfig *) sConfig);
      }

      iok = 1;

      /*
       * Pick a unique identifier for the subject/type pair, and
       * send it to the domain server & remember it for future use
       * Mutex protect this operation as many cmsg_rc_connect calls may
       * operate in parallel on this static variable.
       */
      staticMutexLock();
      uniqueId = subjectTypeId++;
      staticMutexUnlock();
      domain->subscribeInfo[i].id = uniqueId;

      break;
    } /* for i */

    /* done protecting subscribe */
    cMsgSubscribeMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);

    if (iok == 0) {
      err = CMSG_OUT_OF_MEMORY;
    }

    return(err);
}


/*-------------------------------------------------------------------*/


/**
 * This routine unsubscribes to messages of the given handle (which
 * represents a given subject, type, callback, and user argument).
 * This routine is called by the user through
 * cMsgUnSubscribe() given the appropriate UDL. In this domain cMsgFlush()
 * does nothing and does not need to be called for cmsg_rc_unsubscribe to be
 * started immediately.
 *
 * @param domainId id of the domain connection
 * @param handle void pointer obtained from cmsg_rc_subscribe
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id, handle or its subject, type, or callback are null,
 *                            or the given subscription (thru handle) does not have
 *                            an active subscription or callbacks
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_rc_unsubscribe(void *domainId, void *handle) {

    int status, err=CMSG_OK;
    cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
    cbArg           *cbarg;
    subInfo         *subscriptionInfo;
    subscribeCbInfo *callbackInfo;


    /* check args */
    if (domain == NULL) {
      return(CMSG_BAD_ARGUMENT);
    }
  
    if (handle == NULL) {
      return(CMSG_BAD_ARGUMENT);  
    }

    cbarg = (cbArg *)handle;

    if (cbarg->domainId != (uintptr_t)domainId  ||
        cbarg->subIndex < 0 ||
        cbarg->cbIndex  < 0 ||
        cbarg->subIndex >= CMSG_MAX_SUBSCRIBE ||
        cbarg->cbIndex  >= CMSG_MAX_CALLBACK    ) {
      return(CMSG_BAD_ARGUMENT);    
    }

    /* convenience variables */
    subscriptionInfo = &domain->subscribeInfo[cbarg->subIndex];
    callbackInfo     = &subscriptionInfo->cbInfo[cbarg->cbIndex];  

    /* if subscription has no active callbacks ... */
    if (!subscriptionInfo->active ||
        !callbackInfo->active     ||
         subscriptionInfo->numCallbacks < 1) {
      return(CMSG_BAD_ARGUMENT);  
    }

    /* gotta have subject, type, and callback */
    if ( (cMsgCheckString(subscriptionInfo->subject) != CMSG_OK ) ||
         (cMsgCheckString(subscriptionInfo->type)    != CMSG_OK ) ||
         (callbackInfo->callback == NULL)                    )  {
      return(CMSG_BAD_ARGUMENT);
    }
    
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      return(CMSG_LOST_CONNECTION);
    }

    /* make sure subscribe and unsubscribe are not run at the same time */
    cMsgSubscribeMutexLock(domain);
        
    /* Delete entry if there was at least 1 callback
     * to begin with and now there are none for this subject/type.
     */
    if (subscriptionInfo->numCallbacks - 1 < 1) {
      /* do the unsubscribe. */
      free(subscriptionInfo->subject);
      free(subscriptionInfo->type);
      free(subscriptionInfo->subjectRegexp);
      free(subscriptionInfo->typeRegexp);
      /* set these equal to NULL so they aren't freed again later */
      subscriptionInfo->subject       = NULL;
      subscriptionInfo->type          = NULL;
      subscriptionInfo->subjectRegexp = NULL;
      subscriptionInfo->typeRegexp    = NULL;
      /* make array space available for another subscription */
      subscriptionInfo->active        = 0;
    }
    
    /* free mem */
    free(cbarg);
    
    /* one less callback */
    subscriptionInfo->numCallbacks--;

    /* tell callback thread to end */
    callbackInfo->quit = 1;

    /* wakeup callback thread */
    status = pthread_cond_broadcast(&callbackInfo->cond);
    if (status != 0) {
      cmsg_err_abort(status, "Failed callback condition signal");
    }
    
    /*
     * Once this subscription wakes up it sets the array location
     * as inactive/available (callbackInfo->active = 0). Don't do
     * that yet as another subscription may be done
     * (and set subscription->active = 1) before it wakes up
     * and thus not end itself.
     */
    
    /* done protecting unsubscribe */
    cMsgSubscribeMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);

    return(err);
}


/*-------------------------------------------------------------------*/


/**
 * The monitor function is not implemented in the rc domain.
 */   
int cmsg_rc_monitor(void *domainId, const char *command, void **replyMsg) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


/**
 * This routine enables the receiving of messages and delivery to callbacks.
 * The receiving of messages is disabled by default and must be explicitly
 * enabled.
 *
 * @param domainId id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is null
 */   
int cmsg_rc_start(void *domainId) {

  if (domainId == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  ((cMsgDomainInfo *) domainId)->receiveState = 1;
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine disables the receiving of messages and delivery to callbacks.
 * The receiving of messages is disabled by default. This routine only has an
 * effect when cMsgReceiveStart() was previously called.
 *
 * @param domainId id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is null
 */   
int cmsg_rc_stop(void *domainId) {

  if (domainId == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  ((cMsgDomainInfo *) domainId)->receiveState = 0;
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine disconnects the client from the RC server.
 *
 * @param domainId pointer to id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId or the pointer it points to is NULL
 */   
int cmsg_rc_disconnect(void **domainId) {

    cMsgDomainInfo *domain;
    int i, j, status;
    subscribeCbInfo *subscription;
    struct timespec wait4thds = {0, 100000000}; /* 0.1 sec */

    if (domainId == NULL) return(CMSG_BAD_ARGUMENT);
    domain = (cMsgDomainInfo *) (*domainId);
    if (domain == NULL) return(CMSG_BAD_ARGUMENT);
    
    /* When changing initComplete / connection status, protect it */
    cMsgConnectWriteLock(domain);

    domain->gotConnection = 0;

    /* close TCP sending socket */
    close(domain->sendSocket);

    /* close UDP sending socket */
    close(domain->sendUdpSocket);

    /* stop listening and client communication threads */
    if (cMsgDebug >= CMSG_DEBUG_INFO) {
      fprintf(stderr, "cmsg_rc_disconnect:cancel listening & client threads\n");
    }

    pthread_cancel(domain->pendThread);
    /* close listening socket */
    close(domain->listenSocket);

    /* terminate all callback threads */
    for (i=0; i<CMSG_MAX_SUBSCRIBE; i++) {
      /* if there is a subscription ... */
      if (domain->subscribeInfo[i].active == 1)  {
        /* search callback list */
        for (j=0; j<CMSG_MAX_CALLBACK; j++) {
          /* convenience variable */
          subscription = &domain->subscribeInfo[i].cbInfo[j];

	  if (subscription->active == 1) {          

            /* tell callback thread to end */
            subscription->quit = 1;

            if (cMsgDebug >= CMSG_DEBUG_INFO) {
              fprintf(stderr, "cmsg_rc_disconnect:wake up callback thread\n");
            }

            /* wakeup callback thread */
            status = pthread_cond_broadcast(&subscription->cond);
            if (status != 0) {
              cmsg_err_abort(status, "Failed callback condition signal");
            }
	  }
        }
      }
    }
    
    /* give the above threads a chance to quit before we reset everytbing */
    nanosleep(&wait4thds, NULL);
    
    /* Unblock SIGPIPE */
    cMsgRestoreSignals(domain);
    
    cMsgConnectWriteUnlock(domain);

    /* Clean up memory */
    cMsgDomainFree(domain);
    free(domain);
    *domainId = NULL;

    return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*   shutdown handler functions                                      */
/*-------------------------------------------------------------------*/


/**
 * This routine is the default shutdown handler function.
 * @param userArg argument to shutdown handler 
 */   
static void defaultShutdownHandler(void *userArg) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "Ran default shutdown handler\n");
    }
    exit(-1);      
}


/*-------------------------------------------------------------------*/


/**
 * This routine sets the shutdown handler function.
 *
 * @param domainId id of the domain connection
 * @param handler shutdown handler function
 * @param userArg argument to shutdown handler 
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id is null
 */   
int cmsg_rc_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler,
                               void *userArg) {
  
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
    
  domain->shutdownHandler = handler;
  domain->shutdownUserArg = userArg;
      
  return CMSG_OK;
}


/*-------------------------------------------------------------------*/


/** shutdownClients is not implemented in the rc domain. */
int cmsg_rc_shutdownClients(void *domainId, const char *client, int flag) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


/** shutdownServers is not implemented in the rc domain. */
int cmsg_rc_shutdownServers(void *domainId, const char *server, int flag) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


/**
 * This routine parses, using regular expressions, the RC domain
 * portion of the UDL sent from the next level up" in the API.
 */
static int parseUDL(const char *UDLR,
                    char **host,
                    unsigned short *port,
                    char **expid,
                    int  *broadcastTO,
                    int  *connectTO,
                    char **junk) {

    int        err, Port, index;
    size_t     len, bufLength;
    char       *udlRemainder, *val;
    char       *buffer;
    const char *pattern = "(([a-zA-Z]+[a-zA-Z0-9\\.\\-]*)|([0-9]+\\.[0-9\\.]+))?:?([0-9]+)?/?(.*)";
    regmatch_t matches[6]; /* we have 6 potential matches: 1 whole, 5 sub */
    regex_t    compiled;
    
    if (UDLR == NULL) {
        return (CMSG_BAD_FORMAT);
    }
    
    /* make a copy */        
    udlRemainder = (char *) strdup(UDLR);
    if (udlRemainder == NULL) {
      return(CMSG_OUT_OF_MEMORY);
    }
/* printf("parseUDL: udl remainder = %s\n", udlRemainder); */
 
    /* make a big enough buffer to construct various strings, 256 chars minimum */
    len       = strlen(udlRemainder) + 1;
    bufLength = len < 256 ? 256 : len;    
    buffer    = (char *) malloc(bufLength);
    if (buffer == NULL) {
      free(udlRemainder);
      return(CMSG_OUT_OF_MEMORY);
    }
    
    /* RC domain UDL is of the form:
     *        cMsg:rc://<host>:<port>/?expid=<expid>&broadcastTO=<timeout>&connectTO=<timeout>
     *
     * Remember that for this domain:
     * 1) port is optional with a default of RC_BROADCAST_PORT (6543)
     * 2) host is optional with a default of 255.255.255.255 (broadcast)
     *    and may be "localhost" or in dotted decimal form
     * 3) the experiment id or expid is optional, it is taken from the
     *    environmental variable EXPID
     * 4) broadcastTO is the time to wait in seconds before connect returns a
     *    timeout when a rc broadcast server does not answer 
     * 5) connectTO is the time to wait in seconds before connect returns a
     *    timeout while waiting for the rc server to send a special (tcp)
     *    concluding connect message
     */

    /* compile regular expression */
    err = cMsgRegcomp(&compiled, pattern, REG_EXTENDED);
    if (err != 0) {
        free(udlRemainder);
        free(buffer);
        return (CMSG_ERROR);
    }
    
    /* find matches */
    err = cMsgRegexec(&compiled, udlRemainder, 6, matches, 0);
    if (err != 0) {
        /* no match */
        free(udlRemainder);
        free(buffer);
        return (CMSG_BAD_FORMAT);
    }
    
    /* free up memory */
    cMsgRegfree(&compiled);
            
    /* find host name, default = 255.255.255.255 (broadcast) */
    if (matches[1].rm_so > -1) {
       buffer[0] = 0;
       len = matches[1].rm_eo - matches[1].rm_so;
       strncat(buffer, udlRemainder+matches[1].rm_so, len);
                
        /* if the host is "localhost", find the actual host name */
        if (strcmp(buffer, "localhost") == 0) {
            /* get canonical local host name */
            if (cMsgLocalHost(buffer, bufLength) != CMSG_OK) {
                /* error finding local host so just broadcast */
                buffer[0] = 0;
                strncat(buffer, "255.255.255.255", 15);
            }
        }
    }
    else {
        buffer[0] = 0;
        strncat(buffer, "255.255.255.255", 15);
    }
    
    if (host != NULL) {
        *host = (char *)strdup(buffer);
    }    
/* printf("parseUDL: host = %s\n", buffer); */


    /* find port */
    index = 4;
    if (matches[index].rm_so < 0) {
        /* no match for port so use default */
        Port = RC_BROADCAST_PORT;
        if (cMsgDebug >= CMSG_DEBUG_WARN) {
            fprintf(stderr, "parseUDLregex: guessing that the name server port is %d\n",
                   Port);
        }
    }
    else {
        buffer[0] = 0;
        len = matches[index].rm_eo - matches[index].rm_so;
        strncat(buffer, udlRemainder+matches[index].rm_so, len);        
        Port = atoi(buffer);        
    }

    if (Port < 1024 || Port > 65535) {
      if (host != NULL) free((void *) *host);
      free(buffer);
      return (CMSG_OUT_OF_RANGE);
    }
               
    if (port != NULL) {
      *port = Port;
    }
/* printf("parseUDL: port = %hu\n", Port ); */

    /* find junk */
    index++;
    buffer[0] = 0;
    if (matches[index].rm_so < 0) {
        /* no match */
        len = 0;
        if (junk != NULL) {
            *junk = NULL;
        }
    }
    else {
        len = matches[index].rm_eo - matches[index].rm_so;
        strncat(buffer, udlRemainder+matches[index].rm_so, len);
                
        if (junk != NULL) {
            *junk = (char *) strdup(buffer);
        }        
/* printf("parseUDL: remainder = %s, len = %d\n", buffer, len); */
    }

    /* find expid parameter in the junk if it exists*/
    len = strlen(buffer);
    while (len > 0) {

        /* look for ?expid=value& or &expid=value& */
        pattern = "expid=([a-zA-Z0-9_\\-]+)&?";

        /* compile regular expression */
        cMsgRegcomp(&compiled, pattern, REG_EXTENDED | REG_ICASE);

        /* find matches */
        val = strdup(buffer);
        err = cMsgRegexec(&compiled, val, 2, matches, 0);
        
        /* if there's a match ... */
        if (err == 0) {
            /* find expid */
            if (matches[1].rm_so >= 0) {
               buffer[0] = 0;
               len = matches[1].rm_eo - matches[1].rm_so;
               strncat(buffer, val+matches[1].rm_so, len);
               if (expid != NULL) {
                 *expid = (char *) strdup(buffer);
               }        
/*printf("parseUDL: expid = %s\n", buffer);*/
            }
        }
                
         /* free up memory */
        cMsgRegfree(&compiled);
       
        /* now look for ?broadcastTO=value& or &broadcastTO=value& */
        pattern = "broadcastTO=([0-9]+)&?";

        /* compile regular expression */
        cMsgRegcomp(&compiled, pattern, REG_EXTENDED | REG_ICASE);

        /* find matches */
        err = cMsgRegexec(&compiled, val, 2, matches, 0);
        
        /* if there's a match ... */
        if (err == 0) {
            /* find timeout (in milliseconds) */
            if (matches[1].rm_so >= 0) {
               int t;
               buffer[0] = 0;
               len = matches[1].rm_eo - matches[1].rm_so;
               strncat(buffer, val+matches[1].rm_so, len);
               t = atoi(buffer);
               if (t < 1) t=0;
               if (broadcastTO != NULL) {
                 *broadcastTO = t;
               }        
/* printf("parseUDL: broadcast timeout = %d\n", t); */
            }
        }
        
        
        /* free up memory */
        cMsgRegfree(&compiled);
       
        /* now look for ?connectTO=value& or &connectTO=value& */
        pattern = "connectTO=([0-9]+)&?";

        /* compile regular expression */
        cMsgRegcomp(&compiled, pattern, REG_EXTENDED | REG_ICASE);

        /* find matches */
        err = cMsgRegexec(&compiled, val, 2, matches, 0);
        
        /* if there's a match ... */
        if (err == 0) {
            /* find timeout (in milliseconds) */
            if (matches[1].rm_so >= 0) {
               int t;
               buffer[0] = 0;
               len = matches[1].rm_eo - matches[1].rm_so;
               strncat(buffer, val+matches[1].rm_so, len);
               t = atoi(buffer);
               if (t < 1) t=0;
               if (connectTO != NULL) {
                 *connectTO = t;
               }        
/* printf("parseUDL: connection timeout = %d\n", t); */
            }
        }
        
        /* free up memory */
        cMsgRegfree(&compiled);
        free(val);
        
        break;
    }



    /* UDL parsed ok */
/* printf("DONE PARSING UDL\n"); */
    free(udlRemainder);
    free(buffer);
    return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*   miscellaneous local functions                                   */
/*-------------------------------------------------------------------*/


/**
 * This routine locks the pthread mutex used when creating unique id numbers
 * and doing the one-time intialization. */
static void staticMutexLock(void) {

  int status = pthread_mutex_lock(&generalMutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed mutex lock");
  }
}


/*-------------------------------------------------------------------*/


/**
 * This routine unlocks the pthread mutex used when creating unique id numbers
 * and doing the one-time intialization. */
static void staticMutexUnlock(void) {

  int status = pthread_mutex_unlock(&generalMutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed mutex unlock");
  }
}
