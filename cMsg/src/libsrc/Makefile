#
# cMsg C library
#

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

MODULE_NAME = cmsg

# set optimization level
OPTIMIZE = 3

# filter this out of the regular libraries
NO_OBJS = dummyDomain.o

# if you don't want either of these libs, comment out the definition(s)
MAKE_SHARED_LIB = make_me_please
#MAKE_STATIC_LIB = make_me_please

# Things common to all builds live in Makefile.common
include $(BMS_HOME)/Makefile.common

# Things common to building libraries live in Makefile.libs
include $(BMS_HOME)/Makefile.libs
