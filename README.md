----------------------------
# **ET 16.4 SOFTWARE PACKAGE**
----------------------------

The ET or Event Transfer system is used to transfer events (data buffers)
between different user processes using shared memory. It's designed
for speed and reliability. Remote access to ET system data is possible
over TCP sockets as well.
 
It was written by Carl Timmer of the Data Acquisition group of the
Thomas Jefferson National Accelerator Facility.

This software runs on Linux and Mac OSX.
VxWorks is no longer supported in this version.

You must install Java version 8 or higher if you plan to compile
the ET java code and run it. If you're using the jar file from the CODA
website, Java 8 or higher is necessary since it was compiled with that version.


**The home page is:**

  https://coda.jlab.org/drupal/content/event-transfer-et/

**All code is contained in the github repository (default = master branch):**

  https://github.com/JeffersonLab/et

  
-----------------------------
## **Documentation**

Documentation is contained in the repository but may also be accessed at the home site:

Documentation Type | Link
------------ | -------------
PDF User's Guide | https://coda.jlab.org/drupal/content/et-164-users-guide
Javadoc | https://coda.jlab.org/drupal/content/et-164-javadoc
Javadoc for developers | https://coda.jlab.org/drupal/content/et-164-developers-javadoc
Doxygen doc for C | https://coda.jlab.org/drupal/content/et-164-doxygen


----------------------------
# **C LIBRARIES**
----------------------------

There are 3 separate C libraries that are built. The first is the full ET library,
libet, with all of the functionality. The second is a library, libet_remote,
for remote users of an existing ET system. This library has none of the code
to start up an ET system, but does communicate over the network with an existing system.
Originally, this was done because ET clients needed to run on the VxWorks operating
system and this avoided having to port difficult code to it. Finally, the third library,
et_jni, is the JNI shared library to allow Java classes to wrap C client code.
This results in significant performance gain since a C-based ET system can be used
instead of a Java-based system which is much less performant. This library can only
be made if a Java JDK is installed on the user’s host.

-----------------------------
## **Compiling using SCons**

From the SCons website:
>SCons is an Open Source software
>construction tool -- that is, a next-generation build tool.
>Think of SCons as an improved, cross-platform substitute for
>the classic Make utility with integrated functionality similar
>to autoconf/automake and compiler caches such as ccache. In short,
>SCons is an easier, more reliable and faster way to build software.

SCons is written in python, thus to use this build system with ET,
both python and SCons packages need previous installation. If your
system does not have one or the other, go to the http://www.python.org/
and http://www.scons.org/ websites for downloading.

The SCons files needed for compiling are already included as part of
the ET distribution. To compile, the user needs merely to run "scons"
in the top level ET directory. To compile and install libraries and
header files, first define the CODA environmental variable containing
the directory in which to install things and then run:

    scons install

The following are the options of _**scons**_:

Command | Action
------------ | -------------
scons -h             |         print out help
scons                |         make C libraries
scons -c             |         remove all generated files
scons install        |    make C libraries,
.                    |   place libraries in architecture-specific lib directory
.                    |   make binaries
.                    |   place execsrc binaries in architecture-specific bin directory
.                    |   place example binaries in bin/examples directory
.                    |   place test binaries in bin/test directory
.                    |   place headers in include directory
scons install -c     |    uninstall libs, headers, and binaries
scons tar            |    create a tar file (et-16.x.tgz) of the ET top level
.                    |   directory and put in ./tar directory
scons doc            |    generate html documentation from javadoc and doxygen
.                    |   comments in the source code and put in ./doc directory
scons undoc          |    remove the doc/javadoc directory
scons --dbg          |    compile with debug flag
scons --32bits       |    compile 32bit libraries & executables on 64bit system
scons --prefix=<dir> | use base directory <dir> when doing install.
.                    | Defaults to CODA environmental variable.
.                    | Libs go in <dir>/<arch>/lib, headers in <dir>/<arch>/include
.                    | and executables in <dir>/<arch>/bin
scons --incdir=<dir> | copy header files to directory <dir> when doing install
.                    | (takes precedence over --prefix or default location)
scons --libdir=<dir> | copy library files to directory <dir> when doing install
.                    | (takes precedence over --prefix or default location)
scons --bindir=<dir> | copy executable files to directory <dir> when doing install
.                    | (takes precedence over --prefix or default location)


You can see these options by running "scons -h"


Note that currently only Linux and Darwin (Mac OSX)
operating systems are supported. The libraries and executables
are placed into the $CODA/<arch>/lib and bin subdirectories
(eg. ...Linux-x86_64/lib).  Be sure to change your LD_LIBRARY_PATH
environmental variable to include the correct lib directory.


