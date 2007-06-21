/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 7-Jul-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.server;

import java.io.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.cMsgNetworkConstants;

/**
 * This class implements a cMsg name server in the cMsg domain.
 * A word of caution. If multiple cMsgNameServer objects exist in
 * a single JVM and they both service clients in the cMsg subdomain,
 * then there will be undesirable effects. In other words, the
 * cMsg subdomain uses static data in some of its implementing
 * classes (cMsgServerBridge & subdomains.cMsg).
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgNameServer extends Thread {

    /** This server's name. */
    private String serverName;

    /** This server's TCP listening port number. */
    private int port;

    /** This server's UDP listening port number for receiving broadcasts. */
    private int broadcastPort;

    /**
     * This is the time ordering property of the server.
     * If this is true, then all non-(un)subscribe commands sent to it
     * are guaranteed to be passed to the subdomain handler objects in
     * the order in which they were received.
     */
    private boolean timeOrdered;

    /** Server channel (contains socket). */
    private ServerSocketChannel serverChannel;

    /** UDP socket on which to read broadcast packets sent from cMsg clients. */
    private DatagramSocket broadcastSocket;

    /** Thread which receives client broadcasts. */
    private cMsgBroadcastListeningThread broadcastThread;

    /**
     * Set of all active domain server objects. It is implemented as a HashMap
     * but that is only to take advantage of the concurrency protection.
     * The values are all null. This set will be used to call the active domain servers'
     * handleServerShutdown methods when this name server is being
     * shutdown or also when creating monitoring data strings.
     */
    ConcurrentHashMap<cMsgDomainServer,String> domainServers;

    /** Level of debug output for this class. */
    private int debug;

    /**
     * Set of all subscriptions (including the subAndGets) of all clients
     * on this server. This is mutex protected by {@link #subscribeLock}.
     */
    HashSet<cMsgServerSubscribeInfo> subscriptions =
            new HashSet<cMsgServerSubscribeInfo>(100);

    /** Lock to ensure all access to {@link #subscriptions} is sequential. */
    final ReentrantLock subscribeLock = new ReentrantLock();

    /** Tell the server to kill spawned threads. */
    private boolean killAllThreads;

    /**
     * Sets boolean to kill this and all spawned threads.
     * @param b setting to true will kill this and all spawned threads
     */
    public void setKillAllThreads(boolean b) {killAllThreads = b;}

    /**
     * Gets boolean value specifying whether to kill this and all spawned threads.
     * @return boolean value specifying whether to kill this and all spawned threads
     */
    public boolean getKillAllThreads() {return killAllThreads;}

    /**
     * List of all ClientHandler objects. This list is used to
     * end these threads nicely during a shutdown.
     */
    private ArrayList<ClientHandler> handlerThreads;

    /** String which contains the entire monitor data of the server cloud (xml format). */
    String fullMonitorXML;

    /** String which contains the monitor data of this particular name server (xml format). */
    String nsMonitorXML;

    /**
     * Password that clients need to match before being allowed to connect.
     * This is subdomain independent and applies to the server as a whole.
     */
    String clientPassword;

    //--------------------------------------------------------
    // The following class members are associated with the
    // server-to-server operation of the cMsg subdomain.
    //--------------------------------------------------------

    /**
     * Password that this server needs to join a cloud and the password that
     * another server needs to join this one. This is cMsg subdomain specific.
     */
    String cloudPassword;

    /**
     * Does this server stand alone and NOT allow bridges
     * to/from other cMsg subdomain servers?
     */
    boolean standAlone;

    /** Server this name server is in the middle of or starting to connect to. */
    volatile cMsgServerBridge bridgeBeingCreated;

    /**
     * Use this to signal that this server's listening thread has been started
     * so bridges may be created.
     */
    CountDownLatch listeningThreadStartedSignal = new CountDownLatch(1);

    /**
     * Use this to signal the point at which other servers and clients
     * are allowed to connect to this server.
     */
    CountDownLatch allowConnectionsSignal = new CountDownLatch(1);

    /** Server is in the server cloud. */
    static final byte INCLOUD  = 0;
    /** Server is NOT in the server cloud. */
    static final byte NONCLOUD = 1;
    /** Server is in the process of becoming a part of the server cloud. */
    static final byte BECOMINGCLOUD = 2;
    /** Server cloud status is unknown. */
    static final byte UNKNOWNCLOUD = 3;

    /**
     * Keep track of all name servers which have connected to this server.
     * This hashset stores the server name (host:port).
     */
    Set<String> nameServers = Collections.synchronizedSet(new HashSet<String>(20));

    /**
     * Keep track of all the cMsgServerBridge objects in the cMsg subdomain.
     * A bridge is a connection from this cMsg server to another.
     * The server name (host:port) is the key and the bridge object is the value.
     */
    ConcurrentHashMap<String, cMsgServerBridge> bridges =
            new ConcurrentHashMap<String, cMsgServerBridge>(20);

    /**
     * This value tells the relationship of this server to the cloud.
     * The value may be one of {@link #INCLOUD}, {@link #NONCLOUD},
     * or {@link #BECOMINGCLOUD}. It may only be used/changed when
     * the cloudLock is locked.
     */
    private int cloudStatus = NONCLOUD;

    /**
     * Lock to ensure that servers are added to the server cloud one-at-a-time
     * and to ensure that clients are added to servers one-at-a-time as well.
     * This is used only in the cMsg subdomain.
     */
    private ReentrantLock cloudLock = new ReentrantLock();

    /**
     * This method locks a lock used in adding servers to the server cloud and in adding
     * clients to servers. This is used only in the cMsg subdomain.
     */
    public void cloudLock() {
//System.out.println(">> NS: try to lock cloud (blocking)");
        cloudLock.lock();
    }

    /**
     * This method locks a lock used in adding servers to the server cloud and in adding
     * clients to servers. This is used only in the cMsg subdomain.
     * @param delay time in milliseconds to wait for the lock before timing out
     * @return true if locked, false otherwise
     */
    public boolean cloudLock(int delay) {
        try {
//System.out.println(">> NS: try to lock cloud (timeout = " + delay + " ms)");
            return cloudLock.tryLock(delay, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * This method unlocks a lock used in adding servers to the server cloud and in adding
     * clients to servers. This is used only in the cMsg subdomain.
     */
    public void cloudUnlock() {
//System.out.println(">> NS: try to unlock cloud");
        cloudLock.unlock();
//System.out.println(">> NS: unlocked cloud");
    }

    /**
     * Get the status of the relationship of this server to the cMsg subdomain
     * server cloud.
     * @return status which is one of {@link #INCLOUD}, {@link #NONCLOUD},
     *         or {@link #BECOMINGCLOUD}
     */
    public int getCloudStatus() {
        return cloudStatus;
    }

    /**
     * Set the status of the relationship of this server to the cMsg subdomain
     * server cloud. The status may only be one of {@link #INCLOUD}, {@link #NONCLOUD},
     * or {@link #BECOMINGCLOUD}.
     * @param status status of the relationship of this server to the cMsg subdomain
     *               server cloud
     */
    public void setCloudStatus(int status) {
        if ((status != INCLOUD) &&
            (status != NONCLOUD) &&
            (status != BECOMINGCLOUD)) {
            return;
        }
        cloudStatus = status;
    }

    //--------------------------------------------------------
    //--------------------------------------------------------

    /**
     * Get this server's name (host:port).
     * @return server's name
     */
    public String getServerName() {
         return serverName;
     }


    /**
     * Get the name server's listening port.
     * @return listening port
     */
    public int getPort() {
        return port;
    }


    /**
     * Constructor which reads environmental variables and opens listening sockets.
     *
     * @param port TCP listening port for communication from clients
     * @param udpPort UDP listening port for receiving broadcasts from clients
     * @param timeOrdered if true all client commands are processed in the order received
     * @param standAlone  if true no other cMsg servers are allowed to attached to this one and form a cloud
     * @param clientPassword password client needs to provide to connect to this server
     * @param cloudPassword  password server needs to provide to connect to this server to become part of a cloud
     * @param debug desired level of debug output
     */
    public cMsgNameServer(int port, int udpPort, boolean timeOrdered, boolean standAlone,
                          String clientPassword, String cloudPassword, int debug) {

        domainServers  = new ConcurrentHashMap<cMsgDomainServer,String>(20);
        handlerThreads = new ArrayList<ClientHandler>(10);

        this.debug          = debug;
        this.timeOrdered    = timeOrdered;
        this.standAlone     = standAlone;
        this.cloudPassword  = cloudPassword;
        this.clientPassword = clientPassword;

        // read env variable for starting (desired) port number
        if (port < 1) {
            try {
                String env = System.getenv("CMSG_PORT");
                if (env != null) {
                    port = Integer.parseInt(env);
                }
            }
            catch (NumberFormatException ex) {
                System.out.println("Bad port number specified in CMSG_PORT env variable");
                ex.printStackTrace();
                System.exit(-1);
            }
        }

        if (port < 1) {
            port = cMsgNetworkConstants.nameServerStartingPort;
        }

        // port #'s < 1024 are reserved
        if (port < 1024) {
            System.out.println("Port number must be > 1023");
            System.exit(-1);
        }

        // read env variable for starting (desired) UDP port number
        if (udpPort < 1) {
            try {
                String env = System.getenv("CMSG_BROADCAST_PORT");
                if (env != null) {
                    udpPort = Integer.parseInt(env);
                }
            }
            catch (NumberFormatException ex) {
                System.out.println("Bad port number specified in CMSG_BROADCAST_PORT env variable");
                ex.printStackTrace();
                System.exit(-1);
            }
        }

        if (udpPort < 1) {
            udpPort = cMsgNetworkConstants.nameServerBroadcastPort;
        }

        // port #'s < 1024 are reserved
        if (udpPort < 1024) {
            System.out.println("\nPort number must be > 1023");
            System.exit(-1);
        }

        // At this point, bind to the TCP listening port. If that isn't possible, throw
        // an exception.
        try {
            serverChannel = ServerSocketChannel.open();
        }
        catch (IOException ex) {
            System.out.println("Exiting Server: cannot open a listening socket");
            ex.printStackTrace();
            System.exit(-1);
        }

        ServerSocket listeningSocket = serverChannel.socket();

        try {
            listeningSocket.bind(new InetSocketAddress(port));
        }
        catch (IOException ex) {
            System.out.println("TCP port number " + port + " in use.");
            ex.printStackTrace();
            System.exit(-1);
        }

        this.port = port;

        // Create a UDP socket for accepting broadcasts from cMsg clients
        try {
            // create socket to receive at all interfaces
            broadcastSocket = new DatagramSocket(udpPort);
            broadcastSocket.setReceiveBufferSize(65535);
//System.out.println("Created UDP broadcast listening socket at port " + udpPort);
        }
        catch (SocketException e) {
            System.out.println("UDP port number " + udpPort + " in use.");
            e.printStackTrace();
            System.exit(-1);
        }
        broadcastPort = udpPort;

        // record our own name

        try {
            serverName = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
        }
        serverName = serverName + ":" + port;
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage: java [-Dport=<tcp listening port>]\n"+
                             "            [-Dudp=<udp listening port>]\n" +
                             "            [-DsubdomainName=<className>]\n" +
                             "            [-Dserver=<hostname:serverport>]\n" +
                             "            [-Ddebug=<level>]\n" +
                             "            [-Dtimeorder]\n" +
                             "            [-Dstandalone]\n" +
                             "            [-Dpassword=<password>]\n" +
                             "            [-Dcloudpassword=<password>]  cMsgNameServer\n");
        System.out.println("       port is the TCP port this server listens on");
        System.out.println("       udp  is the UDP port this server listens on for broadcasts");
        System.out.println("       subdomainName  is the name of a subdomain and className is the");
        System.out.println("                      name of the java class used to implement the subdomain");
        System.out.println("       server         hostname is the name of another host on which a cMsg");
        System.out.println("                      server is running whose cMsg subdomain you want to join");
        System.out.println("                      and serverport is that server's port");
        System.out.println("       debug level has acceptable values of:");
        System.out.println("               info   for full output");
        System.out.println("               warn   for severity of warning or greater");
        System.out.println("               error  for severity of error or greater");
        System.out.println("               severe for severity of \"cannot go on\"");
        System.out.println("               none   for no debug output (default)");
        System.out.println("       timeorder      means messages handled in order received");
        System.out.println("       standalone     means no other servers may connect or vice versa");
        System.out.println("       password       is used to block clients without this password in their UDL's");
        System.out.println("       cloudpassword  is used to join a password-protected cloud or to allow");
        System.out.println("                      servers with this password to join this cloud");
        System.out.println();
    }


    /** This method prints out the sizes of all objects which store other objects. */
    public void printSizes() {
        System.out.println("     bridges        = " + bridges.size());
        System.out.println("     nameservers    = " + nameServers.size());
        System.out.println("     domainservers  = " + domainServers.size());
        System.out.println("     subscriptions  = " + subscriptions.size());
        System.out.println("     handlerThreads = " + handlerThreads.size());

        System.out.println();

        for (cMsgServerBridge b : bridges.values()) {
            System.out.println("       bridge " + b.serverName + ":");
            b.printSizes();
        }

        if (bridges.size() > 0) {
            System.out.println();
        }
    }


    /** Run as a stand-alone application. */
    public static void main(String[] args) {

        int debug = cMsgConstants.debugNone;
        int port = 0, udpPort = 0;
        boolean timeOrdered   = false;
        boolean standAlone    = false;
        String serverToJoin   = null;
        String cloudPassword  = null;
        String clientPassword = null;

        if (args.length > 0) {
            usage();
            System.exit(-1);
        }

        // First check to see if debug level, port number, or timeordering
        // was set on the command line. This can be done, while ignoring case,
        // by scanning through all the properties.
        for (Iterator i = System.getProperties().keySet().iterator(); i.hasNext();) {
            String s = (String) i.next();

            if (s.contains(".")) {
                continue;
            }

            if (s.equalsIgnoreCase("port")) {
                try {
                    port = Integer.parseInt(System.getProperty(s));
                }
                catch (NumberFormatException e) {
                    System.out.println("\nBad port number specified");
                    usage();
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            if (s.equalsIgnoreCase("udp")) {
                try {
                    udpPort = Integer.parseInt(System.getProperty(s));
                }
                catch (NumberFormatException e) {
                    System.out.println("\nBad port number specified");
                    usage();
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            else if (s.equalsIgnoreCase("debug")) {
                String arg = System.getProperty(s);

                if (arg.equalsIgnoreCase("info")) {
                    debug = cMsgConstants.debugInfo;
                }
                else if (arg.equalsIgnoreCase("warn")) {
                    debug = cMsgConstants.debugWarn;
                }
                else if (arg.equalsIgnoreCase("error")) {
                    debug = cMsgConstants.debugError;
                }
                else if (arg.equalsIgnoreCase("severe")) {
                    debug = cMsgConstants.debugSevere;
                }
                else if (arg.equalsIgnoreCase("none")) {
                    debug = cMsgConstants.debugNone;
                }
                else {
                    System.out.println("\nBad debug value");
                    usage();
                    System.exit(-1);
                }
            }
            else if (s.equalsIgnoreCase("server")) {
                serverToJoin = System.getProperty(s);
            }
            else if (s.equalsIgnoreCase("timeorder")) {
                timeOrdered = true;
            }
            else if (s.equalsIgnoreCase("standalone")) {
                standAlone = true;
            }
            else if (s.equalsIgnoreCase("cloudpassword")) {
                cloudPassword = System.getProperty(s);
            }
            else if (s.equalsIgnoreCase("password")) {
                clientPassword = System.getProperty(s);
            }
        }

        if (standAlone) {
            serverToJoin = null;
        }

        // create server object
        cMsgNameServer server = new cMsgNameServer(port, udpPort, timeOrdered, standAlone,
                                                   clientPassword, cloudPassword, debug);

        // start server
        server.startServer(serverToJoin);
    }


    /**
     * Method to start up this server and join the cMsg server cloud that the
     * argument, serverToJoin, is a part of. If the argument is null, this
     * server is the nucleas of a new server cloud.
     *
     * @param serverToJoin server whose cloud this server is to be joined to
     */
    public void startServer(String serverToJoin) {
        // start this server
        start();
//System.out.println("startServer; IN");
        // Create a bridge to another server (if specified) which
        // will also generate a connection to this server from that
        // server in response.
        if (serverToJoin != null) {
            // first make sure the given server name is of the right format (host:port)
            try {
                serverToJoin = cMsgMessageMatcher.constructServerName(serverToJoin);
            }
            catch (cMsgException e) {
                System.out.println("Name of server to join not in \"host:port\" format");
                System.exit(-1);
            }

            new cMsgServerCloudJoiner(this, getPort(), serverToJoin, debug);
        }
        else {
            // if we're not joining a cloud, then we're by definition the nucleas of one
            cloudStatus = INCLOUD;
            // allow client connections
            allowConnectionsSignal.countDown();
        }

        // start UDP listening thread
//System.out.println("Start Broadcast thd on port " );
        broadcastThread = new cMsgBroadcastListeningThread(port, broadcastSocket, clientPassword, debug);
        broadcastThread.start();

        // Start thread to gather monitor info
        MonitorDataThread monitorThread = new MonitorDataThread();
        monitorThread.start();
//System.out.println("startServer; Done");
    }


    /**
     * Method to gracefully shutdown all threads associated with this server
     * and to clean up.
     */
    synchronized void shutdown() {
        // stop cloud joiners
        cloudLock();

        // Shutdown this object's listening thread
        setKillAllThreads(true);

        // Shutdown UDP listening thread
        broadcastThread.killThread();

        // Shutdown all domain servers
        for (cMsgDomainServer server : domainServers.keySet()) {
            // need to shutdown this domain server
            if (server.calledShutdown.compareAndSet(false, true)) {
//System.out.println("DS shutdown to be run by NameServer");
                server.shutdown();
            }
        }

        cloudUnlock();

        bridges.clear();
        nameServers.clear();
        handlerThreads.clear();
        domainServers.clear();
        subscriptions.clear();
    }


    /**
     * This method creates a particular subdomain's client handler object.
     *
     * @param subdomain subdomain for which to create client handler
     * @return client handler object
     * @throws cMsgException if no class was found or class could not be instantiated or
     *                       accessed
     */
    static private cMsgSubdomainInterface createClientHandler(String subdomain)
            throws cMsgException {

        // Object to handle clients' inputs
        cMsgSubdomainInterface clientHandler;

        String clientHandlerClass = null;

        // no one can mess with (substitute another class for) the cMsg subdomain
        if (subdomain.equalsIgnoreCase("cMsg")) {
            clientHandlerClass = "org.jlab.coda.cMsg.subdomains.cMsg";
        }

        // Check to see if handler class name was set on the command line.
        // Do this by scanning through all the properties.
        if (clientHandlerClass == null) {
            for (Iterator i = System.getProperties().keySet().iterator(); i.hasNext();) {
                String s = (String) i.next();
                if (s.contains(".")) {
                    continue;
                }
                if (s.equalsIgnoreCase(subdomain)) {
                    clientHandlerClass = System.getProperty(s);
                    break;
                }
            }
        }

        // If it wasn't given on the command line,
        // check the appropriate environmental variable.
        if (clientHandlerClass == null) {
            clientHandlerClass = System.getenv("CMSG_HANDLER");
        }

        // If there is still no handler class look in predefined classes.
        if (clientHandlerClass == null) {
            if (subdomain.equalsIgnoreCase("CA")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.CA";
            }
            else if (subdomain.equalsIgnoreCase("Database")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.database";
            }
            else if (subdomain.equalsIgnoreCase("LogFile")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.LogFile";
            }
            else if (subdomain.equalsIgnoreCase("Queue")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.queue";
            }
            else if (subdomain.equalsIgnoreCase("SmartSockets")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.smartsockets";
            }
            else if (subdomain.equalsIgnoreCase("TcpServer")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.tcpserver";
            }
            else if (subdomain.equalsIgnoreCase("FileQueue")) {
                clientHandlerClass = "org.jlab.coda.cMsg.subdomains.FileQueue";
            }
        }

        // all options are exhaused, throw error
        if (clientHandlerClass == null) {
            cMsgException ex = new cMsgException("no handler class found");
            ex.setReturnCode(cMsgConstants.errorNoClassFound);
            throw ex;
        }


        // Get handler class name and create handler object
        try {
            clientHandler = (cMsgSubdomainInterface) (Class.forName(clientHandlerClass).newInstance());
        }
        catch (InstantiationException e) {
            cMsgException ex = new cMsgException("cannot instantiate "+ clientHandlerClass +
                                                 " class");
            ex.setReturnCode(cMsgConstants.error);
            throw ex;
        }
        catch (IllegalAccessException e) {
            cMsgException ex = new cMsgException("cannot access "+ clientHandlerClass +
                                                 " class");
            ex.setReturnCode(cMsgConstants.error);
            throw ex;
        }
        catch (ClassNotFoundException e) {
            cMsgException ex = new cMsgException("no handler class found");
            ex.setReturnCode(cMsgConstants.errorNoClassFound);
            throw ex;
        }

        return clientHandler;
    }


    /** This method is executed as a thread. */
    public void run() {
        if (debug >= cMsgConstants.debugInfo) {
            System.out.println(">> NS: Running Name Server at " + (new Date()) );
        }

        Selector selector = null;

        try {
            // get things ready for a select call
            selector = Selector.open();

            // set nonblocking mode for the listening socket
            serverChannel.configureBlocking(false);

            // register the channel with the selector for accepts
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            // Tell whoever is waiting for this server to start, that
            // it has now started. Actually there is a slight race
            // condition as it is not actually started until the select
            // statement below is executed. But it'll be OK since the thread
            // which is waiting must first create a bridge to another
            // server who then must make a reciprocal connection to this
            // server (right here as a matter of fact).
            listeningThreadStartedSignal.countDown();

            while (true) {
                // 3 second timeout
                int n = selector.select(3000);

                // if no channels (sockets) are ready, listen some more
                if (n == 0) {
                    // but first check to see if we've been commanded to die
                    if (getKillAllThreads()) {
                        return;
                    }
                    continue;
                }

                // get an iterator of selected keys (ready sockets)
                Iterator it = selector.selectedKeys().iterator();

                // look at each key
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();

                    // is this a new connection coming in?
                    if (key.isValid() && key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        // accept the connection from the client
                        SocketChannel channel = server.accept();

                        // set socket options
                        Socket socket = channel.socket();
                        // Set tcpNoDelay so no packets are delayed
                        socket.setTcpNoDelay(true);
                        // set recv buffer size
                        socket.setReceiveBufferSize(65535);

                        // start up client handling thread & store reference
                        handlerThreads.add(new ClientHandler(channel));
//System.out.println("handlerThd size = " + handlerThreads.size());

                        if (debug >= cMsgConstants.debugInfo) {
                            System.out.println(">> NS: new connection");
                        }
                    }
                    // remove key from selected set since it's been handled
                    it.remove();
                }
            }
        }
        catch (IOException ex) {
            if (debug >= cMsgConstants.debugError) {
                //ex.printStackTrace();
            }
        }
        finally {
            try {serverChannel.close();} catch (IOException e) {}
            try {selector.close();}      catch (IOException e) {}
        }

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("\n>> NS: Quitting Name Server");
        }
    }


    /** Class to handle a socket connection to the client of which there may be many. */
    private class ClientHandler extends Thread {
        /** Type of domain this is. */
        private String domain = "cMsg";

        /** Socket channel to client. */
        SocketChannel channel;

        /** Buffered input communication streams for efficiency. */
        DataInputStream  in;
        /** Buffered output communication streams for efficiency. */
        DataOutputStream out;

        /**
         * Constructor.
         * @param channel socket channel to client
         */
        ClientHandler(SocketChannel channel) {
            this.channel = channel;
            this.start();
        }


        /**
          * This method handles all communication between a cMsg user
          * and this name server for that domain.
          * Note to those who would make changes in the protocol, keep the first three
          * ints the same. That way the server can reliably check for mismatched versions.
          */
         public void run() {
//System.out.println("clientHandler; IN");

            try {
                // buffered communication streams for efficiency
                in  = new DataInputStream(new BufferedInputStream(channel.socket().getInputStream(), 65536));
                out = new DataOutputStream(new BufferedOutputStream(channel.socket().getOutputStream(), 2048));
                // message id
                int msgId = in.readInt();
                // major version
                int version = in.readInt();
                // minor version
                int minorVersion = in.readInt();

                // immediately check if this domain server is different cMsg version than client
                if (version != cMsgConstants.version) {
                    // send error to client
                    out.writeInt(cMsgConstants.errorDifferentVersion);
                    // send error string to client
                    String s = "version mismatch";
                    out.writeInt(s.length());
                    try { out.write(s.getBytes("US-ASCII")); }
                    catch (UnsupportedEncodingException e) {}

                    out.flush();
                    return;
                }

                switch(msgId) {
                    case cMsgConstants.msgConnectRequest:
                        handleClient();
                        break;
                    case cMsgConstants.msgServerConnectRequest:
                        handleServer();
                        break;
                    default:
                        if (debug >= cMsgConstants.debugError) {
                            System.out.println("cMsg name server: can't understand your message -> " + msgId);
                        }
                        break;
                }
            }
            catch (IOException ex) {
                if (debug >= cMsgConstants.debugError) {
                    System.out.println("cMsgNameServer's Client thread: IO error in talking to client");
                }
            }
            finally {
                handlerThreads.remove(this);
                // we are done with the channel
                try {in.close();}      catch (IOException ex) {}
                try {out.close();}     catch (IOException ex) {}
                try {channel.close();} catch (IOException ex) {}
            }
         }


        /**
         * This method registers a client with this name server. This method passes on the
         * registration function to the client handler object. Part of the information
         * in the cMsgClientInfo object is the subdomain which specifies the type of
         * client handler object needed. This handler object gets the UDL remainder
         * (also part of the cMsgClientInfo object) which it can parse as it sees fit.
         * <p/>
         * The subdomain should have a class by that name that can be loaded and used
         * as the client handler object. The classes corresponding to these handlers
         * can be passed to the name server on the command line as in the following:
         * java cMsgNameServer -DmySubdomain=myCmsgClientHandlerClass
         *
         * @param info object containing information about the client
         * @return created subdomain handler object
         * @throws IOException If a client could not be registered in the cMsg server cloud
         * @throws cMsgException If a domain server could not be started for the client
         */
        synchronized private cMsgSubdomainInterface registerClient(cMsgClientInfo info)
                throws cMsgException, IOException {

            // create a subdomain handler object
            cMsgSubdomainInterface subdomainHandler = createClientHandler(info.getSubdomain());

            // The first thing we do is pass the UDL remainder to the handler.
            // In the cMsg subdomain, it is parsed to find the namespace which
            // is stored in the subdomainHandler object.
            subdomainHandler.setUDLRemainder(info.getUDLremainder());

            // The next thing to do is create an object enabling the handler
            // to communicate with only this client in this cMsg domain.
            cMsgMessageDeliverer deliverer;
            try {
                deliverer = new cMsgMessageDeliverer(info);
            }
            catch (IOException e) {
                cMsgException ex = new cMsgException("socket communication error");
                ex.setReturnCode(cMsgConstants.errorNetwork);
                throw ex;
            }
            // The cMsg subdomain does not use this reference to the deliverer
            // to communicate, but other subdomains do.
            subdomainHandler.setMessageDeliverer(deliverer);

            // Also store deliverer object in client info object.
            // The cMsg subdomain uses this reference to communicate.
            info.setDeliverer(deliverer);

            // Register client with the subdomain.
            // If we're in the cMsg subdomain ...
            if (subdomainHandler instanceof org.jlab.coda.cMsg.subdomains.cMsg) {
                // Wait until clients are allowed to connect (i.e. this server
                // has joined the cloud of cMsg subdomain name servers).
                try {
                    // If we've timed out ...
                    if (!allowConnectionsSignal.await(5L, TimeUnit.SECONDS)) {
                        cMsgException ex = new cMsgException("nameserver not in server cloud - timeout error");
                        ex.setReturnCode(cMsgConstants.errorTimeout);
                        throw ex;
                    }
                }
                catch (InterruptedException e) {
                    cMsgException ex = new cMsgException("interrupted while waiting for name server to join server cloud");
                    ex.setReturnCode(cMsgConstants.error);
                    throw ex;
                }

                // We need to do a global registration spanning
                // all cMsg domain servers in this cloud.
                cMsgSubdomainRegistration(info, subdomainHandler);
            }
            else {
                subdomainHandler.registerClient(info);
            }

            // Create a domain server thread, and get back its host & port
            cMsgDomainServer server = new cMsgDomainServer(cMsgNameServer.this, subdomainHandler, info,
                                                           cMsgNetworkConstants.domainServerStartingPort,
                                                           timeOrdered);

            // kill this thread too if name server thread quits
            server.setDaemon(true);
            server.start();
            // store ref to this domain server
            domainServers.put(server, "");

            return subdomainHandler;
        }


        /**
         * This method registers a server "client" with this name server. This method passes on the
         * registration function to the client handler object. These server clients exist only in
         * the cMsg subdomain.
         *
         * @param info object containing information about the client
         * @return created subdomain handler object
         * @throws cMsgException If a domain server could not be started for the client
         */
        synchronized private cMsgSubdomainInterface registerServer(cMsgClientInfo info) throws cMsgException {
            // Create instance of cMsg subdomain handler. We need to access
            // methods not in the cMsgSubdomainInterface so do a cast.
            org.jlab.coda.cMsg.subdomains.cMsg subdomainHandler =
                    (org.jlab.coda.cMsg.subdomains.cMsg) createClientHandler("cMsg");

            // The first thing we do is set the namespace to the default namespace.
            // Server clients don't use the namespace so it doesn't matter.
            subdomainHandler.setUDLRemainder(null);

            // The next thing to do is create an object enabling the handler
            // to communicate with only this client in this cMsg domain.
            cMsgMessageDeliverer deliverer;
            try {
                deliverer = new cMsgMessageDeliverer(info);
            }
            catch (IOException e) {
                cMsgException ex = new cMsgException("socket communication error");
                ex.setReturnCode(cMsgConstants.errorNetwork);
                throw ex;
            }

            // Store deliverer object in client info object.
            // The cMsg subdomain uses this reference to communicate.
            info.setDeliverer(deliverer);

            // Do a local registration with handler object.
            // Registrations with other servers is done when bridge objects contact them.
            subdomainHandler.registerServer(info);

            // Create a domain server thread, and get back its host & port
            cMsgDomainServer server = new cMsgDomainServer(cMsgNameServer.this, subdomainHandler, info,
                                                           cMsgNetworkConstants.domainServerStartingPort,
                                                           timeOrdered);

            // kill this thread too if name server thread quits
            server.setDaemon(true);
            server.start();
            // store ref to this domain server
            domainServers.put(server, "");

            return subdomainHandler;
        }


        /**
         * This method registers regular (non-server) clients in the cMsg subdomain.
         * Registration is more complicated in this domain than other domains as it
         * must contact all other cMsg servers to which it has a bridge. To ensure
         * global uniqueness of a client name, locks must be taken out on all servers
         * so that no other potential client may connect during this time.
         *
         * @param info             object containing information about the client
         * @param subdomainHandler subdomain handler object
         * @throws cMsgException if a registration lock on another server cannot be grabbed within
         *                       1/2 second, or the client trying to connect here does not have a
         *                       unique name
         * @throws IOException   if trouble communicating with other servers
         */
        private void cMsgSubdomainRegistration(cMsgClientInfo info, cMsgSubdomainInterface subdomainHandler)
                throws cMsgException, IOException {

            org.jlab.coda.cMsg.subdomains.cMsg cMsgSubdomainHandler =
                    (org.jlab.coda.cMsg.subdomains.cMsg) subdomainHandler;

//System.out.println(">> NS: IN subdomainRegistration");
            // If there are no connections to other servers (bridges), do local registration only
            if (bridges.size() < 1) {
//System.out.println(">> NS: no bridges so do regular registration of client");
                subdomainHandler.registerClient(info);
                return;
            }

            int     grabLockTries;
            boolean gotCloudLock  = false;
            boolean gotRegistrationLock = false;
            boolean registrationSuccessful = false;
            int maxNumberOfTrys=3, numberOfTrys=0;
            LinkedList<cMsgServerBridge> lockedServers = new LinkedList<cMsgServerBridge>();

            startOver:

                while (numberOfTrys++ < maxNumberOfTrys) {
//System.out.println(">> NS: startOver");
                    lockedServers.clear();

// BUG BUG send timeout length ??
                    // We need to calculate the number of locks which constitute a
                    // majority of servers currently a part of the cloud. This can
                    // only be done once we have a lock of a cloud member (thereby
                    // preventing anyone else from joining).
                    grabLockTries = 0;

                    do {
//System.out.println(">> NS: grabLockTries = " + grabLockTries);
//System.out.println(">> NS: try local cloud lock");

                        // First, (since we are in the cloud now) we grab our own
                        // cloud lock so we stop cloud-joiners and check all cloud
                        // members' clients. Can also calculate a majority of cloud members.
// boolean locked = cloudLock.isLocked();
//System.out.println("local cloud locked = " + locked);
                        if (!gotCloudLock && cloudLock(500)) {
//System.out.println(">> NS: grabbed local cloud lock");
                            gotCloudLock = true;
                        }

                        // Second, Grab our own registration lock
//System.out.println(">> NS: try to grab this registration lock");
                        if (!gotRegistrationLock && cMsgSubdomainHandler.registrationLock(500)) {
//System.out.println(">> NS: grabbed registration lock");
                            gotRegistrationLock = true;
                        }

                        // Can't grab a/both locks, wait and try again (at most 3 times)
                        if (!gotCloudLock || !gotRegistrationLock) {
                            // if we've reached our limit of tries ...
                            if (++grabLockTries > 3) {
                                if (debug >= cMsgConstants.debugWarn) {
                                    System.out.println("    << JR: Failed to grab inital cloud lock");
                                }
                                // delay 1/2 sec
                                try {Thread.sleep(500);}
                                catch (InterruptedException e) {}
                                // take it from the top
//System.out.println(">> NS: continue to startOver");
                                continue startOver;
                            }

//System.out.println(">> NS: cannot grab a/both locks, wait .01 sec and try againr");
                            try {Thread.sleep(10);}
                            catch (InterruptedException e) {}
                        }
                    } while (!gotCloudLock || !gotRegistrationLock);

                    // Calculate the majority
                    int totalCloudMembers = 1; // we is first
                    for (cMsgServerBridge b : bridges.values()) {
                        if (b.getCloudStatus() == cMsgNameServer.INCLOUD) {
                            totalCloudMembers++;
                        }
                    }
                    int majority = totalCloudMembers / 2 + 1;
                    int numberOfLockedCloudMembers = 1;

                    // Try to get all of the in-cloud servers' registration locks
//System.out.println(">> NS: Try to get all of the in-cloud servers' registration locks");
                    do {
                        // Grab the locks of other servers
                        for (cMsgServerBridge bridge : bridges.values()) {

                            // If it's already locked or not in the cloud, skip it
                            if (lockedServers.contains(bridge) ||
                                bridge.getCloudStatus() != cMsgNameServer.INCLOUD) {
                                continue;
                            }

                            try {
                                // If sucessfull in locking remote server ...
//System.out.println(">> NS: Try to lock bridge to " + bridge.server);
                                if (bridge.registrationLock(200)) {
//System.out.println(">> NS: LOCKED IT!!");
                                    lockedServers.add(bridge);
                                    numberOfLockedCloudMembers++;
                                }
                                // else if cannot lock remote server, try next one
                                else {
//System.out.println(">> NS: CANNOT Lock it, so skip it");
                                }
                            }
                            // We're here if lock or unlock fails in its communication with server.
                            // If we cannot talk to the server, it's probably dead.
                            catch (IOException e) { }
                        }

                        //System.out.println(">> NS: FAILED TO LOCKED IT!!");

                        // If we have all the in-cloud locks we're done and can move on.
                        if (numberOfLockedCloudMembers >= totalCloudMembers) {
//System.out.println(">> NS: Got all Locks");
                            break;
                        }
                        // If we have a majority (but not all) in-cloud locks, try to get the rest.
                        else if (numberOfLockedCloudMembers >= majority) {
                            // Let other greedy lock-grabbers release theirs locks first
//System.out.println(">> NS: Get More Locks");
                            try {Thread.sleep(10);}
                            catch (InterruptedException e) {}
                        }
                        // If we do NOT have the majority of locks ...
                        else {
                            // release all locks
                            for (cMsgServerBridge b : lockedServers) {
                                try {b.registrationUnlock();}
                                catch (IOException e) {}
                            }
                            cMsgSubdomainHandler.registrationUnlock();
                            cloudUnlock();

                            // try to lock 'em again
                            gotCloudLock = false;
                            gotRegistrationLock = false;

                            // Wait for a random time initially between 10 & 300
                            // milliseconds which doubles each loop.
                            Random rand = new Random();
                            int milliSec = (int) ((10 + rand.nextInt(291))*(Math.pow(2., numberOfTrys-1.)));
                            try {Thread.sleep(milliSec);}
                            catch (InterruptedException e) {}

                            // start over
//System.out.println(">> NS: Drop locks and start over");
                            continue startOver;
                        }

                    } while (true);


                    try {
                        // Get the list of client names and namespaces from each connected server
                        // and compare to the name/namespace of the client trying to connect to
                        // this server. Only accept a unique name/namespace combo, else reject it.
                        String[] nameList;
                        for (cMsgServerBridge bridge : bridges.values()) {
                            nameList = bridge.getClientNamesAndNamespaces();
                            String name,ns;
                            for (int i=0; i < nameList.length; i+=2) {
                                name = nameList[i];
                                ns   = nameList[i+1];
                                if (name.equals(info.getName()) &&
                                        ns.equals(cMsgSubdomainHandler.getNamespace())) {
//System.out.println(">> NS: THIS MATCHES NAME OF CONNECTING CLIENT");
                                    cMsgException e = new cMsgException("client already exists");
                                    e.setReturnCode(cMsgConstants.errorAlreadyExists);
                                    throw e;
                                }
                            }

                        }

                        // FINALLY, REGISTER CLIENT!!!
                        subdomainHandler.registerClient(info);
                    }
                    finally {
                        // release the locks
                        for (cMsgServerBridge b : bridges.values()) {
                            try {b.registrationUnlock();}
                            catch (IOException e) {}
                        }
                        cMsgSubdomainHandler.registrationUnlock();
                        cloudUnlock();
                    }

//System.out.println(">> NS: registration is successful!\n\n");
                    registrationSuccessful = true;
                    break;
                }

                // If we could not register the client due to not being able to get the required locks ...
                if (!registrationSuccessful) {
                    // release the locks
                    for (cMsgServerBridge b : bridges.values()) {
                        try {b.registrationUnlock();}
                        catch (IOException e) {}
                    }

                    if (gotRegistrationLock) {
                        cMsgSubdomainHandler.registrationUnlock();
                    }

                    if (gotCloudLock) {
                        cloudUnlock();
                    }

                    System.out.println(">> NS: **********************************************************************");
                    System.out.println(">> NS: Cannot register the specified client, since cannot grab required locks");
                    System.out.println(">> NS: **********************************************************************");
                    cMsgException e = new cMsgException("cannot grab required locks");
                    e.setReturnCode(cMsgConstants.error);
                    throw e;
                }
        }


        /**
         * This method handles all communication between a regular (non-server) cMsg client
         * and this name server for that domain.
         *
         * @throws IOException if problems with socket communication
         */
        private void handleClient() throws IOException {
//System.out.println("handleClient: IN");
            // listening port of client
            int clientListeningPort = in.readInt();
            // length of password
            int lengthPassword = in.readInt();
            // length of domain type client is expecting to connect to
            int lengthDomainType = in.readInt();
            // length of subdomain type client is expecting to use
            int lengthSubdomainType = in.readInt();
            // length of UDL remainder to pass to subdomain handler
            int lengthUDLRemainder = in.readInt();
            // length of client's host name
            int lengthHost = in.readInt();
            // length of client's name
            int lengthName = in.readInt();
            // length of client's UDL
            int lengthUDL = in.readInt();
            // length of client's description
            int lengthDescription = in.readInt();

            // bytes expected
            int bytesToRead = lengthPassword + lengthDomainType + lengthSubdomainType +
                              lengthUDLRemainder + lengthHost + lengthName + lengthUDL +
                              lengthDescription;
//System.out.println("handleClient: bytesToRead = " + bytesToRead);
            int offset = 0;

            // read all string bytes
            byte[] bytes = new byte[bytesToRead];
            in.readFully(bytes, 0, bytesToRead);

            // read password
            String password = new String(bytes, offset, lengthPassword, "US-ASCII");
            offset += lengthPassword;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  password = " + password);
            }

            // read domain
            String domainType = new String(bytes, offset, lengthDomainType, "US-ASCII");
            offset += lengthDomainType;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  domain = " + domainType);
            }

            // read subdomain
            String subdomainType = new String(bytes, offset, lengthSubdomainType, "US-ASCII");
            offset += lengthSubdomainType;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  subdomain = " + subdomainType);
            }

            // Elliott wanted this printed out
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  server port = " + port);
            }

            // read UDL remainder
            String UDLRemainder = new String(bytes, offset, lengthUDLRemainder, "US-ASCII");
            offset += lengthUDLRemainder;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  remainder = " + UDLRemainder);
            }

            // read host
            String host = new String(bytes, offset, lengthHost, "US-ASCII");
            offset += lengthHost;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  host = " + host);
            }

            // read name
            String name = new String(bytes, offset, lengthName, "US-ASCII");
            offset += lengthName;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  client port = " + clientListeningPort);
                System.out.println("  name = " + name);
            }

            // read UDL
            String UDL = new String(bytes, offset, lengthUDL, "US-ASCII");
            offset += lengthUDL;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  UDL = " + UDL);
            }

            // read description
            String description = new String(bytes, offset, lengthDescription, "US-ASCII");
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println("  description = " + description);
            }

            // if this is not the domain of server the client is expecting, return an error
            if (!domainType.equalsIgnoreCase(this.domain)) {
                // send error to client
                out.writeInt(cMsgConstants.errorWrongDomainType);
                // send error string to client
                String s = "this server implements " + this.domain + " domain";
                out.writeInt(s.length());
                try {
                    out.write(s.getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {
                }

                out.flush();
                return;
            }

            // if the client does not provide the correct password if required, return an error
            if (clientPassword != null) {

                if (debug >= cMsgConstants.debugInfo) {
                    System.out.println("  local password = " + clientPassword);
                    System.out.println("  given password = " + password);
                }

                if (password == null ||
                        password.length() < 1 ||
                        !clientPassword.equals(password)) {

                    if (debug >= cMsgConstants.debugError) {
                        System.out.println("  wrong password sent");
                    }

                    // send error to client
                    out.writeInt(cMsgConstants.errorWrongPassword);
                    // send error string to client
                    String s = "wrong password given";
                    out.writeInt(s.length());
                    try {
                        out.write(s.getBytes("US-ASCII"));
                    }
                    catch (UnsupportedEncodingException e) {
                    }

                    out.flush();
                    return;
                }
            }

            // Try to register this client. If the cMsg system already has a
            // client by this name, it will fail.
            try {
                cMsgClientInfo info = new cMsgClientInfo(name, port,
                                                         clientListeningPort, host,
                                                         subdomainType, UDLRemainder,
                                                         UDL, description);
                if (debug >= cMsgConstants.debugInfo) {
                    System.out.println("name server try to register " + name);
                }

                cMsgSubdomainInterface handler = registerClient(info);

                // send ok back as acknowledgment
                out.writeInt(cMsgConstants.ok);

                // send back attributes of clientHandler class/object
                // 1 = has, 0 = don't have: send, subscribeAndGet, sendAndGet, subscribe, unsubscribe
                byte[] atts = new byte[7];
                atts[0] = handler.hasSend() ? (byte) 1 : (byte) 0;
                atts[1] = handler.hasSyncSend() ? (byte) 1 : (byte) 0;
                atts[2] = handler.hasSubscribeAndGet() ? (byte) 1 : (byte) 0;
                atts[3] = handler.hasSendAndGet() ? (byte) 1 : (byte) 0;
                atts[4] = handler.hasSubscribe() ? (byte) 1 : (byte) 0;
                atts[5] = handler.hasUnsubscribe() ? (byte) 1 : (byte) 0;
                atts[6] = handler.hasShutdown() ? (byte) 1 : (byte) 0;
                out.write(atts);

                // send cMsg domain host & port contact info back to client
                out.writeInt(info.getDomainPort());
                out.writeInt(info.getDomainUdpPort());
                out.writeInt(info.getDomainHost().length());
                try {
                    out.write(info.getDomainHost().getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {
                }

                out.flush();
            }
            catch (cMsgException ex) {
                // send int error code to client
                out.writeInt(ex.getReturnCode());
                // send error string to client
                out.writeInt(ex.getMessage().length());
                try {
                    out.write(ex.getMessage().getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {
                }
                out.flush();
            }
        }


        /**
         * This method handles all communication between a cMsg server
         * and this name server in the cMsg domain / cMsg subdomain.
         *
         * @throws IOException   if problems with socket communication
         */
        private void handleServer() throws IOException {
//System.out.println(">> NS: IN handleServer");

            // listening port of server client
            int clientListeningPort = in.readInt();
            // What relationship does the connecting server have to the server cloud?
            // Can be INCLOUD, NONCLOUD, or BECOMINGCLOUD.
            byte connectingCloudStatus = in.readByte();
            // Is connecting server originating connection or is this a reciprocal one?
            boolean isReciprocalConnection = in.readByte() == 0;
            // listening port of name server that client is a part of
            int nameServerListeningPort = in.readInt();
            // length of server client's host name
            int lengthHost = in.readInt();
            // length of server client's password
            int lengthPassword = in.readInt();

            // bytes expected
            int bytesToRead = lengthHost + lengthPassword;
            int offset = 0;

            // read all string bytes
            byte[] bytes = new byte[bytesToRead];
            in.readFully(bytes, 0, bytesToRead);

            // read host
            String host = new String(bytes, offset, lengthHost, "US-ASCII");
            offset += lengthHost;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println(">> NS: host = " + host);
            }

            // read password
            String password = new String(bytes, offset, lengthPassword, "US-ASCII");
            offset += lengthPassword;
            if (debug >= cMsgConstants.debugInfo) {
                System.out.println(">> NS: given cloud password = " + password);
            }

            // Make this client's name = "host:port"
            String name = host + ":" + nameServerListeningPort;
//System.out.println(">> NS: host name = " + host + ", client hame = " + name);

            // At this point grab the "cloud" lock so no other servers
            // can connect simultaneously
            cloudLock.lock();

            try {

                cMsgClientInfo info;

                try {
                    // First, check to see if password matches.
//System.out.println("local cloudpassword = " + cloudPassword +
//                   ", given password = " + password);
                    if (cloudPassword != null && !cloudPassword.equals(password)) {
//System.out.println(">> NS: PASSWORDS DO NOT MATCH");
                        cMsgException ex = new cMsgException("wrong password - connection refused");
                        ex.setReturnCode(cMsgConstants.errorWrongPassword);
                        throw ex;
                    }

                    // Second, check to see if this is a stand alone server.
                    if (standAlone) {
                        cMsgException ex = new cMsgException("stand alone server - no server connections allowed");
                        ex.setReturnCode(cMsgConstants.error);
                        throw ex;
                    }

                    // Third, check to see if this server is already connected.
                    if (nameServers.contains(name)) {
//System.out.println(">> NS: ALREADY CONNECTED TO " + name);
                        cMsgException ex = new cMsgException("already connected");
                        ex.setReturnCode(cMsgConstants.errorAlreadyExists);
                        throw ex;
                    }

                    // Allow other servers to connect to this one if:
                    //   (1) this server is a cloud member, or
                    //   (2) this server is not a cloud member and it is a
                    //       reciprocal connection from a cloud member, or
                    //   (3) this server is becoming a cloud member and it is an
                    //       original or reciprocal connection from a another server
                    //       that is simultaneously trying to become a cloud member.
                    boolean allowConnection = false;

                    if (cloudStatus == INCLOUD) {
                        allowConnection = true;
                        // If I'm in the cloud, the connecting server cannot be making
                        // a reciprocal connection since a reciprocal connection
                        // is the only kind I can make.
                        isReciprocalConnection = false;
                    }
                    else if (connectingCloudStatus == INCLOUD) {
                        allowConnection = true;
                        // If the connecting server is a cloud member and I am not,
                        // it must be making a reciprocal connection since that's the
                        // only kind of connection a cloud member can make.
                        isReciprocalConnection = true;
                    }
                    else if (cloudStatus == BECOMINGCLOUD && connectingCloudStatus == BECOMINGCLOUD) {
                        allowConnection = true;
                    }
                    else {
                        // If we've reached here, then it's a connection from a noncloud server
                        // trying to connect to a noncloud/becomingcloud server or vice versa which
                        // is forbidden. This connection will not be allowed to proceed until this
                        // server becomes part of the cloud.
                    }

                    if (!allowConnection) {
                        try {
                            // Wait here up to 5 sec if the connecting server is not allowed to connect.
//System.out.println(">> NS: Connection NOT allowed so wait up to 5 sec for connection");
                            if (!allowConnectionsSignal.await(5L, TimeUnit.SECONDS)) {
                                cMsgException ex = new cMsgException("nameserver not in server cloud - timeout error");
                                ex.setReturnCode(cMsgConstants.errorTimeout);
                                throw ex;
                            }
                        }
                        catch (InterruptedException e) {
                            cMsgException ex = new cMsgException("interrupted while waiting for name server to join server cloud");
                            ex.setReturnCode(cMsgConstants.error);
                            throw ex;
                        }
                    }

                    // Register this client. If this cMsg server already has a
                    // client by this name (it never should), it will fail.
                    info = new cMsgClientInfo(name, nameServerListeningPort,
                                                             clientListeningPort, host);

                    // register the server (store info in cMsg subdomain class)
                    if (debug >= cMsgConstants.debugInfo) {
                        System.out.println(">> NS: try to register " + name);
                    }
//System.out.println(">> NS: Register connecting server");
                    registerServer(info);
                }
                catch (cMsgException ex) {
                    // send int error code to client
                    out.writeInt(ex.getReturnCode());
                    // send error string to client
                    out.writeInt(ex.getMessage().length());
                    try {
                        out.write(ex.getMessage().getBytes("US-ASCII"));
                    }
                    catch (UnsupportedEncodingException e) {
                    }

                    out.flush();
                    return;
                }

                // send ok back as acknowledgment
                out.writeInt(cMsgConstants.ok);

                // send cMsg domain host & port contact info back to client
                out.writeInt(info.getDomainPort());
                out.writeInt(info.getDomainHost().length());
                try {
                    out.write(info.getDomainHost().getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {
                }

                try {
                    // If this is not a reciprocal connection, we need to make one.
                    if (!isReciprocalConnection) {
//System.out.println(">> NS: Create reciprocal bridge to " + name);
                        cMsgServerBridge b = new cMsgServerBridge(cMsgNameServer.this, name, port);
                        // connect as reciprocal (originating = false)
                        b.connect(false, cloudPassword);
//System.out.println(">> NS: Add " + name + " to bridges");
                        bridges.put(name, b);
                        // If status was NONCLOUD, it is now BECOMINGCLOUD,
                        // and if we're here it is not INCLOUD.
                        b.setCloudStatus(cMsgNameServer.BECOMINGCLOUD);
//System.out.println(">> NS: set bridge (" + b + ") status to " + b.getCloudStatus());
                    }
                    // If this is a reciprocal connection, look up bridge for
                    // connecting server and change its cloud status.
                    else {
//System.out.println(">> NS: Do NOT create reciprocal bridge to " + name);
                        // We cannot look up the bridge in "bridges" as it is still
                        // in the middle of being created and has not been added
                        // to that collection yet. We have saved a reference, however.
                        cMsgServerBridge b = bridgeBeingCreated;
                        if (b != null) {
                            b.setCloudStatus(connectingCloudStatus);
//System.out.println(">> NS: set bridge (" + b + ") status to " + b.getCloudStatus());
                        }
                        else {
                            System.out.println(">> NS: bridge  = " + b);
                        }
                    }
                }
                catch (cMsgException e) {
                    e.printStackTrace();
                }

                // If I'm in the cloud, send a list of cMsg servers I'm already connected to.
                // If there are no connections to other servers, we can forget it.
                // Do this only if not a reciprocal connection.
                if ((cloudStatus == INCLOUD) &&
                    (nameServers.size() > 0) &&
                     (!isReciprocalConnection)) {
                    // send number of servers I'm connected to
//System.out.println(">> NS: Tell connecting server " + nameServers.size() + " servers are connected to us:");
                    out.writeInt(nameServers.size());

                    // for each cloud server, send name length, then name
                    synchronized (nameServers) {
                        for (String serverName : nameServers) {
                            System.out.println(">>    - " + serverName);
                            out.writeInt(serverName.length());
                            out.write(serverName.getBytes("US-ASCII"));
                        }
                    }
                }
                else {
                    // no servers are connected
//System.out.println(">> NS: Tell connecting server no one is connected to us");
                    out.writeInt(0);
                }
                out.flush();

                // store this connection in hashtable
                if (connectingCloudStatus == NONCLOUD) {
                    // If we're this far then we're in the cloud and the connecting
                    // server is trying to become part of it.
                    connectingCloudStatus = BECOMINGCLOUD;
                }
//System.out.println(">> NS: Add " + name + " to nameServers with status = " + connectingCloudStatus);
                nameServers.add(name);

            }
            finally {
                // At this point release the "cloud" lock
                cloudLock.unlock();
            }
//System.out.println("");
        }

    }


    /** Class to gather all monitor data into 1 place. */
    private class MonitorDataThread extends Thread {

        /** This method is executed as a thread. */
        public void run() {

            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL,DateFormat.FULL);

            while (true) {
                if (killAllThreads) {
                    break;
                }
                StringBuilder xml = new StringBuilder(1000);

                // Gather all the xml monitor data into 1 place for final
                // distribution to clients asking for it in XML format.
                xml.append("\n  <server  name=\"");
                xml.append(serverName);
                xml.append("\">\n");
                String indent1 = "      ";

                for (cMsgDomainServer ds : domainServers.keySet()) {
//System.out.println("  ns looking at client " + ds.info.getName());
                    // Skip other servers' bridges to us,
                    // they're not real clients.
                    if (ds.info.isServer()) {
//System.out.println("Skipping other server's bridge client");
                        continue;
                    }

                    // subdomain
                    String sd = ds.info.getSubdomain();

                    xml.append("\n    <client  name=\"");
                    xml.append(ds.info.getName());
                    xml.append("\"  subdomain=\"");
                    xml.append(sd);
                    xml.append("\">\n");

                    // time created
                    xml.append(indent1);
                    xml.append("<timeConnected>");
                    xml.append(dateFormat.format(ds.monData.birthday));
                    xml.append("</timeConnected>\n");

                    // namespace
                    String ns = ds.info.getNamespace();
                    if (ns != null) {
                        // get rid of beginning slash (add by subdomain)
                        ns = ns.substring(1, ns.length());
                        xml.append(indent1);
                        xml.append("<namespace>");
                        xml.append(ns);
                        xml.append("</namespace>\n");
                    }

                    // list subscriptions sent from client (cmsg subdomain only)
                    if (sd != null && sd.equalsIgnoreCase("cmsg") && ds.monData.monXML != null) {
                        xml.append(indent1);
                        xml.append("<sendStats");

                        xml.append("  tcpSends=\"");
                        xml.append(ds.monData.clientTcpSends);

                        xml.append("\"  udpSends=\"");
                        xml.append(ds.monData.clientUdpSends);

                        xml.append("\"  syncSends=\"");
                        xml.append(ds.monData.clientSyncSends);

                        xml.append("\"  sendAndGets=\"");
                        xml.append(ds.monData.clientSendAndGets);
                        xml.append("\" />\n");
                        
                        xml.append(indent1);
                        xml.append("<subStats");

                        xml.append("   subscribes=\"");
                        xml.append(ds.monData.clientSubscribes);

                        xml.append("\"  unsubscribes=\"");
                        xml.append(ds.monData.clientUnsubscribes);

                        xml.append("\"  subAndGets=\"");
                        xml.append(ds.monData.clientSubAndGets);
                        xml.append("\" />\n");

                        // add subscription & callback stuff here (from client)
//System.out.println("  ns adding from client: \n" + ds.monData.monXML);
                        xml.append(ds.monData.monXML);
                    }
                    else {
                        xml.append(indent1);
                        xml.append("<sendStats");

                        xml.append("  tcpSends=\"");
                        xml.append(ds.monData.tcpSends);

                        xml.append("\"  udpSends=\"");
                        xml.append(ds.monData.udpSends);

                        xml.append("\"  syncSends=\"");
                        xml.append(ds.monData.syncSends);

                        xml.append("\"  sendAndGets=\"");
                        xml.append(ds.monData.sendAndGets);
                        xml.append("\" />\n");

                        xml.append(indent1);
                        xml.append("<subStats");

                        xml.append("  subscribes=\"");
                        xml.append(ds.monData.subscribes);

                        xml.append("\"  unsubscribes=\"");
                        xml.append(ds.monData.unsubscribes);

                        xml.append("\"  subAndGets=\"");
                        xml.append(ds.monData.subAndGets);
                        xml.append("\" />\n");
                    }
                    xml.append("    </client>\n");
                }
                xml.append("\n  </server>\n\n");

                // store this as an xml string describing local server only
                nsMonitorXML = xml.toString();

                xml.insert(0,"<cMsgMonitorData  domain=\"cmsg\">\n");
              //  xml.insert(0, "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n\n");
                xml.insert(0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n");

                // allow no changes to "bridges" while iterating
                synchronized (bridges) {
                    // foreach bridge ...
                    for (cMsgServerBridge b : bridges.values()) {
                        xml.append(b.client.monitorXML);
                    }
                }
                xml.append("</cMsgMonitorData>\n");

                // store this as an xml string describing all monitor data
                fullMonitorXML = xml.toString();
//System.out.println("  fullMonitorXML = \n" + fullMonitorXML);

                if (killAllThreads) {
                    break;
                }

                try { Thread.sleep(2000); }
                catch (InterruptedException e) { }
            }

        }
    }


}


