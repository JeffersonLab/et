/*----------------------------------------------------------------------------*
 *
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    E.Wolin, 15-Jul-2004, Jefferson Lab                                     *
 *                                                                            *
 *    Authors: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *             Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 * Description:
 *
 *  Implements cMsg CODA domain
 *
 *
 *----------------------------------------------------------------------------*/

/**
 * @file
 * This file contains the cMsg domain implementation of the cMsg user API.
 * This a messaging system programmed by the Data Acquisition Group at Jefferson
 * Lab. The cMsg domain has a dual function. It acts as a framework so that the
 * cMsg client can connect to a variety of subdomains (messaging systems). However,
 * it also acts as a messaging system itself in the cMsg <b>subdomain</b>.
 */  
 

/* system includes */
#ifdef VXWORKS
#include <vxWorks.h>
#include <taskLib.h>
#include <hostLib.h>
#include <timers.h>
#include <sysLib.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>
#include <errno.h>
#include <time.h>
#include <string.h>
#include <ctype.h>


/* package includes */
#include "cMsgNetwork.h"
#include "cMsgPrivate.h"
#include "cMsg.h"
#include "cMsgDomain.h"
#include "rwlock.h"
#include "regex.h"



/**
 * Structure for arg to be passed to receiver/broadcast threads.
 * Allows data to flow back and forth with these threads.
 */
typedef struct thdArg_t {
    int sockfd;
    socklen_t len;
    int port;
    struct sockaddr_in addr;
    struct sockaddr_in *paddr;
    int   bufferLen;
    char *buffer;
} thdArg;


/* built-in limits */
/** Number of seconds to wait for cMsgClientListeningThread threads to start. */
#define WAIT_FOR_THREADS 10

/* local variables */
/** Pthread mutex to protect one-time initialization and the local generation of unique numbers. */
static pthread_mutex_t generalMutex = PTHREAD_MUTEX_INITIALIZER;

/** Id number which uniquely defines a subject/type pair. */
static int subjectTypeId = 1;

/** Size of buffer in bytes for sending messages. */
static int initialMsgBufferSize = 15000;

/** Mutex for waiting for broadcast response.*/
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

/** Condition variable for waiting for broadcast response.*/
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

/* Prototypes of the functions which implement the standard cMsg tasks in the cMsg domain. */
int   cmsg_cmsg_connect           (const char *myUDL, const char *myName, const char *myDescription,
                                   const char *UDLremainder,void **domainId);
int   cmsg_cmsg_send              (void *domainId, const void *msg);
int   cmsg_cmsg_syncSend          (void *domainId, const void *msg, const struct timespec *timeout, int *response);
int   cmsg_cmsg_flush             (void *domainId, const struct timespec *timeout);
int   cmsg_cmsg_subscribe         (void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                                   void *userArg, cMsgSubscribeConfig *config, void **handle);
int   cmsg_cmsg_unsubscribe       (void *domainId, void *handle);
int   cmsg_cmsg_subscribeAndGet   (void *domainId, const char *subject, const char *type,
                                   const struct timespec *timeout, void **replyMsg);
int   cmsg_cmsg_sendAndGet        (void *domainId, const void *sendMsg, const struct timespec *timeout,
                                   void **replyMsg);
int   cmsg_cmsg_monitor           (void *domainId, const char *command,  void **replyMsg);
int   cmsg_cmsg_start             (void *domainId);
int   cmsg_cmsg_stop              (void *domainId);
int   cmsg_cmsg_disconnect        (void **domainId);
int   cmsg_cmsg_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg);
int   cmsg_cmsg_shutdownClients   (void *domainId, const char *client, int flag);
int   cmsg_cmsg_shutdownServers   (void *domainId, const char *server, int flag);


/** List of the functions which implement the standard cMsg tasks in the cMsg domain. */
static domainFunctions functions = {cmsg_cmsg_connect, cmsg_cmsg_send,
                                    cmsg_cmsg_syncSend, cmsg_cmsg_flush,
                                    cmsg_cmsg_subscribe, cmsg_cmsg_unsubscribe,
                                    cmsg_cmsg_subscribeAndGet, cmsg_cmsg_sendAndGet,
                                    cmsg_cmsg_monitor, cmsg_cmsg_start,
                                    cmsg_cmsg_stop, cmsg_cmsg_disconnect,
                                    cmsg_cmsg_shutdownClients, cmsg_cmsg_shutdownServers,
                                    cmsg_cmsg_setShutdownHandler};

/* cMsg domain type */
domainTypeInfo cmsgDomainTypeInfo = {
  "cmsg",
  &functions
};



/* local prototypes */

/* mutexes and read/write locks */
static void  staticMutexLock(void);
static void  staticMutexUnlock(void);

/* threads */
static void *keepAliveThread(void *arg);

/* failovers */
static int restoreSubscriptions(cMsgDomainInfo *domain) ;
static int failoverSuccessful(cMsgDomainInfo *domain, int waitForResubscribes);
static int resubscribe(cMsgDomainInfo *domain, const char *subject, const char *type);

/* misc */
static int  disconnectFromKeepAlive(void **domainId);
static int  connectDirect(cMsgDomainInfo *domain, int failoverIndex);
static int  talkToNameServer(cMsgDomainInfo *domain, int serverfd, int failoverIndex);
static int  parseUDL(const char *UDL, char **password,
                           char **host, int *port,
                           char **UDLRemainder,
                           char **subdomainType,
                           char **UDLsubRemainder,
                           int   *broadcast,
                           int   *timeout);
static int  unSendAndGet(void *domainId, int id);
static int  unSubscribeAndGet(void *domainId, const char *subject,
                              const char *type, int id);
static void  defaultShutdownHandler(void *userArg);
static void *receiverThd(void *arg);
static void *broadcastThd(void *arg);
static int   connectWithBroadcast(cMsgDomainInfo *domain, int failoverIndex,
                                  char **host, int *port);


/*-------------------------------------------------------------------*/
/**
 * This routine restores subscriptions to a new server which replaced a crashed server
 * during failover.
 *
 * @param domain id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 */
