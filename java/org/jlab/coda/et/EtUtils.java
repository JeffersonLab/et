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

/**
 * Collection of methods to help manipulate bytes in arrays.
 * @author timmer
 */
public class EtUtils {
    /**
     * Copies a short value into 2 bytes of a byte array.
     * @param shortVal short value
     * @param b byte array
     * @param off offset into the byte array
     */
    public static final void shortToBytes(short shortVal, byte[] b, int off) {
        b[off]   = (byte) ((shortVal & 0xff00) >>> 8);
        b[off+1] = (byte)  (shortVal & 0x00ff);
    }

    /**
     * Converts 4 bytes of a byte array into an integer.
     *
     * @param b   byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return integer value
     */
    public static final int bytesToInt(byte[] b, int off) {
        return ((b[off]     & 0xff) << 24 |
                (b[off + 1] & 0xff) << 16 |
                (b[off + 2] & 0xff) <<  8 |
                 b[off + 3] & 0xff);
    }

    /**
     * Copies an integer value into 4 bytes of a byte array.
     * @param intVal integer value
     * @param b byte array
     * @param off offset into the byte array
     */
    public static final void intToBytes(int intVal, byte[] b, int off) {
      b[off]   = (byte) ((intVal & 0xff000000) >>> 24);
      b[off+1] = (byte) ((intVal & 0x00ff0000) >>> 16);
      b[off+2] = (byte) ((intVal & 0x0000ff00) >>>  8);
      b[off+3] = (byte)  (intVal & 0x000000ff);
    }

    /**
     * Converts 8 bytes of a byte array into a long.
     * @param b byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return long value
     */
    public static final long bytesToLong(byte[] b, int off) {
      return ((b[off]   & 0xffL) << 56 |
              (b[off+1] & 0xffL) << 48 |
              (b[off+2] & 0xffL) << 40 |
              (b[off+3] & 0xffL) << 32 |
              (b[off+4] & 0xffL) << 24 |
              (b[off+5] & 0xffL) << 16 |
              (b[off+6] & 0xffL) <<  8 |
               b[off+7] & 0xffL);
    }

    /**
     * Copies an long (64 bit) value into 8 bytes of a byte array.
     * @param longVal long value
     * @param b byte array
     * @param off offset into the byte array
     */
    public static final void longToBytes(long longVal, byte[] b, int off) {
        b[off]   = (byte) ((longVal & 0xff00000000000000L) >>> 56);
        b[off+1] = (byte) ((longVal & 0x00ff000000000000L) >>> 48);
        b[off+2] = (byte) ((longVal & 0x0000ff0000000000L) >>> 40);
        b[off+3] = (byte) ((longVal & 0x000000ff00000000L) >>> 32);
        b[off+4] = (byte) ((longVal & 0x00000000ff000000L) >>> 24);
        b[off+5] = (byte) ((longVal & 0x0000000000ff0000L) >>> 16);
        b[off+6] = (byte) ((longVal & 0x000000000000ff00L) >>>  8);
        b[off+7] = (byte)  (longVal & 0x00000000000000ffL);
    }


    /**
     * Swaps 4 bytes of a byte array in place.
     * @param b byte array
     * @param off offset into the byte array
     */
    public static final void swapArrayInt(byte[] b, int off) {
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
     * Converts 2 bytes of a byte array into a short.
     * @param b byte array
     * @param off offset into the byte array (0 = start at first element)
     * @return short value
     */
    public static final short bytesToShort(byte[] b, int off) {
        return (short) (((b[off]&0xff) << 8) | (b[off+1]&0xff));
    }

    /**
     * Swaps 2 bytes of a byte array in place.
     * @param b byte array
     * @param off offset into the byte array
     */
    public static final void swapArrayShort(byte[] b, int off) {
        byte b1, b2;
        b1 = b[off];
        b2 = b[off+1];
        b[off+1] = b1;
        b[off]   = b2;
    }
}
