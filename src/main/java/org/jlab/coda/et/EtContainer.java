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
import org.jlab.coda.et.system.AttachmentLocal;

import java.util.List;

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
    public enum MethodType {NEW, NEW_LOCAL, GET, GET_LOCAL, PUT, PUT_LOCAL, DUMP, DUMP_LOCAL;}

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

    /** Array returned by new/getEvents when using jni. */
    private EtEventImpl[] jniEvents;

    /** Actual ET events with buffers/real memory. */
    EtEventImpl[] realEvents;

    /** Reference to events being put/dumped using normal calls.
     *  Actual events were originally placed in either jniEvents or realEvents. */
    EtEventImpl[] putDumpEvents;

    /** Place to store references to events being put/dumped using JNI with local calls. */
    EtEventImpl[] holdEvents;

    /** When doing put/dumpEvents, offset into event array arg. */
    int offset;
    
    /** When doing put/dumpEvents, number of events to put/dump. */
    int length;

    /** If used by CODA, does an element of holdEvents/putEvents array have the END event? */
    private boolean hasEndEvent;

    /**
     * Sometimes not all events obtained through new/getEvents will
     * be used. If only some are useful, store the last useful event's or
     * "last" index in this variable. The caller can do a putEvents on the
     * useful and a dumpEvents on the rest.<p>
     * This is relevant when dealing with prestart or end events in CODA.
     */
    private int lastIndex = -1;

    /** Attachment to ET system used to make, get, put, and dump events. */
    EtAttachment att;

    /** Local attachment to ET system used to make, get, put, and dump events. */
    AttachmentLocal attLocal;

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
     * @param eventSize  ET system event size in bytes.
     * @throws EtException if eventSize &lt; 1.
     */
    public EtContainer(int eventSize) throws EtException {
        if (eventSize < 1) {
            throw new EtException("eventSize must be > 0");
        }
        etEventSize = eventSize;
    }

    /**
     * Constructor specifying the number and size of internal buffers
     * which are then created and initialized.
     * If the specified size is less than the ET buffer size, it is
     * internally set to the ET buffer size.
     *
     * @param count      number of internal buffers/events.
     * @param eventSize  ET system event size in bytes.
     * @throws EtException if either arg &lt; 1.
     */
    public EtContainer(int count, int eventSize) throws EtException {
        if (count < 1 || eventSize < 1) {
            throw new EtException("args must be > 0");
        }

        // Be smart here, we want the size of our internal buffers to be at
        // least the size of the ET events or may get into a mode of operation
        // that is slow and inefficient by having to constantly allocate bigger
        // buffers.
        bufSize = etEventSize = eventSize;

        eventArraySize = count;

        holdEvents = new EtEventImpl[eventArraySize];
        realEvents = new EtEventImpl[eventArraySize];

        for (int i=0; i < eventArraySize; i++) {
            realEvents[i] = new EtEventImpl(bufSize);
        }
    }

    /**
     * Get the size of ET system events in bytes.
     * @return size of ET system events in bytes.
     */
    public int getEtEventSize() {return etEventSize;}

    /**
     * Get the attachment to ET system.
     * @return attachment to ET system.
     */
    public EtAttachment getAtt() {return att;}

    /**
     * If ET is java-based in this JVM, attachment to it.
     * @return attachment to ET system if system is java-based in this JVM.
     */
    public AttachmentLocal getAttLocal() {return attLocal;}

    /**
     * Get the mode used to obtain events with newEvents() or getEvents().
     * @return mode used to obtain events with newEvents() or getEvents().
     */
    public Mode getMode() {return mode;}

    /**
     * Get the number of events asked for in newEvents() or getEvents().
     * @return number of events asked for in newEvents() or getEvents().
     */
    public int getCount() {return count;}

    /**
     * Get the size in bytes of events asked for in newEvents().
     * @return size in bytes of events asked for in newEvents().
     */
    public int getSize() {return size;}

    /**
     * Get the group number from which to get events in newEvents().
     * @return group number from which to get events in newEvents().
     */
    public int getGroup() {return group;}

    /**
     * Get the timeout in microseconds used when obtaining
     * events with newEvents() or getEvents().
     * @return timeout in microseconds used when obtaining
     *         events with newEvents() or getEvents().
     */
    public int getMicroSec() {return microSec;}

    /**
     * Get the array used to store references to events being put/dumped using JNI with local calls.
     * OR containing events resulting from a local call to newEvents() or getEvents().
     * @return array used to store references to events used with local calls.
     */
    public EtEventImpl[] getHoldEvents() {return holdEvents;}

    /**
     * Get variable keeping track of whether we're setup to do a
     * newEvents(), getEvents(), putEvents(), or dumpEvents().
     * @return variable keeping track of whether we're setup to do a
     *         newEvents(), getEvents(), putEvents(), or dumpEvents().
     */
    public MethodType getMethod() {return method;}

    /**
     * Make sure the event arrays have at least the given number of events
     * at the given data buffer size (with ET event size minimum).
     * @param count    needed number of events in arrays.
     * @param size     needed bytes in each event's internal data buffer.
     * @param allocate if true, allocate memory for internal storage of events.
     */
    private void adjustEventArraySize(int count, int size, boolean allocate) {
        if (realEvents == null || eventArraySize < count) {
            // Pick the largest
            bufSize = bufSize < size ? size : bufSize;
            bufSize = bufSize < etEventSize? etEventSize : bufSize;

            eventArraySize = count;
//System.out.println("Init event/realEvent arrays to len = " + eventArraySize);

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
        else if (allocate) {
            if (bufSize < size) bufSize = size;
            for (int i=0; i < eventArraySize; i++) {
                realEvents[i] = new EtEventImpl(bufSize);
            }
        }
    }
     
    //***********************************************

    // There are 2 varieties of newEvents, one takes "group" as an arg, the other does not.
    // First is the simpler form that does NOT

    /**
     * Set this container up for getting new (unused) events from an ET system.
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
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if attachment object is invalid;
     */
    public void newEvents(EtAttachment att, Mode mode,
                          int microSec, int count, int size)
            throws EtException {
        newEvents(att, mode, microSec, count, size, false);
    }

    /**
     * Set this container up for getting new (unused) events from an ET system.
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
     * @param allocate  if true, allocate new objects in which to store the new events.
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if attachment object is invalid;
     */
    public void newEvents(EtAttachment att, Mode mode,
                          int microSec, int count, int size, boolean allocate)
            throws EtException {

        if (mode == null) {
            throw new EtException("mode arg null");
        }
        else if (att == null || !att.isUsable()) {
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

        this.att = att;
        this.mode = mode;
        this.microSec = microSec;
        this.count = count;
        this.size = size;

        // We're setup to call the newEvents() method
        method = NEW;
        jniEvents = null;
        lastIndex = -1;
        hasEndEvent = false;

        // Make sure there's room for the desired sized new events
        adjustEventArraySize(count, size, allocate);

        for (int i=0; i < eventArraySize; i++) {
            realEvents[i].init();
        }
    }

    /**
     * This method is for expert use only!
     * Set this container up for getting new (unused) events from an ET system.
     * Will access local Java-based ET systems running in the same JVM.
     *
     * @param att       local attachment object.
     * @param modeVal   if there are no new events available, this parameter specifies
     *                  whether to wait for some by sleeping {@link EtConstants#sleep},
     *                  to wait for a set time {@link EtConstants#timed},
     *                  or to return immediately {@link EtConstants#async}.
     * @param microSec  the number of microseconds to wait if a timed wait is specified.
     * @param count     the number of events desired .
     * @param size      the size of events in bytes.
     *
     * @throws EtException
     *     if arguments have bad values;
     */
    public void newEvents(AttachmentLocal att, int modeVal,
                          int microSec, int count, int size)
            throws EtException {

        Mode mode = Mode.getMode(modeVal);

        if (mode == null) {
            throw new EtException("modeVal arg invalid");
        }
        else if (att == null) {
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

        this.attLocal = att;
        this.mode = mode;
        this.microSec = microSec;
        this.count = count;
        this.size = size;

        // We're setup to call the local newEvents() method
        method = NEW_LOCAL;
        jniEvents = null;
        lastIndex = -1;
        hasEndEvent = false;

        // Space for new events is created when SystemCreate.newEvents()
        // calls holdLocalEvents().
    }


    // Second form specifies group

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
        newEvents(att, mode, microSec, count, size, group, false);
    }

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
     * @param allocate  if true, allocate new objects in which to store the new events.
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if attachment object is invalid;
     */
    public void newEvents(EtAttachment att, Mode mode,
                          int microSec, int count, int size, int group,
                          boolean allocate)
                throws EtException {

        if (mode == null) {
            throw new EtException("mode arg null");
        }
        else if (att == null || !att.isUsable()) {
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
        lastIndex = -1;
        hasEndEvent = false;

        // Make sure there's room for the desired sized new events
        adjustEventArraySize(count, size, allocate);

        for (int i=0; i < eventArraySize; i++) {
            realEvents[i].init();
        }
    }

    /**
     * This method is for expert use only!
     * Set this container up for getting new (unused) events from a specified group
     * of such events in an ET system.
     * Will access local Java-based ET systems running in the same JVM.
     *
     * @param att       local attachment object.
     * @param modeVal   if there are no new events available, this parameter specifies
     *                  whether to wait for some by sleeping {@link EtConstants#sleep},
     *                  to wait for a set time {@link EtConstants#timed},
     *                  or to return immediately {@link EtConstants#async}.
     * @param microSec  the number of microseconds to wait if a timed wait is specified.
     * @param count     the number of events desired .
     * @param size      the size of events in bytes.
     * @param group     group number from which to draw new events. Some ET systems have
     *                  unused events divided into groups whose numbering starts at 1.
     *                  For ET system not so divided, all events belong to group 1.
     *
     * @throws EtException
     *     if arguments have bad values;
     */
    public void newEvents(AttachmentLocal att, int modeVal,
                          int microSec, int count, int size, int group)
                throws EtException {

        Mode mode = Mode.getMode(modeVal);

        if (mode == null) {
            throw new EtException("modeVal arg invalid");
        }
        else if (att == null) {
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

        this.attLocal = att;
        this.mode = mode;
        this.microSec = microSec;
        this.count = count;
        this.size = size;
        this.group = group;

        // We're setup to call the local newEvents() method
        method = NEW_LOCAL;
        jniEvents = null;
        lastIndex = -1;
        hasEndEvent = false;

        // Space for new events is created when SystemCreate.newEvents()
        // calls holdLocalEvents().
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
        getEvents(att, mode, modify, microSec, count, false);
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
     * @param allocate if true, allocate new objects in which to store the new events.
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if the attachment's station is GRAND_CENTRAL;
     *     if the attachment object is invalid;
     */
    public void getEvents(EtAttachment att, Mode mode, Modify modify,
                          int microSec, int count, boolean allocate)
                throws EtException {

        if (att == null|| !att.isUsable()) {
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
        lastIndex = -1;
        hasEndEvent = false;

        // Make sure there's room for ET sized new events
        adjustEventArraySize(count, 0, allocate);
    }


    /**
     * This method is for expert use only!
     * Set this container up for getting events from an ET system.
     * Will access local Java-based ET systems running in the same JVM.
     *
     * @param att      local attachment object.
     * @param modeVal  if there are no new events available, this parameter specifies
     *                 whether to wait for some by sleeping {@link EtConstants#sleep},
     *                 to wait for a set time {@link EtConstants#timed},
     *                 or to return immediately {@link EtConstants#async}.
     * @param microSec the number of microseconds to wait if a timed wait is specified.
     * @param count    the number of events desired.
     *
     * @throws EtException
     *     if arguments have bad values;
     *     if the attachment's station is GRAND_CENTRAL;
     *     if the attachment object is invalid;
     */
    public void getEvents(AttachmentLocal att, int modeVal, int microSec, int count)
                throws EtException {

        Mode mode = Mode.getMode(modeVal);

        if (att == null) {
            throw new EtException("Invalid attachment");
        }
        else if (att.getStation().getId() == 0) {
            throw new EtException("may not get events from GRAND_CENTRAL");
        }
        else if (count < 0) {
            throw new EtException("count arg negative");
        }
        else if (mode == null) {
            throw new EtException("modeVal arg invalid");
        }
        else if ((mode == Mode.TIMED) && (microSec < 0)) {
            throw new EtException("microSec arg negative");
        }

        this.attLocal = att;
        this.mode = mode;
        this.microSec = microSec;
        this.count = count;

        // We're setup to call the local getEvents() method
        method = GET_LOCAL;
        jniEvents = null;
        lastIndex = -1;
        hasEndEvent = false;
    }


    /**
     * Set this container up for putting (used) events back into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att     attachment object
     * @param offset  offset into array
     * @param length  number of array elements to put
     *
     * @throws EtException
     *     if invalid arg(s);
     *     if events are not owned by this attachment;
     */
    public void putEvents(EtAttachment att, int offset, int length)
                    throws EtException {

        // Where are our events stored?
        if (jniEvents != null) {
            putDumpEvents = jniEvents;
        }
        else {
            putDumpEvents = realEvents;
        }

        if (offset < 0 || length < 0 || offset + length > putDumpEvents.length) {
            throw new EtException("Bad offset or length argument(s)");
        }

        if (att == null || !att.isUsable()) {
            throw new EtException("Invalid attachment");
        }

        this.att = att;
        this.offset = offset;
        this.length = length;

        // We're setup to call the putEvents() method
        method = PUT;

        // May need space to hold all evs elements in EtEventImpl array
        if ((offset > 0) && (jniEvents != null) && (holdEvents.length < length)) {
            holdEvents = new EtEventImpl[length];
        }
    }


    /**
     * This method is for expert use only!
     * Set this container up for putting (used) events back into an ET system.
     * Will access local Java-based ET systems running in the same JVM.
     *
     * @param att local attachment object
     *
     * @throws EtException
     *     if invalid arg(s);
     *     if events are not owned by this attachment;
     */
    public void putEvents(AttachmentLocal att)
                    throws EtException {

        if (att == null) {
            throw new EtException("Invalid attachment");
        }

        this.attLocal = att;

        // We're setup to call the local local putEvents() method
        method = PUT_LOCAL;
    }


    /**
     * Set this container up for dumping events back into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att     attachment object
     * @param offset  offset into array
     * @param length  number of array elements to put
     *
     * @throws EtException
     *     if invalid arg(s);
     *     if events are not owned by this attachment;
     */
    public void dumpEvents(EtAttachment att, int offset, int length)
                    throws EtException {

        // Where are our events stored?
        if (jniEvents != null) {
            putDumpEvents = jniEvents;
        }
        else {
            putDumpEvents = realEvents;
        }

        if (offset < 0 || length < 0 || offset + length > putDumpEvents.length) {
            throw new EtException("Bad offset or length argument(s)");
        }

        if (att == null || !att.isUsable()) {
            throw new EtException("Invalid attachment");
        }

        this.att = att;
        this.offset = offset;
        this.length = length;

        // We're setup to call the dumpEvents() method
        method = DUMP;

        // May need space to hold all evs elements in EtEventImpl array
        if ((offset > 0) && (jniEvents != null) && (holdEvents.length < length)) {
            holdEvents = new EtEventImpl[length];
        }
    }


    /**
     * This method is for expert use only!
     * Set this container up for dumping events back into an ET system.
     * Will access local Java-based ET systems running in the same JVM.
     *
     * @param att local attachment object
     *
     * @throws EtException
     *     if invalid arg(s);
     *     if events are not owned by this attachment;
     */
    public void dumpEvents(AttachmentLocal att)
                    throws EtException {

        if (att == null) {
            throw new EtException("Invalid attachment");
        }

        this.attLocal = att;

        // We're setup to call the local dumpEvents() method
        method = DUMP_LOCAL;
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
     * This method is <b>NOT</b> for general use! Experts only!
     * Take the new events obtained in
     * {@link org.jlab.coda.et.system.SystemCreate#newEvents(EtContainer)}
     * and assign them to local storage.
     * @param list list of events which will be placed in {@link #holdEvents}.
     */
    public void holdLocalEvents(List<EtEventImpl> list) {
        eventCount = list.size();

        // I know, terribly inefficient, but caused by SystemCreate's lack of
        // offset and length args when putting and dumping events.
        if (holdEvents == null || holdEvents.length != eventCount) {
            holdEvents = new EtEventImpl[eventCount];
        }

        for (int i=0; i < eventCount; i++) {
            holdEvents[i] = list.get(i);
        }
    }


    /**
     * This method is <b>NOT</b> for general use! Experts only!
     * Take the events obtained in
     * {@link org.jlab.coda.et.system.SystemCreate#getEvents(EtContainer)}
     *  and assign them to local storage.
     * @param evs array of events which will be placed in {@link #holdEvents}.
     */
    public void holdLocalEvents(EtEventImpl[] evs) {
        eventCount = evs.length;
        holdEvents = evs;
    }


    /**
     * Get an array containing events resulting from a call to
     * {@link #newEvents(EtAttachment, Mode, int, int, int, int)} or
     * {@link #getEvents(EtAttachment, Mode, Modify, int, int)}.
     * Not all of these are valid since the returned array is a reference to a
     * large internal array. Get the number of valid events by calling
     * {@link #getEventCount()}. Don't do anything nasty to
     * the elements of this internal array.
     *
     * @return events from a newEvents() or getEvents() call; null if last call
     *         was not to either of these.
     */
    public EtEvent[] getEventArray() {
        if (method == NEW || method == GET) {
            if (jniEvents != null) {
                return jniEvents;
            }
            return realEvents;
        }
        return null;
    }

    /**
     * Get an array containing events resulting from a call to
     * {@link #newEvents(AttachmentLocal, int, int, int, int, int)} or
     * {@link #getEvents(AttachmentLocal, int, int, int)}.
     * Not all of these are valid since the returned array is a reference to a
     * large internal array. Get the number of valid events by calling
     * {@link #getEventCount()}. Don't do anything nasty to
     * the elements of this internal array.
     *
     * @return events from a LOCAL newEvents() or getEvents() call; null if last call
     *         was not to either of these.
     */
    public EtEventImpl[] getEventArrayLocal() {
        if (method == NEW_LOCAL || method == GET_LOCAL) {
            return holdEvents;
        }
        return null;
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
        if (realEvents != null) {
            for (EtEventImpl ev : realEvents) {
                ev.init();
            }
        }
    }

    //****************************************************
    // For CODA online and use in the Disruptor package.
    //****************************************************

    /**
     * Get the index of the last valid event in the array of the events being put.
     * Useful in CODA.
     * @return index of the last valid event in the array of the events being put.
     *         Equals -1 if none.
     */
    public int getLastIndex() {return lastIndex;}

    /**
     * Set the index of the last valid event in the array of the events being put.
     * Useful in CODA.
     * @param index index of the last valid event in the array of the events being put.
     *              Set to -1 if none.
     */
    public void setLastIndex(int index) {lastIndex = index;}

    /**
     * Get whether the events being put contain the END event.
     * @return true if the events being put contain the END event.
     */
    public boolean hasEndEvent() {return hasEndEvent;}

    /**
     * Set whether the events being put contain the END event.
     * @param index true if the events being put contain the END event.
     */
    public void setHasEndEvent(boolean index) {hasEndEvent = index;}

}
