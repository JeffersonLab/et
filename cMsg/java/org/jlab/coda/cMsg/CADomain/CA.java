// still to do:
//    other CA datatypes besides double



/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    E. Wolin, 12-Nov-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.CADomain;

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
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * This class implements a client in the cMsg Channel Access (CA) domain.
 *
 * UDL:  cMsg:CA://channelName?addr_list=list.
 *
 * where addr_list specifies the UDP broadcast address list.
 *
 * @author Elliott Wolin
 * @version 1.0
 */
public class CA extends cMsgDomainAdapter {


    /** JCALibrary and context. */
    private JCALibrary myJCA  = null;
    private Context myContext = null;


    /** channel info. */
    private String myChannelName = null;
    private Channel myChannel    = null;
    private String myAddrList    = "none";


    /** for subscriptions and monitoring */
    private Monitor myMonitor    = null;
    private ArrayList<SubInfo> mySubList  = new ArrayList<SubInfo>(10);


    /** misc params. */
    private double myContextPend = 3.0;
    private double myPutPend     = 3.0;


    /** for monitoring */
    private class MonitorListenerImpl implements MonitorListener {

        public void monitorChanged(MonitorEvent event) {

            if(event.getStatus()==CAStatus.NORMAL) {

                //  get cMsg and fill common fields
                cMsgMessageFull cmsg = new cMsgMessageFull();
                cmsg.setDomain(domain);
                cmsg.setCreator(myChannelName);
                cmsg.setSender(myChannelName);
                cmsg.setSenderHost(myAddrList);
                cmsg.setSenderTime(new Date());
                cmsg.setReceiver(name);
                cmsg.setReceiverHost(host);
                cmsg.setReceiverTime(new Date());
                cmsg.setText("" + (((DOUBLE)event.getDBR()).getDoubleValue())[0]);


                // dispatch cMsg to all registered callbacks
                synchronized (mySubList) {
                    for(SubInfo s : mySubList) {
                        cmsg.setSubject(s.subject);
                        cmsg.setType(s.type);
                        new Thread(new DispatchCB(s,(cMsgMessage)cmsg)).start();
                    }
                }


            } else {
                System.err.println("Monitor error: " + event.getStatus());
            }
        }
    }

    
    /** for dispatching callbacks */
    private class DispatchCB extends Thread {

        private SubInfo s;
        private cMsgMessage c;

        DispatchCB(SubInfo s, cMsgMessage c) {
            this.s=s;
            this.c=c;
        }

        public void run() {
            s.cb.callback(c,s.userObj);
        }

    }


    /** holds subscription info */
    private class SubInfo {

        String subject;
        String type;
        cMsgCallbackInterface cb;
        Object userObj;

