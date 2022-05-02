package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.entity.RiskControlSetting;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeEncoder;
import com.lunar.message.sender.RiskStateSender;
import com.lunar.position.RiskState;
import com.lunar.position.RiskStateWrapper;
import com.lunar.service.ServiceConstant;
import com.lunar.util.AssertUtil;

public class RiskStateFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(RequestFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final RiskStateSbeEncoder sbeEncoder = new RiskStateSbeEncoder();
	private final RiskStateSbeDecoder sbeDecoder = new RiskStateSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void testSbeToFbs(){
		// Given
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
		double maxCapUsed = 20000.01;
		wrapper.updateOpenPosition(RiskState.RESULT_EXCEEDED, openPosition);
		wrapper.updateMaxProfit(RiskState.RESULT_OK, totalPnl);
		wrapper.updateMaxCapUsed(RiskState.RESULT_EXCEEDED, maxCapUsed);

		RiskStateSender.encodeRiskStateOnly(buffer, 0, sbeEncoder, riskState);
		sbeDecoder.wrap(buffer, 0, RiskStateSbeDecoder.BLOCK_LENGTH, RiskStateSbeDecoder.SCHEMA_VERSION);

		// When
		int offset = RiskStateFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);

		RiskStateFbs riskStateFbs = RiskStateFbs.getRootAsRiskStateFbs(builder.dataBuffer());
		assertEquals(riskState.entitySid(), riskStateFbs.entitySid());
		assertEquals(riskState.entityType().value(), riskStateFbs.entityType());
		int settingCount = riskStateFbs.detailsLength();
		assertEquals(4, settingCount);
		for (int i = 0; i < settingCount; i++){
			RiskStateDetailsFbs details = riskStateFbs.details(i);
			if (details.type() == RiskControlTypeFbs.MAX_LOSS){
				AssertUtil.assertDouble(totalPnl, details.current(), "max loss");
			}
			else if (details.type() == RiskControlTypeFbs.MAX_PROFIT){
				AssertUtil.assertDouble(totalPnl, details.current(), "max profit");
			}
			else if (details.type() == RiskControlTypeFbs.MAX_OPEN_POSITION){
				AssertUtil.assertDouble(openPosition, details.current(), "max open position");				
			}
			else if (details.type() == RiskControlTypeFbs.MAX_CAP_LIMIT){
				AssertUtil.assertDouble(maxCapUsed, details.current(), "max cap limit");				
			}
		}
	}
}
