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

import org.jlab.coda.cMsg.cMsgException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.util.*;

/**
 * This class implements a cMsg domain monitor.
 *
 * @author Carl Timmer
 */
public class Monitor extends JPanel {

    /** TabbedPane widget which shows a cMsg connection in each pane. */
    JTabbedPane tabbedPane;

    /** ComboBox widget that holds the udl to connect to. */
    private JComboBox comboBox;

    /** Map to keep track of connections to & monitors of cMsg systems. */
    final Map<String, MonitorPanel> monitors =
            Collections.synchronizedMap(new HashMap<String, MonitorPanel>(20));


    public Monitor() {
        super();

        BorderLayout layout = new BorderLayout();
        this.setLayout(layout);

        Dimension frameSize = new Dimension(600, 800);

        // tabbedPane stuff
        //UIManager.put("TabbedPane.selected", selectedTabColor);
        tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(new EmptyBorder(10,10,10,10));
        tabbedPane.setPreferredSize(frameSize);
        add(tabbedPane, BorderLayout.CENTER);

        JPanel p = makeTopPanel();
        add(p, BorderLayout.NORTH);
        
        // Add to help screen to main window's tabbed pane
        tabbedPane.addTab("Help", null, makeHelpPane(), "help");
     }



    public static void main(String[] args) {

        JFrame frame = new JFrame("cMsg Monitor");
        Monitor mainPanel;

        mainPanel = new Monitor();
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        frame.pack();
        frame.setVisible(true);
    }


   /**
    * Method to create the panel at the top of the GUI which allows the user to
    * specify a UDL and connect to it.
    */
    private JPanel makeTopPanel() {

        // combo box uses this to filter input
        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox jcb = (JComboBox) e.getSource();
                String listItem;
                String selectedItem = (String) jcb.getSelectedItem();
                int numItems = jcb.getItemCount();
                boolean addNewItem = true;

                if (selectedItem == null || selectedItem.equals("")) {
                    addNewItem = false;
                }
                else if (numItems == 0) {
                    addNewItem = true;
                }
                else {
                    for (int i = 0; i < numItems; i++) {
                        listItem = (String) jcb.getItemAt(i);
                        if (listItem.equals(selectedItem)) {
                            addNewItem = false;
                            break;
                        }
                    }
                }

                if (addNewItem) {
                    jcb.addItem(selectedItem);
                }
            }
        };

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        EmptyBorder eb = new EmptyBorder(10, 10, 10, 10);
        BevelBorder bb = new BevelBorder(BevelBorder.LOWERED);
        CompoundBorder cb = new CompoundBorder(eb, bb);
        panel.setBorder(cb);

        JButton monitorButton = new JButton("Monitor");
        monitorButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                String udl = (String) comboBox.getSelectedItem();
                // check to see if this connection exists already
                if (monitors.containsKey(udl)) {
                    return;
                }
                MonitorPanel p;
                try { p = new MonitorPanel(udl, Monitor.this); }
                catch (cMsgException e1) {
System.out.println("CANNOT connect to " + udl);
                    JOptionPane.showMessageDialog(new JFrame(),
                                                  "Cannot connect to " + udl,
                                                  "Error",
                                                  JOptionPane.ERROR_MESSAGE);
                    //e1.printStackTrace();
                    return;
                }
                monitors.put(udl, p);
                tabbedPane.addTab(udl, null, p, udl);                
            }
        });

        JLabel label = new JLabel("  UDL : ");

        comboBox = new JComboBox();
        comboBox.setEditable(true);
        comboBox.addActionListener(al);
        comboBox.addItem("cMsg://aslan:3456/cMsg/test");

        // button for UDL removal
        final JButton removeUDL = new JButton("X");
        removeUDL.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = comboBox.getSelectedIndex();
                if (index > 0) {
                    comboBox.removeItemAt(index);
                }
            }
        });
        removeUDL.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(monitorButton);
        panel.add(label);
        panel.add(comboBox);
        panel.add(removeUDL);

        return panel;
    }


   /** Method to create an initial help window in the tabbedpane widget. */
    private JScrollPane makeHelpPane() {

       // Put this into the tabbedPane.
        JTextArea text = new JTextArea(10, 200);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setTabSize(3);
        text.setEditable(false);
        text.setBorder(new EmptyBorder(20, 20, 20, 20));
        JScrollPane pane = new JScrollPane(text);

        // Put stuff into the text area.
        text.append(
                "CONNECTING TO AN CMSG SERVER\n" +
                        "Type in the UDL or Universial Domain Locator in the text area above. " +
                        "Then hit the \"Monitor\" button to attempt a connection to the cMsg"  +
                        "server. If the attempt was successful, a new tabbed pane will appear " +
                        "with monitoring information about not only that server but the entire " +
                        "cloud of servers that server may be a part of. The data should be updated " +
                        "every 2 seconds."
        );
        return pane;
    }


}
