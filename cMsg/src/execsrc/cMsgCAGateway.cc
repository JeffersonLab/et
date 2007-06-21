// to do:
//    aitString not working ???



//  cMsgCAGateway

// Portable ca server serves cMsg data as CA channels

// XML config file lists subject/type/channel correspondance
// Can also send "config" type message to name, text is XML config info

// Multiple channels may correspond to the same message, 
//   and multiple messages may correspond to the same channel

// cMsg message text field contains value



/*----------------------------------------------------------------------------*
*  Copyright (c) 2005        Southeastern Universities Research Association, *
*                            Thomas Jefferson National Accelerator Facility  *
*                                                                            *
*    This software was developed under a United States Government license    *
*    described in the NOTICE file included as part of this distribution.     *
*                                                                            *
*    E.Wolin, 18-Aug-2005, Jefferson Lab                                     *
*                                                                            *
*    Authors: Elliott Wolin                                                  *
*             wolin@jlab.org                    Jefferson Lab, MS-6B         *
*             Phone: (757) 269-7365             12000 Jefferson Ave.         *
*             Fax:   (757) 269-5519             Newport News, VA 23606       *
*
*----------------------------------------------------------------------------*/


using namespace std;


// system
#include <string>
#include <map>
#include <iostream>
#include <iomanip>
#include <signal.h>


// for cMsg
#include <cMsg.hxx>
using namespace cmsg;


// for ca 
#include <casdef.h>
#include <fdManager.h>
#include <gdd.h>


//  for xml
#include <expat.h>


// epics server class defs
#include <epics_server.h>


// for cMsg
cMsg *cMsgSys               = NULL;
static string udl           = "cMsg://ollie:3456/cMsg";
static string name          = "cMsgCAGateway";
static string descr         = "cMsg CA Gateway utility";
static string cfgType       = "config";
static string cfgFile;


// misc variables
static int pendTime          = 1;
static bool done             = false;
static int debug             = 0;


// PV map
static map<const string, myPV*> pvMap;


// prototypes
void decode_command_line(int argc, char **argv);
void create_signal_handler(void);
void quit_callback(int sig);
void parseXMLFile(string f);
void parseXMLString(string s);
void startElement(void *userData, const char *xmlname, const char **atts);


//--------------------------------------------------------------------------
//--------------------------------------------------------------------------


// cMsg callback class
class myCallbackObject:public cMsgCallback {
  
  void callback(cMsgMessage *msg, void* userObject) {


    // NULL is for new xml config, otherwise PV update
    if(userObject==NULL) {

      // parse xml config string
      parseXMLString(msg->getText());

    } else {

      // fill pv value from text field
      myPV *p = (myPV*)userObject;

      switch (p->myType) {
      case aitEnumFloat64:
      case aitEnumFloat32:
        p->fillPV(atof(msg->getText().c_str()));
        break;

      case aitEnumInt32:
      case aitEnumUint32:
      case aitEnumInt16:
      case aitEnumUint16:
      case aitEnumInt8:
      case aitEnumUint8:
        p->fillPV(atoi(msg->getText().c_str()));
        break;

      case aitEnumFixedString:
      case aitEnumString:
        p->fillPVString(aitString(msg->getText().c_str()));
        break;
      }

    }

    delete(msg);
  }
};


//-----------------------------------------------------------------------------
//--------------------------------------------------------------------------


// CA server class
class myServer : public caServer {
  

public:
  
  pvExistReturn pvExistTest(const casCtx &ctx, const char *pPVName) {
    
    string s(pPVName,0,strcspn(pPVName,"."));
    if(pvMap.count(s)<=0) {
      return pverDoesNotExistHere;
    } else {
      return pverExistsHere;
    }
  }
  
  
//---------------------------------------------------
  
  
  pvCreateReturn createPV(const casCtx &ctx, const char *pPVName) {
    
    int PVNameLen      = strlen(pPVName);
    int PVLen          = strcspn(pPVName,".");
    const char *pattr  = pPVName+PVLen+1;
    int lattr          = PVNameLen-PVLen-1;

    string pvName(pPVName,0,PVLen);
    map<const string, myPV*>::iterator iter = pvMap.find(pvName);
    if(iter!=pvMap.end()) {
      if( (PVNameLen==PVLen) || ((lattr==3)&&(strncasecmp(pattr,"VAL",3)==0)) ) {
        return(*(iter->second));
      } else {
        return(*(new myAttrPV(iter->second,pattr,lattr)));
      }
    }
    return(S_casApp_pvNotFound);
  }
  
    
//---------------------------------------------------
    
    
  ~myServer() {
    if(debug==0)cout << "myServer destructor" << endl;
    return; 
    }
};


