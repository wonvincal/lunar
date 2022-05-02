package com.lunar.fsm.channelbuffer;

public interface ChannelMissingMessageRequester {
	void request(ChannelBufferContext context, int channelId, long expectedNextSeq, long fromSeq, long toSeq, int senderSinkId);
	
	public static ChannelMissingMessageRequester NULL_INSTANCE = new ChannelMissingMessageRequester() {
		@Override
		public void request(ChannelBufferContext context, int channelId, long expectedNextSeq, long fromSeq, long toSeq, int senderSinkId) {
			throw new UnsupportedOperationException();
		}
	};
}
