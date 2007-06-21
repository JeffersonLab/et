/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12H        *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;

/**
 * This class represents an error that occurred while attempting to execute a cMsg method.
 *
 * @author Carl Timmer
 */
public class cMsgException extends Exception {
    private int returnCode;

    public int getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }

    /**
     * Constructs a new exception with the specified message and a return code.
     *
     * @param msg the error message
     * @param returnCode send an integer along with the exception
     */ 
    public cMsgException(String msg, int returnCode) {
        super(msg);
        this.returnCode = returnCode;
    }

    /**
      * Constructs a new exception with the specified detail message and
      * cause.
      *
      * @param  message the detail message (which is saved for later retrieval
      *         by the {@link #getMessage()} method).
      * @param  cause the cause (which is saved for later retrieval by the
      *         {@link #getCause()} method).  (A <tt>null</tt> value is
      *         permitted, and indicates that the cause is nonexistent or
      *         unknown.)
      * @since  1.4
      */
    public cMsgException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified message.
     * @param msg the error message
     */
    public cMsgException(String msg) {
        super(msg);
    }

    /** Constructs a new exception with no message. */
    public cMsgException() {
    }
}

