package org.jlab.coda.et;

import org.jlab.coda.et.exception.*;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;


/**
 * This class handles all calls to native methods which, in turn,
 * makes calls to the C ET library routines to get, new, put, and dump events.
 */
class EtJniAccess {

    /** Serialize access to classMap and creation of these objects. */
    static ReentrantLock classLock = new ReentrantLock();

    /** Store EtJniAccess objects here since we only want to create 1 object per ET system. */
    static HashMap<String, EtJniAccess> classMap = new HashMap<String, EtJniAccess>(10);

    /** Has the jni library been loaded? */
    static boolean jniLibLoaded;


    /**
     * Get an instance of this object for a particular ET system. Only one EtJniAccess object
     * is created for a particular ET system.
     *
     * @param etName name of ET system to open
     * @return object of this type to use for interaction with local, C-based ET system
     * @throws EtException        for failure to load the jni library;
     *                            for any failure to open ET system except timeout
     * @throws EtTimeoutException for failure to open ET system within the specified time limit
     */
    static EtJniAccess getInstance(String etName) throws EtException, EtTimeoutException {
        try {

            classLock.lock();

            // Only want to do this once
            if (!jniLibLoaded) {
                try {
                    System.loadLibrary("et_jni");
                }
                catch (Error e) {
                    // If the library cannot be found, we can still
                    // continue to use the ET system with sockets -
                    // just not locally with the C library.
//System.out.println("\nERROR LOADIN JNI LIB !!!!\n");
                    throw new EtException("Error loading libet_jni.so");
                }
            }

            jniLibLoaded = true;

            // See if we've already opened the ET system being asked for, if so, return that
            if (classMap.containsKey(etName)) {
//System.out.println("USE ALREADY EXISTING ET JNI OBJECT for et -> " + etName);
                EtJniAccess jni = classMap.get(etName);
                jni.numberOpens++;
//System.out.println("numberOpens = " + jni.numberOpens);
                return jni;
            }

            EtJniAccess jni = new EtJniAccess();
            jni.openLocalEtSystem(etName);
            jni.etSystemName = etName;
            jni.numberOpens = 1;
//System.out.println("CREATING ET JNI OBJECT for et -> " + etName);
//System.out.println("numberOpens = " + jni.numberOpens);
            classMap.put(etName, jni);

            return jni;
        }
        finally {
            classLock.unlock();
        }
    }

    private int numberOpens;

    /** Place to store id (pointer) returned from et_open in C code. */
    private long localEtId;

    /** Store the name of the ET system. */
    private String etSystemName;

    /**
     * Create EtJniAccess objects with the {@link #getInstance(String)} method.
     */
    private EtJniAccess() {}


    /**
     * Get the et id.
     * @return et id
     */
    long getLocalEtId() {
        return localEtId;
    }


    /**
     * Set the et id. Used inside native method {@link #openLocalEtSystem(String)}.
     * @param id et id
     */
    private void setLocalEtId(long id) {
        localEtId = id;
    }


    /**
     * Open a local, C-based ET system and store it's id in {@link #localEtId}.
     * This only needs to be done once per local system even though many connections
     * to the ET server may be desired.
     *
     * @param etName name of ET system to open
     *
     * @throws EtException        for any failure to open ET system except timeout
     * @throws EtTimeoutException for failure to open ET system within the specified time limit
     */
    private native void openLocalEtSystem(String etName)
            throws EtException, EtTimeoutException;


    /**
     * Close the local, C-based ET system that we previously opened.
     */
    void close() {
        try {
            classLock.lock();
            numberOpens--;
//System.out.println("close: numberOpens = " + numberOpens);
            if (numberOpens < 1) {
                classMap.remove(etSystemName);
//System.out.println("close: really close local ET system");
                closeLocalEtSystem(localEtId);
            }
        }
        finally {
            classLock.unlock();
        }
    }


    /**
     * Close the local, C-based ET system that we previously opened.
     *
     * @param etId ET system id
     */
    private native void closeLocalEtSystem(long etId);


    /**
     * Kill the local, C-based ET system that we previously opened.
     *
     * @param etId ET system id
     */
    native void killEtSystem(long etId);


    /**
     * Put the given array of events back into the local, C-based ET system.
     *
     * @param etId   ET system id
     * @param attId  attachment id
     * @param evs    array of events
     * @param length number of events to be put (starting at index of 0)
     * 
     * @throws EtException for variety of general errors
     * @throws EtDeadException if ET system is dead
     * @throws EtClosedException if ET system is closed
     */
    native void putEvents(long etId, int attId, EtEventImpl[] evs, int length)
            throws EtException, EtDeadException, EtClosedException;


