package com.lunar.message.binary;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Strings;
import com.lunar.journal.JournalBuilder;
import com.lunar.journal.JournalReader;
import com.lunar.message.binary.JournalPrinters.MarketDataToCsvUserSuppliedType;

public class JournalPrinterMain {
	@Parameter(names={"--path", "-p"})
	String path;
	
	@Parameter(names={"--nameSeed", "-n"})
	String fileNameSeed;
	
	@Parameter(names={"--date", "-d"})
	String date;
	
	@Parameter(names={"--output", "-o"})
	String output;
	
	@Parameter(names={"--help", "-h"})
	boolean help;
	
	@Parameter(names={"--rows", "-r"})
	int numOfRows;
	
	@Parameter(names={"--printer"})
	String printer;
	
	public static enum PrinterType {
		PERF_CSV,
		MD_CSV
	}
	
	public static void main(final String[] args) throws Exception
	{
		JournalPrinterMain main = new JournalPrinterMain();
		new JCommander(main, args);
		
		if (main.help){
			System.out.println("--path -p | Path to the journal files");
			System.out.println("--printer | Printer (PERF_CSV, MD_CSV) ");
			System.out.println("--nameSeed -n | Prefix of the journal files to be read (ex. mds, rds...etc)");
			System.out.println("--date -d | Date of data");
			System.out.println("--output -o | Path of output.  If not specified, output will be written to System.out");
			System.out.println("--rows -r | Number of rows");
			System.out.println("--help -h | Display this help");
			return;
		}
		
		if (Strings.isNullOrEmpty(main.path)){
			System.out.println("Path must be specified");
			return;
		}
		
		Path inputPath = Paths.get(main.path);
		if (Files.notExists(inputPath)){
			System.out.println("Path must exist");
			return;
		}
		if (!Files.isDirectory(inputPath)){
			System.out.println("Path must be a directory");
			return;
		}
		if (Strings.isNullOrEmpty(main.fileNameSeed)){
			System.out.println("Name seed must not be null");
			return;
		}
		
		String fileNameSeed = main.fileNameSeed;
		
		PrintStream outputStream = System.out;
		if (!Strings.isNullOrEmpty(main.output)){
			Path outputPath = Paths.get(main.output);
			if (Files.isDirectory(outputPath)){
				System.out.println("Output must be a file path");
				return;
			}
			outputStream = new PrintStream(new FileOutputStream(main.output), false);
		}
		
		if (Strings.isNullOrEmpty(main.date)){
			System.out.println("Date must not be null");
			return; 
		}
		if (!main.date.matches("\\d{4}-\\d{2}-\\d{2}")){
			System.out.println("Date must match YYYY-MM-DD pattern");
			return;
		}

		JournalBuilder builder = JournalBuilder.of(inputPath, fileNameSeed);
		JournalReader reader = builder.buildReader();
		
		JournalPrinter printer;
		try {
			PrinterType printerType = PrinterType.valueOf(main.printer);
			switch (printerType){
			case MD_CSV:
				printer = JournalPrinters.MARKET_DATA_TO_CSV;
				break;
			case PERF_CSV:
				printer = JournalPrinters.PERF_DATA_TO_CSV;
				break;
			default:
				System.out.println("Unsupported printer type: " + main.printer);
				return;				
			}
		}
		catch (Exception e){
			System.out.println("Invalid printer type");
			e.printStackTrace();
			return;
		}
		
		printer.outputStream(outputStream)
			.addUserSupplied(MarketDataToCsvUserSuppliedType.DATA_DATE, main.date);
		
		int rows = (main.numOfRows == 0) ? Integer.MAX_VALUE : main.numOfRows;
		printer.output(reader, rows);
		if (outputStream != System.out){
			outputStream.close();
		}
	}
	
	public void test(){
		String inputPath = "/home/wongca/dev/workspace/lunar/lunar/src/gradle.properties";
		Path path = Paths.get(inputPath);
		Path fileNameSeed = path.getFileName();
		Path directory = path.getParent();
		System.out.println("fileNameSeed: " + fileNameSeed.toString());
		System.out.println("directory: " + directory.toString());
	}
}
