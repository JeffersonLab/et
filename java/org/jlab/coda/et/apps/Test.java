package org.jlab.coda.et.apps;

import org.jlab.coda.et.system.SystemConfig;
import org.jlab.coda.et.system.SystemCreate;
import org.jlab.coda.et.Constants;

import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: timmer
 * Date: Apr 15, 2010
 * Time: 9:49:32 AM
 * To change this template use File | Settings | File Templates.
 */
public class Test {

        /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n" +
                "   java StartEt [-n <# of events>]\n" +
                "                [-s <size of events (bytes)>]\n" +
                "                [-p <server port>]\n" +
                "                [-debug]\n" +
                "                [-h]\n" +
                "                -f <file name>\n");
    }


    public Test() { }

    public static void main(String[] args) {
        int numEvents = 3000, size = 128, serverPort = 11111;
        boolean debug = false;
        String file = null;

        // loop over all args
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-h")) {
                usage();
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                numEvents = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-f")) {
                file = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-p")) {
                serverPort = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-s")) {
                size = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

//        if (file == null) {
//            usage();
//            System.exit(-1);
//        }

        try {
            // create array of ASCII bytes representing  Carl\0Arthur\0Timmer\0 + padding
            byte[] s = {67,97,114,108,0,65,114,116,104,117,114,0,84,105,109,109,101,114,0,0,0,11,10,4,4};
            //byte[] s = {0,67,96,4,0};
           // StringBuffer stringData = new StringBuffer(new String(s, "US-ASCII"));
            StringBuffer stringData = null;
            int stringEnd = 0;


            String[] strings = {"first", "second", "third"};

            for (int i=0; i < 3; i++) {
                // add string
                if (stringData == null) {
                    System.out.println("Start with : null string");
                    stringData = new StringBuffer(strings[i].length() + 1);
                }
                // else remove any existing padding
                else {
                    stringData.delete(stringEnd, stringData.length());
                    System.out.println("Start with : stringData = " + stringData.toString());
                }

                // add string
                stringData.append(strings[i]);

                // add ending null
                stringData.append('\000');

                // mark end of data before adding padding
                stringEnd = stringData.length();

                // add any necessary padding to 4 byte boundaries
                int[] pads = {0,3,2,1};
                switch (pads[stringData.length()%4]) {
                    case 3:
                        stringData.append("\004\004\004"); break;
                    case 2:
                        stringData.append("\004\004"); break;
                    case 1:
                        stringData.append('\004');
                    default:
                }
                System.out.println("End with : stringData = " + stringData.toString());
            }


//            char c;
//            LinkedList<Integer> indexList = new LinkedList<Integer>();
//
//            for (int i=0; i < stringData.length(); i++) {
//                c = stringData.charAt(i);
//                if ( c == '\000' ) {
//                    indexList.add(i);
//                }
//                else if ( Character.isISOControl(c) ) {
//                    break;
//                }
//            }
//
//            String[] strs = new String[indexList.size()];
//            int lastIndex, firstIndex=0;
//            for (int i=0; i < indexList.size(); i++) {
//                lastIndex  = indexList.get(i);
//                strs[i]    = stringData.substring(firstIndex, lastIndex);
//                firstIndex = lastIndex + 1;
//            }
//
//
//            System.out.println("size of strs = " + strs.length);
//
//
//
//            //String[] strs = stringData.toString().split("[\\000\\004]");
//
////            String str = new String(s, "US-ASCII");
////            System.out.println("print out string =\n" + str);
////            String[] strs = str.split("[\\000\\004]");
//            for (String ss : strs) {
//                System.out.println("split = " + ss);
//                if (ss.indexOf('\004') > -1) {
//                    System.out.println("string " + ss + " has illegal 4 char in it");
//                }
//            }
//            System.out.println("END");
//
//
//           // System.out.println("Map ET system file");
//           // SystemCreate sys = new SystemCreate(file);
        }
        catch (Exception ex) {
            System.out.println("Error testing ET system");
            ex.printStackTrace();
        }

    }

}
