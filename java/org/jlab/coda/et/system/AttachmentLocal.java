/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
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
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et.system;


/**
 * This class defines an attachment to a station of an ET system for use by the
 * ET system itself and not the user. Attachments are used to keep track of
 * event ownership and as places to conveniently keep some statistics and other
 * information on the "getting" of events. Attachments can only be created by an
 * ET system's {@link SystemCreate#attach} method.
 *
 * @author Carl Timmer
 */

class AttachmentLocal {

    // keep a list or set of events we currently have out?

    /** Unique id number. */
    private int id;

    /** Process id number for attachments written in C language. */
    private int pid;

    /** Name of the host the attachment is residing on. */
    private String host;

    /** Station the attachment is associated with. */
    private StationLocal station;

    /** Number of events put by a user into the attachment. */
    private long eventsPut;

    /** Number of events gotten by a user from the attachment. */
    private long eventsGet;

    /** Number of events dumped (recycled by returning to GRAND_CENTRAL station)
     *  by a user through the attachment. */
    private long eventsDump;

    /** Number of new events gotten by a user from the attachment. */
    private long eventsMake;

    /** Flag telling whether the attachment is blocked waiting to read events
     *  from a station that has no events.  */
    private boolean waiting;

    /**
     * Flag telling whether the attachment is currently in the sleep mode of
     * getEvents or newEvents. Since the implementation of this mode is
     * done by using the timed wait mode, it occasionally happens that the
     * attachment is told to wake up while it is not actually in getEvents or
     * newEvents. If this flag is true, the command to wake up will go ahead
     * and set "wakeUp" to true - even if "waiting" is false.
     */
    private volatile boolean sleepMode;

    /** Flag telling the attachment blocked on a read to wake up or return. */
    private volatile boolean wakeUp;


    /** Gets the attachment id number.
     *  @return attachment id number */
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }


    public boolean isWaiting() {
        return waiting;
    }
    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }
    public boolean isWakeUp() {
        return wakeUp;
    }
    public void setWakeUp(boolean wakeUp) {
        this.wakeUp = wakeUp;
    }
    
    public boolean isSleepMode() {
        return sleepMode;
    }

    public void setSleepMode(boolean sleepMode) {
        this.sleepMode = sleepMode;
    }

    public StationLocal getStation() {
        return station;
    }

    public void setStation(StationLocal station) {
        this.station = station;
    }

    public long getEventsPut() {
        return eventsPut;
    }

    public void setEventsPut(long eventsPut) {
        this.eventsPut = eventsPut;
    }

    public long getEventsGet() {
        return eventsGet;
    }

    public void setEventsGet(long eventsGet) {
        this.eventsGet = eventsGet;
    }

    public long getEventsDump() {
        return eventsDump;
    }

    public void setEventsDump(long eventsDump) {
        this.eventsDump = eventsDump;
    }

    public long getEventsMake() {
        return eventsMake;
    }

    public void setEventsMake(long eventsMake) {
        this.eventsMake = eventsMake;
    }



    /**
     * Attachments are only created by an ET system's
     * {@link SystemCreate#attach} method.
     */
    public AttachmentLocal() {
        id         = -1;
        pid        = -1;
    }

}



