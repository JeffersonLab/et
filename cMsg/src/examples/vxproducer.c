/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 7-Sep-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/
 
#ifdef VXWORKS
#include <vxWorks.h>
#include <string.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>
#include <time.h>

#include "cMsg.h"
extern int vxTicks;

int cMsgProducer(void) {
  
  char *myName = "VX-Producer";
  char *myDescription = "VX - producer";
  char *subject = "SUBJECT";
  char *type    = "TYPE";
  char *text    = "TEXT";
  char *bytes   = NULL;
  char *UDL     = "cMsg:cMsg://aslan:3456/cMsg/test";
  int   err, debug=1, msgSize=0, counter=0;
  void *msg, *domainId;
  
  /* msg rate measuring variables */
  int             dostring=1, count, i, delay=1, loops=5, ignore=0, numTimes=5;
  struct timespec t1, t2;
  double          freq, freqAvg=0., deltaT, totalT=0;
  long long       totalC=0;
  
  /*
  struct timespec t0;
  double          tickRate;
  int             response, once=0;
  int             tick1, tick2, tick0=0;
  */
  
  char *argv1 = "-s";
  char *argv2 = "5000";
  int   argc = 2;
  
  if (argc > 1) {
     if (strcmp("-s", argv1) == 0) {
       dostring = 1;
     }
     else if (strcmp("-b", argv1) == 0) {
       dostring = 0;
     }
     else {
       printf("specify -s or -b flag for string or bytearray data\n");
       exit(1);
     }
  }
  
  if (argc > 2) {
    if (dostring) {
      char *p;
      msgSize = atoi(argv2);
      text = p = (char *) malloc((size_t) (msgSize + 1));
      if (p == NULL) exit(1);
      printf("using text msg size %d\n", msgSize);
      for (i=0; i < msgSize; i++) {
        *p = 'A';
        p++;
      }
      *p = '\0';
    }
    else {
      char *p;
      msgSize = atoi(argv2);
      bytes = p = (char *) malloc((size_t) msgSize);
      if (p == NULL) exit(1);
      printf("using array msg size %d\n", msgSize);
      for (i=0; i < msgSize; i++) {
        *p = i%255;
        p++;
      }
    }
  }
  else {
      printf("using no text or byte array\n");
      dostring = 1;
  }
  
    
  if (debug) {
    printf("Running the cMsg producer, \"%s\"\n", myName);
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
  
  /* create message to be sent */
  msg = cMsgCreateMessage();
  cMsgSetSubject(msg, subject);
  cMsgSetType(msg, type);
  
  if (dostring) {
    printf("setting text\n");
    cMsgSetText(msg, text);
  }
  else {
    printf("setting byte array\n");
    cMsgSetByteArrayAndLimits(msg, bytes, 0, msgSize);
  }
  
  /*
  tickRate = sysClkRateGet();
  
  while (1) {
      count = 0;
      
      tick1 = vxTicks;
      
      if (!once) {
        tick0 = tick1;
        once++;
      }

      for (i=0; i < loops; i++) {
          cMsgSetUserInt(msg, counter);
          if (counter % 5000 == 0) {
            tick2 = vxTicks;
            deltaT = ((double)(tick2-tick0))/tickRate;
            printf("%d @ %6.3f sec, ticks = %d\n", counter, deltaT, tick2);
          }
          counter++;
          if (cMsgSend(domainId, msg) != CMSG_OK) {
            printf("cMsgSend: %s\n",cMsgPerror(err));
            fflush(stdout);
            goto end;
          }
          cMsgFlush(domainId,NULL);
          count++;
          
          if (delay != 0) {
              sleep(delay);
          }          
      }

      deltaT = ((double)(vxTicks-tick1))/tickRate;
      totalT += deltaT;
      totalC += count;
      freq    = count/deltaT;
      freqAvg = (double)totalC/totalT;

      printf("count = %d, %9.1f Hz, %9.1f Hz Avg., deltaT = %6.3f sec\n",
              count, freq, freqAvg, deltaT);
  }
  */
  
  while (numTimes-- > 0) {
      count = 0;
      
      clock_gettime(CLOCK_REALTIME, &t1);

      for (i=0; i < loops; i++) {
          cMsgSetUserInt(msg, counter++);
          if (cMsgSend(domainId, msg) != CMSG_OK) {
            printf("cMsgSend: %s\n",cMsgPerror(err));
            fflush(stdout);
            goto end;
          }
          cMsgFlush(domainId,NULL);
          count++;
          
          if (delay != 0) {
              sleep(delay);
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

  end:
  
  err = cMsgDisconnect(&domainId);
  if (err != CMSG_OK) {
      if (debug) {
          printf("%s\n",cMsgPerror(err));
      }
  }
    
  return(0);
}

