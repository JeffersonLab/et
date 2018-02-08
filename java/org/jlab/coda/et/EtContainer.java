/*
 * Copyright (c) 2018, Jefferson Science Associates
 *
 * Thomas Jefferson National Accelerator Facility
 * Data Acquisition Group
 *
 * 12000 Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 */
package org.jlab.coda.et;

import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import org.jlab.coda.et.exception.*;

import java.util.BitSet;

import static org.jlab.coda.et.EtContainer.MethodType.*;

/**
 * This is a helper class to facilitate efficient java programming in
 * making, getting, putting and dumping ET events. Designed for use with the
 * Disruptor (ring) software package and in the emu package of CODA
 * online software.
 */
public class EtContainer {

    /**
     * Keep track of whether we're setup to do a
     * newEvents(), getEvents(), putEvents(), or dumpEvents().
     */
    enum MethodType {NEW, GET, PUT, DUMP;}

    /** The method this container is set to deal with. */
    MethodType method = NEW;

    /** Size in bytes of the events in the ET system. */
    private int etEventSize;

    /** Size in bytes of buffer in each "real" event. */
    private int bufSize;

    /** Number of valid events when getting event array for newEvents and getEvents. */
    int eventCount;

    /** Convenience variable for realEvents.length - total # of events we have. */
    private int eventArraySize;

    /** Place to hold ET events. */
    EtEvent[] events;

    /** Array returned by new/getEvents when using jni. */
    private EtEvent[] jniEvents;

    /** Actual ET events with buffers/real memory. */
    EtEventImpl[] realEvents;

    /** Place to temporarily store events being put/dumped. */
    EtEvent[] putEvents;

    /** Place to temporarily store events being put/dumped. */
    EtEventImpl[] holdEvents;

    /** When doing put/dumpEvents, offset into event array arg. */
    int offset;
    
    /** When doing put/dumpEvents, number of events to put/dump. */
    int length;

    /** Attachment to ET system used to make, get, put, and dump events. */
    EtAttachment att;

    /** Et system object to track if putting event from correct attachment. */
    private EtSystem sys;

    /**
     * Array used in newEvents(), getEvents(), putEvents(), or dumpEvents()
     * for any purpose. Defined here to prevent allocation each time method
     * is called. Reduces generated garbage. Will be expanded as needed.
     */
    byte[] byteArray = new byte[1000];

    /** Modify used to obtain events with getEvents(). */
    Modify modify;

    /** Mode used to obtain events with newEvents() or getEvents(). */
    Mode mode;

    /** Timeout in microseconds used when obtaining events with newEvents() or getEvents(). */
    int microSec;

    /** Number of events asked for in newEvents() or getEvents(). */
    int count;

    /** Size in bytes of events asked for in newEvents(). */
    int size;

    /** Group number from which to get events in newEvents(). */
    int group;


    /**
     * Constructor.
     * @param sys  ET system object.
     * @throws EtException  if arg is null.
     */
    public EtContainer(EtSystem sys) throws EtException {
        if (sys == null) {
            throw new EtException("sys arg null");
        }
        this.sys = sys;
    }

    /**
     * Constructor specifying the number and size of internal buffers
     * which are then created and initialized.
     * If the specified size is less than the ET buffer size, it is
     * internally set to the ET buffer size.
     *
     * @param sys     ET system object.
     * @param count   number of internal buffers/events.
     * @param size    number of bytes in each internal buffer.
     * @throws EtException if sys arg null or count arg < 1.
     */
    public EtContainer(EtSystem sys, int count, int size) throws EtException {
        if (sys == null) {                                
            throw new EtException("sys arg null");
        }
        else if (count < 1) {
            throw new EtException("count arg must be > 0");
        }

        this.sys = sys;

        // Be smart here, we want the size of our internal buffers to be at
        // least the size of the ET events or may get into a mode of operation
        // that is slow and inefficient by having to constantly allocate bigger
        // buffers.
        etEventSize  = (int) sys.getEventSize();
        bufSize = size > etEventSize ? size : etEventSize;

        eventArraySize = count;

        events = new EtEvent[eventArraySize];
        holdEvents = new EtEventImpl[eventArraySize];
        realEvents = new EtEventImpl[eventArraySize];

        for (int i=0; i < eventArraySize; i++) {
            realEvents[i] = new EtEventImpl(bufSize);
        }
    }


