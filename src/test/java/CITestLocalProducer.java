import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;

public class CITestLocalProducer {
    public static void main(String[] args) {
        try {
            // Configure to open existing ET system (shared memory file) by name
            EtSystemOpenConfig config = new EtSystemOpenConfig();
            config.setEtName("/tmp/et_ci_localtest");       // ET system name (file) to connect to
            config.setHost(EtConstants.hostLocal);          // local host (since ET is local)
            // By default, this will try JNI/shared memory for local ET. If you want **only** sockets (pure Java), add:
            // config.setConnectRemotely(true);

            // Open a connection to the ET system
            EtSystem etSys = new EtSystem(config);
            etSys.open();  // establish connection to ET (throws exception if fails)

            // Attach to the GrandCentral station (for producers, GrandCentral is the source of new events)
            EtStation gc = etSys.stationNameToObject("GRAND_CENTRAL");
            EtAttachment att = etSys.attach(gc);
            
            System.out.println("Producer: waiting for consumer to attach...");
            // Wait until the consumer station "consumer_station" is created by the consumer program
            while (!etSys.stationExists("consumer_station")) {
                if (!etSys.alive()) {
                    System.err.println("ERROR: ET system not alive, exiting");
                    etSys.close();
                    return;
                }
                Thread.sleep(100);  // sleep 0.1 sec and try again
                System.out.println("Still waiting for station 'consumer_station'...");
            }
            System.out.println("Producer: consumer detected, starting event transmission...");

            // Once consumer station exists, start sending 500 events
            int totalEvents = 500;
            for (int i = 1; i <= totalEvents; ++i) {
                // Request a new/free event from ET (will block until available)
                EtEvent[] newEvents = etSys.newEvents(att, Mode.SLEEP, 0, 1, 100);
                if (newEvents == null || newEvents.length == 0) {
                    System.err.printf("ERROR: et_newEvents failed at event %d\n", i);
                    break;
                }
                EtEvent ev = newEvents[0];
                // Prepare event data (message string)
                String message = "ET is great, event " + i;
                byte[] msgBytes = (message + "\0").getBytes("UTF-8");  // include null terminator
                // Copy message into event's data buffer
                byte[] dataBuf = ev.getData();
                int msgLen = msgBytes.length;
                if (msgLen > dataBuf.length) msgLen = dataBuf.length;  // truncate if too long
                System.arraycopy(msgBytes, 0, dataBuf, 0, msgLen);
                ev.setLength(msgLen);
                // Send (put) the event to the ET system (to be delivered to consumer_station)
                etSys.putEvents(att, new EtEvent[]{ev});
            }

            // Detach and close ET connection
            etSys.detach(att);
            etSys.close();
            System.out.println("Producer: completed sending events and disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
