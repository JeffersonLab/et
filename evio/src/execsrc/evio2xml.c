/*
 *  evio2xml.c
 *
 *   Converts binary evio data to xml, full support for tagsegments and 64-bit
 *
 *   NOTE:  does NOT handle VAX float or double, packets, or repeating structures
 *
 *   Use -gz to write output file using gzip
 *
 *   Author: Elliott Wolin, JLab, 6-sep-2001
*/



/* for posix */
#define _POSIX_SOURCE_ 1
#define __EXTENSIONS__


/*  misc macros, etc. */
#define MAXEVIOBUF   100000
#define MAXXMLSTRING 500000


/* include files */
#include <stdio.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <evio.h>


/*  misc variables */
static char *filename;
static char *dictfilename = NULL;
static char *outfilename  = NULL;
static int gzip           = 0;
static char *main_tag     = (char*)"evio-data";
static int nevent         = 0;
static int skip_event     = 0;
static int max_event      = 0;
static int nevok          = 0;
static int evok[100];
static int nnoev          = 0;
static int noev[100];
static int nfragok        = 0;
static int fragok[100];
static int nnofrag        = 0;
static int nofrag[100];
static int pause          = 0;
static int debug          = 0;
static int done           = 0;
static char xml[MAXXMLSTRING];


/* prototypes */
void decode_command_line(int argc, char **argv);
void evio_xmldump_init(char *dictfilename);
void evio_xmldump(unsigned int *buf, int evnum, char *string, int len);
void evio_xmldump_done(char *string, int len);
void writeit(FILE *f, char *s, int len);
int user_event_select(unsigned int *buf);
int user_frag_select(int tag);
FILE *gzopen(char*,char*);
void gzclose(FILE*);
void gzwrite(FILE*,char*,int);
int set_event_tag(char *tag);
int set_bank2_tag(char *tag);
int set_n8(int val);
int set_n16(int val);
int set_n32(int val);
int set_n64(int val);
int set_w8(int val);
int set_w16(int val);
int set_w32(int val);
int set_w64(int val);
int set_xtod(int val);
int set_indent_size(int val);
int set_max_depth(int val);
int set_no_typename(int val);
int set_verbose(int val);


/*--------------------------------------------------------------------------*/


int main (int argc, char **argv) {

  int handle,status;
  unsigned int buf[MAXEVIOBUF];
  FILE *out = NULL;
  char s[256];
  

  /* decode command line */
  decode_command_line(argc,argv);


  /* open evio input file */
  if((status=evOpen(filename,"r",&handle))!=0) {
    printf("\n ?Unable to open file %s, status=%d\n\n",filename,status);
    exit(EXIT_FAILURE);
  }


  /* open output file, gzip format if requested */
  if(outfilename!=NULL) {
    if(gzip==0) {
      out=fopen(outfilename,"w");
    } else {
      out=(FILE*)gzopen(outfilename,"wb");
    }  
  }
  

  /* init xmldump */
  set_user_frag_select_func(user_frag_select);
  evio_xmldump_init(dictfilename);
  sprintf(s,"<!-- xml boilerplate needs to go here -->\n\n");
  writeit(out,s,strlen(s));
  sprintf(s,"<%s>\n\n",main_tag);
  writeit(out,s,strlen(s));


  /* loop over events, perhaps skip some, dump up to max_event events */
  nevent=0;
  while ((status=evRead(handle,buf,MAXEVIOBUF))==0) {
    nevent++;
    if(skip_event>=nevent)continue;
    if(user_event_select(buf)==0)continue;
    evio_xmldump(buf,nevent,xml,MAXXMLSTRING);
    writeit(out,xml,strlen(xml));


    if(pause!=0) {
      printf("\n\nHit return to continue, q to quit: ");
      fgets(s,sizeof(s),stdin);
      if(tolower(s[strspn(s," \t")])=='q')done=1;
    }

    if((done!=0)||((nevent>=max_event+skip_event)&&(max_event!=0)))break;
  }



  /* done */
  evio_xmldump_done(xml,MAXXMLSTRING);
  writeit(out,xml,strlen(xml));
  sprintf(s,"</%s>\n\n",main_tag);
  writeit(out,s,strlen(s));
  evClose(handle);
  if((out!=NULL)&&(gzip!=0))gzclose(out);
  exit(EXIT_SUCCESS);
}


/*---------------------------------------------------------------- */


void writeit(FILE *f, char *s, int len) {

  if(f==NULL) {
    printf("%s",s);
  } else if (gzip==0) {
    fprintf(f,s,len);
  } else {
    gzwrite(f,s,len);
  }

}


/*---------------------------------------------------------------- */


int user_event_select(unsigned int *buf) {

  int i;
  int event_tag = buf[1]>>16;


  if((nevok<=0)&&(nnoev<=0)) {
    return(1);

  } else if(nevok>0) {
    for(i=0; i<nevok; i++) if(event_tag==evok[i])return(1);
    return(0);
    
  } else {
    for(i=0; i<nnoev; i++) if(event_tag==noev[i])return(0);
    return(1);
  }

}


