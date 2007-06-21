/*----------------------------------------------------------------------------*
 *  Copyright (c) 2004        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 16-Nov-2005, Jefferson Lab                                   *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-6B         *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.client;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.cMsg.cMsgDomain.server.cMsgNameServer;

import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.*;
import java.io.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * This class implements a cMsg client which is created in a cMsg server for the
 * purpose of communicating with other cMsg servers in the cMsg domain.
 *
 * @author Carl Timmer
 * @version 1.0
 */
public class cMsgServerClient extends cMsg {
    /** NameServer that this server client object resides in. */
    private cMsgNameServer nameServer;

    /**
     * Collection of all of this server client's {@link cMsgSendAndGetCallbackThread} objects
     * which are threads waiting for results of a {@link #serverSendAndGet} on a connected server.
     *
     * Key is senderToken object, value is {@link cMsgSendAndGetCallbackThread} object.
     */
    ConcurrentHashMap<Integer,cMsgSendAndGetCallbackThread> serverSendAndGets;

    /**
     * Collection of all Future objects from this server client's {@link #serverSendAndGet}
     * calls. These objects allow the cancellation of such a call currently in execution.
     * Interrupting these threads directly seems to cause problems with the thread pool
     * executor.
     *
     * Key is senderToken object, value is {@link Future} object.
     */
    ConcurrentHashMap<Integer,Future<Boolean>> serverSendAndGetCancel;

    /** This method prints sizes of maps for debugging purposes. */
    public void printSizes() {
        System.out.println("              cli send&Gets         = " + serverSendAndGets.size());
        System.out.println("              cli send&Gets cancels = " + serverSendAndGetCancel.size());
    }

    /** A pool of threads to handle all the sendAndGetCallback threads. */
    private ThreadPoolExecutor sendAndGetCallbackThreadPool;

    /**
     * Class for telling a thread pool what to do for rejected requests to start a new
     * thread (when the pool already is using its maximum number of threads.
     */
    class RejectHandler implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // Just run a new thread
//System.out.println("REJECT HANDLER: start new sendAndGet callback thread");
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Constructor.
     * @param nameServer nameServer this client is running in
     * @throws cMsgException if local host name cannot be found
     */
    public cMsgServerClient(cMsgNameServer nameServer) throws cMsgException {
        super();
        this.nameServer = nameServer;

        serverSendAndGets      = new ConcurrentHashMap<Integer,cMsgSendAndGetCallbackThread>(20);
        serverSendAndGetCancel = new ConcurrentHashMap<Integer,Future<Boolean>>(20);
        // Run up to 5 threads with no queue. Wait 2 min before terminating
        // extra (more than 5) unused threads. Overflow tasks spawn independent
        // threads.
        sendAndGetCallbackThreadPool =
                new ThreadPoolExecutor(5, 5, 120L, TimeUnit.SECONDS,
                                       new SynchronousQueue(),
                                       new RejectHandler());

    }


//-----------------------------------------------------------------------------


    /** Method to clean up after this object. */
    public void cleanup() {
        // shutdown thread pool threads
        sendAndGetCallbackThreadPool.shutdownNow();

        // clear hashes
        serverSendAndGets.clear();
        serverSendAndGetCancel.clear();
    }


//-----------------------------------------------------------------------------


