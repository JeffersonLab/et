/*
 *  evioswap.c
 *
 *   evioswap() swaps one evio version 2 event
 *       - in place if dest is NULL
 *       - copy to dest if not NULL
 *
 *   swap_long_val() swaps one long, call by val
 *   swap_long() swaps an array of unsigned longs
 *
 *   thread safe
 *
 *
 *   Author: Elliott Wolin, JLab, 21-nov-2003
 *
 *
 *  Notes:
 *     simple loop in swap_xxx takes 50% longer than pointers and unrolled loops
 *     -O is over twice as fast as -g
 *
 *  To do:
 *     use in evio.c, replace swap_util.c
 *
*/


/* include files */
#include <stdlib.h>


/* entry points */
void evioswap(unsigned long *buffer, int tolocal, unsigned long *dest);
int swap_long_value(int val);
unsigned long *swap_long(unsigned long *data, unsigned long length,unsigned long *dest);


/* internal prototypes */
static void swap_bank(unsigned long *buf, int tolocal, unsigned long *dest);
static void swap_segment(unsigned long *buf, int tolocal, unsigned long *dest);
static void swap_tagsegment(unsigned long *buf, int tolocal, unsigned long *dest);
static void swap_data(unsigned long *data, unsigned long type, unsigned long length,
		      int tolocal,  unsigned long *dest);
static void swap_longlong(unsigned long long *data, unsigned long length,
			  unsigned long long *dest);
static void swap_short(unsigned short *data, unsigned long length, unsigned short *dest);
static void copy_data(unsigned long *data, unsigned long length, unsigned long *dest);


/*--------------------------------------------------------------------------*/


void evioswap(unsigned long *buf, int tolocal, unsigned long *dest) {

  swap_bank(buf,tolocal,dest);

  return;
}


/*---------------------------------------------------------------- */


static void swap_bank(unsigned long *buf, int tolocal, unsigned long *dest) {

  unsigned long data_length,data_type;
  unsigned long *p=buf;


  /* swap header, get length and contained type */
  if(tolocal)p = swap_long(buf,2,dest);
  data_length  = p[0]-1;
  data_type    = (p[1]>>8)&0xff;
  if(!tolocal)swap_long(buf,2,dest);
  
  swap_data(&buf[2], data_type, data_length, tolocal, (dest==NULL)?NULL:&dest[2]);

  return;
}


/*---------------------------------------------------------------- */


static void swap_segment(unsigned long *buf, int tolocal, unsigned long *dest) {

  unsigned long data_length,data_type;
  unsigned long *p=buf;


  /* swap header, get length and contained type */
  if(tolocal)p = swap_long(buf,1,dest);
  data_length  = (p[0]&0xffff);
  data_type    = (p[0]>>16)&0xff;
  if(!tolocal)swap_long(buf,1,dest);

  swap_data(&buf[1], data_type, data_length, tolocal, (dest==NULL)?NULL:&dest[1]);
  
  return;
}


/*---------------------------------------------------------------- */


static void swap_tagsegment(unsigned long *buf, int tolocal, unsigned long *dest) {

  unsigned long data_length,data_type;
  unsigned long *p=buf;


  /* swap header, get length and contained type */
  if(tolocal)p = swap_long(buf,1,dest);
  data_length  = (p[0]&0xffff);
  data_type    = (p[0]>>16)&0xf;
  if(!tolocal)swap_long(buf,1,dest);

  swap_data(&buf[1], data_type, data_length, tolocal, (dest==NULL)?NULL:&dest[1]);
  
  return;
}


/*---------------------------------------------------------------- */


