package org.jlab.coda.cMsg.RCDomain;

import org.jlab.coda.cMsg.*;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;
import java.util.Set;
import java.io.*;
import java.net.*;

/**
 * This class implements a runcontrol client's thread which listens for
 * communications from the runcontrol domain server. The server sends it
 * messages. Note that this is largely a copy of cMsgClientListeningThread
 * and changes here must be made there. In the C cmsg implementation, both
 * the cMsg client code and this domain use the identical code.
 */
public class rcListeningThread extends Thread {

    /** Type of domain this is. */
    private String domainType = "rc";

    /** cMsg client that created this object. */
    private RunControl client;

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
     * Constructor.
     *
     * @param myClient run control client that created this object
     * @param channel socket for listening for connections
     */
    public rcListeningThread(RunControl myClient, ServerSocketChannel channel) {

        client = myClient;
        serverChannel = channel;
        debug = client.debug;
        handlerThreads = new ArrayList<ClientHandler>(2);
        // die if no more non-daemon thds running
        setDaemon(true);
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
            System.out.println("Running RC Client Listening Thread");
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
                    if (killThread) {
                        return;
                    }
                    continue;
                }

                if (killThread) {
                    return;
                }

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
                        // set buffer sizes
                        socket.setReceiveBufferSize(65535);
                        socket.setSendBufferSize(65535);

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
            try {selector.close();} catch (IOException ex) {}
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
        private DataInputStream in;

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
                    //System.out.println(" size = " + size + ", id = " + id);

                    // read client's request
                    int msgId = in.readInt();
                    //System.out.println(" msgId = " + msgId + ", id = " + id);

                    cMsgMessageFull msg;

                    switch (msgId) {

                        case cMsgConstants.msgSubscribeResponse: // receiving a message
                            // read the message
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

                        // (rc broadcast) server bailing out of connect loop
                        case cMsgConstants.msgRcAbortConnect:
//System.out.println("Got ABORT message!!!");
                            client.abandonConnection = true;
                            client.connectCompletion.countDown();
                            break;

                        // (rc) server finishing connect loop
                        case cMsgConstants.msgRcConnect:
                            // read the message
                            msg = readIncomingMessage();

                            // We need 3 pieces of info from the server: 1) server's host,
                            // 2) server's UDP port, 3) server's TCP port. These are in the
                            // message and must be recorded in the client for future use.
                            client.rcServerAddress = InetAddress.getByName(msg.getSenderHost());
                            String[] ports = msg.getText().split(":");
                            client.rcUdpServerPort = Integer.parseInt(ports[0]);
                            client.rcTcpServerPort = Integer.parseInt(ports[1]);

                            if (client.isConnected()) {
                                // Reestablish the broken socket.
                                // Create a UDP "connection". This means security check is done only once
                                // and communication with any other host/port is not allowed.
                                client.udpSocket.connect(client.rcServerAddress, client.rcUdpServerPort);
                                client.sendUdpPacket = new DatagramPacket(new byte[0], 0,
                                                                          client.rcServerAddress, client.rcUdpServerPort);

                                // create a TCP connection to the RC Server
                                client.tcpSocket = new Socket(client.rcServerAddress, client.rcTcpServerPort);
                                client.tcpSocket.setTcpNoDelay(true);
                                client.tcpSocket.setSendBufferSize(65535);
                                client.domainOut = new DataOutputStream(
                                        new BufferedOutputStream(client.tcpSocket.getOutputStream(), 65536));
                            }
                            else {
//System.out.println("client not connect yet");
                                // complete the client's connect() call
                                client.connectCompletion.countDown();
                            }

                            // Send back a response - the name of this client
                            out.writeInt(client.getName().length());
                            try {
                                out.write(client.getName().getBytes("US-ASCII"));
                            }
                            catch (UnsupportedEncodingException e) { }
                            out.flush();

                            break;

                        default:
                            if (debug >= cMsgConstants.debugWarn) {
                                System.out.println("handleClient: can't understand server message = " + msgId);
                            }
                            break;
                    }
                }
            }
            catch (IOException e) {
                if (debug >= cMsgConstants.debugError) {
                    System.out.println("handleClient: I/O ERROR in RC client");
                }
            }
            finally {
                // We're here if there is an IO error.
                // Disconnect the client (kill listening (this) thread).
                handlerThreads.remove(this);
                try {in.close();}      catch (IOException e1) {}
                try {out.close();}     catch (IOException e1) {}
                try {channel.close();} catch (IOException e1) {}                
            }

            return;
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

            return msg;
        }


        /**
         * This method runs all appropriate callbacks - each in their own thread -
         * for client subscribe and subscribeAndGet calls.
         *
         * @param msg incoming message
         */
        private void runCallbacks(cMsgMessageFull msg)  {

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

    }


}
