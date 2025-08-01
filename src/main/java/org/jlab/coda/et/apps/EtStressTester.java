/*----------------------------------------------------------------------------*
 *  Copyright (c) 2021        Southeastern Universities Research Association, *
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
import org.jlab.coda.et.enums.Modify;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

/**
 * This class is used to stress test an ET system by having multiple threads which
 * inject events remotely into an ET system (opening and closing for each insertion)
 * and a single thread which acts as an ER
 * would by opening, attaching, getting, detaching and closing an ET system in a cycle.
 *
 * @author Carl Timmer
 */
public class EtStressTester {

    static EtSystemOpenConfig openConfig;
    static EtSystemOpenConfig directConfig;

    static boolean verbose = false;
    static boolean userEvent = true;
    static boolean disconnect = false;
    static boolean remote = false;

    // millisec
    static int delay;
    static int consumerDelay;

//            Runtime.getRuntime().addShutdownHook(shutdownThread);
//
//    /** Thread used to kill ET and remove file if JVM exited by control-C. */
//    private ControlCThread shutdownThread = new ControlCThread();
//
//    /** Class describing thread to be used for killing ET
//     *  and removing file if JVM exited by control-C. */
//    static private class ControlCThread extends Thread {
//        EtSystem etSys;
//        String etFileName;
//
//        ControlCThread() {}
//
//        ControlCThread(EtSystem etSys,String etFileName) {
//            this.etSys = etSys;
//            this.etFileName = etFileName;
//        }
//
//        void reset(EtSystem etSys,String etFileName) {
//            this.etSys = etSys;
//            this.etFileName = etFileName;
//        }
//
//        public void run() {
//
////System.out.println("    DataTransport Et: HEY, I'm running control-C handling thread!\n");
//            // Try killing ET system (should also delete file)
//            if (etSys != null && etSys.alive()) {
//                try {
////System.out.println("    DataTransport Et: try killing ET");
//                    etSys.kill();
//                }
//                catch (Exception e) {}
//            }
//
//            // Try deleting any ET system file
//            if (etFileName != null) {
//                try {
//                    File etFile = new File(etFileName);
//                    if (etFile.exists() && etFile.isFile()) {
////System.out.println("    DataTransport Et: try deleting file");
//                        etFile.delete();
//                    }
//                }
//                catch (Exception e) {}
//            }
//        }
//    }



    static class Injector extends Thread {

        private EtSystem sys;
        private EtAttachment att;
        private int id;


        Injector(int i) {id = i;}


        /**
         * Connect to ET system.
         * @return true if connection made, else false.
         */
        boolean connectToEt() {
            try {
                // create ET system object with verbose debugging output
                sys = new EtSystem(openConfig);
                if (verbose) {
                    sys.setDebug(EtConstants.debugInfo);
                }
                sys.open();

                // get GRAND_CENTRAL station object
                EtStation gc = sys.stationNameToObject("GRAND_CENTRAL");

                // attach to GRAND_CENTRAL
                att = sys.attach(gc);

            } catch (Exception e) {
                return false;
            }
            return true;
        }


        /** Disconnect from ET system. */
        void disconnectFromEt() {
            try {
                sys.detach(att);
                sys.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        /** Start this thread. */
        public void run() {

            int group=1, size=56;
            boolean connected;

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

  System.out.println("event injector thread " + id + " about to CONNECT");
                connected = connectToEt();

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

                EtEvent[]   events;
                long        t1, t2, time, totalT=0L, totalCount=0L, count=0L;
                double      rate, avgRate;

                // keep track of time for event rate calculations
                t1 = System.currentTimeMillis();

                while (true) {

                    if (disconnect && !connected) {
                        connectToEt();
                        connected = true;
                    }

                    // Get array of new events (don't allocate local mem if blasting)
                    events = sys.newEvents(att, Mode.SLEEP, false, 0, 1, size, group);
                    count += validEvents = events.length;

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

                    if (delay > 0) Thread.sleep(delay + (100*id));

                    // put events back into ET system
                    sys.putEvents(att, events);

                    System.out.println(id);

                    if (disconnect) {
                        disconnectFromEt();
                        connected = false;
                    }

//                    // calculate the event rate
//                    t2 = System.currentTimeMillis();
//                    time = t2 - t1;
//
//                    if (time > 5000) {
//                        // reset things if necessary
//                        if ( (totalCount >= (Long.MAX_VALUE - count)) ||
//                                (totalT >= (Long.MAX_VALUE - time)) )  {
//                            totalT = totalCount = count = 0L;
//                            t1 = t2;
//                            continue;
//                        }
//
//                        rate = 1000.0 * ((double) count) / time;
//                        totalCount += count;
//                        totalT += time;
//                        avgRate = 1000.0 * ((double) totalCount) / totalT;
//                        // Event rates
//                        System.out.println("Events = " + String.format("%.3g", rate) +
//                                " Hz,    avg = " + String.format("%.3g", avgRate));
//
//                        // Data rates
//                        rate    = ((double) count) * size / time;
//                        avgRate = ((double) totalCount) * size / totalT;
//                        System.out.println("  Data = " + String.format("%.3g", rate) +
//                                " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");
//
//                        count = 0L;
//                        t1 = System.currentTimeMillis();
//                    }

                }
            }
            catch (Exception ex) {
                System.out.println("Error using ET system as producer");
                ex.printStackTrace();
            }
        }
    }


