//
// Copyright 2022, Jefferson Science Associates, LLC.
// Subject to the terms in the LICENSE file found in the top-level directory.
//
// EPSCI Group
// Thomas Jefferson National Accelerator Facility
// 12000, Jefferson Ave, Newport News, VA 23606
// (757)-269-7100


#include "et_fifo.h"
#include "et_private.h"


/**
 * @file
 * <p>
 * These routines use an ET system as a FIFO. The idea is that an ET system divides
 * its events (or buffers) into groups (or arrays) of fixed size. Each array is then a
 * single FIFO entry. Thus the FIFO's entries are each an array of related buffers.
 * </p>
 *
 * <p>
 * When requesting an entry, this interface does so in arrays of this fixed size.
 * Likewise, when putting them back it does so only in the same arrays.
 * In order to use this interface, the ET system should be run using the <b>et_start_fifo</b>
 * program and not et_start. This sets up the ET to be comprised of properly grouped events.
 * It also creates one station, called "Users" to which the consumers of FIFO entries
 * will attach.
 * </p>
 *
 * <p>
 * This interface is intended to be used by connecting to a local ET system using shared
 * memory - providing a fast, wide FIFO available to multiple local processes. Although it
 * could be used by remote users of the ET system, it's advantages would be lost.
 * </p>
 *
 * <p>
 * These routines are threadsafe with the exception of {@link #et_fifo_getBuf},
 * but that should never be called on the same arg with different threads.
 * These routines are designed to work as follows:
 * <ol>
 *  <li>Call {@link #et_open} and get id</li>
 *  <li>Call {@link #et_fifo_open} and get fifo id as either a producer or consumer of entries</li>
 *      <ul>
 *          <li>Makes sure ET is setup properly</li>
 *          <li>Attaches to GrandCentral station if producing entries</li>
 *          <li>Attaches to Users station if consuming entries</li>
 *      </ul>
 *  <li>Call {@link #et_fifo_newEntry} and get pointer to {@link et_fifo_entry} if producing fifo entry</li>
 *      <ul>
 *          <li>Call {@link #et_fifo_getBufs} to get access to all the events of a single fifo entry</li>
 *          <li>Call {@link #et_fifo_getEntryCount} to get the number of events in array. Need only be done once.</li>
 *          <li>Call {@link #et_fifo_getBufSize} to get available size of each event. Need only be done once.</li>
 *          <li>Call {@link #et_fifo_setId} to set an id for a particular event</li>
 *          <li>Call {@link #et_event_setlength} to set the size of valid data for a particular event</li>
 *      </ul>
 *  <li>Call {@link #et_fifo_getEntry} and get pointer to {@link et_fifo_entry} if consuming fifo entry</li>
 *      <ul>
 *          <li>Call {@link #et_fifo_getBufs} to get access to all the events of a single fifo entry</li>
 *          <li>Call {@link #et_fifo_getEntryCount} to get the number of events in array. Need only be done once.</li>
 *          <li>Call {@link #et_fifo_getBufSize} to get available size of each event. Need only be done once.</li>
 *          <li>Call {@link #et_fifo_getId} to get an id for a particular event</li>
 *          <li>Call {@link #et_event_getlength} to get the size of valid data for a particular event</li>
 *      </ul>
 *  <li>Call {@link #et_fifo_putEntry} when finished with fifo entry</li>
 *  <li>Call {@link #et_fifo_close} when finished with fifo interface</li>
 *  <li>Call {@link #et_close} when finished with ET system</li>
 * </ol>
 * </p>
 *
 * <p>
 * The first integer of an event's control array is reserved to hold an id
 * to distinguish it from the other events. It's initially set to -1.
 * Note that once you call {@link #et_fifo_getBufs} and get pointers to the events,
 * you have full access to the control array of each event by calling
 * {@link #et_event_getcontrol} and {@link #et_event_setcontrol}
 * and are not restricted to using {@link #et_fifo_getId} and {@link #et_fifo_setId}
 * which only access the first integer of the control array.
 * The control array is of size {@link ET_STATION_SELECT_INTS}.
 * </p>
 *
 */

/**
 * @defgroup fifo Fifo
 *
 * These routines use the ET system as a FIFO.
 *
 * @{
 */



