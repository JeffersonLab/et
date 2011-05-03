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

package org.jlab.coda.et;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.channels.FileChannel;
import java.nio.MappedByteBuffer;
import java.nio.ByteOrder;

import org.jlab.coda.et.exception.*;

/**
 * This class opens (finds and connects to) an ET system. The use of this class
 * is hidden from the user. There is no reason to use it explicitly.
 *
 * @author Carl Timmer
 */

public class EtSystemOpen {

    /** Object specifying how to open an ET system. */
    private EtSystemOpenConfig config;

    /** TCP socket connection established with an ET system's server. */
    private Socket sock;

    /** Name of the host the ET system resides on. */
    private String host;

    /** Port number of the ET system's tcp server. */
    private int tcpPort;

    /** In case of multiple responding ET systems, a map of their hosts & ports. */
    private LinkedHashMap<String, Integer> responders;

    /** Is this object connected to a real, live ET system? */
    private boolean connected;

    /** Debug level. Set by {@link EtSystemOpen#setDebug(int)}. */
    private int debug;

    // using shared memory

    /** If opening a local, C-based ET system, try mapping the memory containing
     *  the event data instead of sending it over sockets. */
    private boolean mapLocalSharedMemory;

    /** Buffer containing mapped memory if {@link #mapLocalSharedMemory} flag is true. */
    private MappedByteBuffer buffer;

    /** Object for accessing native methods which use C library to get and put events. */
    private EtJniAccess jni;

    // properties of opened ET system

    /** Endian value of the opened ET system. */
    private int endian;

    /** Total number of events of the opened ET system. */
    private int numEvents;

    /** Event size in bytes of the opened ET system. */
    private long eventSize;

    /** Major version number of the opened ET system. */
    private int version;

    /** Number of select integers in the opened ET system. */
    private int stationSelectInts;

    /** Language used to implement the opened ET system. The possible values are
     *  {@link EtConstants#langJava} for Java, {@link EtConstants#langCpp} for C++,
     *  and {@link EtConstants#langC} for C. */
    private int language;

    /** True if ET system is 64 bit, else false. */
    private boolean bit64;


    // convenience variables
    private final boolean  foundServer=true, cannotFindServer=false;
    private final boolean  gotMatch=true,    noMatch=false;


    /**
     * Constructor which stores copy of argument.
     * @param config EtSystemOpenConfig object
     */
    public EtSystemOpen(EtSystemOpenConfig config) {
        this.config = new EtSystemOpenConfig(config);
        debug = EtConstants.debugError;
        responders = new  LinkedHashMap<String, Integer>(20);
    }


    // public sets


    /**
     * Sets the debug output level. Must be either {@link EtConstants#debugNone},
     * {@link EtConstants#debugSevere}, {@link EtConstants#debugError},
     * {@link EtConstants#debugWarn}, or {@link EtConstants#debugInfo}.
     *
     * @param debug debug level
     * @throws EtException
     *     if bad argument value
     */
    public void setDebug(int debug) throws EtException {
        if ((debug != EtConstants.debugNone)   &&
            (debug != EtConstants.debugSevere) &&
            (debug != EtConstants.debugError)  &&
            (debug != EtConstants.debugWarn)   &&
            (debug != EtConstants.debugInfo))    {
            throw new EtException("bad debug argument");
        }
        this.debug = debug;
    }


    // public gets


    /** Gets the total number of events of the opened ET system.
     *  @return total number of events */
    public int getNumEvents() {return numEvents;}

    /** Gets the size of the normal events in bytes of the opened ET system.
     *  @return size of normal events in bytes */
    public long getEventSize() {return eventSize;}

    /** Gets the tcp server port number of the opened ET system.
     *  @return tcp server port number */
    public int getTcpPort() {return tcpPort;}

    /** Gets the host name the opened ET system is running on.
     *  @return host name */
    public String getHost() {return host;}

    /** Gets the name of the ET system (file).
     *  @return ET system name */
    public String getName() {return config.getEtName();}

    /** Gets the endian value of the opened ET system.
     *  @return endian value */
    public int getEndian() {return endian;}

    /** Gets the major version number of the opened ET system.
     *  @return major ET version number */
    public int getVersion() {return version;}

    /** Gets the language used to implement the opened ET system.
     *  @return language */
    public int getLanguage() {return language;}

    /** Gets the number of station select integers of the opened ET system.
     *  @return number of select integers */
    public int getSelectInts() {return stationSelectInts;}

    /** Gets the socket connecting this object to the ET system.
     *  @return socket */
    public Socket getSocket() {return sock;}

    /** Gets the debug output level.
     *  @return debug output level */
    public int getDebug() {return debug;}

    /** Gets a copy of the EtSystemOpenConfig configuration object.
     *  @return copy of configuration object */
    public EtSystemOpenConfig getConfig() {return new EtSystemOpenConfig(config);}

