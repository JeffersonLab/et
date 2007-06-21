// still to do:
//   check file permissions


/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *     E.Wolin, 5-oct-2004                                                    *
 *                                                                            *
 *     Author: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/


package org.jlab.coda.cMsg.subdomains;

import org.jlab.coda.cMsg.*;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.regex.*;



//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * cMsg subdomain handler for FileQueue subdomain.
 *
 * UDL:  cMsg:cMsg://host:port/FileQueue/myQueueName?dir=myDirName.
 *
 * e.g. cMsg:cMsg://ollie/FileQueue/ejw?dir=/home/wolin/qdir.
 *
 * stores/retrieves cMsgMessageFull messages from file-based queue.
 *
 * @author Elliiott Wolin
 * @version 1.0
 */
public class FileQueue extends cMsgSubdomainAdapter {


    /** global list of queue names, for synchronizing access to queues */
    private static HashMap<String,Object> queueHashMap = new HashMap<String,Object>(100);


    /** registration params. */
    private cMsgClientInfo myClientInfo;


    /** UDL remainder for this subdomain handler. */
    private String myUDLRemainder;


    /** Object used to deliver messages to the client. */
    private cMsgDeliverMessageInterface myDeliverer;


    /** for synchronizing access to queue */
    private Object mySyncObject;


