/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 20-Aug-2004, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.client;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.cMsgSubscription;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.io.*;
import java.util.*;
import java.net.Socket;

/**
 * <p>
 * This class implements a cMsg client's thread which listens for
 * communications from the domain server. The server sends it keep alives,
 * messages to which the client has subscribed, and other directives.
 * </p><p>
 * Note that the class org.jlab.cMsg.RCDomain.rcListeningThread is largely
 * the same as this one. If there are any changes made here the identical
 * changes should be made there as well.
 * </p>
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgClientListeningThread extends Thread {

    /** Type of domain this is. */
    private String domainType = "cMsg";

    /** cMsg client that created this object. */
    private cMsg client;

    /** cMsg server client that created this object. */
    private cMsgServerClient serverClient;

    /** Server channel (contains socket). */
    private ServerSocketChannel serverChannel;

    /** Level of debug output for this class. */
    private int debug;

    /**
     * List of all ClientHandler objects. This list is used to
     * end these threads nicely during a shutdown.
     */
    private ArrayList<ClientHandler> handlerThreads;

    /** Setting this to true will kill this thread. */
    private boolean killThread;

    /** Kills this thread. */
    public void killThread() {
        killThread = true;
    }


    /**
     * Constructor for regular clients.
     *
     * @param myClient cMsg client that created this object
     * @param channel socket for listening for connections
     */
    public cMsgClientListeningThread(cMsg myClient, ServerSocketChannel channel) {

        client = myClient;
        serverChannel = channel;
        debug = client.debug;
        handlerThreads = new ArrayList<ClientHandler>(2);
        // die if no more non-daemon thds running
        setDaemon(true);
    }


    /**
     * Constructor for server clients.
     *
     * @param myClient cMsg server client that created this object
     * @param channel suggested port on which to starting listening for connections
     */
    public cMsgClientListeningThread(cMsgServerClient myClient, ServerSocketChannel channel) {
        this((cMsg)myClient, channel);
        this.serverClient = myClient;
    }


    /** Kills ClientHandler threads. */
    void killClientHandlerThreads() {
        // stop threads that get commands/messages over sockets
        for (ClientHandler h : handlerThreads) {
            h.interrupt();
            try {h.channel.close();}
            catch (IOException e) {}
        }
    }


    /** This method is executed as a thread. */
    public void run() {

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("Running Client Listening Thread");
        }

        Selector selector = null;

        try {
            // get things ready for a select call
            selector = Selector.open();

            // set nonblocking mode for the listening socket
            serverChannel.configureBlocking(false);

            // register the channel with the selector for accepts
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            // cMsg object is waiting for this thread to start in connect method,
            // so tell it we've started.
            synchronized(this) {
                notifyAll();
            }

            while (true) {
                // 2 second timeout
                int n = selector.select(2000);

                // if no channels (sockets) are ready, listen some more
                if (n == 0) {
                    // but first check to see if we've been commanded to die
                    if (killThread) return;
                    continue;
                }

                if (killThread) return;

                // get an iterator of selected keys (ready sockets)
                Iterator it = selector.selectedKeys().iterator();

                // look at each key
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();

                    // is this a new connection coming in?
                    if (key.isValid() && key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        // accept the connection from the client
                        SocketChannel channel = server.accept();

                        // set socket options
                        Socket socket = channel.socket();
                        // Set tcpNoDelay so no packets are delayed
                        socket.setTcpNoDelay(true);
                        // set incoming buffer size
                        socket.setReceiveBufferSize(cMsgNetworkConstants.bigBufferSize);

                        // Start up client handling thread & store reference.
                        // The first of the 2 connections is for message receiving.
                        // The second is to respond to keepAlives from the server.
                        handlerThreads.add(new ClientHandler(channel));

                        if (debug >= cMsgConstants.debugInfo) {
                            System.out.println("cMsgClientListeningThread: new connection");
                        }
                    }
                    // remove key from selected set since it's been handled
                    it.remove();
                }
            }
        }
        catch (IOException ex) {
            if (debug >= cMsgConstants.debugError) {
                ex.printStackTrace();
            }
        }
        finally {
            try {serverChannel.close();} catch (IOException ex) {}
            try {if (selector != null) selector.close();} catch (IOException ex) {}
            killClientHandlerThreads();
        }

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("Quitting Client Listening Thread");
        }

        return;
    }


    /**
     * Class to handle a socket connection to the client of which
     * there are 2. One connections handles the server's keepAlive
     * requests of the client. The other handles everything else.
     */
    private class ClientHandler extends Thread {
        /** Socket channel data is coming in on. */
        SocketChannel channel;

        /** Socket input stream associated with channel. */
        private DataInputStream  in;

        /** Socket output stream associated with channel. */
        private DataOutputStream out;

        /** Allocate byte array once (used for reading in data) for efficiency's sake. */
        private byte[] bytes = new byte[65536];

        /** Does the server want an acknowledgment returned? */
        private boolean acknowledge;


        /**
         * Constructor.
         * @param channel socket channel data is coming in on
         */
        ClientHandler(SocketChannel channel) {
            this.channel = channel;

            // die if no more non-daemon thds running
            setDaemon(true);
            start();
        }


        /**
         * This method handles all incoming commands and messages from a domain server to this
         * cMsg client.
         */
        public void run() {

            try {
                // buffered communication streams for efficiency
                in  = new DataInputStream(new BufferedInputStream(channel.socket().getInputStream(), 65536));
                out = new DataOutputStream(new BufferedOutputStream(channel.socket().getOutputStream(), 2048));

                while (true) {
                    if (this.isInterrupted()) {
                        return;
                    }

                    // read first int -- total size in bytes
                    int size = in.readInt();
                    //System.out.println(" size = " + size);

                    // read client's request
                    int msgId = in.readInt();
                    //System.out.println(" msgId = " + msgId);

                    cMsgMessageFull msg;

                    switch (msgId) {

                        case cMsgConstants.msgSubscribeResponse: // receiving a message
                            // read the message here
                            msg = readIncomingMessage();

                            // if server wants an acknowledgment, send one back
                            if (acknowledge) {
                                // send ok back as acknowledgment
                                out.writeInt(cMsgConstants.ok);
                                out.flush();
                            }

                            // run callbacks for this message
                            runCallbacks(msg);

                            break;

                        case cMsgConstants.msgGetResponse:       // receiving a message for sendAndGet
                        case cMsgConstants.msgServerGetResponse: // server receiving a message for sendAndGet
                            // read the message here
                            msg = readIncomingMessage();
                            msg.setGetResponse(true);

                            // if server wants an acknowledgment, send one back
                            if (acknowledge) {
                                // send ok back as acknowledgment
                                out.writeInt(cMsgConstants.ok);
                                out.flush();
                            }

                            // wakeup caller with this message
                            if (msgId == cMsgConstants.msgGetResponse) {
                                wakeGets(msg);
                            }
                            else {
                                runServerCallbacks(msg);
                            }

                            break;

                        case cMsgConstants.msgKeepAlive: // server ckecking to see if this client is still alive
                            if (debug >= cMsgConstants.debugInfo) {
                                System.out.println("handleClient: got keep alive from server");
                            }

                            // if we're a server client, don't send all that monitor data
                            if (serverClient != null) {
//System.out.println("SERVER CLIENT: don't send monitor data");
                                out.writeInt(0);
                                out.flush();
                            }
                            else {
                                sendMonitorInfo();
                            }

                            // send ok back as acknowledgment
                            //out.writeInt(cMsgConstants.ok);
                            //out.flush();
                            break;

                        case cMsgConstants.msgShutdownClients: // server told this client to shutdown
                            if (debug >= cMsgConstants.debugInfo) {
                                System.out.println("handleClient: got shutdown from server");
                            }

                            // If server wants an acknowledgment, send one back.
                            // Do this BEFORE running shutdown.
                            acknowledge = in.readInt() == 1;
                            if (acknowledge) {
                                // send ok back as acknowledgment
                                out.writeInt(cMsgConstants.ok);
                                out.flush();
                            }

                            if (client.getShutdownHandler() != null) {
//System.out.println("handleClient: run client's shutdown handlerr");
                                client.getShutdownHandler().handleShutdown();
                            }
                            break;

                        default:
                            if (debug >= cMsgConstants.debugWarn) {
                                System.out.println("handleClient: can't understand server message = " + msgId);
                            }
                            break;
                    }
                }
            }
//            catch (InterruptedIOException e) {
//            }
            catch (IOException e) {
                if (debug >= cMsgConstants.debugError) {
                    System.out.println("handleClient: I/O ERROR in cMsg client");
                }
            }
            finally {
                // We're here if there is an IO error.
                // Disconnect the client (kill listening (this) thread and keepAlive thread)
                try {in.close();}      catch (IOException e1) {}
                try {out.close();}     catch (IOException e1) {}
                try {channel.close();} catch (IOException e1) {}
            }

            return;
        }


        /**
         * This method gathers and sends monitoring data to the server
         * as a response to the keep alive command.
         * @throws IOException if communication error
         */
        private void sendMonitorInfo() throws IOException {

            String indent1 = "      ";
            String indent2 = "        ";

            StringBuilder xml = new StringBuilder(2048);

            synchronized (client.subscriptions) {

                // for each subscription of this client ...
                for (cMsgSubscription sub : client.subscriptions) {

                    xml.append(indent1);
                    xml.append("<subscription  subject=\"");
                    xml.append(sub.getSubject());
                    xml.append("\"  type=\"");
                    xml.append(sub.getType());
                    xml.append("\">\n");

                    // run through all callbacks
                    int num=0;
                    for (cMsgCallbackThread cbThread : sub.getCallbacks()) {
                        xml.append(indent2);
                        xml.append("<callback  id=\"");
                        xml.append(num++);
                        xml.append("\"  received=\"");
                        xml.append(cbThread.getMsgCount());
                        xml.append("\"  cueSize=\"");
                        xml.append(cbThread.getCueSize());
                        xml.append("\"/>\n");
                    }

                    xml.append(indent1);
                    xml.append("</subscription>\n");
                }
            }

            int size = xml.length() + 4*4 + 8*7;
            out.writeInt(size);
            out.writeInt(xml.length());
            out.writeInt(1); // This is a java client (0 is for C/C++)
            out.writeInt(client.subscribeAndGets.size()); // pending sub&gets
            out.writeInt(client.sendAndGets.size());      // pending send&gets

            out.writeLong(client.numTcpSends);
            out.writeLong(client.numUdpSends);
            out.writeLong(client.numSyncSends);
            out.writeLong(client.numSendAndGets);
            out.writeLong(client.numSubscribeAndGets);
            out.writeLong(client.numSubscribes);
            out.writeLong(client.numUnsubscribes);

            out.write(xml.toString().getBytes("US-ASCII"));
            out.flush();
        }



        /**
         * This method reads an incoming message from the server.
         *
         * @return message read from channel
         * @throws IOException if socket read or write error
         */
        private cMsgMessageFull readIncomingMessage() throws IOException {

            // create a message
            cMsgMessageFull msg = new cMsgMessageFull();

            msg.setVersion(in.readInt());
            // second incoming integer is for future use
            in.skipBytes(4);
            msg.setUserInt(in.readInt());
            msg.setInfo(in.readInt());
            // time message was sent = 2 ints (hightest byte first)
            // in milliseconds since midnight GMT, Jan 1, 1970
            long time = ((long) in.readInt() << 32) | ((long) in.readInt() & 0x00000000FFFFFFFFL);
            msg.setSenderTime(new Date(time));
            // user time
            time = ((long) in.readInt() << 32) | ((long) in.readInt() & 0x00000000FFFFFFFFL);
            msg.setUserTime(new Date(time));
            msg.setSysMsgId(in.readInt());
            msg.setSenderToken(in.readInt());
            // String lengths
            int lengthSender = in.readInt();
            int lengthSenderHost = in.readInt();
            int lengthSubject = in.readInt();
            int lengthType = in.readInt();
            int lengthCreator = in.readInt();
            int lengthText = in.readInt();
            int lengthBinary = in.readInt();
            acknowledge = in.readInt() == 1;

            // bytes expected
            int stringBytesToRead = lengthSender + lengthSenderHost + lengthSubject +
                    lengthType + lengthCreator + lengthText;
            int offset = 0;

            // read all string bytes
            if (stringBytesToRead > bytes.length) {
                bytes = new byte[stringBytesToRead];
            }
            in.readFully(bytes, 0, stringBytesToRead);

            // read sender
            msg.setSender(new String(bytes, offset, lengthSender, "US-ASCII"));
            //System.out.println("sender = " + msg.getSender());
            offset += lengthSender;

            // read senderHost
            msg.setSenderHost(new String(bytes, offset, lengthSenderHost, "US-ASCII"));
            //System.out.println("senderHost = " + msg.getSenderHost());
            offset += lengthSenderHost;

            // read subject
            msg.setSubject(new String(bytes, offset, lengthSubject, "US-ASCII"));
            //System.out.println("subject = " + msg.getSubject());
            offset += lengthSubject;

            // read type
            msg.setType(new String(bytes, offset, lengthType, "US-ASCII"));
            //System.out.println("type = " + msg.getType());
            offset += lengthType;

            // read creator
            msg.setCreator(new String(bytes, offset, lengthCreator, "US-ASCII"));
            //System.out.println("creator = " + msg.getCreator());
            offset += lengthCreator;

            // read text
            if (lengthText > 0) {
                msg.setText(new String(bytes, offset, lengthText, "US-ASCII"));
                //System.out.println("text = " + msg.getText());
                offset += lengthText;
            }

            // read binary array
            if (lengthBinary > 0) {
                byte[] b = new byte[lengthBinary];

                // read all binary bytes
                in.readFully(b, 0, lengthBinary);

                try {
                    msg.setByteArrayNoCopy(b, 0, lengthBinary);
                }
                catch (cMsgException e) {
                }
            }

            // fill in message object's members
            msg.setDomain(domainType);
            msg.setReceiver(client.getName());
            msg.setReceiverHost(client.getHost());
            msg.setReceiverTime(new Date()); // current time
//System.out.println("MESSAGE RECEIVED");
            return msg;
        }


        /**
         * This method runs all appropriate callbacks - each in their own thread -
         * for client subscribe and subscribeAndGet calls.
         *
         * @param msg incoming message
         */
        private void runCallbacks(cMsgMessageFull msg)  {

            if (client.subscribeAndGets.size() > 0) {
                // for each subscribeAndGet called by this client ...
                cMsgGetHelper helper;
                for (Iterator i = client.subscribeAndGets.values().iterator(); i.hasNext();) {
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
            Set<cMsgSubscription> set = client.subscriptions;

            if (set.size() > 0) {
                // if callbacks have been stopped, return
                if (!client.isReceiving()) {
                    if (debug >= cMsgConstants.debugInfo) {
                        System.out.println("runCallbacks: all subscription callbacks have been stopped");
                    }
                    return;
                }

                // set is NOT modified here
//BUGBUG sendMessage can block forever!! then no new subscriptions can be made !!
                synchronized (set) {
                    // for each subscription of this client ...
                    for (cMsgSubscription sub : set) {
                        // if subject & type of incoming message match those in subscription ...
                        if (cMsgMessageMatcher.matches(msg.getSubject(), msg.getType(), sub)) {
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


        /**
         * This method wakes up a thread in a server client waiting in the sendAndGet method
         * and delivers a message to it.
         *
         * @param msg incoming message
         */
        private void runServerCallbacks(cMsgMessageFull msg)  {

            // Get thread waiting on sendAndGet response from another cMsg server.
            // Remove it from table.
            cMsgSendAndGetCallbackThread cbThread =
                    serverClient.serverSendAndGets.remove(msg.getSenderToken());

            // Remove future object for thread waiting on sendAndGet response from
            // another cMsg server.  Remove it from table.
            serverClient.serverSendAndGetCancel.remove(msg.getSenderToken());

            if (cbThread == null) {
                return;
            }

            cbThread.sendMessage(msg);
        }


        /**
         * This method wakes up a thread in a regular client waiting in the sendAndGet method
         * and delivers a message to it.
         *
         * @param msg incoming message
         */
        private void wakeGets(cMsgMessageFull msg) {

            cMsgGetHelper helper = client.sendAndGets.remove(msg.getSenderToken());

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


}

