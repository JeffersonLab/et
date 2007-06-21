#
# evio top level Makefile
#

MAKEFILE = Makefile

# define TOPLEVEL for use in making doxygen docs
TOPLEVEL = .

# list directories in which there are makefiles to be run (relative to this one)
SRC_DIRS = src/libsrc src/libsrc++ src/execsrc src/examples

# declaring a target phony skips the implicit rule search and saves time
.PHONY : first help java javaClean javaDistClean doc tar


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
	export TOPLEVEL=$(TOPLEVEL); doxygen doc/doxygen/Doxyfile
	cd doc; $(MAKE) -f $(MAKEFILE);

tar:
	-$(RM) tar/evio-2.0.tar.gz;
	tar -X tar/tarexclude -C .. -c -z -f tar/evio-2.0.tar.gz evio

# Use this pattern rule for all other targets
%:
	@for i in $(SRC_DIRS); do \
	   $(MAKE) -C $$i -f $(MAKEFILE) $@; \
	done;
        
# If vxworks, overwrite above pattern rule to print message and return.
# This must come AFTER above rule.
ifeq ($(OS), vxworks)
  %:
	@echo "No vxworks stuff in evio package"
endif
