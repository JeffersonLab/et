/*
 *  xml_util.c
 *
 *   Utility for converting binary evio data to xml
 *
 *   NOTE:  does NOT handle VAX float or double, packets, or repeating structures
 *
 *
 *  Definitions of terms used in output XML:
 *
 *   name         size in bytes
 *   ----         -------------
 *   byte              1
 *   int16             2
 *   int32             4
 *   float32           4 IEEE format
 *   int64             8
 *   float64           8 IEEE format
 *
 *  The prefix 'u' means unsigned
 *
 *
 *   Author:  Elliott Wolin, JLab, 6-sep-2001
 *   Revised: Elliott Wolin, 5-apr-2004 convert to library, print to string
*/

/* still to do
 * -----------
 *  check string length, use snprintf
 *
*/


/* container types used locally */
enum {
  BANK = 0,
  SEGMENT,
  TAGSEGMENT,
};



/* for posix */
#define _POSIX_SOURCE_ 1
#define __EXTENSIONS__


/*  misc macros, etc. */
#define MAXXMLBUF  10000
#define MAXDICT    5000
#define MAXDEPTH   512
#define min(a, b)  ( ( (a) > (b) ) ? (b) : (a) )


/* include files */
#include <evio.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <expat.h>


/* xml tag dictionary */
static XML_Parser dictParser;
static char xmlbuf[MAXXMLBUF];
typedef struct {
  int ntag;
  int *tag;
  char *name;
  int hasNum;
  int num;
} dict_entry;
static dict_entry dict[MAXDICT];
static int ndict          = 0;


/* fragment info */
char *fragment_name[] = {"bank","segment","tagsegment"};
int fragment_offset[] = {2,1,1};


/* formatting info */
static int xtod         = 0;
static int n8           = 8;
static int n16          = 8;
static int n32          = 5;
static int n64          = 2;
static int w8           = 4;
static int w16          = 9;
static int w32          = 14;
static int w64          = 28;


/*  misc variables */
static int nbuf;
static char *event_tag    = (char*)"event";
static char *bank2_tag    = (char*)"bank";
static int max_depth      = -1;
static int depth          = 0;
static int tagstack[MAXDEPTH];
static int numstack[MAXDEPTH];
static int no_typename    = 0;
static int verbose        = 0;
static int nindent        = 0;
static int indent_size    = 3;
static char *xml;
static int xmllen;


/* prototypes */
static void create_dictionary(char *dictfilename);
static void startDictElement(void *userData, const char *name, const char **atts);
static void dump_fragment(unsigned int *buf, int fragment_type);
static void dump_data(unsigned int *data, int type, int length, int noexpand);
static int get_ndata(int type, int nwords);
static const char *get_tagname();
static void indent(void);
static const char *get_char_att(const char **atts, const int natt, const char *tag);

/* user supplied fragment select function via set_user_frag_select_func(int (*f) (int tag)) */
static int (*USER_FRAG_SELECT_FUNC) (int tag) = NULL;


/*--------------------------------------------------------------------------*/


void evio_xmldump_init(char *dictfilename) {

  create_dictionary(dictfilename);

  return;
}


/*---------------------------------------------------------------- */


static void create_dictionary(char *dictfilename) {

  FILE *dictfile;
  int len;


  /* is dictionary file specified */
  if(dictfilename==NULL)return;


  /* read add'l tags from xml file */
  if((dictfile=fopen(dictfilename,"r"))==NULL) {
    printf("\n?unable to open dictionary file %s\n\n",dictfilename);
    exit(EXIT_FAILURE);
  }


  /* create parser and set callbacks */
  dictParser=XML_ParserCreate(NULL);
  XML_SetElementHandler(dictParser,startDictElement,NULL);


  /* read and parse dictionary file */
  do {
    len=fread(xmlbuf,1,MAXXMLBUF,dictfile);
    XML_Parse(dictParser,xmlbuf,len,len<MAXXMLBUF);
  } while (len==MAXXMLBUF);
  

  fclose(dictfile);
  return;
}


/*---------------------------------------------------------------- */


