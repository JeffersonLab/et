//  general purpose cMsg alarm server

//      -url jdbc:mysql://xdaq/test -driver com.mysql.jdbc.Driver -account fred



/*----------------------------------------------------------------------------*
*  Copyright (c) 2004        Southeastern Universities Research Association, *
*                            Thomas Jefferson National Accelerator Facility  *
*                                                                            *
*    This software was developed under a United States Government license    *
*    described in the NOTICE file included as part of this distribution.     *
*                                                                            *
*    E.Wolin, 9-Aug-2005, Jefferson Lab                                      *
*                                                                            *
*    Authors: Elliott Wolin                                                  *
*             wolin@jlab.org                    Jefferson Lab, MS-6B         *
*             Phone: (757) 269-7365             12000 Jefferson Ave.         *
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
 * Logs special cMsg alarm messages to screen, file, and/or database.
 *
 * @version 1.0
 */
public class cMsgAlarmServer {


    /** Universal Domain Locator and cMsg system object. */
    private static String udl = "cMsg:cMsg://ollie/cMsg";
    private static cMsg cmsg  = null;


    /** name,description of client, generally must be unique within domain. */
    private static String name = null;
    private static String description = "cMsg Alarm Server";


    /** alarm subject. */
    private static String alarmSubject = "cMsgAlarm";


    /** toScreen true to log to screen. */
    private static boolean toScreen = false;
    private static String format = "%-8d   %-24s   %25s   %2d   %s";


    /** filename not null to log to file. */
    private static boolean noAppend    = false;
    private static String fileName     = null;
    private static PrintWriter pWriter = null;


    /** url not null to log to database. */
    private static String url                = null;
    private static String driver             = "com.mysql.jdbc.Driver";
    private static String account            = "";
    private static String password           = "";
    private static Connection con            = null;
    private static ResultSet rs              = null;
    private static PreparedStatement ps      = null;

    /** table names not null to log to tables. */
    private static String fullHistoryTable        = null;
    private static String historyTable            = null;
    private static String changeTable             = null;
    private static String latestTable             = null;

    private static PreparedStatement fullHistoryPStmt = null;
    private static PreparedStatement historyPStmt1    = null;
    private static PreparedStatement historyPStmt2    = null;
    private static PreparedStatement changePStmt1     = null;
    private static PreparedStatement changePStmt2     = null;
    private static PreparedStatement changePStmt3     = null;
    private static PreparedStatement latestPStmt1     = null;
    private static PreparedStatement latestPStmt2     = null;
    private static PreparedStatement latestPStmt3     = null;


    // misc
    private static int count           = 0;
    private static boolean force       = false;
    private static boolean done        = false;
    private static boolean debug       = false;


    /** Class to implement the callback. */
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
                System.out.println(String.format(format,count,
                                                 msg.getType(),
                                                 new java.sql.Timestamp(msg.getSenderTime().getTime()),
                                                 msg.getUserInt(),
                                                 msg.getText()));
            }


            // output to file
            if(fileName!=null) {
                pWriter.println(String.format(format,count,
                                              msg.getType(),
                                              new java.sql.Timestamp(msg.getSenderTime().getTime()),
                                              msg.getUserInt(),
                                              msg.getText()));
                pWriter.flush();
            }


            // output to database
            if(url!=null) {
                logToDatabase(msg);
            }
        }

    }


