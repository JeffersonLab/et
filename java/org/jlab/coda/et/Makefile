#
# Makefile for java ET
#


JAVAC = javac

JAVAC_FLAGS = -classpath $(ET_DIR)/java

JAVA_OBJS = \
	SystemUse.class \
	SystemCreate.class \
	Constants.class \
	EtException.class \
	EtBusyException.class \
	EtEmptyException.class \
	EtExistsException.class \
	EtReadException.class \
	EtTimeoutException.class \
	EtTooManyException.class \
	EtWakeUpException.class \
	EtWriteException.class \
	AllData.class \
	ProcessData.class \
	StationData.class \
	SystemData.class \
	AttachmentData.class \
	Event.class \
	EventList.class \
	EventSelectable.class \
	Attachment.class \
	AttachmentLocal.class \
	StationConfig.class \
	Station.class \
	StationLocal.class \
	StationSelection.class \
	SystemConfig.class \
	SystemOpenConfig.class \
	SystemOpen.class \
	SystemTcpServer.class \
	SystemUdpServer.class \
	EtMonitor.class

all: $(JAVA_OBJS)

clean: 
	-rm -f core *~ *.class et-9.0.jar

# SUFFIX RULES

.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $< $(JAVAC_FLAGS)
