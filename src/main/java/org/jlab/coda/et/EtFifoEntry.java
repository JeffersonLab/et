//
// Copyright 2022, Jefferson Science Associates, LLC.
// Subject to the terms in the LICENSE file found in the top-level directory.
//
// EPSCI Group
// Thomas Jefferson National Accelerator Facility
// 12000, Jefferson Ave, Newport News, VA 23606
// (757)-269-7100


package org.jlab.coda.et;

import org.jlab.coda.et.exception.*;


/**
 * <p>
 * When the ET system is used as a FIFO, this class represents a single entry in that fifo.
 * The idea is that an ET system divides its events (or buffers) into groups (or arrays)
 * of fixed size. Thus a FIFO entry is an array of related buffers. This is implemented
 * through the new ET api based on the {@link EtContainer} class, designed to miminize
 * generating garbage by reusing arrays and objects. This class is designed to hide the
 * complexities of using ET as a fifo.
 * </p>
 */

public class EtFifoEntry {

    // max size of each ET event in bytes
    private final int  evSize;
    // total number of buffers in this entry whether used or not
    private final int  capacity;
    // reusable place to store events
    private final EtContainer container;


    /**
     * Create a fifo representation of the ET system and
     * interact with it as a consumer of fifo entries.
     *
     * @param sys  object representing an open ET system.
     * @param fifo object representing an ET system as a fifo.
     *
     * @throws EtException if
     */
    public EtFifoEntry(EtSystem sys, EtFifo fifo) throws EtException {

        // Events max there are in 1 entry
        capacity = fifo.getEntryCapacity();
        evSize   = (int) sys.getEventSize();

        // Place to store events in ET's new, minimum-garbage interface
        container = new EtContainer(capacity, evSize);
    }


    /**
     * Get the EtContainer which this object encapsulates.
     * Only to be used by {@link EtFifo}.
     * @return EtContainer which this object encapsulates.
     */
    EtContainer getContainer() {return container;}


    /**
     * Gets the array of ET events.
     * @return  array of ET events.
     */
    public EtEvent[] getBuffers() {return container.getEventArray();}


    // Can also call these methods from the EtFifo object

    /**
     * Gets the size of each event's data buffer in bytes.
     * @return  size of each event's data buffer in bytes.
     */
    public long getBufferSize() {return evSize;}


    /**
     * Gets the max number of events in this FIFO entry.
     * @return  max number of events in this FIFO entry.
     */
    public int getEntryCapacity() {return capacity;}


}
