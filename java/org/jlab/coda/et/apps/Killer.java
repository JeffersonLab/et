/*----------------------------------------------------------------------------*
 *  Copyright (c) 2013        Jefferson Science Associates,                   *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, #3            *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et.apps;

import org.jlab.coda.et.*;


/**
 * This class is an example of a client that kills an ET system.
 *
 * @author Carl Timmer
 */
public class Killer {

    public Killer() {
    }


    private static void usage() {
        System.out.println("\nUsage: java Killer -f <ET name> -host <ET host> [-h] [-v] [-r] [-p <ET server port>]\n\n" +
                "       -host  ET system's host\n" +
                "       -f     ET system's (memory-mapped file) name\n" +
                "       -h     help\n" +
                "       -v     verbose output\n" +
                "       -r     act as remote (TCP) client even if ET system is local\n" +
                "       -p     ET server port\n" +
                "       -i     outgoing network interface IP address (dot-decimal)\n\n" +
                "        This consumer works by making a direct connection to the\n" +
                "        ET system's server port.\n");
    }


    public static void main(String[] args) {

        String etName = null, host = null, netInterface = null;
        int port = EtConstants.serverPort;
        boolean verbose = false;
        boolean remote  = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-f")) {
                etName = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-i")) {
                netInterface = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-host")) {
                host = args[++i];
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
            else if (args[i].equalsIgnoreCase("-v")) {
                verbose = true;
            }
            else if (args[i].equalsIgnoreCase("-r")) {
                remote = true;
            }
            else {
                usage();
                return;
            }
        }

        if (host == null || etName == null) {
            usage();
            return;
        }

        try {
            // Make a direct connection to ET system's tcp server
            EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host, port);
            config.setConnectRemotely(remote);

            // EXAMPLE: Broadcast to find ET system
            //EtSystemOpenConfig config = new EtSystemOpenConfig();
            //config.setHost(host);
            //config.setEtName(etName);
            //config.addBroadcastAddr("129.57.29.255"); // this call is not necessary

            // EXAMPLE: Multicast to find ET system
            //ArrayList<String> mAddrs = new ArrayList<String>();
            //mAddrs.add(EtConstants.multicastAddr);
            //EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host,
            //                                mAddrs, EtConstants.multicastPort, 32);

            if (netInterface != null) config.setNetworkInterface(netInterface);

            // create ET system object with verbose debugging output
            EtSystem sys = new EtSystem(config);
            if (verbose) sys.setDebug(EtConstants.debugInfo);
            sys.open();

            sys.kill();
        }
        catch (Exception ex) {
            System.out.println("Error using ET system as killer");
            ex.printStackTrace();
        }
    }
    
}
