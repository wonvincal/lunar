package com.lunar.service;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EchoSbeDecoder;

public class ServiceUtil {
	private static final Logger LOG = LogManager.getLogger(ServiceUtil.class);
	public enum EchoResultType {
//		COMPLETED,/
//		SENT_RESPONSE,
//		SENT_NEXT,
//		MISSING_KEY,
//		CORRUPTED,
		OK_TO_SEND_RESPONSE,
		OK,
		INVALID_SEQ
	}

	public static EchoResultType handleEcho(final Frame message,
											final MessageCodec messageCodec,
											final int senderSvcId,
											final int selfSvcId,
											final int seq,
											final EchoSbeDecoder codec,
											long expected)
	{
		final BooleanType isResponse = codec.isResponse();
		if (isResponse == BooleanType.FALSE){
			// send an ECHO back with isResponse = true
			//LOG.debug("sent back event with remaining: {}", codec.remaining());
			return EchoResultType.OK_TO_SEND_RESPONSE;
		}
		if (expected != seq){
			LOG.error("invalid seq: expected:{}, actual:{}", expected, seq);
			return EchoResultType.INVALID_SEQ;
		}
		return EchoResultType.OK;
	}
	
/*	public static EchoResultType handleEcho(final Frame message,
								  final Frame newMessage,
								  final MessageCodec messageCodec,
								  final Int2ObjectMap<Frame> outstandingMsgs,
								  ActorRef sender,
								  ActorRef self,
								  int senderSvcId,
								  int selfSvcId,
								  EchoSbe codec,
								  long expected,
								  long expectedLastSeq){
		final BooleanType isResponse = codec.isResponse();
		final int key = codec.key();
		if (isResponse == BooleanType.FALSE){
			// send an ECHO back with isResponse = true
			// final Message newMessage = messageCodec.createMessage();
			//LOG.debug("sent back event with remaining: {}", codec.remaining());
			Frame m = messageCodec.createMessage();
//			LOG.debug("received and sending back start:{}", codec.startTime());
			messageCodec.encodeEcho(m, selfSvcId, senderSvcId, key, codec.seq(), codec.startTime(), BooleanType.TRUE);
			sender.tell(m, self);
			return EchoResultType.SENT_RESPONSE;
		}
		// initiate next echo if required
		if (!outstandingMsgs.containsKey(key)){
			return EchoResultType.MISSING_KEY;
		}
		//LOG.debug(messageCodec.dump(message));
		int seq = codec.seq();
		if (expected != seq && seq != -1){
			return EchoResultType.CORRUPTED;
		}

		if (seq < 0){
			// no need to generate a new msgKey
			// final Message newMessage = messageCodec.createMessage();
			messageCodec.encodeEcho(newMessage, selfSvcId, senderSvcId, key, remaining, timerService.nanoTime(), BooleanType.FALSE);
			sender.tell(newMessage, self);
			return EchoResultType.SENT_NEXT;
		}
		outstandingMsgs.remove(key);
		return EchoResultType.COMPLETED;
	}

	public static EchoResultType receiveEchoResponse(final Frame message,
			final Frame newMessage,
			final MessageCodec messageCodec,
			final Int2ObjectMap<Frame> outstandingMsgs,
			ActorRef sender, 
			ActorRef self,
			int senderSvcId,
			int selfSvcId,
			EchoSbe codec,
			long expected,
			long[] totalLatency){
		final BooleanType isResponse = codec.isResponse();
		final int key = codec.key();
		if (isResponse != BooleanType.TRUE){
			return EchoResultType.CORRUPTED;
		}
		// initiate next echo if required
		if (!outstandingMsgs.containsKey(key)){
			return EchoResultType.MISSING_KEY;
		}
		int remaining = codec.remaining();
		if (expected != remaining){
			LOG.debug("expected {}, remaining {}", expected, remaining);
			return EchoResultType.CORRUPTED;
		}
		long currentTime = timerService.nanoTime();
		long latency = (currentTime - codec.startTime());
		totalLatency[0] += latency;
//		LOG.debug("total:{}, current:{}, start:{}", totalLatency[0], currentTime, codec.startTime());

		if (latency < 0){
			LOG.debug("total:{}, current:{}, start:{}", totalLatency[0], currentTime, codec.startTime());
			throw new RuntimeException("negative latency...");
		}
		return EchoResultType.COMPLETED;
	}

	public static EchoResultType handleEcho(final Frame message,
			final Frame newMessage,
			final MessageCodec messageCodec,
			RingBuffer<Frame> senderBuffer,
			RingBuffer<Frame> selfBuffer,
			int senderSvcId,
			int selfSvcId,
			EchoSbe codec,
			long expected){
		final BooleanType isResponse = codec.isResponse();
		final int key = codec.key();
		if (isResponse == BooleanType.FALSE){
			// send an ECHO back with isResponse = true
			// final Message newMessage = messageCodec.createMessage();
			//LOG.debug("sent back event with remaining: {}", codec.remaining());
			Frame m = messageCodec.createMessage();
			//LOG.debug("received and sending back start:{}", codec.startTime());
			messageCodec.encodeEcho(m, selfSvcId, senderSvcId, key, codec.remaining(), codec.startTime(), BooleanType.TRUE);
			senderBuffer.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, m);
			return EchoResultType.SENT_RESPONSE;
		}
		//LOG.debug(messageCodec.dump(message));
		int remaining = codec.remaining();
		if (expected != remaining){
			return EchoResultType.CORRUPTED;
		}

		remaining--;
		if (remaining > 0){
			// no need to generate a new msgKey
			// final Message newMessage = messageCodec.createMessage();
			messageCodec.encodeEcho(newMessage, selfSvcId, senderSvcId, key, remaining, timerService.nanoTime(), BooleanType.FALSE);
			senderBuffer.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, newMessage);
			return EchoResultType.SENT_NEXT;
		}
		return EchoResultType.COMPLETED;
	}

	public static EchoResultType receiveEchoResponse(final Frame message,
			final Frame newMessage,
			final MessageCodec messageCodec,
			RingBuffer<Frame> senderBuffer,
			RingBuffer<Frame> selfBuffer,
			int senderSvcId,
			int selfSvcId,
			EchoSbe codec,
			long expected,
			long[] totalLatency){
		final BooleanType isResponse = codec.isResponse();
		final int key = codec.key();
		if (isResponse != BooleanType.TRUE){
			return EchoResultType.CORRUPTED;
		}
		int remaining = codec.remaining();
		if (expected != remaining){
			LOG.debug("expected {}, remaining {}", expected, remaining);
			return EchoResultType.CORRUPTED;
		}
		long currentTime = timerService.nanoTime();
		long latency = (currentTime - codec.startTime());
		totalLatency[0] += latency;
//		LOG.debug("total:{}, current:{}, start:{}", totalLatency[0], currentTime, codec.startTime());

		if (latency < 0){
			LOG.debug("total:{}, current:{}, start:{}", totalLatency[0], currentTime, codec.startTime());
			throw new RuntimeException("negative latency...");
		}
		return EchoResultType.COMPLETED;
	}*/
}
