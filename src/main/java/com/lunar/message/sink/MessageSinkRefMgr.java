package com.lunar.message.sink;

import org.agrona.MutableDirectBuffer;

import com.lmax.disruptor.RingBuffer;
import com.lunar.core.MessageSinkRefList;
import com.lunar.message.io.sbe.ServiceType;

import io.aeron.Publication;

public interface MessageSinkRefMgr {
	public static final String NULL_MNEM = "null";
	public static final String NA_MNEM = "na";
	public MessageSinkRef omes();
	public MessageSinkRef otss();
	public MessageSinkRef perf();
	public MessageSinkRef risk();
	public MessageSinkRefList strats();
	public MessageSinkRef deadLetters();
	public MessageSinkRef persi();
	public MessageSinkRef warmup();
	public MessageSinkRef ns();
	public MessageSinkRef prc();
	public MessageSinkRef admin();
	public MessageSinkRef rds();
	public MessageSinkRef mds();
	public MessageSinkRef mdsss();
	public MessageSinkRef dashboard();
    public MessageSinkRef scoreboard();

	public MessageSinkRef register(MessageSinkRef sink);

	public int id();
	public int systemId();
	public int maxNumSinks();
	public MessageSinkRef get(int sinkId);
	public void deactivateBySystem(int systemId);
	public MessageSinkRef deactivate(MessageSinkRef sink);
		
	public MessageSinkRef createAndRegisterAeronMessageSink(int systemId, int sinkId, ServiceType serviceType, String name, Publication publication);
	public MessageSinkRef createAndRegisterRingBufferMessageSink(int sinkId, ServiceType serviceType, String name, RingBuffer<MutableDirectBuffer> ringBuffer);
	public MessageSinkRef[] localSinks();
	public MessageSinkRef[] remoteSinks();
	public MessageSinkRef[] remoteAdmins();
	public long volatileModifyTime();

}
