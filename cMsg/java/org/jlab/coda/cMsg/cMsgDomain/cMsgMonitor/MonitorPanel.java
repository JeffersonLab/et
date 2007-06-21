/*----------------------------------------------------------------------------*
 *  Copyright (c) 2006        Jefferson Science Associates,                   *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    C. Timmer, 8-Dec-2006, Jefferson Lab                                    *
 *                                                                            *
 *     Author: Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.cMsg.cMsgDomain.cMsgMonitor;

import org.jlab.coda.cMsg.cMsg;
import org.jlab.coda.cMsg.cMsgException;
import org.jlab.coda.cMsg.cMsgMessage;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.border.EmptyBorder;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.tree.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * Class to put a cMsg domain monitor of a single cMsg system (cloud) into a single swing panel.
 */
public class MonitorPanel extends JPanel {

    /** UDL of connection to cMsg server. */
    private String udl;

    /** Tree of monitor data which gets displayed. */
    private JTree tree;
    /** Monitor data tree model. */
    private DefaultTreeModel treeModel;
    /** Top node of monitor data tree. */
    private DefaultMutableTreeNode topNode;

    /** Map of all servers in the cloud being monitored. */
    private HashMap<String,DefaultMutableTreeNode> serverMap = new HashMap<String,DefaultMutableTreeNode>(10);
    /** Map of all clients in the cloud being monitored. */
    private HashMap<String,DefaultMutableTreeNode> clientMap = new HashMap<String,DefaultMutableTreeNode>(20);
    /** Map of all subscriptions in the cloud being monitored. */
    private HashMap<String,DefaultMutableTreeNode> subMap    = new HashMap<String,DefaultMutableTreeNode>(50);

    /** cMsg connection. */
    private cMsg connection;

    /** Parsed XML document. */
    private Document document;
    /** Has the tree (parsed XML DOM tree) been updated at least once? */
    private boolean  updated;

    /** GUI Object which contains this panel. */
    private Monitor monitor;
    /** Widget for pausing the updating of this display. */
    private JButton pauseButton;
    /** Widget for changing the diplay updating period (seconds). */
    private JSpinner period;
    /** Is the updating of the display paused? */
    private boolean paused;
    /** Time period in seconds between display updates. */
    private int updatePeriod = 2;
    /** Thread which obtains and processes monitoring messages. */
    private MessageProcessor processor;
    /** Should we kill the processor thread? */
    private boolean killThread;


    /**
     * Constructor.
     * @param udl UDL to connect to
     * @param tabbedPane tabbedPane widget in which to display data of connection
     * @throws cMsgException if a connecton cannot be made to the UDL
     */
    public MonitorPanel(String udl, Monitor tabbedPane) throws cMsgException {

        this.udl = udl;
        this.monitor = tabbedPane;

        // make a connection to the cMsg server
        String uniqueName = "monitor_" + (new Date()).getTime();
        connection = new cMsg(udl, uniqueName, "monitor");
        connection.connect();

        // create static tree node
        topNode = new DefaultMutableTreeNode("Monitor Data");

        // create display of monitor data
        createDisplay();

        // start monitoring the cMsg server
        processor = new MonitorPanel.MessageProcessor();
        processor.start();
    }


    /** This class periodically gets a message of monitor data and updates the display. */
    class MessageProcessor extends Thread {

        /** Message with monitoring data. */
        private cMsgMessage msg;


