package org.jlab.coda.et;

import org.jlab.coda.et.exception.EtException;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * User: timmer
 * Date: Apr 8, 2010
 * Time: 10:32:30 AM
 * To change this template use File | Settings | File Templates.
 */
public interface Event {
    /** Initialize an event's fields. Called for an event each time it passes
     *  through GRAND_CENTRAL station. */
    void init();

    /** Gets the event's id number.
     *  @return event's id number */
    int   getId();

    /** Gets the age of the event, either {@link org.jlab.coda.et.Constants#eventNew} if a new event or
     *  {@link org.jlab.coda.et.Constants#eventUsed} otherwise.
     *  @return age of the event. */
    int    getAge();

    /** Gets the group the event belongs to.
     *  @return the group the event belongs to. */
    int    getGroup();

    /** Gets the event's priority.
     *  @return event's priority */
    int    getPriority();

    /** Gets the length of the data in bytes.
     *  @return length of the data in bytes */
    int    getLength();

    /** Gets the size of the data buffer in bytes.
     *  @return size of the data buffer in bytes */
    int    getMemSize();

    /** Gets the size limit of the data buffer in bytes when using a
     *  C-based ET system.
     *  @return size size limit of the data buffer in bytes */
    int    getSizeLimit();

    /** Gets the status of the data.
     *  @return status of the data */
    int    getDataStatus();

    /** Gets the event's modify value.
     *  @return event's modify value */
    int    getModify();

    /** Gets the event's control array.
     *  @return event's control array */
    int[]  getControl();

    /** Gets the event's data array.
     *  @return event's data array */
    byte[] getData();

    /** Gets the event's data buffer.
     *  @return event's data buffer */
    ByteBuffer getDataBuffer();

    /** Gets the event's data array.
     *  @return a clone of the event's data array */
    byte[] copyData();

    /**
     *
     * @return
     */
    int    getOwner();



    /** Sets the event's data without copying. The length and memSize members of
   *  the event are automatically set to the data array's length.
   *  @param dat data array
   */
    void setData(byte[] dat) throws EtException;

    /** Set the event's data by copying it in. The event's length member
     *  is set to the length of the argument array.
     *  @param dat data array
     *  @throws org.jlab.coda.et.exception.EtException
     *     if the data array is the wrong size
     */
    void copyDataIn(byte[] dat) throws EtException;

    /** Set the event's data by copying it in. The event's length member
     *  is not changed.
     *  @param dat data array
     *  @param srcOff offset in "dat" byte array
     *  @param destOff offset in the event's byte array
     *  @param len bytes of data to copy
     *  @throws org.jlab.coda.et.exception.EtException
     *     if the data array is the wrong size
     */
    void copyDataIn(byte[] dat, int srcOff, int destOff, int len) throws EtException;

    /** Sets the event's priority.
     *  @param pri event priority
     *  @throws org.jlab.coda.et.exception.EtException
     *     if argument is a bad value
     */
    void setPriority(int pri) throws EtException;

    /** Sets the event's data length in bytes.
     *  @param len data length
     *  @throws org.jlab.coda.et.exception.EtException
     *     if length is less than zero
     */
    void setLength(int len) throws EtException;

    /** Sets the event's control array by copying it in.
     *  @param con control array
     *  @throws org.jlab.coda.et.exception.EtException
     *     if control array has the wrong number of elements
     */
    void setControl(int[] con) throws EtException;

    /** Sets the event's data status.
     *  @param status data status
     *  @throws org.jlab.coda.et.exception.EtException
     *     if argument is a bad value
     */
    void setDataStatus(int status) throws EtException;

    /** Gets the event's byte order - either {@link org.jlab.coda.et.Constants#endianBig} or
     *  {@link org.jlab.coda.et.Constants#endianLittle}.
     *  @return event's byte order */
    int getByteOrder();

    /** Set the event's byte order. Values can be {@link org.jlab.coda.et.Constants#endianBig},
     *  {@link org.jlab.coda.et.Constants#endianLittle}, {@link org.jlab.coda.et.Constants#endianLocal},
     *  {@link org.jlab.coda.et.Constants#endianNotLocal}, or {@link org.jlab.coda.et.Constants#endianSwitch}
     *  @param endian endian value
     *  @throws org.jlab.coda.et.exception.EtException
     *     if argument is a bad value
     */
    void setByteOrder(int endian) throws EtException;

    /** Tells caller if the event data needs to be swapped in order to be the
     *  correct byte order.
     *  @return <code>true</code> if swapping is needed, otherwise <code>false</code>
     *  @throws org.jlab.coda.et.exception.EtException
     *     if the byte order has a bad value
     */
    boolean needToSwap() throws EtException;
}