        SubInfo(String s, String t, cMsgCallbackInterface c, Object o) {
            subject = s;
            type    = t;
            cb      = c;
            userObj = o;
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Constructor for CADomain.
     *
     * @throws cMsgException if domain in not implemented or there are problems
     */
    public CA() throws cMsgException {
        try {
            host = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            System.err.println(e);
            host = "unknown";
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Connects to channel after parsing UDL.
     *
     * @throws cMsgException if there are communication problems
     */
    synchronized public void connect() throws cMsgException {

        Pattern p;
        Matcher m;
        String remainder = null;

        if (connected) return;

        domain="CA";

        if(UDLremainder==null) throw new cMsgException("?invalid UDL");


        // get channel name
        int ind = UDLremainder.indexOf('?');
        if (ind > 0) {
            myChannelName = UDLremainder.substring(0,ind);
            remainder     = UDLremainder.substring(ind) + "&";
        } else {
            myChannelName = UDLremainder;
        }


        // get JCA library
        myJCA = JCALibrary.getInstance();


        // parse remainder and set JCA context options
        DefaultConfiguration conf = new DefaultConfiguration("myContext");
        conf.setAttribute("class", JCALibrary.CHANNEL_ACCESS_JAVA);
        conf.setAttribute("auto_addr_list","false");
        if(remainder!=null) {
            p = Pattern.compile("[&\\?]addr_list=(.*?)&", Pattern.CASE_INSENSITIVE);
            m = p.matcher(remainder);
            if(m.find()) {
                myAddrList=m.group(1);
                conf.setAttribute("addr_list",myAddrList);
            }
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

        connected = true;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to close the connection to the domain server. This method results in this object
     * becoming functionally useless.
     *
     * @throws cMsgException always throws an exception since this is a dummy implementation
     */
    synchronized public void disconnect() throws cMsgException {
        if (!connected) return;

        try {
            connected = false;
            myChannel.destroy();
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


    /**
     * Method to send a message to the domain server for further distribution.
     *
     * @param msg message
     * @throws cMsgException always throws an exception since this is a dummy implementation
     */
    synchronized public void send(cMsgMessage msg) throws cMsgException {
        if (!connected) {
            throw new cMsgException("Not connected, Call \"connect\" first");
        }

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
     * Method to force cMsg client to send pending communications with domain server.
     *
     * @param timeout time in milliseconds to wait for completion
     * @throws cMsgException always throws an exception since this is a dummy implementation
     */
    synchronized public void flush(int timeout) throws cMsgException {
        if (!connected) {
            throw new cMsgException("Not connected, Call \"connect\" first");
        }

        try {
            myContext.flushIO();
        } catch (CAException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }
    }


//-----------------------------------------------------------------------------


    /**
     * This method does two separate things depending on the specifics of message in the
     * argument. If the message to be sent has its "getRequest" field set to be true using
     * {@link cMsgMessage#isGetRequest()}, then the message is sent as it would be in the
     * {@link #send} method. The server notes the fact that a response to it is expected,
     * and sends it to all subscribed to its subject and type. When a marked response is
     * received from a client, it sends that first response back to the original sender
     * regardless of its subject or type.
     *
     * In a second usage, if the message did NOT set its "getRequest" field to be true,
     * then the server grabs the first incoming message of the requested subject and type
     * and sends that to the original sender in response to the get.
     *
     * @param subject subject of message desired from server
     * @param type type of message desired from server
     * @param timeout time in milliseconds to wait for a reponse message
     * @return response message
     * @throws cMsgException always throws an exception since this is a dummy implementation
     */
    public cMsgMessage subscribeAndGet(String subject, String type, int timeout) throws cMsgException {


        cMsgMessageFull response = new cMsgMessageFull();
        response.setDomain(domain);
        response.setCreator(myChannelName);
        response.setSender(myChannelName);
        response.setSenderHost(myAddrList);
        response.setSenderTime(new Date());
        response.setReceiver(name);
        response.setReceiverHost(host);
        response.setReceiverTime(new Date());
        response.setSubject(subject);
        response.setType(type);

        DBR dbr = null;

        synchronized (this) {
            if (!connected) {
                throw new cMsgException("Not connected, Call \"connect\" first");
            }
            // get channel data
            try {
                dbr = myChannel.get();
            }
            catch (CAException e) {
                response.setText(null);
                e.printStackTrace();
                return((cMsgMessage)response);
            }
        }

        // Waiting for answer so don't synchronized this
        try {
            myContext.pendIO(timeout/1000.0);
            response.setText("" + (((DOUBLE)dbr).getDoubleValue())[0]);
        } catch  (CAException e) {
            response.setText(null);
            e.printStackTrace();
        } catch  (TimeoutException e) {
            response.setText(null);
            e.printStackTrace();
        }

        return((cMsgMessage)response);
    }


//-----------------------------------------------------------------------------


    /**
     * Method to subscribe to receive messages of a subject and type from the domain server.
     *
     * @param subject message subject
     * @param type    message type
     * @param cb      callback object whose single method is called upon receiving a message
     *                of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @return handle object to be used for unsubscribing
     * @throws cMsgException always throws an exception since this is a dummy implementation
     */
    synchronized public Object subscribe(String subject, String type, cMsgCallbackInterface cb, Object userObj)
            throws cMsgException {

        if (!connected) {
            throw new cMsgException("Not connected, Call \"connect\" first");
        }

        SubInfo info = new SubInfo(subject,type,cb,userObj);

        synchronized (mySubList) {
            mySubList.add(info);
        }

        if(myMonitor==null) {
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

        return info;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to unsubscribe a previous subscription to receive messages of a subject and type
     * from the domain server. Since many subscriptions may be made to the same subject and type
     * values, but with different callbacks, the callback must be specified so the correct
     * subscription can be removed.
     *
     * @param obj the object "handle" returned from a subscribe call
     * @throws cMsgException always throws an exception since this is a dummy implementation
     */
    synchronized public void unsubscribe(Object obj)
            throws cMsgException {

        if (!connected) {
            throw new cMsgException("Not connected, Call \"connect\" first");
        }

        int cnt=0;
        SubInfo info = (SubInfo)obj;

        // remove subscrition from list
        synchronized (mySubList) {
            mySubList.remove(info);
            cnt = mySubList.size();
        }


        // clear monitor if no subscriptions left
        if(cnt<=0) {
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


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
}