static int restoreSubscriptions(cMsgDomainInfo *domain)  {
  int i, err;
  
  /*
   * We don't want any cMsg commands to be sent to the server
   * while we are busy resubscribing to a failover server.
   */
  cMsgConnectWriteLock(domain);  

  /* for each client subscription ... */
  for (i=0; i<CMSG_MAX_SUBSCRIBE; i++) {

    /* if subscription not active, forget about it */
    if (domain->subscribeInfo[i].active != 1) {
      continue;
    }
/* printf("Restore Subscription to sub = %s, type = %s\n",
                              domain->subscribeInfo[i].subject,
                              domain->subscribeInfo[i].type); */
                              
    err = resubscribe(domain, domain->subscribeInfo[i].subject,
                              domain->subscribeInfo[i].type);
    
    if (err != CMSG_OK) {
        cMsgConnectWriteUnlock(domain);  
        return(err);
    }        
  }
  
  cMsgConnectWriteUnlock(domain);  

  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/


/**
 * This routine waits a while for a possible failover to a new cMsg server 
 * before attempting to complete an interrupted command to the server or
 * before returning an error.
 *
 * @param domain id of the domain connection
 * @param waitForResubscribe 1 if waiting for all subscriptions to be
 *                           reinstated before returning, else 0
 *
 * @returns 1 if there is a connection to a cMsg server in 3 seconds or 0 if not 
 */
static int failoverSuccessful(cMsgDomainInfo *domain, int waitForResubscribes) {
    int err;
    struct timespec wait;
        
    wait.tv_sec  = 3;
    wait.tv_nsec = 0; /* 3 secs */

/* printf("IN failoverSuccessful\n"); */
    /*
     * If only 1 viable UDL is given by client, forget about
     * waiting for failovers to complete before returning an error.
     */
    if (!domain->implementFailovers) return 0;

    /*
     * Wait for 3 seconds for a new connection
     * before giving up and returning an error.
     */
    
    err = cMsgLatchAwait(&domain->syncLatch, &wait);
/* printf("IN failoverSuccessful, cMsgLatchAwait return = %d\n", err); */
    /* if latch reset or timedout, return false */
    if (err < 1) {
      return 0;
    }

    if (waitForResubscribes) {
       if (domain->gotConnection && domain->resubscribeComplete) return 1;
    }
    else {
       if (domain->gotConnection) return 1;
    }
    
    return 0;
}


/*-------------------------------------------------------------------*/


/**
 * This routine is called once to connect to a cMsg domain. It is called
 * by the user through top-level cMsg API, "cMsgConnect()".
 * The argument "myUDL" is the Universal Domain Locator (or can be a semicolon
 * separated list of UDLs) used to uniquely identify the cMsg server to connect to.
 * It has the form:<p>
 *       <b>cMsg:cMsg://host:port/subdomainType/namespace/?cmsgpassword=<password>& ... </b><p>
 * where the first "cMsg:" is optional. The subdomain is optional with
 * the default being cMsg (if nothing follows the host & port).
 * If the namespace is given, the subdomainType must be specified as well.
 * If the name server requires a password to connect, this can be specified by
 * ?cmsgpassword=<password> immediately after the namespace. It may also be 
 * included later as one of several optional key-value pairs specified.
 *
 * If "myUDL" is a list of UDLs, the first valid one is connected to. If that
 * server fails, this client will automatically failover to the next valid
 * UDL on the list. If this client attempts and fails to connect to each
 * UDL on the list, an error is returned.
 *
 * The argument "myName" is the client's name and may be required to be
 * unique depending on the subdomainType.
 * The argument "myDescription" is an arbitrary string used to describe the
 * client.
 * If successful, this routine fills the argument "domainId", which identifies
 * the connection uniquely and is required as an argument by many other routines.
 *
 * This routine mainly does the UDL parsing. The actual connecting
 * to the name server is done in "connectDirect".
 * 
 * @param myUDL the Universal Domain Locator used to uniquely identify the cMsg
 *        server to connect to
 * @param myName name of this client
 * @param myDescription description of this client
 * @param UDLremainder partially parsed (initial cMsg:domainType:// stripped off)
 *                     UDL which gets passed down from the API level (cMsgConnect())
 * @param domainId pointer to integer which gets filled with a unique id referring
 *        to this connection.
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if general error
 * @returns CMSG_BAD_FORMAT if the UDL is malformed
 * @returns CMSG_BAD_ARGUMENT if no UDL given
 * @returns CMSG_OUT_OF_MEMORY if the allocating memory failed
 * @returns CMSG_SOCKET_ERROR if the listening thread finds all the ports it tries
 *                            to listen on are busy, or socket options could not be
 *                            set
 * @returns CMSG_NETWORK_ERROR if no connection to the name or domain servers can be made,
 *                             or a communication error with either server occurs.
 */   
int cmsg_cmsg_connect(const char *myUDL, const char *myName, const char *myDescription,
                      const char *UDLremainder, void **domainId) {
        
  char *p, *udl;
  int failoverUDLCount = 0, failoverIndex=0, viableUDLs = 0;
  int gotConnection = 0;        
  int i, err=CMSG_OK;
  char temp[CMSG_MAXHOSTNAMELEN];
  cMsgDomainInfo *domain;

  /* allocate struct to hold connection info */
  domain = (cMsgDomainInfo *) calloc(1, sizeof(cMsgDomainInfo));
  if (domain == NULL) {
    return(CMSG_OUT_OF_MEMORY);  
  }
  cMsgDomainInit(domain);  

  /* allocate memory for message-sending buffer */
  domain->msgBuffer     = (char *) malloc(initialMsgBufferSize);
  domain->msgBufferSize = initialMsgBufferSize;
  if (domain->msgBuffer == NULL) {
    cMsgDomainFree(domain);
    free(domain);
    return(CMSG_OUT_OF_MEMORY);
  }

  /* store our host's name */
  gethostname(temp, CMSG_MAXHOSTNAMELEN);
  domain->myHost = (char *) strdup(temp);

  /* store names, can be changed until server connection established */
  domain->name        = (char *) strdup(myName);
  domain->udl         = (char *) strdup(myUDL);
  domain->description = (char *) strdup(myDescription);

  /*
   * The UDL may be a semicolon separated list of UDLs, separate them and
   * store them for future use in failovers.
   */

  /* On first pass, just do a count. */
  udl = (char *)strdup(myUDL);        
  p = strtok(udl, ";");
  while (p != NULL) {
    failoverUDLCount++;
    p = strtok(NULL, ";");
  }
  free(udl);
/*printf("Found %d UDLs\n", failoverUDLCount);*/

  if (failoverUDLCount < 1) {
    cMsgDomainFree(domain);
    free(domain);
    return(CMSG_BAD_ARGUMENT);        
  }

  /* Now that we know how many UDLs there are, allocate array. */
  domain->failoverSize = failoverUDLCount;
  domain->failovers = (parsedUDL *) calloc(failoverUDLCount, sizeof(parsedUDL));
  if (domain->failovers == NULL) {
    cMsgDomainFree(domain);
    free(domain);
    return(CMSG_OUT_OF_MEMORY);
  }

  /* On second pass, stored parsed UDLs. */
  udl = (char *)strdup(myUDL);        
  p   = strtok(udl, ";");
  i   = 0;
  while (p != NULL) {
    /* Parse the UDL (Uniform Domain Locator) */
    if ( (err = parseUDL(p, &domain->failovers[i].password,
                            &domain->failovers[i].nameServerHost,
                            &domain->failovers[i].nameServerPort,
                            &domain->failovers[i].udlRemainder,
                            &domain->failovers[i].subdomain,
                            &domain->failovers[i].subRemainder,
                            &domain->failovers[i].mustBroadcast,
                            &domain->failovers[i].timeout )) != CMSG_OK ) {

      /* There's been a parsing error, mark as invalid UDL */
      domain->failovers[i].valid = 0;
    }
    else {
      domain->failovers[i].valid = 1;
      viableUDLs++;
    }
    domain->failovers[i].udl = strdup(p);
/*printf("Found UDL = %s\n", domain->failovers[i].udl);*/
    p = strtok(NULL, ";");
    i++;
  }
  free(udl);


  /*-------------------------*/
  /* Make a real connection. */
  /*-------------------------*/

  /* If there are no viable UDLs ... */
  if (viableUDLs < 1) {
      cMsgDomainFree(domain);
      free(domain);
      return(CMSG_BAD_FORMAT);            
  }
  /* Else if there's only 1 viable UDL ... */
  else if (viableUDLs < 2) {
/*printf("Only 1 UDL = %s\n", domain->failovers[0].udl);*/

      /* Ain't using failovers */
      domain->implementFailovers = 0;
      
      /* connect using that UDL */
      if (!domain->failovers[0].valid) {
          cMsgDomainFree(domain);
          free(domain);
          return(CMSG_BAD_FORMAT);            
      }
      
      if (domain->failovers[0].mustBroadcast == 1) {
        free(domain->failovers[0].nameServerHost);
        connectWithBroadcast(domain, 0,
                             &domain->failovers[0].nameServerHost,
                             &domain->failovers[0].nameServerPort);     
      }
      
      err = connectDirect(domain, 0);
      domain->failoverIndex = 0;
      if (err != CMSG_OK) {
          cMsgDomainFree(domain);
          free(domain);
          return(err);            
      }
  }
  else {
    int connectFailures = 0;

    /* We're using failovers */
    domain->implementFailovers = 1;
    
    /* Go through the UDL's until one works */
    failoverIndex = -1;
    do {
      /* check to see if UDL valid for cMsg domain */
      if (!domain->failovers[++failoverIndex].valid) {
        connectFailures++;
        continue;
      }

      /* connect using that UDL info */
/*printf("\nTrying to connect with UDL = %s\n",
      domain->failovers[failoverIndex].udl);*/
      if (domain->failovers[failoverIndex].mustBroadcast == 1) {
        free(domain->failovers[failoverIndex].nameServerHost);
        connectWithBroadcast(domain, failoverIndex,
                             &domain->failovers[failoverIndex].nameServerHost,
                             &domain->failovers[failoverIndex].nameServerPort);     
      }

      err = connectDirect(domain, failoverIndex);
      if (err == CMSG_OK) {
        domain->failoverIndex = failoverIndex;
        gotConnection = 1;
/*printf("Connected!!\n");*/
        break;
      }

      connectFailures++;

    } while (connectFailures < failoverUDLCount);

    if (!gotConnection) {
      cMsgDomainFree(domain);
      free(domain);
      return(err);                      
    }        
  }

  /* connection is complete */
  *domainId = (void *) domain;

  /* install default shutdown handler (exits program) */
  cmsg_cmsg_setShutdownHandler((void *)domain, defaultShutdownHandler, NULL);
  
  domain->gotConnection = 1;

  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/


/**
 * Method to broadcast in order to find the domain server from this client.
 * Once the server is found and returns its host and port, a direct connection
 * can be made.
 *
 */
static int connectWithBroadcast(cMsgDomainInfo *domain, int failoverIndex,
                                char **host, int *port) {    
    char   buffer[1024];
    int    err, status, len, passwordLen, sockfd;
    int    outGoing[3], broadcastTO=0, gotResponse=0;
    const int on=1;
    
    pthread_t rThread, bThread;
    thdArg    rArg,    bArg;
    
    struct timespec wait, time;
    struct sockaddr_in servaddr;
     
    /*------------------------
    * Talk to cMsg server
    *------------------------*/
    
    /* create UDP socket for broadcasting */
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        return(CMSG_SOCKET_ERROR);
    }
/*printf("+udp broad %d\n", sockfd);*/

    /* turn broadcasting on */
    err = setsockopt(sockfd, SOL_SOCKET, SO_BROADCAST, (char*) &on, sizeof(on));
    if (err < 0) {
        close(sockfd);
        return(CMSG_SOCKET_ERROR);
    }
       
    memset((void *)&servaddr, 0, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
/*printf("Broadcast thd uses port %hu\n", ((uint16_t)domain->failovers[failoverIndex].nameServerPort));*/
    servaddr.sin_port   = htons((uint16_t) (domain->failovers[failoverIndex].nameServerPort));
    /* send packet to broadcast address */
    if ( (err = cMsgStringToNumericIPaddr("255.255.255.255", &servaddr)) != CMSG_OK ) {
        close(sockfd);
        return(err);
    }
    
    /*
     * We send 2 items explicitly:
     *   1) int describing action to be done
     *   2) password
     * The host we're sending from gets sent for free
     * as does the UDP port we're sending from.
     */
    passwordLen = 0;
    if (domain->failovers[failoverIndex].password != NULL) {
        passwordLen = strlen(domain->failovers[failoverIndex].password);
    }
    /* magic int */
    outGoing[0] = htonl(0xc0da1);
    /* type of broadcast */
    outGoing[1] = htonl(CMSG_DOMAIN_BROADCAST);
    /* length of "password" string */
    outGoing[2] = htonl(passwordLen);

    /* copy data into a single buffer */
    memcpy(buffer, (void *)outGoing, sizeof(outGoing));
    len = sizeof(outGoing);
    if (passwordLen > 0) {
        memcpy(buffer+len, (const void *)domain->failovers[failoverIndex].password, passwordLen);
        len += passwordLen;
    }
        
    /* create and start a thread which will receive any responses to our broadcast */
    memset((void *)&rArg.addr, 0, sizeof(rArg.addr));
    rArg.len             = (socklen_t) sizeof(rArg.addr);
    rArg.port            = 0;
    rArg.sockfd          = sockfd;
    rArg.addr.sin_family = AF_INET;
    
    status = pthread_create(&rThread, NULL, receiverThd, (void *)(&rArg));
    if (status != 0) {
        cmsg_err_abort(status, "Creating broadcast response receiving thread");
    }
    
    /* create and start a thread which will broadcast every second */
    bArg.len       = (socklen_t) sizeof(servaddr);
    bArg.sockfd    = sockfd;
    bArg.paddr     = &servaddr;
    bArg.buffer    = buffer;
    bArg.bufferLen = len;
    
    status = pthread_create(&bThread, NULL, broadcastThd, (void *)(&bArg));
    if (status != 0) {
        cmsg_err_abort(status, "Creating broadcast sending thread");
    }
    
    /* Wait for a response. If broadcastTO is given in the UDL, that is used.
     * The default wait or the wait if broadcastTO is set to 0, is forever.
     * Round things to the nearest second since we're only broadcasting a
     * message every second anyway.
     */
    broadcastTO = domain->failovers[failoverIndex].timeout;
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
            if (host != NULL) {
                *host = rArg.buffer;
            }
            if (port != NULL) {
                *port = rArg.port;
            }
/*printf("Response received, host = %s, port = %hu\n", rArg.buffer, rArg.port);*/
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
 
/*printf("Wait forever for broadcast to be answered\n");*/
        status = pthread_cond_wait(&cond, &mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_cond_timedwait");
        }
        gotResponse = 1;
        
        if (host != NULL) {
            *host = rArg.buffer;
        }
        if (port != NULL) {
            *port = rArg.port;
        }
/*printf("Response received, host = %s, port = %hu\n", rArg.buffer, rArg.port);*/

        status = pthread_mutex_unlock(&mutex);
        if (status != 0) {
            cmsg_err_abort(status, "pthread_mutex_lock");
        }
    }
    
    /* stop broadcasting thread */
    pthread_cancel(bThread);
    
/*printf("-udp broad %d\n", sockfd);*/
    close(sockfd);
    
    if (!gotResponse) {
/*printf("Got no response\n");*/
        return(CMSG_TIMEOUT);
    }

    return(CMSG_OK);
}

/*-------------------------------------------------------------------*/

/**
 * This routine starts a thread to receive a return UDP packet from
 * the server due to our initial broadcast.
 */
static void *receiverThd(void *arg) {
    int port, nameLen, magicInt;
    thdArg *threadArg = (thdArg *) arg;
    char buf[1024], *pchar;
    ssize_t len;
    
    /* release resources when done */
    pthread_detach(pthread_self());
    
    while (1) {
        /* zero buffer */
        pchar = memset((void *)buf,0,1024);

        /* ignore error as it will be caught later */   
        len = recvfrom(threadArg->sockfd, (void *)buf, 1024, 0,
                       (SA *) &threadArg->addr, &(threadArg->len));
/*
printf("Broadcast response from: %s, on port %hu, with msg len = %hd\n",
                inet_ntoa(threadArg->addr.sin_addr),
                ntohs(threadArg->addr.sin_port), len); 
*/
        /* server is sending 3 ints + string in java, len > 13 bytes */
        if (len < 13) continue;

        /* The server is sending back its 1) port, 2) host name length, 3) host name */
        memcpy(&magicInt, pchar, sizeof(int));
        magicInt = ntohl(magicInt);
        pchar += sizeof(int);
        memcpy(&port, pchar, sizeof(int));
        port = ntohl(port);
        pchar += sizeof(int);
        memcpy(&nameLen, pchar, sizeof(int));
        nameLen = ntohl(nameLen);
        pchar += sizeof(int);
/*printf("  magic int = 0x%x, port = %d, len = %d\n", magicInt, port, nameLen);*/
        
        if ((magicInt != 0xc0da1) || 
                (port < 1024 || port > 65535) ||
                (nameLen < 0 || nameLen > 1024-12)) {
            /* wrong format so ignore */
/*printf("  Broadcast response has wrong format\n");*/
            continue;
        }
        if (nameLen != strlen(pchar)) {
            /* wrong format so ignore */
/*printf("  Broadcast response has wrong format, bad host name\n");*/
            continue;
        }
        
        /* send info back to calling function */
        threadArg->buffer = strdup(pchar);
        threadArg->port   = port;
/*printf("  Receiver thread: host = %s, port = %d\n", pchar, port);*/
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
 * every second in order to connect.
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
/*printf("Send broadcast to cMsg server on socket %d\n", threadArg->sockfd);*/
      sendto(threadArg->sockfd, (void *)threadArg->buffer, threadArg->bufferLen, 0,
             (SA *) threadArg->paddr, threadArg->len);
      
      sleep(1);
    }
    
    pthread_exit(NULL);
    return NULL;
}


/*-------------------------------------------------------------------*/


/**
 * This routine is called by cmsg_cmsg_connect and does the real work of
 * connecting to the cMsg name server.
 * 
 * @param domain pointer to connection information structure.
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_OUT_OF_MEMORY if the allocating memory failed
 * @returns CMSG_SOCKET_ERROR if the listening thread finds all the ports it tries
 *                            to listen on are busy, socket options could not be
 *                            set, cannot create or set buffer size for udp socket
 * @returns CMSG_NETWORK_ERROR if no connection to the name or domain servers can be made,
 *                             or a communication error with either server occurs.
 */   
static int connectDirect(cMsgDomainInfo *domain, int failoverIndex) {

  int i, err, serverfd, status, hz, num_try, try_max;
  char *portEnvVariable=NULL;
  unsigned short startingPort;
  cMsgThreadInfo *threadArg;
  struct timespec waitForThread;
  const int size=CMSG_BIGSOCKBUFSIZE; /* bytes */
  struct sockaddr_in  servaddr;
    
  /*
   * First find a port on which to receive incoming messages.
   * Do this by trying to open a listening socket at a given
   * port number. If that doesn't work add one to port number
   * and try again.
   * 
   * But before that, define a port number from which to start looking.
   * If CMSG_PORT is defined, it's the starting port number.
   * If CMSG_PORT is NOT defind, start at CMSG_CLIENT_LISTENING_PORT (2345).
   */

  /* pick starting port number */
  if ( (portEnvVariable = getenv("CMSG_PORT")) == NULL ) {
    startingPort = CMSG_CLIENT_LISTENING_PORT;
    if (cMsgDebug >= CMSG_DEBUG_WARN) {
      fprintf(stderr, "connectDirect: cannot find CMSG_PORT env variable, first try port %hu\n", startingPort);
    }
  }
  else {
    i = atoi(portEnvVariable);
    if (i < 1025 || i > 65535) {
      startingPort = CMSG_CLIENT_LISTENING_PORT;
      if (cMsgDebug >= CMSG_DEBUG_WARN) {
        fprintf(stderr, "connectDirect: CMSG_PORT contains a bad port #, first try port %hu\n", startingPort);
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
    return(err);
  }

  /* launch pend thread and start listening on receive socket */
  threadArg = (cMsgThreadInfo *) malloc(sizeof(cMsgThreadInfo));
  if (threadArg == NULL) {
      return(CMSG_OUT_OF_MEMORY);  
  }
  threadArg->isRunning   = 0;
  threadArg->thd0started = 0;
  threadArg->thd1started = 0;
  threadArg->listenFd    = domain->listenSocket;
  threadArg->blocking    = CMSG_NONBLOCKING;
  threadArg->domain      = domain;
  threadArg->domainType  = strdup("cmsg");
  
  /* Block SIGPIPE for this and all spawned threads. */
  cMsgBlockSignals(domain);

  status = pthread_create(&domain->pendThread, NULL,
                          cMsgClientListeningThread, (void *) threadArg);
  if (status != 0) {
    cmsg_err_abort(status, "Creating message listening thread");
  }
  
  /*
   * Wait for flag to indicate thread is actually running before
   * continuing on. This thread must be running before we talk to
   * the name server since the server tries to communicate with
   * the listening thread.
   */
   
#ifdef VXWORKS
  hz = sysClkRateGet();
#else
  /* get system clock rate - probably 100 Hz */
  hz = sysconf(_SC_CLK_TCK);
  if (hz < 0) hz = 100;
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
      fprintf(stderr, "connectDirect, cannot start listening thread\n");
    }
    exit(-1);
  }
       
  /* threadArg is used in cMsgClientListeningThread and its cleanup handler,
   * so do NOT free its memory!
   */
  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: created listening thread\n");
  }
  
  /*---------------------------------------------------------------*/
  /* connect & talk to cMsg name server to check if name is unique */
  /*---------------------------------------------------------------*/
    
  /* first connect to server host & port (default send & rcv buf sizes) */
  if ( (err = cMsgTcpConnect(domain->failovers[failoverIndex].nameServerHost,
                             (unsigned short) domain->failovers[failoverIndex].nameServerPort,
                             0, 0, &serverfd)) != CMSG_OK) {
    cMsgRestoreSignals(domain);
    /* stop listening & connection threads */
    pthread_cancel(domain->pendThread);
    return(err);
  }
  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: connected to name server\n");
  }
  
  /* get host & port (domain->sendHost,sendPort) to send messages to */
  err = talkToNameServer(domain, serverfd, failoverIndex);
  if (err != CMSG_OK) {
    cMsgRestoreSignals(domain);
    close(serverfd);
    pthread_cancel(domain->pendThread);
    return(err);
  }
  
  /* BUGBUG free up memory allocated in parseUDL & no longer needed */

  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: got host and port from name server\n");
  }
  
  /* done talking to server */
  close(serverfd);
 
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: closed name server socket\n");
    fprintf(stderr, "connectDirect: sendHost = %s, sendPort = %d\n",
                             domain->sendHost,
                             domain->sendPort);
  }
  
  /* create receiving socket and store (default send & rcv buf sizes) */
  if ( (err = cMsgTcpConnect(domain->sendHost,
                             (unsigned short) domain->sendPort,
                              0, 0, &domain->receiveSocket)) != CMSG_OK) {
    cMsgRestoreSignals(domain);
    pthread_cancel(domain->pendThread);
    return(err);
  }

  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: created receiving socket fd = %d\n", domain->receiveSocket);
  }
    
  /* create keep alive socket and store (default send & rcv buf sizes) */
  if ( (err = cMsgTcpConnect(domain->sendHost,
                             (unsigned short) domain->sendPort,
                              0, 0, &domain->keepAliveSocket)) != CMSG_OK) {
    cMsgRestoreSignals(domain);
    close(domain->receiveSocket);
    pthread_cancel(domain->pendThread);
    return(err);
  }
  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: created keepalive socket fd = %d\n",domain->keepAliveSocket );
  }
  
  /* create thread to send periodic keep alives and handle dead server */
  status = pthread_create(&domain->keepAliveThread, NULL,
                          keepAliveThread, (void *)domain);
  if (status != 0) {
    cmsg_err_abort(status, "Creating keep alive thread");
  }
     
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "connectDirect: created keep alive thread\n");
  }

  /* create sending socket and store (default rcv buf, 128K send buf) */
  if ( (err = cMsgTcpConnect(domain->sendHost,
                             (unsigned short) domain->sendPort,
                              CMSG_BIGSOCKBUFSIZE, 0, &domain->sendSocket)) != CMSG_OK) {
    cMsgRestoreSignals(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    pthread_cancel(domain->pendThread);
    return(err);
  }

  /* create sending UDP socket */
  if ((domain->sendUdpSocket = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
    cMsgRestoreSignals(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    pthread_cancel(domain->pendThread);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "connectDirect: socket error, %s\n", strerror(errno));
    return(CMSG_SOCKET_ERROR);
  }

  /* set send buffer size */
  err = setsockopt(domain->sendUdpSocket, SOL_SOCKET, SO_SNDBUF, (char*) &size, sizeof(size));
  if (err < 0) {
    cMsgRestoreSignals(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    close(domain->sendUdpSocket);
    pthread_cancel(domain->pendThread);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "connectDirect: setsockopt error\n");
    return(CMSG_SOCKET_ERROR);
  }

  memset((void *)&servaddr, 0, sizeof(servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_port   = htons(domain->sendUdpPort);

  if ( (err = cMsgStringToNumericIPaddr(domain->sendHost, &servaddr)) != CMSG_OK ) {
    cMsgRestoreSignals(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    close(domain->sendUdpSocket);
    pthread_cancel(domain->pendThread);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "connectDirect: host name error\n");
    return(CMSG_SOCKET_ERROR);
  }

  err = connect(domain->sendUdpSocket, (SA *) &servaddr, (socklen_t) sizeof(servaddr));
  if (err < 0) {
    cMsgRestoreSignals(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    close(domain->sendUdpSocket);
    pthread_cancel(domain->pendThread);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "connectDirect: UDP connect error\n");
    return(CMSG_SOCKET_ERROR);
  }

  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine is called by the keepAlive thread upon the death of the
 * cMsg server in an attempt to failover to another server whose UDL was
 * given in the original call to connect(). 
 * 
 * @param domain pointer to connection info structure
 * @param failoverIndex index into the array of parsed UDLs to which
 *                      a connection is to be attempted with this function
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_OUT_OF_MEMOERY if the allocating memory failed
 * @returns CMSG_SOCKET_ERROR if socket options could not be set
 * @returns CMSG_NETWORK_ERROR if no connection to the name or domain servers can be made,
 *                             or a communication error with either server occurs.
 */   
static int reconnect(cMsgDomainInfo *domain, int failoverIndex) {

  int i, err, serverfd, status;
  struct timespec waitForThread = {0,500000000};
  getInfo *info;
  const int size=CMSG_BIGSOCKBUFSIZE; /* bytes */
  struct sockaddr_in  servaddr;
    
  cMsgConnectWriteLock(domain);  

  /*--------------------------------------------------------------------*/
  /* Connect to cMsg name server to check if server can be connected to.
   * If not, don't waste any more time and try the next one.            */
  /*--------------------------------------------------------------------*/
  /* connect to server host & port */
  if ( (err = cMsgTcpConnect(domain->failovers[failoverIndex].nameServerHost,
                             (unsigned short) domain->failovers[failoverIndex].nameServerPort,
                             0, 0, &serverfd)) != CMSG_OK) {
    cMsgConnectWriteUnlock(domain);
    return(err);
  }  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "reconnect: connected to name server\n");
  }

  /* The thread listening for TCP connections needs to keep running.
   * Keep all existing callback threads for the subscribes. */

  /* wakeup all sendAndGets - they can't be saved */
  for (i=0; i<CMSG_MAX_SEND_AND_GET; i++) {
    
      info = &domain->sendAndGetInfo[i];

      if (info->active != 1) {
        continue;
      }
    
      info->msg = NULL;
      info->msgIn = 1;
      info->quit  = 1;
      info->error = CMSG_SERVER_DIED;

      /* wakeup the sendAndGet */
      status = pthread_cond_signal(&info->cond);
      if (status != 0) {
        cmsg_err_abort(status, "Failed get condition signal");
      }
  }


  /* wakeup all existing subscribeAndGets - they can't be saved */
  for (i=0; i<CMSG_MAX_SUBSCRIBE_AND_GET; i++) {
    
      info = &domain->subscribeAndGetInfo[i];

      if (info->active != 1) {
        continue;
      }

      info->msg = NULL;
      info->msgIn = 1;
      info->quit  = 1;
      info->error = CMSG_SERVER_DIED;

      /* wakeup the subscribeAndGet */      
      status = pthread_cond_signal(&info->cond);
      if (status != 0) {
        cmsg_err_abort(status, "Failed get condition signal");
      }
      
  }           

  /* shutdown extra listening threads, do nothing if there's an error */
  
  /* First, cancel the keepAlive responding thread. */
  pthread_cancel(domain->clientThread[1]);
  
  /* Second, GRACEFULLY shutdown the thread which receives messages.
   * This thread may be cancelled only if it's not blocked in a
   * pthread_cond_wait (which it will be if any callback's cue is
   * full and cMsgRunCallbacks tries to add another.
   * To shut it down correctly, set a flag and wake up it in case 
   * it's waiting. Give it 1/2 second and then just cancel it.
   * It is somewhat of a brute force method but it should work.
   */
  domain->killClientThread = 1;
  status = pthread_cond_signal(&domain->subscribeCond);
  if (status != 0) {
    cmsg_err_abort(status, "Failed callback condition signal");
  }
  nanosleep(&waitForThread, NULL);
  pthread_cancel(domain->clientThread[0]);
  domain->killClientThread = 0;
  
  
  /*-----------------------------------------------------*/
  /* talk to cMsg name server to check if name is unique */
  /*-----------------------------------------------------*/
  /* get host & port (domain->sendHost,sendPort) to send messages to */
  err = talkToNameServer(domain, serverfd, failoverIndex);
  if (err != CMSG_OK) {
    cMsgConnectWriteUnlock(domain);
    close(serverfd);
    return(err);
  }
  
/* BUGBUG free up memory allocated in parseUDL & no longer needed */

  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "reconnect: got host and port from name server\n");
  }
  
  /* done talking to server */
  close(serverfd);
 
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "reconnect: closed name server socket\n");
    fprintf(stderr, "reconnect: sendHost = %s, sendPort = %d\n",
                            domain->sendHost,
                            domain->sendPort);
  }
  
  /* create receiving socket and store */
  if ( (err = cMsgTcpConnect(domain->sendHost,
                             (unsigned short) domain->sendPort,
                             0, 0, &domain->receiveSocket)) != CMSG_OK) {
    cMsgConnectWriteUnlock(domain);
    return(err);
  }

  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "reconnect: created receiving socket fd = %d\n", domain->receiveSocket);
  }
    
  /* create keep alive socket and store */
  if ( (err = cMsgTcpConnect(domain->sendHost,
                             (unsigned short) domain->sendPort,
                             0, 0, &domain->keepAliveSocket)) != CMSG_OK) {
    cMsgConnectWriteUnlock(domain);
    close(domain->receiveSocket);
    return(err);
  }
  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "reconnect: created keepalive socket fd = %d\n",domain->keepAliveSocket );
  }
  
  /* Do not create another keepalive thread as we already got one.
   * But we gotta use the new socket as the old socket is to the
   * old server which is gone now.
   */

  /* create sending socket and store */
  if ( (err = cMsgTcpConnect(domain->sendHost,
                             (unsigned short) domain->sendPort,
                             CMSG_BIGSOCKBUFSIZE, 0, &domain->sendSocket)) != CMSG_OK) {
    cMsgConnectWriteUnlock(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    return(err);
  }
   
  /* create sending UDP socket */
  if ((domain->sendUdpSocket = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
      close(domain->keepAliveSocket);
      close(domain->receiveSocket);
      close(domain->sendSocket);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "reconnect: socket error, %s\n", strerror(errno));
      return(CMSG_SOCKET_ERROR);
  }

  /* set send buffer size */
  err = setsockopt(domain->sendUdpSocket, SOL_SOCKET, SO_SNDBUF, (char*) &size, sizeof(size));
  if (err < 0) {
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    close(domain->sendUdpSocket);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "reconnect: setsockopt error\n");
    return(CMSG_SOCKET_ERROR);
  }

  memset((void *)&servaddr, 0, sizeof(servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_port   = htons(domain->sendUdpPort);

  if ( (err = cMsgStringToNumericIPaddr(domain->sendHost, &servaddr)) != CMSG_OK ) {
    cMsgRestoreSignals(domain);
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    close(domain->sendUdpSocket);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "reconnect: host name error\n");
    return(CMSG_SOCKET_ERROR);
  }

  err = connect(domain->sendUdpSocket, (SA *) &servaddr, (socklen_t) sizeof(servaddr));
  if (err < 0) {
    close(domain->keepAliveSocket);
    close(domain->receiveSocket);
    close(domain->sendSocket);
    close(domain->sendUdpSocket);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) fprintf(stderr, "reconnect: UDP connect error\n");
    return(CMSG_SOCKET_ERROR);
  }
  
  cMsgConnectWriteUnlock(domain);
  
