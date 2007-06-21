/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 7-Sep-2005, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.Arrays;
import java.util.Date;

/**
 * Double-threaded consumer used for testing purposes
 */
public class cMsgDualConsumer extends Thread {
    String  name = "dualConsumer";
    String  description = "java dual sub&Get consumer";
    String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";
    String  subject = "SUBJECT";
    String  type = "TYPE";

    String  text = "TEXT";
    char[]  textChars;
    int     textSize;

    int     timeout = 1000; // 1 second default timeout
    int     delay   = 1; // no default delay
    boolean debug;
    long    count;

    CountDownLatch latch;
    cMsg coda;
    int id;


    /** Constructor. */
    cMsgDualConsumer(String[] args, cMsg coda, CountDownLatch latch, int id) {
        decodeCommandLine(args);
        this.latch = latch;
        this.coda  = coda;
        this.id = id;
    }


    /**
     * Method to decode the command line used to start this application.
     * @param args command line arguments
     */
    public void decodeCommandLine(String[] args) {

        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                usage();
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                //name = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-d")) {
                //description = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-u")) {
                //UDL= args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-s")) {
                subject = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-t")) {
                type = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
            }
            else if (args[i].equalsIgnoreCase("-delay")) {
                delay = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-text")) {
                text = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-textsize")) {
                textSize  = Integer.parseInt(args[i + 1]);
                textChars = new char[textSize];
                Arrays.fill(textChars, 'A');
                text = new String(textChars);
                System.out.println("text len = " + text.length());
                i++;
            }
            else if (args[i].equalsIgnoreCase("-to")) {
                timeout = Integer.parseInt(args[i + 1]);
                i++;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        return;
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
            "   java cMsgProducer [-n name] [-d description] [-u UDL]\n" +
            "                     [-s subject] [-t type] [-text text]\n" +
            "                     [-textsize size in bytes]\n" +
            "                     [-delay millisec] [-debug]\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {

            String  name = "dualConsumer";
            String  description = "java dual consumer";
            String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";

            for (int i = 0; i < args.length; i++) {

                if (args[i].equalsIgnoreCase("-h")) {
                    usage();
                    System.exit(-1);
                }
                else if (args[i].equalsIgnoreCase("-n")) {
                    name = args[i + 1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-d")) {
                    description = args[i + 1];
                    i++;
                }
                else if (args[i].equalsIgnoreCase("-u")) {
                    UDL= args[i + 1];
                    i++;
                }
            }

            cMsg coda = new cMsg(UDL, name, description);
            coda.connect();

            // for simultaneous starting of threads
            CountDownLatch latch = new CountDownLatch(1);

            cMsgDualConsumer c1 = new cMsgDualConsumer(args, coda, latch, 1);
            c1.start();
            cMsgDualConsumer c2 = new cMsgDualConsumer(args, coda, latch, 2);
            c2.start();

            // wait till both threads are ready and waiting
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException e) {
            }

            // start both at the "same" time
            latch.countDown();
        }
        catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /**
     * Method to convert a double to a string with a specified number of decimal places.
     *
     * @param d double to convert to a string
     * @param places number of decimal places
     * @return string representation of the double
     */
    private static String doubleToString(double d, int places) {
        if (places < 0) places = 0;

        double factor = Math.pow(10,places);
        String s = "" + (double) (Math.round(d * factor)) / factor;

        if (places == 0) {
            return s.substring(0, s.length()-2);
        }

        while (s.length() - s.indexOf(".") < places+1) {
            s += "0";
        }

        return s;
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
        }

        public boolean maySkipMessages() {return false;}

        public boolean mustSerializeMessages() {return true;}

        public int  getMaximumThreads() {return 200;}
     }



    /**
     * This method is executed as a thread.
     */
    public void run() {

        if (debug) {
            System.out.println("Running cMsg dual consumer " + id + " subscribed to:\n" +
                               "    subject = " + subject +
                               "\n    type    = " + type);
        }

        // enable message reception
        coda.start();

        // subscribe to subject/type
        //cMsgCallbackInterface cb = new cMsgDualConsumer.myCallback();
        //coda.subscribe(subject, type, cb, null);


        // create a message to send to a responder
        cMsgMessage msg = null;
        cMsgMessage sendMsg = new cMsgMessage();
        sendMsg.setSubject(subject);
        sendMsg.setType(type);
        //sendmsg.setText(text);

        byte[] bin = new byte[10000];
        for (int i=0; i< bin.length; i++) {
            bin[i] = 1;
        }
        sendMsg.setByteArrayNoCopy(bin);

        // variables to track incoming message rate
        double freq=0., freqAvg=0.;
        long   t1, t2, deltaT, totalT=0, totalC=0, count;

        try { latch.await(); }
        catch (InterruptedException e) { }


        while (true) {
            count = 0;

            t1 = (new Date()).getTime();

            // do a bunch of gets
            for (int i=0; i < 1; i++) {
                // do the synchronous sendAndGet with timeout
                try {msg = coda.subscribeAndGet(subject, type, timeout);}
                //try {msg = coda.sendAndGet(sendMsg, timeout);}
                catch (cMsgException e) {
                    e.printStackTrace();
                    continue;
                }
                catch (TimeoutException e) {
                    //System.out.println("Timeout in sendAndGet");
                    continue;
                }
                if (msg == null) {
                    System.out.println("Got null msg");
                }
                else {
                    System.out.println("CLIENT GOT MESSAGE!!!");
                }
                count++;

                //if (delay > 0) {
                //    try {Thread.sleep(delay);}
                //    catch (InterruptedException e) {}
                //}
            }

            t2 = (new Date()).getTime();

            deltaT  = t2 - t1; // millisec
            freq    = (double)count/deltaT*1000;
            totalT += deltaT;
            totalC += count;
            freqAvg = (double)totalC/totalT*1000;

            if (debug) {
                System.out.println("count = " + count + ", " +
                                   doubleToString(freq, 0) + " Hz, Avg = " +
                                   doubleToString(freqAvg, 0) + " Hz");
            }

            if (!coda.isConnected()) {
                System.out.println("No longer connected to domain server, quitting");
                System.exit(-1);
            }
        }

    }
}
