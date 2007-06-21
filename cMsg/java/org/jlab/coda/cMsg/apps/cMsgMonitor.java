package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.*;

/**
 * Application that gets and prints monitor data.
 */
public class cMsgMonitor {

    String  name = "monitor";
    String  description = "java monitor";
    String  UDL = "cMsg:cMsg://aslan:3456/cMsg/test";
    boolean debug;


    /** Constructor. */
    cMsgMonitor(String[] args) {
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
            "   java cMsgConsumer [-n name] [-d description] [-u UDL] [-debug]\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {
            cMsgMonitor mon = new cMsgMonitor(args);
            mon.run();
        }
        catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /**
     * This method is executed as a thread.
     */
    public void run() throws cMsgException {

        if (debug) {
            System.out.println("Running cMsg monitor");
        }

        // connect to cMsg server
        cMsg coda = new cMsg(UDL, name, description);
        coda.connect();

        long period = 3000; // millisec

        cMsgMessage msg;

        while (true) {
            msg = coda.monitor(null);
            System.out.println("Msg ->\n" + msg.getText());
            // wait
            try { Thread.sleep(period); }
            catch (InterruptedException e) {}
        }
    }
}
