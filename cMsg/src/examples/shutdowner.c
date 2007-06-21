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

/* shutdown handler function */
static void shutdownHandler(void *arg) {
  printf("RAN SHUTDOWN HANDLER!!\n");
  exit(-1);
}



int main(int argc,char **argv) {  
  char *myName = "shutdowner";
  char *myDescription = "C shutdowner";
  int err, debug=1;
  void *domainId;
    
  if (argc > 1) {
    myName = argv[1];
  }
  
  if (debug) {
    printf("Running the cMsg C shutdowner, \"%s\"\n", myName);
  }
  
  /* connect to cMsg server */
  err = cMsgConnect("cMsg:cMsg://aslan:3456/cMsg/test/", myName, myDescription, &domainId);
  if (err != CMSG_OK) {
      printf("cMsgConnect: %s\n",cMsgPerror(err));
      exit(1);
  }
  
  /* install shutdown handler */
  err = cMsgSetShutdownHandler(domainId, shutdownHandler, NULL);
  if (err != CMSG_OK) {
      printf("cMsgSetShutdownHandler: %s\n",cMsgPerror(err));
      exit(1);
  }
    
  printf("Kill myself now\n");

  /* tell client "shutdowner" (including me) to shutdown */
  err = cMsgShutdownClients(domainId, "shutdowner", CMSG_SHUTDOWN_INCLUDE_ME);
  if (err != CMSG_OK) {
      printf("cMsgShutdown: %s\n",cMsgPerror(err));
      exit(1);
  }
  
  /* wait 10 sec */
  sleep(10);
  
  /* If we got here, my shutdown handler didn't kill me. */
  printf("Oops, shutdown handler didn't kill me!\n");
  
  return(0);
}