/* printf("reconnect END\n"); */
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends a msg to the specified cMsg domain server. It is called
 * by the user through cMsgSend() given the appropriate UDL. It is asynchronous
 * and should rarely block. It will only block if the TCP protocol is used and
 * the cMsg domain server has
 * reached it maximum number of request-handling threads and each of those threads
 * has a cue which is completely full. In this domain cMsgFlush() does nothing and
 * does not need to be called for the message to be sent immediately.<p>
 *
 * This version of this routine uses writev to write all data in one write call.
 * Another version was tried with many writes (one for ints and one for each
 * string), but the performance died sharply.
 *
 * @param domainId id of the domain connection
 * @param vmsg pointer to a message structure
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id or message argument is null or has null subject or type
 * @returns CMSG_OUT_OF_RANGE  if the message is too large to be sent by UDP
 * @returns CMSG_OUT_OF_MEMORY if the allocating memory for message buffer failed
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement sending
 *                               messages
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_cmsg_send(void *domainId, const void *vmsg) {
  
  int err, len, lenSubject, lenType, lenCreator, lenText, lenByteArray;
  int fd, highInt, lowInt, outGoing[16];
  ssize_t sendLen;
  cMsgMessage_t *msg = (cMsgMessage_t *) vmsg;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  char *creator;
  uint64_t llTime;
  struct timespec now;

  
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
    
  if (!domain->hasSend) {
    return(CMSG_NOT_IMPLEMENTED);
  }
  
  /* check args */
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  if ( (cMsgCheckString(msg->subject) != CMSG_OK ) ||
       (cMsgCheckString(msg->type)    != CMSG_OK )    ) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  if (!msg->context.udpSend) {
    fd = domain->sendSocket;  
  }
  else {
    fd = domain->sendUdpSocket;
  }
  
  tryagain:
  while (1) {
    err = CMSG_OK;
    
    /* Cannot run this while connecting/disconnecting */
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      err = CMSG_LOST_CONNECTION;
      break;
    }

    if (msg->text == NULL) {
      lenText = 0;
    }
    else {
      lenText = strlen(msg->text);
    }

    /* message id (in network byte order) to domain server */
    outGoing[1] = htonl(CMSG_SEND_REQUEST);
    /* reserved for future use */
    outGoing[2] = 0;
    /* user int */
    outGoing[3] = htonl(msg->userInt);
    /* system msg id */
    outGoing[4] = htonl(msg->sysMsgId);
    /* sender token */
    outGoing[5] = htonl(msg->senderToken);
    /* bit info */
    outGoing[6] = htonl(msg->info);

    /* time message sent (right now) */
    clock_gettime(CLOCK_REALTIME, &now);
    /* convert to milliseconds */
    llTime  = ((uint64_t)now.tv_sec * 1000) +
              ((uint64_t)now.tv_nsec/1000000);
    highInt = (int) ((llTime >> 32) & 0x00000000FFFFFFFF);
    lowInt  = (int) (llTime & 0x00000000FFFFFFFF);
    outGoing[7] = htonl(highInt);
    outGoing[8] = htonl(lowInt);

    /* user time */
    llTime  = ((uint64_t)msg->userTime.tv_sec * 1000) +
              ((uint64_t)msg->userTime.tv_nsec/1000000);
    highInt = (int) ((llTime >> 32) & 0x00000000FFFFFFFF);
    lowInt  = (int) (llTime & 0x00000000FFFFFFFF);
    outGoing[9]  = htonl(highInt);
    outGoing[10] = htonl(lowInt);

    /* length of "subject" string */
    lenSubject   = strlen(msg->subject);
    outGoing[11] = htonl(lenSubject);
    /* length of "type" string */
    lenType      = strlen(msg->type);
    outGoing[12] = htonl(lenType);

    /* send creator (this sender's name if msg created here) */
    creator = msg->creator;
    if (creator == NULL) creator = domain->name;
    /* length of "creator" string */
    lenCreator   = strlen(creator);
    outGoing[13] = htonl(lenCreator);

    /* length of "text" string */
    outGoing[14] = htonl(lenText);

    /* length of byte array */
    lenByteArray = msg->byteArrayLength;
    outGoing[15] = htonl(lenByteArray);

    /* total length of message (minus first int) is first item sent */
    len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType +
          lenCreator + lenText + lenByteArray;
    outGoing[0] = htonl(len);

    if (msg->context.udpSend && len > 8192) {
      cMsgConnectReadUnlock(domain);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        printf("cmsg_cmsg_send: messges is too big for UDP packet\n");
      }
      return(CMSG_OUT_OF_RANGE);
    }

    /* Make send socket communications thread-safe. That
     * includes protecting the one buffer being used.
     */
    cMsgSocketMutexLock(domain);

    /* allocate more memory for message-sending buffer if necessary */
    if (domain->msgBufferSize < (int)(len+sizeof(int))) {
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
    memcpy(domain->msgBuffer+len, (void *)msg->subject, lenSubject);
    len += lenSubject;
    memcpy(domain->msgBuffer+len, (void *)msg->type, lenType);
    len += lenType;
    memcpy(domain->msgBuffer+len, (void *)creator, lenCreator);
    len += lenCreator;
    memcpy(domain->msgBuffer+len, (void *)msg->text, lenText);
    len += lenText;
    memcpy(domain->msgBuffer+len, (void *)&((msg->byteArray)[msg->byteArrayOffset]), lenByteArray);
    len += lenByteArray;   
    
    if (!msg->context.udpSend) {
      /* send data over TCP socket */
      sendLen = cMsgTcpWrite(fd, (void *) domain->msgBuffer, len);
      if (sendLen == len) domain->monData.numTcpSends++;
    }
    else {
      /* send data over UDP socket */
      sendLen = send(fd, (void *)domain->msgBuffer, len, 0);      
      if (sendLen == len) domain->monData.numUdpSends++;
    }
    if (sendLen != len) {
      cMsgSocketMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_send: write failure\n");
      }
      err = CMSG_NETWORK_ERROR;
      break;
    }

    /* done protecting communications */
    cMsgSocketMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);
    break;

  }
  
  if (err!= CMSG_OK) {
    /* don't wait for resubscribes */
    if (failoverSuccessful(domain, 0)) {
       fd = domain->sendSocket;
       printf("cmsg_cmsg_send: FAILOVER SUCCESSFUL, try send again\n");
       goto tryagain;
    }  
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      printf("cmsg_cmsg_send: FAILOVER NOT successful, quitting, err = %d\n", err);
    }
  }
  
  return(err);
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends a msg to the specified domain server and receives a response.
 * It is a synchronous routine and as a result blocks until it receives a status
 * integer from the cMsg server. It is called by the user through cMsgSyncSend()
 * given the appropriate UDL. In this domain cMsgFlush() does nothing and
 * does not need to be called for the message to be sent immediately.
 *
 * @param domainId id of the domain connection
 * @param vmsg pointer to a message structure
 * @param timeout amount of time to wait for the response
 * @param response integer pointer that gets filled with the server's response
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id or message argument is null or has null subject or type
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement the
 *                               synchronous sending of messages
 * @returns CMSG_OUT_OF_MEMORY if the allocating memory for message buffer failed
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_cmsg_syncSend(void *domainId, const void *vmsg, const struct timespec *timeout, int *response) {
  
  int err, len, lenSubject, lenType, lenCreator, lenText, lenByteArray;
  int fd, fdIn, highInt, lowInt, outGoing[16];
  cMsgMessage_t *msg = (cMsgMessage_t *) vmsg;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  char *creator;
  uint64_t llTime;
  struct timespec now;

    
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd   = domain->sendSocket;
  fdIn = domain->receiveSocket;
  
  if (!domain->hasSyncSend) {
    return(CMSG_NOT_IMPLEMENTED);
  }
 
  /* check args */
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  if ( (cMsgCheckString(msg->subject) != CMSG_OK ) ||
       (cMsgCheckString(msg->type)    != CMSG_OK )    ) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  tryagain:
  while (1) {
    err = CMSG_OK;

    /* Cannot run this while connecting/disconnecting */
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      err = CMSG_LOST_CONNECTION;
      break;
    }

    if (msg->text == NULL) {
      lenText = 0;
    }
    else {
      lenText = strlen(msg->text);
    }

    /* message id (in network byte order) to domain server */
    outGoing[1] = htonl(CMSG_SYNC_SEND_REQUEST);
    /* reserved */
    outGoing[2] = 0;
    /* user int */
    outGoing[3] = htonl(msg->userInt);
    /* system msg id */
    outGoing[4] = htonl(msg->sysMsgId);
    /* sender token */
    outGoing[5] = htonl(msg->senderToken);
    /* bit info */
    outGoing[6] = htonl(msg->info);

    /* time message sent (right now) */
    clock_gettime(CLOCK_REALTIME, &now);
    /* convert to milliseconds */
    llTime  = ((uint64_t)now.tv_sec * 1000) + ((uint64_t)now.tv_nsec/1000000);
    highInt = (int) ((llTime >> 32) & 0x00000000FFFFFFFF);
    lowInt  = (int) (llTime & 0x00000000FFFFFFFF);
    outGoing[7] = htonl(highInt);
    outGoing[8] = htonl(lowInt);

    /* user time */
    llTime  = ((uint64_t)msg->userTime.tv_sec * 1000) +
              ((uint64_t)msg->userTime.tv_nsec/1000000);
    highInt = (int) ((llTime >> 32) & 0x00000000FFFFFFFF);
    lowInt  = (int) (llTime & 0x00000000FFFFFFFF);
    outGoing[9]  = htonl(highInt);
    outGoing[10] = htonl(lowInt);

    /* length of "subject" string */
    lenSubject   = strlen(msg->subject);
    outGoing[11]  = htonl(lenSubject);
    /* length of "type" string */
    lenType      = strlen(msg->type);
    outGoing[12] = htonl(lenType);

    /* send creator (this sender's name if msg created here) */
    creator = msg->creator;
    if (creator == NULL) creator = domain->name;
    /* length of "creator" string */
    lenCreator   = strlen(creator);
    outGoing[13] = htonl(lenCreator);

    /* length of "text" string */
    outGoing[14] = htonl(lenText);

    /* length of byte array */
    lenByteArray = msg->byteArrayLength;
    outGoing[15] = htonl(lenByteArray);

    /* total length of message (minus first int) is first item sent */
    len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType +
          lenCreator + lenText + lenByteArray;
    outGoing[0] = htonl(len);

    /* make syncSends be synchronous 'cause we need a reply */
    cMsgSyncSendMutexLock(domain);

    /* make send socket communications thread-safe */
    cMsgSocketMutexLock(domain);

    /* allocate more memory for message-sending buffer if necessary */
    if (domain->msgBufferSize < (int)(len+sizeof(int))) {
      free(domain->msgBuffer);
      domain->msgBufferSize = len + 1004; /* give us 1kB extra */
      domain->msgBuffer = (char *) malloc(domain->msgBufferSize);
      if (domain->msgBuffer == NULL) {
        cMsgSocketMutexUnlock(domain);
        cMsgSyncSendMutexUnlock(domain);
        cMsgConnectReadUnlock(domain);
        if (cMsgDebug >= CMSG_DEBUG_ERROR) {
          fprintf(stderr, "cmsg_cmsg_syncSend: out of memory\n");
        }
        return(CMSG_OUT_OF_MEMORY);
      }
    }

    /* copy data into a single static buffer */
    memcpy(domain->msgBuffer, (void *)outGoing, sizeof(outGoing));
    len = sizeof(outGoing);
    memcpy(domain->msgBuffer+len, (void *)msg->subject, lenSubject);
    len += lenSubject;
    memcpy(domain->msgBuffer+len, (void *)msg->type, lenType);
    len += lenType;
    memcpy(domain->msgBuffer+len, (void *)creator, lenCreator);
    len += lenCreator;
    memcpy(domain->msgBuffer+len, (void *)msg->text, lenText);
    len += lenText;
    memcpy(domain->msgBuffer+len, (void *)&((msg->byteArray)[msg->byteArrayOffset]), lenByteArray);
    len += lenByteArray;   

    /* send data over socket */
    if (cMsgTcpWrite(fd, (void *) domain->msgBuffer, len) != len) {
      cMsgSocketMutexUnlock(domain);
      cMsgSyncSendMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_syncSend: write failure\n");
      }
      err = CMSG_NETWORK_ERROR;
      break;
    }

    /* done protecting outgoing communications */
    cMsgSocketMutexUnlock(domain);

    /* now read reply */
    if (cMsgTcpRead(fdIn, (void *) &err, sizeof(err)) != sizeof(err)) {
      cMsgSyncSendMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_syncSend: read failure\n");
      }
      err = CMSG_NETWORK_ERROR;
      break;
    }

    domain->monData.numSyncSends++;
    
    cMsgSyncSendMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);
    break;
  }
  
  
  if (err!= CMSG_OK) {
    /* don't wait for resubscribes */
    if (failoverSuccessful(domain, 0)) {
       fd = domain->sendSocket;
/*printf("cmsg_cmsg_syncSend: FAILOVER SUCCESSFUL, try suncSend again\n");*/
       goto tryagain;
    }  
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      printf("cmsg_cmsg_syncSend: FAILOVER NOT successful, quitting, err = %d\n", err);
    }
  }
  
      

  /* return domain server's reply */  
  *response = ntohl(err);  
  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine gets one message from a one-shot subscription to the given
 * subject and type. It is called by the user through cMsgSubscribeAndGet()
 * given the appropriate UDL. In this domain cMsgFlush() does nothing and
 * does not need to be called for the subscription to be started immediately.
 *
 * @param domainId id of the domain connection
 * @param subject subject of message subscribed to
 * @param type type of message subscribed to
 * @param timeout amount of time to wait for the message; if NULL, wait forever
 * @param replyMsg message received
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the domainId, subject, type, or replyMsg arguments are null
 * @returns CMSG_TIMEOUT if routine received no message in the specified time
 * @returns CMSG_OUT_OF_MEMORY if all available subscription memory has been used
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement
 *                               subscribeAndGet
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_cmsg_subscribeAndGet(void *domainId, const char *subject, const char *type,
                              const struct timespec *timeout, void **replyMsg) {
                             
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  int i, err, fd, uniqueId, status, len, lenSubject, lenType,gotSpot, outGoing[6];
  getInfo *info = NULL;
  struct timespec wait;
  struct iovec iov[3];
  

  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  if (!domain->hasSubscribeAndGet) {
    return(CMSG_NOT_IMPLEMENTED);
  }   
           
  /* check args */
  if (replyMsg == NULL) return(CMSG_BAD_ARGUMENT);
  
  if ( (cMsgCheckString(subject) != CMSG_OK ) ||
       (cMsgCheckString(type)    != CMSG_OK )    ) {
    return(CMSG_BAD_ARGUMENT);
  }

  cMsgConnectReadLock(domain);

  if (domain->gotConnection != 1) {
    cMsgConnectReadUnlock(domain);
    return(CMSG_LOST_CONNECTION);
  }
  
  /*
   * Pick a unique identifier for the subject/type pair, and
   * send it to the domain server & remember it for future use
   * Mutex protect this operation as many cmsg_cmsg_connect calls may
   * operate in parallel on this static variable.
   */
  staticMutexLock();
  uniqueId = subjectTypeId++;
  staticMutexUnlock();

  /* make new entry and notify server */
  gotSpot = 0;

  for (i=0; i<CMSG_MAX_SUBSCRIBE_AND_GET; i++) {
    if (domain->subscribeAndGetInfo[i].active != 0) {
      continue;
    }

    info = &domain->subscribeAndGetInfo[i];
    info->id      = uniqueId;
    info->active  = 1;
    info->error   = CMSG_OK;
    info->msgIn   = 0;
    info->quit    = 0;
    info->msg     = NULL;
    info->subject = (char *) strdup(subject);
    info->type    = (char *) strdup(type);
    gotSpot = 1;
    break;
  } 

  if (!gotSpot) {
    cMsgConnectReadUnlock(domain);
    free(info->subject);
    free(info->type);
    info->subject = NULL;
    info->type    = NULL;
    info->active  = 0;
    return(CMSG_OUT_OF_MEMORY);
  }
  
  /* notify domain server */

  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_SUBSCRIBE_AND_GET_REQUEST);
  /* unique id for receiverSubscribeId */
  outGoing[2] = htonl(uniqueId);  
  /* length of "subject" string */
  lenSubject  = strlen(subject);
  outGoing[3] = htonl(lenSubject);
  /* length of "type" string */
  lenType     = strlen(type);
  outGoing[4] = htonl(lenType);
  /* length of "namespace" string (0 in this case, since
   * only used for server-to-server) */
  outGoing[5] = htonl(0);

  /* total length of message (minus first int) is first item sent */
  len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType;
  outGoing[0] = htonl(len);
  
  iov[0].iov_base = (char*) outGoing;
  iov[0].iov_len  = sizeof(outGoing);
  
  iov[1].iov_base = (char*) subject;
  iov[1].iov_len  = lenSubject;
  
  iov[2].iov_base = (char*) type;
  iov[2].iov_len  = lenType;
  
  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);
  
  if (cMsgTcpWritev(fd, iov, 3, 16) == -1) {
    cMsgSocketMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);
    free(info->subject);
    free(info->type);
    info->subject = NULL;
    info->type    = NULL;
    info->active  = 0;
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "get: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
  
  /* done protecting communications */
  cMsgSocketMutexUnlock(domain);
  cMsgConnectReadUnlock(domain);
  
  /* Now ..., wait for asynchronous response */
  
  /* lock mutex */
  status = pthread_mutex_lock(&info->mutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed callback mutex lock");
  }

  domain->monData.subAndGets++;
  domain->monData.numSubAndGets++;

  /* wait while there is no message */
  while (info->msgIn == 0) {
    /* wait until signaled */
    if (timeout == NULL) {
      status = pthread_cond_wait(&info->cond, &info->mutex);
    }
    /* wait until signaled or timeout */
    else {
      cMsgGetAbsoluteTime(timeout, &wait);
      status = pthread_cond_timedwait(&info->cond, &info->mutex, &wait);
    }
    
    if (status == ETIMEDOUT) {
      break;
    }
    else if (status != 0) {
      cmsg_err_abort(status, "Failed callback cond wait");
    }

    /* quit if commanded to */
    if (info->quit) {
      break;
    }
  }
  
  domain->monData.subAndGets--;
  
  /* unlock mutex */
  status = pthread_mutex_unlock(&info->mutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed callback mutex unlock");
  }

  /* If we timed out, tell server to forget the get. */
  if (info->msgIn == 0) {
      /*printf("get: timed out\n");*/
      
      /* remove the get from server */
      unSubscribeAndGet(domainId, subject, type, uniqueId);
      *replyMsg = NULL;
      err = CMSG_TIMEOUT;
  }
  /* If we've been woken up with an error condition ... */
  else if (info->error != CMSG_OK) {
      *replyMsg = NULL;
      err = info->error;    
  }
  /* If we did not timeout and everything's OK */
  else {
      /*
       * Don't need to make a copy of message as only 1 receipient.
       * Message was allocated in client's listening thread and user
       * must free it.
       */
      *replyMsg = info->msg;
      err = CMSG_OK;
  }
  
  /* free up memory */
  free(info->subject);
  free(info->type);
  info->subject = NULL;
  info->type    = NULL;
  info->msg     = NULL;
  info->active  = 0;

  /*printf("get: SUCCESS!!!\n");*/

  return(err);
}


