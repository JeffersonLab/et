// evioUtilTemplates.hxx

//  all the evio templates, some prototypes in evioUtil.hxx

//  ejw, 5-dec-2006



#ifndef _evioUtilTemplates_hxx
#define _evioUtilTemplates_hxx




//--------------------------------------------------------------
//----------------------- local class  -------------------------
//--------------------------------------------------------------


/**
 * Templated utility class has method that returns content type based on typename T.
 * Complete specializations supplied for all defined types.
 * Cumbersome, but this is the only way to do it on solaris...ejw, 9-jan-2007.
 * Note that the long data type is not supported due to ambiguities on different architectures.
 */
template <typename T> class evioUtil {

public: static int evioContentType(void) throw(evioException) {
    throw(evioException(0,"?evioUtil<T>::evioContentType...unsupported data type",__FILE__,__FUNCTION__,__LINE__));
    return(0);
  }
};

template <> class evioUtil<uint32_t>     {public: static int evioContentType(void)  throw(evioException) {return(0x1);}};
template <> class evioUtil<float>        {public: static int evioContentType(void)  throw(evioException) {return(0x2);}};
template <> class evioUtil<string&>      {public: static int evioContentType(void)  throw(evioException) {return(0x3);}};
template <> class evioUtil<int16_t>      {public: static int evioContentType(void)  throw(evioException) {return(0x4);}};
template <> class evioUtil<uint16_t>     {public: static int evioContentType(void)  throw(evioException) {return(0x5);}};
template <> class evioUtil<int8_t>       {public: static int evioContentType(void)  throw(evioException) {return(0x6);}};
template <> class evioUtil<uint8_t>      {public: static int evioContentType(void)  throw(evioException) {return(0x7);}};
template <> class evioUtil<double>       {public: static int evioContentType(void)  throw(evioException) {return(0x8);}};
template <> class evioUtil<int64_t>      {public: static int evioContentType(void)  throw(evioException) {return(0x9);}};
template <> class evioUtil<uint64_t>     {public: static int evioContentType(void)  throw(evioException) {return(0xa);}};
template <> class evioUtil<int32_t>      {public: static int evioContentType(void)  throw(evioException) {return(0xb);}};


//-----------------------------------------------------------------------------
//--------------------- evioDOMNode templated methods -------------------------
//-----------------------------------------------------------------------------


/** 
 * Static factory method to create leaf node of type T.
 * @param tag Node tag
 * @param num Node num
 * @param tVec vector<T> of data
 * @return Pointer to new node
 */
template <typename T> evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, const vector<T> tVec)
  throw(evioException) {
  return(new evioDOMLeafNode<T>(NULL,tag,num,tVec));
}


//-----------------------------------------------------------------------------


/** 
 * Static factory method to create leaf node of type T.
 * @param tag Node tag
 * @param num Node num
 * @param t Pointer to array containg data of type T
 * @param len Length of array
 * @return Pointer to new node
 */
template <typename T> evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, const T* t, int len)
  throw(evioException) {
  return(new evioDOMLeafNode<T>(NULL,tag,num,t,len));
}


//-----------------------------------------------------------------------------


/** 
 * Static factory method to create container node using serialize method to fill container.
 * @param tag Node tag
 * @param num Node num
 * @param t Pointer to object having serialize method
 * @param userArg User arg passed to serialize method
 * @param cType Container node content type
 * @return Pointer to new node
 */
template <typename T> evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, T *t, 
                                                                  void *userArg, ContainerType cType) throw(evioException) {
  evioDOMContainerNode *c = new evioDOMContainerNode(NULL,tag,num,cType);
  t->serialize(c,userArg);
  return(c);
}


//-----------------------------------------------------------------------------


/** 
 * Static factory method to create container node filled via object method pointer.
 * @param tag Node tag
 * @param num Node num
 * @param t Pointer to object
 * @param mfp Pointer to object method used to fill container
 * @param userArg User arg passed to object method
 * @param cType Container node content type
 * @return Pointer to new node
 */
