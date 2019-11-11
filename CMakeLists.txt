cmake_minimum_required(VERSION 3.2)
project(coda_et
  VERSION 16.1.0
  )

include(GNUInstallDirs)
include(cmake/os.cmake)

include(FindThreads)

# This assumes you are installing into a director like
# /usr/local/opt/project_version and 
# /usr/local/etc/modulesfiles is in MODULEPATH
set(LMOD_INSTALL_PREFIX ../../etc/modulefiles)
set(INSTALL_LMOD_FILES ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(HEADER_FILES
  src/libsrc/et_data.h
  src/libsrc/et_network.h
  src/libsrc/et_private.h
  src/libsrc/et.h
  src/libsrc/etCommonNetwork.h
  #  src/libsrc/org_jlab_coda_et_EtJniAccess.h
  )
set(SOURCE_FILES
  src/libsrc/et_attachment.c
  src/libsrc/et_bridge.c
  src/libsrc/et_common.c
  src/libsrc/et_data.c
  src/libsrc/et_event.c
  src/libsrc/et_init.c
  #src/libsrc/et_jni.c
  src/libsrc/et_local.c
  src/libsrc/et_mem.c
  src/libsrc/et_network.c
  src/libsrc/et_noshare.c
  src/libsrc/et_openconfig.c
  src/libsrc/et_readwrite.c
  #src/libsrc/et_remoteclient.c
  src/libsrc/et_remote.c
  src/libsrc/et_server.c
  src/libsrc/et_statconfig.c
  src/libsrc/et_station.c
  src/libsrc/et_sysconfig.c
  src/libsrc/et_system.c
  src/libsrc/etCommonNetwork.c
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

add_library(coda_et SHARED ${SOURCE_FILES})

target_include_directories(coda_et PUBLIC 
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/src/libsrc>
  $<INSTALL_INTERFACE:include/coda/et>
  )

target_link_libraries(coda_et PUBLIC Threads::Threads dl m)
# java :( 
#$ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/linux $ENV{JAVA_HOME}/Headers)

add_executable(et_start    src/execsrc/et_start.c)
add_executable(et_monitor  src/execsrc/et_monitor.c)
add_executable(et_2_et     src/execsrc/et_2_et.c)
add_executable(et_killer   src/execsrc/et_killer.c)

target_link_libraries(et_start   PRIVATE coda_et)
target_link_libraries(et_monitor PRIVATE coda_et)
target_link_libraries(et_2_et    PRIVATE coda_et)
target_link_libraries(et_killer  PRIVATE coda_et)


include(GNUInstallDirs)
# ---------------------------------
# Configure/install modulefile
configure_file (
  etc/modulefile.in
  ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_VERSION}
  )

install(
  FILES ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_VERSION}
  DESTINATION ${LMOD_INSTALL_PREFIX}/${PROJECT_NAME}
  )

# ---------------------------------
# install header files
install(FILES ${HEADER_FILES}
  DESTINATION include/coda/et
  )
#install(FILES ${CMAKE_CURRENT_BINARY_DIR}/hcCalibrationDict_rdict.pcm
#  DESTINATION ${CMAKE_INSTALL_LIBDIR}
#  )

 


install(TARGETS coda_et  et_start  et_monitor et_2_et   et_killer
  EXPORT ${PROJECT_NAME}Targets
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
)
#add_executable(et_consumer src/examples/et_consumer.c)
#add_executable(et_producer src/examples/et_producer.c)

#include_directories($ENV{CODA}/common/include $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/linux $ENV{JAVA_HOME}/Headers)

#target_include_directories(et_16_1_GIT src/libsrc $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/linux $ENV{JAVA_HOME}/Headers)

# -------------------------
# install library config
include(CMakePackageConfigHelpers)

# Install and export targets
install(EXPORT ${PROJECT_NAME}Targets
    FILE ${PROJECT_NAME}Targets.cmake
    NAMESPACE ${PROJECT_NAME}::
    DESTINATION lib/${PROJECT_NAME}
    )

set(TARGETS_INSTALL_PATH lib/${PROJECT_NAME}/${PROJECT_NAME}Targets.cmake)
configure_package_config_file(
  cmake/${PROJECT_NAME}Config.cmake.in  
  ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}Config.cmake
  INSTALL_DESTINATION lib/${PROJECT_NAME}
  PATH_VARS TARGETS_INSTALL_PATH
  )

  write_basic_package_version_file("${PROJECT_NAME}ConfigVersion.cmake"
    VERSION ${VERSION}
    COMPATIBILITY SameMajorVersion
    )

install(FILES
  ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}Config.cmake
  ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}ConfigVersion.cmake
  DESTINATION lib/${PROJECT_NAME}
  )

