package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.Exchange;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.ExchangeSbeDecoder;
import com.lunar.message.io.sbe.ExchangeSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExchangeSenderTest {
	static final Logger LOG = LogManager.getLogger(ExchangeSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final ExchangeSbeEncoder sbeEncoder = new ExchangeSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
												refSenderSink);
		final int refSeq = sender.peekNextSeq();
		final long refSid = 12345;
		final String refName = "Exch";
//		final String refName = "Hong Kong Stock Exch";
		final String refCode = "SEHK";
		final int refLastUpdateSeq = 5;
		
		final MessageSinkRef refMdsSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.MarketDataService, "test-mds");
		Exchange exchange = Exchange.of(refSid, refName, refCode, refMdsSink);
		exchange.lastUpdateSeq(refLastUpdateSeq);
		ExchangeSender.encodeExchange(sender, 
				refDstSinkId, 
				buffer, 
				0, 
				sbeEncoder, 
				exchange);
				
		MessageReceiver receiver = MessageReceiver.of();
		receiver.exchangeHandlerList().add(new Handler<ExchangeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   ExchangeSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refLastUpdateSeq, codec.lastUpdateSeq());
				assertEquals(refMdsSink.sinkId(), codec.mdsSinkId());
				assertEquals(refSid, codec.sid());
				assertEquals(refDstSinkId, header.dstSinkId());
				
				ByteBuffer b = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
				codec.getCode(b.array(), 0);
				String code = new String(b.array(), 0, ExchangeSbeDecoder.codeLength()).trim();
				assertEquals(refCode.substring(0, ExchangeSbeDecoder.codeLength()).trim(), code);

				codec.getName(b.array(), 0);
				String name = new String(b.array(), 0, ExchangeSbeDecoder.nameLength()).trim();
				assertEquals((refName.length() > ExchangeSbeDecoder.nameLength()) ? refName.substring(0, ExchangeSbeDecoder.nameLength()).trim() : refName, name);
			}
		});
		receiver.receive(buffer, 0);
	}
}