    /**
     * Dump (dispose of) the given array of unwanted events back into the local, C-based ET system.
     *
     * @param etId   ET system id
     * @param attId  attachment id
     * @param evs    array of event objects
     * @param length number of events to be dumped (starting at index of 0)
     *
     * @throws EtException for variety of general errors
     * @throws EtDeadException if ET system is dead
     * @throws EtClosedException if ET system is closed
     */
    native void dumpEvents(long etId, int attId, EtEventImpl[] evs, int length)
            throws EtException, EtDeadException, EtClosedException;


    /**
     * Get events from the local, C-based ET system.
     *
     * @param etId   ET system id
     * @param attId  attachment id
     * @param mode   if there are no events available, this parameter specifies
     *               whether to wait for some by sleeping {@link EtConstants#sleep},
     *               to wait for a set time {@link EtConstants#timed},
     *               or to return immediately {@link EtConstants#async}.
     * @param sec    the number of seconds to wait if a timed wait is specified
     * @param nsec   the number of nanoseconds to wait if a timed wait is specified
     * @param count  number of events desired. Size may be different from that requested.
     *
     * @return array of events obtained from ET system. Count may be different from that requested.
     *
     * @throws EtException
     *     if general errors
     * @throws EtDeadException
     *     if the ET system process is dead
     * @throws EtClosedException
     *     if ET system is closed
     * @throws EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @throws EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @throws EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @throws EtWakeUpException
     *     if the attachment has been commanded to wakeup
     */
    native EtEventImpl[] getEvents(long etId, int attId, int mode, int sec, int nsec, int count)
            throws EtException, EtDeadException, EtClosedException, EtEmptyException,
                   EtBusyException, EtTimeoutException, EtWakeUpException;


    /**
     * Get new (unused) events from the local, C-based ET system.
     *
     * @param etId   ET system id
     * @param attId  attachment id
     * @param mode   if there are no new events available, this parameter specifies
     *               whether to wait for some by sleeping {@link EtConstants#sleep},
     *               to wait for a set time {@link EtConstants#timed},
     *               or to return immediately {@link EtConstants#async}.
     * @param sec    the number of seconds to wait if a timed wait is specified
     * @param nsec   the number of nanoseconds to wait if a timed wait is specified
     * @param count  number of events desired
     * @param size   the size in bytes of the events desired
     *
     * @return array of unused events obtained from ET system. Count may be different from that requested.
     *
     * @throws EtException
     *     if general errors
     * @throws EtDeadException
     *     if the ET system process is dead
     * @throws EtClosedException
     *     if ET system is closed
     * @throws EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @throws EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @throws EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @throws EtWakeUpException
     *     if the attachment has been commanded to wakeup
     */
    native EtEventImpl[] newEvents(long etId, int attId, int mode, int sec, int nsec, int count, int size)
            throws EtException, EtDeadException, EtClosedException, EtEmptyException,
                   EtBusyException, EtTimeoutException, EtWakeUpException;


    /**
     * Get new (unused) events from a specified group of such events from the local, C-based ET system.
     *
     * @param etId   ET system id
     * @param attId  attachment id
     * @param mode   if there are no new events available, this parameter specifies
     *               whether to wait for some by sleeping {@link EtConstants#sleep},
     *               to wait for a set time {@link EtConstants#timed},
     *               or to return immediately {@link EtConstants#async}.
     * @param sec    the number of seconds to wait if a timed wait is specified
     * @param nsec   the number of nanoseconds to wait if a timed wait is specified
     * @param count  number of events desired
     * @param size   the size in bytes of the events desired
     * @param group  group number from which to draw new events. Some ET systems have
     *               unused events divided into groups whose numbering starts at 1.
     *               For ET system not so divided, all events belong to group 1.
     *
     * @return array of unused events obtained from ET system. Count may be different from that requested.
     *
     * @throws EtException
     *     if general errors
     * @throws EtDeadException
     *     if the ET system process is dead
     * @throws EtClosedException
     *     if ET system is closed
     * @throws EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @throws EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @throws EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @throws EtWakeUpException
     *     if the attachment has been commanded to wakeup
     */
    native EtEventImpl[] newEvents(long etId, int attId, int mode, int sec, int nsec, int count, int size, int group)
            throws EtException, EtDeadException, EtClosedException, EtEmptyException,
            EtBusyException, EtTimeoutException, EtWakeUpException;


