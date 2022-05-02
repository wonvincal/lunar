package com.lunar.message.sink;

import java.util.Map;

import com.lunar.core.TimerService;

public class TestMessageSinkRefMgr extends NormalMessageSinkRefMgr {
	private final Map<Integer, MessageSink> messageSinksById;
	
	public TestMessageSinkRefMgr(int systemId,
			int capacity, 
			String aeronDirName, 
			String localAeronChannel,
			int localAeronStreamId, 
			int deadLettersSinkId,
			Map<Integer, MessageSink> messageSinksById, 
			TimerService timerService) {
		super(systemId, capacity, timerService);
		this.messageSinksById = messageSinksById;
		for (MessageSink sink : messageSinksById.values()){
			register(sink);
		}
	}
	
	@Override
	public MessageSinkRef register(MessageSinkRef sink){
		if (!messageSinksById.containsKey(sink.sinkId())){
			throw new IllegalStateException("please supply your own MessageSink into TestMessageSinkRefMgr");
		}
		return super.register(sink);
	}
}
