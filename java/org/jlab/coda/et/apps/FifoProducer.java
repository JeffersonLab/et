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
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class is an example of an event producer for an ET system.
 *
 * @author Carl Timmer
 */
public class FifoProducer {

    public FifoProducer() {
    }


    private static void usage() {
        System.out.println("\nUsage: java Producer -f <et name> -ids <comma-separated source id list>\n" +
                "                      [-h] [-v] [-r] [-m] [-b] [-nd] [-w] [-blast]\n" +
                "                      [-host <ET host>]\n" +
                "                      [-s <event size>] [-c <chunk size>] [-g <group>]\n" +
                "                      [-d <delay>] [-p <ET port>]\n" +
                "                      [-i <interface address>] [-a <mcast addr>]\n" +
                "                      [-rb <buf size>] [-sb <buf size>]\n\n" +

                "       -f     ET system's (memory-mapped file) name\n" +
                "       -ids   comma-separated list of incoming data ids (no white space)\n\n" +

                "       -host  ET system's host if direct connection (default to local)\n" +
                "       -h     help\n" +
                "       -v     verbose output\n" +
                "       -d     delay in millisec between each round of getting and putting events\n\n" +

                "       -p     ET port (TCP for direct, UDP for broad/multicast)\n" +
                "       -r     act as remote (TCP) client even if ET system is local\n" +
                "       -w     write data\n" +
                "       -blast if remote, use external data buf (no mem allocation),\n" +
                "              do not write data (overrides -w)\n\n" +

                "       -i     outgoing network interface address (dot-decimal)\n" +
                "       -a     multicast address(es) (dot-decimal), may use multiple times\n" +
                "       -m     multicast to find ET (use default address if -a unused)\n" +
                "       -b     broadcast to find ET\n\n" +

                "       -rb    TCP receive buffer size (bytes)\n" +
                "       -sb    TCP send    buffer size (bytes)\n" +
                "       -nd    use TCP_NODELAY option\n\n" +

                "        This producer works by making a direct connection to the\n" +
                "        ET system's server port and host unless at least one multicast address\n" +
                "        is specified with -a, the -m option is used, or the -b option is used\n" +
                "        in which case multi/broadcasting used to find the ET system.\n" +
                "        If multi/broadcasting fails, look locally to find the ET system.\n" +
                "        This program gets new events from the ET system as a fifo entry and puts them back.\n\n");
    }


