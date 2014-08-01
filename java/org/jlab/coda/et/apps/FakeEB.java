/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Jefferson Science Associates,                   *
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

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * This class is an fake event builder for debugging of Hall D CODA crashes.
 *
 * @author Carl Timmer
 * (8/1/2014)
 */
public class FakeEB extends Thread {

    private int chunk = 1;
    private final String[] args;


    public FakeEB(String[] args) {
        this.args = args;
    }


    private static void usage() {
        System.out.println("\nUsage: java FakeEB -etin <et in name> -hostin <ET in host> \n" + "" +
                "                     -etout <et out name> -hostout <ET out host> -file <config file>\n" +
                "                      [-h] [-v] [-pin <ET in port>] [-pout <ET out port>]\n" +
                "                      [-c <chunk size>] [-s <output event size>]\n\n" +
                "       -hostin   input  ET system's host\n" +
                "       -etin     input  ET system's (file) name\n" +
                "       -hostout  output ET system's host\n" +
                "       -etout    output ET system's (file) name\n" +
                "       -file     config file with ROC id #s\n" +
                "       -h        help\n" +
                "       -v        verbose output\n" +
                "       -pin      input  ET TCP server port\n" +
                "       -pout     output ET TCP server port\n" +
                "       -c        number of events in one output get/new/put array (default 1)\n" +
                "       -s        size in bytes of output ET events (default 256KB)\n" +
                "        This consumer works by making a direct connection\n" +
                "        to the ET systems' tcp server port.\n");
    }


    public static void main(String[] args) {
        FakeEB eb = new FakeEB(args);
        eb.start();
    }


