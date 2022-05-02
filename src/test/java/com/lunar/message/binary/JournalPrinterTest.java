package com.lunar.message.binary;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Ignore;
import org.junit.Test;

import com.lunar.journal.JournalBuilder;
import com.lunar.journal.JournalReader;
import com.lunar.message.binary.JournalPrinters.MarketDataToCsvUserSuppliedType;

public class JournalPrinterTest {

	@Ignore
	@Test
	public void test() throws Exception{
		//PrintStream outputStream = System.out;
		PrintStream outputStream = new PrintStream(new FileOutputStream("C:\\Users\\wongca\\home\\output.txt"));
		String fileNameSeed = "mds";
		String date = "2016-06-16";
		Path inputPath = Paths.get("C:\\Users\\wongca\\home\\dev\\lunar\\lunar\\journal");
		JournalBuilder builder = JournalBuilder.of(inputPath, fileNameSeed);
		JournalReader reader = builder.buildReader();
		JournalPrinter printer = JournalPrinters.MARKET_DATA_TO_CSV
				.outputStream(outputStream)
				.addUserSupplied(MarketDataToCsvUserSuppliedType.DATA_DATE, date);
		printer.output(reader, Integer.MAX_VALUE);
		outputStream.close();

	}
}
