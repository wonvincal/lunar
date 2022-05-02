package com.lunar.message.sink;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventPoller.Handler;
import com.lmax.disruptor.EventPoller.PollState;

import org.agrona.MutableDirectBuffer;

public class RingBufferMessageSinkPoller {
	private final RingBufferMessageSink sink;
	private final EventPoller<MutableDirectBuffer> poller;
	private Handler<MutableDirectBuffer> handler;
	
	public static RingBufferMessageSinkPoller of(RingBufferMessageSink sink){
		return new RingBufferMessageSinkPoller(sink);
	}

	RingBufferMessageSinkPoller(RingBufferMessageSink sink){
		this.sink = sink;
		this.poller = this.sink.ringBuffer().newPoller();
	}
	
	public RingBufferMessageSinkPoller handler(Handler<MutableDirectBuffer> handler){
		this.handler = handler;
		return this;
	}
	
	public boolean poll(){
		if (this.handler == null){
			throw new IllegalStateException("Handler has not been yet.  Either set the handler first, or pass a handler while calling poll().");
		}
		return poll(this.handler);
	}
	
	public void pollAll(){
		while (poll()){};
	}
	
	public boolean poll(Handler<MutableDirectBuffer> handler){
		PollState pollState = null;
		try {
			pollState = poller.poll(handler);
		} 
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (pollState == null || pollState != PollState.PROCESSING){
			return false;
		}
		return true;
	}
	
	public RingBufferMessageSink sink(){
		return sink;
	}
	
	
}
