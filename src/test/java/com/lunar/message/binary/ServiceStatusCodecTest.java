package com.lunar.message.binary;

public class ServiceStatusCodecTest {
	/*
	@Test
	public void testEncodeAndDecodeServiceStatus(){
		final int expectedSenderSinkId = 100;
		final int expectedDstSinkId = 64;
		final int seq = 1;
		final int sinkId = 3;
		final ServiceType serviceType = ServiceType.DashboardService;
		final ServiceStatusType statusType = ServiceStatusType.INITIALIZING;
		final long now = System.nanoTime();

		ServiceStatusSbeEncoder serviceStatusCodec = ServiceStatusSbeEncoder.of();
		MessageHeaderEncoder messageHeader = new MessageHeaderEncoder();
		Frame frame = new Frame(true, messageHeader.encodedLength() + ServiceStatusSbeEncoder.BLOCK_LENGTH);
		serviceStatusCodec.encodeServiceStatus(frame.buffer(), frame.offset(), messageHeader, expectedSenderSinkId, expectedDstSinkId, seq, 
												sinkId, 
												serviceType, 
												statusType, 
												now);
		ServiceStatusDecoder serviceStatusDecoder = ServiceStatusDecoder.of();
		serviceStatusDecoder.handlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(serviceType, codec.serviceType());
				assertEquals(statusType, codec.statusType());
				assertEquals(now, codec.modifyTimeAtOrigin());
			}
		});
		CodecUtil.decode(new MessageHeaderDecoder(), frame, serviceStatusDecoder);
	}
*/
}
