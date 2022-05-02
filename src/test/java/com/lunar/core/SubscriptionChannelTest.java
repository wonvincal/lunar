package com.lunar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;

public class SubscriptionChannelTest {

	@Test(expected=IllegalArgumentException.class)
	public void testException(){
		SubscriptionChannel.of(1, 0, 1);
	}
	
	@Test
	public void test(){
		int refChannelId = 1;
		long refSeq = 1;
		int refExpectedUpdates = 100;
	
		SubscriptionChannel channel = SubscriptionChannel.of(refChannelId, refSeq, refExpectedUpdates);
		for (int i = 0; i < 50; i++){
			assertEquals(i + 1, channel.getAndIncrementSeq());
		}
		
		MessageSinkRef sink = DummyMessageSink.refOf(1, 1, "dummy", ServiceType.AdminService);
		assertTrue(channel.addSubscriber(sink));
		assertEquals(1, channel.numSubscribers());
		assertTrue(channel.removeSubscriber(sink));
		assertEquals(0, channel.numSubscribers());
	}
	
}