/**
 * <p>This routine creates a handle (et_fifo_id) with which to interact with an ET system
 * by treating it as a FIFO in which each element has multiple buffers (events).</p>
 *
 * <b>In order for this to work, the ET system being used must be started by
 * et_start_fifo, NOT et_start.</b>
 *
 * @param id         id of an opened ET system.
 * @param fid        pointer to fifo id which gets filled in if ET system successfully opened
 *                   and defined in a fifo-consistent manner.
 * @param isProducer true if client will be getting empty buffers and filling them with data,
 *                   false if client will be getting buffers already full of data.
 * @param bufIds     array of ids, assign one to each buffer of each fifo entry.
 *                   If more provided than needed, only the first are used.
 * @param bidCount   number of ints in bufIds array.
 *
 * @returns @ref ET_OK             if successful.
 * @returns @ref ET_ERROR          if either id or fid is NULL,
 *                                 or id not initialized,
 *                                 or number of events not multiple of fifo entries,
 *                                 or no Users station exists.
 * @returns @ref ET_ERROR_NOMEM    if memory cannot be allocated.
 * @returns @ref ET_ERROR_CLOSED   if et_close already called.
 * @returns @ref ET_ERROR_DEAD     if ET system is dead.
 * @returns @ref ET_ERROR_READ     for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE    for a remote user's network write error.
 * @returns @ref ET_ERROR_TOOMANY  if the existing number of attachments to GC or Users is already equal
 *                                 to the station or system limit.
 * @returns @ref ET_ERROR_REMOTE   for a memory allocation error of a remote user
 */
static int et_fifo_open(et_sys_id id, et_fifo_id *fid, int isProducer, const int *bufIds, int bidCount) {

    et_stat_id statId;
    et_fifo_ctx *ctx;

    ctx = (et_fifo_ctx *) malloc(sizeof(et_fifo_ctx));
    if (ctx == NULL) {
        et_logmsg("ERROR", "et_fifo_open, cannot allocate memory\n");
        return ET_ERROR_NOMEM;
    }

    ctx->producer  = isProducer;
    ctx->capacity  = 0;
    ctx->entries   = 0;
    ctx->openId    = id;
    ctx->attId     = -1;
    ctx->evCount   = 0;
    ctx->evSize    = 0;
    ctx->idCount   = 0;
    ctx->bufIds    = NULL;

    // Look into ET system
    int err = et_system_geteventsize(id, &ctx->evSize);
    if (err != ET_OK) {
        free(ctx);
        return err;
    }

    // If the previous routine worked, this will too
    et_system_getnumevents(id, &ctx->evCount);

    // Use the et_start_fifo program to start up the ET system.
    // That program divides events/buffers into groups which tell
    // us the number of fifo entries in ET.
    et_system_getgroupcount(id, &ctx->entries);

    // Check to make sure ET system is setup properly
    if (ctx->evCount % ctx->entries != 0) {
        // When starting the ET, use et_start_fifo to set the number of fifo entries
        // and the number of buffers/entry.
        // This ensures the total number of events is divided equally
        // among all the fifo entries.
        free(ctx);
        et_logmsg("ERROR", "Number of events in ET must be multiple of number of entries");
        return ET_ERROR;
    }

    ctx->capacity = ctx->evCount / ctx->entries;

    // Now the station attachments.
    // In an ET system started by et_start_fifo, there are 2 stations:
    // GrandCentral and Users.
    if (isProducer) {
        // Attach to GC
        if ((err = et_station_attach(id, ET_GRANDCENTRAL, &ctx->attId)) < 0) {
            et_logmsg("ERROR", "Error in attaching to GC\n");
            free(ctx);
            return err;
        }
    }
    else {
        // Attach to "Users" station
        if ((err = et_station_name_to_id(id, &statId, "Users")) < 0) {
            et_logmsg("ERROR", "Cannot find \"Users\" station\n");
            free(ctx);
            return err;
        }
        if ((err = et_station_attach(id, statId, &ctx->attId)) < 0) {
            et_logmsg("ERROR", "Error in attaching to \"Users\"\n");
            free(ctx);
            return err;
        }
    }

    // Save ids if any provided
    if (isProducer && (bufIds != NULL) && (bidCount > 0)) {
        bidCount = bidCount > ctx->capacity ? ctx->capacity : bidCount;
        ctx->idCount = bidCount;
        ctx->bufIds  = (int *) malloc(bidCount * sizeof(int));
        memcpy(ctx->bufIds, bufIds, bidCount * sizeof(int));
    }

    et_logmsg("INFO", "et_fifo_open, ET events of size %lu, count %d, entry width %d, fifo entries %d\n",
              ctx->evSize, ctx->evCount, ctx->capacity, ctx->entries);


    *fid = (et_fifo_id) ctx;
    return ET_OK;
}


