/*----------------------------------------------------------------------------*
*  Copyright (c) 2005        Southeastern Universities Research Association, *
*                            Thomas Jefferson National Accelerator Facility  *
*                                                                            *
*    This software was developed under a United States Government license    *
*    described in the NOTICE file included as part of this distribution.     *
*                                                                            *
*    E.Wolin, 25-Feb-2005, Jefferson Lab                                     *
*                                                                            *
*    Authors: Elliott Wolin                                                  *
*             wolin@jlab.org                    Jefferson Lab, MS-6B         *
*             Phone: (757) 269-7365             12000 Jefferson Ave.         *
*             Fax:   (757) 269-5519             Newport News, VA 23606       *
*
*----------------------------------------------------------------------------*/


#ifndef _cMsg_hxx
#define _cMsg_hxx


#include <cMsg.h>
#include <string>
#include <exception>


/**
 * All cMsg symbols reside in the cmsg namespace.  
 */
namespace cmsg {

using namespace std;


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Exception includes description and return code.
 */
class cMsgException : public exception {

public:
  cMsgException(void);
  cMsgException(const string &descr);
  cMsgException(const string &descr, int code);
  cMsgException(const cMsgException &e);
  virtual ~cMsgException(void) throw();

  virtual string toString(void) const throw();
  virtual const char *what(void) const throw();


public:
  string descr;    /**<Description.*/
  int returnCode;  /**<Return code.*/
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Wrapper for cMsg message class.  
 */
class cMsgMessage {

  friend class cMsg;  /**<Allows cMsg to see myMsgPointer.*/
  
  
public:
  cMsgMessage(void) throw(cMsgException);
  cMsgMessage(const cMsgMessage &m) throw(cMsgException);
  cMsgMessage(void *msgPointer) throw(cMsgException);
  virtual ~cMsgMessage(void);

  virtual string getSubject(void) const throw(cMsgException);
  virtual void setSubject(const string &subject) throw(cMsgException);
  virtual string getType(void) const throw(cMsgException);
  virtual void setType(const string &type) throw(cMsgException);
  virtual string getText(void) const throw(cMsgException);
  virtual void setText(const string &text) throw(cMsgException);
  virtual void setByteArrayLength(int length) throw(cMsgException);
  virtual int getByteArrayLength(void) const throw(cMsgException);
  virtual void setByteArrayOffset(int offset) throw(cMsgException);
  virtual int getByteArrayOffset(void) const throw(cMsgException);
  virtual int getByteArrayEndian(void) const throw(cMsgException);
  virtual void setByteArrayEndian(int endian) throw(cMsgException);
  virtual bool needToSwap(void) const throw(cMsgException);
  virtual void setByteArray(char *array) throw(cMsgException);
  virtual char* getByteArray(void) const throw(cMsgException);
  virtual void setByteArrayAndLimits(char *array, int offset, int length) throw(cMsgException);
  virtual void copyByteArray(char* array, int offset, int length) throw(cMsgException);
  virtual int getUserInt(void) const throw(cMsgException);
  virtual void setUserInt(int i) throw(cMsgException);
  virtual struct timespec getUserTime(void) const throw(cMsgException);
  virtual void setUserTime(const struct timespec &userTime) throw(cMsgException);
  virtual int getVersion(void) const throw(cMsgException);
  virtual string getDomain(void) const throw(cMsgException);
  virtual string getCreator(void) const throw(cMsgException);
  virtual string getReceiver(void) const throw(cMsgException);
  virtual string getReceiverHost(void) const throw(cMsgException);
  virtual string getSender(void) const throw(cMsgException);
  virtual string getSenderHost(void) const throw(cMsgException);
  virtual struct timespec getReceiverTime(void) const throw(cMsgException);
  virtual struct timespec getSenderTime(void) const throw(cMsgException);
  virtual bool isGetRequest(void) const throw(cMsgException);
  virtual bool isGetResponse(void) const throw(cMsgException);
  virtual bool isNullGetResponse(void) const throw(cMsgException);
  virtual void makeNullResponse(cMsgMessage &msg) throw(cMsgException);
  virtual void makeNullResponse(cMsgMessage *msg) throw(cMsgException);
  virtual void makeResponse(cMsgMessage &msg) throw(cMsgException);
  virtual void makeResponse(cMsgMessage *msg) throw(cMsgException);
  virtual void setGetResponse(bool b) throw(cMsgException);
  virtual void setNullGetResponse(bool b) throw(cMsgException);
  virtual string toString(void) const throw(cMsgException);
  virtual cMsgMessage *copy(void) const throw(cMsgException);
  virtual cMsgMessage *nullResponse(void) const throw(cMsgException);
  virtual cMsgMessage *response(void) const throw(cMsgException);
  virtual string getSubscriptionDomain() const throw(cMsgException);
  virtual string getSubscriptionSubject() const throw(cMsgException);
  virtual string getSubscriptionType() const throw(cMsgException);
  virtual string getSubscriptionUDL() const throw(cMsgException);
  virtual int    getSubscriptionCueSize(void) const throw(cMsgException);
  virtual bool   getReliableSend(void) const throw(cMsgException);
  virtual void   setReliableSend(bool b) throw(cMsgException);


private:
  void *myMsgPointer;  /**<Pointer to C message structure.*/
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Interface defines callback method.
 */
class cMsgCallback {

public:
  virtual void callback(cMsgMessage *msg, void *userObject) = 0;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Manages subscriptions configurations.
 */
class cMsgSubscriptionConfig {

public:
  cMsgSubscriptionConfig(void);
  virtual ~cMsgSubscriptionConfig(void);

