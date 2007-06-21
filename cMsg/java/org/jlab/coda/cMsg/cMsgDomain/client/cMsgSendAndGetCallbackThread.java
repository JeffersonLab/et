/*----------------------------------------------------------------------------*
 *  Copyright (c) 2005        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 1-Dec-2005, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.client;

import org.jlab.coda.cMsg.cMsgMessageFull;
import org.jlab.coda.cMsg.cMsgCallbackInterface;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.Callable;

/**
 * This class is a callback-running thread to be used with the enterprise-level
 * implementation of sendAndGet.
 */
public class cMsgSendAndGetCallbackThread implements Callable {

     /** A cue containing a single message to be passed to the callback. */
     private SynchronousQueue<cMsgMessageFull> messageCue;

     /** User argument to be passed to the callback. */
     private Object arg;

     /** Callback to be run. */
     private cMsgCallbackInterface callback;


     /**
      * Constructor used to pass an arbitrary argument to callback method.
      * @param callback callback to be run when message arrives
      * @param arg user-supplied argument for callback
      */
     cMsgSendAndGetCallbackThread(cMsgCallbackInterface callback, Object arg) {
         this.callback = callback;
         this.arg      = arg;
         messageCue    = new SynchronousQueue<cMsgMessageFull>();
     }


    /**
     * Put message on a cue waiting to be taken by the callback.
     * @param message message to be passed to callback
     */
     public void sendMessage(cMsgMessageFull message) {
         try { messageCue.put(message); }
         catch (InterruptedException e) { }
     }


     /** This method is executed as a thread which runs the callback method */
     public Boolean call() {
         cMsgMessageFull message = null;

         try {
             message = messageCue.take();

             // only callback gets message so don't bother to copy it
             callback.callback(message, arg);

             // end this thread after running callback
             return Boolean.TRUE;
         }
         catch (InterruptedException e) {
//System.out.println("INTERRUPTED S&G callback thread");
             return Boolean.FALSE;
         }
     }

}
