/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 17-Nov-2004, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;


import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.io.*;

/**
 * This class is instantiated by a client in order to connect to a cMsg server.
 * The instantiated object will be the main means by which the client will
 * interact with cMsg.</p>
 * This class acts as a multiplexor to direct a cMsg client to the proper
 * subdomain based on the UDL given.
 */
public class cMsg {
    /** Level of debug output for this class. */
    int debug = cMsgConstants.debugError;

    /** String containing the whole UDL. */
    private String UDL;

    /** String containing the client's name. */
    private String name;

    /** String containing the client's description. */
    private String description;

    /** String containing the remainder part of the UDL. */
    private String UDLremainder;

    /** String containing the domain part of the UDL. */
    private String domain;

    /** A specific implementation of the cMsg API. */
    private cMsgDomainInterface connection;


    /** Constructor. */
    private cMsg() { }


    /**
     * Constructor which automatically tries to connect to the name server specified.
     *
     * @param UDL semicolon separated list of Uniform Domain Locators - each of which specifies
     *            a server to connect to
     * @param name name of this client which must be unique in this domain
     * @param description description of this client
     * @throws cMsgException if domain in not implemented;
     *                          there are problems communicating with the name/domain server;
     *                          name contains colon;
     *                          the UDL is invalid;
     *                          the UDL contains an unreadable file;
     *                          any of the arguments are null
     */
    public cMsg(String UDL, String name, String description) throws cMsgException {

        if (UDL == null || name == null || description == null) {
            throw new cMsgException("a cMsg constructor argument is null");
        }

        // do not allow colons in the name string
        if (name.contains(":")) {
            throw new cMsgException("invalid name - contains \":\"");
        }

        this.UDL  = processUDLs(UDL);
        this.name = name;
        this.description = description;

        // create real connection object to server of specific domain
        connection = createDomainConnection();

        // Since the connection object is created with a no-arg constructor,
        // we must pass in information with setters.

        // Pass in the UDL
        connection.setUDL(UDL);
        // Pass in the name
        connection.setName(name);
        // Pass in the description
        connection.setDescription(description);
        // Pass in the UDL remainder
        connection.setUDLRemainder(UDLremainder);
    }


