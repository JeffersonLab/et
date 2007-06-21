/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 7-Jul-2004, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.subdomains;

import org.jlab.coda.cMsg.cMsgSubscription;
import org.jlab.coda.cMsg.cMsgNotifier;
import org.jlab.coda.cMsg.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;

/**
 * This class handles all client cMsg requests in the cMsg subdomain.
 * Much of the functionality of this class is obtained by using static
 * members. Practically, this means that only 1 name server can be run
 * in a single JVM when using the cMsg subdomain.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsg extends cMsgSubdomainAdapter {
    /** Used to create a unique id number associated with a specific sendAndGet message. */
    static private AtomicInteger sysMsgId = new AtomicInteger();

    /** HashMap to store server clients. Name is key and cMsgClientInfo is value. */
    static private ConcurrentHashMap<String,cMsgClientInfo> servers =
            new ConcurrentHashMap<String,cMsgClientInfo>(100);

    /**
     * This is a set to store regular clients as cMsgClientInfo objects. This is not done by
     * having a HashMap where the client name is the key since client names themselves
     * are not unique - only the name/namespace combination is unique.
     */
    static private Set<cMsgClientInfo> clients =
            Collections.synchronizedSet(new HashSet<cMsgClientInfo>(100));

    /**
     * HashMap to store sendAndGets in progress. sysMsgId of get msg is key,
     * and client name is value.
     */
    static private ConcurrentHashMap<Integer,GetInfo> sendAndGets =
            new ConcurrentHashMap<Integer,GetInfo>(100);

    /**
     * Convenience class for storing data in a hashmap used for sending
     * a response to the "send" part of a sendAndGet.
     */
    static private class GetInfo {
        cMsgClientInfo info;
        cMsgNotifier notifier;
        GetInfo(cMsgClientInfo info) {
            this(info,null);
        }
        GetInfo(cMsgClientInfo info, cMsgNotifier notifier) {
            this.info = info;
            this.notifier = notifier;
        }
    }

    /**
     * Convenience class for storing data in a hashmap used for removing
     * sendAndGets which have timed out.
     */
    static private class DeleteGetInfo {
        String name;
        int senderToken;
        int sysMsgId;
        DeleteGetInfo(String name, int senderToken, int sysMsgId) {
            this.name = name;
            this.senderToken = senderToken;
            this.sysMsgId = sysMsgId;
        }
    }

    /**
     * HashMap to store mappings of local client's senderTokens to static sysMsgIds.
     * This allows the cancellation of a "sendAndGet" using a senderToken (which the
     * client knows) which can then be used to look up the sysMsgId and cancel the get.
     */
    static private ConcurrentHashMap<Integer,DeleteGetInfo> deleteGets =
            new ConcurrentHashMap<Integer,DeleteGetInfo>(100);

    /** Set of all subscriptions (including the subscribeAndGets) of regular & bridge clients. */
    static private HashSet<cMsgSubscription> subscriptions =
            new HashSet<cMsgSubscription>(100);

    /** This lock is used in global registrations for regular clients and server clients. */
    static private final ReentrantLock registrationLock = new ReentrantLock();

    /** This lock is used to ensure all access to {@link #subscriptions} is sequential. */
    static private final ReentrantLock subscribeLock = new ReentrantLock();

    static public void printStaticSizes() {
        System.out.println("cMsg subdomain static:");
        System.out.println("     servers       = " + servers.size());
        System.out.println("     clients       = " + clients.size());
        System.out.println("     sendAndGets   = " + sendAndGets.size());
        System.out.println("     deleteGets    = " + deleteGets.size());
        System.out.println("     subscriptions = " + subscriptions.size());
    }


    public void printSizes() {
        System.out.println("cMsg subdomain handler:");
        System.out.println("     sendToSet      = " + sendToSet.size());
        System.out.println("     subscriptions:");

        subscribeLock.lock();
        for (cMsgSubscription sub : subscriptions) {
            sub.printSizes();
            System.out.println();
        }
        subscribeLock.unlock();
    }

    /** Set of client info objects to send the message to. */
    private HashSet<cMsgClientInfo> sendToSet = new HashSet<cMsgClientInfo>(100);

    /** Level of debug output for this class. */
    private int debug = cMsgConstants.debugError;

    /** Remainder of UDL client used to connect to domain server. */
    private String UDLRemainder;

    /** Namespace this client sends messages to. */
    private String namespace;

    /** Name of client using this subdomain handler. */
    private String name;

    /** Object containing information about the client this object corresponds to. */
    private cMsgClientInfo myInfo;



    /** No-arg constructor. */
    public cMsg() {}

    /**
     * Getter for namespace. This is needed in the registration process for the
     * client. Since it is the subdomain handler object which parses out the namespace
     * and the client/namespace combo must be unique, the registration process must
     * query the subdomain handler what the client's namespace would be if it's accepted
     * as a client.
     *
     * @return namespace of client
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * This lock must be locked before a client registration in the cMsg subdomain
     * can be made. Because this method and its lock are static, there is effectively
     * one lock per cMsg server.
     * @param delay time in milliseconds to wait for the lock before timing out
     * @return true if lock successful, false otherwise
     */
    static public boolean registrationLock(int delay) {
        try {
            return registrationLock.tryLock(delay, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            return false;
        }
    }


    /** Unlock the registration lock. */
    static public void registrationUnlock() {
        registrationLock.unlock();
    }


    /**
     * This method gets the names of all clients in the cMsg subdomain.
     * @return an array of names of all clients in the cMsg subdomain
     */
    public String[] getClientNames() {
        String[] s = new String[clients.size()];
        int i=0;
        synchronized (clients) {
            for (cMsgClientInfo ci : clients) {
                s[i++] = ci.getName();
            }
        }
        return s;
    }


    /**
     * This method gets the names and namespaces of all clients in the cMsg subdomain.
     * @return  an array of names and namespaces of all clients in the cMsg subdomain
     */
    public String[] getClientNamesAndNamespaces() {
        String[] s = new String[2*clients.size()];
        int i=0;
        synchronized (clients) {
            for (cMsgClientInfo ci : clients) {
                s[i++] = ci.getName();
                s[i++] = ci.getNamespace();
            }
        }
        return s;
    }


    /**
     * Method saying this subdomain implements {@link #handleSendRequest}.
     * @return true
     */
    public boolean hasSend() {return true;}


    /**
     * Method saying this subdomain implements {@link #handleSyncSendRequest}.
     * @return true
     */
    public boolean hasSyncSend() {return true;}


    /**
     * Method saying this subdomain implements {@link #handleSubscribeAndGetRequest}.
     * @return true
     */
    public boolean hasSubscribeAndGet() {return true;}


    /**
     * Method saying this subdomain implements {@link #handleSendAndGetRequest}.
     * @return true
     */
    public boolean hasSendAndGet() {return true;}


    /**
     * Method saying this subdomain implements {@link #handleSubscribeRequest}.
     * @return true
     */
    public boolean hasSubscribe() {return true;}


    /**
     * Method saying this subdomain implements {@link #handleUnsubscribeRequest}.
     * @return true
     */
    public boolean hasUnsubscribe() {return true;}


    /**
     * Method saying this subdomain implements {@link #handleShutdownClientsRequest}.
     * @return true
     */
    public boolean hasShutdown() {return true;}


    /**
     * This method gives this subdomain handler the appropriate part
     * of the UDL the client used to talk to the domain server.
     * In the cMsg subdomain of the cMsg domain, each client sends messages to a namespace.
     * If no namespace is specified, the namespace is "/defaultNamespace".
     * The namespace is specified in the client supplied UDL as follows:
     *     cMsg:cMsg://<host>:<port>/cMsg/<namespace>
     * A single beginning forward slash is enforced in a namespace.
     * A question mark will terminate but will not be included in the namespace.
     * All trailing forward slashes will be removed.
     *
     * @param UDLRemainder last part of the UDL appropriate to the subdomain handler
     * @throws cMsgException if there's an invalid namespace
     */
    public void setUDLRemainder(String UDLRemainder) throws cMsgException {
        this.UDLRemainder = UDLRemainder;

        // if no namespace specified, set to default
        if (UDLRemainder == null || UDLRemainder.length() < 1) {
            namespace = "/defaultNamespace";
            if (debug >= cMsgConstants.debugInfo) {
               System.out.println("setUDLRemainder:  namespace = " + namespace);
            }
            return;
        }

        // parse UDLRemainder to find the namespace this client is in
        Pattern pattern = Pattern.compile("^([\\w/]*)\\?*.*");
        Matcher matcher = pattern.matcher(UDLRemainder);

        String s;

        if (matcher.lookingAt()) {
            s = matcher.group(1);
        }
        else {
            throw new cMsgException("invalid namespace");
        }

        if (s == null) {
            throw new cMsgException("invalid namespace");
        }

        // strip off all except one beginning slash and all ending slashes
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length()-1);
        }

        // if namespace is blank, use default
        if (s.equals("")) {
            namespace = "/defaultNamespace";
        }
        else {
            namespace = "/" + s;
        }

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("setUDLRemainder:  namespace = " + namespace);
        }
    }


    /**
     * Method to give the subdomain handler on object able to deliver messages
     * to the client. This copy of the deliverer is not used in the cMsg subdomain --
     * only the one in the cMsgClientInfo object.
     *
     * @param deliverer object able to deliver messages to the client
     */
    public void setMessageDeliverer(cMsgDeliverMessageInterface deliverer) {}


    /**
     * Method to see if domain client is registered.
     * @param name name of client
     * @return true if client registered, false otherwise
     */
    public boolean isRegistered(String name) {
        synchronized (clients) {
            for (cMsgClientInfo ci : clients) {
                if ( name.equals(ci.getName()) ) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Method to register a domain client.
     *
     * @param info information about client
     * @throws cMsgException if client already exists or argument is null
     */
    public void registerClient(cMsgClientInfo info) throws cMsgException {
        // Need meaningful client information
        if (info == null) {
            cMsgException e = new cMsgException("argument is null");
            e.setReturnCode(cMsgConstants.errorBadArgument);
            throw e;
        }

        synchronized (clients) {
            for (cMsgClientInfo ci : clients) {
                if ( info.getName().equals(ci.getName()) ) {
//System.out.println("Already a client by the name of " + info.getName());
                    // There already is a client by this name.
                    // Check to see if the namespace is the same as well.
                    if (namespace.equals(ci.getNamespace())) {
                        cMsgException e = new cMsgException("client already exists");
                        e.setReturnCode(cMsgConstants.errorAlreadyExists);
                        throw e;
                    }
                }
            }
            clients.add(info);
        }

        this.name   = info.getName();
        this.myInfo = info;

        // this client is registered in this namespace
        info.setNamespace(namespace);
    }


    /**
     * Method to register cMsg domain server as client.
     * Name is of the form "nameServerHost:nameServerPort".
     *
     * @param info information about client
     */
    public void registerServer(cMsgClientInfo info) {
        String clientName = info.getName();

        servers.putIfAbsent(clientName, info);

        this.name   = clientName;
        this.myInfo = info;

        // this client is registered in this namespace
        info.setNamespace(namespace);
    }


    /**
     * This method handles a message sent by a local bridge object's callback.
     * Try to follow this. A bridge object has a server client. That client of
     * another cMsg server has a callback. So when messages come in from the remote
     * server, the callback gets run. That callback uses the single subdomain handler
     * for the bridge and passes the message on to this server by means of this method.
     * Thus, the message which originated on another server gets passes around to the
     * REGULAR clients of this server (that's why it's called a LOCAL send). If it were
     * to pass these messages to ALL clients on this server, we could get infinite loops.
     *
     * The message's subject and type are matched against all clients' subscriptions.
     * For each client, the message is compared to each of its subscriptions until
     * a match is found. At that point, the message is sent to that client.
     * The client is responsible for finding all the matching gets
     * and subscribes and distributing the message among them as necessary.
     * No messages are sent to server clients to avoid infinite loops.
     *
     * This method is synchronized because the use of {@link #sendToSet} is not
     * thread-safe otherwise. Multiple threads in the domain server can be calling
     * this object's methods simultaneously.
     *
     * @param message message from sender
     * @param namespace namespace of original message sender
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    synchronized public void localSend(cMsgMessage message, String namespace) throws cMsgException {
//System.out.println("\nIN localSend\n");
        if (message == null) return;

        // start by sending nothing to no one
        sendToSet.clear();

        // If message is sent in response to a sendAndGet ...
        if (message.isGetResponse()) {
            int id = message.getSysMsgId();
            // Recall the client who originally sent the get request
            // and remove the item from the hashtable
//System.out.println(" localSend, get rid of send&Get id = " + id);
            deleteGets.remove(id);
            GetInfo gi = sendAndGets.remove(id);
            cMsgClientInfo info = null;
            if (gi != null) {
//System.out.println("          , got a send&get object");
                info = gi.info;
            }

            // If this is the first response to a sendAndGet ...
            if (info != null) {
//System.out.println(" localSend: handle send msg for send&get to " + info.getName());
                    // fire notifier
                    if (gi.notifier != null) {
//System.out.println("          , fire notifier for send&Get response");
                        gi.notifier.latch.countDown();
                    }
//                    else {
//System.out.println("          , NO notifier");
//                    }
                try {
                    // deliver message
                    info.getDeliverer().deliverMessage(message, cMsgConstants.msgGetResponse);
                }
                catch (IOException e) {
                }
                return;
            }
            // If we're here, treat it like it's a normal message.
            // Send it like any other to all subscribers.
        }

        // Scan through subscriptions of regular clients. Don't send to bridges.
        // Lock for subscriptions
        subscribeLock.lock();
        try {
            cMsgSubscription sub;
            Iterator it = subscriptions.iterator();

            while (it.hasNext()) {
                sub = (cMsgSubscription)it.next();

                // send only to matching namespace
                if (!namespace.equalsIgnoreCase(sub.getNamespace())) {
                    continue;
                }

                // subscription subject and type must match msg's
                /*
                if (!cMsgMessageMatcher.matches(sub.getSubjectPattern(), message.getSubject()) ||
                    !cMsgMessageMatcher.matches(sub.getTypePattern(), message.getType())) {
                    continue;
                }
                */
                if (!cMsgMessageMatcher.matches(message.getSubject(), message.getType(), sub)) {
                    continue;
                }

                // Put all subscribers and subscribeAndGetters of this
                // subscription in the set of clients to send to.
                sendToSet.addAll(sub.getClientSubscribers());
                sendToSet.addAll(sub.getSubAndGetters().keySet());

                // Clear subAndGetter lists as they're only a 1-shot deal.
                // Note that all subscribeAndGets are done by local clients.
                // Servers implement sub&Get with subscribes.
                sub.clearSubAndGetters();

                // fire off all notifiers for this subscription
                for (cMsgNotifier notifier : sub.getNotifiers()) {
//System.out.println("subdh: bridgeSend: Firing notifier");
                    notifier.latch.countDown();
                }
                sub.clearNotifiers();

                // delete sub if no more subscribers
                if (sub.numberOfSubscribers() < 1) {
//System.out.println("subdh: bridgeSend: remove subscription");
                    it.remove();
                }
            }
        }
        finally {
            subscribeLock.unlock();
        }

        // Once we have the subscription/get, msg, and client info,
        // no more need for sychronization
        for (cMsgClientInfo client : sendToSet) {
            // Deliver this msg to this client.
            try {
                // message delivery in cMsg subdomain is synchronized
                client.getDeliverer().deliverMessage(message, cMsgConstants.msgSubscribeResponse);
            }
            catch (IOException e) { }
        }
    }


    /**
     * This method handles a message sent by reuglar (non-server) client. The message's subject
     * and type are matched against all clients' subscriptions. For each client, the message is
     * compared to each of its subscriptions until a match is found. At that point, the message
     * is sent to that client. The client is responsible for finding all the matching gets
     * and subscribes and distributing the message among them as necessary.
     *
     * This method is synchronized because the use of sendToSet is not
     * thread-safe otherwise. Multiple threads in the domain server can be calling
     * this object's methods simultaneously.
     *
     * @param message message from sender
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    synchronized public void handleSendRequest(cMsgMessageFull message) throws cMsgException {
//System.out.println("\nIN REGULAR SEND\n");
        if (message == null) return;

        // start by sending nothing to no one
        sendToSet.clear();

        // If message is sent in response to a sendAndGet ...
        if (message.isGetResponse()) {
            int id = message.getSysMsgId();
            // Recall the client who originally sent the get request
            // and remove the item from the hashtable
//System.out.println(" Reg subdh handleSendRequest, get rid of send&Get id = " + id);
            deleteGets.remove(id);
            GetInfo gi = sendAndGets.remove(id);
            cMsgClientInfo info = null;
            if (gi != null) {
//System.out.println("                          , got a specific Gets object");
                info = gi.info;
            }

            // If this is the first response to a sendAndGet ...
            if (info != null) {
                int flag;

                if (gi.notifier != null) {
//System.out.println("                          , fire notifier for send&Get response");
                    gi.notifier.latch.countDown();
                }
                else {
//System.out.println("                          , NO notifier");
                }

                if (info.isServer()) {
                    flag = cMsgConstants.msgServerGetResponse;
                }
                else {
                    flag = cMsgConstants.msgGetResponse;
                }

                try {
//System.out.println(" handle send msg for send&get to " + info.getName());
                    info.getDeliverer().deliverMessage(message, flag);
                }
                catch (IOException e) {
                    return;
                }
                return;
            }
            // If we're here, treat it like it's a normal message.
            // Send it like any other to all subscribers.
        }

        // Scan through all subscriptions.
        // Lock for subscriptions
        subscribeLock.lock();
        try {
            cMsgSubscription sub;
            Iterator it = subscriptions.iterator();

            while (it.hasNext()) {
                sub = (cMsgSubscription)it.next();

                // send only to matching namespace
//System.out.println("handleSendRequest(subdh): compare sub ns = " + sub.getNamespace() +
//                   " to my ns = " + namespace);
                if (!namespace.equalsIgnoreCase(sub.getNamespace())) {
                    continue;
                }

                // subscription subject and type must match msg's
//                if (!cMsgMessageMatcher.matches(sub.getSubjectPattern(), message.getSubject()) ||
//                    !cMsgMessageMatcher.matches(sub.getTypePattern(), message.getType())) {
//                    continue;
//                }
                if (!cMsgMessageMatcher.matches(message.getSubject(), message.getType(), sub)) {
                    continue;
                }

                // Put all subscribers and subscribeAndGetters of this
                // subscription in the set of clients to send to.
//System.out.println("handleSendRequest(subdh): add client to send list");
                sendToSet.addAll(sub.getAllSubscribers());
                sendToSet.addAll(sub.getSubAndGetters().keySet());

//System.out.println("  A# of subscribers = " + sub.getAllSubscribers().size());
//System.out.println("  A# of sub&Getters = " + sub.getClientSubAndGetters().size());
                //Iterator it1 = sub.getClientSubAndGetters().keySet().iterator();
                //cMsgClientInfo info1 =  (cMsgClientInfo)it1.next();
                //System.out.println("  subs count of sub&Getters = " + sub.getClientSubAndGetters().get(info1));
                // clear subAndGetter list as it's only a 1-shot deal
                sub.clearSubAndGetters();
//System.out.println("  B# of subscribers = " + sub.getAllSubscribers().size());
//System.out.println("  B# of sub&Getters = " + sub.getClientSubAndGetters().size());
                //System.out.println("  subs count of sub&Getters = " + sub.getClientSubAndGetters().get(info1));

                // fire off all notifiers for this subscription
                for (cMsgNotifier notifier : sub.getNotifiers()) {
//System.out.println("handleSendRequest(subdh): Firing notifier");
                    notifier.latch.countDown();
                }
                sub.clearNotifiers();

                // delete sub if no more subscribers
                if (sub.numberOfSubscribers() < 1) {
//System.out.println("handleSendRequest(subdh): remove subscription");
                    it.remove();
                }
            }
        }
        finally {
            subscribeLock.unlock();
        }

        // Once we have the subscription/get, msg, and client info,
        // no more need for sychronization
        for (cMsgClientInfo client : sendToSet) {
            // Deliver this msg to this client.
            try {
//System.out.println("handleSendRequest(subdh): send msg to client " + client.getName());
                client.getDeliverer().deliverMessage(message, cMsgConstants.msgSubscribeResponse);
            }
            catch (IOException e) { }
        }
    }



    /**
     * Method to handle message sent by client in synchronous mode.
     * It requires a synchronous integer response from this object.
     *
     * @param message message from sender
     * @return response from this object
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    public int handleSyncSendRequest(cMsgMessageFull message) throws cMsgException {
        handleSendRequest(message);
        return 0;
    }


    /**
     * Method to handle subscribe request sent by regular client.
     *
     * @param subject  subject to subscribe to
     * @param type     type to subscribe to
     * @param id       id (not used)
     * @throws cMsgException if a subscription for this subject and type already exists for this client
     */
    public void handleSubscribeRequest(String subject, String type, int id)
            throws cMsgException {

        boolean subscriptionExists = false;
        cMsgSubscription sub = null;

        subscribeLock.lock();

        try {

            Iterator it = subscriptions.iterator();

            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {

                    if (sub.containsSubscriber(myInfo)) {
                        throw new cMsgException("handleSubscribeRequest: subscription already exists for subject = " +
                                                subject + " and type = " + type);
                    }
                    // found existing subscription to subject and type so add this client to its list
                    subscriptionExists = true;
                    break;
                }
            }

            // add this client to an exiting subscription
            if (subscriptionExists) {
//System.out.println("handleSubscribeRequest ADD sub: subject = " + subject + ", type = " + type + ", ns = " + namespace);
                sub.addSubscriber(myInfo);
                sub.addClientSubscriber(myInfo);
            }
            // or else create a new subscription
            else {
                sub = new cMsgSubscription(subject, type, namespace);
//System.out.println("handleSubscribeRequest NEW sub: subject = " + subject + ", type = " + type + ", ns = " + namespace);
                sub.addSubscriber(myInfo);
                sub.addClientSubscriber(myInfo);
                subscriptions.add(sub);
            }
        }
        finally {
            // Lock for subscriptions
            subscribeLock.unlock();
        }
    }


    /**
     * Method to handle subscribe request sent by another cMsg server (server client).
     *
     * @param subject subject subscribed to
     * @param type    type subscribed to
     * @param namespace namespace subscription resides in
     * @throws cMsgException if subscription already exists for this particular server client
     */
    public void handleServerSubscribeRequest(String subject, String type, String namespace)
            throws cMsgException {

        boolean subscriptionExists = false;
        cMsgSubscription sub = null;

        subscribeLock.lock();

        try {

            Iterator it = subscriptions.iterator();

            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {

                    if (sub.containsSubscriber(myInfo)) {
                        throw new cMsgException("handleServerSubscribeRequest: subscription already exists for subject = " +
                                                subject + " and type = " + type);
                    }
                    // found existing subscription to subject and type so add this client to its list
                    subscriptionExists = true;
                    break;
                }
            }

            // add this client to an exiting subscription
            if (subscriptionExists) {
//System.out.println("handleServerSubscribeRequest ADD sub: subject = " + subject + ", type = " + type + ", ns = " + namespace);
                sub.addSubscriber(myInfo);
            }
            // or else create a new subscription
            else {
                sub = new cMsgSubscription(subject, type, namespace);
//System.out.println("handleServerSubscribeRequest NEW sub: subject = " + subject + ", type = " + type + ", ns = " + namespace);
                sub.addSubscriber(myInfo);
                subscriptions.add(sub);
            }
        }
        finally {
            // Lock for subscriptions
            subscribeLock.unlock();
        }
    }



    /**
     * Method to handle unsubscribe request sent by regular client.
     *
     * @param subject  subject of subscription
     * @param type     type of subscription
     * @param id       id (not used)
     */
     public void handleUnsubscribeRequest(String subject, String type, int id) {
        cMsgSubscription sub = null;

        subscribeLock.lock();

        try {

            Iterator it = subscriptions.iterator();

            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {

//System.out.println("handleUNSubscribeRequest sub: subject = " + subject + ", type = " + type);
                    sub.removeSubscriber(myInfo);
                    sub.removeClientSubscriber(myInfo);
                    break;
                }
            }

            // get rid of this subscription if no more subscribers left
            if (sub != null  &&  sub.numberOfSubscribers() < 1) {
//System.out.println("  : remove entire subscription");
                subscriptions.remove(sub);
            }
        }
        finally {
            // Lock for subscriptions
            subscribeLock.unlock();
        }
    }



    /**
     * Method to handle unsubscribe request sent by another cMsg server (server client).
     *
     * @param subject  subject of subscription
     * @param type     type of subscription
     * @param namespace namespace subscription resides in
     */
     public void handleServerUnsubscribeRequest(String subject, String type, String namespace) {
        cMsgSubscription sub = null;

        subscribeLock.lock();

        try {

            Iterator it = subscriptions.iterator();

            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {
//System.out.println("SERVER UNSUBSCRIBE");
                    sub.removeSubscriber(myInfo);
                    break;
                }
            }

            // get rid of this subscription if no more subscribers left
            if (sub != null  &&  sub.numberOfSubscribers() < 1) {
                subscriptions.remove(sub);
            }
        }
        finally {
            // Lock for subscriptions
            subscribeLock.unlock();
        }
    }



    /**
     * Method to synchronously get a single message from a responder to a
     * message being sent by the client.
     *
     * @param message message requesting what sort of message to get
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    public void handleSendAndGetRequest(cMsgMessageFull message) throws cMsgException {
        // Create a unique number
        int id = sysMsgId.getAndIncrement();
        // Put that into the message
        message.setSysMsgId(id);
        // Store this client's info with the number as the key so any response to it
        // can retrieve this associated client
        sendAndGets.put(id, new GetInfo(myInfo));
        // Allow for cancelation of this sendAndGet
        DeleteGetInfo dgi = new DeleteGetInfo(name, message.getSenderToken(), id);
        deleteGets.put(id, dgi);
//System.out.println("handleS&GRequest: msg id = " + message.getSenderToken() +
//                           ", server id = " + id);

        // Now send this message on its way to any receivers out there.
        // SenderToken and sysMsgId get sent back by response. The sysMsgId
        // tells us which client to send to and the senderToken tells the
        // client which "get" to wakeup.
        handleSendRequest(message);
    }



    /**
     * Method to synchronously get a single message from a responder to a
     * message being sent by the client. This method is called as a result
     * of a regular client calling sendAndGet. If there are bridges to other
     * cMsg servers (server cloud contains more than one server), then this
     * server does a LOCAL sendAndGet (this method) before doing a sendAndGet
     * through all the bridge objects. The purpose of this is to install the
     * notifier object and letting local responders immediate ability to
     * respond to the message going out from this sendAndGet.
     *
     * @param message message requesting what sort of message to get
     * @param namespace namespace message resides in
     * @param notifier object used to notify others that a response message has arrived
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     * @return the unique id generated for the sendAndGet message to be sent
     */
    public int handleServerSendAndGetRequest(cMsgMessageFull message, String namespace,
                                              cMsgNotifier notifier)
            throws cMsgException {
        // Create a unique number
        int id = sysMsgId.getAndIncrement();
        // Put that into the message
        message.setSysMsgId(id);
        // Store this client's info with the number as the key so any response to it
        // can retrieve this associated client
        sendAndGets.put(id, new GetInfo(myInfo,notifier));
        // Allow for cancelation of this sendAndGet
        DeleteGetInfo dgi = new DeleteGetInfo(name, message.getSenderToken(), id);
        deleteGets.put(id, dgi);
//System.out.println("handleServerS&GRequest1: msg id = " + message.getSenderToken() +
//                                   ", server id = " + id);
//System.out.println("handleServerS&GRequest1: REGISTER NOTIFIER");

        // Now send this message on its way to any LOCAL receivers out there.
        // SenderToken and sysMsgId get sent back by response. The sysMsgId
        // tells us which client to send to and the senderToken tells the
        // client which "get" to wakeup.
        localSend(message, namespace);
        return id;
    }



    /**
     * Method to synchronously get a single message from a resonder to a message
     * beug sent by the server client. This method is called as a result of a bridge
     * client doing a serverSendAndGet to a remote server. No notifier is needed here.
     * We only want to send to local responders to avoid imfinite loops that would occur
     * if servers should get this request.
     *
     * @param message message requesting what sort of message to get
     * @param namespace namespace message resides in
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    public void handleServerSendAndGetRequest(cMsgMessageFull message, String namespace)
            throws cMsgException {
        // Create a unique number
        int id = sysMsgId.getAndIncrement();
        // Put that into the message
        message.setSysMsgId(id);
        // Store this client's info with the number as the key so any response to it
        // can retrieve this associated client
        sendAndGets.put(id, new GetInfo(myInfo));
        // Allow for cancelation of this sendAndGet
        DeleteGetInfo dgi = new DeleteGetInfo(name, message.getSenderToken(), id);
        deleteGets.put(id, dgi);
//System.out.println("handleServerS&GRequest2: msg id = " + message.getSenderToken() +
//                                           ", server id = " + id);

        // Now send this message on its way to any LOCAL receivers out there.
        localSend(message, namespace);
    }



    /**
     * Method to handle remove sendAndGet request sent by any client
     * (hidden from user).
     *
     * @param id sendAndGet message's senderToken id
     */
    public void handleUnSendAndGetRequest(int id) {
        int sysId = -1;
        DeleteGetInfo dgi;

        // Scan through list of name/senderToken value pairs. (This combo is unique.)
        // Find the one that matches ours and get its associated sysMsgId number.
        // Use that number as a key to remove the sendAndGet.
        for (Iterator i=deleteGets.values().iterator(); i.hasNext(); ) {
            dgi = (DeleteGetInfo) i.next();
            if (dgi.name.equals(name) && dgi.senderToken == id) {
                sysId = dgi.sysMsgId;
                i.remove();
                break;
            }
        }

        // If it has already been removed, forget about it
        if (sysId < 0) {
            return;
        }

        // clean up hashmap
        GetInfo myInfo = sendAndGets.remove(sysId);

        // fire notifier if there is one
        if ((myInfo != null) && (myInfo.notifier != null)) {
//System.out.println("handleUnSend&GetRequest: FIRE NOTIFIER & get rid of send&G due to timeout");
             myInfo.notifier.latch.countDown();
        }
    }


    /**
     * Method for regular client to synchronously get a single message from
     * the server for a one-time subscription of a subject and type.
     *
     * @param subject subject subscribed to
     * @param type    type subscribed to
     * @param id      id (not used)
     */
    public void handleSubscribeAndGetRequest(String subject, String type, int id) {
        boolean subscriptionExists = false;
        cMsgSubscription sub = null;

        // Lock subscriptions
        subscribeLock.lock();

        try {
            Iterator it = subscriptions.iterator();

//System.out.println("In sub&GetRequest:");
            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {

                    // found existing subscription to subject and type so add this client to its list
                    subscriptionExists = true;
                    break;
                }
            }

            // add this client to an exiting subscription
            if (subscriptionExists) {
//System.out.println("    add sub&Gettter");
//System.out.println("    SUBDH cli: add sub&Getter");
                sub.addSubAndGetter(myInfo);
            }
            // or else create a new subscription
            else {
//System.out.println("    create subscription");
                sub = new cMsgSubscription(subject, type, namespace);
//System.out.println("    SUBDH cli: create new sub & add sub&Getter");
                sub.addSubAndGetter(myInfo);
                subscriptions.add(sub);
            }
        }
        finally {
            // Lock for subscriptions
            subscribeLock.unlock();
        }
