#
# Makefile for ET java examples
#


JAVAC = javac

JAVAC_FLAGS = -classpath $(ET_DIR)/java

JAVA_OBJS = \
	Consumer.class \
	StartEt.class \
	Producer.class

all: $(JAVA_OBJS)

clean: 
	-rm -f core *~ *.class

# SUFFIX RULES

.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $< $(JAVAC_FLAGS)
