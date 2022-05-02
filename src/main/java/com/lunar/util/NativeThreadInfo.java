package com.lunar.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NativeThreadInfo {
    private static final Logger LOG = LogManager.getLogger(NativeThreadInfo.class);

    public static native int getTid();
    public final static boolean LOADED;
	
	static {
		boolean isLoaded = false;
		try {
			System.loadLibrary("nativethreadinfo");
			isLoaded = true;
		}
		catch (Exception ex){
			LOG.error("Could not load nativethreadinfo", ex);
		}
		LOADED = isLoaded;
	}
	
	public static int getNativeTid(){
		return (LOADED) ? getTid() : -1; 
	}
}