template <typename T> evioDOMNodeP evioDOMNode::createEvioDOMNode(uint16_t tag, uint8_t num, T *t, 
                                                                  void* T::*mfp(evioDOMNodeP c, void *userArg),
                                                                  void *userArg, ContainerType cType) throw(evioException) {
  evioDOMContainerNode *c = new evioDOMContainerNode(NULL,tag,num,cType);
  t->mfp(c,userArg);
  return(c);
}


//-----------------------------------------------------------------------------


/** 
 * Appends more data to leaf node.
 * Must be done this way because C++ forbids templated virtual functions...ejw, dec-2006
 * @param tVec vector<T> of data to add to leaf node
 */
template <typename T> void evioDOMNode::append(const vector<T> &tVec) throw(evioException) {
  evioDOMLeafNode<T> *l = dynamic_cast<evioDOMLeafNode<T>*>(this);
  if(l!=NULL) {
    copy(tVec.begin(),tVec.end(),inserter(l->data,l->data.end()));
  } else {
    throw(evioException(0,"?evioDOMNode::append...not a leaf node",__FILE__,__FUNCTION__,__LINE__));
  }
}


//-----------------------------------------------------------------------------


/** 
 * Appends more data to leaf node.
 * Must be done this way because C++ forbids templated virtual functions...ejw, dec-2006
 * @param tBuf Buffer of data of type T
 * @param len Length of buffer
 */
template <typename T> void evioDOMNode::append(const T* tBuf, int len) throw(evioException) {
  evioDOMLeafNode<T> *l = dynamic_cast<evioDOMLeafNode<T>*>(this);
  if(l!=NULL) {
    for(int i=0; i<len; i++) l->data.push_back(tBuf[i]);
  } else {
    throw(evioException(0,"?evioDOMNode::append...not a leaf node",__FILE__,__FUNCTION__,__LINE__));
  }
}


//-----------------------------------------------------------------------------


/** 
 * Replaces data in leaf node.
 * Must be done this way because C++ forbids templated virtual functions...ejw, dec-2006
 * @param tVec vector<T> of data
 */
template <typename T> void evioDOMNode::replace(const vector<T> &tVec) throw(evioException) {
  evioDOMLeafNode<T> *l = dynamic_cast<evioDOMLeafNode<T>*>(this);
  if(l!=NULL) {
    l->data.clear();
    copy(tVec.begin(),tVec.end(),inserter(l->data,l->data.begin()));
  } else {
    throw(evioException(0,"?evioDOMNode::replace...not a leaf node",__FILE__,__FUNCTION__,__LINE__));
  }
}


//-----------------------------------------------------------------------------


/** 
 * Replaces data in leaf node.
 * Must be done this way because C++ forbids templated virtual functions...ejw, dec-2006
 * @param tBuf Buffer of data of type T
 * @param len Length of buffer
 */
template <typename T> void evioDOMNode::replace(const T* tBuf, int len) throw(evioException) {
  evioDOMLeafNode<T> *l = dynamic_cast<evioDOMLeafNode<T>*>(this);
  if(l!=NULL) {
    l->data.clear();
    for(int i=0; i<len; i++) l->data.push_back(tBuf[i]);
  } else {
    throw(evioException(0,"?evioDOMNode::replace...not a leaf node",__FILE__,__FUNCTION__,__LINE__));
  }
}


//-----------------------------------------------------------------------------


/** 
 * Returns pointer to leaf node vector<T> of data
 * Must be done this way because C++ forbids templated virtual functions...ejw, dec-2006
 * @return Pointer to vector<T>
 */
template <typename T> vector<T> *evioDOMNode::getVector(void) throw(evioException) {
  if(!isLeaf())return(NULL);
  evioDOMLeafNode<T> *l = dynamic_cast<evioDOMLeafNode<T>*>(this);
  return((l==NULL)?NULL:(&l->data));
}


//-----------------------------------------------------------------------------


/** 
 * Appends single data value to leaf node
 * @param tVal Data of type T
 */
template <typename T> evioDOMNode& evioDOMNode::operator<<(T tVal) throw(evioException) {
  append(&tVal,1);
  return(*this);
}


//-----------------------------------------------------------------------------


/** 
 * Appends more data to leaf node.
 * @param tVec vector<T> of data to add to leaf node
 */
