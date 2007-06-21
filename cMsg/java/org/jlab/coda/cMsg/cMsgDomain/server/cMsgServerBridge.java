/*----------------------------------------------------------------------------*
 *  Copyright (c) 2005        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 8-Jun-2005, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.server;

import org.jlab.coda.cMsg.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

/**
 * This class acts to bridge two cMsg domain servers by existing in one server
 * and becoming a client of another cMsg domain server.
 */
public class cMsgServerBridge {

    /** Reference to subdomain handler object for use by all bridges in this server. */
    static private org.jlab.coda.cMsg.subdomains.cMsg subdomainHandler = new org.jlab.coda.cMsg.subdomains.cMsg();

    /** Need 1 callback for all subscriptions. */
    static private SubscribeCallback subCallback = new SubscribeCallback();

    /** Client of cMsg server this object is a bridge to. It is the means of connection. */
    org.jlab.coda.cMsg.cMsgDomain.client.cMsgServerClient client;

    /** Name of cMsg server ("host:port") this server is connected to. */
    String serverName;

    /** The cloud status of the cMsg server this server is connected to. */
    private volatile int cloudStatus = cMsgNameServer.UNKNOWNCLOUD;

    /** The port this name server is listening on. */
    private int port;

    /**
     * Map to store a sendAndGet id of the originating cMsg server and the corresponding
     * id of the propagated sendAndGet in the server connected to this bridge.
     */
    private ConcurrentHashMap<Integer,Integer> idStorage;

    /** This method prints sizes of maps for debugging purposes. */
    public void printSizes() {
        System.out.println("              ids  = " + idStorage.size());
        client.printSizes();
    }


    /**
     * This class defines the callback to be run when a message matching
     * our subscription arrives from a bridge connection and must be
     * passed on to a client(s).
     */
    static class SubscribeCallback extends cMsgCallbackAdapter {
        /**
         * Callback which passes on a message to other clients.
         *
         * @param msg message received from domain server
         * @param userObject in this case the original msg sender's namespace.
         */
        public void callback(cMsgMessage msg, Object userObject) {
//System.out.println("In bridge callback!!!");
            String namespace = (String) userObject;

            // check for null text
            if (msg.getText() == null) {
                msg.setText("");
            }
            //try {
            //    Thread.sleep(1);
            //}
            //catch (InterruptedException e) {}
            try {
                // pass this message on to local clients
                subdomainHandler.localSend(msg, namespace);
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }
        }

        // Increase cue size from 1000 to 3000 for server smoothness.
        public int getMaximumCueSize() {return 3000;}
    }


//-----------------------------------------------------------------------------

    /**
     * This method returns a reference to the one callback needed for
     * subscribes and subAndGets to propogate messages back to
     * original client.
     * @return callback object for propogating messages back to original client
     */
    static public cMsgCallbackAdapter getSubAndGetCallback() {
        return subCallback;
    }


//-----------------------------------------------------------------------------


    /**
     * This class defines the callback to be run when a sendAndGet response message
     * arrives from a bridge connection and must be passed on to a client(s).
     */
    static class SendAndGetCallback extends cMsgCallbackAdapter {
        /** Send messges's senderToken on this server. */
        int id;
        /** Send message's sysMsgId on this server. */
        int sysMsgId;
        /** Allow this callback to skip extra incoming messages since only one is needed.  */
        AtomicBoolean skip = new AtomicBoolean(false);

        /**
         * Constructor.
         * @param id incoming message's senderToken needs to be changed to this value
         * @param sysMsgId incoming message's sysMsgId needs to be changed to this value
         */
        SendAndGetCallback(int id, int sysMsgId) {
            this.id = id;
            this.sysMsgId = sysMsgId;
        }


        /**
         * Callback which passes on a message to local clients.
         *
         * @param msg message received from domain server
         * @param userObject in this case the original msg sender's namespace.
         */
        public void callback(cMsgMessage msg, Object userObject) {
            // If skip's current value is false, set it to true (atomically).
            // If skip's current value is NOT false, return.
            if (!skip.compareAndSet(false,true)) {
                return;
            }

            // The message delivered to this callback is actually
            // a cMsgMessageFull object and can be cast to that.
            cMsgMessageFull fullMsg = (cMsgMessageFull) msg;

            String namespace = (String) userObject;

            try {
                // check for null text
                if (fullMsg.getText() == null) {
                    fullMsg.setText("");
                }

                // put in correct ids for local sendAndGet (isGetResponse already set)
                fullMsg.setSenderToken(id);
                fullMsg.setSysMsgId(sysMsgId);
//System.out.println("CB: changed senderToken,sysMsgId from");
//System.out.print("    " + msg.getSenderToken() + ", " + msg.getSysMsgId() + " to " );
//System.out.println("   " + id + ", " + sysMsgId);
                // pass this message on to local clients
                subdomainHandler.localSend(fullMsg, namespace);
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }
        }
    }


//-----------------------------------------------------------------------------


