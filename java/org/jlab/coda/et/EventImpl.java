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

package org.jlab.coda.et;

import java.lang.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.jlab.coda.et.exception.*;

/**
 * This class defines an ET event.
 *
 * @author Carl Timmer
 */

public class EventImpl implements Event {

    // For efficiency in initializing event headers when passing
    // through GrandCentral, define some static final variables.
    private static final int   numSelectInts = Constants.stationSelectInts;
    private static final int[] controlInitValues = new int[numSelectInts];
    private static final int   newAge = Constants.eventNew;
    private static final int   system = Constants.system;
    private static final int   high = Constants.high;
    private static final int   low = Constants.low;
    private static final int   ok = Constants.dataOk;

    /** Unique id number (place of event if C system). */
    private int id;

    /** Specifies whether the event was obtained as a new event (through
     *  {@link SystemUse#newEvents}), or as a "used" event (through  {@link SystemUse#getEvents}).
     *  If the event is new, its value is {@link Constants#eventNew} otherwise
     *  {@link Constants#eventUsed}. */
    private int age;

    /** Group to which this event belongs. Used for multiple producers. */
    private int group;

    /** Event priority which is either high {@link Constants#high} or low {@link Constants#low}. */
    private int priority;

    /** The attachment id which owns or got the event. If it's owned by the
     *  system its value is {@link Constants#system}. */
    private int owner;

    /** Length of the valid data in bytes. */
    private int length;

    /** Size of the data buffer in bytes. */
    private int memSize;

    /** Size limit of events' data buffers in bytes. This is important to
     *  know when Java users connect to C-based ET systems. The C-based ET
     *  systems cannot allow users to increase an event's data size beyond
     *  what was originally allocated. In Java systems there is no size
     *  limit besides computer and JVM limits.
     */
    private int sizeLimit;

    /** Status of the data. It can be ok {@link Constants#dataOk}, corrupted
     *  {@link Constants#dataCorrupt}, or possibly corrupted
     *  {@link Constants#dataPossiblyCorrupt}. */
    private int dataStatus;

    /** An integer used to keep track of the data's byte ordering. */
    private int byteOrder;

    /** Specifies whether the user wants to read the event only, will modify
     *  only the event header, or will modify the data. */
    private int modify;

    /** An array of integers normally used by stations to filter events out of
     *  their input lists. It is used to control the flow of events through
     *  the ET system. */
    private int[] control;

    /** The event data is stored here. */
    private byte[] data;

    /** Store data in a ByteBuffer object for maximum flexibility. */
    private ByteBuffer dataBuffer;
    
    /** Flag specifying whether the ET system process is Java based or not. */
    private boolean isJava;


    
    /** Creates an event object for users of Java-based ET systems or by the
     *  system itself. Event objects are only created once in the ET
     *  system process - when the ET system is started up.
     *  @param size size of the data array in bytes
     */
    public EventImpl(int size) {
        memSize    = size;
        isJava     = true;
        data       = new byte[size];
        control    = new int[numSelectInts];
        dataBuffer = ByteBuffer.wrap(data);
        init();
    }

    /**
     * Creates an event object for ET system users when connecting to C-based ET systems.
     *
     *  @param size size of the data array in bytes.
     *  @param limit limit on the size of the data array in bytes. Only used
     *         for C-based ET systems.
     *  @param isJavaSystem is ET system Java based?
     */
    EventImpl(int size, int limit, boolean isJavaSystem) {
        memSize    = size;
        sizeLimit  = limit;
        isJava     = isJavaSystem;
        data       = new byte[size];
        control    = new int[numSelectInts];
        dataBuffer = ByteBuffer.wrap(data);
        init();
    }

