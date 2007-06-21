/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 8-Jul-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>
#include <time.h>
#include <pthread.h>

#include "cMsg.h"
#include "cMsgDomain.h"

int count = 0, oldInt=-1;
void *domainId;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;


/******************************************************************/
static void callback(void *msg, void *arg) {
  /*
  int userInt;
  struct timespec sleeep;
  
  sleeep.tv_sec  = 1;
  sleeep.tv_nsec = 0;
  */ 
  
  pthread_mutex_lock(&mutex);
  
  count++;
  /*printf("Running callback, count = %d\n", count);*/
  
  /*nanosleep(&sleeep, NULL);*/
  
  /*
  cMsgGetUserInt(msg, &userInt);
  if (userInt != oldInt+1)
    printf("%d -> %d; ", oldInt, userInt);
  
  oldInt = userInt;
  */  
  pthread_mutex_unlock(&mutex);
  
  /* user MUST free messages passed to the callback */
  cMsgFreeMessage(&msg);
}



/******************************************************************/
int main(int argc,char **argv) {

  char *myName   = "Coda component name";
  char *myDescription = "RC test";
  char *subject = "rcSubject";
  char *type    = "rcType";
  
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
  char *UDL     = "cMsg:rc://?expid=carlExp";
  
  int   err, debug = 1;
  cMsgSubscribeConfig *config;
  void *unSubHandle, *msg;
  int  loops = 5;
  

  if (argc > 1) {
    myName = argv[1];
  }
  
  if (argc > 2) {
    UDL = argv[2];
  }
  
  if (debug) {
    printf("Running the cMsg client, \"%s\"\n", myName);
    printf("  connecting to, %s\n", UDL);
  }

  /* connect to cMsg server */
  err = cMsgConnect(UDL, myName, myDescription, &domainId);
  if (err != CMSG_OK) {
      if (debug) {
          printf("cMsgConnect: %s\n",cMsgPerror(err));
      }
      exit(1);
  }
  
  /* start receiving messages */
  cMsgReceiveStart(domainId);
  
  /* set the subscribe configuration */
  config = cMsgSubscribeConfigCreate();
  cMsgSetDebugLevel(CMSG_DEBUG_ERROR);
  
  /* subscribe */
  err = cMsgSubscribe(domainId, subject, type, callback, NULL, config, &unSubHandle);
  if (err != CMSG_OK) {
      if (debug) {
          printf("cMsgSubscribe: %s\n",cMsgPerror(err));
      }
      exit(1);
  }
  
  cMsgSubscribeConfigDestroy(config);
  
  msg = cMsgCreateMessage();
  cMsgSetSubject(msg, "subby");
  cMsgSetType(msg, "typey");
  cMsgSetText(msg, "send with TCP");
  cMsgSetReliableSend(msg, 1);
    
  while (loops-- > 0) {      
      /* send msgs to rc server */
      err = cMsgSend(domainId, msg);
      if (err != CMSG_OK) {
          printf("ERROR in sending message!!\n");
          continue;
      }
  }
    
  cMsgSetText(msg, "send with UDP");
  cMsgSetReliableSend(msg, 0);
  loops=5;
  while (loops-- > 0) {      
      err = cMsgSend(domainId, msg);
      if (err != CMSG_OK) {
          printf("ERROR in sending message!!\n");
          continue;
      }
  }
  
  sleep(7);
 
  cMsgSetSubject(msg, "blah");
  cMsgSetType(msg, "yech");

  loops=5;
  while (loops-- > 0) {      
      err = cMsgSend(domainId, msg);
      if (err != CMSG_OK) {
          printf("ERROR in sending message!!\n");
          continue;
      }
  }
  
  
  cMsgSetText(msg, "send with TCP");
  cMsgSetSubject(msg, "subby");
  cMsgSetType(msg, "typey");
  cMsgSetReliableSend(msg, 1);
  loops=5;
  while (loops-- > 0) {      
      err = cMsgSend(domainId, msg);
      if (err != CMSG_OK) {
          printf("ERROR in sending message!!\n");
          continue;
      }
  }
    
/*printf("rcClient try disconnect\n");*/
  cMsgFreeMessage(&msg);
  cMsgUnSubscribe(domainId, unSubHandle);
  cMsgDisconnect(&domainId);
/*printf("rcClient done disconnect\n");*/

  return(0);
}