//System.out.println("    subs count of sub&Getters = " + sub.getClientSubAndGetters().get(myInfo));
    }


    // BUG BUG, registering sub & setting notifier must be "simultaneous" - no message sent
    // during that interval (synchronizing works but may be too restrictive
  //  * @param notifier object which allows the subdomain handler to notify other objects
  //  *                 that a message matching this subscription has been sent (by a local
  //  *                 client)
    /**
     * Method for regular client to synchronously get a single message from
     * the server for a one-time subscription of a subject and type.
     * This method is called as a result of a regular client calling subscribeAndGet.
     * If there are bridges to other cMsg servers (server cloud contains more than
     * one server), then this server does NOT call {@link #handleSubscribeAndGetRequest}
     * but instead calls this method after doing a subscribeAndGet through all the bridge
     * objects. The purpose of this is to install the notifier object.
     *
     * @param subject  subject subscribed to
     * @param type     type subscribed to
     * @param notifier object used to notify others that a matching message has arrived
     */
    public void handleServerSubscribeAndGetRequest(String subject, String type,
                                                   cMsgNotifier notifier) {
        boolean subscriptionExists = false;
        cMsgSubscription sub = null;

        subscribeLock.lock();

        try {

            Iterator it = subscriptions.iterator();
//System.out.println("In handleServerSub&GetRequest:");
            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {

                    // found existing subscription to subject and type so add this client to its list
                    subscriptionExists = true;
                    break;
                }
            }

            // add this client to an exiting subscription
            if (subscriptionExists) {
//System.out.println("    SUBDH server: add sub&Getter");
                sub.addSubAndGetter(myInfo);
            }
            // or else create a new subscription
            else {
                sub = new cMsgSubscription(subject, type, namespace);
//System.out.println("    SUBDH server: create subscription & add sub&Getter");
                sub.addSubAndGetter(myInfo);
                subscriptions.add(sub);
            }
//System.out.println("  add notifier");
            // Need to unsubscribe from remote servers if sub&Get is cancelled.
            // This object notifies of the need to do so.
            sub.addNotifier(notifier);
/*
            if (subscriptions.size() > 100 && subscriptions.size()%100 == 0) {
                System.out.println("subdh sub size = " + subscriptions.size());
            }
            if (sub.numberOfNotifiers() > 100 && sub.numberOfNotifiers()%100 == 0) {
                System.out.println("subdh sub size = " + sub.numberOfNotifiers());
            }
*/
        }
        finally {
            // Lock for subscriptions
            subscribeLock.unlock();
        }
    }


    /**
     * Method to handle a remove subscribeAndGet request sent by client
     * (hidden from user).
     *
     * @param subject  subject of subscription
     * @param type     type of subscription
     * @param id       request id
     */
    public void handleUnsubscribeAndGetRequest(String subject, String type, int id) {

        boolean subscriptionExists = false;
        cMsgSubscription sub = null;

        subscribeLock.lock();

        try {
            Iterator it = subscriptions.iterator();

//System.out.println("In UN Sub&GetRequest: s,t,ns = " + subject + ", " + type + ", " + namespace);
            while (it.hasNext()) {
                sub = (cMsgSubscription) it.next();
//System.out.println("  sub subject,type,ns = " + sub.getSubject() + ", " +
//                                   sub.getType() + ", " + sub.getNamespace());
                if (sub.getNamespace().equals(namespace) &&
                        sub.getSubject().equals(subject) &&
                        sub.getType().equals(type)) {
//System.out.println("  remove Sub&Getter from sub object");
//System.out.println("  subs A# of sub&Getters = " + sub.getClientSubAndGetters().size());
//System.out.println("  subs count of sub&Getters = " + sub.getClientSubAndGetters().get(myInfo));
                    sub.removeSubAndGetter(myInfo);
                    subscriptionExists = true;
//System.out.println("  removed Sub&Getter from sub object");
                    break;
                }
            }

            // If a msg was sent and sub removed simultaneously while a sub&Get (on client)
            // timed out so an unSub&Get was sent, ignore the unSub&get.
            // if (sub == null) return;
            if (!subscriptionExists) return;

            // get rid of this subscription if no more subscribers left
//System.out.println("  subs B# of sub&Getters = " + sub.getClientSubAndGetters().size());
//System.out.println("  subs count of sub&Getters = " + sub.getClientSubAndGetters().get(myInfo));
            if (sub.numberOfSubscribers() < 1) {
//System.out.println("  remove subscription object");
                subscriptions.remove(sub);
            }

            // fire notifier if one exists, then get rid of it
            for (cMsgNotifier notifier : sub.getNotifiers()) {
                if (notifier.client == myInfo  && notifier.id == id) {
//System.out.println("  fire notifier now and remove from subscription object, id = " + id);
                    notifier.latch.countDown();
                    sub.removeNotifier(notifier);
                    break;
                }
            }
        }
        finally {
            subscribeLock.unlock();
        }
    }



    /**
     * Method to handle request to shutdown clients sent by client.
     *
     * @param client client(s) to be shutdown
     * @param includeMe   if true, this client may be shutdown too
     * @throws cMsgException if a channel to the client is closed, cannot be created,
     *                       or socket properties cannot be set
     */
    public void handleShutdownClientsRequest(String client, boolean includeMe) throws cMsgException {

//System.out.println("dHandler: try to kill client " + client);
        // Match all clients that need to be shutdown.
        // Scan through all clients.
        for (cMsgClientInfo info : clients) {
            // Do not shutdown client sending this command, unless told to with flag "includeMe"
            String clientName = info.getName();
            if ( !includeMe && clientName.equals(name) ) {
//System.out.println("  dHandler: skip client " + clientName);
                continue;
            }

            if (cMsgMessageMatcher.matches(client, clientName, true)) {
                try {
//System.out.println("  dHandler: deliver shutdown message to client " + clientName);
                    info.getDeliverer().deliverMessage(null, cMsgConstants.msgShutdownClients);
                }
                catch (IOException e) {
                    if (debug >= cMsgConstants.debugError) {
                        System.out.println("dHandler: cannot tell client " + name + " to shutdown");
                    }
                }
            }
        }

        // match all servers that need to be shutdown (not implemented yet)
    }



    /**
      * Method to handle keepalive sent by domain client checking to see
      * if the domain server socket is still up. Normally nothing needs to
      * be done as the domain server simply returns an "OK" to all keepalives.
      * This method is run after all exchanges between domain server and client.
      */
     public void handleKeepAlive() {
     }


    /**
     * Method to handle a client or domain server shutdown.
     * This method is run after all exchanges between domain server and client but
     * before the domain server thread is killed (since that is what is running this
     * method).
     */
    public void handleClientShutdown() {
        if (debug >= cMsgConstants.debugWarn) {
            System.out.println("dHandler: SHUTDOWN client " + name);
        }

        clients.remove(myInfo);
        servers.remove(name);

        // Scan through list of name/senderToken value pairs.
        // Find those that match our client name and get the
        // associated sysMsgId numbers. Use that number as a
        // key to remove the sendAndGet.
        int sysId = -1;
        DeleteGetInfo dgi;
        for (Iterator it=deleteGets.values().iterator(); it.hasNext(); ) {
            dgi = (DeleteGetInfo) it.next();
            if (dgi.name.equals(name)) {
                sysId = dgi.sysMsgId;
                it.remove();
            }

            // If it has already been removed, forget about it
            if (sysId > -1) {
//System.out.println("  CLIENT SHUTDOWN: remove specific get for " + name);
                sendAndGets.remove(sysId);
            }
        }

        // Remove subscribes and subscribeAndGets
        cMsgSubscription sub;
        subscribeLock.lock();
        try {
            for (Iterator it=subscriptions.iterator(); it.hasNext(); ) {
                sub = (cMsgSubscription) it.next();
                // remove any subscribes
                boolean b = sub.removeSubscriber(myInfo);

/*
                if (b)
                    System.out.println("  CLIENT SHUTDOWN: removed subscriber");
                else
                    System.out.println("  CLIENT SHUTDOWN: did NOT remove subscriber");
*/

                b = sub.removeClientSubscriber(myInfo);

/*
                if (b)
                    System.out.println("  CLIENT SHUTDOWN: removed client subscriber");
                else
                    System.out.println("  CLIENT SHUTDOWN: did NOT remove client subscriber");
*/

//                int count = sub.getSubAndGetters().get(myInfo);
//System.out.println("  CLIENT SHUTDOWN: removed " + count + " sub&Gets");

                // remove any subscribeAndGets
                sub.removeSubAndGetter(myInfo);
                // fire associated notifier if one exists, then get rid of it
//System.out.println("  CLIENT SHUTDOWN: number of  notifiers = " + (sub.getNotifiers().size()));
                cMsgNotifier notifier;
                for (Iterator it2 = sub.getNotifiers().iterator(); it2.hasNext(); ) {
                    notifier = (cMsgNotifier) it2.next();
                    if (notifier.client == myInfo) {
//System.out.println("  CLIENT SHUTDOWN: fire notifier and remove");
                        notifier.latch.countDown();
                        it2.remove();
                    }
                }

                // get rid of this subscription entirely if no more subscribers left
                if (sub.numberOfSubscribers() < 1) {
//System.out.println("  CLIENT SHUTDOWN: removed subscription entirely");
                    it.remove();
                }
            }
        }
        finally {
            subscribeLock.unlock();
        }
//System.out.println("  CLIENT SHUTDOWN: EXITING METHOD");

    }


}