/**
 * <p>
 * This routine creates a handle (et_fifo_id) with which to interact with an ET system
 * by treating it as a FIFO in which each element has multiple buffers (events).
 * The caller acts only as a data producer, with calls to {@link #et_fifo_newEntry}
 * allowed, but calls to {@link #et_fifo_getEntry} forbidden.
 * </p>
 *
 * <b>
 * In order for this to work, the ET system being used must be started by
 * et_start_fifo, NOT et_start.
 * </b>
 *
 * @param id         id of an opened ET system.
 * @param fid        pointer to fifo id which gets filled in if ET system successfully opened
 *                   and defined in a fifo-consistent manner.
 * @param bufIds     array of ids, assign one to each buffer of each fifo entry.
 *                   If more provided than needed, only the first are used.
 * @param idCount    number of ints in bufIds array.
 *
 * @returns @ref ET_OK             if successful.
 * @returns @ref ET_ERROR          if either id or fid is NULL,
 *                                 or id not initialized,
 *                                 or number of events not multiple of fifo entries.
 * @returns @ref ET_ERROR_NOMEM    if memory cannot be allocated.
 * @returns @ref ET_ERROR_CLOSED   if et_close already called.
 * @returns @ref ET_ERROR_DEAD     if ET system is dead.
 * @returns @ref ET_ERROR_READ     for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE    for a remote user's network write error.
 * @returns @ref ET_ERROR_TOOMANY  if the existing number of attachments to GC or Users is already equal
 *                                 to the station or system limit.
 * @returns @ref ET_ERROR_REMOTE   for a memory allocation error of a remote user
 */
int et_fifo_openProducer(et_sys_id id, et_fifo_id *fid, const int *bufIds, int idCount) {
    return et_fifo_open(id, fid, 1, bufIds, idCount);
}


/**
 * <p>
 * This routine creates a handle (et_fifo_id) with which to interact with an ET system
 * by treating it as a FIFO in which each element has multiple buffers (events).
 * The caller acts only as a data consumer , with calls to {@link #et_fifo_getEntry}
 * allowed, but calls to {@link #et_fifo_newEntry} forbidden.
 * </p>
 *
 * <b>
 * In order for this to work, the ET system being used must be started by
 * et_start_fifo, NOT et_start.
 * </b>
 *
 * @param id         id of an opened ET system.
 * @param fid        pointer to fifo id which gets filled in if ET system successfully opened
 *                   and defined in a fifo-consistent manner.
 * @param bufIds     array of ids, assign one to each buffer of each fifo entry.
 *                   If more provided than needed, only the first are used.
 * @param bidCount   number of ints in bufIds array.
 *
 * @returns @ref ET_OK             if successful.
 * @returns @ref ET_ERROR          if either id or fid is NULL,
 *                                 or id not initialized,
 *                                 or number of events not multiple of fifo entries.
 * @returns @ref ET_ERROR_NOMEM    if memory cannot be allocated.
 * @returns @ref ET_ERROR_CLOSED   if et_close already called.
 * @returns @ref ET_ERROR_DEAD     if ET system is dead.
 * @returns @ref ET_ERROR_READ     for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE    for a remote user's network write error.
 * @returns @ref ET_ERROR_TOOMANY  if the existing number of attachments to GC or Users is already equal
 *                                 to the station or system limit.
 * @returns @ref ET_ERROR_REMOTE   for a memory allocation error of a remote user
 */
int et_fifo_openConsumer(et_sys_id id, et_fifo_id *fid) {
    return et_fifo_open(id, fid, 0, NULL, 0);
}


/**
 * Routine to close the fifo handle opened with {@link #et_fifo_open}.
 * <b>Closing the ET system must be done separately.</b>
 *
 * @param fid fifo id.
 */
void et_fifo_close(et_fifo_id fid) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)fid;
    if (ctx == NULL) return;

    // If attached to a station, detach
    if (ctx->attId > -1) {
        et_station_detach(ctx->openId, ctx->attId);
    }

    // Free mem
    if (ctx->bufIds != NULL) free(ctx->bufIds);
    free(ctx);
}


