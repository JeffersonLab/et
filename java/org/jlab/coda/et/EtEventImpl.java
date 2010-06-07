/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et;

import java.lang.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jlab.coda.et.exception.*;
import org.jlab.coda.et.enums.Age;
import org.jlab.coda.et.enums.Priority;
import org.jlab.coda.et.enums.DataStatus;
import org.jlab.coda.et.enums.Modify;

/**
 * This class defines an ET event.
 *
 * @author Carl Timmer
 */

public class EtEventImpl implements EtEvent {

    // convenience variables
    private static final int   numSelectInts = EtConstants.stationSelectInts;
    private static final int[] controlInitValues = new int[numSelectInts];

    /** Unique id number (place of event in C-based ET system). */
    private int id;

    /**
     * Specifies whether the event was obtained as a new event (through
     * {@link EtSystemUse#newEvents}), or as a "used" event (through {@link EtSystemUse#getEvents}).
     * If the event is new, its value is {@link Age#NEW} otherwise {@link Age#USED}.
     */
    private Age age;

    /** Group to which this event belongs (1, 2, ...) if ET system events are divided into groups.
     *  If not, group = 1. Used so some producers don't hog events from others. */
    private int group;

    /** Event priority which is either {@link Priority#HIGH} or {@link Priority#LOW}. */
    private Priority priority;

    /**
     * The attachment id which owns or got the event. If it's owned by the
     * system its value is {@link EtConstants#system}.
     */
    private int owner;

    /** Length of the valid data in bytes. */
    private int length;

    /** Size of the data buffer in bytes. */
    private int memSize;

    /**
     * Size limit of events' data buffers in bytes. This is important to
     * know when Java users connect to C-based ET systems. The C-based ET
     * systems cannot allow users to increase an event's data size beyond
     * what was originally allocated. In Java systems there is no size
     * limit besides computer and JVM limits.
     */
    private int sizeLimit;

    /**
     * Status of the data. It can be ok {@link DataStatus#OK}, corrupted
     * {@link DataStatus#CORRUPT}, or possibly corrupted
     * {@link DataStatus#POSSIBLYCORRUPT}.
     */
    private DataStatus dataStatus;

    /**
     * Specifies whether the user wants to read the event only, will modify only the event header
     * (everything except the data), or will modify the data and/or header.
     * Modifying the data and/or header is {@link Modify#ANYTHING}, modifying only the header
     * is {@link Modify#HEADER}, else the default assumed, {@link Modify#NOTHING},
     * is that nothing is modified resulting in this event being put back into
     * the ET system (by remote server) immediately upon being copied and that copy
     * sent to the user.
     */
    private Modify modify;

    /**
     * An integer used to keep track of the data's byte ordering.
     * Values can be 0x04030201 (local endian) or 0x01020304 (not local endian).
     */
    private int byteOrder;

    /**
     * An array of integers normally used by stations to filter events out of
     * their input lists. It is used to control the flow of events through
     * the ET system.
     */
    private int[] control;

    /**
     * This byte array backs the dataBuffer when receiving events from a Java-based
     * ET system or from over the network. If connected to a local, C-based ET system,
     * a MappedByteBuffer is used which has <b>no</b> backing array.
     */
    private byte[] data;

    /** This ByteBuffer object is a wrapper for the data byte array for convenience. */
    private ByteBuffer dataBuffer;

    /** Flag specifying whether the ET system process is Java based or not. */
    private boolean isJava;


    
    /**
     * Creates an event object for users of Java-based ET systems or by the
     * system itself. Event objects are only created once in the ET
     * system process - when the ET system is started up.
     *
     * @param size size of the data array in bytes
     */
    public EtEventImpl(int size) {
        memSize    = size;
        isJava     = true;
        data       = new byte[size];
        control    = new int[numSelectInts];
        dataBuffer = ByteBuffer.wrap(data);
        init();
    }

    /**
     * Creates an event object for ET system users when connecting to ET systems
     * over the network. Called by
     * {@link EtSystemUse#getEvents(EtAttachment , org.jlab.coda.et.enums.Mode, org.jlab.coda.et.enums.Modify, int, int)},
     * and
     * {@link EtSystemUse#newEvents(EtAttachment , org.jlab.coda.et.enums.Mode, int, int, int, int)}.
     *
     * @param size   size of the data array in bytes.
     * @param limit  limit on the size of the data array in bytes. Only used
     *               for C-based ET systems.
     * @param isJava is ET system Java based?
     */
    EtEventImpl(int size, int limit, boolean isJava) {
        memSize     = size;
        sizeLimit   = limit;
        this.isJava = isJava;
        data        = new byte[size];
        control     = new int[numSelectInts];
        dataBuffer  = ByteBuffer.wrap(data);
        init();
    }

