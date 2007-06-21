/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 17-Nov-2004, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;

/**
 * Interface defining the context in which a subscription's callback is run.
 */
public interface cMsgMessageContextInterface {

    /**
     * Gets the domain this callback is running in.
     * @return domain this callback is running in.
     */
    public String getDomain();


    /**
     * Gets the subject of this callback's subscription.
     * @return subject of this callback's subscription.
     */
    public String getSubject();


    /**
     * Gets the type of this callback's subscription.
     * @return type of this callback's subscription.
     */
    public String getType();


    /**
     * Gets the value of this callback's cue size.
     * @return value of this callback's cue size, -1 if no info available
     */
    public int getCueSize();


    /**
     * Sets whether the send will be reliable (default, TCP)
     * or will be allowed to be unreliable (UDP).
     *
     * @param b false if using UDP, or true if using TCP
     */
    public void setReliableSend(boolean b);


    /**
     * Gets whether the send will be reliable (default, TCP)
     * or will be allowed to be unreliable (UDP).
     *
     * @return value true if using TCP, else false
     */
    public boolean getReliableSend();

}
