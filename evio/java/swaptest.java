import java.lang.*;



public class swaptest {
    

    static int swap_int_value(int val) {
	
	int temp = 0;
	
	temp|=(val&0xff)<<24;
	temp|=(val&0xff00)<<8;
	temp|=(val&0xff0000)>>>8;
	temp|=(val&0xff000000)>>>24;
	
	return(temp);
    }
    
    
    
    static public void main (String[] args) {
	
	int x;

	x = 0xffeeddcc;
	System.out.println("0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

	x = 0x04030201;
	System.out.println("0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

	x = 0x01020304;
	System.out.println("0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

	x = 0x01234567;
	System.out.println("0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

	x = 0x87654321;
	System.out.println("0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

	x = 0x21436587;
	System.out.println("0x" + Integer.toHexString(x) + ":   " + 
			   "0x" + Integer.toHexString(swap_int_value(x)));

    }
    
}
