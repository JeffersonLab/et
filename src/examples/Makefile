#
# ET examples makefile
#

PACKAGES = et
MODULE_NAME = et

# include files are in et/src directory
CFLAGS    += -I../src
CXXFLAGS  += -I../src

# list your main files here if you don't want all files with main compiled
# MAIN_FILES =

# Setting TOPLEVEL tells Makefile.common where to find Makefile.local
# and to include it.
ifeq ($(MAKELEVEL), 0)
  ifndef TOPLEVEL
    TOPLEVEL = $(shell pwd)/../..
  endif
endif

ifndef BMS_HOME
  $(error "Need to define BMS_HOME")
endif

# Things for building executables
include $(BMS_HOME)/Makefile.bin

