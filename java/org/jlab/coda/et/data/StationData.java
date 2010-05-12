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

import java.lang.*;
import java.io.*;

/**
 * This class holds all information about an station. It parses
 * the information from a stream of data sent by an ET system.
 *
 * @author Carl Timmer
 */

public class StationData {

  /** Station's id number.
   *  @see org.jlab.coda.et.Station#id
   *  @see org.jlab.coda.et.system.StationLocal#id */
  private int num;
  /** Station's status. It may have the values {@link org.jlab.coda.et.Constants#stationUnused},
   *  {@link org.jlab.coda.et.Constants#stationIdle}, or {@link org.jlab.coda.et.Constants#stationActive}.
   *  @see org.jlab.coda.et.system.StationLocal#status */
  private int status;
  /** Transfer mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked} if
   *  locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems, since in Java, mutexes cannot be tested without
   *  possibility of blocking. This is not boolean for C ET system compatibility.
   *  @see org.jlab.coda.et.system.StationLocal#stopTransfer */
  private int mutex;
  /** Number of attachments to this station.
   *  @see org.jlab.coda.et.system.StationLocal#attachments */
  private int attachments;
  /** Array of attachment id numbers. Only the first "attachments" 
   *  number of elements are meaningful. 
   *  @see org.jlab.coda.et.system.StationLocal#attachments */
  private int attIds[] = new int[Constants.attachmentsMax];

  /** Input list mutex status. It has the value {@link Constants#mutexLocked} if
   *  locked and {@link Constants#mutexUnlocked} otherwise. This is only relevant
   *  in C ET systems, since in Java, mutexes cannot be tested without the
   *  chance of blocking. This is not boolean for C ET system compatibility. */
  private int  inListMutex;
  /** Number of events in the input list.
   *  @see org.jlab.coda.et.system.EventList#events */
  private int  inListCount;
  /** Number of events that were attempted to be put into the input list. This is
   *  relevant only when there is prescaling.
   *  @see org.jlab.coda.et.system.EventList#eventsTry */
  private long inListTry;
  /** Number of events that were put into the input list.
   *  @see org.jlab.coda.et.system.EventList#eventsIn */
  private long inListIn;

  /** Output list mutex status. It has the value {@link Constants#mutexLocked} if
   *  locked and {@link Constants#mutexUnlocked} otherwise. This is only relevant
   *  in C ET systems, since in Java, mutexes cannot be tested without the
   *  chance of blocking. This is not boolean for C ET system compatibility. */
  private int  outListMutex;
  /** Number of events in the output list.
   *  @see org.jlab.coda.et.system.EventList#events */
  private int  outListCount;
  /** Number of events that were taken out of the output list.
   *  @see org.jlab.coda.et.system.EventList#eventsOut */
  private long outListOut;

  // station configuration

  /** Station configuration's flow mode.
   *  @see org.jlab.coda.et.StationConfig#userMode */
  private int flowMode;
  /** Station configuration's user mode.
   *  @see org.jlab.coda.et.StationConfig#userMode */
  private int userMode;
  /** Station configuration's restore mode.
   *  @see org.jlab.coda.et.StationConfig#restoreMode */
  private int restoreMode;
  /** Station configuration's blocking mode.
   *  @see org.jlab.coda.et.StationConfig#blockMode */
  private int blockMode;
  /** Station configuration's prescale value.
   *  @see org.jlab.coda.et.StationConfig#prescale */
  private int prescale;
  /** Station configuration's input cue size.
   *  @see org.jlab.coda.et.StationConfig#cue */
  private int cue;
  /** Station configuration's select mode.
   *  @see org.jlab.coda.et.StationConfig#selectMode */
  private int selectMode;
  /** Station configuration's select array.
   *  @see org.jlab.coda.et.StationConfig#select */
  private int select[] = new int[Constants.stationSelectInts];

  /** Name of user select function in C version ET library.
   *  @see org.jlab.coda.et.StationConfig#selectFunction */
  private String selectFunction;
  /** Name of C library containing user select function in C version ET system.
   *  @see org.jlab.coda.et.StationConfig#selectLibrary */
  private String selectLibrary;
  /** Name of Java class containing user select method in Java version ET system.
   *  @see org.jlab.coda.et.StationConfig#selectClass */
  private String selectClass;
  /** Name of station.
   *  @see org.jlab.coda.et.Station#name
   *  @see org.jlab.coda.et.system.StationLocal#name */
  private String name;

  // get methods

  /** Get the station's id number.
   *  @see org.jlab.coda.et.Station#id
   *  @see org.jlab.coda.et.system.StationLocal#id */
  public int getId() {return num;}
  /** Get the station's status. It may have the values {@link Constants#stationUnused},
   *  {@link Constants#stationIdle}, or {@link Constants#stationActive}.
   *  @see org.jlab.coda.et.system.StationLocal#status */
  public int getStatus() {return status;}
  /** Get the transfer mutex status. It has the value {@link Constants#mutexLocked}
   *  if locked and {@link Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems. */
  public int getMutex() {return mutex;}
  /** Get the number of attachments to this station.
   *  @see org.jlab.coda.et.system.StationLocal#attachments */
  public int getAttachments() {return attachments;}
  /** Get the array of attachment id numbers.
   *  @see org.jlab.coda.et.system.StationLocal#attachments */
  public int[] getAttachmentIds() {return attIds.clone();}

