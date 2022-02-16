/*----------------------------------------------------------------------------*
 *  Copyright (c) 1998        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12H        *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 * Description:
 *      Header file for ET system
 *
 *----------------------------------------------------------------------------*/

#ifndef ET_FIFO_H_
#define ET_FIFO_H_

#include <stdio.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <time.h>
#include <sys/time.h>

#include "et.h"


#ifdef	__cplusplus
extern "C" {
#endif


/** Structure to hold the current state of a station. */
typedef struct et_fifo_context_t {
    size_t     evSize;    /**< Size in bytes of each ET buffer/event. */
    int     evCount;   /**< Number of buffers/events in ET system. */
    int     entries;   /**< Number of fifo entries in ET system. */
    int     producer;   /**< True if producing fifo entries, false if consuming fifo entries. */
    int        count;   /**< Width of this fifo element (number of buffers contained max). */
    et_sys_id  openId;  /**< Id returned from et_open. */
    et_att_id  attId;    /**< Attachment to GrandCentral Station for data producers
                          * User for data consumers. */
    et_event **bufs;
} et_fifo_ctx;


typedef void *et_fifo_id;        /**< ET fifo id. */


extern int  et_fifo_open(et_sys_id id, et_fifo_id *fifoId, int isProducer);
extern void et_fifo_close(et_fifo_id fifoId);

extern int  et_fifo_newBufs(et_fifo_id id);
extern int  et_fifo_newBufsTO(et_fifo_id id, struct timespec *deltatime);

extern int  et_fifo_getBufs(et_fifo_id id);
extern int  et_fifo_getBufsTO(et_fifo_id id, struct timespec *deltatime);

extern int  et_fifo_putBufs(et_fifo_id id);

extern int  et_fifo_getBufSize(et_fifo_id id);
extern int  et_fifo_getEntryCount(et_fifo_id id);
extern et_event** et_fifo_getBufArray(et_fifo_id id);



#ifdef	__cplusplus
}
#endif

#endif
