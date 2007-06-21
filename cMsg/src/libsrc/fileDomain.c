// to do:
//   textOnly



/*----------------------------------------------------------------------------*
 *
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    E.Wolin, 13-Jan-2006, Jefferson Lab                                     *
 *                                                                            *
 *    Authors: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 *
 * Description:
 *
 *  Implements built-in cMsg FILE domain
 *
 *  To enable a new built-in domain:
 *     edit cMsg.c, search for codaDomainTypeInfo, and do the same for the new domain
 *     add new file to C/Makefile.*, same as cMsgDomain.*
 *     add new file to CC/Makefile.*, same as cMsgDomain.*
 *
 *
 *----------------------------------------------------------------------------*/

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cMsgPrivate.h"
#include "cMsg.h"
#include "cMsgNetwork.h"
#include "rwlock.h"

#ifdef VXWORKS
#include <vxWorks.h>
#endif



/* Prototypes of the 14 functions which implement the standard cMsg tasks in the cMsg domain. */
int   cmsg_file_connect(const char *myUDL, const char *myName, const char *myDescription,
                        const char *UDLremainder, void **domainId);
int   cmsg_file_send(void *domainId, const void *msg);
int   cmsg_file_syncSend(void *domainId, const void *msg, const struct timespec *timeout, int *response);
int   cmsg_file_flush(void *domainId, const struct timespec *timeout);
int   cmsg_file_subscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                          void *userArg, cMsgSubscribeConfig *config, void **handle);
int   cmsg_file_unsubscribe(void *domainId, void *handle);
int   cmsg_file_subscribeAndGet(void *domainId, const char *subject, const char *type,
                                const struct timespec *timeout, void **replyMsg);
int   cmsg_file_sendAndGet(void *domainId, const void *sendMsg, const struct timespec *timeout, void **replyMsg);
int   cmsg_file_monitor(void *domainId, const char *command, void **replyMsg);
int   cmsg_file_start(void *domainId);
int   cmsg_file_stop(void *domainId);
int   cmsg_file_disconnect(void **domainId);
int   cmsg_file_shutdownClients(void *domainId, const char *client, int flag);
int   cmsg_file_shutdownServers(void *domainId, const char *server, int flag);
int   cmsg_file_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg);


/** List of the functions which implement the standard cMsg tasks in the cMsg domain. */
static domainFunctions functions = { cmsg_file_connect, cmsg_file_send,
                                     cmsg_file_syncSend, cmsg_file_flush,
                                     cmsg_file_subscribe, cmsg_file_unsubscribe,
                                     cmsg_file_subscribeAndGet, cmsg_file_sendAndGet,
                                     cmsg_file_monitor, cmsg_file_start,
                                     cmsg_file_stop, cmsg_file_disconnect,
                                     cmsg_file_shutdownClients, cmsg_file_shutdownServers,
                                     cmsg_file_setShutdownHandler
};


/* for registering the domain */
domainTypeInfo fileDomainTypeInfo = {"file",&functions};


/* local domain info */
typedef struct {
  char *domain;
  char *host;
  char *name;
  char *descr;
  FILE *file;
  int textOnly;
} fileDomainInfo;


/*-------------------------------------------------------------------*/


