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

# if make is called here, override CODA stuff with ET stuff
ifeq ($(MAKELEVEL), 0)
  include ../../Makefile.local
endif

ifndef BMS_HOME
  BMS_HOME = $(shell pwd)/../BMS
endif

# Things for building executables
include $(BMS_HOME)/Makefile.bin

