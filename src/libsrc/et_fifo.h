//
// Copyright 2022, Jefferson Science Associates, LLC.
// Subject to the terms in the LICENSE file found in the top-level directory.
//
// EPSCI Group
// Thomas Jefferson National Accelerator Facility
// 12000, Jefferson Ave, Newport News, VA 23606
// (757)-269-7100


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
    int       entries;     /**< Total number of fifo entries in ET system. */
    int   userEntries;     /**< For consumers, max number of fifo entries in Users station (&lt;= entries). */
    int      producer;     /**< True if producing fifo entries, false if consuming fifo entries. */
    int      capacity;     /**< Total number of buffer contained in this fifo element. */
    et_sys_id  openId;     /**< Id returned from et_open. */
    et_stat_id userStatId; /**< User station id for both producers & consumers. */
    et_att_id   attId;     /**< Attachment to GrandCentral Station for data producers
                            * User for data consumers. */
    int idCount;           /**< Number of elements in bufIds array (if is producer). */
    int *bufIds;           /**< Array to hold ids - one for each buffer of a fifo entry (if is producer). */
} et_fifo_ctx;


/** Structure to hold the a fifo entry obtained from ET. */
typedef struct et_fifo_entry_t {
    et_event **bufs;   /**< array of ET events. */
    et_fifo_id  fid;   /**< fifo id used to get these events from ET. */
} et_fifo_entry;


extern int  et_fifo_openProducer(et_sys_id fid, et_fifo_id *fifoId, const int *bufIds, int idCount);
extern int  et_fifo_openConsumer(et_sys_id fid, et_fifo_id *fifoId);
extern void et_fifo_close(et_fifo_id fid);

extern int  et_fifo_newEntry(et_fifo_id fid, et_fifo_entry *entry);
extern int  et_fifo_newEntryTO(et_fifo_id fid, et_fifo_entry *entry, struct timespec *deltatime);

extern int  et_fifo_getEntry(et_fifo_id fid, et_fifo_entry *entry);
extern int  et_fifo_getEntryTO(et_fifo_id fid, et_fifo_entry *entry, struct timespec *deltatime);

extern int  et_fifo_putEntry(et_fifo_entry *entry);
extern int  et_fifo_allHaveData(et_fifo_id id, et_fifo_entry *entry,
                                int *incompleteBufs, size_t *incompleteBytes);

extern int et_fifo_getEntryCount(et_fifo_id id);
extern int et_fifo_getFillLevel(et_fifo_id id);

extern size_t et_fifo_getBufSize(et_fifo_id fid);
extern int    et_fifo_getEntryCapacity(et_fifo_id fid);

extern et_event** et_fifo_getBufs(et_fifo_entry *entry);
extern et_event*  et_fifo_getBuf(int id, et_fifo_entry *entry);

extern void et_fifo_setId(et_event *ev, int id);
extern  int et_fifo_getId(et_event *ev);

extern void et_fifo_setHasData(et_event *ev, int hasData);
extern  int et_fifo_hasData(et_event *ev);

extern int et_fifo_getIdCount(et_fifo_id id);
extern int et_fifo_getBufIds(et_fifo_id id, int *bufIds);

extern et_fifo_entry* et_fifo_entryCreate(et_fifo_id fid);
extern void et_fifo_freeEntry(et_fifo_entry *entry);


#ifdef	__cplusplus
}
#endif

#endif