        /** This method is executed as a thread. */
        public void run() {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            try {
                builder = factory.newDocumentBuilder();
            }
            catch (ParserConfigurationException e) {
                // Parser with specified options can't be built
                e.printStackTrace();
            }

            // keep track of when we updated last
            long lastUpdate=0, now, diff;

            while (true) {

                // get message with monitor data in it
                try {
                    if (this.isInterrupted() && killThread) {
//System.out.println("DATA TAKING THREAD INTERRUPTED TO KILL, A !!!");
                        return;
                    }

                    now = (new Date()).getTime();
                    diff = now - lastUpdate;

                    // only update if not paused and it's been
                    // at least 1 second since last time
                    if (!paused && diff > 1000)  {
                        // get another message with monitoring data
                        msg = connection.monitor(null);
                        lastUpdate = (new Date()).getTime();
//System.out.println("UPDATED!!!!");
                    }
                }
                catch (cMsgException e) {
                    e.printStackTrace();
                    close();
                    return;
                }

                try {
                    // analyze message
                    String xml = msg.getText();
//System.out.println("msg = \n" + xml);
                    byte[] buf = xml.getBytes("US-ASCII");
                    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                    document = builder.parse(bais);
                    SwingUtilities.invokeLater(updateDisplay);
                }
                catch (SAXException sxe) {
                    // Error generated during parsing
                    Exception x = sxe;
                    if (sxe.getException() != null)
                        x = sxe.getException();
                    x.printStackTrace();

                }
                catch (IOException ioe) {
                    // I/O error
                    ioe.printStackTrace();
                }

                // get message every N seconds
                try {Thread.sleep(updatePeriod*1000);}
                catch (InterruptedException e) {
                    if (killThread) {
//System.out.println("DATA TAKING THREAD INTERRUPTED TO KILL, B !!!");
                        return;
                    }
                }
            }
        }
    }


    /** Method to disconnect the cMsg connection and remove the display from the GUI. */
    private void close() {
        // cmsg disconnect
        try {connection.disconnect();}
        catch (cMsgException e1) {}

        // remove tabbedPane for this connection
        monitor.tabbedPane.remove(this);

        // remove udl from "monitors" hashtable
        monitor.monitors.remove(udl);

        // stop data taking
        killThread = true;
        processor.interrupt();
    }


    /** Method to create a display for this cMsg connection. */
    private void createDisplay() {

        // Create a tree
        treeModel = new DefaultTreeModel(topNode);
        tree = new JTree(treeModel);
        tree.setDoubleBuffered(true);
        tree.setLargeModel(true);
        tree.putClientProperty("JTree.lineStyle", "Angled");

        // Get rid of tree's leaf icon.
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(null);
        tree.setCellRenderer(renderer);

        // Build tree view
        JScrollPane treeView = new JScrollPane(tree);

        // Add GUI components
        setLayout(new BorderLayout());
        add(BorderLayout.CENTER, treeView);
        add(BorderLayout.SOUTH, createLowerPanel());
    }


