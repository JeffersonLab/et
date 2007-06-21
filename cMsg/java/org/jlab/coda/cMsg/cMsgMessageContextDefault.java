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
 * The class defines the default context that a message starts with when it's created.
 */
public class cMsgMessageContextDefault implements cMsgMessageContextInterface {

    boolean reliableSend = true;

    public cMsgMessageContextDefault() {
    }

    public cMsgMessageContextDefault(boolean reliableSend) {
        this.reliableSend = reliableSend;
    }
    
    /**
     * Gets the domain this callback is running in.
     * @return domain this callback is running in.
     */
    public String getDomain() {return null;}


    /**
     * Gets the subject of this callback's subscription.
     * @return subject of this callback's subscription.
     */
    public String getSubject() {return null;}


    /**
     * Gets the type of this callback's subscription.
     * @return type of this callback's subscription.
     */
    public String getType() {return null;}


    /**
     * Gets the value of this callback's cue size.
     * @return value of this callback's cue size, -1 if no info available
     */
    public int getCueSize() {return -1;}


    /**
     * Sets whether the send will be reliable (default, TCP)
     * or will be allowed to be unreliable (UDP).
     *
     * @param b false if using UDP, or true if using TCP
     */
    public void setReliableSend(boolean b) {
        reliableSend = b;
    }


    /**
     * Gets whether the send will be reliable (default, TCP)
     * or will be allowed to be unreliable (UDP).
     *
     * @return value true if using TCP, else false
     */
    public boolean getReliableSend() {
        return reliableSend;
    }

}