/*-------------------------------------------------------------------*/


/**
 * This routine tells the cMsg server to "forget" about the cMsgSubscribeAndGet()
 * call (specified by the id argument) since a timeout occurred. Internal use
 * only.
 *
 * @param domainId id of the domain connection
 * @param subject subject of message subscribed to
 * @param type type of message subscribed to
 * @param id unique id associated with a subscribeAndGet
 *
 * @returns CMSG_BAD_ARGUMENT if the domain id is null
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 */   
static int unSubscribeAndGet(void *domainId, const char *subject, const char *type, int id) {
  
  int fd, len, outGoing[6], lenSubject, lenType;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  struct iovec iov[3];

  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_UNSUBSCRIBE_AND_GET_REQUEST);
  /* receiverSubscribe */
  outGoing[2] = htonl(id);
  /* length of "subject" string */
  lenSubject  = strlen(subject);
  outGoing[3] = htonl(lenSubject);
  /* length of "type" string */
  lenType     = strlen(type);
  outGoing[4] = htonl(lenType);
  /* length of "namespace" string (0 in this case, since
   * only used for server-to-server) */
  outGoing[5] = htonl(0);

  /* total length of message (minus first int) is first item sent */
  len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType;
  outGoing[0] = htonl(len);

  iov[0].iov_base = (char*) outGoing;
  iov[0].iov_len  = sizeof(outGoing);

  iov[1].iov_base = (char*) subject;
  iov[1].iov_len  = lenSubject;

  iov[2].iov_base = (char*) type;
  iov[2].iov_len  = lenType;

  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);

  if (cMsgTcpWritev(fd, iov, 3, 16) == -1) {
    cMsgSocketMutexUnlock(domain);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "unSubscribeAndGet: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }

  cMsgSocketMutexUnlock(domain);

  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine gets one message from another cMsg client by sending out
 * an initial message to that responder. It is a synchronous routine that
 * fails when no reply is received with the given timeout. This function
 * can be thought of as a peer-to-peer exchange of messages.
 * One message is sent to all listeners. The first responder
 * to the initial message will have its single response message sent back
 * to the original sender. This routine is called by the user through
 * cMsgSendAndGet() given the appropriate UDL. In this domain cMsgFlush()
 * does nothing and does not need to be called for the mesage to be
 * sent immediately.
 *
 * @param domainId id of the domain connection
 * @param sendMsg messages to send to all listeners
 * @param timeout amount of time to wait for the response message; if NULL,
 *                wait forever
 * @param replyMsg message received from the responder
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id, sendMsg or replyMsg arguments or the
 *                            sendMsg's subject or type are null
 * @returns CMSG_TIMEOUT if routine received no message in the specified time
 * @returns CMSG_OUT_OF_MEMORY if all available sendAndGet memory has been used
 *                             or allocating memory for message buffer failed
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement
 *                               sendAndGet
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_cmsg_sendAndGet(void *domainId, const void *sendMsg, const struct timespec *timeout,
                      void **replyMsg) {
  
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  cMsgMessage_t *msg = (cMsgMessage_t *) sendMsg;
  int i, err, uniqueId, status;
  int len, lenSubject, lenType, lenCreator, lenText, lenByteArray;
  int gotSpot, fd, highInt, lowInt, outGoing[16];
  getInfo *info = NULL;
  struct timespec wait;
  char *creator;
  uint64_t llTime;
  struct timespec now;

  
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  if (!domain->hasSendAndGet) {
    return(CMSG_NOT_IMPLEMENTED);
  }   
           
  /* check args */
  if (sendMsg == NULL || replyMsg == NULL) return(CMSG_BAD_ARGUMENT);
  
  if ( (cMsgCheckString(msg->subject) != CMSG_OK ) ||
       (cMsgCheckString(msg->type)    != CMSG_OK )    ) {
    return(CMSG_BAD_ARGUMENT);
  }

  cMsgConnectReadLock(domain);

  if (domain->gotConnection != 1) {
    cMsgConnectReadUnlock(domain);
    return(CMSG_LOST_CONNECTION);
  }
 
  if (msg->text == NULL) {
    lenText = 0;
  }
  else {
    lenText = strlen(msg->text);
  }
  
  /*
   * Pick a unique identifier for the subject/type pair, and
   * send it to the domain server & remember it for future use
   * Mutex protect this operation as many cmsg_cmsg_connect calls may
   * operate in parallel on this static variable.
   */
  staticMutexLock();
  uniqueId = subjectTypeId++;
  staticMutexUnlock();

  /* make new entry and notify server */
  gotSpot = 0;

  for (i=0; i<CMSG_MAX_SEND_AND_GET; i++) {
    if (domain->sendAndGetInfo[i].active != 0) {
      continue;
    }

    info = &domain->sendAndGetInfo[i];
    info->id      = uniqueId;
    info->active  = 1;
    info->error   = CMSG_OK;
    info->msgIn   = 0;
    info->quit    = 0;
    info->msg     = NULL;
    info->subject = (char *) strdup(msg->subject);
    info->type    = (char *) strdup(msg->type);
    gotSpot = 1;
    break;
  }

  if (!gotSpot) {
    cMsgConnectReadUnlock(domain);
    /* free up memory */
    free(info->subject);
    free(info->type);
    info->subject = NULL;
    info->type    = NULL;
    info->active  = 0;
    return(CMSG_OUT_OF_MEMORY);
  }
  
  /* notify domain server */

  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_SEND_AND_GET_REQUEST);
  /* reserved */
  outGoing[2] = 0;
  /* user int */
  outGoing[3] = htonl(msg->userInt);
  /* unique id (senderToken) */
  outGoing[4] = htonl(uniqueId);
  /* bit info */
  outGoing[5] = htonl(msg->info | CMSG_IS_GET_REQUEST);

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

  /* length of "subject" string */
  lenSubject   = strlen(msg->subject);
  outGoing[10] = htonl(lenSubject);
  /* length of "type" string */
  lenType      = strlen(msg->type);
  outGoing[11] = htonl(lenType);
  
  /* namespace length */
  outGoing[12] = htonl(0);
  
  /* send creator (this sender's name if msg created here) */
  creator = msg->creator;
  if (creator == NULL) creator = domain->name;
  /* length of "creator" string */
  lenCreator   = strlen(creator);
  outGoing[13] = htonl(lenCreator);
  
  /* length of "text" string */
  outGoing[14] = htonl(lenText);
  
  /* length of byte array */
  lenByteArray = msg->byteArrayLength;
  outGoing[15] = htonl(lenByteArray);
    
  /* total length of message (minus first int) is first item sent */
  len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType +
        lenCreator + lenText + lenByteArray;
  outGoing[0] = htonl(len);  

  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);
  
  /* allocate more memory for message-sending buffer if necessary */
  if (domain->msgBufferSize < (int)(len+sizeof(int))) {
    free(domain->msgBuffer);
    domain->msgBufferSize = len + 1004; /* give us 1kB extra */
    domain->msgBuffer = (char *) malloc(domain->msgBufferSize);
    if (domain->msgBuffer == NULL) {
      cMsgSocketMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      free(info->subject);
      free(info->type);
      info->subject = NULL;
      info->type    = NULL;
      info->active  = 0;
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_sendAndGet: out of memory\n");
      }
      return(CMSG_OUT_OF_MEMORY);
    }
  }
  
  /* copy data into a single static buffer */
  memcpy(domain->msgBuffer, (void *)outGoing, sizeof(outGoing));
  len = sizeof(outGoing);
  memcpy(domain->msgBuffer+len, (void *)msg->subject, lenSubject);
  len += lenSubject;
  memcpy(domain->msgBuffer+len, (void *)msg->type, lenType);
  len += lenType;
  memcpy(domain->msgBuffer+len, (void *)creator, lenCreator);
  len += lenCreator;
  memcpy(domain->msgBuffer+len, (void *)msg->text, lenText);
  len += lenText;
  memcpy(domain->msgBuffer+len, (void *)&((msg->byteArray)[msg->byteArrayOffset]), lenByteArray);
  len += lenByteArray;   
    
  /* send data over socket */
  if (cMsgTcpWrite(fd, (void *) domain->msgBuffer, len) != len) {
    cMsgSocketMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);
    free(info->subject);
    free(info->type);
    info->subject = NULL;
    info->type    = NULL;
    info->active  = 0;
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_cmsg_sendAndGet: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
     
  /* done protecting communications */
  cMsgSocketMutexUnlock(domain);
  cMsgConnectReadUnlock(domain);
  
  /* Now ..., wait for asynchronous response */
  
  /* lock mutex */
  status = pthread_mutex_lock(&info->mutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed callback mutex lock");
  }

  domain->monData.sendAndGets++;
  domain->monData.numSendAndGets++;

  /* wait while there is no message */
  while (info->msgIn == 0) {
    /* wait until signaled */
    if (timeout == NULL) {
      status = pthread_cond_wait(&info->cond, &info->mutex);
    }
    /* wait until signaled or timeout */
    else {
      cMsgGetAbsoluteTime(timeout, &wait);
      status = pthread_cond_timedwait(&info->cond, &info->mutex, &wait);
    }
    
    if (status == ETIMEDOUT) {
      break;
    }
    else if (status != 0) {
      cmsg_err_abort(status, "Failed callback cond wait");
    }

    /* quit if commanded to */
    if (info->quit) {
      break;
    }
  }

  domain->monData.sendAndGets--;

  /* unlock mutex */
  status = pthread_mutex_unlock(&info->mutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed callback mutex unlock");
  }

  /* If we timed out, tell server to forget the get. */
  if (info->msgIn == 0) {
      /*printf("get: timed out\n");*/
      
      /* remove the get from server */
      unSendAndGet(domainId, uniqueId);

      *replyMsg = NULL;
      err = CMSG_TIMEOUT;
  }
  /* If we've been woken up with an error condition ... */
  else if (info->error != CMSG_OK) {
      *replyMsg = NULL;
      err = info->error;    
  }
  /* If we did not timeout and everything's OK */
  else {
      /*
       * Don't need to make a copy of message as only 1 receipient.
       * Message was allocated in client's listening thread and user
       * must free it.
       */
      *replyMsg = info->msg;
      err = CMSG_OK;
  }
  
  /* free up memory */
  free(info->subject);
  free(info->type);
  info->subject = NULL;
  info->type    = NULL;
  info->msg     = NULL;
  info->active  = 0;
  
  /*printf("get: SUCCESS!!!\n");*/

  return(err);
}


