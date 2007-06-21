/*
 *  evioUtil.cc
 *
 *   Author:  Elliott Wolin, JLab, 11-dec-2006
*/


#include "evioUtil.hxx"


#ifndef sun
#include <execinfo.h>
#include <sstream>
#include <cxxabi.h>
#endif


using namespace std;
using namespace evio;



//--------------------------------------------------------------
//-------------------- local utilities -------------------------
//--------------------------------------------------------------


namespace evio {

  /**
   * Only used internally, always returns true.
   * @return true Always!
   */
  static bool isTrue(const evioDOMNodeP pNode) {
    return(true);
  }
  
  
//--------------------------------------------------------------


  /** 
   * Returns stack trace.
   * @return String containing stack trace
   */
  string getStackTrace() {

#ifdef sun
    return("");

#else
    size_t dlen = 1024;
    char *dname = (char*)malloc(dlen);
    void *trace[1024];
    int status;


    // get trace messages
    int trace_size = backtrace(trace,1024);
    if(trace_size>1024)trace_size=1024;
    char **messages = backtrace_symbols(trace, trace_size);
    
    // demangle and create string
    stringstream ss;
    for(int i=0; i<trace_size; ++i) {
      
      // find first '(' and '+'
      char *ppar = strchr(messages[i],'(');
      char *pplus = strchr(messages[i],'+');
      if((ppar!=NULL)&&(pplus!=NULL)) {
        
        // replace '+' with nul, then get demangled name
        *pplus='\0';
        abi::__cxa_demangle(ppar+1,dname,&dlen,&status);
        
        // add to stringstream
        *(ppar+1)='\0';
        *pplus='+';
        ss << "   " << messages[i] << dname << pplus << endl;

      } else {
        ss << "   " << messages[i] << endl;
      }

    }
    ss << ends;
      
    free(dname);
    free(messages);
    return(ss.str());
#endif

  }

}  // namespace evio


//-----------------------------------------------------------------------
//-------------------------- evioException ------------------------------
//-----------------------------------------------------------------------


/**
 * Constructor.
 * @param typ User-defined exception type
 * @param txt Basic text
 * @param aux Auxiliary text
 */
evioException::evioException(int typ, const string &txt, const string &aux) 
  : type(typ), text(txt), auxText(aux), trace(getStackTrace()) {
}


//--------------------------------------------------------------


/**
 * Constructor.
 * @param typ Exception type user-defined
 * @param txt Basic exception text
 * @param file __FILE__
 * @param func __FUNCTION__ (not on sun)
 * @param line __LINE__
 */
evioException::evioException(int typ, const string &txt, const string &file, const string &func, int line) 
  : type(typ), text(txt), trace(getStackTrace()) {

  ostringstream oss;
  oss <<  "    evioException occured in file " << file << ", function " << func << ", line " << line << ends;
  auxText=oss.str();
}


//--------------------------------------------------------------


/**
 * Returns XML string listing exception object contents.
 * @return XML string listing contents
 */
string evioException::toString(void) const throw() {
  ostringstream oss;
  oss << "?evioException type = " << type << "    text = " << text << endl << endl << auxText;
  if(trace.size()>0) oss << endl << endl << endl << "Stack trace:" << endl << endl << trace << endl;
  return(oss.str());
}


//-----------------------------------------------------------------------


/**
 * Returns char * listing exception object contents.
 * @return char * listing contents
 */
const char *evioException::what(void) const throw() {
  return(toString().c_str());
}


//-----------------------------------------------------------------------
//------------------------- evioFileChannel -----------------------------
//-----------------------------------------------------------------------


/**
 * Constructor opens file for reading or writing.
 * @param f File name
 * @param m I/O mode, "r" or "w"
 * @param size Internal buffer size
 */
evioFileChannel::evioFileChannel(const string &f, const string &m, int size) throw(evioException) 
  : filename(f), mode(m), handle(0), bufSize(size) {

  // allocate buffer
  buf = new uint32_t[bufSize];
  if(buf==NULL)throw(evioException(0,"?evioFileChannel constructor...unable to allocate buffer",__FILE__,__FUNCTION__,__LINE__));
}


//-----------------------------------------------------------------------


/**
 * Destructor closes file, deletes internal buffer.
 */
evioFileChannel::~evioFileChannel(void) {
  if(handle!=0)close();
  if(buf!=NULL)delete(buf);
}


//-----------------------------------------------------------------------


/**
 * Opens channel for reading or writing.
 */
void evioFileChannel::open(void) throw(evioException) {

  if(buf==NULL)throw(evioException(0,"evioFileChannel::open...null buffer",__FILE__,__FUNCTION__,__LINE__));
  if(evOpen(const_cast<char*>(filename.c_str()),const_cast<char*>(mode.c_str()),&handle)<0)
    throw(evioException(0,"?evioFileChannel::open...unable to open file",__FILE__,__FUNCTION__,__LINE__));
  if(handle==0)throw(evioException(0,"?evioFileChannel::open...zero handle",__FILE__,__FUNCTION__,__LINE__));
}


//-----------------------------------------------------------------------


