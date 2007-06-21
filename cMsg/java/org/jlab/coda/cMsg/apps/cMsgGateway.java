//  general purpose cMsg gateway


/*----------------------------------------------------------------------------*
*  Copyright (c) 2004        Southeastern Universities Research Association, *
*                            Thomas Jefferson National Accelerator Facility  *
*                                                                            *
*    This software was developed under a United States Government license    *
*    described in the NOTICE file included as part of this distribution.     *
*                                                                            *
*    E.Wolin, 6-Jan-2005, Jefferson Lab                                      *
*                                                                            *
*    Authors: Elliott Wolin                                                  *
*             wolin@jlab.org                    Jefferson Lab, MS-6B         *
*             Phone: (757) 269-7365             12000 Jefferson Ave.         *
*             Fax:   (757) 269-5800             Newport News, VA 23606       *
*                                                                            *
*             Carl Timmer                                                    *
*             timmer@jlab.org                   Jefferson Lab, MS-6B         *
*             Phone: (757) 269-5130             12000 Jefferson Ave.         *
*             Fax:   (757) 269-5800             Newport News, VA 23606       *
*                                                                            *
*----------------------------------------------------------------------------*/


package org.jlab.coda.cMsg.apps;

import org.jlab.coda.cMsg.*;



//-----------------------------------------------------------------------------


/**
 * Cross-posts message between two domains.
 * subject and type defaults to * and *, can be set via command line parameters.
 *
 * @version 1.0
 */
public class cMsgGateway {


    /** Subject and type of messages passed through gateway. */
    private static String subject = "*";
    private static String type    = "*";


    /** cMsg system objects. */
    private static cMsg cmsg1  = null;
    private static cMsg cmsg2  = null;


    /** Names, generally must be unique within domain. */
    private static String name1 = "cMsgGateway";
    private static String name2 = "cMsgGateway";


    /** UDL's. */
    private static String UDL1 = "cMsg:cMsg://ollie/cMsg";
    private static String UDL2 = "cMsg:cMsg://ollie/cMsg";


    /** Descriptions. */
    private static String descr1 = "Generic cMsg Gateway";
    private static String descr2 = "Generic cMsg Gateway";


    // misc
    private static int count1    = 0;
    private static int count2    = 0;

    private static boolean done  = false;
    private static boolean debug = false;
    private static int delay     = 500;


    /** callbacks for cross-posting. */
    static class cb1 extends cMsgCallbackAdapter {

        public void callback(cMsgMessage msg, Object userObject) {

            count1++;

            // do not cross post if already cross posted!
            if(!msg.getSender().equals(name1)) {
                try {
                    cmsg2.send(msg);
                } catch (cMsgException e) {
                    System.out.println("?unable to send message using cmsg2");
                }
            }

            if(debug) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }

        }
    }

    static class cb2 extends cMsgCallbackAdapter {

        public void callback(cMsgMessage msg, Object userObject) {

            count2++;

            // do not cross post if already cross posted!
            if(!msg.getSender().equals(name2)) {
                try {
                    cmsg1.send(msg);
                } catch (cMsgException e) {
                    System.out.println("?unable to send message using cmsg1");
                }
            }

            if(debug) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }

        }
    }



//-----------------------------------------------------------------------------


    static public void main(String[] args) {


        // decode command line
        decode_command_line(args);


        // connect to both cMsg systems
        try {
            cmsg1 = new cMsg(UDL1, name1, descr1);
            cmsg2 = new cMsg(UDL2, name2, descr2);
            cmsg1.connect();
            cmsg2.connect();
        } catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // subscribe and provide callbacks
        try {
            cmsg1.subscribe(subject, type, new cb1(), null);
            cmsg2.subscribe(subject, type, new cb2(), null);
        } catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // enable message receipt
        cmsg1.start();
        cmsg2.start();


        // wait forever
        try {
            while (!done&&cmsg1.isConnected()&&cmsg2.isConnected()) {
                Thread.sleep(1);
            }
        } catch (Exception e) {
            System.err.println(e);
        }


        // disable message receipt
        cmsg1.stop();
        cmsg2.stop();


        // done
        try {
            cmsg1.disconnect();
            cmsg2.disconnect();
        } catch (Exception e) {
            System.exit(-1);
        }
        System.exit(0);

    }


//-----------------------------------------------------------------------------


    /**
     * Method to decode the command line used to start this application.
     * @param args command line arguments
     */
    static public void decode_command_line(String[] args) {

        String help = "\nUsage:\n\n" +
            "   java cMsgGateway [-subject subject] [-type type]\n" +
            "                    [-name1 name1] [-udl1 udl1] [-descr1 descr1]\n" +
            "                    [-name2 name2] [-udl2 udl2] [-descr2 descr2]\n" +
            "                    [-debug]\n\n";


        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                System.out.println(help);
                System.exit(-1);

            }
            else if (args[i].equalsIgnoreCase("-subject")) {
                subject = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-type")) {
                type= args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-name1")) {
                name1 = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-udl1")) {
                UDL1= args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-descr1")) {
                descr1 = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-name2")) {
                name2 = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-udl2")) {
                UDL2= args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-descr2")) {
                descr2 = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
            }
        }

        return;
    }


//-----------------------------------------------------------------------------
//  end class definition:  cMsgLogger
//-----------------------------------------------------------------------------
}
