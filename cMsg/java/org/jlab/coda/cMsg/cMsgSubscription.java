/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 11-Aug-2004, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg;

import org.jlab.coda.cMsg.cMsgCallbackThread;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Class to store a client's subscription to a particular message subject and type.
 * Used by both the cMsg domain API and cMsg subdomain handler objects. Once the
 * domain server object is created, the only set method called is setNamespace.
 * Thus, this object is for all intents and purposes immutable during its lifetime.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgSubscription {

    /** Subject subscribed to. */
    private String subject;

    /** Subject turned into regular expression that understands * and ?. */
    private String subjectRegexp;

    /** Compiled regular expression given in {@link #subjectRegexp}. */
    private Pattern subjectPattern;

    /** Are there any * or ? characters in the subject? */
    private boolean wildCardsInSub;

    /** Type subscribed to. */
    private String type;

    /** Type turned into regular expression that understands * and ?. */
    private String typeRegexp;

    /** Compiled regular expression given in {@link #typeRegexp}. */
    private Pattern typePattern;

    /** Are there any * or ? characters in the type? */
    private boolean wildCardsInType;

    /** Client generated id. */
    private int id;

    /**
     * This refers to a cMsg subdomain's namespace in which this subscription resides.
     * It is useful only when another server becomes a client for means of bridging
     * messages. In this case, this special client does not reside in 1 namespace but
     * represents subscriptions from different namespaces. This is used on the server
     * side.
     */
    private String namespace;

    /** Set of objects used to notify servers that their subscribeAndGet is complete. */
    private HashSet<cMsgNotifier> notifiers;

    /**
     * This set contains all of the callback thread objects
     * {@link cMsgCallbackThread}
     * used on the client side.
     */
    private HashSet<cMsgCallbackThread> callbacks;

    /**
     * This set contains all clients (regular and bridge) subscribed to this exact subject, type,
     * and namespace and is used on the server side.
     */
    private HashSet<cMsgClientInfo> allSubscribers;

    /**
     * This map contains all regular clients (servers do not call
     * subscribeAndGet but use subscribe to implement it) who have
     * called {@link org.jlab.coda.cMsg.cMsg#subscribeAndGet}
     * with this exact subject, type, and namespace. A count is
     * keep of how many times subscribeAndGet for a particular
     * client has been called. The client info object is the key
     * and count is the value. This is used on the server side.
     */
    private HashMap<cMsgClientInfo, Integer> clientSubAndGetters;

    /**
     * This set contains only regular clients subscribed to this exact subject, type,
     * and namespace and is used on the server side.
     */
    private HashSet<cMsgClientInfo> clientSubscribers;


    /**
     * Constructor used by cMsg subdomain handler.
     * @param subject subscription subject
     * @param type subscription type
     * @param namespace namespace subscription exists in
     */
    public cMsgSubscription(String subject, String type, String namespace) {
        this.subject = subject;
        this.type = type;
        this.namespace = namespace;

        // we only need to do the regexp stuff if there are wildcards chars in subj or type
        if (subject != null) {
            if ((subject.contains("*") || subject.contains("?"))) {
                wildCardsInSub = true;
                subjectRegexp = cMsgMessageMatcher.escape(subject);
                subjectPattern = Pattern.compile(subjectRegexp);
            }
        }
        if (type != null) {
            if ((type.contains("*") || type.contains("?"))) {
                wildCardsInType = true;
                typeRegexp = cMsgMessageMatcher.escape(type);
                typePattern = Pattern.compile(typeRegexp);
            }
        }

        notifiers            = new HashSet<cMsgNotifier>(30);
        allSubscribers       = new HashSet<cMsgClientInfo>(30);
        clientSubAndGetters  = new HashMap<cMsgClientInfo, Integer>(30);
        clientSubscribers    = new HashSet<cMsgClientInfo>(30);
    }


    /**
     * Constructor used by cMsg domain API.
     * @param subject subscription subject
     * @param type subscription type
     * @param id unique id referring to subject and type combination
     * @param cbThread object containing callback, its argument, and the thread to run it
     */
    public cMsgSubscription(String subject, String type, int id, cMsgCallbackThread cbThread) {
        this.subject = subject;
        this.type = type;
        this.id = id;

        // we only need to do the regexp stuff if there are wildcards chars in subj or type
        if (subject != null) {
            if ((subject.contains("*") || subject.contains("?"))) {
                wildCardsInSub = true;
                subjectRegexp = cMsgMessageMatcher.escape(subject);
                subjectPattern = Pattern.compile(subjectRegexp);
            }
        }
        if (type != null) {
            if ((type.contains("*") || type.contains("?"))) {
                wildCardsInType = true;
                typeRegexp = cMsgMessageMatcher.escape(type);
                typePattern = Pattern.compile(typeRegexp);
            }
        }

        notifiers            = new HashSet<cMsgNotifier>(30);
        clientSubAndGetters  = new HashMap<cMsgClientInfo, Integer>(30);
        allSubscribers       = new HashSet<cMsgClientInfo>(30);
        clientSubscribers    = new HashSet<cMsgClientInfo>(30);
        callbacks            = new HashSet<cMsgCallbackThread>(30);
        callbacks.add(cbThread);
    }


    /** This method prints the sizes of all sets and maps. */
    public void printSizes() {
        System.out.println("        notifiers           = " + notifiers.size());
        if (callbacks != null) {
            System.out.println("        callbacks           = " + callbacks.size());
        }
        System.out.println("        allSubscribers      = " + allSubscribers.size());
        System.out.println("        clientSubscribers   = " + clientSubscribers.size());
        System.out.println("        clientSubAndGetters = " + clientSubAndGetters.size());
    }

    /**
     * Returns true if there are * or ? characters in subject.
     * @return true if there are * or ? characters in subject.
     */
    public boolean areWildCardsInSub() {
        return wildCardsInSub;
    }


    /**
     * Returns true if there are * or ? characters in type.
     * @return true if there are * or ? characters in type.
     */
    public boolean areWildCardsInType() {
        return wildCardsInType;
    }


    /**
     * Gets subject subscribed to.
     * @return subject subscribed to
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Gets subject turned into regular expression that understands * and ?.
     * @return subject subscribed to in regexp form
     */
    public String getSubjectRegexp() {
        return subjectRegexp;
    }

    /**
     * Gets subject turned into compiled regular expression pattern.
     * @return subject subscribed to in compiled regexp form
     */
    public Pattern getSubjectPattern() {
        return subjectPattern;
    }

    /**
     * Gets type subscribed to.
     * @return type subscribed to
     */
    public String getType() {
        return type;
    }

    /**
     * Gets type turned into regular expression that understands * and ?.
     * @return type subscribed to in regexp form
     */
     public String getTypeRegexp() {
         return typeRegexp;
     }

    /**
     * Gets type turned into compiled regular expression pattern.
     * @return type subscribed to in compiled regexp form
     */
    public Pattern getTypePattern() {
        return typePattern;
    }

    /**
     * Gets the id which client generates (receiverSubscribeId).
     * @return receiverSubscribeId
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the id which client generates (receiverSubscribeId).
     * @param id id which client generates (receiverSubscribeId)
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Gets the namespace in the cMsg subdomain in which this subscription resides.
     * @return namespace subscription resides in
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace in the cMsg subdomain in which this subscription resides.
     * @param namespace namespace subscription resides in
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    //-----------------------------------------------------
    // Methods for dealing with callbacks
    //-----------------------------------------------------

    /**
     * Gets the hashset storing callback threads.
     * @return hashset storing callback threads
     */
    public HashSet<cMsgCallbackThread> getCallbacks() {
        return callbacks;
    }


    /**
     * Method to add a callback thread.
     * @param cbThread  object containing callback thread, its argument,
     *                  and the thread to run it
     */
    public void addCallback(cMsgCallbackThread cbThread) {
        callbacks.add(cbThread);
    }


    /**
     * Method to remove a callback thread.
     * @param cbThread  object containing callback thread to be removed
     */
    public void removeCallback(cMsgCallbackThread cbThread) {
        callbacks.remove(cbThread);
    }


    /**
     * Method to return the number of callback threads registered.
     * @return number of callback registered
     */
    public int numberOfCallbacks() {
        return callbacks.size();
    }


    //-------------------------------------------------------------------------
    // Methods for dealing with clients subscribed to the sub/type/ns
    //-------------------------------------------------------------------------

    /**
     * Gets the set containing only regular clients subscribed to this subject, type,
     * and namespace. Used only on the server side.
     *
     * @return set containing only regular clients subscribed to this subject, type,
     *         and namespace
     */
    public HashSet<cMsgClientInfo> getClientSubscribers() {
        return clientSubscribers;
    }

    /**
     * Adds a client to the set containing only regular clients subscribed to this subject, type,
     * and namespace. Used only on the server side.
     *
     * @param client regular client to be added to subscription
     * @return true if set did not already contain client, else false
     */
    public boolean addClientSubscriber(cMsgClientInfo client) {
        return clientSubscribers.add(client);
    }

    /**
     * Removes a client from the set containing only regular clients subscribed to this subject, type,
     * and namespace. Used only on the server side.
     *
     * @param client regular client to be removed from subscription
     * @return true if set contained client, else false
     */
    public boolean removeClientSubscriber(cMsgClientInfo client) {
        return clientSubscribers.remove(client);
    }


    //-------------------------------------------------------------------------
    // Methods for dealing with clients & servers subscribed to the sub/type/ns
    //-------------------------------------------------------------------------

    /**
     * Gets the set containing all clients (regular and bridge) subscribed to this
     * subject, type, and namespace. Used only on the server side.
     *
     * @return set containing all clients (regular and bridge) subscribed to this subject, type,
     *         and namespace
     */
    public HashSet<cMsgClientInfo> getAllSubscribers() {
        return allSubscribers;
    }

    /**
     * Is this a subscription of the given client (regular or bridge)?
     * Used only on the server side.
     *
     * @param client client
     * @return true if this is a subscription of the given client, else false
     */
    public boolean containsSubscriber(cMsgClientInfo client) {
        return allSubscribers.contains(client);
    }

    /**
     * Adds a client to the set containing all clients (regular and bridge) subscribed
     * to this subject, type, and namespace. Used only on the server side.
     *
     * @param client client to be added to subscription
     * @return true if set did not already contain client, else false
     */
    public boolean addSubscriber(cMsgClientInfo client) {
        return allSubscribers.add(client);
    }

    /**
     * Removes a client from the set containing all clients (regular and bridge) subscribed
     * to this subject, type, and namespace. Used only on the server side.
     *
     * @param client client to be removed from subscription
     * @return true if set contained client, else false
     */
    public boolean removeSubscriber(cMsgClientInfo client) {
        return allSubscribers.remove(client);
    }


    //-------------------------------------------------------------------------------
    // Methods for dealing with clients who subscribeAndGet to the sub/type/namespace
    //-------------------------------------------------------------------------------

    /**
     * Gets the hashmap containing all regular clients who have
     * called {@link org.jlab.coda.cMsg.cMsg#subscribeAndGet}
     * with this subject, type, and namespace. A count is
     * keep of how many times subscribeAndGet for a particular
     * client has been called. The client info object is the key
     * and count is the value. This is used on the server side.
     *
     * @return hashmap containing all regular clients who have
     *         called {@link org.jlab.coda.cMsg.cMsg#subscribeAndGet}
     */
    public HashMap<cMsgClientInfo, Integer> getSubAndGetters() {
        return clientSubAndGetters;
    }

    /**
     * Adds a client to the hashmap containing all regular clients who have
     * called {@link org.jlab.coda.cMsg.cMsg#subscribeAndGet}.
     * This is used on the server side.
     *
     * @param client client who called subscribeAndGet
     */
    public void addSubAndGetter(cMsgClientInfo client) {
//System.out.println("      SUB: addSub&Getter arg = " + client);
        Integer count = clientSubAndGetters.get(client);
        if (count == null) {
//System.out.println("      SUB: set sub&Getter cnt to 1");
            clientSubAndGetters.put(client, 1);
        }
        else {
//System.out.println("      SUB: set sub&Getter cnt to " + (count + 1));
            clientSubAndGetters.put(client, count + 1);
        }
    }

    /**
     * Clears the hashmap containing all regular clients who have
     * called {@link org.jlab.coda.cMsg.cMsg#subscribeAndGet}.
     * This is used on the server side.
     */
    public void clearSubAndGetters() {
        clientSubAndGetters.clear();
    }

    /**
     * Removes a client from the hashmap containing all regular clients who have
     * called {@link org.jlab.coda.cMsg.cMsg#subscribeAndGet}. If the client has
     * called subscribeAndGet more than once, it's entry in the table stays but
     * it's count of active subscribeAndGet calls is decremented.
     * This is used on the server side.
     *
     * @param client client who called subscribeAndGet
     */
    public void removeSubAndGetter(cMsgClientInfo client) {
        Integer count = clientSubAndGetters.get(client);
//System.out.println("      SUB: removeSub&Getter: count = " + count);
        if (count == null || count < 2) {
//System.out.println("      SUB: remove sub&Getter completely (count = 0)");
            clientSubAndGetters.remove(client);
        }
        else {
//System.out.println("      SUB: reduce sub&Getter cnt to " + (count - 1));
            clientSubAndGetters.put(client, count - 1);
        }
    }

    /**
     * Returns the total number of subscribers and subscribeAndGet callers
     * subscribed to this subject, type, and namespace.
     * @return the total number of subscribers and subscribeAndGet callers
     *         subscribed to this subject, type, and namespace
     */
    public int numberOfSubscribers() {
        return (allSubscribers.size() + clientSubAndGetters.size());
    }


    //--------------------------------------------------------------------------
    // Methods for dealing with servers' notifiers of subscribeAndGet completion
    //--------------------------------------------------------------------------

    /**
     * Gets the set of objects used to notify servers that their subscribeAndGet is complete.
     *
     * @return set of objects used to notify servers that their subscribeAndGet is complete
     */
    public Set<cMsgNotifier> getNotifiers() {
        return notifiers;
    }

    /**
     * Add a notifier object to the set of objects used to notify servers that their
     * subscribeAndGet is complete.
     *
     * @param notifier notifier object
     */
    public void addNotifier(cMsgNotifier notifier) {
        notifiers.add(notifier);
    }

    /**
     * Remove a notifier object from the set of objects used to notify servers that their
     * subscribeAndGet is complete.
     *
     * @param notifier  notifier object
     */
    public void removeNotifier(cMsgNotifier notifier) {
        notifiers.remove(notifier);
    }

    /**
     * Clear all notifier objects from the set of objects used to notify servers that their
     * subscribeAndGet is complete.
     */
    public void clearNotifiers() {
        notifiers.clear();
    }

 }
