/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et.apps;


import java.lang.*;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Priority;

/**
 * This class is an example of an event producer for an ET system.
 *
 * @author Carl Timmer
 */
public class Producer {

    public Producer() {
    }


    private static void usage() {
        System.out.println("\nUsage: java Producer -f <et name> [-p <server port>] [-host <host>]\n\n" +
                "       -f     ET system's name\n" +
                "       -s     size in bytes for requested events\n" +
                "       -p     port number for a udp broadcast\n" +
                "       -d     delay in millisec between getting and putting events\n" +
                "       -g     group number of events\n" +
                "       -host  host the ET system resides on (defaults to anywhere)\n\n" +
                "        This consumer works by making a connection to the\n" +
                "        ET system's tcp server port.\n");
    }


    public static void main(String[] args) {

        String etName = null, host = null;
        int port = EtConstants.serverPort;
        int group = 1;
        int delay = 0;
        int size = 32;

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("-f")) {
                    etName = args[++i];
                }
                else if (args[i].equalsIgnoreCase("-host")) {
                    host = args[++i];
                }
                else if (args[i].equalsIgnoreCase("-p")) {
                    try {
                        port = Integer.parseInt(args[++i]);
                        if ((port < 1024) || (port > 65535)) {
                            System.out.println("Port number must be between 1024 and 65535.");
                            usage();
                            return;
                        }
                    }
                    catch (NumberFormatException ex) {
                        System.out.println("Did not specify a proper port number.");
                        usage();
                        return;
                    }
                }
                else if (args[i].equalsIgnoreCase("-s")) {
                    try {
                        size = Integer.parseInt(args[++i]);
                        if (size < 1) {
                            System.out.println("Size needs to be positive int.");
                            usage();
                            return;
                        }
                    }
                    catch (NumberFormatException ex) {
                        System.out.println("Did not specify a proper size.");
                        usage();
                        return;
                    }
                }
                else if (args[i].equalsIgnoreCase("-g")) {
                    try {
                        group = Integer.parseInt(args[++i]);
                        if ((group < 1) || (group > 10)) {
                            System.out.println("Group number must be between 0 and 10.");
                            usage();
                            return;
                        }
                    }
                    catch (NumberFormatException ex) {
                        System.out.println("Did not specify a proper group number.");
                        usage();
                        return;
                    }
                }
                else if (args[i].equalsIgnoreCase("-d")) {
                    try {
                        delay = Integer.parseInt(args[++i]);
                        if (delay < 1) {
                            System.out.println("delay must be > 0.");
                            usage();
                            return;
                        }
                    }
                    catch (NumberFormatException ex) {
                        System.out.println("Did not specify a proper delay.");
                        usage();
                        return;
                    }
                }
                else {
                    usage();
                    return;
                }
            }

            if (host == null) {
                host = EtConstants.hostAnywhere;
                /*
                try {
                    host = InetAddress.getLocalHost().getHostName();
                }
                catch (UnknownHostException ex) {
                    System.out.println("Host not specified and cannot find local host name.");
                    usage();
                    return;
                }
                */
            }

            if (etName == null) {
                usage();
                return;
            }

            // make a direct connection to ET system's tcp server
            EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host, port);

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config, EtConstants.debugInfo);

            // get GRAND_CENTRAL station object
            EtStation gc = sys.stationNameToObject("GRAND_CENTRAL");

            // attach to grandcentral
            EtAttachment att = sys.attach(gc);

            // array of events
            EtEvent[] mevs;

            int chunk = 1, count = 0, startingVal = 0;
            long t1, t2, counter = 0, totalT = 0, totalCount = 0;
            double rate, avgRate;
            int[] con = {1, 2, 3, 4};
            String s;

            // keep track of time for event rate calculations
            t1 = System.currentTimeMillis();

            for (int i = 0; i < 50; i++) {
                while (count < 300000L) {
                    // get array of new events
                    mevs = sys.newEvents(att, Mode.SLEEP, 0, chunk, size, group);

                    if (delay > 0) Thread.sleep(delay);

                    // example of how to manipulate events
                    if (true) {
                        for (int j = 0; j < mevs.length; j++) {
                            // put integer (j) into front of data buffer
                            int swappedData = j + startingVal;
                            //swappedData = Integer.reverseBytes(swappedData);
                            mevs[j].getDataBuffer().putInt(swappedData);
                            //mevs[j].setByteOrder(ByteOrder.LITTLE_ENDIAN);
                            // set data length to be 4 bytes (1 integer)
                            mevs[j].setLength(4);
                            // set every other event's priority as high
                            if (j % 2 == 0) mevs[j].setPriority(Priority.HIGH);
                            // set event's control array
                            mevs[j].setControl(con);
                        }
                    }

                    // put events back into ET system
                    sys.putEvents(att, mevs);
                    count += mevs.length;

                    startingVal++;
                }

                // calculate the event rate
                t2 = System.currentTimeMillis();
                rate = 1000.0 * ((double) count) / ((double) (t2 - t1));
                totalCount += count;
                totalT += t2 - t1;
                avgRate = 1000.0 * ((double) totalCount) / totalT;
                System.out.println("rate = " + String.format("%.3g", rate) +
                                   " Hz,   avg = " + String.format("%.3g", avgRate));
                count = 0;
                t1 = System.currentTimeMillis();
            }
            System.out.println("End of producing events, now close");
            sys.close();
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as producer");
            ex.printStackTrace();
        }
    }
    
}
