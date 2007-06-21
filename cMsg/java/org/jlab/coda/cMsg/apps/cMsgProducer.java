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

import org.jlab.coda.cMsg.cMsgException;
import org.jlab.coda.cMsg.cMsgMessage;
import org.jlab.coda.cMsg.cMsg;

import java.util.Arrays;

/**
 * An example class which creates a cMsg message producer.
 */
public class cMsgProducer {
    String  name = "producer";
    String  description = "java producer";
    String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";
    //String  UDL = "cMsg:cmsg://broadcast/cMsg/test";
    String  subject = "SUBJECT";
    String  type = "TYPE";

    String  text;
    char[]  textChars;
    int     textSize;
    boolean sendText;

    byte[]  binArray;
    int     binSize;
    boolean sendBinary;

    int     delay;
    boolean debug;


    /** Constructor. */
    cMsgProducer(String[] args) {
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
                type = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-text")) {
                text = args[i + 1];
                sendText = true;
                i++;
            }
            else if (args[i].equalsIgnoreCase("-textsize")) {
                textSize  = Integer.parseInt(args[i + 1]);
                textChars = new char[textSize];
                Arrays.fill(textChars, 'A');
                text = new String(textChars);
                System.out.println("text len = " + text.length());
                sendText = true;
                i++;
            }
            else if (args[i].equalsIgnoreCase("-binsize")) {
                binSize  = Integer.parseInt(args[i + 1]);
                binArray = new byte[binSize];
                for (int j=0; j < binSize; j++) {
                  binArray[j] = (byte)(j%255);
                }
                System.out.println("binary size = " + binSize);
                sendBinary = true;
                i++;
            }
            else if (args[i].equalsIgnoreCase("-delay")) {
                delay = Integer.parseInt(args[i + 1]);
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
            cMsgProducer producer = new cMsgProducer(args);
            producer.run();
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
     * This method is executed as a thread.
     */
    public void run() throws cMsgException {

        if (debug) {
            System.out.println("Running cMsg producer sending to:\n" +
                               "    subject = " + subject +
                               "\n    type    = " + type);
        }

        // connect to cMsg server
        cMsg coda = new cMsg(UDL, name, description);
        coda.connect();

        // create a message
        cMsgMessage msg = new cMsgMessage();
        msg.setSubject(subject);
        msg.setType(type);
        // set for UDP send
        //msg.getContext().setReliableSend(false);
        if (sendText) {
          System.out.println("Sending text\n");
          msg.setText(text);
        }
        if (sendBinary) {
          System.out.println("Sending byte array\n");
          msg.setByteArrayNoCopy(binArray);
        }

        // variables to track message rate
        double freq=0., freqAvg=0.;
        long t1, t2, deltaT, totalT=0, totalC=0, count=50000, ignore=0;

        // delay between messages
        //if (delay != 0) rcCount = rcCount/(20 + delay);

        while (true) {
            t1 = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                coda.send(msg);
                //int a = coda.syncSend(msg);
                coda.flush(0);
                // delay between messages sent
                if (delay != 0) {
                    try {Thread.sleep(delay);}
                    catch (InterruptedException e) {}
                }
            }
            t2 = System.currentTimeMillis();

            if (ignore == 0) {
                deltaT = t2 - t1; // millisec
                freq = (double) count / deltaT * 1000;
                totalT += deltaT;
                totalC += count;
                freqAvg = (double) totalC / totalT * 1000;

                if (debug) {
                    System.out.println(doubleToString(freq, 1) + " Hz, Avg = " +
                                       doubleToString(freqAvg, 1) + " Hz");
                }
            }
            else {
                ignore--;
            }

        }
    }

}
