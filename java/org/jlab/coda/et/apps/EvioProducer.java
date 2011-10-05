package org.jlab.coda.et.apps;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.jevio.*;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * User: timmer
 * Date: May 28, 2010
 * Time: 4:01:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class EvioProducer {


    public EvioProducer() {
    }


     private static void usage() {
         System.out.println("\nUsage: java Producer -f <et name> [-p <server port>] [-host <host>]" +
                 "                     [-d <delay in millisec>] [-g <group #>]\n\n" +
                 "       -f     ET system's name\n" +
                 "       -s     size in bytes for requested events\n" +
                 "       -p     port number for a udp broadcast\n" +
                 "       -d     delay in millisec between getting and putting events\n" +
                 "       -g     group number of new events to get\n" +
                 "       -host  host the ET system resides on (defaults to anywhere)\n\n" +
                 "        This consumer works by making a connection to the\n" +
                 "        ET system's tcp server port.\n");
     }

    /** Setting this to false will make the buffer be recreated from scratch for each event. */
    static boolean fastMode = true;

    /** Buffer to use for generated evio data. */
    static ByteBuffer buffie;

    /**
     * Create an evio bank for sending.
     */
    public static ByteBuffer evioBytes() throws EvioException {

        if (fastMode && buffie != null) {
            buffie.flip();
            return buffie;
        }

        // count the events we make for testing
        int eventNumber = 1;

        // use a tag of 11 for events--for no particular reason
        int tag = 11;

        // second event, more traditional bank of banks
        EventBuilder eventBuilder = new EventBuilder(tag, DataType.BANK, eventNumber++);
        EvioEvent event2 = eventBuilder.getEvent();

        // add a bank of doubles
        EvioBank bank1 = new EvioBank(22, DataType.DOUBLE64, 0);
        eventBuilder.appendDoubleData(bank1, new double[] {1.1,2.2,-3.3, 1.12345678912345678912, -1.2e-99, -2.2e-11, -6.7e-10});
        eventBuilder.addChild(event2, bank1);

        // add a bank of floats
        EvioBank bank4 = new EvioBank(22, DataType.FLOAT32, 0);
        eventBuilder.appendFloatData(bank4, new float[] {1.1F,2.2F,-3.3F, 1.12345678912345678912F, -1.2e+38F, -2.2e-38F, -6.7e-29F});
        eventBuilder.addChild(event2, bank4);

        // add a bank of longs
        EvioBank bank5 = new EvioBank(22, DataType.LONG64, 0);
        eventBuilder.appendLongData(bank5, new long[] {1L,2L,3L, 1000000000000000000L, -1000000000000000000L, -2L, -3L});
        eventBuilder.addChild(event2, bank5);

        // add a bank of ints
        EvioBank bank6 = new EvioBank(22, DataType.INT32, 0);
        eventBuilder.appendIntData(bank6, new int[] {1,2,3, 1000000000, -1000000000, -2, -3});
        eventBuilder.addChild(event2, bank6);

        // add a bank of bytes
        EvioBank bank7 = new EvioBank(22, DataType.CHAR8, 0);
        eventBuilder.appendByteData(bank7, new byte[] {1,2,3});
        eventBuilder.addChild(event2, bank7);

        // lets modify event2
        event2.getHeader().setNumber(eventNumber++);
        EvioBank bank2 = new EvioBank(33, DataType.BANK, 0);
        eventBuilder.addChild(event2, bank2);

        EvioBank subBank1 = new EvioBank(34, DataType.INT32, 1);
        eventBuilder.addChild(bank2, subBank1);
        eventBuilder.appendIntData(subBank1, new int[] {4,5,6});

        // now add a bank of segments
        EvioBank subBank2 = new EvioBank(33, DataType.SEGMENT, 0);
        eventBuilder.addChild(bank2, subBank2);

        EvioSegment segment1 = new EvioSegment(34, DataType.SHORT16);
        eventBuilder.addChild(subBank2, segment1);
        eventBuilder.appendShortData(segment1, new short[] {7,8,9,10, 10000, 20000});

        // now add a bank of tag segments
        EvioBank subBank3 = new EvioBank(45, DataType.TAGSEGMENT, 0);
        eventBuilder.addChild(bank2, subBank3);

        // now add a tag segment of tag segments
        EvioTagSegment tagsegment2 = new EvioTagSegment(35, DataType.TAGSEGMENT);
        eventBuilder.addChild(subBank3, tagsegment2);

        EvioTagSegment tagsegment3 = new EvioTagSegment(36, DataType.CHARSTAR8);
        eventBuilder.addChild(tagsegment2, tagsegment3);
        eventBuilder.appendStringData(tagsegment3, "This is a string");

        event2.setAllHeaderLengths();


        // write the event
        buffie = ByteBuffer.allocate(event2.getTotalBytes());
        event2.write(buffie);
        buffie.flip();

        //System.out.println("Event = \n"+ event2.toXML());
        return buffie;
    }


    /**
     * Main program for testing.
     */
    public static void main(String[] args) {

         String etName = null, host = null;
         int port = EtConstants.serverPort;
         int group = 1;
         int delay = 0;
         int size = 32;

         try {
             for (int i = 0; i < args.length; i++) {
                 if (args[i].equalsIgnoreCase("-f")) {
                     etName = args[++i];
                 }
                 else if (args[i].equalsIgnoreCase("-host")) {
                     host = args[++i];
                 }
                 else if (args[i].equalsIgnoreCase("-p")) {
                     try {
                         port = Integer.parseInt(args[++i]);
                         if ((port < 1024) || (port > 65535)) {
                             System.out.println("Port number must be between 1024 and 65535.");
                             usage();
                             return;
                         }
                     }
                     catch (NumberFormatException ex) {
                         System.out.println("Did not specify a proper port number.");
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

             if (etName == null) {
                 usage();
                 return;
             }

             // make a direct connection to ET system's tcp server
             EtSystemOpenConfig config = new EtSystemOpenConfig(etName, host, port);

             // create ET system object with verbose debugging output
             EtSystem sys = new EtSystem(config, EtConstants.debugInfo);
             sys.open();

             // get GRAND_CENTRAL station object
             EtStation gc = sys.stationNameToObject("GRAND_CENTRAL");

             // attach to grandcentral
             EtAttachment att = sys.attach(gc);

             // array of events
             EtEvent[] mevs;

             int chunk = 1, count = 0, startingVal = 0;
             long t1, t2, counter = 0, totalT = 0, totalCount = 0;
             double rate, avgRate;
             int[] con = {1, 2, 3, 4};
             String s;
             ByteBuffer buf;

             // keep track of time for event rate calculations
             t1 = System.currentTimeMillis();

             for (int i = 0; i < 50; i++) {
                 while (count < 30000L) {
                     // get array of new events
                     mevs = sys.newEvents(att, Mode.SLEEP, false, 0, chunk, size, group);

                     if (delay > 0) Thread.sleep(delay);

                     // example of how to manipulate events
                     if (true) {
                         for (int j = 0; j < mevs.length; j++) {
                             // put integer (j) into front of data buffer
                             //int swappedData = j + startingVal;
                             //swappedData = Integer.reverseBytes(swappedData);
                             buf = evioBytes();

                             mevs[j].getDataBuffer().put(buf);
                             int len = buf.position();
                             //mevs[j].setByteOrder(ByteOrder.LITTLE_ENDIAN);
                             // set data length to be 4 bytes (1 integer)
                             mevs[j].setLength(len);
                             // set every other event's priority as high
                             //if (j % 2 == 0) mevs[j].setPriority(Priority.HIGH);
                             // set event's control array
                             //mevs[j].setControl(con);
                         }
                         startingVal++;
                     }

                     // put events back into ET system
                     sys.putEvents(att, mevs);
                     count += mevs.length;

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
             System.out.println("End of producing events, now close");
             sys.close();
         }
         catch (Exception ex) {
             System.out.println("Error using ET system as producer");
             ex.printStackTrace();
         }
     }


}