//---------------------------------------------------------------------------
//---------------------------------------------------------------------------


main(int argc,char **argv) {


  // decode command line
  decode_command_line(argc,argv);


  // create signal handler
  create_signal_handler();


  // connect to cMsg system
  cMsgSys = new cMsg(udl,name,descr);
  cMsgSys->connect();


  //  subscribe to name/cfgType and set callback for on-the-fly XML configuration
  cMsgSys->subscribe(name,cfgType,new myCallbackObject(),(void *)0);


  // set epics server debug flag
  setDebug(debug);


  // create ca server
  myServer *cas = new myServer();


  // read config file, create PV's and subscribe to corresponding subjects
  if(cfgFile.size()>0) parseXMLFile(cfgFile);
  

  // start processing messages
  cMsgSys->start();


  // CA server loop
  while(!done) {

    fileDescriptorManager.process((double)pendTime);

    // check for PV updates and satisfy monitorOn requests
    map<const string, myPV*>::iterator iter;
    for(iter=pvMap.begin(); iter!=pvMap.end(); iter++) {
      myPV *pPV = iter->second;
      if((pPV->myMonitor!=0)&&(pPV->myUpdate!=0)) {
	if(debug!=0)cout << "(monitorOn response for " << iter->first << ")" << endl;
	gdd *value = new gdd();
	caServer *pCAS = pPV->getCAS();
	casEventMask select(pCAS->valueEventMask | pCAS->logEventMask);
	pPV->getVAL(*value);
	pPV->postEvent(select,*value);
	pPV->myUpdate=0;
      }
    }
  }


  // done...clean up
  cMsgSys->disconnect();
  cout << endl << " *** cMsgCAGateway done ***" << endl << endl;
  exit(EXIT_SUCCESS);
}
       

//--------------------------------------------------------------------------


void create_signal_handler(void) {

  signal(SIGTERM,quit_callback);
  signal(SIGQUIT,quit_callback);
  signal( SIGINT,quit_callback);
  signal( SIGHUP,quit_callback);
}


//--------------------------------------------------------------------------


void quit_callback(int sig) {
  done=true;
}


//--------------------------------------------------------------------------


void parseXMLFile(string file) {

  int status,len;
  char buf[1024];

  FILE *f = fopen(file.c_str(),"r");
  if(f!=NULL) {

    XML_Parser xmlParser = XML_ParserCreate(NULL);
    XML_SetElementHandler(xmlParser,startElement,NULL);
    
    do {
      len=fread(buf,1,1024,f);
      status=XML_Parse(xmlParser,buf,len,len!=0);
      if(status==0) {
        cerr << "?cMsgCAGateway...parseXMLFile parse error for " << file << endl << endl;
        exit(EXIT_FAILURE);
      }
    } while (len!=0);
    fclose(f);

  } else {
    cerr << "?cMsgCAGateway...parseXMLFile unable to open " << file << endl << endl;
    exit(EXIT_FAILURE);
  }
    
}


//--------------------------------------------------------------------------


void parseXMLString(string s) {

  XML_Parser xmlParser = XML_ParserCreate(NULL);
  XML_SetElementHandler(xmlParser,startElement,NULL);
    
  int len=s.size();
  int status=XML_Parse(xmlParser,s.c_str(),len,1);
  if(status==0) {
    cerr << "?cMsgCAGateway...parseXMLString parse error for:" << endl << s << endl;
  }
}
       

//--------------------------------------------------------------------------


