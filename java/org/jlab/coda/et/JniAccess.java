package org.jlab.coda.et;

import org.jlab.coda.et.exception.EtException;

import java.io.IOException;

/**
 * This class handles all calls to native methods.
 */
class JniAccess {

    static {
        try {
            System.loadLibrary("et");
            System.loadLibrary("et_jni");
        }
        catch (Error e) {
            System.out.println("error loading libet_jni.so");
            System.exit(-1);
        }
    }

    private long localEtId;

    long getLocalEtId() {
        return localEtId;
    }

    private void setLocalEtId(long id) {
        localEtId = id;
    }


    native void openLocalEtSystem(String etName) throws EtException;

    native void putEvents(long etId, int attId, EventImpl[] evs, int length) throws EtException;

    native void dumpEvents(long etId, int attId, EventImpl[] evs, int length) throws EtException;

    native EventImpl[] getEvents(long etId, int attId, int mode, int sec, int nsec, int count) throws EtException;

    native EventImpl[] newEvents(long etId, int attId, int mode, int sec, int nsec, int count, int size, int group) throws EtException;
}
