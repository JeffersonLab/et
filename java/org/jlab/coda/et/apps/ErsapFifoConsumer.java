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

        int delay=0;
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
            EtSystemOpenConfig config = new EtSystemOpenConfig();
            config.setHost(EtConstants.hostLocal);
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
            if (entryCap != 1) {
                // ERROR
            }
            //***********************/

            // array of events
            EtEvent[] mevs;
            int    idCount=0, len, num, bufId;
            long   bytes=0L, totalBytes=0L;

            while (true) {

                // get events from ET system.
                fifo.getEntry(entry);
                mevs = entry.getBuffers();
                // only 1 event per fifo entry since we setup the ET system that way
                EtEvent ev = mevs[0];

                // Does this buffer have any data? (Set by producer)
                // This should never happen in this case.
                if (!ev.hasFifoData()) {
                    // Once we hit a buffer with no data, there is no further data
                    fifo.putEntry(entry);
                    continue;
                }
                idCount++;

                // Id associated with this buffer in this fifo entry
                bufId = ev.getFifoId();

                // Get event's data buffer
                ByteBuffer buf = ev.getDataBuffer();
                len = ev.getLength();
                bytes += len;
                totalBytes += len;

                if (verbose) System.out.println("    data (len = " + ev.getLength() + ") = ");

                if (buf.hasArray()) {
                    // If using byte array you need to watch out for endianness
                    byte[] data = ev.getData();
                    System.out.println("data byte order = " + ev.getByteOrder());
                    if (ev.needToSwap()) {
                        System.out.println("    data (len = " + len + ") needs swapping");
                    }
                    else {
                        System.out.println("    data (len = " + len + ")does NOT need swapping");
                    }
                }
                else {
                    // Access data thru buf object - not underlying array
                    int i = buf.getInt();
                }

                if (delay > 0) {
                    Thread.sleep(delay);
                }

                // put event back into ET system
                fifo.putEntry(entry);
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