  /** Get the input list mutex status. It has the value {@link Constants#mutexLocked}
   *  if locked and {@link Constants#mutexUnlocked} otherwise. This is only relevant
   *  in C ET systems. */
  public int  getInListMutex() {return inListMutex;}
  /** Get the number of events in the input list.
   *  @see org.jlab.coda.et.system.EventList#events */
  public int  getInListCount() {return inListCount;}
  /** Get the number of events that were attempted to be put into the input
   *  list. This is relevant only when there is prescaling.
   * @see org.jlab.coda.et.system.EventList#eventsTry */
  public long getInListTry() {return inListTry;}
  /** Get the number of events that were put into the input list.
   *  @see org.jlab.coda.et.system.EventList#eventsIn */
  public long getInListIn() {return inListIn;}

  /** Get the output list mutex status. It has the value {@link Constants#mutexLocked}
   *  if locked and {@link Constants#mutexUnlocked} otherwise. This is only relevant
   *  in C ET systems. */
  public int  getOutListMutex() {return outListMutex;}
  /** Get the number of events in the output list.
   *  @see org.jlab.coda.et.system.EventList#events */
  public int  getOutListCount() {return outListCount;}
  /** Get the number of events that were taken out of the output list.
   *  @see org.jlab.coda.et.system.EventList#eventsOut */
  public long getOutListOut() {return outListOut;}

  // station configuration parameters ...

  /** Get the station configuration's flow mode.
   *  @see org.jlab.coda.et.StationConfig#flowMode */
  public int getFlowMode() {return flowMode;}
  /** Get the station configuration's user mode.
   *  @see org.jlab.coda.et.StationConfig#userMode */
  public int getUserMode() {return userMode;}
  /** Get the station configuration's restore mode.
   *  @see org.jlab.coda.et.StationConfig#restoreMode */
  public int getRestoreMode() {return restoreMode;}
  /** Get the station configuration's blocking mode.
   *  @see org.jlab.coda.et.StationConfig#blockMode */
  public int getBlockMode() {return blockMode;}
  /** Get the station configuration's prescale value.
   *  @see org.jlab.coda.et.StationConfig#prescale */
  public int getPrescale() {return prescale;}
  /** Get the station configuration's input cue size.
   *  @see org.jlab.coda.et.StationConfig#cue */
  public int getCue() {return cue;}
  /** Get the station configuration's select mode.
   *  @see org.jlab.coda.et.StationConfig#selectMode */
  public int getSelectMode() {return selectMode;}
  /** Get the station configuration's select array.
   *  @see org.jlab.coda.et.StationConfig#select */
  public int[] getSelect() {return select.clone();}

  /** Get the name of the user select function in the C version ET library.
   *  @see org.jlab.coda.et.StationConfig#selectFunction */
  public String getSelectFunction() {return selectFunction;}
  /** Get the name of the C library containing the user select function in
   *  the C version ET system.
   *  @see org.jlab.coda.et.StationConfig#selectLibrary */
  public String getSelectLibrary() {return selectLibrary;}
  /** Get the name of the Java class containing the user select method in
   *  the Java version ET system.
   *  @see org.jlab.coda.et.StationConfig#selectClass */
  public String getSelectClass() {return selectClass;}
  /** Get the name of the station.
   * @see org.jlab.coda.et.Station#name
   * @see org.jlab.coda.et.system.StationLocal#name */
  public String getName() {return name;}


  /**
   *  Reads the station information from a data stream which is sent out by
   *  an ET system over the network.
   *  @param dis data input stream
   *  @throws java.io.IOException
   *     if data stream read error
   */
  public void read(DataInputStream dis) throws IOException {
    attachments  = dis.readInt();
    num          = dis.readInt();
    status       = dis.readInt();
    mutex        = dis.readInt();
    for (int i=0; i < attachments; i++) {
      attIds[i]  = dis.readInt();
    }

    inListMutex  = dis.readInt();
    inListCount  = dis.readInt();
    inListTry    = dis.readLong();
    inListIn     = dis.readLong();
    outListMutex = dis.readInt();
    outListCount = dis.readInt();
    outListOut   = dis.readLong();

    flowMode     = dis.readInt();
    userMode     = dis.readInt();
    restoreMode  = dis.readInt();
    blockMode    = dis.readInt();
    prescale     = dis.readInt();
    cue          = dis.readInt();
    selectMode   = dis.readInt();
//System.out.println("station read: userMode = " + userMode + ", restore = " + restoreMode +
//", block = " + blockMode + ", pre = " + prescale + ", cue = " + cue + ", selectM = " + selectMode);
    for (int i=0; i < Constants.stationSelectInts; i++) {
      select[i]  = dis.readInt();
    }

    // read strings, lengths first
    int length1 = dis.readInt();
    int length2 = dis.readInt();
    int length3 = dis.readInt();
    int length4 = dis.readInt();
//System.out.println("station read: len 1-4 = " + length1 + ", " + length2 +
//                   ", " + length3 + ", " + length4);
    int length  = length1 + length2 + length3 + length4;

    byte[] buf = new byte[length];
    dis.readFully(buf, 0, length);
    int off = 0;

    if (length1 > 0) {
      selectFunction = new String(buf, off, length1-1, "ASCII");
      off += length1;
    }
    if (length2 > 0) {
      selectLibrary = new String(buf, off, length2-1, "ASCII");
      off += length2;
    }
    if (length3 > 0) {
      selectClass = new String(buf, off, length3-1, "ASCII");
      off += length3;
    }
    if (length4 > 0) {
      name = new String(buf, off, length4-1, "ASCII");
//System.out.println("station read: station name = " + stationName);
    }

  }
}

