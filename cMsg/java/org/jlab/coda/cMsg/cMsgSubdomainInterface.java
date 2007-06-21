/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 14-Jul-2004, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;


/**
 * This interface is an API for an object that a domain server
 * uses to respond to client demands. An implementation of this
 * interface handles all communication with a particular subdomain
 * (such as SmartSockets or JADE agents).
 *
 * Implementors of this interface must understand that each
 * client using cMsg will have its own handler object from a class
 * implementing this interface. Several clients may concurrently use
 * objects of the same class. Thus implementations must be thread-safe.
 * Furthermore, when the name server shuts dowm, the method handleServerShutdown
 * may be executed more than once for the same reason.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public interface cMsgSubdomainInterface {
    /**
     * Method to give the subdomain handler the appropriate part
     * of the UDL the client used to talk to the domain server.
     *
     * @param UDLRemainder last part of the UDL appropriate to the subdomain handler
     * @throws org.jlab.coda.cMsg.cMsgException
     */
    public void setUDLRemainder(String UDLRemainder) throws cMsgException;


    /**
     * Method to give the subdomain handler on object able to deliver messages
     * to the client.
     *
     * @param deliverer object able to deliver messages to the client
     * @throws org.jlab.coda.cMsg.cMsgException
     */
    public void setMessageDeliverer(cMsgDeliverMessageInterface deliverer) throws cMsgException;


    /**
     * Method to register a domain client.
     *
     * @param info information about client
     * @throws cMsgException
     */
    public void registerClient(cMsgClientInfo info) throws cMsgException;


    /**
     * Method to handle a message sent by a domain client.
     *
     * @param message message from sender
     * @throws cMsgException
     */
    public void handleSendRequest(cMsgMessageFull message) throws cMsgException;


    /**
     * Method to handle a message sent by a domain client in synchronous mode.
     * It requries an integer response from the subdomain handler.
     *
     * @param message message from sender
     * @return response from subdomain handler
     * @throws cMsgException
     */
    public int handleSyncSendRequest(cMsgMessageFull message) throws cMsgException;


    /**
     * Method to synchronously get a single message from the server for a one-time
     * subscription of a subject and type.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id
     * @throws cMsgException
     */
    public void handleSubscribeAndGetRequest(String subject, String type, int id)
            throws cMsgException;


    /**
     * Method to remove a subscribeAndGet request previously sent by a domain client.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id
     * @throws cMsgException
     */
    public void handleUnsubscribeAndGetRequest(String subject, String type, int id)
            throws cMsgException;


    /**
     * Method to synchronously get a single message by sending out a
     * message which is responded to by its receiver(s).
     *
     * @param message message requesting what sort of response message to get
     * @throws cMsgException
     */
    public void handleSendAndGetRequest(cMsgMessageFull message) throws cMsgException;


    /**
     * Method to remove a sendAndGet request previously sent by a domain client.
     *
     * @param id message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleUnSendAndGetRequest(int id) throws cMsgException;


    /**
     * Method to handle a subscribe request sent by a domain client.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id
     * @throws cMsgException
     */
    public void handleSubscribeRequest(String subject, String type, int id)
            throws cMsgException;


    /**
     * Method to handle an unsubscribe request sent by a domain client.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id
     * @throws cMsgException
     */
    public void handleUnsubscribeRequest(String subject, String type, int id)
            throws cMsgException;


    /**
     * Method to handle a request to shutdown clients sent by a domain client.
     *
     * @param client client(s) to be shutdown
     * @param includeMe   if true, this client may be shutdown too
     * @throws cMsgException
     */
    public void handleShutdownClientsRequest(String client, boolean includeMe)
            throws cMsgException;


    /**
     * Method to handle a keepalive sent by a domain client checking to see
     * if the domain server is still up.
     *
     * @throws cMsgException
     */
    public void handleKeepAlive() throws cMsgException;


    /**
     * Method to handle a client or domain server (and therefore subdomain handler) shutdown.
     *
     * @throws cMsgException
     */
    public void handleClientShutdown() throws cMsgException;


    /**
     * Method to tell if the "send" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendRequest}
     * method.
     *
     * @return true if send implemented in {@link #handleSendRequest}
     */
    public boolean hasSend();


    /**
     * Method to tell if the "syncSend" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSyncSendRequest}
     * method.
     *
     * @return true if send implemented in {@link #handleSyncSendRequest}
     */
    public boolean hasSyncSend();


    /**
     * Method to tell if the "subscribeAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeAndGetRequest}
     * method.
     *
     * @return true if subscribeAndGet implemented in {@link #handleSubscribeAndGetRequest}
     */
    public boolean hasSubscribeAndGet();


    /**
     * Method to tell if the "sendAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendAndGetRequest}
     * method.
     *
     * @return true if sendAndGet implemented in {@link #handleSendAndGetRequest}
     */
    public boolean hasSendAndGet();


    /**
     * Method to tell if the "subscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeRequest}
     * method.
     *
     * @return true if subscribe implemented in {@link #handleSubscribeRequest}
     */
    public boolean hasSubscribe();


    /**
     * Method to tell if the "unsubscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleUnsubscribeRequest}
     * method.
     *
     * @return true if unsubscribe implemented in {@link #handleUnsubscribeRequest}
     */
    public boolean hasUnsubscribe();


    /**
     * Method to tell if the "shutdown" cMsg API function is implemented
     * by this interface implementation in the {@link #handleShutdownClientsRequest}
     * method.
     *
     * @return true if shutdown implemented in {@link #handleShutdownClientsRequest}
     */
    public boolean hasShutdown();
}
