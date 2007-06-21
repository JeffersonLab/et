//
//  Evioswap.java
//
//   java equivalent of evioswap.c, but not as efficient
//     ...note that routines with same name are subtly different from c version


package org.jlab.coda.evio;

//----------------------------------------------------------------
//----------------------------------------------------------------

/**
 *  <b>Utilities to byte-swap evio format events</b><br><br>
 *
 *  - Compatible with evio version 2<br>
 *  - Thread safe<br>
 *  - All routines are static<br>
 *
 * @version 1.0
 * @author E.Wolin, Jefferson Lab, 8-dec-2003
 */

public class Evioswap {



//----------------------------------------------------------------

 /**
  *  Swaps one evio event in integer array
  *
  *  @param buf        integer buffer to swap
  *  @param tolocal    true to swap from other to local format,
  *                    false for reverse
  *  @param dest       destination for swapped event, null if swap in place
  */
    public static void swap(int[] buf, boolean tolocal, int[] dest) {

	swap_bank(buf,0,tolocal,dest);
	return;
    }


//---------------------------------------------------------------- 


    private static void swap_bank(int[] buf, int offset, boolean tolocal, int[] dest) {

	int data_length,data_type;
	

	if(tolocal) {
	    data_length = swap_int_value(buf[offset])-1;
	    data_type   = (swap_int_value(buf[offset+1])>>8)&0xff;
	    swap_int(buf,offset,2,dest);
	} else {
	    data_length = buf[offset]-1;
	    data_type   = (buf[offset+1]>>8)&0xff;
	    swap_int(buf,offset,2,dest);
	}
	
	swap_data(buf,offset+2,data_type,data_length,tolocal,dest);
	
	return;
    }
    

//---------------------------------------------------------------- 


    private static void swap_segment(int[] buf, int offset, boolean tolocal, int[] dest) {
	
	int d,data_length,data_type;
	

	if(tolocal) {
	    d=swap_int_value(buf[offset]);
	    swap_int(buf,offset,1,dest);
	} else {
	    d=buf[offset];
	    swap_int(buf,offset,1,dest);
	}
	
	data_length  = (d&0xffff);
	data_type    = (d>>16)&0xff;
	swap_data(buf,offset+1,data_type,data_length,tolocal,dest);
	
	return;
    }
    

//---------------------------------------------------------------- 

  
    private static void swap_tagsegment(int[]  buf, int offset, boolean tolocal, int[]  dest) {
    
	int d,data_length,data_type;
    
    
	if(tolocal) {
	    d=swap_int_value(buf[offset]);
	    swap_int(buf,offset,1,dest);
	} else {
	    d=buf[offset];
	    swap_int(buf,offset,1,dest);
	}
    
	data_length  = (d&0xffff);
	data_type    = (d>>16)&0xf;
	swap_data(buf,offset+1,data_type,data_length,tolocal,dest);
    
	return;
    }


//---------------------------------------------------------------- 


    private static void swap_data(int[] data, int offset,
			  int type, int length, boolean tolocal,
			  int[] dest) {
    
	int fraglen;
	int l=0;


	// swap the data or call swap_fragment 
	switch (type) {
      
      
	    // undefined...no swap 
	case 0x0:
	    copy_data(data,offset,length,dest);
	    break;
      
      
	    // int
	case 0x1:
	case 0x2:
	case 0xb:
	    swap_int(data,offset,length,dest);
	    break;


	    // char or byte 
	case 0x3:
	case 0x6:
	case 0x7:
	    copy_data(data,offset,length,dest);
	    break;


	    // short 
	case 0x4:
	case 0x5:
	    swap_short(data,offset,length,dest);
	    break;


	    // long
	case 0x8:
	case 0x9:
	case 0xa:
	    swap_long(data,offset,length,dest);
	    break;



	    // bank 
	case 0xe:
	case 0x10:
	    while(l<length) {
		if(tolocal) {
		    swap_bank(data,offset+l,tolocal,dest);
		    fraglen=(dest==null)?data[offset+l]+1:dest[offset+l]+1;
		} else {
		    fraglen=data[offset+l]+1;
		    swap_bank(data,offset+l,tolocal,dest);
		}
		l+=fraglen;
	    }
	    break;


	    // segment 
	case 0xd:
	case 0x20:
	    while(l<length) {
		if(tolocal) {
		    swap_segment(data,offset+l,tolocal,dest);
		    fraglen=(dest==null)?(data[offset+l]&0xffff)+1:(dest[offset+l]&0xffff)+1;
		} else {
		    fraglen=(data[offset+l]&0xffff)+1;
		    swap_segment(data,offset+l,tolocal,dest);
		}
		l+=fraglen;
	    }
	    break;


	    // tagsegment 
	case 0xc:
	case 0x40:
	    while(l<length) {
		if(tolocal) {
		    swap_tagsegment(data,offset+l,tolocal,dest);
		    fraglen=(dest==null)?(data[offset+l]&0xffff)+1:(dest[offset+l]&0xffff)+1;
		} else {
		    fraglen=(data[offset+l]&0xffff)+1;
		    swap_tagsegment(data,offset+l,tolocal,dest);
		}
		l+=fraglen;
	    }
	    break;


	    // unknown type, just copy 
	default:
	    copy_data(data,offset,length,dest);
	    break;
	}

	return;
    }


//---------------------------------------------------------------- 