void startElement(void *userData, const char *xmlname, const char **atts) {

  string pvName,pvSubject,pvType;

  aitEnum pvCAType = aitEnumFloat64;   // default
  string pvUnits="";
  int alrm=0,prec=0;
  double hihi=0.,lolo=0.,high=0.,low=0.,hopr=0.,lopr=0.,drvh=0.,drvl=0.;
  string val = "0";


  // only parse pv definitions
  if(strcasecmp(xmlname,"pv")==0) {

    for (int i = 0; atts[i]; i+=2) {
      if(strcasecmp(atts[i],"name")==0) {
        pvName=atts[i+1];

      } else if(strcasecmp(atts[i],"subject")==0) {
        pvSubject=atts[i+1];

      } else if(strcasecmp(atts[i],"type")==0) {
        pvType=atts[i+1];

      } else if(strcasecmp(atts[i],"CAType")==0) {
        if(strcasecmp(atts[i+1],"int32")==0) {
          pvCAType=aitEnumInt32;
        } else if(strcasecmp(atts[i+1],"uint32")==0) {
          pvCAType=aitEnumUint32;
        } else if(strcasecmp(atts[i+1],"int16")==0) {
          pvCAType=aitEnumInt16;
        } else if(strcasecmp(atts[i+1],"uint16")==0) {
          pvCAType=aitEnumUint16;
        } else if(strcasecmp(atts[i+1],"int8")==0) {
          pvCAType=aitEnumInt8;
        } else if(strcasecmp(atts[i+1],"uint8")==0) {
          pvCAType=aitEnumUint8;
        } else if(strcasecmp(atts[i+1],"float32")==0) {
          pvCAType=aitEnumFloat32;
        } else if(strcasecmp(atts[i+1],"float64")==0) {
          pvCAType=aitEnumFloat64;
        } else if(strcasecmp(atts[i+1],"string")==0) {
             cerr << "?cMsgCAGateway...string not supported for pvName " << pvName 
                  << ", defaulting to float64" << endl;
//           pvCAType=aitEnumString;
        } else if(strcasecmp(atts[i+1],"fixedString")==0) {
             cerr << "?cMsgCAGateway...fixedString not supported for pvName " << pvName 
                  << ", defaulting to float64" << endl;
//           pvCAType=aitEnumFixedString;
        }

      } else if(strcasecmp(atts[i],"units")==0) {
        pvUnits=atts[i+1];

      } else if(strcasecmp(atts[i],"alrm")==0) {
        alrm=atoi(atts[i+1]);

      } else if(strcasecmp(atts[i],"val")==0) {
        val=atts[i+1];

      } else if(strcasecmp(atts[i],"hihi")==0) {
        hihi=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"lolo")==0) {
        lolo=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"high")==0) {
        high=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"low")==0) {
        low=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"hopr")==0) {
        hopr=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"lopr")==0) {
        lopr=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"drvh")==0) {
        drvh=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"drvl")==0) {
        drvl=atof(atts[i+1]);

      } else if(strcasecmp(atts[i],"prec")==0) {
        prec=atoi(atts[i+1]);
      }
    }


    // create new pv if needed
    if(pvMap.count(pvName)<=0) {
      myPV *p = new myPV(pvName.c_str(),pvCAType,pvUnits.c_str(),
                         alrm,hihi,lolo,high,low,hopr,lopr,drvh,drvl,prec);

      switch (pvCAType) {
      case aitEnumFloat64:
      case aitEnumFloat32:
        p->fillPV(atof(val.c_str()));
        break;

      case aitEnumInt32:
      case aitEnumUint32:
      case aitEnumInt16:
      case aitEnumUint16:
      case aitEnumInt8:
      case aitEnumUint8:
        p->fillPV(atoi(val.c_str()));
        break;
      case aitEnumFixedString:
      case aitEnumString:
        p->fillPVString(aitString(val.c_str()));
        break;
      }

      pvMap[pvName] = p;
    }
    

    // subscribe
    cMsgSys->subscribe(pvSubject,pvType,new myCallbackObject(),(void *)pvMap[pvName]);
    
  }

  return;
}


//--------------------------------------------------------------------------


void decode_command_line(int argc, char**argv) {

  const char *help = "\nusage:\n\n cMsgCAGateway [-name name] [-udl udl] [-descr description]\n"
    "              [-cfgType cfgType] [-cfgFile cfgFile] [-pend pendTime] [-debug]\n";


  // loop over all arguments, except the 1st (which is program name)
  int i=1;
  while(i<argc) {
    if(strncasecmp(argv[i],"-h",2)==0) {
      cout << help << endl;
      exit(EXIT_SUCCESS);
    }
    else if (strncasecmp(argv[i],"-debug",6)==0) {
      debug=1;
      i=i+1;
    }
    else if (strncasecmp(argv[i],"-name",5)==0) {
      name=argv[i+1];
      i=i+2;
    }
    else if (strncasecmp(argv[i],"-udl",4)==0) {
      udl=argv[i+1];
      i=i+2;
    }
    else if (strncasecmp(argv[i],"-descr",6)==0) {
      descr=argv[i+1];
      i=i+2;
    }
    else if (strncasecmp(argv[i],"-cfgType",8)==0) {
      cfgType=argv[i+1];
      i=i+2;
    }
    else if (strncasecmp(argv[i],"-cfgFile",8)==0) {
      cfgFile=argv[i+1];
      i=i+2;
    }
    else if (strncasecmp(argv[i],"-pend",5)==0) {
      pendTime=atoi(argv[i+1]);
      i=i+2;
    }
    else if (strncasecmp(argv[i],"-",1)==0) {
      cout << "Unknown command line arg: " << argv[i] << argv[i+1] << endl << endl;
      i=i+1;
    }
  }

  return;
}

  
//----------------------------------------------------------------