    /**
     * Method to connect to the domain server from a cMsg server acting as a bridge.
     * This method is only called by the bridge object
     * {@link org.jlab.coda.cMsg.cMsgDomain.server.cMsgServerBridge}.
     * Unfortunately, this is a method largely duplicated from the base class but
     * with a few small changes.
     *
     * @param fromNameServerPort port of name server calling this method
     * @param isOriginator true if originating the connection between the 2 servers and
     *                     false if this is the response or reciprocal connection
     * @param cloudPassword password for connecting to a server in a particular cloud
     * @return set of servers (names of form "host:port") that the server
     *         we're connecting to is already connected with
     * @throws cMsgException if there are problems parsing the UDL or
     *                       communication problems with the server
     */
    public HashSet<String> connect(int fromNameServerPort, boolean isOriginator,
                                   String cloudPassword) 
            throws cMsgException {
        
        // list of servers that the server we're connecting to is already connected with
        HashSet<String> serverSet = null;

        // parse the UDL (Uniform Domain Locator)
        ParsedUDL p = parseUDL(UDL);
        p.copyToLocal();

        creator = name+":"+nameServerHost+":"+nameServerPort;

        // cannot run this simultaneously with any other public method
        connectLock.lock();
        try {
            if (connected) return null;

            // read env variable for starting port number
            try {
                String env = System.getenv("CMSG_CLIENT_PORT");
                if (env != null) {
                    startingPort = Integer.parseInt(env);
                }
            }
            catch (NumberFormatException ex) {
            }

            // port #'s < 1024 are reserved
            if (startingPort < 1024) {
                startingPort = cMsgNetworkConstants.clientServerStartingPort;
            }

            // At this point, find a port to bind to. If that isn't possible, throw
            // an exception.
            try {
                serverChannel = ServerSocketChannel.open();
            }
            catch (IOException ex) {
                ex.printStackTrace();
                throw new cMsgException("connect: cannot open a listening socket");
            }

            port = startingPort;
            ServerSocket listeningSocket = serverChannel.socket();

            while (true) {
                try {
                    listeningSocket.bind(new InetSocketAddress(port));
                    break;
                }
                catch (IOException ex) {
                    // try another port by adding one
                    if (port < 65536) {
                        port++;
                    }
                    else {
                        // close channel
                        try { serverChannel.close(); }
                        catch (IOException e) { }

                        ex.printStackTrace();
                        throw new cMsgException("connect: cannot find port to listen on");
                    }
                }
            }

            // launch thread and start listening on receive socket
            listeningThread = new cMsgClientListeningThread(this, serverChannel);
            listeningThread.start();

            // Wait for indication thread is actually running before
            // continuing on. This thread must be running before we talk to
            // the name server since the server tries to communicate with
            // the listening thread.
            synchronized (listeningThread) {
                if (!listeningThread.isAlive()) {
                    try {
                        listeningThread.wait();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            // connect & talk to cMsg name server to check if name is unique
            SocketChannel channel = null;
            try {
//System.out.println("        << CL: open socket to  " + nameServerHost + ":" + nameServerPort);
                channel = SocketChannel.open(new InetSocketAddress(nameServerHost, nameServerPort));
                // set socket options
                Socket socket = channel.socket();
                // Set tcpNoDelay so no packets are delayed
                socket.setTcpNoDelay(true);
                // no need to set buffer sizes
            }
            catch (IOException e) {
                // undo everything we've just done
                listeningThread.killThread();
                try {if (channel != null) channel.close();} catch (IOException e1) {}

                if (debug >= cMsgConstants.debugError) {
                    e.printStackTrace();
                }
                throw new cMsgException("connect: cannot create channel to name server");
            }

            // get host & port to send messages & other info from name server
            try {
                // Returns list of servers that the server we're
                // connecting to is already connected with.
                serverSet = talkToNameServerFromServer(channel,
                                                       nameServer.getCloudStatus(),
                                                       fromNameServerPort,
                                                       isOriginator,
                                                       cloudPassword);
            }
            catch (IOException e) {
                // undo everything we've just done
                listeningThread.killThread();
                try {channel.close();} catch (IOException e1) {}

                if (debug >= cMsgConstants.debugError) {
                    e.printStackTrace();
                }
                throw new cMsgException("connect: cannot talk to name server");
            }

            // done talking to server
            try {
                channel.close();
            }
            catch (IOException e) {
                if (debug >= cMsgConstants.debugError) {
                    System.out.println("connect: cannot close channel to name server, continue on");
                    e.printStackTrace();
                }
            }

            // create request response reading (from domain) channel
            try {
                domainInChannel = SocketChannel.open(new InetSocketAddress(domainServerHost, domainServerPort));
                // buffered communication streams for efficiency
                Socket socket = domainInChannel.socket();
                socket.setTcpNoDelay(true);
                socket.setReceiveBufferSize(2048);
                domainIn = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 2048));
            }
            catch (IOException e) {
                // undo everything we've just done
                listeningThread.killThread();
                try {channel.close();} catch (IOException e1) {}
                try {if (domainInChannel != null) domainInChannel.close();} catch (IOException e1) {}

                if (debug >= cMsgConstants.debugError) {
                    e.printStackTrace();
                }
                throw new cMsgException("connect: cannot create channel to domain server");
            }

            // create keepAlive socket
            try {
                keepAliveChannel = SocketChannel.open(new InetSocketAddress(domainServerHost, domainServerPort));
                Socket socket = keepAliveChannel.socket();
                socket.setTcpNoDelay(true);

                // Create thread to send periodic keep alives and handle dead server
                // but with no failover capability.
                keepAliveThread = new KeepAlive(keepAliveChannel, false, null);
                keepAliveThread.start();
            }
            catch (IOException e) {
                // undo everything we've just done so far
                listeningThread.killThread();
                try { channel.close(); }         catch (IOException e1) {}
                try { domainInChannel.close(); } catch (IOException e1) {}
                try { if (keepAliveChannel != null) keepAliveChannel.close(); } catch (IOException e1) {}
                if (keepAliveThread != null) keepAliveThread.killThread();

                if (debug >= cMsgConstants.debugError) {
                    e.printStackTrace();
                }
                throw new cMsgException("connect: cannot create keepAlive channel to domain server");
            }

            // create request sending (to domain) channel (This takes longest so do last)
            try {
                domainOutChannel = SocketChannel.open(new InetSocketAddress(domainServerHost, domainServerPort));
                // buffered communication streams for efficiency
                Socket socket = domainOutChannel.socket();
                socket.setTcpNoDelay(true);
                socket.setSendBufferSize(65535);
                domainOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
            }
            catch (IOException e) {
                // undo everything we've just done so far
                listeningThread.killThread();
                keepAliveThread.killThread();
                try { channel.close(); }          catch (IOException e1) {}
                try { domainInChannel.close(); }  catch (IOException e1) {}
                try { keepAliveChannel.close(); } catch (IOException e1) {}
                try {if (domainOutChannel != null) domainOutChannel.close();} catch (IOException e1) {}

                if (debug >= cMsgConstants.debugError) {
                    e.printStackTrace();
                }
                throw new cMsgException("connect: cannot create channel to domain server");
            }

            connected = true;
        }
        finally {
            connectLock.unlock();
        }

//System.out.println("        << CL: done connecting to  " + nameServerHost + ":" + nameServerPort);
        return serverSet;
    }


//-----------------------------------------------------------------------------


    /**
     * The message is sent by a server client to another server as it would be in the
     * {@link #send} method. The receiving server notes
     * the fact that a response to it is expected, and sends it to all subscribed to its
     * subject and type. When a marked response is received from a client, it sends that
     * first response back to the original sender regardless of its subject or type.
     *
     * @param message message sent to server
     * @param namespace namespace of message sent
     * @param cb callback to run on receipt of response message
     * @return the receiverSubscribe id number of the sendAndGet
     * @throws IOException if there are communication problems with the server
     */
    public int serverSendAndGet(cMsgMessage message, String namespace,
                                cMsgCallbackInterface cb) throws IOException {
        int id = 0;

        String subject    = message.getSubject();
        String type       = message.getType();
        String text       = message.getText();
        String msgCreator = message.getCreator();
        // this sender's creator if msg created here
        if (msgCreator == null) msgCreator = creator;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            // First generate a unique id for the receiveSubscribeId and senderToken field.
            // We're expecting a specific response, so the senderToken is sent back
            // in the response message, allowing us to run the correct callback.
            id = uniqueId.getAndIncrement();

            // create callback thread
            cMsgSendAndGetCallbackThread thd = new cMsgSendAndGetCallbackThread(cb, namespace);
            // run callback thread in thread pool
            Future<Boolean> future = sendAndGetCallbackThreadPool.submit(thd);
            // track specific get requests
            serverSendAndGets.put(id, thd);
            serverSendAndGetCancel.put(id, future);

            int binaryLength = message.getByteArrayLength();

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(4 * 15 + subject.length() + type.length() + namespace.length() +
                        msgCreator.length() + text.length() + binaryLength);

                domainOut.writeInt(cMsgConstants.msgServerSendAndGetRequest);
                domainOut.writeInt(0); // reserved for future use
                domainOut.writeInt(message.getUserInt());
                domainOut.writeInt(id);
                domainOut.writeInt(message.getInfo() | cMsgMessage.isGetRequest);

                long now = new Date().getTime();
                // send the time in milliseconds as 2, 32 bit integers
                domainOut.writeInt((int) (now >>> 32)); // higher 32 bits
                domainOut.writeInt((int) (now & 0x00000000FFFFFFFFL)); // lower 32 bits
                domainOut.writeInt((int) (message.getUserTime().getTime() >>> 32));
                domainOut.writeInt((int) (message.getUserTime().getTime() & 0x00000000FFFFFFFFL));

                domainOut.writeInt(subject.length());
                domainOut.writeInt(type.length());
                domainOut.writeInt(namespace.length());
                domainOut.writeInt(msgCreator.length());
                domainOut.writeInt(text.length());
                domainOut.writeInt(binaryLength);

                // write strings & byte array
                try {
                    domainOut.write(subject.getBytes("US-ASCII"));
                    domainOut.write(type.getBytes("US-ASCII"));
                    domainOut.write(namespace.getBytes("US-ASCII"));
                    domainOut.write(msgCreator.getBytes("US-ASCII"));
                    domainOut.write(text.getBytes("US-ASCII"));
                    if (binaryLength > 0) {
                        domainOut.write(message.getByteArray(),
                                        message.getByteArrayOffset(),
                                        binaryLength);
                    }
                }
                catch (UnsupportedEncodingException e) {
                }
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock

        }
        // release lock 'cause we can't block connect/disconnect forever
        finally {
            notConnectLock.unlock();
        }

        return id;
    }


//-----------------------------------------------------------------------------


