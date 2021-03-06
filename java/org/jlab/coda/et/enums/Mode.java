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

package org.jlab.coda.et.enums;

import org.jlab.coda.et.EtConstants;

/**
 * This enum represents 3 possible modes in which to get new or existing events
 * when no events are currently available (in calls newEvents() or getEvents()).
 * SLEEP indicates that the user will wait (forever if necessary) until some
 * are available. TIMED means the user will wait up to the given delay time, and
 * ASYNC means that the call will return immediately.
 *
 * @author timmer
 */
public enum Mode {
    /** Wait forever for events to become available. */
    SLEEP   (EtConstants.sleep),
    /** Wait up to a given delay time for events to become available. */
    TIMED   (EtConstants.timed),
    /** Do not wait for events to become available. */
    ASYNC   (EtConstants.async);

    private int value;

    /** Fast way to convert integer values into Mode objects. */
    private static Mode[] intToType;

    // Fill array after all enum objects created
    static {
        intToType = new Mode[3];
        for (Mode type : values()) {
            intToType[type.value] = type;
        }
    }

	/**
	 * Obtain the enum from the value.
	 *
	 * @param val the value to match.
	 * @return the matching enum, or <code>null</code>.
	 */
    public static Mode getMode(int val) {
        if (val > 2 || val < 0) return null;
        return intToType[val];
    }

    /**
     * Obtain the name from the value.
     *
     * @param val the value to match.
     * @return the name, or <code>null</code>.
     */
    public static String getName(int val) {
        Mode s = getMode(val);
        if (s == null) return null;
        return s.name();
    }

    Mode(int value) {
        this.value = value;
    }

    /**
     * Get the enum's value.
     * @return the value
     */
    public int getValue() {
        return value;
    }

}
