/* etInsertEventR.c
 *
 *  Updated etInsertEvent routine to access a CODA3 ET System
 *  either locally or remotely.
 *
 */



#include <stdio.h>
#include <time.h>
#include "et.h"
#include "et_private.h"


#define OK 0
#define ERROR -1
#define DEBUG 1  /* if 1, print some debug statements */




int etInsertEventR(int evbuffer[], char* et_name, char *et_host, int et_port)
{  
  int        i, j, status;
  et_att_id  et_attach;
  et_sys_id  et_sys;
  et_event   *pe;
  et_openconfig openconfig;
  char   *pdata;
  size_t et_eventsize;
  struct timespec timeout;
  int sendbufsize;

  timeout.tv_sec  = 10;
  timeout.tv_nsec = 0;

  /* Establish the Size of the record to be sent */
  sendbufsize =  (evbuffer[0])*4;
  if(sendbufsize<40) {  /* Must include at least EVIO Header and 1 Bank Header */
    printf("etInsertEventR: ERROR: Event size is too small (%d words)\n",evbuffer[0]);
    return ERROR;
  }

  if(DEBUG) printf("In etInsertEventR: event length %d bytes  et_filename %s \n",sendbufsize,et_name);

  /* Initialize a new ET config structure */
  if (et_open_config_init(&openconfig) != ET_OK) {
    printf("etInsertEventR: ERROR: cannot allocate mem to open ET system\n");
    return ERROR;
  }
  et_open_config_setwait(openconfig, ET_OPEN_WAIT);
  et_open_config_settimeout(openconfig, timeout);

  /* If et_host is NULL then we should default to REMOTE, MULTICAST access
     else we will try a direct connection */
  if(et_host == NULL) {
     if(et_port == 0) et_port = 23912;   /* default to UDP Multicast port */
     
     /* always connect as if the Process is remote */
    et_open_config_sethost(openconfig,ET_HOST_ANYWHERE);
    et_open_config_setmode(openconfig,ET_HOST_AS_REMOTE);

    et_open_config_setcast(openconfig,ET_MULTICAST);
    et_open_config_addmulticast(openconfig,"239.200.0.0");
    et_open_config_setport(openconfig,et_port);
    et_open_config_setTTL(openconfig,16);

  }else if(strcmp(et_host,"localhost")==0) {   
    /* Try to connect to a Local - same host - ET */
    printf("etInsertEventR: Connecting to ET on the localhost\n");
    et_open_config_sethost(openconfig,ET_HOST_LOCAL);
    et_open_config_setmode(openconfig,ET_HOST_AS_LOCAL);

  }else{
    if(et_port == 0) et_port = 23911;   /* default to TCP port */
    printf("etInsertEventR: Connecting directly to ET on host %s and port %d\n",et_host,et_port);
    et_open_config_sethost(openconfig,et_host);
    et_open_config_setcast(openconfig,ET_DIRECT);
    et_open_config_setserverport(openconfig,et_port);
  }


  /* Sometimes we see a problem with Processes failing to connect to a remote ET system
     when there are many other processes attempting to connect at the same time (at Prestart).
     So if the et_open fails we will try one additional time to see if it can connect
     on the second attempt  */
  if (et_open(&et_sys, et_name, openconfig) != ET_OK) {
    printf(" **Failure to connect to ET - Will try one more time...\n");
    if (et_open(&et_sys, et_name, openconfig) != ET_OK) {
      printf ("etInsertEventR: ERROR: cannot open ET system (%s)\n",et_name);
      return ERROR;
    }
  }

  /* Dump the config structure - We do not need it anymore */
  et_open_config_destroy(openconfig);

  /* Set level of debug output */
  et_system_setdebug(et_sys, ET_DEBUG_ERROR);

  if ((status=et_station_attach(et_sys, ET_GRANDCENTRAL, &et_attach)) < 0) {
    et_close(et_sys);
    printf ("etInsertEventR: ERROR: cannot attach to ET station (status=%d)\n",status);
    return ERROR;
  }

  /* Find out event size in this ET system */
  if (et_system_geteventsize(et_sys, &et_eventsize) != ET_OK) {
    et_close(et_sys);
    printf ("etInsertEventR: ERROR: cannot establish event size in ET system\n");
    return ERROR;
  }

  /* Check if et_system is setup for large buffers */
  if(et_eventsize < sendbufsize) {
    et_close(et_sys);
    printf ("etInsertEventR: ERROR: ET system event size (%d bytes) is smaller than the output buffer (%d bytes)\n",
            (int)et_eventsize, sendbufsize);
    return ERROR;
  }else{
    if (DEBUG) printf("etInsertEventR: ET Buffer size = %d bytes - OK (> User Event size: %d bytes)\n", (int)et_eventsize, sendbufsize);
  }

  if (DEBUG) printf ("etInsertEventR: ET fully initialized\n");

  /* get new/unused event */  
  status = et_event_new(et_sys, et_attach, &pe, ET_SLEEP, NULL, sendbufsize);
  if (status != ET_OK) {
     printf("etInsertEventR: ERROR in getting a new event (et_event_new)\n");
     return ERROR;
  }

  et_event_getdata(pe, (void **)&pdata);
  et_event_setlength(pe, sendbufsize);

  memcpy((void*)pdata, (const void *)evbuffer, sendbufsize);

  if(DEBUG) printf("getdata, setlength, memcpy ok\n");

  /* put event back into the ET system */
  status = et_event_put(et_sys, et_attach, pe);
  if (status != ET_OK) {
     printf("etInsertEventR: put error\n");
     return ERROR;
  }
  
  if (et_station_detach(et_sys, et_attach) != ET_OK) {
    printf("etInsertEventR: ERROR: et_station_detach\n");
  }
  if (et_close(et_sys) != ET_OK) {
    printf("etInsertEventR: ERROR: et_close\n");
  }

  if(DEBUG) printf("etInsertEventR finished successfully\n");


  return OK;
}
