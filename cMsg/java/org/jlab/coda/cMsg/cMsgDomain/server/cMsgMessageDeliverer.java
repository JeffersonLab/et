/*----------------------------------------------------------------------------*
 *  Copyright (c) 2005        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 8-Feb-2005, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.server;

import org.jlab.coda.cMsg.*;

import java.io.*;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * This class delivers messages from the subdomain handler objects in the cMsg
 * domain to a particular client.<p>
 * Various types of messages may be sent. These are defined to be: <p>
 * <ul>
 * <li>{@link org.jlab.coda.cMsg.cMsgConstants#msgGetResponse} for a message sent in
 * response to a {@link org.jlab.coda.cMsg.cMsg#sendAndGet}<p>
 * <li>{@link org.jlab.coda.cMsg.cMsgConstants#msgServerGetResponse} for a message sent in
 * response to a {@link org.jlab.coda.cMsg.cMsgDomain.client.cMsgServerClient#serverSendAndGet}
 * with a return acknowlegment<p>
 * <li>{@link org.jlab.coda.cMsg.cMsgConstants#msgSubscribeResponse} for a message sent in
 * response to a {@link org.jlab.coda.cMsg.cMsg#subscribe}
 * with a return acknowlegment<p>
 * <li>{@link org.jlab.coda.cMsg.cMsgConstants#msgShutdownClients} for a message to shutdown
 * the receiving client<p>
 * </ul>
 */
public class cMsgMessageDeliverer implements cMsgDeliverMessageInterface {

    /** Communication channel used by subdomainHandler to talk to client. */
    private SocketChannel channel;
    /** Buffered data input stream associated with channel socket. */
    private DataInputStream  in;
    /** Buffered data output stream associated with channel socket. */
    private DataOutputStream out;

    //-----------------------------------------------------------------------------------

    /**
     * Create a message delivering object for use with one specific client.
     * @param info information object about client to talk to
     * @throws IOException if cannot establish communication with client
     */
    public cMsgMessageDeliverer(cMsgClientInfo info) throws IOException {
        createClientConnection(info);
    }

    /**
     * Method to close all streams and sockets.
     */
    synchronized public void close() {
        try {in.close();}
        catch (IOException e) {}

        try {out.close();}
        catch (IOException e) {}

        try {channel.close();}
        catch (IOException e) {}
    }

    /**
     * Method to deliver a message from a domain server's subdomain handler to a client.
     *
     * @param msg message to sent to client
     * @param msgType type of communication with the client
     * @throws IOException if the message cannot be sent over the channel
     *                     or client returns an error
     * @throws cMsgException if connection to client has not been established
     */
    synchronized public void deliverMessage(cMsgMessage msg, int msgType)
            throws IOException, cMsgException {
        deliverMessageReal(msg, msgType, false);
    }

    /**
     * Method to deliver a message from a domain server's subdomain handler to a client
     * and receive acknowledgment that the message was received.
     *
     * @param msg message to sent to client
     * @param msgType type of communication with the client
     * @return true if message acknowledged by receiver or acknowledgment undesired, otherwise false
     * @throws IOException if the message cannot be sent over the channel
     *                     or client returns an error
     * @throws cMsgException if connection to client has not been established
     */
    synchronized public boolean deliverMessageAndAcknowledge(cMsgMessage msg, int msgType)
            throws IOException, cMsgException {
        return deliverMessageReal(msg, msgType, true);
    }

    /**
     * Creates a socket communication channel to a client.
     * @param info client information object
     * @throws IOException if socket cannot be created
     */
    synchronized public void createClientConnection(cMsgClientInfo info) throws IOException {
        channel = SocketChannel.open(new InetSocketAddress(info.getClientHost(),
                                                           info.getClientPort()));
        // set socket options
        Socket socket = channel.socket();
        // Set tcpNoDelay so no packets are delayed
        socket.setTcpNoDelay(true);
        // set buffer sizes
        socket.setReceiveBufferSize(65535);
        socket.setSendBufferSize(65535);

        // create buffered communication streams for efficiency
        in  = new DataInputStream(new BufferedInputStream(channel.socket().getInputStream(), 2048));
        out = new DataOutputStream(new BufferedOutputStream(channel.socket().getOutputStream(), 65536));
    }

    /**
     * Method to deliver a message to a client.
     *
     * @param msg     message to be sent
     * @param msgType type of communication with the client
     * @param acknowledge if acknowledgement of message is desired, set to true
     * @return true if message acknowledged by receiver or acknowledgment undesired, otherwise false
     * @throws cMsgException if connection to client has not been established
     * @throws IOException if the message cannot be sent over the channel
     *                     or client returns an error
     */
    private boolean deliverMessageReal(cMsgMessage msg, int msgType, boolean acknowledge)
            throws IOException, cMsgException {

        if (in == null || out == null || !channel.isOpen()) {
            throw new cMsgException("Call createClientConnection first to create connection to client");
        }


        if (msgType == cMsgConstants.msgShutdownClients) {
            // size
            out.writeInt(8);
            // msg type
            out.writeInt(msgType);
            // want an acknowledgment?
            out.writeInt(acknowledge ? 1 : 0);
            out.flush();
        }
        else {
            // write 20 ints
//System.out.println("deliverer: Normal message actually being sent");
            int len1 = msg.getSender().length();
            int len2 = msg.getSenderHost().length();
            int len3 = msg.getSubject().length();
            int len4 = msg.getType().length();
            int len5 = msg.getCreator().length();
            int len6 = msg.getText().length();
            int binLength;
            if (msg.getByteArray() == null) {
                binLength = 0;
            }
            else {
                binLength = msg.getByteArrayLength();
            }
            // size of everything sent (except "size" itself which is first integer)
            int size = len1 + len2 + len3 + len4 + len5 + len6 +
                       binLength + 4*19;

            out.writeInt(size);
            out.writeInt(msgType);
            out.writeInt(msg.getVersion());
            out.writeInt(0); // reserved for future use
            out.writeInt(msg.getUserInt());
            out.writeInt(msg.getInfo());

            // send the time in milliseconds as 2, 32 bit integers
            out.writeInt((int) (msg.getSenderTime().getTime() >>> 32)); // higher 32 bits
            out.writeInt((int) (msg.getSenderTime().getTime() & 0x00000000FFFFFFFFL)); // lower 32 bits
            out.writeInt((int) (msg.getUserTime().getTime() >>> 32));
            out.writeInt((int) (msg.getUserTime().getTime() & 0x00000000FFFFFFFFL));

            out.writeInt(msg.getSysMsgId());
            out.writeInt(msg.getSenderToken());
            out.writeInt(len1);
            out.writeInt(len2);
            out.writeInt(len3);
            out.writeInt(len4);
            out.writeInt(len5);
            out.writeInt(len6);
            out.writeInt(binLength);
            out.writeInt(acknowledge ? 1 : 0);

            // write strings
            try {
                out.write(msg.getSender().getBytes("US-ASCII"));
                out.write(msg.getSenderHost().getBytes("US-ASCII"));
                out.write(msg.getSubject().getBytes("US-ASCII"));
                out.write(msg.getType().getBytes("US-ASCII"));
                out.write(msg.getCreator().getBytes("US-ASCII"));
                out.write(msg.getText().getBytes("US-ASCII"));
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

        }

        // If no acknowledgment required, return
        if (!acknowledge) {
            return true;
        }

        if (in.readInt() != cMsgConstants.ok) return false;
        return true;
    }


}
