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


typedef void *et_fifo_id;        /**< ET fifo id. */


/** Structure to hold the context of a fifo connection to ET. */
typedef struct et_fifo_context_t {
    size_t     evSize;     /**< Size in bytes of each ET buffer/event. */
    int       evCount;     /**< Number of buffers/events in ET system. */
    int       entries;     /**< Number of fifo entries in ET system. */
    int      producer;     /**< True if producing fifo entries, false if consuming fifo entries. */
    int      capacity;     /**< Total number of buffer contained in this fifo element. */
    et_sys_id  openId;     /**< Id returned from et_open. */
    et_att_id   attId;     /**< Attachment to GrandCentral Station for data producers
                            * User for data consumers. */
    int idCount;           /**< Number of elements in bufIds array (if is producer). */
    int *bufIds;           /**< Array to hold ids - one for each buffer of a fifo entry (if is producer). */
} et_fifo_ctx;


/** Structure to hold the a fifo entry obtained from ET. */
typedef struct et_fifo_entry_t {
    et_event **bufs;   /**< array of ET events. */
    et_fifo_id  fid;   /**< fifo id used to get these events from ET. */
    int        size;   /**< Number of buffers containing data. */
} et_fifo_entry;


extern int  et_fifo_openProducer(et_sys_id fid, et_fifo_id *fifoId, const int *bufIds, int bidCount);
extern int  et_fifo_openConsumer(et_sys_id fid, et_fifo_id *fifoId);
extern void et_fifo_close(et_fifo_id fid);

extern int  et_fifo_newEntry(et_fifo_id fid, et_fifo_entry **entry);
extern int  et_fifo_newEntryTO(et_fifo_id fid, et_fifo_entry **entry, struct timespec *deltatime);

extern int  et_fifo_getEntry(et_fifo_id fid, et_fifo_entry **entry);
extern int  et_fifo_getEntryTO(et_fifo_id fid, et_fifo_entry **entry, struct timespec *deltatime);

extern int  et_fifo_putEntry(et_fifo_entry *entry);

extern size_t et_fifo_getBufSize(et_fifo_id fid);
extern int    et_fifo_getEntryCapacity(et_fifo_id fid);
extern int    et_fifo_getEntrySize(et_fifo_entry *entry);
extern int    et_fifo_incrementEntrySize(et_fifo_entry *entry);
extern int    et_fifo_decrementEntrySize(et_fifo_entry *entry);

extern et_event** et_fifo_getBufs(et_fifo_entry *entry);
extern et_event*  et_fifo_getBuf(int id, et_fifo_entry *entry);

extern void et_fifo_setId(et_event *ev, int id);
extern  int et_fifo_getId(et_event *ev);

extern int et_fifo_getIdCount(et_fifo_id id);
extern int et_fifo_getBufIds(et_fifo_id id, int **bufIds);


#ifdef	__cplusplus
}
#endif

#endif
