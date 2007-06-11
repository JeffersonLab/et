#
# ET top level Makefile
#

MAKEFILE = Makefile

.PHONY : all src env mkdirs install uninstall relink clean distClean execClean java tar doc


all: src

src:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE);
	cd src/examples; $(MAKE) -f $(MAKEFILE);

vxworks-$(ARCH):
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE).vxworks-$(ARCH);

env:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) env;
	cd src/examples; $(MAKE) -f $(MAKEFILE) env;

mkdirs:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) mkdirs;
	cd src/examples; $(MAKE) -f $(MAKEFILE) mkdirs;
	ant prepare;

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
	ant clean;

distClean:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) distClean;
	cd src/examples; $(MAKE) -f $(MAKEFILE) distClean;
	ant cleanall;

execClean:
	cd src/libsrc;   $(MAKE) -f $(MAKEFILE) execClean;
	cd src/examples; $(MAKE) -f $(MAKEFILE) execClean;

java:
	ant;

doc:
	ant javadoc;

tar:
	-$(RM) tar/et-9.0.tar.gz;
	tar -X tar/tarexclude -C .. -c -z -f tar/et-9.0.tar.gz et