template <typename T> evioDOMNode& evioDOMNode::operator<<(const vector<T> &tVec) throw(evioException) {
  append(tVec);
  return(*this);
}


//-----------------------------------------------------------------------------


/** 
 * Appends more data to leaf node.
 * @param tVec vector<T> of data to add to leaf node
 */
template <typename T> evioDOMNode& evioDOMNode::operator<<(vector<T> &tVec) throw(evioException) {
  append(tVec);
  return(*this);
}


//-----------------------------------------------------------------------------
//--------------------- evioDOMLeafNode templated methods ---------------------
//-----------------------------------------------------------------------------


/** 
 * Leaf node constructor used internally.
 * @param par Parent node
 * @param tag Node tag
 * @param num Node num
 * @param v vector<T> of data
 */
template <typename T> evioDOMLeafNode<T>::evioDOMLeafNode(evioDOMNodeP par, uint16_t tag, uint8_t num, const vector<T> &v)
  throw(evioException) : evioDOMNode(par,tag,num,evioUtil<T>::evioContentType()) {
  
  copy(v.begin(),v.end(),inserter(data,data.begin()));
}


//-----------------------------------------------------------------------------


/** 
 * Leaf node constructor used internally.
 * @param par Parent node
 * @param tag Node tag
 * @param num Node num
 * @param p Pointer to array containg data of type T
 * @param ndata Length of array
 */
template <typename T> evioDOMLeafNode<T>::evioDOMLeafNode(evioDOMNodeP par, uint16_t tag, uint8_t num, const T* p, int ndata) 
  throw(evioException) : evioDOMNode(par,tag,num,evioUtil<T>::evioContentType()) {
  
  // fill vector with data
  for(int i=0; i<ndata; i++) data.push_back(p[i]);
}


//-----------------------------------------------------------------------------


/**
 * Returns XML string containing header needed by toString
 * @param depth Current depth
 * @return XML string
 */
template <typename T> string evioDOMLeafNode<T>::getHeader(int depth) const {

  ostringstream os;
  string indent = getIndent(depth);
  string indent2 = indent + "    ";

  int wid,swid;
  switch (contentType) {

  case 0x0:
  case 0x1:
  case 0x2:
  case 0xb:
    wid=5;
    swid=10;
    break;
  case 0x4:
  case 0x5:
    wid=8;
    swid=6;
    break;
  case 0x6:
  case 0x7:
    wid=8;
    swid=4;
    break;
  case 0x8:
  case 0x9:
  case 0xa:
    wid=2;
    swid=28;
    break;
  default:
    wid=1;
   swid=30;
    break;
  }


  // dump header
  os << indent
     <<  "<" << get_typename(contentType) 
     << " data_type=\"" << hex << showbase << contentType
     << dec << "\" tag=\"" << tag;
  if((parent==NULL)||((parent->getContentType()==0xe)||(parent->getContentType()==0x10))) os << dec << "\" num=\"" << (int)num;
  os << "\">" << endl;


  // dump data...odd what has to be done for 1-byte types 0x6,0x7 due to bugs in ostream operator <<
  int16_t k;
  typename vector<T>::const_iterator iter;
  for(iter=data.begin(); iter!=data.end();) {

    if(contentType!=0x3)os << indent2;
    for(int j=0; (j<wid)&&(iter!=data.end()); j++) {
      switch (contentType) {

      case 0x0:
      case 0x1:
      case 0x5:
      case 0xa:
        os << hex << showbase << setw(swid) << *iter << "  ";
        break;
      case 0x2:
        os << setprecision(6) << showpoint << setw(swid) << *iter << "  ";
        break;
      case 0x3:
        os << "<!CDATA[" << endl << *iter << endl << "]]>";
        break;
      case 0x6:
        k = (*((int16_t*)(&(*iter)))) & 0xff;
        if((k&0x80)!=0)k|=0xff00;
        os << setw(swid) << k << "  ";
        break;
      case 0x7:
        os << hex << showbase << setw(swid) << ((*(int*)&(*iter))&0xff) << "  ";
        break;
      case 0x8:
        os << setw(swid) << setprecision(20) << scientific << *iter << "  ";
        break;
      default:
        os << setw(swid) << *iter << "  ";
        break;
      }
      iter++;
    }
    os << dec << endl;

  }

  return(os.str());
}


