/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 30-Aug-2005, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.server;

import org.jlab.coda.cMsg.cMsgNotifier;
import org.jlab.coda.cMsg.cMsgCallbackAdapter;

import java.util.Set;
import java.io.IOException;

/**
 * This class handles a server client's subscribeAndGet request and propagates it to all the
 * connected servers. It takes care of all the details of getting a response and forwarding
 * that to the client as well as cancelling the request to servers after the first
 * response is received.
 */
public class cMsgServerSubscribeAndGetter implements Runnable {
    /** Name server object that this thread is run by. */
    private cMsgNameServer nameServer;

    /**
     * Wait on this object which tells us when a matching message has been sent
     * to a subscribeAndGet so we can notify bridges to cancel their subscriptions
     * (which are used to implement remote subAndGets).
     */
    cMsgNotifier notifier;

    /** Contains all subscriptions made by this server. */
    Set subscriptions;

    /** Contains information about this particular subscription. */
    cMsgServerSubscribeInfo sub;

    /** Callback for this subscription. */
    cMsgCallbackAdapter cb;


    /** Constructor. */
    public cMsgServerSubscribeAndGetter(cMsgNameServer nameServer,
                                        cMsgNotifier notifier,
                                        cMsgCallbackAdapter cb,
                                        Set subscriptions,
                                        cMsgServerSubscribeInfo sub) {
        this.cb = cb;
        this.sub = sub;
        this.notifier = notifier;
        this.nameServer = nameServer;
        this.subscriptions = subscriptions;
    }

    public void run() {
        // Wait for a signal to cancel remote subscriptions
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

        nameServer.subscribeLock.lock();
        try {
            for (cMsgServerBridge b : nameServer.bridges.values()) {
                try {
                    // only cloud members please
                    if (b.getCloudStatus() != cMsgNameServer.INCLOUD) {
                        continue;
                    }
                    b.unsubscribeAndGet(sub.subject, sub.type, sub.namespace, cb);

                }
                catch (IOException e) {
                    // ignore exceptions as server is probably gone and so no matter
                }
            }

            sub.removeSubAndGetter(notifier.id);

            // If a msg was sent and sub removed simultaneously while a sub&Get (on client)
            // timed out so an unSub&Get was sent, ignore the unSub&get.
            if (sub.numberOfSubscribers() < 1) {
                subscriptions.remove(sub);
            }
        }
        finally {
            nameServer.subscribeLock.unlock();
        }
    }


}
