/*----------------------------------------------------------------------------*
 *                                                                            *
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    c. Timmer, 31-Mar-2006, Jefferson Lab                                     *
 *                                                                            *
 *    Authors: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 *
 * Description:
 *
 *  Implements a dummy domain (just prints stuff out) to serve as an example
 *  of how to go about writing a dynamically loadable domain in C.
 *
 *----------------------------------------------------------------------------*/


#include "cMsgPrivate.h"
#include "cMsg.h"
#include "cMsgNetwork.h"


#ifdef VXWORKS
#include <vxWorks.h>
#endif



/* Prototypes of the 14 functions which implement the standard cMsg tasks. */
int   cmsgd_connect(const char *myUDL, const char *myName, const char *myDescription,
              const char *UDLremainder, void **domainId);
int   cmsgd_send(void *domainId, void *msg);
int   cmsgd_syncSend(void *domainId, void *msg, int *response);
int   cmsgd_flush(void *domainId);
int   cmsgd_subscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                           void *userArg, cMsgSubscribeConfig *config, void **handle);
int   cmsgd_unsubscribe(void *domainId, void *handle);
int   cmsgd_subscribeAndGet(void *domainId, const char *subject, const char *type,
                                 const struct timespec *timeout, void **replyMsg);
int   cmsgd_sendAndGet(void *domainId, void *sendMsg, const struct timespec *timeout, void **replyMsg);
int   cmsgd_start(void *domainId);
int   cmsgd_stop(void *domainId);
int   cmsgd_disconnect(void *domainId);
int   cmsgd_shutdownClients(void *domainId, const char *client, int flag);
int   cmsgd_shutdownServers(void *domainId, const char *server, int flag);
int   cmsgd_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg);


/*-------------------------------------------------------------------*/


int cmsgd_connect(const char *myUDL, const char *myName, const char *myDescription,
                  const char *UDLremainder, void **domainId) {
  printf("Connect, my name is %s\n", myName);
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_send(void *domainId, void *vmsg) {  
  printf("Send\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_syncSend(void *domainId, void *vmsg, int *response) {
  *response=0;
  printf("SyncSend\n");
  return(cmsgd_send(domainId, vmsg));
}


/*-------------------------------------------------------------------*/


int cmsgd_subscribeAndGet(void *domainId, const char *subject, const char *type,
                          const struct timespec *timeout, void **replyMsg) {
  printf("SubscribeAndGet\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_sendAndGet(void *domainId, void *sendMsg, const struct timespec *timeout,
                     void **replyMsg) {
  printf("SendAndGet\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_flush(void *domainId) {  
  printf("Flush\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_subscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                    void *userArg, cMsgSubscribeConfig *config, void **handle) {
  printf("Subscribe\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_unsubscribe(void *domainId, void *handle) {
  printf("Unsubscribe\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_start(void *domainId) {
  printf("Start\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_stop(void *domainId) {
  printf("Stop\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_disconnect(void *domainId) {
  printf("Disconnect\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*   shutdown handler functions                                      */
/*-------------------------------------------------------------------*/


int cmsgd_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg) {
  printf("SetShutdownHandler\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_shutdownClients(void *domainId, const char *client, int flag) {
  printf("ShutdownClients\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsgd_shutdownServers(void *domainId, const char *server, int flag) {
  printf("ShutdownServers\n");
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
