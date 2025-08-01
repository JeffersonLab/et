//
// Copyright 2022, Jefferson Science Associates, LLC.
// Subject to the terms in the LICENSE file found in the top-level directory.
//
// EPSCI Group
// Thomas Jefferson National Accelerator Facility
// 12000, Jefferson Ave, Newport News, VA 23606
// (757)-269-7100


package org.jlab.coda.et;


import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import org.jlab.coda.et.exception.*;

import java.io.IOException;

/**
 * <p>
 * This class is designed to use an ET system as a FIFO. The idea is that an ET system divides
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
 * This interface is intended to be used by connecting to a local C-based ET system using shared
 * memory - providing a fast, wide FIFO available to multiple local processes. Although it
 * could be used by remote users of the ET system, it's advantages would be lost.
 * </p>
 *
 * <p>
 * These routines are threadsafe with the exception of {@link #getBuffer(int, EtFifoEntry)},
 * but that should never be called on the same arg with different threads.
 * These routines are designed to work as follows:</p>
 * <ol>
 *  <li>Create an {@link EtSystem} object</li>
 *  <li>Call {@link EtSystem#open()} to open ET system</li>
 *  <li>Create an {@link EtFifo} object as either an entry producer or consumer</li>
 *  <li>Create an {@link EtFifoEntry} object
 *      <ul>
 *          <li>Makes sure ET is setup properly</li>
 *          <li>Attaches to GrandCentral station if producing entries</li>
 *          <li>Attaches to Users station if consuming entries</li>
 *      </ul>
 *  </li>
 *  <li>Call {@link #newEntry(EtFifoEntry)} or {@link #newEntry(EtFifoEntry, int)} if producing fifo entry
 *      <ul>
 *          <li>Call {@link EtFifoEntry#getBuffers()} to get access to all the events of a single fifo entry</li>
 *          <li>Call {@link #getEntryCapacity()} to get the number of events in array. Need only be done once.</li>
 *          <li>Call {@link #getBufferSize()} to get available size of each event. Need only be done once.</li>
 *          <li>Call {@link EtEvent#setFifoId(int)} to set an id for a particular event</li>
 *          <li>Call {@link EtEvent#setLength(int)} to set the size of valid data for a particular event</li>
 *      </ul>
 *  </li>
 *  <li>Call {@link #getEntry(EtFifoEntry)} or {@link #getEntry(EtFifoEntry, int)} if consuming fifo entry
 *      <ul>
 *          <li>Call {@link EtFifoEntry#getBuffers()} to get access to all the events of a single fifo entry</li>
 *          <li>Call {@link #getEntryCapacity()} to get the number of events in array. Need only be done once.</li>
 *          <li>Call {@link #getBufferSize()} to get available size of each event. Need only be done once.</li>
 *          <li>Call {@link EtEvent#setFifoId(int)} to set an id for a particular event</li>
 *          <li>Call {@link EtEvent#setLength(int)} to set the size of valid data for a particular event</li>
 *      </ul>
 *  </li>
 *  <li>Call {@link #putEntry(EtFifoEntry)} when finished with fifo entry</li>
 *  <li>Call {@link #close()} when finished with fifo interface</li>
 *  <li>Call {@link EtSystem#close()} when finished with ET system</li>
 * </ol>
 *
 * <p>
 * The first integer of an event's control array is reserved to hold an id
 * to distinguish it from the other events. It's initially set to -1.
 * Note that once you call {@link EtFifoEntry#getBuffers()} and get the events,
 * you have full access to the control array of each event by calling
 * {@link EtEvent#getControl()} and {@link EtEvent#setControl(int[])}
 * and are not restricted to using {@link EtEvent#getFifoId()} and {@link EtEvent#setFifoId(int)}
 * which only access the first integer of the control array.
 * The control array is of size {@link  EtConstants#stationSelectInts}.
 * </p>
 *
 */

public class EtFifo {

    private EtSystem sys;
    // Total size of each buffer in ET system in bytes
    private long evSize;
    // Total number of buffers in ET system
    private int  evCount;
    // Number of fifo entries in ET system
    private int  entries;
    // Number of buffers in each fifo entry
    private int  capacity;

