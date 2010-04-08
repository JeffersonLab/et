/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
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

package org.jlab.coda.et.data;

import org.jlab.coda.et.Constants;
import org.jlab.coda.et.Utils;

import java.lang.*;
import java.io.*;

/**
 * This class holds all information about an attachment. It parses
 * the information from a stream of data sent by an ET system.
 *
 * @author Carl Timmer
 */

public class AttachmentData {

  /** Attachment's id number.
   *  @see org.jlab.coda.et.Attachment#id
   *  @see org.jlab.coda.et.system.AttachmentLocal#id */
  private int num;
  /** Id number of ET process that created this attachment (only relevant in C
   *  ET systems). */
  private int proc;
  /** Id number of the attachment's station.
   *  @see org.jlab.coda.et.Attachment#station
   *  @see org.jlab.coda.et.system.AttachmentLocal#station */
  private int stat;
  /** Unix process id of the program that created this attachment (only
   *  relevant in C ET systems).
   *  @see org.jlab.coda.et.system.AttachmentLocal#pid */
  private int pid;
  /** Flag indicating if this attachment is blocked waiting to read events. Its
   *  value is {@link org.jlab.coda.et.Constants#attBlocked} if blocked and
   *  {@link org.jlab.coda.et.Constants#attUnblocked} otherwise.
   *  This is not boolean for C ET system compatibility.
   *  @see org.jlab.coda.et.system.AttachmentLocal#waiting */
  private int blocked;
  /** Flag indicating if this attachment has been told to quit trying to read
   *  events and return. Its value is {@link org.jlab.coda.et.Constants#attQuit} if it has been
   *  told to quit and {@link org.jlab.coda.et.Constants#attContinue} otherwise.
   *  This is not boolean for C ET system compatibility.
   *  @see org.jlab.coda.et.system.AttachmentLocal#wakeUp */
  private int quit;
  /** The number of events owned by this attachment */
  private int eventsOwned;

  /** Number of events put back into the station.
   *  @see org.jlab.coda.et.Attachment#getEventsPut
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsPut */
  private long eventsPut;
  /** Number of events gotten from the station.
   *  @see org.jlab.coda.et.Attachment#getEventsGet
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsGet */
  private long eventsGet;

    /** Number of events dumped (recycled by returning to GRAND_CENTRAL) through
   *  the station.

   *  @see org.jlab.coda.et.Attachment#getEventsDump
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsDump */
  private long eventsDump;
  /** Number of new events gotten from the station.
   *  @see org.jlab.coda.et.Attachment#getEventsMake
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsMake */
  private long eventsMake;

  /** Name of the host running this attachment.
   *  @see org.jlab.coda.et.system.AttachmentLocal#host */
  private String host;
  /**  Name of the station this attachment is associated with. */
  private String stationName;


  // getters


  /** Get the attachment's id number.
   *  @see org.jlab.coda.et.Attachment#id
   *  @see org.jlab.coda.et.system.AttachmentLocal#id */
  public int getId() {return num;}
  /** Get the id number of ET process that created this attachment
   *  (only relevant in C ET systems). */
  public int getProc() {return proc;}
  /** Get the id number of the station to which this attachment belongs.
   *  @see org.jlab.coda.et.Attachment#station
   *  @see org.jlab.coda.et.system.AttachmentLocal#station */
  public int getStationId() {return stat;}
  /** Get the Unix process id of the program that created this attachment (only
   *  relevant in C ET systems).
   *  @see org.jlab.coda.et.system.AttachmentLocal#pid */
  public int getPid() {return pid;}
  
  /** Indicates if this attachment is blocked waiting to read events.
   *  @see org.jlab.coda.et.system.AttachmentLocal#waiting */
  public boolean blocked() {if (blocked == Constants.attBlocked) return true;
                            return false;}
  /** Indicates if this attachment has been told to quit trying to read
   *  events and return.
   *  @see org.jlab.coda.et.system.AttachmentLocal#wakeUp */
  public boolean quitting() {if (quit == Constants.attQuit) return true;
                            return false;}

  /** Get the number of events owned by this attachment */
  public int getEventsOwned() {return eventsOwned;}
  /** Get the number of events put back into the station.
   *  @see org.jlab.coda.et.Attachment#getEventsPut
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsPut */
  public long getEventsPut() {return eventsPut;}
  /** Get the number of events gotten from the station.
   *  @see org.jlab.coda.et.Attachment#getEventsGet
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsGet */
  public long getEventsGet() {return eventsGet;}
  /** Get the number of events dumped (recycled by returning to GRAND_CENTRAL)
   *  through the station.
   *  @see org.jlab.coda.et.Attachment#getEventsDump
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsDump */
  public long getEventsDump() {return eventsDump;}
  /** Get the number of new events gotten from the station.
   *  @see org.jlab.coda.et.Attachment#getEventsMake
   *  @see org.jlab.coda.et.system.AttachmentLocal#eventsMake */
  public long getEventsMake() {return eventsMake;}

  /** Get the name of the host running this attachment.
   *  @see org.jlab.coda.et.system.AttachmentLocal#host */
  public String getHost() {return host;}
  /**  Get the name of the station this attachment is associated with. */
  public String getStationName() {return stationName;}


  /**
   *  Reads the attachment information from a data stream which is sent out by
   *  an ET system over the network.
   *  @param dis data input stream
   *  @exception java.io.IOException
   *     if data stream read error
   */
  public void read(DataInputStream dis) throws IOException {
      byte[] info = new byte[68];
      dis.readFully(info);

      num = Utils.bytesToInt(info, 0);
      proc = Utils.bytesToInt(info, 4);
      stat = Utils.bytesToInt(info, 8);
      pid = Utils.bytesToInt(info, 12);
      blocked = Utils.bytesToInt(info, 16);
      quit = Utils.bytesToInt(info, 20);
      eventsOwned = Utils.bytesToInt(info, 24);
      eventsPut = Utils.bytesToLong(info, 28);
      eventsGet = Utils.bytesToLong(info, 36);
      eventsDump = Utils.bytesToLong(info, 44);
      eventsMake = Utils.bytesToLong(info, 52);

      // read strings, lengths first
      int length1 = Utils.bytesToInt(info, 60);
      int length2 = Utils.bytesToInt(info, 64);

      if (length1 + length2 > 68) {
          info = new byte[length1 + length2];
      }
      dis.readFully(info, 0, length1 + length2);
      host = new String(info, 0, length1 - 1, "ASCII");
      stationName = new String(info, length1, length2 - 1, "ASCII");
  }
}



