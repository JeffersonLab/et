//  cMsgCommand.cc
//
//  sends cMsg message based on command line params
//
//  E.Wolin, 27-apr-2005



// system includes
using namespace std;
#include <iostream>
#include <unistd.h>


// for cMsg
#include <cMsg.hxx>

using namespace cmsg;


// connection parameters
static string udl;
static string name;
static string description;


// message parameters
static string subject;
static string type;
static string text;
static int userInt;


// misc parameters
static int sleepTime = 10000;  // units are micro-sec


// prototypes
void decodeCommandLine(int argc, char **argv);



//-----------------------------------------------------------------------------


main(int argc, char **argv) {


  // set defaults
  udl           = "cMsg://ollie:3456/cMsg/vmeTest";       // universal domain locator
  name          = "cMsgCommand";                          // unique name
  description   = "cMsgCommand utility";                  // description is arbitrary
  subject       = "mySubject";
  type          = "myType";
  userInt       = 0;
  text          = "hello world";


  // decode command line parameters
  decodeCommandLine(argc,argv);


  // connect to cMsg server
  cMsg c(udl,name,description);

  try {
    c.connect();
    
    
  // create and fill message
    cMsgMessage m;
    m.setSubject(subject);
    m.setType(type);
    m.setUserInt(userInt);
    m.setText(text);
    
    
    // send message
    try {
      c.send(m);
    } catch (cMsgException e) {
      cerr << endl << "  ?unable to send message" << endl << endl;
    }
    
    
    // allow some time for message to be transferred before disconnecting
    usleep(sleepTime);
    

  } catch (cMsgException e) {
    cerr << endl << e.toString() << endl << endl;
    exit(EXIT_FAILURE);
  }
  
  
  // done
  c.disconnect();
}


//-----------------------------------------------------------------------------


void decodeCommandLine(int argc, char **argv) {
  

  const char *help = 
    "\nusage:\n\n   cMsgCommand [-u udl] [-n name] [-d description] [-sleep sleepTime]\n"
    "              [-s subject] [-type type] [-i userInt] [-text text]\n\n";
  
  

  // loop over arguments
  int i=1;
  while (i<argc) {
    if (strncasecmp(argv[i],"-h",2)==0) {
      cout << help << endl;
      exit(EXIT_SUCCESS);

    } else if (strncasecmp(argv[i],"-type",5)==0) {
      type=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-text",5)==0) {
      text=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-sleep",6)==0) {
      sleepTime=atoi(argv[i+1]);
      i=i+2;

    } else if (strncasecmp(argv[i],"-u",2)==0) {
      udl=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-n",2)==0) {
      name=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-d",2)==0) {
      description=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-s",2)==0) {
      subject=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-i",2)==0) {
      userInt=atoi(argv[i+1]);
      i=i+2;
    }
  }

}


//-----------------------------------------------------------------------------
