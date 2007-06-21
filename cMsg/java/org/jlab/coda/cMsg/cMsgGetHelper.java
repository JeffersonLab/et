/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 2-Jan-2006, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;

import java.util.regex.Pattern;

/**
 * This class is used to help in implementing subscribeAndGet and sendAndGet methods.
 * This is true in the cMsg domain for the client and in the RCBroadcast and RCServer
 * domains for servers.
 * An object of this class stores a msg from a sender to the method's caller and
 * is used to synchronize/wait/notify on. It also indicates whether the call timed
 * out or not.
 */
public class cMsgGetHelper {
    /**  Message object. */
    cMsgMessageFull message;

    /** Has the "subscribeAndGet" or "sendAndGet" call timed out? */
    boolean timedOut;

    /**
     * When a "subscribeAndGet" or "sendAndGet" is woken up by an error condition,
     * such as "the server died", this code is set.
     */
    int errorCode;

    /** Subject. */
    String subject;

    /** Subject turned into regular expression that understands * and ?. */
    String subjectRegexp;

    /** Compiled regular expression given in {@link #subjectRegexp}. */
    Pattern subjectPattern;

    /** Are there any * or ? characters in the subject? */
    boolean wildCardsInSub;


    /** Type. */
    String type;

    /** Type turned into regular expression that understands * and ?. */
    String typeRegexp;

    /** Compiled regular expression given in {@link #typeRegexp}. */
    Pattern typePattern;

    /** Are there any * or ? characters in the type? */
    boolean wildCardsInType;


    /** Constructor used in sendAndGet. */
    public cMsgGetHelper() {
        timedOut  = true;
        errorCode = cMsgConstants.ok;
    }


    /**
     * Constructor used in subscribeAndGet.
     * @param subject subject of subscription
     * @param type type of subscription
     */
    public cMsgGetHelper(String subject, String type) {
        this.subject = subject;
        this.type    = type;
        timedOut     = true;
        errorCode    = cMsgConstants.ok;

        // we only need to do the regexp stuff if there are wildcards chars in subj or type
        if ((subject.contains("*") || subject.contains("?"))) {
            wildCardsInSub = true;
            subjectRegexp  = cMsgMessageMatcher.escape(subject);
            subjectPattern = Pattern.compile(subjectRegexp);
        }
        if ((type.contains("*") || type.contains("?"))) {
            wildCardsInType = true;
            typeRegexp      = cMsgMessageMatcher.escape(type);
            typePattern     = Pattern.compile(typeRegexp);
        }
    }


    /**
     * Returns the message object.
     * @return the message object.
     */
    public cMsgMessageFull getMessage() {
        return message;
    }

    /**
     * Sets the messge object;
     * @param message the message object
     */
    public void setMessage(cMsgMessageFull message) {
        this.message = message;
    }

    /**
     * Returns true if the "subscribeAndGet" or "sendAndGet" call timed out.
     * @return true if the "subscribeAndGet" or "sendAndGet" call timed out.
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Set boolean telling whether he "subscribeAndGet" or "sendAndGet" call timed out or not.
     * @param timedOut boolean telling whether he "subscribeAndGet" or "sendAndGet" call timed out or not.
     */
    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    /**
     * Gets the error code from when a "subscribeAndGet" or "sendAndGet" is woken up by an error condition.
     * @return error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the error code from when a "subscribeAndGet" or "sendAndGet" is woken up by an error condition.
     * @param errorCode error code
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Returns true if there are * or ? characters in subject.
     * @return true if there are * or ? characters in subject.
     */
    public boolean areWildCardsInSub() {
        return wildCardsInSub;
    }


    /**
     * Returns true if there are * or ? characters in type.
     * @return true if there are * or ? characters in type.
     */
    public boolean areWildCardsInType() {
        return wildCardsInType;
    }


    /**
     * Gets subject subscribed to.
     * @return subject subscribed to
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Gets subject turned into regular expression that understands * and ?.
     * @return subject subscribed to in regexp form
     */
    public String getSubjectRegexp() {
        return subjectRegexp;
    }

    /**
     * Gets subject turned into compiled regular expression pattern.
     * @return subject subscribed to in compiled regexp form
     */
    public Pattern getSubjectPattern() {
        return subjectPattern;
    }

    /**
     * Gets type subscribed to.
     * @return type subscribed to
     */
    public String getType() {
        return type;
    }

    /**
     * Gets type turned into regular expression that understands * and ?.
     * @return type subscribed to in regexp form
     */
     public String getTypeRegexp() {
         return typeRegexp;
     }

    /**
     * Gets type turned into compiled regular expression pattern.
     * @return type subscribed to in compiled regexp form
     */
    public Pattern getTypePattern() {
        return typePattern;
    }

}
