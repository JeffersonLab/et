//  general purpose cMsg logger


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

import java.lang.*;
import java.io.*;
import java.sql.*;
import java.util.Date;
import java.net.*;


//-----------------------------------------------------------------------------


/**
 * Logs cMsg messages to screen, file, and/or database.
 * subject/type specified on command line, defaults to *.
 *
 * @version 1.0
 */
public class cMsgLogger {


    /** Universal Domain Locator and cMsg system object. */
    private static String UDL = "cMsg:cMsg://aslan/cMsg";
    private static cMsg cmsg  = null;


    /** Name of this client, generally must be unique within domain. */
    private static String name = null;


    /** Description of this client. */
    private static String description = "Generic cMsg Logger";


    /** Subject and type of messages being logged. */
    private static String subject = "*";
    private static String type    = "*";


    /** toScreen true to log to screen. */
    private static boolean toScreen = false;
    private static boolean verbose  = false;
    private static boolean header   = false;
    private static boolean wide     = false;

    private static String normalFormat    = "%-6d  %18s  %18s  %24s    %9d    %-18s  %-18s    %s";
    private static String normalHeader    = "%-6s  %18s  %18s  %24s    %9s    %-18s  %-18s    %s";

    private static String wideFormat      = "%-6d  %18s  %18s  %24s    %9d    %-30s  %-30s    %s";
    private static String wideHeader      = "%-6s  %18s  %18s  %24s    %9s    %-30s  %-30s    %s";



    /** filename not null to log to file. */
    private static String filename     = null;
    private static PrintWriter pWriter = null;


    /** url not null to log to database. */
    private static String url              = null;
    private static String table            = "cMsgLog";
    private static String driver           = "com.mysql.jdbc.Driver";
    private static String account          = "";
    private static String password         = "";
    private static Connection con          = null;
    private static PreparedStatement pStmt = null;


    // misc
    private static int count     = 0;
    private static boolean done  = false;
    private static boolean debug = false;


    /** Class to implement the callback interface. */
    static class cb extends cMsgCallbackAdapter {
        /**
         * Called when message arrives, logs to screen, file, and/or database.
         *
         * @param msg cMsg message
         * @param userObject object given by user to callback as argument.
         */
        public void callback(cMsgMessage msg, Object userObject) {

            count++;

            // output to screen
            if(toScreen) {
                if(!verbose) {
                    System.out.println(String.format(wide?wideFormat:normalFormat,
                                                     count,
                                                     msg.getCreator(),
                                                     msg.getSenderHost(),
                                                     new java.sql.Timestamp(msg.getSenderTime().getTime()),
                                                     msg.getUserInt(),
                                                     msg.getSubject(),
                                                     msg.getType(),
                                                     msg.getText()));
                } else {
                    System.out.println("msg count is: " + count);
                    System.out.println(msg.toString());
                }
            }


            // output to file
            if(filename!=null) {
                if(!verbose) {
                    pWriter.println(String.format(wide?wideFormat:normalFormat,
                                                  count,
                                                  msg.getCreator(),
                                                  msg.getSenderHost(),
                                                  new java.sql.Timestamp(msg.getSenderTime().getTime()),
                                                  msg.getSubject(),
                                                  msg.getType(),
                                                  msg.getText()));
                } else {
                    pWriter.println("msg count is: " + count);
                    pWriter.println(msg);
                }
                pWriter.flush();
            }


            // output to database
            if(url!=null) {
                try {
                    int i = 1;
                    pStmt.setInt(i++,       msg.getVersion());
                    pStmt.setString(i++,    msg.getDomain());
                    pStmt.setInt(i++,       msg.getSysMsgId());

                    pStmt.setInt(i++,       (msg.isGetRequest()?1:0));
                    pStmt.setInt(i++,       (msg.isGetResponse()?1:0));

                    pStmt.setString(i++,    msg.getCreator());
                    pStmt.setString(i++,    msg.getSender());
                    pStmt.setString(i++,    msg.getSenderHost());
                    pStmt.setTimestamp(i++, new java.sql.Timestamp(msg.getSenderTime().getTime()));
                    pStmt.setInt(i++,       msg.getSenderToken());

                    pStmt.setInt(i++,       msg.getUserInt());
                    pStmt.setTimestamp(i++, new java.sql.Timestamp(msg.getUserTime().getTime()));

                    pStmt.setString(i++,    msg.getReceiver());
                    pStmt.setString(i++,    msg.getReceiverHost());
                    pStmt.setTimestamp(i++, new java.sql.Timestamp(msg.getReceiverTime().getTime()));
                    pStmt.setInt(i++,       msg.getReceiverSubscribeId());

                    pStmt.setString(i++,    msg.getSubject());
                    pStmt.setString(i++,    msg.getType());
                    pStmt.setString(i++,    msg.getText());

                    pStmt.execute();
                } catch (SQLException e) {
                    System.err.println("?sql error in callback\n" + e);
                    System.exit(-1);
                }
            }
        }

    }


//-----------------------------------------------------------------------------


