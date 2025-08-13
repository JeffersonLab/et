import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;

public class CITestLocalProducer {
    public static void main(String[] args) {


        // Parse arg(s)
        boolean noSocket = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-ns")) noSocket = true;
        }


        try {
            // Configure to open existing ET system (shared memory file) by name
            EtSystemOpenConfig config = new EtSystemOpenConfig();
            config.setEtName("/tmp/et_ci_localtest");       // ET system name (file) to connect to
            config.setHost(EtConstants.hostLocal);          // local host (since ET is local)
            config.setNetworkContactMethod(EtConstants.direct);
            System.out.println("Connecting to " + EtConstants.hostLocal);

            // Open a connection to the ET system
            EtSystem etSys = new EtSystem(config);
            etSys.open();  // establish connection to ET (throws exception if fails)

            if (etSys.usingJniLibrary()) {
                System.out.println("ET is local\n");
            }
            else {
                if(noSocket) {
                    System.out.println("No JNI library loaded and noSocket specified. Quitting!");
                    return;
                }
                System.out.println("No JNI lib: ET running in remote mode\n");
                config.setConnectRemotely(true);
                config.setTcpPort(EtConstants.serverPort);
            }

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
                Thread.sleep(1000);  // sleep 1 sec and try again
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
                ByteBuffer buf = ev.getDataBuffer();
                buf.order(ev.getByteOrder());
                // Prepare event data (message string)
                byte[] msgBytes = (("ET is great, event " + i) + "\0").getBytes(StandardCharsets.UTF_8);
                
                buf.clear();
                buf.put(msgBytes, 0, msgBytes.length);

                // Tell ET how many valid bytes are in this event
                ev.setLength(msgBytes.length);
                                
                // Send the event
                etSys.putEvents(att, new EtEvent[]{ev});

                // Copy message into event's data buffer
                // byte[] dataBuf = ev.getData();
                // int msgLen = msgBytes.length;
                // if (msgLen > dataBuf.length) msgLen = dataBuf.length;  // truncate if too long
                // System.arraycopy(msgBytes, 0, dataBuf, 0, msgLen);
                // ev.setLength(msgLen);
                // Send (put) the event to the ET system (to be delivered to consumer_station)
                // etSys.putEvents(att, new EtEvent[]{ev});
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
