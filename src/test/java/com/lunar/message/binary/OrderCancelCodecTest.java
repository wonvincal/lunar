package com.lunar.message.binary;

public class OrderCancelCodecTest {
	/*
	@Test
	public void testEncodeAndDecodeOrderCancel(){
		final int expectedSenderSinkId = 100;
		final int expectedDstSinkId = 64;
		final int seq = 1001001;
		final int ordSid = 2002002;

		OrderCancelCodec orderCancelCodec = OrderCancelCodec.of();
		MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
		Frame frame = new Frame(true, messageHeader.encodedLength() + OrderCancelSbeDecoder.BLOCK_LENGTH);
		
		orderCancelCodec.encodeOrder(frame, messageHeader, expectedSenderSinkId, expectedDstSinkId, seq, ordSid);
		orderCancelCodec.addHandler(new Handler<OrderCancelSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoderOrderCancelSbeDecoder codec) {
				assertEquals(seq, codec.seq());
				assertEquals(ordSid, codec.ordSid());
			}
		});
		CodecUtil.decode(messageHeader, frame, orderCancelCodec.decoder());
	}
	*/
}
