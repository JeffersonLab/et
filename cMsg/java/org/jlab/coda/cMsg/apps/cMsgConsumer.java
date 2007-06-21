/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer,       2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.*;


/**
 * An example class which creates a cMsg message consumer.
 */
public class cMsgConsumer {

    String  name = "consumer";
    String  description = "java consumer";
    //String  UDL = "cMsg:configFile://configgy;cMsg://aslan:3456/cMsg/test";
    //String  UDL = "cMsg:cmsg://broadcast/cMsg/test";
    String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";
    String  subject = "SUBJECT";
    String  type = "TYPE";
    boolean debug;
    long    count;


    /** Constructor. */
    cMsgConsumer(String[] args) {
        decodeCommandLine(args);
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
                type= args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
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
            "   java cMsgConsumer [-n name] [-d description] [-u UDL]\n" +
            "                     [-s subject] [-t type] [-debug]\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {
            cMsgConsumer consumer = new cMsgConsumer(args);
            consumer.run();
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
//            try {Thread.sleep(10);}
//            catch (InterruptedException e) {}

//System.out.println("Cue size = " + msg.getContext().getCueSize());
            count++;
        }

        public int getMaximumCueSize() {return 10000;}

        public boolean maySkipMessages() {return false;}

        public boolean mustSerializeMessages() {return true;}

        public int  getMaximumThreads() {return 200;}
     }


    /**
     * This method is executed as a thread.
     */
    public void run() throws cMsgException {

        if (debug) {
            System.out.println("Running cMsg consumer subscribed to:\n" +
                               "    subject = " + subject +
                               "\n    type    = " + type);
        }

        // connect to cMsg server
        cMsg coda = new cMsg(UDL, name, description);
        coda.connect();

        // enable message reception
        coda.start();

        // subscribe to subject/type
        cMsgCallbackInterface cb = new myCallback();
        Object unsub  = coda.subscribe(subject, type, cb, null);

        // variables to track incoming message rate
        double freq=0., freqAvg=0.;
        long   totalT=0, totalC=0, period = 5000; // millisec

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
                System.out.println("count = " + count + ", " +
                                   doubleToString(freq, 1) + " Hz, Avg = " +
                                   doubleToString(freqAvg, 1) + " Hz");
            }

            if (!coda.isConnected()) {
                // wait 2 seconds for failover before declaring us dead-in-the-water
                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException e) {
                }
                if (!coda.isConnected()) {
                    System.out.println("No longer connected to domain server, quitting");
                    System.exit(-1);
                }
            }
        }
    }
}
