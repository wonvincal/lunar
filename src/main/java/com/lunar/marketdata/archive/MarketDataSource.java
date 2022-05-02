package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.Messenger;
import com.lunar.message.sink.MessageSinkRef;

public final class MarketDataSource implements Runnable {
	private static final Logger LOG = LogManager.getLogger(MarketDataSource.class);

	@SuppressWarnings("unused")
	private final MessageSinkRef parent;
	@SuppressWarnings("unused")
	private final Messenger messenger;
	private final MarketDataInfoBitSet mdi;
	private final Publisher[] publishers;
	
	public MarketDataSource(MessageSinkRef mds, MarketDataInfoBitSet mdi, Messenger messenger){
		this.parent = mds;
		this.messenger = messenger;
		this.mdi = mdi;
		this.publishers = new Publisher[2];
		this.publishers[0] = Publisher.NULL_PUBLISHER;
		this.publishers[1] = new NormalPublisher();
	}

	/**
	 * TODO think of logics for handling sudden exception
	 */
	@Override
	public void run() {
		// create a binary message
		Thread thread = Thread.currentThread();
		try{
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			long updateTime = 0;
			while (!thread.isInterrupted()){
				updateTime = mdi.volatileUpdateTime();
				int id = 100;
				this.publishers[mdi.isSubscribed(id)].publish(buffer);
			}
			LOG.info("run stopped, last update time was {}", updateTime);
		}
		catch (Exception e)
		{
			LOG.error("buffer receive failed, exiting...", e);
		}
	}

	public MarketDataInfoBitSet mdi(){
		return mdi;
	}
	
	interface Publisher {
		void publish(ByteBuffer buffer);
		
		static Publisher NULL_PUBLISHER = new Publisher() {
			@Override
			public void publish(ByteBuffer buffer) {}
		};
	}
	
	public class NormalPublisher implements Publisher {

		@Override
		public void publish(ByteBuffer buffer) {
//			FrameEncoder encoder = new FrameEncoder() {
//				@Override
//				public void encode(int dstSinkId, MessageCodec threadSafeCodec, Frame frame) {
//				}
//			};
			//parent.publish(encoder, threadSafeCodec, threadSafeBuffer)
		}
	}
}
