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
 * This class implements a run control client.
 */
public class rcClient {

    int count;
    cMsg cmsg;

    public static void main(String[] args) throws cMsgException {
         rcClient client = new rcClient();
         client.run();
     }

    /**
     * This class defines the callback to be run when a message matching
     * our subscription arrives.
     */
    class myCallback extends cMsgCallbackAdapter {
        /**
         * Callback method definition.
         *
         * @param msg message received from domain server
         * @param userObject object passed as an argument which was set when the
         *                   client orginally subscribed to a subject and type of
         *                   message.
         */
        public void callback(cMsgMessage msg, Object userObject) {
            count++;
            System.out.println("Got msg with sub = " + msg.getSubject() + ", typ = " + msg.getType() + ", txt = " + msg.getText());
        }
     }


    /** This class defines our callback object. */
    class sAndGCallback extends cMsgCallbackAdapter {

        /**
         * Callback method definition.
         *
         * @param msg message received from domain server
         * @param userObject object passed as an argument which was set when the
         *                   client orginally subscribed to a subject and type of
         *                   message.
         */
        public void callback(cMsgMessage msg, Object userObject) {
            try {
                cMsgMessage sendMsg;
                try {
                    sendMsg = msg.response();
                }
                catch (cMsgException e) {
                    System.out.println("Callback received non-sendAndGet msg - ignoring");
                    return;
                }
                sendMsg.setSubject("RESPONDING");
                sendMsg.setType("TO MESSAGE");
                sendMsg.setText("responder's text");
                sendMsg.getContext().setReliableSend(false);
                cmsg.send(sendMsg);
                count++;
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }
        }
     }


     public void run() throws cMsgException {

         System.out.println("Starting RC Client");

         /* Runcontrol domain UDL is of the form:
          *        cMsg:rc://<host>:<port>/?expid=<expid>&broadcastTO=<timeout>&connectTO=<timeout>
          *
          * Remember that for this domain:
          * 1) port is optional with a default of 6543 (cMsgNetworkConstants.rcBroadcastPort)
          * 2) host is optional with a default of 255.255.255.255 (broadcast)
          *    and may be "localhost" or in dotted decimal form
          * 3) the experiment id or expid is optional, it is taken from the
          *    environmental variable EXPID
          * 4) broadcastTO is the time to wait in seconds before connect returns a
          *    timeout when a rc broadcast server does not answer
          * 5) connectTO is the time to wait in seconds before connect returns a
          *    timeout while waiting for the rc server to send a special (tcp)
          *    concluding connect message
          */
         //String UDL = "cMsg:rc://?expid=carlExp&broadcastTO=5&connectTO=5";
         String UDL = "cMsg:rc://?expid=carlExp";

         cmsg = new cMsg(UDL, "java rc client", "rc trial");
         cmsg.connect();

         // enable message reception
         cmsg.start();

         // subscribe to subject/type to receive from RC Server
         cMsgCallbackInterface cb = new myCallback();
         Object unsub = cmsg.subscribe("rcSubject", "rcType", cb, null);

         // subscribe to subject/type to receive from RC Server
         cMsgCallbackInterface cb2 = new sAndGCallback();
         Object unsub2 = cmsg.subscribe("sAndGSubject", "sAndGType", cb2, null);

         try {Thread.sleep(1000); }
         catch (InterruptedException e) {}

         // send stuff to RC Server
         cMsgMessage msg = new cMsgMessage();
         msg.setSubject("subby");
         msg.setType("typey");
         msg.setText("Send with TCP");

         System.out.println("Send subby, typey with TCP");
         cmsg.send(msg);

         msg.setText("Send with UDP");
         msg.getContext().setReliableSend(false);
         System.out.println("Send subby, typey with UDP");
         cmsg.send(msg);

         msg.setText("Send to other subs/types");
         msg.setSubject("blah");
         msg.setType("yech");
         msg.getContext().setReliableSend(true);
         System.out.println("Send blah, yech with TCP");
         cmsg.send(msg);

         try {Thread.sleep(12000); }
         catch (InterruptedException e) {}

         cmsg.stop();
         cmsg.unsubscribe(unsub);
         cmsg.unsubscribe(unsub2);
         cmsg.disconnect();

     }
}
