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

import org.jlab.coda.et.Utils;

import java.lang.*;
import java.io.*;

/**
 * This class holds all system level information about an ET system. It parses
 * the information from a stream of data sent by an ET system.
 *
 * @author Carl Timmer
 */

public class SystemData {

  // values which can change

  /** Flag which specifying whether the ET system is alive. A value of 1 means
   *  alive and 0 means dead. */
  private int alive;
  /** Heartbeat count of the ET system process. It is not relevant in Java ET
   *  systems. */
  private int heartbeat;
  /** Count of the current amount of temporary events. It is not relevant in
   *  Java ET systems. */
  private int temps;
  /** Count of the current number of stations in the linked list (are either
   *  active or idle).
   *  @see org.jlab.coda.et.system.SystemCreate#stations */
  private int stations;
  /** Count of the current number of attachments.
   *  @see org.jlab.coda.et.system.SystemCreate#attachments */
  private int attachments;
  /** Count of the current number of processes. It is not relevant in Java ET
   *  systems. */
  private int processes;
  /** Number of events owned by the system (as opposed to attachments). */
  private int eventsOwned;

  /** System mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked} if
   *  locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems, since in Java, mutexes cannot be tested without
   *  possibility of blocking. This is not boolean for C ET system compatibility.
   *  {@link org.jlab.coda.et.system.SystemCreate#systemLock}. */
  private int mutex;
  /** Station mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked} if
   *  locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems, since in Java, mutexes cannot be tested without
   *  possibility of blocking. This is not boolean for C ET system compatibility.
   *  {@link org.jlab.coda.et.system.SystemCreate#stationLock}. */
  private int statMutex;
  /** Add-station mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked}
   *  if locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems as this mutex is not used in Java systems. */
  private int statAddMutex;

  // values which do NOT change

  /** Endian of host running the ET system. This can have values of either
   *  {@link org.jlab.coda.et.Constants#endianBig} or {@link org.jlab.coda.et.Constants#endianLittle}.
   */
  private int endian;
  /** Flag specifying whether the operating system can share mutexes between
   *  processes. It has the value {@link org.jlab.coda.et.Constants#mutexShare} if they can be
   *  shared and {@link org.jlab.coda.et.Constants#mutexNoShare} otherwise. This is not
   *  relevant in Java ET systems. */
  private int share;
  /** Unix pid of the ET system process. This is not relevant for Java ET
   *  systems, and C based ET systems on Linux may have several pids. */
  private int mainPid;
  /** The number of ints in a station's select array.
   *  @see org.jlab.coda.et.Constants#stationSelectInts */
  private int selects;
  /** Total number of events in a system.
   *  @see org.jlab.coda.et.system.SystemConfig#numEvents
   *  @see org.jlab.coda.et.system.SystemCreate#events */
  private int events;
  /** Size of "normal" events in bytes.
   *  @see org.jlab.coda.et.system.SystemConfig#eventSize  */
  private long eventSize;
  /** Is the operating system running the ET system 64 bit? */
  private boolean bit64;
  /** Maximum number of temporary events allowed in the ET system.  This is not
   *  relevant in Java ET systems. */
  private int tempsMax;
  /** Maximum number of station allowed in the ET system.
   *  @see org.jlab.coda.et.system.SystemConfig#stationsMax */
  private int stationsMax;
  /** Maximum number of attachments allowed in the ET system.
   *  @see org.jlab.coda.et.system.SystemConfig#attachmentsMax */
  private int attachmentsMax;
  /** Maximum number of processes allowed in the ET system. This is not
   *  relevant in Java ET systems. */
  private int processesMax;

  /** Port number of the ET TCP server.
   *  @see org.jlab.coda.et.system.SystemConfig#serverPort */
  private int tcpPort;
  /** Port number of the ET UDP broadcast listening thread.
   *  @see org.jlab.coda.et.system.SystemConfig#udpPort */
  private int udpPort;
  /** Port number of the ET UDP multicast listening thread.
   *  @see org.jlab.coda.et.system.SystemConfig#multicastPort */
  private int multicastPort;

  /** Number of network interfaces on the host computer. */
  private int interfaceCount;
  /** Number of multicast addresses the UDP server listens on. */
  private int multicastCount;

