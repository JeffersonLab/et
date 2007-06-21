/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 31-Mar-2006, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.RCDomain;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.cMsgCallbackThread;

import java.io.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.Date;
import java.util.Collections;
import java.util.HashSet;
import java.net.*;
import java.nio.channels.ServerSocketChannel;

/**
 * This class implements a cMsg client in the RunControl (or RC) domain.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class RunControl extends cMsgDomainAdapter {
    /** Port number to listen on. */
    int port;

    /** Port number from which to start looking for a suitable listening port. */
    int startingPort;

    /** Server channel (contains socket). */
     ServerSocketChannel serverChannel;

    /** Thread listening for TCP connections and responding to RC domain server commands. */
    rcListeningThread listeningThread;

    /** Name of local host. */
    String localHost;

    /** Coda experiment id under which this is running. */
    String expid;

    /** Timeout in milliseconds to wait for server to respond to broadcasts. */
    int broadcastTimeout;

    /**
     * Timeout in seconds to wait for RC server to finish connection
     * once RC broadcast server responds.
     */
    int connectTimeout;

    /** Quit a connection in progress if true. */
    volatile boolean abandonConnection;

    /** RunControl server's net address obtained from broadcast resonse. */
    volatile InetAddress rcServerAddress;

    /** RunControl server's UDP listening port obtained from {@link #connect}. */
    volatile int rcUdpServerPort;

    /** RunControl server's TCP listening port obtained from {@link #connect}. */
    volatile int rcTcpServerPort;

    /** RunControl server's net address obtained from UDL. */
    InetAddress rcServerBroadcastAddress;

    /** RunControl server's broadcast listening port obtained from UDL. */
    int rcServerBroadcastPort;

    /** Packet to send over UDP to RC server to implement {@link #send}. */
    DatagramPacket sendUdpPacket;

    /** Socket over which to UDP broadcast to and receive UDP packets from the RCBroadcast server. */
    DatagramSocket broadcastUdpSocket;

    /** Socket over which to end messages to the RC server over UDP. */
    DatagramSocket udpSocket;

    /** Socket over which to send messages to the RC server over TCP. */
    Socket tcpSocket;

    /** Output TCP data stream from this client to the RC server. */
    DataOutputStream domainOut;

    /**
     * Collection of all of this client's message subscriptions which are
     * {@link cMsgSubscription} objects. This set is synchronized. A client is either
     * a regular client or a bridge but not both. That means it does not matter that
     * a bridge client will add namespace data to the stored subscription but a regular
     * client will not.
     */
    public Set<cMsgSubscription> subscriptions;

    /**
     * HashMap of all of this client's callback threads (keys) and their associated
     * subscriptions (values). The cMsgCallbackThread object of a new subscription
     * is returned (as an Object) as the unsubscribe handle. When this object is
     * passed as the single argument of an unsubscribe, a quick lookup of the
     * subscription is done using this hashmap.
     */
    private ConcurrentHashMap<Object, cMsgSubscription> unsubscriptions;

    /**
     * This lock is for controlling access to the methods of this class.
     * It is inherently more flexible than synchronizing code. The {@link #connect}
     * and {@link #disconnect} methods of this object cannot be called simultaneously
     * with each other or any other method. However, the {@link #send} method is
     * thread-safe and may be called simultaneously from multiple threads.
     */
    private final ReentrantReadWriteLock methodLock = new ReentrantReadWriteLock();

    /** Lock for calling {@link #connect} or {@link #disconnect}. */
    Lock connectLock = methodLock.writeLock();

    /** Lock for calling methods other than {@link #connect} or {@link #disconnect}. */
    Lock notConnectLock = methodLock.readLock();

    /** Lock to ensure {@link #subscribe} and {@link #unsubscribe} calls are sequential. */
    Lock subscribeLock = new ReentrantLock();

    /** Lock to ensure that methods using the socket, write in sequence. */
    Lock socketLock = new ReentrantLock();

    /** Used to create unique id numbers associated with a specific message subject/type pair. */
    AtomicInteger uniqueId;

    /** Signal to coordinate the broadcasting and waiting for responses. */
    CountDownLatch broadcastResponse;

    /** Signal to coordinate the finishing of the 3-leg connect method. */
    CountDownLatch connectCompletion;

    /** Level of debug output for this class. */
    int debug = cMsgConstants.debugNone;


    /**
        * Converts 4 bytes of a byte array into an integer.
        *
        * @param b byte array
        * @param off offset into the byte array (0 = start at first element)
        * @return integer value
        */
       private static final int bytesToInt(byte[] b, int off) {
         int result = ((b[off]  &0xff) << 24) |
                      ((b[off+1]&0xff) << 16) |
                      ((b[off+2]&0xff) <<  8) |
                       (b[off+3]&0xff);
         return result;
       }




    /** Constructor. */
    public RunControl() throws cMsgException {
        domain = "rc";
        subscriptions    = Collections.synchronizedSet(new HashSet<cMsgSubscription>(20));
        uniqueId         = new AtomicInteger();
        unsubscriptions  = new ConcurrentHashMap<Object, cMsgSubscription>(20);

        try {
            localHost = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            throw new cMsgException(e.getMessage());
        }
    }


    /**
     * Method to connect to the codaComponent server from this client.
     *
     * @throws org.jlab.coda.cMsg.cMsgException if there are problems parsing the UDL or
     *                       communication problems with the server(s)
     */
    public void connect() throws cMsgException {
        parseUDL(UDLremainder);

        // cannot run this simultaneously with any other public method
        connectLock.lock();

        try {
            if (connected) return;
//System.out.println("Connecting");

            // set the latches
            broadcastResponse = new CountDownLatch(1);
            connectCompletion = new CountDownLatch(1);

            // read env variable for starting port number
            try {
                String env = System.getenv("CMSG_RC_CLIENT_PORT");
                if (env != null) {
                    startingPort = Integer.parseInt(env);
                }
            }
            catch (NumberFormatException ex) {
            }

            // port #'s < 1024 are reserved
            if (startingPort < 1024) {
                startingPort = cMsgNetworkConstants.rcClientPort;
            }

            // At this point, find a port to bind to. If that isn't possible, throw
            // an exception.
            try {
                serverChannel = ServerSocketChannel.open();
            }
            catch (IOException ex) {
                ex.printStackTrace();
                throw new cMsgException("connect: cannot open a listening socket", ex);
            }

            port = startingPort;
            ServerSocket listeningSocket = serverChannel.socket();

            while (true) {
                try {
                    listeningSocket.bind(new InetSocketAddress(port));
                    break;
                }
                catch (IOException ex) {
                    // try another port by adding one
                    if (port < 65535) {
                        port++;
                        try { Thread.sleep(100);  }
                        catch (InterruptedException e) {}
                    }
                    else {
                        // close channel
                        try { serverChannel.close(); }
                        catch (IOException e) { }

                        ex.printStackTrace();
                        throw new cMsgException("connect: cannot find port to listen on", ex);
                    }
                }
            }

            // launch thread and start listening on receive socket
            listeningThread = new rcListeningThread(this, serverChannel);
            listeningThread.start();

            // Wait for indication thread is actually running before
            // continuing on. This thread must be running before we talk to
            // the name server since the server tries to communicate with
            // the listening thread.

            synchronized (listeningThread) {
                if (!listeningThread.isAlive()) {
                    try {
                        listeningThread.wait();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            //-------------------------------------------------------
            // broadcast on local subnet to find RunControl server
            //-------------------------------------------------------
            DatagramPacket udpPacket;

            // create byte array for broadcast
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            DataOutputStream out = new DataOutputStream(baos);

            try {
                // Put our TCP listening port, our name, and
                // the EXPID (experiment id string) into byte array.
                out.writeInt(cMsgNetworkConstants.rcDomainBroadcastClient); // broadcast is from rc domain client
                out.writeInt(port);
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

                // create socket to receive at anonymous port & all interfaces
                broadcastUdpSocket = new DatagramSocket();

                // create packet to broadcast from the byte array
                byte[] buf = baos.toByteArray();
                udpPacket = new DatagramPacket(buf, buf.length,
                                               rcServerBroadcastAddress,
                                               rcServerBroadcastPort);
                baos.close();
            }
            catch (IOException e) {
                listeningThread.killThread();
                try { out.close();} catch (IOException e1) {}
                try {baos.close();} catch (IOException e1) {}
                if (broadcastUdpSocket != null) broadcastUdpSocket.close();

                if (debug >= cMsgConstants.debugError) {
                    System.out.println("I/O Error: " + e);
                }
                throw new cMsgException(e.getMessage(), e);
            }

            // create a thread which will receive any responses to our broadcast
            BroadcastReceiver receiver = new BroadcastReceiver();
            receiver.start();

            // create a thread which will send our broadcast
            Broadcaster sender = new Broadcaster(udpPacket);
            sender.start();

            // wait up to broadcast timeout seconds
            boolean response = false;
            if (broadcastTimeout > 0) {
                try {
                    if (broadcastResponse.await(broadcastTimeout, TimeUnit.MILLISECONDS)) {
                        response = true;
                    }
                }
                catch (InterruptedException e) {}
            }
            // wait forever
            else {
                try { broadcastResponse.await(); response = true;}
                catch (InterruptedException e) {}
            }

            broadcastUdpSocket.close();
            sender.interrupt();

            if (!response) {
                throw new cMsgException("No response to UDP broadcast received");
            }
            else {
//System.out.println("Got a response!");
            }

            // Now that we got a response from the RC Broadcast server,
            // wait for that server to pass its info on to the RC server
            // which should complete this connect by sending a "connect"
            // message to our listening thread.

            // wait up to connect timeout seconds
            boolean completed = false;
            if (connectTimeout > 0) {
                try {
                    if (connectCompletion.await(connectTimeout, TimeUnit.MILLISECONDS)) {
                        completed = true;
                    }
                }
                catch (InterruptedException e) {}
            }
            // wait forever
            else {
                try { connectCompletion.await(); completed = true;}
                catch (InterruptedException e) {}
            }

            // RC Broadcast server told me to abandon the connection attempt
            if (abandonConnection) {
                throw new cMsgException("RC Broadcast server says to quit the connect attempt");
            }

            if (!completed) {
//System.out.println("connect: Did NOT complete the connection");
                throw new cMsgException("No connect from the RC server received");
            }
            else {
//System.out.println("connect: Completed the connection!");
            }

            // Create a UDP "connection". This means security check is done only once
            // and communication with any other host/port is not allowed.
            // create socket to receive at anonymous port & all interfaces
            try {
                udpSocket = new DatagramSocket();
                udpSocket.setReceiveBufferSize(cMsgNetworkConstants.bigBufferSize);
            }
            catch (SocketException e) {
                listeningThread.killThread();
                if (udpSocket != null) udpSocket.close();
                e.printStackTrace();
            }
            udpSocket.connect(rcServerAddress, rcUdpServerPort);
            sendUdpPacket = new DatagramPacket(new byte[0], 0, rcServerAddress, rcUdpServerPort);

            // create a TCP connection to the RC Server
            try {
                tcpSocket = new Socket(rcServerAddress,rcTcpServerPort);
                //tcpSocket.connect(sockAddr);
                tcpSocket.setTcpNoDelay(true);
                tcpSocket.setSendBufferSize(cMsgNetworkConstants.bigBufferSize);
                domainOut = new DataOutputStream(new BufferedOutputStream(tcpSocket.getOutputStream(),
                                                                          cMsgNetworkConstants.bigBufferSize));
            }
            catch (IOException e) {
                listeningThread.killThread();
                udpSocket.close();
                if (domainOut != null) try {domainOut.close();}  catch (IOException e1) {}
                if (tcpSocket != null) try {tcpSocket.close();}  catch (IOException e1) {}

                throw new cMsgException("Cannot make TCP connection to RC server", e);
            }

            // create request sending (to domain) channel (This takes longest so do last)
            connected = true;
        }
        finally {
            connectLock.unlock();
        }

        return;
    }




    /**
     * Method to parse the Universal Domain Locator (UDL) into its various components.
     *
     * @param udlRemainder partial UDL to parse
     * @throws cMsgException if udlRemainder is null
     */
     void parseUDL(String udlRemainder) throws cMsgException {

        if (udlRemainder == null) {
            throw new cMsgException("invalid UDL");
        }

        /* Runcontrol domain UDL is of the form:
         *        cMsg:rc://<host>:<port>/?expid=<expid>&broadcastTO=<timeout>&connectTO=<timeout>
         *
         * Remember that for this domain:
         * 1) port is optional with a default of 6543 (cMsgNetworkConstants.rcBroadcastPort)
         * 2) host is optional with a default of 255.255.255.255 (broadcast)
         *    and may be "localhost" or in dotted decimal form
         * 3) the experiment id or expid is optional, it is taken from the
         *    environmental variable EXPID
         * 4) broadcastTO is the time to wait in seconds before connect returns a
         *    timeout when a rc broadcast server does not answer
         * 5) connectTO is the time to wait in seconds before connect returns a
         *    timeout while waiting for the rc server to send a special (tcp)
         *    concluding connect message
         */

        Pattern pattern = Pattern.compile("((?:[a-zA-Z]+[\\w\\.\\-]*)|(?:[\\d]+\\.[\\d\\.]+))?:?(\\d+)?/?(.*)");
        Matcher matcher = pattern.matcher(udlRemainder);

        String udlHost, udlPort, remainder;

        if (matcher.find()) {
            // host
            udlHost = matcher.group(1);
            // port
            udlPort = matcher.group(2);
            // remainder
            remainder = matcher.group(3);

            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("\nparseUDL: " +
                                   "\n  host      = " + udlHost +
                                   "\n  port      = " + udlPort +
                                   "\n  remainder = " + remainder);
            }
        }
        else {
            throw new cMsgException("invalid UDL");
        }

        // if host given ...
        if (udlHost != null) {
            // if the host is "localhost", find the actual, fully qualified  host name
            if (udlHost.equalsIgnoreCase("localhost")) {
                try {
                    udlHost = InetAddress.getLocalHost().getCanonicalHostName();
                }
                catch (UnknownHostException e) {
                    udlHost = null;
                }

                if (debug >= cMsgConstants.debugWarn) {
                    System.out.println("parseUDL: codaComponent host given as \"localhost\", substituting " +
                                       udlHost);
                }
            }
            else {
                try {
                    udlHost = InetAddress.getByName(udlHost).getCanonicalHostName();
                }
                catch (UnknownHostException e) {
                    udlHost = null;
                }
            }

            // If the host is NOT given we broadcast on local subnet.
            // If the host is     given we unicast to this particular host.
            if (udlHost != null) {
                // Note that a null arg to getByName gives the loopback address
                // so we need to rule that out.
                try { rcServerBroadcastAddress = InetAddress.getByName(udlHost); }
                catch (UnknownHostException e) {}
            }
//System.out.println("Will unicast to host " + udlHost);
        }
        else {
//System.out.println("Will broadcast to 255.255.255.25");
            try {rcServerBroadcastAddress = InetAddress.getByName("255.255.255.255"); }
            catch (UnknownHostException e) {}
        }

        // get codaComponent port or guess if it's not given
        if (udlPort != null && udlPort.length() > 0) {
            try { rcServerBroadcastPort = Integer.parseInt(udlPort); }
            catch (NumberFormatException e) {
                rcServerBroadcastPort = cMsgNetworkConstants.rcBroadcastPort;
                if (debug >= cMsgConstants.debugWarn) {
                    System.out.println("parseUDL: non-integer port, guessing codaComponent port is " + rcServerBroadcastPort);
                }
            }
        }
        else {
            rcServerBroadcastPort = cMsgNetworkConstants.rcBroadcastPort;
            if (debug >= cMsgConstants.debugWarn) {
                System.out.println("parseUDL: guessing codaComponent port is " + rcServerBroadcastPort);
            }
        }

        if (rcServerBroadcastPort < 1024 || rcServerBroadcastPort > 65535) {
            throw new cMsgException("parseUDL: illegal port number");
        }
//System.out.println("Port = " + rcServerBroadcastPort);

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
//System.out.println("broadcast TO = " + broadcastTimeout);
            }
            catch (NumberFormatException e) {
                // ignore error and keep value of 0
            }
        }

        // now look for ?connectTO=value& or &connectTO=value&
        pattern = Pattern.compile("[\\?&]connectTO=([0-9]+)");
        matcher = pattern.matcher(remainder);
        if (matcher.find()) {
            try {
                connectTimeout = 1000 * Integer.parseInt(matcher.group(1));
//System.out.println("broadcast TO = " + connectTimeout);
            }
            catch (NumberFormatException e) {
                // ignore error and keep value of 0
            }
        }

    }
    

    /**
     * Method to close the connection to the domain server. This method results in this object
     * becoming functionally useless.
     */
    public void disconnect() {
        // cannot run this simultaneously with any other public method
        connectLock.lock();

        try {

            if (!connected) return;

            connected = false;
            broadcastUdpSocket.close();
            udpSocket.close();
            try {tcpSocket.close();} catch (IOException e) {}
            try {domainOut.close();} catch (IOException e) {}

            // stop listening and client communication thread & close channel
            listeningThread.killThread();

            // stop all callback threads
            synchronized (subscriptions) {
                for (cMsgSubscription sub : subscriptions) {
                    // run through all callbacks
                    for (cMsgCallbackThread cbThread : sub.getCallbacks()) {
                        // Tell the callback thread(s) to wakeup and die
                        if (Thread.currentThread() == cbThread) {
                            //System.out.println("Don't interrupt my own thread!!!");
                            cbThread.dieNow(false);
                        }
                        else {
                            cbThread.dieNow(true);
                        }
                    }
                }
            }

            // empty all hash tables
            subscriptions.clear();
            unsubscriptions.clear();
        }
        finally {
            connectLock.unlock();
        }
    }


        /**
         * Method to send a message to the domain server for further distribution.
         *
         * @param message message to send
         * @throws cMsgException if there are communication problems with the server;
         *                       subject and/or type is null
         */
        public void send(final cMsgMessage message) throws cMsgException {

            if (!message.getContext().getReliableSend()) {
                udpSend(message);
                return;
            }

            String subject = message.getSubject();
            String type    = message.getType();
            String text    = message.getText();

            // check message fields first
            if (subject == null || type == null) {
                throw new cMsgException("message subject and/or type is null");
            }

            if (text == null) {
                text = "";
            }

            int msgType = cMsgConstants.msgSubscribeResponse;
            if (message.isGetResponse()) {
                msgType = cMsgConstants.msgGetResponse;
            }

            int binaryLength = message.getByteArrayLength();

            // cannot run this simultaneously with connect, reconnect, or disconnect
            notConnectLock.lock();
            // protect communicatons over socket
            socketLock.lock();
            try {
                if (!connected) {
                    throw new IOException("not connected to server");
                }

                // length not including first int
                int totalLength = (4 * 14) + name.length() + subject.length() +
                        type.length() + text.length() + binaryLength;

                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(totalLength);
                domainOut.writeInt(msgType);
                domainOut.writeInt(cMsgConstants.version);
                domainOut.writeInt(message.getUserInt());
                domainOut.writeInt(message.getInfo());
                domainOut.writeInt(message.getSenderToken());

                long now = new Date().getTime();
                // send the time in milliseconds as 2, 32 bit integers
                domainOut.writeInt((int) (now >>> 32)); // higher 32 bits
                domainOut.writeInt((int) (now & 0x00000000FFFFFFFFL)); // lower 32 bits
                domainOut.writeInt((int) (message.getUserTime().getTime() >>> 32));
                domainOut.writeInt((int) (message.getUserTime().getTime() & 0x00000000FFFFFFFFL));

                domainOut.writeInt(name.length());
                domainOut.writeInt(subject.length());
                domainOut.writeInt(type.length());
                domainOut.writeInt(text.length());
                domainOut.writeInt(binaryLength);

                // write strings & byte array
                try {
                    domainOut.write(name.getBytes("US-ASCII"));
                    domainOut.write(subject.getBytes("US-ASCII"));
                    domainOut.write(type.getBytes("US-ASCII"));
                    domainOut.write(text.getBytes("US-ASCII"));
                    if (binaryLength > 0) {
                        domainOut.write(message.getByteArray(),
                                  message.getByteArrayOffset(),
                                  binaryLength);
                    }
                }
                catch (UnsupportedEncodingException e) {
                }

                domainOut.flush();

            }
            catch (IOException e) {
                if (debug >= cMsgConstants.debugError) {
                    System.out.println("send: " + e.getMessage());
                }
                throw new cMsgException(e.getMessage());
            }
            finally {
                socketLock.unlock();
                notConnectLock.unlock();
            }
        }

