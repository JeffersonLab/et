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

import org.jlab.coda.cMsg.*;

/**
 * Simulates an rc broadcast server and rc server together. It dies and them comes back.
 */
public class RCBroadcastServer {

    class BroadcastCallback extends cMsgCallbackAdapter {
         public void callback(cMsgMessage msg, Object userObject) {
             String host = msg.getSenderHost();
             int    port = msg.getUserInt();
             String name = msg.getSender();
             
             //System.out.println("Ran broadcast cb, host = " + host +
             //                   ", port = " + port +
             //                   ", name = " + name);

             // now that we have the message, we know what TCP host and port
             // to connect to in the RC Server domain.
             //System.out.println("Starting RC Server");

             //RcServerReconnectThread rcserver  = new RcServerReconnectThread(host, port);
             ConnectDisconnectThread rcserver2 = new ConnectDisconnectThread(host, port);
             //RcServerThread rcserver2 = new RcServerThread(host, port);
             rcserver2.start();
        }
    }


    class rcCallback extends cMsgCallbackAdapter {
        public void callback(cMsgMessage msg, Object userObject) {
            String s = (String) userObject;
            System.out.println("RAN CALLBACK, msg sub = " + msg.getSubject() +
                    ", type " + msg.getType() + ", text = " + msg.getText() + ", for " + s);
        }
    }

    class nullCallback extends cMsgCallbackAdapter {
        public void callback(cMsgMessage msg, Object userObject) {
            String s = (String) userObject;
            System.out.println("RAN CALLBACK, for NULL subscription, msg sub = " + msg.getSubject() +
                    ", type " + msg.getType() + ", text = " + msg.getText() + ", for " + s);
        }
    }


    public RCBroadcastServer() {
    }


    public static void main(String[] args) throws cMsgException {
        RCBroadcastServer server = new RCBroadcastServer();
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

        String UDL = "cMsg:rcb://33444?expid=carlExp";

        cMsg cmsg = new cMsg(UDL, "broadcast listener", "udp trial");
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

    }

    /**
     * Class for handling client which does endless connects and disconnects.
     */
    class RcServerThread extends Thread{

        String rcClientHost;
        int rcClientTcpPort;

        RcServerThread(String host, int port) {
            rcClientHost = host;
            rcClientTcpPort = port;
        }

        public void run() {
            try {
                String rcsUDL = "cMsg:rcs://" + rcClientHost + ":" + rcClientTcpPort;

                cMsg server = new cMsg(rcsUDL, "rc server", "connect trial");
//System.out.println("connect");
                server.connect();
                try {Thread.sleep(1000);}
                catch (InterruptedException e) {}
                server.disconnect();
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Class for handling client which does endless connects and disconnects.
     */
    class ConnectDisconnectThread extends Thread{

        String rcClientHost;
        int rcClientTcpPort;

        ConnectDisconnectThread(String host, int port) {
            rcClientHost = host;
            rcClientTcpPort = port;
        }

        public void run() {
            try {
                String rcsUDL = "cMsg:rcs://" + rcClientHost + ":" + rcClientTcpPort;

                cMsg server = new cMsg(rcsUDL, "rc server", "connect/disconnect trial");
//System.out.println("connect");
                server.connect();
                try {Thread.sleep(200);}
                catch (InterruptedException e) {}
                server.disconnect();
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Class for client testing message sending after server disconnects then reconnects.
     */
    class RcServerReconnectThread extends Thread{

        String rcClientHost;
        int rcClientTcpPort;

        RcServerReconnectThread(String host, int port) {
            rcClientHost = host;
            rcClientTcpPort = port;
        }

        public void run() {
            try {
                System.out.println("Starting RC Broadcast Server");
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
                Object unsub2 = server.subscribe(null, null, nullCb, "2nd sub");

                // send stuff to rc client
                cMsgMessage msg = new cMsgMessage();
                msg.setSubject("rcSubject");
                msg.setType("rcType");

                int loops = 5;
                while(loops-- > 0) {
                    System.out.println("Send msg " +  (loops+1) + " to rc client");
                    msg.setUserInt(loops+1);
                    try {Thread.sleep(500);}
                    catch (InterruptedException e) {}
                    server.send(msg);
                }
                server.unsubscribe(unsub);
                server.unsubscribe(unsub2);
                server.disconnect();

                // test reconnect feature
                System.out.println("Now wait 2 seconds and connect again");
                try {Thread.sleep(2000);}
                catch (InterruptedException e) {}

                cMsg server2 = new cMsg(rcsUDL, "rc server", "udp trial");
                server2.connect();
                server2.start();

                server2.subscribe("subby", "typey", cb2, "3rd sub");
                server2.subscribe(null, null, nullCb, "4th sub");

                loops = 5;
                while(loops-- > 0) {
                    System.out.println("Send msg " +  (loops+1) + " to rc client");
                    msg.setUserInt(loops+1);
                    server2.send(msg);
                    try {Thread.sleep(500);}
                    catch (InterruptedException e) {}
                }
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }
        }

    }


}