    /**
     * Creates an event object for ET system users when connecting to local, C-based ET systems
     * and using native methods to call et_events_get.
     * No data array or buffer are created since we will be using shared
     * memory and it will be taken care of later. Tons of args since it's a lot easier in
     * JNI to call one method with lots of args then to call lots of set methods on one object.
     *
     * @param size      {@link #memSize}
     * @param limit     {@link #sizeLimit}
     * @param status    {@link #dataStatus}
     * @param id        {@link #id}
     * @param age       {@link #age}
     * @param owner     {@link #owner}
     * @param modify    {@link #modify}
     * @param length    {@link #length}
     * @param priority  {@link #modify}
     * @param byteOrder {@link #byteOrder}
     * @param control   {@link #control}
     */
    EtEventImpl(int size, int limit, int status, int id, int age, int owner,
              int modify, int length, int priority, int byteOrder, int[] control) {

        isJava         = false;
        memSize        = size;
        sizeLimit      = limit;
        dataStatus     = DataStatus.getStatus(status);
        this.id        = id;
        this.age       = Age.getAge(age);
        this.owner     = owner;
        this.modify    = Modify.getModify(modify);
        this.length    = length;
        this.priority  = Priority.getPriority(priority);
        this.byteOrder = byteOrder;
        this.control   = control.clone();
    }

    /**
     * Creates an event object for ET system users when connecting to local, C-based ET systems
     * and using native methods to call et_events_new_group.
     * No data array or buffer are created since we will be using shared
     * memory and it will be taken care of later.
     *
     * @param limit {@link #sizeLimit}, {@link #memSize}
     * @param id    {@link #id}
     * @param owner {@link #owner}
     */
    EtEventImpl(int limit, int id, int owner) {

        age        = Age.NEW;
        priority   = Priority.LOW;
        isJava     = false;
        byteOrder  = 0x04030201;
        length     = 0;
        modify     = Modify.NOTHING;
        dataStatus = DataStatus.OK;
        control    = new int[numSelectInts];

        memSize    = limit;
        sizeLimit  = limit;
        this.id    = id;
        this.owner = owner;
    }

    /** Initialize an event's fields. Called for an event each time it passes
     *  through GRAND_CENTRAL station. */
    public void init() {
        age        = Age.NEW;
        priority   = Priority.LOW;
        owner      = EtConstants.system;
        length     = 0;
        modify     = Modify.NOTHING;
        byteOrder  = 0x04030201;
        dataStatus = DataStatus.OK;
        System.arraycopy(controlInitValues, 0, control, 0, numSelectInts);
    }


    // getters

    
    /**
     * {@inheritDoc}
     */
    public int getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    public Age getAge() {
        return age;
    }

    /**
     * {@inheritDoc}
     */
    public int getGroup() {
        return group;
    }

    /**
     * {@inheritDoc}
     */
    public Priority getPriority() {
        return priority;
    }