    /** Gets whether the ET system is connected (opened) or not.
     *  @return status of connection to ET system */
    synchronized public boolean isConnected() {return connected;}

    /** Gets whether the operating system is 64 bit or not.
     *  @return <code>true</code> if the operating system is 64 bit, else <code>false</code>  */
    public boolean isBit64() {return bit64;}

    /** Gets a map of the hosts and ports of responding ET systems to broad/multicasts.
     *  @return a map of the hosts and ports of responding ET systems to broad/multicasts */
    public LinkedHashMap<String, Integer> getResponders() {return responders;}

    /** Gets whether opening a local, C-based ET system and trying to map the memory containing
     *  the event data instead of sending it over sockets.
     *  @return <code>true</code> if opening a local, C-based ET system and trying to map the memory containing
     *          the event data instead of sending it over sockets  */
    public boolean isMapLocalSharedMemory() {return mapLocalSharedMemory;}

    /** Gets buffer containing the memory mapped file if {@link #isMapLocalSharedMemory} returns true.
     *  @return buffer contained the memory mapped file */
    public MappedByteBuffer getBuffer() {return buffer;}

    /** Gets the object used to access native methods when using local, C-based ET system. */
    public EtJniAccess getJni() {return jni;}


    // The next two methods are really only useful when the
    // EtTooManyException is thrown from "connect" or findServerPort.


    /** Gets all host names when multiple ET systems respond.
     *  @return all host names from responding ET systems */
    public String[] getAllHosts() {
        if (responders.size() == 0) {
            if (host == null) {
                return null;
            }
            return new String[] {host};
        }
        return (String []) responders.keySet().toArray();
    }

    /** Gets all port numbers when multiple ET systems respond.
     *  @return all port numbers from responding ET systems */
    public int[] getAllPorts() {
        if (responders.size() == 0) {
            if (tcpPort == 0) {
                return null;
            }
            return new int[] {tcpPort};
        }

        Integer[] p = (Integer []) responders.values().toArray();
        int[] ports = new int[p.length];
        for (int i=0; i < p.length; i++) {
            ports[i] = p[i];
        }
        return ports;
    }


