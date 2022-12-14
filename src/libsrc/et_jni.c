/*----------------------------------------------------------------------------*
 *  Copyright (c) 2010        Jefferson Science Associates,                   *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*
 *
 * Description:
 *      Routines to get, put, and dump events through Java's JNI interface
 *
 *----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>

#include "et_private.h"
#include "org_jlab_coda_et_EtJniAccess.h"


static int debug = 0;
static int localByteOrder;

/* cache some frequently used values */
static jclass eventImplClass;
static jfieldID fid[4];
static jmethodID constrMethodId2, constrMethodId3,
                 getPriorityVal, getDataStatusVal;


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    closeLocalEtSystem
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_closeLocalEtSystem
        (JNIEnv *env, jobject thisObj, jlong etId)
{
    et_close((et_sys_id)etId);
}

/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    killEtSystem
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_killEtSystem
        (JNIEnv *env, jobject thisObj, jlong etId)
{
    et_kill((et_sys_id) etId);
}

/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    openLocalEtSystem
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_openLocalEtSystem
        (JNIEnv *env, jobject thisObj, jstring fileName)
{
    int err;
    const char* mappedFile;
    et_sys_id id; /* (void *) */
    et_openconfig openconfig;
    jclass clazz, class1, classEventImpl;
    jmethodID mid;

    /* get C string from java arg */
    mappedFile = (*env)->GetStringUTFChars(env, fileName, NULL);
    if (mappedFile == NULL) {
        /* pending exception already thrown, need to return for it to complete */
        (*env)->ReleaseStringUTFChars(env, fileName, mappedFile);
        return;
    }
    
    /* open ET system */
    et_open_config_init(&openconfig);
    et_open_config_sethost(openconfig, ET_HOST_LOCAL); /* must be local host */
    err = et_open(&id, mappedFile, openconfig);
    if (err != ET_OK) {
        if (err == ET_ERROR_TIMEOUT) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtTimeoutException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "openLocalEtSystem: cannot open ET system in native code");
        return;
    }
    et_open_config_destroy(openconfig);

    /* store byte order of local system */
    err = etNetLocalByteOrder(&localByteOrder);
    if (err != ET_OK) {
        localByteOrder = ET_ENDIAN_LITTLE;
    }

    /* store et id in object by calling this native method */
    class1 = (*env)->GetObjectClass(env, thisObj);
    mid    = (*env)->GetMethodID(env, class1, "setLocalEtId", "(J)V");
    (*env)->CallVoidMethod(env, thisObj, mid, (jlong)id);

    /*******************************************/
    /* cache objects for efficient, future use */
    /*******************************************/
    classEventImpl = (*env)->FindClass(env, "org/jlab/coda/et/EtEventImpl");
    eventImplClass = (*env)->NewGlobalRef(env, classEventImpl);
 
    /* find id's of all the fields that we'll read/write directly to */
    fid[0] = (*env)->GetFieldID(env, classEventImpl, "id",         "I");
    fid[1] = (*env)->GetFieldID(env, classEventImpl, "length",     "I");
    fid[2] = (*env)->GetFieldID(env, classEventImpl, "byteOrder",  "I");
    fid[3] = (*env)->GetFieldID(env, classEventImpl, "control",    "[I");

    /* methods to get event's enum values */
    getPriorityVal   = (*env)->GetMethodID(env, classEventImpl, "getPriorityValue",   "()I");
    getDataStatusVal = (*env)->GetMethodID(env, classEventImpl, "getDataStatusValue", "()I");

    /* get id's of a few different constructors */
    constrMethodId2 = (*env)->GetMethodID(env, classEventImpl, "<init>", "(IIIIIIIIZLjava/nio/ByteBuffer;)V");
    constrMethodId3 = (*env)->GetMethodID(env, classEventImpl, "<init>", "(IIIIIIIIII[IZLjava/nio/ByteBuffer;)V");
    
    if (debug) printf("\nopenLocalEtSystem (native) : done, opened ET system\n\n");
}

/*
 * The following routine WORKS but is slower than
 * the version in which the buffer is set in Java.
 */
 
