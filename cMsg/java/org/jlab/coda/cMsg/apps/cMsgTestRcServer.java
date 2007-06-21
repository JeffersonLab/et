/*----------------------------------------------------------------------------*
 *  Copyright (c) 2007        Jefferson Science Associates,                   *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 1-Feb-2007, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.cMsg;
import org.jlab.coda.cMsg.cMsgCallbackAdapter;
import org.jlab.coda.cMsg.cMsgMessage;
import org.jlab.coda.cMsg.cMsgException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

/**
 * Simulates an rc broadcast server and rc server together.
 */
public class cMsgTestRcServer {
    String rcClientHost;
    int rcClientTcpPort;
    String name;
    CountDownLatch latch;
    int count;
    cMsg cmsg;

    class BroadcastCallback extends cMsgCallbackAdapter {
         public void callback(cMsgMessage msg, Object userObject) {
             rcClientHost = msg.getSenderHost();
             rcClientTcpPort = msg.getUserInt();
             name = msg.getSender();

             System.out.println("RAN CALLBACK, host = " + rcClientHost +
                                ", port = " + rcClientTcpPort +
                                ", name = " + name);

             // finish connection
             latch.countDown();
        }
    }


    class rcCallback extends cMsgCallbackAdapter {
        public void callback(cMsgMessage msg, Object userObject) {
            count++;
            String s = (String) userObject;
            System.out.println("RAN CALLBACK, for regular subscription");
            System.out.println("              msg text = " + msg.getText());
            System.out.println("              msg  sub = " + msg.getSubject());
            System.out.println("              msg type = " + msg.getType());
        }
    }

    class nullCallback extends cMsgCallbackAdapter {
        public void callback(cMsgMessage msg, Object userObject) {
            count++;
            System.out.println("RAN CALLBACK, for NULL subscription");
            System.out.println("              msg text = " + msg.getText());
            System.out.println("              msg  sub = " + msg.getSubject());
            System.out.println("              msg type = " + msg.getType());
        }
    }


    public cMsgTestRcServer() {
        latch = new CountDownLatch(1);
    }


    public static void main(String[] args) throws cMsgException {
        cMsgTestRcServer server = new cMsgTestRcServer();
        server.run();
    }


    public void run() throws cMsgException {
        System.out.println("Starting RC Broadcast Server");

        // RC Broadcast domain UDL is of the form:
        //       cMsg:rcb://<udpPort>?expid=<expid>
        //
        // The intial cMsg:rcb:// is stripped off by the top layer API
        //
        // Remember that for this domain:
        // 1) udp listening port is optional and defaults to 6543 (cMsgNetworkConstants.rcBroadcastPort)
        // 2) the experiment id is given by the optional parameter expid. If none is
        //    given, the environmental variable EXPID is used. if that is not defined,
        //    an exception is thrown

        String UDL = "cMsg:rcb://?expid=carlExp";

        cmsg = new cMsg(UDL, "broadcast listener", "udp trial");
        try {cmsg.connect();}
        catch (cMsgException e) {
            System.out.println(e.getMessage());
            return;
        }

        // enable message reception
        cmsg.start();

        BroadcastCallback cb = new BroadcastCallback();
        // subject and type are ignored in this domain
        cmsg.subscribe("sub", "type", cb, null);

        // wait for incoming message from rc client
        try { latch.await(); }
        catch (InterruptedException e) {}



        // now that we have the message, we know what TCP host and port
        // to connect to in the RC Server domain.
        System.out.println("Starting RC Server");

        //try {Thread.sleep(4000);}
        //catch (InterruptedException e) {}

        // RC Server domain UDL is of the form:
        //       cMsg:rcs://<host>:<tcpPort>?port=<udpPort>
        //
        // The intial cMsg:rcs:// is stripped off by the top layer API
        //
        // Remember that for this domain:
        // 1) host is NOT optional (must start with an alphabetic character according to "man hosts" or
        //    may be in dotted form (129.57.35.21)
        // 2) host can be "localhost"
        // 3) tcp port is optional and defaults to 7654 (cMsgNetworkConstants.rcClientPort)
        // 4) the udp port to listen on may be given by the optional port parameter.
        //    if it's not given, the system assigns one
        String rcsUDL = "cMsg:rcs://" + rcClientHost + ":" + rcClientTcpPort + "/?port=5859";

        cMsg server = new cMsg(rcsUDL, "rc server", "udp trial");
        server.connect();

        server.start();

        rcCallback cb2 = new rcCallback();
        Object unsub = server.subscribe("subby", "typey", cb2, "1st sub");

        nullCallback nullCb = new nullCallback();
        Object unsu2 = server.subscribe(null, null, nullCb, "2nd sub");

        System.out.println("wait 2 sec");
        try {Thread.sleep(2000);}
        catch (InterruptedException e) {}
        System.out.println("done waiting");

        // send stuff to rc client
        cMsgMessage msg = new cMsgMessage();
        msg.setSubject("rcSubject");
        msg.setType("rcType");

        server.send(msg);
        System.out.println("Sent msg (sub = rcSubject, typ = rcType) to rc client, wait 2 ...\n");
        try {Thread.sleep(2000);}
        catch (InterruptedException e) {}

        // Test the sendAndGet
        try {
            msg.setSubject("sAndGSubject");
            msg.setType("sAndGType");
            System.out.println("Call sendAndGet msg (sub = sAndGSubject, typ = sAndGType) to rc client");
            cMsgMessage response = server.sendAndGet(msg,4000);
            System.out.println("Response msg sub = " + response.getSubject() +
                                    ", type = " + response.getType() +
                                    ", text = " + response.getText());
        }
        catch (TimeoutException e) {
            System.out.println("TIMEOUT");
        }

        try {Thread.sleep(2000);}
        catch (InterruptedException e) {}

        server.unsubscribe(unsub);
        server.disconnect();
    }

}
