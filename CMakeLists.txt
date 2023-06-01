# To use this, do the following the first time:
#   % mkdir build
#   % cd build
#   % cmake .. -DCMAKE_BUILD_TYPE=Release
#   % make (install)
#
#
# If you want the examples compiled also, then this instead
#  % cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_EXAMPLES=True
#
#
# After doing this once can do the following to recompile
#  % cd <top level evio dir>
#  % cmake --build build (--target install)
#
#
# The installation will default to the CODA env variable if it's defined
# This can be overridden on command line (in build dir) with:
#   % cmake -DCODA_INSTALL=<my_dir> ..
#   % make install


# This call must be placed BEFORE "project" command
cmake_minimum_required(VERSION 3.3)

project(evio VERSION 16.4)

set(CMAKE_DEBUG_POSTFIX -dbg)


# Place libs & binaries in build/lib and bin (this is not for installation)
set(LIBRARY_OUTPUT_PATH    ${CMAKE_BINARY_DIR}/lib)
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_BINARY_DIR}/bin)


# Set architecture-related string for installation consistent with scons file
set(ARCH ${CMAKE_HOST_SYSTEM_NAME}-${CMAKE_HOST_SYSTEM_PROCESSOR})
message(STATUS "ARCH = " ${ARCH})

# Install into $INSTALL_DIR directory (general installation)
if (DEFINED INSTALL_DIR)
    message(STATUS "INSTALL_DIR = " ${INSTALL_DIR})
    set(INSTALL_DIR_DEFINED 1)
    set(CMAKE_INSTALL_PREFIX ${INSTALL_DIR})
    set(CMAKE_INSTALL_INCLUDEDIR ${INSTALL_DIR}/include)
    message(STATUS "CMAKE_INSTALL_PREFIX reset to " ${CMAKE_INSTALL_PREFIX})
else()
    # Install into $CODA_INSTALL directory if using as part of CODA
    if (DEFINED CODA_INSTALL)
        message(STATUS "CODA_INSTALL = " ${CODA_INSTALL})
        set(INSTALL_DIR_DEFINED 1)
        set(CMAKE_INSTALL_PREFIX ${CODA_INSTALL}/${ARCH})
        set(CMAKE_INSTALL_INCLUDEDIR ${CODA_INSTALL}/common/include)
        message(STATUS "CMAKE_INSTALL_PREFIX reset to " ${CMAKE_INSTALL_PREFIX})
    # Install into $CODA directory
    elseif (DEFINED ENV{CODA})
        message(STATUS "CODA = " $ENV{CODA})
        set(INSTALL_DIR_DEFINED 1)
        set(CMAKE_INSTALL_PREFIX $ENV{CODA}/${ARCH})
        set(CMAKE_INSTALL_INCLUDEDIR $ENV{CODA}/common/include)
        message(STATUS "CMAKE_INSTALL_PREFIX reset to " ${CMAKE_INSTALL_PREFIX})
    else()
        message(STATUS "Use -DINSTALL_DIR= or -DCODA_INSTALL=<dir> on cmd line, set \"CODA\" env var, or it'll be installed in /usr/local")
    endif()
endif()


set(HEADER_FILES
        src/libsrc/et_data.h
        src/libsrc/et_network.h
        src/libsrc/et_private.h
        src/libsrc/et.h
        src/libsrc/etCommonNetwork.h
        src/libsrc/et_fifo.h
        )

set(SOURCE_FILES
        src/libsrc/et_attachment.c
        src/libsrc/et_bridge.c
        src/libsrc/et_common.c
        src/libsrc/et_data.c
        src/libsrc/et_event.c
        src/libsrc/et_init.c
        src/libsrc/et_local.c
        src/libsrc/et_mem.c
        src/libsrc/et_network.c
        src/libsrc/et_noshare.c
        src/libsrc/et_openconfig.c
        src/libsrc/et_readwrite.c
        src/libsrc/et_remote.c
        src/libsrc/et_server.c
        src/libsrc/et_statconfig.c
        src/libsrc/et_station.c
        src/libsrc/et_sysconfig.c
        src/libsrc/et_system.c
        src/libsrc/etCommonNetwork.c
        src/libsrc/et_fifo.c
        )

set (REMOTE_SOURCE_FILES
        src/libsrc/et_init.c
        src/libsrc/et_network.c
        src/libsrc/etCommonNetwork.c
        src/libsrc/et_openconfig.c
        src/libsrc/et_remote.c
        src/libsrc/et_remoteclient.c
        src/libsrc/et_statconfig.c
        src/libsrc/et_sysconfig.c
        )

set (EXEC_SOURCE_FILES
        src/execsrc/et32_2_et64.c
        src/execsrc/et_2_et.c
        src/execsrc/et_killer.c
        src/execsrc/et_monitor.c
        src/execsrc/et_printIFinfo.c
        src/execsrc/et_start.c
        src/execsrc/et_start_fifo.c
        )

