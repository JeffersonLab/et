/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 3-Mar-2005, Jefferson Lab                                    *
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
#include <math.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <pthread.h>

#include "cMsg.h"

int count = 0;
void *domainId;
 
void mycallback(void *msg, void *arg) {
    struct timespec wait;
    /* Create a response to the incoming message */
    void *sendMsg = cMsgCreateResponseMessage(msg);
    /*
     * If we've run out of memory, msg is NULL, or
     * msg was not sent from a sendAndGet() call,
     * sendMsg will be NULL.
     */
    if (sendMsg == NULL) {
        cMsgFreeMessage(&msg);
        return;
    }
    /*sleep(1);*/
    cMsgSetSubject(sendMsg, "RESPONDING");
    cMsgSetType(sendMsg,"TO MESSAGE");
    cMsgSetText(sendMsg,"responder's text");
    
    cMsgSend(domainId, sendMsg);
    cMsgFlush(domainId, NULL);
    
    /*
     * By default, subscriptions run callbacks serially. 
     * If that is not the case, global data (like "count")
     * must be mutex protected.
     */
    count++;
    
    /* user MUST free messages passed to the callback */
    cMsgFreeMessage(&msg);
    /* user MUST free messages created in this callback */
    cMsgFreeMessage(&sendMsg);
}


/******************************************************************/
int main(int argc,char **argv) {  

  char *myName   = "C getResponder";
  char *myDescription = "C getresponder";
  char *subject = "SUBJECT";
  char *type    = "TYPE";
  char *UDL     = "cMsg:cMsg://localhost:3456/cMsg/test";
  int   err, debug = 1, loops=0;
  cMsgSubscribeConfig *config;
  void *unSubHandle;

  /* msg rate measuring variables */
  int             period = 5; /* sec */
  double          freq, freqAvg=0., totalT=0.;
  long long       totalC=0;

  if (argc > 1) {
    myName = argv[1];
  }
  
  if (debug) {
    printf("Running the cMsg C getResponsder, \"%s\"\n", myName);
    cMsgSetDebugLevel(CMSG_DEBUG_ERROR);
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
  
  /* set debug level */
  cMsgSetDebugLevel(CMSG_DEBUG_ERROR);
    
  /* set the subscribe configuration */
  config = cMsgSubscribeConfigCreate();
  cMsgSubscribeSetMaxCueSize(config, 1000);
  cMsgSubscribeSetSkipSize(config, 200);
  cMsgSubscribeSetMaySkip(config, 0);
  cMsgSubscribeSetMustSerialize(config, 1);
  cMsgSubscribeSetMaxThreads(config, 10);
  cMsgSubscribeSetMessagesPerThread(config, 150);
  cMsgSetDebugLevel(CMSG_DEBUG_ERROR);

  /* subscribe with default configuration */
  err = cMsgSubscribe(domainId, subject, type, mycallback, NULL, config, &unSubHandle);
  if (err != CMSG_OK) {
      if (debug) {
          printf("cMsgSubscribe: %s\n",cMsgPerror(err));
      }
      exit(1);
  }

  while (1) {
      count = 0;
      
      /* wait for messages */
      sleep(period);
      
      /* calculate rate */
      totalT += period;
      totalC += count;
      freq    = (double)count/period;
      freqAvg = totalC/totalT;
      printf("count = %d, %9.0f Hz, %9.0f Hz Avg.\n", count, freq, freqAvg);
      if (++loops > 99) break;
  }

  return(0);
}
