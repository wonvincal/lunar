package com.lunar.core;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * A generic class that manages subscription and sequence number generation
 * @author Calvin
 *
 */
public class ChannelBasedSubscriptionManager {
	private static final Logger LOG = LogManager.getLogger(ChannelBasedSubscriptionManager.class);
	private final static long CHANNEL_SEQ_RANGE_SIZE = 1_000_000_000;
	private final int defaultChannelSubcriberCapacity;
	private final Int2ObjectOpenHashMap<SubscriptionChannel> channels;

	public static ChannelBasedSubscriptionManager of(int initialCapacity, float loadFactor, int defaultChannelSubcriberCapacity){
		return new ChannelBasedSubscriptionManager(initialCapacity, loadFactor, defaultChannelSubcriberCapacity);
	}
		
	ChannelBasedSubscriptionManager(int initialCapacity, float loadFactor, int defaultChannelSubcriberCapacity){
		this.channels = new Int2ObjectOpenHashMap<SubscriptionChannel>(initialCapacity, loadFactor);
		this.defaultChannelSubcriberCapacity = defaultChannelSubcriberCapacity;
	}
	
	public SubscriptionChannel subscribe(int channelId, MessageSinkRef sink){
		SubscriptionChannel channel = this.channels.get(channelId);
		if (channel == null){
			channel = SubscriptionChannel.of(channelId, (long)channelId * CHANNEL_SEQ_RANGE_SIZE, defaultChannelSubcriberCapacity);
			this.channels.put(channelId, channel);
		}
		channel.addSubscriber(sink);
		return channel;
	}
	
	public SubscriptionChannel unsubscribe(int channelId, MessageSinkRef sink){
		SubscriptionChannel channel = this.channels.get(channelId);
		if (channel == null){
			LOG.error("Attempt to unsubscribe a channel that has not exist [channelId:{}]", channelId);
			channel = SubscriptionChannel.of(channelId, (long)channelId * CHANNEL_SEQ_RANGE_SIZE, defaultChannelSubcriberCapacity);
			this.channels.put(channelId, channel);
			return channel;
		}
		channel.removeSubscriber(sink);
		return channel;
	}
	
}
