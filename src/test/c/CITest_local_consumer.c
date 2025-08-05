// consumer.c: Consumer that receives events and logs them to a file
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "et.h"

int main() {
    // Info about whole ET sytem
    et_sys_id id; et_openconfig openconfig;     // For opening existing ET system
    // Info for consumer station
    et_statconfig sconfig;
    et_stat_id    stat_id_consumer; 
    et_att_id     attach_id_consumer; // Attachment id for consumer

    int status;

    // Open an existing ET system by name (local shared memory file)
    et_open_config_init(&openconfig);
    status = et_open(&id, "/tmp/et_ci_localtest", openconfig);
    et_open_config_destroy(openconfig);
    if (status != ET_OK) {
        fprintf(stderr, "ERROR: Cannot open ET system (status %d)\n", status);
        exit(1);
    }

    //Create consumer station configuration
    et_station_config_init(&sconfig);
    et_station_config_setblock(sconfig, ET_STATION_BLOCKING);
    et_station_config_setselect(sconfig, ET_STATION_SELECT_ALL);
    et_station_config_setuser(sconfig, 1);  // Allow 1 user (consumer)
    et_station_config_setrestore(sconfig, ET_STATION_RESTORE_OUT);
    
    // Create the consumer station
    status = et_station_create(id, &stat_id_consumer, "consumer_station", sconfig);
    if (status != ET_OK) {
        fprintf(stderr, "ERROR: et_station_create failed (status %d)\n", status);
        et_close(id);
        exit(1);
    }
    et_station_config_destroy(sconfig);

    // Attach this consumer to the station
    status = et_station_attach(id, stat_id_consumer, &attach_id_consumer);
    if (status != ET_OK) {
        fprintf(stderr, "ERROR: et_station_attach (consumer) failed (status %d)\n", status);
        et_close(id);
        exit(1);
    }
    else printf("Consumer: attached to station '%s' with attach_id %d\n", "consumer_station", attach_id_consumer);

    // Open output file for logging received messages
    FILE *fout = fopen("et_output.txt", "w");
    if (!fout) {
        perror("fopen et_output.txt");
        // (Continue even if file open fails, just print to console)
    }

    printf("Consumer: attached to station, waiting for events...\n");
    // Receive 500 events and process them
    for (int count = 1; count <= 500; ++count) {
        et_event *pe;
        // Block until an event is available:contentReference[oaicite:10]{index=10}
        status = et_event_get(id, attach_id_consumer, &pe, ET_SLEEP, NULL);
        if (status != ET_OK) {
            fprintf(stderr, "ERROR: et_event_get failed at event %d (status %d)\n", count, status);
            exit(1);
        }
        // Access the event data (assume it's a null-terminated string)
        char *ev_data = (char *) pe->pdata;
        printf("Received: %s\n", ev_data);
        if (fout) {
            fprintf(fout, "%s\n", ev_data);
        }
        // Return the event to ET system (mark as processed)
        status = et_event_put(id, attach_id_consumer, pe);
        if (status != ET_OK) {
            fprintf(stderr, "ERROR: et_event_put (consumer) failed at %d (status %d)\n", count, status);
            exit(1);
        }
    }

    if (fout) fclose(fout);
    et_station_detach(id, attach_id_consumer);
    et_close(id);
    printf("Consumer: completed receiving events and disconnected.\n");
    return 0;
}