  /** Dotted-decimal IP addresses of network interfaces on the host. */
  private String interfaceAddresses[];
  /** Dotted-decimal multicast addresses the UDP server listens on.
   *  @see org.jlab.coda.et.system.SystemConfig#getMulticastStrings
   *  @see org.jlab.coda.et.system.SystemConfig#getMulticastAddrs
   *  @see org.jlab.coda.et.system.SystemConfig#addMulticastAddr
   *  @see org.jlab.coda.et.system.SystemConfig#removeMulticastAddr */
  private String multicastAddresses[];
  /** The ET system (file) name.
   *  @see org.jlab.coda.et.system.SystemCreate#SystemCreate
   *  @see org.jlab.coda.et.system.SystemCreate#name */
  private String etName;


  // Get methods

  /** Specifies whether the ET system is alive. */
  public boolean alive() {if (alive == 1) return true; return false;};
  /** Get the heartbeat count of the ET system process. It is not relevant
   *  in Java ET systems.*/
  public int getHeartbeat() {return heartbeat;}
  /** Get the current number of temporary events. */
  public int getTemps() {return temps;}
  /** Get the current number of stations in the linked list
   * (either active or idle). */
  public int getStations() {return stations;}
  /** Get the current number of attachments. */
  public int getAttachments() {return attachments;}
  /** Get the current number of processes. It is not relevant in Java ET
   *  systems.*/
  public int getProcesses() {return processes;}
  /** Get the number of events owned by the system (not by attachments). */
  public int getEventsOwned() {return eventsOwned;}

  /** Get the system mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked}
   *  if locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems as this mutex is not used in Java systems. */
  public int getMutex() {return mutex;}
  /** Get the station mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked}
   *  if locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems as this mutex is not used in Java systems. */
  public int getStatMutex() {return statMutex;}
  /** Get the add-station mutex status. It has the value {@link org.jlab.coda.et.Constants#mutexLocked}
   *  if locked and {@link org.jlab.coda.et.Constants#mutexUnlocked} otherwise. This is only
   *  relevant in C ET systems as this mutex is not used in Java systems. */
  public int getStatAddMutex() {return statAddMutex;}


  /** Get the endian value of the host running the ET system.  This can
   *  have values of either {@link org.jlab.coda.et.Constants#endianBig} or
   *  {@link org.jlab.coda.et.Constants#endianLittle}. */
  public int getEndian() {return endian;}
  /** Get the value specifying whether the operating system can share
   *  mutexes between processes. It has the value {@link org.jlab.coda.et.Constants#mutexShare}
   *  if they can be shared and {@link org.jlab.coda.et.Constants#mutexNoShare} otherwise.
   *  It is not relevant in Java ET systems.*/
  public int getShare() {return share;}
  /** Get the Unix pid of the ET system process. Java ET  systems return
   *  -1, and C based ET systems on Linux may have several, additional pids
   *  not given here. */
  public int getMainPid() {return mainPid;}
  /** Get the number of ints in a station's select array.
   *  @see org.jlab.coda.et.Constants#stationSelectInts */
  public int getSelects() {return selects;}
  /** Get the total number of events in a system.
   *  @see org.jlab.coda.et.system.SystemConfig#numEvents
   *  @see org.jlab.coda.et.system.SystemCreate#events */
  public int getEvents() {return events;}
  /** Get the size of "normal" events in bytes.
   *  @see org.jlab.coda.et.system.SystemConfig#eventSize  */
  public long getEventSize() {return eventSize;}
  /** Gets wether the number of bits of the operating system running the ET system
   * is 64 bits. If not, then it's 32 bits. */
  public boolean isBit64() {return bit64;}
  /** Get the maximum number of temporary events allowed in the ET system.
   *  This is not relevant in Java ET systems. */
  public int getTempsMax() {return tempsMax;}
  /** Get the maximum number of station allowed in the ET system.
   *  @see org.jlab.coda.et.system.SystemConfig#stationsMax */
  public int getStationsMax() {return stationsMax;}
  /** Get the maximum number of attachments allowed in the ET system.
   *  @see org.jlab.coda.et.system.SystemConfig#attachmentsMax */
  public int getAttachmentsMax() {return attachmentsMax;}
  /** Get the maximum number of processes allowed in the ET system.
   *  This is not relevant in Java ET systems. */
  public int getProcessesMax() {return processesMax;}

  /** Get the port number of the ET TCP server.
   *  @see org.jlab.coda.et.system.SystemConfig#serverPort */
  public int getTcpPort() {return tcpPort;}
  /** Get the port number of the ET UDP broadcast listening thread.
   *  @see org.jlab.coda.et.system.SystemConfig#udpPort */
  public int getUdpPort() {return udpPort;}
  /** Get the port number of the ET UDP multicast listening thread.
   *  @see org.jlab.coda.et.system.SystemConfig#multicastPort */
  public int getMulticastPort() {return multicastPort;}