    static public void main(String[] args) {


        // decode command line
        decode_command_line(args);


        // generate name if not set
        if(name==null) {
            String host="";
            try {
                host=InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                System.err.println("?unknown host exception");
            }
            name = "cMsgLogger@" + host + "@" + (new Date()).getTime();
        }


        // connect to cMsg system
        try {
            cmsg = new cMsg(UDL, name, description);
            cmsg.connect();
        } catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // subscribe and provide callback
        try {
            cmsg.subscribe(subject, type, new cb(), null);
        } catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // enable screen logging if nothing else enabled
        if((filename==null)&&(url==null)) toScreen=true;
        if(verbose)header=false;
        if(toScreen&&header) {
            System.out.println(String.format(wide?wideHeader:normalHeader,
                                             "Count","Creator","SenderHost","SenderTime      ","UserInt","Subject","Type","Text"));
            System.out.println(String.format(wide?wideHeader:normalHeader,
                                             "-----","-------","----------","----------      ","-------","-------","----","----"));
        }


        // init file logging
        if(filename!=null) {
            try {
                pWriter = new PrintWriter(new BufferedWriter(new FileWriter(filename,true)));
            } catch (IOException e) {
                System.err.println("?unable to open file " + filename);
                filename=null;
            }
        }


        // init database logging
        if(url!=null) {

            // load jdbc driver
            try {
                Class.forName(driver);
            } catch (Exception e) {
                System.err.println("?unable to load driver: " + driver + "\n" + e);
                System.exit(-1);
            }

            // connect to database
            try {
                con = DriverManager.getConnection(url, account, password);
            } catch (SQLException e) {
                System.err.println("?unable to connect to database url: " + url + "\n" + e);
                System.exit(-1);
            }

            // check if table exists, create if needed
            try {
                DatabaseMetaData dbmeta = con.getMetaData();
                ResultSet dbrs = dbmeta.getTables(null,null,table,new String [] {"TABLE"});
                if((!dbrs.next())||(!dbrs.getString(3).equalsIgnoreCase(table))) {
                    String sql="create table " + table + " (" +
                        "version int, domain varchar(255), sysMsgId int," +
                        "getRequest int, getResponse int, creator varchar(128)," +
                        "sender varchar(128), senderHost varchar(128),senderTime datetime, senderToken int," +
                        "userInt int, userTime datetime," +
                        "receiver varchar(128), receiverHost varchar(128), receiverTime datetime, receiverSubscribeId int," +
                        "subject  varchar(255), type varchar(128), text text)";
                    con.createStatement().executeUpdate(sql);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                System.exit(-1);
            }

            // get prepared statement object
            try {
                String sql = "insert into " + table + " (" +
                    "version,domain,sysMsgId," +
                    "getRequest,getResponse,creator" +
                    "sender,senderHost,senderTime,senderToken," +
                    "userInt,userTime," +
                    "receiver,receiverHost,receiverTime,receiverSubscribeId," +
                    "subject,type,text" +
                    ") values (" +
                    "?,?,?," + "?,?," + "?,?,?,?," + "?,?," + "?,?,?,?," + "?,?,?" + ")";
                pStmt = con.prepareStatement(sql);
            } catch (SQLException e) {
                System.err.println("?unable to prepare statement\n" + e);
                System.exit(-1);
            }
        }


        // enable receipt of messages and delivery to callback
        cmsg.start();


        // wait for messages
        try {
            while (!done&&(cmsg.isConnected())) {
                Thread.sleep(1);
            }
        } catch (Exception e) {
            System.err.println(e);
        }


        // disable message delivery to callbacks
        cmsg.stop();


        // done
        try {
            if(filename!=null) {
                pWriter.flush();
                pWriter.close();
            }
            if(url!=null)con.close();
            cmsg.disconnect();
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
            "   java cMsgLogger [-name name] [-descr description] [-udl domain] [-subject subject] [-type type]\n" +
            "                   [-screen] [-file filename] [-verbose] [-header] [-wide]\n" +
            "                   [-url url] [-table table] [-driver driver] [-account account] [-pwd password]\n" +
            "                   [-debug]\n\n";


        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                System.out.println(help);
                System.exit(-1);

            }
            else if (args[i].equalsIgnoreCase("-name")) {
                name = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-descr")) {
                description = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-udl")) {
                UDL= args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-subject")) {
                subject = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-type")) {
                type= args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-screen")) {
                toScreen=true;

            }
            else if (args[i].equalsIgnoreCase("-verbose")) {
                verbose=true;

            }
            else if (args[i].equalsIgnoreCase("-header")) {
                header=true;

            }
            else if (args[i].equalsIgnoreCase("-file")) {
                filename= args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-url")) {
                url = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-table")) {
               table = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-driver")) {
                driver = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-account")) {
                account = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-pwd")) {
                password = args[i + 1];
                i++;

            }
            else if (args[i].equalsIgnoreCase("-wide")) {
                wide = true;

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
