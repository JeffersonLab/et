/*----------------------------------------------------------------------------*
 *  Copyright (c) 2005        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 10-Feb-2005, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;

import java.util.regex.Matcher;
import java.net.InetAddress;
import java.net.UnknownHostException;


/**
 * This class contains the methods used to determine whether a message's subject
 * and type match a subscription's subject and type. It also contains a method
 * to check the format of a server's name.
 */
public class cMsgMessageMatcher {
    /**
     * Characters which need to be escaped to avoid special interpretation in
     * regular expressions.
     */
    static final private String escapeChars = "\\(){}[]+.|^$";

    /** Array of characters to look for in a given string. */
    static final private String[] lookFor = {"\\", "(", ")", "{", "}", "[",
                                             "]", "+" ,".", "|", "^", "$"};

    /** Length of each item in {@link #lookFor}. */
    static final private int lookForLen = 1;

    /** Array of regexp strings to replace the found special characters in a given string. */
    static final private String[] replaceWith = {"\\\\", "\\(", "\\)", "\\{", "\\}", "\\[",
                                                 "\\]",  "\\+" ,"\\.", "\\|", "\\^", "\\$"};

    /** Length of each item in {@link #replaceWith}. */
    static final private int replaceWithLen = 2;



    /**
     * This method implements a simple wildcard matching scheme where "*" means
     * any or no characters and "?" means exactly 1 character.
     *
     * @param regexp subscription string that can contain wildcards (* and ?)
     * @param s message string to be matched (can be blank which only matches *)
     * @param escapeRegexp if true, the regexp argument is escaped, else not
     * @return true if there is a match, false if there is not
     */
    static public boolean matches(String regexp, String s, boolean escapeRegexp) {
        // It's a match if regexp (subscription string) is null
        if (regexp == null) return true;

        // If the message's string is null, something's wrong
        if (s == null) return false;

        // The first order of business is to take the regexp arg and modify it so that it is
        // a regular expression that Java can understand. This means subbing all occurrences
        // of "*" and "?" with ".*" and ".{1}". And it means escaping other regular
        // expression special characters.
        // Sometimes regexp is already escaped, so no need to redo it.
        if (escapeRegexp) {
            regexp = escape(regexp);
        }

        // Now see if there's a match with the string arg
        return s.matches(regexp);
    }

    /**
     * This method checks to see if there is a match between a subject & type
     * pair and a subscribeAndGet helper object.
     * The subscription's subject and type may include
     * wildcards where "*" means any or no characters and "?" means exactly 1
     * character. There is a match only if both subject and type strings match
     * their conterparts in the subscription.
     *
     * @param subject subject
     * @param type type
     * @param helper subscription
     * @return true if there is a match of both subject and type, false if there is not
     */
    static public boolean matches(String subject, String type, cMsgGetHelper helper) {
        boolean matchSubj;
        boolean matchType;

        // if there are no wildcards in the helper's subject, just use string compare
        if (!helper.areWildCardsInSub()) {
            matchSubj = subject.equals(helper.getSubject());
        }
        // else if there are wildcards in the helper's subject, use regexp matching
        else {
            Matcher m = helper.getSubjectPattern().matcher(subject);
            matchSubj = m.matches();
        }

        if (!helper.areWildCardsInType()) {
            matchType = type.equals(helper.getType());
        }
        else {
            Matcher m = helper.getTypePattern().matcher(type);
            matchType = m.matches();
        }

        return (matchType && matchSubj);
    }



