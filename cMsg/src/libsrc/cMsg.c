/*----------------------------------------------------------------------------*
 *
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    E.Wolin, 15-Jul-2004, Jefferson Lab                                     *
 *                                                                            *
 *    Authors: Elliott Wolin                                                  *
 *             wolin@jlab.org                    Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-7365             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *             Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 * Description:
 *
 *  Implements cMsg client api and dispatches to multiple domains
 *  Includes all message functions
 *
 *

 *----------------------------------------------------------------------------*/

/**
 * @mainpage
 * 
 * <H2>
 * cMsg (pronounced "see message") is a software package conceived and written
 * at Jefferson Lab by Elliott Wolin, Carl Timmer, and Vardan Gyurjyan. At one
 * level this package is an API to a message-passing system (or domain as we
 * refer to it). Users can create messages and have them handled knowing only
 * a UDL (Uniform Domain Locator) to specify a particular server (domain) to use.
 * Thus the API acts essentially as a multiplexor, diverting messages to the
 * desired domain.
 * </H2><p>
 * <H2>
 * At another level, cMsg is an implementation of a domain. Not only can cMsg
 * pass off messages to Channel Access, SmartSockets, or a number of other
 * domains, it can handle messages itself. It was designed to be extremely
 * flexible and so adding another domain is relatively easy.
 * </H2><p>
 * <H2>
 * There is a User's Guide, API documentation in the form of web pages generated
 * by javadoc and doxygen, and a Developer's Guide. Try it and let us know what
 * you think about it. If you find any bugs, we have a bug-report web page at
 * http://xdaq.jlab.org/CODA/portal/html/ .
 * </H2><p>
 */
  
 
/**
 * @file
 * This file contains the entire cMsg user API.
 *
 * <b>Introduction</b>
 *
 * The user API acts as a multiplexor. Depending on the particular UDL used to
 * connect to a specific cMsg server, the API will direct the user's library
 * calls to the appropriate cMsg implementation.
 */  
 
/* system includes */
#ifdef VXWORKS
#include <vxWorks.h>
#include <taskLib.h>
#include <symLib.h>
#include <symbol.h>
#include <sysSymTbl.h>
#else
#include <strings.h>
#include <dlfcn.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <ctype.h>

/* package includes */
#include "cMsgNetwork.h"
#include "cMsgPrivate.h"
#include "cMsg.h"
#include "regex.h"

/**
 * Because MAXHOSTNAMELEN is defined to be 256 on Solaris and 64 on Linux,
 * use CMSG_MAXHOSTNAMELEN as a substitute that is uniform across all platforms.
 */
#define CMSG_MAXHOSTNAMELEN 256


/* local variables */
/** Is the one-time initialization done? */
static int oneTimeInitialized = 0;
/** Pthread mutex serializing calls to cMsgConnect() and cMsgDisconnect(). */
static pthread_mutex_t connectMutex = PTHREAD_MUTEX_INITIALIZER;
/** Store references to different domains and their cMsg implementations. */
static domainTypeInfo dTypeInfo[CMSG_MAX_DOMAIN_TYPES];
/** Excluded characters from subject, type, and description strings. */
static const char *excludedChars = "`\'\"";


/** Global debug level. */
int cMsgDebug = CMSG_DEBUG_NONE;

/** For domain implementations. */
extern domainTypeInfo cmsgDomainTypeInfo;
extern domainTypeInfo fileDomainTypeInfo;
extern domainTypeInfo   rcDomainTypeInfo;


/* local prototypes */
static int   readConfigFile(char *fileName, char **newUDL);
static int   checkString(const char *s);
static int   registerPermanentDomains();
static int   registerDynamicDomains(char *domainType);
static void  domainInit(cMsgDomain *domain);
static void  domainFree(cMsgDomain *domain);
static int   parseUDL(const char *UDL, char **domainType, char **UDLremainder);
static void  connectMutexLock(void);
static void  connectMutexUnlock(void);
static void  initMessage(cMsgMessage_t *msg);
static int   freeMessage(void *vmsg);



#ifdef VXWORKS

/** Implementation of strdup for vxWorks. */
char *strdup(const char *s1) {
    char *s;    
    if (s1 == NULL) return NULL;    
    if ((s = (char *) malloc(strlen(s1)+1)) == NULL) return NULL;    
    return strcpy(s, s1);
}

/** Implementation of strcasecmp for vxWorks. */
int strcasecmp(const char *s1, const char *s2) {
  int i, len1, len2;
  
  /* handle NULL's */
  if (s1 == NULL && s2 == NULL) {
    return 0;
  }
  else if (s1 == NULL) {
    return -1;  
  }
  else if (s2 == NULL) {
    return 1;  
  }
  
  len1 = strlen(s1);
  len2 = strlen(s2);
  
  /* handle different lengths */
  if (len1 < len2) {
    for (i=0; i<len1; i++) {
      if (toupper((int) s1[i]) < toupper((int) s2[i])) {
        return -1;
      }
      else if (toupper((int) s1[i]) > toupper((int) s2[i])) {
         return 1;   
      }
    }
    return -1;
  }
  else if (len1 > len2) {
    for (i=0; i<len2; i++) {
      if (toupper((int) s1[i]) < toupper((int) s2[i])) {
        return -1;
      }
      else if (toupper((int) s1[i]) > toupper((int) s2[i])) {
         return 1;   
      }
    }
    return 1;  
  }
  
  /* handle same lengths */
  for (i=0; i<len1; i++) {
    if (toupper((int) s1[i]) < toupper((int) s2[i])) {
      return -1;
    }
    else if (toupper((int) s1[i]) > toupper((int) s2[i])) {
       return 1;   
    }
  }
  
  return 0;
}

#endif

/*-------------------------------------------------------------------*/