  virtual int getMaxCueSize(void);
  virtual void setMaxCueSize(int size);
  virtual int getSkipSize(void);
  virtual void setSkipSize(int size);
  virtual bool getMaySkip(void);
  virtual void setMaySkip(bool maySkip);
  virtual bool getMustSerialize(void);
  virtual void setMustSerialize(bool mustSerialize);
  virtual int getMaxThreads(void);
  virtual void setMaxThreads(int max);
  virtual int getMessagesPerThread(void);
  virtual void setMessagesPerThread(int mpt);
  virtual size_t getStackSize(void);
  virtual void setStackSize(size_t size);

public:
  cMsgSubscribeConfig *config;   /**<Pointer to subscription config struct.*/
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Wraps most cMsg C calls, provides main functionality.
 */
class cMsg {

public:
  cMsg(const string &UDL, const string &name, const string &descr);
  virtual ~cMsg(void);
  virtual void connect() throw(cMsgException);
  virtual void disconnect(void) throw(cMsgException);
  virtual void send(cMsgMessage &msg) throw(cMsgException);
  virtual void send(cMsgMessage *msg) throw(cMsgException);
  virtual int  syncSend(cMsgMessage &msg, const struct timespec *timeout = NULL) throw(cMsgException);
  virtual int  syncSend(cMsgMessage *msg, const struct timespec *timeout = NULL) throw(cMsgException);
  virtual void *subscribe(const string &subject, const string &type, cMsgCallback *cb, void *userArg, 
                          const cMsgSubscriptionConfig *cfg = NULL) throw(cMsgException);
  virtual void *subscribe(const string &subject, const string &type, cMsgCallback &cb, void *userArg,
                          const cMsgSubscriptionConfig *cfg = NULL) throw(cMsgException);
  virtual void unsubscribe(void *handle) throw(cMsgException);
  virtual cMsgMessage *sendAndGet(cMsgMessage &sendMsg, const struct timespec *timeout = NULL) 
    throw(cMsgException);
  virtual cMsgMessage *sendAndGet(cMsgMessage *sendMsg, const struct timespec *timeout = NULL)
    throw(cMsgException);
  virtual cMsgMessage *subscribeAndGet(const string &subject, const string &type, const struct timespec *timeout = NULL)
    throw(cMsgException);
  virtual void flush(const struct timespec *timeout = NULL) throw(cMsgException);
  virtual void start(void) throw(cMsgException);
  virtual void stop(void) throw(cMsgException);
  virtual string getUDL(void) const;
  virtual string getName(void) const;
  virtual string getDescription(void) const;
  virtual bool isConnected(void) const;
  virtual bool isReceiving(void) const;
  virtual void setShutdownHandler(cMsgShutdownHandler *handler, void* userArg) throw(cMsgException);
  virtual void shutdownClients(const string &client, int flag) throw(cMsgException);
  virtual void shutdownServers(const string &server, int flag) throw(cMsgException);
  virtual cMsgMessage *monitor(const string &monString) throw(cMsgException);


private:
  void  *myDomainId;    /**<C Domain id.*/
  string myUDL;         /**<UDL.*/
  string myName;        /**<Name.*/
  string myDescr;       /**<Description.*/
  bool initialized;     /**<True if initialized.*/
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
// private templates that should not be in doxygen doc
#include <cMsgPrivate.hxx>


} // namespace cMsg

#endif /* _cMsg_hxx */