    // Is this object for a fifo entry producer or consumer?
    boolean isProducer;
    // ET attachment to GrandCentral station if producer or Users station if consumer
    EtAttachment att;

    // Only for producers, the ids of all data sources
    private int[] ids;
    private int idCount;


    /**
     * Create a fifo representation of the ET system and
     * interact with it as a consumer of fifo entries.
     *
     * @param sys object representing an open ET system.
     *
     * @throws IOException if network communication error.
     * @throws EtException if ET system is improperly setup (no \"Users\" station).
     *                     If ET system has more than 2 stations.
     * @throws EtClosedException if {@link EtSystem#close()} has been called to close connection to ET.
     * @throws EtDeadException if ET system is dead.
     * @throws EtTooManyException if cannot attach to ET station due to max
     *                            number of such attachments already made.
     */
    public EtFifo(EtSystem sys)
            throws IOException, EtException,
            EtClosedException, EtDeadException, EtTooManyException{
        init(sys, false,null);
    }

    /**
     * Create a fifo representation of the ET system and
     * interact with it as a producer of fifo entries.
     *
     * @param sys object representing an open ET system.
     * @param bufferIds array of expected source ids of data.
     *
     * @throws IOException if network communication error.
     * @throws EtException if ET system is improperly setup. If bufferIds arg is null or 0 length,
     *                     or is too large (in which case ET needs to be created with more buffers in fifo entry).
     *                     If ET system has more than 2 stations.
     * @throws EtClosedException if {@link EtSystem#close()} has been called to close connection to ET.
     * @throws EtDeadException if ET system is dead.
     * @throws EtTooManyException if cannot attach to ET station due to max
     *                            number of such attachments already made.
     */
    public EtFifo(EtSystem sys, int[] bufferIds)
            throws IOException, EtException,
                   EtClosedException, EtDeadException, EtTooManyException{
        init(sys, true, bufferIds);
    }

    /**
     * Perform necessary steps to initialize this fifo.
     *
     * @param sys object representing an open ET system.
     * @param isProducer true if using fifo as producer, else false.
     * @param bufferIds array of expected source ids of data.
     *
     * @throws IOException if network communication error.
     * @throws EtException if ET system is improperly setup. If isProducer true and bufferIds arg is null or 0 length,
     *                     or is too large (in which case ET needs to be created with more buffers in fifo entry).
     *                     If ET system has more than 2 stations.
     * @throws EtClosedException if {@link EtSystem#close()} has been called to close connection to ET.
     * @throws EtDeadException if ET system is dead.
     * @throws EtTooManyException if cannot attach to ET station due to max
     *                            number of such attachments already made.
     */
    private void init(EtSystem sys, boolean isProducer, int[] bufferIds)
            throws  IOException, EtException,
                    EtClosedException, EtDeadException, EtTooManyException {

        // Look into ET system
        evSize   = sys.getEventSize();
        evCount  = sys.getNumEvents();
        this.sys = sys;
        this.isProducer = isProducer;

        // Use the et_start_fifo program to start up the ET system.
        // That program divides events/buffers into groups which tell
        // us the number of fifo entries in ET.
        entries = sys.getGroupCount();

        // Check to make sure ET system is setup properly
        if (evCount % entries != 0) {
            // When starting the ET, use et_start_fifo to set the number of fifo entries
            // and the number of buffers/entry.
            // This ensures the total number of events is divided equally
            // among all the fifo entries.
            throw new EtException("Number of events in ET must be multiple of number of entries");
        }

        capacity = evCount / entries;

        // Save ids if any provided
        if (isProducer) {
            if ((bufferIds == null) || (bufferIds.length < 1)) {
                throw new EtException("must provide buffer ids if fifo entry producer");
            }

            if (bufferIds.length > capacity) {
                throw new EtException("too many ids provided, restart ET as: et_start_fifo -e " + bufferIds.length);
            }

            idCount = bufferIds.length;
            ids = new int[idCount];
            System.arraycopy(bufferIds, 0, ids, 0, idCount);
        }
        
        // Now the station attachments.
        // In an ET system started by et_start_fifo, there are 2 stations:
        // GrandCentral and Users.
        if (isProducer) {
            // Get GRAND_CENTRAL station object
            EtStation gc = sys.stationNameToObject("GRAND_CENTRAL");
            // Attach to GRAND_CENTRAL
            att = sys.attach(gc);
        }
        else {
            // Get GRAND_CENTRAL station object
            EtStation stat = sys.stationNameToObject("Users");
            if (stat == null) {
                throw new EtException("Start ET system with et_start_fifo program so \"Users\" station is created");
            }
            // Attach to "Users" station
            att = sys.attach(stat);
        }

        if (sys.getNumStations() > 2) {
            throw new EtException("ET has more than 2 stations, improperly setup, use et_start_fifo only");
        }
    }