static void startDictElement(void *userData, const char *name, const char **atts) {

  int natt=XML_GetSpecifiedAttributeCount(dictParser);
  char *tagtext,*p;
  const char *pNum;
  int i,npt;
  int *ip;


  if(strcasecmp(name,"xmldumpDictEntry")!=0)return;

  ndict++;
  if(ndict>MAXDICT) {
    printf("\n?dictionary too large\n\n");
    exit(EXIT_FAILURE);
  }
  

  /* allocate array for sub-tags */
  tagtext=strdup(get_char_att(atts,natt,"tag"));
  npt=0; for(i=0; i<strlen(tagtext); i++) if(tagtext[i]=='.')npt++;
  ip=(int*)malloc((npt+1)*sizeof(int));
  

  /* fill array with sub-tags */
  i=0; 
  p=tagtext-1;
  do {
    ip[i++]=atoi(++p);
    p=strchr(p,'.');
  } while (p!=NULL);


  /* store dictionary info */
  dict[ndict-1].ntag = npt+1;
  dict[ndict-1].tag  = ip;
  dict[ndict-1].name = strdup(get_char_att(atts,natt,"name"));
  pNum=get_char_att(atts,natt,"num");  
  if(pNum==NULL) {
    dict[ndict-1].hasNum=0;
  } else {
    dict[ndict-1].hasNum=1;
    dict[ndict-1].num=atoi(pNum);
  }  

  return;
}


/*---------------------------------------------------------------- */


void evio_xmldump(unsigned int *buf, int bufnum, char *string, int len) {

  nbuf=bufnum;
  xml=string;
  xmllen=len;

  xml+=sprintf(xml,"\n\n<!-- ===================== Buffer %d contains %d words (%d bytes) "
	       "===================== -->\n\n",nbuf,buf[0]+1,4*(buf[0]+1));
  
  depth=0;
  dump_fragment(buf,BANK);


  return;
}


/*---------------------------------------------------------------- */


void set_user_frag_select_func( int (*f) (int tag) ) {
  USER_FRAG_SELECT_FUNC = f;
  return;
}


/*---------------------------------------------------------------- */


static void dump_fragment(unsigned int *buf, int fragment_type) {

  int length,type,is_a_container,noexpand;
  unsigned short tag;
  unsigned char num;

  char *myname;


  /* get type-dependent info */
  switch(fragment_type) {
  case 0:
    length  	= buf[0]+1;
    tag     	= buf[1]>>16;
    type    	= (buf[1]>>8)&0xff;
    num     	= buf[1]&0xff;
    break;

  case 1:
    length  	= (buf[0]&0xffff)+1;
    type    	= (buf[0]>>16)&0xff;
    tag     	= (buf[0]>>24)&0xff;
    num     	= 0;
    break;
    
  case 2:
    length  	= (buf[0]&0xffff)+1;
    type    	= (buf[0]>>16)&0xf;
    tag     	= (buf[0]>>20)&0xfff;
    num     	= 0;
    break;

  default:
    printf("?illegal fragment_type in dump_fragment: %d",fragment_type);
    exit(EXIT_FAILURE);
    break;
  }


  /* user selection on fragment tags (not on event tag) */
  if( (depth>0) && (USER_FRAG_SELECT_FUNC != NULL) && (USER_FRAG_SELECT_FUNC(tag)==0) )return;


  /* update depth, tagstack, etc. */
  depth++;
  if(depth>(sizeof(tagstack)/sizeof(int))) {
    printf("?error...tagstack overflow\n");
    exit(EXIT_FAILURE);
  }
  tagstack[depth-1]=tag;
  numstack[depth-1]=num;
  is_a_container=is_container(type);
  myname=(char*)get_tagname();
  noexpand=is_a_container&&(max_depth>=0)&&(depth>max_depth);


  /* print header word (as comment) if requested */
  if(verbose!=0) {
    xml+=sprintf(xml,"\n"); indent();
    if(fragment_type==BANK) {
      xml+=sprintf(xml,"<!-- header words: %d, %#x -->\n",buf[0],buf[1]);
    } else {
      xml+=sprintf(xml,"<!-- header word: %#x -->\n",buf[0]);
    }
  }


  /* fragment opening xml tag */
  indent();
  if((fragment_type==BANK)&&(depth==1)) {
    xml+=sprintf(xml,"<%s format=\"evio\" count=\"%d\"",event_tag,nbuf);
    xml+=sprintf(xml," content=\"%s\"",get_typename(type));
  } else if(myname!=NULL) {
    xml+=sprintf(xml,"<%s",myname);
    xml+=sprintf(xml," content=\"%s\"",get_typename(type));
  } else if((fragment_type==BANK)&&(depth==2)) {
    xml+=sprintf(xml,"<%s",bank2_tag);
    xml+=sprintf(xml," content=\"%s\"",get_typename(type));
  } else if(is_a_container||no_typename) {
    xml+=sprintf(xml,"<%s",fragment_name[fragment_type]);
    xml+=sprintf(xml," content=\"%s\"",get_typename(type));
  } else {
    xml+=sprintf(xml,"<%s",get_typename(type));
  }
  xml+=sprintf(xml," data_type=\"0x%x\"",type);
  xml+=sprintf(xml," tag=\"%d\"",tag);
  if(fragment_type==BANK)xml+=sprintf(xml," num=\"%d\"",(int)num);
  if(verbose!=0) {
    xml+=sprintf(xml," length=\"%d\" ndata=\"%d\"",length,
	   get_ndata(type,length-fragment_offset[fragment_type]));
  }
  if(noexpand)xml+=sprintf(xml," opt=\"noexpand\"");
  xml+=sprintf(xml,">\n");
  

  /* fragment data */
  dump_data(&buf[fragment_offset[fragment_type]],type,
	    length-fragment_offset[fragment_type],noexpand);


  /* fragment close tag */
  indent();
  if((fragment_type==BANK)&&(depth==1)) {
    xml+=sprintf(xml,"</%s>\n\n",event_tag);
    xml+=sprintf(xml,"<!-- end buffer %d -->\n\n",nbuf);
  } else if(myname!=NULL) {
    xml+=sprintf(xml,"</%s>\n",myname);
  } else if((fragment_type==BANK)&&(depth==2)) {
    xml+=sprintf(xml,"</%s>\n",bank2_tag);
  } else if(is_a_container||no_typename) {
    xml+=sprintf(xml,"</%s>\n",fragment_name[fragment_type]);
  } else {
    xml+=sprintf(xml,"</%s>\n",get_typename(type));
  }


  /* decrement stack depth */
  depth--;


  return;
}


