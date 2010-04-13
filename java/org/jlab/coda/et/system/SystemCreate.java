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

package org.jlab.coda.et.system;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import org.jlab.coda.et.exception.*;
import org.jlab.coda.et.*;

/**
 * This class creates an ET system.
 *
 * @author Carl Timmer
 */

public class SystemCreate {

    /** A copy of the ET system configuration. */
    private SystemConfig config;
    /** The name of the ET system file name. */
    String name;

    /** A list of stations defining the flow of events. Only the first
     *  station of a single group of parallel stations is included in
     *  this list. The other parallel stations are available in a list
     *  kept by the first parallel station. */
    private LinkedList<StationLocal> stations;            // protected by stopTransfer & systemLock
    /** The total number of idle and active stations. This consists
     * of the number of main stations given by the size of the "stations"
     * linked list (stations.size()) and the number of additional parallel
     * stations added together. */
    int stationCount;
    /** GRAND_CENTRAL station object */
    private StationLocal gcStation;
    /** Table of all ET system attachments. */
    HashMap<Integer,AttachmentLocal> attachments;         // protected by systemLock
    /** Table of all ET system events. */
    HashMap<Long, EventImpl> events;
    /** All local IP addresses */
    InetAddress[]netAddresses;
    /** Flag telling if the ET system is running. */
    private boolean running;
    /** Mutex for system stuff. */
    byte[] systemLock;

    /** Mutex for station stuff. */
    private byte[] stationLock;
    /** Flag for killing all threads started by ET system. */
    volatile boolean killAllThreads;

    // Variables for gathering system information for distribution.
    // Do it no more than once per second.

    /** Flag for specifying it's time to regather system information. */
    private boolean gather = true;
    /** Monitor time when gathering system information. */
    private long time1 = 0L;
    /** Length of valid data in array storing system information. */
    int dataLength = 0;
    /** Array for storing system information for distribution. */
    byte[] infoArray = new byte[6000];


    public LinkedList<StationLocal> getStations() {
        return stations;
    }

    public byte[] getStationLock() {
        return stationLock;
    }


    /**
     * Creates a new ET system using default parameters and starts it running.
     * The default parameters are:
     *      number of events          = {@link org.jlab.coda.et.Constants#defaultNumEvents},
     *      event size                = {@link org.jlab.coda.et.Constants#defaultEventSize},
     *      max number of stations    = {@link org.jlab.coda.et.Constants#defaultStationsMax},
     *      max number of attachments = {@link org.jlab.coda.et.Constants#defaultAttsMax},
     *      debug level               = {@link org.jlab.coda.et.Constants#debugError},
     *      udp port                  = {@link org.jlab.coda.et.Constants#broadcastPort},
     *      server (tcp) port         = {@link org.jlab.coda.et.Constants#serverPort}, and
     *      multicasting port         = {@link org.jlab.coda.et.Constants#multicastPort}
     *
     * @param _name   file name
     * @exception EtException
     *     if the file already exists or cannot be created
     */
    public SystemCreate(String _name) throws EtException {
        this(_name, new SystemConfig());
    }

    /**
     * Creates a new ET system with specified parameters and starts it running.
     *
     * @param name     file name
     * @param config   ET system configuration
     * @exception EtException
     *     if the file already exists or cannot be created
     */
    public SystemCreate (String name, SystemConfig config) throws EtException {

        // check config for consistency
        if (!config.selfConsistent()) {
            if (config.getDebug() >= Constants.debugInfo) {
                System.out.println("Number of events in groups does not equal total number of events");
            }
            throw new EtException("Number of events in groups does not equal total number of events");
        }

        this.name = name;
        this.config = new SystemConfig(config);
        attachments = new HashMap<Integer, AttachmentLocal>(Constants.attachmentsMax + 1);
        events = new HashMap<Long, EventImpl>(config.getNumEvents() + 1);
        stations = new LinkedList<StationLocal>();
        // netAddresses will be set in SystemUdpServer
        systemLock  = new byte[0];
        stationLock = new byte[0];

        // The ET name is a file (which is really irrelevant in Java)
        // but is a convenient way to make all system names unique.
        File etFile = new File(name);
        try {
            // if file already exists ...
            if (!etFile.createNewFile()) {
                if (config.getDebug() >= Constants.debugInfo) {
                    System.out.println("ET file already exists");
                }
                throw new EtException("ET file already exists");
            }
        }
        catch (IOException ex) {
            if (config.getDebug() >= Constants.debugInfo) {
                System.out.println("cannot create ET file");
            }
            throw new EtException("Cannot create ET file");
        }
        etFile.deleteOnExit();

        // Write ascii into the file indicating a JAVA ET system
        // is creating and using it. This is for the benefit of
        // C-based ET systems which may try to open and read local
        // ET system files thinking they contain shared memory.
        try {
            FileOutputStream fos = new FileOutputStream(etFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "ASCII");
            osw.write("JAVA ET SYSTEM FILE", 0, 19);
            osw.flush();
        }
        catch (FileNotFoundException ex) {
        }
        catch (UnsupportedEncodingException ex) {
        }
        catch (IOException ex) {
        }

        // store local IP addresses
        try {
            netAddresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        }
        catch (UnknownHostException ex) {
            if (config.getDebug() >= Constants.debugError) {
                System.out.println("cannot find local IP addresses");
                ex.printStackTrace();
            }
            throw new EtException("Cannot find local IP addresses");
        }

        // start things running
        startUp();
    }


    /** Gets the ET system file name.
     *  @return ET system file name */
    public String getName() {return name;}

    /** Gets the ET system configuration.
     *  @return ET system configuration */
    public SystemConfig getConfig() {return new SystemConfig(config);}

    /** Tells if the ET system is running or not.
     *  @return <code>true</code> if the system is running and <code>false</code>
     *  if it is not */
    synchronized public boolean running() {return running;}


    /** Starts the ET system running. If the system is already running, nothing
     * is done. */
    synchronized public void startUp() {
        if (running) return;

        // make grandcentral
        gcStation = createGrandCentral();

        // fill GC with standard sized events
        EventImpl ev;
        int index = 0, count = 0;
        ArrayList<EventImpl> eventList = new ArrayList<EventImpl>(config.getNumEvents());

        for (long i=0; i < config.getNumEvents(); i++) {
            ev = new EventImpl(config.getEventSize());
            ev.setId(i);

            // assign group numbers
            if (count < 1)
                count = (config.getGroups())[index++];
            ev.setGroup(index);
            count--;

            eventList.add(ev);
            // add to hashTable for future easy access
            events.put(ev.getId(), ev);
        }

        gcStation.getInputList().putInLow(eventList);
        // undo statistics keeping for inital event loading
        gcStation.getInputList().setEventsIn(0);

        // run tcp server thread
        SystemTcpServer tcpServer = new SystemTcpServer(this);
        tcpServer.start();

        // run udp listening thread
        SystemUdpServer udpServer = new SystemUdpServer(this);
        udpServer.start();

        running = true;
    }


    /** Stops the ET system if it is running. All threads are stopped.
     * If the system is not running, nothing is done. */
    synchronized public void shutdown() {
        if (!running) return;
        // tell threads to kill themselves
        killAllThreads = true;
        // sockets on 2 second timeout so wait
        try {Thread.sleep(2500);}
        catch (InterruptedException ex) {}
        // delete file
        File etFile = new File(name);
        etFile.delete();

        // clear everything
        stations       = null;
        attachments    = null;
        events         = null;
        netAddresses   = null;
        stationLock    = null;
        killAllThreads = false;
        running        = false;
    }

