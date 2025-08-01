# **C Library**

There are 3 separate C libraries created from source files here:
* The first is the full ET library, `libet`, with all of the functionality. 
* The second is a library, `libet_remote`,
for remote users of an existing ET system. This library has none of the code
to start up an ET system, but does communicate over the network with an existing system.
Originally, this was done because ET clients needed to run on the VxWorks operating
system and this avoided having to port difficult code to it. 
* The third library, `et_jni`, is the JNI shared library to allow Java classes to wrap C client code. This results in significant performance gain since a C-based ET system can be used instead of a Java-based system which is much less performant. This library can only be made if a Java JDK is installed on the user’s host (see find_package(JNI 1.8) in CMakeLists.txt).
