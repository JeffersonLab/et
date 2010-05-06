
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "et_private.h"
#include "org_jlab_coda_et_JniAccess.h"


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
    jclass clazz;
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
    if (et_open(&id, mappedFile, openconfig) != ET_OK) {
        clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        (*env)->ThrowNew(env, clazz, "openLocalEtSystem: cannot open ET system in native code");
        return;
    }
    et_open_config_destroy(openconfig);

    /* store et id in object by calling this native method */
    clazz = (*env)->GetObjectClass(env, thisObj);
    mid   = (*env)->GetMethodID(env, clazz, "setLocalEtId", "(J)V");
    (*env)->CallVoidMethod(env, thisObj, mid, (jlong)id);
    
printf("\nopenLocalEtSystem (native) : done, opened ET system\n\n");
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
    jmethodID methodId;
    jboolean isCopy;
    jint* intArrayElems;
    jintArray controlInts;
    jobjectArray eventArray;
    jobject event;

    /* translate timeout */
    struct timespec deltaTime;
    deltaTime.tv_sec  = sec;
    deltaTime.tv_nsec = nsec;
printf("getEvents (native) : will attempt to get events\n");

    /* reading array of up to "count" events */
    status = et_events_get((et_sys_id)etId, (et_att_id)attId, pe, mode, &deltaTime, count, &numread);
    if (status != ET_OK) {
        printf("et_client: get error\n");
        clazz = (*env)->FindClass(env, "org/jlab/coda/et/exception/EtException");
        (*env)->ThrowNew(env, clazz, "getEvents: cannot get events in native code");
        return;
    }

    /* create array of EventImpl objects */
    clazz      = (*env)->FindClass(env, "org/jlab/coda/et/EventImpl");
    eventArray = (*env)->NewObjectArray(env, numread, clazz, NULL);
    /* get appropriate constructor for them */
    methodId = (*env)->GetMethodID(env, clazz, "<init>", "(IIIIIIIIII[I)V");

    /* fill array */
    for (i=0; i < numread; i++) {
        printf("getEvents (native) : data for event %d = %d\n", i, *((int *)pe[i]->pdata));

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
        event = (*env)->NewObject(env, clazz, methodId, /* constructor args ... */
        (jint)pe[i]->memsize, (jint)pe[i]->memsize, (jint)pe[i]->datastatus,
        (jint)pe[i]->place,   (jint)pe[i]->age,     (jint)pe[i]->owner,
        (jint)pe[i]->modify,  (jint)pe[i]->length,  (jint)pe[i]->priority,
        (jint)pe[i]->byteorder, controlInts);
        
        /* put event in array */
        (*env)->SetObjectArrayElement(env, eventArray, i, event);
        (*env)->DeleteLocalRef(env, event);
    }
printf("getEvents (native) : filled array!\n");
   
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
    jfieldID fid[6];
    jobject event;
    jboolean isCopy;
    jint *controlElements;
    jintArray controlInts;
    et_id *etid = (et_id *) etId;
    
printf("putEvents (native) : put 'em back\n");

    /* look up class object */
    event = (*env)->GetObjectArrayElement(env, events, 0);
    clazz = (*env)->GetObjectClass(env, event);

    /* find id's of all the fields that might have changed */
    fid[0] = (*env)->GetFieldID(env, clazz, "id",         "I");
    fid[1] = (*env)->GetFieldID(env, clazz, "priority",   "I");
    fid[2] = (*env)->GetFieldID(env, clazz, "length",     "I");
    fid[3] = (*env)->GetFieldID(env, clazz, "dataStatus", "I");
    fid[4] = (*env)->GetFieldID(env, clazz, "byteOrder",  "I");
    fid[5] = (*env)->GetFieldID(env, clazz, "control",    "[I");

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

}

/*
 * Class:     org_jlab_coda_et_JniAccess
 * Method:    newEvents
 * Signature: (JIIIIIII)[Lorg/jlab/coda/et/EventImpl;
 */
JNIEXPORT jobjectArray JNICALL Java_org_jlab_coda_et_JniAccess_newEvents
        (JNIEnv *env, jobject thisObj, jlong etId, jint attId, jint mode,
         jint sec, jint nsec, jint count, jint size, jint group) {

}