/**
 * Reads from file into internal buffer.
 * @return true if successful, false on EOF or other evRead error condition
 */
bool evioFileChannel::read(void) throw(evioException) {
  if(buf==NULL)throw(evioException(0,"evioFileChannel::read...null buffer",__FILE__,__FUNCTION__,__LINE__));
  if(handle==0)throw(evioException(0,"evioFileChannel::read...0 handle",__FILE__,__FUNCTION__,__LINE__));
  return(evRead(handle,&buf[0],bufSize)==0);
}


//-----------------------------------------------------------------------


/**
 * Writes to file from internal buffer.
 */
void evioFileChannel::write(void) throw(evioException) {
  if(buf==NULL)throw(evioException(0,"evioFileChannel::write...null buffer",__FILE__,__FUNCTION__,__LINE__));
  if(handle==0)throw(evioException(0,"evioFileChannel::write...0 handle",__FILE__,__FUNCTION__,__LINE__));
  if(evWrite(handle,buf)!=0) throw(evioException(0,"?evioFileChannel::write...unable to write",__FILE__,__FUNCTION__,__LINE__));
}


//-----------------------------------------------------------------------


/**
 * Writes to file from user-supplied buffer.
 * @param myBuf Buffer containing event
 */
void evioFileChannel::write(const uint32_t *myBuf) throw(evioException) {
  if(myBuf==NULL)throw(evioException(0,"evioFileChannel::write...null myBuf",__FILE__,__FUNCTION__,__LINE__));
  if(handle==0)throw(evioException(0,"evioFileChannel::write...0 handle",__FILE__,__FUNCTION__,__LINE__));
  if(evWrite(handle,myBuf)!=0) throw(evioException(0,"?evioFileChannel::write...unable to write from myBuf",__FILE__,__FUNCTION__,__LINE__));
}


//-----------------------------------------------------------------------


/**
 * Writes to file from internal buffer of another evioChannel object.
 * @param channel Channel object
 */
void evioFileChannel::write(const evioChannel &channel) throw(evioException) {
  if(handle==0)throw(evioException(0,"evioFileChannel::write...0 handle",__FILE__,__FUNCTION__,__LINE__));
  if(evWrite(handle,channel.getBuffer())!=0) throw(evioException(0,"?evioFileChannel::write...unable to write from channel",__FILE__,__FUNCTION__,__LINE__));
}


//-----------------------------------------------------------------------


/**
 * Writes from internal buffer of another evioChannel object.
 * @param channel Pointer to channel object
 */
void evioFileChannel::write(const evioChannel *channel) throw(evioException) {
  if(channel==NULL)throw(evioException(0,"evioFileChannel::write...null channel",__FILE__,__FUNCTION__,__LINE__));
  evioFileChannel::write(*channel);
}


//-----------------------------------------------------------------------


/**
 * Writes from contents of evioDOMTree object.
 * @param tree evioDOMTree containing event
 */
void evioFileChannel::write(const evioDOMTree &tree) throw(evioException) {
  if(handle==0)throw(evioException(0,"evioFileChannel::write...0 handle",__FILE__,__FUNCTION__,__LINE__));
  tree.toEVIOBuffer(buf,bufSize);
  evioFileChannel::write();
}


//-----------------------------------------------------------------------


/**
 * Writes from contents of evioDOMTree object.
 * @param tree Pointer to evioDOMTree containing event
 */
void evioFileChannel::write(const evioDOMTree *tree) throw(evioException) {
  if(tree==NULL)throw(evioException(0,"evioFileChannel::write...null tree",__FILE__,__FUNCTION__,__LINE__));
  evioFileChannel::write(*tree);
}


//-----------------------------------------------------------------------


/**
 * For setting evIoctl parameters.
 * @param request String containing evIoctl parameters
 * @param argp Additional evIoctl parameter
 */
void evioFileChannel::ioctl(const string &request, void *argp) throw(evioException) {
  if(handle==0)throw(evioException(0,"evioFileChannel::ioctl...0 handle",__FILE__,__FUNCTION__,__LINE__));
  if(evIoctl(handle,const_cast<char*>(request.c_str()),argp)!=0)
    throw(evioException(0,"?evioFileChannel::ioCtl...error return",__FILE__,__FUNCTION__,__LINE__));
}


//-----------------------------------------------------------------------


/**
 * Closes channel.
 */
void evioFileChannel::close(void) throw(evioException) {
  if(handle==0)throw(evioException(0,"evioFileChannel::close...0 handle",__FILE__,__FUNCTION__,__LINE__));
  evClose(handle);
  handle=0;
}


//-----------------------------------------------------------------------


/**
 * Returns channel file name.
 * @return String containing file name
 */
string evioFileChannel::getFileName(void) const {
  return(filename);
}


//-----------------------------------------------------------------------


/**
 * Returns channel I/O mode.
 * @return String containing I/O mode
 */
string evioFileChannel::getMode(void) const {
  return(mode);
}


//-----------------------------------------------------------------------


/**
 * Returns pointer to internal channel buffer.
 * @return Pointer to internal buffer
 */