    /**
     * Finds the ET system's tcp server port number and host.
     *
     * @return <code>true</code> if server found, else <code>false</code>
     *
     * @throws java.io.IOException
     *     if problems with network comunications
     * @throws java.net.UnknownHostException
     *     if the host address(es) is(are) unknown
     * @throws EtTooManyException
     *     if there were more than one valid response when policy is set to
     *     {@link EtConstants#policyError} and we are looking either
     *     remotely or anywhere for the ET system.
     */
    private boolean findServerPort() throws IOException, UnknownHostException, EtTooManyException {

        boolean match = noMatch;
        int     status, totalPacketsSent = 0, sendPacketLimit = 4;
        int     timeOuts[] = {100, 2000, 4000, 7000};
        int     waitTime, socketTimeOut = 20000; // socketTimeOut > sum of timeOuts
        String  specifiedHost = null;

        // clear out any previously stored objects
        responders.clear();

        // Put outgoing packet info into a byte array to send to ET systems
        ByteArrayOutputStream  baos = new ByteArrayOutputStream(122);
        DataOutputStream        dos = new DataOutputStream(baos);

        // write magic #s
        dos.writeInt(EtConstants.magicNumbers[0]);
        dos.writeInt(EtConstants.magicNumbers[1]);
        dos.writeInt(EtConstants.magicNumbers[2]);
        // write ET version
        dos.writeInt(EtConstants.version);
        // write string length of ET name
        dos.writeInt(config.getEtName().length() + 1);
        // write ET name
        try {
            dos.write(config.getEtName().getBytes("ASCII"));
            dos.writeByte(0);
        }
        catch (UnsupportedEncodingException ex) {/* will never happen */}
        dos.flush();

        // construct byte array to send over a socket
        final byte sbuffer[] = baos.toByteArray();
        dos.close();
        baos.close();

        // We may need to send packets over many different sockets
        // as there may be broadcasting on multiple subnets as well
        // as multicasts on several addresses. Keep track of these
        // sockets, addresses, & packets with this class:
        class send {
            int             port;
            String          address;
            InetAddress     addr;
            MulticastSocket socket;
            DatagramPacket  packet;

            send (String address, MulticastSocket socket, int port) throws UnknownHostException {
                this.port    = port;
                this.address = address;
                this.socket  = socket;
                addr    = InetAddress.getByName(address);  //UnknownHostEx
                packet  = new DatagramPacket(sbuffer, sbuffer.length, addr, port);
            }
        }

        // store all places to send packets to in a list
        LinkedList<send> sendList = new LinkedList<send>();

        // unqualifed, specified host
        String unqualifedHost = null;

        // find fully-qualifed, local host
        String localHost = null;
        InetAddress localAddr = null;
        try {
            localAddr = InetAddress.getLocalHost();
            localHost = localAddr.getHostName();
        }
        catch (UnknownHostException ex) {}

        // If the host is not remote or anywhere out there. If it's
        // local or we know its name, send a UDP packet to it alone.
        if ((!config.getHost().equals(EtConstants.hostRemote)) &&
            (!config.getHost().equals(EtConstants.hostAnywhere)))  {

            // We can use multicast socket for regular UDP - it works
            MulticastSocket socket = new MulticastSocket();	//IOEx
            // Socket will unblock after timeout,
            // letting reply collecting thread quit
            try {socket.setSoTimeout(socketTimeOut);}
            catch (SocketException ex) {}

            // If it's local, find name and send packet directly there.
            // This will work in Java where the server listens on all addresses.
            // But it won't work for C where only broad and multicast address
            // are listened to.
            if ((config.getHost().equals(EtConstants.hostLocal)) ||
                (config.getHost().equals("localhost")))  {
                specifiedHost = localHost;
                // else if we know host's name ...
            } else {
                specifiedHost = config.getHost();
            }
            unqualifedHost = specifiedHost.substring(0, specifiedHost.indexOf("."));
            sendList.add(new send(specifiedHost, socket, config.getUdpPort()));

            // setup broadcast sockets & packets first
            if ((config.getNetworkContactMethod() == EtConstants.broadcast) ||
                (config.getNetworkContactMethod() == EtConstants.broadAndMulticast)) {

                // We can use multicast socket for broadcasting - it works
                socket = new MulticastSocket();    //IOEx
                // Socket will unblock after timeout,
                // letting reply collecting thread quit
                try {
                    socket.setSoTimeout(socketTimeOut);
                    socket.setBroadcast(true);
                }
                catch (SocketException ex) {
                }

                sendList.add(new send(config.broadcastIP, socket, config.getUdpPort()));
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("findServerPort: broadcasting to " + config.broadcastIP +
                            " on port " + config.getUdpPort());
                }
            }

            // setup multicast sockets & packets next
            if ((config.getNetworkContactMethod() == EtConstants.multicast) ||
                (config.getNetworkContactMethod() == EtConstants.broadAndMulticast)) {

                for (String addr : config.getMulticastAddrs()) {
                    socket = new MulticastSocket();    //IOEx
                    try {
                        socket.setSoTimeout(socketTimeOut);
                    }
                    catch (SocketException ex) {
                    }

                    if (config.getTTL() != 1) {
                        socket.setTimeToLive(config.getTTL());        //IOEx
                    }

                    sendList.add(new send(addr, socket, config.getMulticastPort()));
                    if (debug >= EtConstants.debugInfo) {
                        System.out.println("findServerPort: multicasting to " + addr + " on port " + config.getMulticastPort());
                    }
                }
            }

            if (debug >= EtConstants.debugInfo) {
                System.out.println("findServerPort: send to local or specified host " + specifiedHost +
                        " on port " + config.getUdpPort());
            }
        }

        // else if the host name is not specified, and it's either
        // remote or anywhere out there, broad/multicast to find it
        else {

            // setup broadcast sockets & packets first
            if ((config.getNetworkContactMethod() == EtConstants.broadcast) ||
                (config.getNetworkContactMethod() == EtConstants.broadAndMulticast)) {

                // We can use multicast socket for broadcasting - it works
                MulticastSocket socket = new MulticastSocket();    //IOEx
                // Socket will unblock after timeout,
                // letting reply collecting thread quit
                try {
                    socket.setSoTimeout(socketTimeOut);
                    socket.setBroadcast(true);
                }
                catch (SocketException ex) {
                }

                sendList.add(new send(config.broadcastIP, socket, config.getUdpPort()));
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("findServerPort: broadcasting to " + config.broadcastIP +
                            " on port " + config.getUdpPort());
                }
            }

