/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 8-May-2006, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.RCServerDomain;

import org.jlab.coda.cMsg.*;


import java.util.Iterator;
import java.util.Date;
import java.util.Set;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * This class implements a thread to listen to runcontrol clients in the
 * runcontrol server domain over UDP.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class rcUdpListeningThread extends Thread {

    /** Type of domain this is. */
    private String domainType = "rcs";

    /** Port on which to accept UDP communications. */
    int port;

    /** cMsg server that created this object. */
    private RCServer server;

    /** Level of debug output for this class. */
    private int debug;

    /** Socket to receive messages from RC client on. */
    DatagramSocket receiveSocket;

    /** Setting this to true will kill this thread. */
    private boolean killThread;

    /** Kills this thread. */
    void killThread() {
        // stop threads that get commands/messages over sockets
        killThread = true;
        this.interrupt();
        receiveSocket.close();
    }


    /**
     * Get the UDP listening port of this server.
     * @return UDP listening port of this server
     */
    public int getPort() {
        return port;
    }


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


    /**
     * Constructor for regular clients.
     *
     * @param server RC server that created this object
     */
    public rcUdpListeningThread(RCServer server) throws IOException {

        this.server = server;
        debug = server.debug;
        createUDPClientSocket();
        // die if no more non-daemon thds running
        setDaemon(true);
    }


    /**
      * Creates a UDP receiving socket for a runcontrol client.
      *
      * @throws IOException if socket cannot be created
      */
     private void createUDPClientSocket() throws IOException {

         try {
             // Create a socket to listen for udp packets.
             // First try the port given in the UDL (if any).
             if (port > 0) {
                 try {
                     receiveSocket = new DatagramSocket(port);
//System.out.println("Listening on UDP port " + port);
                     return;
                 }
                 catch (SocketException e) {}
             }
             receiveSocket = new DatagramSocket();
             port = receiveSocket.getLocalPort();
             receiveSocket.setReuseAddress(true);
//System.out.println("Listening on UDP port " + port);
         }
         catch (SocketException e) {
             if (receiveSocket != null) receiveSocket.close();
             throw e;
         }
//System.out.println("UDP on " + port);
     }


    /** This method is executed as a thread. */
    public void run() {

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("Running RC Server Listening Thread");
        }

        // RC server object is waiting for this thread to start in connect method,
        // so tell it we've started.
        synchronized (this) {
            notifyAll();
        }

        try {
            cMsgMessageFull msg;
            // read in data packet
            byte[] buf = new byte[cMsgNetworkConstants.biggestUdpPacketSize];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            // now listen for sends
            while (true) {

                if (this.isInterrupted()) {
                    return;
                }

                if (killThread) return;
                // enable the packet to receive all the data
                pkt.setLength(cMsgNetworkConstants.biggestUdpPacketSize);
                receiveSocket.receive(pkt);
//System.out.println("RECEIVED UDP PACKET!!!");
                if (killThread) return;

                // read incoming message
                int len   = bytesToInt(buf, 0);
                int msgId = bytesToInt(buf, 4);

                switch (msgId) {

                    case cMsgConstants.msgSubscribeResponse: // receiving a message

                        msg = readIncomingMessage(buf, 8);
                        // run callbacks for this message
                        runCallbacks(msg);

                        break;

                    case cMsgConstants.msgGetResponse: // receiving a message for sendAndGet
                        // read the message
                        msg = readIncomingMessage(buf, 8);
                        msg.setGetResponse(true);

                        // wakeup caller with this message
                        wakeGets(msg);
                        break;

                    default:
                        if (debug >= cMsgConstants.debugWarn) {
                            System.out.println("rcUdpListenThread: can't understand client message = " + msgId);
                        }
                        break;
                }
            }
        }

        catch (IOException e) {
            if (debug >= cMsgConstants.debugError) {
                System.out.println("rcUdpListenThread: I/O ERROR in rc server");
                System.out.println("rcUdpListenThread: close server socket, port = " + receiveSocket.getLocalPort());
            }
        }
        finally {
            // We're here if there is an IO error.
            // Disconnect the server (kill this thread).
            receiveSocket.close();
        }

        return;
    }

    /**
     * This method reads an incoming message from the client.
     *
     * @return message read from channel
     * @throws IOException if socket read or write error
     */
    private cMsgMessageFull readIncomingMessage(byte[] buf, int index) throws IOException {

        // create a message
        cMsgMessageFull msg = new cMsgMessageFull();

        msg.setVersion(bytesToInt(buf, index));
        index += 4;
        msg.setUserInt(bytesToInt(buf, index));
        index += 4;
        msg.setInfo(bytesToInt(buf, index));
        index += 4;
        msg.setSenderToken(bytesToInt(buf, index));
        index += 4;

        // time message was sent = 2 ints (hightest byte first)
        // in milliseconds since midnight GMT, Jan 1, 1970
        long time = ((long) bytesToInt(buf, index) << 32) | ((long) bytesToInt(buf, index + 4) & 0x00000000FFFFFFFFL);
        msg.setSenderTime(new Date(time));
        index += 8;

        // user time
        time = ((long) bytesToInt(buf, index) << 32) | ((long) bytesToInt(buf, index + 4) & 0x00000000FFFFFFFFL);
        msg.setUserTime(new Date(time));
        index += 8;

        // String lengths
        int lengthSender  = bytesToInt(buf, index);
        index += 4;
        int lengthSubject = bytesToInt(buf, index);
        index += 4;
        int lengthType    = bytesToInt(buf, index);
        index += 4;
        int lengthText    = bytesToInt(buf, index);
        index += 4;
        int lengthBinary  = bytesToInt(buf, index);
        index += 4;

        // read sender
        msg.setSender(new String(buf, index, lengthSender, "US-ASCII"));
        //System.out.println("sender = " + msg.getSender());
        index += lengthSender;

        // read subject
        msg.setSubject(new String(buf, index, lengthSubject, "US-ASCII"));
        //System.out.println("subject = " + msg.getSubject());
        index += lengthSubject;

        // read type
        msg.setType(new String(buf, index, lengthType, "US-ASCII"));
        //System.out.println("type = " + msg.getType());
        index += lengthType;

        // read text
        if (lengthText > 0) {
            msg.setText(new String(buf, index, lengthText, "US-ASCII"));
            //System.out.println("text = " + msg.getText());
            index += lengthText;
        }

        // read binary array
        if (lengthBinary > 0) {
            try {
                msg.setByteArrayNoCopy(buf, index, lengthBinary);
            }
            catch (cMsgException e) {
            }
        }

        // fill in message object's members
        msg.setDomain(domainType);
        msg.setReceiver(server.getName());
        msg.setReceiverHost(server.getHost());
        msg.setReceiverTime(new Date()); // current time
//System.out.println("MESSAGE RECEIVED");
        return msg;
    }


    /**
     * This method runs all appropriate callbacks - each in their own thread -
     * for server subscribe and subscribeAndGet calls.
     *
     * @param msg incoming message
     */
    private void runCallbacks(cMsgMessageFull msg) {

        if (server.subscribeAndGets.size() > 0) {
            // for each subscribeAndGet called by this server ...
            cMsgGetHelper helper;
            for (Iterator i = server.subscribeAndGets.values().iterator(); i.hasNext();) {
                helper = (cMsgGetHelper) i.next();
                if (cMsgMessageMatcher.matches(msg.getSubject(), msg.getType(), helper)) {

                    helper.setTimedOut(false);
                    helper.setMessage(msg.copy());
                    // Tell the subscribeAndGet-calling thread to wakeup
                    // and retrieve the held msg
                    synchronized (helper) {
                        helper.notify();
                    }
                }
                i.remove();
            }
        }

        // handle subscriptions
        Set<cMsgSubscription> set = server.subscriptions;

        if (set.size() > 0) {
            // if callbacks have been stopped, return
            if (!server.isReceiving()) {
                if (debug >= cMsgConstants.debugInfo) {
                    System.out.println("runCallbacks: all subscription callbacks have been stopped");
                }
                return;
            }

            boolean gotMatch = false;

            // set is NOT modified here
            synchronized (set) {
                // for each subscription of this server ...
                for (cMsgSubscription sub : set) {
                    // if subject & type of incoming message match those in subscription ...
                    if (cMsgMessageMatcher.matches(msg.getSubject(), msg.getType(), sub)) {
                        gotMatch = true;
                        // run through all callbacks
                        for (cMsgCallbackThread cbThread : sub.getCallbacks()) {
                            // The callback thread copies the message given
                            // to it before it runs the callback method on it.
                            cbThread.sendMessage(msg);
                        }
                    }
                }
            }

            // The message was NOT delivered to a regular subscription callback
            // so deliver it to any default callbacks that maybe registered.
            if (!gotMatch) {
                synchronized (set) {
                    // for each subscription of this server ...
                    for (cMsgSubscription sub : set) {
                        // Is this a default subscription?
                        if (sub.getSubject() == null && sub.getType() == null) {
                            // run through all callbacks
                            for (cMsgCallbackThread cbThread : sub.getCallbacks()) {
                                // The callback thread copies the message given
                                // to it before it runs the callback method on it.
                                cbThread.sendMessage(msg);
                            }
                        }
                    }
                }
            }


        }
    }

    /**
     * This method wakes up a thread in a server waiting in the sendAndGet method
     * and delivers a message to it.
     *
     * @param msg incoming message
     */
    private void wakeGets(cMsgMessageFull msg) {

        cMsgGetHelper helper = server.sendAndGets.remove(msg.getSenderToken());

        if (helper == null) {
            return;
        }
        helper.setTimedOut(false);
        // Do NOT need to copy msg as only 1 receiver gets it
        helper.setMessage(msg);

        // Tell the sendAndGet-calling thread to wakeup and retrieve the held msg
        synchronized (helper) {
            helper.notify();
        }
    }




}