const uint32_t *evioFileChannel::getBuffer(void) const throw(evioException) {
  if(buf==NULL)throw(evioException(0,"evioFileChannel::getbuffer...null buffer",__FILE__,__FUNCTION__,__LINE__));
  return(buf);
}


//-----------------------------------------------------------------------



/**
 * Returns internal channel buffer size.
 * @return Internal buffer size in 4-byte words
 */
int evioFileChannel::getBufSize(void) const {
  return(bufSize);
}


//-----------------------------------------------------------------------
//------------------------ evioStreamParser -----------------------------
//-----------------------------------------------------------------------


/**
 * Stream parses event in buffer.
 * @param buf Buffer containing event
 * @param handler evioStreamParserHandler object containing callbacks to handle container and leaf nodes
 * @param userArg Passed to handler callbacks
 * @return void* Return value from parseBank
 */
void *evioStreamParser::parse(const uint32_t *buf, 
                              evioStreamParserHandler &handler, void *userArg) throw(evioException) {
  
  if(buf==NULL)throw(evioException(0,"?evioStreamParser::parse...null buffer",__FILE__,__FUNCTION__,__LINE__));

  return((void*)parseBank(buf,BANK,0,handler,userArg));
}


//--------------------------------------------------------------


/**
 * Used internally to parse banks.
 * @param buf Buffer containing bank
 * @param bankType Bank type
 * @param depth Current depth in depth-first parse of event
 * @param handler evioStreamParserHandler object containing callbacks to handle container and leaf nodes
 * @param userArg Passed to handler callbacks
 * @return void* Used internally
 */
void *evioStreamParser::parseBank(const uint32_t *buf, int bankType, int depth, 
                                 evioStreamParserHandler &handler, void *userArg) throw(evioException) {

  int length,contentType,dataOffset,p,bankLen;
  uint16_t tag;
  uint8_t num;
  const uint32_t *data;
  uint32_t mask;

  void *newUserArg = userArg;


  /* get type-dependent info */
  switch(bankType) {

  case 0xe:
  case 0x10:
    length  	= buf[0]+1;
    tag     	= buf[1]>>16;
    contentType	= (buf[1]>>8)&0xff;
    num     	= buf[1]&0xff;
    dataOffset  = 2;
    break;

  case 0xd:
  case 0x20:
    length  	= (buf[0]&0xffff)+1;
    tag     	= buf[0]>>24;
    contentType = (buf[0]>>16)&0xff;
    num     	= 0;
    dataOffset  = 1;
    break;
    
  case 0xc:
  case 0x40:
    length  	= (buf[0]&0xffff)+1;
    tag     	= buf[0]>>20;
    contentType	= (buf[0]>>16)&0xf;
    num     	= 0;
    dataOffset  = 1;
    break;

  default:
    ostringstream ss;
    ss << hex << showbase << bankType << ends;
    throw(evioException(0,"?evioStreamParser::parseBank...illegal bank type: " + ss.str(),__FILE__,__FUNCTION__,__LINE__));
  }


  /* 
   * if a leaf node, call leaf handler.
   * if container node, call node handler and then parse contained banks.
   */
  switch (contentType) {

  case 0x0:
  case 0x1:
  case 0x2:
  case 0xb:
    // four-byte types
    handler.leafNodeHandler(length-dataOffset,tag,contentType,num,depth,&buf[dataOffset],userArg);
    break;

  case 0x3:
  case 0x6:
  case 0x7:
    // one-byte types
    handler.leafNodeHandler((length-dataOffset)*4,tag,contentType,num,depth,(int8_t*)(&buf[dataOffset]),userArg);
    break;

  case 0x4:
  case 0x5:
    // two-byte types
    handler.leafNodeHandler((length-dataOffset)*2,tag,contentType,num,depth,(int16_t*)(&buf[dataOffset]),userArg);
    break;

  case 0x8:
  case 0x9:
  case 0xa:
    // eight-byte types
    handler.leafNodeHandler((length-dataOffset)/2,tag,contentType,num,depth,(int64_t*)(&buf[dataOffset]),userArg);
    break;

  case 0xe:
  case 0x10:
  case 0xd:
  case 0x20:
  case 0xc:
  case 0x40:
    // container types
    newUserArg=handler.containerNodeHandler(length,tag,contentType,num,depth,userArg);


    // parse contained banks
    p       = 0;
    bankLen = length-dataOffset;
    data    = &buf[dataOffset];
    mask    = ((contentType==0xe)||(contentType==0x10))?0xffffffff:0xffff;

    depth++;
    while(p<bankLen) {
      parseBank(&data[p],contentType,depth,handler,newUserArg);
      p+=(data[p]&mask)+1;
    }
    depth--;

    break;


  default:
    ostringstream ss;
    ss << hex << showbase << contentType << ends;
    throw(evioException(0,"?evioStreamParser::parseBank...illegal content type: " + ss.str(),__FILE__,__FUNCTION__,__LINE__));
    break;

  }


  // new user arg is pointer to parent node
  return(newUserArg);
}


//-----------------------------------------------------------------------------
//---------------------------- evioDOMNode ------------------------------------
//-----------------------------------------------------------------------------