    /**
     * Thread to play the role the EventRecorder does.
     * In a cycle, it opens, gets events, puts events and closes.
     */
    static class EventConsumer extends Thread {

        private EtSystem sys;
        private EtStation myStation;
        private EtAttachment att;
        private EtStationConfig statConfig;



//        /**
//         * This class is a thread designed to get ET events from the ET system.
//         * It runs simultaneously with the thread that parses these events
//         * with evio data and the thread that puts them back.
//         */
//        final private class EvGetter extends Thread {
//
//            /** Constructor. */
//            EvGetter (ThreadGroup group, String name) {
//                super(group, name);
//            }
//
//            /**
//             * {@inheritDoc}<p>
//             * Get the ET events.
//             */
//            public void run() {
//
//                long sequence;
//                boolean gotError;
//                String errorString;
//                EtContainer etContainer;
//                boolean useDirectEt = (etSysLocal != null);
//
//                // Tell the world I've started
//                latch.countDown();
//
//                try {
//
//                    while (true) {
//                        if (stopGetterThread) {
//                            return;
//                        }
//
//                        // Will block here if no available slots in ring.
//                        // It will unblock when ET events are put back by the other thread.
//                        sequence = rb.nextIntr(1); // This just spins on parkNanos
//                        etContainer = rb.get(sequence);
//
//                        if (useDirectEt) {
//                            while (true) {
//                                try {
//                                    // We need to do a timed WAIT since getEvents is NOT interruptible
//                                    etContainer.getEvents(attachmentLocal, Mode.TIMED.getValue(), 500, chunk);
//                                    etSysLocal.getEvents(etContainer);
//                                }
//                                catch (EtTimeoutException e) {}
//                                if (stopGetterThread) {
//                                    return;
//                                }
//                            }
//                        }
//                        else {
//                            // Now that we have a free container, get events & store them in container
//                            etContainer.getEvents(attachment, Mode.SLEEP, Modify.NOTHING, 0, chunk);
//                            etSystem.getEvents(etContainer);
//                        }
//
//                        // Set local reference to null for error recovery
//                        etContainer = null;
//
//                        // Make container available for parsing/putting thread
//                        rb.publish(sequence++);
//                    }
//                }
//                catch (EtWakeUpException e) {
//                    // Told to wake up because we're ending or resetting
//                    if (haveInputEndEvent) {
//                        logger.info("      DataChannel Et in: " + name + ", Getter thd woken up, got END event");
//                    }
//                    else if (gotResetCmd) {
//                        logger.info("      DataChannel Et in: " + name + ", Getter thd woken up, got RESET cmd");
//                    }
//                    else {
//                        logger.info("      DataChannel Et in: " + name + ", Getter thd woken up");
//                    }
//                    return;
//                }
//                catch (IOException e) {
//                    gotError = true;
//                    errorString = "DataChannel Et in: network communication error with Et";
//                }
//                catch (EtException e) {
//                    gotError = true;
//                    errorString = "DataChannel Et in: internal error handling Et";
//                }
//                catch (EtDeadException e) {
//                    gotError = true;
//                    errorString = "DataChannel Et in: Et system dead";
//                }
//                catch (EtClosedException e) {
//                    gotError = true;
//                    errorString = "DataChannel Et in: Et connection closed";
//                }
//                catch (Exception e) {
//                    gotError = true;
//                    errorString = "DataChannel Et in: " + e.getMessage();
//                }
//
//                // ET system problem - run will come to an end
//                if (gotError) {
//                    channelState = CODAState.ERROR;
//                    System.out.println("      DataChannel Et in: " + name + ", " + errorString);
//                    emu.setErrorState(errorString);
//                }
//                System.out.println("      DataChannel Et in: GETTER is Quitting");
//            }
//        }


        EventConsumer() {
            // configuration of a new station
            statConfig = new EtStationConfig();
        }


