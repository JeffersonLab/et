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
import java.nio.ByteBuffer;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;


/**
 * This class is an example of an event consumer for an ET system.
 *
 * @author Carl Timmer
 */
public class Consumer {

    public Consumer() {
    }


    private static void usage() {
        System.out.println("\nUsage: java Consumer -f <et name> -host <ET host> -s <station name> [-h] [-v] [-nb]\n" +
                "                      [-p <ET server port>] [-c <chunk size>] [-q <queue size>]\n" +
                "                      [-pos <station position>] [-ppos <parallel station position>]\n\n" +
                "       -host  ET system's host\n" +
                "       -f     ET system's (memory-mapped file) name\n" +
                "       -s     create station of this name\n" +
                "       -h     help\n" +
                "       -v     verbose output\n" +
                "       -nb    make station non-blocking\n" +
                "       -p     ET server port\n" +
                "       -c     number of events in one get/put array\n" +
                "       -q     queue size if creating nonblocking station\n" +
                "       -pos   position of created station in station list (1,2,...)\n" +
                "       -ppos  position of created station within a group of parallel stations (-1=end, -2=head)\n\n" +
                "        This consumer works by making a direct connection\n" +
                "        to the ET system's tcp server port.\n");
    }


    public static void main(String[] args) {

        int position = 1, pposition = 0, qSize = 0, chunk = 1;
        boolean blocking = true, verbose = false;
        String etName = null, host = null, statName = null;
        int port = EtConstants.serverPort;
        int flowMode = EtConstants.stationSerial;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-host")) {
                host = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-nb")) {
                blocking = false;
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
            }
            else if (args[i].equalsIgnoreCase("-s")) {
                statName = args[++i];
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
            else if (args[i].equalsIgnoreCase("-q")) {
                try {
                    qSize = Integer.parseInt(args[++i]);
                    if (qSize < 1) {
                        System.out.println("Queue size must be > 0.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper queue size number.");
                    usage();
                    return;
                }
            }
            else if (args[i].equalsIgnoreCase("-pos")) {
                try {
                    position = Integer.parseInt(args[++i]);
                    if (position < 1) {
                        System.out.println("Position must be > 0.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper position number.");
                    usage();
                    return;
                }
            }
            else if (args[i].equalsIgnoreCase("-ppos")) {
                try {
                    pposition = Integer.parseInt(args[++i]);
                    if (pposition < -2 || pposition == 0) {
                        System.out.println("Parallel position must be > -3 and != 0.");
                        usage();
                        return;
                    }
                    System.out.println("FLOW moDE is ||");
                    flowMode = EtConstants.stationParallel;
                    if (pposition == -2) pposition = EtConstants.newHead;
                    else if (pposition == -1) pposition = EtConstants.end;

                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper parallel position number.");
                    usage();
                    return;
                }
            }
            else {
                usage();
                return;
            }
        }

        if (host == null || etName == null || statName == null) {
            usage();
            return;
        }

        try {
            // make a direct connection to ET system's tcp server
            EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host, port);

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config, EtConstants.debugInfo);
            sys.open();

            // configuration of a new station
            EtStationConfig statConfig = new EtStationConfig();
            statConfig.setFlowMode(flowMode);
            if (!blocking) {
                statConfig.setBlockMode(EtConstants.stationNonBlocking);
                if (qSize > 0) {
                    statConfig.setCue(qSize);
                }
            }

            // create station
            EtStation stat = sys.createStation(statConfig, statName, position, pposition);

            // attach to new station
            EtAttachment att = sys.attach(stat);

            // array of events
            EtEvent[] mevs;

            int num, count = 0;
            long t1=0, t2=0, time, totalT=0, totalCount=0;
            double rate, avgRate;

            // keep track of time
            t1 = System.currentTimeMillis();

            while (true) {

                // get events from ET system
                mevs = sys.getEvents(att, Mode.SLEEP, null, 0, chunk);

                // example of reading & printing event data
                if (verbose) {
                    for (EtEvent mev : mevs) {
                        // Get event's data buffer
                        // buf.limit() = length of the actual data (not buffer capacity)
                        ByteBuffer buf = mev.getDataBuffer();

                        num = buf.getInt(0);
                        System.out.println("data byte order = " + mev.getByteOrder());
                        if (mev.needToSwap()) {
                            System.out.println("    data needs swapping, swapped int = " + Integer.reverseBytes(num));
                        }
                        else {
                            System.out.println("    data does NOT need swapping, int = " + num);
                        }

                        System.out.print("control array = {");
                        int[] con = mev.getControl();
                        for (int j : con) {
                            System.out.print(j + " ");
                        }
                        System.out.println("}");
                    }
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
            System.out.println("Error using ET system as consumer");
            ex.printStackTrace();
        }
    }
    
}