/*-------------------------------------------------------------------*/


/**
 * This routine tells the cMsg server to "forget" about the cMsgSendAndGet()
 * call (specified by the id argument) since a timeout occurred. Internal use
 * only.
 *
 * @param domainId id of the domain connection
 * @param id unique id associated with a sendAndGet
 *
 * @returns CMSG_BAD_ARGUMENT  if the domain id is null
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 */   
static int unSendAndGet(void *domainId, int id) {
  
  int fd, outGoing[3];
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
    
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  /* size of info coming - 8 bytes */
  outGoing[0] = htonl(8);
  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_UN_SEND_AND_GET_REQUEST);
  /* senderToken id */
  outGoing[2] = htonl(id);

  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);
  
  /* send ints over together */
  if (cMsgTcpWrite(fd, (void *) outGoing, sizeof(outGoing)) != sizeof(outGoing)) {
    cMsgSocketMutexUnlock(domain);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "unSendAndGet: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
 
  cMsgSocketMutexUnlock(domain);

  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This method is a synchronous call to receive a message containing monitoring
 * data which describes the state of the cMsg domain the user is connected to.
 * The time is data was sent can be obtained by calling cMsgGetSenderTime.
 * The monitoring data in xml format can be obtained by calling cMsgGetText.
 *
 * @param domainId id of the domain connection
 * @param command string to monitor data collecting routine
 * @param replyMsg message received from the domain containing monitor data
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns CMSG_OUT_OF_MEMORY if no memory available
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSMonitor
 */   
int cmsg_cmsg_monitor(void *domainId, const char *command, void **replyMsg) {
    
  int err, len;
  int fd, fdIn, outGoing[2], inComing[3];
  cMsgMessage_t *msg;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  uint64_t llTime;
  
    
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd   = domain->sendSocket;
  fdIn = domain->receiveSocket;
   
  /* check args */
  if (replyMsg == NULL) return(CMSG_BAD_ARGUMENT);  
    
  tryagain:
  while (1) {
    err = CMSG_OK;

    /* Cannot run this while connecting/disconnecting */
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      err = CMSG_LOST_CONNECTION;
      break;
    }

    outGoing[0] = htonl(4); /* sending one int */
    /* message id (in network byte order) to domain server */
    outGoing[1] = htonl(CMSG_MONITOR_REQUEST);

    /* make monitor be synchronous 'cause we need a reply */
    cMsgSyncSendMutexLock(domain);

    /* make send socket communications thread-safe */
    cMsgSocketMutexLock(domain);

    /* send data over socket */
    if (cMsgTcpWrite(fd, (void *) outGoing, sizeof(outGoing)) != sizeof(outGoing)) {
      cMsgSocketMutexUnlock(domain);
      cMsgSyncSendMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_monitor: write failure\n");
      }
      err = CMSG_NETWORK_ERROR;
      break;
    }

    /* done protecting outgoing communications */
    cMsgSocketMutexUnlock(domain);

    /* now read reply */
    if (cMsgTcpRead(fdIn, (void *) inComing, sizeof(inComing)) != sizeof(inComing)) {
      cMsgSyncSendMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_monitor: read failure\n");
      }
      err = CMSG_NETWORK_ERROR;
      break;
    }
    
    msg = (cMsgMessage_t *) cMsgCreateMessage();

    /*
     * Time arrives as the high 32 bits followed by the low 32 bits
     * of a 64 bit integer in units of milliseconds.
     */
    llTime = (((uint64_t) ntohl(inComing[0])) << 32) |
             (((uint64_t) ntohl(inComing[1])) & 0x00000000FFFFFFFF);
    /* turn long long into struct timespec */
    msg->senderTime.tv_sec  =  llTime/1000;
    msg->senderTime.tv_nsec = (llTime%1000)*1000000;
    
    len = ntohl(inComing[2]);
    
    if ( (msg->text = (char *) calloc(1, len+1)) == NULL ) {
      cMsgSyncSendMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      err = CMSG_OUT_OF_MEMORY;
      break;    
    }

    if (cMsgTcpRead(fdIn, (void *)msg->text, len) != len) {
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "cmsg_cmsg_monitor: error read failure\n");
      }
      cMsgSyncSendMutexUnlock(domain);
      cMsgConnectReadUnlock(domain);
      err = CMSG_NETWORK_ERROR;
      break;    
    }
    
    *replyMsg = msg;
    
    cMsgSyncSendMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);
    break;
  }
  
  
  if (err!= CMSG_OK) {
    /* don't wait for resubscribes */
    if (failoverSuccessful(domain, 0)) {
       fd = domain->sendSocket;
/*printf("cmsg_cmsg_monitor: FAILOVER SUCCESSFUL, try suncSend again\n");*/
       goto tryagain;
    }  
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      printf("cmsg_cmsg_monitor: FAILOVER NOT successful, quitting, err = %d\n", err);
    }
  }

  /* return error code */  
  return(err);  
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends any pending (queued up) communication with the server.
 * In the cMsg domain, however, all sockets are set to TCP_NODELAY -- meaning
 * all writes over the socket are sent immediately. Thus, this routine does
 * nothing.
 *
 * @param domainId id of the domain connection
 * @param timeout amount of time to wait for completion
 *
 * @returns CMSG_OK always
 */   
