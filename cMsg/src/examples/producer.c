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
#else
#include <strings.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>
#include <time.h>

#include "cMsg.h"


/******************************************************************/
static void usage() {
  printf("Usage:  producer [-s <size> | -b <size>] -u <UDL>\n");
  printf("                  -s sets the byte size for text data, or\n");
  printf("                  -b sets the byte size for binary data\n");
  printf("                  -u sets the connection UDL\n");
}


/******************************************************************/
int main(int argc,char **argv) {  

  char *myName        = "producer";
  char *myDescription = "C producer";
  char *subject       = "SUBJECT";
  char *type          = "TYPE";
  char *text          = "JUNK";
  char *bytes         = NULL;
  char *UDL           = "cMsg:cmsg://localhost:3456/cMsg/test";
  char *p;
  int   i, j, err, debug=1, msgSize=0, mainloops=200, response;
  void *msg, *domainId;
  
  /* msg rate measuring variables */
  int             dostring=1, count, delay=0, loops=20000, ignore=0;
  struct timespec t1, t2, sleeep;
  double          freq, freqAvg=0., deltaT, totalT=0.;
  long long       totalC=0;
  
  sleeep.tv_sec  = 3; /* 3 sec */
  sleeep.tv_nsec = 0;
  
  if (argc > 1) {
  
    for (i=1; i<argc; i++) {

       if (strcmp("-s", argv[i]) == 0) {
         if (argc < i+2) {
           usage();
           return(-1);
         }
         
         dostring = 1;
         
         msgSize = atoi(argv[++i]);
         text = p = (char *) malloc((size_t) (msgSize + 1));
         if (p == NULL) exit(1);
         printf("using text msg size %d\n", msgSize);
         for (j=0; j < msgSize; j++) {
           *p = 'A';
           p++;
         }
         *p = '\0';
         /*printf("Text = %s\n", text);*/
         
       }
       else if (strcmp("-b", argv[i]) == 0) {
         if (argc < i+2) {
           usage();
           return(-1);
         }
         
         dostring = 0;
         
         msgSize = atoi(argv[++i]);
         if (msgSize < 1) {
           usage();
           return(-1);
         }
         printf("using array msg size %d\n", msgSize);
         bytes = p = (char *) malloc((size_t) msgSize);
         if (p == NULL) exit(1);
         for (i=0; i < msgSize; i++) {
           *p = i%255;
           p++;
         }

       }
       else if (strcmp(argv[i], "-u") == 0) {
         if (argc < i+2) {
           usage();
           return(-1);
         }
         UDL = argv[++i];
       }
       else if (strcmp(argv[i], "-h") == 0) {
         usage();
         return(-1);
       }
       else {
         usage();
         return(-1);
       }

    }
    
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
  msg = cMsgCreateMessage();    /* allocating mem here */
  cMsgSetSubject(msg, subject); /* allocating mem here */
  cMsgSetType(msg, type);       /* allocating mem here */
 /* cMsgSetReliableSend(msg, 0);*/
  
  if (dostring) {
    printf("  try setting text to %s\n", text);
    cMsgSetText(msg, text);
  }
  else {
    printf("  setting byte array\n");
    cMsgSetByteArrayAndLimits(msg, bytes, 0, msgSize);
  }
   
  while (mainloops-- > 0) {
      count = 0;
      
      /* read time for rate calculations */
      clock_gettime(CLOCK_REALTIME, &t1);

      for (i=0; i < loops; i++) {
          /* send msg */
          /* err = cMsgSyncSend(domainId, msg, NULL, &response); */
          err = cMsgSend(domainId, msg);
          if (err != CMSG_OK) {
            printf("cMsgSend: err = %d, %s\n",err, cMsgPerror(err));
            fflush(stdout);
            goto end;
          }
          cMsgFlush(domainId, NULL);
          count++;
          
          if (delay != 0) {
              nanosleep(&sleeep, NULL);
          }          
      }

      /* rate */
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
printf("producer: will free msg\n");  
  cMsgFreeMessage(&msg);
printf("producer: will disconnect\n");  
  err = cMsgDisconnect(&domainId);
  if (err != CMSG_OK) {
      if (debug) {
          printf("err = %d, %s\n",err, cMsgPerror(err));
      }
  }
    
  return(0);
}