/** 
 * Container node constructor used internally.
 * @param par Parent node
 * @param tag Node tag
 * @param num Node num
 * @param contentType Container node content type
 */
evioDOMNode::evioDOMNode(evioDOMNodeP par, uint16_t tag, uint8_t num, int contentType) throw(evioException)
  : parent(par), parentTree(NULL), contentType(contentType), tag(tag), num(num)  {
}


//-----------------------------------------------------------------------------


/** 
 * Static factory method to create container node.
 * @param tag Node tag
 * @param num Node num
 * @param cType Container node content type
 * @return Pointer to new node
 */
evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, ContainerType cType) throw(evioException) {
  return(new evioDOMContainerNode(NULL,tag,num,cType));
}


//-----------------------------------------------------------------------------


/** 
 * Static factory method to create container node holding evioSerializable object.
 * @param tag Node tag
 * @param num Node num
 * @param o evioSerializable object
 * @param cType Container node content type
 * @return Pointer to new node
 */
evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, const evioSerializable &o, ContainerType cType) 
  throw(evioException) {
  evioDOMContainerNode *c = new evioDOMContainerNode(NULL,tag,num,cType);
  o.serialize(c);
  return(c);
}


//-----------------------------------------------------------------------------


/** 
 * Static factory method to create container node using C function to fill container.
 * @param tag Node tag
 * @param num Node num
 * @param f C function that fills container node
 * @param userArg User arg passed to C function
 * @param cType Container node content type
 * @return Pointer to new node
 */
evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, void (*f)(evioDOMNodeP c, void *userArg), 
                                            void *userArg, ContainerType cType) throw(evioException) {
  evioDOMContainerNode *c = new evioDOMContainerNode(NULL,tag,num,cType);
  f(c,userArg);
  return(c);
}


//-----------------------------------------------------------------------------


/** 
 * Destructor recursively deletes children if this is a container node.
 */
evioDOMNode::~evioDOMNode(void) {
  evioDOMContainerNode *c = dynamic_cast<evioDOMContainerNode*>(this);
  if(c!=NULL) {
    evioDOMNodeList::iterator iter;
    for(iter=c->childList.begin(); iter!=c->childList.end(); iter++) {
      delete(*iter);
    }
  }
}


//-----------------------------------------------------------------------------


/** 
 * Cleanly removes node from tree or node hierarchy.
 * @return Pointer to now liberated node
 */
evioDOMNodeP evioDOMNode::cut(void) throw(evioException) {
  if(parent!=NULL) {
    evioDOMContainerNode *par = static_cast<evioDOMContainerNode*>(parent);
    par->childList.remove(this);
    parent=NULL;
  } else if (parentTree!=NULL) {
    parentTree->root=NULL;
    parentTree=NULL;
  }
  return(this);
}


//-----------------------------------------------------------------------------


/** 
 * Cleanly removes node from tree or node hierarchy and recursively deletes node and its contents.
 */
void evioDOMNode::cutAndDelete(void) throw(evioException) {
  cut();
  delete(this);
}


//-----------------------------------------------------------------------------


/** 
 * Changes node parent.
 * @param newParent New parent
 * @return Pointer to now node just moved
 */
evioDOMNodeP evioDOMNode::move(evioDOMNodeP newParent) throw(evioException) {

  cut();

  evioDOMContainerNode *par = dynamic_cast<evioDOMContainerNode*>(newParent);
  if(par==NULL)throw(evioException(0,"?evioDOMNode::move...parent node not a container",__FILE__,__FUNCTION__,__LINE__));
  
  par->childList.push_back(this);
  parent=newParent;
  return(this);
}


//-----------------------------------------------------------------------------


/** 
 * Adds node to container node.
 * @param node Node to be added
 */
void evioDOMNode::addNode(evioDOMNodeP node) throw(evioException) {
  if(node==NULL)return;
  if(!isContainer())throw(evioException(0,"?evioDOMNode::addNode...not a container",__FILE__,__FUNCTION__,__LINE__));
  node->move(this);
}


//-----------------------------------------------------------------------------


/** 
 * Returns pointer to child list of container node.
 */
evioDOMNodeList *evioDOMNode::getChildList(void) throw(evioException) {
  if(!isContainer())return(NULL);
  evioDOMContainerNode *c = dynamic_cast<evioDOMContainerNode*>(this);
  return(&c->childList);
}


//-----------------------------------------------------------------------------


/** 
 * Adds node to container node.
 * @param node Node to be added
 * @return Reference to node to be added
 */
evioDOMNode& evioDOMNode::operator<<(evioDOMNodeP node) throw(evioException) {
  addNode(node);
  return(*this);
}


//-----------------------------------------------------------------------------


/** 
 * True if node tag equals value.
 * @param tg Value to compare to
 * @return true if tags agree
 */
bool evioDOMNode::operator==(uint16_t tg) const {
  return(this->tag==tg);
}


//-----------------------------------------------------------------------------


/** 
 * True if node tag does not equal value
 * @param tg Value to compare to
 * @return true if tags disagree
 */
