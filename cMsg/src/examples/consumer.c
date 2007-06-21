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
#include <strings.h>
#include <time.h>
#include <pthread.h>

#include "cMsg.h"
#include "cMsgDomain.h"

int count = 0;
void *domainId;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;


/******************************************************************/
static void callback(void *msg, void *arg) {
  /*
  int cueSize;
  struct timespec sleeep;

  sleeep.tv_sec  = 0;
  sleeep.tv_nsec = 10000000;
  */
  

  pthread_mutex_lock(&mutex);
  /*
  cMsgGetSubscriptionCueSize(msg, &cueSize);
  printf("%d\n",cueSize);
  */
  count++;

  pthread_mutex_unlock(&mutex);
  /*nanosleep(&sleeep, NULL);*/
  
  /* user MUST free messages passed to the callback */
  cMsgFreeMessage(&msg);
}



/******************************************************************/
static void usage() {
  printf("Usage:  consumer <name> <UDL>\n");
}


/******************************************************************/
int main(int argc,char **argv) {

  char *myName        = "consumer";
  char *myDescription = "C consumer";
  char *subject       = "SUBJECT";
  char *type          = "TYPE";
  char *UDL           = "cMsg:cMsg://localhost/cMsg/test";
  /* char *UDL           = "cMsg:cMsg://broadcast/cMsg/test"; */
  int   err, debug = 1, loops=5;
  cMsgSubscribeConfig *config;
  void *unSubHandle;
  
  /* msg rate measuring variables */
  int             period = 5, ignore=0;
  double          freq, freqAvg=0., totalT=0.;
  long long       totalC=0;
  

  
  if (argc > 1) {
    myName = argv[1];
    if (strcmp(myName, "-h") == 0) {
      usage();
      return(-1);
    }
  }
  
  if (argc > 2) {
    UDL = argv[2];
    if (strcmp(UDL, "-h") == 0) {
      usage();
      return(-1);
    }
  }
  
  if (argc > 3) {
    usage();
    return(-1);
  }
  
  if (debug) {
    printf("Running the cMsg consumer, \"%s\"\n", myName);
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
  cMsgSubscribeSetMaxCueSize(config, 10000);
  cMsgSubscribeSetSkipSize(config, 20);
  cMsgSubscribeSetMaySkip(config,0);
  cMsgSubscribeSetMustSerialize(config, 1);
  cMsgSubscribeSetMaxThreads(config, 290);
  cMsgSubscribeSetMessagesPerThread(config, 150);
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
    
  while (loops-- > 0) {
      count = 0;
      
      /* wait for messages */
      sleep(period);
      
      /* calculate rate */
      if (!ignore) {
          totalT += period;
          totalC += count;
          freq    = (double)count/period;
          freqAvg = totalC/totalT;
          printf("count = %d, %9.1f Hz, %9.1f Hz Avg.\n", count, freq, freqAvg);
      }
      else {
          ignore--;
      }
  }
  
  cMsgUnSubscribe(domainId, unSubHandle);
  cMsgDisconnect(&domainId);

  return(0);
}
