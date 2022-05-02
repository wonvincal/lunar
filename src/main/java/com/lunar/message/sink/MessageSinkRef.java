package com.lunar.message.sink;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.SinkInfo;
import com.lunar.message.io.sbe.ServiceType;

import org.agrona.DirectBuffer;

/**
 * Note: Equality check only looks at the underlying sinkId.  All
 * @author wongca
 *
 */
public final class MessageSinkRef {
	static final Logger LOG = LogManager.getLogger(MessageSinkRef.class);
	private final static AtomicInteger ID_SEQ = new AtomicInteger(); 
	public static MessageSinkRef NA_INSTANCE = MessageSinkRef.createNaSinkRef();

	private MessageSink messageSink;
	private final int id;
	private final String mnem;
	
	private MessageSinkRef(MessageSink messageSink, String mnem){
		this.messageSink = messageSink;
		this.id = ID_SEQ.getAndIncrement();
		this.mnem = mnem;
	}
	
	/**
	 * Thread safety: Yes
	 * 
	 * @param newSink
	 * @return
	 */
	public MessageSinkRef messageSink(MessageSink newSink){
		LOG.debug("Changed underlying message sink [mnem:{}, refId:{}, from:[{}], to:[{}]]", this.mnem, this.id, this.messageSink, newSink);
		if (this.messageSink.serviceType() != ServiceType.NULL_VAL &&
				this.messageSink.serviceType() != newSink.serviceType()){
			throw new IllegalArgumentException("Cannot replace an existing sink [" + this.sinkId() + "," + this.serviceType().name() + "] with another sink of different type: [" + newSink.serviceType().name() + "]");
		}
		this.messageSink = newSink;
		return this;
	}

	public int systemId(){
		return messageSink.systemId();
	}
	
	public int sinkId() {
		return messageSink.sinkId();
	}

	public String mnem(){
		return mnem;
	}
	
	public String name() {
		return messageSink.name();
	}

	public ServiceType serviceType() {
		return messageSink.serviceType();
	}

	public long publish(DirectBuffer buffer, int offset, int length){
		return messageSink.publish(buffer, offset, length);
	}

	public long tryPublish(DirectBuffer buffer, int offset, int length){
		return messageSink.tryPublish(buffer, offset, length);
	}

	public long tryClaim(int length, MessageSinkBufferClaim bufferClaim){
		return messageSink.tryClaim(length, bufferClaim);
	}
	
	public boolean commit(){
		return false;
	}

	public int backPressureIntensity() {
		return messageSink.backPressureIntensity();
	}

	public SinkInfo processingRate() {
		return messageSink.processingRate();
	}
	
	public MessageSink underlyingSink(){
		return messageSink;
	}

	public boolean disablePublishToNullSinkWarning(){
		if (messageSink instanceof ValidNullMessageSink){
			ValidNullMessageSink nullSink = (ValidNullMessageSink)messageSink;
			nullSink.suppressWarning(true);
			return true;
		}
		return false;
	}
	
	public boolean enablePublishToNullSinkWarning(){
		if (messageSink instanceof ValidNullMessageSink){
			ValidNullMessageSink nullSink = (ValidNullMessageSink)messageSink;
			nullSink.suppressWarning(false);
			return true;
		}
		return false;		
	}

	/**
	 * 
	 * @return
	 */
	public static MessageSinkRef createNullSinkRef(){
		return new MessageSinkRef(MessageSink.NULL_INSTANCE, MessageSinkRefMgr.NULL_MNEM);
	}
	
	/**
	 * "Not Applicable" MessageSinkRef
	 * 
	 * 
	 * @return
	 */
	public static MessageSinkRef createNaSinkRef(){
		return new MessageSinkRef(MessageSink.NA_INSTANCE, MessageSinkRefMgr.NA_MNEM);
	}

	public static MessageSinkRef createValidNullSinkRef(int systemId, ServiceType serviceType, String mnem){
		return new MessageSinkRef(ValidNullMessageSink.of(systemId, serviceType), mnem);
	}

	public static MessageSinkRef of(MessageSink sink){
		return new MessageSinkRef(sink, sink.name());
	}

	public static MessageSinkRef of(MessageSink sink, String mnem){
		return new MessageSinkRef(sink, mnem);
	}
	
	MessageSink sink(){
		return this.messageSink;
	}
	
	@Override
	public String toString() {
		return "[sinkName:" + this.name() + ", id:" + this.sinkId() + ", mnem:" + this.mnem + ", serviceType:" + this.serviceType() + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + sinkId();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MessageSinkRef other = (MessageSinkRef) obj;
		if (sinkId() != other.sinkId())
			return false;
		return true;
	}
	
	public static MessageSinkRef cloneWithSpecificSink(MessageSinkRef refSink, MessageSink specificSink){
		if (specificSink instanceof RingBufferMessageSink){
			RingBufferMessageSink rbSink = (RingBufferMessageSink)specificSink;
			RingBufferMessageSink newSink = new RingBufferMessageSink(refSink.systemId(), 
					refSink.sinkId(), 
					refSink.serviceType(), 
					refSink.name(), 
					rbSink.ringBuffer());
			return MessageSinkRef.of(newSink, refSink.mnem);
		}		
		if (specificSink instanceof AeronMessageSink){
			AeronMessageSink aeronSink = (AeronMessageSink)specificSink;
			AeronMessageSink newSink = new AeronMessageSink(refSink.systemId(), 
					refSink.sinkId(), 
					refSink.serviceType(), 
					aeronSink.publication());
			return MessageSinkRef.of(newSink, refSink.mnem);
		}
		if (specificSink instanceof ValidNullMessageSink){
			ValidNullMessageSink newSink = ValidNullMessageSink.of(refSink.systemId(), refSink.serviceType());
			return MessageSinkRef.of(newSink, refSink.mnem);
		}
		throw new UnsupportedOperationException("Not able to clone sink type of " + specificSink.getClass().getSimpleName());
	}
}