    /**
     * Make sure the event arrays have at least the given number of events
     * at the given data buffer size (with ET event size minimum).
     * @param count needed number of events in arrays.
     * @param size  needed bytes in each event's internal data buffer.
     */
    private void adjustEventArraySize(int count, int size) {
        if (realEvents == null || eventArraySize < count) {
            // Pick the largest
            bufSize = bufSize < size ? size : bufSize;
            etEventSize  = (int) sys.getEventSize();
            bufSize = bufSize < etEventSize? etEventSize : bufSize;

            eventArraySize = count;
//System.out.println("Init event/realEvent arrays to len = " + eventArraySize);

            events = new EtEvent[eventArraySize];
            holdEvents = new EtEventImpl[eventArraySize];
            realEvents = new EtEventImpl[eventArraySize];

            for (int i=0; i < eventArraySize; i++) {
//System.out.println("Create real event #" + i);
                realEvents[i] = new EtEventImpl(bufSize);
            }
        }
        else if (bufSize < size) {
            bufSize = size;
            for (int i=0; i < eventArraySize; i++) {
//System.out.println("RE-create real event #" + i);
                realEvents[i] = new EtEventImpl(bufSize);
            }
        }
    }
     
    //***********************************************

    /**
     * Set this container up for getting new (unused) events from a specified group
     * of such events in an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att       attachment object.
     * @param mode      if there are no new events available, this parameter specifies
     *                  whether to wait for some by sleeping {@link Mode#SLEEP},
     *                  to wait for a set time {@link Mode#TIMED},
     *                  or to return immediately {@link Mode#ASYNC}.
     * @param microSec  the number of microseconds to wait if a timed wait is specified.
     * @param count     the number of events desired .
     * @param size      the size of events in bytes.
     * @param group     group number from which to draw new events. Some ET systems have
     *                  unused events divided into groups whose numbering starts at 1.
     *                  For ET system not so divided, all events belong to group 1.
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if attachment object is invalid;
     */
    public void newEvents(EtAttachment att, Mode mode,
                          int microSec, int count, int size, int group)
                throws EtException {

        if (mode == null) {
            throw new EtException("mode arg null");
        }
        else if (att == null || !att.isUsable() || att.getSys() != sys) {
            throw new EtException("Invalid attachment");
        }
        else if (count < 0) {
            throw new EtException("count arg negative");
        }
        else if ((microSec < 0) && (mode == Mode.TIMED)) {
            throw new EtException("microSec arg negative");
        }
        else if (size < 1) {
            throw new EtException("size arg < 1");
        }
        else if (group < 1) {
            throw new EtException("group arg < 1");
        }

        this.att = att;
        this.mode = mode;
        this.microSec = microSec;
        this.count = count;
        this.size = size;
        this.group = group;

        // We're setup to call the newEvents() method
        method = NEW;
        jniEvents = null;

        // Make sure there's room for the desired sized new events
        adjustEventArraySize(count, size);

        for (int i=0; i < eventArraySize; i++) {
            realEvents[i].init();
        }
    }


    /**
     * Set this container up for getting events from an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att      attachment object.
     * @param mode     if there are no events available, this parameter specifies
     *                 whether to wait for some by sleeping {@link Mode#SLEEP},
     *                 to wait for a set time {@link Mode#TIMED},
     *                 or to return immediately {@link Mode#ASYNC}.
     * @param modify   this specifies whether this application plans
     *                 on modifying the data in events obtained {@link Modify#ANYTHING}, or
     *                 only modifying headers {@link Modify#HEADER}. The default assumed ,{@link Modify#NOTHING},
     *                 means that no values are modified resulting in the events being put back into
     *                 the ET system (by remote server) immediately upon being copied and that copy
     *                 sent to this method's caller.
     * @param microSec the number of microseconds to wait if a timed wait is specified.
     * @param count    the number of events desired.
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if the attachment's station is GRAND_CENTRAL;
     *     if the attachment object is invalid;
     */
    public void getEvents(EtAttachment att, Mode mode, Modify modify,
                          int microSec, int count)
                throws EtException {

        if (att == null|| !att.isUsable() || att.getSys() != sys) {
            throw new EtException("Invalid attachment");
        }
        else if (att.getStation().getId() == 0) {
            throw new EtException("may not get events from GRAND_CENTRAL");
        }
        else if (count < 0) {
            throw new EtException("count arg negative");
        }
        else if (mode == null) {
            throw new EtException("mode arg null");
        }
        else if ((mode == Mode.TIMED) && (microSec < 0)) {
            throw new EtException("microSec arg negative");
        }

        this.att = att;
        this.mode = mode;
        this.modify = modify == null? Modify.NOTHING : modify;
        this.microSec = microSec;
        this.count = count;

        // We're setup to call the getEvents() method
        method = GET;
        jniEvents = null;

        // Make sure there's room for ET sized new events
        adjustEventArraySize(count, 0);
    }