    /**
     * Create a panel to display widgets controlling data-taking period, pausing, and closing.
     * @return the lower panel
     */
    private JPanel createLowerPanel() {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        EmptyBorder eb = new EmptyBorder(10, 10, 10, 10);
        BevelBorder bb = new BevelBorder(BevelBorder.LOWERED);
        CompoundBorder cb = new CompoundBorder(eb, bb);
        panel.setBorder(cb);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });

        pauseButton = new JButton("Pause");
        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (paused) {
                    paused = false;
                    pauseButton.setText("Pause");
                }
                else {
                    paused = true;
                    pauseButton.setText("Resume");
                }
            }
        });

        JPanel panel1 = new JPanel();
        JLabel label1 = new JLabel("Update period (sec) : ");
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 1, 100, 1);
        spinnerModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updatePeriod = (Integer)period.getValue();
                // don't want to get stuck forever at a period of 100 seconds!
                processor.interrupt();
            }
        });
        period = new JSpinner(spinnerModel);
        panel1.add(label1);
        panel1.add(period);

        panel.add(pauseButton);
        panel.add(panel1);
        panel.add(closeButton);

        return panel;
    }


    /** Enable displayDomTree to be run in the swing thread. */
    Runnable updateDisplay = new Runnable() {
        public void run() {
            displayDomTree();
        }
    };


    /** Method to update the display of monitor data. */
    private void displayDomTree() {
        int serverIndex=0, clientIndex, end=499, last=99;
        StringBuilder str = new StringBuilder(500);
        StringBuilder nameBuilder = new StringBuilder(100);

        DefaultMutableTreeNode jtreeServerNode, jtreeClientNode;
        DefaultMutableTreeNode jtreeCbNode, jtreeNode;
        String serverName, clientName, fullClientName, subName;
        Element el, e2;
        String id, received, cueSize, subdomain;

        NodeList serverList = document.getChildNodes().item(0).getChildNodes();
        NodeList clientList, cbList, domList;
        Node serverNode, clientNode, cbNode, domNode;

        // make a copy of the current server names
        Set<String> removeServerNames  = new HashSet<String>(serverMap.keySet());
        Set<String> currentServerNames = new HashSet<String>(10);

        Set<String> removeClientNames  = new HashSet<String>(clientMap.keySet());
        Set<String> currentClientNames = new HashSet<String>(20);

        Set<String> removeSubNames  = new HashSet<String>(subMap.keySet());
        Set<String> currentSubNames = new HashSet<String>(50);

        // iterate through servers
        for (int i = 0; i < serverList.getLength(); i++) {

            serverNode = serverList.item(i);

            // pick out Element type in xml doc (server is only element at this level of dom tree)
            if (!(serverNode.getNodeType() == 1)) {
//System.out.println("Skipping (i=" + i + ") server node = " + serverNode.getNodeName());
                continue;
            }

            // server node is of class "Element" so cast to play with attributes
            el = (Element) serverNode;
            serverName = el.getAttribute("name");

//System.out.println("node name = " + serverNode.getNodeName() + ", name attribute = " + serverName);

            str.append("server = ");
            str.append(serverName);
//System.out.println("Looking in map for server = " + serverName + ", is " + serverMap.containsKey(serverName));

            if (serverMap.containsKey(serverName)) {
//System.out.println("Exists already in map");
                jtreeServerNode = serverMap.get(serverName);
                jtreeServerNode.setUserObject(str.toString());
                treeModel.nodeChanged(jtreeServerNode);
            }
            else {
                jtreeServerNode = new DefaultMutableTreeNode(str.toString());
//System.out.println("Creating a new one: put(" + serverName + ", " + jtreeServerNode + ")");
                treeModel.insertNodeInto(jtreeServerNode, topNode, serverIndex);
                serverMap.put(serverName, jtreeServerNode);
            }
            currentServerNames.add(serverName);

            str.delete(0, end);
            serverIndex++;
            clientIndex = 0;

            // add children (clients) to the JTree server node
            if (serverNode.hasChildNodes()) {

                clientList = serverNode.getChildNodes();

//System.out.println("Server has " + clientList.getLength() + " children");
                for (int j = 0; j < clientList.getLength(); j++) {

                    clientNode = clientList.item(j);
                    if (!(clientNode.getNodeType() == 1)) {
//System.out.println("skip over (j=" + j + "), " + clientNode.getNodeName());
                        continue;
                    }

                    // client node is of class "Element" so cast to play with attributes
                    el = (Element) clientNode;
                    clientName = el.getAttribute("name");
                    subdomain = el.getAttribute("subdomain");

                    str.append("client = ");
                    str.append(clientName);
                    fullClientName = clientName + ":" + serverName;
//System.out.println("NOT skipping over (j=" + j + "), " + clientName);
 
                    boolean clientJustCreated;

                    if (clientMap.containsKey(fullClientName)) {
//System.out.println("Client " + fullClientName + " exists already in map");
                        jtreeClientNode = clientMap.get(fullClientName);
                        jtreeClientNode.setUserObject(str.toString());
                        treeModel.nodeChanged(jtreeClientNode);
                        clientJustCreated = false;
                    }
                    else {
                        jtreeClientNode = new DefaultMutableTreeNode(str.toString());
//System.out.println("Creating a new client: put(" + fullClientName + ", " + jtreeClientNode + ")");
                        treeModel.insertNodeInto(jtreeClientNode, jtreeServerNode, clientIndex);
                        clientMap.put(fullClientName, jtreeClientNode);
                        clientJustCreated = true;
                    }
                    currentClientNames.add(fullClientName);

                    str.delete(0, end);
                    clientIndex++;

                    // add subdomain, namespace, time, stats, and subscriptions to client node
                    if (clientNode.hasChildNodes()) {

                        int index = 0;
//System.out.println("Client " + clientName  + " has " + clientNode.getChildNodes().getLength() + " children");

                        // find "namespace" element
                        domList = el.getElementsByTagName("namespace");
                        domNode = domList.item(0);
                        str.append("subdomain = ");
                        str.append(subdomain);
                        str.append(",  namespace = ");
                        str.append(domNode.getTextContent());

                        if (clientJustCreated) {
                            jtreeNode = new DefaultMutableTreeNode(str.toString());
                            treeModel.insertNodeInto(jtreeNode, jtreeClientNode, index);
                        }
                        else {
                            jtreeNode = (DefaultMutableTreeNode) jtreeClientNode.getChildAt(index);
                            jtreeNode.setUserObject(str.toString());
                        }

                        str.delete(0, end);
                        index++;

                        // find "timeConnected" element
                        domList = el.getElementsByTagName("timeConnected");
                        domNode = domList.item(0);
                        str.append("time connected = ");
                        str.append(domNode.getTextContent());


                        if (clientJustCreated) {
                            jtreeNode = new DefaultMutableTreeNode(str.toString());
                            treeModel.insertNodeInto(jtreeNode, jtreeClientNode, index);
                        }
                        else {
                            jtreeNode = (DefaultMutableTreeNode) jtreeClientNode.getChildAt(index);
                            jtreeNode.setUserObject(str.toString());
                        }

                        str.delete(0, end);
                        index++;

                        // find "sendStats" element
                        domList = el.getElementsByTagName("sendStats");
                        e2 = (Element) domList.item(0);
                        str.append("send stats : tcpSends= ");
                        str.append(e2.getAttribute("tcpSends"));
                        str.append(",  udpSends= ");
                        str.append(e2.getAttribute("udpSends"));
                        str.append(",  syncSends= ");
                        str.append(e2.getAttribute("syncSends"));
                        str.append(",  send&Gets= ");
                        str.append(e2.getAttribute("sendAndGets"));


                        if (clientJustCreated) {
                            jtreeNode = new DefaultMutableTreeNode(str.toString());
                            treeModel.insertNodeInto(jtreeNode, jtreeClientNode, index);
                        }
                        else {
                            jtreeNode = (DefaultMutableTreeNode) jtreeClientNode.getChildAt(index);
                            jtreeNode.setUserObject(str.toString());
                        }

                        str.delete(0, end);
                        index++;

                        // find "subStats" element
                        domList = el.getElementsByTagName("subStats");
                        e2 = (Element) domList.item(0);
                        str.append("sub stats : subscribes= ");
                        str.append(e2.getAttribute("subscribes"));
                        str.append(",  unsubscribes= ");
                        str.append(e2.getAttribute("unsubscribes"));
                        str.append(",  sub&Gets= ");
                        str.append(e2.getAttribute("subAndGets"));


                        if (clientJustCreated) {
                            jtreeNode = new DefaultMutableTreeNode(str.toString());
                            treeModel.insertNodeInto(jtreeNode, jtreeClientNode, index);
                        }
                        else {
                            jtreeNode = (DefaultMutableTreeNode) jtreeClientNode.getChildAt(index);
                            jtreeNode.setUserObject(str.toString());
                        }

                        str.delete(0, end);
                        index++;

                        // go thru all subscriptions
                        domList = el.getElementsByTagName("subscription");
                        if (domList.getLength() < 1) {
                            // go on to next client ...
                            continue;
                        }

                        for (int n=0; n < domList.getLength(); n++) {

                            domNode = domList.item(n);
                            e2 = (Element) domNode;
                            str.append("subscription :  subject= ");
                            str.append(e2.getAttribute("subject"));
                            str.append(",  type= ");
                            str.append(e2.getAttribute("type"));

                            // Make the subscription handle as <subject>:<fullClientName>:<type> .
                            // This breaks degenerate pairs of subject and type such as:
                            // sub=aX, type=b   &   sub=a, type=Xb .
                            nameBuilder.append(e2.getAttribute("subject"));
                            nameBuilder.append(":" );
                            nameBuilder.append(fullClientName);
                            nameBuilder.append(":");
                            nameBuilder.append(e2.getAttribute("type"));

                            subName = nameBuilder.toString();

                            if (subMap.containsKey(subName)) {
//System.out.println("sub " + nameBuilder.toString() + " exists already in map");
                                jtreeNode = subMap.get(subName);
                                jtreeNode.setUserObject(str.toString());
                                treeModel.nodeChanged(jtreeNode);
                            }
                            else {
                                jtreeNode = new DefaultMutableTreeNode(str.toString());
//System.out.println("Creating a new sub: put(" + nameBuilder.toString() + ", " + jtreeNode + ")");
                                treeModel.insertNodeInto(jtreeNode, jtreeClientNode, index);
                                subMap.put(nameBuilder.toString(), jtreeNode);
                            }
                            currentSubNames.add(subName);

                            nameBuilder.delete(0,last);
                            str.delete(0, end);
                            index++;

                            // callbacks
                            if (domNode.hasChildNodes()) {

                                cbList = domNode.getChildNodes();
                                int index2 = 0;

                                for (int m = 0; m < cbList.getLength(); m++) {

                                    cbNode = cbList.item(m);
                                    if (!(cbNode.getNodeType() == 1)) {
                                        continue;
                                    }

                                    // callback node is of class "Element"
                                    e2 = (Element) cbNode;
                                    id = e2.getAttribute("id");
                                    received = e2.getAttribute("received");
                                    cueSize = e2.getAttribute("cueSize");

                                    str.append("callback : id= ");
                                    str.append(id);
                                    str.append(",  received= ");
                                    str.append(received);
                                    str.append(", cueSize= ");
                                    str.append(cueSize);

                                    if (updated) {
                                        try {
                                            jtreeCbNode = (DefaultMutableTreeNode) jtreeNode.getChildAt(index2);
                                            jtreeCbNode.setUserObject(str.toString());
                                        }
                                        catch (Exception e) {
                                            // no more kids, ignore exception, add new node
                                            jtreeCbNode = new DefaultMutableTreeNode(str.toString());
                                            treeModel.insertNodeInto(jtreeCbNode, jtreeNode, index2);
                                        }
                                    }
                                    else {
                                        jtreeCbNode = new DefaultMutableTreeNode(str.toString());
                                        treeModel.insertNodeInto(jtreeCbNode, jtreeNode, index2);
                                    }

                                    str.delete(0, end);
                                    index2++;

                                } // next callback

                            } //  if callbacks

                        } // next subscription

                    } // if client has kids

                } // next client

            } // if server has children

        } // next server


        // if extra server nodes, get rid of excess
        removeServerNames.removeAll(currentServerNames);
        for (String nam : removeServerNames) {
//System.out.println("Removing server = "+ nam);
            treeModel.removeNodeFromParent(serverMap.get(nam));
            serverMap.remove(nam);
        }

        // if extra client nodes, get rid of excess
        removeClientNames.removeAll(currentClientNames);
        for (String nam : removeClientNames) {
//System.out.println("Removing client = "+ nam);
            treeModel.removeNodeFromParent(clientMap.get(nam));
            clientMap.remove(nam);
        }

        // if extra subscription nodes, get rid of excess
        removeSubNames.removeAll(currentSubNames);
        for (String nam : removeSubNames) {
//System.out.println("Removing sub = "+ nam);
            treeModel.removeNodeFromParent(subMap.get(nam));
            subMap.remove(nam);
        }

        if (!updated) {
            tree.expandPath(new TreePath(topNode));
        }

        treeModel.nodeChanged(topNode);

        updated = true;
    }



}
