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
 * This enum represents the 3 possible states of an event's data's status.
 * OK is default. CORRUPT is never used. POSSIBLYCORRUPT is assigned to events
 * whose owning process crashed and were recovered by the system.
 * 
 * @author timmer
 */
public enum DataStatus {
    /** Data is OK or uncorrupted. */
    OK               (EtConstants.dataOk),
    /** Data is worthless or corrupted. */
    CORRUPT          (EtConstants.dataCorrupt),
    /** Data status is unkown and might be corrupted. */
    POSSIBLYCORRUPT  (EtConstants.dataPossiblyCorrupt);

    private int value;

    /** Fast way to convert integer values into DataStatus objects. */
    private static DataStatus[] intToType;

    // Fill array after all enum objects created
    static {
        intToType = new DataStatus[3];
        for (DataStatus type : values()) {
            intToType[type.value] = type;
        }
    }

	/**
	 * Obtain the enum from the value.
	 *
	 * @param val the value to match.
	 * @return the matching enum, or <code>null</code>.
	 */
    public static DataStatus getStatus(int val) {
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
        DataStatus s = getStatus(val);
        if (s == null) return null;
        return s.name();
    }

    DataStatus(int value) {
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