    /**
     * Close this object, which is just a detach from an ET station.
     * <b>Closing the ET system must be done separately.</b>
     */
    public void close() {
        // If attached to a station, detach
        try {
            if (att != null && sys != null) {
                sys.detach(att);
            }
        } catch (IOException e) {
        } catch (EtDeadException e) {
        } catch (EtClosedException e) {
        } catch (EtException e) {
        }
    }


    /**
     * This routine is called when a user wants an array of related, empty buffers from the ET system
     * into which data can be placed. This routine will block until the buffers become available.
     * Access the buffers by calling {@link EtFifoEntry#getBuffers()}.
     * <b>Each call to this routine MUST be accompanied by a following call to {@link #putEntry(EtFifoEntry)}!</b>
     *
     * @see EtSystem#newEvents(EtContainer)
     *
     * @param entry fifo entry to be filled with new events.
     *
     * @throws IOException  if problems with network communications.
     * @throws EtException
     *     if called by consumer,
     *     or if thread calling this is interrupted while reading from socket,
     *     or attachment not active,
     *     or did not get the full number of buffers comprising one fifo entry,
     *     or for other general errors.
     * @throws EtDeadException   if the ET system processes are dead.
     * @throws EtClosedException if the ET system is closed.
     * @throws EtWakeUpException if the attachment has been commanded to wakeup.
     */
    public void newEntry(EtFifoEntry entry)
            throws EtException, EtDeadException, EtClosedException,
                   EtWakeUpException, IOException {

        int[] control;
        EtEvent[] events = null;

        if (!isProducer) {
            throw new EtException("Only a fifo producer can call this routine");
        }
        int nread = 0;

        try {
            // When getting new events we forget about group # - not needed anymore
            EtContainer container = entry.getContainer();
            container.newEvents(att, Mode.SLEEP, 0, capacity, (int)evSize);
            sys.newEventsNoGroup(container);
            nread = container.getEventCount();
            events = container.getEventArray();
        } catch (EtTimeoutException e) {
            // never happen
        } catch (EtEmptyException e) {
            // never happen
        } catch (EtBusyException e) {
            // never happen
        }

        if (nread != capacity) {
            // System is designed to always get ctx->count buffers
            // with each read if there are bufs available
            throw new EtException("Asked for " + capacity + " but only got " + nread + " events");
        }

        // Fill the first event-control-int with provided id.
        // Otherwise fill with -1 (allows a valid value of 0 for id).
        for (int i = 0; i < idCount; i++) {
            control = events[i].getControl();
            control[0] = ids[i];
            // By default a new event's length is total buffer size
            control[1] = 0;
        }
        for (int i = idCount; i < capacity; i++) {
            events[i].getControl()[0] = -1;
            events[i].getControl()[1] = 0;
        }
    }


