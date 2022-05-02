package com.lunar.util;

import java.lang.management.ManagementFactory;

public class ProcessUtil {
	/**
	 * Please note that this does not guarantee to work in all JVM implementations
	 * We need to perform proper test case and extend this method if necessary 
	 * @return
	 */
	public static int getProcessId(){
		return Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
	}
}
