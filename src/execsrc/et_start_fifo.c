//
// Copyright 2022, Jefferson Science Associates, LLC.
// Subject to the terms in the LICENSE file found in the top-level directory.
//
// EPSCI Group
// Thomas Jefferson National Accelerator Facility
// 12000, Jefferson Ave, Newport News, VA 23606
// (757)-269-7100

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <getopt.h>

#include "et_private.h"


void printHelp(char *program) {
    fprintf(stderr,
            "\nusage: %s  %s\n%s\n%s\n%s",
            program,
            "[-h] [-v] [-d] [-b] [-f <file>]",
            "                 [-s <bytes/buf>] [-n <bufs/entry>] [-e <entries>]",
            "                 [-p <TCP server port>] [-u <UDP port>] [-a <multicast address>]",
            "                 [-rb <buf size>] [-sb <buf size>] [-nd]\n\n");

    fprintf(stderr, "          -h     help\n");
    fprintf(stderr, "          -v     verbose output\n");
    fprintf(stderr, "          -d     deletes any existing file first\n");
    fprintf(stderr, "          -f     memory-mapped file name\n\n");

    fprintf(stderr, "          -s     buffer size in bytes (3000 default)\n");
    fprintf(stderr, "          -n     number of buffers per fifo entry (10 default\n");
    fprintf(stderr, "          -e     number of fifo entries (2000 max, 10 min, 100 default)\n\n");
    fprintf(stderr, "          -b     make fifo blocking (nonblocking default)\n");

    fprintf(stderr, "          -p     TCP server port #\n");
    fprintf(stderr, "          -u     UDP (broadcast &/or multicast) port #\n");
    fprintf(stderr, "          -a     multicast address\n\n");

    fprintf(stderr, "          -rb    TCP receive buffer size (bytes)\n");
    fprintf(stderr, "          -sb    TCP send    buffer size (bytes)\n");
    fprintf(stderr, "          -nd    use TCP_NODELAY option\n\n");

    fprintf(stderr, "          Starts up ET system as fifo w/ each entry having multiple buffers.\n");
    fprintf(stderr, "          Listens on 239.200.0.0 by default.\n\n");
}


