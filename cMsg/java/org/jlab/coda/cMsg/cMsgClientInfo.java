/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 8-Jul-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;

import java.lang.*;


/**
 * This class stores a cMsg client's information.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgClientInfo {
    /** Client's name. */
    private String  name;
    /** Client supplied description. */
    private String  description;
    /** Client supplied UDL. */
    private String  UDL;
    /** Remainder from client's UDL. */
    private String  UDLremainder;
    /** Subdomain client wishes to use. */
    private String  subdomain;
    /** cMsg subdomain namespace client is using. */
    private String  namespace;
    /** Client's host. */
    private String  clientHost;
    /** Client's port. */
    private int     clientPort;
    /** Domain server's host. */
    private String  domainHost;
    /** Domain server's TCP port. */
    private int     domainPort;
    /** Domain server's UDP port. */
    private int     domainUdpPort;

    /** Is this client another cMsg server? */
    boolean isServer;
    /**
     * If this client is a cMsg server, this quantity is the name server's port
     * of the server which has just become a client of cMsg server where this
     * object lives. (I hope you got that).
     */
    private int serverPort;

    /** Object for delivering messges to this client. */
    cMsgDeliverMessageInterface deliverer;


    /**
     * Constructor specifing client's name, port, host, subdomain, and UDL remainder.
     * Used in nameServer for a connecting regular client.
     *
     * @param name  client's name
     * @param nsPort name server's listening port
     * @param port  client's listening port
     * @param host  client's host
     * @param subdomain    client's subdomain
     * @param UDLRemainder client's UDL's remainder
     * @param UDL          client's UDL
     * @param description  client's description
     */
    public cMsgClientInfo(String name, int nsPort, int port, String host, String subdomain,
                          String UDLRemainder, String UDL, String description) {
        this.name = name;
        serverPort = nsPort;
        clientPort = port;
        clientHost = host;
        this.subdomain = subdomain;
        this.UDLremainder = UDLRemainder;
        this.UDL = UDL;
        this.description = description;
    }

    /**
     * Constructor used when cMsg server acts as a client and connects a to cMsg server.
     * Used in nameServer for a connecting server client.
     *
     * @param name  client's name
     * @param nsPort name server's listening port
     * @param port  client's listening port
     * @param host  client's host
     */
    public cMsgClientInfo(String name, int nsPort, int port, String host) {
        this.name  = name;
        serverPort = nsPort;
        clientPort = port;
        clientHost = host;
        isServer   = true;
    }


    //-----------------------------------------------------------------------------------

    /**
     * Gets client's name.
     * @return client's name
     */
    public String getName() {return name;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets client's description.
     * @return client's description
     */
    public String getDescription() {return description;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets client's UDL.
     * @return client's UDL
     */
    public String getUDL() {return UDL;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets remainder of the UDL the client used to connect to the domain server.
     * @return remainder of the UDL
     */
    public String getUDLremainder() {return UDLremainder;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets subdomain client is using.
     * @return subdomain client is using
     */
    public String getSubdomain() {return subdomain;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets the namespace of client's cMsg subdomain.
     * @return namespace of client's cMsg subdomain
     */
    public String getNamespace() {return namespace;}

    /**
     * Sets the namespace of client's cMsg subdomain.
     * @param namespace namespace of client's cMsg subdomain
     */
    public void setNamespace(String namespace) {this.namespace = namespace;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets host client is running on.
     * @return host client is running on
     */
    public String getClientHost() {return clientHost;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets TCP port client is listening on.
     * @return TCP port client is listening on
     */
    public int getClientPort() {return clientPort;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets host domain server is running on.
     * @return host domain server is running on
     */
    public String getDomainHost() {return domainHost;}

    /**
     * Sets host domain server is running on.
     * @param domainHost host domain server is running on
     */
    public void setDomainHost(String domainHost) {this.domainHost = domainHost;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets TCP port domain server is listening on.
     * @return TCP port domain server is listening on
     */
    public int getDomainPort() {return domainPort;}

    /**
     * Sets TCP port domain server is listening on.
     * @param domainPort TCP port domain server is listening on
     */
    public void setDomainPort(int domainPort) {this.domainPort = domainPort;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets UDP port domain server is listening on.
     * Meaningful only if client is sending by UDP.
     * @return UDP port domain server is listening on
     */
    public int getDomainUdpPort() {return domainUdpPort;}

    /**
     * Sets UDP port domain server is listening on.
     * Meaningful only if client is sending by UDP.
     * @param domainUdpPort TCP port domain server is listening on
     */
    public void setDomainUdpPort(int domainUdpPort) {this.domainUdpPort = domainUdpPort;}

    //-----------------------------------------------------------------------------------

    /**
     * Gets host connecting name server (client of this server) is running on.
     * @return host connecting name server is running on
     */
    public String getServerHost() {return clientHost;}

    /**
     * Gets the TCP port the connecting name server (client of this server)
     * is listening on.
     * @return TCP port connecting name server is listening on
     */
    public int getServerPort() {return serverPort;}

    //-----------------------------------------------------------------------------------

    /**
     * States whether this client is a cMsg server or not.
     * @return true if this client is a cMsg server
     */
    public boolean isServer() {
        return isServer;
    }

    //-----------------------------------------------------------------------------------

    /**
     * Gets the object used to deliver messages to this client.
     * @return object used to deliver messages to this client
     */
    public cMsgDeliverMessageInterface getDeliverer() {
        return deliverer;
    }

    /**
     * Sets the object used to deliver messages to this client.
     * @param deliverer object used to deliver messages to this client
     */
    public void setDeliverer(cMsgDeliverMessageInterface deliverer) {
        this.deliverer = deliverer;
    }


 }

