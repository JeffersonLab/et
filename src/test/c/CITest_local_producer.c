// producer.c: Producer that waits for consumer then sends 500 events
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "et.h"

int main() {
    // Info about whole ET sytem
    et_sys_id id; et_openconfig openconfig; // For opening existing ET system
    // Info for consumer station (really only needed to check existence here)
    // et_statconfig sconfig;
    et_stat_id    stat_id_consumer;
    // Info for producer station
    et_att_id     attach_id_producer; // Attachment id for producer

    int status;

    // Open an existing ET system by name (local shared memory file)
    et_open_config_init(&openconfig);
    status = et_open(&id, "/tmp/et_ci_localtest", openconfig);
    // Like we always do after opening an ET system, we destroy the openconfig
    et_open_config_destroy(openconfig);
    if (status != ET_OK) {
        fprintf(stderr, "ERROR: Cannot open ET system (status %d)\n", status);
        exit(1);
    }

    // Attach to Grand Central station of ET system (as is typical for producer)
    et_att_id attach;
    if (et_station_attach(id, ET_GRANDCENTRAL, &attach) != ET_OK) {
        fprintf(stderr, "Error attaching to GRAND_CENTRAL\n");
        et_system_close(id);
        exit(1);
    }

    // Wait for consumer station to appear
    printf("Producer: waiting for consumer to attach...\n");
    while(!et_station_exists(id, &stat_id_consumer, "consumer_station")) {
        // If the ET system isn't alive, give up
        if (!et_alive(id)) {
            fprintf(stderr, "ERROR: ET system not alive\n");
            et_close(id);
            exit(1);
        }
        sleep(5);  // sleep 5 secs and try again
        printf("Still waiting for station '%s'...\n", "consumer_station");
    }

    // Now the consumer is connected and ready; start sending events
    printf("Producer: consumer detected, starting event transmission...\n");

    // Send 500 events
    size_t ev_size = 100;  // size of each event data buffer
    for (int i = 1; i <= 500; ++i) {
        et_event *pe;
        // Get a new/free event from ET system (blocking until available)
        status = et_event_new(id, attach_id_producer, &pe, ET_SLEEP, NULL, ev_size);
        if (status != ET_OK) {
            fprintf(stderr, "ERROR: et_event_new failed at event %d (status %d)\n", i, status);
            break;
        }
        // Fill the event buffer with our message
        char message[ev_size];
        snprintf(message, sizeof(message), "ET is great, event %d", i);
        size_t msg_len = strlen(message) + 1;  // +1 to include null terminator
        // Copy message into event data buffer
        char *ev_data = (char *) pe->pdata;   // pointer to event's data buffer
        memcpy(ev_data, message, msg_len);
        et_event_setlength(pe, msg_len);

        // Put (send) the event to the station
        status = et_event_put(id, attach_id_producer, pe);
        if (status != ET_OK) {
            fprintf(stderr, "ERROR: et_event_put failed at event %d (status %d)\n", i, status);
            break;
        }
    }

    // Detach and close ET connection
    et_station_detach(id, attach_id_producer);
    et_close(id);
    printf("Producer: completed sending events and disconnected.\n");
    return 0;
}