/*---------------------------------------------------------------- */


int user_frag_select(int tag) {

  int i;

  if((nfragok<=0)&&(nnofrag<=0)) {
    return(1);

  } else if(nfragok>0) {
    for(i=0; i<nfragok; i++) if(tag==fragok[i])return(1);
    return(0);
    
  } else {
    for(i=0; i<nnofrag; i++) if(tag==nofrag[i])return(0);
    return(1);
  }

}


/*---------------------------------------------------------------- */


void decode_command_line(int argc, char**argv) {
  
  const char *help = 
    "\nusage:\n\n  evio2xml [-max max_event] [-pause] [-skip skip_event] [-dict dictfilename]\n"
    "           [-ev evtag] [-noev evtag] [-frag frag] [-nofrag frag] [-max_depth max_depth]\n"
    "           [-n8 n8] [-n16 n16] [-n32 n32] [-n64 n64]\n"
    "           [-w8 w8] [-w16 w16] [-w32 w32] [-w64 w64]\n"
    "           [-verbose] [-xtod] [-m main_tag] [-e event_tag]\n"
    "           [-indent indent_size] [-no_typename] [-debug]\n"
    "           [-out outfilenema] [-gz] filename\n";
  int i;
    
    
  if(argc<2) {
    printf("%s\n",help);
    exit(EXIT_SUCCESS);
  } 


  /* loop over arguments */
  i=1;
  while (i<argc) {
    if (strncasecmp(argv[i],"-h",2)==0) {
      printf("%s\n",help);
      exit(EXIT_SUCCESS);

    } else if (strncasecmp(argv[i],"-pause",6)==0) {
      pause=1;
      i=i+1;

    } else if (strncasecmp(argv[i],"-out",4)==0) {
      outfilename=strdup(argv[i+1]);
      i=i+2;

    } else if (strncasecmp(argv[i],"-debug",6)==0) {
      debug=1;
      i=i+1;

    } else if (strncasecmp(argv[i],"-gz",3)==0) {
      gzip=1;
      i=i+1;

    } else if (strncasecmp(argv[i],"-verbose",8)==0) {
      set_verbose(1);
      i=i+1;

    } else if (strncasecmp(argv[i],"-no_typename",12)==0) {
      set_no_typename(1);
      i=i+1;

    } else if (strncasecmp(argv[i],"-max_depth",10)==0) {
      set_max_depth(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-indent",7)==0) {
      set_indent_size(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-max",4)==0) {
      max_event=atoi(argv[i+1]);
      i=i+2;

    } else if (strncasecmp(argv[i],"-skip",5)==0) {
      skip_event=atoi(argv[i+1]);
      i=i+2;

    } else if (strncasecmp(argv[i],"-dict",5)==0) {
      dictfilename=strdup(argv[i+1]);
      i=i+2;

    } else if (strncasecmp(argv[i],"-xtod",5)==0) {
      set_xtod(1);
      i=i+1;

    } else if (strncasecmp(argv[i],"-ev",3)==0) {
      if(nevok<(sizeof(evok)/sizeof(int))) {
	evok[nevok++]=atoi(argv[i+1]);
	i=i+2;
      } else {
	printf("?too many ev flags: %s\n",argv[i+1]);
      }

    } else if (strncasecmp(argv[i],"-noev",5)==0) {
      if(nnoev<(sizeof(noev)/sizeof(int))) {
	noev[nnoev++]=atoi(argv[i+1]);
	i=i+2;
      } else {
	printf("?too many noev flags: %s\n",argv[i+1]);
      }

    } else if (strncasecmp(argv[i],"-frag",5)==0) {
      if(nfragok<(sizeof(fragok)/sizeof(int))) {
	fragok[nfragok++]=atoi(argv[i+1]);
	i=i+2;
      } else {
	printf("?too many frag flags: %s\n",argv[i+1]);
      }

    } else if (strncasecmp(argv[i],"-nofrag",7)==0) {
      if(nnofrag<(sizeof(nofrag)/sizeof(int))) {
	nofrag[nnofrag++]=atoi(argv[i+1]);
	i=i+2;
      } else {
	printf("?too many nofrag flags: %s\n",argv[i+1]);
      }

    } else if (strncasecmp(argv[i],"-n8",3)==0) {
      set_n8(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-n16",4)==0) {
      set_n16(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-n32",4)==0) {
      set_n32(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-n64",4)==0) {
      set_n64(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-w8",3)==0) {
      set_w8(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-w16",4)==0) {
      set_w16(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-w32",4)==0) {
      set_w32(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-w64",4)==0) {
      set_w64(atoi(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-m",2)==0) {
      main_tag=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-e",2)==0) {
      set_event_tag(strdup(argv[i+1]));
      i=i+2;

    } else if (strncasecmp(argv[i],"-",1)==0) {
      printf("\n  ?unknown command line arg: %s\n\n",argv[i]);
      exit(EXIT_FAILURE);

    } else {
      break;
    }
  }
  
  /* last arg better be filename */
  filename=argv[argc-1];

  return;
}


/*---------------------------------------------------------------- */