//-----------------------------------------------------------------------------


/**
 * Returns XML string containing footer needed by toString
 * @param depth Current depth
 * @return XML string
 */
template <typename T> string evioDOMLeafNode<T>::getFooter(int depth) const {
  ostringstream os;
  os << getIndent(depth) << "</" << get_typename(this->contentType) << ">" << endl;
  return(os.str());
}


//-----------------------------------------------------------------------------

                                   
/**
 * Returns XML string listing leaf node contents.
 * @return XML string listing contents
 */
template <typename T> string evioDOMLeafNode<T>::toString(void) const {
  ostringstream os;
  os << getHeader(0) << getFooter(0);
  return(os.str());
}


//-----------------------------------------------------------------------------
//----------------------- evioDOMTree templated methods -----------------------
//-----------------------------------------------------------------------------


/**
 * Returns list of nodes in tree satisfying predicate.
 * @param pred Function object true if node meets predicate criteria
 * @return Pointer to node list (actually auto_ptr<>)
 */
template <class Predicate> evioDOMNodeListP evioDOMTree::getNodeList(Predicate pred) throw(evioException) {
  evioDOMNodeList *pList = addToNodeList(root, new evioDOMNodeList(), pred);
  return(evioDOMNodeListP(pList));
}  


//-----------------------------------------------------------------------------


/**
 * Adds node to node list, used internally by getNodeList.
 * @param pNode Node to check agains predicate
 * @param pList Current node list
 * @param pred true if node meets predicate criteria
 * @return Pointer to node list
 */
template <class Predicate> evioDOMNodeList *evioDOMTree::addToNodeList(evioDOMNodeP pNode, evioDOMNodeList *pList, Predicate pred)
  throw(evioException) {

  if(pNode==NULL)return(pList);


  // add this node to list
  if(pred(pNode))pList->push_back(pNode);
  
  
  // add children to list
  evioDOMContainerNode *c = dynamic_cast<evioDOMContainerNode*>(pNode);
  if(c!=NULL) {
    evioDOMNodeList::iterator iter;
    for(iter=c->childList.begin(); iter!=c->childList.end(); iter++) {
      addToNodeList(*iter,pList,pred);
    }
  }


  // return the list
  return(pList);
}


//-----------------------------------------------------------------------------


/**
 * Creates leaf node and adds it to tree root node.
 * @param tag Node tag
 * @param num Node num
 * @param dataVec vector<T> of data
 */
template <typename T> void evioDOMTree::addBank(uint16_t tag, uint8_t num, const vector<T> dataVec) throw(evioException) {

  if(root==NULL) {
    root = evioDOMNode::createEvioDOMNode(tag,num,dataVec);
    root->parentTree=this;

  } else {
    evioDOMContainerNode* c = dynamic_cast<evioDOMContainerNode*>(root);
    if(c==NULL)throw(evioException(0,"?evioDOMTree::addBank...root not a container node",__FILE__,__FUNCTION__,__LINE__));
    evioDOMNodeP node = evioDOMNode::createEvioDOMNode(tag,num,dataVec);
    c->childList.push_back(node);
    node->parent=root;
  }
}


//-----------------------------------------------------------------------------


/** 
 * Creates leaf node and adds it to tree root node.
 * @param tag Node tag
 * @param num Node num
 * @param dataBuf Pointer to array containg data of type T
 * @param dataLen Length of array
 */
template <typename T> void evioDOMTree::addBank(uint16_t tag, uint8_t num, const T* dataBuf, int dataLen)
  throw(evioException) {

  if(root==NULL) {
    root = evioDOMNode::createEvioDOMNode(tag,num,dataBuf,dataLen);
    root->parentTree=this;

  } else {
    evioDOMContainerNode* c = dynamic_cast<evioDOMContainerNode*>(root);
    if(c==NULL)throw(evioException(0,"?evioDOMTree::addBank...root not a container node",__FILE__,__FUNCTION__,__LINE__));
    evioDOMNodeP node = evioDOMNode::createEvioDOMNode(tag,num,dataBuf,dataLen);
    c->childList.push_back(node);
    node->parent=root;
  }
}


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
//--------------------- Misc Function Objects ---------------------------------
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares to content type for typename T.
 */
