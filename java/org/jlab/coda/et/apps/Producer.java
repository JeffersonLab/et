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
import java.nio.ByteOrder;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;

/**
 * This class is an example of an event producer for an ET system.
 *
 * @author Carl Timmer
 */
public class Producer {

    public Producer() {
    }


    private static void usage() {
        System.out.println("\nUsage: java Producer -f <ET name> -host <ET host> [-h] [-v] [-r] [-c <chunk size>] [-d <delay>]\n" +
                "                     [-s <event size>] [-g <group>] [-p <ET server port>] [-i <interface address>]\n\n" +
                "       -host  ET system's host\n" +
                "       -f     ET system's (memory-mapped file) name\n" +
                "       -h     help\n" +
                "       -v     verbose output\n" +
                "       -r     act as remote (TCP) client even if ET system is local\n" +
                "       -c     number of events in one get/put array\n" +
                "       -d     delay in millisec between each round of getting and putting events\n" +
                "       -s     event size in bytes\n" +
                "       -g     group from which to get new events (1,2,...)\n" +
                "       -p     ET server port\n" +
                "       -i     outgoing network interface IP address (dot-decimal)\n\n" +
                "        This consumer works by making a direct connection to the\n" +
                "        ET system's server port.\n");
    }


    public static void main(String[] args) {

        String etName = null, host = null, netInterface = null;
        int port = EtConstants.serverPort;
        int group = 1;
        int delay = 0;
        int size  = 32;
        int chunk = 1;
        boolean verbose = false;
        boolean remote  = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-i")) {
                netInterface = args[++i];
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
            else if (args[i].equalsIgnoreCase("-c")) {
                try {
                    chunk = Integer.parseInt(args[++i]);
                    if ((chunk < 1) || (chunk > 1000)) {
                        System.out.println("Chunk size may be 1 - 1000.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper chunk size.");
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
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
            }
            else if (args[i].equalsIgnoreCase("-r")) {
                remote = true;
            }
            else {
                usage();
                return;
            }
        }

        if (host == null || etName == null) {
            usage();
            return;
        }

        try {
            // Make a direct connection to ET system's tcp server
            EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host, port);
            config.setConnectRemotely(remote);

            // EXAMPLE: Broadcast to find ET system
            //EtSystemOpenConfig config = new EtSystemOpenConfig();
            //config.setHost(host);
            //config.setEtName(etName);
            //config.addBroadcastAddr("129.57.29.255"); // this call is not necessary

            // EXAMPLE: Multicast to find ET system
            //ArrayList<String> mAddrs = new ArrayList<String>();
            //mAddrs.add(EtConstants.multicastAddr);
            //EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host,
            //                                mAddrs, EtConstants.multicastPort, 32);

            if (netInterface != null) config.setNetworkInterface(netInterface);

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config);
            if (verbose) sys.setDebug(EtConstants.debugInfo);
            sys.open();

            // get GRAND_CENTRAL station object
            EtStation gc = sys.stationNameToObject("GRAND_CENTRAL");

            // attach to GRAND_CENTRAL
            EtAttachment att = sys.attach(gc);

            // array of events
            EtEvent[] mevs;

            int    count = 0, startingVal = 0;
            long   t1, t2, time, totalT = 0, totalCount = 0;
            double rate, avgRate;

            // create control array of correct size
            int[] con = new int[EtConstants.stationSelectInts];
            for (int i=0; i < EtConstants.stationSelectInts; i++) {
                con[i] = i+1;
            }

            // keep track of time for event rate calculations
            t1 = System.currentTimeMillis();

            while (true) {
                // get array of new events
                mevs = sys.newEvents(att, Mode.SLEEP, false, 0, chunk, size, group);

                if (delay > 0) Thread.sleep(delay);

                // example of how to manipulate events
                if (false) {
                    for (int j = 0; j < mevs.length; j++) {
                        // put integer (j + startingVal) into data buffer
                        int swappedData = Integer.reverseBytes(j + startingVal);
                        mevs[j].getDataBuffer().putInt(swappedData);

                        // big endian by default
                        mevs[j].setByteOrder(ByteOrder.LITTLE_ENDIAN);

                        // set data length to be 4 bytes (1 integer)
                        mevs[j].setLength(4);

                        // set event's control array
                        mevs[j].setControl(con);
                    }
                    startingVal += mevs.length;
                }

                // put events back into ET system
                sys.putEvents(att, mevs);
                count += mevs.length;

                // calculate the event rate
                t2 = System.currentTimeMillis();
                time = t2 - t1;

                if (time > 5000) {
                    // reset things if necessary
                    if ( (totalCount >= (Long.MAX_VALUE - count)) ||
                         (totalT >= (Long.MAX_VALUE - time)) )  {
                        totalT = totalCount = count = 0;
                        t1 = System.currentTimeMillis();
                        continue;
                    }
                    rate = 1000.0 * ((double) count) / time;
                    totalCount += count;
                    totalT += time;
                    avgRate = 1000.0 * ((double) totalCount) / totalT;
                    System.out.println("rate = " + String.format("%.3g", rate) +
                                       " Hz,  avg = " + String.format("%.3g", avgRate));
                    count = 0;
                    t1 = System.currentTimeMillis();
                }
            }
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as producer");
            ex.printStackTrace();
        }
    }
    
}
