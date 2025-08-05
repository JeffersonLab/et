import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import java.io.*;

public class CITestLocalConsumer {
    public static void main(String[] args) {
        try {
            // Configure to open the same ET system (by file name)
            EtSystemOpenConfig config = new EtSystemOpenConfig();
            config.setEtName("/tmp/et_ci_localtest");
            config.setHost(EtConstants.hostLocal);
            // If using pure Java (no JNI), add: config.setConnectRemotely(true);

            EtSystem etSys = new EtSystem(config);
            etSys.open();  // connect to existing ET system

            // Create a new station configuration for the consumer station
            EtStationConfig sConfig = new EtStationConfig();
            sConfig.setBlockMode(EtConstants.stationBlocking);
            sConfig.setSelectMode(EtConstants.stationSelectAll);
            sConfig.setUserMode(EtConstants.stationUserSingle);    // only 1 attachment (the consumer)
            sConfig.setRestoreMode(EtConstants.stationRestoreOut);

            // Create the "consumer_station" in the ET system
            EtStation station = etSys.createStation(sConfig, "consumer_station");
            // Attach this consumer process to the new station
            EtAttachment att = etSys.attach(station);
            System.out.printf("Consumer: attached to station '%s' with attach_id %d\n",
                               "consumer_station", att.getId());

            // Open output file for logging received messages
            PrintWriter outFile = null;
            try {
                outFile = new PrintWriter(new FileWriter("et_output.txt"));
            } catch (IOException io) {
                System.err.println("Warning: couldn't open et_output.txt for writing.");
            }

            System.out.println("Consumer: attached to station, waiting for events...");
            // Receive 500 events and process them
            for (int count = 1; count <= 500; ++count) {
                // Wait for an event from the station (blocking)
                EtEvent[] events = etSys.getEvents(att, Mode.SLEEP, Modify.NOTHING, 0, 1);
                if (events == null || events.length == 0) {
                    System.err.printf("ERROR: et_getEvents failed at event %d\n", count);
                    break;
                }
                EtEvent ev = events[0];
                // Read event data as a string (assume null-terminated string from producer)
                byte[] data = ev.getData();
                int length = ev.getLength();
                if (length > 0 && data[length-1] == 0) {
                    length -= 1; // exclude null terminator for printing
                }
                String message = new String(data, 0, length, "UTF-8");
                System.out.println("Received: " + message);
                if (outFile != null) {
                    outFile.println(message);
                }
                // Mark the event as processed (put back to ET system for reuse)
                etSys.putEvents(att, events);
            }

            // Close file, detach and close ET connection
            if (outFile != null) outFile.close();
            etSys.detach(att);
            etSys.close();
            System.out.println("Consumer: completed receiving events and disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
