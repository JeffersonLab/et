#
# et top level Makefile
#

MAKEFILE = Makefile

# if using vxworks, use different set of the lowest level makefiles
ifeq ($(OS), vxworks)
  ifdef ARCH
    MAKEFILE = Makefile.$(OS)-$(ARCH)
  else
    $(error "Need to define ARCH if using OS = vxworks")
  endif
endif

# define TOPLEVEL for use in making doxygen docs
TOPLEVEL = .

# list directories in which there are makefiles to be run (relative to this one)
SRC_DIRS = src/libsrc src/examples

# targets for which we run lower-level makefiles
TARGETS = all env mkdirs install uninstall relink clean distClean execClean

# declaring a target phony skips the implicit rule search and saves time
.PHONY : java doc tar help $(TARGETS)


first: all

help:
	@echo "make [option]"
	@echo "      env       - list env variables"
	@echo "      mkdirs    - make necessary directories for C,C++"
	@echo "      install   - install all headers and compiled files for C,C++"
	@echo "      uninstall - uninstall all headers and compiled files for C,C++"
	@echo "      relink    - delete libs and executables, and relink object files"
	@echo "      clean     - delete all exec, library, object, and dependency files"
	@echo "      distClean - clean and remove hidden OS directory"
	@echo "      execClean - delete all exec and library files"

java:
	ant;

javaClean:
	ant clean;

javaDistClean:
	ant cleanall;

doc:
	ant javadoc;
	export TOPLEVEL=$(TOPLEVEL); doxygen doc/doxygen/DoxyfileC
	export TOPLEVEL=$(TOPLEVEL); doxygen doc/doxygen/DoxyfileCC
	cd doc; $(MAKE) -f $(MAKEFILE);

tar:
	-$(RM) tar/et-9.0.tar.gz;
	tar -X tar/tarexclude -C .. -c -z -f tar/et-9.0.tar.gz et

# Use the same rule for each of these targets
$(TARGETS):
	@for i in $(SRC_DIRS); do \
	   $(MAKE) -C $$i -f $(MAKEFILE) $@; \
	done;