    //---------------------------------------------------------------------------
    // Station related methods, mainly to manipulate the linked lists of stations
    //---------------------------------------------------------------------------

    /**
     * This method locks the stopTransfer locks of all existing stations which ensures no events
     * are currently being moved.
     */
    private void lockAllStationTransferLocks() {
        for (StationLocal mainListStation : stations) {
            // lock station in main linked list
            mainListStation.getStopTransferLock().lock();
            // if this station is the head of a parallel linked list, grab all their locks too
            if (mainListStation.getConfig().getFlowMode() == Constants.stationParallel) {
                // skip first element in parallel list as it is identical to mainListStation
                StationLocal parallelListStation;
                for (ListIterator iter = mainListStation.getParallelStations().listIterator(1); iter.hasNext();) {
                    parallelListStation = (StationLocal) iter.next();
                    parallelListStation.getStopTransferLock().lock();
                }
            }
        }
    }


    /**
     * This method unlocks the stopTransfer locks of all existing stations which means events
     * are now allowed to be moved.
     */
    private void unlockAllStationTransferLocks() {
        for (StationLocal mainListStation : stations) {
            // unlock station in main linked list
            mainListStation.getStopTransferLock().unlock();
            // if this station is the head of a parallel linked list, release all their locks too
            if (mainListStation.getConfig().getFlowMode() == Constants.stationParallel) {
                // skip first element in parallel list as it is identical to mainListStation
                StationLocal parallelListStation;
                for (ListIterator iter = mainListStation.getParallelStations().listIterator(1); iter.hasNext();) {
                    parallelListStation = (StationLocal) iter.next();
                    parallelListStation.getStopTransferLock().unlock();
                }
            }
        }
    }


    /**
     * Method used to add a new station to all the relevant linked lists of stations.
     *
     * @param newStation station object
     * @param position the desired position in the main linked list of stations
     * @param parallelPosition the desired position of a parallel station in the
     *      group of parallel stations it's being added to
     * @exception EtException
     *     if trying to add an incompatible parallel station to an existing group
     *     of parallel stations or to the head of an existing group of parallel
     *     stations.
     */
    private void insertStation(StationLocal newStation, int position, int parallelPosition) throws EtException {

        // If GRAND_CENTRAL is only existing station, or if we're at
        // or past the end of the linked list, put station on the end
        if ((stations.size() < 2) ||
                (position >= stations.size()) ||
                (position == Constants.end)) {

            stations.add(newStation);
            if (newStation.getConfig().getFlowMode() == Constants.stationParallel) {
                newStation.getParallelStations().clear();
                newStation.getParallelStations().add(newStation);
            }
        }
        // else, put the station in the desired position in the middle somewhere
        else {
            StationLocal stat = stations.get(position);

            // if the station in "position" and this station are both parallel ...
            if ((newStation.getConfig().getFlowMode() == Constants.stationParallel) &&
                    (stat.getConfig().getFlowMode() == Constants.stationParallel) &&
                    (parallelPosition != Constants.newHead)) {

                // If these 2 stations have incompatible definitions or we're trying to place
                // a parallel station in the first (already taken) spot of its group ...
                if (!StationConfig.compatibleParallelConfigs(stat.getConfig(), newStation.getConfig())) {
                    throw new EtException("trying to add incompatible parallel station\n");
                }
                else if (parallelPosition == 0) {
                    throw new EtException("trying to add parallel station to head of existing parallel group\n");
                }

                // Add this parallel station in the "parallelPosition" slot in the
                // parallel linked list or to the end if parallelPosition = Constants.end.
                if ((parallelPosition == Constants.end) ||
                        (parallelPosition >= stat.getParallelStations().size())) {
                    stat.getParallelStations().add(newStation);
                }
                else {
                    stat.getParallelStations().add(parallelPosition, newStation);
                }
            }
            else {
                stations.add(position, newStation);
                if (newStation.getConfig().getFlowMode() == Constants.stationParallel) {
                    newStation.getParallelStations().clear();
                    newStation.getParallelStations().add(newStation);
                }
            }
        }
    }


    /**
     * Method used to remove a station from all relevant linked lists of stations.
     * @param station station object
     */
    private void deleteStation(StationLocal station) {
        // The only tricky part in removing a station is to remember that it may not
        // be in the main linked list if it is a parallel station.

        // if the station is in the main linked list ...
        if (stations.contains(station)) {

            // remember where the station was located
            int index = stations.indexOf(station);

            // remove it from main list
            stations.remove(station);

            // if it's not a parallel station, we're done
            if (station.getConfig().getFlowMode() == Constants.stationSerial) {
                return;
            }

            // if the station is parallel, it's the head of another linked list.
            station.getParallelStations().removeFirst();

            // if no other stations in the group, we're done
            if (station.getParallelStations().size() < 1) {
                return;
            }

            // If there are other stations in the group, make sure that the linked
            // list of parallel stations is passed on to the next member. And put
            // the new head of the parallel list into the main list.
            StationLocal nextStation = station.getParallelStations().getFirst();
            nextStation.getParallelStations().clear();
            nextStation.getParallelStations().addAll(station.getParallelStations());
            station.getParallelStations().clear();
            stations.add(index, nextStation);
        }

        // else if it's not in the main linked list, we'll have to hunt it down
        else {
            // loop thru all stations in main list
            for (StationLocal nextStation : stations) {
                // If it's a parallel station, try to remove "station" from the
                // list of parallel stations registered with it.
                if (nextStation.getConfig().getFlowMode() == Constants.stationParallel) {
                    if (nextStation.getParallelStations().remove(station)) {
                        // we got it
                        return;
                    }
                }
            }
        }
    }


    /**
     * Method for use by {@link #createStation} to grab all stations'
     * transfer locks and stop all event transfer before adding a new station to
     * the ET system's linked lists of stations.
     *
     * @param newStation station to add
     * @param position the desired position in the main linked list of stations
     * @param parallelPosition the desired position of a parallel station in the
     *     group of parallel stations it's being added to
     * @exception EtException
     *     if trying to add an incompatible parallel station to an existing group
     *     of parallel stations or to the head of an existing group of parallel
     *     stations.
     */
   private void addStationToList(StationLocal newStation, int position, int parallelPosition) throws EtException {
        lockAllStationTransferLocks();
        try {
            insertStation(newStation, position, parallelPosition);
            // since we locked all stations' transfer locks, do so with the new one too
            newStation.getStopTransferLock().lock();
        }
        finally {
            unlockAllStationTransferLocks();
        }
    }


    /**
     * Method for use by {@link #removeStation} to grab all stations'
     * transfer locks and stop all event transfer before removing a station from
     * the ET system's linked lists of stations.
     * 
     * @param station station to remove
     */
    private void removeStationFromList(StationLocal station) {
        lockAllStationTransferLocks();
        try {
            deleteStation(station);
            // since we will unlock all stations' transfer locks, do so with the new one too
            station.getStopTransferLock().unlock();
        }
        finally {
            unlockAllStationTransferLocks();
        }
    }


