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
#include <unistd.h>

#include "cMsg.h"


int main(int argc,char **argv) {  
  char *myName = "monie";
  char *myDescription = "C-monitor";
  char *text;
  int err, debug=1,loops=10;
  void *domainId;
  void *msg;
    
  if (argc > 1) {
    myName = argv[1];
  }
  
  if (debug) {
    printf("Running the cMsg C monitor, \"%s\"\n", myName);
  }
  
  /* connect to cMsg server */
  err = cMsgConnect("cMsg:cMsg://aslan:3456/cMsg/test/", myName, myDescription, &domainId);
  if (err != CMSG_OK) {
      printf("cMsgConnect: %s\n",cMsgPerror(err));
      exit(1);
  }
  
  while(loops-->0) {
    err = cMsgMonitor(domainId, "junk", &msg);
    if (err != CMSG_OK) {
      printf("cMsgMonitor: %s\n",cMsgPerror(err));
      break;
    }
    cMsgGetText(msg, &text);
    printf("%s\n",text);
    sleep(3);
  }
    
  return(0);
}

