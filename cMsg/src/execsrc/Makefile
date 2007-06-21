#
# $Header$
#
#--------------------------------------------------------
# Cannot compile EPICS stuff at the moment
#
# To compile cMsgCAGateway:
#  1) comment out NO_MAIN_FILES & NO_DEP_OBJS
#  2) uncomment:
#      a) OTHER_TARGETS
#      b) PACKAGES
#      c) cMsgCAGateway rule
#
#--------------------------------------------------------

ifndef BMS_HOME
  $(error "Need to define BMS_HOME")
endif

ifeq ($(OS), vxworks)
  $(error "This makefile is for unix, use Makefile.vxworks-ppc")
endif

# Setting TOPLEVEL tells Makefile.common where to find Makefile.local
# and to include it if make is run from this dir or the top-level package
# directory.
TOPLEVEL = ../..

# use cMsg libraries
PACKAGES = cMsg
#PACKAGES = cMsg:expat:EPICS

# only here for print out
MODULE_NAME = cmsg

# list the main files that you don't want compiled
NO_MAIN_FILES = cMsgCAGateway
# list files for which you want no dependencies generated
NO_DEP_OBJS   = cMsgCAGateway.o epics_server.o

#OTHER_TARGETS += $(BIN_DIR)/cMsgCAGateway

CFLAGS   += -I../libsrc -I../libsrc++
CXXFLAGS += -I../libsrc -I../libsrc++

include $(BMS_HOME)/Makefile.common

include $(BMS_HOME)/Makefile.bin

#cMsgCAGateway: cMsgCAGateway.o epics_server.o
#	$(LD) $(LD_DIRS) $(LD_FLAGS) $(OBJ_DIR)/cMsgCAGateway.o $(OBJ_DIR)/epics_server.o $(LD_LIBS) $(MISC_LIBS) -o $(BIN_DIR)/$@
        