    /**
     * Method to remove a previous serverSendAndGet. This method is only called when a
     * serverSendAndGet times out and the server must be told to forget about the serverSendAndGet.
     *
     * @param id unique id of serverSendAndGet request to delete
     * @throws IOException if there are communication problems with the server
     */
    public void serverUnSendAndGet(int id) throws IOException {

        if (!connected) {
            throw new IOException("not connected to server");
        }

        // Kill off callback thread waiting for a response.
        cMsgSendAndGetCallbackThread thd = serverSendAndGets.remove(id);
        Future<Boolean> future = serverSendAndGetCancel.remove(id);
        if (thd != null) {
//System.out.println("serverUnSendAndGet: tell cb thread to DIE");
            future.cancel(true);
        }
        else {
            // There's a chance 2 unSendAndGets may be sent if a get
            // response is on its way and fires the notifier just before
            // the sendAndGet times out and sends its own unSendAndGet.
            // In this case, ignore it and return;
//System.out.println("serverUnSendAndGet: nothing to undo");
            return;
        }

        socketLock.lock();
        try {
            // total length of msg (not including this int) is 1st item
            domainOut.writeInt(8);
            domainOut.writeInt(cMsgConstants.msgServerUnSendAndGetRequest);
            domainOut.writeInt(id); // reserved for future use
            domainOut.flush();
        }
        finally {
            socketLock.unlock();
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method for a server to subscribe to receive messages of a subject
     * and type from another domain server. The combination of arguments must be unique.
     * In other words, only 1 subscription is allowed for a given set of subject,
     * type, callback, and userObj.
     *
     * @param subject    message subject
     * @param type       message type
     * @param namespace  message namespace
     * @param cb      callback object whose single method is called upon receiving a message
     *                of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @throws IOException there are communication problems with the server
     */
    public void serverSubscribe(String subject, String type, String namespace,
                                cMsgCallbackInterface cb, Object userObj)
            throws IOException {

        boolean addedHashEntry  = false;
        cMsgSubscription newSub = null;
        cMsgCallbackThread cbThread = null;

        try {
            // cannot run this simultaneously with connect or disconnect
            notConnectLock.lock();
            // cannot run this simultaneously with unsubscribe (get wrong order at server)
            // or itself (iterate over same hashtable)
            subscribeLock.lock();

            try {
                if (!connected) {
                    throw new IOException("not connected to server");
                }

                // null namespace means default namespace
                if (namespace == null) {
                    namespace = "/defaultNamespace";
                }

                // add to callback list if subscription to same subject/type exists

                int id;

                // client listening thread may be interating thru subscriptions concurrently
                // and we may change set structure
                synchronized (subscriptions) {

                    // for each subscription ...
                    for (cMsgSubscription sub : subscriptions) {
                        // If subscription to subject, type & namespace exist already, keep track of it
                        // locally and don't bother the server since any matching message will be delivered
                        // to this client anyway. The clients who call subscribe will never call
                        // serverSubscribe and vice versa so we may match namespaces in the following
                        // line of code and not worry about conflicts arising due to clients calling
                        // subscribe.
                        if (sub.getSubject().equals(subject) &&
                                sub.getType().equals(type) &&
                                sub.getNamespace().equals(namespace)) {

                            // for each callback listed ...
                            for (cMsgCallbackThread cbt : sub.getCallbacks()) {
                                // Unlike the regular subscribe, here we allow duplicate identical
                                // subscriptions. The reason is that "subscribeAndGet" is implemented
                                // for other servers in the cloud as a "subscribe".
                                //
                                // It is possible to for 2 different thds of a client to each do an
                                // identical subscribeAndGet. This results in 2 identical subscriptions
                                // which would normally not be permitted but in this case we must.
                                //
                                // This method is also used to implement the client's regular subscribes
                                // on other cloud servers. In this case, the client calls the regular
                                // subscribe which will not allow duplicate subscriptions. Thus, passing
                                // that on to other servers will also NOT result in duplicate subscriptions.
                                if ((cbt.getCallback() == cb) && (cbt.getArg() == userObj)) {
                                    // increment a count which will be decremented during an unsubscribe
//System.out.println("bridge cli sub: count = " + cbThread.getCount() + " -> " +
//(cbThread.getCount() + 1));
                                    cbt.setCount(cbt.getCount() + 1);
                                    return;
                                }
                            }

                            // add to existing set of callbacks
                            sub.addCallback(new cMsgCallbackThread(cb, userObj, domain, subject, type));
                            return;
                        }
                    }

                    // If we're here, the subscription to that subject & type in namespace does not exist yet.
                    // We need to create it and register it with the domain server.

                    // First generate a unique id for the receiveSubscribeId field. This info
                    // allows us to unsubscribe.
                    id = uniqueId.getAndIncrement();

                    // add a new subscription & callback
                    cbThread = new cMsgCallbackThread(cb, userObj, domain, subject, type);
                    newSub   = new cMsgSubscription(subject, type, id, cbThread);


                    newSub.setNamespace(namespace);
                    // client listening thread may be interating thru subscriptions concurrently
                    // and we're changing the set structure
                    subscriptions.add(newSub);
                    addedHashEntry = true;
                }
//System.out.println("bridge client sub to server, size = " + subscriptions.size());

                socketLock.lock();
                try {
                    // total length of msg (not including this int) is 1st item
                    domainOut.writeInt(4*5 + subject.length() + type.length() + namespace.length());
                    domainOut.writeInt(cMsgConstants.msgServerSubscribeRequest);
                    domainOut.writeInt(id); // reserved for future use
                    domainOut.writeInt(subject.length());
                    domainOut.writeInt(type.length());
                    domainOut.writeInt(namespace.length());

                    // write strings & byte array
                    try {
                        domainOut.write(subject.getBytes("US-ASCII"));
                        domainOut.write(type.getBytes("US-ASCII"));
                        domainOut.write(namespace.getBytes("US-ASCII"));
                    }
                    catch (UnsupportedEncodingException e) {}
                }
                finally {
                    socketLock.unlock();
                }

                domainOut.flush();
            }
            finally {
                subscribeLock.unlock();
                notConnectLock.unlock();
            }
        }
        catch (IOException e) {
            // undo the modification of the hashtable we made & stop the created thread
            if (addedHashEntry) {
                // "subscriptions" is synchronized so it's mutex protected
                subscriptions.remove(newSub);
                cbThread.dieNow(true);
            }
            throw e;
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method for a server to unsubscribe a previous subscription to receive messages of a subject and type
     * from another domain server. Since many subscriptions may be made to the same subject and type
     * values, but with different callbacks and user objects, the callback and user object must
     * be specified so the correct subscription can be removed.
     *
     * @param subject    message subject
     * @param type       message type
     * @param namespace  message namespace
     * @param cb      callback object whose single method is called upon receiving a message
     *                of subject and type
     * @param userObj any user-supplied object to be given to the callback method as an argument
     * @throws IOException there are communication problems with the server
     */
    public void serverUnsubscribe(String subject, String type, String namespace,
                                  cMsgCallbackInterface cb, Object userObj)
            throws IOException {

        cMsgSubscription   oldSub   = null;
        cMsgCallbackThread cbThread = null;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // cannot run this simultaneously with subscribe (get wrong order at server)
        // or itself (iterate over same hashtable)
        subscribeLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            // null namespace means default namespace
            if (namespace == null) {
                namespace = "/defaultNamespace";
            }

            boolean foundMatch = false;
            int id = 0;

            // client listening thread may be interating thru subscriptions concurrently
            // and we may change set structure
            synchronized (subscriptions) {

                // for each subscription ...
                for (cMsgSubscription sub : subscriptions) {
                    // If subscription to subject, type & namespace exist already, we may be
                    // able to take care of it locally and not bother the server.
                    // The clients who call unsubscribe will never call serverUnsubscribe and
                    // vice versa so we may match namespaces in the following line of code and
                    // not worry about conflicts arising due to clients calling unsubscribe.
                    if (sub.getSubject().equals(subject) &&
                            sub.getType().equals(type) &&
                            sub.getNamespace().equals(namespace)) {

                        // for each callback listed ...
                        for (cMsgCallbackThread cbt : sub.getCallbacks()) {
                            if ((cbt.getCallback() == cb) && (cbt.getArg() == userObj)) {
                                // Found our cb & userArg pair to get rid of.
                                // However, don't kill the thread and remove it from
                                // the callback set until the server is notified or
                                // the server doesn't need to be notified.
                                // That way we can "undo" the unsubscribe if there
                                // is an IO error.
                                foundMatch = true;
                                cbThread = cbt;

                                // first check the count
                                cbt.setCount(cbt.getCount() - 1);
                                if (cbt.getCount() > 0) {
                                    return;
                                }
                                break;
                            }
                        }

                        // if no subscription to sub/type/cb/arg, return
                        if (!foundMatch) {
                            return;
                        }

                        // If there are still callbacks left,
                        // don't unsubscribe for this subject/type
                        if (sub.numberOfCallbacks() > 1) {
//System.out.println("br cli serverUnsubscribe: callbacks > 0");
                            // kill callback thread
                            if (Thread.currentThread() == cbThread) {
                                //System.out.println("Don't interrupt my own thread!!!");
                                cbThread.dieNow(false);
                            }
                            else {
                                cbThread.dieNow(true);
                            }
                            // remove this callback from the set
                            sub.getCallbacks().remove(cbThread);
                            return;
                        }
                        // else get rid of the whole subscription
                        else {
//System.out.println("br cli serverUnsubscribe: remove subscription");
                            id = sub.getId();
                            oldSub = sub;
                            //iter.remove();
                        }
                        break;
                    }
                }
            }
            // if no subscription to sub/type/ns, return
            if (!foundMatch) {
//System.out.println("br cli serverUnsubscribe: NO SUB TO UNSUBSCRIBE");
                return;
            }

            // notify the domain server
//System.out.println("br cli server UNSUBSCRIBE: tell server");

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(5 * 4 + subject.length() + type.length() + namespace.length());
                domainOut.writeInt(cMsgConstants.msgServerUnsubscribeRequest);
                domainOut.writeInt(id); // reserved for future use
                domainOut.writeInt(subject.length());
                domainOut.writeInt(type.length());
                domainOut.writeInt(namespace.length());

                // write strings & byte array
                try {
                    domainOut.write(subject.getBytes("US-ASCII"));
                    domainOut.write(type.getBytes("US-ASCII"));
                    domainOut.write(namespace.getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {
                }
                domainOut.flush();
            }
            finally {
                socketLock.unlock();
            }

            // Now that we've communicated with the server,
            // delete stuff from hashes & kill threads -
            // basically, do the unsubscribe now.
            if (Thread.currentThread() == cbThread) {
                //System.out.println("Don't interrupt my own thread!!!");
                cbThread.dieNow(false);
            }
            else {
                cbThread.dieNow(true);
            }
            synchronized (subscriptions) {
                oldSub.getCallbacks().remove(cbThread);
                subscriptions.remove(oldSub);
            }

        }
        finally {
            subscribeLock.unlock();
            notConnectLock.unlock();
        }

    }


//-----------------------------------------------------------------------------


    /**
     * Method to shutdown the given clients.
     * Wildcards used to match client names with the given string.
     *
     * @param client client(s) to be shutdown
     * @param includeMe  if true, it is permissible to shutdown calling client
     * @throws IOException if there are communication problems with the server
     */
    public void serverShutdownClients(String client, boolean includeMe) throws IOException {

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            // make sure null args are sent as blanks
            if (client == null) {
                client = new String("");
            }

            int flag = includeMe ? cMsgConstants.includeMe : 0;

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(3*4 + client.length());
                domainOut.writeInt(cMsgConstants.msgServerShutdownClients);
                domainOut.writeInt(flag);
                domainOut.writeInt(client.length());

                // write string
                try {
                    domainOut.write(client.getBytes("US-ASCII"));
                }
                catch (UnsupportedEncodingException e) {}
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush();

        }
        finally {
            notConnectLock.unlock();
        }
    }


//-----------------------------------------------------------------------------


    /**
     * Method to shutdown the server connected to.
     * @throws IOException if there are communication problems with the server
     */
    public void serverShutdown() throws IOException {
        // cannot run this simultaneously with any other public method
        connectLock.lock();
        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(4);
                domainOut.writeInt(cMsgConstants.msgServerShutdownSelf);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush();

        }
        finally {
            connectLock.unlock();
        }
    }


//-----------------------------------------------------------------------------


    /**
     * This method gets the host and port of the domain server from the name server.
     * It also gets information about the subdomain handler object.
     * Note to those who would make changes in the protocol, keep the first three
     * ints the same. That way the server can reliably check for mismatched versions.
     *
     * @param channel nio socket communication channel
     * @param fromNameServerPort port of name server calling this method
     * @param isOriginator true if originating the connection between the 2 servers and
     *                     false if this is the response or reciprocal connection.
     * @param cloudPassword password for connecting to a server in a particular cloud
     * @throws cMsgException error returned from server
     * @throws IOException if there are communication problems with the name server
     */
    HashSet<String> talkToNameServerFromServer(SocketChannel channel,
                                               int cloudStatus,
                                               int fromNameServerPort,
                                               boolean isOriginator,
                                               String cloudPassword)
            throws IOException, cMsgException {
        byte[] buf = new byte[512];

        DataInputStream  in  = new DataInputStream(new BufferedInputStream(channel.socket().getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(channel.socket().getOutputStream()));

        out.writeInt(cMsgConstants.msgServerConnectRequest);
        out.writeInt(cMsgConstants.version);
        out.writeInt(cMsgConstants.minorVersion);
        // This client's listening port
        out.writeInt(port);
        // What relationship does this server have to the server cloud?
        // Can be INCLOUD, NONCLOUD, or BECOMINGCLOUD.
        out.writeByte(cloudStatus);
        // Is this client originating the connection or making a reciprocal one?
        out.writeByte(isOriginator ? 1 : 0);
        // This name server's listening port
        out.writeInt(fromNameServerPort);
        // Length of local host name
        out.writeInt(host.length());
        // Length of password
        if (cloudPassword == null) {
            cloudPassword = "";
        }
        System.out.println("length of cloud password = " + cloudPassword.length());
        out.writeInt(cloudPassword.length());

        // write strings & byte array
        try {
            out.write(host.getBytes("US-ASCII"));
            out.write(cloudPassword.getBytes("US-ASCII"));
        }
        catch (UnsupportedEncodingException e) {}

//System.out.println("        << CL: Write ints & host");
        out.flush(); // no need to be protected by socketLock

        // read acknowledgment
        int error = in.readInt();

        // if there's an error, read error string then quit
        if (error != cMsgConstants.ok) {

//System.out.println("        << CL: Read error");
            // read string length
            int len = in.readInt();
            if (len > buf.length) {
                buf = new byte[len+100];
            }

            // read error string
            in.readFully(buf, 0, len);
            String err = new String(buf, 0, len, "US-ASCII");
//System.out.println("        << CL: Error = " + err);

            cMsgException ex = new cMsgException("Error from server: " + err);
            ex.setReturnCode(error);
            throw ex;
        }

        // read port & length of host name
        domainServerPort = in.readInt();
        int hostLength   = in.readInt();

        // read host name
        if (hostLength > buf.length) {
            buf = new byte[hostLength];
        }
        in.readFully(buf, 0, hostLength);
        domainServerHost = new String(buf, 0, hostLength, "US-ASCII");

        if (debug >= cMsgConstants.debugInfo) {
            System.out.println("        << CL: domain server host = " + domainServerHost +
                               ", port = " + domainServerPort);
        }

        // return list of servers
        HashSet<String> s = null;

        // First, get the number of servers
        int numServers = in.readInt();

        // Second, for each server name, get string length then string
        if (numServers > 0) {
//System.out.println("        << CL: Try reading server names ...");
            s = new HashSet<String>(numServers);
            int serverNameLength;
            String serverName;

            for (int i = 0; i < numServers; i++) {
                serverNameLength = in.readInt();
                byte[] bytes = new byte[serverNameLength];
                in.readFully(bytes, 0, serverNameLength);
                serverName = new String(bytes, 0, serverNameLength, "US-ASCII");
//System.out.println("        << CL: Got server \"" + serverName + "\" from server");
                s.add(serverName);
                if (debug >= cMsgConstants.debugInfo) {
                    System.out.println("  server = " + serverName);
                }
            }
        }

        hasSend            = true;
        hasSyncSend        = true;
        hasSubscribeAndGet = true;
        hasSendAndGet      = true;
        hasSubscribe       = true;
        hasUnsubscribe     = true;
        hasShutdown        = true;

        return s;
    }


    /**
     * Lock the server (in cMsg subdomain) so that no other servers may
     * simultaneously join the cMsg subdomain server cloud or register a client.
     *
     * @param delay time in milliseconds to wait for locked to be grabbed before timing out
     * @return true if successful, else false
     * @throws IOException if there are communication problems with the name server
     */
    public boolean cloudLock(int delay) throws IOException {
        int response;
//System.out.println("        << CL: in cloudLock");
//System.out.println("        << CL: try nonConnect lock");
        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
//System.out.println("        << CL: try returnCommunication lock");
        // cannot run this simultaneously with commands receiving a response
        returnCommunicationLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

//System.out.println("        << CL: try socket lock");
            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
//System.out.println("        << CL: write size, msgServerCloudLock, delay");
                domainOut.writeInt(8);
                domainOut.writeInt(cMsgConstants.msgServerCloudLock);
//System.out.println("Sent msgServerCloudLock command (" + cMsgConstants.msgServerCloudLock + ")");
                domainOut.writeInt(delay);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock
//System.out.println("        << CL: try reading lock response");
            response = domainIn.readInt();
//System.out.println("        << CL: done reading lock response");
        }
        finally {
            returnCommunicationLock.unlock();
            notConnectLock.unlock();
        }

        if (response == 1) {
            return true;
        }
        return false;
    }


//-----------------------------------------------------------------------------

    /**
     * Unlock the server enabling other servers to join the
     * cMsg subdomain server cloud or register a client.
     *
     * @throws IOException if there are communication problems with the name server
     */
    public void cloudUnlock() throws IOException {
        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(4);
                domainOut.writeInt(cMsgConstants.msgServerCloudUnlock);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock
        }
        finally {
            notConnectLock.unlock();
        }
    }


//-----------------------------------------------------------------------------

    /**
     * Grab the registration lock (for adding a client)
     * of another cMsg domain server.
     *
     * @param delay time in milliseconds to wait for the lock before timing out
     * @return true if successful, else false
     * @throws IOException if there are communication problems with the name server
     */
    public boolean registrationLock(int delay) throws IOException {
        int response;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // cannot run this simultaneously with commands receiving a response
        returnCommunicationLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
//System.out.println("        << CL: try registration lock");
                domainOut.writeInt(8);
                domainOut.writeInt(cMsgConstants.msgServerRegistrationLock);
                domainOut.writeInt(delay);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock
            response = domainIn.readInt();
//System.out.println("        << CL: got registration lock = " + response);
        }
        finally {
            returnCommunicationLock.unlock();
            notConnectLock.unlock();
        }

        if (response == 1) {
            return true;
        }
        return false;
    }


//-----------------------------------------------------------------------------