set (EXAMPLE_FILES
        src/examples/et_blaster.c
        src/examples/et_consumer.c
        src/examples/et_producer.c
        src/examples/et_fifoProducer.c
        src/examples/et_fifoConsumer.c
        src/examples/et_producerTest.c
        src/examples/et_connectionTest.c
        )

# ET build is optional and set it to ON by default
option(BUILD_EXAMPLES "Build ET-related example programs" ON)

# documentation build is optional and set it to OFF by default
option(BUILD_DOC "Build documentation" OFF)

# Search for doxygen executable
find_package(Doxygen)
if (DOXYGEN_FOUND AND BUILD_DOC)
    message(STATUS "Doxygen found")

    # set output file
    set(DOXYGEN_OUT ${CMAKE_CURRENT_SOURCE_DIR}/doc/doxygen/DoxyfileC)

    # note the option ALL which allows to build the docs together with the application
    add_custom_target( doc_doxygen ALL
            COMMAND ${DOXYGEN_EXECUTABLE} ${DOXYGEN_OUT}
            WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
            COMMENT "Generating API documentation with Doxygen"
            VERBATIM )
endif()


# Shared evio library
add_library(et SHARED ${SOURCE_FILES})
set_target_properties(et PROPERTIES DEBUG_POSTFIX ${CMAKE_DEBUG_POSTFIX})
target_link_libraries(et PUBLIC pthread dl m)


# Remote evio library
add_library(et_remote SHARED ${REMOTE_SOURCE_FILES})
set_target_properties(et_remote PROPERTIES DEBUG_POSTFIX ${CMAKE_DEBUG_POSTFIX})
target_link_libraries(et_remote PUBLIC pthread dl m)


# Search for JNI header so ET's jni interface library can be made
find_package(JNI 1.8)
if (JNI_FOUND)
    message (STATUS "JNI header found")
    add_library(et_jni SHARED src/libsrc/et_jni.c)
    set_target_properties(et_jni PROPERTIES DEBUG_POSTFIX ${CMAKE_DEBUG_POSTFIX})
    include_directories(et_jni PUBLIC ${JNI_INCLUDE_DIRS})
    target_link_libraries(et_jni PUBLIC et)
    install(TARGETS et_jni LIBRARY DESTINATION lib)
endif()


# Executables
foreach(fileName ${EXEC_SOURCE_FILES})
    # Get file name with no directory or extension as executable name
    get_filename_component(execName ${fileName} NAME_WE)
    # Create executable from file
    add_executable(${execName} ${fileName})
    # Put debug extension on if applicable
    set_target_properties(${execName} PROPERTIES DEBUG_POSTFIX ${CMAKE_DEBUG_POSTFIX})
    # Include directories
    include_directories(${execName} PUBLIC src/libsrc src/multiFifo)
    # Needs these libs
    target_link_libraries(${execName} et)

    # Only install if installation directory has been defined
    if (DEFINED INSTALL_DIR_DEFINED)
        # Install into bin/testC dir
        install(TARGETS ${execName} RUNTIME DESTINATION bin)
    endif()
endforeach(fileName)


# Example Programs
if(BUILD_EXAMPLES)
    foreach(fileName ${EXAMPLE_FILES})
        get_filename_component(execName ${fileName} NAME_WE)
        add_executable(${execName} ${fileName})
        set_target_properties(${execName} PROPERTIES DEBUG_POSTFIX ${CMAKE_DEBUG_POSTFIX})
        include_directories(${execName} PUBLIC src/libsrc)
        target_link_libraries(${execName} et pthread)

        if (DEFINED INSTALL_DIR_DEFINED)
            install(TARGETS ${execName} RUNTIME DESTINATION bin/examples)
        endif()
    endforeach(fileName)


    # fileToEvent done a little differently
    get_filename_component(execName1 src/examples/fileToEvent.c NAME_WE)
    add_executable(${execName1} src/examples/fileToEvent.c src/examples/etInsertEventR.c)
    set_target_properties(${execName1} PROPERTIES DEBUG_POSTFIX ${CMAKE_DEBUG_POSTFIX})
    include_directories(${execName1} PUBLIC src/libsrc)
    target_link_libraries(${execName1} et pthread)
    if (DEFINED INSTALL_DIR_DEFINED)
        install(TARGETS ${execName1} RUNTIME DESTINATION bin/examples)
    endif()

endif()


# Only install if installation directory has been defined
if (DEFINED INSTALL_DIR_DEFINED)
    # Installation defaulting to ${CMAKE_INSTALL_PREFIX}/lib or include
    install(TARGETS et et_remote LIBRARY DESTINATION lib)
    install(FILES ${HEADER_FILES} DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
endif()


# Uninstall target
if(NOT TARGET uninstall)
    configure_file(
            "${CMAKE_CURRENT_SOURCE_DIR}/cmake_uninstall.cmake.in"
            "${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake" IMMEDIATE @ONLY)

    add_custom_target(uninstall
            COMMAND ${CMAKE_COMMAND} -P ${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake)
endif()