//-----------------------------------------------------------------------------


    /**
     * Method to send a message to the domain server over UDP for further distribution.
     *
     * @param message message to send
     * @throws cMsgException if there are communication problems with the server;
     *                       subject and/or type is null; message is too big for
     *                       UDP packet size if doing UDP send
     */
    private void udpSend(cMsgMessage message) throws cMsgException {

        String subject = message.getSubject();
        String type    = message.getType();
        String text    = message.getText();

        // check message fields first
        if (subject == null || type == null) {
            throw new cMsgException("message subject and/or type is null");
        }

        if (text == null) {
            text = "";
        }

        int msgType = cMsgConstants.msgSubscribeResponse;
        if (message.isGetResponse()) {
//System.out.println("sending get-response with UDP");
            msgType = cMsgConstants.msgGetResponse;
        }

        int binaryLength = message.getByteArrayLength();

        // total length of msg (not including first int which is this size)
        int totalLength = (4 * 14) + name.length() + subject.length() +
                type.length() + text.length() + binaryLength;

        if (totalLength > 8192) {
            throw new cMsgException("Too big a message for UDP to send");
        }

        // create byte array for broadcast
        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        DataOutputStream out = new DataOutputStream(baos);

        // cannot run this simultaneously with connect, reconnect, or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            out.writeInt(totalLength); // total length of msg (not including this int)
            out.writeInt(msgType);
            out.writeInt(cMsgConstants.version);
            out.writeInt(message.getUserInt());
            out.writeInt(message.getInfo());
            out.writeInt(message.getSenderToken());

            long now = new Date().getTime();
            // send the time in milliseconds as 2, 32 bit integers
            out.writeInt((int) (now >>> 32)); // higher 32 bits
            out.writeInt((int) (now & 0x00000000FFFFFFFFL)); // lower 32 bits
            out.writeInt((int) (message.getUserTime().getTime() >>> 32));
            out.writeInt((int) (message.getUserTime().getTime() & 0x00000000FFFFFFFFL));

            out.writeInt(name.length());
            out.writeInt(subject.length());
            out.writeInt(type.length());
            out.writeInt(text.length());
            out.writeInt(binaryLength);

            // write strings & byte array
            try {
                out.write(name.getBytes("US-ASCII"));
                out.write(subject.getBytes("US-ASCII"));
                out.write(type.getBytes("US-ASCII"));
                out.write(text.getBytes("US-ASCII"));
                if (binaryLength > 0) {
                    out.write(message.getByteArray(),
                              message.getByteArrayOffset(),
                              binaryLength);
                }
            }
            catch (UnsupportedEncodingException e) {
            }
            out.flush();
            out.close();

            // send message packet from the byte array
            byte[] buf = baos.toByteArray();

            synchronized (sendUdpPacket) {
                // setData is synchronized on the packet.
                sendUdpPacket.setData(buf, 0, buf.length);
                // send in synchronized internally on the packet object.
                // Because we only use one packet object for this client,
                // all udp sends are synchronized.
                udpSocket.send(sendUdpPacket);
            }
        }
        catch (IOException e) {
            throw new cMsgException("Cannot create or send message packet", e);
        }
        finally {
            notConnectLock.unlock();
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to subscribe to receive messages of a subject and type from the domain server.
     * The combination of arguments must be unique. In other words, only 1 subscription is
     * allowed for a given set of subject, type, callback, and userObj.
     *
     * Note about the server failing and an IOException being thrown. All existing
     * subscriptions are resubscribed on the new failover server by the keepAlive thread.
     * However, this routine will recover from an IO error during the subscribe itself
     * if the failover is successful.
     *
     * @param subject message subject
     * @param type    message type
     * @param cb      callback object whose single method is called upon receiving a message
     *                of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @return handle object to be used for unsubscribing
     * @throws cMsgException if the callback, subject and/or type is null or blank;
     *                       an identical subscription already exists; there are
     *                       communication problems with the server
     */
    public Object subscribe(String subject, String type, cMsgCallbackInterface cb, Object userObj)
            throws cMsgException {

        // check args first
        if (subject == null || type == null || cb == null) {
            throw new cMsgException("subject, type or callback argument is null");
        }
        else if (subject.length() < 1 || type.length() < 1) {
            throw new cMsgException("subject or type is blank string");
        }

        cMsgCallbackThread cbThread = null;

        cMsgSubscription newSub;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // cannot run this simultaneously with unsubscribe
        // or itself (iterate over same hashtable)
        subscribeLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to server");
            }

            // add to callback list if subscription to same subject/type exists

            int id;

            // client listening thread may be interating thru subscriptions concurrently
            // and we may change set structure
            synchronized (subscriptions) {

                // for each subscription ...
                for (cMsgSubscription sub : subscriptions) {
                    // If subscription to subject & type exist already, keep track of it locally
                    // and don't bother the server since any matching message will be delivered
                    // to this client anyway.
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
                // We need to create it.

                // First generate a unique id for the receiveSubscribeId field. This info
                // allows us to unsubscribe.
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
     * Method to unsubscribe a previous subscription to receive messages of a subject and type
     * from the domain server.
     *
     * Note about the server failing and an IOException being thrown. To have "unsubscribe" make
     * sense on the failover server, we must wait until all existing subscriptions have been
     * successfully resubscribed on the new server.
     *
     * @param obj the object "handle" returned from a subscribe call
     * @throws cMsgException if there are communication problems with the server; object arg is null
     */
    public void unsubscribe(Object obj)
            throws cMsgException {

        // check arg first
        if (obj == null) {
            throw new cMsgException("argument is null");
        }

        // unsubscriptions is concurrent hashmap so this is OK
        cMsgSubscription sub = unsubscriptions.remove(obj);
        // already unsubscribed
        if (sub == null) {
            return;
        }
        cMsgCallbackThread cbThread = (cMsgCallbackThread) obj;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // cannot run this simultaneously with subscribe
        // or itself (iterate over same hashtable)
        subscribeLock.lock();

        try {
            if (!connected) {
                throw new cMsgException("not connected to server");
            }

            // If there are still callbacks left,
            // don't unsubscribe for this subject/type
            if (sub.numberOfCallbacks() > 1) {
                // kill callback thread
                cbThread.dieNow(false);
                // remove this callback from the set
                synchronized (subscriptions) {
                    sub.getCallbacks().remove(cbThread);
                }
                return;
            }

            // Delete stuff from hashes & kill threads
            cbThread.dieNow(false);
            synchronized (subscriptions) {
                sub.getCallbacks().remove(cbThread);
                subscriptions.remove(sub);
            }
        }
        finally {
            subscribeLock.unlock();
            notConnectLock.unlock();
        }
    }



    /**
     * This class gets any response to our UDP broadcast. A response will
     * stop the broadcast and tell us to wait for the completion of the
     * connect call by the RC server (not RC Broadcast server).
     */
    class BroadcastReceiver extends Thread {

        public void run() {

            /* A slight delay here will help the main thread (calling connect)
             * to be already waiting for a response from the server when we
             * broadcast to the server here (prompting that response). This
             * will help insure no responses will be lost.
             */
            try { Thread.sleep(200); }
            catch (InterruptedException e) {}

            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, 1024);
            int index;

            while (true) {
                // reset for each round
                packet.setLength(1024);
                index = 0;

                try {
                    broadcastUdpSocket.receive(packet);
//System.out.println("received broadcast packet");
                    int code = bytesToInt(buf, index);
                    index += 4;
                    int port = bytesToInt(buf, index);
                    index += 4;
                    int hostLen = bytesToInt(buf, index);
                    index += 4;
                    int expidLen = bytesToInt(buf, index);
                    index += 4;
//System.out.println("code = " + Integer.toHexString(code) + ", port = " + port);

                    // get host
                    if (hostLen > 0 && hostLen < 1025-16) {
                        String host = new String(buf, index, hostLen, "US-ASCII");
//System.out.println("host = " + host);
                        index += hostLen;
                    }
                    else {
                        hostLen = 0;
                    }

                    // get expid
                    String serverExpid = null;
                    if (expidLen > 0 && expidLen < 1025-16-hostLen) {
                        serverExpid = new String(buf, index, expidLen, "US-ASCII");
//System.out.println("expid = " + serverExpid);
                    }

                    /* make sure the response is from the RCBroadcastServer */
                    if (code != 0xc0da) {
                        if (debug >= cMsgConstants.debugWarn) {
                            System.out.println("Broadcast receiver: got bad secret code response to broadcast (" + code + ")");
                        }
                        continue;
                    }
                    else if (port != rcServerBroadcastPort) {
                        if (debug >= cMsgConstants.debugWarn) {
                            System.out.println("Broadcast receiver: got bad port response to broadcast (" + port + ")");
                        }
                        continue;
                    }
                    else if (!expid.equals(serverExpid)) {
                        if (debug >= cMsgConstants.debugWarn) {
                            System.out.println("Broadcast receiver: got bad expid response to broadcast (" + serverExpid + ")");
                        }
                        continue;
                    }
                }
                catch (UnsupportedEncodingException e) {continue;}
                catch (IOException e) {
//System.out.println("Got IOException in broadcast receive, exiting");
                    return;
                }
                break;
            }

            broadcastResponse.countDown();
        }
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
//System.out.println("Send broadcast packet to RC Broadcast server");
                        broadcastUdpSocket.send(packet);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    Thread.sleep(1000);
                }
            }
            catch (InterruptedException e) {
                // time to quit
//System.out.println("Interrupted sender");
            }
        }
    }


}

