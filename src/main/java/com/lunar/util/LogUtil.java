package com.lunar.util;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.message.binary.Frame;

public class LogUtil {
	public enum LogType {
		SERVICE_STATUS(0);
		
		private final int id;
		LogType(int id){
			this.id = id;
		}
		public int id(){ return id;}
	}
	
	static final Logger LOG = LogManager.getLogger(LogUtil.class);

	public static String dumpBinary(final MutableDirectBuffer buffer){
		return dumpBinary(buffer.byteBuffer(), 104, 8);
	}

	public static String dumpBinary(final ByteBuffer buffer){
		return dumpBinary(buffer, 104, 8);
	}

	public static String dumpBinary(final ByteBuffer buffer, int numBytesToDisplay, int newLineEveryNBytes){
		return dumpBinary(buffer, 0, numBytesToDisplay, newLineEveryNBytes);
	}

	public static String dumpBinary(final ByteBuffer buffer, int offset, int numBytesToDisplay, int newLineEveryNBytes){
		StringBuilder sb = new StringBuilder();
		int numBytes = Math.min(numBytesToDisplay, buffer.capacity());
		numBytes -= (numBytes % 4); 
		LOG.debug("numBytes {}, newLineAt {}", numBytes, newLineEveryNBytes);
		for (int index = offset; index < offset + numBytes; index+=4){
			sb.append(String.format("%02X %02X %02X %02X ", 
					buffer.get(index),
					buffer.get(index + 1),
					buffer.get(index + 2),
					buffer.get(index + 3)));
			int written = index + 4;
			if (written % newLineEveryNBytes == 0){
				sb.append(" | " + String.valueOf(written) + "\n");
			}
		}
		return sb.toString();		
	}

	public static String dumpBinary(final byte[] bytes, int numBytesToDisplay, int newLineEveryNBytes){
		StringBuilder sb = new StringBuilder();
		int numBytes = Math.min(numBytesToDisplay, bytes.length);
		numBytes -= (numBytes % 4); 
		LOG.debug("numBytes {}, newLineAt {}", numBytes, newLineEveryNBytes);
		for (int offset = 0; offset < numBytes; offset+=4){
			sb.append(String.format("%02X %02X %02X %02X ", 
					bytes[offset],
					bytes[offset + 1],
					bytes[offset + 2],
					bytes[offset + 3]));
			int written = offset + 4;
			if (written % newLineEveryNBytes == 0){
				sb.append(" | " + String.valueOf(written) + "\n");
			}
		}
		return sb.toString();		
	}

	public static String dumpBinary(final Frame message){
		return dumpBinary(message, 104, 8);
	}

	public static String dumpBinary(final Frame message, int numBytesToDisplay, int newLineEveryNBytes){
		return dumpBinary(message.buffer().byteBuffer(), numBytesToDisplay, newLineEveryNBytes);
	}
	
	public static String dumpBinary(final DirectBuffer buffer, int numBytesToDisplay, int newLineEveryNBytes){
		StringBuilder sb = new StringBuilder();
		int numBytes = Math.min(numBytesToDisplay, buffer.capacity());
		numBytes -= (numBytes % 4); 
		LOG.debug("numBytes {}, newLineAt {}", numBytes, newLineEveryNBytes);
		for (int index = 0; index < numBytes; index+=4){
			sb.append(String.format("%02X %02X %02X %02X ", 
					buffer.getByte(index),
					buffer.getByte(index + 1),
					buffer.getByte(index + 2),
					buffer.getByte(index + 3)));
			int written = index + 4;
			if (written % newLineEveryNBytes == 0){
				sb.append(" | " + String.valueOf(written) + "\n");
			}
		}
		return sb.toString();		
	}

	public static void logService(Logger log, LunarService messageService, String info){
		log.info("Service {} [logType:{}, id:{}, name:{}, state:{}]", 
				info, 
				LogType.SERVICE_STATUS.id(), 
				messageService.messenger().self().sinkId(),
				messageService.name(), 
				messageService.state());
	}
	
	public static void logService(Logger log, LunarService messageService){
		log.info("Service [logType:{}, id:{}, name:{}, state:{}]", 
				LogType.SERVICE_STATUS.id(), 
				messageService.messenger().self().sinkId(),
				messageService.name(), 
				messageService.state());
	}
}