/**
 * Routine to read a configuration file and return the cMsg UDL stored there.
 * @param fileName name of file to be read
 * @param newUDL pointer to char pointer that gets filled with the UDL
 *               contained in config file, NULL if none
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if not successful reading file
 * @returns CMSG_BAD_ARGUMENT if one of the arguments is bad
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 */
static int readConfigFile(char *fileName, char **newUDL) {
#define MAX_STR_LEN 2000
    int i,j, gotUDL=0;
    FILE *fp;
    char str[MAX_STR_LEN], *pchar;
    
    if (fileName == NULL) return(CMSG_BAD_ARGUMENT);
    
    if ( (fp = fopen(fileName,"r")) == NULL) {
        return(CMSG_ERROR);
    }
    
    /* skip over lines with no UDL (no ://) */
    while (fgets(str, MAX_STR_LEN, fp) != NULL) {
    
/*printf("readConfigFile: string = %s\n", str);*/
      /* Remove white space at beginning and end. */
      i = 0;
      pchar = str;
      while (isspace(str[i])) {
        pchar++;
        i++; 
      }

      for (j=0; j<MAX_STR_LEN-i; j++) {
        if (isspace(pchar[j])) {
            pchar[j] = '\0';
            break;
        }
      }
      
      /* if first char is #, treat as comment */
      if (pchar[0] == '#') {
/*printf("SKIP over comment\n");*/
        continue;
      }
      /* length of 5 is shortest possible UDL (a://b) */
      else if (strlen(str) < 5) {
        continue;
      }
      else {
/*printf("read configFile, UDL = %s\n", pchar);*/
        /* this string is not a UDL so continue on */
        if (strstr(pchar, "://") == NULL) {
            continue;
        }
        if (newUDL != NULL) *newUDL = (char *) (strdup(pchar));
        gotUDL = 1;
        break;
      }
    }
    
    fclose(fp);

    if (!gotUDL) {
      return(CMSG_ERROR);
    }
    return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/**
 * This structure contains the components of a given UDL broken down
 * into its consituent parts.
 */
typedef struct parsedUDL_t {
  char *udl;                /**< whole UDL for name server. */
  char *domain;             /**< domain name. */
  char *remainder;          /**< domain specific part of the UDL. */
  struct parsedUDL_t *next; /**< next element in linked list. */
} parsedUDL;


/**
 * Routine to free a single element of structure parsedUDL.
 * @param p pointer to element to be freed
 */
static void freeElement(parsedUDL *p) {
    if (p->udl       != NULL) {free(p->udl);       p->udl = NULL;}
    if (p->domain    != NULL) {free(p->domain);    p->domain = NULL;}
    if (p->remainder != NULL) {free(p->remainder); p->remainder = NULL;}
}

/**
 * Routine to free a linked list of elements of structure parsedUDL.
 * @param p pointer to head of list to be freed
 */
static void freeList(parsedUDL *p) {
    parsedUDL *pPrev=NULL;
    
    while (p != NULL) {
        freeElement(p);
        pPrev = p;
        p = p->next;
        free(pPrev);
    }
}



/**
 * Routine to split a string of semicolon separated UDLs into a linked list
 * of parsedUDL structures. Each structure contains easily accessible info
 * about the UDL it represents.
 *
 * @param myUDL UDL to be split
 * @param list pointer to parsedUDL pointer that gets filled with the head of
 *             the linked list
 * @param firstDomain pointer to char pointer which gets filled with the domain
 *                    of the first element in the list
 * @param count pointer to int which gets filled with number of items in list
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_FORMAT if one of the UDLs is not in the correct format
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 */
static int splitUDL(const char *myUDL, parsedUDL** list, char **firstDomain, int *count) {

  char *p, *udl, *domain, *remainder;
  int udlCount=0, err, gotFirst=0; 
  parsedUDL *pUDL, *prevUDL=NULL, *firstUDL=NULL;

  /*
   * The UDL may be a semicolon separated list of UDLs.
   * Separate them and return a linked list of them.
   */
  udl = (char *)strdup(myUDL);
  if (udl == NULL) return(CMSG_OUT_OF_MEMORY);       
  p = strtok(udl, ";");
  
  while (p != NULL) {
    /* Parse the UDL (Uniform Domain Locator) */
    if ( (err = parseUDL(p, &domain, &remainder)) != CMSG_OK ) {
      /* There's been a parsing error */
      free(udl);
      return(CMSG_BAD_FORMAT);
    }
    
    pUDL = (parsedUDL *)calloc(1, sizeof(parsedUDL));
    pUDL->udl = strdup(p);
    pUDL->domain = domain;
    pUDL->remainder = remainder;
    
    /* linked list */
    if (prevUDL != NULL) prevUDL->next = pUDL;
    prevUDL = pUDL;
    
    /* bookkeeping */
    if (!gotFirst) {
        firstUDL = pUDL;
        if (firstDomain != NULL) {
            *firstDomain = (char *)strdup(domain);
        }
        gotFirst = 1;
    }
    udlCount++;
    p = strtok(NULL, ";");
  }
  
  free(udl);
  
  if (count != NULL) *count = udlCount;
  
  if (list != NULL) {
    *list  = firstUDL;
  }
  else if (firstUDL != NULL) {
    freeList(firstUDL);
  }

  return(CMSG_OK);
}


/**
 * Routine to check a linked list of parsedUDL structures to see if all the
 * elements' domains are the same.
 *
 * @param domain domain to compare everything to
 * @param list pointer to head of parsedUDL linked list
 *
 * @returns CMSG_OK if successful or list is NULL
 * @returns CMSG_BAD_ARGUMENT if domain arg is NULL
 * @returns CMSG_WRONG_DOMAIN_TYPE one of the domains is of the wrong type
 */
static int isSameDomain(const char *domain, parsedUDL *list) {
  
  parsedUDL *pUDL;

  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  if (list   == NULL) return(CMSG_OK);
    
  /* first make sure all domains are the same */
  pUDL = list;
  while (pUDL != NULL) {
    if (strcasecmp(pUDL->domain, domain) != 0) {
        return(CMSG_WRONG_DOMAIN_TYPE);
    }
    pUDL = pUDL->next;
  }
    
  return(CMSG_OK);
}


/**
 * Routine to remove duplicate entries in a linked list of parsedUDL structures.
 *
 * @param list pointer to head of parsedUDL linked list
 */
static void removeDuplicateUDLs(parsedUDL *list) {
  
  int index1=0, index2=0, itemRemoved=0;
  parsedUDL *pUDL, *pUDL2, *pPrev=NULL;

  if (list == NULL) return;
    
  /* eliminate duplicates from the linked list */
  pUDL = list;
  while (pUDL != NULL) {
    pUDL2  = list;
    /* start comparing with the next element on the list */
    index2 = index1 + 1;
    while (pUDL2 != NULL) {
      if (index2-- > 0) {
        pPrev = pUDL2;
        pUDL2 = pUDL2->next;
        continue;
      }
      /* if the part of the UDL after cMsg:<domain>:// is identical, remove 2nd occurrance */
      if (strcmp(pUDL->remainder, pUDL2->remainder) == 0) {
         /* remove from list */
         pPrev->next = pUDL2->next;
         pUDL2 = pPrev;
         itemRemoved++;
      }
    
      pPrev = pUDL2;
      pUDL2 = pUDL2->next;    
    }
    
    pUDL = pUDL->next;
    index1++;
  }
  
  if (itemRemoved) {
    if (cMsgDebug >= CMSG_DEBUG_WARN) {
      fprintf(stderr, "cleanUpUDLs: duplicate UDL removed from list\n");
    }
  }
  
  return;
}


/**
 * Routine to expand all the UDLs in the given list in the configFile domain.
 * Each config file UDL in the original list has its file read to obtain its
 * UDL which is then substituted for the original UDL.
 *
 * @param list pointer to the linked list
 * @param firstDomain pointer to char pointer which gets filled with the domain
 *                    of the first element in the list
 * @param count pointer to int which gets filled with number of items in list
 *
 * @returns CMSG_OK if successful or list is NULL
 * @returns CMSG_ERROR if not successful reading file
 * @returns CMSG_BAD_FORMAT if file contains UDL of configFile domain or
 *                          if one of the UDLs is not in the correct format
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 */
static int expandConfigFileUDLs(parsedUDL **list, char **firstDomain, int *count) {
  
  int i, err, len=0, size;
  parsedUDL *pUDL, *newList, *pLast, *pTmp, *pPrev=NULL, *pFirst;
  char  *newUDL, *udlLowerCase;

  if (list == NULL) return(CMSG_OK);
    
  pFirst = pUDL = *list;
  if (pUDL == NULL) return(CMSG_OK);
  
  while (pUDL != NULL) {
    
    /* if not configFile domain, skip to next */
    if (strcasecmp(pUDL->domain, "configFile") != 0) {
/* printf("expandConfigFileUDLs: in %s domain\n", pUDL->domain); */
        pPrev = pUDL;
        pUDL  = pUDL->next;
        len++;
        continue;
    }
/* printf("expandConfigFileUDLs: in configFile domain\n"); */

    /* Do something special if the domain is configFile.
     * Read the file and use that as the real UDL.  */

    /* read file (remainder of UDL) */
    if ( (err = readConfigFile(pUDL->remainder, &newUDL)) != CMSG_OK ) {
      return(err);      
    }
/* printf("expandConfigFileUDLs: read file, udl = %s\n", newUDL); */

    /* make a copy in all lower case */
    udlLowerCase = (char *) strdup(newUDL);
    len = strlen(udlLowerCase);
    for (i=0; i<len; i++) {
      udlLowerCase[i] = tolower(udlLowerCase[i]);
    }

    if (strstr(udlLowerCase, "configfile") != NULL) {
      free(newUDL);
      free(udlLowerCase);
      if (cMsgDebug >= CMSG_DEBUG_ERROR) {
        fprintf(stderr, "expandConfigFileUDLs: one configFile domain UDL may NOT reference another\n");
      }
      return(CMSG_BAD_FORMAT);
    }

    free(udlLowerCase);

    /* The UDL may be a semicolon separated list, so put elements into a linked list */
    if ( (err = splitUDL(newUDL, &newList, NULL, &size)) != CMSG_OK) {
        free(newUDL);
        return(err);
    }
    len += size;
    free(newUDL);

    /* find the end of the file's list */
    pLast = newList;
    while (pLast->next != NULL) {
      pLast = pLast->next;
    }
     
    /**********************************************************/
    /* Replace the UDL that was expanded in the original list */
    /**********************************************************/
   
    /* This item will be analyzed next */
    pTmp = pUDL->next;
    
    /* If this is not the first item in the original list, have the previous item point
     * to the new list (eliminating the current item which is being replaced) */
    if (pPrev != NULL) {
        pPrev->next = newList;
    }
    /* If the first item in the original list is being replaced, record that */
    else {
        pFirst = newList;
    }
    /*pUDL->next  = newList;*/
    pLast->next = pTmp;
    
    /* move on to next item in original list */
    pUDL = pTmp;
    
  }
  
  if (firstDomain != NULL) *firstDomain = (char *) strdup(pFirst->domain);
  if (list  != NULL) *list  = pFirst;
  if (count != NULL) *count = len;
  
  return(CMSG_OK);
}


/**
 * Routine to create a single, semicolon-separated UDL from the given linked
 * list of parsedUDL structures.
 *
 * @param pList pointer to the head of the linked list
 * @param domainType domain of each UDL
 * @param UDL pointer to char pointer which gets filled with the resultant list
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 */
static int reconstructUDL(char *domainType, parsedUDL *pList, char **UDL) {
  int  prefixLen, totalLen=0;
  char *udl, *prefix;
  parsedUDL *pUDL;

 /* Reconstruct the UDL for passing on to the proper domain (if necessary).
   * Do that by first scanning thru the list to get the length of string
   * we'll be dealing with. Then scan again to construct the string. */
  prefixLen = 8+strlen(domainType); /* length of cMsg:<domainType>:// */
  prefix = (char *) calloc(1, prefixLen+1);
  if (prefix == NULL) {
    return(CMSG_OUT_OF_MEMORY);
  }
  sprintf(prefix, "%s%s%s", "cMsg:", domainType, "://");
  
  pUDL = pList;
  while (pUDL != NULL) {
    totalLen += prefixLen + strlen(pUDL->remainder) + 1; /* +1 is for semicolon */
    pUDL = pUDL->next;
  }
  totalLen--; /* don't need last semicolon */
  
  udl = (char *) calloc(1,totalLen+1);
  if (udl == NULL) {
    free(prefix);
    return(CMSG_OUT_OF_MEMORY);
  }
  
  /* String all UDLs together */
  pUDL = pList;
  while (pUDL != NULL) {
    strcat(udl, prefix);
    strcat(udl, pUDL->remainder);
    if (pUDL->next != NULL) strcat(udl, ";");
    pUDL = pUDL->next;
  }
  
  free(prefix);
  
  if (UDL != NULL) {
    *UDL = udl;
  }
  else {
    free(udl);
  }
  
  return(CMSG_OK);
}



/**
 * This routine is called once to connect to a domain.
 * The argument "myUDL" is the Universal Domain Locator used to uniquely
 * identify the cMsg server to connect to. It has the form:<p>
 *       <b><i>cMsg:domainType://domainInfo </i></b><p>
 * The argument "myName" is the client's name and may be required to be
 * unique within the domain depending on the domain.
 * The argument "myDescription" is an arbitrary string used to describe the
 * client.
 * If successful, this routine fills the argument "domainId", which identifies
 * the connection uniquely and is required as an argument by many other routines.
 * 
 * @param myUDL the Universal Domain Locator used to uniquely identify the cMsg
 *        server to connect to
 * @param myName name of this client
 * @param myDescription description of this client
 * @param domainId pointer to pointer which gets filled with a unique id referring
 *        to this connection.
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if regular expression compilation fails during UDL parsing
 *                     or circular UDL references
 * @returns CMSG_BAD_ARGUMENT if one of the arguments is bad
 * @returns CMSG_BAD_FORMAT if the UDL is formatted incorrectly
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgConnect
 */   
int cMsgConnect(const char *myUDL, const char *myName,
                const char *myDescription, void **domainId) {

  int     i, err, listSize;
  char  *domainType=NULL, *newUDL=NULL;
  cMsgDomain *domain;
  parsedUDL  *pList;
  
  /* check args */
  if ( (checkString(myName)        != CMSG_OK) ||
       (checkString(myUDL)         != CMSG_OK) ||
       (checkString(myDescription) != CMSG_OK) ||
       (domainId                   == NULL   ))  {
    return(CMSG_BAD_ARGUMENT);
  }

  /* The UDL may be a semicolon separated list, so put elements into a linked list */
  if ( (err = splitUDL(myUDL, &pList, NULL, NULL)) != CMSG_OK) {
      return(err);
  }
  
  /* Expand any elements referring to a configFile UDL.
   * The expansion is only done at one level, i.e. no
   * files referring to other files. */
  if ( (err = expandConfigFileUDLs(&pList, &domainType, &listSize)) != CMSG_OK) {
      free(domainType);
      freeList(pList);
      return(err);
  }

  if (listSize > 1) {
    /* Make sure that all UDLs in the list are of the same domain */
    if ( (err = isSameDomain(domainType, pList)) != CMSG_OK) {
        free(domainType);
        freeList(pList);
        return(err);  
    }

    /* Remove duplicate UDLs from list. A warning is printed to stderr 
     * if the debug was set and any UDLs were removed. */
    removeDuplicateUDLs(pList);
  }  
  
  /* Take the linked list and turn it into a semicolon separated string */
  if ( (err = reconstructUDL(domainType, pList, &newUDL)) != CMSG_OK) {
        free(domainType);
        freeList(pList);
        return(err);  
  }
/*printf("Reconstructed udl = %s\n", newUDL);*/

  /* First, grab mutex for thread safety. This mutex must be held until
   * the initialization is completely finished.
   */
  connectMutexLock();

  /* do one time initialization */
  if (!oneTimeInitialized) {

    /* clear array */
    for (i=0; i<CMSG_MAX_DOMAIN_TYPES; i++) {
        dTypeInfo[i].type = NULL;
    }

    /* register domain types */
    if ( (err = registerPermanentDomains()) != CMSG_OK ) {
      /* if we can't find the domain lib, or run out of memory, return error */
      free(newUDL);
      free(domainType);
      freeList(pList);
      connectMutexUnlock();
      return(err);
    }

    oneTimeInitialized = 1;
  }
  
  connectMutexUnlock();
  
  
  /* register dynamic domain types */
  if ( (err = registerDynamicDomains(domainType)) != CMSG_OK ) {
    /* if we can't find the domain lib, or run out of memory, return error */
      free(newUDL);
      free(domainType);
      freeList(pList);
      return(err);
  }
  

  /* allocate struct to hold connection info */
  domain = (cMsgDomain *) calloc(1, sizeof(cMsgDomain));
  if (domain == NULL) {
      free(newUDL);
      free(domainType);
      freeList(pList);
      return(CMSG_OUT_OF_MEMORY);  
  }
  domainInit(domain);  


  /* store names, can be changed until server connection established */
  domain->name         = (char *) strdup(myName);
  domain->udl          = newUDL;
  domain->description  = (char *) strdup(myDescription);
  domain->type         = domainType;
  domain->UDLremainder = (char *) strdup(pList->remainder);
  
  freeList(pList);

  /* if such a domain type exists, store pointer to functions */
  domain->functions = NULL;
  for (i=0; i<CMSG_MAX_DOMAIN_TYPES; i++) {
    if (dTypeInfo[i].type != NULL) {
      if (strcasecmp(dTypeInfo[i].type, domain->type) == 0) {
	domain->functions = dTypeInfo[i].functions;
	break;
      }
    }
  }
  if (domain->functions == NULL) return(CMSG_BAD_DOMAIN_TYPE);
  

  /* dispatch to connect function registered for this domain type */
  err = domain->functions->connect(newUDL, myName, myDescription,
                                   domain->UDLremainder, &domain->implId);
  if (err != CMSG_OK) {
      domainFree(domain);
      free(domain);
      return err;
  }  
  
  domain->connected = 1;
  *domainId = (void *)domain;
  
  return CMSG_OK;
}


/*-------------------------------------------------------------------*/


/**
 * This routine is called once to connect to a domain.
 * The argument "myUDL" is the Universal Domain Locator used to uniquely
 * identify the cMsg server to connect to. It has the form:<p>
 *       <b><i>cMsg:domainType://domainInfo </i></b><p>
 * The argument "myName" is the client's name and may be required to be
 * unique within the domain depending on the domain.
 * The argument "myDescription" is an arbitrary string used to describe the
 * client.
 * If successful, this routine fills the argument "domainId", which identifies
 * the connection uniquely and is required as an argument by many other routines.
 * 
 * @param myUDL the Universal Domain Locator used to uniquely identify the cMsg
 *        server to connect to
 * @param myName name of this client
 * @param myDescription description of this client
 * @param domainId pointer to pointer which gets filled with a unique id referring
 *        to this connection.
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if regular expression compilation fails during UDL parsing
 *                     or circular UDL references
 * @returns CMSG_BAD_ARGUMENT if one of the arguments is bad
 * @returns CMSG_BAD_FORMAT if the UDL is formatted incorrectly
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgConnect
 */   
int cMsgConnectOrig(const char *myUDL, const char *myName,
                    const char *myDescription, void **domainId) {

  int i, loops=0, err, reconstruct=0;
  size_t len;
  char *pchar, udl[1000], *domainType, *UDLremainder, *newUDL=NULL;
  cMsgDomain *domain;
  
  /* check args */
  if ( (checkString(myName)        != CMSG_OK) ||
       (checkString(myUDL)         != CMSG_OK) ||
       (checkString(myDescription) != CMSG_OK) ||
       (domainId                   == NULL   ))  {
    return(CMSG_BAD_ARGUMENT);
  }
  
  /* the UDL may be a semicolon separated list.
   * Look at the first one if that's the case.
   */
  if ( (pchar = strchr(myUDL,';')) != NULL) {
    len = strlen(myUDL) - strlen(pchar);
    strncpy(udl, myUDL, len);
    udl[len] = '\0';
/*printf("The first udl = %s, parse that\n", udl);*/
    /* parse the UDL - Uniform Domain Locator */
    if ( (err = parseUDL(udl, &domainType, &UDLremainder)) != CMSG_OK ) {
      return(err);
    } 
  }
  else {
    /* parse the UDL - Uniform Domain Locator */
    if ( (err = parseUDL(myUDL, &domainType, &UDLremainder)) != CMSG_OK ) {
      return(err);
    }
  }
   
  /* Do something special if the domain is configFile.
   * Read the file and use that as the real UDL.
   */
  while (strcasecmp(domainType,"configFile") == 0) {
/*printf("in configFile domain\n");*/
    /* read file (remainder of UDL) */
    if (newUDL != NULL) free(newUDL);
    if ( (err = readConfigFile(UDLremainder, &newUDL)) != CMSG_OK ) {
      return(err);      
    }
    
    /* free strings before we overwrite */
    free(domainType);
    free(UDLremainder);
    
    if ( (err = parseUDL(newUDL, &domainType, &UDLremainder)) != CMSG_OK ) {
      return(err);
    }
     
    myUDL = newUDL;
    reconstruct = 1;
    
    /* check for circular references */
    if (loops++ > 20) {
      free(newUDL);
      free(domainType);
      free(UDLremainder);
      return(CMSG_ERROR);
    }
  }
  
  /* reconstruct the UDL if necessary */
  if (reconstruct && (pchar != NULL)) {
    sprintf(udl,"%s%s\n", newUDL, pchar);
/*printf("Reconstructed udl = %s\n", udl);*/
    myUDL = udl;
  }

  /* First, grab mutex for thread safety. This mutex must be held until
   * the initialization is completely finished.
   */
  connectMutexLock();

  /* do one time initialization */
  if (!oneTimeInitialized) {

    /* clear array */
    for (i=0; i<CMSG_MAX_DOMAIN_TYPES; i++) {
        dTypeInfo[i].type = NULL;
    }

    /* register domain types */
    if ( (err = registerPermanentDomains()) != CMSG_OK ) {
      /* if we can't find the domain lib, or run out of memory, return error */
      connectMutexUnlock();
      return(err);
    }

    oneTimeInitialized = 1;
  }
  
  connectMutexUnlock();
  
  
  /* register dynamic domain types */
  if ( (err = registerDynamicDomains(domainType)) != CMSG_OK ) {
    /* if we can't find the domain lib, or run out of memory, return error */
    return(err);
  }
  

  /* allocate struct to hold connection info */
  domain = (cMsgDomain *) calloc(1, sizeof(cMsgDomain));
  if (domain == NULL) {
    return(CMSG_OUT_OF_MEMORY);  
  }
  domainInit(domain);  


  /* store names, can be changed until server connection established */
  domain->name         = (char *) strdup(myName);
  domain->udl          = (char *) strdup(myUDL);
  domain->description  = (char *) strdup(myDescription);
  domain->type         = domainType;
  domain->UDLremainder = UDLremainder;

  /* if such a domain type exists, store pointer to functions */
  domain->functions = NULL;
  for (i=0; i<CMSG_MAX_DOMAIN_TYPES; i++) {
    if (dTypeInfo[i].type != NULL) {
      if (strcasecmp(dTypeInfo[i].type, domain->type) == 0) {
	domain->functions = dTypeInfo[i].functions;
	break;
      }
    }
  }
  if (domain->functions == NULL) return(CMSG_BAD_DOMAIN_TYPE);
  

  /* dispatch to connect function registered for this domain type */
  err = domain->functions->connect(myUDL, myName, myDescription,
                                       domain->UDLremainder, &domain->implId);

  if (err != CMSG_OK) {
    domainFree(domain);
    free(domain);
    return err;
  }  
  
  domain->connected = 1;
  *domainId = (void *)domain;
  
  return CMSG_OK;
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends a msg to the specified domain server. It is completely
 * asynchronous and never blocks. The domain may require cMsgFlush() to be
 * called to force delivery.
 * The domainId argument is created by calling cMsgConnect()
 * and establishing a connection to a cMsg server. The message to be sent
 * may be created by calling cMsgCreateMessage(),
 * cMsgCreateNewMessage(), or cMsgCopyMessage().
 *
 * @param domainId id of the domain connection
 * @param msg pointer to a message structure
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSend
 */   
int cMsgSend(void *domainId, const void *msg) {
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  /* dispatch to function registered for this domain type */
  return(domain->functions->send(domain->implId, msg));
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends a msg to the specified domain server and receives a response.
 * It is a synchronous routine and as a result blocks until it receives a status
 * integer from the cMsg server.
 * The domainId argument is created by calling cMsgConnect()
 * and establishing a connection to a cMsg server. The message to be sent
 * may be created by calling cMsgCreateMessage(),
 * cMsgCreateNewMessage(), or cMsgCopyMessage().
 *
 * @param domainId id of the domain connection
 * @param msg pointer to a message structure
 * @param timeout amount of time to wait for the response
 * @param response integer pointer that gets filled with the response
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSyncSend
 */   
int cMsgSyncSend(void *domainId, const void *msg, const struct timespec *timeout, int *response) {
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  /* dispatch to function registered for this domain type */
  return(domain->functions->syncSend(domain->implId, msg, timeout, response));
}


/*-------------------------------------------------------------------*/


/**
 * This routine sends any pending (queued up) communication with the server.
 * The implementation of this routine depends entirely on the domain in which 
 * it is being used. In the cMsg domain, this routine does nothing as all server
 * communications are sent immediately upon calling any function.
 *
 * @param domainId id of the domain connection
 * @param timeout amount of time to wait for completion
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgFlush
 */   
int cMsgFlush(void *domainId, const struct timespec *timeout) {
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  return(domain->functions->flush(domain->implId, timeout));
}


/*-------------------------------------------------------------------*/


/**
 * This routine subscribes to messages of the given subject and type.
 * When a message is received, the given callback is passed the message
 * pointer and the userArg pointer and then is executed. A configuration
 * structure is given to determine the behavior of the callback.
 * Only 1 subscription for a specific combination of subject, type, callback
 * and userArg is allowed.
 *
 * @param domainId id of the domain connection
 * @param subject subject of messages subscribed to
 * @param type type of messages subscribed to
 * @param callback pointer to callback to be executed on receipt of message
 * @param userArg user-specified pointer to be passed to the callback
 * @param config pointer to callback configuration structure
 * @param handle pointer to handle (void pointer) to be used for unsubscribing
 *               from this subscription
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSubscribe
 */   
int cMsgSubscribe(void *domainId, const char *subject, const char *type, cMsgCallbackFunc *callback,
                  void *userArg, cMsgSubscribeConfig *config, void **handle) {
                    
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  return(domain->functions->subscribe(domain->implId, subject, type, callback,
                                        userArg, config, handle));
}


/*-------------------------------------------------------------------*/


/**
 * This routine unsubscribes to messages of the given handle (which
 * represents a given subject, type, callback, and user argument).
 *
 * @param domainId id of the domain connection
 * @param handle void pointer obtained from cMsgSubscribe
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgUnSubscribe
 */   
int cMsgUnSubscribe(void *domainId, void *handle) {
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  return(domain->functions->unsubscribe(domain->implId, handle));
} 


/*-------------------------------------------------------------------*/


/**
 * This routine gets one message from another cMsg client by sending out
 * an initial message to that responder. It is a synchronous routine that
 * fails when no reply is received with the given timeout. This function
 * can be thought of as a peer-to-peer exchange of messages.
 * One message is sent to all listeners. The first responder
 * to the initial message will have its single response message sent back
 * to the original sender.
 *
 * @param domainId id of the domain connection
 * @param sendMsg messages to send to all listeners
 * @param timeout amount of time to wait for the response message
 * @param replyMsg message received from the responder
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSendAndGet
 */   
int cMsgSendAndGet(void *domainId, const void *sendMsg, const struct timespec *timeout, void **replyMsg) {
    
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  return(domain->functions->sendAndGet(domain->implId, sendMsg, timeout, replyMsg));
}


/*-------------------------------------------------------------------*/


/**
 * This routine gets one message from a one-time subscription to the given
 * subject and type.
 *
 * @param domainId id of the domain connection
 * @param subject subject of message subscribed to
 * @param type type of message subscribed to
 * @param timeout amount of time to wait for the message
 * @param replyMsg message received
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSendAndGet
 */   
int cMsgSubscribeAndGet(void *domainId, const char *subject, const char *type,
                        const struct timespec *timeout, void **replyMsg) {
  
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  return(domain->functions->subscribeAndGet(domain->implId, subject, type,
                                                timeout, replyMsg));
}


/*-------------------------------------------------------------------*/


/**
 * This method is a synchronous call to receive a message containing monitoring
 * data which describes the state of the cMsg domain the user is connected to.
 * The time is data was sent can be obtained by calling cMsgGetSenderTime.
 * The monitoring data in xml format can be obtained by calling cMsgGetText.
 *
 * @param domainId id of the domain connection
 * @param command string to monitor data collecting routine
 * @param replyMsg message received from the domain containing monitor data
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgSendAndGet
 */   
int cMsgMonitor(void *domainId, const char *command, void **replyMsg) {
    
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  return(domain->functions->monitor(domain->implId, command, replyMsg));
}


/*-------------------------------------------------------------------*/


/**
 * This routine enables the receiving of messages and delivery to callbacks.
 * The receiving of messages is disabled by default and must be explicitly enabled.
 *
 * @param domainId id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgReceiveStart
 */   
int cMsgReceiveStart(void *domainId) {

  int err;
  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  
  if ( (err = domain->functions->start(domain->implId)) != CMSG_OK) {
    return err;
  }
  
  domain->receiveState = 1;
  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine disables the receiving of messages and delivery to callbacks.
 * The receiving of messages is disabled by default. This routine only has an
 * effect when cMsgReceiveStart() was previously called.
 *
 * @param domainId id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgReceiveStop
 */   
int cMsgReceiveStop(void *domainId) {

  int err;
  cMsgDomain *domain = (cMsgDomain *) domainId;

  if (domain == NULL)     return(CMSG_BAD_ARGUMENT);
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
  
  if ( (err = domain->functions->stop(domain->implId)) != CMSG_OK ) {
    return err;
  }
  
  domain->receiveState = 0;
  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine disconnects the client from the cMsg server.
 *
 * @param domainId pointer to id of the domain connection
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if domainId or the pointer it points to is NULL
 * @returns CMSG_LOST_CONNECTION if no longer connected to domain
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgDisconnect
 */   
int cMsgDisconnect(void **domainId) {
  
  int err;
  cMsgDomain *domain;
  
  if (domainId == NULL) return(CMSG_BAD_ARGUMENT);
  domain = (cMsgDomain *) *domainId;
  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  
  if (!domain->connected) return(CMSG_LOST_CONNECTION);
    
  domain->connected = 0;
  
  if ( (err = domain->functions->disconnect(&domain->implId)) != CMSG_OK) {
    return err;
  }
  
  domainFree(domain);
  free(domain);
  *domainId = NULL;
    
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*   shutdown handler functions                                      */
/*-------------------------------------------------------------------*/


/**
 * This routine sets the shutdown handler function.
 *
 * @param domainId id of the domain connection
 * @param handler shutdown handler function
 * @param userArg argument to shutdown handler 
 *
 * @returns CMSG_OK if successful
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgDisconnect
 */   
int cMsgSetShutdownHandler(void *domainId, cMsgShutdownHandler *handler, void *userArg) {
  
  cMsgDomain *domain = (cMsgDomain *) domainId;
  return(domain->functions->setShutdownHandler(domain->implId, handler, userArg));
}

/*-------------------------------------------------------------------*/

/**
 * Method to shutdown the given clients.
 *
 * @param domainId id of the domain connection
 * @param client client(s) to be shutdown
 * @param flag   flag describing the mode of shutdown: 0 to not include self,
 *               CMSG_SHUTDOWN_INCLUDE_ME to include self in shutdown.
 * 
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement shutdown
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_BAD_ARGUMENT if one of the arguments is bad
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgDisconnect
 */
int cMsgShutdownClients(void *domainId, const char *client, int flag) {
  
  cMsgDomain *domain = (cMsgDomain *) domainId;

  if (flag != 0 && flag!= CMSG_SHUTDOWN_INCLUDE_ME) return(CMSG_BAD_ARGUMENT);
    
  return(domain->functions->shutdownClients(domain->implId, client, flag));

}

/*-------------------------------------------------------------------*/

/**
 * Method to shutdown the given servers.
 *
 * @param domainId id of the domain connection
 * @param server server(s) to be shutdown
 * @param flag   flag describing the mode of shutdown: 0 to not include self,
 *               CMSG_SHUTDOWN_INCLUDE_ME to include self in shutdown.
 * 
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_IMPLEMENTED if the subdomain used does NOT implement shutdown
 * @returns CMSG_NETWORK_ERROR if error in communicating with the server
 * @returns CMSG_BAD_ARGUMENT if one of the arguments is bad
 * @returns any errors returned from the actual domain dependent implemenation
 *          of cMsgDisconnect
 */
int cMsgShutdownServers(void *domainId, const char *server, int flag) {
  
  cMsgDomain *domain = (cMsgDomain *) domainId;

  if (flag != 0 && flag!= CMSG_SHUTDOWN_INCLUDE_ME) return(CMSG_BAD_ARGUMENT);
    
  return(domain->functions->shutdownServers(domain->implId, server, flag));

}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/


/**
 * This routine returns a string describing the given error condition.
 * It can also print out that same string with printf if the debug level
 * is set to CMSG_DEBUG_ERROR or CMSG_DEBUG_SEVERE by cMsgSetDebugLevel().
 * The returned string is a static char array. This means it is not 
 * thread-safe and will be overwritten on subsequent calls.
 *
 * @param error error condition
 *
 * @returns error string
 */   
char *cMsgPerror(int error) {

  static char temp[256];

  switch(error) {

  case CMSG_OK:
    sprintf(temp, "CMSG_OK:  action completed successfully\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_OK:  action completed successfully\n");
    break;

  case CMSG_ERROR:
    sprintf(temp, "CMSG_ERROR:  generic error return\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_ERROR:  generic error return\n");
    break;

  case CMSG_TIMEOUT:
    sprintf(temp, "CMSG_TIMEOUT:  no response from cMsg server within timeout period\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_TIMEOUT:  no response from cMsg server within timeout period\n");
    break;

  case CMSG_NOT_IMPLEMENTED:
    sprintf(temp, "CMSG_NOT_IMPLEMENTED:  function not implemented\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_NOT_IMPLEMENTED:  function not implemented\n");
    break;

  case CMSG_BAD_ARGUMENT:
    sprintf(temp, "CMSG_BAD_ARGUMENT:  one or more arguments bad\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_BAD_ARGUMENT:  one or more arguments bad\n");
    break;

  case CMSG_BAD_FORMAT:
    sprintf(temp, "CMSG_BAD_FORMAT:  one or more arguments in the wrong format\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_BAD_FORMAT:  one or more arguments in the wrong format\n");
    break;

  case CMSG_BAD_DOMAIN_TYPE:
    sprintf(temp, "CMSG_BAD_DOMAIN_TYPE:  domain type not supported\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_BAD_DOMAIN_TYPE:  domain type not supported\n");
    break;

  case CMSG_ALREADY_EXISTS:
    sprintf(temp, "CMSG_ALREADY_EXISTS: a unique item with that property already exists\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_ALREADY_EXISTS:  a unique item with that property already exists\n");
    break;

  case CMSG_NOT_INITIALIZED:
    sprintf(temp, "CMSG_NOT_INITIALIZED:  cMsgConnect needs to be called\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_NOT_INITIALIZED:  cMsgConnect needs to be called\n");
    break;

  case CMSG_ALREADY_INIT:
    sprintf(temp, "CMSG_ALREADY_INIT:  cMsgConnect already called\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_ALREADY_INIT:  cMsgConnect already called\n");
    break;

  case CMSG_LOST_CONNECTION:
    sprintf(temp, "CMSG_LOST_CONNECTION:  connection to cMsg server lost\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_LOST_CONNECTION:  connection to cMsg server lost\n");
    break;

  case CMSG_NETWORK_ERROR:
    sprintf(temp, "CMSG_NETWORK_ERROR:  error talking to cMsg server\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_NETWORK_ERROR:  error talking to cMsg server\n");
    break;

  case CMSG_SOCKET_ERROR:
    sprintf(temp, "CMSG_SOCKET_ERROR:  error setting socket options\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_SOCKET_ERROR:  error setting socket options\n");
    break;

  case CMSG_PEND_ERROR:
    sprintf(temp, "CMSG_PEND_ERROR:  error waiting for messages to arrive\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_PEND_ERROR:  error waiting for messages to arrive\n");
    break;

  case CMSG_ILLEGAL_MSGTYPE:
    sprintf(temp, "CMSG_ILLEGAL_MSGTYPE:  pend received illegal message type\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_ILLEGAL_MSGTYPE:  pend received illegal message type\n");
    break;

  case CMSG_OUT_OF_MEMORY:
    sprintf(temp, "CMSG_OUT_OF_MEMORY:  ran out of memory\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_OUT_OF_MEMORY:  ran out of memory\n");
    break;

  case CMSG_OUT_OF_RANGE:
    sprintf(temp, "CMSG_OUT_OF_RANGE:  argument is out of range\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_OUT_OF_RANGE:  argument is out of range\n");
    break;

  case CMSG_LIMIT_EXCEEDED:
    sprintf(temp, "CMSG_LIMIT_EXCEEDED:  trying to create too many of something\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_LIMIT_EXCEEDED:  trying to create too many of something\n");
    break;

  case CMSG_BAD_DOMAIN_ID:
    sprintf(temp, "CMSG_BAD_DOMAIN_ID: id does not match any existing domain\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_BAD_DOMAIN_ID: id does not match any existing domain\n");
    break;

  case CMSG_BAD_MESSAGE:
    sprintf(temp, "CMSG_BAD_MESSAGE: message is not in the correct form\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_BAD_MESSAGE: message is not in the correct form\n");
    break;

  case CMSG_WRONG_DOMAIN_TYPE:
    sprintf(temp, "CMSG_WRONG_DOMAIN_TYPE: UDL does not match the server type\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_WRONG_DOMAIN_TYPE: UDL does not match the server type\n");
    break;
  case CMSG_NO_CLASS_FOUND:
    sprintf(temp, "CMSG_NO_CLASS_FOUND: class cannot be found to instantiate a subdomain client handler\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_NO_CLASS_FOUND: class cannot be found to instantiate a subdomain client handler\n");
    break;

  case CMSG_DIFFERENT_VERSION:
    sprintf(temp, "CMSG_DIFFERENT_VERSION: client and server are different versions\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_DIFFERENT_VERSION: client and server are different versions\n");
    break;

  case CMSG_WRONG_PASSWORD:
    sprintf(temp, "CMSG_WRONG_PASSWORD: wrong password given\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_WRONG_PASSWORD: wrong password given\n");
    break;

  case CMSG_SERVER_DIED:
    sprintf(temp, "CMSG_SERVER_DIED: server died\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_SERVER_DIED: server died\n");
    break;

  case CMSG_ABORT:
    sprintf(temp, "CMSG_ABORT: abort procedure\n");
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("CMSG_ABORT: aborted procedure\n");
    break;

  default:
    sprintf(temp, "?cMsgPerror...no such error: %d\n",error);
    if (cMsgDebug>CMSG_DEBUG_ERROR) printf("?cMsgPerror...no such error: %d\n",error);
    break;
  }

  return(temp);
}


/*-------------------------------------------------------------------*/


/**
 * This routine sets the level of debug output. The argument should be
 * one of:<p>
 * - #CMSG_DEBUG_NONE
 * - #CMSG_DEBUG_INFO
 * - #CMSG_DEBUG_SEVERE
 * - #CMSG_DEBUG_ERROR
 * - #CMSG_DEBUG_WARN
 *
 * @param level debug level desired
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if debug level is bad
 */   
int cMsgSetDebugLevel(int level) {
  
  if ((level != CMSG_DEBUG_NONE)  &&
      (level != CMSG_DEBUG_INFO)  &&
      (level != CMSG_DEBUG_WARN)  &&
      (level != CMSG_DEBUG_ERROR) &&
      (level != CMSG_DEBUG_SEVERE)) {
    return(CMSG_BAD_ARGUMENT);
  }
  
  cMsgDebug = level;
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*
 *
 * Internal functions
 *
 *-------------------------------------------------------------------*/


/**
 * This routine registers a few permanent domain implementations consisting
 * of a set of functions that implement all the basic domain
 * functionality (connect, disconnect, send, syncSend, flush, subscribe,
 * unsubscribe, sendAndGet, subscribeAndGet, start, and stop). These
 * permanent domains are the cmsg, rc, and file domains.
 * This routine must be updated to include each new permanent domain
 * accessible from the "C" language.
 */   
static int registerPermanentDomains() {
  
  /* cMsg type */
  dTypeInfo[0].type = (char *)strdup(cmsgDomainTypeInfo.type); 
  dTypeInfo[0].functions = cmsgDomainTypeInfo.functions;


  /* runcontrol (rc) domain */
  dTypeInfo[1].type = (char *)strdup(rcDomainTypeInfo.type); 
  dTypeInfo[1].functions = rcDomainTypeInfo.functions;


  /* for file domain */
  dTypeInfo[2].type = (char *)strdup(fileDomainTypeInfo.type); 
  dTypeInfo[2].functions = fileDomainTypeInfo.functions;
     
  return(CMSG_OK);  
}


/**
 * This routine registers domain implementations dynamically.
 * The registration includes the name of the domain
 * along with the set of functions that implement all the basic domain
 * functionality (connect, disconnect, send, syncSend, flush, subscribe,
 * unsubscribe, sendAndGet, subscribeAndGet, start, and stop).
 * This routine is used when a connection to a user-written domain
 * is found in a client's UDL.
 */   
static int registerDynamicDomains(char *domainType) {

  char *lowerCase;
  unsigned int i;
  int   len, index=-1;
  char  functionName[256];
  domainFunctions *funcs;
#ifdef VXWORKS
  char     *pValue;
  SYM_TYPE  pType;
#else
  char  libName[256];
  void *libHandle, *sym;
#endif
      
  /*
   * We have already loaded the cmsg, rc, and file domains.
   * Now we need to dynamically load any libraries needed
   * to support other domains. Look for shared libraries
   * with names of cMsgLib<domain>.so where domain is the
   * domain found in the parsing of the UDL lowered to all
   * lower case letters.
   */
   
  /* First lower the domain name to all lower case letters. */
  lowerCase = (char *) strdup(domainType);
  len = strlen(lowerCase);
  for (i=0; i<len; i++) {
    lowerCase[i] = tolower(lowerCase[i]);
  }
  
  /* Check to see if it's been loaded already */
  for (i=0; i < CMSG_MAX_DOMAIN_TYPES; i++) {
    if (dTypeInfo[i].type == NULL) {
        if (index < 0) {
            index = i;
        }
        continue;
    }    
    
    if ( strcmp(lowerCase, dTypeInfo[i].type) == 0 ) {
        /* already have this domain loaded */
/* printf("registerDynamicDomains: domain %s is already loaded\n", lowerCase); */
        free(lowerCase);
        return(CMSG_OK);    
    }  
  }
  
  /* we need to load a new domain library */  
  funcs = (domainFunctions *) malloc(sizeof(domainFunctions));
  if (funcs == NULL) {
    free(lowerCase);
    return(CMSG_OUT_OF_MEMORY);  
  }
  

#ifdef VXWORKS

  /* get "connect" function from global symbol table */
  sprintf(functionName, "cmsg_%s_connect", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->connect = (CONNECT_PTR) pValue;
  
  /* get "send" function from global symbol table */
  sprintf(functionName, "cmsg_%s_send", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->send = (SEND_PTR) pValue;
  
  /* get "syncSend" function from global symbol table */
  sprintf(functionName, "cmsg_%s_syncSend", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->syncSend = (SYNCSEND_PTR) pValue;
  
  /* get "flush" function from global symbol table */
  sprintf(functionName, "cmsg_%s_flush", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->flush = (FLUSH_PTR) pValue;
  
  /* get "subscribe" function from global symbol table */
  sprintf(functionName, "cmsg_%s_subscribe", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->subscribe = (SUBSCRIBE_PTR) pValue;
  
  /* get "unsubscribe" function from global symbol table */
  sprintf(functionName, "cmsg_%s_unsubscribe", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->unsubscribe = (UNSUBSCRIBE_PTR) pValue;
  
  /* get "subscribeAndGet" function from global symbol table */
  sprintf(functionName, "cmsg_%s_subscribeAndGet", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->subscribeAndGet = (SUBSCRIBE_AND_GET_PTR) pValue;
  
  /* get "sendAndGet" function from global symbol table */
  sprintf(functionName, "cmsg_%s_sendAndGet", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->sendAndGet = (SEND_AND_GET_PTR) pValue;
  
  /* get "start" function from global symbol table */
  sprintf(functionName, "cmsg_%s_start", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->start = (START_STOP_PTR) pValue;
  
  /* get "stop" function from global symbol table */
  sprintf(functionName, "cmsg_%s_stop", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->stop = (START_STOP_PTR) pValue;
  
  /* get "disconnect" function from global symbol table */
  sprintf(functionName, "cmsg_%s_disconnect", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->disconnect = (DISCONNECT_PTR) pValue;
    
  /* get "shutdownClients" function from global symbol table */
  sprintf(functionName, "cmsg_%s_shutdownClients", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->shutdownClients = (SHUTDOWN_PTR) pValue;

  /* get "shutdownServers" function from global symbol table */
  sprintf(functionName, "cmsg_%s_shutdownServers", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->shutdownServers = (SHUTDOWN_PTR) pValue;

  /* get "setShutdownHandler" function from global symbol table */
  sprintf(functionName, "cmsg_%s_setShutdownHandler", lowerCase);
  if (symFindByName(sysSymTbl, functionName, &pValue, &pType) != OK) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  funcs->setShutdownHandler = (SET_SHUTDOWN_HANDLER_PTR) pValue;


#else 
  
  /* create name of library to look for */
  sprintf(libName, "libcmsg%s.so", lowerCase);
/* printf("registerDynamicDomains: looking for %s\n", libName); */
  
  /* open library */
  libHandle = dlopen(libName, RTLD_NOW);
  if (libHandle == NULL) {
    free(funcs);
    free(lowerCase);
    return(CMSG_ERROR);
  }
  
  /* get "connect" function */
  sprintf(functionName, "cmsg_%s_connect", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->connect = (CONNECT_PTR) sym;

  /* get "send" function */
  sprintf(functionName, "cmsg_%s_send", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->send = (SEND_PTR) sym;

  /* get "syncSend" function */
  sprintf(functionName, "cmsg_%s_syncSend", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->syncSend = (SYNCSEND_PTR) sym;

  /* get "flush" function */
  sprintf(functionName, "cmsg_%s_flush", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->flush = (FLUSH_PTR) sym;

  /* get "subscribe" function */
  sprintf(functionName, "cmsg_%s_subscribe", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->subscribe = (SUBSCRIBE_PTR) sym;

  /* get "unsubscribe" function */
  sprintf(functionName, "cmsg_%s_unsubscribe", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->unsubscribe = (UNSUBSCRIBE_PTR) sym;

  /* get "subscribeAndGet" function */
  sprintf(functionName, "cmsg_%s_subscribeAndGet", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->subscribeAndGet = (SUBSCRIBE_AND_GET_PTR) sym;

  /* get "sendAndGet" function */
  sprintf(functionName, "cmsg_%s_sendAndGet", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->sendAndGet = (SEND_AND_GET_PTR) sym;

  /* get "start" function */
  sprintf(functionName, "cmsg_%s_start", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->start = (START_STOP_PTR) sym;

  /* get "stop" function */
  sprintf(functionName, "cmsg_%s_stop", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->stop = (START_STOP_PTR) sym;

  /* get "disconnect" function */
  sprintf(functionName, "cmsg_%s_disconnect", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->disconnect = (DISCONNECT_PTR) sym;

  /* get "shutdownClients" function */
  sprintf(functionName, "cmsg_%s_shutdownClients", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->shutdownClients = (SHUTDOWN_PTR) sym;

  /* get "shutdownServers" function */
  sprintf(functionName, "cmsg_%s_shutdownServers", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->shutdownServers = (SHUTDOWN_PTR) sym;

  /* get "setShutdownHandler" function */
  sprintf(functionName, "cmsg_%s_setShutdownHandler", lowerCase);
  sym = dlsym(libHandle, functionName);
  if (sym == NULL) {
    free(funcs);
    free(lowerCase);
    dlclose(libHandle);
    return(CMSG_ERROR);
  }
  funcs->setShutdownHandler = (SET_SHUTDOWN_HANDLER_PTR) sym;


#endif  

   /* for new domain */
  dTypeInfo[index].type = lowerCase; 
  dTypeInfo[index].functions = funcs;

      
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine initializes the given domain data structure. All strings
 * are set to null.
 *
 * @param domain pointer to structure holding domain info
 */   
static void domainInit(cMsgDomain *domain) {  
  domain->connected      = 0;
  domain->receiveState   = 0;
      
  domain->implId         = NULL;
  domain->type           = NULL;
  domain->name           = NULL;
  domain->udl            = NULL;
  domain->description    = NULL;
  domain->UDLremainder   = NULL;
  domain->functions      = NULL;  
}


/*-------------------------------------------------------------------*/


/**
 * This routine frees all of the allocated memory of the given domain
 * data structure. 
 *
 * @param domain pointer to structure holding domain info
 */   
static void domainFree(cMsgDomain *domain) {  
  if (domain->type         != NULL) {free(domain->type);         domain->type         = NULL;}
  if (domain->name         != NULL) {free(domain->name);         domain->name         = NULL;}
  if (domain->udl          != NULL) {free(domain->udl);          domain->udl          = NULL;}
  if (domain->description  != NULL) {free(domain->description);  domain->description  = NULL;}
  if (domain->UDLremainder != NULL) {free(domain->UDLremainder); domain->UDLremainder = NULL;}
}


/*-------------------------------------------------------------------*
 * Mutex functions
 *-------------------------------------------------------------------*/


/**
 * This routine locks the mutex used to prevent cMsgConnect() and
 * cMsgDisconnect() from being called concurrently.
 */   
static void connectMutexLock(void) {
  int status = pthread_mutex_lock(&connectMutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed mutex lock");
  }
}


/*-------------------------------------------------------------------*/


/**
 * This routine unlocks the mutex used to prevent cMsgConnect() and
 * cMsgDisconnect() from being called concurrently.
 */   
static void connectMutexUnlock(void) {
  int status = pthread_mutex_unlock(&connectMutex);
  if (status != 0) {
    cmsg_err_abort(status, "Failed mutex unlock");
  }
}


/*-------------------------------------------------------------------*/
/*   miscellaneous local functions                                   */
/*-------------------------------------------------------------------*/


/**
 * This routine parses the UDL given by the client in cMsgConnect().
 *
 * The UDL is the Universal Domain Locator used to uniquely
 * identify the cMsg server to connect to. It has the form:<p>
 *       <b><i>cMsg:domainType://domainInfo </i></b><p>
 * The domainType portion gets returned as the domainType.
 * The domainInfo portion gets returned the the UDLremainder.
 * Memory gets allocated for both the domainType and domainInfo which
 * must be freed by the caller.
 *
 * @param UDL UDL
 * @param domainType string which gets filled in with the domain type (eg. cMsg)
 * @param UDLremainder string which gets filled in with everything in the UDL
 *                     after the ://
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if regular expression compilation fails
 * @returns CMSG_BAD_ARGUMENT if the UDL is null
 * @returns CMSG_BAD_FORMAT if the UDL is formatted incorrectly
 * @returns CMSG_OUT_OF_MEMORY if out of memory
 */   
static int parseUDL(const char *UDL, char **domainType, char **UDLremainder) {

    int        err;
    size_t     len, bufLength;
    char       *udl, *buffer;
    const char *pattern = "(cMsg)?:?([a-zA-Z0-9_\\-]+)://(.*)?";  
    regmatch_t matches[4]; /* we have 4 potential matches: 1 whole, 3 sub */
    regex_t    compiled;
    
    if (UDL == NULL) {
        return (CMSG_BAD_FORMAT);
    }
    
    /* make a copy */
    udl = (char *) strdup(UDL);
    
    /* make a big enough buffer to construct various strings, 256 chars minimum */
    len       = strlen(UDL) + 1;
    bufLength = len < 256 ? 256 : len;    
    buffer    = (char *) malloc(bufLength);
    if (buffer == NULL) {
      free(udl);
      return(CMSG_OUT_OF_MEMORY);
    }

    /*
     * cMsg domain UDL is of the form:
     *        cMsg:<domain>://<domain-specific-stuff>
     * where the first "cMsg:" is optional and case insensitive.
     */

    /* compile regular expression (case insensitive matching) */
    err = cMsgRegcomp(&compiled, pattern, REG_EXTENDED|REG_ICASE);
    if (err != 0) {
        free(udl);
        free(buffer);
        return (CMSG_ERROR);
    }
    
    /* find matches */
    err = cMsgRegexec(&compiled, udl, 4, matches, 0);
    if (err != 0) {
        /* no match */
        free(udl);
        free(buffer);
        return (CMSG_BAD_FORMAT);
    }
    
    /* free up memory */
    cMsgRegfree(&compiled);
            
    /* find domain name */
    if ((unsigned int)(matches[2].rm_so) < 0) {
        /* no match for host */
        free(udl);
        free(buffer);
        return (CMSG_BAD_FORMAT);
    }
    else {
       buffer[0] = 0;
       len = matches[2].rm_eo - matches[2].rm_so;
       strncat(buffer, udl+matches[2].rm_so, len);
                        
        if (domainType != NULL) {
            *domainType = (char *)strdup(buffer);
        }
    }
/*printf("parseUDL: domain = %s\n", buffer);*/


    /* find domain remainder */
    buffer[0] = 0;
    if (matches[3].rm_so < 0) {
        /* no match */
        if (UDLremainder != NULL) {
            *UDLremainder = NULL;
        }
    }
    else {
        buffer[0] = 0;
        len = matches[3].rm_eo - matches[3].rm_so;
        strncat(buffer, udl+matches[3].rm_so, len);
                
        if (UDLremainder != NULL) {
            *UDLremainder = (char *) strdup(buffer);
        }        
/* printf("parseUDL: domain remainder = %s\n", buffer); */
    }

    /* UDL parsed ok */
    free(udl);
    free(buffer);
    return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine checks a string given as a function argument.
 * It returns an error if it contains an unprintable character or any
 * character from a list of excluded characters (`'").
 *
 * @param s string to check
 *
 * @returns CMSG_OK if string is OK
 * @returns CMSG_ERROR if string contains excluded or unprintable characters
 */   
static int checkString(const char *s) {

  int i, len;

  if (s == NULL) return(CMSG_ERROR);
  len = strlen(s);

  /* check for printable character */
  for (i=0; i<len; i++) {
    if (isprint((int)s[i]) == 0) return(CMSG_ERROR);
  }

  /* check for excluded chars */
  if (strpbrk(s, excludedChars) != NULL) return(CMSG_ERROR);
  
  /* string ok */
  return(CMSG_OK);
}



/*-------------------------------------------------------------------*/
/*   system accessor functions                                       */
/*-------------------------------------------------------------------*/

/**
 * This routine gets the UDL used to establish a cMsg connection.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param domainId id of the domain connection
 * @param udl pointer to pointer which gets filled with the UDL
 *
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_OK if successful
 */   
int cMsgGetUDL(void *domainId, char **udl) {

  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  
  if (domain->udl == NULL) {
    *udl = NULL;
  }
  else {
    *udl = (char *) (strdup(domain->udl));
  }
  return(CMSG_OK);
}
  
  
/*-------------------------------------------------------------------*/

/**
 * This routine gets the client name used in a cMsg connection.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param domainId id of the domain connection
 * @param name pointer to pointer which gets filled with the name
 *
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_OK if successful
 */   
int cMsgGetName(void *domainId, char **name) {

  cMsgDomain *domain = (cMsgDomain *) domainId;

  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  
  if (domain->name == NULL) {
    *name = NULL;
  }
  else {
    *name = (char *) (strdup(domain->name));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/


/**
 * This routine gets the client description used in a cMsg connection.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param domainId id of the domain connection
 * @param description pointer to pointer which gets filled with the description
 *
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_OK if successful
 */   
int cMsgGetDescription(void *domainId, char **description) {

  cMsgDomain *domain = (cMsgDomain *) domainId;

  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  
  if (domain->description == NULL) {
    *description = NULL;
  }
  else {
    *description = (char *) (strdup(domain->description));
  }
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine gets the state of a cMsg connection. If connectState gets
 * filled with a one, there is a valid connection. Anything else (zero
 * in this case), indicates no connection to a cMsg server.
 *
 * @param domainId id of the domain connection
 * @param connectState integer pointer to be filled in with the connection state
 *
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_OK if successful
 */   
int cMsgGetConnectState(void *domainId, int *connectState) {

  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  *connectState = domain->connected;
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine gets the message receiving state of a cMsg connection. If
 * receiveState gets filled with a one, all messages sent to the client
 * will be received and sent to appropriate callbacks . Anything else (zero
 * in this case), indicates no messages will be received or sent to callbacks.
 *
 * @param domainId id of the domain connection
 * @param receiveState integer pointer to be filled in with the receive state
 *
 * @returns CMSG_BAD_ARGUMENT if domainId is NULL
 * @returns CMSG_OK if successful
 */   
int cMsgGetReceiveState(void *domainId, int *receiveState) {

  cMsgDomain *domain = (cMsgDomain *) domainId;
  
  if (domain == NULL) return(CMSG_BAD_ARGUMENT);
  *receiveState = domain->receiveState;
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*   message accessor functions                                      */
/*-------------------------------------------------------------------*/


/**
 * This routine initializes a given message structure.
 *
 * @param msg pointer to message structure being initialized
 */   
static void initMessage(cMsgMessage_t *msg) {
    int endian;
    if (msg == NULL) return;
    
    msg->version  = CMSG_VERSION_MAJOR;
    msg->sysMsgId = 0;
    msg->bits     = 0;
    msg->info     = 0;
    
    /* default is local endian */
    if (cMsgLocalByteOrder(&endian) == CMSG_OK) {
        if (endian == CMSG_ENDIAN_BIG) {
            msg->info |= CMSG_IS_BIG_ENDIAN;
        }
        else {
            msg->info &= ~CMSG_IS_BIG_ENDIAN;
        }
    }
    
    msg->domain    = NULL;
    msg->creator   = NULL;
    msg->subject   = NULL;
    msg->type      = NULL;
    msg->text      = NULL;
    msg->byteArray = NULL;
    
    msg->byteArrayOffset  = 0;
    msg->byteArrayLength  = 0;
    msg->reserved         = 0;
    msg->userInt          = 0;
    msg->userTime.tv_sec  = 0;
    msg->userTime.tv_nsec = 0;

    msg->sender             = NULL;
    msg->senderHost         = NULL;
    msg->senderTime.tv_sec  = 0;
    msg->senderTime.tv_nsec = 0;
    msg->senderToken        = 0;

    msg->receiver             = NULL;
    msg->receiverHost         = NULL;
    msg->receiverTime.tv_sec  = 0;
    msg->receiverTime.tv_nsec = 0;
    msg->receiverSubscribeId  = 0;
    
    msg->context.domain  = NULL;
    msg->context.subject = NULL;
    msg->context.type    = NULL;
    msg->context.udl     = NULL;
    msg->context.cueSize = NULL;
    msg->context.udpSend = 0;

    return;
  }


/*-------------------------------------------------------------------*/


/**
 * This routine frees the memory of the components of a message,
 * but not the message itself.
 *
 * @param vmsg address of pointer to message structure being freed
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if msg is NULL
 */   
static int freeMessage(void *vmsg) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
   
  if (msg->domain       != NULL) {free(msg->domain);       msg->domain       = NULL;}
  if (msg->creator      != NULL) {free(msg->creator);      msg->creator      = NULL;}
  if (msg->subject      != NULL) {free(msg->subject);      msg->subject      = NULL;}
  if (msg->type         != NULL) {free(msg->type);         msg->type         = NULL;}
  if (msg->text         != NULL) {free(msg->text);         msg->text         = NULL;}
  if (msg->sender       != NULL) {free(msg->sender);       msg->sender       = NULL;}
  if (msg->senderHost   != NULL) {free(msg->senderHost);   msg->senderHost   = NULL;}
  if (msg->receiver     != NULL) {free(msg->receiver);     msg->receiver     = NULL;}
  if (msg->receiverHost != NULL) {free(msg->receiverHost); msg->receiverHost = NULL;}
  
  if (msg->context.domain  != NULL) {free(msg->context.domain);  msg->context.domain  = NULL;}
  if (msg->context.subject != NULL) {free(msg->context.subject); msg->context.subject = NULL;}
  if (msg->context.type    != NULL) {free(msg->context.type);    msg->context.type    = NULL;}
  if (msg->context.udl     != NULL) {free(msg->context.udl);     msg->context.udl     = NULL;}
  if (msg->context.cueSize != NULL) {                            msg->context.cueSize = NULL;}

  /* only free byte array if it was copied into the msg */
  if ((msg->byteArray != NULL) && ((msg->bits & CMSG_BYTE_ARRAY_IS_COPIED) > 0)) {
    free(msg->byteArray);
  }
  
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine frees the memory allocated in the creation of a message.
 * The cMsg client must call this routine on any messages created to avoid
 * memory leaks.
 *
 * @param vmsg address of pointer to message structure being freed
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if msg is NULL
 */   
int cMsgFreeMessage(void **vmsg) {
  int err;
  cMsgMessage_t *msg = (cMsgMessage_t *) (*vmsg);

  if ( (err = freeMessage(msg)) != CMSG_OK) {
    return err;
  }
  free(msg);
  *vmsg = NULL;

  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/


/**
 * This routine copies a message. Memory is allocated with this
 * function and can be freed by cMsgFreeMessage(). Note that the
 * copy of the byte array will only have byteArrayLength number of
 * bytes. Since in C the original size of the array in unknown,
 * a whole copy cannot be guaranteed unless the orginial message
 * has its offset at zero and its length to be the total length
 * of its array.
 *
 * @param vmsg pointer to message structure being copied
 *
 * @returns a pointer to the message copy
 * @returns NULL if argument is NULL or no memory available
 */   
  void *cMsgCopyMessage(const void *vmsg) {
    cMsgMessage_t *newMsg, *msg = (cMsgMessage_t *)vmsg;
    
    if (vmsg == NULL) {
      return NULL;
    }
    
    /* create a message structure */
    if ((newMsg = (cMsgMessage_t *)calloc(1, sizeof(cMsgMessage_t))) == NULL) {
      return NULL;
    }
    
    /*----------------*/
    /* copy over ints */
    /*----------------*/
    
    newMsg->version             = msg->version;
    newMsg->sysMsgId            = msg->sysMsgId;
    newMsg->info                = msg->info;
    newMsg->bits                = msg->bits;
    newMsg->reserved            = msg->reserved;
    newMsg->userInt             = msg->userInt;
    newMsg->userTime            = msg->userTime;
    newMsg->senderTime          = msg->senderTime;
    newMsg->senderToken         = msg->senderToken;
    newMsg->receiverTime        = msg->receiverTime;
    newMsg->receiverSubscribeId = msg->receiverSubscribeId;
    newMsg->byteArrayOffset     = msg->byteArrayOffset;
    newMsg->byteArrayLength     = msg->byteArrayLength;
    
    /*-------------------*/
    /* copy over strings */
    /*-------------------*/
    
    /* copy domain */
    if (msg->domain != NULL) {
        newMsg->domain = (char *) strdup(msg->domain);
        if (newMsg->domain == NULL) {
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->domain = NULL;
    }
        
    /* copy creator */
    if (msg->creator != NULL) {
        newMsg->creator = (char *) strdup(msg->creator);
        if (newMsg->creator == NULL) {
            if (newMsg->domain != NULL) free(newMsg->domain);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->creator = NULL;
    }
        
    /* copy subject */
    if (msg->subject != NULL) {
        newMsg->subject = (char *) strdup(msg->subject);
        if (newMsg->subject == NULL) {
            if (newMsg->domain  != NULL) free(newMsg->domain);
            if (newMsg->creator != NULL) free(newMsg->creator);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->subject = NULL;
    }
        
    /* copy type */
    if (msg->type != NULL) {
        newMsg->type = (char *) strdup(msg->type);
        if (newMsg->type == NULL) {
            if (newMsg->domain  != NULL) free(newMsg->domain);
            if (newMsg->creator != NULL) free(newMsg->creator);
            if (newMsg->subject != NULL) free(newMsg->subject);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->type = NULL;
    }
        
    /* copy text */
    if (msg->text != NULL) {
        newMsg->text = (char *) strdup(msg->text);
        if (newMsg->text == NULL) {
            if (newMsg->domain  != NULL) free(newMsg->domain);
            if (newMsg->creator != NULL) free(newMsg->creator);
            if (newMsg->subject != NULL) free(newMsg->subject);
            if (newMsg->type    != NULL) free(newMsg->type);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->text = NULL;
    }
    
    /* copy sender */
    if (msg->sender != NULL) {
        newMsg->sender = (char *) strdup(msg->sender);
        if (newMsg->sender == NULL) {
            if (newMsg->domain  != NULL) free(newMsg->domain);
            if (newMsg->creator != NULL) free(newMsg->creator);
            if (newMsg->subject != NULL) free(newMsg->subject);
            if (newMsg->type    != NULL) free(newMsg->type);
            if (newMsg->text    != NULL) free(newMsg->text);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->sender = NULL;
    }
    
    /* copy senderHost */
    if (msg->senderHost != NULL) {
        newMsg->senderHost = (char *) strdup(msg->senderHost);
        if (newMsg->senderHost == NULL) {
            if (newMsg->domain  != NULL) free(newMsg->domain);
            if (newMsg->creator != NULL) free(newMsg->creator);
            if (newMsg->subject != NULL) free(newMsg->subject);
            if (newMsg->type    != NULL) free(newMsg->type);
            if (newMsg->text    != NULL) free(newMsg->text);
            if (newMsg->sender  != NULL) free(newMsg->sender);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->senderHost = NULL;
    }
    
    
    /* copy receiver */
    if (msg->receiver != NULL) {
        newMsg->receiver = (char *) strdup(msg->receiver);
        if (newMsg->receiver == NULL) {
            if (newMsg->domain     != NULL) free(newMsg->domain);
            if (newMsg->creator    != NULL) free(newMsg->creator);
            if (newMsg->subject    != NULL) free(newMsg->subject);
            if (newMsg->type       != NULL) free(newMsg->type);
            if (newMsg->text       != NULL) free(newMsg->text);
            if (newMsg->sender     != NULL) free(newMsg->sender);
            if (newMsg->senderHost != NULL) free(newMsg->senderHost);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->receiver = NULL;
    }
        
    /* copy receiverHost */
    if (msg->receiverHost != NULL) {
        newMsg->receiverHost = (char *) strdup(msg->receiverHost);
        if (newMsg->receiverHost == NULL) {
            if (newMsg->domain     != NULL) free(newMsg->domain);
            if (newMsg->creator    != NULL) free(newMsg->creator);
            if (newMsg->subject    != NULL) free(newMsg->subject);
            if (newMsg->type       != NULL) free(newMsg->type);
            if (newMsg->text       != NULL) free(newMsg->text);
            if (newMsg->sender     != NULL) free(newMsg->sender);
            if (newMsg->senderHost != NULL) free(newMsg->senderHost);
            if (newMsg->receiver   != NULL) free(newMsg->receiver);
            free(newMsg);
            return NULL;
        }
    }
    else {
        newMsg->receiverHost = NULL;
    }

    /*-----------------------*/
    /* copy over binary data */
    /*-----------------------*/
    
    /* copy byte array */
    if (msg->byteArray != NULL) {
      /* if byte array was copied into msg, copy it again */
      if ((msg->bits & CMSG_BYTE_ARRAY_IS_COPIED) > 0) {
        newMsg->byteArray = (char *) malloc(msg->byteArrayLength);
        if (newMsg->byteArray == NULL) {
            if (newMsg->domain       != NULL) free(newMsg->domain);
            if (newMsg->creator      != NULL) free(newMsg->creator);
            if (newMsg->subject      != NULL) free(newMsg->subject);
            if (newMsg->type         != NULL) free(newMsg->type);
            if (newMsg->text         != NULL) free(newMsg->text);
            if (newMsg->sender       != NULL) free(newMsg->sender);
            if (newMsg->senderHost   != NULL) free(newMsg->senderHost);
            if (newMsg->receiver     != NULL) free(newMsg->receiver);
            if (newMsg->receiverHost != NULL) free(newMsg->receiverHost);
            free(newMsg);
            return NULL;
        }
        memcpy(newMsg->byteArray,
               &((msg->byteArray)[msg->byteArrayOffset]),
               (size_t) msg->byteArrayLength);
        newMsg->byteArrayOffset = 0;
      }
      /* else copy pointer only */
      else {
        newMsg->byteArray = msg->byteArray;
      }
    }
    else {
      newMsg->byteArray = NULL;
    }

    return (void *)newMsg;
  }


/*-------------------------------------------------------------------*/


/**
 * This routine initializes a message. It frees all allocated memory,
 * sets all strings to NULL, and sets all numeric values to their default
 * state.
 *
 * @param vmsg pointer to message structure being initialized
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if msg is NULL
 */   
int cMsgInitMessage(void *vmsg) {
    int err;
    cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

    if ( (err = freeMessage(msg)) != CMSG_OK) {
      return err;
    }
    
    initMessage(msg);
    return(CMSG_OK);
  }


/*-------------------------------------------------------------------*/


/**
 * This routine creates a new, initialized message. Memory is allocated with this
 * function and can be freed by cMsgFreeMessage().
 *
 * @returns a pointer to the new message
 * @returns NULL if no memory available
 */   
void *cMsgCreateMessage(void) {
  cMsgMessage_t *msg;
  
  msg = (cMsgMessage_t *) malloc(sizeof(cMsgMessage_t));
  if (msg == NULL) return NULL;
  /* initialize the memory */
  initMessage(msg);
  
  return((void *)msg);
}


/*-------------------------------------------------------------------*/


/**
 * This routine creates a new, initialized message with the creator
 * field set to null. Memory is allocated with this
 * function and can be freed by cMsgFreeMessage().
 *
 * @param vmsg pointer to message from which creator field is taken
 *
 * @returns a pointer to the new message
 * @returns NULL if no memory available or message argument is NULL
 */   
void *cMsgCreateNewMessage(const void *vmsg) {  
    cMsgMessage_t *newMsg;
    
    if (vmsg == NULL) return NULL;  
    
    if ((newMsg = (cMsgMessage_t *)cMsgCopyMessage(vmsg)) == NULL) {
      return NULL;
    }
    
    if (newMsg->creator != NULL) {
        free(newMsg->creator);
        newMsg->creator = NULL;
    }
    
    return (void *)newMsg;
}


/*-------------------------------------------------------------------*/


/**
 * This routine creates a new, initialized message with some fields
 * copied from the given message in order to make it a proper response
 * to a sendAndGet() request. Memory is allocated with this
 * function and can be freed by cMsgFreeMessage().
 *
 * @param vmsg pointer to message to which response fields are set
 *
 * @returns a pointer to the new message
 * @returns NULL if no memory available, message argument is NULL, or
 *               message argument is not from calling sendAndGet()
 */   
void *cMsgCreateResponseMessage(const void *vmsg) {
    cMsgMessage_t *newMsg;
    cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
    
    if (vmsg == NULL) return NULL;
    
    /* if message is not a get request ... */
    if (!(msg->info & CMSG_IS_GET_REQUEST)) return NULL;
    
    if ((newMsg = (cMsgMessage_t *)cMsgCreateMessage()) == NULL) {
      return NULL;
    }
    
    newMsg->senderToken = msg->senderToken;
    newMsg->sysMsgId    = msg->sysMsgId;
    newMsg->info        = CMSG_IS_GET_RESPONSE;
    
    return (void *)newMsg;
}


/*-------------------------------------------------------------------*/


/**
 * This routine creates a new, initialized message with some fields
 * copied from the given message in order to make it a proper "NULL"
 * (or no message) response to a sendAndGet() request.
 * Memory is allocated with this function and can be freed by
 * cMsgFreeMessage().
 *
 * @param vmsg pointer to message to which response fields are set
 *
 * @returns a pointer to the new message
 * @returns NULL if no memory available, message argument is NULL, or
 *               message argument is not from calling sendAndGet()
 */   
void *cMsgCreateNullResponseMessage(const void *vmsg) {
    cMsgMessage_t *newMsg;
    cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
    
    if (vmsg == NULL) return NULL;  
    
    /* if message is not a get request ... */
    if (!(msg->info & CMSG_IS_GET_REQUEST)) return NULL;
    
    if ((newMsg = (cMsgMessage_t *)cMsgCreateMessage()) == NULL) {
      return NULL;
    }
    
    newMsg->senderToken = msg->senderToken;
    newMsg->sysMsgId    = msg->sysMsgId;
    newMsg->info        = CMSG_IS_GET_RESPONSE | CMSG_IS_NULL_GET_RESPONSE;
    
    return (void *)newMsg;
}


/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the cMsg major version number of a message.
 *
 * @param vmsg pointer to message
 * @param version integer pointer to be filled in with cMsg major version
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetVersion(const void *vmsg, int *version) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *version = msg->version;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the "get response" field of a message. The "get
 * reponse" field indicates the message is a response to a message sent
 * by a sendAndGet call, if it has a value of 1. Any other value indicates
 * it is not a response to a sendAndGet.
 *
 * @param vmsg pointer to message
 * @param getResponse set to 1 if message is a response to a sendAndGet,
 *                    anything else otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message argument is NULL
 */   
int cMsgSetGetResponse(void *vmsg, int getResponse) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  msg->info = getResponse ? msg->info |  CMSG_IS_GET_RESPONSE :
                            msg->info & ~CMSG_IS_GET_RESPONSE;

  return(CMSG_OK);
}

/**
 * This routine gets the "get response" field of a message. The "get
 * reponse" field indicates the message is a response to a message sent
 * by a sendAndGet call, if it has a value of 1. A value of 0 indicates
 * it is not a response to a sendAndGet.
 *
 * @param vmsg pointer to message
 * @param getResponse integer pointer to be filled in 1 if message
 *                    is a response to a sendAndGet and 0 otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetGetResponse(const void *vmsg, int *getResponse) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *getResponse = (msg->info & CMSG_IS_GET_RESPONSE) == CMSG_IS_GET_RESPONSE ? 1 : 0;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the "null get response" field of a message. If it
 * has a value of 1, the "null get response" field indicates that if the
 * message is a response to a message sent by a sendAndGet call, when sent
 * it will be received as a NULL pointer - not a message. Any other value
 * indicates it is not a null get response to a sendAndGet.
 *
 * @param vmsg pointer to message
 * @param nullGetResponse set to 1 if message is a null get response to a
 *                        sendAndGet, anything else otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message argument is NULL
 */   
int cMsgSetNullGetResponse(void *vmsg, int nullGetResponse) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  msg->info = nullGetResponse ? msg->info |  CMSG_IS_NULL_GET_RESPONSE :
                                msg->info & ~CMSG_IS_NULL_GET_RESPONSE;

  return(CMSG_OK);
}

/**
 * This routine gets the "NULL get response" field of a message. If it
 * has a value of 1, the "NULL get response" field indicates that if the
 * message is a response to a message sent by a sendAndGet call, when sent
 * it will be received as a NULL pointer - not a message. Any other value
 * indicates it is not a null get response to a sendAndGet.
 *
 * @param vmsg pointer to message
 * @param nullGetResponse integer pointer to be filled in with 1 if message
 *                        is a NULL response to a sendAndGet and 0 otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetNullGetResponse(const void *vmsg, int *nullGetResponse) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *nullGetResponse = (msg->info & CMSG_IS_NULL_GET_RESPONSE) == CMSG_IS_NULL_GET_RESPONSE ? 1 : 0;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the "get request" field of a message. The "get
 * request" field indicates the message was sent by a sendAndGet call,
 * if it has a value of 1. A value of 0 indicates it was not sent by
 * a sendAndGet.
 *
 * @param vmsg pointer to message
 * @param getRequest integer pointer to be filled in with 1 if message
 *                   sent by a sendAndGet and 0 otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetGetRequest(const void *vmsg, int *getRequest) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *getRequest = (msg->info & CMSG_IS_GET_REQUEST) == CMSG_IS_GET_REQUEST ? 1 : 0;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the domain of a message. When a message is newly
 * created (eg. by cMsgCreateMessage()), the domain field of a message
 * is not set. In the cMsg domain, the cMsg server sets this field
 * when it receives a client's sent message.
 * Messages received from the server will have this field set.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param domain pointer to pointer which gets filled with a message's 
 *               cMsg domain
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetDomain(const void *vmsg, char **domain) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->domain == NULL) {
    *domain = NULL;
  }
  else {
    *domain = (char *) (strdup(msg->domain));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the creator of a message. When a newly created
 * message is sent, on the server it's creator field is set to the sender.
 * Once set, this value never changes. On the client, this field never gets
 * set. Messages received from the server will have this field set.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param creator pointer to pointer which gets filled with a message's 
 *               creator
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetCreator(const void *vmsg, char **creator) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->creator == NULL) {
    *creator = NULL;
  }
  else {
    *creator = (char *) (strdup(msg->creator));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the subject of a message.
 *
 * @param vmsg pointer to message
 * @param subject message subject
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetSubject(void *vmsg, const char *subject) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->subject != NULL) free(msg->subject);  
  if (subject == NULL) {
    msg->subject = NULL;    
  }
  else {
    msg->subject = (char *)strdup(subject);
  }

  return(CMSG_OK);
}

/**
 * This routine gets the subject of a message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param subject pointer to pointer which gets filled with a message's 
 *                subject
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetSubject(const void *vmsg, char **subject) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->subject == NULL) {
    *subject = NULL;
  }
  else {
    *subject = (char *) (strdup(msg->subject));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the type of a message.
 *
 * @param vmsg pointer to message
 * @param type message type
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetType(void *vmsg, const char *type) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->type != NULL) free(msg->type);
  if (type == NULL) {
    msg->type = NULL;    
  }
  else {
    msg->type = (char *)strdup(type);
  }

  return(CMSG_OK);
}

/**
 * This routine gets the type of a message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param type pointer to pointer which gets filled with a message's type
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetType(const void *vmsg, char **type) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->type == NULL) {
    *type = NULL;
  }
  else {
    *type = (char *) (strdup(msg->type));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the text of a message.
 *
 * @param vmsg pointer to message
 * @param text message text
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetText(void *vmsg, const char *text) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->text != NULL) free(msg->text);
  if (text == NULL) {
    msg->text = NULL;    
  }
  else {
    msg->text = (char *)strdup(text);
  }

  return(CMSG_OK);
}

/**
 * This routine gets the text of a message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param text pointer to pointer which gets filled with a message's text
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetText(const void *vmsg, char **text) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->text == NULL) {
    *text = NULL;
  }
  else {
    *text = (char *) (strdup(msg->text));
  }
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets a message's user-defined integer.
 *
 * @param vmsg pointer to message
 * @param userInt message's user-defined integer
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetUserInt(void *vmsg, int userInt) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  msg->userInt = userInt;

  return(CMSG_OK);
}

/**
 * This routine gets a message's user-defined integer.
 *
 * @param vmsg pointer to message
 * @param userInt integer pointer to be filled with message's user-defined integer
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetUserInt(const void *vmsg, int *userInt) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *userInt = msg->userInt;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets a message's user-defined time (in seconds since
 * midnight GMT, Jan 1st, 1970).
 *
 * @param vmsg pointer to message
 * @param userTime message's user-defined time
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetUserTime(void *vmsg, const struct timespec *userTime) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  msg->userTime = *userTime;

  return(CMSG_OK);
}

/**
 * This routine gets a message's user-defined time (in seconds since
 * midnight GMT, Jan 1st, 1970).
 *
 * @param vmsg pointer to message
 * @param userTime time_t pointer to be filled with message's user-defined time
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetUserTime(const void *vmsg, struct timespec *userTime) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *userTime = msg->userTime;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the length of a message's byte array.
 *
 * @param vmsg pointer to message
 * @param length byte array's length
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL or length is negative
 */   
int cMsgSetByteArrayLength(void *vmsg, int length) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (length < 0)  return(CMSG_BAD_ARGUMENT);
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  msg->byteArrayLength = length;

  return(CMSG_OK);
}

/**
 * This routine gets the length of a message's byte array.
 *
 * @param vmsg pointer to message
 * @param length int pointer to be filled with byte array length
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetByteArrayLength(const void *vmsg, int *length) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *length = msg->byteArrayLength;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the offset of a message's byte array.
 *
 * @param vmsg pointer to message
 * @param offset byte array's offset
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetByteArrayOffset(void *vmsg, int offset) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  msg->byteArrayOffset = offset;

  return(CMSG_OK);
}

/**
 * This routine gets the offset of a message's byte array.
 *
 * @param vmsg pointer to message
 * @param offset int pointer to be filled with byte array offset
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetByteArrayOffset(const void *vmsg, int *offset) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *offset = msg->byteArrayOffset;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the endianness of the byte array data.
 * @param vmsg pointer to message
 * @param endian byte array's endianness
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if local endianness is unknown
 * @returns CMSG_BAD_ARGUMENT if message is NULL, or endian is not equal to either
 *                            CMSG_ENDIAN_BIG,   CMSG_ENDIAN_LITTLE,
 *                            CMSG_ENDIAN_LOCAL, CMSG_ENDIAN_NOTLOCAL, or
 *                            CMSG_ENDIAN_SWITCH 
 */   
int cMsgSetByteArrayEndian(void *vmsg, int endian) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  int ndian;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
    
  if ((endian != CMSG_ENDIAN_BIG)   && (endian != CMSG_ENDIAN_LITTLE)   &&
      (endian != CMSG_ENDIAN_LOCAL) && (endian != CMSG_ENDIAN_NOTLOCAL) &&
      (endian != CMSG_ENDIAN_SWITCH)) {
      return(CMSG_BAD_ARGUMENT);
  }
  
  /* set to local endian value */
  if (endian == CMSG_ENDIAN_LOCAL) {
      if (cMsgLocalByteOrder(&ndian) != CMSG_OK) {
          return CMSG_ERROR;
      }
      if (ndian == CMSG_ENDIAN_BIG) {
          msg->info |= CMSG_IS_BIG_ENDIAN;
      }
      else {
          msg->info &= ~CMSG_IS_BIG_ENDIAN;
      }
  }
  /* set to opposite of local endian value */
  else if (endian == CMSG_ENDIAN_NOTLOCAL) {
      if (cMsgLocalByteOrder(&ndian) != CMSG_OK) {
          return CMSG_ERROR;
      }
      if (ndian == CMSG_ENDIAN_BIG) {
          msg->info &= ~CMSG_IS_BIG_ENDIAN;
      }
      else {
          msg->info |= CMSG_IS_BIG_ENDIAN;
      }
  }
  /* switch endian value from big to little or vice versa */
  else if (endian == CMSG_ENDIAN_SWITCH) {
      /* if big switch to little */
      if ((msg->info & CMSG_IS_BIG_ENDIAN) > 1) {
          msg->info &= ~CMSG_IS_BIG_ENDIAN;
      }
      /* else switch to big */
      else {
          msg->info |= CMSG_IS_BIG_ENDIAN;
      }
  }
  /* set to big endian */
  else if (endian == CMSG_ENDIAN_BIG) {
      msg->info |= CMSG_IS_BIG_ENDIAN;
  }
  /* set to little endian */
  else if (endian == CMSG_ENDIAN_LITTLE) {
      msg->info &= ~CMSG_IS_BIG_ENDIAN;
  }

  return(CMSG_OK);
}

/**
 * This routine gets the endianness of the byte array data.
 *
 * @param vmsg pointer to message
 * @param endian int pointer to be filled with byte array data endianness
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetByteArrayEndian(const void *vmsg, int *endian) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  if ((msg->info & CMSG_IS_BIG_ENDIAN) > 1) {
      *endian = CMSG_ENDIAN_BIG;
  }
  else {
      *endian = CMSG_ENDIAN_LITTLE;
  }

  return (CMSG_OK);
}

/**
 * This method specifies whether the endian value of the byte array is
 * the same value as the local host. If not, a 1 is returned indicating
 * that the data needs to be swapped. If so, a 0 is returned indicating
 * that no swap is needed.
 *
 * @param swap int pointer to be filled with 1 if byte array needs swapping, else 0
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_ERROR if local endianness is unknown
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */
int cMsgNeedToSwap(const void *vmsg, int *swap) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  int localEndian, msgEndian;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  /* find local host's byte order */ 
  if (cMsgLocalByteOrder(&localEndian) != CMSG_OK) return CMSG_ERROR;
  
  /* find messge byte array's byte order */
  if ((msg->info & CMSG_IS_BIG_ENDIAN) > 1) {
      msgEndian = CMSG_ENDIAN_BIG;
  }
  else {
      msgEndian = CMSG_ENDIAN_LITTLE;
  }
  
  if (localEndian == msgEndian) {
      *swap = 0;
  }
  else {
      *swap = 1;
  }
  
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets a message's byte array by setting the pointer
 * and NOT copying the data.
 *
 * @param vmsg pointer to message
 * @param array byte array
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetByteArray(void *vmsg, char *array) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  msg->bits &= ~CMSG_BYTE_ARRAY_IS_COPIED; /* byte array is NOT copied */
  msg->byteArray = array;

  return(CMSG_OK);
}


/**
 * This routine sets a message's byte array by setting the pointer
 * and NOT copying the data. It also sets the offset index into and
 * length of the array.
 *
 * @param vmsg pointer to message
 * @param array byte array
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL or length is negative
 */   
int cMsgSetByteArrayAndLimits(void *vmsg, char *array, int offset, int length) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (length < 0)  return(CMSG_BAD_ARGUMENT);
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  msg->bits &= ~CMSG_BYTE_ARRAY_IS_COPIED; /* byte array is NOT copied */
  msg->byteArray       = array;
  msg->byteArrayOffset = offset;
  msg->byteArrayLength = length;

  return(CMSG_OK);
}


/**
 * This routine sets a message's byte array by copying the data
 * into a newly allocated array using the given offset and length
 * values. No existing byte array memory is freed. The offset is
 * reset to zero while length is set to the given value.
 *
 * @param vmsg pointer to message
 * @param array byte array
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL or length is negative
 */   
int cMsgCopyByteArray(void *vmsg, char *array, int offset, int length) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (length < 0)  return(CMSG_BAD_ARGUMENT);
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  msg->byteArray = (char *) malloc(msg->byteArrayLength);
  if (msg->byteArray == NULL) {
    return (CMSG_OUT_OF_MEMORY);
  }

  memcpy(msg->byteArray, &(array[offset]), (size_t) length);
  msg->bits |= CMSG_BYTE_ARRAY_IS_COPIED; /* byte array IS copied */
  msg->byteArrayOffset = 0;
  msg->byteArrayLength = length;

  return(CMSG_OK);
}


/**
 * This routine gets a message's byte array.
 *
 * @param vmsg pointer to message
 * @param array pointer to be filled with byte array
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetByteArray(const void *vmsg, char **array) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *array = msg->byteArray;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the sender of a message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param sender pointer to pointer which gets filled with a message's 
 *               sender
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetSender(const void *vmsg, char **sender) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->sender == NULL) {
    *sender = NULL;
  }
  else {
    *sender = (char *) (strdup(msg->sender));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the host of the sender of a message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param senderHost pointer to pointer which gets filled with the host of
 *                   the sender of a message
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetSenderHost(const void *vmsg, char **senderHost) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->senderHost == NULL) {
    *senderHost = NULL;
  }
  else {
    *senderHost = (char *) (strdup(msg->senderHost));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the time a message was last sent (in seconds since
 * midnight GMT, Jan 1st, 1970).
 *
 * @param vmsg pointer to message
 * @param senderTime pointer to be filled with time message was last sent
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetSenderTime(const void *vmsg, struct timespec *senderTime) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *senderTime = msg->senderTime;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the receiver of a message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param receiver pointer to pointer which gets filled with a message's 
 *                 receiver
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetReceiver(const void *vmsg, char **receiver) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->receiver == NULL) {
    *receiver = NULL;
  }
  else {
    *receiver = (char *) (strdup(msg->receiver));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the host of the receiver of a message. This field
 * is NULL for a newly created message.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param receiverHost pointer to pointer which gets filled with the host of
 *                     the receiver of a message
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetReceiverHost(const void *vmsg, char **receiverHost) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->receiverHost == NULL) {
    *receiverHost = NULL;
  }
  else {
    *receiverHost = (char *) (strdup(msg->receiverHost));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine gets the time a message was received (in seconds since
 * midnight GMT, Jan 1st, 1970).
 *
 * @param vmsg pointer to message
 * @param receiverTime pointer to be filled with time message was received
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetReceiverTime(const void *vmsg, struct timespec *receiverTime) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *receiverTime = msg->receiverTime;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/


/**
 * This routine converts the message to a printable string.
 *
 * @param vmsg pointer to message
 * @param string is pointer to char* that will hold the malloc'd string
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgToString(const void *vmsg, char **string) {

  char *format =
    "<cMsgMessage date=\"%s\"\n"
    "     version              = \"%d\"\n"
    "     domain               = \"%s\"\n"
    "     getRequest           = \"%s\"\n"
    "     getResponse          = \"%s\"\n"
    "     nullGetResponse      = \"%s\"\n"
    "     creator              = \"%s\"\n"
    "     sender               = \"%s\"\n"
    "     senderHost           = \"%s\"\n"
    "     senderTime           = \"%s\"\n"
    "     userInt              = \"%d\"\n"
    "     userTime             = \"%s\"\n"
    "     receiver             = \"%s\"\n"
    "     receiverHost         = \"%s\"\n"
    "     receiverTime         = \"%s\"\n"
    "     subject              = \"%s\"\n"
    "     type                 = \"%s\"\n"
    "<![CDATA[\n"
    "%s\n]]>\n"
    "</cMsgMessage>\n\n";
  int formatLen = strlen(format);

  char *buffer;
  int slen;
  time_t now;
  char nowBuf[32],userTimeBuf[32],senderTimeBuf[32],receiverTimeBuf[32];
#ifdef VXWORKS
  size_t len=sizeof(nowBuf);
#endif

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);


  /* get times in ascii and remove newlines */
  now=time(NULL);
#ifdef VXWORKS
  ctime_r(&now,nowBuf,&len);                                nowBuf[strlen(nowBuf)-1]='\0';
  ctime_r(&msg->senderTime.tv_sec,senderTimeBuf,&len);      senderTimeBuf[strlen(senderTimeBuf)-1]='\0';
  ctime_r(&msg->receiverTime.tv_sec,receiverTimeBuf,&len);  receiverTimeBuf[strlen(receiverTimeBuf)-1]='\0';
  ctime_r(&msg->userTime.tv_sec,userTimeBuf,&len);          userTimeBuf[strlen(userTimeBuf)-1]='\0';
#else
  ctime_r(&now,nowBuf);                               nowBuf[strlen(nowBuf)-1]='\0';
  ctime_r(&msg->senderTime.tv_sec,senderTimeBuf);     senderTimeBuf[strlen(senderTimeBuf)-1]='\0';
  ctime_r(&msg->receiverTime.tv_sec,receiverTimeBuf); receiverTimeBuf[strlen(receiverTimeBuf)-1]='\0';
  ctime_r(&msg->userTime.tv_sec,userTimeBuf);         userTimeBuf[strlen(userTimeBuf)-1]='\0';
#endif

  /* get string len */
  slen=formatLen;
  if(msg->domain!=NULL)        slen+=strlen(msg->domain);
  if(msg->creator!=NULL)       slen+=strlen(msg->creator);
  if(msg->sender!=NULL)        slen+=strlen(msg->sender);
  if(msg->senderHost!=NULL)    slen+=strlen(msg->senderHost);
  if(msg->receiver!=NULL)      slen+=strlen(msg->receiver);
  if(msg->receiverHost!=NULL)  slen+=strlen(msg->receiverHost);
  if(msg->subject!=NULL)       slen+=strlen(msg->subject);
  if(msg->type!=NULL)          slen+=strlen(msg->type);
  if(msg->text!=NULL)          slen+=strlen(msg->text);
  slen+=1024;   /* to account for everything else */


  /* allocate and fill buffer */
  buffer=(char*)malloc(slen);
  sprintf(buffer,format,
          nowBuf,msg->version,msg->domain,
          ((msg->info & CMSG_IS_GET_REQUEST)!=0)?"true":"false",
          ((msg->info & CMSG_IS_GET_RESPONSE)!=0)?"true":"false",
          ((msg->info & CMSG_IS_NULL_GET_RESPONSE)!=0)?"true":"false",
          msg->creator,msg->sender,msg->senderHost,senderTimeBuf,
          msg->userInt,userTimeBuf,
          msg->receiver,msg->receiverHost,receiverTimeBuf,
          msg->subject,msg->type,msg->text);


  /* hand newly allocated buffer off to user */
  *string=buffer;

  return (CMSG_OK);
}


/*-------------------------------------------------------------------*/
/*   message context accessor functions                              */
/*-------------------------------------------------------------------*/


/**
 * This routine gets the domain a subscription is running in and is valid
 * only when used in a callback on the message given in the callback
 * argument.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param domain pointer to pointer which gets filled with a subscription's domain
 *                or NULL if no information is available
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message or message context is NULL
 */   
int cMsgGetSubscriptionDomain(const void *vmsg, char **domain) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->context.domain == NULL) {
    *domain = NULL;
  }
  else {
    *domain = (char *) (strdup(msg->context.domain));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/

/**
 * This routine gets the subject a subscription is using and is valid
 * only when used in a callback on the message given in the callback
 * argument.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param subject pointer to pointer which gets filled with a subscription's subject
 *                or NULL if no information is available
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetSubscriptionSubject(const void *vmsg, char **subject) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->context.subject == NULL) {
    *subject = NULL;
  }
  else {
    *subject = (char *) (strdup(msg->context.subject));
  }
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/

/**
 * This routine gets the type a subscription is using and is valid
 * only when used in a callback on the message given in the callback
 * argument.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param type pointer to pointer which gets filled with a subscription's type
 *                or NULL if no information is available
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message or message context is NULL
 */   
int cMsgGetSubscriptionType(const void *vmsg, char **type) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->context.type == NULL) {
    *type = NULL;
  }
  else {
    *type = (char *) (strdup(msg->context.type));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/

/**
 * This routine gets the udl of a subscription's connection and is valid
 * only when used in a callback on the message given in the callback
 * argument.
 * If succesful, this routine will have memory allocated and assigned to
 * the dereferenced char ** argument. This memory must be freed eventually.
 *
 * @param vmsg pointer to message
 * @param udl pointer to pointer which gets filled with the udl of a subscription's
 *            connection or NULL if no information is available
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message or message context is NULL
 */   
int cMsgGetSubscriptionUDL(const void *vmsg, char **udl) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->context.udl == NULL) {
    *udl = NULL;
  }
  else {
    *udl = (char *) (strdup(msg->context.udl));
  }
  return(CMSG_OK);
}


/*-------------------------------------------------------------------*/

/**
 * This routine gets the cue size of a callback and is valid
 * only when used in a callback on the message given in the callback
 * argument.
 *
 * @param vmsg pointer to message
 * @param size pointer which gets filled with a callback's cue size
 *             or -1 if no information is available
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message or message context is NULL
 */   
int cMsgGetSubscriptionCueSize(const void *vmsg, int *size) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;
  
  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  if (msg->context.cueSize == NULL) {
    *size = -1;
  }
  else {
    *size = (*(msg->context.cueSize));
  }
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/

/**
 * This routine sets whether the send will be reliable (default, TCP)
 * or will be allowed to be unreliable (UDP).
 *
 * @param vmsg pointer to message
 * @param boolean 0 if false (use UDP), anything else true (use TCP)
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgSetReliableSend(void *vmsg, int boolean) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  
  msg->context.udpSend = boolean == 0 ? 1 : 0;

  return(CMSG_OK);
}

/**
 * This routine gets whether the send will be reliable (default, TCP)
 * or will be allowed to be unreliable (UDP).
 *
 * @param vmsg pointer to message
 * @param boolean int pointer to be filled with 1 if true (TCP), else
 *                0 (UDP)
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if message is NULL
 */   
int cMsgGetReliableSend(void *vmsg, int *boolean) {

  cMsgMessage_t *msg = (cMsgMessage_t *)vmsg;

  if (msg == NULL) return(CMSG_BAD_ARGUMENT);
  *boolean = msg->context.udpSend == 1 ? 0 : 1;
  return (CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*   subscribe config functions                                      */
/*-------------------------------------------------------------------*/


/**
 * This routine creates a structure of configuration information used
 * to determine the behavior of a cMsgSubscribe()'s callback. The
 * configuration is filled with default values. Each aspect of the
 * configuration may be modified by setter and getter functions. The
 * defaults are:
 * - maximum messages to cue for callback is 10000
 * - no messages may be skipped
 * - calls to the callback function must be serialized
 * - may skip up to 2000 messages at once if skipping is enabled
 * - maximum number of threads when parallelizing calls to the callback
 *   function is 100
 * - enough supplemental threads are started so that there are
 *   at most 150 unprocessed messages for each thread
 *
 * Note that this routine allocates memory and cMsgSubscribeConfigDestroy()
 * must be called to free it.
 *
 * @returns NULL if no memory available
 * @returns pointer to configuration if successful
 */   
cMsgSubscribeConfig *cMsgSubscribeConfigCreate(void) {
  subscribeConfig *sc;
  
  sc = (subscribeConfig *) malloc(sizeof(subscribeConfig));
  if (sc == NULL) {
    return NULL;
  }
  
  /* default configuration for a subscription */
  sc->maxCueSize = 10000;  /* maximum number of messages to cue for callback */
  sc->skipSize   =  2000;  /* number of messages to skip over (delete) from the cue
                            * for a callback when the cue size has reached it limit */
  sc->maySkip        = 0;  /* may NOT skip messages if too many are piling up in cue */
  sc->mustSerialize  = 1;  /* messages must be processed in order */
  sc->init           = 1;  /* done intializing structure */
  sc->maxThreads   = 100;  /* max number of supplemental threads to run callback
                            * if mustSerialize = 0 */
  sc->msgsPerThread = 150; /* enough supplemental threads are started so that there are
                            * at most this many unprocessed messages for each thread */

  return (cMsgSubscribeConfig*) sc;

}


/*-------------------------------------------------------------------*/



/**
 * This routine frees the memory associated with a configuration
 * created by cMsgSubscribeConfigCreate();
 * 
 * @param config pointer to configuration
 *
 * @returns CMSG_OK
 */   
int cMsgSubscribeConfigDestroy(cMsgSubscribeConfig *config) {
  if (config != NULL) {
    free((subscribeConfig *) config);
  }
  return CMSG_OK;
}


/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets a subscribe configuration's maximum message cue
 * size. Messages are kept in the cue until they can be processed by
 * the callback function.
 *
 * @param config pointer to configuration
 * @param size maximum cue size
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL or size < 1
 */   
int cMsgSubscribeSetMaxCueSize(cMsgSubscribeConfig *config, int size) {
  subscribeConfig *sc = (subscribeConfig *) config;
  
  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  }
   
  if (config == NULL || size < 1)  {
    return CMSG_BAD_ARGUMENT;
  }
  
  sc->maxCueSize = size;
  return CMSG_OK;
}


/**
 * This routine gets a subscribe configuration's maximum message cue
 * size. Messages are kept in the cue until they can be processed by
 * the callback function.
 *
 * @param config pointer to configuration
 * @param size integer pointer to be filled with configuration's maximum
 *             cue size
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetMaxCueSize(cMsgSubscribeConfig *config, int *size) {
  subscribeConfig *sc = (subscribeConfig *) config;   
  
  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *size = sc->maxCueSize;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the number of messages to skip over (delete) if too
 * many messages are piling up in the cue. Messages are only skipped if
 * cMsgSubscribeSetMaySkip() sets the configuration to do so.
 *
 * @param config pointer to configuration
 * @param size number of messages to skip (delete)
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL or size < 0
 */   
int cMsgSubscribeSetSkipSize(cMsgSubscribeConfig *config, int size) {
  subscribeConfig *sc = (subscribeConfig *) config;
  
  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  }
   
  if (config == NULL || size < 0)  {
    return CMSG_BAD_ARGUMENT;
  }
  
  sc->skipSize = size;
  return CMSG_OK;
}

/**
 * This routine gets the number of messages to skip over (delete) if too
 * many messages are piling up in the cue. Messages are only skipped if
 * cMsgSubscribeSetMaySkip() sets the configuration to do so.
 *
 * @param config pointer to configuration
 * @param size integer pointer to be filled with the number of messages
 *             to skip (delete)
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetSkipSize(cMsgSubscribeConfig *config, int *size) {
  subscribeConfig *sc = (subscribeConfig *) config;    

  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *size = sc->skipSize;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets whether messages may be skipped over (deleted)
 * if too many messages are piling up in the cue. The maximum number
 * of messages skipped at once is determined by cMsgSubscribeSetSkipSize().
 *
 * @param config pointer to configuration
 * @param maySkip set to 0 if messages may NOT be skipped, set to anything
 *                else otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeSetMaySkip(cMsgSubscribeConfig *config, int maySkip) {
  subscribeConfig *sc = (subscribeConfig *) config;

  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  } 
    
  if (config == NULL)  {
    return CMSG_BAD_ARGUMENT;
  }
    
  sc->maySkip = maySkip;
  return CMSG_OK;
}

/**
 * This routine gets whether messages may be skipped over (deleted)
 * if too many messages are piling up in the cue. The maximum number
 * of messages skipped at once is determined by cMsgSubscribeSetSkipSize().
 *
 * @param config pointer to configuration
 * @param maySkip integer pointer to be filled with 0 if messages may NOT
 *                be skipped (deleted), or anything else otherwise
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetMaySkip(cMsgSubscribeConfig *config, int *maySkip) {
  subscribeConfig *sc = (subscribeConfig *) config;    

  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *maySkip = sc->maySkip;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets whether a subscribe's callback must be run serially
 * (in one thread), or may be parallelized (run simultaneously in more
 * than one thread) if more than 1 message is waiting in the cue.
 *
 * @param config pointer to configuration
 * @param serialize set to 0 if callback may be parallelized, or set to
 *                  anything else if callback must be serialized
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeSetMustSerialize(cMsgSubscribeConfig *config, int serialize) {
  subscribeConfig *sc = (subscribeConfig *) config;

  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  }   
    
  if (config == NULL)  {
    return CMSG_BAD_ARGUMENT;
  }
    
  sc->mustSerialize = serialize;
  return CMSG_OK;
}

/**
 * This routine gets whether a subscribe's callback must be run serially
 * (in one thread), or may be parallelized (run simultaneously in more
 * than one thread) if more than 1 message is waiting in the cue.
 *
 * @param config pointer to configuration
 * @param serialize integer pointer to be filled with 0 if callback may be
 *                  parallelized, or anything else if callback must be serialized
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetMustSerialize(cMsgSubscribeConfig *config, int *serialize) {
  subscribeConfig *sc = (subscribeConfig *) config;

  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *serialize = sc->mustSerialize;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the maximum number of threads a parallelized
 * subscribe's callback can run at once. This setting is only used if
 * cMsgSubscribeSetMustSerialize() was called with an argument of 0.
 *
 * @param config pointer to configuration
 * @param threads the maximum number of threads a parallelized
 *                subscribe's callback can run at once
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL or threads < 0
 */   
int cMsgSubscribeSetMaxThreads(cMsgSubscribeConfig *config, int threads) {
  subscribeConfig *sc = (subscribeConfig *) config;
  
  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  }
   
  if (config == NULL || threads < 0)  {
    return CMSG_BAD_ARGUMENT;
  }
  
  sc->maxThreads = threads;
  return CMSG_OK;
}

/**
 * This routine gets the maximum number of threads a parallelized
 * subscribe's callback can run at once. This setting is only used if
 * cMsgSubscribeSetMustSerialize() was called with an argument of 0.
 *
 * @param config pointer to configuration
 * @param threads integer pointer to be filled with the maximum number
 *                of threads a parallelized subscribe's callback can run at once
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetMaxThreads(cMsgSubscribeConfig *config, int *threads) {
  subscribeConfig *sc = (subscribeConfig *) config;    

  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *threads = sc->maxThreads;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the maximum number of unprocessed messages per thread
 * before a new thread is started, if a callback is parallelized 
 * (cMsgSubscribeSetMustSerialize() set to 0).
 *
 * @param config pointer to configuration
 * @param mpt set to maximum number of unprocessed messages per thread
 *            before starting another thread
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL or mpt < 1
 */   
int cMsgSubscribeSetMessagesPerThread(cMsgSubscribeConfig *config, int mpt) {
  subscribeConfig *sc = (subscribeConfig *) config;
  
  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  }
   
  if (config == NULL || mpt < 1)  {
    return CMSG_BAD_ARGUMENT;
  }
  
  sc->msgsPerThread = mpt;
  return CMSG_OK;
}

/**
 * This routine gets the maximum number of unprocessed messages per thread
 * before a new thread is started, if a callback is parallelized 
 * (cMsgSubscribeSetMustSerialize() set to 0).
 *
 * @param config pointer to configuration
 * @param mpt integer pointer to be filled with the maximum number of
 *            unprocessed messages per thread before starting another thread
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetMessagesPerThread(cMsgSubscribeConfig *config, int *mpt) {
  subscribeConfig *sc = (subscribeConfig *) config;    

  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *mpt = sc->msgsPerThread;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
/*-------------------------------------------------------------------*/

/**
 * This routine sets the stack size in bytes of the subscription thread.
 * By default the stack size is unspecified. 
 *
 * @param config pointer to configuration
 * @param size stack size in bytes of subscription thread
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_NOT_INITIALIZED if configuration was not initialized
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL or size < 1 byte
 */   
int cMsgSubscribeSetStackSize(cMsgSubscribeConfig *config, size_t size) {
  subscribeConfig *sc = (subscribeConfig *) config;
  
  if (sc->init != 1) {
    return CMSG_NOT_INITIALIZED;
  }
   
  if (config == NULL || size < 1)  {
    return CMSG_BAD_ARGUMENT;
  }
  
  sc->stackSize = size;
  return CMSG_OK;
}

/**
 * This routine gets the stack size in bytes of the subscription thread.
 * By default the stack size is unspecified (returns 0).
 *
 * @param config pointer to configuration
 * @param mpt integer pointer to be filled with the maximum number of
 *            unprocessed messages per thread before starting another thread
 *
 * @returns CMSG_OK if successful
 * @returns CMSG_BAD_ARGUMENT if configuration is NULL
 */   
int cMsgSubscribeGetStackSize(cMsgSubscribeConfig *config, size_t *size) {
  subscribeConfig *sc = (subscribeConfig *) config;    

  if (config == NULL) return(CMSG_BAD_ARGUMENT);
  *size = sc->stackSize;
  return(CMSG_OK);
}

/*-------------------------------------------------------------------*/
