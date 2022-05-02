package com.lunar.message.binary;

import static org.junit.Assert.*;

import org.junit.Test;

public class JournalPrinterMainTest {
	@Test
	public void testDateFormat(){
		String date = "2016-06-16";
		assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2}"));
		
		date = "2016-06-3";
		assertFalse(date.matches("\\d{4}-\\d{2}-\\d{2}"));

		date = "2016-06-03";
		assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2}"));
	}
}