bool evioDOMNode::operator!=(uint16_t tg) const {
  return(this->tag!=tg);
}


//-----------------------------------------------------------------------------


/** 
 * True if node tag and num same as in tagNum pair.
 * @param tnPair tagNum pair to compare to
 * @return true if tag and num agree
 */
bool evioDOMNode::operator==(tagNum tnPair) const {
  return(
         (this->tag==tnPair.first) &&
         (this->num==tnPair.second)
         );
}


//-----------------------------------------------------------------------------


/** 
 * True if node tag and num NOT the same as in tagNum pair.
 * @param tnPair tagNum pair to compare to
 * @return true if tag and num disagree
 */
bool evioDOMNode::operator!=(tagNum tnPair) const {
  return(
         (this->tag!=tnPair.first) ||
         (this->num!=tnPair.second)
         );
}


//-----------------------------------------------------------------------------


/** 
 * Returns parent evioDOMNode.
 * @return Pointer to parent evioDOMNode
 */
const evioDOMNodeP evioDOMNode::getParent(void) const {
  return(parent);
}


//-----------------------------------------------------------------------------


/** 
 * Returns parent evioDOMTree.
 * @return Pointer to parent evioDOMTree
 */
const evioDOMTreeP evioDOMNode::getParentTree(void) const {
  return(parentTree);
}


//-----------------------------------------------------------------------------


/** 
 * Returns content type.
 * @return Content type
 */
int evioDOMNode::getContentType(void) const {
  return(contentType);
}


//-----------------------------------------------------------------------------


/** 
 * True if node is a container node.
 * @return true if node is a container
 */
bool evioDOMNode::isContainer(void) const {
  return(::is_container(contentType)==1);
}


//-----------------------------------------------------------------------------


/** 
 * True if node is a leaf node.
 * @return true if node is a leaf
 */
bool evioDOMNode::isLeaf(void) const {
  return(::is_container(contentType)==0);
}


//--------------------------------------------------------------


/** 
 * Returns indent for toString method, used internally
 * @return String containing proper number of indent spaces
 */
string evioDOMNode::getIndent(int depth) {
  string s;
  for(int i=0; i<depth; i++) s+="   ";
  return(s);
}


//-----------------------------------------------------------------------------
//----------------------- evioDOMContainerNode --------------------------------
//-----------------------------------------------------------------------------


/** 
 * Container node constructor used internally.
 * @param par Parent node
 * @param tg Node tag
 * @param num Node num
 * @param cType Container node content type
 */
evioDOMContainerNode::evioDOMContainerNode(evioDOMNodeP par, uint16_t tg, uint8_t num, ContainerType cType) throw(evioException)
  : evioDOMNode(par,tg,num,cType) {
}


//-----------------------------------------------------------------------------


/**
 * Returns XML string listing container node contents.
 * @return XML string listing contents
 */
string evioDOMContainerNode::toString(void) const {
  ostringstream os;
  os << getHeader(0) << getFooter(0);
  return(os.str());
}


//-----------------------------------------------------------------------------

                                   
/**
 * Returns XML string containing header needed by toString
 * @param depth Current depth
 * @return XML string
 */
string evioDOMContainerNode::getHeader(int depth) const {
  ostringstream os;
  os << getIndent(depth)
     <<  "<" << get_typename(parent==NULL?BANK:parent->getContentType()) << " content=\"" << get_typename(contentType)
     << "\" data_type=\"" << hex << showbase << getContentType()
     << dec << "\" tag=\""  << tag;
  if((parent==NULL)||((parent->getContentType()==0xe)||(parent->getContentType()==0x10))) os << dec << "\" num=\"" << (int)num;
  os << "\">" << endl;
  return(os.str());
}


//-----------------------------------------------------------------------------

                                   
/**
 * Returns XML string containing footer needed by toString
 * @param depth Current depth
 * @return XML string
 */
string evioDOMContainerNode::getFooter(int depth) const {
  ostringstream os;
  os << getIndent(depth) << "</" << get_typename(this->parent==NULL?BANK:this->parent->getContentType()) << ">" << endl;
  return(os.str());
}


//-----------------------------------------------------------------------------
//------------------------------- evioDOMTree ---------------------------------
//-----------------------------------------------------------------------------


/**
 * Constructor fills tree from contents of evioChannel object.
 * @param channel evioChannel object
 * @param name Name of tree
 */
evioDOMTree::evioDOMTree(const evioChannel &channel, const string &name) throw(evioException) : root(NULL), name(name) {
  const uint32_t *buf = channel.getBuffer();
  if(buf==NULL)throw(evioException(0,"?evioDOMTree constructor...channel delivered null buffer",__FILE__,__FUNCTION__,__LINE__));
  root=parse(buf);
  root->parentTree=this;
}


//-----------------------------------------------------------------------------


/**
 * Constructor fills tree from contents of evioChannel object.
 * @param channel Pointer to evioChannel object
 * @param name Name of tree
 */