            // setup multicast sockets & packets next
            if ((config.getNetworkContactMethod() == EtConstants.multicast) ||
                (config.getNetworkContactMethod() == EtConstants.broadAndMulticast)) {

                for (String addr : config.getMulticastAddrs()) {
                    MulticastSocket socket = new MulticastSocket();    //IOEx
                    try {
                        socket.setSoTimeout(socketTimeOut);
                    }
                    catch (SocketException ex) {
                    }

                    if (config.getTTL() != 1) {
                        socket.setTimeToLive(config.getTTL());        //IOEx
                    }

                    sendList.add(new send(addr, socket, config.getMulticastPort()));
                    if (debug >= EtConstants.debugInfo) {
                        System.out.println("findServerPort: multicasting to " + addr + " on port " + config.getMulticastPort());
                    }
                }
            }
        }

        /** Class to help receive a packet on a socket. */
        class get {
            // min data size = 8*4 + 3 + Constants.ipAddrStrLen +
            //                 2*Constants.maxHostNameLen(); = 558 bytes
            // but give us a bit of extra room for lots of names with 4k bytes
            byte[] buffer = new byte[4096];
            DatagramReceive thread;
            DatagramPacket  packet;
            MulticastSocket socket;

            get(MulticastSocket sock) {
                packet = new DatagramPacket(buffer, buffer.length);
                socket = sock;
            }

            // start up thread to receive single udp packet on single socket
            void start() {
                thread = new DatagramReceive(packet, socket);
                thread.start();
            }
        }

        // store things here
        LinkedList<get> receiveList = new LinkedList<get>();

        // start reply collecting threads
        for (send sender : sendList) {
            get receiver = new get(sender.socket);
            receiveList.add(receiver);
            // start single thread
            if (debug >= EtConstants.debugInfo) {
                System.out.println("findServerPort: starting thread to socket " + sender.socket);
            }
            receiver.start();
        }

        Thread.yield();

        sendPoint:
        // set a limit on the total # of packet groups sent out to find a server
        while (totalPacketsSent < sendPacketLimit) {
            // send packets out on all sockets
            for (send sender : sendList) {
                sender.socket.send(sender.packet); //IOException
            }
            // set time to wait for reply (gets longer with each round)
            waitTime = timeOuts[totalPacketsSent++];

            get:
            while (true) {
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("findServerPort: wait for " + waitTime + " milliseconds");
                }
                // wait for replies
                try {
                    Thread.sleep(waitTime);
                }
                catch (InterruptedException ix) {
                }

                // check for replies on all sockets
                for (get receiver : receiveList) {
                    status = receiver.thread.waitForReply(10);
                    if (debug >= EtConstants.debugInfo) {
                        System.out.println("findServerPort: receive on socket " + receiver.socket +
                                ", status = " + status);
                    }

                    // if error or timeout ...
                    if ((status == DatagramReceive.error) || (status == DatagramReceive.timedOut)) {
                        // continue;
                    }

                    // else if got packet ...
                    else if (status == DatagramReceive.receivedPacket) {
                        // Analyze packet to see it matches the ET system we were
                        // looking for; if not, try to get another packet. If it
                        // is a match, store it in a HashMap (responders).
                        if (replyMatch(receiver.packet)) { // IOEx, UnknownHostEx
                            if (debug >= EtConstants.debugInfo) {
                                System.out.println("findServerPort: found match");
                            }
                            match = gotMatch;
                        }
                        else {
                            if (debug >= EtConstants.debugInfo) {
                                System.out.println("findServerPort: no match");
                            }
                        }
                        // See if there are other packets cued up,
                        // but don't wait too long. The thread we
                        // started is ended so start another up again.
                        waitTime = 50;
                        receiver.start();
                        Thread.yield();

                        continue get;
                    }

                }


                // if we don't have a match, try again
                if (!match) {
                    // If max # of packets not yet sent, send another
                    // batch and try again with a longer wait
                    if (totalPacketsSent < sendPacketLimit) {
                        if (debug >= EtConstants.debugInfo) {
                            System.out.println("findServerPort: timedout, try again with longer wait");
                        }
                        continue sendPoint;
                    }
                }

                break sendPoint;

            } // while (true)
        } // while (totalPacketsSent < sendPacketLimit)


        if (match) {
            // If the host is not remote or anywhere (i.e. we know its name).
            if ((!config.getHost().equals(EtConstants.hostRemote)) &&
                (!config.getHost().equals(EtConstants.hostAnywhere))) {

                // if we have more than one responding ET system ...
                if (responders.size() > 1) {
                    // pick first ET system that matches the specified host's name
                    for (Map.Entry<String, Integer> entry : responders.entrySet()) {
                        String h = entry.getKey();
                        if (specifiedHost.equals(h) || unqualifedHost.equals(h)) {
                            host = h;
                            tcpPort = entry.getValue();
                            return foundServer;
                        }
                    }
                }
            }
            // if we're looking remotely or anywhere
            else {
                // if we have more than one responding ET system
                if (responders.size() > 1) {
                    // if picking first responding ET system ...
                    if (config.getResponsePolicy() == EtConstants.policyFirst) {
                        Iterator<Map.Entry<String,Integer>> i = responders.entrySet().iterator();
                        Map.Entry<String,Integer> e = i.next();
                        host = e.getKey();
                        tcpPort = e.getValue();
                    }
                    // else if picking local system first ...
                    else if (config.getResponsePolicy() == EtConstants.policyLocal) {
                        // compare local host to responding hosts
                        boolean foundLocalHost = false;

                        for (Map.Entry<String, Integer> entry : responders.entrySet()) {
                            InetAddress hAddr = InetAddress.getByName(entry.getKey());
                            if (localAddr.equals(hAddr)) {
                                host = entry.getKey();
                                tcpPort = entry.getValue();
                                foundLocalHost = true;
                            }
                        }

                        // if no local host found, pick first responder
                        if (!foundLocalHost) {
                            Iterator<Map.Entry<String,Integer>> i = responders.entrySet().iterator();
                            Map.Entry<String,Integer> e = i.next();
                            host = e.getKey();
                            tcpPort = e.getValue();
                        }
                    }
                    // else if policy.Error
                    else {
                        throw new EtTooManyException("too many responding ET systems");
                    }
                }
            }
            return foundServer;
        }
        if (debug >= EtConstants.debugInfo) {
            System.out.println("findServerPort: cannot find server, quitting");
        }
        host = null;
        tcpPort = 0;
        return cannotFindServer;
    }


    /**
     * Analyze a received UDP packet & see if it matches the ET system we're looking for.
     *
     * @param packet responding UDP packet
     * @throws java.io.IOException
     *     if problems with network comunications
     * @throws java.net.UnknownHostException
     *     if the replied host address(es) is(are) unknown
     */
    private boolean replyMatch(DatagramPacket packet)
            throws IOException, UnknownHostException {

        byte buf[];
        ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
        DataInputStream dis = new DataInputStream(bais);
        // In case of multiple names from a responding ET system, a list of names. */
        ArrayList<String> hosts = new ArrayList<String>(20);

        // decode packet from ET system:
        // (0)  ET magic numbers (3 ints)
        // (1)  ET version #
        // (2)  port of tcp server thread (not udp config->port)
        // (3)  Constants.broadcast .multicast or broadAndMulticast (int)
        // (4)  length of next string
        // (5)    broadcast address (dotted-dec) if broadcast received or
        //        multicast address (dotted-dec) if multicast received
        //        (see int #3)
        // (6)  length of next string
        // (7)    hostname given by "uname" (used as a general
        //        identifier of this host no matter which interface is used)
        // (8)  number of names for this IP addr starting with canonical
        // (9)    32bit, net-byte ordered IPv4 address assoc with following name
        // (10)   length of next string
        // (11)       first name = canonical
        // (12)   32bit, net-byte ordered IPv4 address assoc with following name
        // (13)   length of next string
        // (14)       first alias ...
        //
        // All aliases are sent here.

        // (0)  ET magic numbers (3 ints)
        int magic1 = dis.readInt();
        int magic2 = dis.readInt();
        int magic3 = dis.readInt();
        if (magic1 != EtConstants.magicNumbers[0] ||
            magic2 != EtConstants.magicNumbers[1] ||
            magic3 != EtConstants.magicNumbers[2])  {
//System.out.println("replyMatch:  Magic numbers did NOT match");
            return noMatch;
        }

        // (1) ET version #
        int version = dis.readInt();         //IOEx
        if (version != EtConstants.version) {
//System.out.println("replyMatch:  version did NOT match");
            return noMatch;
        }

        // (2) server port #
        int port = dis.readInt();
        if ((port < 1) || (port > 65536)) {
            return noMatch;
        }

        // (3) response to what type of cast?
        int cast = dis.readInt();
        if ((cast != EtConstants.broadcast) &&
            (cast != EtConstants.multicast) &&
            (cast != EtConstants.broadAndMulticast)) {
            return noMatch;
        }

        // (4) read length of IP address (dotted-decimal) of responding address
        //     or 0.0.0.0 if java
        int length = dis.readInt();
        if ((length < 1) || (length > EtConstants.ipAddrStrLen)) {
            return noMatch;
        }

        // (5) read IP address
        buf = new byte[length];
        dis.readFully(buf, 0, length);
        String repliedIpAddress = null;
        try {repliedIpAddress = new String(buf, 0, length - 1, "ASCII");}
        catch (UnsupportedEncodingException e) {/*never happens*/}

        // (6) Read length of "uname" or InetAddress.getLocalHost().getHostName() if java,
        //     used as identifier of this host no matter which interface used.
        length = dis.readInt();
        if ((length < 1) || (length > EtConstants.maxHostNameLen)) {
            return noMatch;
        }

        // (7) read uname
        buf = new byte[length];
        dis.readFully(buf, 0, length);
        String repliedUname = null;
        try {repliedUname = new String(buf, 0, length - 1, "ASCII");}
        catch (UnsupportedEncodingException e) {}

        // (8) # of following names
        int numNames = dis.readInt();
        if (numNames < 0) {
            return noMatch;
        }

        int addr;

        for (int i=0; i<numNames; i++) {
            // (9) 32 bit network byte ordered address - not currently used
            addr = dis.readInt();
            // (10) read length of name of responding interface
            //      or name returned by InetAddress.getAllByName() if java
            length = dis.readInt();
            // (11) read host name (minus ending null)
            buf = new byte[length];
            dis.readFully(buf, 0, length);
            String repliedHostName = null;
            try {repliedHostName = new String(buf, 0, length - 1, "ASCII");}
            catch (UnsupportedEncodingException e) {}

            // store things
            hosts.add(repliedHostName);
        }

        if (debug >= EtConstants.debugInfo) {
            System.out.println("replyMatch: port = " + port +
                    ", replied IP addr = " + repliedIpAddress +
                    ", uname = " + repliedUname);
            for (int i=0; i<numNames; i++) {
                System.out.println("          : name " + (i+1) + " = " + hosts.get(i));
            }
        }

        dis.close();
        bais.close();

        InetAddress localHost = InetAddress.getLocalHost();      //UnknownHostEx

        for (String rHost : hosts) {

            // set ip address value for replied host
            InetAddress repliedHost = InetAddress.getByName(rHost);   //UnknownHostEx

            // if we're looking for a host anywhere
            if (config.getHost().equals(EtConstants.hostAnywhere)) {
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("replyMatch: .anywhere");
                }

                // Store host & port in ordered map in case there are several systems
                // that respond and user must chose which one he wants.
                responders.put(rHost, port);

                // store info here in case only 1 response
                host = rHost;
                tcpPort = port;
                return gotMatch;
            }
            // else if we're looking for a remote host
            else if (config.getHost().equals(EtConstants.hostRemote)) {
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("replyMatch: .remote");
                }
                if (!localHost.equals(repliedHost)) {
                    // Store host & port in lists in case there are several systems
                    // that respond and user must chose which one he wants
                    responders.put(rHost, port);

                    // store info here in case only 1 response
                    host = rHost;
                    tcpPort = port;
                    return gotMatch;
                }
            }
            // else if we're looking for a local host
            else if ((config.getHost().equals(EtConstants.hostLocal)) ||
                     (config.getHost().equals("localhost"))) {
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("replyMatch: .local");
                }
                if (localHost.equals(repliedHost)) {
                    // Store values. In this case no other match will be found.
                    host = rHost;
                    tcpPort = port;
                    return gotMatch;
                }
            }
            // else a specific host name has been specified
            else {
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("replyMatch: <name>");
                }
                // "config.host" is the host name we're looking for
                InetAddress etHost = InetAddress.getByName(config.getHost());    //UnknownHostEx
                if (etHost.equals(repliedHost)) {
                    // Store values. In this case no other match will be found.
                    host = rHost;
                    tcpPort = port;
                    return gotMatch;
                }
            }
        }

        return noMatch;
    }


    /**
     * Connect to ET system's server.
     *
     * @throws IOException
     *     if problems with network comunications
     * @throws EtException
     *     if the responing ET system has the wrong name, runs a different version
     *     of ET, or has a different value for {@link EtConstants#stationSelectInts}
     */
    private void connectToEtServer() throws IOException, EtException {

        DataInputStream  dis = new DataInputStream(sock.getInputStream());
        DataOutputStream dos = new DataOutputStream(sock.getOutputStream());

        // write magic #s
        dos.writeInt(EtConstants.magicNumbers[0]);
        dos.writeInt(EtConstants.magicNumbers[1]);
        dos.writeInt(EtConstants.magicNumbers[2]);

        // write our endian, length of ET filename, and ET filename
        dos.writeInt(EtConstants.endianBig);
        dos.writeInt(config.getEtName().length() + 1);
        dos.writeInt(0);    // 1 means 64 bit, 0 means 32 bit (all java is 32 bit)
        dos.writeLong(0L);	// write one 64 bit long instead of 2, 32 bit ints since = 0 anyway
        try {
            dos.write(config.getEtName().getBytes("ASCII"));
            dos.writeByte(0);
        }
        catch (UnsupportedEncodingException ex) {/* will never happen */}
        dos.flush();

        // read what ET's tcp server sends back
        if (dis.readInt() != EtConstants.ok) {
            throw new EtException("found the wrong ET system");
        }
        endian            = dis.readInt();
        numEvents         = dis.readInt();
        eventSize         = dis.readLong();
        version           = dis.readInt();
        stationSelectInts = dis.readInt();
        language          = dis.readInt();
        bit64             = dis.readInt() > 0;
        dis.skipBytes(4);

        // check to see if connecting to same version ET software
        if (version != EtConstants.version) {
            disconnect();
            throw new EtException("may not open wrong version ET system");
        }
        // double check to see if # of select ints are the same
        if (stationSelectInts != EtConstants.stationSelectInts) {
            disconnect();
            throw new EtException("may not open ET system with different # of select integers");
        }

        connected = true;

        if (debug >= EtConstants.debugInfo) {
            System.out.println("open: endian = " + (endian == EtConstants.endianBig ? "big" : "little") +
                    ", nevents = " + numEvents +
                    ", event size = " + eventSize +
                    ", version = " + version +
                    ",\n      selectInts = " + stationSelectInts +
                    ", language = " + (language == 1 ? "C" : "java"));
        }
    }


    /**
     * Is the given host identical to the local host?
     *
     * @param host host to test
     * @return <code>true</code> if host is local, else <code>false</code>
     * @throws UnknownHostException if host cannot be resolved
     */
    private boolean isHostLocal(String host) throws UnknownHostException {

        InetAddress[] allLocalAddresses = InetAddress.getAllByName(InetAddress.getLocalHost().getCanonicalHostName());
        InetAddress[] allHostAddresses  = InetAddress.getAllByName(host);

        // Compare to see if there is a match. If so, host is the local machine
        for (InetAddress hostIP : allLocalAddresses) {
            for (InetAddress localIP : allHostAddresses) {
                if (hostIP.equals(localIP)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Creates a connection to an ET system.
     *
     * @throws java.io.IOException
     *     if problems with network comunications
     * @throws java.net.UnknownHostException
     *     if the host address(es) is(are) unknown
     * @throws EtException
     *     if the responding ET system has the wrong name, runs a different
     *     version of ET, or has a different value for
     *     {@link EtConstants#stationSelectInts}
     * @throws EtTooManyException
     *     if there were more than one valid response when policy is set to
     *     {@link EtConstants#policyError} and we are looking either
     *     remotely or anywhere for the ET system.
     */
    synchronized public void connect() throws IOException, UnknownHostException,
                                              EtException, EtTooManyException {

        // In Java, all clients make a connection the the ET system server through sockets.
        // However, in cases where there is a local C-based, ET system, an attempt is also
        // made to memory map it and access events through JNI (header) and a memory mapped
        // buffer (data).
        mapLocalSharedMemory = false;
        if (config.getNetworkContactMethod() == EtConstants.direct) {
            // if making direct connection, we have host & port
            if (debug >= EtConstants.debugInfo) {
                System.out.println("connect: make a direct connection");
            }
            tcpPort = config.getTcpPort();

            // if "local" specified, find actual hostname
            if (config.getHost().equals(EtConstants.hostLocal) || config.getHost().equals("localhost")) {
                host = InetAddress.getLocalHost().getHostName();
                mapLocalSharedMemory = true;
            }
            else {
                // We prefer a fully qualified host name. If there are no "."'s
                // in it, try getHostName even though that is not guaranteed
                // to return a fully qualified name.
                if (config.getHost().indexOf(".") < 0) {
                    if (debug >= EtConstants.debugInfo) {
                        System.out.println("connect: try to make " + config.getHost() + " a fully qualified name");
                    }
                    host = InetAddress.getByName(config.getHost()).getHostName();
                }
                else {
                    host = config.getHost();
                }

                // if host is local, try to map memory
                if (isHostLocal(host)) {
                    mapLocalSharedMemory = true;
                }
            }
        }
        else {
            if (debug >= EtConstants.debugInfo) {
                System.out.println("connect: try to find server port");
            }
            // send a UDP broad or multicast packet to find ET TCP server & port
            if (!findServerPort()) {    // IOEx, UnknownHostEx, EtTooMany
                throw new EtException("Cannot find ET system");
            }

            // if host is local, try to map memory
            if (isHostLocal(host)) {
                mapLocalSharedMemory = true;
            }
        }

        // If user only wants to use sockets, don't map memory
        if (config.isConnectRemotely()) {
            mapLocalSharedMemory = false;
        }

        // open the ET system, waiting if requested & necessary
        if (debug >= EtConstants.debugInfo) {
            System.out.println("connect: try to connect to ET system");
        }

        boolean gotConnection = false;
        IOException ioException = null;
        long t1, t2;
        t1 = t2 = System.currentTimeMillis();
        while (t2 <= (t1 + config.getWaitTime())) {
            try {
                // Create a connection to an ET system TCP Server
//System.out.println("           Creating socket to ET");
                sock = new Socket(host, tcpPort);        // IOEx
                try {
                    // Set NoDelay option for fast response
                    sock.setTcpNoDelay(true);
                    // Set reading timeout to 2 second so dead ET sys
                    // can be found by reading on a socket.
                    sock.setSoTimeout(2000);
                    // Set KeepAlive so we can tell if ET system is dead
                    sock.setKeepAlive(true);
                    // set buffer size
                    sock.setReceiveBufferSize(65535);
                    sock.setSendBufferSize(65535);
                }
                catch (SocketException ex) {
                }

                connectToEtServer();    // IOEx if no ET, EtEx if incompatible ET
                gotConnection = true;
                break;
            }
            catch (IOException e) {
//System.out.println("           FAILED connection to ET, try again?");
                ioException = e;
                try {Thread.sleep(250);}
                catch (InterruptedException e1) {}
                t2 = System.currentTimeMillis();
            }
        }

        if (!gotConnection) {
            throw new IOException("Cannot create network connection to ET system", ioException);
        }

        // try using memory mapped file
        if (mapLocalSharedMemory) {
            try {
                RandomAccessFile file = new RandomAccessFile(config.getEtName(), "rw");
                FileChannel fc = file.getChannel();
                // First, map only the first part of the file which contains some
                // important data in 6 ints and 5 longs (64 bytes). Once that info is read,
                // remap the file properly to get at the data.
                buffer = fc.map(FileChannel.MapMode.READ_WRITE, 0, EtConstants.initialSharedMemBytes);

                int byteOrder = buffer.getInt();
                if (byteOrder != 0x04030201) {
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                }
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("byteOrder = " + Integer.toHexString(byteOrder));
                }

                int next = buffer.getInt();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("systemType = " + next);
                }

                next = buffer.getInt();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("major version = " + next);
                }

                next = buffer.getInt();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("minor version = " + next);
                }

                next = buffer.getInt();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("num select ints = " + next);
                }

                next = buffer.getInt();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("head byte size = " + next);
                }

                long nextLong = buffer.getLong();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("event byte size = " + nextLong);
                }

                nextLong = buffer.getLong();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("header position = " + nextLong);
                }

                long dataPosition = nextLong = buffer.getLong();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("data position = " + nextLong);
                }

                long totalFileSize = nextLong = buffer.getLong();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("total file size = " + nextLong + ", but is really " + fc.size());
                }

                long usedFileSize = nextLong = buffer.getLong();
                if (debug >= EtConstants.debugInfo) {
                    System.out.println("used file size = " + nextLong);
                }
                
                // look at data - map only data part of the file
                buffer = fc.map(FileChannel.MapMode.READ_WRITE, dataPosition,
                                usedFileSize + EtConstants.initialSharedMemBytes);
                if (byteOrder != 0x04030201) {
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                }

                // closing the file channel does NOT affect the buffer
                fc.close();

                // open the ET system locally with native method
                jni = EtJniAccess.getInstance(config.getEtName());

            }
            catch (EtTimeoutException e) {
                // cannot open an ET system through JNI, so use sockets only to connect to ET system
                mapLocalSharedMemory = false;
            }
            catch (EtException e) {
                // cannot open an ET system through JNI, so use sockets only to connect to ET system
                mapLocalSharedMemory = false;
System.out.println("Error in opening ET with jni, Et exception, use sockets only to talk to ET system");
            }
            catch (IOException e) {
                // cannot open a file, so use sockets only to connect to ET system
                mapLocalSharedMemory = false;
System.out.println("Error in opening ET with jni, IO exception, use sockets only to talk to ET system");
            }
        }
    }


    /**
     * Disconnect from the ET system server.
     */
    synchronized public void disconnect() {
        connected = false;
        try {sock.close();}
        catch (IOException ioex) {}
        // this will get rid of memory mapping once buffer is garbage-collected
        buffer = null;
    }
}