template <typename T> class typeIs : public unary_function<const evioDOMNodeP,bool> {

public:
  typeIs(void) : type(evioUtil<T>::evioContentType()) {}
  bool operator()(const evioDOMNodeP node) const {return(node->getContentType()==type);}
private:
  int type;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on type.
 */
class typeEquals : public unary_function<const evioDOMNodeP,bool> {

public:
  typeEquals(int aType) : type(aType) {}
  bool operator()(const evioDOMNodeP node) const {return(node->getContentType()==type);}
private:
  int type;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on tag.
 */
class tagEquals : public unary_function<const evioDOMNodeP,bool> {

public:
  tagEquals(uint16_t aTag) : tag(aTag) {}
  bool operator()(const evioDOMNodeP node) const {return(node->tag==tag);}
private:
  uint16_t tag;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on num.
 */
class numEquals : public unary_function<const evioDOMNodeP,bool> {

public:
  numEquals(uint8_t aNum) : num(aNum) {}
  bool operator()(const evioDOMNodeP node) const {return(node->num==num);}
private:
  uint8_t num;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on tag and num.
 */
class tagNumEquals : public unary_function<const evioDOMNodeP, bool> {

public:
  tagNumEquals(uint16_t aTag, uint8_t aNum) : tag(aTag), num(aNum) {}
  tagNumEquals(tagNum tn) : tag(tn.first), num(tn.second) {}
  bool operator()(const evioDOMNodeP node) const {return((node->tag==tag)&&(node->num==num));}
private:
  uint16_t tag;
  uint8_t num;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on parent content type.
 */
class parentTypeEquals : public unary_function<const evioDOMNodeP, bool> {

public:
  parentTypeEquals(int aType) : type(aType) {}
  bool operator()(const evioDOMNodeP node) const {return((node->getParent()==NULL)?false:(node->getParent()->getContentType()==type));}
private:
  int type;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on parent tag.
 */
class parentTagEquals : public unary_function<const evioDOMNodeP, bool> {

public:
  parentTagEquals(uint16_t aTag) : tag(aTag) {}
  bool operator()(const evioDOMNodeP node) const {return((node->getParent()==NULL)?false:(node->getParent()->tag==tag));}
private:
  uint16_t tag;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on parent num.
 */
class parentNumEquals : public unary_function<const evioDOMNodeP, bool> {

public:
  parentNumEquals(uint8_t aNum) : num(aNum) {}
  bool operator()(const evioDOMNodeP node) const {return((node->getParent()==NULL)?false:(node->getParent()->num==num));}
private:
  uint8_t num;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object compares on parent tag and num.
 */
class parentTagNumEquals : public unary_function<const evioDOMNodeP, bool> {

public:
  parentTagNumEquals(uint16_t aTag, uint8_t aNum) : tag(aTag), num(aNum) {}
  parentTagNumEquals(tagNum tn) : tag(tn.first), num(tn.second) {}
  bool operator()(const evioDOMNodeP node) const {
    return((node->getParent()==NULL)?false:((node->getParent()->tag==tag)&&(node->getParent()->num==num)));}
private:
  uint16_t tag;
  uint8_t num;
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object true if container node.
 */
class isContainer : public unary_function<const evioDOMNodeP,bool> {

public:
  isContainer(void) {}
  bool operator()(const evioDOMNodeP node) const {return(node->isContainer());}
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Boolean function object true if leaf node.
 */
class isLeaf : public unary_function<const evioDOMNodeP,bool> {

public:
  isLeaf(void) {}
  bool operator()(const evioDOMNodeP node) const {return(node->isLeaf());}
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------


/**
 * Function object streams node->toString() to cout.
 */
class toCout: public unary_function<const evioDOMNodeP,void> {

public:
  toCout(void) {}
  void operator()(const evioDOMNodeP node) const {cout << node->toString() << endl;}
};


//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

#endif
