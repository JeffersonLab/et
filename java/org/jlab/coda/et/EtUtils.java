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
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et;

import org.jlab.coda.et.exception.EtException;

import java.net.*;
import java.nio.ByteOrder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collection of methods to help manipulate bytes in arrays.
 * @author timmer
 */
public class EtUtils {

    /**
     * Turn short into byte array.
     * Avoids creation of new byte array with each call.
     *
     * @param data short to convert
     * @param byteOrder byte order of returned bytes (big endian if null)
     * @param dest array in which to store returned bytes
     * @param off offset into dest array where returned bytes are placed
     */
    public static void shortToBytes(short data, ByteOrder byteOrder, byte[] dest, int off) {

        if (byteOrder == null || byteOrder == ByteOrder.BIG_ENDIAN) {
            dest[off  ] = (byte)(data >>> 8);
            dest[off+1] = (byte)(data      );
        }
        else {
            dest[off  ] = (byte)(data      );
            dest[off+1] = (byte)(data >>> 8);
        }
    }


    /**
      * Turn int into byte array.
      * Avoids creation of new byte array with each call.
      *
      * @param data int to convert
      * @param byteOrder byte order of returned bytes (big endian if null)
      * @param dest array in which to store returned bytes
      * @param off offset into dest array where returned bytes are placed
      */
     public static void intToBytes(int data, ByteOrder byteOrder, byte[] dest, int off) {

         if (byteOrder == null || byteOrder == ByteOrder.BIG_ENDIAN) {
             dest[off  ] = (byte)(data >> 24);
             dest[off+1] = (byte)(data >> 16);
             dest[off+2] = (byte)(data >>  8);
             dest[off+3] = (byte)(data      );
         }
         else {
             dest[off  ] = (byte)(data      );
             dest[off+1] = (byte)(data >>  8);
             dest[off+2] = (byte)(data >> 16);
             dest[off+3] = (byte)(data >> 24);
         }
     }

     /**
      * Turn long into byte array.
      * Avoids creation of new byte array with each call.
      *
      * @param data long to convert
      * @param byteOrder byte order of returned bytes (big endian if null)
      * @param dest array in which to store returned bytes
      * @param off offset into dest array where returned bytes are placed
      */
     public static void longToBytes(long data, ByteOrder byteOrder, byte[] dest, int off) {

         if (byteOrder == null || byteOrder == ByteOrder.BIG_ENDIAN) {
             dest[off  ] = (byte)(data >> 56);
             dest[off+1] = (byte)(data >> 48);
             dest[off+2] = (byte)(data >> 40);
             dest[off+3] = (byte)(data >> 32);
             dest[off+4] = (byte)(data >> 24);
             dest[off+5] = (byte)(data >> 16);
             dest[off+6] = (byte)(data >>  8);
             dest[off+7] = (byte)(data      );
         }
         else {
             dest[off  ] = (byte)(data      );
             dest[off+1] = (byte)(data >>  8);
             dest[off+2] = (byte)(data >> 16);
             dest[off+3] = (byte)(data >> 24);
             dest[off+4] = (byte)(data >> 32);
             dest[off+5] = (byte)(data >> 40);
             dest[off+6] = (byte)(data >> 48);
             dest[off+7] = (byte)(data >> 56);
         }
     }

    /**
     * Copies a short value into 2 bytes of a byte array.
     * @param shortVal short value
     * @param b byte array
     * @param off offset into the byte array
     */
    public static void shortToBytes(short shortVal, byte[] b, int off) {
        shortToBytes(shortVal, null, b, off);
    }

    /**
     * Copies an integer value into 4 bytes of a byte array.
     * @param intVal integer value
     * @param b byte array
     * @param off offset into the byte array
     */
    public static void intToBytes(int intVal, byte[] b, int off) {
        intToBytes(intVal, null, b, off);
    }

    /**
     * Copies an long (64 bit) value into 8 bytes of a byte array.
     * @param longVal long value
     * @param b byte array
     * @param off offset into the byte array
     */
    public static void longToBytes(long longVal, byte[] b, int off) {
        longToBytes(longVal, null, b, off);
    }


    /**
     * Turn section of byte array into a short.
     *
     * @param data byte array to convert
     * @param byteOrder byte order of supplied bytes (big endian if null)
     * @param off offset into data array
     * @return short converted from byte array
     */
    public static short bytesToShort(byte[] data, ByteOrder byteOrder, int off) {

        if (byteOrder == null || byteOrder == ByteOrder.BIG_ENDIAN) {
            return (short)(
                (0xff & data[  off]) << 8 |
                (0xff & data[1+off])
            );
        }
        else {
            return (short)(
                (0xff & data[  off]) |
                (0xff & data[1+off] << 8)
            );
        }
    }

