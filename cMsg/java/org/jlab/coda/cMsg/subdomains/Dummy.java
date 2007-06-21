/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 30-Jan-2006, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.subdomains;

import org.jlab.coda.cMsg.*;

import java.util.Date;

/**
 * This class is a subdomain which does nothing. It was used for finding a
 * memory leak in the cMsg server.
 */
public class Dummy extends cMsgSubdomainAdapter {

    long counter;
    Date now;

    public Dummy() {
        now = new Date();
    }


    /**
     * Method to give the subdomain handler the appropriate part
     * of the UDL the client used to talk to the domain server.
     *
     * @param UDLRemainder last part of the UDL appropriate to the subdomain handler
     *
     */
    public void setUDLRemainder(String UDLRemainder) {
        return;
    }


    /**
     * Method to give the subdomain handler on object able to deliver messages
     * to the client.
     *
     * @param deliverer object able to deliver messages to the client
     */
    public void setMessageDeliverer(cMsgDeliverMessageInterface deliverer) {
        return;
    }


    /**
     * Method to register domain client.
     *
     * @param info information about client
     */
    public void registerClient(cMsgClientInfo info) {
        return;
    }


    /**
     * Method to handle message sent by domain client.
     *
     * @param message message from sender
     */
    synchronized public void handleSendRequest(cMsgMessageFull message) {
        // for printing out request cue size periodically
        counter++;
        Date t = new Date();
        if (now.getTime() + 10000 <= t.getTime()) {
            System.out.println("Message count = " + counter);
            now = t;
        }
        return;
    }


    /**
     * Method to handle message sent by domain client in synchronous mode.
     * It requries an integer response from the subdomain handler.
     *
     * @param message message from sender
     * @return 0
     */
    public int handleSyncSendRequest(cMsgMessageFull message) {
        return 0;
    }


    /**
     * Method to synchronously get a single message from the server for a one-time
     * subscription of a subject and type.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id refering to these specific subject and type values
     */
    public void handleSubscribeAndGetRequest(String subject, String type, int id) {
        return;
    }


    /**
     * Method to synchronously get a single message from a receiver by sending out a
     * message to be responded to.
     *
     * @param message message requesting what sort of message to get
     */
    public void handleSendAndGetRequest(cMsgMessageFull message) {
        return;
    }


    /**
     * Method to handle remove sendAndGet request sent by domain client
     * (hidden from user).
     *
     * @param id message id refering to these specific subject and type values
     */
    public void handleUnSendAndGetRequest(int id) {
        return;
    }


    /**
     * Method to handle remove subscribeAndGet request sent by domain client
     * (hidden from user).
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id message id refering to these specific subject and type values
     */
    public void handleUnsubscribeAndGetRequest(String subject, String type, int id) {
        return;
    }


    /**
     * Method to handle subscribe request sent by domain client.
     *
     * @param subject  message subject to subscribe to
     * @param type     message type to subscribe to
     * @param id       message id refering to these specific subject and type values
     */
    public void handleSubscribeRequest(String subject, String type, int id) {
        return;
    }

    /**
     * Method to handle unsubscribe request sent by domain client.
     *
     * @param subject  message subject to subscribe to
     * @param type     message type to subscribe to
     * @param id       message id refering to these specific subject and type values
     */
    public void handleUnsubscribeRequest(String subject, String type, int id) {
        return;
    }


    /**
     * Method to handle request to shutdown clients sent by domain client.
     *
     * @param client client(s) to be shutdown
     * @param includeMe   if true, this client may be shutdown too
     */
    public void handleShutdownClientsRequest(String client, boolean includeMe) {
        return;
    }


    /**
     * Method to handle keepalive sent by domain client checking to see
     * if the domain server socket is still up.
     */
    public void handleKeepAlive() {
        return;
    }


    /**
     * Method to handle a client or domain server down.
     */
    public void handleClientShutdown() {
        return;
    }


    /**
     * Method to tell if the "send" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendRequest}
     * method.
     *
     * @return true
     */
    public boolean hasSend() {
        return true;
    }


    /**
     * Method to tell if the "syncSend" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSyncSendRequest}
     * method.
     *
     * @return true
     */
    public boolean hasSyncSend() {
        return true;
    }


    /**
     * Method to tell if the "subscribeAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeAndGetRequest}
     * method.
     *
     * @return true
     */
    public boolean hasSubscribeAndGet() {
        return true;
    }


    /**
     * Method to tell if the "sendAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendAndGetRequest}
     * method.
     *
     * @return true
     */
    public boolean hasSendAndGet() {
        return true;
    }


    /**
     * Method to tell if the "subscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeRequest}
     * method.
     *
     * @return true
     */
    public boolean hasSubscribe() {
        return true;
    }


    /**
     * Method to tell if the "unsubscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleUnsubscribeRequest}
     * method.
     *
     * @return true
     */
    public boolean hasUnsubscribe() {
        return true;
    }


    /**
     * Method to tell if the "shutdown" cMsg API function is implemented
     * by this interface implementation in the {@link #handleShutdownClientsRequest}
     * method.
     *
     * @return true
     */
    public boolean hasShutdown() {
        return true;
    }




}