int main(int argc, char **argv) {

    int c, i_tmp, errflg = 0;
    int mcastAddrCount = 0, mcastAddrMax = 10;
    char mcastAddr[mcastAddrMax][ET_IPADDRSTRLEN];
    int j, status, sig_num, serverPort = 0, udpPort = 0;
    int et_verbose = ET_DEBUG_NONE, deleteFile = 0;
    int sendBufSize = 0, recvBufSize = 0, noDelay = 0;
    int blockingFifo = 0;

    sigset_t sigblockset, sigwaitset;
    et_sysconfig config;
    et_sys_id id;
    et_statconfig sconfig;
    et_stat_id    my_stat;

    /************************************************
     * Default config which sets this up as a FIFO
     * with 100 entries, each entry with 10 buffers.
     ************************************************/
    int entries = 100;              /* 100 FIFO entries */
    int entryCount = 10;            /* 10 buffers in each FIFO entry */
    int eventSize = 3000;           /* size of buffer in bytes */
    char *et_filename = NULL;
    char et_name[ET_FILENAME_LENGTH];

    /* multiple character command-line options */
    static struct option long_options[] = {
            {"rb",    1, NULL, 1},
            {"sb",    1, NULL, 2},
            {"nd",    0, NULL, 3},
            {0,       0, 0,    0}
    };

    /* Use default multicast address */
    memset(mcastAddr, 0, ET_IPADDRSTRLEN);

    while ((c = getopt_long_only(argc, argv, "bvhdn:s:p:u:m:a:f:e:", long_options, 0)) != EOF) {

        if (c == -1)
            break;

        switch (c) {
            case 'n':
                i_tmp = atoi(optarg);
                if (i_tmp > 0) {
                    entryCount = i_tmp;
                } else {
                    printf("Invalid argument to -n. Must be a positive integer.\n");
                    exit(-1);
                }
                break;

            case 's':
                i_tmp = atoi(optarg);
                if (i_tmp > 0) {
                    eventSize = i_tmp;
                } else {
                    printf("Invalid argument to -s. Must be a positive integer.\n");
                    exit(-1);
                }
                break;

            case 'p':
                i_tmp = atoi(optarg);
                if (i_tmp > 1023 && i_tmp < 65535) {
                    serverPort = i_tmp;
                } else {
                    printf("Invalid argument to -p. Must be < 65535 & > 1023.\n");
                    exit(-1);
                }
                break;

            case 'u':
                i_tmp = atoi(optarg);
                if (i_tmp > 1023 && i_tmp < 65535) {
                    udpPort = i_tmp;
                } else {
                    printf("Invalid argument to -u. Must be < 65535 & > 1023.\n");
                    exit(-1);
                }
                break;

            case 'a':
                if (strlen(optarg) >= 16) {
                    fprintf(stderr, "Multicast address is too long\n");
                    exit(-1);
                }
                if (mcastAddrCount >= mcastAddrMax) break;
                strcpy(mcastAddr[mcastAddrCount++], optarg);
                break;

            case 'e':
                i_tmp = atoi(optarg);
                if (i_tmp > 9 && i_tmp < ET_EVENT_GROUPS_MAX+1) {
                    entries = i_tmp;
                } else {
                    printf("Invalid argument to -e. Must be 5001 > e > 9.\n");
                    exit(-1);
                }
                break;

            case 'f':
                if (strlen(optarg) >= ET_FILENAME_LENGTH) {
                    fprintf(stderr, "ET file name is too long\n");
                    exit(-1);
                }
                strcpy(et_name, optarg);
                et_filename = et_name;
                break;

                /* Make the User's station blocking so the whole fifo becomes blocking. */
            case 'b':
                blockingFifo = 1;
                break;

                /* Remove an existing memory-mapped file first, then recreate it. */
            case 'd':
                deleteFile = 1;
                break;

            case 'v':
                et_verbose = ET_DEBUG_INFO;
                break;

            case 1:
                i_tmp = atoi(optarg);
                if (i_tmp < 1) {
                    printf("Invalid argument to -rb. Recv buffer size must be > 0.\n");
                    exit(-1);
                }
                recvBufSize = i_tmp;
                break;

            case 2:
                i_tmp = atoi(optarg);
                if (i_tmp < 1) {
                    printf("Invalid argument to -rb. Send buffer size must be > 0.\n");
                    exit(-1);
                }
                sendBufSize = i_tmp;
                break;

            case 3:
                noDelay = 1;
                break;

            case 'h':
                printHelp(argv[0]);
                exit(1);

            case ':':
            case '?':
            default:
                errflg++;
        }
    }

    /* Total # events */
    int nevents = entries * entryCount;

    /* Listen to default multicast address if nothing else */
    if (mcastAddrCount < 1) {
        strcpy(mcastAddr[0], ET_MULTICAST_ADDR);
        mcastAddrCount++;
    }

    /* Error of some kind */
    if (optind < argc || errflg) {
        printHelp(argv[0]);
        exit(2);
    }

    /* Check et_filename */
    if (et_filename == NULL) {
        /* see if env variable SESSION is defined */
        if ((et_filename = getenv("SESSION")) == NULL) {
            fprintf(stderr, "No ET file name given and SESSION env variable not defined\n");
            exit(-1);
        }
        /* check length of name */
        if ((strlen(et_filename) + 12) >= ET_FILENAME_LENGTH) {
            fprintf(stderr, "ET file name is too long\n");
            exit(-1);
        }
        sprintf(et_name, "%s%s", "/tmp/et_sys_", et_filename);
    }

    for (; optind < argc; optind++) {
        printf("%s\n", argv[optind]);
    }

    if (et_verbose) {
        printf("et_start_fifo: in FIFO form, %d entries, %d bufs/entry, %d bytes/buf, %d total events\n",
               entries, entryCount, eventSize, nevents);
    }

    if (deleteFile) {
        remove(et_name);
    }

    /********************************/
    /* set configuration parameters */
    /********************************/

    if (et_system_config_init(&config) == ET_ERROR) {
        printf("et_start_fifo: no more memory\n");
        exit(1);
    }

    /* Divide events into equal groups, or in this case, fifo entries.
     * Strictly speaking this is not necessary, but it's the only way
     * to set up the ET system in such a way in which a client which
     * attaches can know the number of fifo slots there are. */
    int i;
    int groups[entries];
    for (i = 0; i < entries; i++) {
        groups[i] = entryCount;
    }
    et_system_config_setgroups(config, groups, entries);

    /* total number of events */
    et_system_config_setevents(config, nevents);

    /* size of event in bytes */
    et_system_config_setsize(config, eventSize);

    /* set TCP server port */
    if (serverPort > 0) et_system_config_setserverport(config, serverPort);

    /* set UDP (broadcast/multicast) port */
    if (udpPort > 0) et_system_config_setport(config, udpPort);

    /* set server's TCP parameters */
    et_system_config_settcp(config, recvBufSize, sendBufSize, noDelay);

    /* add multicast address to listen to  */
    for (j = 0; j < mcastAddrCount; j++) {
        if (strlen(mcastAddr[j]) > 7) {
            status = et_system_config_addmulticast(config, mcastAddr[j]);
            if (status != ET_OK) {
                printf("et_start_fifo: bad multicast address argument\n");
                exit(1);
            }
            printf("et_start_fifo: adding multicast address %s\n",mcastAddr[j]);
        }
    }

    /* Make sure filename is null-terminated string */
    if (et_system_config_setfile(config, et_name) == ET_ERROR) {
        printf("et_start_fifo: bad filename argument\n");
        exit(1);
    }

    /*************************/
    /* setup signal handling */
    /*************************/
    sigfillset(&sigblockset);
    status = pthread_sigmask(SIG_BLOCK, &sigblockset, NULL);
    if (status != 0) {
        printf("et_start_fifo: pthread_sigmask failure\n");
        exit(1);
    }
    sigemptyset(&sigwaitset);
    /* Java uses SIGTERM to end processes it spawns.
     * So if this program is run by a Java JVM, the
     * following line allows the JVM to kill it. */
    sigaddset(&sigwaitset, SIGTERM);
    sigaddset(&sigwaitset, SIGINT);

    /*************************/
    /*    start ET system    */
    /*************************/
    if (et_verbose) {
        printf("et_start_fifo: starting ET system %s\n", et_name);
    }
    if (et_system_start(&id, config) != ET_OK) {
        printf("et_start_fifo: error in starting ET system\n");
        exit(1);
    }

    /*
     * Define a station to create for consumers of data-filled fifo entries.
     * It allows multiple users by default.
     * By default, set it to be non-blocking and set the stations input queue size
     * to be 5% shy (2 min) of the full number of fifo entries. Why do this?
     * For 2 reasons. First, so that the producer is always able to get a new
     * entry at any time without being blocked. Second, when this station's input is
     * full because of a slow consumer, it will essentially drop all the new data
     * automatically with no special intervention necessary - default behavior.
     */
    et_station_config_init(&sconfig);
    if (!blockingFifo) {
        et_station_config_setblock(sconfig, ET_STATION_NONBLOCKING);
        // Cue contains 95% of entries => 5% free with minimum of 2 ...
        int freeEntries = entries/20;
        freeEntries = freeEntries < 2 ? 2 : freeEntries;
        et_station_config_setcue(sconfig, nevents - freeEntries*entryCount);
    }
    
    if ((status = et_station_create(id, &my_stat, "Users", sconfig)) != ET_OK) {
        printf("%s: error in creating station \"Users\"\n", argv[0]);
        exit(1);
    }
    et_station_config_destroy(sconfig);

    et_system_setdebug(id, et_verbose);

    /* turn this thread into a signal handler */
    sigwait(&sigwaitset, &sig_num);

    printf("Interrupted by CONTROL-C or SIGTERM\n");
    printf("ET is exiting\n");
    et_system_close(id);

    exit(0);
}

