/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 9-Nov-2006, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.server;

import org.jlab.coda.cMsg.*;

import java.net.*;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * This class implements a thread to listen to cMsg clients broadcasting
 * in order to find and then fully connect to a cMsg server.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgBroadcastListeningThread extends Thread {

    /** cMsg name server's main tcp listening port. */
     private int serverTcpPort;

    /** cMsg name server's client password. */
     private String serverPassword;

     /** UDP socket on which to read packets sent from cMsg clients. */
    private DatagramSocket broadcastSocket;

    /** Level of debug output for this class. */
    private int debug;

    /** Setting this to true will kill this thread. */
    private boolean killThread;

    /** Kills this thread. */
    void killThread() {
        killThread = true;
        this.interrupt();
        broadcastSocket.close();
    }


    /**
     * Converts 4 bytes of a byte array into an integer.
     *
     * @param b   byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return integer value
     */
    private static final int bytesToInt(byte[] b, int off) {
        int result = ((b[off] & 0xff) << 24)     |
                     ((b[off + 1] & 0xff) << 16) |
                     ((b[off + 2] & 0xff) << 8)  |
                      (b[off + 3] & 0xff);
        return result;
    }


    /**
     * Constructor.
     *
     * @param port cMsg name server's main tcp listening port
     * @param socket udp socket on which to receive broadcasts from cMsg clients
     * @param password cMsg server's client password
     * @param debug cMsg server's debug level
     */
    public cMsgBroadcastListeningThread(int port, DatagramSocket socket, String password, int debug) {
        broadcastSocket = socket;
        serverTcpPort   = port;
        serverPassword  = password;
        this.debug      = debug;
        // die if no more non-daemon thds running
        setDaemon(true);
    }


    /** This method is executed as a thread. */
    public void run() {

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("Running cMsgNameserver Broadcast Listening Thread");
        }

        // create a packet to be written into
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, 1024);

        // create a packet to be send back to the client
        DatagramPacket sendPacket  = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        DataOutputStream out       = new DataOutputStream(baos);
        String myHost              = "unknown";
        try {myHost = InetAddress.getLocalHost().getCanonicalHostName();}
        catch (UnknownHostException e) { }

        try {
            // Put our magic int, TCP listening port, and our host into byte array
            out.writeInt(0xc0da1);
            out.writeInt(serverTcpPort);
            out.writeInt(myHost.length());
            try {out.write(myHost.getBytes("US-ASCII"));}
            catch (UnsupportedEncodingException e) { }
            out.flush();
            out.close();

            // create packet to broadcast from the byte array
            byte[] outBuf = baos.toByteArray();
            sendPacket = new DatagramPacket(outBuf, outBuf.length);
        }
        catch (IOException e) {
            if (debug >= cMsgConstants.debugError) {
                System.out.println("I/O Error: " + e);
            }
        }

        // listen for broadcasts and interpret packets
        try {
            while (true) {
                if (killThread) { return; }

                packet.setLength(1024);
                broadcastSocket.receive(packet);
                if (debug >= cMsgConstants.debugInfo) {
                    System.out.println("RECEIVED CMSG DOMAIN BROADCAST PACKET !!!");
                }

                if (killThread) { return; }

                // pick apart byte array received
                InetAddress clientAddress = packet.getAddress();
                int clientUdpPort = packet.getPort();   // port to send response packet to
                int magicInt      = bytesToInt(buf, 0); // magic number
                int msgType       = bytesToInt(buf, 4); // what type of broadcast is this ?
                int passwordLen   = bytesToInt(buf, 8); // password length
//System.out.println("magic int = " + Integer.toHexString(magicInt) + ", msgtype = " + msgType + ", pswd len = " + passwordLen);

                // sanity check
                if (magicInt != 0xc0da1 || msgType != cMsgNetworkConstants.cMsgDomainBroadcast) {
                    // ignore broadcasts from unknown sources
//System.out.println("bad magic # or msgtype");
                    continue;
                }

                // password
                String pswd = null;
                if (passwordLen > 0) {
                    try { pswd = new String(buf, 12, passwordLen, "US-ASCII"); }
                    catch (UnsupportedEncodingException e) {}
                }

                // Compare sent password with name server's password.
                // Reject mismatches.
                if (serverPassword != null) {
                    if (pswd == null || !serverPassword.equals(pswd)) {
                        if (debug >= cMsgConstants.debugInfo) {
                            System.out.println("REJECTING PASSWORD server's (" + serverPassword +
                            ") does not match client's (" + pswd + ")");
                        }
                        continue;
                    }
                }
                else {
                    if (pswd != null) {
                        if (debug >= cMsgConstants.debugInfo) {
                            System.out.println("REJECTING PASSWORD server's (" + serverPassword +
                            ") does not match client's (" + pswd + ")");
                        }
                        continue;
                    }
                }

                // Send a reply to broadcast. This must contain this name server's
                // host and tcp port so a regular connect can be done by the client.
                try {
                    // set address and port for responding packet
                    sendPacket.setAddress(clientAddress);
                    sendPacket.setPort(clientUdpPort);
//System.out.println("Send reponse packet");
                    broadcastSocket.send(sendPacket);
                }
                catch (IOException e) {
                    if (debug >= cMsgConstants.debugError) {
                        System.out.println("I/O Error: " + e);
                    }
                }
            }
        }
        catch (IOException e) {
            if (debug >= cMsgConstants.debugError) {
                System.out.println("cMsgBroadcastListenThread: I/O ERROR in rc broadcast server");
                System.out.println("cMsgBroadcastListenThread: close broadcast socket, port = " + broadcastSocket.getLocalPort());
            }
        }
        finally {
            // We're here if there is an IO error. Close socket and kill this thread.
            broadcastSocket.close();
        }

        return;
    }

}
