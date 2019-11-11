/*----------------------------------------------------------------------------*
 *  Copyright (c) 1998        Southeastern Universities Research Association, *
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
 *----------------------------------------------------------------------------*
 *
 * Description:
 *      Sample event transfer between 2 ET system
 *
 *----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <getopt.h>


#include "et.h"

/* Transfer 2 billion events in one statement */
#define NUMEVENTS 2000000000
//#define NUMEVENTS 1000

/* prototype */
static void * signal_thread (void *arg);

int main(int argc,char **argv) {  
    pthread_t       tid;
    et_statconfig   sconfig;
    et_openconfig   openconfig;
    et_bridgeconfig bconfig;
    sigset_t        sigblock;
    et_att_id       att_from, att_to;
    et_stat_id      stat_from;
    et_sys_id       id_from, id_to;
    
    int i, j, c, i_tmp, status, ntransferred=0;
    int errflg=0, chunk=100, queue=ET_STATION_CUE;
    int multicast=1, statPos=ET_END;
    int sendBufSize=0, recvBufSize=0, noDelay=0, nonBlock=0;
    int debugLevel = ET_DEBUG_ERROR;
    unsigned short port=0;
    char etNameIn[ET_FILENAME_LENGTH], etNameOut[ET_FILENAME_LENGTH];
    char host[256], interface[16], statIn[ET_STATNAME_LENGTH];
    int mcastAddrCount = 0, mcastAddrMax = 10;
    char mcastAddr[mcastAddrMax][16];
    struct timespec timeout = {0, 0};


    /* 4 multiple character command-line options */
    static struct option long_options[] =
            {{"host",  1, NULL, 1},
             {"fin",   1, NULL, 2},
             {"fout",  1, NULL, 3},
             {"sin",   1, NULL, 4},
             {"spos",  1, NULL, 5},
             {"nd",    0, NULL, 6},
             {"nb",    0, NULL, 7},
             {0,       0, 0,    0}
            };

    memset(host,      0, 256);
    memset(interface, 0, 16);
    memset(mcastAddr, 0, mcastAddrMax*16);
    memset(etNameIn,  0, ET_FILENAME_LENGTH);
    memset(etNameOut, 0, ET_FILENAME_LENGTH);
    memset(statIn,    0, ET_STATNAME_LENGTH);

    /* Default name of input station name */
    strcpy(statIn, "TransferOut");

    while ((c = getopt_long_only(argc, argv, "vdh:a:p:c:i:q:", long_options, 0)) != EOF) {

        if (c == -1)
            break;

        switch (c) {
            case 'c':
                i_tmp = atoi(optarg);
                if (i_tmp > 0 && i_tmp < 1001) {
                    chunk = i_tmp;
                    printf("Setting chunk to %d\n", chunk);
                } else {
                    printf("Invalid argument to -c. Must < 1001 & > 0.\n");
                    exit(-1);
                }
                break;

            case 'p':
                i_tmp = atoi(optarg);
                if (i_tmp > 1023 && i_tmp < 65535) {
                    port = i_tmp;
                } else {
                    printf("Invalid argument to -p. Must be < 65535 & > 1023.\n");
                    exit(-1);
                }
                break;

            case 'q':
                i_tmp = atoi(optarg);
                if (i_tmp < 1) {
                    printf("Invalid argument to -q. Must be > 0.\n");
                    exit(-1);
                } else {
                    queue = i_tmp;
                }
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

            case 1:
                if (strlen(optarg) >= 255) {
                    fprintf(stderr, "host name is too long\n");
                    exit(-1);
                }
                strcpy(host, optarg);
                break;

            /* case ET name in */
            case 2:
                if (strlen(optarg) >= ET_FILENAME_LENGTH) {
                    fprintf(stderr, "input ET file name is too long\n");
                    exit(-1);
                }
                strcpy(etNameIn, optarg);
printf("Input ET file name is set to %s\n", etNameIn);
                break;

            /* case ET name out */
            case 3:
                if (strlen(optarg) >= ET_FILENAME_LENGTH) {
                    fprintf(stderr, "output ET file name is too long\n");
                    exit(-1);
                }
                strcpy(etNameOut, optarg);
printf("Output ET file name is set to %s\n", etNameOut);
                break;

            /* case ET station in */
            case 4:
                if (strlen(optarg) >= ET_STATNAME_LENGTH) {
                    fprintf(stderr, "input station name is too long\n");
                    exit(-1);
                }
                strcpy(statIn, optarg);
                break;

            /* case ET station position (0 = GC) */
            case 5:
                i_tmp = atoi(optarg);
                if (i_tmp < 1) {
                    fprintf(stderr, "Invalid argument to -spos. Must be > 0 (pos of GC).\n");
                    exit(-1);
                }
                statPos = i_tmp;
                break;

            /* case nd */
            case 6:
                noDelay = 1;
                break;

            /* case nb */
            case 7:
                nonBlock = 1;
                break;

            case 'v':
                debugLevel = ET_DEBUG_INFO;
                break;

            case 'd':
                multicast = 0;
                break;

            case ':':
            case 'h':
            case '?':
            default:
                errflg++;
        }
    }


    if (optind < argc || errflg || strlen(etNameIn) < 1 || strlen(etNameOut) < 1) {
        fprintf(stderr,
                "\nusage: %s  %s\n%s\n%s\n%s\n%s\n\n",
                argv[0], "-fin <input ET name>  -fout <output ET name>",
                "                     [-h] [-v] [-d] [-nd] [-nb]",
                "                     [-host <host>] [-c <chunk size>] [-q <station q size>]",
                "                     [-sin <station name>] [-spos <station position]",
                "                     [-p <port>] [-i <interface addr>] [-a <mcast addr>]");


        fprintf(stderr, "          -sin   input station name (defaults to \"TransferOut\")\n");
        fprintf(stderr, "          -spos  input station position (defaults to last)\n");
        fprintf(stderr, "          -q     input station queue size if nonblocking (defaults to 10)\n");
        fprintf(stderr, "          -nb    input station is nonblocking\n");
        fprintf(stderr, "          -v     verbose output\n");
        fprintf(stderr, "          -h     help\n\n");

        fprintf(stderr, "          -c     chunk = number of events in one get/put operation (defaults to 100)\n");
        fprintf(stderr, "          -d     attach to output ET by a direct connection (NOT by multicasting)\n");
        fprintf(stderr, "          -host  output ET system's host if directly connecting (default = local)\n\n");

        fprintf(stderr, "          -p     ET system port (TCP for direct, UDP for multicast)\n");
        fprintf(stderr, "          -i     outgoing network interface address (dot-decimal)\n");
        fprintf(stderr, "          -a     multicast address(es) (dot-decimal), may use multiple times\n");
        fprintf(stderr, "          -nd    use TCP_NODELAY option in socket to output ET\n\n");

        fprintf(stderr, "          This program transfers ET events from one ET system to another.\n");
        fprintf(stderr, "          The input ET system is assumed to be local (shared mem) while the output ET system\n");
        fprintf(stderr, "          is connected to with sockets. This is done to facilitate transferring between 32-bit\n");
        fprintf(stderr, "          and 64-bit systems.\n");
        fprintf(stderr, "          By default, the output ET is found using multicasting. In this case, the\n");
        fprintf(stderr, "          default multicast address and port are used unless specified by -a and -p.\n");
        fprintf(stderr, "          The output ET may also be connected to directly. In this case, specify -host and -p.\n");
        fprintf(stderr, "          If the output ET is on another node, the outgoing network interface may be\n");
        fprintf(stderr, "          specified with -i.\n\n");
        exit(2);
    }


    /*************************/
    /* Setup signal handling */
    /*************************/
    /* Block all signals */
    sigfillset(&sigblock);
    status = pthread_sigmask(SIG_BLOCK, &sigblock, NULL);
    if (status != 0) {
        printf("%s: pthread_sigmask failure\n", argv[0]);
        exit(1);
    }

    /* Spawn signal handling thread */
    pthread_create(&tid, NULL, signal_thread, (void *)NULL);


    /********************************/
    /* Open local, input ET system  */
    /********************************/
    et_open_config_init(&openconfig);
    et_open_config_sethost(openconfig, ET_HOST_LOCAL);
    et_open_config_setwait(openconfig, ET_OPEN_WAIT);
    et_open_config_settimeout(openconfig, timeout);
    et_open_config_setdebugdefault(openconfig, debugLevel);
    if (et_open(&id_from, etNameIn, openconfig) != ET_OK) {
        printf("%s: et_open problems\n", argv[0]);
        exit(1);
    }
printf("open et = %s\n", etNameIn);

    /************************************/
    /* Open output (remote?) ET system  */
    /************************************/
    if (multicast) {
        if (mcastAddrCount < 1) {
            /* Use default mcast address if not given on command line */
            status = et_open_config_addmulticast(openconfig, ET_MULTICAST_ADDR);
        }
        else {
            /* Add multicast addresses to use  */
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

        if (port < 1) {
            /* Use default */
            port = ET_MULTICAST_PORT;
        }
        et_open_config_setport(openconfig, port);
        et_open_config_setcast(openconfig, ET_MULTICAST);
        et_open_config_sethost(openconfig, ET_HOST_ANYWHERE);
        /* Make sure we use sockets */
        et_open_config_setmode(openconfig, ET_HOST_AS_REMOTE);
        et_open_config_setdebugdefault(openconfig, debugLevel);
    }
    else {
        /* Use a direct connection to ET's TCP server */
        if (port < 1) {
            port = ET_SERVER_PORT;
        }
        et_open_config_setserverport(openconfig, port);
        et_open_config_setcast(openconfig, ET_DIRECT);
        et_open_config_sethost(openconfig, host);
        et_open_config_setmode(openconfig, ET_HOST_AS_REMOTE);
    }

    /* Defaults are to use operating system default buffer sizes and turn off TCP_NODELAY */
    if (noDelay) {
        et_open_config_settcp(openconfig, 0, 0, noDelay);
    }

    /* Specifying outgoing network interface */
    if (strlen(interface) > 6) {
        et_open_config_setinterface(openconfig, interface);
    }

    if (et_open(&id_to, etNameOut, openconfig) != ET_OK) {
        printf("%s: et_open problems\n", argv[0]);
        exit(1);
    }

printf("open et = %s\n", etNameOut);

    et_open_config_destroy(openconfig);


    /************************************/
    /* Both ETs have been opened.       */
    /************************************/
    /* Create input station.            */
    /************************************/
    et_station_config_init(&sconfig);
    if (nonBlock) {
        et_station_config_setblock(sconfig, ET_STATION_NONBLOCKING);
        et_station_config_setcue(sconfig, queue);
    }
    else {
        et_station_config_setblock(sconfig, ET_STATION_BLOCKING);
    }
    if ((status = et_station_create_at(id_from, &stat_from, statIn, sconfig, statPos, 0)) < ET_OK) {
        if (status == ET_ERROR_EXISTS) {
            /* stat_from contains pointer to existing station */;
            printf("%s: station already exists\n", argv[0]);
        }
        else {
            printf("%s: error in station creation\n", argv[0]);
            goto error;
        }
    }
    et_station_config_destroy(sconfig);

printf("created station = %s\n", statIn);

    /************************************/
    /* Attach to stations.              */
    /************************************/
    if (et_station_attach(id_from, stat_from, &att_from) != ET_OK) {
        printf("%s: error in station attach\n", argv[0]);
        goto error;
    }

    if (et_station_attach(id_to, ET_GRANDCENTRAL, &att_to) != ET_OK) {
        printf("%s: error in station attach\n", argv[0]);
        goto error;
    }

    printf("attached to station = %s and to GC\n", statIn);

    /************************************/
    /* Transfer events forever          */
    /************************************/
    et_bridge_config_init(&bconfig);
    et_bridge_config_setchunkto(bconfig, chunk);
    et_bridge_config_setchunkfrom(bconfig, chunk);

    status = ET_OK;
    while (status == ET_OK) {
        printf("Call et_events_bridge\n\n");
        status = et_events_bridge(id_from, id_to, att_from, att_to,
                                  bconfig, NUMEVENTS, &ntransferred);
        printf("status from et_bridge = %d, ntransferred = %d\n", status, ntransferred);
    }


    et_bridge_config_destroy(bconfig);
    et_forcedclose(id_from);
    et_forcedclose(id_to);
    return 0;

    error:
    return -1;
};



/*************************************************/
/*   Separate thread to handle signals           */
/*************************************************/
static void * signal_thread (void *arg)
{
    sigset_t   signal_set;
    int        sig_number;

    sigemptyset(&signal_set);
    sigaddset(&signal_set, SIGINT);

    /* Not necessary to clean up as ET system will do it */
    sigwait(&signal_set, &sig_number);
    printf("et_2_et: got a control-C, exiting\n");
    exit(1);
}