/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    getEvents
 * Signature: (JIIIIII)[Lorg/jlab/coda/et/EtEvent;
 */
JNIEXPORT jobjectArray JNICALL Java_org_jlab_coda_et_EtJniAccess_getEvents
        (JNIEnv *env , jobject thisObj, jlong etId, jint attId,
         jint mode, jint sec, jint nsec, jint count)
{
    int i, j, numread, status, biteOrder;
    void *data;
    et_event *pe[count];
    jclass clazz;
    jboolean isCopy;
    jint* intArrayElems;
    jintArray controlInts;
    jobjectArray eventArray;
    jobject event, byteBuf;

    /* translate timeout */
    struct timespec deltaTime;
    deltaTime.tv_sec  = sec;
    deltaTime.tv_nsec = nsec;

if (debug) printf("getEvents (native) : will attempt to get events\n");

    /* reading array of up to "count" events */
    status = et_events_get((et_sys_id)etId, (et_att_id)attId, pe, mode, &deltaTime, count, &numread);
    if (status != ET_OK) {
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_DEAD");
        }
        else if (status == ET_ERROR_WAKEUP) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtWakeUpException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_WAKEUP");
        }
        else if (status == ET_ERROR_TIMEOUT) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtTimeoutException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_TIMEOUT");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_CLOSED");
        }
        else if (status == ET_ERROR_BUSY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtBusyException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_BUSY");
        }
        else if (status == ET_ERROR_EMPTY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtEmptyException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_EMPTY");
        }
        else if (status == ET_ERROR_READ) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtReadException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_READ");
        }
        else if (status == ET_ERROR_WRITE) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtWriteException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_WRITE");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
            (*env)->ThrowNew(env, clazz, "getEvents (native): ET_ERROR_REMOTE");
        }
        
        return NULL;
    }

    /* create array of EventImpl objects */
    eventArray = (*env)->NewObjectArray(env, numread, eventImplClass, NULL);

    /* fill array */
    for (i=0; i < numread; i++) {
        /*printf("getEvents (native) : data for event %d = %d\n", i, *((int *)pe[i]->pdata));*/

        /* create control int array */
        controlInts   = (*env)->NewIntArray(env, ET_STATION_SELECT_INTS);
        intArrayElems = (*env)->GetIntArrayElements(env, controlInts, &isCopy);
        for (j=0; j < ET_STATION_SELECT_INTS; j++) {
            intArrayElems[j] = pe[i]->control[j];
        }
        if (isCopy == JNI_TRUE) {
            (*env)->ReleaseIntArrayElements(env, controlInts, intArrayElems, 0);
        }
        
        /* If we're on a little endian machine, int args will be swapped as
           they go through the jni interface. We don't want this for the int
           designating the byte order, so swap it here to compensate. */
        biteOrder = pe[i]->byteorder;
        if (localByteOrder == ET_ENDIAN_LITTLE) {
            biteOrder = ET_SWAP32(biteOrder);
        }

        /* wrap data pointer in ByteBuffer object */
        et_event_getdata(pe[i], &data);
        byteBuf = (*env)->NewDirectByteBuffer(env, data, (jlong) pe[i]->memsize);

        /* create event object */
        event = (*env)->NewObject(env, eventImplClass, constrMethodId3, /* constructor args ... */
        (jint)pe[i]->memsize, (jint)pe[i]->memsize, (jint)pe[i]->datastatus,
        (jint)pe[i]->place,   (jint)pe[i]->age,     (jint)pe[i]->owner,
        (jint)pe[i]->modify,  (jint)pe[i]->length,  (jint)pe[i]->priority,
        (jint)biteOrder, controlInts, (jboolean)pe[i]->temp, byteBuf);
      
        /* put event in array */
        (*env)->SetObjectArrayElement(env, eventArray, i, event);

        /* get rid of uneeded references - good if numread is big */
        (*env)->DeleteLocalRef(env, event);
        (*env)->DeleteLocalRef(env, controlInts);
        (*env)->DeleteLocalRef(env, byteBuf);
    }