int cmsg_file_connect(const char *myUDL, const char *myName, const char *myDescription,
                      const char *UDLremainder, void **domainId) {


  char *fname;
  const char *c;
  int textOnly;
  fileDomainInfo *fdi;
  FILE *f;


  /* get file name and textOnly attribute from UDLremainder */
  if(UDLremainder==NULL)return(CMSG_ERROR);
  c = strchr(UDLremainder,'?');
  if(c==NULL) {
    fname=strdup(UDLremainder);
    textOnly=1;
  } else {
    size_t nc = c-UDLremainder+1;
    fname=(char*)malloc(nc+1);
    strncpy(fname,UDLremainder,nc);
    fname[nc]='\0';

    /* search for textOnly=, case insensitive, 0 is true ??? */
    textOnly=1;
  }


  /* open file */
  f = fopen(fname,"a");
  if(f==NULL)return(CMSG_ERROR);

  
  /* store file domain info */
  fdi = (fileDomainInfo*)malloc(sizeof(fileDomainInfo));
  fdi->domain   = strdup("file");
  fdi->host     = (char*)malloc(256);
  cMsgLocalHost(fdi->host,256);
  fdi->name     = strdup(myName);
  fdi->descr    = strdup(myDescription);
  fdi->file     = f;
  fdi->textOnly = textOnly;
  *domainId=(void*)fdi;


  /* done */
  if(fname!=NULL)free(fname);
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsg_file_send(void *domainId, const void *vmsg) {

  char *s;
  time_t now;
  char nowBuf[32];
#ifdef VXWORKS
  size_t nowLen=sizeof(nowBuf);
#endif
  int stat;
  cMsgMessage_t  *msg = (cMsgMessage_t*)vmsg;
  fileDomainInfo *fdi = (fileDomainInfo*)domainId;
  

  /* set domain */
  msg->domain=strdup(fdi->domain);


  /* check creator */
  if(msg->creator==NULL)msg->creator=strdup(fdi->name);
    

  /* set sender,senderTime,senderHost */
  msg->sender            = strdup(fdi->name);
  msg->senderHost        = strdup(fdi->host);
  msg->senderTime.tv_sec = time(NULL);
  

  /* write msg to file */
  if(fdi->textOnly!=0) {
    cMsgToString(vmsg,&s);
    stat = fwrite(s,strlen(s),1,fdi->file);
    free(s);
  } else {
    now=time(NULL);
#ifdef VXWORKS
    ctime_r(&now,nowBuf,&nowLen);
#else
    ctime_r(&now,nowBuf);
#endif
    nowBuf[strlen(nowBuf)-1]='\0';
    s=(char*)malloc(strlen(nowBuf)+strlen(msg->text)+64);
    sprintf(s,"%s:    %s\n",nowBuf,msg->text);
    stat = fwrite(s,strlen(s),1,fdi->file);
    free(s);
  }


  /* done */
  return((stat!=0)?CMSG_OK:CMSG_ERROR);
}


/*-------------------------------------------------------------------*/


int cmsg_file_syncSend(void *domainId, const void *vmsg, const struct timespec *timeout, int *response) {
  *response=0;
  return(cmsg_file_send(domainId,vmsg));
}


/*-------------------------------------------------------------------*/


int cmsg_file_subscribeAndGet(void *domainId, const char *subject, const char *type,
                           const struct timespec *timeout, void **replyMsg) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_sendAndGet(void *domainId, const void *sendMsg, const struct timespec *timeout,
                      void **replyMsg) {
  return(CMSG_NOT_IMPLEMENTED);
}

/*-------------------------------------------------------------------*/


/**
 * The monitor function is not implemented in the rc domain.
 */   
int cmsg_file_monitor(void *domainId, const char *command, void **replyMsg) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_flush(void *domainId, const struct timespec *timeout) {  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


int cmsg_file_subscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                     void *userArg, cMsgSubscribeConfig *config, void **handle) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_unsubscribe(void *domainId, void *handle) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_start(void *domainId) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_stop(void *domainId) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_disconnect(void **domainId) {

  int stat;
  fileDomainInfo *fdi;

  if(domainId==NULL)return(CMSG_ERROR);
  if(*domainId==NULL)return(CMSG_ERROR);
  fdi = (fileDomainInfo*) (*domainId);

  /* close file */
  stat = fclose(fdi->file);
  
  /* clean up */
  if(fdi->domain!=NULL) free(fdi->domain);
  if(fdi->host!=NULL)   free(fdi->host);
  if(fdi->name!=NULL)   free(fdi->name);
  if(fdi->descr!=NULL)  free(fdi->descr);
  free(fdi);

  /* done */
  return((stat==0)?CMSG_OK:CMSG_ERROR);
}


/*-------------------------------------------------------------------*/
/*   shutdown handler functions                                      */
/*-------------------------------------------------------------------*/


int cmsg_file_setShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_shutdownClients(void *domainId, const char *client, int flag) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/


int cmsg_file_shutdownServers(void *domainId, const char *server, int flag) {
  return(CMSG_NOT_IMPLEMENTED);
}


/*-------------------------------------------------------------------*/
