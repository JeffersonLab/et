/*----------------------------------------------------------------------------*
 *  Copyright (c) 2010        Jefferson Science Associates,                   *
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

import org.jlab.coda.et.exception.EtException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Interface used to define methods necessary to be an Event.
 */
public interface Event {

    /**
     * Initialize an event's fields. Called for an event each time it passes
     * through GRAND_CENTRAL station.
     */
    public void init();


    // getters


    /**
     * Gets the event's id number.
     * @return event's id number.
     */
    public int getId();

    /**
     * Gets the age of the event, either {@link org.jlab.coda.et.Constants#eventNew}
     * if a new event or {@link org.jlab.coda.et.Constants#eventUsed} otherwise.
     * @return age of the event.
     */
    public int getAge();

    /**
     * Gets the group the event belongs to.
     * @return the group the event belongs to.
     */
    public int getGroup();

    /**
     * Gets the event's priority, either high {@link Constants#high} or low {@link Constants#low}.
     * @return event's priority.
     */
    public int getPriority();

    /**
     * Gets the length of the data in bytes.
     * @return length of the data in bytes.
     */
    public int getLength();

    /**
     * Gets the size of the data buffer in bytes.
     * @return size of the data buffer in bytes.
     */
    public int getMemSize();

    /**
     * Gets the size limit of the data buffer in bytes when using a C-based ET system.
     * @return size size limit of the data buffer in bytes when using a C-based ET system.
     */
    public int getSizeLimit();

    /**
     * Gets the status of the data.
     * It can be ok {@link Constants#dataOk}, corrupted
     * {@link Constants#dataCorrupt}, or possibly corrupted
     * {@link Constants#dataPossiblyCorrupt}.
     * 
     * @return status of the data.
     */
    public int getDataStatus();

    /**
     * Gets the event's modify value. This specifies whether the user which
     * obtained the event over the network plans on modifying the data {@link Constants#modify}, or
     * only modifying the header {@link Constants#modifyHeader}. The default assumed (0)
     * is that the no values are modified resulting in this event being put back into
     * the ET system (by remote server) immediately upon being copied and that copy
     * sent to this user.
     * 
     * @return event's modify value.
     */
    public int getModify();

    /**
     * Gets the event's control array.
     * @return event's control array.
     */
    public int[] getControl();

    /**
     * Gets the event's data array which is backing the event's data buffer.
     * @return event's data array.
     */
    public byte[] getData();

    /**
     * Gets the event's data buffer.
     * @return event's data buffer.
     */
    public ByteBuffer getDataBuffer();

    /**
     * Gets the attachment id of the attachment which owns or got the event.
     * If it's owned by the system its value is {@link Constants#system}.
     * @return id of owning attachment or {@link Constants#system} if system owns it
     */
    public int getOwner();

    /**
     * Gets the event's byte order.
     * @return event's byte order
     */
    ByteOrder getByteOrder();


    // setters


    /**
     * Sets the event's priority.
     *
     * @param pri event priority
     * @throws EtException if argument is a bad value
     */
    void setPriority(int pri) throws EtException;

    /**
     * Sets the event's data length in bytes.
     *
     * @param len data length
     * @throws EtException if length is less than zero
     */
    void setLength(int len) throws EtException;

    /**
     * Sets the event's control array by copying it in.
     *
     * @param con control array
     * @throws EtException if control array has the wrong number of elements
     */
    void setControl(int[] con) throws EtException;

    /**
     * Sets the event's data status.
     *
     * @param status data status
     * @throws EtException if argument is a bad value
     */
    void setDataStatus(int status) throws EtException;

    /**
     * Set the event's byte order. Values can be {@link org.jlab.coda.et.Constants#endianBig},
     * {@link org.jlab.coda.et.Constants#endianLittle}, {@link org.jlab.coda.et.Constants#endianLocal},
     * {@link org.jlab.coda.et.Constants#endianNotLocal}, or {@link org.jlab.coda.et.Constants#endianSwitch}
     *
     * @param endian endian value
     * @throws EtException if argument is a bad value
     */
    void setByteOrder(int endian) throws EtException;


    // miscellaneous

    
    /**
     * Tells caller if the event data needs to be swapped in order to be the
     * correct byte order.
     *
     * @return <code>true</code> if swapping is needed, otherwise <code>false</code>
     * @throws EtException if the byte order has a bad value
     */
    boolean needToSwap() throws EtException;
}
