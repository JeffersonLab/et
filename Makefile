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


.PHONY : all mkdirs src clean distClean execClean install uninstall relink


all: src

src:
	cd src;      $(MAKE) -f $(MAKEFILE);
	cd coda;     $(MAKE) -f $(MAKEFILE);
	cd examples; $(MAKE) -f $(MAKEFILE);

env:
	cd src;      $(MAKE) -f $(MAKEFILE) env;
	cd coda;     $(MAKE) -f $(MAKEFILE) env;
	cd examples; $(MAKE) -f $(MAKEFILE) env;

mkdirs:
	cd src;      $(MAKE) -f $(MAKEFILE) mkdirs;
	cd coda;     $(MAKE) -f $(MAKEFILE) mkdirs;
	cd examples; $(MAKE) -f $(MAKEFILE) mkdirs;

install:
	cd src;      $(MAKE) -f $(MAKEFILE) install;
	cd coda;     $(MAKE) -f $(MAKEFILE) install;
	cd examples; $(MAKE) -f $(MAKEFILE) install;

uninstall: 
	cd src;      $(MAKE) -f $(MAKEFILE) uninstall;
	cd coda;     $(MAKE) -f $(MAKEFILE) uninstall;
	cd examples; $(MAKE) -f $(MAKEFILE) uninstall;

relink:
	cd src;      $(MAKE) -f $(MAKEFILE) relink;
	cd coda;     $(MAKE) -f $(MAKEFILE) relink;
	cd examples; $(MAKE) -f $(MAKEFILE) relink;

clean:
	cd src;      $(MAKE) -f $(MAKEFILE) clean;
	cd coda;     $(MAKE) -f $(MAKEFILE) clean;
	cd examples; $(MAKE) -f $(MAKEFILE) clean;

distClean:
	cd src;      $(MAKE) -f $(MAKEFILE) distClean;
	cd coda;     $(MAKE) -f $(MAKEFILE) distClean;
	cd examples; $(MAKE) -f $(MAKEFILE) distClean;

execClean:
	cd src;      $(MAKE) -f $(MAKEFILE) execClean;
	cd coda;     $(MAKE) -f $(MAKEFILE) execClean;
	cd examples; $(MAKE) -f $(MAKEFILE) execClean;