    /**
     * This method ensures that: 1) in a semicolon separated list of UDLs, all the domains
     * are the same, 2) any domain of type "configFile" is expanded before analysis, and
     * 3) no duplicate UDLs are in the list
     *
     * @param clientUDL UDL to be analyzed
     * @return a list of semicolon separated UDLs where all domains are the same and
     *         all configFile domain UDLs are expanded
     * @throws cMsgException if more than one domain is included in the list of UDLs or
     *                       files given by a configFile domain cannot be read
     */
    private String processUDLs(String clientUDL) throws cMsgException {

        // Since the UDL may be a semicolon separated list of UDLs, separate them
        String udlStrings[]  = clientUDL.split(";");

        // Turn String array into a linked list (array list does not allow
        // use of the method "remove" for some reason)
        List<String> l = Arrays.asList(udlStrings);
        LinkedList<String> udlList = new LinkedList<String>(l);

        // To eliminate duplicate udls, don't compare domains (which are forced to be identical
        // anyway). Just compare the udl remainders which may be case sensitive and will be
        // treated as such. Remove any duplicate items by placing them in a set (which does
        // this automatically). Make it a linked hash set to preserve order.
        LinkedHashSet<String> udlSet = new LinkedHashSet<String>();

        String udl, domainName=null;
        String[] parsedUDL;   // first element = domain, second = remainder
        int startIndex=0;
        boolean gotDomain = false;

        // One difficulty in implementing a domain in which a file contains the actual UDL
        // is that there is the possibility for self-referential, infinite loops. In other
        // words, the first file references to a 2nd file and that references the first, etc.
        // To avoid such a problem, it is NOT allowed for a configFile UDL to point to a
        // UDL in which there is another configFile domain UDL.

        topLevel:
            while(true) {
                // For each UDL in the list ...
                for (int i=startIndex; i < udlList.size(); i++) {

                    udl = udlList.get(i);
//System.out.println("udl = " + udl + ", at position " + i);

                    // Get the domain & remainder from the UDL
                    parsedUDL = parseUDL(udl);

                    // If NOT configFile domain ...
                    if (!parsedUDL[0].equalsIgnoreCase("configFile")) {
                        // Keep track of the valid UDL remainders
//System.out.println("storing remainder = " + parsedUDL[1]);
                        udlSet.add(parsedUDL[1]);

                        // Grab the first valid domain and make all other UDLs be the same
                        if (!gotDomain) {
                            domainName = parsedUDL[0];
                            gotDomain = true;
//System.out.println("Got domain, = " + parsedUDL[0]);
                        }
                        else {
                            if (!domainName.equalsIgnoreCase(parsedUDL[0])) {
                                throw new cMsgException("All UDLs must belong to the same domain");
                            }
                        }
                    }
                    // If configFile domain ...
                    else {
                        try {
//System.out.println("reading config file " + parsedUDL[1]);

                            // Read file to obtain actual UDL
                            String newUDL = readConfigFile(parsedUDL[1]);

                            // Check to see if this string contains a UDL which is in the configFile
                            // domain. That is NOT allowed in order to avoid infinite loops.
                            if (newUDL.toLowerCase().contains("configfile://")) {
                                throw new cMsgException("one configFile domain UDL may NOT reference another");
                            }

                            // Since the UDL may be a semicolon separated list of UDLs, separate them
                            String udls[] = newUDL.split(";");

                            // Substitute these new udls for "udl" they're replacing in the original
                            // list and start the process over again
//System.out.println("  about to remove item #" + i + " from list " + udlList);
                            udlList.remove(i);
                            for (int j = 0; j < udls.length; j++) {
                                udlList.add(i+j, udls[j]);
//System.out.println("  adding udl = " + udls[j] + ", at position " + (i+j));
                            }

                            // skip over udls already done
                            startIndex = i;

                            continue topLevel;
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                            throw new cMsgException("Cannot read UDL in file", e);
                        }
                    }
                }
                break;
            }

        // Warn user if there are duplicate UDLs in the UDL list that were removed
        if (udlList.size() != udlSet.size()) {
            System.out.println("\nWarning: duplicate UDL(s) removed from the UDL list\n");
        }

        // reconstruct the list of UDLs
        int i=0;
        StringBuffer finalUDL = new StringBuffer(500);
        for (String s : udlSet) {
            finalUDL.append("cMsg://");
            finalUDL.append(s);
            finalUDL.append(";");
            // pick off first udl remainder
            if (i++ == 0) {
                UDLremainder = s;
            }
        }
        // remove the last semicolon
        finalUDL.deleteCharAt(finalUDL.length() - 1);

        domain = domainName;

//System.out.println("Return processed UDL as " + finalUDL.toString());
//System.out.println("domain = " + domain + ", and remainder = " + UDLremainder);

        return finalUDL.toString();
    }


