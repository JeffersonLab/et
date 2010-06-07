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

import org.jlab.coda.et.system.StationLocal;
import org.jlab.coda.et.system.SystemCreate;


/**
 * This class contains an example of a user-defined method used to
 * select events for a station.
 *
 * @author Carl Timmer
 */

public class EtStationSelection implements EtEventSelectable {

    public EtStationSelection() {
    }

    public boolean select(SystemCreate sys, StationLocal st, EtEvent ev) {
        if (ev.getId()%2 == 0) {
            //System.out.println("  Select Func: accept ev.id = " + ev.id);
            return true;
        }
        //System.out.println("  Select Func: reject ev.id = " + ev.id);
        return false;
    }
}
