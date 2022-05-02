package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.CommandAck;
import com.lunar.message.Message;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;


public final class CommandAckDecoder implements Decoder {
	private final CommandAckSbeDecoder sbe = new CommandAckSbeDecoder();
	private final HandlerList<CommandAckSbeDecoder> handlerList;

	static final Handler<CommandAckSbeDecoder> NULL_HANDLER = new Handler<CommandAckSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder codec) {
			LOG.warn("Message sent to command ack null handler [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	HandlerList<CommandAckSbeDecoder> handlerList(){
		return handlerList;
	}
	
	public static CommandAckDecoder of(){
		return new CommandAckDecoder(new HandlerList<CommandAckSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, NULL_HANDLER));
	}
	
	CommandAckDecoder(Handler<CommandAckSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<CommandAckSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}
	
	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}
	
	@Override
	public Message decodeAsMessage(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return new CommandAck(header.senderSinkId(), sbe.clientKey(), sbe.ackType(), sbe.commandType());
	}

	public static String decodeToString(CommandAckSbeDecoder sbe){
		return String.format("commandSeq:%d, ackType:%s, commandType:%s",
				sbe.clientKey(),
				sbe.ackType().name(),
				sbe.commandType().name());
	}
}
