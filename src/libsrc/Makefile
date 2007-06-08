#
# ET src makefile
#

MODULE_NAME = et

# filter this out of the regular libraries
NO_OBJS = et_remoteclient.o et_monitor.o

# objects for making a remote library
ROBJS = et_statconfig.o \
	et_openconfig.o \
	et_init.o \
	et_sysconfig.o \
	et_remote.o \
	et_network.o \
	et_remoteclient.o

RLINK_OBJS = $(addprefix $(OBJ_DIR)/,$(ROBJS))

OTHER_TARGETS += $(LIB_DIR)/libet_remote.so

# if you don't want either of these libs, comment out the definition(s)
MAKE_SHARED_LIB = make_me_please
MAKE_STATIC_LIB = make_me_please

# if make is called here, override CODA stuff with ET stuff
ifeq ($(MAKELEVEL), 0)
  include ../Makefile.local
endif

ifndef BMS_HOME
  BMS_HOME = $(shell pwd)/../BMS
endif

# Things for building libs
include $(BMS_HOME)/Makefile.libs

# This new target must come AFTER the makefile include above or else it's the first target.
# The other stuff must come BEFORE since the included makefiles have a target defined.
$(LIB_DIR)/libet_remote.so: $(ROBJS)
	$(LD) $(LD_SO_FLAGS) $(LD_DIRS) $(RLINK_OBJS) $(LD_LIBS) -o $@