    /**
     * This routine is called when a user wants an array of related, empty buffers from the ET system
     * into which data can be placed. This routine will block until it times out.
     * Access the buffers by calling {@link EtFifoEntry#getBuffers()}.
     * <b>Each call to this routine MUST be accompanied by a following call to {@link #putEntry}!</b>
     *
     * @see EtSystem#newEvents(EtContainer)
     *
     * @param entry fifo entry to be filled with new events.
     * @param microSec  time to wait before returning.
     *
     * @throws IOException  if problems with network communications.
     * @throws EtException
     *     if called by consumer,
     *     or if thread calling this is interrupted while reading from socket,
     *     or attachment not active,
     *     or did not get the full number of buffers comprising one fifo entry,
     *     or for other general errors.
     * @throws EtDeadException    if the ET system processes are dead.
     * @throws EtClosedException  if the ET system is closed.
     * @throws EtWakeUpException  if the attachment has been commanded to wakeup.
     * @throws EtTimeoutException if the given time has expired (never thrown if microsec = 0).
     */
    public void newEntry(EtFifoEntry entry, int microSec)
            throws EtException, EtDeadException, EtClosedException,
                   EtTimeoutException, EtWakeUpException, IOException {

        int[] control;
        EtEvent[] events = null;

        if (microSec < 0) microSec = 0;

        if (!isProducer) {
            throw new EtException("Only a fifo producer can call this routine");
        }
        int nread = 0;

        try {
            // When getting new events we forget about group # - not needed anymore
            EtContainer container = entry.getContainer();
            container.newEvents(att, Mode.TIMED, microSec, capacity, (int)evSize);
            sys.newEventsNoGroup(container);
            nread = container.getEventCount();
            events = container.getEventArray();
        } catch (EtEmptyException e) {
            // never happen
        } catch (EtBusyException e) {
            // never happen
        }

        if (nread != capacity) {
            // System is designed to always get ctx->count buffers
            // with each read if there are bufs available
            throw new EtException("Asked for " + capacity + " but only got " + nread + " events");
        }

        // Fill the first event-control-int with provided id.
        // Otherwise fill with -1 (allows a valid value of 0 for id).
        for (int i = 0; i < idCount; i++) {
            control = events[i].getControl();
            control[0] = ids[i];
            // By default a new event's length is total buffer size
            control[1] = 0;
        }
        for (int i = idCount; i < capacity; i++) {
            events[i].getControl()[0] = -1;
            events[i].getControl()[1] = 0;
        }
    }


    /**
     * This routine is called when a user wants an array of related, data-filled buffers from the
     * ET system. This routine will block until the buffers become available.
     * Access the buffers by calling {@link EtFifoEntry#getBuffers()}.
     * <b>Each call to this routine MUST be accompanied by a following call to {@link #putEntry}!</b>
     *
     * @see EtSystem#getEvents(EtContainer)
     *
     * @param entry fifo entry to be filled with events.
     *
     * @throws IOException  if problems with network communications.
     * @throws EtException
     *     if called by consumer,
     *     or if thread calling this is interrupted while reading from socket,
     *     or attachment not active,
     *     or did not get the full number of buffers comprising one fifo entry,
     *     or for other general errors.
     * @throws EtDeadException    if the ET system processes are dead.
     * @throws EtClosedException  if the ET system is closed.
     * @throws EtWakeUpException  if the attachment has been commanded to wakeup.
     */
    public void getEntry(EtFifoEntry entry)
            throws EtException, EtDeadException, EtClosedException,
                   EtWakeUpException, IOException {

        if (isProducer) {
            throw new EtException("Only a fifo consumer can call this routine");
        }

        int nread = 0;

        try {
            EtContainer container = entry.getContainer();
            container.getEvents(att, Mode.SLEEP, Modify.ANYTHING, 0, capacity);
            sys.getEvents(container);
            nread = container.getEventCount();
        } catch (EtTimeoutException e) {
            // never happen
        } catch (EtEmptyException e) {
            // never happen
        } catch (EtBusyException e) {
            // never happen
        }

        if (nread != capacity) {
            // System is designed to always get ctx->count buffers
            // with each read if there are bufs available
            throw new EtException("Asked for " + capacity + " but only got " + nread + " events");
        }
    }


