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
import java.nio.ByteBuffer;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.StringWriter;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.jevio.ByteParser;
import org.jlab.coda.jevio.EvioEvent;
import org.jlab.coda.jevio.EvioFile;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;


/**
 * This class is an example of an event consumer for an ET system.
 *
 * @author Carl Timmer
 */
public class Consumer {

    public Consumer() {
    }


    private static void usage() {
        System.out.println("\nUsage: java Consumer -f <et name> -s <station> [-p <server port>] [-host <host>]\n\n" +
                "       -f    ET system's name\n" +
                "       -s    station name\n" +
                "       -p    port number for a udp broadcast\n" +
                "       -pos  position in station list (GC=0)\n" +
                "       -ppos position in group of parallel staton (-1=end, -2=head)\n" +
                "       -host host the ET system resides on (defaults to anywhere)\n" +
                "       -nb   make station non-blocking\n\n" +
                "        This consumer works by making a direct connection\n" +
                "        to the ET system's tcp server port.\n");
    }


    public static void main(String[] args) {

        int position = 1, pposition = 0, blocking = 1;
        String etName = null, host = null, statName = null;
        int port = Constants.serverPort;

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("-f")) {
                    etName = args[++i];
                }
                else if (args[i].equalsIgnoreCase("-host")) {
                    host = args[++i];
                }
                else if (args[i].equalsIgnoreCase("-nb")) {
                    blocking = 0;
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
                else if (args[i].equalsIgnoreCase("-pos")) {
                    try {
                        position = Integer.parseInt(args[++i]);
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

            if (host == null) {
                host = Constants.hostAnywhere;
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
            else if (statName == null) {
                usage();
                return;
            }

            // make a direct connection to ET system's tcp server
            SystemOpenConfig config = new SystemOpenConfig(etName, host, port);

            // create ET system object with verbose debugging output
            SystemUse sys = new SystemUse(config, Constants.debugInfo);

            // configuration of a new station
            StationConfig statConfig = new StationConfig();
            //statConfig.setFlowMode(Constants.stationParallel);
            //statConfig.setCue(100);
            if (blocking == 0) {
                statConfig.setBlockMode(Constants.stationNonBlocking);
            }

            // create station
            Station stat = sys.createStation(statConfig, statName, position, pposition);

            // attach to new station
            Attachment att = sys.attach(stat);

            // array of events
            Event[] mevs;

            int num, chunk = 1, count = 0;
            long t1, t2, totalT = 0, totalCount = 0;
            double rate, avgRate;

            // initialize
            t1 = System.currentTimeMillis();

            for (int i = 0; i < 50; i++) {
                while (count < 300000L) {
                    // get events from ET system
                    mevs = sys.getEvents(att, Mode.SLEEP, null, 0, chunk);

                    // keep track of time
                    if (count == 0) t1 = System.currentTimeMillis();

                    // example of reading & printing event data
                    if (true) {

                        for (Event mev : mevs) {
                            // get one integer's worth of data
                            ByteBuffer buf = mev.getDataBuffer();
                            buf.limit(mev.getLength());
                            ByteParser parser = new ByteParser();
                            EvioEvent ev = parser.parseEvent(buf);
                            System.out.println("Event = \n"+ev.toXML());

//                            System.out.println("buffer cap = " + buf.capacity() + ", lim = " + buf.limit() +
//                            ", pos = " + buf.position());
//                            num = mev.getDataBuffer().getInt(0);
//                            System.out.println("data byte order = " + mev.getByteOrder());

//                            if (mev.needToSwap()) {
//                                System.out.println("    data swap = " + Integer.reverseBytes(num));
//                            }
//                            else {
//                                System.out.println("    data = " + num);
//                            }

//                            int[] con = mev.getControl();
//                            for (int j : con) {
//                                System.out.print(j + " ");
//                            }
//
//                            System.out.println("pri = " + mev.getPriority());
                        }
                    }

                    // put events back into ET system
                    sys.putEvents(att, mevs);
                    //sys.dumpEvents(att, mevs);
                    count += mevs.length;
                }

                // calculate the event rate
                t2 = System.currentTimeMillis();
                rate = 1000.0 * ((double) count) / (t2 - t1);
                totalCount += count;
                totalT += t2 - t1;
                avgRate = 1000.0 * ((double) totalCount) / totalT;
                System.out.println("rate = " + String.format("%.3g", rate) +
                                   " Hz,   avg = " + String.format("%.3g", avgRate));
                count = 0;
            }
            sys.detach(att);
            sys.removeStation(stat);
            sys.close();
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as consumer");
            ex.printStackTrace();
        }
    }
    
}
