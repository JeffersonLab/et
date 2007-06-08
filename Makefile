#
# ET top level Makefile
#

MAKEFILE = Makefile

# if make is called here, override CODA stuff with ET stuff
ifeq ($(MAKELEVEL), 0)
  include Makefile.local
endif

ifndef BMS_HOME
  BMS_HOME = $(shell pwd)/BMS
endif
# send value to called makefiles
export BMS_HOME


.PHONY : all src env mkdirs install uninstall relink clean distClean execClean java


all: src

src:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE);
	cd src/examples; $(MAKE) -f $(MAKEFILE);

env:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) env;
	cd src/examples; $(MAKE) -f $(MAKEFILE) env;

mkdirs:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) mkdirs;
	cd src/examples; $(MAKE) -f $(MAKEFILE) mkdirs;

install:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) install;
	cd src/examples; $(MAKE) -f $(MAKEFILE) install;

uninstall: 
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) uninstall;
	cd src/examples; $(MAKE) -f $(MAKEFILE) uninstall;

relink:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) relink;
	cd src/examples; $(MAKE) -f $(MAKEFILE) relink;

clean:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) clean;
	cd src/examples; $(MAKE) -f $(MAKEFILE) clean;

distClean:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) distClean;
	cd src/examples; $(MAKE) -f $(MAKEFILE) distClean;

execClean:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) execClean;
	cd src/examples; $(MAKE) -f $(MAKEFILE) execClean;

java:
	ant;