    /**
     * Creates an event object for ET system users when connecting to C-based ET systems
     * and using native methods. No data array or buffer are created since we will be using shared
     * memory and it will be taken care of later. Tons of args since it's a lot easier in
     * JNI to call one method with lots of args then to call lots of set methods on one object.
     *
     *  @param size size of the data array in bytes.
     *  @param limit limit on the size of the data array in bytes. Only used
     *         for C-based ET systems.
     */
    EventImpl(int size, int limit, int status, int id, int age, int owner,
              int modify, int length, int priority, int byteOrder, int[] control) {

        isJava         = false;
        memSize        = size;
        sizeLimit      = limit;
        dataStatus     = status;
        this.id        = id;
        this.age       = age;
        this.owner     = owner;
        this.modify    = modify;
        this.length    = length;
        this.priority  = priority;
        this.byteOrder = byteOrder;
        this.control   = control.clone();
    }

    /** Initialize an event's fields. Called for an event each time it passes
     *  through GRAND_CENTRAL station. */
    public void init() {
        age        = newAge;
        priority   = low;
        owner      = system;
        length     = 0;
        modify     = 0;
        byteOrder  = 0x04030201;
        dataStatus = ok;
        System.arraycopy(controlInitValues, 0, control, 0, numSelectInts);
    }

    // public gets

    /** Gets the event's id number.
     *  @return event's id number */
    public int    getId()   {return id;}
    /** Gets the age of the event, either {@link Constants#eventNew} if a new event or
     *  {@link Constants#eventUsed} otherwise.
     *  @return age of the event. */
    public int    getAge()        {return age;}
    /** Gets the group the event belongs to.
     *  @return the group the event belongs to. */
    public int    getGroup()      {return group;}
    /** Gets the event's priority.
     *  @return event's priority */
    public int    getPriority()   {return priority;}
    /** Gets the length of the data in bytes.
     *  @return length of the data in bytes */
    public int    getLength()     {return length;}
    /** Gets the size of the data buffer in bytes.
     *  @return size of the data buffer in bytes */
    public int    getMemSize()    {return memSize;}
    /** Gets the size limit of the data buffer in bytes when using a
     *  C-based ET system.
     *  @return size size limit of the data buffer in bytes */
    public int    getSizeLimit()   {return sizeLimit;}
    /** Gets the status of the data.
     *  @return status of the data */
    public int    getDataStatus() {return dataStatus;}
    /** Gets the event's modify value.
     *  @return event's modify value */
    public int    getModify()     {return modify;}
    /** Gets the event's control array.
     *  @return event's control array */
    public int[]  getControl()    {return control.clone();}
    /** Gets the event's data array.
     *  @return event's data array */
    public byte[] getData()       {return data;}

    public ByteBuffer getDataBuffer() {
        return dataBuffer;
    }


    /** Gets the event's data array.
     *  @return a clone of the event's data array */
    public byte[] copyData()      {return data.clone();}


    /**
     *
     * @return
     */
    public int    getOwner() {return owner;}


    // public sets

    // TODO: error checking
    public void setModify(int modify) {
        this.modify = modify;
    }
    public void setMemSize(int memSize) {
        this.memSize = memSize;
    }
    public void setId(int id) {
        this.id = id;
    }
    public void setGroup(int group) {
        this.group = group;
    }
    public void setOwner(int owner) {
        this.owner = owner;
    }
    public void setAge(int age) {
        this.age = age;
    }
    public void setDataBuffer(ByteBuffer dataBuffer) {
        this.dataBuffer = dataBuffer;
    }






    /** Sets the event's data without copying. The length and memSize members of
   *  the event are automatically set to the data array's length.
   *  @param dat data array
   */
  public void setData(byte[] dat) throws EtException {
    if (!isJava) {
      // In C-based ET systems, user cannot increase data size beyond
      // what was initially allocated.
      if (dat.length > sizeLimit) {
        throw new EtException("data array is too big, limit is "
				+ sizeLimit + " bytes");
      }
    }
    data    = dat;
    length  = dat.length;
    memSize = dat.length;
  }