    /**
     * Method for use by {@link #removeStationFromList} to grab all stations'
     * transfer locks and stop all event transfer before moving a station in
     * the ET system's linked lists of stations.
     *
     * @param station station to move
     * @param position the desired position in the main linked list of stations
     * @param parallelPosition the desired position of a parallel station in the
     *      group of parallel stations it's being added to
     * @exception EtException
     *     if trying to move an incompatible parallel station to an existing group
     *     of parallel stations or to the head of an existing group of parallel
     *     stations.
     */
    private void moveStationInList(StationLocal station, int position, int parallelPosition) throws EtException {
        lockAllStationTransferLocks();
        try {
            deleteStation(station);
            insertStation(station, position, parallelPosition);
        }
        finally {
            unlockAllStationTransferLocks();
        }
    }


    /**
     * Method for use by {@link #deleteStation(StationLocal)} and {@link #detach(AttachmentLocal)}
     * to grab all stations' transfer locks and stop all event transfer before changing a station's status.
     *
     * @param station station to set status on
     * @param status the desired status of the station
     */
    void changeStationStatus(StationLocal station, int status) {
        lockAllStationTransferLocks();
        try {
            station.setStatus(status);
        }
        finally {
            unlockAllStationTransferLocks();
        }
    }

    
    //
    // Done with station manipulation methods
    //


    /**
     * Creates a new station placed at the end of the linked list of stations.
     *
     * @param stationConfig   station configuration
     * @param name            station name
     *
     * @return the new station object
     *
     * @exception EtException
     *     if the select method's class cannot be loaded
     * @exception EtExistsException
     *     if the station already exists but with a different configuration
     * @exception EtTooManyException
     *     if the maximum number of stations has been created already
     */
    public StationLocal createStation(StationConfig stationConfig, String name)
            throws EtException, EtExistsException, EtTooManyException {
        synchronized(stationLock) {
            return createStation(stationConfig, name, stations.size(), Constants.end);
        }
    }


    /**
     * Creates a new station at a specified position in the linked list of
     * stations. Cannot exceed the maximum number of stations allowed in a system.
     *
     * @param stationConfig   station configuration
     * @param name            station name
     * @param position        position in the linked list to put the station.
     *
     * @return                the new station object
     *
     * @exception EtException
     *     if the select method's class cannot be loaded
     * @exception EtExistsException
     *     if the station already exists but with a different configuration
     * @exception EtTooManyException
     *     if the maximum number of stations has been created already
     */
    StationLocal createStation(StationConfig stationConfig, String name,
                               int position, int parallelPosition)
            throws EtException, EtExistsException, EtTooManyException {


        int id = 0;
        StationLocal station;

        // grab station mutex
        synchronized (stationLock) {
            // check to see if hit maximum allowed # of stations
            if (stations.size() >= config.getStationsMax()) {
                throw new EtTooManyException("Maximum number of stations already created");
            }
            else if (position > stations.size()) {
                position = stations.size();
            }

            // check to see if it already exists
            StationLocal listStation;
            try {
                listStation = stationNameToObject(name);
                StationConfig listStationConfig = listStation.getConfig();
                // it's got the same name, let's see if it's defined the same
                if ((listStationConfig.getFlowMode()    == stationConfig.getFlowMode())    &&
                    (listStationConfig.getUserMode()    == stationConfig.getUserMode())    &&
                    (listStationConfig.getBlockMode()   == stationConfig.getBlockMode())   &&
                    (listStationConfig.getSelectMode()  == stationConfig.getSelectMode())  &&
                    (listStationConfig.getRestoreMode() == stationConfig.getRestoreMode()) &&
                    (listStationConfig.getPrescale()    == stationConfig.getPrescale())    &&
                    (listStationConfig.getCue()         == stationConfig.getCue())         &&
                    (Arrays.equals(listStationConfig.getSelect(), stationConfig.getSelect()))) {

                    if ((listStationConfig.getSelectClass() != null) &&
                            (!listStationConfig.getSelectClass().equals(stationConfig.getSelectClass()))) {
                        throw new EtExistsException("Station already exists with different configuration");
                    }
                    // station definitions are the same, use listStation
                    return listStation;
                }
                throw new EtExistsException("Station already exists with different configuration");
            }
            catch (EtException ex) {
                // station does NOT exist, continue on
            }

            // find smallest possible unique id number
            search:
            for (int i = 0; i < stationCount + 1; i++) {
                for (ListIterator j = stations.listIterator(); j.hasNext();) {
                    listStation = (StationLocal) j.next();
                    if (listStation.getStationId() == i) {
                        continue search;
                    }
                    if (listStation.getConfig().getFlowMode() == Constants.stationParallel) {
                        for (ListIterator k = listStation.getParallelStations().listIterator(1); k.hasNext();) {
                            listStation = (StationLocal) k.next();
                            if (listStation.getStationId() == i) {
                                continue search;
                            }
                        }
                    }
                }
                // only get down here if "i" is not a used id number
                id = i;
                break;
            }

            // create station
            station = new StationLocal(this, name, stationConfig, id);

            // start its conductor thread
            station.start();
            // give up processor so thread can start
            Thread.yield();

            // make sure the conductor is started or we'll get race conditions
            while (station.getStatus() != Constants.stationIdle) {
                if (config.getDebug() >= Constants.debugInfo) {
                    System.out.println("Waiting for " + name + "'s conductor thread to start");
                }
                // sleep for minimum amount of time (1 nsec haha)
                try {
                    Thread.sleep(0, 1);
                }
                catch (InterruptedException ex) {
                }
            }

            // put in linked list(s) - first grabbing stopTransfer mutexes
            addStationToList(station, position, parallelPosition);
            // keep track of the total number of stations
            stationCount++;
        } // release station mutex

        return station;
    }


    /**
     * Creates the first station by the name of GRAND_CENTRAL and starts its
     * conductor thread.
     *
     * @return GRAND_CENTRAL station's object
     */
    private StationLocal createGrandCentral() {
        // use the default configuration
        StationConfig gcConfig = new StationConfig();
        StationLocal station = null;
        // create station
        try {
            station = new StationLocal(this, "GRAND_CENTRAL", gcConfig, 0);
        }
        catch (EtException ex) {}

        // put in linked list
        stations.clear();
        stations.addFirst(station);

        // start its conductor thread
        station.start();

        // keep track of the total number of stations
        stationCount++;
        return station;
    }

    
    /**
     * Removes an existing station.
     *
     * @param   statId station id
     * @exception EtException
     *     if attachments to the station still exist or the station does not exist
     */
    void removeStation(int statId) throws EtException {
        StationLocal stat;
        // grab station mutex
        synchronized(stationLock) {
            stat = stationIdToObject(statId);
            // only remove if no attached processes
            if (stat.getAttachments().size() != 0) {
                throw new EtException("Remove all attachments before removing station");
            }

            // remove from linked list - first grabbing stopTransfer mutexes
            removeStationFromList(stat);

            // kill conductor thread
            stat.killConductor();
            stat.interrupt();

            // set status
            stat.setStatus(Constants.stationUnused);

            // keep track of the total number of stations
            stationCount--;
            return;
        }
    }

  /**
   * Changes the position of a station in the linked lists of stations.
   *
   * @param statId     station id
   * @param position   position in the main linked list of stations (starting at 0)
   * @param parallelPosition position of a parallel station in a group of
   *     parallel stations (starting at 0)
   * @exception EtException
   *     if the station does not exist, or
   *     if trying to move an incompatible parallel station to an existing group
   *     of parallel stations or to the head of an existing group of parallel
   *     stations.
   */
  void setStationPosition(int statId, int position, int parallelPosition)
                                                          throws EtException {
    StationLocal stat;
    // grab station mutex
    synchronized(stationLock) {
      stat = stationIdToObject(statId);
      // change linked list - first grabbing stopTransfer mutexes
      moveStationInList(stat, position, parallelPosition);
    }
  }

