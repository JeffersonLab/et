/*-----------------------------------------------------------------------------
 * Copyright (c) 1991,1992 Southeastern Universities Research Association,
 *                         Continuous Electron Beam Accelerator Facility
 *
 * This software was developed under a United States Government license
 * described in the NOTICE file included as part of this distribution.
 *
 * CEBAF Data Acquisition Group, 12000 Jefferson Ave., Newport News, VA 23606
 * Email: coda@cebaf.gov  Tel: (804) 249-7101  Fax: (804) 249-7363
 *-----------------------------------------------------------------------------
 * 
 * Description:
 *	Byte swapping utilities
 *	
 * Author:  Jie Chen, CEBAF Data Acquisition Group
 *
 * Modifications:  EJW, 19-jun-2001 bug fixes
 *
 * Revision History:
 *   $Log: swap_util.c,v $
 *   Revision 1.8  2007/01/09 17:56:30  wolin
 *   Created evioUtil<T>::getContentType() to solve solaris bug
 *
 *   Revision 1.7  2007/01/09 16:09:26  wolin
 *   Minor mods for solaris
 *
 *   Revision 1.6  2003/12/01 18:48:11  wolin
 *   Minor, but now replaced by evioswap
 *
 *   Revision 1.5  2001/09/13 18:53:25  wolin
 *   Added tagsegment support, fixed bugs, removed VAX stuff
 *
 *   Revision 1.3  1999/10/14 18:04:56  rwm
 *   Now compiles with CC for Bob Micheals. Other cleanups.
 *
 *   Revision 1.2  1998/09/21 15:07:22  abbottd
 *   Changes for compile on vxWorks
 *
 *   Revision 1.1.1.1  1996/09/19 18:25:20  chen
 *   original port to solaris
 *
 *	  Revision 1.1  95/01/20  14:00:33  14:00:33  abbottd (David Abbott)
 *	  Initial revision
 *	  
 *	  Revision 1.4  1994/08/12  17:15:18  chen
 *	  handle char string data type correctly
 *
 *	  Revision 1.3  1994/05/17  14:24:19  chen
 *	  fix memory leaks
 *
 *	  Revision 1.2  1994/04/12  18:02:20  chen
 *	  fix a bug when there is no event wrapper
 *
 *	  Revision 1.1  1994/04/11  13:09:18  chen
 *	  Initial revision
 *
 *	  Revision 1.2  1993/11/05  16:54:50  chen
 *	  change comment
 *
 *	  Revision 1.1  1993/10/27  09:39:44  heyes
 *	  Initial revision
 *
 *	  Revision 1.1  93/08/30  19:13:49  19:13:49  chen (Jie chen)
 *	  Initial revision
 *	  
 *   Mods
 *   ----
 *
 *    21-jun-01 ejw added full tagsegment support, removed VAX support
 *
 *
 */


#ifdef VXWORKS
# include <vxWorks.h>
# include <stdlib.h>
# include <stdio.h>
# include <string.h>
# include <errno.h>
#else
# include <stdio.h>
# include <stdlib.h>
# include <string.h>
# include <errno.h>
#endif


typedef struct _stack
{
  int length;      /* inclusive size */
  int posi;        /* event start position */
  int type;        /* data type */
  int tag;         /* tag value */
  int num;         /* num field */
  struct _stack *next;
}evStack;


typedef struct _lk
{
  int head_pos;
  int type;
}LK_AHEAD;         /* find out header */


/*---------------------------------------------------------------------------*/


/**********************************************************
 *   evStack *init_evStack()                              *
 *   set up the head for event stack                      *
 *********************************************************/
static evStack *init_evStack()
{
  evStack *evhead;
  
  evhead = (evStack *)malloc(1*sizeof(evStack));
  if(evhead == NULL){
    fprintf(stderr,"Cannot allocate memory for evStack\n");
    exit (1);
  }
  evhead->length = 0;
  evhead->posi = 0;
  evhead->type = 0x0;
  evhead->tag = 0x0;
  evhead->num = 0x0;
  evhead->next = NULL;
  return evhead;
}


/*---------------------------------------------------------------------------*/


/*********************************************************
 *    evStack *evStack_top(evStack *head)                *
 *  return the top of the evStack pointed by head        *
 ********************************************************/
static evStack *evStack_top(evStack *head)
{
  evStack *p;

  p = head;
  if (p->next == NULL)
    return (NULL);
  else
    return (p->next);
}


/*---------------------------------------------------------------------------*/