  /** Get the number of network interfaces on the host computer. */
  public int getInterfaces() {return interfaceCount;}
  /** Get the number of multicast addresses the UDP server listens on. */
  public int getMulticasts() {return multicastCount;}

  /** Get the dotted-decimal IP addresses of network interfaces on the host. */
  public String[] getInterfaceAddresses() {return interfaceAddresses.clone();}
  /** Get the dotted-decimal multicast addresses the UDP server listens on.
   *  @see org.jlab.coda.et.system.SystemConfig#getMulticastStrings
   *  @see org.jlab.coda.et.system.SystemConfig#getMulticastAddrs
   *  @see org.jlab.coda.et.system.SystemConfig#addMulticastAddr
   *  @see org.jlab.coda.et.system.SystemConfig#removeMulticastAddr */
  public String[] getMulticastAddresses() {return multicastAddresses.clone();}
  /** Get the ET system (file) name.
   *  @see org.jlab.coda.et.system.SystemCreate#SystemCreate
   *  @see org.jlab.coda.et.system.SystemCreate#name */
  public String getEtName() {return etName;}



  /**
   *  Reads the system level information from a data stream which is sent out by
   *  an ET system over the network.
   *  @param dis data input stream
   *  @exception java.io.IOException
   *     if data stream read error
   */
  public void read(DataInputStream dis) throws IOException {
    int off = 0;
    byte[] info = new byte[108];
    dis.readFully(info);

    alive          = Utils.bytesToInt(info, off);
    heartbeat      = Utils.bytesToInt(info, off+=4);
    temps          = Utils.bytesToInt(info, off+=4);
    stations       = Utils.bytesToInt(info, off+=4);
    attachments    = Utils.bytesToInt(info, off+=4);
    processes      = Utils.bytesToInt(info, off+=4);
    eventsOwned    = Utils.bytesToInt(info, off+=4);
    mutex          = Utils.bytesToInt(info, off+=4);
    statMutex      = Utils.bytesToInt(info, off+=4);
    statAddMutex   = Utils.bytesToInt(info, off+=4);

    endian         = Utils.bytesToInt(info, off+=4);
    share          = Utils.bytesToInt(info, off+=4);
    mainPid        = Utils.bytesToInt(info, off+=4);
    selects        = Utils.bytesToInt(info, off+=4);
    events         = Utils.bytesToInt(info, off+=4);
    eventSize      = Utils.bytesToLong(info, off+=4);
    bit64          = Utils.bytesToInt(info, off+=8) == 1;

    tempsMax       = Utils.bytesToInt(info, off+=4);
    stationsMax    = Utils.bytesToInt(info, off+=4);
    attachmentsMax = Utils.bytesToInt(info, off+=4);
    processesMax   = Utils.bytesToInt(info, off+=4);

    tcpPort        = Utils.bytesToInt(info, off+=4);
    udpPort        = Utils.bytesToInt(info, off+=4);
    multicastPort  = Utils.bytesToInt(info, off+=4);

    interfaceCount = Utils.bytesToInt(info, off+=4);
    multicastCount = Utils.bytesToInt(info, off+=4);

    // read string lengths first
    off = 0;
    int lengthTotal = 0;
    int lengths[] = new int[interfaceCount+multicastCount+1];
    for (int i=0; i < interfaceCount+multicastCount+1; i++) {
      lengths[i]   = dis.readInt();
      lengthTotal += lengths[i];
    }

    if (lengthTotal > 100) {
      info = new byte[lengthTotal];
    }
    dis.readFully(info, 0, lengthTotal);

    // read network interface addresses
    interfaceAddresses = new String[interfaceCount];
    for (int i=0; i < interfaceCount; i++) {
      interfaceAddresses[i] = new String(info, off, lengths[i]-1, "ASCII");
      off += lengths[i];
    }

    // read multicast addresses
    multicastAddresses = new String[multicastCount];
    for (int i=0; i < multicastCount; i++) {
      multicastAddresses[i] = new String(info, off, lengths[i+interfaceCount]-1, "ASCII");
      off += lengths[i+interfaceCount];
    }

    // read et name
    etName = new String(info, off, lengths[interfaceCount+multicastCount]-1, "ASCII");
  }

}