    /**
     * This routine is called when a user wants an array of related, data-filled buffers from the
     * ET system. This routine will block until it times out.
     * Access the buffers by calling {@link EtFifoEntry#getBuffers()}.
     * <b>Each call to this routine MUST be accompanied by a following call to {@link #putEntry}!</b>
     *
     * @see EtSystem#getEvents(EtContainer)
     *
     * @param entry fifo entry to be filled with events.
     * @param microSec  time to wait before returning.
     *
     * @throws IOException  if problems with network communications.
     * @throws EtException
     *     if called by consumer,
     *     or if bad arg,
     *     or if thread calling this is interrupted while reading from socket,
     *     or attachment not active,
     *     or did not get the full number of buffers comprising one fifo entry,
     *     or for other general errors.
     * @throws EtDeadException    if the ET system processes are dead.
     * @throws EtClosedException  if the ET system is closed.
     * @throws EtWakeUpException  if the attachment has been commanded to wakeup.
     * @throws EtTimeoutException if the given time has expired (never thrown if microsec = 0).
     */
    public void getEntry(EtFifoEntry entry, int microSec)
            throws EtException, EtDeadException, EtClosedException,
                   EtTimeoutException, EtWakeUpException, IOException {

        if (isProducer) {
            throw new EtException("Only a fifo consumer can call this routine");
        }

        int nread = 0;

        try {
            EtContainer container = entry.getContainer();
            container.getEvents(att, Mode.TIMED, Modify.ANYTHING, microSec, capacity);
            sys.getEvents(container);
            nread = container.getEventCount();
        } catch (EtEmptyException e) {
            // never happen
        } catch (EtBusyException e) {
            // never happen
        }

        if (nread != capacity) {
            // System is designed to always get ctx->count buffers
            // with each read if there are bufs available
            throw new EtException("Asked for " + capacity + " but only got " + nread + " events");
        }
    }


    /**
     * This routine is called when a user wants to place an array of related, data-filled buffers
     * (single FIFO entry) back into the ET system. This must be called after calling either
     * {@link #getEntry(EtFifoEntry)}, {@link #getEntry(EtFifoEntry, int)} ,
     * {@link #newEntry(EtFifoEntry)} , or {@link #newEntry(EtFifoEntry, int)} .
     * This routine will never block.
     *
     * @see EtSystem#putEvents(EtContainer)
     *
     * @param entry fifo entry with events to be returned to ET.
     *
     * @throws IOException  if problems with network communications.
     * @throws EtException
     *     or attachment not active,
     *     or for other general errors.
     * @throws EtDeadException    if the ET system processes are dead.
     * @throws EtClosedException  if the ET system is closed.
     */
    public void putEntry(EtFifoEntry entry)
            throws EtException, EtDeadException, EtClosedException, IOException {

        EtContainer container = entry.getContainer();
        container.putEvents(att, 0, capacity);
        sys.putEvents(container);
    }


    /**
     * Does this ET-fifo user produce fifo entries?
     * @return true if this ET-fifo user produces fifo entries, else false if it consumes fifo entries.
     */
    public boolean isProducer() {return isProducer;}


    /**
     * Gets the size of each event's data buffer in bytes.
     * @return  size of each event's data buffer in bytes.
     */
    public long getBufferSize() {return evSize;}


    /**
     * Gets the max number of events in each FIFO entry.
     * @return  max number of events in each FIFO entry.
     */
    public int getEntryCapacity() {return capacity;}


    /**
     * Gets the number of buffers assigned an id in each FIFO entry.
     * @return  number of buffers assigned an id in each FIFO entry.
     */
    public int getIdCount() {return idCount;}


    /**
     * Gets the array of ids assigned to buffers in each FIFO entry.
     * The same as constructors' bufferIds argument.
     * <p><b>The returned array is not a copy, so don't change values!</b></p>
     *
     * @return number of buffers assigned an id in each FIFO entry, or ET_ERROR if bad arg(s).
     */
    public int[] getBufferIds() {return ids;}


    /**
     * Find the event/buffer in the fifo entry corresponding to the given id.
     * If none, get the first unused buffer, assign it that id,
     * and return it. The id value for an event is stored in its first
     * control integer. NOT threadsafe.
     *
     * @param id    id.
     * @param entry fifo entry with events to be searched.
     *
     * @return buffer labelled with id or first one that's unused.
     *         NULL if no bufs are available.
     */
    public static EtEvent getBuffer(int id, EtFifoEntry entry) {
        if (entry == null) return null;

        int c0;
        EtEvent[] evs = entry.getBuffers();

        for (EtEvent event : evs) {
            if (event == null) continue;

            c0 = event.getControl()[0];

            if (c0 == id) {
                return event;
            }

            // If we've reached the end of reserved buffers, and
            // still haven't found one associated with id, reserved & use this one.
            if (c0 == -1) {
                event.getControl()[0] = id;
                return event;
            }
        }

        return null;
    }



}