  /**
   * Gets the position of a station in the main linked list of stations.
   *
   * @param statId   station id
   * @return         the position of a station in the linked list of stations
   * @exception EtException
   *     if the station does not exist
   */
  int getStationPosition(int statId) throws EtException {
      // GrandCentral is always first
      if (statId == 0) return 0;
      int position = 0;

      synchronized (stationLock) {
          for (StationLocal stat : stations) {
              if (stat.getStationId() == statId) {
                  return position;
              }
              if (stat.getConfig().getFlowMode() == Constants.stationParallel) {
                  for (StationLocal stat2 : stat.getParallelStations()) {
                      if (stat2.getStationId() == statId) {
                          return position;
                      }
                  }
              }
              position++;
          }
      }
      throw new EtException("cannot find station");
  }

  /**
   * Gets the position of a parallel station in its linked list of 
   * parallel stations.
   *
   * @param statId   station id
   * @return         the position of a parallel station in its linked list
   *      of parallel stations, or zero if station is serial
   * @exception EtException
   *     if the station does not exist
   */
  int getStationParallelPosition(int statId) throws EtException {
      // parallel position is 0 for serial stations
      if (statId == 0) return 0;
      int pposition;

      synchronized (stationLock) {
          for (StationLocal stat : stations) {
              if (stat.getStationId() == statId) {
                  return 0;
              }
              if (stat.getConfig().getFlowMode() == Constants.stationParallel) {
                  pposition = 1;
                  for (StationLocal stat2 : stat.getParallelStations()) {
                      if (stat2.getStationId() == statId) {
                          return pposition;
                      }
                      pposition++;
                  }
              }
          }
      }
      throw new EtException("cannot find station");
  }

  /**
   * Tells if an attachment is attached to a station.
   *
   * @param statId   station id
   * @param attId    attachment id
   * @return         <code>true</code> if an attachment is attached to a station
   *                 and <code>false</code> otherwise
   * @exception EtException
   *     if the station does not exist
   */
  boolean stationAttached(int statId, int attId) throws EtException {
      StationLocal stat;
      synchronized (stationLock) {
          stat = stationIdToObject(statId);
          for (AttachmentLocal att : stat.getAttachments()) {
              if (att.getId() == attId) {
                  return true;
              }
          }
          return false;
      }
  }

