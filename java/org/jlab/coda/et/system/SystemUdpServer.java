/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12H        *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et.system;

import java.lang.*;
import java.io.*;
import java.net.*;
import org.jlab.coda.et.*;

/**
 * This class implements a thread which starts other threads - each of which
 * listen at a different IP address for users trying to find the ET system by
 * broadcasting, multicasting, or the direct sending of a udp packet.
 *
 * @author Carl Timmer
 */

class SystemUdpServer extends Thread {

    /** Udp port to listen on. */
    private int port;

    /** ET system object. */
    private SystemCreate sys;

    /** ET system configuration. */
    private SystemConfig config;


    /**
     * Createes a new SystemUdpServer object.
     * @param sys ET system object
     */
    SystemUdpServer(SystemCreate sys) {
        this.sys = sys;
        config = sys.getConfig();
        port = config.getServerPort();
    }

    /** Starts threads to listen for packets at a different addresses. */
    public void run() {
        if (config.getDebug() >= EtConstants.debugInfo) {
            System.out.println("Running UDP Listening Threads");
        }

        // use the default port number since one wasn't specified
        if (port < 1) {
            port = EtConstants.serverPort;
        }

        // If we're broadcasting, then we use 1 thread with 1 socket,
        // bound to the wildcard address, to listen to broadcasts from all local
        // subnets.
        //
        // If we're multicasting and the specified multicast port is the same as the
        // broadcast port, then we use 1 thread to listen to multicasts and broadcasts
        // on one socket.
        //
        // If we're multicasting with a different port than the broadcasting/direct
        // port, then multicasting is treated separately from everything else and has
        // its own socket and thread.

        if (config.getMulticastAddrs().size() > 0) {
            try {
System.out.println("setting up for multicast on port " + config.getMulticastPort());
                MulticastSocket sock = new MulticastSocket(config.getMulticastPort());
                sock.setReceiveBufferSize(512);
                sock.setSendBufferSize(512);
                ListeningThread lis = new ListeningThread(sys, sock);
                lis.start();
            }
            catch (IOException e) {
                System.out.println("cannot listen on port " + config.getMulticastPort() + " for multicasting");
                e.printStackTrace();
            }

            if (config.getMulticastPort() == config.getUdpPort()) {
                // only need to listen on the multicast socket, so we're done
                return;
            }
        }

        try {
System.out.println("setting up for broadcast on port " + config.getUdpPort());
            DatagramSocket sock = new DatagramSocket(config.getUdpPort());
            sock.setBroadcast(true);
            sock.setReceiveBufferSize(512);
            sock.setSendBufferSize(512);
            ListeningThread lis = new ListeningThread(sys, sock);
            lis.start();
        }
        catch (SocketException e) {
            e.printStackTrace();
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}


/**
 * This class implements a thread which listens on a particular address for a
 * udp packet. It sends back a udp packet with the tcp server port, host name,
 * and other information necessary to establish a tcp connection between the
 * tcp server thread of the ET system and the user.
 *
 * @author Carl Timmer
 */

class ListeningThread extends Thread {

    /** ET system object. */
    private SystemCreate sys;

    /** ET system configuration object. */
    private SystemConfig config;

    /** Setup a socket for receiving udp packets. */
    private DatagramSocket sock;

    /** Is this thread responding to a multicast or broadcast or perhaps either. */
    private int cast;

    /** Don't know which address the broad/multicast was sent to since we're using
     * "INADDR_ANY" so just return this. */
    private String incomingAddress = "0.0.0.0";


    /**
     *  Creates a new ListeningThread object for a UDP multicasts.
     *
     *  @param sys ET system object
     *  @param mSock multicast udp socket
     */
    ListeningThread(SystemCreate sys, MulticastSocket mSock) throws IOException {
        this.sys = sys;
        config   = sys.getConfig();
        for (InetAddress address : config.getMulticastAddrs()) {
            if (address.isMulticastAddress()) {
                mSock.joinGroup(address);
            }
        }
        sock = mSock;
        cast = EtConstants.broadAndMulticast;
    }


    /**
     *  Creates a new ListeningThread object for a UDP broadcasts.
     *
     *  @param sys ET system object
     *  @param sock udp socket
     */
    ListeningThread(SystemCreate sys, DatagramSocket sock) throws UnknownHostException {
        this.sys  = sys;
        config    = sys.getConfig();
        this.sock = sock;
        cast = EtConstants.broadcast;
    }


    /**
     * Starts a single thread to listen for udp packets at a specific address
     * and respond with ET system information.
     */
    public void run() {
        // packet & buffer to receive UDP packets
        byte[] rBuffer = new byte[512]; // much larger than needed
        DatagramPacket rPacket = new DatagramPacket(rBuffer, 512);

        // Prepare output buffer we send in answer to inquiries:
        //
        // (0)  ET magic numbers (3 ints)
        // (1)  ET version #
        // (2)  port of tcp server thread (not udp config->port)
        // (3)  ET_BROADCAST or ET_MULTICAST (int)
        // (4)  length of next string
        // (5)    broadcast address (dotted-dec) if broadcast received or
        //        multicast address (dotted-dec) if multicast received
        //        (see int #3)
        // (6)  length of next string
        // (7)    hostname given by "uname" (used as a general
        //        identifier of this host no matter which interface is used)

        // (8)  length of next string
        // (9)    canonical name of host
        // (10) number of IP addresses
        // (11)   32bit, net-byte ordered IPv4 address assoc with following address
        // (12)   length of next string
        // (13)       first dotted-decimal IPv4 address
        // (14)   32bit, net-byte ordered IPv4 address assoc with following address
        // (15)   length of next string
        // (16)       second dotted-decimal IPv4 address ...
        //
        // All known IP addresses are sent here both in numerical & dotted-decimal forms.
        //

        // buffer for reading ET name
        byte[] etNameBytes = new byte[EtConstants.fileNameLengthMax];

        // Put outgoing packet into byte array
        ByteArrayOutputStream baos = null;

        try {
            InetAddress addr = InetAddress.getLocalHost();
            String canon = addr.getCanonicalHostName();
            String hostName = addr.getHostName();

            // the send buffer needs to be of byte size ...
            int bufferSize = 10*4 + incomingAddress.length() + hostName.length() + canon.length() + 3;
            for (InetAddress netAddress : sys.getNetAddresses()) {
                bufferSize += 8 + netAddress.getHostAddress().length() + 1;
            }

            baos = new ByteArrayOutputStream(bufferSize);
            DataOutputStream dos = new DataOutputStream(baos);

            // (0) magic #s
            dos.writeInt(EtConstants.magicNumbers[0]);
            dos.writeInt(EtConstants.magicNumbers[1]);
            dos.writeInt(EtConstants.magicNumbers[2]);

            // (1), (2) & (3)
            dos.writeInt(EtConstants.version);
            dos.writeInt(config.getServerPort());
            dos.writeInt(cast);

            // (4) & (5)  incomingAddress = 0.0.0.0 since this is Java
            dos.writeInt(incomingAddress.length() + 1);
            dos.write(incomingAddress.getBytes("ASCII"));
            dos.writeByte(0);

            // (6) & (7) Local host name (equivalent to uname?)
            dos.writeInt(hostName.length() + 1);
            dos.write(hostName.getBytes("ASCII"));
            dos.writeByte(0);

            // (8) & (9) canonical host name
            dos.writeInt(canon.length() + 1);
            dos.write(canon.getBytes("ASCII"));
            dos.writeByte(0);

            // (10) number of addresses to follow
            dos.writeInt(sys.getNetAddresses().length);

            // Send all addresses (32 bit and dot-decimal) associated with this host
            int addr32;
            for (InetAddress netAddress : sys.getNetAddresses()) {
                // convert array of 4 bytes into 32 bit network byte-ordered address
                addr32 = 0;
                for (int j = 0; j < 4; j++) {
                    addr32 = addr32 << 8 | (((int) (netAddress.getAddress())[j]) & 0xFF);
                }
// System.out.println("sending addr32 = " + addr32 + ", IP addr = " + netAddress.getHostAddress());
                // (11)
                dos.writeInt(addr32);
                // (12)
                dos.writeInt(netAddress.getHostAddress().length() + 1);
                // (13)
                dos.write(netAddress.getHostAddress().getBytes("ASCII"));
                dos.writeByte(0);
            }

            dos.flush();
        }
        catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
            // this will never happen.
        }
        catch (UnknownHostException ex) {
            ex.printStackTrace();
            // local host is always known
        }
        catch (IOException ex) {
            ex.printStackTrace();
            // this will never happen since we're writing to array
        }

        // construct byte array to send over a socket
        byte[] sBuffer = baos.toByteArray();

        while (true) {
            try {
                // read incoming data without blocking forever
                while (true) {
                    try {
//System.out.println("Waiting to receive packet, sock broadcast = " + sock.getBroadcast());
                        sock.receive(rPacket);
//System.out.println("Received packet ...");
                        break;
                    }
                    // socket receive timeout
                    catch (InterruptedIOException ex) {
                        // check to see if we've been commanded to die
                        if (sys.killAllThreads()) {
                            return;
                        }
                    }
                }

                // decode the data:
                // (1) ET magic numbers (3 ints),
                // (2) ET version #,
                // (3) length of string,
                // (4) ET file name

                ByteArrayInputStream bais = new ByteArrayInputStream(rPacket.getData());
                DataInputStream dis = new DataInputStream(bais);

                int magic1 = dis.readInt();
                int magic2 = dis.readInt();
                int magic3 = dis.readInt();
                if (magic1 != EtConstants.magicNumbers[0] ||
                    magic2 != EtConstants.magicNumbers[1] ||
                    magic3 != EtConstants.magicNumbers[2])  {
//System.out.println("SystemUdpServer:  Magic numbers did NOT match");
                    continue;
                }

                int version = dis.readInt();
                int length = dis.readInt();
//System.out.println("et_listen_thread: received packet version =  " + version +
//                    ", length = " + length);

                // reject incompatible ET versions
                if (version != EtConstants.version) {
                    continue;
                }
                // reject improper formats
                if ((length < 1) || (length > EtConstants.fileNameLengthMax)) {
                    continue;
                }

                // read all string bytes
                if (length > etNameBytes.length) {
                    etNameBytes = new byte[length];
                }
                dis.readFully(etNameBytes, 0, length-1);
                String etName = new String(etNameBytes, 0, length-1, "US-ASCII");

//System.out.println("et_listen_thread: received packet version =  " + version +
//                                  ", ET = " + etName);
                if (config.getDebug() >= EtConstants.debugInfo) {
                    System.out.println("et_listen_thread: received packet from " +
                            rPacket.getAddress().getHostName() +
                            " @ " + rPacket.getAddress().getHostAddress() +
                            " for " + etName);
                }

                // check if the ET system the client wants is ours
                if (etName.equals(sys.getName())) {
                    // we're the one the client is looking for, send a reply
                    DatagramPacket sPacket = new DatagramPacket(sBuffer, sBuffer.length,
                                                                rPacket.getAddress(), rPacket.getPort());
                    if (config.getDebug() >= EtConstants.debugInfo) {
                        System.out.println("et_listen_thread: send return packet");
                    }
                    sock.send(sPacket);
                }
            }
            catch (IOException ex) {
                if (config.getDebug() >= EtConstants.debugError) {
                    System.out.println("error handling UDP packets");
                    ex.printStackTrace();
                }
            }
        }
    }


}