  /** Set the event's data by copying it in. The event's length member
   *  is set to the length of the argument array.
   *  @param dat data array
   *  @exception EtException
   *     if the data array is the wrong size
   */
  public void copyDataIn(byte[] dat) throws EtException {
    if (dat.length > memSize) {
      throw new EtException("data array is too big, limit is "
				+ memSize + " bytes");
    }
    System.arraycopy(dat, 0, data, 0, dat.length);
    length = dat.length;
  }

  /** Set the event's data by copying it in. The event's length member
   *  is not changed.
   *  @param dat data array
   *  @param srcOff offset in "dat" byte array
   *  @param destOff offset in the event's byte array
   *  @param len bytes of data to copy
   *  @exception EtException
   *     if the data array is the wrong size
   */
  public void copyDataIn(byte[] dat, int srcOff, int destOff, int len) throws EtException {
    if (len > data.length) {
      throw new EtException("too much data, try using \"setData\" method");
    }
    System.arraycopy(dat, srcOff, data, destOff, len);
  }

  /** Sets the event's priority.
   *  @param pri event priority
   *  @exception EtException
   *     if argument is a bad value
   */
  public void setPriority(int pri) throws EtException {
    if (pri != low && pri != high) {
      throw new EtException("bad value for event priority");
    }
    priority = pri;
  }

  /** Sets the event's data length in bytes.
   *  @param len data length
   *  @exception EtException
   *     if length is less than zero
   */
  public void setLength(int len) throws EtException {
    if (len < 0) {
      throw new EtException("bad value for event data length");
    }
    length = len;
  }

  /** Sets the event's control array by copying it in.
   *  @param con control array
   *  @exception EtException
   *     if control array has the wrong number of elements
   */
  public void setControl(int[] con) throws EtException {
    if (con.length != numSelectInts) {
      throw new EtException("wrong number of elements in control array");
    }
    System.arraycopy(con, 0, control, 0, numSelectInts);
  }

  /** Sets the event's data status.
   *  @param status data status
   *  @exception EtException
   *     if argument is a bad value
   */
  public void setDataStatus(int status) throws EtException {
      if ((status != Constants.dataOk) &&
          (status != Constants.dataCorrupt) &&
          (status != Constants.dataPossiblyCorrupt)) {
          throw new EtException("bad value for data status");
      }
      dataStatus = status;
  }

    // byte order stuff

    /** Gets the event's byte order - either {@link Constants#endianBig} or
     *  {@link Constants#endianLittle}.
     *  @return event's byte order */
    public int getByteOrder() {
        // java is always big endian
        return ((byteOrder == 0x04030201) ? Constants.endianBig : Constants.endianLittle);
    }

    /** Set the event's byte order. Values can be {@link Constants#endianBig},
     *  {@link Constants#endianLittle}, {@link Constants#endianLocal},
     *  {@link Constants#endianNotLocal}, or {@link Constants#endianSwitch}
     *  @param endian endian value
     *  @exception EtException
     *     if argument is a bad value
     */
    public void setByteOrder(int endian) throws EtException {
        if (endian == Constants.endianBig) {
            byteOrder = 0x04030201;
        }
        else if (endian == Constants.endianLittle) {
            byteOrder = 0x01020304;
        }
        else if (endian == Constants.endianLocal) {
            byteOrder = 0x04030201;
        }
        else if (endian == Constants.endianNotLocal) {
            byteOrder = 0x01020304;
        }
        else if (endian == Constants.endianSwitch) {
            byteOrder = Integer.reverseBytes(byteOrder);
        }
        else {
            throw new EtException("bad value for byte order");
        }
        return;
    }

    /** Tells caller if the event data needs to be swapped in order to be the
     *  correct byte order.
     *  @return <code>true</code> if swapping is needed, otherwise <code>false</code>
     *  @exception EtException
     *     if the byte order has a bad value
     */
    public boolean needToSwap() throws EtException {
        if (byteOrder == 0x04030201)
            return false;
        else if (byteOrder == 0x01020304) {
            return true;
        }
        throw new EtException("byteOrder member has bad value");
    }

}
