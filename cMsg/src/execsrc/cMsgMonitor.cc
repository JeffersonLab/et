//  cMsgMonitor.cc
//
//  Monitors cMsg system
//
//  E.Wolin, 17-may-2007



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
static int repeat_time = 3;


// prototypes
void decodeCommandLine(int argc, char **argv);



//-----------------------------------------------------------------------------


int main(int argc, char **argv) {


  // set defaults
  udl           = "cMsg://broadcast/cMsg";
  name          = "cMsgMonitor";
  description   = "cMsgMonitor Utility ";


  // decode command line parameters
  decodeCommandLine(argc,argv);


  // create cMsg object
  cMsg *c;
  try {
    c = new cMsg(udl,name,description);
  } catch (exception &e) {
    cerr << e.what() << endl;
    exit(EXIT_FAILURE);
  } catch (...) {
    cerr << "?unknown exception" << endl;
    exit(EXIT_FAILURE);
  }
  

  // connect and loop forever
  try {
    c->connect();

    while(true) {
      cMsgMessage *m = c->monitor("");
      cout << m->getText() << endl;
      cout << "----------------------------------------------------------" << endl << endl;
      delete(m);
      sleep(repeat_time);
    }
    
  } catch (exception &e) {
    cerr << e.what() << endl;
    exit(EXIT_FAILURE);
  } catch (...) {
    cerr << "?unknown exception" << endl;
    exit(EXIT_FAILURE);
  }
  
  exit(EXIT_SUCCESS);
}


//-----------------------------------------------------------------------------


void decodeCommandLine(int argc, char **argv) {
  

  const char *help = 
    "\nusage:\n\n   cMsgMonitor [-udl udl] [-n name] [-d description] [-r repeat_time]\n\n";
  
  

  // loop over arguments
  int i=1;
  while (i<argc) {
    if (strncasecmp(argv[i],"-h",2)==0) {
      cout << help << endl;
      exit(EXIT_SUCCESS);

    } else if (strncasecmp(argv[i],"-udl",4)==0) {
      udl=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-n",2)==0) {
      name=argv[i+1];
      i=i+2;

    } else if (strncasecmp(argv[i],"-d",2)==0) {
      description=argv[i+1];
      i=i+2;
    } else if (strncasecmp(argv[i],"-r",2)==0) {
      repeat_time=atoi(argv[i+1]);
      i=i+2;
    }
  }

}


//-----------------------------------------------------------------------------
