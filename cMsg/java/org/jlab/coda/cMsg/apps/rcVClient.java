package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.*;

/**
 * Vardan's runcontrol client.
 */
public class rcVClient {
    cMsg cmsg;
    int  rcCount;
    String[] args;
    String   myName;
    String   state = "unknown";
    boolean  shouldRun = true;


    public static void main(String[] args) throws cMsgException {
        rcVClient client = new rcVClient(args);
        client.run();
    }

    /** Constructor. */
    public rcVClient(String[] args) {
        this.args = args;
    }


    /**
     * This class defines the callback to be run when...
     */
    class myCallback1 extends cMsgCallbackAdapter {

        public void callback(cMsgMessage mg, Object userObject) {
            String text=null;

            System.out.println(">>>>>> in the monitorTransaction");

            text = mg.getText();
            if(text == null){
                System.out.println("NULL TEXT in callback");
                return;
            }

            if(text.equalsIgnoreCase("stopmonitor")){
                shouldRun = false;
                rcCount=0;
                System.out.println("stopmonitor....?");
                return;
            }
            else if (text.equalsIgnoreCase("startmonitor")) {
                shouldRun = true;
                rcCount=0;
            }

            System.out.println("=======> gets the request to " +
                    text + " rcCount = " + rcCount + " shouldrun = " + shouldRun);
        }
    }



    /**
     * This class defines the callback to be run when a transition message arrives.
     */
    class myCallback2 extends cMsgCallbackAdapter {

         public void callback(cMsgMessage mg, Object userObject) {
            String s=null,ss=null;
            cMsgMessage msg;

            System.out.println(">>>>>> in the callbackTransaction\n");

            rcCount++;

            if (mg == null) {
               System.out.println("Callback got NULL msg passed in !!");
               return;
            }

            // send message "active"
            msg = new cMsgMessage();
            msg.setSubject(myName);
            msg.setType("status");

            s = mg.getText();
            System.out.println("Incoming msg's text = " + s);

            if (s == null){
                System.out.println("NULL TEXT in callback");
                return;
            }

            if(s.equalsIgnoreCase("preprestart")) {
              ss = "sta:" + myName + " preprestarted";
              state = "preprestarted";
            }
            else if(s.contains("prestart")) {
                ss = "sta:" + myName + " paused";
                state = "paused";
            }
            else if(s.equalsIgnoreCase("postprestart")){
                ss = "sta:" + myName + " postprestarted";
                state = "postprestarted";
            }
            else if(s.equalsIgnoreCase("predownload")){
                ss = "sta:" + myName + " predownloaded";
                state = "predownloaded";
                //shouldrun = 1;
            }
            else if(s.contains("download")) {
                ss = "sta:" + myName + " downloaded";
                state = "downloaded";
            }
            else if(s.equalsIgnoreCase("postdownload")){
                ss = "sta:" + myName + " postdownloaded";
                state = "postdownloaded";
            }
            else if(s.equalsIgnoreCase("prego")){
                ss = "sta:" + myName + " pregoed";
                state = "pregoed";
            }
            else if(s.equalsIgnoreCase("go")){
                ss = "sta:" + myName + " active";
                state = "active";
                rcCount = 0;
            }
            else if(s.equalsIgnoreCase("postgo")){
                ss = "sta:" + myName + " postgoed";
                state = "postgoed";
            }
            else if(s.equalsIgnoreCase("preend")){
                ss = "sta:" + myName + " preended";
                state = "preended";
                //shouldrun=0;
            }
            else if(s.equalsIgnoreCase("end")){
                ss = "sta:" + myName + " downloaded";
                state = "downloaded";
            }
            else if(s.equalsIgnoreCase("postend")){
                ss = "sta:" + myName + " postended";
                state = "postended";
            }
            else if(s.equalsIgnoreCase("pause")){
                ss = "sta:" + myName + " paused";
                state = "paused";
            }
            else if(s.equalsIgnoreCase("resume")){
                ss = "sta:" + myName + " resumed";
                state = "resumed";
            }
            else if(s.equalsIgnoreCase("reset")){
                ss = "sta:" + myName + " reseted";
                state = "reseted";
                //shouldrun = 0;
            }
            else {
                System.out.println("Bad command from runcontrol!! get out of callback");
                return;
            }

            // send udp msgs to rc server
            if (ss == null) {
                System.out.println("null udp message in callback");
                return;
            }
            msg.setText(ss);
            try {
                System.out.println("<<<<<<< sending message = " + ss + "  to subject = " + myName +
                        " type = status\n");

                cmsg.send(msg);
            }
            catch (cMsgException e) {
                e.printStackTrace();
                return;
            }
        }
     }


     public void run() throws cMsgException {

         System.out.println("Starting RCV Client");

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
         myName = "java rc client";
         if (args.length > 0) {
             myName = args[0];
         }

         String UDL = "cMsg:rc://";
         if (args.length > 1) {
             UDL = args[1];
         }

         cmsg = new cMsg(UDL, myName, "RC test");
         cmsg.connect();
         System.out.println("Connect is completed!");

         // enable message reception
         cmsg.start();

         // subscribe to subject/type
         String subject = myName;
         String type    = "transition";
         cMsgCallbackInterface cb = new myCallback2();
         Object unsub = cmsg.subscribe(subject, type, cb, null);
         System.out.println("subscribing to subject = " + subject + ", type = " + type);

         // create message
         cMsgMessage msg = new cMsgMessage();
         msg.setSubject(myName);
         msg.setType("statistics");

         double eventRate = 100.0;
         double dataRate  = 300.0;
         int    numLong   = 7777;
         String text;

         // loop forever
         while (true) {
             while (shouldRun) {
                 try {Thread.sleep(1000); }  catch (InterruptedException e) {}
                 rcCount++;
                 text = "sta:" + myName + " " + state + " " + rcCount + " " +
                         eventRate + " " + numLong + " " + dataRate;
                 msg.setText(text);
                 try {
                     cmsg.send(msg);
                 }
                 catch (cMsgException e) {
                     System.out.println("Failed in send of statistics to server");
                 }
             }
             try {Thread.sleep(1000); }  catch (InterruptedException e) {}
         }

         //cmsg.disconnect();

     }
}
