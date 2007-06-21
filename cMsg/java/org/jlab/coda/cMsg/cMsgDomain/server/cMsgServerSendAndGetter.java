/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 16-Nov-2005, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.server;

import org.jlab.coda.cMsg.cMsgNotifier;
import org.jlab.coda.cMsg.cMsgException;

import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

/**
 * This class handles a server client's sendAndGet request and propagates it to all the
 * connected servers. It takes care of all the details of getting a response and forwarding
 * that to the client as well as cancelling the request to servers after the first
 * response is received.
 */
public class cMsgServerSendAndGetter implements Runnable {
    /** Name server object that this thread is run by. */
    private cMsgNameServer nameServer;

    /**
     * Wait on this object which tells us when a response message has been sent
     * to a sendAndGet so we can notify bridges to cancel their sendAndGets.
     */
    cMsgNotifier notifier;

    /** A list of all cMsgServerSendAndGetter objects (threads) for this client. */
    ConcurrentHashMap sendAndGetters;


    /** Constructor. */
    public cMsgServerSendAndGetter(cMsgNameServer nameServer,
                                   cMsgNotifier notifier,
                                   ConcurrentHashMap sendAndGetters) {
        this.notifier = notifier;
        this.nameServer = nameServer;
        this.sendAndGetters = sendAndGetters;
    }

    public void run() {
        // Wait for a signal to cancel sendAndGet
//System.out.println("cMsgServerSendAndGetter object: Wait on notifier");
        try {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            notifier.latch.await();
        }
        catch (InterruptedException e) {
            // If we've been interrupted it's because the client has died
            // and the server is cleaning up all the threads (including
            // this one).
            return;
        }

//System.out.println("cMsgServerSendAndGetter object: notifier fired");

        // for each server in the cloud, cancel the sendAndGet
        for (cMsgServerBridge b : nameServer.bridges.values()) {
            //System.out.println("Domain Server: call bridge subscribe");
            try {
                // only cloud members please
                if (b.getCloudStatus() != cMsgNameServer.INCLOUD) {
                    continue;
                }
//System.out.println("cMsgServerSendAndGetter object: unSendAndGet to bridge " + b.serverName +
//                   ", id = " + notifier.id);

                b.unSendAndGet(notifier.id);
            }
            catch (IOException e) {
                // ignore exceptions as server is probably gone and so no matter
            }
        }

        // Clear entry of this object in hash table of all client's sendAndGetter objects
        sendAndGetters.remove(notifier.id);
//System.out.println("EXIT GETTER for id = " + notifier.id + "\n");

    }



}