        /**
         * Connect to ET system.
         * @return true if connection made, else false.
         */
        boolean connectToEt() {
            try {
                // create ET system object with verbose debugging output
                sys = new EtSystem(directConfig);
                if (verbose) {
                    sys.setDebug(EtConstants.debugInfo);
                }
System.out.println("\n    Event Consumer OPEN ET system");
                sys.open();

                // create station
System.out.println("    Event Consumer create myStation");
                myStation = sys.createStation(statConfig, "MyStation");

                // attach to GRAND_CENTRAL
System.out.println("    Event Consumer attach to myStation");
                att = sys.attach(myStation);

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
System.out.println("    Event Consumer DONE connecting\n");
            return true;
        }


        /** Disconnect from ET system. */
        void disconnectFromEt() {
            try {
System.out.println("\n    Event Consumer detach");
                sys.detach(att);
System.out.println("    Event Consumer remove myStation");
                sys.removeStation(myStation);
System.out.println("    Event Consumer close");
                sys.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
System.out.println("    Event Consumer DONE disconnecting\n");
        }


        /** Start this thread. */
        public void run() {

            int size=56;
            boolean connected;

            try {
                System.out.println("Event consumer about to CONNECT");

                connected = connectToEt();

                // Find out if we have a remote connection to the ET system
                // so we know if we can use external data buffer for events
                // for blasting - which is quite a bit faster.
                if (sys.usingJniLibrary()) {
                    System.out.println("ET is local\n");
                }
                else {
                    System.out.println("ET is remote\n");
                }

                EtEvent[]   events;
                long        t1, t2, time, totalT=0L, totalCount=0L, count=0L;
                double      rate, avgRate;

                // keep track of time for event rate calculations
                t1 = System.currentTimeMillis();

                while (true) {

                    if (disconnect && !connected) {
                        System.out.println("Event Consumer reconnect\n");
                        connectToEt();
                        System.out.println("Event Consumer CONNECTED\n");
                        connected = true;
                    }

                    do {
                        //System.out.println("Event Consumer get events\n");
                        // Get array of new events (don't allocate local mem if blasting)
                        events = sys.getEvents(att, Mode.SLEEP, Modify.NOTHING, 0, 1);
                        count += events.length;

                        //System.out.println("Event Consumer put events\n");
                        // put events back into ET system
                        sys.putEvents(att, events);

                        // Find lapsed time
                        t2 = System.currentTimeMillis();
                        time = t2 - t1;
                        //System.out.println("Event Consumer time = \n" + time);

                    } while (time < consumerDelay);

                    t1 = t2;

                    if (disconnect) {
                        disconnectFromEt();
                        connected = false;
                    }

//                    // calculate the event rate
//                    t2 = System.currentTimeMillis();
//                    time = t2 - t1;
//
//                    if (time > 5000) {
//                        // reset things if necessary
//                        if ( (totalCount >= (Long.MAX_VALUE - count)) ||
//                                (totalT >= (Long.MAX_VALUE - time)) )  {
//                            totalT = totalCount = count = 0L;
//                            t1 = t2;
//                            continue;
//                        }
//
//                        rate = 1000.0 * ((double) count) / time;
//                        totalCount += count;
//                        totalT += time;
//                        avgRate = 1000.0 * ((double) totalCount) / totalT;
//                        // Event rates
//                        System.out.println("Events = " + String.format("%.3g", rate) +
//                                           " Hz,    avg = " + String.format("%.3g", avgRate));
//
//                        // Data rates
//                        rate    = ((double) count) * size / time;
//                        avgRate = ((double) totalCount) * size / totalT;
//                        System.out.println("  Data = " + String.format("%.3g", rate) +
//                                " kB/s,  avg = " + String.format("%.3g", avgRate) + "\n");
//
//                        count = 0L;
//                        t1 = System.currentTimeMillis();
//                    }

                }
            }
            catch (Exception ex) {
                System.out.println("Error using ET system as producer");
                ex.printStackTrace();
            }
        }
    }



    public EtStressTester() {    }


    private static void usage() {
        System.out.println("\nUsage: java EtStressTester -f <et name>\n" +
                "                      [-h] [-v] [-r] [-m] [-b] [-dis]\n" +
                "                      [-host <ET host>]\n" +
                "                      [-c <injector count>]\n" +
                "                      [-t <consumer time period is msec>]\n" +
                "                      [-d <delay>] [-p <ET port>]\n" +
                "                      [-i <interface address>] [-a <mcast addr>]\n\n" +

                "       -f     ET system's (memory-mapped file) name\n" +
                "       -host  ET system's host if direct connection (default to local)\n" +
                "       -h     help\n" +
                "       -v     verbose output\n" +

                "       -c     injector count (# of threads inserting events into ET) (1-5)\n" +
                "       -d     delay in millisec between each injector round of getting & putting\n" +
                "       -t     consumer thread cycle's time period, msec\n" +
                "       -dis   injectors disconnect after each insertion then reconnect for the next\n" +
                "       -bor   inserted event is \"first event\" or \"beginning-of-run\" instead of user\n\n" +

                "       -p     ET port (TCP for direct, UDP for broad/multicast)\n" +
                "       -r     act as remote (TCP) client even if ET system is local\n" +

                "       -i     outgoing network interface address (dot-decimal)\n" +
                "       -a     multicast address(es) (dot-decimal), may use multiple times\n" +
                "       -m     multicast to find ET (use default address if -a unused)\n" +
                "       -b     broadcast to find ET\n\n" +

                "        This producer works by making a direct connection to the\n" +
                "        ET system's server port and host unless at least one multicast address\n" +
                "        is specified with -a, the -m option is used, or the -b option is used\n" +
                "        in which case multi/broadcasting used to find the ET system.\n" +
                "        If multi/broadcasting fails, look locally to find the ET system.\n" +
                "        This program gets new events from the system and puts them back.\n\n");
    }



    public static void main(String[] args) {

        int port=0, injectorCount=1;
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
            else if (args[i].equalsIgnoreCase("-dis")) {
                disconnect = true;
            }
            else if (args[i].equalsIgnoreCase("-bor")) {
                userEvent = false;
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
            else if (args[i].equalsIgnoreCase("-t")) {
                try {
                    consumerDelay = Integer.parseInt(args[++i]);
                    if (consumerDelay < 1) {
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
            else if (args[i].equalsIgnoreCase("-c")) {
                try {
                    injectorCount = Integer.parseInt(args[++i]);
                    if ((injectorCount < 1) || (injectorCount > 5)) {
                        System.out.println("Injector count number must be between 1 and 5.");
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
            openConfig = new EtSystemOpenConfig();

            if (broadcast && multicast) {
                broadAndMulticast = true;
            }

            // if multicasting to find ET
            if (multicast) {
                if (multicastAddrs.size() < 1) {
                    // Use default mcast address if not given on command line
                    openConfig.addMulticastAddr(EtConstants.multicastAddr);
                }
                else {
                    // Add multicast addresses to use
                    for (String mcastAddr : multicastAddrs) {
                        openConfig.addMulticastAddr(mcastAddr);
                    }
                }
            }

            if (broadAndMulticast) {
                System.out.println("Broad and Multicasting");
                if (port == 0) {
                    port = EtConstants.udpPort;
                }
                openConfig.setUdpPort(port);
                openConfig.setNetworkContactMethod(EtConstants.broadAndMulticast);
                openConfig.setHost(EtConstants.hostAnywhere);
            }
            else if (multicast) {
                System.out.println("Multicasting");
                if (port == 0) {
                    port = EtConstants.udpPort;
                }
                openConfig.setUdpPort(port);
                openConfig.setNetworkContactMethod(EtConstants.multicast);
                openConfig.setHost(EtConstants.hostAnywhere);
            }
            else if (broadcast) {
                System.out.println("Broadcasting");
                if (port == 0) {
                    port = EtConstants.udpPort;
                }
                openConfig.setUdpPort(port);
                openConfig.setNetworkContactMethod(EtConstants.broadcast);
                openConfig.setHost(EtConstants.hostAnywhere);
            }
            else {
                if (port == 0) {
                    port = EtConstants.serverPort;
                }
                openConfig.setTcpPort(port);
                openConfig.setNetworkContactMethod(EtConstants.direct);
                if (host == null) {
                    host = EtConstants.hostLocal;
                }
                openConfig.setHost(host);
                System.out.println("Direct connection to " + host);
            }

            // Defaults are to use operating system default buffer sizes and turn off TCP_NODELAY
            openConfig.setNetworkInterface(outgoingInterface);
            openConfig.setWaitTime(0);
            openConfig.setEtName(etName);
            openConfig.setResponsePolicy(EtConstants.policyError);

            directConfig = new EtSystemOpenConfig(openConfig);
            directConfig.setConnectRemotely(false);

            if (remote) {
                System.out.println("Set as remote");
                openConfig.setConnectRemotely(remote);
            }

            // Start up consumer thread
            Thread cThd = new Thread(null, new EventConsumer(), "event consumer");
            System.out.println("start event consumer thread");
            cThd.start();

            // Start up injector threads
            for (int i=0; i < injectorCount; i++) {
                Thread inj = new Thread(null, new Injector(i), "injector" + i);
                System.out.println("start event injector thread " + i);
                inj.start();
            }

            while(true) {
                Thread.sleep(2000);
//System.out.print(".");
            }
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as producer");
            ex.printStackTrace();
        }
    }

}