 /**
  *  Utility returns swapped version of 4-byte integer
  *
  *  @param val        integer to swap
  *  @return           swapped 4-byte integer
  */
    public static int swap_int_value(int val) {
	
	int temp = 0;
	
	temp|=(val&0xff)<<24;
	temp|=(val&0xff00)<<8;
	temp|=(val&0xff0000)>>>8;
	temp|=(val&0xff000000)>>>24;
	
	return(temp);
    }
    
    
//---------------------------------------------------------------- 


 /**
  *  Utility swaps sub-array of 4-byte integers
  *
  *  @param data        integer array
  *  @param offset      offset in array to start swapping
  *  @param length      number of integer words to swap
  *  @param dest        destination array, null if swap in place
  */
    public static void swap_int(int[] data, int offset, int length, int[] dest) {
	
	int i;
	
	if(dest==null) {
	    for(i=0; i<length; i++) {
		data[offset+i]=swap_int_value(data[offset+i]);
	    }
	} else {
	    for(i=0; i<length; i++) {
		dest[offset+i]=swap_int_value(data[offset+i]);
	    }
	}
	return;
    }
    

//---------------------------------------------------------------- 


 /**
  *  Utility swaps sub-array of 8-byte longs packed into 4-byte integer array
  *
  *  @param data        integer array containing longs
  *  @param offset      offset in array to start swapping
  *  @param length      number of integer words containing longs to swap
  *  @param dest        destination array, null if swap in place
  */
    public static void swap_long(int data[], int offset, int length, int[] dest) {

	int i,temp;
	
	if(dest==null) {
	    for(i=0; i<length; i+=2) {
		temp=data[offset+i];
		data[offset+i  ]=swap_int_value(data[offset+i+1]);
		data[offset+i+1]=swap_int_value(temp);
	    }
	} else {
	    for(i=0; i<length; i+=2) {
		dest[offset+i  ]=swap_int_value(data[offset+i+1]);
		dest[offset+i+1]=swap_int_value(data[offset+i  ]);
	    }
	}
	return;
    }


//---------------------------------------------------------------- 


 /**
  *  Utility returns swapped version of pair of 2-byte shorts packed into a 4-byte integer
  *
  *  @param val        integer containing pair of 2-byte shorts
  *  @return           swapped pair of 2-byte shorts in 4-byte integer
  */
    public static int swap_short_value(int val) {
	
	int temp = 0;
	
	temp|=(val&0xff)<<8;
	temp|=(val&0xff00)>>>8;
	temp|=(val&0xff0000)<<8;
	temp|=(val&0xff000000)>>>8;
	
	return(temp);
    }
    
    
//---------------------------------------------------------------- 


 /**
  *  Utility swaps sub-array of 2-byte shorts packed in 4-byte integer array
  *
  *  @param data        integer array containing shorts to swap
  *  @param offset      offset in array to start swapping
  *  @param length      number of 4-byte words to swap
  *  @param dest        destination array, null if swap in place
  */
    public static void swap_short(int[] data, int offset, int length, int[] dest) {

	int i;
	
	if(dest==null) {
	    for(i=0; i<length; i++) {
		data[offset+i]=swap_short_value(data[offset+i]);
	    }
	} else {
	    for(i=0; i<length; i++) {
		dest[offset+i]=swap_short_value(data[offset+i]);
	    }
	}
	return;
    }
 
 
//---------------------------------------------------------------- 
 
 
  private static void copy_data(int[] data, int offset, int length, int[] dest) {
    
    int i;
    
    if(dest==null)return;
    for(i=0; i<length; i++)dest[offset+i]=data[offset+i];
    return;
  }
 
 
//---------------------------------------------------------------- 


 /**
  *  For testing only
  */
    static public void main (String[] args) {

	
	int x;
	int[] s = new int[2];
	int[] d = new int[2];


	x = 0xffeeddcc;
	System.out.println("long:   0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

	x = 0xffeeddcc;
	System.out.println("short:  0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_short_value(x)));


	s[0]=0x10203040;
	s[1]=0x50607080;
	swap_long(s, 0, 2, null);
	System.out.println("0x" + Integer.toHexString(s[0]) + Integer.toHexString(s[1]));

	swap_long(s, 0, 2, d);
	System.out.println("0x" + Integer.toHexString(d[0]) + Integer.toHexString(d[1]));

    }


//---------------------------------------------------------------- 
//----------------------------------------------------------------
}