    /** Get a callback for a sendAndGet call. */
    static public cMsgCallbackAdapter getSendAndGetCallback(int id, int sysMsgId) {
        return new SendAndGetCallback(id, sysMsgId);
    }


//-----------------------------------------------------------------------------


    /**
     * Constructor.
     *
     * @param nameServer THIS name server (the one using this object)
     * @param serverName name of server to connect to in the form "host:port"
     * @param thisNameServerPort the port THIS name server is listening on
     * @throws cMsgException
     */
    public cMsgServerBridge(cMsgNameServer nameServer,
                            String serverName,
                            int thisNameServerPort)
            throws cMsgException {

        this.serverName = serverName;
        port = thisNameServerPort;
        idStorage = new ConcurrentHashMap<Integer,Integer>(100);

        // Normally a client uses the top level API. That is unecessary
        // (and undesirable) here because we already know we're in the
        // cMsg subdomain. We'll use the cMsg domain level object so we'll
        // have access to additional methods not part of the API.
        // The next few lines simply do what the top level API does.

        // create a proper UDL out of this server name
        String UDL = "cMsg://" + serverName + "/cMsg";

        // create cMsg connection object
        client = new org.jlab.coda.cMsg.cMsgDomain.client.cMsgServerClient(nameServer);

        // Pass in the UDL
        client.setUDL(UDL);
        // Pass in the name
        client.setName(serverName);
        // Pass in the description
        client.setDescription("server");
        // Pass in the UDL remainder
        client.setUDLRemainder(serverName + "/" + "cMsg");
    }


//-----------------------------------------------------------------------------


    /**
     * Method to connect to server.
     *
     * @param isOriginator true if originating the connection between the 2 servers and
     *                     false if this is the response or reciprocal connection
     * @param cloudPassword password for connecting to a server in a particular cloud
     * @return set of servers (names of form "host:port") that the server
     *         we're connecting to is already connected with
     * @throws cMsgException if there are problems parsing the UDL or
     *                       communication problems with the server
     */
    public Set<String> connect(boolean isOriginator, String cloudPassword) throws cMsgException {
        // create a connection to the UDL
        client.start();
        return client.connect(port, isOriginator, cloudPassword);
    }

   /**
    * This method gets the cloud status of the server this server is
    * connected to. Possible values are {@link cMsgNameServer#INCLOUD},
    * {@link cMsgNameServer#NONCLOUD}, or{@link cMsgNameServer#BECOMINGCLOUD}.
    *
    * @return cloud status
    */
    int getCloudStatus() {
        return cloudStatus;
    }


    /**
     * This method locally sets the cloud status of the server this server is
     * connected to through this bridge. Possible values are {@link cMsgNameServer#INCLOUD},
     * {@link cMsgNameServer#NONCLOUD}, or{@link cMsgNameServer#BECOMINGCLOUD}.
     *
     * @param cloudStatus cloud status of the server this is a bridge to
     */
    void setCloudStatus(int cloudStatus) {
        this.cloudStatus = cloudStatus;
    }


    /**
     * This method tells the server this server is connected to,
     * what cloud status this server has.
     *
     * @param status cMsgNameServer.INCLOUD, .NONCLOUD, or .BECOMINGCLOUD
     * @throws IOException if communication error with server
     */
    void thisServerCloudStatus(int status) throws IOException {
        client.thisServerCloudStatus(status);
    }


    /**
     * This method grabs the cloud lock of a connected server in order to add
     * this server to the cMsg domain server cloud.
     *
     * @param delay time in milliseconds to wait for the lock before timing out
     * @return true if lock was obtained, else false
     * @throws IOException if communication error with server
     */
    boolean cloudLock(int delay) throws IOException {
//System.out.println("      << BR: try locking cloud");
        return client.cloudLock(delay);
    }


    /**
     * This method releases the cloud lock of a connected server when adding
     * this server to the cMsg domain server cloud.
     *
     * @throws IOException if communication error with server
     */
    void cloudUnlock() throws IOException {
//System.out.println("      << BR: try unlocking cloud");
        client.cloudUnlock();
        return;
    }


    /**
     * This method grabs the registration lock (for adding a client)
     * of another cMsg domain server.
     *
     * @param  delay time in milliseconds to wait for the lock before timing out
     * @return true if lock was obtained, else false
     * @throws IOException if communication error with server
     */
    boolean registrationLock(int delay) throws IOException {
        return client.registrationLock(delay);
    }


    /**
     * This method releases the registration lock (when adding a client)
     * of another cMsg domain server.
     *
     * @throws IOException if communication error with server
     */
    void registrationUnlock() throws IOException {
        client.registrationUnlock();
        return;
    }