/**
 * Routine to allocate a structure holding a fifo entry (array of ET events)
 * associated with the given fifo id. This memory must be freed
 * with {@link et_fifo_freeEntry}.
 *
 * @param fid fifo id.
 * @param pointer to allocated fifo entry. NULL if out of mem.
 */
et_fifo_entry* et_fifo_entryCreate(et_fifo_id fid) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)fid;

    et_fifo_entry* pentry = (et_fifo_entry*) malloc(sizeof(et_fifo_entry));
    if (pentry == NULL) return NULL;
    pentry->fid = fid;
    pentry->bufs = (et_event **) malloc(ctx->capacity * sizeof(et_event *));
    if (pentry->bufs == NULL) {
        free(pentry);
        return NULL;
    }
    return pentry;
}


/**
 * Routine to free a fifo entry created with {@link #et_fifo_entryCreate}.
 * @param entry pointer to fifo entry.
 */
void et_fifo_freeEntry(et_fifo_entry *entry) {
    if (entry == NULL) return;
    free(entry->bufs);
    free(entry);
}


/**
 * This routine is called when a user wants an array of related, empty buffers from the ET system
 * into which data can be placed. This routine will block until the buffers become available.
 * Access the buffers by calling {@link #et_fifo_getBufArray}.
 * <b>Each call to this routine MUST be accompanied by a following call to {@link #et_fifo_put}!</b>
 *
 * @see For more insight, see the doxygen doc on {@link #et_events_new}.
 *
 * @param fid    fifo id.
 * @param entry  pointer to fifo entry to be filled with empty fifo entry.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or not a fifo producer, or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_NOMEM   if cannot allocate memory.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 */
int et_fifo_newEntry(et_fifo_id fid, et_fifo_entry *entry) {

    et_fifo_ctx *ctx = (et_fifo_ctx *) fid;
    int i, nread = 0;

    if (ctx == NULL || entry == NULL) return ET_ERROR;
    if (!ctx->producer) {
        et_logmsg("ERROR", "Only a fifo producer can call this routine\n");
        return ET_ERROR;
    }

    // When getting new events we forget about group # - not needed anymore
    int err = et_events_new(ctx->openId, ctx->attId, entry->bufs,
                            ET_SLEEP, NULL, ctx->evSize, ctx->capacity, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->capacity) {
        // System is designed to always get ctx->count buffers
        // with each read if there are bufs available
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->capacity, nread);
        return ET_ERROR;
    }

    // Fill the first event control int with provided id.
    // Otherwise fill with -1, which allows a valid value of 0 for id.
    for (i = 0; i < ctx->idCount; i++) {
        entry->bufs[i]->control[0] = ctx->bufIds[i];
    }
    for (i = ctx->idCount; i < ctx->capacity; i++) {
        entry->bufs[i]->control[0] = -1;
    }

    return ET_OK;
}


/**
 * This routine is called when a user wants an array of related, empty buffers from the ET system
 * into which data can be placed. This routine will block until it times out.
 * Access the buffers by calling {@link #et_fifo_getBufArray}.
 * <b>Each call to this routine MUST be accompanied by a following call to {@link #et_fifo_put}!</b>
 *
 * @see For more insight, see the doxygen doc on {@link #et_events_new}.
 *
 * @param fid        fifo id.
 * @param entry      pointer to fifo entry to be filled with empty fifo entry.
 * @param deltatime  time to wait before returning.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or not a fifo producer, or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_NOMEM   if cannot allocate memory.
 * @returns @ref ET_ERROR_NOMEM   if cannot allocate memory.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 * @returns @ref ET_ERROR_TIMEOUT if timeout.
 */
int et_fifo_newEntryTO(et_fifo_id fid, et_fifo_entry *entry, struct timespec *deltatime) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)fid;
    int i, nread = 0;

    if (ctx == NULL || entry == NULL) return ET_ERROR;
    if (!ctx->producer) {
        et_logmsg("ERROR", "Only a fifo producer can call this routine\n");
        return ET_ERROR;
    }

    int err = et_events_new(ctx->openId, ctx->attId, entry->bufs,
                            ET_TIMED, deltatime, ctx->evSize, ctx->capacity, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->capacity) {
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->capacity, nread);
        return ET_ERROR;
    }

    for (i = 0; i < ctx->idCount; i++) {
        entry->bufs[i]->control[0] = ctx->bufIds[i];
    }
    for (i = ctx->idCount; i < ctx->capacity; i++) {
        entry->bufs[i]->control[0] = -1;
    }

    return ET_OK;
}