    public void run() {

        int verbose = EtConstants.debugError;
        String etInName = null, hostIn = null, configFileName = null;
        String etOutName = null, hostOut = null;
        int rocCount = 0, outputEventSize = 256000;
        int portIn = EtConstants.serverPort, portOut = EtConstants.serverPort;
        ArrayList<Integer> codaIds = new ArrayList<Integer>(128);
        ArrayList<EtAttachment> attachmentsIn = new ArrayList<EtAttachment>(128);
        ArrayList<BlockingQueue<EtEvent>> queues = new ArrayList<BlockingQueue<EtEvent>>(128);

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-etin")) {
                etInName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-hostin")) {
                hostIn = args[++i];
            }
            if (args[i].equalsIgnoreCase("-etout")) {
                etOutName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-hostout")) {
                hostOut = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-file")) {
                configFileName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = EtConstants.debugInfo;
            }
            else if (args[i].equalsIgnoreCase("-pin")) {
                try {
                    portIn = Integer.parseInt(args[++i]);
                    if ((portIn < 1024) || (portIn > 65535)) {
                        System.out.println("Port number must be between 1024 & 65535.");
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
            else if (args[i].equalsIgnoreCase("-pout")) {
                try {
                    portOut = Integer.parseInt(args[++i]);
                    if ((portOut < 1024) || (portOut > 65535)) {
                        System.out.println("Output port # must be between 1024 & 65535.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper output port #.");
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
                    outputEventSize = Integer.parseInt(args[++i]);
                    if (outputEventSize < 1024) {
                        System.out.println("Output event size must be >= 1024.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper output event size size.");
                    usage();
                    return;
                }
            }
            else {
                usage();
                return;
            }
        }

        if (hostIn == null || etInName == null || configFileName == null) {
            usage();
            return;
        }

        // Read config file for the Rocs' coda id numbers, put in list
        try {
            String s;
            InputStream ins = new FileInputStream(configFileName);
            Reader reader = new InputStreamReader(ins, "US-ASCII");
            BufferedReader br = new BufferedReader(reader);
            while ((s = br.readLine()) != null) {
                int id = Integer.parseInt(s);
                codaIds.add(id);
            }
            rocCount = codaIds.size();
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }


        try {
            //---------------------------------
            // INPUT ET
            //---------------------------------
            // Make a direct connection to ET system's tcp server
            EtSystemOpenConfig configIn = new EtSystemOpenConfig(etInName, hostIn, portIn);

            // Create ET system object with verbose debugging output
            EtSystem sysIn = new EtSystem(configIn, verbose);
            sysIn.open();

            CountDownLatch latch = new CountDownLatch(codaIds.size());

            // Create one station for each ROC
            for (int id : codaIds) {
                // Configuration of station
                EtStationConfig statConfig = new EtStationConfig();

                // Create filter for station so only events from a particular ROC
                // (id as defined in config file) make it in.
                // Station filter is the built-in selection function.
                int[] selects = new int[EtConstants.stationSelectInts];
                Arrays.fill(selects, -1);
                selects[0] = id;
                statConfig.setSelect(selects);
                statConfig.setSelectMode(EtConstants.stationSelectMatch);

                // Create station
                EtStation station = sysIn.createStation(statConfig, "stat_"+id);

                // Attach to new station & store in list
                EtAttachment attIn = sysIn.attach(station);
                attachmentsIn.add(attIn);

                // Create a queue for each ROC to hold incoming et events
                BlockingQueue<EtEvent> queue = new LinkedBlockingQueue<EtEvent>(100);
                queues.add(queue);

                // Start a thread to fill the Q
                InputQFiller qThread = new InputQFiller(sysIn, attIn, queue, latch);
                qThread.start();
            }

            // Wait 'til all input threads have started up
            latch.await();

            //---------------------------------
            // OUTPUT ET
            //---------------------------------
            // Make a direct connection to output ET system's tcp server
            EtSystemOpenConfig configOut = new EtSystemOpenConfig(etOutName, hostOut, portOut);

            // Create ET system object with verbose debugging output
            EtSystem sysOut = new EtSystem(configOut, verbose);
            sysOut.open();

            // Get GRAND_CENTRAL station object
            EtStation gc = sysOut.stationNameToObject("GRAND_CENTRAL");

            // Attach to GRAND_CENTRAL
            EtAttachment attOut = sysOut.attach(gc);


            // array of events
            EtEvent[] mevs;
            EtEvent[] buildEvents = new EtEvent[rocCount];

            int  totalOutputSize, totalOutputLength, len, count = 0;
            long t1=0, t2=0, time, totalT=0, totalCount=0;
            double rate, avgRate;

            // keep track of time
            t1 = System.currentTimeMillis();

            while (true) {

                // Get array of new events to fit all data added together
                mevs = sysOut.newEvents(attOut, Mode.SLEEP, false, 0, chunk, outputEventSize, 1);

                // For each OUTPUT ET event ...
                for (EtEvent mev : mevs) {

                    // Start building here. First, grab one event from each Q
                    totalOutputLength = totalOutputSize = 0;
                    for (int i=0; i < rocCount; i++) {
                        buildEvents[i] = queues.get(i).take();
                        // Find total size of incoming data from all ROCs
                        totalOutputSize += buildEvents[i].getLength();
                    }

                    // Check to see if there's enough room in ET event for all data
                    if (totalOutputSize > outputEventSize) {
                        System.out.println("Total data per built event is too big to fit in output ET");
                        System.exit(-1);
                    }

                    // Get output event's data buffer
                    ByteBuffer buf = mev.getDataBuffer();

                    // Copy in data
                    for (int i=0; i < rocCount; i++) {
                        len = buildEvents[i].getLength();
                        buf.put(buildEvents[i].getData(), 0, len);
                        totalOutputLength += len;
                    }
                    mev.setLength(totalOutputLength);
                }

                // put events back into ET system
                sysIn.putEvents(attOut, mevs);
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
            System.out.println("Error using ET system");
            ex.printStackTrace();
        }
    }



    /** One of these threads for each ROC to take its data and place on Q for build thread. */
    class InputQFiller extends Thread {

        EtSystem sys;
        EtAttachment att;
        CountDownLatch latch;
        BlockingQueue<EtEvent> queue;

        public InputQFiller(EtSystem sys, EtAttachment att,
                            BlockingQueue<EtEvent> queue, CountDownLatch latch) {
            this.sys = sys;
            this.att = att;
            this.queue = queue;
            this.latch = latch;
        }


        public void run() {

            latch.countDown();
            EtEvent[] mevs;

            try {
                while (true) {
                    // Get events from ROC in chunks
                    mevs = sys.getEvents(att, Mode.SLEEP, null, 0, chunk);
                    for (EtEvent mev : mevs) {
                        // Place in Q
                        queue.put(mev);
                    }
                }
            }
            catch (Exception ex) {
                System.out.println("Error using ET system as consumer");
                ex.printStackTrace();
            }
        }

    }

}
