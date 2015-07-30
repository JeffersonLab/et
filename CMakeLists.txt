cmake_minimum_required(VERSION 3.2)
project(et_15_1)

include_directories($ENV{CODA}/common/include $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/linux $ENV{JAVA_HOME}/Headers)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES
    src/libsrc/et_attachment.c
    src/libsrc/et_bridge.c
    src/libsrc/et_common.c
    src/libsrc/et_data.c
    src/libsrc/et_event.c
    src/libsrc/et_init.c
    src/libsrc/et_jni.c
    src/libsrc/et_local.c
    src/libsrc/et_mem.c
    src/libsrc/et_network.c
    src/libsrc/et_noshare.c
    src/libsrc/et_openconfig.c
    src/libsrc/et_readwrite.c
    src/libsrc/et_remoteclient.c
    src/libsrc/et_remote.c
    src/libsrc/et_server.c
    src/libsrc/et_statconfig.c
    src/libsrc/et_station.c
    src/libsrc/et_sysconfig.c
    src/libsrc/et_system.c

    src/libsrc/et_data.h
    src/libsrc/et_network.h
    src/libsrc/et_private.h
    src/libsrc/et.h
    src/libsrc/etCommonNetwork.h

    src/execsrc/et_start.c
    src/execsrc/et_monitor.c
    src/execsrc/et_2_et.c
    src/execsrc/et_killer.c

    src/examples/et_consumer.c
    src/examples/et_producer.c
    )

add_executable(et_15_1 ${SOURCE_FILES})