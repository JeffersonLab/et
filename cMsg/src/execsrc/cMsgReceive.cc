//  cMsgReceive.cc
//
//  receives cMsg message based on command line params
//
//  E.Wolin, 28-apr-2005



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


// subscription parameters
static string subject;
static string type;


// misc
bool silent;


// prototypes
void decodeCommandLine(int argc, char **argv);



//-----------------------------------------------------------------------------


// callback class
class myCallbackObject : public cMsgCallback {

  void callback(cMsgMessage *msg, void* userObject) {
     if(!silent) {
         cout << "subject is:            " << msg->getSubject() << endl;
         cout << "type is:               " << msg->getType() << endl;
         cout << "userInt is:            " << msg->getUserInt() << endl;
         cout << "text is:               " << msg->getText() << endl;
         cout << "byte array length is:  " << msg->getByteArrayLength() << endl;
         cout << endl;
     }
     delete(msg);
  }
};


//-----------------------------------------------------------------------------


int main(int argc, char **argv) {


  // set defaults
  udl           = "cMsg://ollie:3456/cMsg/vmeTest";       // universal domain locator
  name          = "cMsgReceive";                          // unique name
  description   = "cMsgReceiveutility ";                  // description is arbitrary
  subject       = "*";
  type          = "*";
  silent        = false;


  // decode command line parameters
  decodeCommandLine(argc,argv);


  // connect to cMsg server
  cMsg c(udl,name,description);
  c.connect();
  

  //  subscribe and start dispatching to callback
  try {
    myCallbackObject *cbo = new myCallbackObject();
    void *handle = c.subscribe(subject,type,cbo,NULL);
    //    c.unsubscribe((void*)((int)handle+1));
    //    c.unsubscribe(handle);
    //    c.subscribe(subject,type,cbo,NULL);
    c.start();
  } catch (cMsgException e) {
    cerr << e.toString();
    exit(EXIT_FAILURE);
  }

  // wait forever for messages
  while(true) {
    sleep(1);
  }
  
  return(EXIT_SUCCESS);

}


//-----------------------------------------------------------------------------


void decodeCommandLine(int argc, char **argv) {
  

  const char *help = 
    "\nusage:\n\n   cMsgReceive [-udl udl] [-n name] [-d description] [-s subject] [-t type] [-silent]\n\n";
  
  

  // loop over arguments
  int i=1;
  while (i<argc) {
    if (strncasecmp(argv[i],"-h",2)==0) {
      cout << help << endl;
      exit(EXIT_SUCCESS);

    } else if (strncasecmp(argv[i],"-silent",7)==0) {
      silent=true;
      i=i+1;

    } else if (strncasecmp(argv[i],"-t",2)==0) {
      type=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-udl",4)==0) {
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
    }
  }

}


//-----------------------------------------------------------------------------
