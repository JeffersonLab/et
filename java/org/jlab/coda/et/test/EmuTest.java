package org.jlab.coda.et.test;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import org.jlab.coda.jevio.*;

import java.nio.ByteBuffer;

/**
 * Try to mimic what a evio producer, event builder, and evio consumer do in succession
 * in order to test the chain of events that the emu goes through.
 */
public class EmuTest {

    public EmuTest() {
    }


     private static void usage() {
         System.out.println("\nUsage: java EmuTest -etin <name of input et> -etout <name of output et>\n" +
                 "                    [-d <delay>] [-host <host>] [-pin <input et port>] [-pout <output et port>]\n\n" +
                 "      -etin  input  ET system's name\n" +
                 "      -etout output ET system's name\n" +
                 "      -pin   port number for input  et udp broadcast\n" +
                 "      -pout  port number for output et udp broadcast\n" +
                 "      -d     delay in millisec between getting and putting events\n" +
                 "      -g     group number of new events to get\n" +
                 "      -host  host the ET system resides on (defaults to anywhere)\n\n" +
                 "       This consumer works by making a connection to the\n" +
                 "       ET system's tcp server port.\n");
     }


    /**
     * Create a simple evio bank for sending.
     */
    public static ByteBuffer evioBytes() throws EvioException {

        // count the events we make for testing
        int eventNumber = 1;

        // use a tag of 2 for no particular reason
        int tag = 2;

        // bank of banks
        EventBuilder eventBuilder = new EventBuilder(tag, DataType.BANK, eventNumber);
        EvioEvent event = eventBuilder.getEvent();

        // add a bank of ints
        EvioBank bank = new EvioBank(3, DataType.INT32, 0);
        eventBuilder.appendIntData(bank, new int[] {1,2,3, 0, -1, -2, -3});
        eventBuilder.addChild(event, bank);

        event.setAllHeaderLengths();

        // write the event
        ByteBuffer buf = ByteBuffer.allocate(event.getTotalBytes());
        event.write(buf);
        buf.flip();

        //System.out.println("Event = \n"+ event2.toXML());
        return buf;
    }