//-----------------------------------------------------------------------------


    static public void main(String[] args) {


        // decode command line
        decode_command_line(args);


        // any output?
        if( !toScreen && (fileName==null) && (fullHistoryTable==null) &&
            (historyTable==null) && (changeTable==null) && (latestTable==null) ) {
            System.out.println("\n ?cMsgAlarmServer...no output specified!\n");
            System.exit(-1);
        }


        // generate name if not set
        if(name==null) {
            String host="";
            try {
                host=InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                System.err.println("?unknown host exception");
            }
            name = "cMsgAlarmServer-" + host + "-" + (new Date()).getTime();
        }


        // connect to cMsg system
        try {
            cmsg = new cMsg(udl, name, description);
            cmsg.connect();
        } catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // subscribe and provide callback
        try {
            cmsg.subscribe(alarmSubject, "*", new cb(), null);
        } catch (cMsgException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // init file logging in append mode
        if(fileName!=null) {
            try {
                pWriter = new PrintWriter(new BufferedWriter(new FileWriter(fileName,!noAppend)));
            } catch (IOException e) {
                System.err.println("?unable to open file " + fileName);
                fileName=null;
            }
        }


        // init database logging
        if(url!=null) {
            init_database();
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


        // done
        cmsg.stop();
        try {
            if(fileName!=null) {
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


    static public void init_database() {

        String sql;


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


        // check if tables exist, create if needed
        // if change or latest tables exist make sure they do not look like history tables
        try {

            DatabaseMetaData dbmeta = con.getMetaData();
            ResultSet dbrs;

            if(fullHistoryTable!=null) {
                dbrs = dbmeta.getTables(null,null,fullHistoryTable,new String [] {"TABLE"});
                if((!dbrs.next())||(!dbrs.getString(3).equalsIgnoreCase(fullHistoryTable))) {
                    sql="create table " + fullHistoryTable +
                        "(channel varchar(128), time datetime, severity int, text varchar(256))";
                    con.createStatement().executeUpdate(sql);
                }
            }


            if(historyTable!=null) {
                dbrs = dbmeta.getTables(null,null,historyTable,new String [] {"TABLE"});
                if((!dbrs.next())||(!dbrs.getString(3).equalsIgnoreCase(historyTable))) {
                    sql="create table " + historyTable +
                        "(channel varchar(128), time datetime, severity int, text varchar(256))";
                    con.createStatement().executeUpdate(sql);
                }
            }


            if(changeTable!=null) {
                dbrs = dbmeta.getTables(null,null,changeTable,new String [] {"TABLE"});
                if((!dbrs.next())||(!dbrs.getString(3).equalsIgnoreCase(changeTable))) {
                    sql="create table " + changeTable +
                        "(channel varchar(128), time datetime, severity int, text varchar(256))";
                    con.createStatement().executeUpdate(sql);
                } else {
                    sql="select channel,count(channel) from " + changeTable + " group by channel";
                    dbrs = con.createStatement().executeQuery(sql);
                    while(dbrs.next()) {
                        if(dbrs.getInt(2)>1) {
                            System.out.print("\n\n   *** Existing change table " + changeTable +
                                             " contains multiple entries for some channels ***\n" +
                                             "       This table looks like a history table!\n\n");
                            if(!force) {
                                System.out.print("       Specify -force on command line to ignore this\n\n\n" );
                                System.exit(-1);
                            } else {
                                System.out.print("       -force specified...continuing...\n\n" );
                                break;
                            }
                        }
                    }
                }
            }


            if(latestTable!=null) {
                dbrs = dbmeta.getTables(null,null,latestTable,new String [] {"TABLE"});
                if((!dbrs.next())||(!dbrs.getString(3).equalsIgnoreCase(latestTable))) {
                    sql="create table " + latestTable +
                        "(channel varchar(128), time datetime, severity int, text varchar(256))";
                    con.createStatement().executeUpdate(sql);
                } else {
                    sql="select channel,count(channel) from " + latestTable + " group by channel";
                    dbrs = con.createStatement().executeQuery(sql);
                    while(dbrs.next()) {
                        if(dbrs.getInt(2)>1) {
                            System.out.print("\n\n   *** Existing latest table " + latestTable +
                                             " contains multiple entries for some channels ***\n" +
                                             "       This table looks like a history table!\n\n");
                            if(!force) {
                                System.out.print("       Specify -force on command line to ignore this\n\n\n" );
                                System.exit(-1);
                            } else {
                                System.out.print("       -force specified...continuing...\n\n" );
                                break;
                            }
                        }
                    }
                }
            }


        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(-1);
        }



        // get various statement objects, etc.
        try {
            // full history
            sql = "insert into " + fullHistoryTable + " (" +
                "channel,time,severity,text" +
                ") values (" +
                "?,?,?,?" + ")";
            fullHistoryPStmt = con.prepareStatement(sql);

            // history
            sql = "select severity from " + historyTable + " where channel=? order by time desc limit 1";
            historyPStmt1 = con.prepareStatement(sql);
            sql = "insert into " + historyTable + " (" +
                "channel,time,severity,text" +
                ") values (" +
                "?,?,?,?" + ")";
            historyPStmt2 = con.prepareStatement(sql);

            // change mode
            sql = "select severity from " + changeTable + " where channel=?";
            changePStmt1 = con.prepareStatement(sql);
            sql = "insert into " + changeTable + " (" +
                "channel,time,severity,text" +
                ") values (" +
                "?,?,?,?" + ")";
            changePStmt2 = con.prepareStatement(sql);
            sql = "update " + changeTable + " set time=?, severity=?, text=? where channel=?";
            changePStmt3 = con.prepareStatement(sql);


            // latest mode
            sql = "select severity from " + latestTable + " where channel=?";
            latestPStmt1 = con.prepareStatement(sql);
            sql = "insert into " + latestTable + " (" +
                "channel,time,severity,text" +
                ") values (" +
                "?,?,?,?" + ")";
            latestPStmt2 = con.prepareStatement(sql);
            sql = "update " + latestTable + " set time=?, severity=?, text=? where channel=?";
            latestPStmt3 = con.prepareStatement(sql);



        } catch (SQLException e) {
            System.err.println("?unable to prepare statements\n" + e);
            System.exit(-1);
        }

    }


//-----------------------------------------------------------------------------


    static public void logToDatabase(cMsgMessage msg) {

        int i;


        try {

            // get msg parameters
            String chan    = msg.getType();
            Timestamp time = new java.sql.Timestamp(msg.getSenderTime().getTime());
            int sev        = msg.getUserInt();
            String text    = msg.getText();


            // full history mode...log everything
            if(fullHistoryTable!=null) {
                i=1;
                fullHistoryPStmt.setString(i++,    chan);
                fullHistoryPStmt.setTimestamp(i++, time);
                fullHistoryPStmt.setInt(i++,        sev);
                fullHistoryPStmt.setString(i++,    text);
                fullHistoryPStmt.execute();
            }


            // history mode...log if channel does not exist or if severity changed
            if(historyTable!=null) {
                historyPStmt1.setString(1,chan);
                rs = historyPStmt1.executeQuery();
                if((!rs.next()) || (rs.getInt(1)!=sev)) {
                    i=1;
                    historyPStmt2.setString(i++,    chan);
                    historyPStmt2.setTimestamp(i++, time);
                    historyPStmt2.setInt(i++,        sev);
                    historyPStmt2.setString(i++,    text);
                    historyPStmt2.execute();
                }
            }


            // change mode...log if channel does not exist or update if state changed
            if(changeTable!=null) {
                changePStmt1.setString(1,chan);
                rs = changePStmt1.executeQuery();
                if(!rs.next()) {
                    i=1;
                    changePStmt2.setString(i++,    chan);
                    changePStmt2.setTimestamp(i++, time);
                    changePStmt2.setInt(i++,        sev);
                    changePStmt2.setString(i++,    text);
                    changePStmt2.execute();
                } else if(rs.getInt(1)!=sev) {
                    i=1;
                    changePStmt3.setTimestamp(i++, time);
                    changePStmt3.setInt(i++,        sev);
                    changePStmt3.setString(i++,    text);
                    changePStmt3.setString(i++,    chan);
                    changePStmt3.execute();
                }
            }


            // latest mode...log if channel does not exist or update if channel exists
            if(latestTable!=null) {
                latestPStmt1.setString(1,chan);
                rs = latestPStmt1.executeQuery();
                if(!rs.next()) {
                    i=1;
                    latestPStmt2.setString(i++,    chan);
                    latestPStmt2.setTimestamp(i++, time);
                    latestPStmt2.setInt(i++,        sev);
                    latestPStmt2.setString(i++,    text);
                    latestPStmt2.execute();
                } else {
                    i=1;
                    latestPStmt3.setTimestamp(i++, time);
                    latestPStmt3.setInt(i++,        sev);
                    latestPStmt3.setString(i++,    text);
                    latestPStmt3.setString(i++,    chan);
                    latestPStmt3.execute();
                }
            }


        } catch (SQLException e) {
            System.err.println("?sql error in logToDatabase\n" + e);
            e.printStackTrace();
            System.exit(-1);
        }
    }


//-----------------------------------------------------------------------------


    /**
     * decodes command line parameters
     * @param args command line arguments
     */
    static public void decode_command_line(String[] args) {

        String help = "\nUsage:\n\n" +
            "   java cMsgAlarmServer [-name name] [-udl udl] [-descr description]\n" +
            "                   [-subject alarmSubject]\n" +
            "                   [-screen] [-file fileName] [-noAppend]\n" +
            "                   [-fullHistory fullHistoryTable] [-history historyTable]\n" +
            "                   [-change changeTable] [-latest latestTable]\n" +
            "                   [-url url] [-driver driver] [-account account] [-pwd password]\n" +
            "                   [-force] [-debug]\n\n";


        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                System.out.println(help);
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-name")) {
                name = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-descr")) {
                description = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-udl")) {
                udl = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-screen")) {
                toScreen = true;

            } else if (args[i].equalsIgnoreCase("-file")) {
                fileName = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-noappend")) {
                noAppend = true;

            } else if (args[i].equalsIgnoreCase("-fullHistory")) {
                fullHistoryTable = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-history")) {
                historyTable = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-change")) {
                changeTable = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-latest")) {
                latestTable = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-url")) {
                url = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-driver")) {
                driver = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-account")) {
                account = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-pwd")) {
                password = args[i + 1];
                i++;

            } else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;

            } else if (args[i].equalsIgnoreCase("-force")) {
                force = true;
            }
        }

        return;
    }


//-----------------------------------------------------------------------------
//  end class definition:  cMsgAlarmServer
//-----------------------------------------------------------------------------
}
