package com.lunar.util;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

public class StringUtilTest {

	@Test
	public void testPrintMicroOfDay(){
		ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
		PrintStream outputStream = new PrintStream(outputByteStream);
		StringUtil.printMicroOfDay(outputStream, 41250894982900l);
		String output = outputByteStream.toString();
		assertEquals(0, output.compareTo("11:27:30.894982"));

		StringUtil.printMicroOfDay(System.out, 32949000000001l);

		
		outputByteStream.reset();
		StringUtil.printMicroOfDay(outputStream, 32949000000001l);
		output = outputByteStream.toString();
		assertEquals(0, output.compareTo("09:09:09.000000"));
		
	}
	
	@Test
	public void testPrintNanoOfDay(){
		ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
		PrintStream outputStream = new PrintStream(outputByteStream);
		StringUtil.prettyPrintNanoOfDay(outputStream, 41250894982900l);
		String output = outputByteStream.toString();
		assertEquals(0, output.compareTo("11:27:30.894982900"));

		StringUtil.prettyPrintNanoOfDay(System.out, 32949000000001l);

		
		outputByteStream.reset();
		StringUtil.prettyPrintNanoOfDay(outputStream, 32949000000001l);
		output = outputByteStream.toString();
		assertEquals(0, output.compareTo("09:09:09.000000001"));
		
	}
}
