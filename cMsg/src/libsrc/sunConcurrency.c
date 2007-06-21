/*----------------------------------------------------------------------------*
 *  Copyright (c) 2005        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 * Description:
 *      This file contains wrappers around SUN's thr_setconcurrency and
 *      thr_getconcurrency functions. This is done because its requisite
 *      header file drags in other functions whose names collide with the
 *      code in rwlock.h, rwlock.c.
 *
 *----------------------------------------------------------------------------*/

/**
 * @file
 * This file contains wrappers around Solaris' thr_setconcurrency and
 * thr_getconcurrency functions. This is done because its requisite
 * header file drags in other functions whose names collide with the
 * code in rwlock.h, rwlock.c if not so isolated.
 */  


#ifdef sun
#ifndef VXWORKS

#include <thread.h>

#endif
#endif
  /**
   * This function encapsulates the Solaris routine to set the desired concurrency
   * level (number of simultaneous threads) of the operating system.
   * This is done because its requisite header file (thread.h) drags in other
   * functions whose names collide with the code in rwlock.h, rwlock.c.
   * This function does nothing on Linux and vxWorks.
   *
   * @param newLevel desired concurrency
   * @returns 0 if successful, EAGAIN if system resources would be exceeded,
   *          EINVAL if newLevel is negative
   */
  int sun_setconcurrency(int newLevel) {
#ifdef sun
#ifndef VXWORKS
    return thr_setconcurrency(newLevel);
#else
    return 0;
#endif
#else
    return 0;
#endif
  }
  
  /**
   * This function encapsulates the Solaris rountine to get the desired concurrency
   * level (number of simultaneous threads) of the operating system.
   * This is done because its requisite header file (thread.h) drags in other
   * functions whose names collide with the code in rwlock.h, rwlock.c.
   * This function does nothing on Linux and vxWorks.
   *
   * @returns current desired concurrency
   */
  int sun_getconcurrency() {
#ifdef sun
#ifndef VXWORKS
    return thr_getconcurrency();
#else
    return 0;
#endif
#else
    return 0;
#endif
  }
