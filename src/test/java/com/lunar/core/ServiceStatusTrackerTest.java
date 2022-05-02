package com.lunar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.ServiceStatusTracker.AggregatedServiceStatusChangeHandler;
import com.lunar.core.ServiceStatusTracker.ServiceStatusChangeHandler;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.MessageHeaderUtil;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeEncoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class ServiceStatusTrackerTest {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(ServiceStatusTrackerTest.class);
	private ServiceStatusTracker tracker;
	private int senderSinkId = 1;
	private TestHelper testHelper = TestHelper.of();
	private String name = "test-service";
	private int selfSinkId = 2;

	@Mock
	private ServiceStatusChangeHandler serviceStatusChangeHandler;
	
	@Mock
	private AggregatedServiceStatusChangeHandler aggregatedServiceStatusChangeHandler; 
	
	@Before
	public void setup(){
		tracker = new ServiceStatusTracker(selfSinkId, name);
	}
	
	@Test
	public void givenNewlyCreatedThenValidateInitialState(){
		assertTrue(tracker.toStatusArray().length == 0);
		
		int count = 0;
		for (@SuppressWarnings("unused") ServiceStatus status : tracker.statuses()){
			count++;
		}
		assertEquals(0, count);
	}

	@Test
	public void givenTrackingOneServiceTypeWhenTwoServiceStatusMsgAreReceivedThenOnlyOneHandlerIsCalled(){
		// given
		tracker.trackServiceType(ServiceType.AdminService, serviceStatusChangeHandler);
		
		// when
		int sinkId = 2;
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
														sinkId,
														ServiceType.AdminService,
														ServiceStatusType.INITIALIZING,
														System.nanoTime());
		tracker.onMessage(serviceStatus);
		
		sinkId = 3;
		serviceStatus = ServiceStatus.of(senderSinkId, 
										  sinkId,
										  ServiceType.AeronService,
										  ServiceStatusType.INITIALIZING,
										  System.nanoTime());
		
		
		
		// then
		ArgumentCaptor<ServiceStatus> argumentCaptor = ArgumentCaptor.forClass(ServiceStatus.class);
		verify(serviceStatusChangeHandler, times(1)).handle(argumentCaptor.capture());
		assertEquals(ServiceType.AdminService, argumentCaptor.getValue().serviceType());
		assertEquals(ServiceStatusType.INITIALIZING, argumentCaptor.getValue().statusType());
	}

	@Test
	public void givenTrackingOneServiceTypeWhenBinaryServiceTypeChangeIsReceivedThenCallHandler(){
		// given
		tracker.trackServiceType(ServiceType.AdminService, serviceStatusChangeHandler);
		
		// when
		final int sinkId = 2;
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.DOWN,
				System.nanoTime());
		// create binary message
		MutableDirectBuffer buffer = testHelper.createDirectBuffer();
		int payloadLength =  ServiceStatusSender.encodeServiceStatusWithoutHeader(buffer,
				MessageHeaderDecoder.ENCODED_LENGTH, 
				new ServiceStatusSbeEncoder(), 
				serviceStatus.systemId(),
				serviceStatus.sinkId(), 
				serviceStatus.serviceType(), 
				serviceStatus.statusType(), 
				serviceStatus.modifyTimeAtOrigin(),
				serviceStatus.sentTime(),
				serviceStatus.healthCheckTime());
		
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0, 
				ServiceStatusSbeEncoder.BLOCK_LENGTH, 
				ServiceStatusSbeEncoder.SCHEMA_VERSION, 
				ServiceStatusSbeEncoder.TEMPLATE_ID, 
				1, 
				payloadLength, 
				(byte)sinkId, 
				(byte)sinkId);
		
		ServiceStatusSbeDecoder decoder = new ServiceStatusSbeDecoder();
		
		tracker.eventHandler().handle(buffer,
				0,
				header,
				decoder.wrap(buffer, MessageHeaderDecoder.ENCODED_LENGTH, ServiceStatusSbeDecoder.BLOCK_LENGTH, ServiceStatusSbeDecoder.SCHEMA_VERSION));

		// then
		ArgumentCaptor<ServiceStatus> argumentCaptor = ArgumentCaptor.forClass(ServiceStatus.class);
		verify(serviceStatusChangeHandler, times(1)).handle(argumentCaptor.capture());
		assertEquals(ServiceType.AdminService, argumentCaptor.getValue().serviceType());
		assertEquals(ServiceStatusType.DOWN, argumentCaptor.getValue().statusType());
	}
	
	@Test
	public void givenTrackingAnyServiceStatusWhenStatustestHandlerForAnyServiceStatus(){
		// given
		tracker.trackAnyServiceStatusChange(serviceStatusChangeHandler);
		
		// when
		int sinkId = 2;
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.INITIALIZING,
				System.nanoTime());

		tracker.onMessage(serviceStatus);
		
		// then
		ArgumentCaptor<ServiceStatus> argumentCaptor = ArgumentCaptor.forClass(ServiceStatus.class);
		verify(serviceStatusChangeHandler, times(1)).handle(argumentCaptor.capture());
		assertEquals(ServiceType.AdminService, argumentCaptor.getValue().serviceType());
		assertEquals(ServiceStatusType.INITIALIZING, argumentCaptor.getValue().statusType());
	}
	
	@Test(expected=IllegalStateException.class)
	public void givenNothingIsBeingTrackedWhenStartToTrackAggregatedStatusThenThrowException(){
		// when
		tracker.trackAggregatedServiceStatus(aggregatedServiceStatusChangeHandler);		
	}

	@Test
	public void givenTrackingSinkIdWhenServiceStatusIsReceivedUnderDifferntScenariosThenHandlerIsCalledMultipleTimes(){
		// given
		int sinkId = 2;
		tracker.trackSinkId(sinkId, serviceStatusChangeHandler);
		
		// when
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId + 1,
				ServiceType.AdminService,
				ServiceStatusType.INITIALIZING,
				System.nanoTime());

		tracker.onMessage(serviceStatus);
		
		serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.INITIALIZING,
				System.nanoTime());
		tracker.onMessage(serviceStatus); // triggered once only
		tracker.onMessage(serviceStatus);

		serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.UP,
				System.nanoTime());
		tracker.onMessage(serviceStatus); // change in status

		// then
		verify(serviceStatusChangeHandler, times(2)).handle(any());
	}
	
	@Test
	public void givenTrackingAllServiceWhenUpServiceStatusIsReceivedThenBothHandlersAreCalled(){
		// given
		int sinkId = 2;
		tracker.trackSinkId(sinkId, serviceStatusChangeHandler);
		tracker.trackAggregatedServiceStatus(aggregatedServiceStatusChangeHandler);

		// when
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.UP,
				System.nanoTime());

		tracker.onMessage(serviceStatus);
		verify(serviceStatusChangeHandler, times(1)).handle(any());
		verify(aggregatedServiceStatusChangeHandler, times(1)).handle(anyBoolean());		
	}

	@Test
	public void givenTrackingAllServiceWhenDownServiceStatusIsReceivedThenAggregateHandlerIsNotCalled(){
		// given
		int sinkId = 2;
		tracker.trackSinkId(sinkId, serviceStatusChangeHandler);
		tracker.trackAggregatedServiceStatus(aggregatedServiceStatusChangeHandler);
		assertFalse(tracker.allTrackedServicesUp());

		// when
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.DOWN,
				System.nanoTime());

		tracker.onMessage(serviceStatus);
		verify(serviceStatusChangeHandler, times(1)).handle(any());
		verify(aggregatedServiceStatusChangeHandler, times(0)).handle(anyBoolean());
		assertFalse(tracker.allTrackedServicesUp());
	}
	
	@Test
	public void givenNoTrackedServiceWhenUntrackThenNothingHappen(){
		// given
		assertFalse(tracker.isTracking());
		
		// when
		tracker.untrackAnyServiceStatusChange(serviceStatusChangeHandler);
		
		// then
		verify(serviceStatusChangeHandler, times(0)).handle(any());		
	}
	
	@Test
	public void givenTrackingAnyChangeWhenUntrackAndReceiveServiceStatusThenDoNothing(){
		// given
		assertFalse(tracker.isTracking());
		tracker.trackAnyServiceStatusChange(serviceStatusChangeHandler);
		
		// when
		tracker.untrackAnyServiceStatusChange(serviceStatusChangeHandler);
		int sinkId = 2;
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.DOWN,
				System.nanoTime());
		tracker.onMessage(serviceStatus);
		
		// then
		verify(serviceStatusChangeHandler, times(0)).handle(any());		
	}
	
	@Test
	public void givenTrackingByServiceTypeWhenUntrackAndReceiveServiceStatusThenDoNothing(){
		// given
		assertFalse(tracker.isTracking());
		tracker.trackServiceType(ServiceType.AdminService, serviceStatusChangeHandler);
		
		// when
		tracker.untrackServiceType(ServiceType.AdminService, serviceStatusChangeHandler);
		int sinkId = 2;
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.DOWN,
				System.nanoTime());
		tracker.onMessage(serviceStatus);
		
		// then
		verify(serviceStatusChangeHandler, times(0)).handle(any());		
 	}
	
	@Test
	public void testIsAllUp(){
		tracker.trackServiceType(ServiceType.AdminService, serviceStatusChangeHandler);

		int sinkId = 2;
		ServiceStatus serviceStatus = ServiceStatus.of(senderSinkId, 
				sinkId,
				ServiceType.AdminService,
				ServiceStatusType.UP,
				System.nanoTime());
		tracker.onMessage(serviceStatus);

		// then
		assertTrue(tracker.isAllUp());
	}
}
