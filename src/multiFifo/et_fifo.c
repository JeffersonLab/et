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
 * In order to use this interface, the ET system should be run using the et_start_fifo
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
 * @param fifoId     pointer to fifo id which gets filled in if ET system successfully opened
 *                   and defined in a fifo-consistent manner.
 * @param isProducer true if client will be getting empty buffers and filling them with data,
 *                   false if client will be getting buffers already full of data.
 *
 * @returns @ref ET_OK             if successful.
 * @returns @ref ET_ERROR          if either id or fifoId is NULL,
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
int et_fifo_open(et_sys_id id, et_fifo_id *fifoId, int isProducer) {

    et_stat_id statId;
    et_fifo_ctx *ctx;

    ctx = (et_fifo_ctx *) malloc(sizeof(et_fifo_ctx));
    if (ctx == NULL) {
        et_logmsg("ERROR", "et_fifo_open, cannot allocate memory\n");
        return ET_ERROR_NOMEM;
    }

    ctx->producer  = isProducer;
    ctx->count     = 0;
    ctx->entries   = 0;
    ctx->openId    = id;
    ctx->attId     = -1;
    ctx->evCount   = 0;
    ctx->evSize    = 0;

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

    ctx->count = ctx->evCount / ctx->entries;

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

    ctx->bufs = (et_event **) malloc(ctx->count * sizeof(et_event *));
    if (ctx->bufs == NULL) {
        et_logmsg("ERROR", "et_fifo_open, cannot allocate memory\n");
        return ET_ERROR_NOMEM;
    }

    et_logmsg("INFO", "et_fifo_open, ET events of size %lu, count %d, entry width %d, fifo entries %d\n",
              ctx->evSize, ctx->evCount, ctx->count, ctx->entries);


    *fifoId = (et_fifo_id) ctx;
    return ET_OK;
}


/**
 * Routine to close the fifo handle opened with {@link #et_fifo_open}.
 * <b>Closing the ET system must be done separately.</b>
 *
 * @param fifoId et fifo handle.
 */
void et_fifo_close(et_fifo_id fifoId) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)fifoId;
    if (ctx == NULL) return;

    // If attached to a station, detach
    if (ctx->attId > -1) {
        et_station_detach(ctx->openId, ctx->attId);
    }

    // Free mem
    free(ctx->bufs);
    free(ctx);
}


/**
 * This routine is called when a user wants an array of related, empty buffers from the ET system
 * into which data can be placed. This routine will block until the buffers become available.
 * Access the buffers by calling {@link #et_fifo_getBufArray}.
 * <b>Each call to this routine MUST be accompanied by a following call to {@link #et_fifo_put}!</b>
 *
 * @see For more insight, see the doxygen doc on {@link #et_events_new}.
 *
 * @param id    ET fifo handle.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 */
int et_fifo_newBufs(et_fifo_id id) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    int nread = 0;

    if (ctx == NULL) return ET_ERROR;

    // When getting new events we forget about group # - not needed anymore
    int err = et_events_new(ctx->openId, ctx->attId, ctx->bufs,
                            ET_SLEEP, NULL, ctx->evSize, ctx->count, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->count) {
        // System is designed to always get ctx->count buffers
        // with each read if there are bufs available
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->count, nread);
        return ET_ERROR;
    }

    // Mark each event in its header right here.
    // Currently nothing I can think of needs to be done.
    //    int i;
    //    int control[ET_STATION_SELECT_INTS];
    //    for (i=0; i < nread; i++) {
    //        // Can fill control array with anything we want
    //        et_event_setcontrol(ctx->buffers[i], control, ET_STATION_SELECT_INTS);
    //    }

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
 * @param id         ET fifo handle.
 * @param deltatime  time to wait before returning.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 * @returns @ref ET_ERROR_TIMEOUT if timeout.
 */
int et_fifo_newBufsTO(et_fifo_id id, struct timespec *deltatime) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    int nread = 0;

    if (ctx == NULL) return ET_ERROR;

    // When getting new events we forget about group # - not needed anymore
    int err = et_events_new(ctx->openId, ctx->attId, ctx->bufs,
                            ET_TIMED, deltatime, ctx->evSize, ctx->count, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->count) {
        // System is designed to always get ctx->count buffers
        // with each read if there are bufs available
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->count, nread);
        return ET_ERROR;
    }

    // Mark each event in its header right here.
    // Currently nothing I can think of needs to be done.
    //    int i;
    //    int control[ET_STATION_SELECT_INTS];
    //    for (i=0; i < nread; i++) {
    //        // Can fill control array with anything we want
    //        et_event_setcontrol(ctx->buffers[i], control, ET_STATION_SELECT_INTS);
    //    }

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
 * @param id    ET fifo handle.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 */
