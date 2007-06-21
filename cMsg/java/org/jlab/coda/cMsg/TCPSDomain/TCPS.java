/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 29-Mar-2006, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.TCPSDomain;

import org.jlab.coda.cMsg.*;

import java.nio.channels.SocketChannel;
import java.io.*;
import java.net.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class implements a cMsg client in the TCPServer (or TCPS) domain.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class TCPS extends cMsgDomainAdapter {

    /** TCPServer's host. */
    String tcpServerHost;

    /** TCPServer's TCP listening port. */
    int tcpServerPort;

    /** Channel for talking to TCPServer. */
    SocketChannel channel;

    /** Socket output stream associated with channel - sends messages to server. */
    DataOutputStream dataOut;

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

     /** Lock to ensure that methods using the socket, write in sequence. */
     Lock socketLock = new ReentrantLock();

     /** Level of debug output for this class. */
     int debug = cMsgConstants.debugNone;



    /** Constructor. */
    public TCPS() {
        domain = "TCPS";
    }


    /**
     * Method to connect to the TCPServer from this client.
     *
     * @throws org.jlab.coda.cMsg.cMsgException if there are problems parsing the UDL or
     *                       communication problems with the server(s)
     */
    public void connect() throws cMsgException {
        parseUDL(UDL);

        // cannot run this simultaneously with any other public method
        connectLock.lock();
        try {
            if (connected) return;

            // connect to TCP server
            SocketChannel channel = null;
            try {
                channel = SocketChannel.open(new InetSocketAddress(tcpServerHost, tcpServerPort));
                // set socket options
                Socket socket = channel.socket();
                // Set tcpNoDelay so no packets are delayed
                socket.setTcpNoDelay(true);
                // no need to set buffer sizes
                socket.setSendBufferSize(2048);
                dataOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 2048));
            }
            catch (IOException e) {
                // undo everything we've just done
                try {if (channel != null) channel.close();}
                catch (IOException e1) {}

                if (debug >= cMsgConstants.debugError) {
                    e.printStackTrace();
                }
                throw new cMsgException("connect: cannot create channel to TCPServer");
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
     * @param udl UDL to parse
     * @throws cMsgException if UDL is null
     */
     private void parseUDL(String udl) throws cMsgException {

        if (udl == null) {
            throw new cMsgException("invalid UDL");
        }

        // TCPS domain UDL is of the form:
        //       cMsg:TCPS://<host>:<port>
        //
        // where the host is optional.

        // strip off the cMsg:TCPS:// to begin with
        String udlLowerCase = udl.toLowerCase();
        int index = udlLowerCase.indexOf("tcps://");
        if (index < 0) {
            throw new cMsgException("invalid UDL");
        }
        String udlSubstring = udl.substring(index+7);

        // Remember that for this domain:
        // 1) host is necessary
        // 2) host can be "localhost" and may also be in dotted form (129.57.35.21)
        // 3) port is optional and defaults to 5432

        Pattern pattern = Pattern.compile("([\\w\\.\\-]+)?:?(\\d+)?/?(.*)");
        Matcher matcher = pattern.matcher(udlSubstring);

        String udlHost=null, udlPort=null, udlRemainder=null;

        if (matcher.find()) {
            // host
            udlHost = matcher.group(1);
            // port
            udlPort = matcher.group(2);
            // remainder
            udlRemainder = matcher.group(3);

            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("\nparseUDL: " +
                                   "\n  host      = " + udlHost +
                                   "\n  port      = " + udlPort +
                                   "\n  remainder = " + udlRemainder);
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
                }

                if (debug >= cMsgConstants.debugWarn) {
                    System.out.println("parseUDL: TCPServer host given as \"localhost\", substituting " +
                                       udlHost);
                }
            }
            else {
                try {
                    udlHost = InetAddress.getByName(udlHost).getCanonicalHostName();
                }
                catch (UnknownHostException e) {
                }
            }

            host = udlHost;
        }

        // get tcpServer port or guess if it's not given
        if (udlPort != null && udlPort.length() > 0) {
            try { tcpServerPort = Integer.parseInt(udlPort); }
            catch (NumberFormatException e) {
                tcpServerPort = cMsgNetworkConstants.tcpServerPort;
                if (debug >= cMsgConstants.debugWarn) {
                    System.out.println("parseUDL: non-integer port, guessing TCPServer port is " + tcpServerPort);
                }
            }
        }
        else {
            tcpServerPort = cMsgNetworkConstants.tcpServerPort;
            if (debug >= cMsgConstants.debugWarn) {
                System.out.println("parseUDL: guessing TCPServer port is " + tcpServerPort);
            }
        }

        if (tcpServerPort < 1024 || tcpServerPort > 65535) {
            throw new cMsgException("parseUDL: illegal port number");
        }

        // any remaining UDL is ...
        if (udlRemainder == null) {
            UDLremainder = "";
        }
        else {
            UDLremainder = udlRemainder;
        }
    }


    /**
     * Method to close the connection to the tcpServer. This method results in this object
     * becoming functionally useless.
     */
    public void disconnect() {
        // cannot run this simultaneously with any other public method
        connectLock.lock();

        connected = false;

        try {
            // tell server we're disconnecting
            socketLock.lock();
            try {
                channel.close();
            }
            catch (IOException e) {
            }
            finally {
                socketLock.unlock();
            }
        }
        finally {
            connectLock.unlock();
        }
    }



    /**
     * Method to send a message/command to the TCPServer. The command is sent as a
     * string in the message's text field.
     *
     * @param message message to send
     * @throws cMsgException if there are communication problems with the server;
     *                       text is null or blank
     */
    public void send(cMsgMessage message) throws cMsgException {

        String text = message.getText();

        // check message fields first
        if (text == null || text.length() < 1) {
            throw new cMsgException("Need to send a command in text field of message");
        }

        try {
            // cannot run this simultaneously with connect or disconnect
            notConnectLock.lock();
            // protect communicatons over socket
            socketLock.lock();
            try {
                if (!connected) {
                    throw new IOException("not connected to server");
                }

                // some sort of flag
                dataOut.writeInt(1);
                dataOut.writeInt(text.length());

                // write strings & byte array
                try {
                    dataOut.write(text.getBytes("US-ASCII"));
                    dataOut.writeByte(0);// send ending "C" null
                }
                catch (UnsupportedEncodingException e) {
                }

                dataOut.flush();
            }
            finally {
                socketLock.unlock();
                notConnectLock.unlock();
            }
        }
        catch (IOException e) {
            throw new cMsgException(e.getMessage());
        }

    }


}
