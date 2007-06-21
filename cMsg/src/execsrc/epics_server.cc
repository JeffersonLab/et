//  epics_server.cc

// pv classes for creating a PCAS


// notes:

//   PCAS probably not thread safe so need myUpdate flags

//   creates attrPV's (on the fly) to handle r/w to (case insensitive) fields:
//       HIHI,HIGH,LOLO,LOW,HOPR,LOPR,DRVH,DRVL,ALRM,STAT,SEVR,PREC
//       (n.b. VAL maps to regular data pv)

//   write only allowed to following fields:
//       ALRM,HIHI,LOLO,HIGH,LOW,HOPR,LOPR,DRVH,DRVL

//   all alarm limits are double, set ALRM to 0(1) to turn alarms off(on)

//   have to set EPICS_CAS_INTF_ADDR_LIST to limit server to single network interface



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


// misc
#include <string>
#include <iostream>
#include <iomanip>
#include <time.h>


// for ca
#include <casdef.h>
#include <alarm.h>
#include <gdd.h>


// for epics_server
#include <epics_server.h>


//  misc variables
static int debug = 0;
static char temp[4096];


//--------------------------------------------------------------------------
//--------------------------------------------------------------------------


//  channel objects for setting read/write access for all data channels
class myChannel : public casChannel {

public:

  myChannel(const casCtx &ctxIn) : casChannel(ctxIn) {
  }


//---------------------------------------------------


  aitBool readAccess() const {
    return aitTrue;
  }


//---------------------------------------------------


  aitBool writeAccess() const {
    return aitFalse;
  }

};


//---------------------------------------------------------------------------
//---------------------------------------------------------------------------


  myPV::myPV(const char *name, aitEnum type, const char *units, int alarm, double hihi, double lolo, 
	     double high, double low, double hopr, double lopr, double drvh, double drvl, int prec) {

    myName=strdup(name);
    myType=type;
    myUnits=strdup(units);
    myAlarm=alarm;
    myHIHI=hihi;
    myLOLO=lolo;
    myHIGH=high;
    myLOW=low;
    myHOPR=hopr;
    myLOPR=lopr;
    myDRVH=drvh;
    myDRVL=drvl;

    myValue = new gddScalar(gddAppType_value,type);
    myValue->putConvert(0);

    myTime=0;
    myStat=epicsAlarmNone;
    mySevr=epicsSevNone;
    myMonitor=0;
    myUpdate=0;

    initFT();
    if(debug!=0)cout << "myPV constructor for " << myName << endl;
  }
  

//---------------------------------------------------


    void myPV::setAlarm() {

      // alarm checking done using double since limits are all doubles

        aitFloat64 dval;
	int oldStat = myStat;
	int oldSevr = mySevr;
	
        myValue->getConvert(dval);
	if(myAlarm!=0) {
	    if(dval>=myHIHI) {
		myStat=epicsAlarmHiHi;
		mySevr=epicsSevMajor;
	    } else if (dval<=myLOLO) {
		myStat=epicsAlarmLoLo;
		mySevr=epicsSevMajor;
	    } else if (dval>=myHIGH) {
		myStat=epicsAlarmHigh;
		mySevr=epicsSevMinor;
	    } else if (dval<=myLOW) {
		myStat=epicsAlarmLow;
		mySevr=epicsSevMinor;
	    } else {
		myStat=epicsAlarmNone;
		mySevr=epicsSevNone;
	    }

	} else {
	    myStat=epicsAlarmNone;
	    mySevr=epicsSevNone;
	}
	

	// force update if alarm state changed
	if((oldStat!=myStat)||(oldSevr!=mySevr))myUpdate=1;
    }


//------------------------------------------------------------------


  casChannel *myPV::createChannel(const casCtx &ctx,
			    const char * const pUserName, 
			    const char * const pHostName) {
    return new myChannel(ctx);
  }


//---------------------------------------------------


  caStatus myPV::read(const casCtx &ctx, gdd &prototype) {
    return(ft.read(*this, prototype));
}


//---------------------------------------------------


  void myPV::initFT() {
  
    if(ft_is_initialized!=0)return;
    ft_is_initialized=1;

    ft.installReadFunc("value",    	    &myPV::getVAL);
    ft.installReadFunc("status",   	    &myPV::getSTAT);
    ft.installReadFunc("severity", 	    &myPV::getSEVR);
    ft.installReadFunc("graphicHigh", 	    &myPV::getHOPR);
    ft.installReadFunc("graphicLow",  	    &myPV::getLOPR);
    ft.installReadFunc("controlHigh", 	    &myPV::getDRVH);
    ft.installReadFunc("controlLow",  	    &myPV::getDRVL);
    ft.installReadFunc("alarmHigh",   	    &myPV::getHIHI);
    ft.installReadFunc("alarmLow",    	    &myPV::getLOLO);
    ft.installReadFunc("alarmHighWarning",  &myPV::getHIGH);
    ft.installReadFunc("alarmLowWarning",   &myPV::getLOW);
    ft.installReadFunc("units",       	    &myPV::getUNITS);
    ft.installReadFunc("precision",   	    &myPV::getPREC);
    ft.installReadFunc("enums",       	    &myPV::getENUM);
}


