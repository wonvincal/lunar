package com.lunar.fsm.channelbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import com.lunar.service.ServiceConstant;
import com.lunar.util.BitUtil;

public class ChannelEventTest {
	@Test
	public void test(){
		ChannelEvent event = new ChannelEvent(false);
		assertEquals(0, event.length());
		
		int channelId = 1;
		long channelSeq = 2;
		int seq = 5;
		int templateId = 6;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		Arrays.fill(sourceBuffer.byteArray(), (byte) -1);
		int offset = 0;
		int length = 100; 
		
		event = new ChannelEvent(true);
		assertEquals(0, event.length());
		event.merge(channelId, 
				channelSeq, 
				seq, 
				templateId, 
				sourceBuffer, 
				offset, 
				length);
		
		assertEquals(channelId, event.channelId());
		assertEquals(channelSeq, event.channelSeq());
		assertEquals(templateId, event.templateId());
		assertTrue(BitUtil.compare(sourceBuffer, offset, event.buffer(), 0, length));
	}
}
