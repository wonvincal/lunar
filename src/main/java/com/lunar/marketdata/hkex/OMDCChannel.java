package com.lunar.marketdata.hkex;

import com.lmax.disruptor.RingBuffer;
import com.lunar.marketdata.archive.MarketDataChannelArbitrator;
import com.lunar.marketdata.archive.MarketDataPublisher;
import com.lunar.message.binary.MessageCodec;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * Arbitrator reference implementation can be found in {@link MarketDataChannelArbitrator}
 * @author wongca
 *
 */
@SuppressWarnings("unused")
public class OMDCChannel implements Runnable {
	private final int channelId;
	private final String name;
	private final MarketDataPublisher publisher;
	private final MarketDataChannelArbitrator arbitrator;
	private final RingBuffer<RetransmissionCommand> retrans;
	
	/**
	 * TODO - create with a configuration file
	 * 
	 * Each channel needs to take:
	 * 1. publisher
	 * 2. retransmission
	 * 
	 * @param channelId
	 * @param name
	 * @return
	 */
	public static OMDCChannel of(int channelId, String name, MarketDataPublisher publisher, ChannelArbitratorBuilder builder, RingBuffer<RetransmissionCommand> retrans){
		return new OMDCChannel(channelId, name, publisher, builder, retrans);
	}
	
	OMDCChannel(int channelId, String name /* multicast addresses */, MarketDataPublisher publisher, ChannelArbitratorBuilder builder, RingBuffer<RetransmissionCommand> retrans){
		this.channelId = channelId;
		this.name = name;
		this.publisher = publisher;
		this.arbitrator = builder.build(publisher, this::handleGapDetected);
		this.retrans = retrans;
	}
	
	public int channelId(){
		return channelId;
	}

	public String name(){
		return name;
	}
	
	@Override
	public void run(){
		// when running
		// receive a message
		
		// received in sequence messages
		// call publisher
	}
	
	private void handleGapDetected(int channelId, long beginSeqNum, long endSeqNum){
		// send a command to ring buffer
		// retrans.publishEvents(translator, arg0, arg1);
	}
	
	public void publish(long fromSeq, int msgCount, UnsafeBuffer srcBuffer, int offset, int length, MessageCodec codec){
		this.arbitrator.publish(fromSeq, msgCount, srcBuffer, offset, length, codec);
	}
}
