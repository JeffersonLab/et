#
# Makefile for java ET monitor
#


JAVAC = javac

JAVAC_FLAGS = -classpath $(ET_DIR)/java:$(ET_DIR)/common/jar/jlayout30.jar:$(ET_DIR)/common/jar/jloox20.jar

JAVA_OBJS = \
	Monitor.class \
	MonitorSingleSystem.class \
	MonitorConfiguration.class \
	MonitorFonts.class \
	WholeNumberField.class

all: $(JAVA_OBJS)

clean: 
	-rm -f core *~ *.class

# SUFFIX RULES

.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $< $(JAVAC_FLAGS)
