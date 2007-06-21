/*
 *  evio_util.c
 *
 *   Assorted evio utilities
 *
 *   Author:  Elliott Wolin, JLab, 23-aug-2005
*/

/* still to do
 * -----------
 *    confusion over container and data types in nh,lh
*/


/* include files */
#include <stdio.h>
#include <stdlib.h>
#include <evio.h>


/* prototypes */
static void parse_bank(unsigned int *buf, int ftype, int depth, NH_TYPE nh, LH_TYPE lh, void *userArg);
static void loop_over_banks(unsigned int *data, int length, int type, int depth, NH_TYPE nh, LH_TYPE lh, void *userArg);


/* container types used locally */
enum {
  BANK = 0,
  SEGMENT,
  TAGSEGMENT,
};



/*---------------------------------------------------------------- */


void evio_stream_parse(unsigned int *buf, NH_TYPE nh, LH_TYPE lh, void *userArg) {

  int depth=0;
  parse_bank(buf,BANK,depth,nh,lh,userArg);

  return;
}


/*---------------------------------------------------------------- */


static void parse_bank(unsigned int *buf, int ftype, int depth, NH_TYPE nh, LH_TYPE lh, void *userArg) {

  int length,tag,type,num,dataOffset;


  /* get type-dependent info */
  switch(ftype) {
  case 0:
    length  	= buf[0]+1;
    tag     	= buf[1]>>16;
    type   	= (buf[1]>>8)&0xff;
    num     	= buf[1]&0xff;
    dataOffset  = 2;
    break;

  case 1:
    length  	= (buf[0]&0xffff)+1;
    type    	= (buf[0]>>16)&0xff;
    tag     	= (buf[0]>>24)&0xff;
    num     	= 0;
    dataOffset  = 1;
    break;
    
  case 2:
    length  	= (buf[0]&0xffff)+1;
    type    	= (buf[0]>>16)&0xf;
    tag     	= (buf[0]>>20)&0xfff;
    num     	= 0;
    dataOffset  = 1;
    break;

  default:
    printf("?illegal fragment type in parse_bank: %d",ftype);
    exit(EXIT_FAILURE);
    break;
  }


  /* 
   * if a leaf or data node, call leaf handler.
   * if an intermediate node, call node handler and then loop over contained banks.
  */
  switch (type) {

  case 0x0:
  case 0x1:
  case 0x2:
  case 0xb:
    if(lh!=NULL)lh(&buf[dataOffset],length-dataOffset,ftype,tag,type,num,depth,userArg);
    break;

  case 0x3:
  case 0x6:
  case 0x7:
    if(lh!=NULL)lh((char*)(&buf[dataOffset]),(length-dataOffset)*4,ftype,tag,type,num,depth,userArg);
    break;

  case 0x4:
  case 0x5:
    if(lh!=NULL)lh((short*)(&buf[dataOffset]),(length-dataOffset)*2,ftype,tag,type,num,depth,userArg);
    break;

  case 0x8:
  case 0x9:
  case 0xa:
    if(lh!=NULL)lh((long long*)(&buf[dataOffset]),(length-dataOffset)/2,ftype,tag,type,num,depth,userArg);
    break;

  case 0xe:
  case 0x10:
  case 0xd:
  case 0x20:
  case 0xc:
  case 0x40:
    if(nh!=NULL)nh(length,ftype,tag,type,num,depth,userArg);
    depth++;
    loop_over_banks(&buf[dataOffset],length-dataOffset,type,depth,nh,lh,userArg);
    depth--;
    break;
  }

  return;
}


/*---------------------------------------------------------------- */


static void loop_over_banks(unsigned int *data, int length, int type, int depth, NH_TYPE nh, LH_TYPE lh, void *userArg) {

  int p=0;

  switch (type) {

  case 0xe:
  case 0x10:
    while(p<length) {
      parse_bank(&data[p],BANK,depth,nh,lh,userArg);
      p+=data[p]+1;
    }
    break;

  case 0xd:
  case 0x20:
    while(p<length) {
      parse_bank(&data[p],SEGMENT,depth,nh,lh,userArg);
      p+=(data[p]&0xffff)+1;
    }
    break;

  case 0xc:
  case 0x40:
    while(p<length) {
      parse_bank(&data[p],TAGSEGMENT,depth,nh,lh,userArg);
      p+=(data[p]&0xffff)+1;
    }
    break;

  }


  return;
}


/*---------------------------------------------------------------- */


const char *get_typename(int type) {

  switch (type) {

  case 0x0:
    return("unknown32");
    break;

  case 0x1:
    return("uint32");
    break;

  case 0x2:
    return("float32");
    break;

  case 0x3:
    return("string");
    break;

  case 0x4:
    return("int16");
    break;

  case 0x5:
    return("uint16");
    break;

  case 0x6:
    return("int8");
    break;

  case 0x7:
    return("uint8");
    break;

  case 0x8:
    return("float64");
    break;

  case 0x9:
    return("int64");
    break;

  case 0xa:
    return("uint64");
    break;

  case 0xb:
    return("int32");
    break;

  case 0xf:
    return("repeating");
    break;

  case 0xe:
  case 0x10:
    return("bank");
    break;

  case 0xd:
  case 0x20:
    return("segment");
    break;

  case 0xc:
  case 0x40:
    return("tagsegment");
    break;

  default:
    return("unknown");
    break;
  }
}


/*---------------------------------------------------------------- */


int is_container(int type) {

  switch (type) {
    
  case (0xc):
  case (0xd):
  case (0xe):
  case (0x10):
  case (0x20):
  case (0x40):
    return(1);

  default:
    return(0);
  }
}


/*---------------------------------------------------------------- */


