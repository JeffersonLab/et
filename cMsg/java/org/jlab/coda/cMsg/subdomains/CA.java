// still to do:
//   multiple CA types (type can be string,double,etc; what about subject,text?)
//   what should be in request and response message?
//   addr_list includes space-separated list of addresses?


// for JCA/CAJ to work must have in classpath:
//    concurrent-1.3.1.jar
//    jca-2.1.5.jar
//    caj-1.0.1.jar



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

import gov.aps.jca.*;
import gov.aps.jca.dbr.DOUBLE;
import gov.aps.jca.dbr.DBR;
import gov.aps.jca.configuration.DefaultConfiguration;
import gov.aps.jca.Monitor;
import gov.aps.jca.event.MonitorListener;
import gov.aps.jca.event.MonitorEvent;
import gov.aps.jca.CAStatus;

import java.util.*;
import java.util.Date;
import java.io.IOException;
import java.util.regex.*;
import java.net.*;


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * cMsg subdomain handler for channel access (CA) subdomain.
 * WARNING: This class may need some thread-safety measures added (Timmer).
 *
 * Executes send/get as CA put/get command.
 *
 * Uses JCA+CAJ.
 *
 * @author Elliott Wolin
 * @version 1.0
 */
public class CA extends cMsgSubdomainAdapter {


    /** registration params. */
    private cMsgClientInfo myClientInfo;


    /** UDL remainder for this subdomain handler. */
    private String myUDLRemainder;


    /** Object used to deliver messages to the client. */
    private cMsgDeliverMessageInterface myDeliverer;


    /** Context. */
    private Context myContext = null;


    /** channel info. */
    private String myChannelName = null;
    private Channel myChannel    = null;


    /** for monitoring */
    private Monitor myMonitor    = null;
    private String mySubscribeType;
    private String mySubscribeSubject;
    private ArrayList<Integer> mySubscribeIdList = new ArrayList<Integer>(10);


    /** misc params. */
    private double myContextPend = 3.0;
    private double myGetPend     = 3.0;
    private double myPutPend     = 3.0;
    private int mySenderId       = 0;