    /**
     * This method checks to see if there is a match between a subject & type
     * pair and a subscription. The subscription's subject and type may include
     * wildcards where "*" means any or no characters and "?" means exactly 1
     * character. There is a match only if both subject and type strings match
     * their conterparts in the subscription.
     *
     * @param subject subject
     * @param type type
     * @param sub subscription
     * @return true if there is a match of both subject and type, false if there is not
     */
    static public boolean matches(String subject, String type, cMsgSubscription sub) {
        boolean matchSubj;
        boolean matchType;

        // first check for null subjects/types in subscription
        if (sub.getSubject() == null || sub.getType() == null) return false;

        // if there are no wildcards in the subscription's subject, just use string compare
        if (!sub.areWildCardsInSub()) {
            matchSubj = subject.equals(sub.getSubject());
        }
        // else if there are wildcards in the subscription's subject, use regexp matching
        else {
            Matcher m = sub.getSubjectPattern().matcher(subject);
            matchSubj = m.matches();
        }

        if (!sub.areWildCardsInType()) {
            matchType = type.equals(sub.getType());
        }
        else {
            Matcher m = sub.getTypePattern().matcher(type);
            matchType = m.matches();
        }

        return (matchType && matchSubj);
    }



    /**
     * This method takes a string and escapes most special, regular expression characters.
     * The return string can allows only * and ? to be passed through in a way meaningful
     * to regular expressions (as .* and .{1} respectively).
     *
     * @param s string to be escaped
     * @return escaped string
     */
    static public String escape(String s) {
        if (s == null) return null;

        // StringBuilder is a nonsynchronized StringBuffer class.
        StringBuilder buf = new StringBuilder(s);
        int lastIndex;

        // Check for characters which need to be escaped to prevent
        // special interpretation in regular expressions.
        for (int i=0; i < lookFor.length; i++) {
            lastIndex = 0;
            for (int j=0; j < buf.length(); j++) {
                lastIndex = buf.indexOf(lookFor[i], lastIndex);
                if (lastIndex < 0) {
                    break;
                }
                buf.replace(lastIndex, lastIndex+lookForLen, replaceWith[i]);
//System.out.println("buf1 = " + buf);
                lastIndex += replaceWithLen;
            }
        }

        // Translate from * and ? to Java regular expression language.

        // Check for "*" characters and replace with ".*"
        lastIndex = 0;
        for (int j=0; j < buf.length(); j++) {
            lastIndex = buf.indexOf("*", lastIndex);
            if (lastIndex < 0) {
                break;
            }
            buf.replace(lastIndex, lastIndex+1, ".*");
//System.out.println("buf2 = " + buf);
            lastIndex += 2;
        }

        // Check for "?" characters and replace with ".{1}"
        lastIndex = 0;
        for (int j=0; j < buf.length(); j++) {
            lastIndex = buf.indexOf("?", lastIndex);
            if (lastIndex < 0) {
                break;
            }
            buf.replace(lastIndex, lastIndex+1, ".{1}");
//System.out.println("buf3 = " + buf);
            lastIndex += 4;
        }

        return buf.toString();
    }


    /**
     * This method tests its input argument to see if it is in the proper format
     * for a server; namely, "host:port". If it is, it makes sure the host is in
     * its canonical form.
     *
     * @param s input string of a possible server name
     * @return an acceptable server name with the canonical form of the host name
     * @throws cMsgException if input string is not in the proper form (host:port)
     *                       or the host is unknown
     */
    static public String constructServerName(String s) throws cMsgException {

        // Separate the server name from the server port.
        // First check for ":"
        int index = s.indexOf(":");
        if (index == -1) {
            throw new cMsgException("arg is not in the \"host:port\" format");
        }

        String sName = s.substring(0, index);
        String sPort = s.substring(index + 1);
        int port;

        // translate the port from string to int
        try {
            port = Integer.parseInt(sPort);
        }
        catch (NumberFormatException e) {
            throw new cMsgException("port needs to be an integer");
        }

        if (port < 1024 || port > 65535) {
            throw new cMsgException("port needs to be an integer between 1024 & 65535");
        }

        // See if this host is recognizable. To do that
        InetAddress address;
        try {
            address = InetAddress.getByName(sName);
        }
        catch (UnknownHostException e) {
            throw new cMsgException("specified server is unknown");
        }

        // put everything in canonical form if possible
        s = address.getCanonicalHostName() + ":" + sPort;

        return s;
    }

}