int cmsg_cmsg_flush(void *domainId, const struct timespec *timeout) {  
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
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement
 *                               subscribe
 * @returns CMSG_ALREADY_EXISTS if an identical subscription already exists
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_cmsg_subscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                     void *userArg, cMsgSubscribeConfig *config, void **handle) {

  int i, j, fd, iok=0, jok=0, uniqueId, status, err;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  subscribeConfig *sConfig = (subscribeConfig *) config;
  cbArg *cbarg;
  struct iovec iov[3];
  pthread_attr_t threadAttribute;

  
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  if (!domain->hasSubscribe) {
    return(CMSG_NOT_IMPLEMENTED);
  } 
  
  /* check args */  
  if ( (cMsgCheckString(subject) != CMSG_OK ) ||
       (cMsgCheckString(type)    != CMSG_OK ) ||
       (callback == NULL)                    ) {
    return(CMSG_BAD_ARGUMENT);
  }

  tryagain:
  while (1) {
    err = CMSG_OK;
    
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      err = CMSG_LOST_CONNECTION;
      break;
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
            
#ifdef VXWORKS
            /* Make 30k bytes the default stack size in vxworks (instead
             * of the normal 20k) since people have been running out of
             * stack memory.
             */
            if (domain->subscribeInfo[i].cbInfo[j].config.stackSize == 0) {
              pthread_attr_setstacksize(&threadAttribute, CMSG_VX_DEFAULT_STACK_SIZE);
            }
#endif
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
      domain->monData.numSubscribes++;
      return(CMSG_OK);
    }

    /* no match, make new entry and notify server */
    iok = 0;
    for (i=0; i<CMSG_MAX_SUBSCRIBE; i++) {
      int len, lenSubject, lenType;
      int outGoing[6];

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

#ifdef VXWORKS
      /* Make 30k bytes the default stack size in vxworks (instead
       * of the normal 20k) since people have been running out of
       * stack memory.
       */
      if (domain->subscribeInfo[i].cbInfo[0].config.stackSize == 0) {
        pthread_attr_setstacksize(&threadAttribute, CMSG_VX_DEFAULT_STACK_SIZE);
      }
#endif

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
       * Mutex protect this operation as many cmsg_cmsg_connect calls may
       * operate in parallel on this static variable.
       */
      staticMutexLock();
      uniqueId = subjectTypeId++;
      staticMutexUnlock();
      domain->subscribeInfo[i].id = uniqueId;

      /* notify domain server */

      /* message id (in network byte order) to domain server */
      outGoing[1] = htonl(CMSG_SUBSCRIBE_REQUEST);
      /* unique id to domain server */
      outGoing[2] = htonl(uniqueId);
      /* length of "subject" string */
      lenSubject  = strlen(subject);
      outGoing[3] = htonl(lenSubject);
      /* length of "type" string */
      lenType     = strlen(type);
      outGoing[4] = htonl(lenType);
      /* length of "namespace" string (0 in this case, since
       * only used for server-to-server) */
      outGoing[5] = htonl(0);

      /* total length of message is first item sent */
      len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType;
      outGoing[0] = htonl(len);

      iov[0].iov_base = (char*) outGoing;
      iov[0].iov_len  = sizeof(outGoing);

      iov[1].iov_base = (char*) subject;
      iov[1].iov_len  = lenSubject;

      iov[2].iov_base = (char*) type;
      iov[2].iov_len  = lenType;

      /* make send socket communications thread-safe */
      cMsgSocketMutexLock(domain);

      if (cMsgTcpWritev(fd, iov, 3, 16) == -1) {
        cMsgSocketMutexUnlock(domain);
        
        /* stop callback thread */
        domain->subscribeInfo[i].cbInfo[0].quit = 1;
        pthread_cancel(domain->subscribeInfo[i].cbInfo[0].thread);
        
        free(domain->subscribeInfo[i].subject);
        free(domain->subscribeInfo[i].type);
        free(domain->subscribeInfo[i].subjectRegexp);
        free(domain->subscribeInfo[i].typeRegexp);
        domain->subscribeInfo[i].subject       = NULL;
        domain->subscribeInfo[i].type          = NULL;
        domain->subscribeInfo[i].subjectRegexp = NULL;
        domain->subscribeInfo[i].typeRegexp    = NULL;
        domain->subscribeInfo[i].active        = 0;
        domain->subscribeInfo[i].numCallbacks--;
        
        if (cMsgDebug >= CMSG_DEBUG_ERROR) {
          fprintf(stderr, "cmsg_cmsg_subscribe: write failure\n");
        }
        
        err = CMSG_LOST_CONNECTION;
        break;
      }
      
      domain->monData.numSubscribes++;

      /* done protecting communications */
      cMsgSocketMutexUnlock(domain);
      break;
    } /* for i */

    /* done protecting subscribe */
    cMsgSubscribeMutexUnlock(domain);
    cMsgConnectReadUnlock(domain);
    break;  
  } /* while(1) */

  if (iok == 0) {
    err = CMSG_OUT_OF_MEMORY;
  }
  else if (err!= CMSG_OK) {
    /* don't wait for resubscribes */
    if (failoverSuccessful(domain, 0)) {
       fd = domain->sendSocket;
       printf("cmsg_cmsg_subscribe: FAILOVER SUCCESSFUL, try subscribe again\n");
       goto tryagain;
    }  
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      printf("cmsg_cmsg_subscribe: FAILOVER NOT successful, quitting, err = %d\n", err);
    }
  }

  return(err);
}


/*-------------------------------------------------------------------*/


/**
 * This routine resubscribes existing subscriptions on another (failover)
 * server. If there is an error in any of the resubscribes, the failover
 * is scrubbed. This routine is called by the routine "restoreSubscriptions"
 * which is protected by the writeConnect lock so no other locks are
 * required.
 *
 * @param domain pointer to struct of the domain connection info
 * @param subject subject of messages subscribed to
 * @param type type of messages subscribed to
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 */   
static int resubscribe(cMsgDomainInfo *domain, const char *subject, const char *type) {

  int i, uniqueId, mySubIndex=-1;
  struct iovec iov[3];
  int len, lenSubject, lenType;
  int fd = domain->sendSocket;
  int outGoing[6];

  if (domain->gotConnection != 1) {
    return(CMSG_LOST_CONNECTION);
  }

  /*
   * This routine is called by the routine "restoreSubscriptions"
   * which is protected by the connectWriteLock so no other
   * locks are required.
   */
  
  /* if an unsubscribe has been done, forget about resubscribing */
  for (i=0; i<CMSG_MAX_SUBSCRIBE; i++) {
    if (domain->subscribeInfo[i].active == 0) {
      continue;
    }
    
    if ((strcmp(domain->subscribeInfo[i].subject, subject) == 0) && 
        (strcmp(domain->subscribeInfo[i].type, type) == 0) ) {
      /* subject & type exist so we're OK */
      mySubIndex = i;
      break;
    }
  }
  
  /* no subscription to this subject and type exist */
  if (mySubIndex < 0) {
    return(CMSG_OK);
  }
  
  /* Pick a unique identifier for the subject/type pair. */
  staticMutexLock();
  uniqueId = subjectTypeId++;
  staticMutexUnlock();
  i = mySubIndex;
  domain->subscribeInfo[i].id = uniqueId;

  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_SUBSCRIBE_REQUEST);
  /* unique id to domain server */
  outGoing[2] = htonl(uniqueId);
  /* length of "subject" string */
  lenSubject  = strlen(subject);
  outGoing[3] = htonl(lenSubject);
  /* length of "type" string */
  lenType     = strlen(type);
  outGoing[4] = htonl(lenType);
  /* length of "namespace" string (0 in this case, since namespace
   * only used for server-to-server) */
  outGoing[5] = htonl(0);

  /* total length of message is first item sent */
  len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType;
  outGoing[0] = htonl(len);

  iov[0].iov_base = (char*) outGoing;
  iov[0].iov_len  = sizeof(outGoing);

  iov[1].iov_base = (char*) subject;
  iov[1].iov_len  = lenSubject;

  iov[2].iov_base = (char*) type;
  iov[2].iov_len  = lenType;


  if (cMsgTcpWritev(fd, iov, 3, 16) == -1) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_cmsg_subscribe: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }

  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine unsubscribes to messages of the given handle (which
 * represents a given subject, type, callback, and user argument).
 * This routine is called by the user through
 * cMsgUnSubscribe() given the appropriate UDL. In this domain cMsgFlush()
 * does nothing and does not need to be called for cmsg_cmsg_unsubscribe to be
 * started immediately.
 *
 * @param domainId id of the domain connection
 * @param handle void pointer obtained from cmsg_cmsg_subscribe
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id, handle or its subject, type, or callback are null,
 *                            or the given subscription (thru handle) does not have
 *                            an active subscription or callbacks
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement
 *                               unsubscribe
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_LOST_CONNECTION if the network connection to the server was closed
 *                               by a call to cMsgDisconnect()
 */   
