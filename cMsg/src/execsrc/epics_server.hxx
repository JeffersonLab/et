//  epics_server.h

//  epics server class defs

//  E.Wolin, 3-Jul-03


#ifndef _epics_server_h
#define _epics_server_h


#include <gdd.h>
#include <gddApps.h>
#include <gddAppFuncTable.h>


static int epicsToLocalTime  = 20*(365*24*60*60) + 5*(24*60*60) - (60*60); //?daylight savings?


void setDebug(int val);


//-------------------------------------------------------------------------
//-------------------------------------------------------------------------


class myPV : public casPV {


private:
  static int ft_is_initialized;
  static gddAppFuncTable<myPV> ft;
  
  
public:
  char *myName;
  aitEnum myType;
  char *myUnits;
  int myAlarm;
  double myHIHI;
  double myLOLO;
  double myHIGH;
  double myLOW;
  double myHOPR;
  double myLOPR;
  double myDRVH;
  double myDRVL;
  int myPREC;

  gddScalar *myValue;

  time_t myTime;
  int myStat;
  int mySevr;
  int myMonitor;
  int myUpdate;

  
  myPV(const char *name, aitEnum type, const char *units, int alarm, double hihi, double lolo, 
       double high, double low, double hopr, double lopr, double drvh, double drvl, int prec);
  

  void setAlarm();
  casChannel *createChannel(const casCtx &ctx,const char * const pUserName, 
			    const char * const pHostName);
  caStatus read(const casCtx &ctx, gdd &prototype);
  static void initFT();
  aitEnum bestExternalType() const;
  gddAppFuncTableStatus getUNITS(gdd &value);
  gddAppFuncTableStatus getVAL(gdd &value);
  gddAppFuncTableStatus getSTAT(gdd &value);
  gddAppFuncTableStatus getSEVR(gdd &value);
  gddAppFuncTableStatus getHIHI(gdd &value);
  gddAppFuncTableStatus getLOLO(gdd &value);
  gddAppFuncTableStatus getHOPR(gdd &value);
  gddAppFuncTableStatus getLOPR(gdd &value);
  gddAppFuncTableStatus getDRVH(gdd &value);
  gddAppFuncTableStatus getDRVL(gdd &value);
  gddAppFuncTableStatus getHIGH(gdd &value);
  gddAppFuncTableStatus getLOW(gdd &value);
  gddAppFuncTableStatus getPREC(gdd &value);
  gddAppFuncTableStatus getENUM(gdd &value);
  caStatus interestRegister();
  void interestDelete();
  epicsShareFunc const char *getName() const;
  void destroy();
  ~myPV();


  // for filling pv's
  void fillPVString(aitString newVal);

  template<typename T>
    void fillPV(const T &newVal) {
    
    T oldVal; 
    myValue->getConvert(oldVal);
    
    if(newVal!=oldVal) {
      myUpdate=1;
      myValue->putConvert(newVal);
      myTime=time(NULL)-epicsToLocalTime;
    }
    setAlarm();
  }


};


//-------------------------------------------------------------------------
//-------------------------------------------------------------------------


class myAttrPV : public casPV {


 public:
    myPV *myPtr;
    char myAttr[4];
    
    myAttrPV(myPV *ptr, const char* attr, int len);
    casChannel *createChannel(const casCtx &ctx,const char * const pUserName, 
			      const char * const pHostName);
    aitEnum bestExternalType() const;
    caStatus read(const casCtx &ctx, gdd &value);
    caStatus write(const casCtx &ctx, gdd &value);
    epicsShareFunc const char *getName() const;
    ~myAttrPV();
    
  };
  

//-------------------------------------------------------------------------
//-------------------------------------------------------------------------


#endif /* _epics_server_h */
