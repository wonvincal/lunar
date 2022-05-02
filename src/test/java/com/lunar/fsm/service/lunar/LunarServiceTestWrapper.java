package com.lunar.fsm.service.lunar;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventPoller.PollState;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.MessengerWrapper;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceLifecycleAware;

import org.agrona.MutableDirectBuffer;

public class LunarServiceTestWrapper {
	private final LunarService messageService;
	private final RingBuffer<MutableDirectBuffer> ringBuffer;
	private final EventPoller<MutableDirectBuffer> poller;
	private final MessengerWrapper messengerWrapper;
	
	public LunarServiceTestWrapper(LunarService messageService, RingBuffer<MutableDirectBuffer> ringBuffer){
		this.messageService = messageService;
		this.ringBuffer = ringBuffer;
		this.poller = ringBuffer.newPoller();
		this.messengerWrapper = new MessengerWrapper(this.messageService.messenger);
	}
	public LunarService messageService(){
		return messageService;
	}
	public ServiceLifecycleAware coreService(){
		return messageService.service();
	}
	public RingBuffer<MutableDirectBuffer> ringBuffer(){
		return ringBuffer;
	}
	public MessengerWrapper messengerWrapper(){
		return messengerWrapper;
	}
	public Messenger messenger(){
		return messageService.messenger();
	}
	public MessageSinkRef sink(){
		return messageService.messenger().self();
	}
	public boolean pushNextMessage(){
		PollState pollState = null;
		try {
			pollState = poller.poll(new  EventPoller.Handler<MutableDirectBuffer>() {
				@Override
				public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
					messageService.onEvent(event, sequence, endOfBatch);
					return true;
				}
			});
		} 
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (pollState == null || pollState != PollState.PROCESSING){
			return false;
		}
		return true;
	}
}
