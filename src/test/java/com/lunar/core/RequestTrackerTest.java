package com.lunar.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RequestTrackerTest {
	@Before
	public void setup(){
	}
	
	@After
	public void cleanup(){
		// noop
	}
	
	@Test
	public void whenSendAndTrackOneRequestThenOneOutstanding(){
	}
	
	@Test
	public void givenOutstandingRequestWhenReceivesObjectResponseThenHandles(){
	}

	@Test
	public void givenOutstandingRequestWhenReceivesUnrelatedResponseThenHandles(){
	}

	@Test
	public void givenOutstandingRequestWhenReceivesBinaryResponseThenHandles(){
	}

	@Test
	public void givenOutstandingRequestWhenReceivesBinaryTimerEventResponseThenHandles(){
	}
}
