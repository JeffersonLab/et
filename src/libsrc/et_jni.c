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
#include <string.h>
#include <unistd.h>

#include "et_private.h"
#include "org_jlab_coda_et_JniAccess.h"

static int debug = 0;
/* cache some frequently used values */
static jclass eventImplClass;
static jfieldID fid[6];
static jmethodID constrMethodId1,constrMethodId2;


/*
 * Class:     org_jlab_coda_et_JniAccess
 * Method:    openLocalEtSystem
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_JniAccess_openLocalEtSystem
        (JNIEnv *env, jobject thisObj, jstring fileName) {

    int err;
    const char* mappedFile;
    et_sys_id id; // void *
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

    /* store et id in object by calling this native method */
    class1 = (*env)->GetObjectClass(env, thisObj);
    mid    = (*env)->GetMethodID(env, class1, "setLocalEtId", "(J)V");
    (*env)->CallVoidMethod(env, thisObj, mid, (jlong)id);

    /*******************************************/
    /* cache objects for efficient, future use */
    /*******************************************/
    classEventImpl = (*env)->FindClass(env, "org/jlab/coda/et/EventImpl");
    eventImplClass = (*env)->NewGlobalRef(env, classEventImpl);
    
    /* find id's of all the fields that we'll read/write to */
    fid[0] = (*env)->GetFieldID(env, classEventImpl, "id",         "I");
    fid[1] = (*env)->GetFieldID(env, classEventImpl, "priority",   "I");
    fid[2] = (*env)->GetFieldID(env, classEventImpl, "length",     "I");
    fid[3] = (*env)->GetFieldID(env, classEventImpl, "dataStatus", "I");
    fid[4] = (*env)->GetFieldID(env, classEventImpl, "byteOrder",  "I");
    fid[5] = (*env)->GetFieldID(env, classEventImpl, "control",    "[I");

    /* get id's of a couple different constructors */
    constrMethodId1 = (*env)->GetMethodID(env, classEventImpl, "<init>", "(III)V");
    constrMethodId2 = (*env)->GetMethodID(env, classEventImpl, "<init>", "(IIIIIIIIII[I)V");
  
if (debug) printf("\nopenLocalEtSystem (native) : done, opened ET system\n\n");
}


/*
 * Class:     org_jlab_coda_et_JniAccess
 * Method:    getEvents
 * Signature: (JIIIIII)[Lorg/jlab/coda/et/Event;
 */
JNIEXPORT jobjectArray JNICALL Java_org_jlab_coda_et_JniAccess_getEvents
        (JNIEnv *env , jobject thisObj, jlong etId, jint attId,
         jint mode, jint sec, jint nsec, jint count)
{

    int i, j, numread, status;
    et_event *pe[count];
    jclass clazz;
    jboolean isCopy;
    jint* intArrayElems;
    jintArray controlInts;
    jobjectArray eventArray;
    jobject event;

    /* translate timeout */
    struct timespec deltaTime;
    deltaTime.tv_sec  = sec;
    deltaTime.tv_nsec = nsec;

if (debug) printf("getEvents (native) : will attempt to get events\n");

    /* reading array of up to "count" events */
    status = et_events_get((et_sys_id)etId, (et_att_id)attId, pe, mode, &deltaTime, count, &numread);
    if (status != ET_OK) {
        clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        (*env)->ThrowNew(env, clazz, "getEvents: cannot get events in native code");
        return;
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

        /* create event object */
        event = (*env)->NewObject(env, eventImplClass, constrMethodId2, /* constructor args ... */
        (jint)pe[i]->memsize, (jint)pe[i]->memsize, (jint)pe[i]->datastatus,
        (jint)pe[i]->place,   (jint)pe[i]->age,     (jint)pe[i]->owner,
        (jint)pe[i]->modify,  (jint)pe[i]->length,  (jint)pe[i]->priority,
        (jint)pe[i]->byteorder, controlInts);
        
        /* put event in array */
        (*env)->SetObjectArrayElement(env, eventArray, i, event);
        (*env)->DeleteLocalRef(env, event);
    }

if (debug) printf("getEvents (native) : filled array!\n");
   
    /* return the array */
    return eventArray;
}


/*
 * Class:     org_jlab_coda_et_JniAccess
 * Method:    putEvents
 * Signature: (JI[Lorg/jlab/coda/et/Event;II)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_JniAccess_putEvents
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId,
         jobjectArray events, jint length) {

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
        pe[i]->priority   = (*env)->GetIntField(env, event, fid[1]);
        pe[i]->length     = (uint64_t) ((*env)->GetIntField(env, event, fid[2]));
        pe[i]->datastatus = (*env)->GetIntField(env, event, fid[3]);
        pe[i]->byteorder  = (*env)->GetIntField(env, event, fid[4]);

        /* set control ints */
        controlInts     = (*env)->GetObjectField(env, event, fid[5]);
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
        clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        (*env)->ThrowNew(env, clazz, "putEvents: cannot put events in native code");
        return;
    }

    return;
}


/*
 * Class:     org_jlab_coda_et_JniAccess
 * Method:    dumpEvents
 * Signature: (JI[Lorg/jlab/coda/et/EventImpl;I)V
 */
JNIEXPORT void JNICALL Java_org_jlab_coda_et_JniAccess_dumpEvents
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId, jobjectArray events, jint length) {

    int i, j, status, place;
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
        clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        (*env)->ThrowNew(env, clazz, "dumpEvents: cannot dump events in native code");
        return;
    }

    return;
}

/*
 * Class:     org_jlab_coda_et_JniAccess
 * Method:    newEvents
 * Signature: (JIIIIIII)[Lorg/jlab/coda/et/EventImpl;
 */
JNIEXPORT jobjectArray JNICALL Java_org_jlab_coda_et_JniAccess_newEvents
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId, jint mode,
         jint sec, jint nsec, jint count, jint size, jint group) {

    int i, j, numread, status;
    et_event *pe[count];
    jclass clazz;
    jboolean isCopy;
    jobjectArray eventArray;
    jobject event;

    /* translate timeout */
    struct timespec deltaTime;
    deltaTime.tv_sec  = sec;
    deltaTime.tv_nsec = nsec;

if (debug) printf("newEvents (native) : will attempt to get new events\n");
    
    /* reading array of up to "count" events */
    status = et_events_new_group((et_sys_id)etId, (et_att_id)attId, pe, mode,
                                  &deltaTime, size, count, group, &numread);
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
        else if (status == ET_ERROR_BUSY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtBusyException");
        }
        else if (status == ET_ERROR_EMPTY) {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtEmptyException");
        }
        else {
            clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        }
        
        (*env)->ThrowNew(env, clazz, "getEvents: cannot get events in native code");
        return;
    }

    /* create array of EventImpl objects */
    eventArray = (*env)->NewObjectArray(env, numread, eventImplClass, NULL);

    /* fill array */
    for (i=0; i < numread; i++) {
        /* create event object */
        event = (*env)->NewObject(env, eventImplClass, constrMethodId1, /* constructor args ... */
                                  (jint)pe[i]->memsize, (jint)pe[i]->place, (jint)pe[i]->owner);

        /* put event in array */
        (*env)->SetObjectArrayElement(env, eventArray, i, event);
        (*env)->DeleteLocalRef(env, event);
    }

if (debug) printf("newEvents (native) : filled array!\n");

    /* return the array */
    return eventArray;
}