/**
 * This class is designed to receive UDP packets from ET systems responding to
 * broadcasts and multicasts trying to locate a particular ET system.
 *
 * @author Carl Timmer
 */

class DatagramReceive extends Thread {

    /** UDP Packet in which to receive communication data. */
    DatagramPacket packet;
    /** UDP Socket over which to communicate. */
    DatagramSocket socket;

    // allowed states

    /** Status of timed out. */
    static final int timedOut = 0;
    /** Status of packet received. */
    static final int receivedPacket = 1;
    /** Status of error. */
    static final int error = -1;

    /** Current status. */
    volatile int status = timedOut;

    /**
     * Creates a DatagramReceive object.
     * @param recvPacket UDP packet in which to receive communication data.
     * @param recvSocket UDP Socket over which to communicate
     *
     */
    DatagramReceive(DatagramPacket recvPacket, DatagramSocket recvSocket) {
        packet = recvPacket;
        socket = recvSocket;
    }


    /**
     * Waits for a UDP packet to be received.
     * This needs to be synchronized so the "wait" will work.
     *
     * @param time time to wait in milliseconds before timing out.
     */
    synchronized int waitForReply(int time) {
        // If the thread was started before we got a chance to wait for the
        // reply, check to see if a reply has already come.
        if (status != timedOut) {
            return status;
        }

        try {wait(time);}
        catch (InterruptedException intEx) {}
        return status;
    }


    // No need to synchronize run as it can only be called once
    // by this object. Furthermore, if it is synchronized, then
    // if no packet is received, it is blocked with the mutex.
    // That, in turn, does not let the wait statement return from
    // a timeout. Since run is the only method that changes "status",
    // status does not have to be mutex-protected.

    /** Method to run thread to receive UDP packet and notify waiters. */
    public void run() {
        status = timedOut;
        try {
            socket.receive(packet);
            status = receivedPacket;
        }
        catch (IOException iox) {
            status = error;
            return;
        }
        synchronized (this) {
            notify();
        }
    }

}