    /** static variables. */
    private static int senderCount = 0;
    private static String host     = null;
    static {
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            System.err.println(e);
            host = "unknown";
        }
    }


    /** for monitoring */
    private class MonitorListenerImpl implements MonitorListener {

        public void monitorChanged(MonitorEvent event) {

            if(event.getStatus()==CAStatus.NORMAL) {

                cMsgMessageFull cmsg = new cMsgMessageFull();
                cmsg.setDomain("cMsg");
                cmsg.setSender("CA");
                cmsg.setUserInt(mySenderId);
                cmsg.setSenderHost(host);
                cmsg.setSenderTime(new Date());
                cmsg.setReceiver(myClientInfo.getName());
                cmsg.setReceiverHost(myClientInfo.getClientHost());
                cmsg.setReceiverTime(new Date());
                cmsg.setSubject(mySubscribeSubject);
                cmsg.setType(mySubscribeType);
                cmsg.setText("" + (((DOUBLE)event.getDBR()).getDoubleValue())[0]);

                synchronized (mySubscribeIdList) {
                    try {
                        myDeliverer.deliverMessage(cmsg, cMsgConstants.msgSubscribeResponse);
                    } catch (cMsgException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            } else {
                System.err.println("Monitor error: " + event.getStatus());
            }
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "send" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendRequest}
     * method.
     *
     * @return true if get implemented in {@link #handleSendRequest}
     */
    public boolean hasSend() {
        return true;
    };


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "syncSend" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSyncSendRequest}
     * method.
     *
     * @return true if send implemented in {@link #handleSyncSendRequest}
     */
    public boolean hasSyncSend() {
        return true;
    };


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "subscribeAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeAndGetRequest}
     * method.
     *
     * @return true if subscribeAndGet implemented in {@link #handleSubscribeAndGetRequest}
     */
    public boolean hasSubscribeAndGet() {
        return true;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "subscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSubscribeRequest}
     * method.
     *
     * @return true if subscribe implemented in {@link #handleSubscribeRequest}
     */
    public boolean hasSubscribe() {
        return true;
    };


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "unsubscribe" cMsg API function is implemented
     * by this interface implementation in the {@link #handleUnsubscribeRequest}
     * method.
     *
     * @return true if unsubscribe implemented in {@link #handleUnsubscribeRequest}
     */
    public boolean hasUnsubscribe() {
        return true;
    };


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
            throw new cMsgException("CA subdomain must be able to deliver messages, set the deliverer.");
        }
        myDeliverer = deliverer;
    }


//-----------------------------------------------------------------------------

    /**
     * Method to register domain client.
     *
     * Creates JCA, context, and channel objects.
     *
     * @param info information about client
     * @throws cMsgException if unable to register
     */
    public void registerClient(cMsgClientInfo info) throws cMsgException {

        String remainder = null;
        Pattern p;
        Matcher m;


        myClientInfo = info;
        mySenderId   = ++senderCount;


        // extract channel from UDL remainder
        int ind = myUDLRemainder.indexOf("?");
        if(ind>0) {
            p = Pattern.compile("^(.+?)(\\?)(.*)$");
            m = p.matcher(myUDLRemainder);
            m.find();
            myChannelName = myUDLRemainder.substring(0,ind);
            remainder     = myUDLRemainder.substring(ind)+ "&";
        } else {
            myChannelName = myUDLRemainder;
        }


        // get JCA library
        JCALibrary myJCA = JCALibrary.getInstance();


        // parse remainder and set JCA context options
        DefaultConfiguration conf = new DefaultConfiguration("myContext");
        conf.setAttribute("class", JCALibrary.CHANNEL_ACCESS_JAVA);
        conf.setAttribute("auto_addr_list","false");
        if(remainder!=null) {
            p = Pattern.compile("[&\\?]addr_list=(.*?)&", Pattern.CASE_INSENSITIVE);
            m = p.matcher(remainder);
            if(m.find())conf.setAttribute("addr_list",m.group(1));
        }


        // create context
        try {
            myContext = myJCA.createContext(conf);
        } catch (CAException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }


        // create channel object
        try {
            myChannel = myContext.createChannel(myChannelName);
            myContext.pendIO(myContextPend);
        } catch  (CAException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        } catch  (TimeoutException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle message sent by client.
     *
     * @param msg message from sender
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                          or socket properties cannot be set
     */
    public void handleSendRequest(cMsgMessageFull msg) throws cMsgException {

        // put value from text field as double
        try {
            myChannel.put(Double.parseDouble(msg.getText()));
            myContext.pendIO(myPutPend);
        } catch  (CAException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        } catch  (TimeoutException e) {
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle message sent by domain client in synchronous mode.
     * It requries an integer response from the subdomain handler.
     *
     * @param msg message from sender
     * @return response from subdomain handler
     * @throws cMsgException
     */
    public int handleSyncSendRequest(cMsgMessageFull msg) throws cMsgException {
        handleSendRequest(msg);
        return 0;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to synchronously get a single message from the server for a one-time
     * subscription of a subject and type.
     *
     * @param subject message subject subscribed to
     * @param type    message type subscribed to
     * @param id      message id refering to these specific subject and type values
     * @throws cMsgException
     */
    public void handleSubscribeAndGetRequest(String subject, String type, int id)
            throws cMsgException {


        // create response message
        cMsgMessageFull response = new cMsgMessageFull();
        //response = message.response();
        response.setDomain("cMsg");
        response.setSender("CA");
        response.setUserInt(mySenderId);
        response.setSenderHost(host);
        response.setSenderTime(new Date());
        response.setReceiver(myClientInfo.getName());
        response.setReceiverHost(myClientInfo.getClientHost());
        response.setReceiverTime(new Date());
        response.setSubject(subject);
        response.setType(type);


        // get value as double, and return as string representation of double
        try {
            DBR dbr = myChannel.get();
            myContext.pendIO(myGetPend);
            response.setText("" + (((DOUBLE)dbr).getDoubleValue())[0]);
        } catch  (CAException e) {
            response.setText(null);
            e.printStackTrace();
        } catch  (TimeoutException e) {
            response.setText(null);
            e.printStackTrace();
        }


        // return response
        try {
            myDeliverer.deliverMessage(response, cMsgConstants.msgSubscribeResponse);
        } catch (IOException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle subscribe request sent by domain client.
     *
     * Performs CA monitorOn.
     *
     * @param subject message subject to subscribe to
     * @param type message type to subscribe to
     * @param receiverSubscribeId message id refering to these specific subject and type values
     * @throws cMsgException if no client information is available or a subscription for this
     *                          subject and type already exists
     */
    public void handleSubscribeRequest(String subject, String type,
                                       int receiverSubscribeId) throws cMsgException {

        synchronized (mySubscribeIdList) {
            mySubscribeIdList.add(receiverSubscribeId);
        }

        if(myMonitor==null) {
            mySubscribeType    = "double";
            mySubscribeSubject = myChannelName;
            try {
                myMonitor = myChannel.addMonitor(Monitor.VALUE, new MonitorListenerImpl());
                myContext.flushIO();

            } catch (CAException e) {
                e.printStackTrace();
                cMsgException ce = new cMsgException(e.toString());
                ce.setReturnCode(1);
                throw ce;
            }
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle sunsubscribe request sent by domain client.
     * This method is run after all exchanges between domain server and client.
     *
     * Performs CA monitorOff.
     *
     * @param subject message subject subscribed to
     * @param type message type subscribed to
     */
    public void handleUnsubscribeRequest(String subject, String type, int receiverSubscribeId)
        throws cMsgException {

        synchronized (mySubscribeIdList) {

            mySubscribeIdList.remove(receiverSubscribeId);

            if((myMonitor!=null)&&(mySubscribeIdList.isEmpty())) {
                try {
                    myMonitor.clear();
                    myMonitor=null;
                } catch (CAException e) {
                    e.printStackTrace();
                    cMsgException ce = new cMsgException(e.toString());
                    ce.setReturnCode(1);
                    throw ce;
                }
            }
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle a client shutdown.
     *
     * @throws cMsgException
     */
    public void handleClientShutdown() throws cMsgException {


        // disconnect channel
        try {
            myChannel.destroy();
        } catch (CAException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }


        // flush and destroy context
        try {
            myContext.flushIO();
            myContext.destroy();
        } catch (CAException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }

    }


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
}

