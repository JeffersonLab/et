/*----------------------------------------------------------------------------*
*  Copyright (c) 2005        Southeastern Universities Research Association, *
*                            Thomas Jefferson National Accelerator Facility  *
*                                                                            *
*    This software was developed under a United States Government license    *
*    described in the NOTICE file included as part of this distribution.     *
*                                                                            *
*    E.Wolin, 14-Jun-2007, Jefferson Lab                                     *
*                                                                            *
*    Authors: Elliott Wolin                                                  *
*             wolin@jlab.org                    Jefferson Lab, MS-12H5       *
*             Phone: (757) 269-7365             12000 Jefferson Ave.         *
*             Fax:   (757) 269-5519             Newport News, VA 23606       *
*
*----------------------------------------------------------------------------*/


#ifndef _cMsgPrivate_hxx
#define _cMsgPrivate_hxx


//-----------------------------------------------------------------------------


/**
 * Allows a cMsg C callback to dispatch to an object member function, used internally.
 */ 
template <class T> class cMsgDispatcher : public cMsgCallback {
private:
  T *t;   /**<Object containing member function.*/
  void (T::*mfp)(cMsgMessage *msg, void* userArg); /**<Member function.*/

public:
  /** Constructor.
   *
   * @param t Object
   * @param mfp Member function
   */
  cMsgDispatcher(T *t, void (T::*mfp)(cMsgMessage *msg, void* userArg)) throw(cMsgException*) : t(t), mfp(mfp) {}
  /** Dispatches to member function. @param msg Message. @param userArg User arg. */
  void callback(cMsgMessage *msg, void* userArg) throw(cMsgException) { (t->*mfp)(msg,userArg); }
};


//-----------------------------------------------------------------------------



#endif /* _cMsgPrivate_hxx */
