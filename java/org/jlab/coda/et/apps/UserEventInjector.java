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


import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

/**
 * This class is used to inject either a beginning-of-run or user event into an ET system.
 * @author Carl Timmer
 */
public class UserEventInjector {


    static EtSystem sys;
    static EtStation gc;
    static EtAttachment att;
    static EtSystemOpenConfig config;
    static boolean verbose = true;


    public UserEventInjector() {
    }


    private static void usage() {
        System.out.println("\nUsage: java UserEventInjector -f <et name>\n" +
                "                      [-h] [-v] [-n] [-r] [-m] [-b] [-nd] [-dis]\n" +
                "                      [-host <ET host>] [-w <big endian? 0/1>]\n" +
                "                      [-g <group>]\n" +
                "                      [-d <delay>] [-p <ET port>]\n" +
                "                      [-i <interface address>] [-a <mcast addr>]\n" +
                "                      [-rb <buf size>] [-sb <buf size>]\n\n" +

                "       -f     ET system's (memory-mapped file) name\n" +
                "       -host  ET system's host if direct connection (default to local)\n" +
                "       -h     help\n" +
                "       -v     verbose output\n" +
                "       -n     use new, non-garbage-generating new,get,put,dump methods\n\n" +

                "       -g     group from which to get new events (1,2,...)\n" +
                "       -d     delay in millisec between each round of getting and putting events\n" +
                "       -dis   disconnect after each event insertion then reconnect for the next insertion\n" +
                "       -bor   inserted event is \"first event\" or beginning-of-run instead of user\n\n" +

                "       -p     ET port (TCP for direct, UDP for broad/multicast)\n" +
                "       -r     act as remote (TCP) client even if ET system is local\n" +
                "       -w     write data (1 sequential int per event), 1 = Java (big) endian, 0 else\n\n" +

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
                "        This program gets new events from the system and puts them back.\n\n");
    }


    static boolean connectToEt() {
        try {
            // create ET system object with verbose debugging output
            sys = new EtSystem(config);
            if (verbose) {
                sys.setDebug(EtConstants.debugInfo);
            }
            sys.open();

            // get GRAND_CENTRAL station object
            gc = sys.stationNameToObject("GRAND_CENTRAL");

            // attach to GRAND_CENTRAL
            att = sys.attach(gc);

        } catch (Exception e) {
            return false;
        }
        return true;
    }