evioDOMTree::evioDOMTree(const evioChannel *channel, const string &name) throw(evioException) : root(NULL), name(name) {
  if(channel==NULL)throw(evioException(0,"?evioDOMTree constructor...null channel",__FILE__,__FUNCTION__,__LINE__));
  const uint32_t *buf = channel->getBuffer();
  if(buf==NULL)throw(evioException(0,"?evioDOMTree constructor...channel delivered null buffer",__FILE__,__FUNCTION__,__LINE__));
  root=parse(buf);
  root->parentTree=this;
}


//-----------------------------------------------------------------------------

/**
 * Constructor fills tree from contents of buffer
 * @param buf Buffer containing event
 * @param name Name of tree
 */
evioDOMTree::evioDOMTree(const uint32_t *buf, const string &name) throw(evioException) : root(NULL), name(name) {
  if(buf==NULL)throw(evioException(0,"?evioDOMTree constructor...null buffer",__FILE__,__FUNCTION__,__LINE__));
  root=parse(buf);
  root->parentTree=this;
}


//-----------------------------------------------------------------------------


/**
 * Constructor creates tree using node as the root node.
 * @param node Pointer to node that becomes the root node
 * @param name Name of tree
 */
evioDOMTree::evioDOMTree(evioDOMNodeP node, const string &name) throw(evioException) : root(NULL), name(name) {
  if(node==NULL)throw(evioException(0,"?evioDOMTree constructor...null evioDOMNode",__FILE__,__FUNCTION__,__LINE__));
  root=node;
  root->parentTree=this;
}


//-----------------------------------------------------------------------------


/**
 * Constructor creates new container node as root node.
 * @param tag Root node tag
 * @param num Root node num
 * @param cType Root node content type
 * @param name Name of tree
 */
evioDOMTree::evioDOMTree(uint16_t tag, uint8_t num, ContainerType cType, const string &name) throw(evioException) : root(NULL), name(name) {
  root=evioDOMNode::createEvioDOMNode(tag,num,cType);
  root->parentTree=this;
}


//-----------------------------------------------------------------------------


/**
 * Destructor deletes root node and contents.
 */
evioDOMTree::~evioDOMTree(void) {
  root->cutAndDelete();
}


//-----------------------------------------------------------------------------


/**
 * Parses contents of buffer and creates node hierarchry.
 * @param buf Buffer containing event data
 * @return Pointer to highest node resulting from parsing of buffer
 */
evioDOMNodeP evioDOMTree::parse(const uint32_t *buf) throw(evioException) {
  evioStreamParser p;
  return((evioDOMNodeP)p.parse(buf,*this,NULL));
}


//-----------------------------------------------------------------------------


/**
 * Creates container node, used internally when parsing buffer.
 * @param length not used
 * @param tag New node tag
 * @param contentType New node content type
 * @param num New node num
 * @param depth Current depth
 * @param userArg Used internally
 * @return void* used internally
 */
void *evioDOMTree::containerNodeHandler(int length, uint16_t tag, int contentType, uint8_t num, int depth, 
                               void *userArg) {
    
    
  // get parent pointer
  evioDOMContainerNode *parent = (evioDOMContainerNode*)userArg;
    

  // create new node
  evioDOMNodeP newNode = evioDOMNode::createEvioDOMNode(tag,num,(ContainerType)contentType);


  // set parent pointers
  if(parent!=NULL) {
    parent->childList.push_back(newNode);
    newNode->parent=parent;
  }
  

  // return pointer to new node
  return((void*)newNode);
}
  
  
//-----------------------------------------------------------------------------


/**
 * Creates leaf node, used internally when parsing buffer.
 * @param length Length of data in buffer
 * @param tag New node tag
 * @param contentType New node content type
 * @param num New node num
 * @param depth Current depth
 * @param data Pointer to data in buffer
 * @param userArg Used internally
 * @return void* used internally
 */
void evioDOMTree::leafNodeHandler(int length, uint16_t tag, int contentType, uint8_t num, int depth, 
                              const void *data, void *userArg) {


  // create and fill new leaf
  evioDOMNodeP newLeaf;
  string s;
  ostringstream os;
  switch (contentType) {

  case 0x0:
  case 0x1:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(uint32_t*)data,length);
    break;
      
  case 0x2:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(float*)data,length);
    break;
      
  case 0x3:
    for(int i=0; i<length; i++) os << ((char*)data)[i];
    os << ends;
    s=os.str();
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,&s,1);
    break;

  case 0x4:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(int16_t*)data,length);
    break;

  case 0x5:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(uint16_t*)data,length);
    break;

  case 0x6:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(int8_t*)data,length);
    break;

  case 0x7:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(uint8_t*)data,length);
    break;

  case 0x8:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(double*)data,length);
    break;

  case 0x9:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(int64_t*)data,length);
    break;

  case 0xa:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(uint64_t*)data,length);
    break;

  case 0xb:
    newLeaf = evioDOMNode::createEvioDOMNode(tag,num,(int32_t*)data,length);
    break;

  default:
    ostringstream ss;
    ss << hex << showbase << contentType<< ends;
    throw(evioException(0,"?evioDOMTree::leafNodeHandler...illegal content type: " + ss.str(),__FILE__,__FUNCTION__,__LINE__));
    break;
  }


  // add new leaf to parent
  evioDOMContainerNode *parent = (evioDOMContainerNode*)userArg;
  if(parent!=NULL) {
    parent->childList.push_back(newLeaf);
    newLeaf->parent=parent;
  }
}


