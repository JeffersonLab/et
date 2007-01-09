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

package org.jlab.coda.et;

import java.lang.*;
import java.io.*;
import java.net.*;

/**
 * This class implements a thread which starts other threads - each of which
 * listen at a different IP address for users trying to find the ET system by
 * broadcasting, multicasting, or the direct sending of a udp packet.
 *
 * @author Carl Timmer
 */

public class SystemUdpServer extends Thread {

  /* Udp port to listen on. */
  private int port;
  /** ET system object. */
  private SystemCreate sys;
  /** ET system configuration. */
  private SystemConfig config;

  /** Createes a new SystemUdpServer object.
   *  @param sys ET system object */
  SystemUdpServer(SystemCreate sys) {
      this.sys = sys;
      config   = sys.config;
      port     = config.serverPort;
  }

  /** Starts threads to listen for packets at a different addresses. */
  public void run() {
      if (config.debug >= Constants.debugInfo) {
          System.out.println("Running UDP Listening Threads");
      }

      // use the default port number since one wasn't specified
      if (port < 1) {
          port = Constants.serverPort;
      }

      // If we're not multicasting or broadcasting, this thread starts one thread
      // for each local IP address (from one or more interfaces).
      //
      // If we're broadcasting, then we use one (1) thread to listen to broadcasts
      // from all local subnets and traffic to all interfaces on one socket. That
      // is because to get broadcasts, Java forces the programmer to bind all
      // addresses to the port being listened on.
      //
      // If we're multicasting and the specified multicast port is the same as the
      // broadcast port, then we use one (1) thread to listen to multicasts,
      // broadcasts from all local subnets, and traffic to all interfaces on one socket.
      // That is because to get multicasts, Java forces the programmer to bind all
      // addresses to the port being listened on and it also gets all broadcasts too.
      //
      // If we're multicasting with a different port than the broadcasting/direct
      // port, then multicasting is treated separately from everything else and has
      // its own socket and thread.

      if (config.getMulticastAddrs().size() > 0) {
          try {
//System.out.println("setting up for multicast on port " + config.getMulticastPort());
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

      if (config.isListeningForBroadcasts()) {
          try {
//System.out.println("setting up for broadcast on port " + config.getUdpPort());
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
      else {

          ListeningThread lis;

          for (InetAddress addr : sys.netAddresses) {
              // ignore loopback address - don't need to listen on that
              //if (addr.getHostAddress().equals("127.0.0.1")) {
              //  continue;
              //}
              if (config.debug >= Constants.debugInfo) {
                  System.out.println("Listening on interface " +
                          addr.getHostAddress() + ", port " + config.udpPort);
              }
              DatagramSocket sock = null;
              try {
                  sock = new DatagramSocket(config.udpPort, addr);
                  sock.setReceiveBufferSize(512);
                  sock.setSendBufferSize(512);
              }
              catch (SocketException e) {
                  e.printStackTrace();
                  System.exit(-1);
              }
              lis = new ListeningThread(sys, addr, sock);
              lis.start();
          }
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
 * @version 6.0
 */

class ListeningThread extends Thread {

  /** Address to listen on. */
  private InetAddress  addr;
  /** ET system object. */
  private SystemCreate sys;
  /** ET system configuration object. */
  private SystemConfig config;
  /** Setup a socket for receiving udp packets. */
  private DatagramSocket sock = null;

    /**
     *  Creates a new ListeningThread object for a UDP multicasts.
     *
     *  @param sys ET system object
     *  @param mSock multicast udp socket
     */
    ListeningThread(SystemCreate sys, MulticastSocket mSock) throws IOException {
        this.sys = sys;
        config   = sys.config;
        for (InetAddress address : config.getMulticastAddrs()) {
            if (address.isMulticastAddress()) {
                mSock.joinGroup(address);
            }
        }
        sock = (DatagramSocket) mSock;
        addr = InetAddress.getLocalHost();
    }

    /**
     *  Creates a new ListeningThread object for a UDP broadcasts.
     *
     *  @param sys ET system object
     *  @param sock udp socket
     */
    ListeningThread(SystemCreate sys, DatagramSocket sock) throws UnknownHostException {
        this.sys  = sys;
        config    = sys.config;
        this.sock = sock;
        addr = InetAddress.getLocalHost();
    }

  /**
   *  Creates a new ListeningThread object.
   *
   *  @param sys ET system object
   *  @param addr address to listen on
   *  a broad/multicasting address (false)
   */
  ListeningThread(SystemCreate sys, InetAddress addr, DatagramSocket sock) {
      this.sys  = sys;
      config    = sys.config;
      this.addr = addr;
      this.sock = sock;
  }

  /** Starts a single thread to listen for udp packets at a specific address
   *  and respond with ET system information. */
  public void run() {

      // packet & buffer to receive UDP packets
      byte[] rBuffer = new byte[512]; // much larger than needed
      DatagramPacket rPacket = new DatagramPacket(rBuffer, 512);

      // Prepare output buffer we send in answer to inquiries:
      // (1) ET version #,
      // (2) port of tcp server thread (not udp port),
      // (3) length of next string,
      // (4) hostname of this interface address
      //     (may or may not be fully qualified),
      // (5) length of next string
      // (6) this interface's address in dotted-decimal form,
      // (7) length of next string,
      // (8) default hostname from getLocalHost (used as a
      //     general identifier of this host no matter which
      //     interface is used). May or may not be fully qualified.

      // Put outgoing packet into byte array
      ByteArrayOutputStream baos = null;

      try {
          String localHost = InetAddress.getLocalHost().getHostName();

          // the send buffer needs to be of byte size ...
          int bufferSize = 3 * 4 + addr.getHostName().length() + 1 +
                  4 + addr.getHostAddress().length() + 1 +
                  4 + localHost.length() + 1;

          baos = new ByteArrayOutputStream(bufferSize);
          DataOutputStream dos = new DataOutputStream(baos);

          dos.writeInt(Constants.version);
          dos.writeInt(config.serverPort);

          // Host name associated with interface, else local host name for broad/multicast
          dos.writeInt(addr.getHostName().length() + 1);
          dos.write(addr.getHostName().getBytes("ASCII"));
          dos.writeByte(0);

          // IP address associated with interface, else local IP address for broad/multicast
          dos.writeInt(addr.getHostAddress().length() + 1);
          dos.write(addr.getHostAddress().getBytes("ASCII"));
          dos.writeByte(0);

          // Send local host name (results of uname in UNIX)
          dos.writeInt(localHost.length() + 1);
          dos.write(localHost.getBytes("ASCII"));
          dos.writeByte(0);
          dos.flush();
      }
      catch (UnsupportedEncodingException ex) {
          // this will never happen.
      }
      catch (UnknownHostException ex) {
          // local host is always known
      }
      catch (IOException ex) {
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
                      if (sys.killAllThreads) {
                          return;
                      }
                  }
              }

              // decode the data:
              // (1) ET version #,
              // (2) length of string,
              // (3) ET file name

              ByteArrayInputStream bais = new ByteArrayInputStream(rPacket.getData());
              DataInputStream dis = new DataInputStream(bais);

              int version = dis.readInt();
              int length = dis.readInt();
//System.out.println("et_listen_thread: received packet version =  " + version +
//                    ", length = " + length);

              // reject incompatible ET versions
              if (version != Constants.version) {
                  continue;
              }
              // reject improper formats
              if ((length < 1) || (length > Constants.fileNameLength)) {
                  continue;
              }

              String etName = new String(rPacket.getData(), 8, length - 1, "ASCII");

              if (config.debug >= Constants.debugInfo) {
                  System.out.println("et_listen_thread: received packet from " +
                          rPacket.getAddress().getHostName() +
                          " @ " + rPacket.getAddress().getHostAddress() +
                          " for " + etName);
              }

              // check if the ET system the client wants is ours
              if (etName.equals(sys.name)) {
                  // we're the one the client is looking for, send a reply
                  DatagramPacket sPacket = new DatagramPacket(sBuffer, sBuffer.length,
                                                              rPacket.getAddress(), rPacket.getPort());
                  if (config.debug >= Constants.debugInfo) {
                      System.out.println("et_listen_thread: send return packet");
                  }
                  sock.send(sPacket);
              }
          }
          catch (IOException ex) {
              if (config.debug >= Constants.debugError) {
                  System.out.println("error handling UDP packets");
                  ex.printStackTrace();
              }
          }
      }
  }


}

