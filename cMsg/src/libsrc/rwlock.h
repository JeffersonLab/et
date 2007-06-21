/*
 * rwlock.h
 *
 * This header file describes the "reader/writer lock" synchronization
 * construct. The type rwlock_t describes the full state of the lock
 * including the POSIX 1003.1c synchronization objects necessary.
 *
 * A reader/writer lock allows a thread to lock shared data either for shared
 * read access or exclusive write access.
 *
 * The rwl_init() and rwl_destroy() functions, respectively, allow you to
 * initialize/create and destroy/free the reader/writer lock.
 */

#ifndef __rwlock_h /* added by Carl Timmer */
#define __rwlock_h

#include <pthread.h>

/*
 * Structure describing a read-write lock.
 */
typedef struct rwLock_tag {
    pthread_mutex_t     mutex;
    pthread_cond_t      read;           /* wait for read */
    pthread_cond_t      write;          /* wait for write */
    int                 valid;          /* set when valid */
    int                 r_active;       /* readers active */
    int                 w_active;       /* writer active */
    int                 r_wait;         /* readers waiting */
    int                 w_wait;         /* writers waiting */
} rwLock_t;

#define RWLOCK_VALID    0xfacade

/*
 * Support static initialization of barriers
 */
#define RWL_INITIALIZER \
    {PTHREAD_MUTEX_INITIALIZER, PTHREAD_COND_INITIALIZER, \
    PTHREAD_COND_INITIALIZER, RWLOCK_VALID, 0, 0, 0, 0}


#ifdef __cplusplus
extern "C" {
#endif


/*
 * Define read-write lock functions
 */
extern int rwl_init (rwLock_t *rwlock);
extern int rwl_destroy (rwLock_t *rwlock);
extern int rwl_readlock (rwLock_t *rwlock);
extern int rwl_readtrylock (rwLock_t *rwlock);
extern int rwl_readunlock (rwLock_t *rwlock);
extern int rwl_writelock (rwLock_t *rwlock);
extern int rwl_writetrylock (rwLock_t *rwlock);
extern int rwl_writeunlock (rwLock_t *rwlock);


#ifdef __cplusplus
}
#endif

#endif
