package com.lunar.message.binary;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.FragmentInitSbeDecoder;
import com.lunar.message.io.sbe.FragmentSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * TODO Update this class once we start using Sender Messenger ID
 * @author wongca
 *
 */
public class FragmentAssembler {
	private static final Logger LOG = LogManager.getLogger(FragmentAssembler.class);
	private static final int INVALID_SEQ = -1;
	
	static class FragmentBuffer {
		int offset;
		int fromSeq;
		int toSeq;
		MutableDirectBuffer payloadBuffer;
		FragmentBuffer(){
			this.offset = 0;
			this.fromSeq = INVALID_SEQ;
			this.toSeq = INVALID_SEQ;
			this.payloadBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		}
	}
	// One FragmentAssembler per MessageReceiver
	// One buffer per senderSinkId
	private final ObjectArrayList<FragmentBuffer> fragmentBufferBySenderSinkId;
	private final MessageReceiver messageReceiver;
	private FragmentExceptionHandler exceptionHandler;
	
	public static FragmentAssembler of(int numSinks, MessageReceiver messageReceiver){
		return new FragmentAssembler(numSinks, messageReceiver, FragmentExceptionHandler.NULL_HANDLER);
	}
	
	public static interface FragmentExceptionHandler {
		void handleDroppedFragments(FragmentBuffer fragmentBuffer);
		void handleDroppedFragment(FragmentSbeDecoder fragment);
		
		static FragmentExceptionHandler NULL_HANDLER = new FragmentExceptionHandler() {

			@Override
			public void handleDroppedFragment(FragmentSbeDecoder fragment) {
				LOG.error("Drop fragment [receivedSeq:{}]", fragment.fragmentSeq());
			}

			@Override
			public void handleDroppedFragments(FragmentBuffer fragmentBuffer) {
				LOG.error("Drop received fragment [fromSeq:{}, toSeq:{}]", fragmentBuffer.fromSeq, fragmentBuffer.toSeq);
			}
		};
	}
	
	FragmentAssembler(int numSinks, MessageReceiver messageReceiver, FragmentExceptionHandler exceptionHandler){
		this.fragmentBufferBySenderSinkId = new ObjectArrayList<>(numSinks);
		for (int i = 0; i <= numSinks; i++){
			this.fragmentBufferBySenderSinkId.add(new FragmentBuffer());			
		}
		this.messageReceiver = messageReceiver;
		this.messageReceiver.fragmentHandlerList().add(this::onEvent);
		this.messageReceiver.fragmentInitHandlerList().add(this::onEvent);
		this.exceptionHandler = exceptionHandler;
	}
	
	private void onEvent(DirectBuffer buffer, int offset, MessageHeaderDecoder header, FragmentSbeDecoder fragment){
		FragmentBuffer fragmentBuffer = fragmentBufferBySenderSinkId.get(header.senderSinkId());
		LOG.info("Received fragment [fragmentSeq:{}, isLast:{}, payloadLength:{}]", fragment.fragmentSeq(), fragment.isLast().name(), fragment.payloadLength());
		if (fragment.fragmentSeq() == fragmentBuffer.toSeq + 1){
			fragmentBuffer.toSeq = fragment.fragmentSeq();
			fragmentBuffer.offset += fragment.getPayload(fragmentBuffer.payloadBuffer, fragmentBuffer.offset, fragment.payloadLength());
			if (fragment.isLast() == BooleanType.TRUE){
				flush(fragmentBuffer);
			}
			return;
		}
		if (fragmentBuffer.offset != 0){
			this.exceptionHandler.handleDroppedFragments(fragmentBuffer);
			fragmentBuffer.offset = 0;
			this.exceptionHandler.handleDroppedFragment(fragment);
		}
		else{
			this.exceptionHandler.handleDroppedFragment(fragment);
		}
	}

	private void onEvent(DirectBuffer buffer, int offset, MessageHeaderDecoder header, FragmentInitSbeDecoder fragment){
		FragmentBuffer fragmentBuffer = fragmentBufferBySenderSinkId.get(header.senderSinkId());
		LOG.info("Received fragment init [fragmentSeq:{}, isLast:{}, payloadLength:{}]", fragment.fragmentSeq(), fragment.isLast().name(), fragment.payloadLength());

		// Drop previous messages
		if (fragmentBuffer.offset != 0){
			this.exceptionHandler.handleDroppedFragments(fragmentBuffer);
			fragmentBuffer.offset = 0;
		}
		fragmentBuffer.fromSeq = fragment.fragmentSeq();
		fragmentBuffer.toSeq = fragment.fragmentSeq();
		fragmentBuffer.offset += fragment.getPayload(fragmentBuffer.payloadBuffer, fragmentBuffer.offset, fragment.payloadLength());
		
		// The first fragment should never be the last fragment
		// TODO remove all related logics
		if (fragment.isLast() == BooleanType.TRUE){
			flush(fragmentBuffer);
		}
	}
	
	private void flush(FragmentBuffer fragmentBuffer){
		fragmentBuffer.offset = 0;
		messageReceiver.receive(fragmentBuffer.payloadBuffer, 0);		
	}
	
	public FragmentAssembler exceptionHandler(FragmentExceptionHandler exceptionHandler){
		this.exceptionHandler = exceptionHandler;
		return this;
	}
}
