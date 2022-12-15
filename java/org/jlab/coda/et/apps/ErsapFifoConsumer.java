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

import java.nio.ByteBuffer;
import java.util.Random;


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
        String etName=null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
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

            // To get more random behavior, skip delay every now & then
            Random rng = new Random();

            EtSystemOpenConfig config = new EtSystemOpenConfig();
            config.setNetworkContactMethod(EtConstants.direct);
            config.setHost(EtConstants.hostLocal);
            config.setWaitTime(0);
            config.setEtName(etName);

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config);
            if (verbose) {
                sys.setDebug(EtConstants.debugInfo);
            }
            sys.open();
            System.out.println("Connect to local ET");

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

            int    len, bufId;
            long   t1=0L, t2=0L, time, totalT=0L, count=0L, totalCount=0L, bytes=0L, totalBytes=0L;
            double rate, avgRate;

            // keep track of time
            t1 = System.currentTimeMillis();

            while (true) {

                // get events from ET system
                fifo.getEntry(entry);
                mevs = entry.getBuffers();

                idCount = 0;

                // reading event data, iterate thru each event in a single fifo entry (1 in this case)
                for (int i=0; i < entryCap; i++) {
                    // Does this buffer have any data? (Set by producer)
                    if (!mevs[i].hasFifoData()) {
                        // Once we hit a buffer with no data, there is no further data
                        break;
                    }
                    idCount++;

                    // Id associated with this buffer in this fifo entry.
                    // Not useful if only 1 buffer in each fifo entry as is the case here.
                    bufId = mevs[i].getFifoId();

                    // Get event's data buffer
                    ByteBuffer buf = mevs[i].getDataBuffer();
                    // Data length in bytes
                    len = mevs[i].getLength();
                    bytes += len;
                    totalBytes += len;

                    // When using a local C-based ET system with JNI, each ET event has data in
                    // a ByteBuffer. In this case, a MappedByteBuffer is used, so it has
                    // NO BACKING ARRAY!! Access data thru ByteBuffer object only!

                    if (!buf.hasArray()) {
                        // Access data only thru ByteBuffer
                        //System.out.println("    data (len = " + len + ") accessed thru ByteBuffer");
                    }
//                    else {
//                        // Data CANNOT be accessed thru backing array
//                    }
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

                int randomInt = rng.nextInt(100);

                if (delay > 0 && randomInt % 51 == 0) {
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