//--------------------------------------------------


  aitEnum myPV::bestExternalType() const {
    return(myType);
}


//---------------------------------------------------


  gddAppFuncTableStatus myPV::getUNITS(gdd &value) {
    if(debug!=0) cout << "...myPV getUNITS for " << myName << endl;
    value.putConvert(myUnits);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getVAL(gdd &value) {
    if(debug!=0) cout << "...myPV getVAL for " << myName << endl;

    value.copy(myValue);
    value.setStat(myStat);
    value.setSevr(mySevr);

    struct timespec t;
    t.tv_sec = myTime;
    t.tv_nsec=0;
    value.setTimeStamp(&t);

    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getSTAT(gdd &value) {
    if(debug!=0) cout << "...myPV getSTAT for " << myName << endl;
    value.putConvert(myStat);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getSEVR(gdd &value) {
    if(debug!=0) cout << "...myPV getSEVR for " << myName << endl;
    value.putConvert(mySevr);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getHIHI(gdd &value) {
    if(debug!=0) cout << "...myPV getHIHI for " << myName << endl;
    value.putConvert(myHIHI);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getLOLO(gdd &value) {
    if(debug!=0) cout << "...myPV getLOLO for " << myName << endl;
    value.putConvert(myLOLO);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getHOPR(gdd &value) {
    if(debug!=0) cout << "...myPV getHOPR for " << myName << endl;
    value.putConvert(myHOPR);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getLOPR(gdd &value) {
    if(debug!=0) cout << "...myPV getLOPR for " << myName << endl;
    value.putConvert(myLOPR);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getDRVH(gdd &value) {
    if(debug!=0) cout << "...myPV getDRVH for " << myName << endl;
    value.putConvert(myDRVH);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getDRVL(gdd &value) {
    if(debug!=0) cout << "...myPV getDRVL for " << myName << endl;
    value.putConvert(myDRVL);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getHIGH(gdd &value) {
    if(debug!=0) cout << "...myPV getHIGH for " << myName << endl;
    value.putConvert(myHIGH);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getLOW(gdd &value) {
    if(debug!=0) cout << "...myPV getLOW for " << myName << endl;
    value.putConvert(myLOW);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getPREC(gdd &value) {
    if(debug!=0) cout << "...myPV getPREC for " << myName << endl;
    value.putConvert(myPREC);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  gddAppFuncTableStatus myPV::getENUM(gdd &value) {
    if(debug!=0) cout << "...myPV getNUM for " << myName << endl;
    value.putConvert(0);
    return S_casApp_success;
  }
  

//---------------------------------------------------


  caStatus myPV::interestRegister() {
    if(debug!=0) cout << "...myPV interestRegister for " << myName << endl;
    myMonitor=1;
    return S_casApp_success;
  }


//---------------------------------------------------


  void myPV::interestDelete() {
    if(debug!=0) cout << "...myPV interestDelete for " << myName << endl;
    myMonitor=0;
  }


//---------------------------------------------------


  epicsShareFunc const char *myPV::getName() const {
    return(myName);
  }
  

//---------------------------------------------------


  void myPV::destroy() {
    if(debug!=0)cout << "myPV destroy for " << myName << endl;
  }
    

//---------------------------------------------------


  myPV::~myPV() {
    if(debug!=0)cout << "myPV destructor for " << myName << endl;
  }


//---------------------------------------------------------------------------


  void myPV::fillPVString(aitString newVal) {
    
    return;  // ???

    aitString oldVal; 
    myValue->getConvert(oldVal);
    
    if(strcasecmp(newVal,oldVal)!=0) {
      myUpdate=1;
      myValue->putConvert(newVal);
      myTime=time(NULL)-epicsToLocalTime;
    }

    // no alarm on string
  }


//---------------------------------------------------------------------------
//---------------------------------------------------------------------------


//  channel objects for setting read/write access for all attribute channels
class myAttrChannel : public casChannel {

public:

  myAttrChannel(const casCtx &ctxIn) : casChannel(ctxIn) {
  }


//---------------------------------------------------


  aitBool readAccess() const {
    return aitTrue;
  }


//---------------------------------------------------


  aitBool writeAccess() const {
    return aitTrue;
  }

};


//---------------------------------------------------------------------------
//---------------------------------------------------------------------------


  myAttrPV::myAttrPV(myPV *ptr, const char* attr, int len) {
      myPtr=ptr;
      strncpy(myAttr,"    ",4);
      strncpy(myAttr,attr,(len<4)?len:4);
      if(debug!=0)cout << "myAttrPV constructor for " << myPtr->myName 
		       << ", attr: " << myAttr << endl;
  }
  

//---------------------------------------------------


  casChannel *myAttrPV::createChannel(const casCtx &ctx,
				      const char * const pUserName, 
				      const char * const pHostName) {
    return new myAttrChannel(ctx);
  }


//---------------------------------------------------


  aitEnum myAttrPV::bestExternalType() const {
    if( (strncasecmp(myAttr,"ALRM",4)==0) || (strncasecmp(myAttr,"STAT",4)==0) ||
        (strncasecmp(myAttr,"SEVR",4)==0) || (strncasecmp(myAttr,"PREC",4)==0) ) {
      return(aitEnumInt32);
    } else {
      return(aitEnumFloat64);
    }
}


//---------------------------------------------------


  caStatus myAttrPV::read(const casCtx &ctx, gdd &value) {
      if(debug!=0)cout << "...myAttrPV read for " << myPtr->myName << ", attr: " << myAttr << endl;


    if(strncasecmp(myAttr,"ALRM",4)==0) {
      value.putConvert(myPtr->myAlarm);
      return(S_casApp_success);

    } else if (strncasecmp(myAttr,"HIHI",4)==0) {
      value.putConvert(myPtr->myHIHI);
      return(S_casApp_success);

    } else if (strncasecmp(myAttr,"LOLO",4)==0) {
      value.putConvert(myPtr->myLOLO);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"HIGH",4)==0) {
      value.putConvert(myPtr->myHIGH);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"LOW ",4)==0) {
      value.putConvert(myPtr->myLOW);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"HOPR",4)==0) {
      value.putConvert(myPtr->myHOPR);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"LOPR",4)==0) {
      value.putConvert(myPtr->myLOPR);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"DRVH",4)==0) {
      value.putConvert(myPtr->myDRVH);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"DRVL",4)==0) {
      value.putConvert(myPtr->myDRVL);
      return(S_casApp_success);
      
    } else if (strncasecmp(myAttr,"STAT",4)==0) {
      value.putConvert(myPtr->myStat);
      return(S_casApp_success);

    } else if (strncasecmp(myAttr,"SEVR",4)==0) {
      value.putConvert(myPtr->mySevr);
      return(S_casApp_success);

    } else if (strncasecmp(myAttr,"PREC",4)==0) {
      value.putConvert(myPtr->myPREC);
      return(S_casApp_success);

    } else {
      cerr << "No read support for attribute: \"" << myAttr << "\"" << endl;
      return(S_casApp_noSupport);
    }

  }
  

//---------------------------------------------------


  caStatus myAttrPV::write(const casCtx &ctx, gdd &value) {
    if(debug!=0)cout << "...myAttrPV write for " << myPtr->myName 
		     << ",  attr: " << myAttr << ",  value: " << (int)value << endl;


    if(strncasecmp(myAttr,"ALRM",4)==0) {
	myPtr->myAlarm=(int)value;

    } else if (strncasecmp(myAttr,"HIHI",4)==0) {
	myPtr->myHIHI=(double)value;

    } else if (strncasecmp(myAttr,"LOLO",4)==0) {
	myPtr->myLOLO=(double)value;
      
    } else if (strncasecmp(myAttr,"HIGH",4)==0) {
	myPtr->myHIGH=(double)value;
      
    } else if (strncasecmp(myAttr,"LOW ",4)==0) {
	myPtr->myLOW=(double)value;

    } else if (strncasecmp(myAttr,"HOPR",4)==0) {
	myPtr->myHOPR=(double)value;

    } else if (strncasecmp(myAttr,"LOPR",4)==0) {
	myPtr->myLOPR=(double)value;

    } else if (strncasecmp(myAttr,"DRVH",4)==0) {
	myPtr->myDRVH=(double)value;

    } else if (strncasecmp(myAttr,"DRVL",4)==0) {
	myPtr->myDRVL=(double)value;

    } else if (strncasecmp(myAttr,"PREC",4)==0) {
	myPtr->myPREC=(int)value;

    } else {
	cerr << "No write support for attr: \"" << myAttr << "\"" << endl;
	return S_casApp_noSupport;
    }

    myPtr->setAlarm();

    return S_casApp_success;
  }
  

//---------------------------------------------------


  epicsShareFunc const char *myAttrPV::getName() const {
    sprintf(temp,"%s.%4s",myPtr->myName,myAttr);
    return(temp);
  }


//---------------------------------------------------


  myAttrPV::~myAttrPV() {
    if(debug!=0)cout << "myAttrPV destructor" << endl;
  }


//---------------------------------------------------------------------------
//---------------------------------------------------------------------------
//---------------------------------------------------------------------------
//---------------------------------------------------------------------------



//  must declare all static vars outside of class definition to allocate storage
int myPV::ft_is_initialized=0;
gddAppFuncTable<myPV> myPV::ft;


//---------------------------------------------------------------------------


void setDebug(int val) {
  debug=val;
}


//---------------------------------------------------------------------------