/********************************************************
 *    void evStack_popoff(evStack *head)                *
 * pop off the top of the stack item                    *
 *******************************************************/
static void evStack_popoff(evStack *head)
{
  evStack *p,*q;

  q = head;
  if(q->next == NULL){
    fprintf(stderr,"Empty stack\n");
    return;
  }
  p = q->next;
  q->next = p->next;
  free (p);
}


/*---------------------------------------------------------------------------*/


/*******************************************************
 *     void evStack_pushon()                           *
 * push an item on to the stack                        *
 ******************************************************/
static void evStack_pushon(int size,
			   int posi,
			   int type,
			   int tag,
			   int num,
			   evStack *head)
{
  evStack *p, *q;

  p = (evStack *)malloc(1*sizeof(evStack));
  if (p == NULL){
    fprintf(stderr,"Not enough memory for stack item\n");
    exit(1);
  }
  q = head;
  p->length = size;
  p->posi = posi;
  p->type = type;
  p->tag = tag;
  p->num = num;
  p->next = q->next;
  q->next = p;
}


/*---------------------------------------------------------------------------*/


/******************************************************
 *       void evStack_free()                          *
 * Description:                                       *
 *    Free all memory allocated for the stack         *
 *****************************************************/
static void evStack_free(evStack *head)
{
  evStack *p, *q;

  p = head;
  while (p != NULL){
    q = p->next;
    free (p);
    p = q;
  }
}


/*---------------------------------------------------------------------------*/


/*********************************************************
 * int int_swap_byte(int input)                          *
 * get integer 32 bit input and output swapped byte      *
 * integer. input is not changed                         *
 ********************************************************/
int int_swap_byte (int input) 
{
  int temp, i, tt, len;
  char *p, *q;

  tt  = input;
  len = sizeof (int);

  p = (char *)&tt; q = (char *)&temp;

  for(i = 0;i < len; i++)
    q [i] = p [len-i-1];

  return temp;
}


/*---------------------------------------------------------------------------*/


/********************************************************
 * void onmemory_swap(int *buffer)                      *
 * swap byte order of buffer, buffer will be changed    *
 ********************************************************/
void onmemory_swap(int* buffer)
{
  int  temp,des_temp;
  int  i,int_len;
  char *p, *q;

  int_len = sizeof(int);
  memcpy((void *)&temp, (void *)buffer,int_len);
  p = (char *)&temp;
  q = (char *)&des_temp;

  for(i = 0;i < int_len; i++)
    q[i] = p[int_len-i-1];

  memcpy((void *)buffer,(void *)&des_temp,int_len);
}


/*---------------------------------------------------------------------------*/


/********************************************************
 * void swapped_intcpy(void *des,void *source, int size)*
 * copy source with size size to dest, but with byte     *
 * order swapped in the unit of byte                    *
 *******************************************************/
void swapped_intcpy(int *des,char *source,int size)
{
  int temp,des_temp;
  int i,j,int_len;
  char *p, *q, *d;
  
  int_len = sizeof(int);
  d = (char *)des;

  i = 0;
  while(i < size) {
    memcpy ((void *)&temp, (void *)&source[i],sizeof(int));
    p = (char *)&temp; q = (char *)&des_temp;
    for (j = 0; j < int_len; j++)
      q [j] = p [int_len - j - 1];
    memcpy ((void *)&d [i],(void *)&des_temp, int_len);
    i += int_len;
  }
}


/*---------------------------------------------------------------------------*/


/*******************************************************
 * void swapped_shortcpy(char *des, char *source, size)*
 * copy short integer or packet with swapped byte order*
 * ****************************************************/
void swapped_shortcpy (short *des,char *source,int size)
{
  short temp, des_temp;
  int  i, j, short_len;
  char *p, *q, *d;
  
  short_len = sizeof(short);
  d = (char *)des;

  i = 0;
  while(i < size){
    memcpy ((void *)&temp, (void *)&source[i], short_len);
    p = (char *)&temp; q = (char *)&des_temp;
    for (j = 0; j < short_len; j++)
      q[j] = p[short_len -j -1];
    memcpy((void *)&(d[i]), (void *)&des_temp, short_len);
    i += short_len;
  }
}


/*---------------------------------------------------------------------------*/


/*******************************************************
 * void swapped_longcpy(char *des, char *source, size) *
 * copy 64 bit with swapped byte order                 *
 * ****************************************************/
