/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 26-Apr-2006, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.RCServerDomain;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.cMsgCallbackThread;
import org.jlab.coda.cMsg.cMsgGetHelper;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.net.*;
import java.io.*;

/**
 * This class implements the runcontrol server (rcs) domain.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class RCServer extends cMsgDomainAdapter {

    /** Runcontrol client's TCP listening port obtained from UDL. */
    int rcClientPort;

    /** Runcontrol client's name returned from the connect message. */
    String rcClientName;

    /** Runcontrol client's host obtained from UDL. */
    String rcClientHost;

    /** UDP port on which to receive messages from the rc client. */
    int localUdpPort;

    /** TCP port on which to receive messages from the rc client. */
    int localTcpPort;

    /** Thread that listens for TCP packets sent to this server. */
    rcTcpListeningThread tcpListener;

    /** Thread that listens for UDP packets sent to this server. */
    rcUdpListeningThread udpListener;

    /** TCP socket over which to send rc commands to runcontrol client. */
    Socket socket;

    /** Buffered data output stream associated with {@link #socket}. */
    private DataOutputStream out;

    /** Buffered data input stream associated with {@link #socket}. */
    private DataInputStream in;

    /**
     * This lock is for controlling access to the methods of this class.
     * The {@link #connect} and {@link #disconnect} methods of this object
     * cannot be called simultaneously with each other or any other method.
     */
    private final ReentrantReadWriteLock methodLock = new ReentrantReadWriteLock();

    /** Lock for calling {@link #connect} or {@link #disconnect}. */
    Lock connectLock = methodLock.writeLock();

    /** Lock for calling methods other than {@link #connect} or {@link #disconnect}. */
    Lock notConnectLock = methodLock.readLock();

    /** Lock to ensure that methods using the socket, write in sequence. */
    Lock socketLock = new ReentrantLock();

    /** Lock to ensure {@link #subscribe} and {@link #unsubscribe} calls are sequential. */
    Lock subscribeLock = new ReentrantLock();

    /** Used to create unique id numbers associated with a specific message subject/type pair. */
    AtomicInteger uniqueId;

    /** Level of debug output for this class. */
    int debug = cMsgConstants.debugNone;

    /**
     * Collection of all of this client's message subscriptions which are
     * {@link cMsgSubscription} objects. This set is synchronized.
     */
    Set<cMsgSubscription> subscriptions;

    /**
     * HashMap of all of this client's callback threads (keys) and their associated
     * subscriptions (values). The cMsgCallbackThread object of a new subscription
     * is returned (as an Object) as the unsubscribe handle. When this object is
     * passed as the single argument of an unsubscribe, a quick lookup of the
     * subscription is done using this hashmap.
     */
    private ConcurrentHashMap<Object, cMsgSubscription> unsubscriptions;

    /**
     * Collection of all of this client's {@link #subscribeAndGet} calls currently in execution.
     * SubscribeAndGets are very similar to subscriptions and can be thought of as
     * one-shot subscriptions.
     * Key is receiverSubscribeId object, value is {@link org.jlab.coda.cMsg.cMsgGetHelper}
     * object.
     */
    ConcurrentHashMap<Integer,cMsgGetHelper> subscribeAndGets;

    /**
     * Collection of all of this client's {@link #sendAndGet} calls currently in execution.
     * Key is senderToken object, value is {@link cMsgGetHelper} object.
     */
    ConcurrentHashMap<Integer,cMsgGetHelper> sendAndGets;

    /**
     * Returns a string back to the top level API user indicating the name
     * of the client that this server is communicating with.
     * @return name of client
     */
    public String getString() {
        return rcClientName;
    }


    /** Constructor. */
    public RCServer() throws cMsgException {
        domain = "rcs";
        subscriptions    = Collections.synchronizedSet(new HashSet<cMsgSubscription>(20));
        subscribeAndGets = new ConcurrentHashMap<Integer,cMsgGetHelper>(20);
        sendAndGets      = new ConcurrentHashMap<Integer,cMsgGetHelper>(20);
        unsubscriptions  = new ConcurrentHashMap<Object, cMsgSubscription>(20);
        uniqueId         = new AtomicInteger();

        // store our host's name
        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            throw new cMsgException("cMsg: cannot find host name");
        }

        class myShutdownHandler implements cMsgShutdownHandlerInterface {
            cMsgDomainInterface cMsgObject;

            myShutdownHandler(cMsgDomainInterface cMsgObject) {
                this.cMsgObject = cMsgObject;
            }

            public void handleShutdown() {
                try {cMsgObject.disconnect();}
                catch (cMsgException e) {}
            }
        }

        // Now make an instance of the shutdown handler
        setShutdownHandler(new myShutdownHandler(this));
    }


    /**
     * Method to connect to the rc client from this server.
     *
     * @throws cMsgException if there are problems parsing the UDL,
     *                       communication problems with the client,
     *                       or cannot start up a TCP listening thread
     */
    public void connect() throws cMsgException {

        try {
            parseUDL(UDLremainder);
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        }

        // cannot run this simultaneously with any other public method
        connectLock.lock();

        try {
            if (connected) {
//System.out.println("Using already established connection with RC client");
                return;
            }

            try {
                // Create an object to deliver messages to the RC client.
                createTCPClientConnection(rcClientHost, rcClientPort);

                // Start listening for tcp connections
                tcpListener = new rcTcpListeningThread(this);
                tcpListener.start();

                // Start listening for udp packets
                udpListener = new rcUdpListeningThread(this);
                udpListener.start();

                // Wait for indication listener threads are actually running before
                // continuing on. These thread must be running before we talk to
                // the client since the client tries to communicate with these
                // listening threads.
                synchronized (tcpListener) {
                    if (!tcpListener.isAlive()) {
                        try {
                            tcpListener.wait();
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Get the port selected for listening on
                localTcpPort = tcpListener.getPort();

                // Wait for indication listener threads are actually running before
                // continuing on. These thread must be running before we talk to
                // the client since the client tries to communicate with these
                // listening threads.
                synchronized (udpListener) {
                    if (!udpListener.isAlive()) {
                        try {
                            udpListener.wait();
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Get the port selected for communicating on
                localUdpPort = udpListener.getPort();

                // Send a special message giving our host & udp port.
                cMsgMessageFull msg = new cMsgMessageFull();
                msg.setSenderHost(InetAddress.getLocalHost().getCanonicalHostName());
                msg.setText(localUdpPort+":"+localTcpPort);
                deliverMessage(msg, cMsgConstants.msgRcConnect, true);

                connected = true;
            }
            catch (IOException e) {
                if (tcpListener != null) tcpListener.killThread();
                if (udpListener != null) udpListener.killThread();
                throw new cMsgException("cannot connect, IO error", e);
            }

        }
        finally {
            connectLock.unlock();
        }

        return;
    }


    /**
     * Method to close the connection to the rc client. This method results in this object
     * becoming functionally useless.
     */
    public void disconnect() {
        // cannot run this simultaneously with connect or send
        connectLock.lock();

        try {
            if (!connected) return;
            connected = false;

            udpListener.killThread();
            tcpListener.killThread();
            if (in != null)     try {in.close();}     catch (IOException e) {}
            if (out != null)    try {out.close();}    catch (IOException e) {}
            if (socket != null) try {socket.close();} catch (IOException e) {}
        }
        finally {
            connectLock.unlock();
        }
    }


    /**
     * Creates a TCP socket to a runcontrol client.
     *
     * @param  clientHost host client is running on
     * @param  clientPort tcp port client is listening on
     * @throws IOException if socket cannot be created
     */
    private void createTCPClientConnection(String clientHost, int clientPort) throws IOException {
        try {
            socket = new Socket(clientHost, clientPort);
            // Set tcpNoDelay so no packets are delayed
            socket.setTcpNoDelay(true);
            // set buffer size
            socket.setSendBufferSize(65535);

            // create buffered communication stream for efficiency
            in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
        }
        catch (IOException e) {
            if (in != null) try {in.close();} catch (IOException e1) {}
            if (out != null) try {out.close();} catch (IOException e1) {}
            if (socket != null) try {socket.close();} catch (IOException e1) {}
            throw e;
        }
    }


    /**
     * Method to parse the Universal Domain Locator (UDL) into its various components.
     *
     * @param udlRemainder partial UDL to parse
     * @throws cMsgException if udlRemainder is null
     */
     private void parseUDL(String udlRemainder) throws cMsgException, UnknownHostException {

        if (udlRemainder == null) {
            throw new cMsgException("invalid UDL");
        }

        // RC Server domain UDL is of the form:
        //       cMsg:rcs://<host>:<tcpPort>?port=<udpPort>
        //
        // The intial cMsg:rcs:// is stripped off by the top layer API
        //
        // Remember that for this domain:
        // 1) host is NOT optional (must start with an alphabetic character according to "man hosts" or
        //    may be in dotted form (129.57.35.21)
        // 2) host can be "localhost"
        // 3) tcp port is optional and defaults to 7654 (cMsgNetworkConstants.rcClientPort)
        // 4) the udp port to listen on may be given by the optional port parameter.
        //    if it's not given, the system assigns one

        Pattern pattern = Pattern.compile("((?:[a-zA-Z]+[\\w\\.\\-]*)|(?:[\\d]+\\.[\\d\\.]+)):?(\\d+)?/?(.*)");
        Matcher matcher = pattern.matcher(udlRemainder);

        String udlHost, udlPort, remainder, udpPort=null;

        if (matcher.find()) {
            // host
            udlHost = matcher.group(1);
            // port
            udlPort = matcher.group(2);
            // remainder
            remainder = matcher.group(3);

           if (debug >= cMsgConstants.debugInfo) {
                System.out.println("\nparseUDL: " +
                                   "\n  host = " + udlHost +
                                   "\n  port = " + udlPort +
                                   "\n  junk = " + remainder);
           }
        }
        else {
            throw new cMsgException("invalid UDL");
        }

        // if the host is "localhost", find the actual, fully qualified  host name
        if (udlHost.equalsIgnoreCase("localhost")) {
            rcClientHost = InetAddress.getLocalHost().getCanonicalHostName();
            if (debug >= cMsgConstants.debugWarn) {
                System.out.println("parseUDL: rctcp client host given as \"localhost\", substituting " +
                                   udlHost);
            }
        }
        else {
            rcClientHost = InetAddress.getByName(udlHost).getCanonicalHostName();
        }

        // get runcontrol client port or guess if it's not given
        if (udlPort != null && udlPort.length() > 0) {
            try {
                rcClientPort = Integer.parseInt(udlPort);
            }
            catch (NumberFormatException e) {
                rcClientPort = cMsgNetworkConstants.rcClientPort;
                if (debug >= cMsgConstants.debugWarn) {
                    System.out.println("parseUDL: non-integer port, guessing codaComponent port is " + rcClientPort);
                }
            }
        }
        else {
            rcClientPort = cMsgNetworkConstants.rcClientPort;
            if (debug >= cMsgConstants.debugWarn) {
                System.out.println("parseUDL: guessing codaComponent port is " + rcClientPort);
            }
        }

        if (rcClientPort < 1024 || rcClientPort > 65535) {
            throw new cMsgException("parseUDL: illegal port number");
        }

        // any remaining UDL is ...
        if (remainder == null) {
            UDLremainder = "";
        }
        else {
            UDLremainder = remainder;
        }

        // Find our udp port in UDL if it exists ...
        if (remainder != null) {
            boolean foundMatch = false;
            // look for ?key=value& or &key=value& pairs
            Pattern pat = Pattern.compile("(?:[&\\?](\\w+)=(\\w+)(?=&))");
            Matcher mat = pat.matcher(remainder + "&");

            loop: while (mat.find()) {
                for (int i = 0; i < mat.groupCount() + 1; i++) {
                    // if key = port ...
                    if (mat.group(i).equalsIgnoreCase("port")) {
                        // udp port must be value
                        udpPort = mat.group(i + 1);
                        if (debug >= cMsgConstants.debugInfo) {
                            System.out.println("  udp port = " + udpPort);
                        }
                        foundMatch = true;
                        break loop;
                    }
                }
            }

            // if udp port given in UDL ...
            if (foundMatch) {
                try {
                    localUdpPort = Integer.parseInt(udpPort);
                }
                catch (NumberFormatException e) {
                    if (debug >= cMsgConstants.debugWarn) {
                        System.out.println("parseUDL: non-integer given for udp port");
                    }
                }
            }
        }
    }


    /**
     * Method to send a message/command to the rc client. The command is sent as a
     * string in the message's text field.
     *
     * @param message message to send
     * @throws cMsgException if there are communication problems with the server;
     *                       text is null or blank
     */
    public void send(cMsgMessage message) throws cMsgException {

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // protect communicatons over socket for thread safety
        socketLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to server");
            }
            deliverMessage(message, cMsgConstants.msgSubscribeResponse, false);
        }
        catch (IOException e) {
            throw new cMsgException(e.getMessage(),e);
        }
        finally {
            socketLock.unlock();
            notConnectLock.unlock();
        }

    }


    /**
     * Method to deliver a message to a client. This is a somewhat modified "copy"
     * of {@link org.jlab.coda.cMsg.cMsgDomain.server.cMsgMessageDeliverer#deliverMessageReal(cMsgMessage, int, boolean)}.
     * It leaves open the possibility that many fields may be null and still will not
     * barf. It's always possible that a knowledgable user could create an object of
     * type cMsgMessageFull and pass that in. That way the user gets to set all fields.
     *
     * @param msg message to be sent
     * @param msgType type of message to be sent
     * @param getResponse true if response expected, else false
     * @throws IOException if the message cannot be sent over the channel
     */
    private void deliverMessage(cMsgMessage msg, int msgType, boolean getResponse) throws IOException {

        int[] len = new int[6]; // int arrays are initialized to 0

        if (msg.getSender()     != null) len[0] = msg.getSender().length();
        if (msg.getSenderHost() != null) len[1] = msg.getSenderHost().length();
        if (msg.getSubject()    != null) len[2] = msg.getSubject().length();
        if (msg.getType()       != null) len[3] = msg.getType().length();
        if (msg.getCreator()    != null) len[4] = msg.getCreator().length();
        if (msg.getText()       != null) len[5] = msg.getText().length();

        int binLength;
        if (msg.getByteArray() == null) {
            binLength = 0;
        }
        else {
            binLength = msg.getByteArrayLength();
        }

        // size of everything sent (except "size" itself which is first integer)
        int size = len[0] + len[1] + len[2] + len[3] + len[4] + len[5] +
                binLength + 4 * 19;

        out.writeInt(size);
        out.writeInt(msgType);
        out.writeInt(msg.getVersion());
        out.writeInt(0); // reserved for future use
        out.writeInt(msg.getUserInt());
        out.writeInt(msg.getInfo());

        // send the time in milliseconds as 2, 32 bit integers
        long now = new Date().getTime();
        out.writeInt((int) (now >>> 32)); // higher 32 bits
        out.writeInt((int) (now & 0x00000000FFFFFFFFL)); // lower 32 bits
        out.writeInt((int) (msg.getUserTime().getTime() >>> 32));
        out.writeInt((int) (msg.getUserTime().getTime() & 0x00000000FFFFFFFFL));

        out.writeInt(msg.getSysMsgId());
        out.writeInt(msg.getSenderToken());
        out.writeInt(len[0]);
        out.writeInt(len[1]);
        out.writeInt(len[2]);
        out.writeInt(len[3]);
        out.writeInt(len[4]);
        out.writeInt(len[5]);
        out.writeInt(binLength);
        out.writeInt(0); // never acknowledge in rc/rcServer domain

        // write strings
        try {
            if (msg.getSender()     != null) out.write(msg.getSender().getBytes("US-ASCII"));
            if (msg.getSenderHost() != null) out.write(msg.getSenderHost().getBytes("US-ASCII"));
            if (msg.getSubject()    != null) out.write(msg.getSubject().getBytes("US-ASCII"));
            if (msg.getType()       != null) out.write(msg.getType().getBytes("US-ASCII"));
            if (msg.getCreator()    != null) out.write(msg.getCreator().getBytes("US-ASCII"));
            if (msg.getText()       != null) out.write(msg.getText().getBytes("US-ASCII"));

            if (binLength > 0) {
                out.write(msg.getByteArray(),
                          msg.getByteArrayOffset(),
                          binLength);
            }
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        out.flush();

        if (getResponse) {
            int lengthClientName = in.readInt();

            // read all string bytes
            byte[] bytes = new byte[lengthClientName];
            in.readFully(bytes, 0, lengthClientName);

            // read subject
            rcClientName = new String(bytes, 0, lengthClientName, "US-ASCII");
//System.out.println("client name = " + name);
        }

        return;
    }


//-----------------------------------------------------------------------------


    /**
     * This is a method to subscribe to receive messages of a subject and type from the rc client.
     * The combination of arguments must be unique. In other words, only 1 subscription is
     * allowed for a given set of subject, type, callback, and userObj.
     *
     * If the subject and type are both null, a "default" subscription is made, meaning any
     * received messages which do not match any other subscription are given to the default
     * subscription.
     *
     * @param subject message subject
     * @param type    message type
     * @param cb      callback object whose single method is called upon receiving a message
     *                of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @return handle object to be used for unsubscribing
     * @throws cMsgException if the callback is null; an identical subscription already exists;
     *                       if not connected to an rc client
     */
    public Object subscribe(String subject, String type, cMsgCallbackInterface cb, Object userObj)
            throws cMsgException {

        // check args first
        if (cb == null) {
            throw new cMsgException("callback argument is null");
        }

        cMsgCallbackThread cbThread = null;
        cMsgSubscription newSub;

        try {
            // cannot run this simultaneously with connect or disconnect
            notConnectLock.lock();
            // cannot run this simultaneously with unsubscribe or itself
            subscribeLock.lock();

            if (!connected) {
                throw new cMsgException("not connected to rc client");
            }

            // add to callback list if subscription to same subject/type exists

            int id;

            // client listening thread may be interating thru subscriptions concurrently
            // and we may change set structure
            synchronized (subscriptions) {

                // for each subscription ...
                for (cMsgSubscription sub : subscriptions) {
                    // Check to see if subscription to subject & type exist already
                    boolean subjectMatches = false, typeMatches = false;

                    // Check for null subject and type in subscription (which denotes default subscription)
                    if (sub.getSubject() == null) {
                        if (subject == null) subjectMatches = true;
                    }
                    else if (sub.getSubject().equals(subject)) {
                        subjectMatches = true;
                    }

                    if (sub.getType() == null) {
                        if (type == null) typeMatches = true;
                    }
                    else if (sub.getType().equals(type)) {
                        typeMatches = true;
                    }

                    // If subscription to subject & type exist already...
                    if (subjectMatches && typeMatches)  {
                        // Only add another callback if the callback/userObj
                        // combination does NOT already exist. In other words,
                        // a callback/argument pair must be unique for a single
                        // subscription. Otherwise it is impossible to unsubscribe.

                        // for each callback listed ...
                        for (cMsgCallbackThread cbt : sub.getCallbacks()) {
                            // if callback and user arg already exist, reject the subscription
                            if ((cbt.getCallback() == cb) && (cbt.getArg() == userObj)) {
                                throw new cMsgException("subscription already exists");
                            }
                        }

                        // add to existing set of callbacks
                        cbThread = new cMsgCallbackThread(cb, userObj, domain, subject, type);
                        sub.addCallback(cbThread);
                        unsubscriptions.put(cbThread, sub);
                        return cbThread;
                    }
                }

                // If we're here, the subscription to that subject & type does not exist yet.
                // We need to create and register it.

                // First generate a unique id for the receiveSubscribeId field.
                // (Left over from cMsg domain).
                id = uniqueId.getAndIncrement();

                // add a new subscription & callback
                cbThread = new cMsgCallbackThread(cb, userObj, domain, subject, type);
                newSub = new cMsgSubscription(subject, type, id, cbThread);
                unsubscriptions.put(cbThread, newSub);

                // client listening thread may be interating thru subscriptions concurrently
                // and we're changing the set structure
                subscriptions.add(newSub);
            }
        }
        finally {
            subscribeLock.unlock();
            notConnectLock.unlock();
        }

        return cbThread;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to unsubscribe a previous subscription.
     *
     * @param obj the object "handle" returned from a subscribe call
     * @throws cMsgException if there is no connection with the rc client; obj is null
     */
    public void unsubscribe(Object obj)
            throws cMsgException {

        // check arg first
        if (obj == null) {
            throw new cMsgException("argument is null");
        }

        cMsgSubscription sub = unsubscriptions.remove(obj);
        // already unsubscribed
        if (sub == null) {
            return;
        }
        cMsgCallbackThread cbThread = (cMsgCallbackThread) obj;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // cannot run this simultaneously with subscribe or itself
        subscribeLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to rc client");
            }

            // Delete stuff from hashes & kill threads.
            // If there are still callbacks left,
            // don't unsubscribe for this subject/type.
            cbThread.dieNow(false);
            synchronized (subscriptions) {
                sub.getCallbacks().remove(cbThread);
                if (sub.numberOfCallbacks() < 1) {
                    subscriptions.remove(sub);
                }
            }
        }
        finally {
            subscribeLock.unlock();
            notConnectLock.unlock();
        }

    }


//-----------------------------------------------------------------------------



    /**
     * This method is like a one-time subscribe. The rc server grabs the first incoming
     * message of the requested subject and type and sends that to the caller.
     *
     * NOTE: Disconnecting when one thread is in the waiting part of a subscribeAndGet may cause that
     * thread to block forever. It is best to always use a timeout with subscribeAndGet so the thread
     * is assured of eventually resuming execution.
     *
     * @param subject subject of message desired from server
     * @param type type of message desired from server
     * @param timeout time in milliseconds to wait for a message
     * @return response message
     * @throws cMsgException if there are communication problems with rc client;
     *                       subject and/or type is null or blank
     * @throws java.util.concurrent.TimeoutException if timeout occurs
     */
    public cMsgMessage subscribeAndGet(String subject, String type, int timeout)
            throws cMsgException, TimeoutException {

        // check args first
        if (subject == null || type == null) {
            throw new cMsgException("message subject or type is null");
        }
        else if (subject.length() < 1 || type.length() < 1) {
            throw new cMsgException("message subject or type is blank string");
        }

        int id = 0;
        cMsgGetHelper helper = null;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to rc client");
            }

            // First generate a unique id for the receiveSubscribeId and senderToken field.
            // (artifact of cMsg domain).
            id = uniqueId.getAndIncrement();

            // create cMsgGetHelper object (not callback thread object)
            helper = new cMsgGetHelper(subject, type);

            // keep track of get calls
            subscribeAndGets.put(id, helper);
        }
        // release lock 'cause we can't block connect/disconnect forever
        finally {
            notConnectLock.unlock();
        }

        // WAIT for the msg-receiving thread to wake us up
        try {
            synchronized (helper) {
                if (timeout > 0) {
                    helper.wait(timeout);
                }
                else {
                    helper.wait();
                }
            }
        }
        catch (InterruptedException e) {
        }

        // Check the message stored for us in helper.
        if (helper.isTimedOut()) {
            // remove the get
            subscribeAndGets.remove(id);
            throw new TimeoutException();
        }

        return helper.getMessage();
    }



    /**
     * The message is sent as it would be in the {@link #send} method except that the
     * senderToken and creator are set. A marked response can be received from a client
     * regardless of its subject or type.
     *
     * NOTE: Disconnecting when one thread is in the waiting part of a sendAndGet may cause that
     * thread to block forever. It is best to always use a timeout with sendAndGet so the thread
     * is assured of eventually resuming execution.
     *
     * @param message message sent to client
     * @param timeout time in milliseconds to wait for a reponse message
     * @return response message
     * @throws cMsgException if there are communication problems with the client;
     *                       subject and/or type is null or blank
     * @throws TimeoutException if timeout occurs
     */
    public cMsgMessage sendAndGet(cMsgMessage message, int timeout)
            throws cMsgException, TimeoutException {

        String subject = message.getSubject();
        String type    = message.getType();

        // check args first
        if (subject == null || type == null) {
            throw new cMsgException("message subject and/or type is null");
        }
        else if (subject.length() < 1 || type.length() < 1) {
            throw new cMsgException("message subject or type is blank string");
        }

        int id = 0;
        cMsgGetHelper helper = null;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to rc client");
            }

            // We're expecting a specific response, so the senderToken is sent back
            // in the response message, allowing us to run the correct callback.
            id = uniqueId.getAndIncrement();

            // for get, create cMsgHolder object (not callback thread object)
            helper = new cMsgGetHelper();

            // track specific get requests
            sendAndGets.put(id, helper);

            cMsgMessageFull fullMsg = new cMsgMessageFull(message);
            if (fullMsg.getCreator() == null) fullMsg.setCreator("rcServer");
            fullMsg.setSenderToken(id);
            fullMsg.setGetRequest(true);
            deliverMessage(fullMsg, cMsgConstants.msgSubscribeResponse, false);
        }
        catch (IOException e) {
            throw new cMsgException(e.getMessage(),e);
        }
        // release lock 'cause we can't block connect/disconnect forever
        finally {
            notConnectLock.unlock();
        }

        // WAIT for the msg-receiving thread to wake us up
        try {
            synchronized (helper) {
                if (timeout > 0) {
                    helper.wait(timeout);
                }
                else {
                    helper.wait();
                }
            }
        }
        catch (InterruptedException e) {
        }

        // Check the message stored for us in helper.
        if (helper.isTimedOut()) {
            // remove the get
            sendAndGets.remove(id);
            throw new TimeoutException();
        }

        return helper.getMessage();
    }


}