int cmsg_cmsg_unsubscribe(void *domainId, void *handle) {

  int fd, status, err;
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  struct iovec iov[3];
  cbArg           *cbarg;
  subInfo         *subscriptionInfo;
  subscribeCbInfo *callbackInfo;
  
  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  if (!domain->hasUnsubscribe) {
    return(CMSG_NOT_IMPLEMENTED);
  }
 
  /* check args */
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

  tryagain:
  while (1) {
    err = CMSG_OK;
    
    cMsgConnectReadLock(domain);

    if (domain->gotConnection != 1) {
      cMsgConnectReadUnlock(domain);
      err = CMSG_LOST_CONNECTION;
      break;
    }

    /* make sure subscribe and unsubscribe are not run at the same time */
    cMsgSubscribeMutexLock(domain);
        
    /* Delete entry and notify server if there was at least 1 callback
     * to begin with and now there are none for this subject/type.
     */
    if (subscriptionInfo->numCallbacks - 1 < 1) {

      int len, lenSubject, lenType;
      int outGoing[6];

      /* notify server */

      /* message id (in network byte order) to domain server */
      outGoing[1] = htonl(CMSG_UNSUBSCRIBE_REQUEST);
      /* unique id associated with subject/type */
      outGoing[2] = htonl(subscriptionInfo->id);
      /* length of "subject" string */
      lenSubject  = strlen(subscriptionInfo->subject);
      outGoing[3] = htonl(lenSubject);
      /* length of "type" string */
      lenType     = strlen(subscriptionInfo->type);
      outGoing[4] = htonl(lenType);
      /* length of "namespace" string (0 in this case, since
       * only used for server-to-server) */
      outGoing[5] = htonl(0);

      /* total length of message (minus first int) is first item sent */
      len = sizeof(outGoing) - sizeof(int) + lenSubject + lenType;
      outGoing[0] = htonl(len);

      iov[0].iov_base = (char*) outGoing;
      iov[0].iov_len  = sizeof(outGoing);

      iov[1].iov_base = (char*) subscriptionInfo->subject;
      iov[1].iov_len  = lenSubject;

      iov[2].iov_base = (char*) subscriptionInfo->type;
      iov[2].iov_len  = lenType;

      /* make send socket communications thread-safe */
      cMsgSocketMutexLock(domain);

      if (cMsgTcpWritev(fd, iov, 3, 16) == -1) {
        cMsgSocketMutexUnlock(domain);
        cMsgSubscribeMutexUnlock(domain);
        cMsgConnectReadUnlock(domain);
        
        if (cMsgDebug >= CMSG_DEBUG_ERROR) {
          fprintf(stderr, "cmsg_cmsg_unsubscribe: write failure\n");
        }
        err = CMSG_NETWORK_ERROR;
        break;
      }

       /* done protecting communications */
      cMsgSocketMutexUnlock(domain);
      
      /* We told the server, now do the unsubscribe. */
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
      
    } /* if gotta notify server */
    
    /* one less callback */
    subscriptionInfo->numCallbacks--;

    /* tell callback thread to end */
    callbackInfo->quit = 1;

    /* wakeup callback thread */
    status = pthread_cond_broadcast(&callbackInfo->cond);
    if (status != 0) {
      cmsg_err_abort(status, "Failed callback condition signal");
    }
    
    domain->monData.numUnsubscribes++;

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
  
    /* free mem */
    free(cbarg);
    
    break;
    
  } /* while(1) */

  if (err!= CMSG_OK) {
    /* wait awhile for possible failover && resubscribe is complete */
    if (failoverSuccessful(domain, 1)) {
       fd = domain->sendSocket;
       printf("cmsg_cmsg_unsubscribe: FAILOVER SUCCESSFUL, try unsubscribe again\n");
       goto tryagain;
    }  
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      printf("cmsg_cmsg_unsubscirbe: FAILOVER NOT successful, quitting, err = %d\n", err);
    }
  }
  
  return(err);
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
int cmsg_cmsg_start(void *domainId) {
  
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
int cmsg_cmsg_stop(void *domainId) {
  
  if (domainId == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  ((cMsgDomainInfo *) domainId)->receiveState = 0;
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine disconnects the client from the cMsg server.
 *
 * @param domainId pointer to id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId or the pointer it points to is NULL
 */   
int cmsg_cmsg_disconnect(void **domainId) {
  
  int i, j, status, outGoing[2];
  cMsgDomainInfo *domain;
  subscribeCbInfo *subscription;
  getInfo *info;
  struct timespec wait4thds = {0, 100000000}; /* 0.1 sec */
  

  if (domainId == NULL) return(CMSG_BAD_ARGUMENT);
  domain = (cMsgDomainInfo *) (*domainId);
  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
        
  cMsgConnectWriteLock(domain);
  
  domain->gotConnection = 0;
  /*
   * If the domain server thread terminates first, our keep alive thread will
   * detect it and call this function. To prevent this, first kill our
   * keep alive thread, close the socket, then tell the server we're going away.
   */
   
  /* stop keep alive thread,  don't care if this fails */
  pthread_cancel(domain->keepAliveThread);
  close(domain->keepAliveSocket);

  /* Tell server we're disconnecting */
  
  /* size of msg */
  outGoing[0] = htonl(4);
  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_SERVER_DISCONNECT);

  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);
  
  /* send int */
  if (cMsgTcpWrite(domain->sendSocket, (char*) outGoing, sizeof(outGoing)) != sizeof(outGoing)) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_cmsg_disconnect: write failure, but continue\n");
    }
  }
 
  cMsgSocketMutexUnlock(domain);
  
  /* close TCP sending socket */
  close(domain->sendSocket);

  /* close UDP sending socket */
  close(domain->sendUdpSocket);

  /* close receiving socket */
  close(domain->receiveSocket);

  /* stop listening and client communication threads */
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "cmsg_cmsg_disconnect:cancel listening & client threads\n");
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
            fprintf(stderr, "cmsg_cmsg_disconnect:wake up callback thread\n");
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

  /* wakeup all gets */
  for (i=0; i<CMSG_MAX_SEND_AND_GET; i++) {
    
    info = &domain->sendAndGetInfo[i];
    if (info->active != 1) {
      continue;
    }
    
    /* wakeup "get" */      
    if (cMsgDebug >= CMSG_DEBUG_INFO) {
      fprintf(stderr, "cmsg_cmsg_disconnect:wake up a sendAndGet\n");
    }
  
    status = pthread_cond_signal(&info->cond);
    if (status != 0) {
      cmsg_err_abort(status, "Failed get condition signal");
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

/**
 * This routine disconnects the client from the cMsg server when
 * called by the keepAlive thread.
 *
 * @param domainId id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId or the pointer it points to is NULL
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_NOT_INITIALIZED if the connection to the server was never made
 *                               since cMsgConnect() was never called
 */   
static int disconnectFromKeepAlive(void **domainId) {
  
  int i, j, status;
  cMsgDomainInfo *domain;
  subscribeCbInfo *subscription;
  getInfo *info;
  struct timespec wait4thds = {0,100000000}; /* 0.1 sec */

  
  if (domainId == NULL) return(CMSG_BAD_ARGUMENT);
  domain = (cMsgDomainInfo *) (*domainId);
  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
      
  cMsgConnectWriteLock(domain);
     
  domain->gotConnection = 0;

  /* stop listening and client communication threads */
  pthread_cancel(domain->pendThread);
   
  /* close sending socket */
  close(domain->sendSocket);

  /* close receiving socket */
  close(domain->receiveSocket);
    
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
            fprintf(stderr, "cmsg_cmsg_disconnect:wake up callback thread\n");
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

  /* wakeup all gets */
  for (i=0; i<CMSG_MAX_SEND_AND_GET; i++) {
    
    info = &domain->sendAndGetInfo[i];
    if (info->active != 1) {
      continue;
    }
    
    /* wakeup "get" */      
    if (cMsgDebug >= CMSG_DEBUG_INFO) {
      fprintf(stderr, "cmsg_cmsg_disconnect:wake up a sendAndGet\n");
    }
  
    status = pthread_cond_signal(&info->cond);
    if (status != 0) {
      cmsg_err_abort(status, "Failed get condition signal");
    }    
  }
  
  /* give the above threads a chance to quit before we reset everytbing */
  nanosleep(&wait4thds, NULL);
  
  /* Unblock SIGPIPE */
  cMsgRestoreSignals(domain);

  cMsgConnectWriteUnlock(domain);

  /* do NOT close listening socket */
  /*close(domain->listenSocket);*/
    
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
int cmsg_cmsg_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler,
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


/**
 * Method to shutdown the given clients.
 *
 * @param domainId id of the domain connection
 * @param client client(s) to be shutdown
 * @param flag   flag describing the mode of shutdown: 0 to not include self,
 *               CMSG_SHUTDOWN_INCLUDE_ME to include self in shutdown.
 * 
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id is null
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement shutdown
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 */
int cmsg_cmsg_shutdownClients(void *domainId, const char *client, int flag) {
  
  int fd, len, cLen, outGoing[4];
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  struct iovec iov[2];


  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  if (!domain->hasShutdown) {
    return(CMSG_NOT_IMPLEMENTED);
  } 
        
  cMsgConnectWriteLock(domain);
    
  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_SHUTDOWN_CLIENTS);
  outGoing[2] = htonl(flag);
  
  if (client == NULL) {
    cLen = 0;
    outGoing[3] = 0;
  }
  else {
    cLen = strlen(client);
    outGoing[3] = htonl(cLen);
  }
  
  /* total length of message (minus first int) is first item sent */
  len = sizeof(outGoing) - sizeof(int) + cLen;
  outGoing[0] = htonl(len);
  
  iov[0].iov_base = (char*) outGoing;
  iov[0].iov_len  = sizeof(outGoing);

  iov[1].iov_base = (char*) client;
  iov[1].iov_len  = cLen;
  
  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);
  
  if (cMsgTcpWritev(fd, iov, 2, 16) == -1) {
    cMsgSocketMutexUnlock(domain);
    cMsgConnectWriteUnlock(domain);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_cmsg_unsubscribe: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
   
  cMsgSocketMutexUnlock(domain);  
  cMsgConnectWriteUnlock(domain);

  return CMSG_OK;

}


/*-------------------------------------------------------------------*/


/**
 * Method to shutdown the given servers.
 *
 * @param domainId id of the domain connection
 * @param server server(s) to be shutdown
 * @param flag   flag describing the mode of shutdown: 0 to not include self,
 *               CMSG_SHUTDOWN_INCLUDE_ME to include self in shutdown.
 * 
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if the id is null
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement shutdown
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 */
int cmsg_cmsg_shutdownServers(void *domainId, const char *server, int flag) {
  
  int fd, len, sLen, outGoing[4];
  cMsgDomainInfo *domain = (cMsgDomainInfo *) domainId;
  struct iovec iov[2];


  if (domain == NULL) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  fd = domain->sendSocket;
  
  if (!domain->hasShutdown) {
    return(CMSG_NOT_IMPLEMENTED);
  } 
        
  cMsgConnectWriteLock(domain);
    
  /* message id (in network byte order) to domain server */
  outGoing[1] = htonl(CMSG_SHUTDOWN_SERVERS);
  outGoing[2] = htonl(flag);
  
  if (server == NULL) {
    sLen = 0;
    outGoing[3] = 0;
  }
  else {
    sLen = strlen(server);
    outGoing[3] = htonl(sLen);
  }

  /* total length of message (minus first int) is first item sent */
  len = sizeof(outGoing) - sizeof(int) + sLen;
  outGoing[0] = htonl(len);
  
  iov[0].iov_base = (char*) outGoing;
  iov[0].iov_len  = sizeof(outGoing);

  iov[1].iov_base = (char*) server;
  iov[1].iov_len  = sLen;
  
  /* make send socket communications thread-safe */
  cMsgSocketMutexLock(domain);
  
  if (cMsgTcpWritev(fd, iov, 2, 16) == -1) {
    cMsgSocketMutexUnlock(domain);
    cMsgConnectWriteUnlock(domain);
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "cmsg_cmsg_unsubscribe: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
   
  cMsgSocketMutexUnlock(domain);  
  cMsgConnectWriteUnlock(domain);

  return CMSG_OK;

}


/*-------------------------------------------------------------------*/


/**
 * This routine exchanges information with the name server.
 *
 * @param domain  pointer to element in domain info array
 * @param serverfd  socket to send to cMsg name server
 * @param failoverIndex  index into the array of parsed UDLs of the current UDL.
 * 
 * @returns CMSG_OK if successful
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 *
 */
static int talkToNameServer(cMsgDomainInfo *domain, int serverfd, int failoverIndex) {

  int  err, lengthDomain, lengthSubdomain, lengthRemainder, lengthPassword;
  int  lengthHost, lengthName, lengthUDL, lengthDescription;
  int  outGoing[12], inComing[3];
  char temp[CMSG_MAXHOSTNAMELEN], atts[7];
  const char *domainType = "cMsg";
  struct iovec iov[9];
  parsedUDL *pUDL = &domain->failovers[failoverIndex];

  /* first send message id (in network byte order) to server */
  outGoing[0] = htonl(CMSG_SERVER_CONNECT);
  /* major version number */
  outGoing[1] = htonl(CMSG_VERSION_MAJOR);
  /* minor version number */
  outGoing[2] = htonl(CMSG_VERSION_MINOR);
  /* send my listening port (as an int) to server */
  outGoing[3] = htonl(domain->listenPort);
  /* send length of password for connecting to server.*/
  if (pUDL->password == NULL) {
    lengthPassword = outGoing[4] = 0;
  }
  else {
    lengthPassword = strlen(pUDL->password);
    outGoing[4]    = htonl(lengthPassword);
  }
  /* send length of the type of domain server I'm expecting to connect to.*/
  lengthDomain = strlen(domainType);
  outGoing[5]  = htonl(lengthDomain);
  /* send length of the type of subdomain handler I'm expecting to use.*/
  lengthSubdomain = strlen(pUDL->subdomain);
  outGoing[6] = htonl(lengthSubdomain);
  /* send length of the UDL remainder.*/
  /* this may be null */
  if (pUDL->subRemainder == NULL) {
    lengthRemainder = outGoing[7] = 0;
  }
  else {
    lengthRemainder = strlen(pUDL->subRemainder);
    outGoing[7] = htonl(lengthRemainder);
  }
  /* send length of my host name to server */
  lengthHost  = strlen(domain->myHost);
  outGoing[8] = htonl(lengthHost);
  /* send length of my name to server */
  lengthName  = strlen(domain->name);
  outGoing[9] = htonl(lengthName);
  /* send length of my udl to server */
  lengthUDL    = strlen(pUDL->udl);
  outGoing[10] = htonl(lengthUDL);
  /* send length of my description to server */
  lengthDescription = strlen(domain->description);
  outGoing[11]      = htonl(lengthDescription);
    
  iov[0].iov_base = (char*) outGoing;
  iov[0].iov_len  = sizeof(outGoing);
  
  iov[1].iov_base = (char*) pUDL->password;
  iov[1].iov_len  = lengthPassword;
  
  iov[2].iov_base = (char*) domainType;
  iov[2].iov_len  = lengthDomain;
  
  iov[3].iov_base = (char*) pUDL->subdomain;
  iov[3].iov_len  = lengthSubdomain;
  
  iov[4].iov_base = (char*) pUDL->subRemainder;
  iov[4].iov_len  = lengthRemainder;
  
  iov[5].iov_base = (char*) domain->myHost;
  iov[5].iov_len  = lengthHost;
  
  iov[6].iov_base = (char*) domain->name;
  iov[6].iov_len  = lengthName;
  
  iov[7].iov_base = (char*) domain->udl;
  iov[7].iov_len  = lengthUDL;
  
  iov[8].iov_base = (char*) domain->description;
  iov[8].iov_len  = lengthDescription;
  
  if (cMsgTcpWritev(serverfd, iov, 9, 16) == -1) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "talkToNameServer: write failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
    
  /* now read server reply */
  if (cMsgTcpRead(serverfd, (void *) &err, sizeof(err)) != sizeof(err)) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "talkToNameServer: read failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
  err = ntohl(err);
    
  /* if there's an error, read error string then quit */
  if (err != CMSG_OK) {
    int   len;
    char *string;

    /* read length of error string */
    if (cMsgTcpRead(serverfd, (char*) &len, sizeof(len)) != sizeof(len)) {
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "talkToNameServer: read failure\n");
      }
      return(CMSG_NETWORK_ERROR);
    }
    len = ntohl(len);

    /* allocate memory for error string */
    string = (char *) malloc((size_t) (len+1));
    if (string == NULL) {
      if (cMsgDebug >= CMSG_DEBUG_SEVERE) {
        fprintf(stderr, "talkToNameServer: cannot allocate memory\n");
      }
      exit(1);
    }
      
    if (cMsgTcpRead(serverfd, (char*) string, len) != len) {
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "talkToNameServer: cannot read error string\n");
      }
      free(string);
      return(CMSG_NETWORK_ERROR);
    }
    /* add null terminator to C string */
    string[len] = 0;
    
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "talkToNameServer: %s\n", string);
    }
    
    free(string);
    return(err);
  }
  
  /*
   * if everything's OK, we expect to get:
   *   1) attributes of subdomain handler
   *   2) host & port
   */
  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "talkToNameServer: read subdomain handler attributes\n");
  }
  
  /* read whether subdomain has various functions implemented */
  if (cMsgTcpRead(serverfd, (char*) atts, sizeof(atts)) != sizeof(atts)) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "talkToNameServer: read failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
  
  /* store attributes of the subdomain handler being used */
  if (atts[0] == 1) domain->hasSend            = 1;
  if (atts[1] == 1) domain->hasSyncSend        = 1;
  if (atts[2] == 1) domain->hasSubscribeAndGet = 1;
  if (atts[3] == 1) domain->hasSendAndGet      = 1;
  if (atts[4] == 1) domain->hasSubscribe       = 1;
  if (atts[5] == 1) domain->hasUnsubscribe     = 1;
  if (atts[6] == 1) domain->hasShutdown        = 1;
  
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "talkToNameServer: read port and length of host from server\n");
  }
  
  /* read port & length of host name to send to*/
  if (cMsgTcpRead(serverfd, (char*) inComing, sizeof(inComing)) != sizeof(inComing)) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "talkToNameServer: read failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
  domain->sendPort    = ntohl(inComing[0]);
  domain->sendUdpPort = ntohl(inComing[1]);
  lengthHost          = ntohl(inComing[2]);

  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "talkToNameServer: port = %d, host len = %d\n",
              domain->sendPort, lengthHost);
    fprintf(stderr, "talkToNameServer: read host from server\n");
  }
  
  /* read host name to send to */
  if (cMsgTcpRead(serverfd, (char*) temp, lengthHost) != lengthHost) {
    if (cMsgDebug >= CMSG_DEBUG_ERROR) {
      fprintf(stderr, "talkToNameServer: read failure\n");
    }
    return(CMSG_NETWORK_ERROR);
  }
  /* be sure to null-terminate string */
  temp[lengthHost] = 0;
  domain->sendHost = (char *) strdup(temp);
  if (cMsgDebug >= CMSG_DEBUG_INFO) {
    fprintf(stderr, "talkToNameServer: host = %s\n", domain->sendHost);
  }
  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine is run as a thread which is used to send keep alive
 * communication to a cMsg server.  If there is no response or there is
 * an I/O error, the other end of the socket & the server is presumed dead.
 */