//-----------------------------------------------------------------------------


/** 
 * Removes and deletes tree root node and all its contents.
 */
void evioDOMTree::clear(void) throw(evioException) {
  if(root!=NULL) {
    root->cutAndDelete();
    root=NULL;
  }
}


//-----------------------------------------------------------------------------


/** 
 * Makes node root if tree is empty, or adds node to root if a container.
 * @param node Node to add to tree
 */
void evioDOMTree::addBank(evioDOMNodeP node) throw(evioException) {

  node->cut();

  if(root==NULL) {
    root=node;
    root->parentTree=this;

  } else {
    evioDOMContainerNode *c = dynamic_cast<evioDOMContainerNode*>(root);
    if(c==NULL)throw(evioException(0,"?evioDOMTree::addBank...root is not container",__FILE__,__FUNCTION__,__LINE__));
    c->childList.push_back(node);
    node->parent=root;
  }
}


//-----------------------------------------------------------------------------


/** 
 * Makes node root if tree is empty, or adds node to root if a container.
 * @param node Node to add to root
 */
evioDOMTree& evioDOMTree::operator<<(evioDOMNodeP node) throw(evioException) {
  addBank(node);
  return(*this);
}


//-----------------------------------------------------------------------------


/** 
 * Serializes tree to buffer.
 * @param buf Buffer that receives serialized tree
 * @param size Size of buffer
 */
void evioDOMTree::toEVIOBuffer(uint32_t *buf, int size) const throw(evioException) {
  toEVIOBuffer(buf,root,size);
}


//-----------------------------------------------------------------------------


/** 
 * Serializes node into buffer, used internally.
 * @param buf Buffer that receives serialized tree
 * @param pNode Node to serialize
 * @param size Size of buffer
 * @return Size of serialized node
 */