    /**
     * Creates a new station at a specified position in the ordered list of
     * stations and in a specified position in an ordered list of parallel
     * stations if it is a parallel station.
     *
     * @param etId       ET system id
     * @param config     station configuration
     * @param name       station name
     * @param position   position in the main list to put the station.
     * @param parallelPosition   position in the list of parallel
     *                           stations to put the station.
     *
     * @return station id
     *
     * @throws EtDeadException
     *     if the ET system processes are dead
     * @throws EtClosedException
     *     if the ET system is closed
     * @throws EtException
     *     if arg is null;
     *     if the select method's class cannot be loaded;
     *     if the position is less than 1 (GRAND_CENTRAL's spot);
     *     if the name is GRAND_CENTRAL (already taken);
     *     if the name is too long;
     *     if the configuration's cue size is too big;
     *     if the configuration needs a select class name;
     *     if the configuration inconsistent
     *     if trying to add incompatible parallel station;
     *     if trying to add parallel station to head of existing parallel group;
     * @throws EtExistsException
     *     if the station already exists but with a different configuration
     * @throws EtTooManyException
     *     if the maximum number of stations has been created already
     */
    native int createStation(long etId, EtStationConfig config, String name, int position, int parallelPosition)
            throws EtDeadException, EtClosedException, EtException,
                   EtExistsException, EtTooManyException;


    /**
     * Gets a station's object representation from its name.
     *
     * @param etId ET system id
     * @param name station name
     * @return station id or -1 if no such station exists
     *
     * @throws EtDeadException
     *     if the ET system processes are dead
     * @throws EtClosedException
     *     if the ET system is closed
     * @throws EtException
     *     if arg is null;
     */
    native int stationNameToObject(long etId, String name)
            throws EtDeadException, EtClosedException, EtException;



    /**
     * Create an attachment to a station.
     *
     * @param etId       ET system id
     * @param stationId  station id
     * @return an attachment id
     *
     * @throws EtDeadException
     *     if the ET system processes are dead
     * @throws EtClosedException
     *     if the ET system is closed
     * @throws EtException
     *     if arg is null;
     *     if the station does not exist or is not in active/idle state;
     *     if station object is invalid
     * @throws EtTooManyException
     *     if no more attachments are allowed to the station and/or ET system
     */
    native int attach(long etId, int stationId)
            throws EtDeadException, EtClosedException, EtException, EtTooManyException;



    /**
     * Remove an attachment from a station.
     *
     * @param etId  ET system id
     * @param attId attachment id
     *
     * @throws EtDeadException
     *     if the ET system processes are dead
     * @throws EtClosedException
     *     if the ET system is closed
     * @throws EtException
     *     if arg is null;
     *     if not attached to station;
     *     if the attachment object is invalid
     */
    native void detach(long etId, int attId)
            throws EtDeadException, EtClosedException, EtException;


    /**
     * Removes an existing station.
     *
     * @param etId    ET system id
     * @param stationId station id
     * @throws EtDeadException   if the ET system processes are dead
     * @throws EtClosedException if the ET system is closed
     * @throws EtException       if arg is null;
     *                           if attachments to the station still exist;
     *                           if the station is GRAND_CENTRAL (which must always exist);
     *                           if the station does not exist;
     *                           if the station object is invalid
     */
    native void removeStation(long etId, int stationId)
            throws EtDeadException, EtClosedException, EtException;


    /**
     * Changes the position of a station in the ordered list of stations.
     *
     * @param etId    ET system id
     * @param stationId station id
     * @param position         position in the main station list (starting at 0)
     * @param parallelPosition position in list of parallel stations (starting at 0)
     *
     * @throws EtDeadException   if the ET system processes are dead
     * @throws EtClosedException if the ET system is closed
     * @throws EtException       if arg is null;
     *                           if the station does not exist;
     *                           if trying to move GRAND_CENTRAL;
     *                           if position is &lt; 1 (GRAND_CENTRAL is always first);
     *                           if parallelPosition &lt; 0;
     *                           if station object is invalid;
     *                           if trying to move an incompatible parallel station to an existing group
     *                           of parallel stations or to the head of an existing group of parallel
     *                           stations.
     */
    native void setStationPosition(long etId, int stationId, int position, int parallelPosition)
            throws EtDeadException, EtClosedException, EtException;


    /**
     * Wake up an attachment that is waiting to read events from a station's empty input list.
     *
     * @param etId   ET system id
     * @param attId  attachment id
     *
     * @throws EtException       if arg is null;
     *                           if the attachment object is invalid
     * @throws EtClosedException if the ET system is closed
     */
    native void wakeUpAttachment(long etId, int attId)
            throws EtException, EtClosedException;


    /**
     * Is the ET system alive and are we connected to it?
     *
     * @param etId    ET system id
     * @return <code>1</code> if the ET system is alive and we're connected to it,
     * otherwise  <code>0</code>
     */
    native int alive(long etId);


}