/*---------------------------------------------------------------- */


static void dump_data(unsigned int *data, int type, int length, int noexpand) {

  int i,j,len;
  int p=0;
  short *s;
  char *c;
  unsigned char *uc;
  char format[132];


  nindent+=indent_size;


  /* dump data in if no expansion, even if this is a container */
  if(noexpand) {
    sprintf(format,"%%#%d%s ",w32,(xtod==0)?"x":"d");
    for(i=0; i<length; i+=n32) {
      indent();
      for(j=i; j<min((i+n32),length); j++) {
	xml+=sprintf(xml,format,data[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    nindent-=indent_size;
    return;
  }


  /* dump the data or call dump_fragment */
  switch (type) {

  case 0x0:
  case 0x1:
    sprintf(format,"%%#%d%s ",w32,(xtod==0)?"x":"d");
    for(i=0; i<length; i+=n32) {
      indent();
      for(j=i; j<min((i+n32),length); j++) {
	xml+=sprintf(xml,format,data[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0x2:
    sprintf(format,"%%#%df ",w32);
    for(i=0; i<length; i+=n32) {
      indent();
      for(j=i; j<min(i+n32,length); j++) {
	xml+=sprintf(xml,format,*(float*)&data[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0x3:
    c=(char*)&data[0];
    len=length*4;
    sprintf(format,"<![CDATA[\n%%.%ds\n]]>\n",len);  /* handle unterminated strings */
    xml+=sprintf(xml,format,c);
    break;

  case 0x4:
    sprintf(format,"%%%dhd ",w16);
    s=(short*)&data[0];
    for(i=0; i<2*length; i+=n16) {
      indent();
      for(j=i; j<min(i+n16,2*length); j++) {
	xml+=sprintf(xml,format,s[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0x5:
    sprintf(format,"%%#%d%s ",w16,(xtod==0)?"hx":"d");
    s=(short*)&data[0];
    for(i=0; i<2*length; i+=n16) {
      indent();
      for(j=i; j<min(i+n16,2*length); j++) {
	xml+=sprintf(xml,format,s[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0x6:
    sprintf(format,"   %%%dd ",w8);
    c=(char*)&data[0];
    for(i=0; i<4*length; i+=n8) {
      indent();
      for(j=i; j<min(i+n8,4*length); j++) {
	xml+=sprintf(xml,format,c[j]);
      }
      xml+=sprintf(xml,"\n");
     }
    break;

  case 0x7:
    sprintf(format,"   %%#%d%s ",w8,(xtod==0)?"x":"d");
    uc=(unsigned char*)&data[0];
    for(i=0; i<4*length; i+=n8) {
      indent();
      for(j=i; j<min(i+n8,4*length); j++) {
	xml+=sprintf(xml,format,uc[j]);
      }
      xml+=sprintf(xml,"\n");
     }
    break;

  case 0x8:
    sprintf(format,"%%#%d.20e ",w64);
    for(i=0; i<length/2; i+=n64) {
      indent();
      for(j=i; j<min(i+n64,length/2); j++) {
	xml+=sprintf(xml,format,*(double*)&data[2*j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0x9:
    sprintf(format,"%%%dlld ",w64);
    for(i=0; i<length/2; i+=n64) {
      indent();
      for(j=i; j<min(i+n64,length/2); j++) {
	xml+=sprintf(xml,format,*(long long*)&data[2*j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0xa:
    sprintf(format,"%%#%dll%s ",w64,(xtod==0)?"x":"d");
    for(i=0; i<length/2; i+=n64) {
      indent();
      for(j=i; j<min(i+n64,length/2); j++) {
	xml+=sprintf(xml,format,*(long long*)&data[2*j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0xb:
    sprintf(format,"%%#%dd ",w32);
    for(i=0; i<length; i+=n32) {
      indent();
      for(j=i; j<min((i+n32),length); j++) {
	xml+=sprintf(xml,format,data[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;

  case 0xe:
  case 0x10:
    while(p<length) {
      dump_fragment(&data[p],BANK);
      p+=data[p]+1;
    }
    break;

  case 0xd:
  case 0x20:
    while(p<length) {
      dump_fragment(&data[p],SEGMENT);
      p+=(data[p]&0xffff)+1;
    }
    break;

  case 0xc:
  case 0x40:
    while(p<length) {
      dump_fragment(&data[p],TAGSEGMENT);
      p+=(data[p]&0xffff)+1;
    }
    break;

  default:
    sprintf(format,"%%#%d%s ",w32,(xtod==0)?"x":"d");
    for(i=0; i<length; i+=n32) {
      indent();
      for(j=i; j<min(i+n32,length); j++) {
	xml+=sprintf(xml,format,(unsigned int)data[j]);
      }
      xml+=sprintf(xml,"\n");
    }
    break;
  }


  /* decrease indent */
  nindent-=indent_size;
  return;
}


/*---------------------------------------------------------------- */


static int get_ndata(int type, int length) {

  switch (type) {

  case 0x0:
  case 0x1:
  case 0x2:
    return(length);
    break;

  case 0x3:
    return(1);
    break;

  case 0x4:
  case 0x5:
    return(2*length);
    break;

  case 0x6:
  case 0x7:
    return(4*length);
    break;

  case 0x8:
  case 0x9:
  case 0xa:
    return(length/2);
    break;

  case 0xb:
  case 0xc:
  case 0xd:
  case 0xe:
  case 0xf:
  case 0x10:
  case 0x20:
  case 0x40:
  default:
    return(length);
    break;
  }
}


/*---------------------------------------------------------------- */


static void indent() {

  int i;

  for(i=0; i<nindent; i++)xml+=sprintf(xml," ");
  return;
}


/*---------------------------------------------------------------- */


static const char *get_char_att(const char **atts, const int natt, const char *name) {

  int i;

  for(i=0; i<natt; i+=2) {
    if(strcasecmp(atts[i],name)==0)return((const char*)atts[i+1]);
  }

  return(NULL);
}


/*---------------------------------------------------------------- */


static const char *get_tagname() {

  int i,j,ntd,nt,hasNum,num;
  int match;


  /* search dictionary for match with current tag, and num if specified */
  for(i=0; i<ndict; i++) {
    
    ntd=dict[i].ntag;
    nt=min(ntd,depth);
    hasNum=dict[i].hasNum;    
    num=dict[i].num;

    match=1;
    for(j=0; j<nt; j++) {
      if(dict[i].tag[ntd-j-1]!=tagstack[depth-j-1]) {
        match=0;
        break;
      }
    }
    if(match&&((hasNum==0)||(num==numstack[depth-1])))return(dict[i].name);
  }

  return(NULL);
}


/*---------------------------------------------------------------- */


void evio_xmldump_done(char *string, int len) {

  sprintf(string," ");
  return;
}


/*---------------------------------------------------------------- */
/*--- Set functions ---------------------------------------------- */
/*---------------------------------------------------------------- */


int set_event_tag(char *tag) {

  event_tag=tag;
  return(0);
}


/*---------------------------------------------------------------- */


int set_bank2_tag(char *tag) {

  bank2_tag=tag;
  return(0);

}


/*---------------------------------------------------------------- */


int set_n8(int val) {

  n8=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_n16(int val) {

  n16=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_n32(int val) {

  n32=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_n64(int val) {

  n64=val;
  return(0);

}


/*---------------------------------------------------------------- */

int set_w8(int val) {

  w8=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_w16(int val) {

  w16=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_w32(int val) {

  w32=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_w64(int val) {

  w64=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_xtod(int val) {

  xtod=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_indent_size(int val) {

  indent_size=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_max_depth(int val) {

  max_depth=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_no_typename(int val) {

  no_typename=val;
  return(0);

}


/*---------------------------------------------------------------- */


int set_verbose(int val) {

  verbose=val;
  return(0);

}


/*---------------------------------------------------------------- */


