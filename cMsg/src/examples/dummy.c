/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 21-Mar-2004, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 *  This application is just to illustrate how to call the funcstions of the
 *  dummy domain. The dummy domain itself is just an outline or example of how
 *  to go about writing a domain in C that can be dynamically loaded.
 * 
 *----------------------------------------------------------------------------*/
 
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>
#include <time.h>

#include "cMsg.h"



/******************************************************************/
static void callback(void *msg, void *arg) {}

/* shutdown handler function */
static void shutdownHandler(void *arg) {
  printf("RAN SHUTDOWN HANDLER!!\n");
  return;
}



/******************************************************************/
int main(int argc,char **argv) {

  char *myName   = "Dumb Dumb";
  char *myDescription = "Dummy consumer";
  char *subject = "SUBJECT";
  char *type    = "TYPE";
  char *UDL     = "cMsG:DUmmY://34aslan:3456/cMsg/test";
  char *UDL2    = "dummy://$blech:2345/";
  int   err, debug = 1, response;
  cMsgSubscribeConfig *config;
  void *domainId, *domainId2, *msg;
  void *unSubHandle;
 
  printf("Running the Dummy consumer, \"%s\"\n", myName);

  /* connect to cMsg server */
  err = cMsgConnect(UDL, myName, myDescription, &domainId);
  if (err != CMSG_OK) {
      if (debug) {
          printf("cMsgConnect: %s\n",cMsgPerror(err));
      }
      exit(1);
  }
  
  printf("  connected to, %s\n", UDL);
  
  /* connect to cMsg server */
  err = cMsgConnect(UDL2, myName, myDescription, &domainId2);
  if (err != CMSG_OK) {
      if (debug) {
          printf("cMsgConnect: %s\n",cMsgPerror(err));
      }
      exit(1);
  }
  printf("  connected to, %s\n", UDL2);
  
  /* create message */
  msg = cMsgCreateMessage();
  cMsgSetSubject(msg, subject);
  cMsgSetType(msg, type);
  
  /* start receiving messages */
  cMsgReceiveStart(domainId);
  cMsgReceiveStop(domainId);
  cMsgFlush(domainId, 0);
  
  /* set the subscribe configuration */
  config = cMsgSubscribeConfigCreate();
  cMsgSetDebugLevel(CMSG_DEBUG_INFO);
  
  /* subscribe */
  cMsgSubscribe(domainId, subject, type, callback, NULL, config, &unSubHandle);
   
  /* unsubscribe */
  cMsgUnSubscribe(domainId, unSubHandle);
  
  /* send */
  cMsgSend(domainId, msg);
  
  /* syncSend */
  cMsgSyncSend(domainId, msg, NULL, &response);
  
  /* subAndGet */
  cMsgSubscribeAndGet(domainId, subject, type, NULL, &msg);
  
  /* sendAndGet */
  cMsgSendAndGet(domainId, msg, NULL, &msg);
  
  /* set shutdown handler */
  cMsgSetShutdownHandler(domainId, shutdownHandler, NULL);
  
  /* shutdown a client call "shutdowner" */
  cMsgShutdownClients(domainId, "shutdowner", CMSG_SHUTDOWN_INCLUDE_ME);
  
  /* shutdown a server call "shutdowner" */
  cMsgShutdownServers(domainId, "shutdowner", CMSG_SHUTDOWN_INCLUDE_ME);
 
  /* disconnect */
  cMsgDisconnect(&domainId);
  
  return(0);
}