void swapped_longcpy(double *des,char *source,int size)
{
  double temp, des_temp;
  int  i, j, long_len;
  char *p, *q, *d;
  
  long_len = sizeof (double);
  d = (char *)des;

  i = 0;
  while(i < size) {
    memcpy ((void *)&temp, (void *)&source[i], long_len);
    p = (char *)&temp; q = (char *)&des_temp;
    for (j = 0; j < long_len; j++)
      q [j] = p [long_len -j -1];
    memcpy ((void *)&(d[i]), (void *)&des_temp, long_len);
    i += long_len;
  }
}


/*---------------------------------------------------------------------------*/


/*************************************************************
 *   int swapped_fread(void *ptr, int size, int n_itmes,file)*
 *   fread from a file stream, but return swapped result     *
 ************************************************************/ 
int swapped_fread(int *ptr,int size,int n_items,FILE *stream)
{
  char *temp_ptr;
  int  nbytes;

  temp_ptr = (char *)malloc(size*n_items);
  nbytes = fread(temp_ptr,size,n_items,stream);
  if(nbytes > 0){
    swapped_intcpy(ptr,temp_ptr,n_items*size);
  }
  free(temp_ptr);
  return(nbytes);
}
	   

/*---------------------------------------------------------------------------*/


/***********************************************************
 *    void swapped_memcpy(char *buffer,char *source,size)  *
 * swapped memory copy from source to buffer accroding     *
 * to data type                                            *
 **********************************************************/
