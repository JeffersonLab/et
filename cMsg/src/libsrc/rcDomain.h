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
 *      Header for rc domain routines
 *
 *----------------------------------------------------------------------------*/
 
/**
 * @file
 * This is the header file for the rc domain implementation of cMsg.
 */
 
#ifndef __rcDomain_h
#define __rcDomain_h

#include "cMsgPrivate.h"
#include "cMsgDomain.h"

#ifdef	__cplusplus
extern "C" {
#endif


/**
 * This structure contains all information concerning a single client
 * connection to this domain.
 */
typedef struct rcDomain_t {  
  
  void *id;            /**< Unique id of connection. */ 
  
  int receiveState;    /**< Boolean telling if messages are being delivered to
                                    callbacks (1) or if they are being igmored (0). */
  int gotConnection;   /**< Boolean telling if connection to rc server is good. */
  
  int sendSocket;      /**< File descriptor for UDP socket to send messages on. */
  int receiveSocket;   /**< File descriptor for TCP socket to receive responses on. */
  int listenSocket;    /**< File descriptor for socket this program listens on for TCP connections. */

  unsigned short sendPort;   /**< Port to send messages to. */
  unsigned short serverPort; /**< Port rc server listens on. */
  unsigned short listenPort; /**< Port this program listens on for this domain's TCP connections. */
  
  char *myHost;       /**< This hostname. */
  char *sendHost;     /**< Host to send messages to. */
  char *serverHost;   /**< Host rc server lives on. */

  char *name;         /**< Name of this user. */
  char *udl;          /**< UDL of rc server. */
  char *description;  /**< User description. */
  char *password;     /**< User password. */
  
  int killClientThread;      /**< Boolean telling if client thread receiving messages should be killed. */
  
  char *msgBuffer;           /**< Buffer used in socket communication to server. */
  int   msgBufferSize;       /**< Size of buffer (in bytes) used in socket communication to server. */
  char *msgInBuffer;         /**< Buffers used in socket communication from server. */

  pthread_t pendThread;      /**< Listening thread. */
  pthread_t clientThread;    /**< Thread from server connecting to client (created by pendThread). */

  /**
   * Read/write lock to prevent connect or disconnect from being
   * run simultaneously with any other function.
   */
  rwLock_t connectLock;
  pthread_mutex_t socketMutex;    /**< Mutex to ensure thread-safety of socket use. */
  pthread_mutex_t subscribeMutex; /**< Mutex to ensure thread-safety of (un)subscribes. */
  pthread_cond_t  subscribeCond;  /**< Condition variable used for waiting on clogged callback cue. */
    
  /** Array of structures - each of which contain a subscription. */
  subInfo subscribeInfo[MAX_SUBSCRIBE]; 
  
  /** Shutdown handler function. */
  cMsgShutdownHandler *shutdownHandler;
  
  /** Shutdown handler user argument. */
  void *shutdownUserArg;  
 
} rcDomain;


/* prototypes */

#ifdef	__cplusplus
}
#endif

#endif
