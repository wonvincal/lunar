package com.lunar.util;

import java.nio.ByteOrder;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageSinkEventFactory;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EchoSbeEncoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.EchoSender;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class LogUtilTest {
	static final Logger LOG = LogManager.getLogger(LogUtilTest.class);

	@Test
	public void testPrintFrameHeaderIntoHex(){
		final int size = 64;
		final int sample = 8;
		Frame frame = new Frame(false, size);
		frame.buffer().putByte(0, (byte)sample);
		String result = LogUtil.dumpBinary(frame, size, 8);
		LOG.info("{} is \n{}", sample, result);
		// assertTrue((result.compareTo("08") == 0));
	}

	@Test
	public void testPrintFrameHeaderOfMultipleDirtyBytesIntoHex(){
		final int size = 64;
		Frame frame = new Frame(false, size);
		frame.buffer().putByte(0, (byte)-1);
		frame.buffer().putByte(1, (byte)127);
		frame.buffer().putInt(2, 1025, ByteOrder.nativeOrder());
		String result = LogUtil.dumpBinary(frame, size, 8);
		LOG.info("byte order {}\n{}", ByteOrder.nativeOrder(), result);
		// assertTrue((result.compareTo("08") == 0));
	}

	@Test
	public void testPrintBytesIntoHex(){
		MutableDirectBuffer frame = MessageSinkEventFactory.of().newInstance();
		EchoSbeEncoder sbe = new EchoSbeEncoder();
		MessageSender sender = MessageSender.of(ServiceConstant.MAX_MESSAGE_SIZE,
				MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-any"));
		EchoSender.encodeEcho(sender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				frame, 
				0, 
				sbe, 
				0, 
				System.nanoTime(), 
				BooleanType.FALSE);
		LOG.info("\n{}\n", LogUtil.dumpBinary(frame));
		
		EchoSender.encodeEcho(sender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				frame, 
				0, 
				sbe, 
				1, 
				System.nanoTime(), 
				BooleanType.FALSE);
		LOG.info("\n{}\n", LogUtil.dumpBinary(frame));

		EchoSender.encodeEcho(sender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				frame, 
				0, 
				sbe, 
				2, 
				System.nanoTime(), 
				BooleanType.FALSE);
		LOG.info("\n{}\n", LogUtil.dumpBinary(frame));
	}

}