if (debug) printf("getEvents (native) : filled array!\n");
   
    /* return the array */
    return eventArray;
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    newEvents
 * Signature: (JIIIIII)[Lorg/jlab/coda/et/EtEventImpl;
 */
JNIEXPORT jobjectArray JNICALL Java_org_jlab_coda_et_EtJniAccess_newEvents__JIIIIII
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId, jint mode,
         jint sec, jint nsec, jint count, jint size)
{
    int i, numread, status, biteOrder;
    void *data;
    et_event *pe[count];
    jclass clazz;
    jobjectArray eventArray;
    jobject event, byteBuf;

    /* translate timeout */
    struct timespec deltaTime;
    deltaTime.tv_sec  = sec;
    deltaTime.tv_nsec = nsec;

    /*if (debug) printf("newEvents (native) : will attempt to get new events\n");*/
    
    /* reading array of up to "count" events */
    status = et_events_new((et_sys_id)etId, (et_att_id)attId, pe, mode,
                            &deltaTime, (size_t)size, count, &numread);
    if (status != ET_OK) {
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_WAKEUP) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtWakeUpException");
        }
        else if (status == ET_ERROR_TIMEOUT) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtTimeoutException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
        }
        else if (status == ET_ERROR_BUSY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtBusyException");
        }
        else if (status == ET_ERROR_EMPTY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtEmptyException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        
        (*env)->ThrowNew(env, clazz, "newEvents (native): cannot get new events");
        return NULL;
    }

    /* create array of EventImpl objects */
    eventArray = (*env)->NewObjectArray(env, numread, eventImplClass, NULL);    
        
    /* fill array */
    for (i=0; i < numread; i++) {        
        /* If we're on a little endian machine, int args will be swapped as
           they go through the jni interface. We don't want this for the int
           designating the byte order, so swap it here to compensate. */
        biteOrder = pe[i]->byteorder;
        if (localByteOrder == ET_ENDIAN_LITTLE) {
            biteOrder = ET_SWAP32(biteOrder);
        }

        /* wrap data pointer in ByteBuffer object */
        et_event_getdata(pe[i], &data);
        byteBuf = (*env)->NewDirectByteBuffer(env, data, (jlong) pe[i]->memsize);

        /* create event object */
        event = (*env)->NewObject(env, eventImplClass, constrMethodId2, /* constructor args ... */
        (jint)pe[i]->memsize, (jint)pe[i]->memsize,
        (jint)pe[i]->place,   (jint)pe[i]->owner,
        (jint)pe[i]->modify,  (jint)pe[i]->length,  (jint)pe[i]->priority,
        (jint)biteOrder,  (jboolean)pe[i]->temp,    byteBuf);
      
        /* put event in array */
        (*env)->SetObjectArrayElement(env, eventArray, i, event);

        /* get rid of uneeded references - good if numread is big */
        (*env)->DeleteLocalRef(env, event);
        (*env)->DeleteLocalRef(env, byteBuf);
    }

    /*if (debug) printf("newEvents (native) : filled array!\n");*/

    /* return the array */
    return eventArray;
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    newEvents
 * Signature: (JIIIIIII)[Lorg/jlab/coda/et/EtEventImpl;
 */
JNIEXPORT jobjectArray JNICALL Java_org_jlab_coda_et_EtJniAccess_newEvents__JIIIIIII
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId, jint mode,
         jint sec, jint nsec, jint count, jint size, jint group)
{
    int i, numread, status, biteOrder;
    void *data;
    et_event *pe[count];
    jclass clazz;
    jobjectArray eventArray;
    jobject event, byteBuf;

    /* translate timeout */
    struct timespec deltaTime;
    deltaTime.tv_sec  = sec;
    deltaTime.tv_nsec = nsec;

    if (debug) printf("newEvents (native) : will attempt to get new events\n");

    /* reading array of up to "count" events */
    status = et_events_new_group((et_sys_id)etId, (et_att_id)attId, pe, mode,
                                 &deltaTime, (size_t)size, count, group, &numread);
    if (status != ET_OK) {
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_WAKEUP) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtWakeUpException");
        }
        else if (status == ET_ERROR_TIMEOUT) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtTimeoutException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
        }
        else if (status == ET_ERROR_BUSY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtBusyException");
        }
        else if (status == ET_ERROR_EMPTY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtEmptyException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }

        (*env)->ThrowNew(env, clazz, "newEvents (native): cannot get new events");
        return NULL;
    }

    /* create array of EventImpl objects */
    eventArray = (*env)->NewObjectArray(env, numread, eventImplClass, NULL);

    /* fill array */
    for (i=0; i < numread; i++) {
        /* If we're on a little endian machine, int args will be swapped as
           they go through the jni interface. We don't want this for the int
           designating the byte order, so swap it here to compensate. */
        biteOrder = pe[i]->byteorder;
        if (localByteOrder == ET_ENDIAN_LITTLE) {
            biteOrder = ET_SWAP32(biteOrder);
        }

        /* wrap data pointer in ByteBuffer object */
        et_event_getdata(pe[i], &data);
        byteBuf = (*env)->NewDirectByteBuffer(env, data, (jlong) pe[i]->memsize);

        /* create event object */
        event = (*env)->NewObject(env, eventImplClass, constrMethodId2, /* constructor args ... */
                                  (jint)pe[i]->memsize, (jint)pe[i]->memsize,
                                  (jint)pe[i]->place,   (jint)pe[i]->owner,
                                  (jint)pe[i]->modify,  (jint)pe[i]->length,  (jint)pe[i]->priority,
                                  (jint)biteOrder,  (jboolean)pe[i]->temp,    byteBuf);

        /* put event in array */
        (*env)->SetObjectArrayElement(env, eventArray, i, event);

        /* get rid of uneeded references - good if numread is big */
        (*env)->DeleteLocalRef(env, event);
        (*env)->DeleteLocalRef(env, byteBuf);
    }

    if (debug) printf("newEvents (native) : filled array!\n");

    /* return the array */
    return eventArray;
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    putEvents
 * Signature: (JI[Lorg/jlab/coda/et/EtEvent;II)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_putEvents
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId,
         jobjectArray events, jint length)
{
    int i, j, status, place;
    et_event *pe[length];
    jclass clazz = NULL;
    jobject event;
    jboolean isCopy;
    jint *controlElements;
    jintArray controlInts;
    et_id *etid = (et_id *) etId;
    
if (debug) printf("putEvents (native) : put 'em back\n");

     /* create array of (pointers to) events */
    for (i=0; i<length; i++) {
        /* get event object from Java array of events */
        event = (*env)->GetObjectArrayElement(env, events, i);
        
        /* find ptr to event in C world */
        place = (*env)->GetIntField(env, event, fid[0]);
        pe[i] = ET_P2EVENT(etid, place);
        
        /* set fields in event struct that may have been modified in Java */
        pe[i]->priority   = (*env)->CallIntMethod(env, event, getPriorityVal);
        pe[i]->length     = (uint64_t) ((*env)->GetIntField(env, event, fid[1]));
        pe[i]->datastatus = (*env)->CallIntMethod(env, event, getDataStatusVal);
        
        /* If we're on a little endian machine, ints will be swapped as
           they go through the jni interface. We don't want this for the int
           designating the byte order, so swap it back again to compensate. */
        pe[i]->byteorder = (*env)->GetIntField(env, event, fid[2]);
        if (localByteOrder == ET_ENDIAN_LITTLE) {
            pe[i]->byteorder = ET_SWAP32(pe[i]->byteorder);
        }

        /* set control ints */
        controlInts     = (*env)->GetObjectField(env, event, fid[3]);
        controlElements = (*env)->GetIntArrayElements(env, controlInts, &isCopy);
        for (j=0; j<ET_STATION_SELECT_INTS; j++) {
            pe[i]->control[j] = controlElements[j];
        }

        /* clean up */
        if (isCopy == JNI_TRUE) {
            (*env)->ReleaseIntArrayElements(env, controlInts, controlElements, 0);
        }

        (*env)->DeleteLocalRef(env, event);
    }
    
    /* put back array of events (pointers) */
    status = et_events_put((et_sys_id)etId, (et_att_id)attId, pe, length);
    if (status != ET_OK) {
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        
        (*env)->ThrowNew(env, clazz, "putEvents (native): cannot put events");
        return;
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    dumpEvents
 * Signature: (JI[Lorg/jlab/coda/et/EtEventImpl;I)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_dumpEvents
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId, jobjectArray events, jint length)
{
    int i, status, place;
    et_event *pe[length];
    jclass clazz = NULL;
    jobject event;
    et_id *etid = (et_id *) etId;
    
if (debug) printf("dumpEvents (native) : dump 'em\n");

    /* create array of (pointers to) events */
    for (i=0; i<length; i++) {
        /* get event object from Java array of events */
        event = (*env)->GetObjectArrayElement(env, events, i);
        
        /* find ptr to event in C world */
        place = (*env)->GetIntField(env, event, fid[0]);
        pe[i] = ET_P2EVENT(etid, place);
        
        (*env)->DeleteLocalRef(env, event);
    }
    
    /* dump array of events (pointers) */
    status = et_events_dump((et_sys_id)etId, (et_att_id)attId, pe, length);
    if (status != ET_OK) {
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "dumpEvents (native): cannot dump events");
        return;
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    createStation
 * Signature: (JLorg/jlab/coda/et/EtStationConfig;Ljava/lang/String;II)I
 */
JNIEXPORT jint JNICALL Java_org_jlab_coda_et_EtJniAccess_createStation
        (JNIEnv *env, jobject thisObj, jlong etId, jobject stationConfig, jstring stationName, jint position, jint pPosition)
{
    et_id *etid = (et_id *) etId;
    et_stat_id my_stat;

    jclass classStatConfigImpl = (*env)->FindClass(env, "org/jlab/coda/et/EtStationConfig");

    // Find id's of all the fields of EtStationConfig that we'll read/write directly from/to
    jfieldID fid_0 = (*env)->GetFieldID(env, classStatConfigImpl, "cue", "I");
    jfieldID fid_1 = (*env)->GetFieldID(env, classStatConfigImpl, "prescale", "I");
    jfieldID fid_2 = (*env)->GetFieldID(env, classStatConfigImpl, "flowMode", "I");
    jfieldID fid_3 = (*env)->GetFieldID(env, classStatConfigImpl, "userMode", "I");
    jfieldID fid_4 = (*env)->GetFieldID(env, classStatConfigImpl, "restoreMode", "I");
    jfieldID fid_5 = (*env)->GetFieldID(env, classStatConfigImpl, "blockMode", "I");
    jfieldID fid_6 = (*env)->GetFieldID(env, classStatConfigImpl, "selectMode", "I");
    jfieldID fid_7 = (*env)->GetFieldID(env, classStatConfigImpl, "selectFunction", "Ljava/lang/String;");
    jfieldID fid_8 = (*env)->GetFieldID(env, classStatConfigImpl, "selectLibrary", "Ljava/lang/String;");
    jfieldID fid_9 = (*env)->GetFieldID(env, classStatConfigImpl, "select", "[I");

    // Define station to create
    et_statconfig sconfig;
    et_station_config_init(&sconfig);

    int qSize = (int) ((*env)->GetIntField(env, stationConfig, fid_0));
    et_station_config_setcue(sconfig, qSize);

    int pre = (int) ((*env)->GetIntField(env, stationConfig, fid_1));
    et_station_config_setprescale(sconfig, pre);

    int flowMode = (int) ((*env)->GetIntField(env, stationConfig, fid_2));
    et_station_config_setflow(sconfig, flowMode);

    int userMode = (int) ((*env)->GetIntField(env, stationConfig, fid_3));
    et_station_config_setuser(sconfig, userMode);

    int restoreMode = (int) ((*env)->GetIntField(env, stationConfig, fid_4));
    et_station_config_setrestore(sconfig, restoreMode);

    int blockMode = (int) ((*env)->GetIntField(env, stationConfig, fid_5));
    et_station_config_setblock(sconfig, blockMode);

    int selectMode = (int) ((*env)->GetIntField(env, stationConfig, fid_6));
    et_station_config_setselect(sconfig, selectMode);

    const char *lib = NULL;
    const char *func = NULL;

    jstring selectFunction = ((*env)->GetObjectField(env, stationConfig, fid_7));
    if (!(*env)->IsSameObject(env, selectFunction, NULL)) {
        func = (*env)->GetStringUTFChars(env, selectFunction, 0);
        et_station_config_setfunction(sconfig, func);
    }

    jstring selectLibrary = ((*env)->GetObjectField(env, stationConfig, fid_8));
    if (!(*env)->IsSameObject(env, selectLibrary, NULL)) {
        lib = (*env)->GetStringUTFChars(env, selectLibrary, 0);
        et_station_config_setlib(sconfig, lib);
    }

    // Get int array of select ints
    jboolean isCopy;
    jintArray selectInts = (*env)->GetObjectField(env, stationConfig, fid_9);
    jint *selectElements = (*env)->GetIntArrayElements(env, selectInts, &isCopy);
    int selects[ET_STATION_SELECT_INTS];
    int j;
    for (j=0; j < ET_STATION_SELECT_INTS; j++) {
        selects[j] = (int) selectElements[j];
    }
    et_station_config_setselectwords(sconfig, selects);

    // Clean up
    if (isCopy == JNI_TRUE) {
        (*env)->ReleaseIntArrayElements(env, selectInts, selectElements, 0);
    }

    // Convert stationName to C string
    const char *stName = (*env)->GetStringUTFChars(env, stationName, 0);

    // Create station
    int status = et_station_create_at((et_sys_id)etid, &my_stat, stName, sconfig, position, pPosition);

    et_station_config_destroy(sconfig);
    if (lib != NULL) {
        (*env)->ReleaseStringUTFChars(env, selectLibrary, lib);
    }
    if (func != NULL) {
        (*env)->ReleaseStringUTFChars(env, selectFunction, func);
    }
    (*env)->ReleaseStringUTFChars(env, stationName, stName);

    if (status != ET_OK) {
        jclass clazz = NULL;
        if (status == ET_ERROR_EXISTS) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtExistsException");
        }
        else if (status == ET_ERROR_TOOMANY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtTooManyException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "createStation (native): cannot create station");

        return (jint)status;
    }

    // Return station id
    return (jint)my_stat;
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    stationNameToObject
 * Signature: (JLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_jlab_coda_et_EtJniAccess_stationNameToObject
        (JNIEnv *env, jobject thisObj, jlong etId, jstring stationName)
{
    et_id *etid = (et_id *) etId;

    // Convert stationName to C string
    const char *stName = (*env)->GetStringUTFChars(env, stationName, 0);

    // Find station
    et_stat_id stat_id;
    int status = et_station_name_to_id((et_sys_id)etid, &stat_id, stName);

    (*env)->ReleaseStringUTFChars(env, stationName, stName);

    if (status == ET_OK) {
        // Return station id
        return (jint)stat_id;
    }
    else if (status == ET_ERROR) {
        // Not found
        return (jint)(-1);
    }
    else {
        jclass clazz = NULL;
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
            (*env)->ThrowNew(env, clazz, "stationNameToObject (native): ET system is dead");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
            (*env)->ThrowNew(env, clazz, "stationNameToObject (native): close() already called");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
            (*env)->ThrowNew(env, clazz, "stationNameToObject (native): error finding station");
        }
        return (jint)status;
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    attachJNI
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_org_jlab_coda_et_EtJniAccess_attach
        (JNIEnv *env, jobject thisObj, jlong etId, jint stationId)
{
    et_id *etid = (et_id *) etId;
    et_stat_id stat_id = (int) stationId;
    et_att_id att;

    int status = et_station_attach((et_sys_id)etid, stat_id, &att);

    if (status != ET_OK) {
        jclass clazz = NULL;
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtClosedException");
        }
        else if (status == ET_ERROR_TOOMANY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtTooManyException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "attach (native): cannot attach to station");

        return (jint)status;
    }

    // Return attachment id
    return (jint)att;
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    detach
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_detach
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId)
{
    et_id *etid = (et_id *) etId;
    et_att_id att_id = (int) attId;

    int status = et_station_detach((et_sys_id)etid, att_id);

    if (status != ET_OK) {
        jclass clazz = NULL;
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtCLosedException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "detach (native): cannot detach from station");
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    removeStation
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_removeStation
        (JNIEnv *env, jobject thisObj, jlong etId, jint stationId)
{
    et_id *etid = (et_id *) etId;
    et_stat_id stat_id = (int) stationId;

    int status = et_station_remove((et_sys_id)etid, stat_id);

    if (status != ET_OK) {
        jclass clazz = NULL;
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtCLosedException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "removeStation (native): cannot remove station");
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    setStationPosition
 * Signature: (JIII)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_setStationPosition
        (JNIEnv *env, jobject thisObj, jlong etId, jint stationId, jint pos, jint parallelPos)
{
    et_id *etid = (et_id *) etId;
    et_stat_id stat_id = (int) stationId;

    int status = et_station_setposition((et_sys_id)etid, stat_id, (int)pos, (int)parallelPos);

    if (status != ET_OK) {
        jclass clazz = NULL;
        if (status == ET_ERROR_DEAD) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtDeadException");
        }
        else if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtCLosedException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "removeStation (native): cannot set station position");
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    wakeUpAttachment
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_EtJniAccess_wakeUpAttachment
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId)
{
    et_id *etid = (et_id *) etId;
    et_att_id att_id = (int) attId;

    int status = et_wakeup_attachment((et_sys_id)etid, att_id);

    if (status != ET_OK) {
        jclass clazz = NULL;
        if (status == ET_ERROR_CLOSED) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtCLosedException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        (*env)->ThrowNew(env, clazz, "detach (native): cannot wake up attachment");
    }
}


/*
 * Class:     org_jlab_coda_et_EtJniAccess
 * Method:    alive
 * Signature: (J)Z
 */
JNIEXPORT jint JNICALL Java_org_jlab_coda_et_EtJniAccess_alive
        (JNIEnv *env, jobject thisObj, jlong etId)
{
    et_id *etid = (et_id *) etId;

    int status = et_alive((et_sys_id)etid);

    // Return status
    return (jint)status;
}