    /**
   * Tells if a station exists.
   *
   * @param station station object
   * @return <code>true</code> if a station exists and
   *         <code>false</code> otherwise
   */
    boolean stationExists(StationLocal station) {
        synchronized (stationLock) {
            if (stations.contains(station)) {
                return true;
            }
            for (StationLocal listStation : stations) {
                if (listStation.getConfig().getFlowMode() == Constants.stationParallel) {
                    if (listStation.getParallelStations().contains(station)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

  /**
   * Tells if a station exists.
   *
   * @param    name station name
   * @return   <code>true</code> if a station exists and
   *           <code>false</code> otherwise
   */
  boolean stationExists(String name) {
      try {
          stationNameToObject(name);
      }
      catch (EtException ex) {
          return false;
      }
      return true;
  }

  /**
   * Gets a station's object representation.
   *
   * @param    name station name
   * @return   a station's object
   * @exception EtException
   *     if the station does not exist
   */
  StationLocal stationNameToObject(String name) throws EtException {
      synchronized (stationLock) {
          for (StationLocal listStation : stations) {
              if (listStation.getStationName().equals(name)) {
                  return listStation;
              }
              if (listStation.getConfig().getFlowMode() == Constants.stationParallel) {
                  for (StationLocal listStation2 : listStation.getParallelStations()) {
                      if (listStation2.getStationName().equals(name)) {
                          return listStation2;
                      }
                  }
              }
          }
      }
      throw new EtException("station " + name + " does not exist");
  }

  /**
   * Given a station id number, this method gets the corresponding
   * StationLocal object.
   *
   * @param     statId station id
   * @return    the station's object
   * @exception EtException
   *     if the station does not exist
   */
  StationLocal stationIdToObject(int statId) throws EtException {
      synchronized (stationLock) {
          for (StationLocal stat : stations) {
              if (stat.getStationId() == statId) {
                  return stat;
              }
              if (stat.getConfig().getFlowMode() == Constants.stationParallel) {
                  for (StationLocal stat2 : stat.getParallelStations()) {
                      if (stat2.getStationId() == statId) {
                          return stat2;
                      }
                  }
              }
          }
      }
      throw new EtException("station  with id \"" + statId + "\" does not exist");
  }

  //
  // attachment related methods
  //

  /**
   * Create an attachment to a station.
   *
   * @param statId   station id
   * @return         an attachment object
   * @exception EtException
   *     if the station does not exist
   * @exception EtTooManyException
   *     if station does not exist, or
   *     if no more attachments are allowed to the station, or
   *     if no more attachments are allowed to ET system
   */
  AttachmentLocal attach(int statId) throws EtException, EtTooManyException {

      AttachmentLocal att;
      synchronized (stationLock) {
          StationLocal station = stationIdToObject(statId);

          // limit on # of attachments to station
          if ((station.getConfig().getUserMode() > 0) &&
                  (station.getConfig().getUserMode() <= station.getAttachments().size())) {
              throw new EtTooManyException("no more attachments allowed to station");
          }

          synchronized (systemLock) {
              // limit on number of attachments to ET system
              if (attachments.size() >= config.getAttachmentsMax()) {
                  throw new EtTooManyException("no more attachments allowed to ET system");
              }

              // Server will overwrite id & host with true (remote) values
              att = new AttachmentLocal();
              att.setStation(station);
              // find smallest possible unique id number
              if (attachments.size() == 0) {
                  att.setId(0);
              }
              else {
                  search:
                  for (int i = 0; i < attachments.size() + 1; i++) {
                      for (Integer j : attachments.keySet()) {
                          if (j == i) continue search;
                      }
                      // only get down here if "i" is not a used id number
                      att.setId(i);
                      break;
                  }
              }
              //att.status = Constants.attActive;
//System.out.println("attach att #" + att.id + " will put into system's map");
              attachments.put(att.getId(), att);
          }

          // keep att stats in station too?
          station.getAttachments().add(att);
          // station.status = Constants.stationActive;
          // change station status - first grabbing stopTransfer mutexes
          changeStationStatus(station, Constants.stationActive);
//System.out.println("attach att #" + att.id + " put into station's map & active");
      }

      return att;
  }


  /**
   * Remove an attachment from a station.
   *
   * @param att   attachment object
   */
  void detach(AttachmentLocal att) {
//System.out.println("detach: IN");
        synchronized (stationLock) {
            // if last attachment & not GrandCentral - mark station idle
            if ((att.getStation().getAttachments().size() == 1) && (att.getStation().getStationId() != 0)) {
//System.out.println("detach: att #" + att.id + " and make idle");
                // att.station.status = Constants.stationIdle;
                // change station status - first grabbing stopTransfer mutexes
                changeStationStatus(att.getStation(), Constants.stationIdle);
                // give other threads a chance to finish putting events in
                Thread.yield();
                // flush any remaining events
                if (att.getStation().getConfig().getRestoreMode() == Constants.stationRestoreRedist) {
                    // send to output list of previous station
                    try {
                        int pos = getStationPosition(att.getStation().getStationId());
                        if (--pos < 0) return;
                        StationLocal prevStat = stations.get(pos);
                        try {
                            moveEvents(prevStat.getOutputList(), Arrays.asList(getEvents(att, Constants.async,
                                                                                         0, config.getNumEvents())));
                        }
                        catch (Exception e) {
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
                else {
                    // send to output list
                    try {
                        putEvents(att, getEvents(att, Constants.async, 0, config.getNumEvents()));
                    }
                    catch (Exception ex) { }
                }
            }
//System.out.println("detach att #" + att.id + " remove from station map");
            att.getStation().getAttachments().remove(att);

            // restore events gotten but not put back into system
            restoreEvents(att);

            synchronized (systemLock) {
                // get rid of attachment
//System.out.println("detach att #" + att.id + " remove from system map");
                attachments.remove(new Integer(att.getId()));
            }
        }
        return;
    }


  /**
   * Restore events gotten by an attachment but lost when its network connection
   * was broken. These events are not guaranteed to be restored in any
   * particular order.
   *
   * @param att   attachment object
   */
  private void restoreEvents(AttachmentLocal att) {
      // Split new events (which should be dumped) from used
      // events which go where directed by station configuration.
      ArrayList<EventImpl> usedEvs = new ArrayList<EventImpl>(config.getNumEvents());
      ArrayList<EventImpl>  newEvs = new ArrayList<EventImpl>(config.getNumEvents());
//System.out.println("into restoreEvents");

      // look at all events
      for (EventImpl ev : events.values()) {
          // find those owned by this attachment
          if (ev.getOwner() == att.getId()) {
              if (ev.getAge() == Constants.eventNew) {
//System.out.println("found new ev " + ev.id + " owned by attachment " + att.id);
                  newEvs.add(ev);
              }
              else {
                  // Put high priority events first.
                  // Original order may get messed up here.
//System.out.println("found used ev " + ev.id + " owned by attachment " + att.id);
                  if (ev.getPriority() == Constants.high) {
                      usedEvs.add(0, ev);
                  }
                  else {
                      usedEvs.add(ev);
                  }
              }
          }
      }

      if (newEvs.size() > 0) {
//System.out.println("dump " + newEvs.size() + " new events");
          dumpEvents(att, newEvs);
      }

      if (usedEvs.size() > 0) {
//System.out.println("restore " + usedEvs.size() + " used events");
          // if normal events are to be returned to GrandCentral
          if ((att.getStation().getConfig().getRestoreMode() == Constants.stationRestoreGC) ||
                  (att.getStation().getStationId() == 0)) {
//System.out.println("restore used events to GC (dump)");
              dumpEvents(att, usedEvs);
          }
          // Else if events are to be returned to station's outputList ...
          // Notice that if we are supposed to put things in the
          // inputList, but we are the last attachement (and in the
          // middle of detaching), then events put into the inputList
          // will be lost to the system. Place them into the outputList.
          else if ((att.getStation().getConfig().getRestoreMode() == Constants.stationRestoreOut) ||
                  ((att.getStation().getConfig().getRestoreMode() == Constants.stationRestoreIn) &&
                          (att.getStation().getAttachments().size() == 0))) {
//System.out.println("restore used events to output list");
              putEvents(att, usedEvs);
          }
          else if (att.getStation().getConfig().getRestoreMode() == Constants.stationRestoreIn) {
              // If the station is blocking, its inputList has room for all
              // the events and there's no problem putting them all.
              // Statistics don't get messed up here.
              if (att.getStation().getConfig().getBlockMode() == Constants.stationBlocking) {
//System.out.println("restore used events to input list of blocking station");
                  moveEvents(att.getStation().getInputList(), usedEvs);
              }
              // Else if nonblocking there may not be enough room.
              // Equivalent to putting events back into the station's inputList
              // is to put them into the previous station' outputList and letting
              // its conductor thread do the work - which is easy to program.
              // This has the unfortunate side effect of probably messing
              // up the statistics as some events may be counted twice.
              else {
                  try {
                      // Find previous station
                      int pos = getStationPosition(att.getStation().getStationId());
                      if (--pos < 0) return;
                      StationLocal prevStat = stations.get(pos);
                      moveEvents(prevStat.getOutputList(), usedEvs);
                  }
                  catch (EtException e) { return; }
//System.out.println("restore used events to input list of nonblocking station");
              }
          }
          else if (att.getStation().getConfig().getRestoreMode() == Constants.stationRestoreRedist) {
              // Put events into the previous station's outputList and let its
              // conductor thread do the work - redistributing them to the members
              // of the parallel station group.
              // This has the unfortunate side effect of probably messing
              // up the statistics as some events may be counted twice.
              try {
//System.out.println("TRY to restore used events to output list of previous station");
                  int pos = getStationPosition(att.getStation().getStationId());
                  if (--pos < 0) return;
                  StationLocal prevStat = stations.get(pos);
//System.out.println("Found previous station -> " + prevStat.name + ", putting " + usedEvs.size() + " number of events");
                  moveEvents(prevStat.getOutputList(), usedEvs);
              }
              catch (Exception e) { return; }
//System.out.println("DID restore used events to output list of previous station");
          }
      }
      return;
  }

  //
  // event related methods
  //

  /**
   * Get new or unused events from an ET system.
   *
   * @param att       attachment object
   * @param mode      if there are no events available, this parameter specifies
   *                  whether to wait for some by sleeping, by waiting for a set
   *                  time, or by returning immediately (asynchronous)
   * @param microSec  the number of microseconds to wait if a timed wait is
   *                  specified
   * @param count     the number of events desired
   * @param size      the size of events in bytes
   *
   * @return an array of events
   *
   * @exception EtEmptyException
   *     if the mode is asynchronous and the station's input list is empty
   * @exception EtBusyException
   *     if the mode is asynchronous and the station's input list is being used
   *     (the mutex is locked)
   * @exception EtTimeoutException
   *     if the mode is timed wait and the time has expired
   * @exception EtWakeUpException
   *     if the attachment has been commanded to wakeup,
   *     {@link EventList#wakeUp}, {@link EventList#wakeUpAll}
   */
  EventImpl[] newEvents(AttachmentLocal att, int mode, int microSec, int count, int size)
                    throws EtEmptyException, EtBusyException,
                           EtTimeoutException, EtWakeUpException {

//System.out.println("newEvents: get " + count + " events");

    // get events from GrandCentral Station's output list
    EventImpl[] evs = gcStation.getInputList().get(att, mode, microSec, count);
//System.out.println("newEvents: got events");

    // for each event ...
    for (EventImpl ev : evs) {
      // initialize fields
      ev.init();
      // registered as owned by this attachment
      ev.setOwner(att.getId());
      // if size is too small make it larger
      if (ev.getMemSize() < size) {
          try {
              ev.setData(new byte[size]);
          }
          catch (EtException e) { }  // TODO: is this kosher??
          ev.setMemSize(size);
      }
//System.out.println("newEvents: ev.id = "+ ev.id + ", size = " + ev.memSize);
    }

    // keep track of # of events made by this attachment
    att.setEventsMake(att.getEventsMake() + evs.length);
//System.out.println("newEvents: att.eventsMake = "+ att.eventsMake);
    return evs;
  }




    /**
     * Get new or unused events from an ET system.
     *
     * @param att       attachment object
     * @param mode      if there are no events available, this parameter specifies
     *                  whether to wait for some by sleeping, by waiting for a set
     *                  time, or by returning immediately (asynchronous)
     * @param microSec  the number of microseconds to wait if a timed wait is
     *                  specified
     * @param count     the number of events desired
     * @param size      the size of events in bytes
     * @param group     the group number of events
     *
     * @return a list of events
     *
     * @exception EtException
     *     if the group number is not meaningful
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     *     {@link EventList#wakeUp}, {@link EventList#wakeUpAll}
     */
    List<EventImpl> newEvents(AttachmentLocal att, int mode, int microSec, int count, int size, int group)
                      throws EtException, EtEmptyException, EtBusyException,
                             EtTimeoutException, EtWakeUpException {

//System.out.println("newEvents: try getting " + count + " events of group # " + group);
      // check to see if value of group is meaningful
      if (group > config.getGroups().length) {
          throw new EtException("group number is too high");
      }

      // get events from GrandCentral Station's output list
      List<EventImpl> evs = gcStation.getInputList().get(att, mode, microSec, count, group);
//System.out.println("newEvents: got events (# = " + evs.size() + ")");

      // for each event ...
      for (EventImpl ev : evs) {
        // initialize fields
        ev.init();
        // registered as owned by this attachment
        ev.setOwner(att.getId());
        // if size is too small make it larger
        if (ev.getMemSize() < size) {
          ev.setData(new byte[size]);
          ev.setMemSize(size);
        }
//System.out.println("newEvents: ev.id = "+ ev.id + ", size = " + ev.memSize + ", group = " + ev.group);
      }

      // keep track of # of events made by this attachment
      att.setEventsMake(att.getEventsMake() + evs.size());
//System.out.println("newEvents: att.eventsMake = "+ att.eventsMake);
      return evs;
    }




  /**
   * Get events from an ET system.
   *
   * @param att      attachment object
   * @param mode     if there are no events available, this parameter specifies
   *                 whether to wait for some by sleeping, by waiting for a set
   *                 time, or by returning immediately (asynchronous)
   * @param microSec the number of microseconds to wait if a timed wait is
   *                 specified
   * @param count    the number of events desired
   *
   * @return an array of events
   *
   * @exception EtEmptyException
   *     if the mode is asynchronous and the station's input list is empty
   * @exception EtBusyException
   *     if the mode is asynchronous and the station's input list is being used
   *     (the mutex is locked)
   * @exception EtTimeoutException
   *     if the mode is timed wait and the time has expired
   * @exception EtWakeUpException
   *     if the attachment has been commanded to wakeup,
   *     {@link EventList#wakeUp}, {@link EventList#wakeUpAll}
   */
  EventImpl[] getEvents(AttachmentLocal att, int mode, int microSec, int count)
                      throws EtEmptyException, EtBusyException,
                             EtTimeoutException, EtWakeUpException {

    EventImpl[] evs = att.getStation().getInputList().get(att, mode, microSec, count);

    // each event is registered as owned by this attachment
    for (EventImpl ev : evs) {
      ev.setOwner(att.getId());
    }

    // keep track of # of events gotten by this attachment
    att.setEventsGet(att.getEventsGet() + evs.length);

    return evs;
  }



  /**
   * Place events into a station's input or output list.
   * Used when moving events stranded at a station due to
   * the last attachment having crashed. Does not change statistics.
   *
   * @param list        station's input or output list into which events are placed
   * @param eventList   list of event objects
   *
   */
  private void moveEvents(EventList list, List<EventImpl> eventList) {
    // mark events as used and as owned by system
    for (EventImpl ev : eventList) {
      ev.setAge(Constants.eventUsed);
      ev.setOwner(Constants.system);
    }

    list.putReverse(eventList);

    return;
  }




    /**
     * Put events into an ET system.
     *
     * @param att          attachment object
     * @param eventArray   array of event objects
     *
     */
    void putEvents(AttachmentLocal att, EventImpl[] eventArray) {
      // mark events as used and as owned by system
      for (EventImpl ev : eventArray) {
//System.out.println("putEvents: set age & owner of event " + i);
          ev.setAge(Constants.eventUsed);
          ev.setOwner(Constants.system);
      }

      att.getStation().getOutputList().put(eventArray);
      // keep track of # of events put by this attachment
      att.setEventsPut(att.getEventsPut() + eventArray.length);

      return;
    }




  /**
   * Put events into an ET system.
   *
   * @param att         attachment object
   * @param eventList   list of event objects
   *
   */
  private void putEvents(AttachmentLocal att, ArrayList<EventImpl> eventList) {
//System.out.println("putEvents: got in, array length = " + eventList.length);
    // mark events as used and as owned by system
    for (EventImpl ev : eventList) {
//System.out.println("putEvents: set age & owner of event " + i);
        ev.setAge(Constants.eventUsed);
        ev.setOwner(Constants.system);
    }

    att.getStation().getOutputList().put(eventList);
    // keep track of # of events put by this attachment
    att.setEventsPut(att.getEventsPut() + eventList.size());

    return;
  }



  /**
   * Dispose of unwanted events in an ET system. The events are recycled and not
   * made available to any other user.
   *
   * @param att          attachment object
   * @param eventArray   array of event objects
   *
   */
  void dumpEvents(AttachmentLocal att, EventImpl[] eventArray) {
    // mark as owned by system
    for (EventImpl ev : eventArray) {
        ev.setOwner(Constants.system);
    }

    // put into GrandCentral Station
    gcStation.getInputList().putInGC(eventArray);

    // keep track of # of events put by this attachment
    att.setEventsDump(att.getEventsDump() + eventArray.length);

    return;
  }



  /**
   * Dispose of unwanted events in an ET system. The events are recycled and not
   * made available to any other user.
   *
   * @param att         attachment object
   * @param eventList   list of event objects
   *
   */
  private void dumpEvents(AttachmentLocal att, ArrayList<EventImpl> eventList) {
    for (EventImpl ev : eventList) {
        ev.setOwner(Constants.system);
    }
    gcStation.getInputList().putInGC(eventList);
    att.setEventsDump(att.getEventsDump() + eventList.size());
    return;
  }



  /**
   * Gather all ET system data for sending over the network and put it into a
   * single byte array. Putting the data into a byte array is done so that its
   * size is known beforehand and can be sent as the first int of data. It is
   * also done so that this gathering of data is done no more than once a second
   * and so that the data is shared by the many attachments that may want it.
   * It is far more efficient to send an existing array of data to an attachment
   * than it is to regather it for each request. The calling of this method is
   * mutex protected so only 1 thread/attachment can modify or access the
   * array at a time.
   *
   * @return <code>Constants.ok</code> if everything is fine and
   *         <code>Constants.error</code> otherwise
   */
  int gatherSystemData() {
      int err = Constants.ok;

      // regather all system information every second at most
      long time2 = System.currentTimeMillis();
      if (time2 - time1 > 1000) {
        gather = true;
      }

      if (gather) {
        time1 = System.currentTimeMillis();
        try {
          // Estimate the maximum space we need to store all the data
          // (assuming no more than 10 network interfaces & multicast addrs).
          // Use of LinkedList/HashMap is not mutex protected here so it
          // may fail. Catch all errors.
          int numStations = stationCount;	  
          int numAtts = attachments.size();
          int size = 600 + 570*numStations + 160*numAtts;

          if (size > infoArray.length) {
            infoArray = new byte[size];
          }

          int len1 = writeSystemData(infoArray, 4);
          int len2 = writeStationData(infoArray, 4+len1, numStations);
          int len3 = writeAttachmentData(infoArray, 4+len1+len2, numAtts);
//System.out.println("len1-3 = " + len1 + ", " + len2 + ", " + len3);
          // no process data in Java ET systems
          Utils.intToBytes(0, infoArray, 4+len1+len2+len3);
          int len4 = 4;
          dataLength = len1+len2+len3+len4;

          // Write size of data (not including the length now being written)
          // in beginning of array.
          Utils.intToBytes(dataLength, infoArray, 0);

          gather = false;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            err = Constants.error;
        }
      }
      return err;
  }


  /**
   * Get ET system data for sending over the network.
   *
   * @param info byte array to hold system data
   * @param off offset of the byte array
   * @return size of the data in bytes placed into the byte array
   */
  private int writeSystemData(byte[] info, int off) {
    // values which can change
    Utils.intToBytes(1, info, off); // alive by definition
    Utils.intToBytes(0, info, off+=4); // no heartbeat
    Utils.intToBytes(0, info, off+=4); // no temp events
    Utils.intToBytes(stations.size(), info, off+=4); // aren't these redundant???
    Utils.intToBytes(attachments.size(), info, off+=4); // aren't these redundant???
    Utils.intToBytes(0, info, off+=4); // no processes sharing memory

    // find out how many events the system owns
    int eventsOwned = 0;
    for (Event ev : events.values()) {
        if (ev.getOwner() == Constants.system) {
          eventsOwned++;
        }
    }
    Utils.intToBytes(eventsOwned, info, off+=4);

    // no way to test and see if mutexes are locked in Java
    Utils.intToBytes(Constants.mutexUnlocked, info, off+=4);
    Utils.intToBytes(Constants.mutexUnlocked, info, off+=4);
    Utils.intToBytes(Constants.mutexUnlocked, info, off+=4);

    // values which do NOT change
    Utils.intToBytes(Constants.endianBig, info, off+=4);
    Utils.intToBytes(Constants.mutexNoShare, info, off+=4);
    Utils.intToBytes(-1, info, off+=4);
    Utils.intToBytes(Constants.stationSelectInts, info, off+=4);
    Utils.intToBytes(config.getNumEvents(), info, off+=4);
    Utils.longToBytes((long)config.getEventSize(), info, off+=4);
    Utils.intToBytes(0, info, off+=8);   // send 0 if 32 bits, else 1 if 64 bits
    Utils.intToBytes(0, info, off+=4);
    Utils.intToBytes(config.getStationsMax(), info, off+=4);
    Utils.intToBytes(config.getAttachmentsMax(), info, off+=4);
    Utils.intToBytes(0, info, off+=4);

    // tcp server port
    Utils.intToBytes(config.getServerPort(), info, off+=4);
    // udp port
    Utils.intToBytes(config.getUdpPort(), info, off+=4);
    // multicast port
    Utils.intToBytes(config.getMulticastPort(), info, off+=4);

    // # of interfaces and multicast addresses
    Utils.intToBytes(netAddresses.length, info, off+=4);
    Utils.intToBytes(config.getMulticastAddrs().size(), info, off+=4);

    int len;
    int totalInts = 27;
    int totalStringLen = 0;

    // length of interface address strings
    for (InetAddress addr : netAddresses) {
      len = addr.getHostAddress().length() + 1;
      Utils.intToBytes(len, info, off+=4);
      totalInts++;
      totalStringLen += len;
    }

    // what about broadcast address strings???

    // length of multicast address strings
    for (InetAddress addr : config.getMulticastAddrs()) {
      len = addr.getHostAddress().length() + 1;
      Utils.intToBytes(len, info, off+=4);
      totalInts++;
      totalStringLen += len;
    }

    // length ET file name
    len = name.length() + 1;
    Utils.intToBytes(len, info, off+=4);
    totalInts++;
    totalStringLen += len;

    // total data size
    int byteSize = 4*totalInts + totalStringLen;

    // write strings into array
    off += 4;
    byte[] outString;

    for (InetAddress addr : netAddresses) {
      try {
        outString = addr.getHostAddress().getBytes("ASCII");
        System.arraycopy(outString, 0, info, off, outString.length);
        off += outString.length;
      }
      catch (UnsupportedEncodingException ex) {}
      info[off++] = 0; // C null terminator
    }

    for (InetAddress addr : config.getMulticastAddrs()) {
      try {
        outString = addr.getHostAddress().getBytes("ASCII");
        System.arraycopy(outString, 0, info, off, outString.length);
        off += outString.length;
      }
      catch (UnsupportedEncodingException ex) {}
      info[off++] = 0; // C null terminator
    }

    try {
      outString = name.getBytes("ASCII");
      System.arraycopy(outString, 0, info, off, outString.length);
      off += outString.length;
    }
    catch (UnsupportedEncodingException ex) {}
    info[off] = 0; // C null terminator

    return byteSize;
  }


  /**
   * Get station data for sending over the network.
   *
   * @param info byte array to hold station data
   * @param offset offset of the byte array
   * @param stationsMax limit on number of stations to gather data on
   * @return size of the data in bytes placed into the byte array
   */
  private int writeStationData(byte[] info, int offset, int stationsMax) {
      // Since we're not grabbing any mutexes, the number of stations may have
      // changed between when the buffer size was set and now. Therefore, do not
      // exceed "stationsMax" number of stations.

      int len1, len2, len3, len4, numAtts, counter, offAtt;
      int off = offset;
      int byteSize = 0;
      int statCount = 0;
      boolean isHead = true;
      // save space at beginning to insert int containing # of stations
      byteSize += 4;
      off += 4;

      ListIterator parallelIterator = null;
      ListIterator mainIterator = stations.listIterator();
      StationLocal stat = (StationLocal) mainIterator.next();
//System.out.println("writeStationData: " + stationsMax + " stats, start with " + stat.name);      
      while (true) {

          // Since the number of attachments may change, be careful,
          // reserve a spot here to be filled later with num of attachments.
          offAtt = off;

          Utils.intToBytes(stat.getStationId(), info, off += 4);
          Utils.intToBytes(stat.getStatus(), info, off += 4);
          Utils.intToBytes(Constants.mutexUnlocked, info, off += 4);

          // since the number of attachments may change, be careful
          counter = 0;
          for (AttachmentLocal att : stat.getAttachments()) {
              Utils.intToBytes(att.getId(), info, off += 4);
              counter++;
          }
          Utils.intToBytes(counter, info, offAtt);
          numAtts = counter;

          Utils.intToBytes(Constants.mutexUnlocked, info, off += 4);
          Utils.intToBytes(stat.getInputList().getEvents().size(), info, off += 4);
          Utils.longToBytes(stat.getInputList().getEventsTry(), info, off += 4);
          Utils.longToBytes(stat.getInputList().getEventsIn(), info, off += 8);
          Utils.intToBytes(Constants.mutexUnlocked, info, off += 8);
          Utils.intToBytes(stat.getOutputList().getEvents().size(), info, off += 4);
          Utils.longToBytes(stat.getOutputList().getEventsOut(), info, off += 4);

          if (stat.getConfig().getFlowMode() == Constants.stationParallel && isHead) {
              Utils.intToBytes(Constants.stationParallelHead, info, off += 8);
          }
          else {
              Utils.intToBytes(stat.getConfig().getFlowMode(), info, off += 8);
          }
          Utils.intToBytes(stat.getConfig().getUserMode(), info, off += 4);
          Utils.intToBytes(stat.getConfig().getRestoreMode(), info, off += 4);
          Utils.intToBytes(stat.getConfig().getBlockMode(), info, off += 4);
          Utils.intToBytes(stat.getConfig().getPrescale(), info, off += 4);
          Utils.intToBytes(stat.getConfig().getCue(), info, off += 4);
          Utils.intToBytes(stat.getConfig().getSelectMode(), info, off += 4);
          int[] select = stat.getConfig().getSelect();
          for (int j = 0; j < Constants.stationSelectInts; j++) {
              Utils.intToBytes(select[j], info, off += 4);
          }

          // write strings, lengths first
          len1 = 0;
          if (stat.getConfig().getSelectFunction() != null) {
              len1 = stat.getConfig().getSelectFunction().length() + 1;
          }
          len2 = 0;
          if (stat.getConfig().getSelectLibrary() != null) {
              len2 = stat.getConfig().getSelectLibrary().length() + 1;
          }
          len3 = 0;
          if (stat.getConfig().getSelectClass() != null) {
              len3 = stat.getConfig().getSelectClass().length() + 1;
          }
          len4 = stat.getStationName().length() + 1;

          Utils.intToBytes(len1, info, off += 4);
          Utils.intToBytes(len2, info, off += 4);
          Utils.intToBytes(len3, info, off += 4);
          Utils.intToBytes(len4, info, off += 4);

          // write strings into array
          off += 4;
          byte[] outString;

          try {
              if (len1 > 0) {
                  outString = stat.getConfig().getSelectFunction().getBytes("ASCII");
                  System.arraycopy(outString, 0, info, off, outString.length);
                  off += outString.length;
                  info[off++] = 0; // C null terminator
              }
              if (len2 > 0) {
                  outString = stat.getConfig().getSelectLibrary().getBytes("ASCII");
                  System.arraycopy(outString, 0, info, off, outString.length);
                  off += outString.length;
                  info[off++] = 0; // C null terminator
              }
              if (len3 > 0) {
                  outString = stat.getConfig().getSelectClass().getBytes("ASCII");
                  System.arraycopy(outString, 0, info, off, outString.length);
                  off += outString.length;
                  info[off++] = 0; // C null terminator
              }

              outString = stat.getStationName().getBytes("ASCII");
              System.arraycopy(outString, 0, info, off, outString.length);
              off += outString.length;
              info[off++] = 0; // C null terminator
          }
          catch (UnsupportedEncodingException ex) {
          }

          // track size of all data stored in buffer
          byteSize += 4 * (19 + numAtts + Constants.stationSelectInts) +
                  8 * 3 + len1 + len2 + len3 + len4;

          // if more stations now than space allowed for, skip rest
          if (++statCount >= stationsMax) {
//System.out.println("statCount = " + statCount + ", reached station limit, break out ");      
              break;
          }

          // if head of parallel linked list ...
          if ((stat.getConfig().getFlowMode() == Constants.stationParallel) &&
                  (stations.contains(stat))) {
              parallelIterator = stat.getParallelStations().listIterator(1);
              if (parallelIterator.hasNext()) {
                  isHead = false;
                  stat = (StationLocal) parallelIterator.next();
//System.out.println("go to || stat " + stat.name);      
                  continue;
              }
          }
          // if in (but not head of) parallel linked list ...
          else if (stat.getConfig().getFlowMode() == Constants.stationParallel) {
              if (parallelIterator.hasNext()) {
                  isHead = false;
                  stat = (StationLocal) parallelIterator.next();
//System.out.println("go to || stat " + stat.name);      
                  continue;
              }
          }

          isHead = true;
          // if in main linked list ...
          if (mainIterator.hasNext()) {
              stat = (StationLocal) mainIterator.next();
//System.out.println("go to main stat " + stat.name);      
          }
      }

      // enter # of stations
      Utils.intToBytes(statCount, info, offset);

    return byteSize;
  }


  /**
   * Get attachment data for sending over the network.
   *
   * @param info byte array to hold attachment data
   * @param offset offset of the byte array
   * @param attsMax limit on number of attachments to gather data on
   * @return size of the data in bytes placed into the byte array
   */
  private int writeAttachmentData(byte[] info, int offset, int attsMax) {
    // Since we're not grabbing any mutexes, the number of attachments may have
    // changed between when the buffer size was set and now. Therefore, do not
    // exceed "attsMax" number of attachments.

    if (attsMax == 0) {
      Utils.intToBytes(0, info, offset);
      return 4;
    }

    int len1, len2;
    int off = offset;
    int eventsOwned;
    int byteSize = 0;
    int attCount = 0;

    off += 4;
    byteSize += 4;

    for (AttachmentLocal att :  attachments.values()) {

      Utils.intToBytes(att.getId(), info, off);
      Utils.intToBytes(-1, info, off+=4); // no ET "processes" in Java
      Utils.intToBytes(att.getStation().getStationId(), info, off+=4);
      Utils.intToBytes(-1, info, off+=4);
      Utils.intToBytes((att.isWaiting()? Constants.attBlocked : Constants.attUnblocked), info, off+=4);
      Utils.intToBytes((att.isWakeUp()? Constants.attQuit : Constants.attContinue), info, off+=4);

      // find out how many events the attachment owns
      eventsOwned = 0;

      for (Event ev : events.values()) {
        if (ev.getOwner() == att.getId()) {
          eventsOwned++;
        }
      }
      Utils.intToBytes(eventsOwned, info, off+=4);

      Utils.longToBytes(att.getEventsPut(),  info, off+=4);
      Utils.longToBytes(att.getEventsGet(),  info, off+=8);
      Utils.longToBytes(att.getEventsDump(), info, off+=8);
      Utils.longToBytes(att.getEventsMake(), info, off+=8);

      // read strings, lengths first
      len1 = att.getHost().length() + 1;
      len2 = att.getStation().getStationName().length() + 1;
      Utils.intToBytes(len1, info, off+=8);
      Utils.intToBytes(len2, info, off+=4);
//System.out.println("writeAttachments: len1 = " + len1 + ", len2 = " + len2);
      // write strings into array
      off += 4;
      byte[] outString;

      try {
        outString = att.getHost().getBytes("ASCII");
        System.arraycopy(outString, 0, info, off, outString.length);
        off += outString.length;
        info[off++] = 0; // C null terminator

        outString = att.getStation().getStationName().getBytes("ASCII");
        System.arraycopy(outString, 0, info, off, outString.length);
        off += outString.length;
        info[off++] = 0; // C null terminator
      }
      catch (UnsupportedEncodingException ex) {}

      // track size of all data stored in buffer
      byteSize += 4*9 + 8*4 + len1 + len2;

      // if more attachments now than space allowed for, skip rest
      if (++attCount >= attsMax) {
        break;
      }

    }
//System.out.println("writeAttachments: #atts = " + attCount + ", byteSize = " + byteSize);

    // send # of attachments
    Utils.intToBytes(attCount, info, offset);

    return byteSize;
  }


}