    /**
     * Set this container up for putting (used) events back into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att     attachment object
     * @param evs     array of event objects
     * @param offset  offset into array
     * @param length  number of array elements to put
     *
     * @throws EtException
     *     if invalid arg(s);
     *     if events are not owned by this attachment;
     */
    public void putEvents(EtAttachment att, EtEvent[] evs, int offset, int length)
                    throws EtException {

        if (evs == null) {
            throw new EtException("Invalid event array arg");
        }

        if (offset < 0 || length < 0 || offset + length > evs.length) {
            throw new EtException("Bad offset or length argument(s)");
        }

        if (att == null || !att.isUsable() || att.getSys() != sys) {
            throw new EtException("Invalid attachment");
        }

        this.att = att;
        this.offset = offset;
        this.length = length;

        // We're setup to call the putEvents() method
        method = PUT;

        // We cannot cast evs to putEvents since putEvents is a subclass
        putEvents = evs;

        // Need space to hold all evs elements in EtEventImpl array
        if (holdEvents.length < length) {
            holdEvents = new EtEventImpl[length];
        }
    }


    /**
     * Set this container up for dumping events back into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att     attachment object
     * @param evs     array of event objects
     * @param offset  offset into array
     * @param length  number of array elements to put
     *
     * @throws EtException
     *     if invalid arg(s);
     *     if events are not owned by this attachment;
     */
    public void dumpEvents(EtAttachment att, EtEvent[] evs, int offset, int length)
                    throws EtException {

        if (evs == null) {
            throw new EtException("Invalid event array arg");
        }

        if (offset < 0 || length < 0 || offset + length > evs.length) {
            throw new EtException("Bad offset or length argument(s)");
        }

        if (att == null || !att.isUsable() || att.getSys() != sys) {
            throw new EtException("Invalid attachment");
        }

        this.att = att;
        this.offset = offset;
        this.length = length;

        // We're setup to call the dumpEvents() method
        method = DUMP;
        
        putEvents = evs;
        
        // Need space to hold all evs elements in EtEventImpl array
        if (holdEvents.length < length) {
            holdEvents = new EtEventImpl[length];
        }
    }


    //***********************************************


    /**
     * Take the new events obtained in {@link EtSystem#newEvents(EtContainer)} or
     * {@link EtSystem#getEvents(EtContainer)} and assign them to local storage.
     * @param evs array of incoming events. This array will be {@link #realEvents}
     *            unless it is from a jni method call, in which case we store it
     *            in the {@link #jniEvents} array.
     */
    void holdNewEvents(EtEventImpl[] evs) {
        holdNewEvents(evs, evs.length);
    }


    /**
     * Take the new events obtained in {@link EtSystem#newEvents(EtContainer)} or
     * {@link EtSystem#getEvents(EtContainer)} and assign them to local storage.
     * @param evs array of incoming events. This array will be {@link #realEvents}
     *            unless it is from a jni method call, in which case we store it
     *            in the {@link #jniEvents} array.
     * @param len number of valid events in array (starting at 0).
     */
    void holdNewEvents(EtEventImpl[] evs, int len) {
        // We already set the internal event array sizes to cover a newEvent request,
        // but we need to track how many we actually received (which may be less
        // but never greater).
        eventCount = len;

        // If the array we've received (evs) is not the one we originally supplied,
        // it's from a jni call. Store this in a separate variable.
        if (evs != realEvents) {
            jniEvents = evs;
        }
    }

    
    /**
     * Get an array containing events resulting from a call to
     * {@link #newEvents(EtAttachment, Mode, int, int, int, int)} or
     * {@link #getEvents(EtAttachment, Mode, Modify, int, int)}.
     * Not all of these are valid since the returned array is a reference to a
     * large internal array. Get the number of valid events by calling
     * {@link #getEventCount()}. Please don't do anything nasty to
     * the elements of this internal array.
     *
     * @return events from a newEvents() or getEvents() call; null if last call
     *         was not to either of these but to putEvents() or dumpEvents().
     */
    public EtEvent[] getEventArray() {
        if (method != NEW && method != GET) return null;
        if (jniEvents != null) {
            return jniEvents;
        }
        return realEvents;
    }


    /**
     * Get the number of valid events resulting from a call to newEvents or getEvents.
     * @return number of valid events resulting from a call to newEvents or getEvents.
     */
    public int getEventCount() {return eventCount;}


    /**
     * Make sure the byte array has at least the given amount of bytes.
     * @param neededBytes needed amount of bytes in byte array.
     */
    void adjustByteArraySize(int neededBytes) {
        if (byteArray.length < neededBytes) {
            byteArray = new byte[neededBytes];
        }
    }

    
    /** Clear out unused event arrays. */
    private void clear() {
        if (events != null) {
            for (int i=0; i < events.length; i++) {
                events[i] = null;
            }
        }

        if (realEvents != null) {
            for (EtEventImpl ev : realEvents) {
                ev.init();
            }
        }
    }


    //****************************************************
    // For CODA online and use in the Disruptor package.
    //****************************************************

    /** Is this the END event? */
    public boolean[] isEnd;

    /** Number of evio events in ET event. */
    public int[] itemCounts;

    /** Bit infos to set in EventWriter. */
    public BitSet bitInfos[] /*= new BitSet(24)*/;

    /** Ids to set in EventWriter. */
    public int[] recordIds;


}