static void swap_data(unsigned long *data, unsigned long type, unsigned long length, 
	       int tolocal, unsigned long *dest) {

  unsigned long fraglen;
  unsigned long l=0;


  /* swap the data or call swap_fragment */
  switch (type) {


    /* undefined...no swap */
  case 0x0:
    copy_data(data,length,dest);
    break;


    /* long */
  case 0x1:
  case 0x2:
  case 0xb:
    swap_long(data,length,dest);
    break;


    /* char or byte */
  case 0x3:
  case 0x6:
  case 0x7:
    copy_data(data,length,dest);
    break;


    /* short */
  case 0x4:
  case 0x5:
    swap_short((unsigned short*)data,length*2,(unsigned short*)dest);
    break;


    /* longlong */
  case 0x8:
  case 0x9:
  case 0xa:
    swap_longlong((unsigned long long*)data,length/2,(unsigned long long*)dest);
    break;



    /* bank */
  case 0xe:
  case 0x10:
    while(l<length) {
      if(tolocal) {
	swap_bank(&data[l],tolocal,(dest==NULL)?NULL:&dest[l]);
	fraglen=(dest==NULL)?data[l]+1:dest[l]+1;
      } else {
	fraglen=data[l]+1;
	swap_bank(&data[l],tolocal,(dest==NULL)?NULL:&dest[l]);
      }
      l+=fraglen;
    }
    break;


    /* segment */
  case 0xd:
  case 0x20:
    while(l<length) {
      if(tolocal) {
	swap_segment(&data[l],tolocal,(dest==NULL)?NULL:&dest[l]);
	fraglen=(dest==NULL)?(data[l]&0xffff)+1:(dest[l]&0xffff)+1;
      } else {
	fraglen=(data[l]&0xffff)+1;
	swap_segment(&data[l],tolocal,(dest==NULL)?NULL:&dest[l]);
      }
      l+=fraglen;
    }
    break;


    /* tagsegment */
  case 0xc:
  case 0x40:
    while(l<length) {
      if(tolocal) {
	swap_tagsegment(&data[l],tolocal,(dest==NULL)?NULL:&dest[l]);
	fraglen=(dest==NULL)?(data[l]&0xffff)+1:(dest[l]&0xffff)+1;
      } else {
	fraglen=(data[l]&0xffff)+1;
	swap_tagsegment(&data[l],tolocal,(dest==NULL)?NULL:&dest[l]);
      }
      l+=fraglen;
    }
    break;


    /* unknown type, just copy */
  default:
    copy_data(data,length,dest);
    break;
  }

  return;
}


/*---------------------------------------------------------------- */


int swap_long_value(int val) {

  int temp;
  char *t = (char*)&temp+4;
  char *v = (char*)&val;
  
  *--t=*(v++);
  *--t=*(v++);
  *--t=*(v++);
  *--t=*(v++);

  return(temp);
}


/*---------------------------------------------------------------- */


unsigned long *swap_long(unsigned long *data, unsigned long length, unsigned long *dest) {

  unsigned long i;
  unsigned long temp;
  char *d,*t;

  if(dest==NULL) {

    d=(char*)data+4;
    for(i=0; i<length; i++) {
      temp=data[i];
      t=(char*)&temp;
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      d+=8;
    }
    return(data);

  } else {

    d=(char*)dest+4;
    t=(char*)data;
    for(i=0; i<length; i++) {
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      d+=8;
    }
    return(dest);
  }
}


/*---------------------------------------------------------------- */


static void swap_longlong(unsigned long long *data, unsigned long length, unsigned long long *dest) {

  unsigned long i;
  unsigned long long temp;
  char *d,*t;

  if(dest==NULL) {

    d=(char*)data+8;
    for(i=0; i<length; i++) {
      temp=data[i];
      t=(char*)&temp;
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      d+=16;
    }

  } else {
    
    d=(char*)dest+8;
    t=(char*)data;
    for(i=0; i<length; i++) {
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      *--d=*(t++);
      d+=16;
    }
    
    return;
  }
}


/*---------------------------------------------------------------- */


static void swap_short(unsigned short *data, unsigned long length, unsigned short *dest) {

  unsigned long i;
  unsigned short temp;
  char *d,*t;

  if(dest==NULL) {

    d=(char*)data+2;
    for(i=0; i<length; i++) {
      temp=data[i];
      t=(char*)&temp;
      *--d=*(t++);
      *--d=*(t++);
      d+=4;
    }

  } else {

    d=(char*)dest+2;
    t=(char*)data;
    for(i=0; i<length; i++) {
      *--d=*(t++);
      *--d=*(t++);
      d+=4;
    }
    
    return;
  }
}
 
 
/*---------------------------------------------------------------- */
 
 
static void copy_data(unsigned long *data, unsigned long length, unsigned long *dest) {
   
  int i;
  
  if(dest==NULL)return;
  for(i=0; i<length; i++)dest[i]=data[i];
  return;
}
 
 
/*---------------------------------------------------------------- */