    /**
     * Release the registration lock (when adding a client)
     * of another cMsg domain server.
     *
     * @throws IOException if communication error with server
     */
    public void registrationUnlock() throws IOException {
        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(4);
                domainOut.writeInt(cMsgConstants.msgServerRegistrationUnlock);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock
        }
        finally {
            notConnectLock.unlock();
        }
    }


//-----------------------------------------------------------------------------

    /**
     * This method tells the server this server is connected to,
     * what cloud status this server has.
     *
     * @param status cMsgNameServer.INCLOUD, .NONCLOUD, or .BECOMINGCLOUD
     * @throws IOException if communication error with server
     */
    public void thisServerCloudStatus(int status) throws IOException {
        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(8);
                domainOut.writeInt(cMsgConstants.msgServerCloudSetStatus);
                domainOut.writeInt(status);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock
        }
        finally {
            notConnectLock.unlock();
        }
    }

//-----------------------------------------------------------------------------


    /**
     * This method gets the names and namespaces of all the local clients (not servers)
     * of another cMsg domain server.
     *
     * @return array of client names and namespaces
     * @throws IOException if communication error with server
     */
    public String[] getClientNamesAndNamespaces() throws IOException {

        String[] names;

        // cannot run this simultaneously with connect or disconnect
        notConnectLock.lock();
        // cannot run this simultaneously with commands receiving a response
        returnCommunicationLock.lock();

        try {
            if (!connected) {
                throw new IOException("not connected to server");
            }

            socketLock.lock();
            try {
                // total length of msg (not including this int) is 1st item
                domainOut.writeInt(4);
                domainOut.writeInt(cMsgConstants.msgServerSendClientNames);
            }
            finally {
                socketLock.unlock();
            }

            domainOut.flush(); // no need to be protected by socketLock

            int offset = 0;
            int stringBytesToRead = 0;

            // read how many strings are coming
            int numberOfStrings = domainIn.readInt();

            int[] lengths = new int[numberOfStrings];
            names = new String[numberOfStrings];

            // read lengths of all names being sent
            for (int i=0; i < numberOfStrings; i++) {
                lengths[i] = domainIn.readInt();
                stringBytesToRead += lengths[i];
            }

            // read all string bytes
            byte[] bytes = new byte[stringBytesToRead];
            domainIn.readFully(bytes, 0, stringBytesToRead);

            // change bytes to strings
            for (int i=0; i < numberOfStrings; i++) {
                names[i] = new String(bytes, offset, lengths[i], "US-ASCII");
                offset += lengths[i];
            }
        }
        finally {
            returnCommunicationLock.unlock();
            notConnectLock.unlock();
        }

        return names;
    }



//-----------------------------------------------------------------------------
}
