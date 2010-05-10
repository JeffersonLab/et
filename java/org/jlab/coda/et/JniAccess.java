package org.jlab.coda.et;

import org.jlab.coda.et.exception.*;


/**
 * This class handles all calls to native methods which, in turn,
 * makes calls to the C ET library routines to get, put, and dump events.
 */
class JniAccess {

    // Load in the necessary library when this class is loaded
    static {
        try {
            System.loadLibrary("et_jni");
        }
        catch (Error e) {
            System.out.println("error loading libet_jni.so");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /** Place to store id (pointer) returned from et_open in C code. */
    private long localEtId;

    /**
     * Get the et id.
     * @return et id
     */
    long getLocalEtId() {
        return localEtId;
    }

    /**
     * Set the et id. Used inside native method {@link #openLocalEtSystem(String)}.
     * @param id et id
     */
    private void setLocalEtId(long id) {
        localEtId = id;
    }


    /**
     * Open a local, C-based ET system and store it's id.
     *
     * @param etName name of ET system to open
     * @throws EtException for any failure to open ET system except timeout
     * @throws EtTimeoutException for failure to open ET system within the specified time limit
     */
    native void openLocalEtSystem(String etName) throws EtException, EtTimeoutException;


    /**
     *
     * @param etId
     * @param attId
     * @param evs
     * @param length
     * @throws EtException
     */
    native void putEvents(long etId, int attId, EventImpl[] evs, int length) throws EtException;


    native void dumpEvents(long etId, int attId, EventImpl[] evs, int length) throws EtException;


    native EventImpl[] getEvents(long etId, int attId, int mode, int sec, int nsec, int count) throws EtException;

    
    native EventImpl[] newEvents(long etId, int attId, int mode, int sec, int nsec, int count, int size, int group)
            throws EtException,        EtDeadException, EtWakeUpException,
                   EtTimeoutException, EtBusyException, EtEmptyException;
}
