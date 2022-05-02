package com.lunar.message.binary;

public class PingCodecTest {
/*
	@Test
	public void testEncodeAndDecodePing(){
		final int expectedSenderSinkId = 100;
		final int expectedDstSinkId = 64;
		final int seq = 1;
		final BooleanType isResponse = BooleanType.FALSE;
		final long timestamp = System.nanoTime();
		final int expectedNumTs = 0;

		PingEncoder pingCodec = PingEncoder.of();
		MessageHeaderEncoder messageHeader = new MessageHeaderEncoder();
		Frame frame = new Frame(true, messageHeader.encodedLength() + PingSbeEncoder.BLOCK_LENGTH + PingSbeEncoder.TimestampsEncoder.sbeHeaderSize() + PingSbeEncoder.TimestampsEncoder.sbeBlockLength() * expectedNumTs);
		pingCodec.encodePing(frame.buffer(), frame.offset(), messageHeader, expectedSenderSinkId, expectedDstSinkId, seq, isResponse, expectedSenderSinkId, timestamp);
		
		PingDecoder pingDecoder = PingDecoder.of();
		pingDecoder.handlerList().add(new Handler<PingSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder codec) {
				assertEquals(expectedSenderSinkId, senderSinkId);
				assertEquals(expectedDstSinkId, dstSinkId);
				assertEquals(seq, codec.seq());
				assertEquals(isResponse, codec.isResponse());
				
				TimestampsDecoder timestamps = codec.timestamps();
				assertEquals(expectedNumTs, timestamps.count());
//				timestamps.next();
//				assertEquals(timestamp, timestamps.timestamp());
//				assertEquals(expectedSenderSinkId, timestamps.sinkId());
			}
		});
		CodecUtil.decode(new MessageHeaderDecoder(), frame, pingDecoder);
	}
	
	@Test
	public void testEncodeAndDecodePingAppendingTimestamp(){
		final int expectedSenderSinkId = 1;
		final int expectedDstSinkId = 2;
		final int expectedMySinkId = 3;
		final int seq = 1;
		final BooleanType isResponse = BooleanType.FALSE;
		final long timestamp = System.nanoTime();
		final long timestampAtMySink = timestamp + 100; 
		final int expectedNumTs = 1;

		PingEncoder pingCodec = PingEncoder.of();
		MessageHeaderEncoder messageHeader = new MessageHeaderEncoder();
		Frame frame = new Frame(true, messageHeader.encodedLength() + PingSbeEncoder.BLOCK_LENGTH + PingSbeEncoder.TimestampsEncoder.sbeHeaderSize() + PingSbeEncoder.TimestampsEncoder.sbeBlockLength() * expectedNumTs);
		pingCodec.encodePing(frame.buffer(), frame.offset(), messageHeader, expectedSenderSinkId, expectedDstSinkId, seq, isResponse, expectedSenderSinkId, timestamp);
		pingCodec.encodePingAppendingTimestamp(frame.buffer(), frame.offset(), messageHeader, expectedSenderSinkId, expectedDstSinkId, isResponse, 0, expectedMySinkId, timestampAtMySink);
		
		PingDecoder pingDecoder = PingDecoder.of();
		pingDecoder.handlerList().add(new Handler<PingSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder codec) {
				assertEquals(expectedSenderSinkId, senderSinkId);
				assertEquals(expectedDstSinkId, dstSinkId);
				assertEquals(seq, codec.seq());
				assertEquals(isResponse, codec.isResponse());
				assertEquals(timestamp, codec.startTime());
				
				TimestampsDecoder timestamps = codec.timestamps();
				assertEquals(expectedNumTs, timestamps.count());
				timestamps.next();
				assertEquals(timestampAtMySink, timestamps.timestamp());
				assertEquals(expectedMySinkId, timestamps.sinkId());
			}
		});
		CodecUtil.decode(new MessageHeaderDecoder(), frame, pingDecoder);
	}
	*/
}
