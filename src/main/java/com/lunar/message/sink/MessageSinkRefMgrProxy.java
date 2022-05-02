package com.lunar.message.sink;

import java.util.Optional;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.RingBuffer;
import com.lunar.core.MessageSinkRefList;
import com.lunar.message.io.sbe.ServiceType;

import io.aeron.Publication;

public class MessageSinkRefMgrProxy implements MessageSinkRefMgr {
	private static final Logger LOG = LogManager.getLogger(MessageSinkRefMgrProxy.class);
	private final int selfSinkId;
	private final MessageSinkRefMgr normalRefMgr;
	private Optional<MessageSinkRefMgr> warmupRefMgr;
	private MessageSinkRefMgr currentRefMgr;

	public static MessageSinkRefMgrProxy of(int selfSinkId, MessageSinkRefMgr normalRefMgr){
		return new MessageSinkRefMgrProxy(selfSinkId, normalRefMgr);
	}
	
	MessageSinkRefMgrProxy(int selfSinkId, MessageSinkRefMgr normalRefMgr){
		this.selfSinkId = selfSinkId;
		this.normalRefMgr = normalRefMgr;
		this.warmupRefMgr = Optional.empty();
		this.currentRefMgr = normalRefMgr;
	}
	
	public MessageSinkRefMgr current(){
		return this.currentRefMgr;
	}
	
	public MessageSinkRefMgr useWarmupRefMgr(){
		LOG.info("Use warmup reference manager now");
		if (!warmupRefMgr.isPresent()){
			if (normalRefMgr instanceof NormalMessageSinkRefMgr){
				NormalMessageSinkRefMgr mgr = (NormalMessageSinkRefMgr)normalRefMgr;
				warmupRefMgr = Optional.of(mgr.createWarmupRefMgr(selfSinkId, normalRefMgr.warmup()));
			}
			else {
				throw new UnsupportedOperationException("Cannot create warmup ref manager with reference manager of type " + normalRefMgr.getClass().getSimpleName());
			}
		}
		this.currentRefMgr = this.warmupRefMgr.get();
		return this.currentRefMgr;
	}
	
	public MessageSinkRefMgr useNormalRefMgr(){
		LOG.info("Use normal reference manager now [selfSinkId:{}]", selfSinkId);
		this.currentRefMgr = this.normalRefMgr;
		return this.currentRefMgr;
	}

	@Override
	public MessageSinkRef omes() {
		return this.currentRefMgr.omes();
	}

	@Override
	public MessageSinkRef otss() {
		return this.currentRefMgr.otss();
	}

	@Override
	public MessageSinkRef perf() {
		return this.currentRefMgr.perf();
	}

	@Override
	public MessageSinkRef risk() {
		return this.currentRefMgr.risk();
	}

	@Override
	public MessageSinkRefList strats() {
		return this.currentRefMgr.strats();
	}

	@Override
	public MessageSinkRef deadLetters() {
		return this.currentRefMgr.deadLetters();
	}

	@Override
	public MessageSinkRef persi() {
		return this.currentRefMgr.persi();
	}

	@Override
	public MessageSinkRef warmup() {
		return this.currentRefMgr.warmup();
	}

	@Override
	public MessageSinkRef ns() {
		return this.currentRefMgr.ns();
	}

	@Override
	public MessageSinkRef prc() {
		return this.currentRefMgr.prc();
	}

	@Override
	public MessageSinkRef admin() {
		return this.currentRefMgr.admin();
	}

	@Override
	public MessageSinkRef rds() {
		return this.currentRefMgr.rds();
	}

	@Override
	public MessageSinkRef mds() {
		return this.currentRefMgr.mds();
	}

	@Override
	public MessageSinkRef mdsss() {
		return this.currentRefMgr.mdsss();
	}

	@Override
	public MessageSinkRef dashboard() {
		return this.currentRefMgr.dashboard();
	}

    @Override
    public MessageSinkRef scoreboard() {
        return this.currentRefMgr.scoreboard();
    }
	
	@Override
	public MessageSinkRef register(MessageSinkRef sink) {
		return this.currentRefMgr.register(sink);
	}

	@Override
	public int systemId() {
		return this.currentRefMgr.systemId();
	}

	@Override
	public int maxNumSinks() {
		return this.currentRefMgr.maxNumSinks();
	}

	@Override
	public MessageSinkRef get(int sinkId) {
		return this.currentRefMgr.get(sinkId);
	}

	@Override
	public void deactivateBySystem(int systemId) {
		this.currentRefMgr.deactivateBySystem(systemId);
	}

	@Override
	public MessageSinkRef deactivate(MessageSinkRef sink) {
		return this.currentRefMgr.deactivate(sink);
	}

	@Override
	public MessageSinkRef createAndRegisterAeronMessageSink(int systemId, int sinkId, ServiceType serviceType,
			String name, Publication publication) {
		return this.currentRefMgr.createAndRegisterAeronMessageSink(systemId, sinkId, serviceType, name, publication);
	}

	@Override
	public MessageSinkRef createAndRegisterRingBufferMessageSink(int sinkId, ServiceType serviceType, String name,
			RingBuffer<MutableDirectBuffer> ringBuffer) {
		return this.currentRefMgr.createAndRegisterRingBufferMessageSink(sinkId, serviceType, name, ringBuffer);
	}

	@Override
	public MessageSinkRef[] localSinks() {
		return this.currentRefMgr.localSinks();
	}

	@Override
	public MessageSinkRef[] remoteSinks() {
		return this.currentRefMgr.remoteSinks();
	}

	@Override
	public MessageSinkRef[] remoteAdmins() {
		return this.currentRefMgr.remoteAdmins();
	}

	@Override
	public long volatileModifyTime() {
		return currentRefMgr.volatileModifyTime();
	}

	@Override
	public int id() {
		return currentRefMgr.id();
	}
}