/**
 * This routine is called when a user wants an array of related, data-filled buffers from the
 * ET system. This routine will block until the buffers become available.
 * Access the buffers by calling {@link #et_fifo_getBufArray}.
 * <b>Each call to this routine MUST be accompanied by a following call to {@link #et_fifo_put}!</b>
 *
 * @see For more insight, see the doxygen doc on {@link #et_events_get}.
 *
 * @param fid    ET fifo handle.
 * @param entry  pointer to fifo entry to be filled with data from next fifo element.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or not a fifo consumer, or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_NOMEM   if cannot allocate memory.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 */
int et_fifo_getEntry(et_fifo_id fid, et_fifo_entry *entry) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)fid;
    int nread = 0;

    if (ctx == NULL || entry == NULL) return ET_ERROR;
    if (ctx->producer) {
        et_logmsg("ERROR", "Only a fifo consumer can call this routine\n");
        return ET_ERROR;
    }

    int err = et_events_get(ctx->openId, ctx->attId, entry->bufs,
                            ET_SLEEP, NULL, ctx->capacity, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->capacity) {
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->capacity, nread);
        return ET_ERROR;
    }

    return ET_OK;
}


/**
 * This routine is called when a user wants an array of related, data-filled buffers from the
 * ET system. This routine will block until it times out.
 * Access the buffers by calling {@link #et_fifo_getBufArray}.
 * <b>Each call to this routine MUST be accompanied by a following call to {@link #et_fifo_put}!</b>
 *
 * @see For more insight, see the doxygen doc on {@link #et_events_get}.
 *
 * @param fid        ET fifo handle.
 * @param entry      pointer to fifo entry to be filled with data from next fifo element.
 * @param deltatime  time to wait before returning.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or not a fifo consumer, or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_NOMEM   if cannot allocate memory.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 * @returns @ref ET_ERROR_TIMEOUT if timeout.
 */
int et_fifo_getEntryTO(et_fifo_id fid, et_fifo_entry *entry, struct timespec *deltatime) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)fid;
    int nread = 0;

    if (ctx == NULL || entry == NULL) return ET_ERROR;
    if (ctx->producer) {
        et_logmsg("ERROR", "Only a fifo consumer can call this routine\n");
        return ET_ERROR;
    }

    int err = et_events_get(ctx->openId, ctx->attId, entry->bufs,
                            ET_TIMED, deltatime, ctx->capacity, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->capacity) {
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->capacity, nread);
        return ET_ERROR;
    }

    return ET_OK;
}


/**
 * This routine is called when a user wants to place an array of related, data-filled buffers
 * (single FIFO entry) back into the ET system. This must be called after calling either
 * {@link #et_fifo_getBufs}, {@link #et_fifo_getBufsTO},
 * {@link #et_fifo_newBufs}, or {@link #et_fifo_newBufsTO}.
 * This routine will never block.
 *
 * @see For more insight, see the doxygen doc on {@link #et_events_put}.
 *
 * @param entry fifo entry to release back to ET.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument or event data length > ET event size.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called
 * @returns @ref ET_ERROR_READ    for a remote user's network read error
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error
 * @returns @ref ET_ERROR_DEAD    if ET system is dead
 * @returns @ref ET_ERROR_REMOTE  bad pointer to data or memory allocation error of a remote user
 */
int et_fifo_putEntry(et_fifo_entry *entry) {

    if (entry == NULL) return ET_ERROR;
    et_fifo_ctx *ctx = (et_fifo_ctx *)(entry->fid);
    if (ctx == NULL) return ET_ERROR;

    // Put back into ET
    int err = et_events_put(ctx->openId, ctx->attId, entry->bufs, ctx->capacity);
    if (err != ET_OK) {
        return err;
    }

    return ET_OK;
}


/**
 * This routine gives access to the ET events or buffers obtained by calling either
 * {@link #et_fifo_getEntry}, {@link #et_fifo_getEntryTO},
 * {@link #et_fifo_newEntry}, or {@link #et_fifo_newEntryTO}.
 * The size of the returned array can be found by calling {@link #et_fifo_getEntryCount}.
 *
 * @param id  ET fifo handle.
 * @return array of pointers to et_event structures or NULL if bad arg.
 */
