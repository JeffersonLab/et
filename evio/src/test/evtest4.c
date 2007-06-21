#include <stdlib.h>
#include <stdio.h>
#include <evio.h>

#define MAXBUFLEN  4096




void nh(int length, int ftype, int tag, int type, int num, int depth, void *userArg) {
  printf("node   depth %2d  ftype %3d   type,tag,num,length:  %6d %6d %6d %6d\n",
         depth,ftype,type,tag,num,length);
} 

void lh(void *data, int length, int ftype, int tag, int type, int num, int depth, void *userArg) {

  int *l;
  short *s;
  char *c;
  float *f;
  double *d;
  long long *ll;

  printf("leaf   depth %2d  ftype %3d   type,tag,num,length:  %6d %6d %6d %6d     data:   ",
         depth,ftype,type,tag,num,length);

  switch (type) {

  case 0x0:
  case 0x1:
  case 0xb:
    l=(int*)(& ((int *)data)[0]);
    printf("%d %d\n",l[0],l[1]);
    break;


  case 0x2:
    f=(float*)(& ((float *)data)[0]);
    printf("%f %f\n",f[0],f[1]);
    break;

  case 0x3:
    c=(char*)(& ((char *)data)[0]);
    printf("%s\n",c);
    break;

  case 0x6:
  case 0x7:
    c=(char*)(& ((char *)data)[0]);
    printf("%d %d\n",c[0],c[1]);
    break;

  case 0x4:
  case 0x5:
    s=(short*)(& ((short *)data)[0]);
    printf("%hd %hd\n",s[0],s[1]);
    break;

  case 0x8:
    d=(double*)(& ((double *)data)[0]);
    printf("%f %f\n",d[0],d[1]);
    break;

  case 0x9:
  case 0xa:
    ll=(long long*)(& ((long long *)data)[0]);
    printf("%lld %lld\n",ll[0],ll[1]);
    break;

  }  
} 






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
    printf("  *** event %d len %d type %d ***\n",nevents,buf[0],(buf[1]&0xffff0000)>>16);
    evio_stream_parse(buf,nh,lh,NULL);
    if((nevents >= maxev) && (maxev != 0)) break;
  }

  printf("last read status 0x%x\n",status);
  evClose(handle);
  
  exit(0);
}
