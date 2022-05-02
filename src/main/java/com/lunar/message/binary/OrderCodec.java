package com.lunar.message.binary;

/**
 * 
 * TODO There is a new of byte[] in dump.  Please think of a better way to do this.
 * @author Calvin
 *
 */
public class OrderCodec {
	/*
	static final Logger LOG = LogManager.getLogger(OrderCodec.class);
	private OrderSbe sbe;
	private Handler<OrderSbe> handler;
	private Decoder decoder;
	private HandlerList<OrderSbe> handlerList; // evil premature optimization

	OrderCodec(Handler<OrderSbe> nullHandler){
		this.sbe = new OrderSbe();
		this.decoder = new OrderDecoder();
		this.handlerList = new HandlerList<OrderSbe>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.handler = this.handlerList.nullHandler();
	}
	
	public static OrderCodec of(){
		return new OrderCodec(NULL_HANDLER);
	}
	
	public static OrderCodec of(Handler<OrderSbe> nullHandler){
		return new OrderCodec(nullHandler);
	}
	
	public OrderSbe sbe(){ return sbe;}

	public OrderCodec addHandler(Handler<OrderSbe> newHandler){
		this.handler = this.handlerList.add(newHandler);
		return this;
	}
	
	public OrderCodec removeHandler(Handler<OrderSbe> remove){
		this.handler = this.handlerList.remove(remove);
		return this;
	}

	public Decoder decoder(){ return decoder;}
	
	public static String decodeToString(OrderSbe sbe){
		StringBuilder sb = new StringBuilder(
				String.format("seq:%d, secSid:%d, orderType:%s, quantity:%d, side:%s, createTime:%d, isAlgoOrder:%s, limitTickLevel:%d",
		sbe.seq(),
		sbe.secSid(),
		sbe.orderType().name(),
		sbe.quantity(),
		sbe.side().name(),
		sbe.createTime(),
		sbe.isAlgoOrder().name(),
		sbe.limitTickLevel()));
		return sb.toString();
	}
	private class OrderDecoder implements Decoder {
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

	public static Handler<OrderSbe> NULL_HANDLER = new Handler<OrderSbe>() {		
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoderOrderSbe codec) {}
	};

	Frame encodeOrder(final Frame message, MessageHeader header, int senderSinkId, int dstSinkId, 
						 int seq, 
						 long secSid, 
						 OrderType orderType){
		int size = MessageCodec.encodeHeader(message.buffer(), 
				header, 
				OrderSbe.BLOCK_LENGTH,
				OrderSbe.TEMPLATE_ID,
				OrderSbe.SCHEMA_ID,
				OrderSbe.SCHEMA_VERSION,
				senderSinkId, 
				dstSinkId);
		sbe.wrapForEncode(message.buffer(), size)
				.secSid(secSid)
				.orderType(orderType);
		return message;
	}
*/
}