int evioDOMTree::toEVIOBuffer(uint32_t *buf, const evioDOMNodeP pNode, int size) const throw(evioException) {

  int bankLen,bankType,dataOffset;

  if(size<=0)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...illegal buffer size",__FILE__,__FUNCTION__,__LINE__));


  if(pNode->parent==NULL) {
    bankType=BANK;
  } else {
    bankType=pNode->parent->contentType;
  }


  // add bank header word(s)
  switch (bankType) {

  case 0xe:
  case 0x10:
    buf[0]=0;
    buf[1] = (pNode->tag<<16) | (pNode->contentType<<8) | pNode->num;
    dataOffset=2;
    break;
  case 0xd:
  case 0x20:
    if(pNode->num!=0)cout << "?warning...num ignored in segment: " << pNode->num << endl;
    buf[0] = (pNode->tag<<24) | ( pNode->contentType<<16);
    dataOffset=1;
    break;
  case 0xc:
  case 0x40:
    if(pNode->tag>0xfff)cout << "?warning...tag truncated to 12 bits in tagsegment: " << pNode->tag << endl;
    if(pNode->num!=0)cout << "?warning...num ignored in tagsegment: " << pNode->num<< endl;
    buf[0] = (pNode->tag<<20) | ( pNode->contentType<<16);
    dataOffset=1;
    break;
  default:
    ostringstream ss;
    ss << hex << showbase << bankType<< ends;
    throw(evioException(0,"evioDOMTree::toEVIOBuffer...illegal bank type in boilerplate: " + ss.str(),__FILE__,__FUNCTION__,__LINE__));
    break;
  }


  // set starting length
  bankLen=dataOffset;


  // loop over contained nodes if container node
  const evioDOMContainerNode *c = dynamic_cast<const evioDOMContainerNode*>(pNode);
  if(c!=NULL) {
    evioDOMNodeList::const_iterator iter;
    for(iter=c->childList.begin(); iter!=c->childList.end(); iter++) {
      bankLen+=toEVIOBuffer(&buf[bankLen],*iter,size-bankLen-1);
      if(bankLen>size)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...buffer too small",__FILE__,__FUNCTION__,__LINE__));
    }


  // leaf node...copy data, and don't forget to pad!
  } else {

    int nword,ndata,i;
    switch (pNode->contentType) {

    case 0x0:
    case 0x1:
    case 0x2:
    case 0xb:
      {
        const evioDOMLeafNode<uint32_t> *leaf = static_cast<const evioDOMLeafNode<uint32_t>*>(pNode);
        ndata = leaf->data.size();
        nword = ndata;
        if(bankLen+nword>size)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...buffer too small",__FILE__,__FUNCTION__,__LINE__));
        for(i=0; i<ndata; i++) buf[dataOffset+i]=(uint32_t)(leaf->data[i]);
      }
      break;
      
    case 0x3:
      {
        const evioDOMLeafNode<string> *leaf = static_cast<const evioDOMLeafNode<string>*>(pNode);
        string s = leaf->data[0];
        ndata = s.size();
        nword = (ndata+3)/4;
        if(bankLen+nword>size)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...buffer too small",__FILE__,__FUNCTION__,__LINE__));
        uint8_t *c = (uint8_t*)&buf[dataOffset];
        for(i=0; i<ndata; i++) c[i]=(s.c_str())[i];
        for(i=ndata; i<ndata+(4-ndata%4)%4; i++) c[i]='\0';
      }
      break;

    case 0x4:
    case 0x5:
      {
        const evioDOMLeafNode<uint16_t> *leaf = static_cast<const evioDOMLeafNode<uint16_t>*>(pNode);
        ndata = leaf->data.size();
        nword = (ndata+1)/2;
        if(bankLen+nword>size)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...buffer too small",__FILE__,__FUNCTION__,__LINE__));
        uint16_t *s = (uint16_t *)&buf[dataOffset];
        for(i=0; i<ndata; i++) s[i]=static_cast<uint16_t>(leaf->data[i]);
        if((ndata%2)!=0)buf[ndata]=0;
      }
      break;

    case 0x6:
    case 0x7:
      {
        const evioDOMLeafNode<uint8_t> *leaf = static_cast<const evioDOMLeafNode<uint8_t>*>(pNode);
        ndata = leaf->data.size();
        nword = (ndata+3)/4;
        if(bankLen+nword>size)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...buffer too small",__FILE__,__FUNCTION__,__LINE__));
        uint8_t *c = (uint8_t*)&buf[dataOffset];
        for(i=0; i<ndata; i++) c[i]=static_cast<uint8_t>(leaf->data[i]);
        for(i=ndata; i<ndata+(4-ndata%4)%4; i++) c[i]='\0';
      }
      break;

    case 0x8:
    case 0x9:
    case 0xa:
      {
        const evioDOMLeafNode<uint64_t> *leaf = static_cast<const evioDOMLeafNode<uint64_t>*>(pNode);
        ndata = leaf->data.size();
        nword = ndata*2;
        if(bankLen+nword>size)throw(evioException(0,"?evioDOMTree::toEVOIBuffer...buffer too small",__FILE__,__FUNCTION__,__LINE__));
        uint64_t *ll = (uint64_t*)&buf[dataOffset];
        for(i=0; i<ndata; i++) ll[i]=static_cast<uint64_t>(leaf->data[i]);
      }
      break;

    default:
      ostringstream ss;
      ss << pNode->contentType<< ends;
      throw(evioException(0,"?evioDOMTree::toEVOIBuffer...illegal leaf type: " + ss.str(),__FILE__,__FUNCTION__,__LINE__));
      break;
    }

    // increment data count
    bankLen+=nword;
  }


  // finaly set node length in buffer
  switch (bankType) {

  case 0xe:
  case 0x10:
    buf[0]=bankLen-1;
    break;
  case 0xd:
  case 0x20:
  case 0xc:
  case 0x40:
    if((bankLen-1)>0xffff)throw(evioException(0,"?evioDOMTree::toEVIOBuffer...length too big for segment type",__FILE__,__FUNCTION__,__LINE__));
    buf[0]|=(bankLen-1);
    break;
  default: 
    ostringstream ss;
    ss << bankType<< ends;
    throw(evioException(0,"?evioDOMTree::toEVIOBuffer...illegal bank type setting length: " + ss.str(),__FILE__,__FUNCTION__,__LINE__));
    break;
  }


  // return length of this bank
  return(bankLen);
}


//-----------------------------------------------------------------------------


/**
 * Returns list of all nodes in tree.
 * @return Pointer to list of nodes in tree (actually auto_ptr<>)
 */
evioDOMNodeListP evioDOMTree::getNodeList(void) throw(evioException) {
  return(evioDOMNodeListP(addToNodeList(root,new evioDOMNodeList,isTrue)));
}


//-----------------------------------------------------------------------------


/**
 * Returns XML string listing tree contents.
 * @return XML string listing contents
 */
string evioDOMTree::toString(void) const {

  if(root==NULL)return("<!-- empty tree -->");

  ostringstream os;
  os << endl << endl << "<!-- Dump of tree: " << name << " -->" << endl << endl;
  toOstream(os,root,0);
  os << endl << endl;
  return(os.str());

}


//-----------------------------------------------------------------------------


/**
 * Used internally by toString method.
 * @param os ostream to append XML fragments
 * @param pNode Node to get XML representation
 * @param depth Current depth
 */
void evioDOMTree::toOstream(ostream &os, const evioDOMNodeP pNode, int depth) const throw(evioException) {

  
  if(pNode==NULL)return;


  // get node header
  os << pNode->getHeader(depth);


  // dump contained banks if node is a container
  const evioDOMContainerNode *c = dynamic_cast<const evioDOMContainerNode*>(pNode);
  if(c!=NULL) {
    evioDOMNodeList::const_iterator iter;
    for(iter=c->childList.begin(); iter!=c->childList.end(); iter++) {
      toOstream(os,*iter,depth+1);
    }
  }


  // get footer
  os << pNode->getFooter(depth);
}


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
