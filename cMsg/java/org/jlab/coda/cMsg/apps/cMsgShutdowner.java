package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.*;

/**
 * This is an example class which creates a cMsg client that shutsdown
 * other specified cMsg clients (possibly including itself).
 */
public class cMsgShutdowner {

    String  name = "shutdowner";
    String  description = "java shutdowner";
    String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";
    String  client = "defaultClientNameHere";
    boolean debug;


    /** Constructor. */
    cMsgShutdowner(String[] args) {
        decodeCommandLine(args);
    }


    /**
     * Method to decode the command line used to start this application.
     * @param args command line arguments
     */
    public void decodeCommandLine(String[] args) {

        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                usage();
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                name = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-d")) {
                description = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-u")) {
                UDL= args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-c")) {
                client = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        return;
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
            "   java cMsgGetConsumer [-n name] [-d description] [-u UDL]\n" +
            "                        [-c client] [-debug]\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {
            cMsgShutdowner shutdowner = new cMsgShutdowner(args);
            shutdowner.run();
        }
        catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    class myShutdownHandler implements cMsgShutdownHandlerInterface {
        public void handleShutdown() {
            System.out.println("RUNNING SHUTDOWN HANDLER");
            System.exit(-1);
        }
    }


    /**
     * Run as a stand-alone application.
     */
    public void run() throws cMsgException {
        if (debug) {
            System.out.println("Running cMsg shutdowner");
        }

        // connect to cMsg server
        cMsg coda = new cMsg(UDL, name, description);
        coda.connect();

        if (debug) {
            System.out.println("Shutting down " + client);
        }

        // add shutdown handler
        coda.setShutdownHandler(new myShutdownHandler());

        try {Thread.sleep(4000);}
        catch (InterruptedException e) {}

        // if we want to shutdown ourselves, then call ...
        coda.shutdownClients(name, true);

        // shutdown specified client
        //coda.shutdownClients(client, 0);

        try {Thread.sleep(5000);}
        catch (InterruptedException e) {}

        coda.disconnect();
    }

}
