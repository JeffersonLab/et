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
#include <signal.h>
#include <sys/time.h>
#include <getopt.h>
#include <limits.h>
#include <time.h>

#include "et.h"
#include "et_fifo.h"

/* prototype */
static void *signal_thread (void *arg);


int main(int argc,char **argv) {

    int             i, j, c, i_tmp, status, numRead, locality;
    int             errflg=0, chunk=1, qSize=0, verbose=0, remote=0, readData=0;
    int             multicast=0, broadcast=0, broadAndMulticast=0;
    int             sendBufSize=0, recvBufSize=0, noDelay=0, delay=0, idCount=0;
    int             debugLevel = ET_DEBUG_ERROR;
    unsigned short  port=0;
    char            et_name[ET_FILENAME_LENGTH], host[256], interface[16];
    char            localAddr[16];

    int             mcastAddrCount = 0, mcastAddrMax = 10;
    char            mcastAddr[mcastAddrMax][16];

    pthread_t       tid;
    et_sys_id       id;
    et_fifo_id      fid;
    et_fifo_entry   *entry;

    et_openconfig   openconfig;
    sigset_t        sigblock;
    struct timespec getDelay;
    struct timespec timeout;
    struct timespec t1, t2;

    /* statistics variables */
    double          rate=0.0, avgRate=0.0;
    int64_t         count=0, totalCount=0, totalT=0, time, time1, time2, bytes=0, totalBytes=0;


    /* 4 multiple character command-line options */
    static struct option long_options[] =
            { {"host", 1, NULL, 1},
              {"rb",   1, NULL, 3},
              {"sb",   1, NULL, 4},
              {"nd",   0, NULL, 5},
              {"read", 0, NULL, 6},
              {0,0,0,0}};

    memset(host, 0, 256);
    memset(interface, 0, 16);
    memset(mcastAddr, 0, (size_t) mcastAddrMax*16);
    memset(et_name, 0, ET_FILENAME_LENGTH);

    while ((c = getopt_long_only(argc, argv, "vbmhrn:s:p:f:a:i:d:", long_options, 0)) != EOF) {

        if (c == -1)
            break;

        switch (c) {

            case 'p':
                i_tmp = atoi(optarg);
                if (i_tmp > 1023 && i_tmp < 65535) {
                    port = (unsigned short)i_tmp;
                } else {
                    printf("Invalid argument to -p. Must be < 65535 & > 1023.\n");
                    exit(-1);
                }
                break;

            case 'd':
                i_tmp = atoi(optarg);
                if (i_tmp >= 0) {
                    delay = i_tmp;
                } else {
                    printf("Invalid argument to -d. Must be >= 0 millisec\n");
                    exit(-1);
                }
                break;

            case 'f':
                if (strlen(optarg) >= ET_FILENAME_LENGTH) {
                    fprintf(stderr, "ET file name is too long\n");
                    exit(-1);
                }
                strcpy(et_name, optarg);
                break;

            case 'i':
                if (strlen(optarg) > 15 || strlen(optarg) < 7) {
                    fprintf(stderr, "interface address is bad\n");
                    exit(-1);
                }
                strcpy(interface, optarg);
                break;

            case 'a':
                if (strlen(optarg) >= 16) {
                    fprintf(stderr, "Multicast address is too long\n");
                    exit(-1);
                }
                if (mcastAddrCount >= mcastAddrMax) break;
                strcpy(mcastAddr[mcastAddrCount++], optarg);
                multicast = 1;
                break;

            /* case host: */
            case 1:
                if (strlen(optarg) >= 255) {
                    fprintf(stderr, "host name is too long\n");
                    exit(-1);
                }
                strcpy(host, optarg);
                break;

                /* case rb */
            case 3:
                i_tmp = atoi(optarg);
                if (i_tmp < 1) {
                    printf("Invalid argument to -rb. Recv buffer size must be > 0.\n");
                    exit(-1);
                }
                recvBufSize = i_tmp;
                break;

                /* case sb */
            case 4:
                i_tmp = atoi(optarg);
                if (i_tmp < 1) {
                    printf("Invalid argument to -sb. Send buffer size must be > 0.\n");
                    exit(-1);
                }
                sendBufSize = i_tmp;
                break;

                /* case nd */
            case 5:
                noDelay = 1;
                break;

                /* case read */
            case 6:
                readData = 1;
                break;

            case 'v':
                verbose = 1;
                debugLevel = ET_DEBUG_INFO;
                break;

            case 'r':
                remote = 1;
                break;

            case 'm':
                multicast = 1;
                break;

            case 'b':
                broadcast = 1;
                break;

            case ':':
            case 'h':
            case '?':
            default:
                errflg++;
        }
    }

    if (!multicast && !broadcast) {
        /* Default to local host if direct connection */
        if (strlen(host) < 1) {
            strcpy(host, ET_HOST_LOCAL);
        }
    }

    if (optind < argc || errflg || strlen(et_name) < 1) {
        fprintf(stderr,
                "\nusage: %s  %s\n%s\n%s\n%s\n%s\n\n",
                argv[0], "-f <ET name>",
                "                     [-h] [-v] [-r] [-m] [-b] [-nd] [-read]",
                "                     [-host <ET host>] [-p <ET port>] [-d <delay ms>]",
                "                     [-i <interface address>] [-a <mcast addr>]",
                "                     [-rb <buf size>] [-sb <buf size>]");

        fprintf(stderr, "          -f    ET system's (memory-mapped file) name\n");
        fprintf(stderr, "          -host ET system's host if direct connection (default to local)\n");
        fprintf(stderr, "          -h    help\n\n");

        fprintf(stderr, "          -v    verbose output (also prints data if reading with -read)\n");
        fprintf(stderr, "          -read read data (1 int for each event)\n");
        fprintf(stderr, "          -r    act as remote (TCP) client even if ET system is local\n");
        fprintf(stderr, "          -p    port, TCP if direct, else UDP\n\n");

        fprintf(stderr, "          -d    delay between fifo gets in milliseconds\n");

        fprintf(stderr, "          -i    outgoing network interface address (dot-decimal)\n");
        fprintf(stderr, "          -a    multicast address(es) (dot-decimal), may use multiple times\n");
        fprintf(stderr, "          -m    multicast to find ET (use default address if -a unused)\n");
        fprintf(stderr, "          -b    broadcast to find ET\n\n");

        fprintf(stderr, "          -rb   TCP receive buffer size (bytes)\n");
        fprintf(stderr, "          -sb   TCP send    buffer size (bytes)\n");
        fprintf(stderr, "          -nd   use TCP_NODELAY option\n\n");

        fprintf(stderr, "          This consumer works by making a direct connection to the\n");
        fprintf(stderr, "          ET system's server port and host unless at least one multicast address\n");
        fprintf(stderr, "          is specified with -a, the -m option is used, or the -b option is used\n");
        fprintf(stderr, "          in which case multi/broadcasting used to find the ET system.\n");
        fprintf(stderr, "          If multi/broadcasting fails, look locally to find the ET system.\n");
        fprintf(stderr, "          This program gets events from the ET system as a fifo and puts them back.\n\n");

        exit(2);
    }


    timeout.tv_sec  = 2;
    timeout.tv_nsec = 0;

    /* delay is in milliseconds */
    if (delay > 0) {
        getDelay.tv_sec = delay / 1000;
        getDelay.tv_nsec = (delay - (delay / 1000) * 1000) * 1000000;
    }

    /*************************/
    /* setup signal handling */
    /*************************/
    /* block all signals */
    sigfillset(&sigblock);
    status = pthread_sigmask(SIG_BLOCK, &sigblock, NULL);
    if (status != 0) {
        printf("%s: pthread_sigmask failure\n", argv[0]);
        exit(1);
    }

    /* spawn signal handling thread */
    pthread_create(&tid, NULL, signal_thread, (void *)NULL);


    /******************/
    /* open ET system */
    /******************/
    et_open_config_init(&openconfig);

    if (broadcast && multicast) {
        broadAndMulticast = 1;
    }

    /* if multicasting to find ET */
    if (multicast) {
        if (mcastAddrCount < 1) {
            /* Use default mcast address if not given on command line */
            status = et_open_config_addmulticast(openconfig, ET_MULTICAST_ADDR);
        }
        else {
            /* add multicast addresses to use  */
            for (j = 0; j < mcastAddrCount; j++) {
                if (strlen(mcastAddr[j]) > 7) {
                    status = et_open_config_addmulticast(openconfig, mcastAddr[j]);
                    if (status != ET_OK) {
                        printf("%s: bad multicast address argument\n", argv[0]);
                        exit(1);
                    }
                    printf("%s: adding multicast address %s\n", argv[0], mcastAddr[j]);
                }
            }
        }
    }

    if (broadAndMulticast) {
        printf("Broad and Multicasting\n");
        if (port == 0) {
            port = ET_UDP_PORT;
        }
        et_open_config_setport(openconfig, port);
        et_open_config_setcast(openconfig, ET_BROADANDMULTICAST);
        et_open_config_sethost(openconfig, ET_HOST_ANYWHERE);
    }
    else if (multicast) {
        printf("Multicasting\n");
        if (port == 0) {
            port = ET_UDP_PORT;
        }
        et_open_config_setport(openconfig, port);
        et_open_config_setcast(openconfig, ET_MULTICAST);
        et_open_config_sethost(openconfig, ET_HOST_ANYWHERE);
    }
    else if (broadcast) {
        printf("Broadcasting\n");
        if (port == 0) {
            port = ET_UDP_PORT;
        }
        et_open_config_setport(openconfig, port);
        et_open_config_setcast(openconfig, ET_BROADCAST);
        et_open_config_sethost(openconfig, ET_HOST_ANYWHERE);
    }
    else {
        if (port == 0) {
            port = ET_SERVER_PORT;
        }
        et_open_config_setserverport(openconfig, port);
        et_open_config_setcast(openconfig, ET_DIRECT);
        if (strlen(host) > 0) {
            et_open_config_sethost(openconfig, host);
        }
        et_open_config_gethost(openconfig, host);
        printf("Direct connection to %s\n", host);
    }

    /* Defaults are to use operating system default buffer sizes and turn off TCP_NODELAY */
    et_open_config_settcp(openconfig, recvBufSize, sendBufSize, noDelay);
    if (strlen(interface) > 6) {
        et_open_config_setinterface(openconfig, interface);
    }

    if (remote) {
        printf("Set as remote\n");
        et_open_config_setmode(openconfig, ET_HOST_AS_REMOTE);
    }

    /* If responses from different ET systems, return error. */
    et_open_config_setpolicy(openconfig, ET_POLICY_ERROR);

    /* debug level */
    et_open_config_setdebugdefault(openconfig, debugLevel);

    et_open_config_setwait(openconfig, ET_OPEN_WAIT);
    if (et_open(&id, et_name, openconfig) != ET_OK) {
        printf("%s: et_open problems\n", argv[0]);
        exit(1);
    }
    et_open_config_destroy(openconfig);

    /*-------------------------------------------------------*/

    /* Find out if we have a remote connection to the ET system */
    et_system_getlocality(id, &locality);
    if (locality == ET_REMOTE) {
        printf("ET is remote\n\n");

        et_system_gethost(id, host);
        et_system_getlocaladdress(id, localAddr);
        printf("Connect to ET, from ip = %s to %s\n", localAddr, host);
    }
    else {
        printf("ET is local\n\n");
    }

    /* set level of debug output (everything) */
    et_system_setdebug(id, debugLevel);

    /***********************/
    /* Use FIFO interface  */
    /***********************/
    status = et_fifo_openConsumer(id, &fid);
    if (status != ET_OK) {
        printf("%s: et_fifo_open problems\n", argv[0]);
        exit(1);
    }

    /* no error here */
    numRead = et_fifo_getEntryCapacity(fid);

    // The way this is setup, the consumer does not know how many id-labeled
    // buffers will be in each fifo entry. Can figure that out after the first getEntry()

    entry = et_fifo_entryCreate(fid);
    if (entry == NULL) {
        printf("%s: et_fifo_open out of mem\n", argv[0]);
        exit(1);
    }


    /* read time for future statistics calculations */
    clock_gettime(CLOCK_REALTIME, &t1);
    time1 = 1000L*t1.tv_sec + t1.tv_nsec/1000000L; /* milliseconds */

    int bufId, hasData, swap;
    size_t len;
    int *data;

    while (1) {

        /**************/
        /* get events */
        /**************/

        /* Example of single, timeout read */
        //status = et_fifo_getEntryTO(fid, &timeout);
        //if (status == ET_ERROR_TIMEOUT) {
        //    printf("%s: got timeout\n", argv[0]);
        //    continue;
        //}

        /* Example of reading a fifo entry */
        status = et_fifo_getEntry(fid, entry);
        if (status != ET_OK) {
            printf("%s: error getting events\n", argv[0]);
            goto error;
        }

        /* Access the new buffers */
        et_event** evts = et_fifo_getBufs(entry);

        /*******************/
        /* read/print data */
        /*******************/
        idCount = 0;

        if (readData) {
            // Look at each event/buffer
            for (j = 0; j < numRead; j++) {
                // Does this buffer have any data? (Set by producer)
                if (!et_fifo_hasData(evts[j])) {
                    // Once we hit a buffer with no data, there is no further data
                    break;
                }
                idCount++;

                // Id associated with this buffer in this fifo entry
                bufId = et_fifo_getId(evts[j]);

                // Data associated with this event
                et_event_getdata(evts[j], (void **) &data);
                // Length of data associated with this event
                et_event_getlength(evts[j], &len);
                // Did this data originate on an opposite endian machine?
                et_event_needtoswap(evts[j], &swap);

                bytes += len;
                totalBytes += len;

                if (verbose) {
                    printf("buf id = %d, has data = %s\n", bufId, hasData ? "true" : "false");
                    if (swap) {
                        printf("    swapped int = %d\n", ET_SWAP32(data[0]));
                    }
                    else {
                        printf("    unswapped int = %d\n", data[0]);
                    }
                }
            }
        }
        else {
            for (j = 0; j < numRead; j++) {
                hasData = et_fifo_hasData(evts[j]);
                if (!hasData) {
                    break;
                }
                idCount++;
                et_event_getlength(evts[j], &len);
                bytes += len;
                totalBytes += len;
            }
        }

        /*******************/
        /* put events */
        /*******************/

        /* putting array of events */
        status = et_fifo_putEntry(entry);
        if (status != ET_OK) {
            printf("%s: error getting events\n", argv[0]);
            goto error;
        }

        count += idCount;

        end:

        /* statistics */
        clock_gettime(CLOCK_REALTIME, &t2);
        time2 = 1000L*t2.tv_sec + t2.tv_nsec/1000000L; /* milliseconds */
        time = time2 - time1;
        if (time > 5000) {
            /* reset things if necessary */
            if ( (totalCount >= (LONG_MAX - count)) ||
                 (totalT >= (LONG_MAX - time)) )  {
                bytes = totalBytes = totalT = totalCount = count = 0;
                time1 = time2;
                continue;
            }

            rate = 1000.0 * ((double) count) / time;
            totalCount += count;
            totalT += time;
            avgRate = 1000.0 * ((double) totalCount) / totalT;

            /* Event rates */
            printf("\n %s Events: %3.4g Hz,  %3.4g Avg.\n", argv[0], rate, avgRate);

            /* Data rates */
            rate    = ((double) bytes) / time;
            avgRate = ((double) totalBytes) / totalT;
            printf(" %s Data:   %3.4g kB/s,  %3.4g Avg.\n\n", argv[0], rate, avgRate);

            bytes = count = 0;

            clock_gettime(CLOCK_REALTIME, &t1);
            time1 = 1000L*t1.tv_sec + t1.tv_nsec/1000000L;
        }

        // Delay before calling et_fifo_getEntry again
        if (delay > 0) {
            nanosleep(&getDelay, NULL);
        }

    } /* while(1) */

    error:
    // Although not necessary at this point
    et_fifo_freeEntry(entry);

    printf("%s: ERROR\n", argv[0]);
    return 0;
}



/************************************************************/
/*              separate thread to handle signals           */
static void *signal_thread (void *arg) {

    sigset_t        signal_set;
    int             sig_number;

    sigemptyset(&signal_set);
    sigaddset(&signal_set, SIGINT);

    /* Wait for Control-C */
    sigwait(&signal_set, &sig_number);

    printf("Got control-C, exiting\n");
    exit(1);
}
