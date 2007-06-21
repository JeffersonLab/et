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

package org.jlab.coda.cMsg.RCBroadcastDomain;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.cMsgGetHelper;
import org.jlab.coda.cMsg.cMsgCallbackThread;

import java.net.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.io.*;

/**
 * This class implements the runcontrol broadcast (rdb) domain.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class RCBroadcast extends cMsgDomainAdapter {

    /** This runcontrol broadcast server's UDP listening port obtained from UDL or default value. */
    int broadcastPort;

    /** The local port used temporarily while broadcasting for other RCBroadcast servers. */
    int localTempPort;

    /** Socket over which to UDP broadcast to and check for other RCBroadcast servers. */
    DatagramSocket udpSocket;

    /** Signal to coordinate the broadcasting and waiting for responses. */
    CountDownLatch broadcastResponse = new CountDownLatch(1);

    /** The host of the responding server to initial broadcast probes of the local subnet. */
    String respondingHost;

    /** Runcontrol's experiment id. */
    String expid;

    /** Timeout in milliseconds to wait for server to respond to broadcasts. Default is 2 sec. */
    int broadcastTimeout = 2000;

    volatile boolean acceptingClients;

    /** Thread that listens for UDP broad/unicasts to this server and responds. */
    rcListeningThread listener;

    /**
     * This lock is for controlling access to the methods of this class.
     * The {@link #connect} and {@link #disconnect} methods of this object cannot be
     * called simultaneously with each other or any other method.
     */
    private final ReentrantReadWriteLock methodLock = new ReentrantReadWriteLock();

    /** Lock for calling {@link #connect} or {@link #disconnect}. */
    Lock connectLock = methodLock.writeLock();

    /** Lock for calling methods other than {@link #connect} or {@link #disconnect}. */
    Lock notConnectLock = methodLock.readLock();

    /** Lock to ensure {@link #subscribe} and {@link #unsubscribe} calls are sequential. */
    Lock subscribeLock = new ReentrantLock();

    /** Level of debug output for this class. */
    int debug = cMsgConstants.debugError;

   /**
     * Collection of all of this server's subscriptions which are
     * {@link cMsgSubscription} objects. This set is synchronized.
     */
    Set<cMsgSubscription> subscriptions;

    /**
     * Collection of all of this server's {@link #subscribeAndGet} calls currently in execution.
     * SubscribeAndGets are very similar to subscriptions and can be thought of as
     * one-shot subscriptions. This set is synchronized and contains objects of class
     * {@link org.jlab.coda.cMsg.cMsgGetHelper}.
     */
    Set<cMsgGetHelper> subscribeAndGets;

    /**
     * HashMap of all of this server's callback threads (keys) and their associated
     * subscriptions (values). The cMsgCallbackThread object of a new subscription
     * is returned (as an Object) as the unsubscribe handle. When this object is
     * passed as the single argument of an unsubscribe, a quick lookup of the
     * subscription is done using this hashmap.
     */
    private ConcurrentHashMap<Object, cMsgSubscription> unsubscriptions;


    public RCBroadcast() throws cMsgException {
        domain = "rcb";
        subscriptions    = Collections.synchronizedSet(new HashSet<cMsgSubscription>(20));
        subscribeAndGets = Collections.synchronizedSet(new HashSet<cMsgGetHelper>(20));
        unsubscriptions  = new ConcurrentHashMap<Object, cMsgSubscription>(20);

        // store our host's name
        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            throw new cMsgException("cMsg: cannot find host name", e);
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
     * Method to connect to rc clients from this server.
     *
     * @throws org.jlab.coda.cMsg.cMsgException if there are problems parsing the UDL or
     *                       creating the UDP socket
     */
    public void connect() throws cMsgException {

        parseUDL(UDLremainder);

        // cannot run this simultaneously with any other public method
        connectLock.lock();

        try {
            if (connected) return;

            // Start listening for udp packets
            listener = new rcListeningThread(this, broadcastPort);
            listener.start();

            // Wait for indication listener thread is actually running before
            // continuing on. This thread must be running before we look to
            // see what other servers are out there.
            synchronized (listener) {
                if (!listener.isAlive()) {
                    try {
                        listener.wait();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            // First need to check to see if there is another RCBroadcastServer
            // on this port with this EXPID. If so, abandon ship.
            //-------------------------------------------------------
            // broadcast on local subnet to find other servers
            //-------------------------------------------------------
            DatagramPacket udpPacket;

            // create byte array for broadcast
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            DataOutputStream out = new DataOutputStream(baos);

            try {
                // Put our TCP listening port, our name, and
                // the EXPID (experiment id string) into byte array.

                // this broadcast is from an rc broadcast domain server
                out.writeInt(cMsgNetworkConstants.rcDomainBroadcastServer);
                // port is irrelevant
                out.writeInt(0);
                out.writeInt(name.length());
                out.writeInt(expid.length());
                try {
                    out.write(name.getBytes("US-ASCII"));
                    out.write(expid.getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {
                }
                out.flush();
                out.close();

                // create socket to send broadcasts to other RCBroadcast servers
                udpSocket = new DatagramSocket();
                localTempPort = udpSocket.getLocalPort();

                InetAddress rcServerBroadcastAddress=null;
                try {rcServerBroadcastAddress = InetAddress.getByName("255.255.255.255"); }
                catch (UnknownHostException e) {}

                // create packet to broadcast from the byte array
                byte[] buf = baos.toByteArray();
                udpPacket = new DatagramPacket(buf, buf.length,
                                               rcServerBroadcastAddress,
                                               broadcastPort);
                baos.close();
            }
            catch (IOException e) {
                listener.killThread();
                try { out.close();} catch (IOException e1) {}
                try {baos.close();} catch (IOException e1) {}
                if (udpSocket != null) udpSocket.close();

                if (debug >= cMsgConstants.debugError) {
                    System.out.println("I/O Error: " + e);
                }
                throw new cMsgException(e.getMessage());
            }

            // create a thread which will send our broadcast
            Broadcaster sender = new Broadcaster(udpPacket);
            sender.start();

            // wait up to broadcast timeout seconds
            boolean response = false;
            try {
                if (broadcastResponse.await(broadcastTimeout, TimeUnit.MILLISECONDS)) {
//System.out.println("Got a response!");
                    response = true;
                }
            }
            catch (InterruptedException e) { }

            sender.interrupt();

            if (response) {
//System.out.println("Another RCBroadcast server is running at port "  + broadcastPort +
//                   " host " + respondingHost + " with EXPID = " + expid);
                // stop listening thread
                listener.killThread();
                udpSocket.close();
                try {Thread.sleep(500);}
                catch (InterruptedException e) {}

                throw new cMsgException("Another RCBroadcast server is running at port " + broadcastPort +
                                        " host " + respondingHost + " with EXPID = " + expid);
            }
//System.out.println("No other RCBroadcast server is running, so start this one up!");
            acceptingClients = true;

            // Releasing the socket after above line diminishes the chance that
            // a client on the same host will grab that port and be filtered
            // out as being this same server's broadcast.
            udpSocket.close();

            // reclaim memory
            broadcastResponse = null;

            connected = true;
        }
        finally {
            connectLock.unlock();
        }

        return;
    }


    /**
     * Method to stop listening for packets from rc clients.
     */
    public void disconnect() {
        // cannot run this simultaneously with connect or send
        connectLock.lock();
        try {
            if (!connected) return;
            connected = false;
            listener.killThread();
        }
        finally {
            connectLock.unlock();
        }
    }


    /**
     * Method to send an abort command to the rc client. Fill in the senderHost
     * with the host and the userInt with the port of the rc client to abort.
     *
     * @param message message to send
     * @throws cMsgException if there are communication problems with the rc client
     */
    public void send(cMsgMessage message) throws cMsgException {

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        Socket socket = null;
        DataOutputStream out = null;

        try {
            if (!connected) {
                throw new cMsgException("not connected to server");
            }
            socket = new Socket(message.getSenderHost(), message.getUserInt());
            // Set tcpNoDelay so packet not delayed
            socket.setTcpNoDelay(true);

            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.writeInt(4);
            out.writeInt(cMsgConstants.msgRcAbortConnect);
            out.flush();

            out.close();
            socket.close();
        }
        catch (IOException e) {
            if (out != null) try {out.close();} catch (IOException e1) {}
            if (socket != null) try {socket.close();} catch (IOException e1) {}
            throw new cMsgException(e.getMessage(), e);
        }
        finally {
            notConnectLock.unlock();
        }

    }


    /**
      * Method to parse the Universal Domain Locator (UDL) into its various components.
      *
      * @param udlRemainder partial UDL to parse
      * @throws cMsgException if udlRemainder is null
      */
    private void parseUDL(String udlRemainder) throws cMsgException {

        if (udlRemainder == null) {
            throw new cMsgException("invalid UDL");
        }

        // RC Broadcast domain UDL is of the form:
        //       cMsg:rcb://<udpPort>?expid=<expid>&broadcastTO=<TO>
        //
        // The intial cMsg:rcb:// is stripped off by the top layer API
        //
        // Remember that for this domain:
        // 1) udp listening port is optional and defaults to 6543 (cMsgNetworkConstants.rcBroadcastPort)
        // 2) the experiment id is given by the optional parameter expid. If none is
        //    given, the environmental variable EXPID is used. if that is not defined,
        //    an exception is thrown

        Pattern pattern = Pattern.compile("(\\d+)?/?(.*)");
        Matcher matcher = pattern.matcher(udlRemainder);

        String udlPort, remainder;

        if (matcher.find()) {
            // port
            udlPort = matcher.group(1);
            // remainder
            remainder = matcher.group(2);

            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("\nparseUDL: " +
                               "\n  port = " + udlPort +
                               "\n  junk = " + remainder);
            }
        }
        else {
            throw new cMsgException("invalid UDL");
        }

        // get broadcast port or use env var or default if it's not given
        if (udlPort != null && udlPort.length() > 0) {
            try {
                broadcastPort = Integer.parseInt(udlPort);
            }
            catch (NumberFormatException e) {
                if (debug >= cMsgConstants.debugWarn) {
                    System.out.println("parseUDL: non-integer port specified in UDL = " + udlPort);
                }
            }
        }

        // next, try the environmental variable RC_BROADCAST_PORT
        if (broadcastPort < 1) {
            try {
                String env = System.getenv("RC_BROADCAST_PORT");
                if (env != null) {
                    broadcastPort = Integer.parseInt(env);
                }
            }
            catch (NumberFormatException ex) {
                System.out.println("parseUDL: bad port number specified in CMSG_BROADCAST_PORT env variable");
            }
        }

        // use default as last resort
        if (broadcastPort < 1) {
            broadcastPort = cMsgNetworkConstants.rcBroadcastPort;
            if (debug >= cMsgConstants.debugWarn) {
                System.out.println("parseUDL: using default broadcast port = " + broadcastPort);
            }
        }

        if (broadcastPort < 1024 || broadcastPort > 65535) {
            throw new cMsgException("parseUDL: illegal port number");
        }

        // any remaining UDL is ...
        if (remainder == null) {
            UDLremainder = "";
        }
        else {
            UDLremainder = remainder;
        }

        // if no remaining UDL to parse, return
        if (remainder == null) {
            return;
        }

        // look for ?expid=value& or &expid=value&
        pattern = Pattern.compile("[\\?&]expid=([\\w\\-]+)");
        matcher = pattern.matcher(remainder);
        if (matcher.find()) {
            expid = matcher.group(1);
//System.out.println("parsed expid = " + expid);
        }
        else {
            expid = System.getenv("EXPID");
            if (expid == null) {
             throw new cMsgException("Experiment ID is unknown");
            }
//System.out.println("env expid = " + expid);
        }


        // now look for ?broadcastTO=value& or &broadcastTO=value&
        pattern = Pattern.compile("[\\?&]broadcastTO=([0-9]+)");
        matcher = pattern.matcher(remainder);
        if (matcher.find()) {
            try {
                broadcastTimeout = 1000 * Integer.parseInt(matcher.group(1));
                if (broadcastTimeout < 1) {
                    broadcastTimeout = 2000;
                }
//System.out.println("broadcast TO = " + broadcastTimeout);
            }
            catch (NumberFormatException e) {
                // ignore error and keep default
            }
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to subscribe to receive messages from rc clients. In this domain,
     * subject and type are ignored and set to the preset values of "s" and "t".
     * The combination of arguments must be unique. In other words, only 1 subscription is
     * allowed for a given set of callback and userObj.
     *
     * @param subject ignored and set to "s"
     * @param type ignored and set to "t"
     * @param cb      callback object whose single method is called upon receiving a message
     *                of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @return handle object to be used for unsubscribing
     * @throws cMsgException if the callback, subject and/or type is null or blank;
     *                       an identical subscription already exists; if not connected
     *                       to an rc client
     */
    public Object subscribe(String subject, String type, cMsgCallbackInterface cb, Object userObj)
            throws cMsgException {

        // Subject and type are ignored in this domain so just
        // set them to some standard values
        subject = "s";
        type    = "t";

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

            // Add to callback list if subscription to same subject/type exists.

            // Listening thread may be interating thru subscriptions concurrently
            // and we may change set structure so synchronize.
            synchronized (subscriptions) {

                // for each subscription ...
                for (cMsgSubscription sub : subscriptions) {
                    // If subscription to subject & type exist already...
                    if (sub.getSubject().equals(subject) && sub.getType().equals(type)) {
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

                // add a new subscription & callback
                cbThread = new cMsgCallbackThread(cb, userObj, domain, subject, type);
                newSub = new cMsgSubscription(subject, type, 0, cbThread);
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
     * @throws cMsgException if there is no connection with rc clients; object is null
     */
    public void unsubscribe(Object obj) throws cMsgException {

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
     * This method is like a one-time subscribe. The rc server grabs an incoming
     * message and sends that to the caller. In this domain, subject and type are
     * ignored and set to the preset values of "s" and "t".
     *
     * @param subject ignored and set to "s"
     * @param type ignored and set to "t"
     * @param timeout time in milliseconds to wait for a message
     * @return response message
     * @throws cMsgException if there are communication problems with rc client;
     *                       subject and/or type is null or blank
     * @throws java.util.concurrent.TimeoutException if timeout occurs
     */
    public cMsgMessage subscribeAndGet(String subject, String type, int timeout)
            throws cMsgException, TimeoutException {

        // Subject and type are ignored in this domain so just
        // set them to some standard values
        subject = "s";
        type    = "t";

        cMsgGetHelper helper = null;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to rc client");
            }

            // create cMsgGetHelper object (not callback thread object)
            helper = new cMsgGetHelper(subject, type);

            // keep track of get calls
            subscribeAndGets.add(helper);
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
            subscribeAndGets.remove(helper);
            throw new TimeoutException();
        }

        // If msg is received, server has removed subscription from his records.
        // Client listening thread has also removed subscription from client's
        // records (subscribeAndGets HashSet).
        return helper.getMessage();
    }



    /**
     * This class defines a thread to broadcast a UDP packet to the
     * RC Broadcast server every second.
     */
    class Broadcaster extends Thread {

        DatagramPacket packet;

        Broadcaster(DatagramPacket udpPacket) {
            packet = udpPacket;
        }


        public void run() {

            try {
                /* A slight delay here will help the main thread (calling connect)
                * to be already waiting for a response from the server when we
                * broadcast to the server here (prompting that response). This
                * will help insure no responses will be lost.
                */
                Thread.sleep(100);

                while (true) {

                    try {
//System.out.println("  Send broadcast packet to RC Broadcast server");
                        udpSocket.send(packet);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    Thread.sleep(500);
                }
            }
            catch (InterruptedException e) {
                // time to quit
 //System.out.println("Interrupted sender");
            }
        }
    }

}