et_event** et_fifo_getBufs(et_fifo_entry *entry) {
    if (entry == NULL) return NULL;
    return entry->bufs;
};


/**
 * This routine gets the size of each buffer in bytes.
 * @param id  ET fifo handle.
 * @return size of ech buffer in bytes, or ET_ERROR if bad arg.
 */
size_t et_fifo_getBufSize(et_fifo_id id) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx == NULL) return ET_ERROR;
    return ctx->evSize;
};


/**
 * This routine gets the max number of buffers in each FIFO entry.
 * @param id  ET fifo handle.
 * @return max number of buffers in each FIFO entry, or ET_ERROR if bad arg.
 */
int et_fifo_getEntryCapacity(et_fifo_id id) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx == NULL) return ET_ERROR;
    return ctx->capacity;
};


/**
 * This routine gets the number of buffers assigned an id in each FIFO entry creation.
 * The same as the idCount argument of {@link #et_fifo_openProducer}.
 * @param id  ET fifo handle.
 * @return number of buffers assigned an id in each FIFO entry, or ET_ERROR if bad arg.
 */
int et_fifo_getIdCount(et_fifo_id id) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx == NULL) return ET_ERROR;
    return ctx->idCount;
};


/**
 * This routine gets the number of buffers assigned an id in each FIFO entry.
 * The same as the bufIds argument of {@link #et_fifo_openProducer}.
 * Assumes the user is passing in an array of length found by calling
 * {@link #et_fifo_getIdCount}.
 * @param id  ET fifo handle.
 * @return number of buffers assigned an id in each FIFO entry, or ET_ERROR if bad arg(s).
 */
int et_fifo_getBufIds(et_fifo_id id, int *bufIds) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx == NULL || bufIds == NULL || ctx->bufIds == NULL) return ET_ERROR;
    memcpy(bufIds, ctx->bufIds, ctx->idCount*sizeof(int));
    return 0;
};


/**
 * This routine sets an id value associated with this ET event/buffer.
 * Stored in event's first control word.
 * @param ev ET event.
 * @param id id of this event.
 */
void et_fifo_setId(et_event *ev, int id) {
    if (ev == NULL) return;
    ev->control[0] = id;
}


/**
 * This routine gets an id value associated with this ET event/buffer.
 * @param ev ET event.
 * @return id of this event, or ET_ERROR if ev = NULL.
 */
int et_fifo_getId(et_event *ev) {
    if (ev == NULL) return ET_ERROR;
    return ev->control[0];
}


/**
 * This routine sets whether this ET event has data in it.
 * Stored in event's second control word.
 * @param ev ET event.
 * @param id id of this event.
 */
void et_fifo_setHasData(et_event *ev, int hasData) {
    if (ev == NULL) return;
    ev->control[1] = hasData;
}


/**
 * This routine gets whether this ET event has data in it or not.
 * @param ev ET event.
 * @return id of this event, or ET_ERROR if ev = NULL.
 */
int et_fifo_hasData(et_event *ev) {
    if (ev == NULL) return ET_ERROR;
    return ev->control[1];
}


/**
 * Find the event/buffer in the fifo entry corresponding to the given id.
 * If none, get the first unused buffer, assign it that id,
 * and return it. The id value for an event is stored in its first
 * control integer. NOT threadsafe.
 *
 * @param id    id.
 * @param entry fifo entry.
 * @return buffer labelled with id or first one that's unused.
 *         NULL if no bufs are available.
 */
et_event* et_fifo_getBuf(int id, et_fifo_entry *entry) {
    if (entry == NULL) return NULL;
    et_fifo_ctx *ctx = (et_fifo_ctx *)(entry->fid);

    int i, c0;
    for (i=0; i < ctx->capacity; i++) {
        c0 = entry->bufs[i]->control[0];

        if (c0 == id) {
            return entry->bufs[i];
        }

        // If we've reached the end of reserved buffers, and
        // still haven't found one associated with id, reserved & use this one.
        if (c0 == -1) {
            entry->bufs[i]->control[0] = id;
            return entry->bufs[i];
        }
    }

    return NULL;
}


/** @} */