    /**
     * Main program for testing.
     */
    public static void main(String[] args) {

         String etNameIn = null, etNameOut = null, host = null;
         int portIn = EtConstants.serverPort, portOut = EtConstants.serverPort;
         int group = 1;
         int delay = 0;
         int size = 32;

         try {
             for (int i = 0; i < args.length; i++) {
                 if (args[i].equalsIgnoreCase("-etin")) {
                     etNameIn = args[++i];
                 }
                 else if (args[i].equalsIgnoreCase("-etout")) {
                     etNameOut = args[++i];
                 }
                 else if (args[i].equalsIgnoreCase("-host")) {
                     host = args[++i];
                 }
                 else if (args[i].equalsIgnoreCase("-pin")) {
                     try {
                         portIn = Integer.parseInt(args[++i]);
                         if ((portIn < 1024) || (portIn > 65535)) {
                             System.out.println("Input port number must be between 1024 and 65535.");
                             usage();
                             return;
                         }
                     }
                     catch (NumberFormatException ex) {
                         System.out.println("Did not specify a proper input port number.");
                         usage();
                         return;
                     }
                 }
                 else if (args[i].equalsIgnoreCase("-pout")) {
                     try {
                         portOut = Integer.parseInt(args[++i]);
                         if ((portOut < 1024) || (portOut > 65535)) {
                             System.out.println("Output port number must be between 1024 and 65535.");
                             usage();
                             return;
                         }
                     }
                     catch (NumberFormatException ex) {
                         System.out.println("Did not specify a proper output port number.");
                         usage();
                         return;
                     }
                 }
                 else if (args[i].equalsIgnoreCase("-s")) {
                     try {
                         size = Integer.parseInt(args[++i]);
                         if (size < 1) {
                             System.out.println("Size needs to be positive int.");
                             usage();
                             return;
                         }
                     }
                     catch (NumberFormatException ex) {
                         System.out.println("Did not specify a proper size.");
                         usage();
                         return;
                     }
                 }
                 else if (args[i].equalsIgnoreCase("-g")) {
                     try {
                         group = Integer.parseInt(args[++i]);
                         if ((group < 1) || (group > 10)) {
                             System.out.println("Group number must be between 0 and 10.");
                             usage();
                             return;
                         }
                     }
                     catch (NumberFormatException ex) {
                         System.out.println("Did not specify a proper group number.");
                         usage();
                         return;
                     }
                 }
                 else if (args[i].equalsIgnoreCase("-d")) {
                     try {
                         delay = Integer.parseInt(args[++i]);
                         if (delay < 1) {
                             System.out.println("delay must be > 0.");
                             usage();
                             return;
                         }
                     }
                     catch (NumberFormatException ex) {
                         System.out.println("Did not specify a proper delay.");
                         usage();
                         return;
                     }
                 }
                 else {
                     usage();
                     return;
                 }
             }

             if (host == null) {
                 host = EtConstants.hostAnywhere;
                 /*
                 try {
                     host = InetAddress.getLocalHost().getHostName();
                 }
                 catch (UnknownHostException ex) {
                     System.out.println("Host not specified and cannot find local host name.");
                     usage();
                     return;
                 }
                 */
             }

             if (etNameIn == null || etNameOut == null) {
                 usage();
                 return;
             }

             ///////////////////////////////////////////////////////////
             // INPUT
             ///////////////////////////////////////////////////////////

             // make a direct connection to ET system's tcp server
             EtSystemOpenConfig configIn = new EtSystemOpenConfig(etNameIn, host, portIn);

             // create ET system object with verbose debugging output
             EtSystem sysIn = new EtSystem(configIn, EtConstants.debugInfo);
             sysIn.open();

             // get GRAND_CENTRAL station object
             EtStation gcIn = sysIn.stationNameToObject("GRAND_CENTRAL");

             // attach to grandcentral
             EtAttachment gcAttIn = sysIn.attach(gcIn);

             // default configuration of a new station
             EtStationConfig statConfig = new EtStationConfig();

             // create new station
             EtStation statIn = sysIn.createStation(statConfig, "in", 1, 0);

             // attach to new station
             EtAttachment attIn1 = sysIn.attach(statIn);

             ///////////////////////////////////////////////////////////
             // OUTPUT
             ///////////////////////////////////////////////////////////

             EtSystemOpenConfig configOut = new EtSystemOpenConfig(etNameOut, host, portOut);
             EtSystem sysOut              = new EtSystem(configOut, EtConstants.debugInfo);
             sysOut.open();
             EtStation gcOut              = sysOut.stationNameToObject("GRAND_CENTRAL");
             EtAttachment gcAttOut        = sysOut.attach(gcOut);
             EtStation statOut            = sysOut.createStation(statConfig, "out", 1, 0);
             EtAttachment attOut1         = sysOut.attach(statOut);

             ///////////////////////////////////////////////////////////

             // arrays of events
             EtEvent[] mevsIn, mevsOut;

             int chunk = 1, count = 0;
             long t1, t2, counter = 0, totalT = 0, totalCount = 0;
             double rate, avgRate;
             int[] con = {1, 2, 3, 4};
             String s;

             // keep track of time for event rate calculations
             t1 = System.currentTimeMillis();

             for (int i = 0; i < 50; i++) {
                 while (count < 300000L) {
                     // get array of new events from input ET
                     mevsIn = sysIn.newEvents(gcAttIn, Mode.SLEEP, 0, chunk, 512);

                     if (delay > 0) Thread.sleep(delay);

                     // put data into events
                     if (true) {
                         for (EtEvent aMevsIn : mevsIn) {
                             ByteBuffer buf = evioBytes();
                             aMevsIn.getDataBuffer().put(buf);
                             int len = buf.position();
                             //aMevsIn.setByteOrder(ByteOrder.LITTLE_ENDIAN);
                             aMevsIn.setLength(len);
                         }
                     }

                     // put events back into input ET system
                     sysIn.putEvents(gcAttIn, mevsIn);

                     ///////////////////////////////////////////////////////////
                     // now transfer events from one ET system to another
                     ///////////////////////////////////////////////////////////

                     // get array of events from input ET
                     mevsIn  = sysIn.getEvents(attIn1, Mode.SLEEP, Modify.NOTHING, 0, chunk);

                     // get array of new events from output ET
                     mevsOut = sysOut.newEvents(gcAttOut, Mode.SLEEP, 0, mevsIn.length, 2048);

                     if (mevsIn.length != mevsOut.length) {
                         System.out.println("NON-matching lengths!!!");
                     }

                     for (int j=0; j < mevsOut.length; j++) {
                         ByteBuffer buf = mevsIn[j].getDataBuffer();
                         mevsOut[j].getDataBuffer().put(buf);
                         int len = buf.position();
                         mevsOut[j].setLength(len);
                     }

                     // put events back into input & output ET systems
                     sysIn.putEvents(attIn1, mevsIn);
                     sysOut.putEvents(gcAttOut, mevsOut);

                     ///////////////////////////////////////////////////////////
                     // now look at events from output ET system
                     ///////////////////////////////////////////////////////////

                     // get array of events from input ET
                     mevsOut = sysOut.getEvents(attOut1, Mode.SLEEP, Modify.NOTHING, 0, chunk);

                     System.out.println("event's data buffer is " + mevsOut[0].getDataBuffer().order() + ", limit = " + mevsOut[0].getDataBuffer().limit() +
                     ", capacity = " + mevsOut[0].getDataBuffer().capacity());
                     System.out.println("swap = " + mevsOut[0].needToSwap());

                     ByteParser parser = new ByteParser();
                     EvioEvent ev = parser.parseEvent(mevsOut[0].getDataBuffer());
                     System.out.println("Event = \n"+ev.toXML());

                     sysOut.putEvents(attOut1, mevsOut);
                     
                     if (delay > 0) Thread.sleep(delay);
                     count++;
                 }

                 // calculate the event rate
                 t2 = System.currentTimeMillis();
                 rate = 1000.0 * ((double) count) / ((double) (t2 - t1));
                 totalCount += count;
                 totalT += t2 - t1;
                 avgRate = 1000.0 * ((double) totalCount) / totalT;
                 System.out.println("rate = " + String.format("%.3g", rate) +
                                    " Hz,   avg = " + String.format("%.3g", avgRate));
                 count = 0;
                 t1 = System.currentTimeMillis();
             }
             System.out.println("End of program, now close et systems");
             sysIn.close();
             sysOut.close();
         }
         catch (Exception ex) {
             System.out.println("Error using ET system as emu test");
             ex.printStackTrace();
         }
     }


}