    /**
     * Get int value associated with Priority enum.
     * @return int value associated with Priority enum
     */
    public int getPriorityValue() {
        return priority.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public int getOwner() {
        return owner;
    }

    /**
     * {@inheritDoc}
     */
    public int getLength() {
        return length;
    }

    /**
     * Gets the size of the data buffer in bytes.
     * @return size of the data buffer in bytes
     */
    public int getMemSize() {
        return memSize;
    }

    /**
     * Gets the size limit of the data buffer in bytes when using a C-based ET system.
     * @return size size limit of the data buffer in bytes
     */
    public int getSizeLimit() {
        return sizeLimit;
    }

    /**
     * {@inheritDoc}
     */
    public DataStatus getDataStatus() {
        return dataStatus;
    }

    /**
     * Get int value associated with DataStatus enum.
     * @return int value associated with DataStatus enum
     */
    public int getDataStatusValue() {
        return dataStatus.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public Modify getModify() {
        return modify;
    }

    /**
     * {@inheritDoc}
     */
    public ByteOrder getByteOrder() {
        // java is always big endian
        return ((byteOrder == 0x04030201) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    public int[] getControl() {
        return control.clone();
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getData() throws UnsupportedOperationException {
        return dataBuffer.array();
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer getDataBuffer() {
        return dataBuffer;
    }


    // setters


    /**
     * Sets the event's id number.
     * @param id event's id number
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Sets the age of the event which is {@link Age#NEW} for new events obtained by calling
     * {@link EtSystemUse#newEvents}), or {@link Age#NEW} for "used" event obtained by calling
     * {@link EtSystemUse#getEvents}).
     *
     * @param age age of the event
     */
    public void setAge(Age age) {
        this.age = age;
    }

    /**
     * Sets the group the event belongs to: (1, 2, ...) if ET system events are divided into groups,
     * or group = 1 if not.
     *
     * @param group group the event belongs to
     */
    public void setGroup(int group) {
        this.group = group;
    }

    /**
     * {@inheritDoc}
     */
    public void setPriority(Priority pri) {
        priority = pri;
    }

    /**
     * Sets the owner of the event (attachment using event or system).
     * @param owner owner of event (attachment using event or system)
     */
    public void setOwner(int owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    public void setLength(int len) throws EtException {
        if (len < 0 || len > sizeLimit) {
            throw new EtException("bad value for event data length");
        }
        length = len;
    }

    /**
     * Set the length of valid data from server where sizeLimit may be 0.
     * @param len  length of valid data
     * @throws EtException if len is negative
     */
    public void setLengthFromServer(int len) throws EtException {
        if (len < 0) {
            throw new EtException("bad value for event data length");
        }
        length = len;
    }

    /**
     * Sets the size of the data buffer in bytes.
     * @param memSize size of the data buffer in bytes
     */
    public void setMemSize(int memSize) {
        this.memSize = memSize;
    }

    /**
     * Sets the event's data status. It can be ok {@link DataStatus#OK} which is the default,
     * corrupted {@link DataStatus#CORRUPT} which is never used actually, or possibly corrupted
     * {@link DataStatus#POSSIBLYCORRUPT} which occurs when a process holding the event crashes
     * and the system recovers it.
     *
     * @param status data status
     */
    public void setDataStatus(DataStatus status) {
        dataStatus = status;
    }

    /**
     * Sets whether the user wants to read the event only, will modify only the event header
     * (everything except the data), or will modify the data and/or header.
     * Modifying the data and/or header is {@link Modify#ANYTHING}, modifying only the header
     * is {@link Modify#HEADER}, else the default assumed, {@link Modify#NOTHING},
     * is that nothing is modified resulting in this event being put back into
     * the ET system (by remote server) immediately upon being copied and that copy
     * sent to the user.
     * 
     * @param modify
     */
    public void setModify(Modify modify) {
        this.modify = modify;
    }

    /**
     * {@inheritDoc}
     */
    public void setByteOrder(int endian) throws EtException {
        if (endian == EtConstants.endianBig) {
            byteOrder = 0x04030201;
        }
        else if (endian == EtConstants.endianLittle) {
            byteOrder = 0x01020304;
        }
        else if (endian == EtConstants.endianLocal) {
            byteOrder = 0x04030201;
        }
        else if (endian == EtConstants.endianNotLocal) {
            byteOrder = 0x01020304;
        }
        else if (endian == EtConstants.endianSwitch) {
            byteOrder = Integer.reverseBytes(byteOrder);
        }
        else {
            throw new EtException("bad value for byte order");
        }
        return;
    }

    /**
     * {@inheritDoc}
     */
    public void setByteOrder(ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            byteOrder = 0x04030201;
        }
        else {
            byteOrder = 0x01020304;
        }
        return;
    }

    /**
     * {@inheritDoc}
     */
    public void setControl(int[] con) throws EtException {
        if (con.length != numSelectInts) {
            throw new EtException("wrong number of elements in control array");
        }
        System.arraycopy(con, 0, control, 0, numSelectInts);
    }

    /**
     * Sets the event's data without copying. The length and memSize members of
     * the event are automatically set to the data array's length.
     * Used only by local Java ET system in newEvents to increase data array size.
     *
     * @param data data array
     */
    public void setData(byte[] data) {
        // In C-based ET systems, user cannot increase data size beyond
        // what was initially allocated, but this is only used by local Java ET system.
        this.data = data;
        length    = data.length;
        memSize   = data.length;
    }

    /**
     * Sets the event's data buffer (backed by data array).
     * @param dataBuffer event's data buffer
     */
    public void setDataBuffer(ByteBuffer dataBuffer) {
        this.dataBuffer = dataBuffer;
    }


    // miscellaneous


    /**
     * {@inheritDoc}
     */
    public boolean needToSwap() {
        return byteOrder != 0x04030201;
    }

}
