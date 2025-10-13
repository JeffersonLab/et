#  **Java JARs Folder**

This folder contains jar files used for executing EVIO code using the Java API. The main jar file was made using Java 17.

Note that the com.loox jar files previously located here are now retrieved from https://coda.jlab.org/maven. By using the
et-jar-with-dependencies.jar file, these no longer need to be explicitly included in your Java `CLASSPATH` environment
variable.

As an example, to start the Monitoring GUI program from the top directory of ET, do:
```
java -cp java/jars/et-16.6.0-all.jar org.jlab.coda.et.monitorGui.Monitor
```