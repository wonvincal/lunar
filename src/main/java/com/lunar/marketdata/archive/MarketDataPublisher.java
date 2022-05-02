package com.lunar.marketdata.archive;

import com.lunar.message.binary.MessageCodec;

import org.agrona.concurrent.UnsafeBuffer;

public interface MarketDataPublisher {
	boolean publish(UnsafeBuffer buffer, int offset, int length, MessageCodec codec);
	boolean publish(long seq, int msgCount, int msgOffset, UnsafeBuffer buffer, int offset, int length, MessageCodec codec);
}