static void *keepAliveThread(void *arg)
{
    cMsgDomainInfo *domain = (cMsgDomainInfo *) arg;
    int socket   = domain->keepAliveSocket;
    int outGoing, alive, err;
    
    int failoverIndex = domain->failoverIndex;
    int connectFailures = 0;
    int weGotAConnection = 1; /* true */
    struct timespec wait;
    
    /* increase concurrency for this thread for early Solaris */
    int  con;
    con = sun_getconcurrency();
    sun_setconcurrency(con + 1);

    wait.tv_sec  = 1;
    wait.tv_nsec = 100000000; /* 1.1 sec */

    /* release system resources when thread finishes */
    pthread_detach(pthread_self());

    /* periodically send a keep alive message and read response */
    if (cMsgDebug >= CMSG_DEBUG_INFO) {
      fprintf(stderr, "keepAliveThread: keep alive thread created, socket = %d\n", socket);
    }
  
    /* request to send */
    outGoing = htonl(CMSG_KEEP_ALIVE);
    
    while (weGotAConnection) {
    
        /* keep checking to see if the server/agent is alive */
        while(1) {       
           if (cMsgTcpWrite(socket, &outGoing, sizeof(outGoing)) != sizeof(outGoing)) {
               if (cMsgDebug >= CMSG_DEBUG_ERROR) {
                   fprintf(stderr, "keepAliveThread: error writing request\n");
               }
               break;
           }

           if ((err = cMsgTcpRead(socket, (char*) &alive, sizeof(alive))) != sizeof(alive)) {
               if (cMsgDebug >= CMSG_DEBUG_ERROR) {
                   fprintf(stderr, "keepAliveThread: read failure\n");
               }
               break;
           }
/*printf("ka: read %d\n", ntohl(alive));*/

           /* sleep for 1 second and try again */
           sleep(1);
        }

        /* clean up */
        close(domain->keepAliveSocket);
        close(domain->receiveSocket);
        close(domain->sendSocket);
        
        /* Start by trying to connect to the first UDL on the list.
         * If we've just been connected to that UDL, try the next. */
        if (failoverIndex != 0) {
            failoverIndex = -1;
        }
        connectFailures = 0;
        weGotAConnection = 0;
        domain->resubscribeComplete = 0;

        while (domain->implementFailovers && !weGotAConnection) {
            if (connectFailures >= domain->failoverSize) {
/* printf("ka: Reached our limit of UDLs so quit\n"); */
              break;
            }
            
            /* Go through the UDL's until one works */
            
            /* check to see if UDL valid for cMsg domain */
            if (!domain->failovers[++failoverIndex].valid) {
              connectFailures++;
/* printf("ka: skip invalid UDL = %s\n",
            domain->failovers[failoverIndex].udl); */
              continue;
            }

            /* connect using that UDL info */
/* printf("ka: trying to reconnect with UDL = %s\n",
            domain->failovers[failoverIndex].udl); */

            if (domain->failovers[failoverIndex].mustBroadcast == 1) {
              free(domain->failovers[failoverIndex].nameServerHost);
              connectWithBroadcast(domain, failoverIndex,
                                   &domain->failovers[failoverIndex].nameServerHost,
                                   &domain->failovers[failoverIndex].nameServerPort);     
            }

            err = reconnect(domain, failoverIndex);
            if (err != CMSG_OK) {
              connectFailures++;
/* printf("ka: ERROR reconnecting, continue\n"); */
              continue;
            }
/* printf("ka: Connected!!, now restore subscriptions\n"); */

            /* restore subscriptions on the new server */
            err = restoreSubscriptions(domain);            
            if (err != CMSG_OK) {
              /* if subscriptions fail, then we do NOT use this failover server */
              connectFailures++;
/* printf("ka: ERROR restoring subscriptions, continue\n"); */
              continue;
            }
            
            domain->failoverIndex = failoverIndex;
            domain->resubscribeComplete = 1;
/* printf("ka: Set domain->keepaliveSocket to %d\n", domain->keepAliveSocket); */            
            socket = domain->keepAliveSocket; 

            /* we got ourselves a new server, boys */
            weGotAConnection = 1;
            
            /* wait for up to 1.1 sec for waiters to respond */
            err = cMsgLatchCountDown(&domain->syncLatch, &wait);
            if (err != 1) {
/* printf("ka: Problems with reporting back to countdowner\n"); */            
            }
            cMsgLatchReset(&domain->syncLatch, 1, NULL);
        }
    }

    /* close communication socket */
    close(socket);

    /* if we've reach here, there's an error, do a disconnect */
    if (cMsgDebug >= CMSG_DEBUG_INFO) {
      fprintf(stderr, "keepAliveThread: server is probably dead, disconnect\n");
    }
    
/* printf("\n\n\nka: DISCONNECTING \n\n\n"); */            
    disconnectFromKeepAlive((void **) &domain);
    
    sun_setconcurrency(con);
    
    return NULL;
}


/*-------------------------------------------------------------------*/
/*   miscellaneous local functions                                   */
/*-------------------------------------------------------------------*/


/**
 * This routine parses, using regular expressions, the cMsg domain
 * portion of the UDL sent from the next level up" in the API.
 */
static int parseUDL(const char *UDL, char **password,
                          char **host, int *port,
                          char **UDLRemainder,
                          char **subdomainType,
                          char **UDLsubRemainder,
                          int   *broadcast,
                          int   *timeout) {

    int        i, err, Port, index;
    int        mustBroadcast = 0;
    size_t     len, bufLength;
    char       *p, *udl, *udlLowerCase, *udlRemainder, *remain;
    char       *buffer;
    const char *pattern = "([a-zA-Z0-9\\.\\-]+):?([0-9]+)?/?([a-zA-Z0-9]+)?/?(.*)";  
    regmatch_t matches[5]; /* we have 5 potential matches: 1 whole, 4 sub */
    regex_t    compiled;
    
    if (UDL == NULL) {
        return (CMSG_BAD_FORMAT);
    }
    
    /* make a copy */
    udl = (char *) strdup(UDL);
    
    /* make a copy in all lower case */
    udlLowerCase = (char *) strdup(UDL);
    len = strlen(udlLowerCase);
    for (i=0; i<len; i++) {
      udlLowerCase[i] = tolower(udlLowerCase[i]);
    }
  
    /* strip off the beginning cMsg:cMsg:// */
    p = strstr(udlLowerCase, "cmsg://");
    if (p == NULL) {
      free(udl);
      free(udlLowerCase);
      return(CMSG_BAD_ARGUMENT);  
    }
    index = (int) (p - udlLowerCase);
    free(udlLowerCase);
    
    udlRemainder = udl + index + 7;
/* printf("parseUDL: udl remainder = %s\n", udlRemainder); */
    
    if (UDLRemainder != NULL) {
        *UDLRemainder = (char *) strdup(udlRemainder);
    }        
  
    /* make a big enough buffer to construct various strings, 256 chars minimum */
    len       = strlen(udlRemainder) + 1;
    bufLength = len < 256 ? 256 : len;    
    buffer    = (char *) malloc(bufLength);
    if (buffer == NULL) {
      free(udl);
      return(CMSG_OUT_OF_MEMORY);
    }
    
    /* cMsg domain UDL is of the form:
     *        cMsg:cMsg://<host>:<port>/<subdomainType>/<subdomain remainder>?tag=value&tag2=value2& ...
     * 
     * the first "cMsg:" is optional. The subdomain is optional with
     * the default being cMsg.
     *
     * Remember that for this domain:
     * 1) port is not necessary
     * 2) host can be "localhost" and may also includes dots (.)
     * 3) if domainType is cMsg, subdomainType is automatically set to cMsg if not given.
     *    if subdomainType is not cMsg, it is required
     * 4) remainder is past on to the subdomain plug-in
     */

    /* compile regular expression */
    err = cMsgRegcomp(&compiled, pattern, REG_EXTENDED);
    if (err != 0) {
        free(udl);
        free(buffer);
        return (CMSG_ERROR);
    }
    
    /* find matches */
    err = cMsgRegexec(&compiled, udlRemainder, 5, matches, 0);
    if (err != 0) {
        /* no match */
        free(udl);
        free(buffer);
        return (CMSG_BAD_FORMAT);
    }
    
    /* free up memory */
    cMsgRegfree(&compiled);
            
    /* find host name */
    if (matches[1].rm_so < 0) {
        /* no match for host */
        free(udl);
        free(buffer);
        return (CMSG_BAD_FORMAT);
    }
    else {
       buffer[0] = 0;
       len = matches[1].rm_eo - matches[1].rm_so;
       strncat(buffer, udlRemainder+matches[1].rm_so, len);
                
        if (strcasecmp(buffer, "broadcast") == 0 ||
            strcmp(buffer, "255.255.255.255") == 0) {
            mustBroadcast = 1;
/* printf("set mustBroadcast to true (locally in parse method)"); */
        }
        /* if the host is "localhost", find the actual host name */
        else if (strcasecmp(buffer, "localhost") == 0) {
/* printf("parseUDL: host = localhost\n"); */
            /* get canonical local host name */
            if (cMsgLocalHost(buffer, bufLength) != CMSG_OK) {
                /* error */
                free(udl);
                free(buffer);
                return (CMSG_BAD_FORMAT);
            }
        }
        
        if (host != NULL) {
            *host = (char *)strdup(buffer);
        }
        if (broadcast != NULL) {
            *broadcast = mustBroadcast;        
        }
    }
/*
printf("parseUDL: host = %s\n", buffer);
printf("parseUDL: mustBroadcast = %d\n", mustBroadcast);
 */

    /* find port */
    if (matches[2].rm_so < 0) {
        /* no match for port so use default */
        if (mustBroadcast == 1) {
            Port = CMSG_NAME_SERVER_BROADCAST_PORT;
        }
        else {
            Port = CMSG_NAME_SERVER_STARTING_PORT;
        }
        if (cMsgDebug >= CMSG_DEBUG_WARN) {
            fprintf(stderr, "parseUDL: guessing that the name server port is %d\n",
                   Port);
        }
    }
    else {
        buffer[0] = 0;
        len = matches[2].rm_eo - matches[2].rm_so;
        strncat(buffer, udlRemainder+matches[2].rm_so, len);        
        Port = atoi(buffer);        
    }

    if (Port < 1024 || Port > 65535) {
      if (host != NULL) free((void *) *host);
      free(udl);
      free(buffer);
      return (CMSG_OUT_OF_RANGE);
    }
               
    if (port != NULL) {
      *port = Port;
    }
/* printf("parseUDL: port = %hu\n", Port ); */


    /* find subdomain */
    if (matches[3].rm_so < 0) {
        /* no match for subdomain, cMsg is default */
        if (subdomainType != NULL) {
            *subdomainType = (char *) strdup("cMsg");
        }
/* printf("parseUDL: subdomain = cMsg\n"); */
    }
    else {
        buffer[0] = 0;
        len = matches[3].rm_eo - matches[3].rm_so;
        strncat(buffer, udlRemainder+matches[3].rm_so, len);
                
        if (subdomainType != NULL) {
            *subdomainType = (char *) strdup(buffer);
        }        
/* printf("parseUDL: subdomain = %s\n", buffer); */
    }


    /* find subdomain remainder */
    buffer[0] = 0;
    if (matches[4].rm_so < 0) {
        /* no match */
        if (UDLsubRemainder != NULL) {
            *UDLsubRemainder = NULL;
        }
    }
    else {
        len = matches[4].rm_eo - matches[4].rm_so;
        strncat(buffer, udlRemainder+matches[4].rm_so, len);
                
        if (UDLsubRemainder != NULL) {
            *UDLsubRemainder = (char *) strdup(buffer);
        }        
/* printf("parseUDL: subdomain remainder = %s, len = %d\n", buffer, len); */
    }


    /* find optional parameters */
    len = strlen(buffer);
    while (len > 0) {
        /* find cmsgpassword parameter if it exists*/
        /* look for ?cmsgpassword=value& or &cmsgpassword=value& */
        pattern = "[&\\?]cmsgpassword=([a-zA-Z0-9]+)&?";

        /* compile regular expression */
        err = cMsgRegcomp(&compiled, pattern, REG_EXTENDED | REG_ICASE);
        if (err != 0) {
            break;
        }
        
        /* this is the udl remainder in which we look */
        remain = strdup(buffer);
        
        /* find matches */
        err = cMsgRegexec(&compiled, remain, 2, matches, 0);
        /* if match */
        if (err == 0) {
          /* find password */
          if (matches[1].rm_so >= 0) {
             buffer[0] = 0;
             len = matches[1].rm_eo - matches[1].rm_so;
             strncat(buffer, remain+matches[1].rm_so, len);
             if (password != NULL) {
               *password = (char *) strdup(buffer);
             }        
/* printf("parseUDL: password = %s\n", buffer); */
          }
        }
        
        /* free up memory */
        cMsgRegfree(&compiled);
       
        /* find broadcast timeout parameter if it exists */
        /* look for ?broadcastTO=value& or &broadcastTO=value& */
        pattern = "[&\\?]broadcastTO=([0-9]+)?";

        /* compile regular expression */
        err = cMsgRegcomp(&compiled, pattern, REG_EXTENDED | REG_ICASE);
        if (err != 0) {
            break;
        }

        /* find matches */
        err = cMsgRegexec(&compiled, remain, 2, matches, 0);
        /* if match */
        if (err == 0) {
          /* find timeout */
          if (matches[1].rm_so >= 0) {
             buffer[0] = 0;
             len = matches[1].rm_eo - matches[1].rm_so;
             strncat(buffer, remain+matches[1].rm_so, len);
             if (timeout != NULL) {
               *timeout = atoi(buffer);
             }        
/* printf("parseUDL: timeout = %d seconds\n", atoi(buffer)); */
          }
        }
        
        /* free up memory */
        cMsgRegfree(&compiled);
                
        free(remain);
        break;
    }
    
    /* UDL parsed ok */
/* printf("DONE PARSING UDL\n"); */
    free(udl);
    free(buffer);
    return(CMSG_OK);
}

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


/*-------------------------------------------------------------------*/
