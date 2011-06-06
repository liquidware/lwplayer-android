package com.liquidware.lwplayer;

public class Util {
	public Util() {
		
	}
	
	public static int toInt8(byte[] b) {
		int r;
		r = b[0] & 0xFF;
		return r;
	}
	
	public static int toInt16(byte[] b) {
		int r;
		r = ((b[1] & 0xFF) << 8) + 
   	    	 (b[0] & 0xFF);
		return r; 
	}

	public static int toInt32(byte[] b) {
		int r;
		r = ((b[3] & 0xFF) << 24) + 
		    ((b[2] & 0xFF) << 16) +
		    ((b[1] & 0xFF) << 8) +
		     (b[0] & 0xFF);
		return r; 
	}
}