    /**
     * This method gets the names of all the local clients (not servers)
     * of another cMsg domain server.
     *
     * @return array of client names
     * @throws IOException if communication error with server
     */
    String[] getClientNamesAndNamespaces() throws IOException {
        return client.getClientNamesAndNamespaces();
    }


    /**
     * Method for a server to subscribe to receive messages of a subject
     * and type from another cMsg server. The combination of arguments must be unique.
     * In other words, only 1 subscription is allowed for a given set of subject,
     * type, callback, and userObj.
     *
     * @param subject    message subject
     * @param type       message type
     * @param namespace  message namespace
     * @throws IOException  there are communication problems with the server
     */
    public void subscribe(String subject, String type, String namespace)
        throws IOException {

//System.out.println("Bridge: call serverSubscribe with ns = " + namespace);
        client.serverSubscribe(subject, type, namespace, subCallback, namespace);
        return;
    }


    /**
     * Method for a server to unsubscribe for messages of a subject
     * and type from another cMsg server.
     *
     * @param subject    message subject
     * @param type       message type
     * @param namespace  message namespace
     * @throws IOException there are communication problems with the server
     */
    public void unsubscribe(String subject, String type, String namespace)
        throws IOException {

//System.out.println("Bridge: call serverUnsubscribe with ns = " + namespace);
        client.serverUnsubscribe(subject, type, namespace, subCallback, namespace);
        return;
    }

    /**
     * Method to do a sendAndGet of a message of subject and type to another cMsg server.
     *
     * @param msg message sent to server
     * @param namespace  message namespace
     * @param cb callback for response to sendAndGet request
     * @throws IOException if there are communication problems with the server
     */
    public void sendAndGet(cMsgMessage msg, String namespace, cMsgCallbackInterface cb)
            throws IOException {
        // id number of the sendAndGet stored for future unSendAndGet
        int id = client.serverSendAndGet(msg, namespace, cb);
        // Store 2 id numbers: 1 from system originating msg,
        // the other from system getting send a propagating msg.
        idStorage.put(msg.getSenderToken(), id);
//System.out.println("bridge   sendAndGet: store id " + id + " with token " + msg.getSenderToken());
        return;
    }


     /**
      * Method to remove a previous sendAndGet to receive a message of a subject
      * and type from the domain server. This method is only called when a sendAndGet
      * times out and the server must be told to forget about the get.
      *
      * @param id receiverSubscribeId of original message sent in sendAndGet
      * @throws IOException if there are communication problems with the server
      */
     public void unSendAndGet(int id)
             throws IOException {
         Integer newId = idStorage.remove(id);
         if (newId == null) {
//System.out.println("bridge unSendAndGet: cannot find id " + id);
             return;
         }
//System.out.print(" -"+idStorage.size());
//System.out.println("bridge unSendAndGet: removed msg id " +  id +
//                            " and new id " + newId.intValue());
         client.serverUnSendAndGet(newId);
         return;
     }


    /**
     * Method for a subscribeAndGet (a one-time subscribe) on another cMsg server.
     *
     * @param subject subject of message desired from server
     * @param type type of message desired from server
     * @param namespace  message namespace
     * @throws IOException there are communication problems with the server
     */
    public void subscribeAndGet(String subject, String type,
            String namespace, cMsgCallbackInterface cb)
            throws IOException {

//System.out.println("Bridge: call serverSubscribe with ns = " + namespace);
        client.serverSubscribe(subject, type, namespace, cb, namespace);
        return;
    }


    /**
     * Method to remove a previous subscribeAndGet ton another cMsg server.
     * This method is only called when a subscribeAndGet times out and the
     * server must be told to forget about the subscribeAndGet call.
     *
     * @param subject subject of message desired from server
     * @param type type of message desired from server
     * @param namespace  message namespace
     * @throws IOException there are communication problems with the server
     */
    public void unsubscribeAndGet(String subject, String type,
                                  String namespace, cMsgCallbackInterface cb)
            throws IOException {
        client.serverUnsubscribe(subject, type, namespace, cb, namespace);
        return;
    }


    /**
     * Method to shutdown the given clients.
     * Wildcards used to match client names with the given string.
     *
     * @param clientName client(s) to be shutdown
     * @param includeMe  if true, it is permissible to shutdown calling client
     * @throws IOException if there are communication problems with the server
     */
    public void shutdownClients(String clientName, boolean includeMe) throws IOException {
        client.serverShutdownClients(clientName, includeMe);
    }


    /**
     * Method to shutdown the given server this client is connected to.
     *
     * @throws IOException if there are communication problems with the server
     */
    public void shutdownServer() throws IOException {
        client.serverShutdown();
    }


 }
