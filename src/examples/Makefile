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
# and to include it if make is run from this dir or the top-level package
# directory.
TOPLEVEL = $(shell pwd)/../..

ifndef BMS_HOME
  $(error "Need to define BMS_HOME")
endif

# Things for building executables
include $(BMS_HOME)/Makefile.bin

