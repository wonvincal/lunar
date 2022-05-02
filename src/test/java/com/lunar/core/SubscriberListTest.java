package com.lunar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;

public class SubscriberListTest {

	@Test
	public void test(){
		SubscriberList list = SubscriberList.of();
		assertEquals(0, list.size());
		
		assertEquals(0, list.size());
		assertNotNull(list.elements());
		
		// add
		assertTrue(list.add(MessageSinkRef.createNaSinkRef()));
		assertEquals(1, list.size());

		// add same
		assertFalse(list.add(MessageSinkRef.createNaSinkRef()));
		assertEquals(1, list.size());
		
		// remove
		assertTrue(list.remove(MessageSinkRef.createNaSinkRef()));
		assertEquals(0, list.size());
		
		// remove non exist
		assertFalse(list.remove(MessageSinkRef.createNaSinkRef()));
		assertEquals(0, list.size());

		// add a real sink
		int sinkId = 10;
		assertTrue(list.add(MessageSinkRef.of(DummyMessageSink.of(sinkId, "dummy", ServiceType.AdminService))));
		assertEquals(1, list.size());

		for (int i = sinkId - 1; i > 0; i--){
			assertTrue(list.add(MessageSinkRef.of(DummyMessageSink.of(i, "dummy" + i, ServiceType.AdminService))));
		}
		assertEquals(sinkId, list.size());
		
		MessageSinkRef[] elements = list.elements();
		int sum = 0;
		for (int i = 0; i < list.size(); i++){
			sum += elements[i].sinkId();
		}
		assertEquals((1 + sinkId) * sinkId / 2, sum);
	}
}