-----------------------------
## **Compiling using CMake**


Evio can also be compiled with cmake using the included CMakeLists.txt file.
To build the libraries and executables on the Mac:

1. cd <et dir>
2. mkdir build
3. cd build
4. cmake .. –DCMAKE_BUILD_TYPE=Release
5. make

In order to compile all the examples as well, place –DMAKE_EXAMPLES=1 on the cmake command line.
The above commands will place everything in the current “build” directory and will keep generated
files from mixing with the source and config files.

In addition to a having a copy in the build directory, installing the library, binary and include
files can be done by calling cmake in 2 ways:

1. cmake .. –DCMAKE_BUILD_TYPE=Release –DCODA_INSTALL=&lt;install dir&gt;
2. make install

or

1. cmake .. –DCMAKE_BUILD_TYPE=Release
2. make install

The first option explicitly sets the installation directory. The second option installs in the directory
given in the CODA environmental variable. If cmake was run previously, remove the CMakeCache.txt file so
new values are generated and used.

To uninstall simply do:

    make uninstall


-----------------------------
## **Macs**

A word about making C libs on the Mac. The jni.h and jni_md.h header files
supplied with the java JDK have lived in different places over the years.
These are needed for compilation. The best way to facilitate that is to
set your JAVA_HOME environmental variable so that these includes can be found.
These days the Oracle Java is placed so that you need to do a:

    setenv JAVA_HOME /Library/Java/JavaVirtualMachines/<jdk_dir>/Contents/Home

where <jdk_dir> is the directory in which your specific java distribution lives.
This package assumes the header files are found in $JAVA_HOME/include and in
$JAVA_HOME/include/darwin.


----------------------------
# **Java**
----------------------------

One can download the pre-built et-16.4.jar file from:

  https://coda.jlab.org/drupal/content/et-164

One can also find the pre-built et-16.4.jar file in the repository in the java/jars/java8
directory built with Java 8, or in the java/jars/java15 directory built with Java 15,
or it can be generated. The generated jar file is placed in build/lib.
In any case, put the jar file into your classpath and run your java application.

If you're using the pre-built jar file, Java version 8 or higher is necessary since
it was compiled with that version. Also, when generating it, it’s advisable to use
Java version 8 or higher since all other pre-built CODA jar files have been compiled with Java 8.
If you wish to recompile the java part of ET, ant must be installed
on your system (http://ant.apache.org):
  
    cd <et dir>
    ant

To get a list of options with ant, type "ant help":

     ant  help       - print out usage
     ant  env        - print out build file variables' values
     ant  compile    - compile java files
     ant  clean      - remove class files
     ant  cleanall   - remove all generated files
     ant  jar        - compile and create et jar file
     and  install    - create et jar file, remove other version et jars,
                       and install all jars into 'prefix'
                       if given on command line by -Dprefix=dir',
                       else install into CODA if defined" />
     ant  uninstall  - remove all current jar files previously installed
                       into 'prefix' or CODA
     ant  all        - clean, compile and create et jar file
     ant  javadoc    - create javadoc documentation
     ant  developdoc - create javadoc documentation for developer
     ant  undoc      - remove all javadoc documentation
     ant  prepare    - create necessary directories


To generate a new ET jar file, type "ant jar" which will
create the file and place it in build/lib.

Included in the java/jars subdirectory are all auxiliary jar files used
by the GUI graphics. These are installed when executing "ant install"
and uninstalled when executing "ant uninstall".


----------------------------
# **Generating Documentation**
----------------------------

All documentation links are provided above.

However, if using the downloaded distribution, some of the documentation
needs to be generated and some already exists. For existing docs look in
doc/users_guide and doc/developers_guide for pdf and Microsoft Word
format documents.

Some of the documentation is in the source code itself and must be generated
and placed into its own directory.
The java code is documented with, of course, javadoc and the C code is
documented with doxygen comments (identical to javadoc comments).

To generate all the these docs, from the top level directory type:

    scons doc
    
To remove it all type:

    scons undoc

The javadoc is placed in the doc/javadoc directory.
The doxygen docs for C code are placed in the doc/doxygen/C/html directory.
To view the html documentation, just point your browser to the index.html
file in that directory.
In the C html docs, select the "modules" option to get a nice summary
of all routines needed by a user.

Alternatively, just the java documentation can be generated by typing

    ant javadoc
    
for user-level documentation, or

    ant developdoc
    
for developer-level documentation. To remove it:

    ant undoc

-----------------------------
## **Copyright**

For any issues regarding use and copyright, read the NOTICE file.

