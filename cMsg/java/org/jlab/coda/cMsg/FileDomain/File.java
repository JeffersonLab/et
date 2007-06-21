// still to do:


/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    E. Wolin, 12-Nov-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.FileDomain;

import org.jlab.coda.cMsg.*;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * This class implements a client in the cMsg File domain.
 *
 * @author Elliott Wolin
 * @version 1.0
 */
public class File extends cMsgDomainAdapter {

    private String myFileName;
    private PrintWriter myPrintHandle;
    private boolean textOnly = false;


//-----------------------------------------------------------------------------


    /**
     * Constructor for File domain.
     * <p/>
     *
     *  UDL:  cMsg:cMsg://fileName?textOnly=value.
     *
     * Default is to print entire message to file.
     * If textOnly=true then only print timestamp and message text to file.
     *
     * @throws cMsgException if domain in not implemented or there are problems
     */
    public File() throws cMsgException {

        try {
            this.host = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            System.err.println(e);
            this.host = "unknown";
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Opens file.
     *
     * @throws cMsgException if there are communication problems
     */
    synchronized public void connect() throws cMsgException {

        if (connected) return;

        parseUDL();

        try {
            myPrintHandle = new PrintWriter(new BufferedWriter(new FileWriter(myFileName, true)));
            myPrintHandle.println("<cMsgFile  name=\"" + myFileName + "\"" + "  date=\"" + (new Date()) + "\">\n\n");
            connected = true;
        }
        catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
            cMsgException ce = new cMsgException("connect: unable to open file");
            ce.setReturnCode(1);
            throw ce;
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Closes file.
     *
     */
    synchronized public void disconnect() {
        if (!connected) return;

        connected = false;
        myPrintHandle.println("\n\n</cMsgFile>\n");
        myPrintHandle.println("\n\n<!--===========================================================================================-->\n\n\n");
        myPrintHandle.close();
    }


//-----------------------------------------------------------------------------


    /**
     * Writes to file.
     *
     * @param msg message to send
     * @throws cMsgException if file is closed (connect was not called, or disconnect was called)
     */
    synchronized public void send(cMsgMessage msg) throws cMsgException {
        if (!connected) {
            throw new cMsgException("File is closed, Call \"connect\" first");
        }
        
        Date now = new Date();
        if (textOnly) {
            myPrintHandle.println(now + ":    " + msg.getText());
        }
        else {
            if((msg.getDomain()==null)||(msg.getDomain().length()<=0)) {
                cMsgMessageFull msgFull = new cMsgMessageFull(msg);
                msgFull.setDomain(domain);
                msgFull.setCreator(name);
                msgFull.setSender(name);
                msgFull.setSenderHost(host);
                msgFull.setSenderTime(now);
                msgFull.setReceiver(domain);
                msgFull.setReceiverTime(now);
                msgFull.setReceiverHost(host);
                myPrintHandle.println(msgFull);
            } else {
                myPrintHandle.println(msg);
            }
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Calls send to write to file.
     *
     * @param message message
     * @param timeout time in milliseconds to wait for a response
     * @return response from subdomain handler
     * @throws cMsgException if file is closed (connect was not called, or disconnect was called)
     */
    public int syncSend(cMsgMessage message, int timeout) throws cMsgException {
        send(message);
        return (0);
    }


//-----------------------------------------------------------------------------


    /**
     * Flushes output.
     * 
     * @param timeout time in milliseconds to wait for completion
     * @throws cMsgException if file is closed (connect was not called, or disconnect was called)
     */
    synchronized public void flush(int timeout) throws cMsgException {
        if (!connected) {
            throw new cMsgException("File is closed, Call \"connect\" first");
        }
        myPrintHandle.flush();
        return;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to parse the domain-specific portion of the Universal Domain Locator
     * (UDL) into its various components.
     *
     * @throws cMsgException if UDL is null, or no host given in UDL
     */
    private void parseUDL() throws cMsgException {

        domain="file";


        if (UDLremainder == null) {
            throw new cMsgException("invalid UDL");
        }


        // get file name
        String remainder = null;
        int ind = UDLremainder.indexOf('?');
        if (ind > 0) {
            myFileName = UDLremainder.substring(0,ind);
            remainder  = UDLremainder.substring(ind+1);
        } else {
            myFileName = UDLremainder;
        }


        // parse remainder
        if (remainder != null) {
            Pattern p = Pattern.compile("textOnly=(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(remainder);
            m.find();
            textOnly = m.group(1).equals("true");
        }
    }


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
}
