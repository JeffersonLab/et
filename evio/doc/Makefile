#
# evio documentation installation
#

ifndef BMS_HOME
  $(error "Need to define BMS_HOME")
endif

# include Makefile.local
TOPLEVEL = ..

#include Makefile.common
include $(BMS_HOME)/Makefile.common

# The top-level evio makefile doesn't know about INSTALL_DIR so
# include above 2 makefiles to get INSTALL_DIR.
# At this point, "make doc" has already been done one level above
# and so all we have to do is copy things to its final resting place.

all: doc install

install:
	@mkdir -p $(INSTALL_DIR)/doc/evio
	cp -rp doxygen $(INSTALL_DIR)/doc/evio

doc:
	export TOPLEVEL=$(TOPLEVEL) ; doxygen doxygen/Doxyfile

docOrig:
	/bin/csh -c "(setenv DOTFONTPATH ./TrueType; /apps/doxygen/doxygen-1.5.1/bin/doxygen doxygen/Doxyfile)"