    static void disconnectFromEt() {
        try {
            sys.detach(att);
            sys.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {

        int group=1, delay=0, size=2000, port=0;
        int chunk=1, recvBufSize=0, sendBufSize=0;
        boolean noDelay=false, newIF=false, remote=false;
        boolean userEvent = true;
        boolean disconnect=false;
        boolean disconnected=false;
        boolean bigEndian=true;
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
            else if (args[i].equalsIgnoreCase("-n")) {
System.out.println("Using NEW interface");
                newIF = true;
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
            else if (args[i].equalsIgnoreCase("-dis")) {
                disconnect = true;
            }
            else if (args[i].equalsIgnoreCase("-bor")) {
                userEvent = false;
            }
            else if (args[i].equalsIgnoreCase("-w")) {
                try {
                    int isBigEndian = Integer.parseInt(args[++i]);
                    bigEndian = isBigEndian != 0;
                }
                catch (NumberFormatException ex) {
                    System.out.println("Did not specify a proper endian value (1=big, else 0).");
                    usage();
                    return;
                }
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

        if (etName == null) {
            usage();
            return;
        }

        //-----------------------------------------------------------------
        // Create a buffer with evio data containing a "first event" or BOR
        //-----------------------------------------------------------------
        ByteBuffer firstBuffer = ByteBuffer.allocate(14*4);
        // Block header
        firstBuffer.putInt(14);
        firstBuffer.putInt(1);
        firstBuffer.putInt(8);
        firstBuffer.putInt(1);
        firstBuffer.putInt(0);
                    // version | is last block | user type | is first event
        firstBuffer.putInt(0x4 | (0x1 << 9) | (0x4 << 10) | (0x1 << 14));
        firstBuffer.putInt(0);
        firstBuffer.putInt(0xc0da0100);
        // Bank header
        firstBuffer.putInt(5);
                      // num=1 | unsigned int | tag
        firstBuffer.putInt(0x1 | (0x1 << 8) | (0x2 << 16));
        // Bank data
        firstBuffer.putInt(1);
        firstBuffer.putInt(2);
        firstBuffer.putInt(3);
        firstBuffer.putInt(4);

        firstBuffer.flip();

        //-----------------------------------------------------------------
        // Create a buffer with evio data but is NOT a "first event" or BOR
        //-----------------------------------------------------------------
        ByteBuffer userBuffer = ByteBuffer.allocate(14*4);
        // Block header
        userBuffer.putInt(14);
        userBuffer.putInt(1);
        userBuffer.putInt(8);
        userBuffer.putInt(1);
        userBuffer.putInt(0);
                   // version | is last block | user type
        userBuffer.putInt(0x4 | (0x1 << 9) | (0x4 << 10));
        userBuffer.putInt(0);
        userBuffer.putInt(0xc0da0100);
        // Bank header
        userBuffer.putInt(5);
                     // num=3 | unsigned int | tag
        userBuffer.putInt(0x3 | (0x1 << 8) | (0x4 << 16));
        // Bank data
        userBuffer.putInt(5);
        userBuffer.putInt(6);
        userBuffer.putInt(7);
        userBuffer.putInt(8);

        userBuffer.flip();


        try {
            config = new EtSystemOpenConfig();

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

            connectToEt();
            disconnected = false;

            // Find out if we have a remote connection to the ET system
            // so we know if we can use external data buffer for events
            // for blasting - which is quite a bit faster.
            if (sys.usingJniLibrary()) {
                System.out.println("ET is local\n");
            }
            else {
                System.out.println("ET is remote\n");
            }

            // Stuff to use the new, garbage-minimizing, non-locking methods
            int validEvents;
            EtContainer container = null;
            if (newIF) {
                container = new EtContainer(chunk, (int)sys.getEventSize());
            }

            EtEvent[]   events;
            long        t1, t2, time, totalT=0L, totalCount=0L, count=0L;
            double      rate, avgRate;

            // keep track of time for event rate calculations
            t1 = System.currentTimeMillis();

            while (true) {

                if (disconnect && disconnected) {
                    connectToEt();
                    disconnected = false;
                }

                // Get array of new events (don't allocate local mem if blasting)
                if (newIF) {
                    container.newEvents(att, Mode.SLEEP, 0, 1, size, group);
                    sys.newEvents(container);
                    count += validEvents = container.getEventCount();
                    events = container.getEventArray();
                }
                else {
                    events = sys.newEvents(att, Mode.SLEEP, false, 0, 1, size, group);
                    count += validEvents = events.length;
                }

                // Write data, set control values here
                for (int j=0; j < validEvents; j++) {
                    ByteBuffer buf = events[j].getDataBuffer();
                    if (userEvent) {
                        buf.put(userBuffer);
                        userBuffer.flip();
                    }
                    else {
                        buf.put(firstBuffer);
                        firstBuffer.flip();
                    }
                    events[j].setByteOrder(ByteOrder.BIG_ENDIAN);
                    events[j].setLength(14*4);
                }

                if (delay > 0) Thread.sleep(delay);

                // put events back into ET system
                if (newIF) {
                    container.putEvents(att, 0, validEvents);
                    sys.putEvents(container);
                }
                else {
                    sys.putEvents(att, events);
                }

                if (disconnect) {
                    disconnectFromEt();
                    disconnected = true;
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
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as producer");
            ex.printStackTrace();
        }
    }
    
}
