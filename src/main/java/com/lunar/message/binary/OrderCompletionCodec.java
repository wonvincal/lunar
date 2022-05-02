package com.lunar.message.binary;

/**
 * 
 * TODO There is a new of byte[] in dump.  Please think of a better way to do this.
 * @author Calvin
 *
 */
public class OrderCompletionCodec {
	/*
	static final Logger LOG = LogManager.getLogger(OrderCompletionCodec.class);
	private OrderCompletionSbe sbe;
	private Handler<OrderCompletionSbe> handler;
	private Decoder decoder;
	private HandlerList<OrderCompletionSbe> handlerList; // evil premature optimization

	OrderCompletionCodec(Handler<OrderCompletionSbe> nullHandler){
		this.sbe = new OrderCompletionSbe();
		this.decoder = new OrderCompletionDecoder();
		this.handlerList = new HandlerList<OrderCompletionSbe>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.handler = this.handlerList.nullHandler();
	}
	
	public static OrderCompletionCodec of(){
		return new OrderCompletionCodec(NULL_HANDLER);
	}
	
	public static OrderCompletionCodec of(Handler<OrderCompletionSbe> nullHandler){
		return new OrderCompletionCodec(nullHandler);
	}
	
	public OrderCompletionSbe sbe(){ return sbe;}

	public OrderCompletionCodec addHandler(Handler<OrderCompletionSbe> newHandler){
		this.handler = this.handlerList.add(newHandler);
		return this;
	}
	
	public OrderCompletionCodec removeHandler(Handler<OrderCompletionSbe> remove){
		this.handler = this.handlerList.remove(remove);
		return this;
	}

	public Decoder decoder(){ return decoder;}
	
	public static String decodeToString(OrderCompletionSbe sbe){
		StringBuilder sb = new StringBuilder(
				String.format("ordSid:%d, status:%s, filledQuantity:%d, remainingQuantity:%d, avgPrice:%f",
		sbe.ordSid(),
		sbe.status().name(),
		sbe.filledQuantity(),
		sbe.remainingQuantity(),
		sbe.avgPrice()));
		return sb.toString();
	}
	private class OrderCompletionDecoder implements Decoder {
		@Override
		public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
			sbe.wrapForDecode(buffer, offset, blockLength, version);
			handler.handle(message, senderSinkId, dstSinkId, sbe);
		}
		@Override
		public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
			sbe.wrapForDecode(buffer, offset, blockLength, version);
			return decodeToString(sbe);
		}
		@Override
		public Message decodeAsMessage(Frame message, DirectBuffer buffer, int offset, int blockLength, int version, int templateId, int senderSinkId, int dstSinkId) {
			throw new UnsupportedOperationException();
		}		
	}

	public static Handler<OrderCompletionSbe> NULL_HANDLER = new Handler<OrderCompletionSbe>() {		
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoderOrderCompletionSbe codec) {}
	};

	Frame encodeOrder(final Frame message, MessageHeader header, int senderSinkId, int dstSinkId, 
						 int ordSid,
						 OrderStatus status,
						 int filledQuantity,
						 int remainingQuantity,
						 float avgPrice){
		int size = MessageCodec.encodeHeader(message.buffer(), 
				header, 
				OrderCompletionSbe.BLOCK_LENGTH,
				OrderCompletionSbe.TEMPLATE_ID,
				OrderCompletionSbe.SCHEMA_ID,
				OrderCompletionSbe.SCHEMA_VERSION,
				senderSinkId, 
				dstSinkId);
		sbe.wrapForEncode(message.buffer(), size)
				.ordSid(ordSid)
				.status(status)
				.filledQuantity(filledQuantity)
				.remainingQuantity(remainingQuantity)
				.avgPrice(avgPrice);
		return message;
	}
*/
}