    // database access objects
    private String myQueueNameFull      = null;
    private String myDirectory          = ".";
    private String myFileNameBase       = null;
    private String myLoSeqFile          = null;
    private String myHiSeqFile          = null;


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "send" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendRequest}
     * method.
     *
     * @return true if get implemented in {@link #handleSendRequest}
     */
    public boolean hasSend() {
        return true;
    };


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "sendAndGet" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSendAndGetRequest}
     * method.
     *
     * @return true if sendAndGet implemented in {@link #handleSendAndGetRequest}
     */
    public boolean hasSendAndGet() {
        return true;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to tell if the "syncSend" cMsg API function is implemented
     * by this interface implementation in the {@link #handleSyncSendRequest}
     * method.
     *
     * @return true if send implemented in {@link #handleSyncSendRequest}
     */
    public boolean hasSyncSend() {
        return true;
    };


//-----------------------------------------------------------------------------


    /**
     * Method to give the subdomain handler the appropriate part
     * of the UDL the client used to talk to the domain server.
     *
     * @param UDLRemainder last part of the UDL appropriate to the subdomain handler
     * @throws cMsgException
     */
    public void setUDLRemainder(String UDLRemainder) throws cMsgException {
        myUDLRemainder=UDLRemainder;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to give the subdomain handler on object able to deliver messages
     * to the client.
     *
     * @param deliverer object able to deliver messages to the client
     * @throws cMsgException
     */
    public void setMessageDeliverer(cMsgDeliverMessageInterface deliverer) throws cMsgException {
        if (deliverer == null) {
            throw new cMsgException("FileQueue subdomain must be able to deliver messages, set the deliverer.");
        }
        myDeliverer = deliverer;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to register domain client.
     * Creates separate database connection for each client connection.
     * UDL contains driver name, database JDBC URL, account, password, and table name to use.
     * Column names are fixed (domain, sender, subject, etc.).
     *
     * @param info information about client
     * @throws cMsgException upon error
     */
    public void registerClient(cMsgClientInfo info) throws cMsgException {

        Pattern p;
        Matcher m;
        String remainder = null;


        // set myClientInfo
        myClientInfo=info;


        // extract queue name from UDL remainder
        String myQueueName;
        if(myUDLRemainder.indexOf("?")>0) {
            p = Pattern.compile("^(.+?)(\\?.*)$");
            m = p.matcher(myUDLRemainder);
            if(m.find()) {
                myQueueName = m.group(1);
                remainder   = m.group(2) + "&";
            } else {
                cMsgException ce = new cMsgException("?illegal UDL");
                ce.setReturnCode(1);
                throw ce;
            }
        } else {
            myQueueName=myUDLRemainder;
        }


        // extract directory name from remainder
        if(remainder!=null) {
            p = Pattern.compile("[&\\?]dir=(.*?)&", Pattern.CASE_INSENSITIVE);
            m = p.matcher(remainder);
            if(m.find()) myDirectory = m.group(1);
        }


        // get canonical dir name
        String myCanonicalDir;
        try {
            myCanonicalDir = new File(myDirectory).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException("Unable to get directory canonical name for " + myDirectory);
            ce.setReturnCode(1);
            throw ce;
        }


        // form various file names using canonical dir name
        if(myCanonicalDir!=null) {
            myQueueNameFull = myCanonicalDir + "/cMsgFileQueue_" + myQueueName;
            myFileNameBase  = myQueueNameFull + "_";
            myHiSeqFile     = myFileNameBase + "Hi";
            myLoSeqFile     = myFileNameBase + "Lo";
        } else {
            cMsgException ce = new cMsgException("?null canonical dir name for " + myDirectory);
            ce.setReturnCode(1);
            throw ce;
        }


        // create queue entry if needed
        synchronized(queueHashMap) {
             if(!queueHashMap.containsKey(myQueueNameFull)) {
                 queueHashMap.put(myQueueNameFull,new Object());
             }
        }


        // get sync/lock object for this queue
        mySyncObject=queueHashMap.get(myQueueNameFull);


        // synchronize on queue name, then init files if needed
        synchronized(mySyncObject) {

            File hi = new File(myHiSeqFile);
            File lo = new File(myLoSeqFile);
            if(!hi.exists()||!lo.exists()) {
                try {
                    FileWriter h = new FileWriter(hi);  h.write("0\n");  h.close();
                    FileWriter l = new FileWriter(lo);  l.write("0\n");  l.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    cMsgException ce = new cMsgException(e.toString());
                    ce.setReturnCode(1);
                    throw ce;
                }
            }
        }


    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle message sent by client.
     * Inserts message into SQL database table via JDBC.
     *
     * @param msg message from sender
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    public void handleSendRequest(cMsgMessageFull msg) throws cMsgException {

        long hi;


        // write message to queue
        synchronized(mySyncObject) {

            try {

                // lock hi file
                RandomAccessFile r = new RandomAccessFile(myHiSeqFile,"rw");
                FileChannel c = r.getChannel();
                FileLock l = c.lock();

                // read hi file, increment, write out new hi value
                hi=Long.parseLong(r.readLine());
                hi++;
                r.seek(0);
                r.writeBytes(hi+"\n");

                // write message to queue file
                FileWriter fw = new FileWriter(String.format("%s%08d",myFileNameBase,hi));
                fw.write(msg.toString());
                fw.close();

                // unlock and close hi file
                l.release();
                r.close();


                } catch (IOException e) {
                    e.printStackTrace();
                    cMsgException ce = new cMsgException(e.toString());
                    ce.setReturnCode(1);
                    throw ce;
                }
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to handle message sent by domain client in synchronous mode.
     * It requries an integer response from the subdomain handler.
     *
     * @param msg message from sender
     * @return response from subdomain handler
     * @throws cMsgException
     */
    public int handleSyncSendRequest(cMsgMessageFull msg) throws cMsgException {
        try {
            handleSendRequest(msg);
            return(0);
        } catch (cMsgException e) {
            return(1);
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method to synchronously get a single message from a receiver by sending out a
     * message to be responded to.
     *
     * Currently just returns message at head of queue.
     *
     * @param message message requesting what sort of message to get
     */
    public void handleSendAndGetRequest(cMsgMessageFull message) throws cMsgException {

        cMsgMessageFull response;
        RandomAccessFile rHi,rLo;
        long hi,lo;
        boolean null_response = false;


        // get message off queue
        synchronized(mySyncObject) {

            try {

                // lock hi file
                rHi = new RandomAccessFile(myHiSeqFile,"rw");
                FileChannel cHi = rHi.getChannel();
                FileLock lHi = cHi.lock();

                // lock lo file
                rLo = new RandomAccessFile(myLoSeqFile,"rw");
                FileChannel cLo = rLo.getChannel();
                FileLock lLo = cLo.lock();

                // read files
                hi=Long.parseLong(rHi.readLine());
                lo=Long.parseLong(rLo.readLine());

                // message file exists only if hi>lo
                if(hi>lo) {
                    lo++;
                    rLo.seek(0);
                    rLo.writeBytes(lo+"\n");

                    // create response from file
                    File f = new File(String.format("%s%08d",myFileNameBase,lo));
                    if(f.exists()) {
                        response = new cMsgMessageFull(f);
                        f.delete();
                    } else {
                        System.err.println("?missing message file " + lo + " in queue " + myQueueNameFull);
                        null_response=true;
                        response = new cMsgMessageFull();
                    }

                } else {
                    null_response=true;
                    response = new cMsgMessageFull();
                }


                // unlock and close files
                lLo.release();
                lHi.release();
                rHi.close();
                rLo.close();


            } catch (IOException e) {
                e.printStackTrace();
                cMsgException ce = new cMsgException(e.toString());
                ce.setReturnCode(1);
                throw ce;
            }
        }


        // mark as (possibly null) response to message
        if(null_response) {
            response.makeNullResponse(message);
        } else {
            response.makeResponse(message);
        }


        // send response to client
        try {
            myDeliverer.deliverMessage(response, cMsgConstants.msgGetResponse);
        } catch (IOException e) {
            e.printStackTrace();
            cMsgException ce = new cMsgException(e.toString());
            ce.setReturnCode(1);
            throw ce;
        }

    }


//-----------------------------------------------------------------------------
//  end class
//-----------------------------------------------------------------------------
}

