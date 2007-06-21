/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 10-Nov-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;


/**
 * This class provides a very basic (non-functional/dummy) implementation
 * of the cMsgSubdomainInterface interface. This class is used
 * by a domain server to respond to client demands. It contains
 * some methods that hide the details of communication
 * with the client. A fully implementated subclass of this
 * class must handle all communication with a particular subdomain
 * (such as SmartSockets or JADE agents).
 * <p/>
 * Understand that each client using cMsg will have its own handler object
 * from either an implemenation of the cMsgSubdomainInterface interface or a
 * subclass of this class. One client may concurrently use the same
 * cMsgHandleRequest object; thus, implementations must be thread-safe.
 * Furthermore, when the name server shuts dowm, the method handleServerShutdown
 * may be executed more than once for the same reason.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgSubdomainAdapter implements cMsgSubdomainInterface {
    /**
     * Method to give the subdomain handler the appropriate part
     * of the UDL the client used to talk to the domain server.
     *
     * @param UDLRemainder last part of the UDL appropriate to the subdomain handler
     * @throws org.jlab.coda.cMsg.cMsgException
     *
     */
    public void setUDLRemainder(String UDLRemainder) throws cMsgException {
        throw new cMsgException("setUDLRemainder is not implemented");
    }


    /**
     * Method to give the subdomain handler on object able to deliver messages
     * to the client.
     *
     * @param deliverer object able to deliver messages to the client
     * @throws org.jlab.coda.cMsg.cMsgException
     */
    public void setMessageDeliverer(cMsgDeliverMessageInterface deliverer) throws cMsgException {
        throw new cMsgException("setMessageDeliverer is not implemented");
    }


    /**
     * Method to register domain client.
     *
     * @param info information about client
     * @throws cMsgException
     */
    public void registerClient(cMsgClientInfo info) throws cMsgException {
        throw new cMsgException("registerClient is not implemented");
    }


    /**
     * Method to handle message sent by domain client.
     *
     * @param message message from sender
     * @throws cMsgException
     */
    public void handleSendRequest(cMsgMessageFull message) throws cMsgException {
        throw new cMsgException("handleSendRequest is not implemented");
    }


    /**
     * Method to handle message sent by domain client in synchronous mode.
     * It requries an integer response from the subdomain handler.
     *
     * @param message message from sender
     * @return response from subdomain handler
     * @throws cMsgException
     */
    public int handleSyncSendRequest(cMsgMessageFull message) throws cMsgException {
        throw new cMsgException("handleSyncSendRequest is not implemented");
    }


    /**
     * Method to synchronously get a single message from the server for a one-time
     * subscription of a subject and type.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleSubscribeAndGetRequest(String subject, String type, int id)
            throws cMsgException {
        throw new cMsgException("handleSubscribeAndGetRequest is not implemented");
    }


    /**
     * Method to synchronously get a single message from a receiver by sending out a
     * message to be responded to.
     *
     * @param message message requesting what sort of message to get
     * @throws cMsgException
     */
    public void handleSendAndGetRequest(cMsgMessageFull message) throws cMsgException {
        throw new cMsgException("handleSendAndGetRequest is not implemented");
    }


    /**
     * Method to handle remove sendAndGet request sent by domain client
     * (hidden from user).
     *
     * @param id message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleUnSendAndGetRequest(int id) throws cMsgException {
        throw new cMsgException("handleUnSendAndGetRequest is not implemented");
    }


    /**
     * Method to handle remove subscribeAndGet request sent by domain client
     * (hidden from user).
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleUnsubscribeAndGetRequest(String subject, String type, int id)
            throws cMsgException {
        throw new cMsgException("handleUnSubscribeAndGetRequest is not implemented");
    }


    /**
     * Method to handle subscribe request sent by domain client.
     *
     * @param subject  message subject to subscribe to
     * @param type     message type to subscribe to
     * @param id       message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleSubscribeRequest(String subject, String type, int id)
            throws cMsgException {
        throw new cMsgException("handleSubscribeRequest is not implemented");
    }

    /**
     * Method to handle unsubscribe request sent by domain client.
     *
     * @param subject  message subject to subscribe to
     * @param type     message type to subscribe to
     * @param id       message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleUnsubscribeRequest(String subject, String type, int id)
            throws cMsgException {
        throw new cMsgException("handleUnsubscribeRequest is not implemented");
    }


    /**
     * Method to handle request to shutdown clients sent by domain client.
     *
     * @param client client(s) to be shutdown
     * @param includeMe   if true, this client may be shutdown too
     * @throws cMsgException
     */
    public void handleShutdownClientsRequest(String client, boolean includeMe)
            throws cMsgException {
        throw new cMsgException("handleShutdownClientsRequest is not implemented");
    }


    /**
     * Method to handle keepalive sent by domain client checking to see
     * if the domain server socket is still up.
     *
     * @throws cMsgException
     */
    public void handleKeepAlive() throws cMsgException {
    }


    /**
     * Method to handle a client or domain server down.
     *
     * @throws cMsgException
     */
    public void handleClientShutdown() throws cMsgException {
    }


    /**
     * Method to tell if the "send" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendRequest}
     * method.
     *
     * @return true if send implemented in {@link #handleSendRequest}
     */
    public boolean hasSend() {
        return false;
    }


    /**
     * Method to tell if the "syncSend" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSyncSendRequest}
     * method.
     *
     * @return true if send implemented in {@link #handleSyncSendRequest}
     */
    public boolean hasSyncSend() {
        return false;
    }


    /**
     * Method to tell if the "subscribeAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeAndGetRequest}
     * method.
     *
     * @return true if subscribeAndGet implemented in {@link #handleSubscribeAndGetRequest}
     */
    public boolean hasSubscribeAndGet() {
        return false;
    }


    /**
     * Method to tell if the "sendAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendAndGetRequest}
     * method.
     *
     * @return true if sendAndGet implemented in {@link #handleSendAndGetRequest}
     */
    public boolean hasSendAndGet() {
        return false;
    }


    /**
     * Method to tell if the "subscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeRequest}
     * method.
     *
     * @return true if subscribe implemented in {@link #handleSubscribeRequest}
     */
    public boolean hasSubscribe() {
        return false;
    }


    /**
     * Method to tell if the "unsubscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleUnsubscribeRequest}
     * method.
     *
     * @return true if unsubscribe implemented in {@link #handleUnsubscribeRequest}
     */
    public boolean hasUnsubscribe() {
        return false;
    }


    /**
     * Method to tell if the "shutdown" cMsg API function is implemented
     * by this interface implementation in the {@link #handleShutdownClientsRequest}
     * method.
     *
     * @return true if shutdown implemented in {@link #handleShutdownClientsRequest}
     */
    public boolean hasShutdown() {
        return false;
    }



}
