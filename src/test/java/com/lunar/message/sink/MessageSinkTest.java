package com.lunar.message.sink;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MessageSinkTest {

	@Test
	public void testMessage(){
		assertEquals(0, "INSUFFICIENT_SPACE".compareTo(MessageSink.getSendResultMessage(MessageSink.INSUFFICIENT_SPACE)));
	}
}