int et_fifo_getBufs(et_fifo_id id) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    int nread = 0;

    // When getting events w/ data we forget about group # - not needed anymore
    int err = et_events_get(ctx->openId, ctx->attId, ctx->bufs,
                            ET_SLEEP, NULL, ctx->count, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->count) {
        // System is designed to always get ctx->count buffers
        // with each read if there are bufs available
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->count, nread);
        return ET_ERROR;
    }

    // Mark each event in its header right here.
    // Currently nothing I can think of needs to be done.
    //    int i;
    //    int control[ET_STATION_SELECT_INTS];
    //    for (i=0; i < nread; i++) {
    //        // Can fill control array with anything we want
    //        et_event_setcontrol(ctx->buffers[i], control, ET_STATION_SELECT_INTS);
    //    }

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
 * @param id         ET fifo handle.
 * @param deltatime  time to wait before returning.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument(s), or attachment not active,
 *                                or did not get the full number of buffers comprising one fifo entry.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called.
 * @returns @ref ET_ERROR_REMOTE  for a memory allocation error of a remote user.
 * @returns @ref ET_ERROR_READ    for a remote user's network read error.
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error.
 * @returns @ref ET_ERROR_DEAD    if ET system is dead.
 * @returns @ref ET_ERROR_WAKEUP  if told to stop sleeping while trying to get an event.
 * @returns @ref ET_ERROR_TIMEOUT if timeout.
 */
int et_fifo_getBufsTO(et_fifo_id id, struct timespec *deltatime) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    int nread = 0;

    // When getting events w/ data we forget about group # - not needed anymore
    int err = et_events_get(ctx->openId, ctx->attId, ctx->bufs,
                            ET_TIMED, deltatime, ctx->count, &nread);
    if (err != ET_OK) {
        return err;
    }

    if (nread != ctx->count) {
        // System is designed to always get ctx->count buffers
        // with each read if there are bufs available
        et_logmsg("ERROR", "Asked for %d but only got %d\n", ctx->count, nread);
        return ET_ERROR;
    }

    // Mark each event in its header right here.
    // Currently nothing I can think of needs to be done.
    //    int i;
    //    int control[ET_STATION_SELECT_INTS];
    //    for (i=0; i < nread; i++) {
    //        // Can fill control array with anything we want
    //        et_event_setcontrol(ctx->buffers[i], control, ET_STATION_SELECT_INTS);
    //    }

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
 * @param id    ET fifo handle.
 *
 * @returns @ref ET_OK            if successful.
 * @returns @ref ET_ERROR         if bad argument or event data length > ET event size.
 * @returns @ref ET_ERROR_CLOSED  if et_close already called
 * @returns @ref ET_ERROR_READ    for a remote user's network read error
 * @returns @ref ET_ERROR_WRITE   for a remote user's network write error
 * @returns @ref ET_ERROR_DEAD    if ET system is dead
 * @returns @ref ET_ERROR_REMOTE  bad pointer to data or memory allocation error of a remote user
 */
int et_fifo_putBufs(et_fifo_id id) {

    et_fifo_ctx *ctx = (et_fifo_ctx *)id;

    // Put back into ET
    int err = et_events_put(ctx->openId, ctx->attId, ctx->bufs, ctx->count);
    if (err != ET_OK) {
        return err;
    }

    // Clear each event's header right here.
    //    int i;
    //    int control[ET_STATION_SELECT_INTS];
    //    memset(control, 0, sizeof(control));
    //    for (i=0; i < nread; i++) {
    //        et_event_setcontrol(ctx->buffers[i], control, ET_STATION_SELECT_INTS);
    //    }

    return ET_OK;
}


/**
 * This routine gives access to the ET events or buffers obtained by calling either
 * {@link #et_fifo_getBufs}, {@link #et_fifo_getBufsTO},
 * {@link #et_fifo_newBufs}, or {@link #et_fifo_newBufsTO}.
 * The size of the returned array can be found by calling {@link #et_fifo_getEntryCount}.
 *
 * @param id  ET fifo handle.
 * @return array of pointers to et_event structures or NULL if bad arg.
 */
et_event** et_fifo_getBufArray(et_fifo_id id) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx ==  NULL) return NULL;
    return ctx->bufs;
};


/**
 * This routine gets the size of each buffer in bytes.
 * @param id  ET fifo handle.
 * @return size of ech buffer in bytes, or ET_ERROR if bad arg.
 */
int et_fifo_getBufSize(et_fifo_id id) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx ==  NULL) return ET_ERROR;
    return ctx->evSize;
};


/**
 * This routine gets the number of buffers in each FIFO entry.
 * @param id  ET fifo handle.
 * @return number of buffers in each FIFO entry, or ET_ERROR if bad arg.
 */
int et_fifo_getEntryCount(et_fifo_id id) {
    et_fifo_ctx *ctx = (et_fifo_ctx *)id;
    if (ctx ==  NULL) return ET_ERROR;
    return ctx->count;
};

/** @} */