    /**
     * Turn section of byte array into an int.
     *
     * @param data byte array to convert
     * @param byteOrder byte order of supplied bytes (big endian if null)
     * @param off offset into data array
     * @return int converted from byte array
     */
    public static int bytesToInt(byte[] data, ByteOrder byteOrder, int off) {

        if (byteOrder == null || byteOrder == ByteOrder.BIG_ENDIAN) {
            return (
                (0xff & data[  off]) << 24 |
                (0xff & data[1+off]) << 16 |
                (0xff & data[2+off]) <<  8 |
                (0xff & data[3+off])
            );
        }
        else {
            return (
                (0xff & data[  off])       |
                (0xff & data[1+off]) <<  8 |
                (0xff & data[2+off]) << 16 |
                (0xff & data[3+off]) << 24
            );
        }
    }

    /**
     * Turn section of byte array into a long.
     *
     * @param data byte array to convert
     * @param byteOrder byte order of supplied bytes (big endian if null)
     * @param off offset into data array
     * @return long converted from byte array
     */
    public static long bytesToLong(byte[] data, ByteOrder byteOrder, int off) {

        if (byteOrder == null || byteOrder == ByteOrder.BIG_ENDIAN) {
            return (
                // Convert to longs before shift because digits
                // are lost with ints beyond the 32-bit limit
                (long)(0xff & data[  off]) << 56 |
                (long)(0xff & data[1+off]) << 48 |
                (long)(0xff & data[2+off]) << 40 |
                (long)(0xff & data[3+off]) << 32 |
                (long)(0xff & data[4+off]) << 24 |
                (long)(0xff & data[5+off]) << 16 |
                (long)(0xff & data[6+off]) <<  8 |
                (long)(0xff & data[7+off])
            );
        }
        else {
            return (
                (long)(0xff & data[  off])       |
                (long)(0xff & data[1+off]) <<  8 |
                (long)(0xff & data[2+off]) << 16 |
                (long)(0xff & data[3+off]) << 24 |
                (long)(0xff & data[4+off]) << 32 |
                (long)(0xff & data[5+off]) << 40 |
                (long)(0xff & data[6+off]) << 48 |
                (long)(0xff & data[7+off]) << 56
            );
        }
    }

    /**
     * Converts 2 bytes of a byte array into a short.
     * @param b byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return short value
     */
    public static short bytesToShort(byte[] b, int off) {
        return bytesToShort(b, null, off);
    }

    /**
     * Converts 4 bytes of a byte array into an integer.
     *
     * @param b   byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return integer value
     */
    public static int bytesToInt(byte[] b, int off) {
        return bytesToInt(b, null, off);
    }

    /**
     * Converts 8 bytes of a byte array into a long.
     * @param b byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return long value
     */
    public static long bytesToLong(byte[] b, int off) {
        return bytesToLong(b, null, off);
    }

    /**
     * Swaps 4 bytes of a byte array in place.
     * @param b byte array
     * @param off offset into the byte array
     */
    public static void swapArrayInt(byte[] b, int off) {
        byte b1, b2, b3, b4;
        b1 = b[off];
        b2 = b[off+1];
        b3 = b[off+2];
        b4 = b[off+3];
        b[off+3] = b1;
        b[off+2] = b2;
        b[off+1] = b3;
        b[off]   = b4;
    }

    /**
     * Swaps 2 bytes of a byte array in place.
     * @param b byte array
     * @param off offset into the byte array
     */
    public static void swapArrayShort(byte[] b, int off) {
        byte b1, b2;
        b1 = b[off];
        b2 = b[off+1];
        b[off+1] = b1;
        b[off]   = b2;
    }

    /**
     * Get all local IP broadcast addresses in a list in dotted-decimal form.
     * This only makes sense for IPv4.
     * @return list of all local IP broadcast addresses in dotted-decimal form.
     */
    public static List<String> getAllBroadcastAddresses() {

        // List of our IP addresses
        LinkedList<String> ipList = new LinkedList<String>();

        try {
            Enumeration<NetworkInterface> enumer = NetworkInterface.getNetworkInterfaces();
            while (enumer.hasMoreElements()) {
                NetworkInterface ni = enumer.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    List<InterfaceAddress> inAddrs = ni.getInterfaceAddresses();
                    for (InterfaceAddress ifAddr : inAddrs) {
                        Inet4Address bAddr;
                        try { bAddr = (Inet4Address)ifAddr.getBroadcast(); }
                        catch (ClassCastException e) {
                            // probably IPv6 so ignore
                            continue;
                        }

                        String broadcastIP = bAddr.getHostAddress();
                        ipList.add(broadcastIP);
                    }
                }
            }
        }
        catch (SocketException e) {
            e.printStackTrace();
        }

        // Try this if nothing else works
        if (ipList.size() < 1) {
            ipList.add("255.255.255.255");
        }

