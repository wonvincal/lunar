package com.lunar.util;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.agrona.MutableDirectBuffer;

import com.lunar.core.StringConstant;

public class StringUtil {	
	public static MutableDirectBuffer copyAndPadSpace(String src, String encoding, MutableDirectBuffer dstBuffer, int maxLength){
		byte[] bytes;
		try {
			bytes = src.getBytes(encoding);
			dstBuffer.setMemory(0, maxLength, StringConstant.WHITESPACE);
			dstBuffer.putBytes(0, bytes, 0, BitUtil.min(maxLength, src.length()));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return dstBuffer;
	}

	public static MutableDirectBuffer copy(String src, MutableDirectBuffer dstBuffer){
		dstBuffer.putBytes(0, src.getBytes(), 0, src.length());
		return dstBuffer;
	}
	
	@SuppressWarnings("deprecation")
	public static byte[] copyIntoBuffer(String src, MutableDirectBuffer byteBuffer){
		src.getBytes(0, src.length(), byteBuffer.byteArray(), 0);
		return byteBuffer.byteArray();
	}

	@SuppressWarnings("deprecation")
	public static byte[] copyAndPadSpace(String src, int length, MutableDirectBuffer byteBuffer){
		byteBuffer.setMemory(0, length, StringConstant.WHITESPACE);
		src.getBytes(0, BitUtil.min(length, src.length()), byteBuffer.byteArray(), 0);
		return byteBuffer.byteArray();
	}
	
	public static PrintStream prettyPrintNanoOfDay(PrintStream outputStream, long nanoOfDay){
		int seconds = (int)(nanoOfDay / 1_000_000_000);
		long nano = nanoOfDay % 1_000_000_000;
		int minutes = seconds / 60;
		seconds = seconds % 60;
		int hours = minutes / 60;
		minutes = minutes % 60;
		
		if (hours < 10){
			outputStream.append(StringConstant.ZERO_CHAR);
		}
		outputStream.print(hours);
		outputStream.append(StringConstant.COLON_CHAR);
		if (minutes < 10){
			outputStream.append(StringConstant.ZERO_CHAR);
		}
		outputStream.print(minutes);
		outputStream.append(StringConstant.COLON_CHAR);
		if (seconds < 10){
			outputStream.append(StringConstant.ZERO_CHAR);
		}
		outputStream.print(seconds);
		outputStream.append(StringConstant.PERIOD_CHAR);
		
		// TODO We can make use of binary hacks to speed this up
		outputStream.print(
				(nano >= 100_000_000) ? "" :
					(nano >= 10_000_000) ? "0" :
						(nano >= 1_000_000) ? "00" :
							(nano >= 100_000) ? "000" :
								(nano >= 10_000) ? "0000" :
									(nano >= 1_000) ? "00000" :
										(nano >= 100) ? "000000" :
											(nano >= 10) ? "0000000" : "00000000");
		outputStream.print(nano);
		
		return outputStream;
	}
	
	public static PrintStream printMicroOfDay(PrintStream outputStream, long nanoOfDay){
		long microOfDay = nanoOfDay / 1000;
		int seconds = (int)(microOfDay / 1000_000);
		microOfDay = microOfDay % 1000_000;
		int minutes = seconds / 60;
		seconds = seconds % 60;
		int hours = minutes / 60;
		minutes = minutes % 60;
		
		if (hours < 10){
			outputStream.append(StringConstant.ZERO_CHAR);
		}
		outputStream.print(hours);
		outputStream.append(StringConstant.COLON_CHAR);
		if (minutes < 10){
			outputStream.append(StringConstant.ZERO_CHAR);
		}
		outputStream.print(minutes);
		outputStream.append(StringConstant.COLON_CHAR);
		if (seconds < 10){
			outputStream.append(StringConstant.ZERO_CHAR);
		}
		outputStream.print(seconds);
		outputStream.append(StringConstant.PERIOD_CHAR);
		
		// 999_999 <= microOfDay <= 0
		// TODO We can make use of binary hacks to speed this up
		outputStream.print((microOfDay >= 100_000) ? "" : (microOfDay >= 10_000) ? "0" : (microOfDay >= 1_000) ? "00" : (microOfDay >= 100) ? "000" : (microOfDay >= 10) ? "0000" : "00000");
		outputStream.print(microOfDay);
		
		return outputStream;
		
	}
}
