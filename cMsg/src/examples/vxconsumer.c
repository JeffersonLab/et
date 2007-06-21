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
#ifdef sun
#include <thread.h>
#endif

#include "cMsg.h"

int count = 0;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

static void callback(void *msg, void *arg);
static int conDiscon(char *UDL);

/******************************************************************/

int cMsgConsumer(void) {  
  char *myName   = "VX-consumer";
  char *myDescription = "trial run";
  char *subject = "SUBJECT";
  char *type    = "TYPE";
  char *UDL     = "cMsg:cMsg://broadcast:22333/cMsg/vx";
  int   err, loops=5;
  void *domainId;
  cMsgSubscribeConfig *config;
  void *unsubHandle;
  
  /* msg rate measuring variables */
  int             period = 5, ignore=0;
  double          freq, freqAvg=0., totalT=0.;
  long long       totalC=0;
  
  printf("Running cMsg consumer %s\n", myName);
    
  err = cMsgConnect(UDL, myName, myDescription, &domainId);
   
  cMsgReceiveStart(domainId);
  
  config = cMsgSubscribeConfigCreate();
  cMsgSubscribeSetMaxCueSize(config, 1000);
  cMsgSubscribeSetSkipSize(config, 200);
  cMsgSubscribeSetMaySkip(config, 0);
  cMsgSubscribeSetMustSerialize(config, 1);
  cMsgSubscribeSetMaxThreads(config, 10);
  cMsgSubscribeSetMessagesPerThread(config, 150);
  cMsgSetDebugLevel(CMSG_DEBUG_ERROR);

  /* subscribe */
  err = cMsgSubscribe(domainId, subject, type, callback, NULL, config, &unsubHandle);
  if (err != CMSG_OK) {
      printf("cMsgSubscribe: %s\n",cMsgPerror(err));
      exit(1);
  }
       
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
  
  cMsgDisconnect(&domainId);

  return(0);
}

/******************************************************************/

static void callback(void *msg, void *arg) {
pthread_mutex_lock(&mutex);
  count++;
pthread_mutex_unlock(&mutex);
  cMsgFreeMessage(&msg);
}

/******************************************************************/

int cMsgGetConsumer(void) {
    /*char *subject = "responder";*/
    char *subject = "SUBJECT";
    char *type    = "TYPE";
    char *UDL     = "cMsg:cMsg://broadcaxt:22333/cMsg/vx";
    char *myName  = "VX-getconsumer";
    char *myDescription = "trial run";
    int   i, err;
    void *domainId, *msg, *getMsg;
    
    /* msg rate measuring variables */
    int             count, loops=1000, ignore=5;
    struct timespec t1, t2, timeout;
    double          freq, freqAvg=0., deltaT, totalT=0;
    long long       totalC=0;

    printf("Running cMsg GET consumer %s\n", myName);

    err = cMsgConnect(UDL, myName, myDescription, &domainId);
 
    cMsgReceiveStart(domainId);
    
    msg = cMsgCreateMessage();
    cMsgSetSubject(msg, subject);
    cMsgSetType(msg, type);
    cMsgSetText(msg, "Message 1");
    
    timeout.tv_sec  = 1;
    timeout.tv_nsec = 0;
  
    while (1) {
        count = 0;
        clock_gettime(CLOCK_REALTIME, &t1);

        /* do a bunch of gets */
        for (i=0; i < loops; i++) {
            /*err = cMsgSendAndGet(domainId, msg, &timeout, &getMsg);*/
            err = cMsgSubscribeAndGet(domainId, subject, type, &timeout, &getMsg);

            if (err == CMSG_TIMEOUT) {
                printf("TIMEOUT in sendAndGet\n");
            }
            else {
                count++;
                if (getMsg != NULL) {
                  cMsgFreeMessage(&getMsg);
                }
            }
        }
        
        if (!ignore) {
          clock_gettime(CLOCK_REALTIME, &t2);
          deltaT  = (double)(t2.tv_sec - t1.tv_sec) + 1.e-9*(t2.tv_nsec - t1.tv_nsec);
          totalT += deltaT;
          totalC += count;
          freq    = count/deltaT;
          freqAvg = (double)totalC/totalT;

          printf("count = %d, %9.1f Hz, %9.1f Hz Avg.\n", count, freq, freqAvg);
        }
        else {
          ignore--;
        } 
   }
}



/******************************************************************/

int cMsgConDiscon(void) {
    return conDiscon("cMsg://broadcast:22333/cMsg/vx?broadcastTO=5");
}

int rcConDiscon(void) {
    return conDiscon("rc://33444/?expid=carlExp&broadcastTO=5");
}

/***********************************************
 * This routine just connects and disconnects
 ***********************************************/
 
static int conDiscon(char *UDL) {
  char *myName   = "VX-conDiscon";
  char *myDescription = "test connects and disconnects";
  void *domainId;
    
  int  err, debug = 1, loops = 0;
  struct timespec sleeep;
  
  sleeep.tv_sec  = 0;
  sleeep.tv_nsec = 500000000; /* 500 millisec */
   
  if (debug) {
    printf("Running the cMsg client, \"%s\"\n", myName);
    printf("  connecting to, %s\n", UDL);
  }
  
  /* connect/disconnect to cMsg server */
  while (loops++ < 1030) {
      err = cMsgConnect(UDL, myName, myDescription, &domainId);
      if (err != CMSG_OK) {
          if (debug) {
              printf("cMsgConnect: %s\n",cMsgPerror(err));
          }
          cMsgDisconnect(&domainId);
          return(1);
      }
            
      err = cMsgDisconnect(&domainId);
      if (err != CMSG_OK) {
          if (debug) {
              printf("cMsgConnect: %s\n",cMsgPerror(err));
          }
          return(1);
      }
      printf("Loops = %d\n", loops);
      nanosleep(&sleeep, NULL);
  }
  
printf("cMsg conDison client done\n");

  return(0);
}

/******************************************************************/

static void callback2(void *msg, void *arg) {
  static int myCount=0;
  
  myCount++;
  printf("Running reconnect callback, count = %d\n", myCount);
    
  cMsgFreeMessage(&msg);
}


/******************************************************************/
int reconnect(void) {

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
  char *UDL = "cMsg:rc://33444/?expid=carlExp";
  
  int   err, debug = 1, loops = 5;
  cMsgSubscribeConfig *config;
  void *unSubHandle, *msg, *domainId;

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
  err = cMsgSubscribe(domainId, subject, type, callback2, NULL, config, &unSubHandle);
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