    public static void main(String[] args) {

        int delay=0, size=32, port=0;
        int idCount=0, recvBufSize=0, sendBufSize=0;

        ArrayList<Integer> idList = new ArrayList<>(32);
        int[] ids = null;

        boolean noDelay=false, verbose=false, remote=false, blast=false;
        boolean writeData=false;
        boolean broadcast=false, multicast=false, broadAndMulticast=false;
        HashSet<String> multicastAddrs = new HashSet<String>();
        String outgoingInterface=null, etName=null, host=null;


        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-ids")) {
                String[] idValues = args[++i].split(",");
                for (String strid : idValues) {
                    try {
                        int id = Integer.parseInt(strid);
                        if (id < 0) {
                            System.out.println("Invalid argument to -ids, each id must be >= 0.");
                            usage();
                            return;
                        }
                        idList.add(id);
                    }
                    catch (NumberFormatException ex) {
                        System.out.println("Invalid argument to -ids, did not specify a proper id number.");
                        usage();
                        return;
                    }

                    // Convert list to array
                    idCount = idList.size();
                    ids = new int[idCount];
                    for (int j=0; j < idCount; j++) {
                        ids[j] = idList.get(j);
                    }
                }
            }
            else if (args[i].equalsIgnoreCase("-host")) {
                host = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-a")) {
                try {
                    String addr = args[++i];
                    if (InetAddress.getByName(addr).isMulticastAddress()) {
                        multicastAddrs.add(addr);
                        multicast = true;
                    }
                    else {
                        System.out.println("\nIgnoring improper multicast address\n");
                    }
                }
                catch (UnknownHostException e) {}
            }
            else if (args[i].equalsIgnoreCase("-i")) {
                outgoingInterface = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-nd")) {
                noDelay = true;
            }
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
            }
            else if (args[i].equalsIgnoreCase("-r")) {
                remote = true;
            }
            else if (args[i].equalsIgnoreCase("-m")) {
                multicast = true;
            }
            else if (args[i].equalsIgnoreCase("-b")) {
                broadcast = true;
            }
            else if (args[i].equalsIgnoreCase("-blast")) {
                blast = true;
            }
            else if (args[i].equalsIgnoreCase("-w")) {
                writeData = true;
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
            else if (args[i].equalsIgnoreCase("-rb")) {
                try {
                    recvBufSize = Integer.parseInt(args[++i]);
                    if (recvBufSize < 0) {
                        System.out.println("Receive buf size must be 0 or greater.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper receive buffer size.");
                    usage();
                    return;
                }
            }
            else if (args[i].equalsIgnoreCase("-sb")) {
                try {
                    sendBufSize = Integer.parseInt(args[++i]);
                    if (sendBufSize < 0) {
                        System.out.println("Send buf size must be 0 or greater.");
                        usage();
                        return;
                    }
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper send buffer size.");
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

        if (etName == null || ids == null || ids.length < 1) {
            usage();
            return;
        }


        try {
            EtSystemOpenConfig config = new EtSystemOpenConfig();

            if (broadcast && multicast) {
                broadAndMulticast = true;
            }

            // if multicasting to find ET
            if (multicast) {
                if (multicastAddrs.size() < 1) {
                    // Use default mcast address if not given on command line
                    config.addMulticastAddr(EtConstants.multicastAddr);
                }
                else {
                    // Add multicast addresses to use
                    for (String mcastAddr : multicastAddrs) {
                        config.addMulticastAddr(mcastAddr);
                    }
                }
            }

            if (broadAndMulticast) {
                System.out.println("Broad and Multicasting");
                if (port == 0) {
                    port = EtConstants.udpPort;
                }
                config.setUdpPort(port);
                config.setNetworkContactMethod(EtConstants.broadAndMulticast);
                config.setHost(EtConstants.hostAnywhere);
            }
            else if (multicast) {
                System.out.println("Multicasting");
                if (port == 0) {
                    port = EtConstants.udpPort;
                }
                config.setUdpPort(port);
                config.setNetworkContactMethod(EtConstants.multicast);
                config.setHost(EtConstants.hostAnywhere);
            }
            else if (broadcast) {
                System.out.println("Broadcasting");
                if (port == 0) {
                    port = EtConstants.udpPort;
                }
                config.setUdpPort(port);
                config.setNetworkContactMethod(EtConstants.broadcast);
                config.setHost(EtConstants.hostAnywhere);
            }
            else {
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
            }

            // Defaults are to use operating system default buffer sizes and turn off TCP_NODELAY
            config.setNoDelay(noDelay);
            config.setTcpRecvBufSize(recvBufSize);
            config.setTcpSendBufSize(sendBufSize);
            config.setNetworkInterface(outgoingInterface);
            config.setWaitTime(0);
            config.setEtName(etName);
            config.setResponsePolicy(EtConstants.policyError);

            if (remote) {
                System.out.println("Set as remote");
                config.setConnectRemotely(remote);
            }

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config);
            if (verbose) {
                sys.setDebug(EtConstants.debugInfo);
            }
            sys.open();

            // Make things self-consistent by not taking time to write data if blasting.
            // Blasting flag takes precedence.
            if (blast) {
                writeData = false;
            }

            // Find out if we have a remote connection to the ET system
            // so we know if we can use external data buffer for events
            // for blasting - which is quite a bit faster.
            if (sys.usingJniLibrary()) {
                // Local blasting is just the same as local producing
                blast = false;
                System.out.println("ET is local\n");
            }
            else {
                System.out.println("ET is remote\n");
            }

            //***********************/
            //* Use FIFO interface  */
            //***********************/
            EtFifo fifo = new EtFifo(sys, ids);

            EtFifoEntry entry = new EtFifoEntry(sys, fifo);

            int entryCap = fifo.getEntryCapacity();
            //***********************/


            // Stuff to use the new, garbage-minimizing, non-locking methods
            EtEvent[]   mevs;
            EtEventImpl realEvent;
            int         startingVal = 0;
            long        t1, t2, time, totalT=0L, totalCount=0L, count=0L;
            double      rate, avgRate;
            // must be backed by array or won't work
            ByteBuffer  fakeDataBuf = ByteBuffer.allocate(size);

            // keep track of time for event rate calculations
            t1 = System.currentTimeMillis();

            while (true) {
                // Get array of new events (don't allocate local mem if blasting)
                fifo.newEntry(entry);
                mevs = entry.getBuffers();

                // If blasting data (and remote), don't write anything,
                // just use what's in fixed (fake) buffer.
                if (blast) {
                    for (int i=0; i < idCount; i++) {
                        realEvent = (EtEventImpl) mevs[i];
                        realEvent.setDataBuffer(fakeDataBuf);

                        // set data length
                        mevs[i].setLength(size);
                        mevs[i].setFifoId(ids[i]);
                        mevs[i].hasFifoData(true);
                    }
                }
                // Write data, set control values here
                else if (writeData) {
                    for (int j=0; j < idCount; j++) {
                        // Put integer (j + startingVal) into data buffer
                        mevs[j].getDataBuffer().putInt(j + startingVal);
                        if (verbose) System.out.println("Wrote " + (j + startingVal));

                        // Set data length to be full buf size even though we only wrote 1 int
                        mevs[j].setLength(size);
                        mevs[j].setFifoId(ids[j]);
                        mevs[j].hasFifoData(true);
                    }
                    startingVal += idCount;
                }
                else {
                    for (int i=0; i < idCount; i++) {
                        mevs[i].setLength(size);
                        mevs[i].setFifoId(ids[i]);
                        mevs[i].hasFifoData(true);
                    }
                }

                // put events back into ET system
                fifo.putEntry(entry);

                count += idCount;

                if (delay > 0) {
                    Thread.sleep(delay);
                }

                // calculate the event rate
                t2 = System.currentTimeMillis();
                time = t2 - t1;

                if (time > 5000) {
                    // reset things if necessary
                    if ( (totalCount >= (Long.MAX_VALUE - count)) ||
                         (totalT >= (Long.MAX_VALUE - time)) )  {
                        totalT = totalCount = count = 0L;
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
                    rate    = ((double) count) * size / time;
                    avgRate = ((double) totalCount) * size / totalT;
                    System.out.println("  Data = " + String.format("%.3g", rate) +
                                               " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");

                    count = 0L;
                    t1 = System.currentTimeMillis();
                }
            }
//            fifo.close();
//            sys.close();
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as producer");
            ex.printStackTrace();
        }
    }
    
}
