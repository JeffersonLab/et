/* fileToEvent.c

   (SAW) 6/29/94 Program to write a file as a CODA event.  The first
   argument is the filename, the second optional argument is the event type.
   If the event type is omitted, then use event type (tag) 130.

   R. Michaels, Jan 2000.
   Modified to use with CODA 2.2 and ET system.
   Also simplified from a version I got from S. Wood, mainly 
   to make arguments are compatible with old versions of fileToEvent
   so I don't have to change any scripts.

   D. Abbott, Feb 2020.
   Modified to work with CODA 3 and EVIO version 4. This is not backwards
   compatible with CODA 2 distributions.

*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>

#define DEBUG 1
#define OK 0

#define ETFILE_HEADER "/tmp/et_"
#define EVIO_HEADER_LEN  12    /* Length of EVIO file and Bank headers (8 + 4)*/

#define BUFLEN 20000
#define DINT 1
#define DSTRING 3    /* 1=integer(32 bit), 2=floating, 3=string */

int etInsertEventR(int evbuffer[], char* et_name, char *et_host, int et_port);


int main(int argc,char **argv)
{
  int c;
  int ilen;
  FILE *fid;
  int tag;
  int default_tag = 130;
  int datatype, bor, et_port=0;
  char *expid;
  char *er_name=NULL;
  char *etfile=NULL;
  char *et_host=NULL;
  char *filename=NULL;
  int ev_len;

  struct EVBUF {
    int header[EVIO_HEADER_LEN];
    union {
      char cbuf[BUFLEN];
      int ibuf[BUFLEN];
    } buf;
  } cbuf;

  int num;
  int stat;

  /*
     This simple program constructs an event from an ascii file and a
     given tag and inserts it into the data stream.
   */

  if (DEBUG) {
    printf("Entering fileToEvent ... \n");
    printf("argc = %d \n",argc);
  }

  if(argc<4) {
    printf("ERROR: Syntax: fileToEvent <file_name> <ER_name> <Event_tag> [<data_type> <BOR> <ET_host> <ET_port>]\n");
    printf("  file_name: The file to insert as a User Event\n");
    printf("  ER_name  : The name of the Event Recorder component (eg ER1)\n");
    printf("  Event_tag: The Event Tag/ID (16 bit integer < 0xff00)\n");
    printf("     --OPTIONAL arguments--\n");
    printf("  Data_type: Either 1,2, or 3 (int, float, string). String is the default.\n");
    printf("  BOR      : Flag to make this a Beginning of Record Event (default is 0 - No BOR)\n");
    printf("  ET_Host  : ET host (name or IP) for TCP or \"localhost\" for Direct (default - Multicast)\n"); 
    printf("  ET_Port  : Port number to attach to (defaults are 23911 TCP and 23912 UDP/Multicast)\n");
    exit(1);
  }

  /* Check all the arguments */
  er_name = malloc(strlen(argv[2])+2);
  strcpy(er_name,"_");
  strcat(er_name,argv[2]);
  printf("Setting er_name to \"%s\"\n",er_name);

  tag = atoi(argv[3]);
  if((tag==0)||(tag>=0xff00)) {
    printf("ERROR: Invalid Event tag - %d\n",tag);
    exit(1);
  }
  
  /*defaults*/
  datatype = DSTRING;
  bor=0;

  if(argc>=5) datatype = atoi(argv[4]);
  if(datatype != DSTRING && datatype != DINT) {
    printf("WARN: Invalid data type %d, assumed to be String\n",datatype);
    datatype = DSTRING;
  }

  if(argc>=6) bor = atoi(argv[5]);
  if(bor)
    printf("INFO: This Event will be inserted as a Beginning of Record Event\n");

  if(argc>=7) {
    et_host = malloc(strlen(argv[6]+2));
    strcat(et_host,argv[6]);
    printf("Setting ET hostname/IP to \"%s\"\n",et_host);
  } else {
    printf("Multicast to find ET System on the network\n");
  }
  if(argc>=8) {
    et_port = atoi(argv[7]);
    printf("Setting ET listening port to %d\n",et_port);
  }
  
  if(datatype != DSTRING && datatype != DINT) {
    printf("WARN: Invalid data type %d, assumed to be String\n",datatype);
    datatype = DSTRING;
  }

  /* Get the Run Control Platform name */
  if(!expid) {
    char *s;
    s = getenv("EXPID");
    if(s) {
      expid = malloc(strlen(s)+1);
      strcpy(expid,s);
    }else{
      printf("ERROR: Environment variable EXPID is not defined\n");
      exit(1);
    }
  }

  if((expid) && (DEBUG)) printf("EXPID = %s ",expid);
  if((er_name) && (DEBUG)) printf("er_name = %s ",er_name);

  if(strlen(er_name)==0) {
    free(er_name);
    er_name = 0;
  }

  if(er_name) {
    etfile = malloc(strlen(ETFILE_HEADER) + strlen(expid) + strlen(er_name) + 1);
    strcpy(etfile,ETFILE_HEADER);
    strcat(etfile,expid);
    strcat(etfile,er_name);
  }

  fid = fopen(argv[1],"r");
  if(fid==NULL) {
      printf("ERROR: The file %s does not exist \n",argv[1]);
      exit(1);
  }
  ilen = 0;
  if(datatype==DSTRING) {
    while(ilen<(BUFLEN-1) && (c = fgetc(fid))!=EOF)
      cbuf.buf.cbuf[ilen++] = c;
    cbuf.buf.cbuf[ilen] = 0;
    ev_len = EVIO_HEADER_LEN + (ilen+3)/sizeof(int);
    if(ilen < 2) {
      printf("ERROR: String event too short, not written\n");
      exit(0);			/* Return success */
    }
  } else if(datatype==DINT) {
    while(ilen<(BUFLEN-1) && fscanf(fid,"%x",&num)!=EOF) 
      cbuf.buf.ibuf[ilen++] = num;
    ev_len = EVIO_HEADER_LEN + ilen;
    if(ilen < 1) {
      printf("ERROR: Integer event too short, not written\n");
      exit(0);			/* Return success */
    }
  }
  fclose(fid);


  /* Wrap the file data in a CODA 3 EVIO and Bank header */
  /* EVIO 4 file header */
  cbuf.header[0] = ev_len;
  cbuf.header[1] = 0;
  cbuf.header[2] = 8;
  cbuf.header[3] = 1;
  cbuf.header[4] = 0;
  if(bor)
    cbuf.header[5] = 0x00005204;
  else
    cbuf.header[5] = 0x00001204;
  cbuf.header[6] = 0;
  cbuf.header[7] = 0xc0da0100;

  /*User Event and Bank headers */ 
  cbuf.header[8] = ev_len - cbuf.header[2] - 1; /* Event length for User event header */
  cbuf.header[9] = (tag<<16) + 0x1000;
  cbuf.header[10] = cbuf.header[8] - 2;	        /* Event length in bank header */
  cbuf.header[11] = datatype<<8;	        /* data type.  No tag for now */

/* use etInsertEvent */
  //  if(DEBUG) printf("calling etInsertEvent... \n");
  //  stat = etInsertEvent(cbuf.header,etfile);   

/* Try using the Remote ET access - default to Multicasting */
  if(DEBUG) printf("calling etInsertEventR... \n");
  stat = etInsertEventR(cbuf.header,etfile,et_host,et_port);

  if(stat==OK) {
      if(DEBUG) printf("fileToEvent: Success!\n");
      exit(0);			/* Acceptable return code */
   } else {
      printf("fileToEvent terminating due to etInsertEvent status=%d\n",stat);
      exit(1);
  }

}
