/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 23-Nov-2005, Jefferson Lab                                   *
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
 * Test enterprise cMsg implementation by having 1 thread do
 * continuous subscribeAndGets simultaneously with another
 * thread that does a subscribe on the same sub/type.
 */
public class cMsgEnterpriseTester extends Thread {
    String  name = "dualConsumer";
    String  description = "java cMsg enterprise testing consumer";
    String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";
    String  subject = "SUBJECT";
    String  type = "TYPE";

    String  text = "TEXT";
    char[]  textChars;
    int     textSize;

    int     timeout = 2000; // 2 second default timeout
    int     delay   = 1; // no default delay
    boolean debug;
    long    count;

    CountDownLatch latch;
    cMsg coda;


    /** Constructor. */
    cMsgEnterpriseTester(String[] args, cMsg coda, CountDownLatch latch) {
        decodeCommandLine(args);
        this.latch = latch;
        this.coda  = coda;
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
            coda.start();

            // for simultaneous starting of threads
            CountDownLatch latch = new CountDownLatch(1);

            cMsgEnterpriseTester tester = new cMsgEnterpriseTester(args, coda, latch);
            tester.start();

            // wait till threads are ready and waiting
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

        while (s.length() - s.indexOf(".") < places + 1) {
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
         * @param msg        message received from domain server
         * @param userObject object passed as an argument which was set when the
         *                   client orginally subscribed to a subject and type of
         *                   message.
         */
        public void callback(cMsgMessage msg, Object userObject) {
            count++;
            Thread.yield();
//System.out.print(" " + msg.getUserInt());
        }

        public boolean maySkipMessages() {
            return false;
        }

        public boolean mustSerializeMessages() {
            return true;
        }

        public int getMaximumThreads() {
            return 200;
        }

        public int getMaximumCueSize() {
            return 1000;
        }

        public int getSkipSize() {
            return 100;
        }
    }


    /**
     * This method is executed as a thread.
     */
    public void run() {

        if (debug) {
            System.out.println("Running cMsg enterprise testing consumer subscribed to:\n" +
                               "    subject = " + subject +
                               "\n    type    = " + type);
        }
        cMsgCallbackInterface cb1 = new myCallback();
        cMsgCallbackInterface cb2 = new myCallback();

        SubAndGetter sgThread = new SubAndGetter();
        RegularSubscriber rsThread1 = new RegularSubscriber(1, cb1, "SUBJECT", "TYPE");
        //RegularSubscriber rsThread2 = new RegularSubscriber(2, cb2, "SUBJECT2", "TYPE2");

        sgThread.start();
        rsThread1.start();
        //rsThread2.start();
    }


    class SubAndGetter extends Thread {
        /**
         * This method is executed as a thread.
         */
        public void run() {
Thread.currentThread().setName("SubscribeAndGet");

            if (debug) {
                System.out.println("Running cMsg sub&Get thread");
            }

            cMsgMessage msg = null;

            // variables to track incoming message rate
            double freq = 0., freqAvg = 0.;
            long t1, t2, deltaT, totalT = 0, totalC = 0, myCount;

            try {
                latch.await();
            }
            catch (InterruptedException e) {
            }


            while (true) {
                myCount = 0;

                t1 = (new Date()).getTime();

                // do a bunch of gets
                for (int i = 0; i < 10; i++) {
                    // do the synchronous sendAndGet with timeout
                    try {
                        msg = coda.subscribeAndGet(subject, type, timeout);
//System.out.print(" *" + msg.getUserInt());
                    }
                    catch (cMsgException e) {
                        e.printStackTrace();
                        continue;
                    }
                    catch (TimeoutException e) {
                        System.out.println("Timeout in sendAndGet");
                        continue;
                    }
                    myCount++;

                    //if (delay > 0) {
                    //    try {Thread.sleep(delay);}
                    //    catch (InterruptedException e) {}
                    //}
                }

                t2 = (new Date()).getTime();

                deltaT = t2 - t1; // millisec
                freq = (double) myCount / deltaT * 1000;
                totalT += deltaT;
                totalC += myCount;
                freqAvg = (double) totalC / totalT * 1000;

                if (debug) {
                    System.out.println("  s&G count = " + myCount + ", " +
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

    class RegularSubscriber extends Thread {
        int id;
        cMsgCallbackInterface cb;
        String subject, type;

        public RegularSubscriber(int id, cMsgCallbackInterface cb, String subject, String type) {
            this.id = id;
            this.cb = cb;
            this.subject = subject;
            this.type = type;
        }

        /**
         * This method is executed as a thread.
         */
        public void run() {
Thread.currentThread().setName("Subscribe");

            if (debug) {
                System.out.println("Running cMsg subscribe thread " + id);
            }

            try {
                // subscribe to subject/type

                try {
                    latch.await();
                }
                catch (InterruptedException e) {
                }

                coda.subscribe(subject, type, cb, null);
            }
            catch (cMsgException e) {
                e.printStackTrace();
            }

            // variables to track incoming message rate
            double freq=0., freqAvg=0.;
            long   totalT=0, totalC=0, period = 4000; // millisec

            while (true) {
                count = 0;

                // wait
                try { Thread.sleep(period); }
                catch (InterruptedException e) {}

                freq    = (double)count/period*1000;
                totalT += period;
                totalC += count;
                freqAvg = (double)totalC/totalT*1000;

                if (debug) {
                    System.out.println("sub count " + id + " = " + count + ", " +
                                       doubleToString(freq, 1) + " Hz, Avg = " +
                                       doubleToString(freqAvg, 1) + " Hz");
                }

                if (!coda.isConnected()) {
                    System.out.println("No longer connected to domain server, quitting");
                    System.exit(-1);
                }
            }
        }
    }



}
