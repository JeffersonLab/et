//
// Copyright 2022, Jefferson Science Associates, LLC.
// Subject to the terms in the LICENSE file found in the top-level directory.
//
// EPSCI Group
// Thomas Jefferson National Accelerator Facility
// 12000, Jefferson Ave, Newport News, VA 23606
// (757)-269-7100


package org.jlab.coda.et.apps;

import org.jlab.coda.et.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashSet;


/**
 * This class is an example of a fifo-based event consumer for an ET system
 * that simulates an ERSAP backend.
 *
 * @author Carl Timmer
 */
public class ErsapFifoConsumer {

    public ErsapFifoConsumer() {}


    private static void usage() {
        System.out.println("\nUsage: java ErsapFifoConsumer -f <et name> [-h] [-v] [-d]\n\n" +
                "       -f     ET system's (memory-mapped file) name\n" +
                "       -d     delay in microseconds to delay between fifo reads\n" +
                "       -v     verbose output (also prints data if reading with -read)\n" +
                "       -h     help\n\n" +
                "        This consumer makes a local, shared memory connection to the\n" +
                "        ET system. It gets events with the fifo API and puts them back.\n" +
                "        It simulates an ERSAP backend.\n");
    }


    public static void main(String[] args) {

        int port=0, delay=0;
        boolean verbose=false;
        String etName=null, host=null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-host")) {
                host = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
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
            else if (args[i].equalsIgnoreCase("-d")) {
                try {
                    delay = Integer.parseInt(args[++i]);
                    if (delay < 0) {
                        System.out.println("Delay must be >= 0.");
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

        if (etName == null) {
            usage();
            return;
        }

        try {
            EtSystemOpenConfig config = new EtSystemOpenConfig();

            if (port == 0) {
                port = EtConstants.serverPort;
            }
            config.setTcpPort(port);
            config.setNetworkContactMethod(EtConstants.direct);
            if (host == null) {
                host = EtConstants.hostLocal;
            }
            config.setHost(host);
            System.out.println("Direct connection to " + host);

            // Defaults are to use operating system default buffer sizes and turn off TCP_NODELAY
            config.setWaitTime(0);
            config.setEtName(etName);

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config);
            if (verbose) {
                sys.setDebug(EtConstants.debugInfo);
            }
            sys.open();
            System.out.println("Connect to ET using local IP addr = " + sys.getLocalAddress());

            //***********************/
            //* Use FIFO interface  */
            //***********************/
            EtFifo fifo = new EtFifo(sys);

            EtFifoEntry entry = new EtFifoEntry(sys, fifo);

            int entryCap = fifo.getEntryCapacity();
            int idCount;
            //***********************/

            // array of events
            EtEvent[] mevs;

            int    len, num, bufId;
            long   t1=0L, t2=0L, time, totalT=0L, count=0L, totalCount=0L, bytes=0L, totalBytes=0L;
            double rate, avgRate;

            // keep track of time
            t1 = System.currentTimeMillis();

            while (true) {

                // get events from ET system
                fifo.getEntry(entry);
                mevs = entry.getBuffers();

                idCount = 0;

                // example of reading & printing event data
                for (int i=0; i < entryCap; i++) {
                    // Does this buffer have any data? (Set by producer)
                    if (!mevs[i].hasFifoData()) {
                        // Once we hit a buffer with no data, there is no further data
                        break;
                    }
                    idCount++;

                    // Id associated with this buffer in this fifo entry
                    bufId = mevs[i].getFifoId();

                    // Get event's data buffer
                    ByteBuffer buf = mevs[i].getDataBuffer();
                    num = buf.getInt(0);
                    len = mevs[i].getLength();
                    bytes += len;
                    totalBytes += len;

                    if (verbose) {
                        System.out.println("    data (len = " + mevs[i].getLength() + ") = " + num);

                        if (buf.hasArray()) {
                            // If using byte array you need to watch out for endianness
                            byte[] data = mevs[i].getData();
                            System.out.println("data byte order = " + mevs[i].getByteOrder());
                            if (mevs[i].needToSwap()) {
                                System.out.println("    data (len = " + len + ") needs swapping");
                            }
                            else {
                                System.out.println("    data (len = " + len + ") does NOT need swapping");
                            }
                        }
                        else {
                            // Access data thru ByteBuffer and not underlying array
                            System.out.println("    data (len = " + len + ") accessed thru ByteBuffer");
                        }
                    }
                }

                // put events back into ET system
                fifo.putEntry(entry);

                count += idCount;

                // calculate the event rate
                t2 = System.currentTimeMillis();
                time = t2 - t1;

                if (time > 5000) {
                    // reset things if necessary
                    if ( (totalCount >= (Long.MAX_VALUE - count)) ||
                            (totalT >= (Long.MAX_VALUE - time)) )  {
                        bytes = totalBytes = totalT = totalCount = count = 0L;
                        t1 = t2;
                        continue;
                    }

                    rate = 1000.0 * ((double) count) / time;
                    totalCount += count;
                    totalT += time;
                    avgRate = 1000.0 * ((double) totalCount) / totalT;
                    // Event rates
                    System.out.println("Events = " + String.format("%.3g", rate) +
                            " Hz,    avg = " + String.format("%.3g", avgRate));

                    // Data rates
                    rate    = ((double) bytes) / time;
                    avgRate = ((double) totalBytes) / totalT;
                    System.out.println("  Data = " + String.format("%.3g", rate) +
                            " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");

                    bytes = count = 0L;
                    t1 = System.currentTimeMillis();
                }

                if (delay > 0) {
                    Thread.sleep(delay);
                }
            }
//            fifo.close();
//            sys.close();
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as consumer");
            ex.printStackTrace();
        }
    }
}
