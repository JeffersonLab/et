#
# ET top level Makefile
#
#     Set the env var ET_INSTALL to the directory in which
#     you want the libs, executables,and docs placed. If not
#     set, this defaults to this (top et) directory.
#
#     Set the env var ET_USE64BITS if you want to compile
#     64 bit libs and executables and are running on a 64
#     bit machine.
#

MAKEFILE = Makefile.unix

# operating system, platform, # of processor bits we are using
OSNAME   := $(shell uname)
PLATFORM := $(shell uname -m)
ET_DIR   := $(shell pwd)

# Look to see if the environmental variable ET_USE64BITS is defined.
# If so, then compile everything for 64 bits. Be sure to do a 
# "make cClean" when switching between 32 & 64 bit compiling.
ifeq ($(findstring ET_USE64BITS, $(shell env | grep ET_USE64BITS)), ET_USE64BITS) 
ET_BIT_ARCH = 64
BITS = 64
else
ET_BIT_ARCH =
BITS = 32
endif

# Define the installation directory.
# By default use the et directory,
# else we use the directory specified in the
# ET_INSTALL environmental variable.
ifneq ($(findstring ET_INSTALL, $(shell env | grep ET_INSTALL)), ET_INSTALL) 
ET_INSTALL = $(ET_DIR)
endif

MESSAGE = "Make for $(OSNAME) on $(PLATFORM), $(BITS) bits"

# if ARCH is defined, do a vxWorks build
ifeq ($(ARCH),VXWORKSPPC)
MAKEFILE = Makefile.vxworks
OSNAME   = vxworks
MESSAGE  = "Make for $(OSNAME) on $(shell uname), 32 bits"
PLATFORM =
ET_BIT_ARCH = 
endif

INC_DIR = $(ET_INSTALL)/common/inc
LIB_DIR = $(ET_INSTALL)/arch/$(OSNAME)/$(PLATFORM)/$(ET_BIT_ARCH)/lib
BIN_DIR = $(ET_INSTALL)/arch/$(OSNAME)/$(PLATFORM)/$(ET_BIT_ARCH)/bin

# send these definitions down to lower level makefiles
export OSNAME
export PLATFORM
export INC_DIR
export LIB_DIR
export BIN_DIR
export ET_DIR
export ET_BIT_ARCH

# needed directories
DIRS =  $(ET_INSTALL)/common \
	$(ET_INSTALL)/common/jar \
	$(ET_INSTALL)/common/tar \
	$(ET_INSTALL)/doc \
	$(ET_INSTALL)/doc/htm \
	$(ET_INSTALL)/doc/javadoc \
	$(ET_INSTALL)/doc/doxygen \
	$(ET_INSTALL)/doc/doxygen/C \
	$(ET_INSTALL)/doc/doxygen/CC \
	$(ET_INSTALL)/arch \
	$(ET_INSTALL)/arch/$(OSNAME) \
	$(ET_INSTALL)/arch/$(OSNAME)/$(PLATFORM) \
	$(ET_INSTALL)/arch/$(OSNAME)/$(PLATFORM)/$(ET_BIT_ARCH) \
	$(INC_DIR) \
	$(LIB_DIR) \
	$(BIN_DIR) \
	./src/.$(OSNAME) \
	./src/.$(OSNAME)/$(PLATFORM) \
	./src/.$(OSNAME)/$(PLATFORM)/$(ET_BIT_ARCH) \
	./examples/.$(OSNAME) \
	./examples/.$(OSNAME)/$(PLATFORM) \
	./examples/.$(OSNAME)/$(PLATFORM)/$(ET_BIT_ARCH)

# for improved performance declare that none of these targets
# produce files of that name (.PHONY)

.PHONY : all echoarch mkdirs src ex tarfile jarfile java doc doxygen html
.PHONY : clean distClean bClean cClean jClean tarClean jarClean

all: echoarch mkdirs src ex java jarfile

echoarch:
	@echo
	@echo $(MESSAGE)
	@echo

mkdirs: mkinstalldirs
	@echo "Creating directories"
	./mkinstalldirs $(DIRS)
	@echo

src:    echoarch mkdirs
	cd ./src; $(MAKE) -f $(MAKEFILE) install;

ex:     echoarch mkdirs
	cd ./examples; $(MAKE) -f $(MAKEFILE) install;

tarfile:
	-$(RM) -f common/tar/et-9.0.tar.gz;
	cd ..; tar cvX ./et/common/tar/tarexclude -f et-9.0.tar.gz -z ./et
	-mv ../et-9.0.tar.gz common/tar/.
	-cp -p common/tar/et-9.0.tar.gz  $(ET_INSTALL)/common/tar/.

jarfile:
	jar cf common/jar/et-9.0.jar `find java/org -type f | grep -v SCCS | grep -v CVS | grep class`
	-cp -p common/jar/*.jar $(ET_INSTALL)/common/jar/.

java:
	cd ./java/org/jlab/coda/et;        $(MAKE);
	cd ./java/org/jlab/coda/etMonitor; $(MAKE);
	cd ./java;                         $(MAKE);

doc: mkdirs html javadoc doxygen 

html:
	-cp -p  doc/htm/*      $(ET_INSTALL)/doc/htm/.

javadoc:
	cd ./java; javadoc -package -d $(ET_INSTALL)/doc/javadoc org.jlab.coda.et
	cd ./java; javadoc -package -d $(ET_INSTALL)/doc/javadoc org.jlab.coda.etMonitor

doxygen:
	@echo installing doxygen documentation in $(ET_INSTALL)
	@echo
	export ET_INSTALL=$(ET_INSTALL) ; doxygen ./doc/doxygen/Doxyfile

clean: cClean jClean tarClean jarClean

distClean: clean bClean

bClean:
	-$(RM) -f $(LIB_DIR)/* $(BIN_DIR)/*

cClean: 
	-$(RM) -f core *~ *.o *.so *.a *.class
	cd ./src;      $(MAKE) -f $(MAKEFILE) clean;
	cd ./examples; $(MAKE) -f $(MAKEFILE) clean;

jClean: 
	cd ./java/org/jlab/coda/et;        $(MAKE) clean;
	cd ./java/org/jlab/coda/etMonitor; $(MAKE) clean;
	cd ./java;                         $(MAKE) clean;

tarClean:
	-$(RM) common/tar/et-9.0.tar.gz

jarClean:
	-$(RM) common/jar/et-9.0.jar
