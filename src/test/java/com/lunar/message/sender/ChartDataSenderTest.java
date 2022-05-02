package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.lunar.entity.ChartData;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.ChartDataSbeDecoder.VolAtPricesDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;
import com.lunar.message.io.sbe.VolumeClusterType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.service.ServiceConstant;
import com.lunar.util.ServiceTestHelper;

public class ChartDataSenderTest {
	static final Logger LOG = LogManager.getLogger(ChartDataSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	
	private MessageSender sender;
	private ChartDataSender chartDataSender;
	private final int testSinkId = 1;
	private RingBufferMessageSinkPoller testSinkPoller;
	private MessageSinkRef testSinkRef;
	private Messenger testMessenger;

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.ClientService, "test-client");
		sender = MessageSender.of(ServiceConstant.MAX_MESSAGE_SIZE, refSenderSink);
		chartDataSender = ChartDataSender.of(sender);
		
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		testSinkPoller = testHelper.createRingBufferMessageSinkPoller(testSinkId, 
				ServiceType.DashboardService, 256, "testDashboard");
		RingBufferMessageSink sink = testSinkPoller.sink();
		testSinkRef = MessageSinkRef.of(sink, "testSink");
		testMessenger = testHelper.createMessenger(sink, "testSinkMessenger");
		testMessenger.registerEvents();
		testSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				testMessenger.receive(event, 0);
				return false;
			}
		});
	}
	
	@Test
	public void testEncodeMessageAndDecode(){
		final long secSid = 12345;
		final int vwap = 94100;
		final long dataTime =  testMessenger.timerService().toNanoOfDay();
		final int windowSizeInSec = 60;
		final int open = 94150;
		final int close = 94350;
		final int high = 94450;
		final int low = 94010;
		final int pointOfControl = 94190;
		final int valueAreaLow = 94180;
		final int valueAreaHigh = 94320;
		final VolumeClusterDirectionType volClusterDirType = VolumeClusterDirectionType.NEUTRAL;
		ChartData entity = ChartData.of(secSid, dataTime, windowSizeInSec, open, 
				close, high, low, pointOfControl, valueAreaLow, 
				valueAreaHigh, vwap, volClusterDirType);
		
		final int[] volAtPrices = new int[]{94180, 94190, 94200, 94210, 94220, 94230, 94240, 94250, 94260, 94270};
		final long[] bidQtyList = new long[]{23, 24, 25, 26, 27, 28, 29, 30, 31, 32};
		final long[] askQtyList = new long[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
		final boolean[] significantList = new boolean[]{false, true, false, true, false, true, false, true, false, true};
		final VolumeClusterType[] clusterTypeList = new VolumeClusterType[]{VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.NORMAL, VolumeClusterType.POC}; 
		
		for (int i = 0 ; i < volAtPrices.length; i++){
			entity.addAskQty(volAtPrices[i], askQtyList[i]);
			entity.addBidQty(volAtPrices[i], bidQtyList[i]);
			entity.significant(volAtPrices[i], significantList[i]);
			entity.volumeClusterType(volAtPrices[i], clusterTypeList[i]);
		}
		chartDataSender.sendChartData(testSinkRef, entity);
				
		testMessenger.receiver().chartDataHandlerList().add(new Handler<ChartDataSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ChartDataSbeDecoder codec) {
				assertEquals(secSid, codec.secSid());
				assertEquals(vwap, codec.vwap());
				assertEquals(dataTime, codec.dataTime());
				assertEquals(windowSizeInSec, codec.windowSizeInSec());
				assertEquals(open, codec.open());
				assertEquals(close, codec.close());
				assertEquals(high, codec.high());
				assertEquals(low, codec.low());
				assertEquals(valueAreaHigh, codec.valueAreaHigh());
				assertEquals(valueAreaLow, codec.valueAreaLow());
				assertEquals(volClusterDirType, codec.volClusterDirection());
				
				VolAtPricesDecoder volAtPricesDecoder = codec.volAtPrices();
				assertEquals(volAtPrices.length, volAtPricesDecoder.count());
				int i = 0;
				for (VolAtPricesDecoder volAtPrice : volAtPricesDecoder){
					assertEquals(volAtPrices[i], volAtPrice.price());
					assertEquals(bidQtyList[i], volAtPrice.bidQty());
					assertEquals(askQtyList[i], volAtPrice.askQty());
					assertEquals(significantList[i], volAtPrice.significant() == BooleanType.FALSE ? false : true);
					assertEquals(clusterTypeList[i], volAtPrice.volumeClusterType());
					i++;
				}
			}
		});
		testSinkPoller.pollAll();
	}
}

