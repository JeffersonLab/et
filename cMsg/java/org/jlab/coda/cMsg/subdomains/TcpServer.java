// still to do:


/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *     E.Wolin, 5-oct-2004                                                    *
 *                                                                            *
 *     Author: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/


package org.jlab.coda.cMsg.subdomains;

import org.jlab.coda.cMsg.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.net.Socket;



//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * cMsg subdomain handler to access tcpserver processes
 *
 *  UDL:   cMsg:cMsg://host:port/TcpServer/srvHost:srvPort
 *
 * @author Elliott Wolin
 * @version 1.0
 */
public class TcpServer extends cMsgSubdomainAdapter{


    /** registration params. */
    private cMsgClientInfo myClientInfo;


    /** UDL remainder. */
    private String myUDLRemainder;


    /** Object used to deliver messages to the client. */
    private cMsgDeliverMessageInterface myDeliverer;


    // for tcpserver connection
    private String mySrvHost            = null;
    private int mySrvPort               = 5001;   // default port
    private static final int srvFlag    = 1;      // special flag


    // misc
    private String myName               = "tcpserver";
    private String myHost               = null;


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "sendAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendAndGetRequest}
     * method.
     *
     * @return true if sendAndGet implemented in {@link #handleSendAndGetRequest}
     */
    public boolean hasSendAndGet() {
        return true;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to give the subdomain handler the appropriate part
     * of the UDL the client used to talk to the domain server.
     *
     * @param UDLRemainder last part of the UDL appropriate to the subdomain handler
     * @throws cMsgException
     */
    public void setUDLRemainder(String UDLRemainder) throws cMsgException {
        myUDLRemainder=UDLRemainder;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to give the subdomain handler on object able to deliver messages
     * to the client.
     *
     * @param deliverer object able to deliver messages to the client
     * @throws cMsgException
     */
    public void setMessageDeliverer(cMsgDeliverMessageInterface deliverer) throws cMsgException {
        if (deliverer == null) {
            throw new cMsgException("TcpServer subdomain must be able to deliver messages, set the deliverer.");
        }
        myDeliverer = deliverer;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to register domain client.
     *
     * Connects to tcpserver.
     *
     * @param info information about client
     * @throws cMsgException if unable to register
     */
    public void registerClient(cMsgClientInfo info) throws cMsgException {


        // save client info
        myClientInfo = info;


        // extract tcpserver host and port from UDL remainder
        String s;
        int ind = myUDLRemainder.indexOf("?");
        if(ind>0) {
            s=myUDLRemainder.substring(0,ind);
        } else {
            s=myUDLRemainder;
        }

        ind=s.indexOf(":");
        if(ind>0) {
            mySrvHost=s.substring(0,ind);
            mySrvPort=Integer.parseInt(s.substring(ind));
        } else {
            mySrvHost=s;
        }


        // set host
        try {
            myHost = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            System.err.println(e);
            myHost = "unknown";
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Sends text string to server to execute, returns result.
     * Uses stateless transaction.
     */
    public void handleSendAndGetRequest(cMsgMessageFull msg) throws cMsgException {

        Socket socket            = null;
        DataOutputStream request = null;
        BufferedReader response  = null;


        // establish connection to server
        try {
            socket = new Socket(mySrvHost,mySrvPort);
            socket.setTcpNoDelay(true);

            request = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            response = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }


        // send request to server
        try {
            request.writeInt(srvFlag);
            request.writeInt(msg.getText().length());
            request.write(msg.getText().getBytes("ASCII"));
            request.writeByte('\0');
            request.flush();
            socket.shutdownOutput();
        } catch (IOException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }


        // get response
        String s;
        StringBuffer sb = new StringBuffer();
        try {
            while(((s=response.readLine())!=null) && (s.indexOf("value =")<0)) {
                sb.append(s+"\n");
            }
        } catch (IOException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        } finally {
            try {
                request.close();
                response.close();
                socket.close();
            } catch (Exception e) {
            }
        }


        // return result
        try {
            cMsgMessageFull responseMsg = new cMsgMessageFull();
            responseMsg.setCreator(myName);
            responseMsg.setSubject(msg.getSubject());
            responseMsg.setType(msg.getType());
            responseMsg.setText(sb.toString());
            responseMsg.setSender(myName);
            responseMsg.setSenderHost(myHost);

            if(msg.getText().length()<=0) {
                responseMsg.makeNullResponse(msg);
            } else {
                responseMsg.makeResponse(msg);
            }
            myDeliverer.deliverMessage(responseMsg, cMsgConstants.msgGetResponse);

        } catch (IOException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }
    }


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
}

