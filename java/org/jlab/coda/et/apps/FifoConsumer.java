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
 * This class is an example of an event consumer for an ET system.
 *
 * @author Carl Timmer
 */
public class FifoConsumer {

    public FifoConsumer() {}


    private static void usage() {
        System.out.println("\nUsage: java Consumer -f <et name>\n" +
                "                      [-h] [-v] [-r] [-m] [-b] [-nd] [-read]\n" +
                "                      [-host <ET host>] [-p <ET port>] [-d <delay ms>]\n" +
                "                      [-i <interface address>] [-a <mcast addr>]\n" +
                "                      [-rb <buf size>] [-sb <buf size>]\n\n" +

                "       -f     ET system's (memory-mapped file) name\n" +
                "       -host  ET system's host if direct connection (default to local)\n" +
                "       -h     help\n\n" +

                "       -v     verbose output (also prints data if reading with -read)\n" +
                "       -read  read data (1 int for each event)\n" +
                "       -r     act as remote (TCP) client even if ET system is local\n" +
                "       -p     port, TCP if direct, else UDP\n\n" +

                "       -d     delay between fifo gets in milliseconds\n" +

                "       -i     outgoing network interface address (dot-decimal)\n" +
                "       -a     multicast address(es) (dot-decimal), may use multiple times\n" +
                "       -m     multicast to find ET (use default address if -a unused)\n" +
                "       -b     broadcast to find ET\n\n" +

                "       -rb    TCP receive buffer size (bytes)\n" +
                "       -sb    TCP send    buffer size (bytes)\n" +
                "       -nd    use TCP_NODELAY option\n\n" +

                "        This consumer works by making a direct connection to the\n" +
                "        ET system's server port and host unless at least one multicast address\n" +
                "        is specified with -a, the -m option is used, or the -b option is used\n" +
                "        in which case multi/broadcasting used to find the ET system.\n" +
                "        If multi/broadcasting fails, look locally to find the ET system.\n" +
                "        This program gets events from the ET system as a fifo and puts them back.\n\n");
    }


    public static void main(String[] args) {

        int port=0, delay=0;
        int recvBufSize=0, sendBufSize=0;
        boolean readData=false, noDelay=false;
        boolean verbose=false, remote=false;
        boolean broadcast=false, multicast=false, broadAndMulticast=false;
        HashSet<String> multicastAddrs = new HashSet<String>();
        String outgoingInterface=null, etName=null, host=null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
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
                        System.out.println("\nignoring improper multicast address\n");
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
            else if (args[i].equalsIgnoreCase("-read")) {
                readData = true;
            }
            else if (args[i].equalsIgnoreCase("-m")) {
                multicast = true;
            }
            else if (args[i].equalsIgnoreCase("-b")) {
                broadcast = true;
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
                if (readData) {
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
                                int idata = EtUtils.bytesToInt(data,0);
                                System.out.println("data byte order = " + mevs[i].getByteOrder());
                                if (mevs[i].needToSwap()) {
                                    System.out.println("    data (len = " +len +
                                                               ") needs swapping, swapped int = " + Integer.reverseBytes(idata));
                                }
                                else {
                                    System.out.println("    data (len = " + len +
                                                               ")does NOT need swapping, int = " + idata);
                                }
                            }

                            System.out.print("control array = {");
                            int[] con = mevs[i].getControl();
                            for (int j : con) {
                                System.out.print(j + " ");
                            }
                            System.out.println("}");
                        }
                    }
                }
                else {
                    for (int i=0; i < entryCap; i++) {
                        if (!mevs[i].hasFifoData()) {
                            break;
                        }
                        idCount++;
                        len = mevs[i].getLength();
                        bytes += len;       // +52 for overhead
                        totalBytes += len;  // +52 for overhead
                    }
                    // For more overhead
                    // bytes += 20;
                    // totalBytes += 20;
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
