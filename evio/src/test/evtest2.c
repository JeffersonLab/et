/************************************************* 
*    Simple test program for opening and reading *
*   an existing CODA event format file.          *
*                                                *
*   Author: David Abbott  CEBAF                  *
*                                                *
*   Arguments to the routine are:                *
*                                                *
*             evt <filename> [nevents]           *
*                                                *
**************************************************/

#include <stdlib.h>
#include <stdio.h>
#include "evio.h"

#define MAXBUFLEN  4096



int main (argc, argv)
     int argc;
     char **argv;
{
  int handle, nevents, status;
  unsigned int buf[MAXBUFLEN];
  int maxev =0;
  

  if(argc < 2) {
    printf("Incorrect number of arguments:\n");
    printf("  usage: evt filename [maxev]\n");
    exit(-1);
  }

  if ( (status = evOpen(argv[1],"r",&handle)) < 0) {
    printf("Unable to open file %s status = %d\n",argv[1],status);
    exit(-1);
  } else {
    printf("Opened %s for reading\n",argv[1]);
  }

  if(argc==3) {
    maxev = atoi(argv[2]);
    printf("Read at most %d events\n",maxev);
  } else {
    maxev = 0;
  }

  nevents=0;
  while ((status=evRead(handle,buf,MAXBUFLEN))==0) {
    nevents++;
    printf("  event %d len %u type %u\n",nevents,buf[0],(buf[1]&0xffff0000)>>16);
    if((nevents >= maxev) && (maxev != 0)) break;
  }

  printf("last read status 0x%x\n",status);
  evClose(handle);
  
  exit(0);
}