void swapped_memcpy(char *buffer,char *source,int size)
{
  evStack  *head, *p;
  LK_AHEAD lk;
  int      int_len, short_len, long_len;
  int      i, j, depth, current_type = 0;
  int      header1, header2;
  int      ev_size,  ev_tag, ev_num, ev_type;
  int      bk_size,  bk_tag, bk_num, bk_type;
  int      sg_size,  sg_tag,         sg_type;
  int      tsg_size, tsg_tag,        tsg_type;
  short    pk_size, pk_tag, pack;
  int      temp;
  short    temp2;
  double   temp8;

  int_len = sizeof(int);
  short_len = sizeof(short);
  long_len = sizeof (double);

  head = init_evStack();
  i = 0;   /* index pointing to 16 bit word */

  swapped_intcpy (&ev_size,source,int_len);
  /*ev_size in unit of 32 bit*/
  memcpy ((void *)&(buffer[i*2]), (void *)&ev_size,int_len);
  i += 2;

  swapped_intcpy(&temp,&(source[i*2]),int_len);
  header2 = temp;
  ev_tag =(header2 >> 16) & (0xffff);
  ev_type=(header2 >> 8) & (0xff);
  ev_num = (header2) & (0xff);
  memcpy(&(buffer[i*2]),&temp,int_len);
  i += 2;

  if((ev_type>=0x10)||(ev_type==0xc)||(ev_type==0xd)||(ev_type==0xe)) {
    evStack_pushon((ev_size+1)*2,i-4,ev_type,ev_tag,ev_num,head);
    lk.head_pos = i;
    lk.type = ev_type;
    if((lk.type==0x10)||(lk.type==0xe))ev_size = ev_size + 1;
  }
  else{              /* sometimes event has no wrapper */
    lk.head_pos = i + 2*(ev_size - 1);
    lk.type = ev_type;
    current_type = ev_type;
  }

/* get into the loop */
  while (i < ev_size*2){
    if((p=evStack_top(head)) != NULL) {
      while(((p = evStack_top(head)) != NULL) && i == (p->length + p->posi)){
	evStack_popoff(head);
	head->length -= 1;
      }
    }

    if (i == lk.head_pos){      /* dealing with header */
      if((p = evStack_top(head)) != NULL)
	lk.type = (p->type);

      switch(lk.type) {

      case 0xe:
      case 0x10:
	swapped_intcpy (&temp,&(source[i*2]),int_len);
	header1 = temp;
	bk_size = header1;
	memcpy(&(buffer[i*2]), (void *)&temp,int_len);
	i = i + 2;

	swapped_intcpy (&temp,&(source[i*2]),int_len);
	header2 = temp;
	memcpy(&(buffer[i*2]), (void *)&temp,int_len);

	bk_tag = (header2 >> 16) & (0xffff);
	bk_type = (header2 >> 8) & (0xff);
	bk_num = (header2) & (0xff);
	depth = head->length;  /* tree depth */
	if((bk_type>=0x10)||(bk_type==0xc)||(bk_type==0xd)||(bk_type==0xe)) {
	  evStack_pushon((bk_size+1)*2,i-2,bk_type,bk_tag,bk_num,head);
	  lk.head_pos = i + 2;
	  head->length += 1;
	  i = i + 2;
	}
	else{  /* real data */
	  current_type = bk_type;
	  lk.head_pos = i + bk_size*2;
	  i = i+ 2;
	}
	break;

      case 0xd:
      case 0x20:
	swapped_intcpy (&temp,&(source[i*2]),int_len);
	header2 = temp;
	memcpy(&(buffer[i*2]), (void *)&temp,int_len);

	sg_size = (header2) & (0xffff);
	sg_size = sg_size + 1;
	sg_tag  = (header2 >> 24) & (0xff);
	sg_type = (header2 >> 16) & (0xff);
	if((sg_type>=0x10)||(sg_type==0xc)||(sg_type==0xd)||(sg_type==0xe)) {
	  evStack_pushon((sg_size)*2,i,sg_type,sg_tag,(int)NULL,head);
	  lk.head_pos = i + 2;
	  head->length += 1;
	  i = i+ 2;
	}
	else{  /* real data */
	  current_type = sg_type;
	  lk.head_pos = i + sg_size*2;
	  i = i + 2;
	}
	break;

      case 0xc:
      case 0x40:
	swapped_intcpy (&temp,&(source[i*2]),int_len);
	header2 = temp;
	memcpy(&(buffer[i*2]), (void *)&temp,int_len);

	tsg_size = (header2) & (0xffff);
	tsg_size = tsg_size + 1;
	tsg_tag  = (header2 >> 20) & (0xfff);
	tsg_type = (header2 >> 16) & (0xf);
	if((tsg_type==0xc)||(tsg_type==0xd)||(tsg_type==0xe)) {
	  evStack_pushon((tsg_size)*2,i,tsg_type,tsg_tag,(int)NULL,head);
	  lk.head_pos = i + 2;
	  head->length += 1;
	  i = i+ 2;
	}
	else{  /* real data */
	  current_type = tsg_type;
	  lk.head_pos = i + tsg_size*2;
	  i = i + 2;
	}
	break;

      default:  /* packet type */
	swapped_shortcpy (&temp2,&(source[i*2]),short_len);
	pack = temp2;
	memcpy(&(buffer[i*2]),(void *)&temp2,short_len);

	if(pack == 0x0000){  /* empty packet increase by 1 */
	  lk.head_pos = i + 1;
	  i++;
	}
	else{
	  pk_tag = (pack >> 8) & (0x00ff);
	  pk_size = (pack) & (0x00ff);
	  current_type = lk.type;
	  lk.head_pos = i + pk_size + 1;
	  i = i + 1;
	}
	break;
      }
    }

    else{      /* deal with real data */

      switch(current_type){

      /* no swap */
      case 0x0:  /* unknown data type */
      case 0x3:  /* char string   */
      case 0x6:  /* signed byte   */
      case 0x7:  /* unsigned byte */
      case 0x36:
      case 0x37:
	memcpy(&(buffer[i*2]),&(source[i*2]),(lk.head_pos - i)*2);
	i = lk.head_pos;		
	break;


      /* 4-byte swap */
      case 0x1:   /* unsigned integer    */
      case 0x2:   /* IEEE floating point */
      case 0xb:   /* signed integer      */
      case 0xF:  /* repeating structure, treat as 4-byte for now */
	for(j = i; j < lk.head_pos; j=j+2){
	  swapped_intcpy (&temp,&(source[j*2]),int_len);
	  memcpy (&(buffer[j*2]), (void *)&temp,int_len);
	}
	i = lk.head_pos;
	break;


      /* 2-byte swap */
      case 0x4:   /* short          */
      case 0x5:   /* unsigned short */
      case 0x30:  
      case 0x34:
      case 0x35:
	for(j = i; j < lk.head_pos; j=j+1){
	  swapped_shortcpy (&temp2,&(source[j*2]),short_len);
	  memcpy (&(buffer[j*2]),(void *)&temp2,short_len);
	}
	i = lk.head_pos;	
	break;


      /* 8-byte swap */
      case 0x8:  /* double        */
      case 0x9:  /* signed long   */
      case 0xA:  /* unsigned long */
	for(j = i; j < lk.head_pos; j=j+4){
	  swapped_longcpy ((double*)&temp8,&(source[j*2]),long_len);
	  memcpy (&(buffer[j*2]), (void *)&temp8,long_len);
	}
	i = lk.head_pos;		
	break;


      default:
	fprintf(stderr,"Unknown datatype 0x%x\n",current_type);
	break;
      }
    }
  }

  evStack_free (head);

}


/*---------------------------------------------------------------------------*/
