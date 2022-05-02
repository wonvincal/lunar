package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.RiskControlSetting;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder.ControlsDecoder;
import com.lunar.message.io.sbe.RiskStateSbeEncoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.position.RiskState;
import com.lunar.position.RiskStateWrapper;
import com.lunar.service.ServiceConstant;
import com.lunar.util.AssertUtil;

public class RiskStateSenderTest {
	static final Logger LOG = LogManager.getLogger(RequestSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final RiskStateSbeEncoder sbeEncoder = new RiskStateSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}

	@Test
	public void testEncodeMessageAndDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(123);
		
		long entitySid = 2121121l;
		EntityType entityType = EntityType.FIRM;
		Optional<Long> maxOpenPosition = Optional.of(500l);
		Optional<Double> maxProfit = Optional.of(2000.00d);
		Optional<Double> maxLoss = Optional.of(-1000.00d);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(entityType, maxOpenPosition, maxProfit, maxLoss, maxCapLimit); 
		RiskState riskState = RiskState.of(entitySid, entityType, setting);
				
		RiskStateSender.encodeRiskState(sender, refDstSinkId, buffer, 0, sbeEncoder, riskState);
		
		MessageReceiver receiver = MessageReceiver.of();
		receiver.riskStateHandlerList().add(new Handler<RiskStateSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(entityType, codec.entityType());
				assertEquals(entitySid, codec.entitySid());
				ControlsDecoder controls = codec.controls();
				assertEquals(4, controls.count());
				for (ControlsDecoder control : controls){
					switch (control.type()){
					case MAX_LOSS:
						AssertUtil.assertDouble(0, control.current(), "max loss");
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max loss previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					case MAX_PROFIT:
						AssertUtil.assertDouble(0, control.current(), "max profit"); 
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max profit previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					case MAX_OPEN_POSITION:
						AssertUtil.assertDouble(0, control.current(), "max open position"); 
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max open position previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					case MAX_CAP_LIMIT:
						AssertUtil.assertDouble(0, control.current(), "max cap used");
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max cap used previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					default:
						throw new IllegalStateException();
					}
				}
			}
		});
		receiver.receive(buffer, 0);
	}

	@Test
	public void testEncodeMessageAndDecodeWithChanges(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(123);
		
		long entitySid = 2121121l;
		EntityType entityType = EntityType.FIRM;
		Optional<Long> maxOpenPosition = Optional.of(500l);
		Optional<Double> maxProfit = Optional.of(2000.00d);
		Optional<Double> maxLoss = Optional.of(-1000.00d);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(entityType, maxOpenPosition, maxProfit, maxLoss, maxCapLimit); 
		RiskState riskState = RiskState.of(entitySid, entityType, setting);
		RiskStateWrapper wrapper = new RiskStateWrapper(riskState);
		long openPosition = 250l;
		double totalPnl = 234567.89;
		double maxCapUsed = 999;
		wrapper.updateOpenPosition(RiskState.RESULT_EXCEEDED, openPosition);
		wrapper.updateMaxProfit(RiskState.RESULT_OK, totalPnl);
		wrapper.updateMaxCapUsed(RiskState.RESULT_OK, maxCapUsed);
		
		RiskStateSender.encodeRiskState(sender, refDstSinkId, buffer, 0, sbeEncoder, riskState);
		
		MessageReceiver receiver = MessageReceiver.of();
		receiver.riskStateHandlerList().add(new Handler<RiskStateSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(entityType, codec.entityType());
				assertEquals(entitySid, codec.entitySid());
				ControlsDecoder controls = codec.controls();
				assertEquals(4, controls.count());
				for (ControlsDecoder control : controls){
					switch (control.type()){
					case MAX_LOSS:
						AssertUtil.assertDouble(totalPnl, control.current(), "max loss");
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max loss previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					case MAX_PROFIT:
						AssertUtil.assertDouble(totalPnl, control.current(), "max profit"); 
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max profit previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					case MAX_OPEN_POSITION:
						AssertUtil.assertDouble(openPosition, control.current(), "max open position");
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max open position previous");
						assertTrue(control.exceeded().equals(BooleanType.TRUE));
						break;
					case MAX_CAP_LIMIT:
						AssertUtil.assertDouble(maxCapUsed, control.current(), "max cap limit");
						AssertUtil.assertDouble(Double.NaN, control.previous(), "max cap limit previous");
						assertTrue(control.exceeded().equals(BooleanType.FALSE));
						break;
					default:
						throw new IllegalStateException();
					}
				}
			}
		});
		receiver.receive(buffer, 0);
	}
	
}