        return ipList;
    }

    /**
     * Takes a list of dotted-decimal formatted IP address strings and orders them
     * so that those on local subnets are first and others come last.
     * This only works for IPv4.
     * @return ordered list of given IP addresses in dotted-decimal form with those
     *         on local subnets listed first.
     */
    public static List<String> orderIPAddresses(List<String> ipAddresses) {

        // Eliminate duplicates in arg
        HashSet<String> ipSet = new HashSet<String>(ipAddresses);

        // List of our local IP info - need local subnet masks
        LinkedList<String> ipList = new LinkedList<String>();

        // iterate through argument list of addresses
        outerLoop:
        for (String ip : ipSet) {
            boolean ipAddressAdded = false;

            try {
                // iterate through local list of addresses
                Enumeration<NetworkInterface> enumer = NetworkInterface.getNetworkInterfaces();
                while (enumer.hasMoreElements()) {
                    NetworkInterface ni = enumer.nextElement();
                    if (!ni.isUp() || ni.isLoopback()) {
                        continue;
                    }
                    List<InterfaceAddress> inAddrs = ni.getInterfaceAddresses();

                    for (InterfaceAddress ifAddr : inAddrs) {
                        Inet4Address addrv4;
                        try { addrv4 = (Inet4Address)ifAddr.getAddress(); }
                        catch (ClassCastException e) {
                            // probably IPv6 so ignore
                            continue;
                        }

                        // Turn net prefix len into integer subnet mask
                        short prefixLength = ifAddr.getNetworkPrefixLength();
                        int subnetMask = 0;
                        for (int j = 0; j < prefixLength; ++j) {
                            subnetMask |= (1 << 31-j);
                        }

                        String localIP = addrv4.getHostAddress();
                        try {
                            // If local and arg addresses are on the same subnet,
                            // we gotta match to put at the head of the list.
                            if (onSameSubnet2(ip, localIP, subnetMask)) {
                                ipList.addFirst(ip);
                                ipAddressAdded = true;
//System.out.println("Add " + ip + " to list top");
                                continue outerLoop;
                            }
                        }
                        // try the next address
                        catch (EtException e) {continue;}
                    }
                }
            }
            catch (SocketException e) {
                continue;
            }

            // This arg address is not on any of the local subnets,
            // so put it at the end of the list.
            if (!ipAddressAdded) {
//System.out.println("Add " + ip + " to list bottom");
                ipList.addLast(ip);
            }
        }

        return ipList;
    }

    /**
     * This method tells whether the 2 given IP addresses in dot-decimal notation
     * are on the same subnet or not given a subnet mask in integer form
     * (local byte order). This only works for IPv4.
     *
     * @param ipAddress1 first  IP address in dot-decimal notation
     * @param ipAddress2 second IP address in dot-decimal notation
     * @param subnetMask subnet mask as LOCAL-byte-ordered 32 bit int
     *
     * @returns {@code true} if on same subnet, else {@code false}.
     */
    static boolean onSameSubnet2(String ipAddress1, String ipAddress2, int subnetMask)
                                    throws EtException
    {
        if (ipAddress1 == null || ipAddress2 == null) {
            throw new EtException("null arg(s)");
        }

        byte[] ipBytes1 = isDottedDecimal(ipAddress1);
        byte[] ipBytes2 = isDottedDecimal(ipAddress2);

        if (ipBytes1 == null || ipBytes2 == null)  {
            throw new EtException("one or both IP address args are not dot-decimal format");
        }

        int addr1 = (ipBytes1[0] << 24) | (ipBytes1[1] << 16) | (ipBytes1[2] << 8) | ipBytes1[3];
        int addr2 = (ipBytes2[0] << 24) | (ipBytes2[1] << 16) | (ipBytes2[2] << 8) | ipBytes2[3];

        if ((addr1 & subnetMask) == (addr2 & subnetMask)) {
            return true;
        }

        return false;
    }

    /**
     * This method tells whether the given IPv4 address is in dot-decimal notation or not.
     * If it is, then the returned bytes are the address in numeric form, else null
     * is returned. This only works for IPv4.
     *
     * @param ipAddress IPV4 address
     * @returns IPV4 address in numeric form if arg is valid dot-decimal address, else null
     */
    static byte[] isDottedDecimal(String ipAddress)
    {
        if (ipAddress == null) return null;

        String IP_ADDRESS = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})";
        Pattern pattern = Pattern.compile(IP_ADDRESS);
        Matcher matcher = pattern.matcher(ipAddress);
        if (!matcher.matches()) return null;

        int[] hostInts = new int[4];
        for (int i = 1; i <= 4; ++i) {
//System.out.println("   group(" + i + ") = " + matcher.group(i));
            hostInts[i-1] = Integer.parseInt(matcher.group(i));
            if (hostInts[i-1] > 255 || hostInts[i-1] < 0) return null;
        }

        // Change ints to bytes
        byte[] hostBytes = new byte[4];
        hostBytes[0] = (byte) hostInts[0];
        hostBytes[1] = (byte) hostInts[1];
        hostBytes[2] = (byte) hostInts[2];
        hostBytes[3] = (byte) hostInts[3];

        return hostBytes;
     }

}
