/*----------------------------------------------------------------------------*
 *
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    E.Wolin, 14-Jul-2004, Jefferson Lab                                     *
 *                                                                            *
 *    Authors: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *             Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 * Description:                                                               *
 *                                                                            *
 *  Defines cMsg API and return codes                                         *
 *                                                                            *
 *                                                                            *
 *----------------------------------------------------------------------------*/
 
#ifndef _cMsgConstants_h
#define _cMsgConstants_h


/* endian values */
/** Is big endian. */
#define CMSG_ENDIAN_BIG      0
/** Is little endian. */
#define CMSG_ENDIAN_LITTLE   1
/** Is same endian as local host. */
#define CMSG_ENDIAN_LOCAL    2
/** Is opposite endian as local host. */
#define CMSG_ENDIAN_NOTLOCAL 3
/** Switch recorded value of data's endian. */
#define CMSG_ENDIAN_SWITCH   4


/* debug levels */
/** No debugging output. */
#define CMSG_DEBUG_NONE    0
/** Output only severe (process-ending) errors for debugging. */
#define CMSG_DEBUG_SEVERE  1
/** Output only errors for debugging. */
#define CMSG_DEBUG_ERROR   2
/** Output warnings and errors for debugging. */
#define CMSG_DEBUG_WARN    3
/** Output everything for debugging. */
#define CMSG_DEBUG_INFO    4


/* shutdown flags */
/** When shutting down clients, include the calling client (me). */
#define CMSG_SHUTDOWN_INCLUDE_ME 1



/** Return codes. */
enum {
  CMSG_OK              = 0, /**< No error. */
  CMSG_ERROR,               /**< Generic error. */
  CMSG_TIMEOUT,             /**< Timeout. */
  CMSG_NOT_IMPLEMENTED,     /**< Feature not implemented. */
  CMSG_BAD_ARGUMENT,        /**< Function argument(s) have illegal value. */
  CMSG_BAD_FORMAT,          /**< Function argument(s) in wrong format. */
  CMSG_BAD_DOMAIN_TYPE,     /**< Domain type not supported. */
  CMSG_ALREADY_EXISTS,      /**< Unique item already exists. */
  CMSG_NOT_INITIALIZED,     /**< Connection not established - call cMsgConnect. */
  CMSG_ALREADY_INIT,        /**< Connection already established. */
  CMSG_LOST_CONNECTION,     /**< No network connection to cMsg server. */
  CMSG_NETWORK_ERROR,       /**< Communication error talking to server. */
  CMSG_SOCKET_ERROR,        /**< Error setting TCP socket option(s). */
  CMSG_PEND_ERROR,          /**< Error when waiting for messages to arrive. */
  CMSG_ILLEGAL_MSGTYPE,     /**< Received illegal message type. */
  CMSG_OUT_OF_MEMORY,       /**< No more memory available. */
  CMSG_OUT_OF_RANGE,        /**< Argument out of acceptable range. */
  CMSG_LIMIT_EXCEEDED,      /**< Trying to create too many of an item. */
  CMSG_BAD_DOMAIN_ID,       /**< Id does not match any existing domain. */
  CMSG_BAD_MESSAGE,         /**< Message is not in the correct form. */
  CMSG_WRONG_DOMAIN_TYPE,   /**< UDL does not match the server type. */
  CMSG_NO_CLASS_FOUND,      /**< Java class cannot be found to instantiate subdomain handler. */
  CMSG_DIFFERENT_VERSION,   /**< Client and server are different cMsg versions. */
  CMSG_WRONG_PASSWORD,      /**< Wrong password given. */
  CMSG_SERVER_DIED,         /**< Server died. */
  CMSG_ABORT                /**< Abort effort. */
};



#endif /* _cMsgConstants_h */
