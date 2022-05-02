package com.lunar.message.binary;

public class OrderStatusCodecTest {
	/*
	@Test
	public void testEncodeAndDecodeOrderStatus(){
		final int expectedSenderSinkId = 100;
		final int expectedDstSinkId = 64;
		final int origSeq = 1001001;
		final int ordSid = 2002002;
		final OrderStatus status = OrderStatus.CANCELLED;
		final int filledQuantity = 1000;
		final int remainingQuantity = 10000;
		final int lastPrice = 123123;

		OrderStatusCodec orderStatusCodec = OrderStatusCodec.of();
		MessageHeader messageHeader = new MessageHeader();
		Frame frame = new Frame(true, messageHeader.size() + OrderStatusSbe.BLOCK_LENGTH);
		
		orderStatusCodec.encodeOrderStatus(frame, 
										   messageHeader, 
										   expectedSenderSinkId, 
										   expectedDstSinkId, 
										   origSeq, 
										   ordSid, 
										   status, 
										   filledQuantity, 
										   remainingQuantity, 
										   lastPrice);
		orderStatusCodec.addHandler(new Handler<OrderStatusSbe>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoderOrderStatusSbe codec) {
				assertEquals(origSeq, codec.origSeq());
				assertEquals(ordSid, codec.ordSid());
				assertEquals(status, codec.status());
				assertEquals(filledQuantity, codec.filledQuantity());
				assertEquals(remainingQuantity, codec.remainingQuantity());
				assertEquals(lastPrice, codec.lastPrice());
			}
		});
		CodecUtil.decode(messageHeader, frame, orderStatusCodec.decoder());
	}
	*/
}