    /**
     * Method to read a configuration file and return the cMsg UDL stored there.
     *
     * @param fileName name of file to be read
     * @return UDL contained in config file, null if none
     * @throws IOException if file IO problem
     * @throws cMsgException if file does not exist or cannot be read
     */
    private String readConfigFile(String fileName) throws IOException, cMsgException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new cMsgException("specified file in UDL does NOT exist");
        }
        if (!file.canRead()) {
            throw new cMsgException("specified file in UDL cannot be read");
        }
        String s = "";
        BufferedReader reader = new BufferedReader(new FileReader(file));
        while (reader.ready() && s.length() < 1) {
            // readLine is a bit quirky :
            // it returns the content of a line MINUS the newline.
            // it returns null only for the END of the stream.
            // it returns an empty String if two newlines appear in a row.
            s = reader.readLine().trim();
//System.out.println("Read this string from file: " + s);
            if (s == null) break;
        }
        return s;
    }


    /**
     * Method to parse the Universal Domain Locator (or UDL) into its various components.
     * The UDL is of the form:
     *   cMsg:<domainType>://<domain dependent remainder>
     * where the initial "cMsg" is optional
     *
     * @param UDL Universal Domain Locator
     * @return array of 2 Strings, the first of which is the domain, the second
     *         of which is the UDL remainder (everything after the domain://)
     * @throws cMsgException if UDL is null; or no domainType is given in UDL
     */
    private String[] parseUDL(String UDL) throws cMsgException {

        if (UDL == null) {
            throw new cMsgException("invalid UDL");
        }

        // cMsg domain UDL is of the form:
        //       cMsg:<domainType>://<domain dependent remainder>
        //
        // (1) initial cMsg: in not necessary
        // (2) cMsg and domainType are case independent

        Pattern pattern = Pattern.compile("(cMsg)?:?([\\w\\-]+)://(.*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(UDL);

        String s0, s1, s2;

        if (matcher.matches()) {
            // cMsg
            s0 = matcher.group(1);
            // domain
            s1 = matcher.group(2);
            // remainder
            s2 = matcher.group(3);
        }
        else {
            throw new cMsgException("invalid UDL");
        }

        if (debug >= cMsgConstants.debugInfo) {
           System.out.println("\nparseUDL: " +
                              "\n  space     = " + s0 +
                              "\n  domain    = " + s1 +
                              "\n  remainder = " + s2);
        }

        // need domain
        if (s1 == null) {
            throw new cMsgException("invalid UDL");
        }

        // any remaining UDL is put here
        if (s2 == null) {
            throw new cMsgException("invalid UDL");
        }

        return new String[] {s1,s2};
    }


    /**
     * Creates the object that makes the real connection to a particular domain's server
     * that was specified in the UDL.
     *
     * @return connection object to domain specified in UDL
     * @throws cMsgException if object could not be created
     */
    private cMsgDomainInterface createDomainConnection()
            throws cMsgException {

        String domainConnectionClass = null;

        /** Object to handle client */
        cMsgDomainInterface domainConnection;

        // First check to see if connection class name was set on the command line.
        // Do this by scanning through all the properties.
        for (Object obj : System.getProperties().keySet()) {
            String s = (String) obj;
            if (s.contains(".")) {continue;}
            if (s.equalsIgnoreCase(domain)) {
                domainConnectionClass = System.getProperty(s);
            }
        }

        // If it wasn't given on the command line,
        // check the appropriate environmental variable.
        if (domainConnectionClass == null) {
            domainConnectionClass = System.getenv("CMSG_DOMAIN");
        }
//System.out.println("Looking for domain " + domain);
        // If there is still no handler class, look for the
        // standard, provided classes.
        if (domainConnectionClass == null) {
            // cMsg and runcontrol domains use the same client code
            if (domain.equalsIgnoreCase("cMsg"))  {
                domainConnectionClass = "org.jlab.coda.cMsg.cMsgDomain.client.cMsg";
            }
            else if (domain.equalsIgnoreCase("rc")) {
                domainConnectionClass = "org.jlab.coda.cMsg.RCDomain.RunControl";
            }
            else if (domain.equalsIgnoreCase("rcs")) {
                domainConnectionClass = "org.jlab.coda.cMsg.RCServerDomain.RCServer";
            }
            else if (domain.equalsIgnoreCase("rcb")) {
                domainConnectionClass = "org.jlab.coda.cMsg.RCBroadcastDomain.RCBroadcast";
            }
            else if (domain.equalsIgnoreCase("TCPS")) {
                domainConnectionClass = "org.jlab.coda.cMsg.TCPSDomain.TCPS";
            }
            else if (domain.equalsIgnoreCase("file")) {
                domainConnectionClass = "org.jlab.coda.cMsg.FileDomain.File";
            }
            else if (domain.equalsIgnoreCase("CA")) {
                domainConnectionClass = "org.jlab.coda.cMsg.CADomain.CA";
            }
            else if (domain.equalsIgnoreCase("smartsockets")) {
                domainConnectionClass = "org.jlab.coda.cMsg.smartsocketsDomain.smartsockets";
            }
            else if (domain.equalsIgnoreCase("database")) {
                domainConnectionClass = "org.jlab.coda.cMsg.databaseDomain.database";
            }
        }

        // all options are exhaused, throw error
        if (domainConnectionClass == null) {
            cMsgException ex = new cMsgException("no handler class found");
            ex.setReturnCode(cMsgConstants.errorNoClassFound);
            throw ex;
        }

        // Get connection class name and create object
        try {
            domainConnection = (cMsgDomainInterface) (Class.forName(domainConnectionClass).newInstance());
        }
        catch (InstantiationException e) {
            cMsgException ex = new cMsgException("cannot instantiate "+ domainConnectionClass +
                                                 " class");
            ex.setReturnCode(cMsgConstants.error);
            throw ex;
        }
        catch (IllegalAccessException e) {
            cMsgException ex = new cMsgException("cannot access "+ domainConnectionClass +
                                                 " class");
            ex.setReturnCode(cMsgConstants.error);
            throw ex;
        }
        catch (ClassNotFoundException e) {
            cMsgException ex = new cMsgException("no handler class found");
            ex.setReturnCode(cMsgConstants.errorNoClassFound);
            throw ex;
        }

        return domainConnection;
    }

    
    /**
      * Method to connect to a particular domain server.
      *
      * @throws cMsgException
      */
     public void connect() throws cMsgException {
        connection.connect();
     }


    /**
     * Method to close the connection to the domain server. This method results in this object
     * becoming functionally useless.
     *
     * @throws cMsgException
     */
    public void disconnect() throws cMsgException {
        connection.disconnect();
    }

    /**
     * Method to determine if this object is still connected to the domain server or not.
     *
     * @return true if connected to domain server, false otherwise
     */
    public boolean isConnected() {
        return connection.isConnected();
    }

    /**
     * Method to send a message to the domain server for further distribution.
     *
     * @param message message
     * @throws cMsgException
     */
    public void send(cMsgMessage message) throws cMsgException {
        connection.send(message);
    }

    /**
     * Method to send a message to the domain server for further distribution
     * and wait for a response from the subdomain handler that got it.
     *
     * @param message message
     * @param timeout time in milliseconds to wait for a response
     * @return response from subdomain handler
     * @throws cMsgException
     */
    public int syncSend(cMsgMessage message, int timeout) throws cMsgException {
        return connection.syncSend(message, timeout);
    }

    /**
     * Method to force cMsg client to send pending communications with domain server.
     * @param timeout time in milliseconds to wait for completion
     * @throws cMsgException
     */
    public void flush(int timeout) throws cMsgException {
        connection.flush(timeout);
    }

    /**
     * This method is like a one-time subscribe. The server grabs the first incoming
     * message of the requested subject and type and sends that to the caller.
     *
     * @param subject subject of message desired from server
     * @param type type of message desired from server
     * @param timeout time in milliseconds to wait for a message
     * @return response message
     * @throws cMsgException
     * @throws TimeoutException if timeout occurs
     */
    public cMsgMessage subscribeAndGet(String subject, String type, int timeout)
            throws cMsgException, TimeoutException {
        return connection.subscribeAndGet(subject, type, timeout);
    }

    /**
     * The message is sent as it would be in the {@link #send} method. The server notes
     * the fact that a response to it is expected, and sends it to all subscribed to its
     * subject and type. When a marked response is received from a client, it sends that
     * first response back to the original sender regardless of its subject or type.
     * The response may be null.
     *
     * @param message message sent to server
     * @param timeout time in milliseconds to wait for a reponse message
     * @return response message
     * @throws cMsgException
     * @throws TimeoutException if timeout occurs
     */
    public cMsgMessage sendAndGet(cMsgMessage message, int timeout)
            throws cMsgException, TimeoutException {
        return connection.sendAndGet(message, timeout);
    }

    /**
     * Method to subscribe to receive messages of a subject and type from the domain server.
     * The combination of arguments must be unique. In other words, only 1 subscription is
     * allowed for a given set of subject, type, callback, and userObj.
     *
     * @param subject message subject
     * @param type    message type
     * @param cb      callback object whose {@link cMsgCallbackInterface#callback(cMsgMessage, Object)}
     *                method is called upon receiving a message of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @return handle object to be used for unsubscribing
     * @throws cMsgException
     */
    public Object subscribe(String subject, String type, cMsgCallbackInterface cb, Object userObj)
            throws cMsgException {
        return connection.subscribe(subject, type, cb, userObj);
    }


    /**
     * Method to unsubscribe a previous subscription to receive messages of a subject and type
     * from the domain server.
     *
     * @param obj the object "handle" returned from a subscribe call
     * @throws cMsgException if there are communication problems with the server
     */
    public void unsubscribe(Object obj)
            throws cMsgException {
        connection.unsubscribe(obj);
    }

    /**
     * This method is a synchronous call to receive a message containing monitoring data
     * which describes the state of the cMsg domain the user is connected to.
     *
     * @param  command directive for monitoring process
     * @return response message containing monitoring information
     * @throws cMsgException
     */
    public cMsgMessage monitor(String command)
            throws cMsgException {
        return connection.monitor(command);
    }

    /**
     * Method to start or activate the subscription callbacks.
     */
    public void start() {
        connection.start();
    }

    /**
     * Method to stop or deactivate the subscription callbacks.
     */
    public void stop() {
        connection.stop();
    }

    /**
     * Method to shutdown the given clients.
     * Wildcards used to match client names with the given string.
     *
     * @param client client(s) to be shutdown
     * @param includeMe  if true, it is permissible to shutdown calling client
     * @throws cMsgException
     */
    public void shutdownClients(String client, boolean includeMe) throws cMsgException {
        connection.shutdownClients(client, includeMe);
    }


    /**
     * Method to shutdown the given servers.
     * Wildcards used to match server names with the given string.
     *
     * @param server server(s) to be shutdown
     * @param includeMyServer  if true, it is permissible to shutdown calling client's
     *                         cMsg server
     * @throws cMsgException
     */
    public void shutdownServers(String server, boolean includeMyServer) throws cMsgException {
        connection.shutdownClients(server, includeMyServer);
    }


    /**
     * Method to set the shutdown handler of the client.
     *
     * @param handler shutdown handler
     */
    public void setShutdownHandler(cMsgShutdownHandlerInterface handler) {
        connection.setShutdownHandler(handler);
    }

    /**
     * Method to get the shutdown handler of the client.
     *
     * @return shutdown handler object
     */
    public cMsgShutdownHandlerInterface getShutdownHandler() {
        return connection.getShutdownHandler();
    }

        /**
     * Get the name of the domain connected to.
     * @return domain name
     */
    public String getDomain() {
        return connection.getDomain();
    }

    /**
     * Get the UDL of the client.
     * @return client's UDL
     */
    public String getUDL() {
        return UDL;
    }

    /**
     * Get the UDL remainder (UDL after cMsg:domain:// is stripped off)
     * of the client.
     * @return client's UDL remainder
     */
    public String getUDLRemainder() {
        return UDLremainder;
    }

    /**
     * Get the name of the client.
     * @return client's name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the client's description.
     * @return client's description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the host the client is running on.
     * @return client's host
     */
    public String getHost() {
        return connection.getHost();
    }

    /**
     * Get a string that the implementation class wants to return up to the top (this) level API.
     * @return a string
     */
    public String getString() {
        return connection.getString();
    }

    /**
     * Method telling whether callbacks are activated or not. The
     * start and stop methods activate and deactivate the callbacks.
     * @return true if callbacks are activated, false if they are not
     */
    public boolean isReceiving() {
        return connection.isReceiving();
    }


}
