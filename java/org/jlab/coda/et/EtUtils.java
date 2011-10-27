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

import java.nio.ByteOrder;

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
     * @throws org.jlab.coda.jevio.EvioException if dest is null or too small or offset negative
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




}
