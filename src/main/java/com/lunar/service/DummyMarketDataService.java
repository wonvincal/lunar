package com.lunar.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.config.MarketDataServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder.EntryEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
public class DummyMarketDataService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(DummyMarketDataService.class);
	private final String name;
	private LunarService messageService;
	private final Messenger messenger;
	private Long2ObjectOpenHashMap<String> securities;
	private Object2ObjectOpenHashMap<String, Long2ObjectLinkedOpenHashMap<MessageSinkRef>> securitySubscriptions;

	public static DummyMarketDataService of(ServiceConfig config, LunarService messageService){
		return new DummyMarketDataService(config, messageService);
	}
	
	DummyMarketDataService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof MarketDataServiceConfig){
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a MarketDataServiceConfig config");
		}
		securities = new Long2ObjectOpenHashMap<String>();
		securitySubscriptions = new Object2ObjectOpenHashMap<String, Long2ObjectLinkedOpenHashMap<MessageSinkRef>>();
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::handleAggregatedServiceStatusChange);
		return StateTransitionEvent.WAIT;
	}
	
	void handleAggregatedServiceStatusChange(boolean status){
		if (status){
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		}
		else { // DOWN or INITIALIZING
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	@Override
	public StateTransitionEvent readyEnter() {
		messenger.receiver().securityHandlerList().add(this::handleSecurity);
		messenger.sendRequest(messenger.referenceManager().rds(),
				RequestType.GET,
				new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.EXCHANGE.value()), Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build(),
				null);
		return StateTransitionEvent.NULL;
	}

	@Override
	public StateTransitionEvent activeEnter() {
		// register commands
		messenger.receiver().requestHandlerList().add(this::handleRequest);
		return StateTransitionEvent.NULL;
	}
	
	@SuppressWarnings("unused")
	private MessageSinkRef subscriberSink;
	private void handleRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
		byte senderSinkId = header.senderSinkId();
		if (request.requestType() == RequestType.SUBSCRIBE){
			MessageSinkRef subscriberSink = messenger.referenceManager().get(senderSinkId);
			
			ParametersDecoder parameters = request.parameters();
			LOG.info("Received request from {}, {} parameter(s)", senderSinkId, parameters.count());
			if (parameters.count() != 1){
				throw new IllegalArgumentException("unexpected number of parameters");
			}
			parameters.next();
			if (parameters.parameterType() != ParameterType.SECURITY_SID){
				throw new IllegalArgumentException("unexpected parameter type: " + parameters.parameterType().name());
			}
			if (parameters.parameterValueLong() == ParametersDecoder.parameterValueLongNullValue()){
				throw new IllegalArgumentException("unexpected parameter null value");
			}
			long secSid = parameters.parameterValueLong();
			String secCode = securities.get(secSid);
			if (secCode == null) {
				throw new IllegalStateException("security sid is not known: " + secSid);
			}
			Long2ObjectLinkedOpenHashMap<MessageSinkRef> subscriptions = securitySubscriptions.get(secCode);
			if (subscriptions == null) {
				subscriptions = new Long2ObjectLinkedOpenHashMap<MessageSinkRef>();
				securitySubscriptions.put(secCode, subscriptions);
			}
			else if (subscriptions.containsKey(senderSinkId)) {
				throw new IllegalStateException("service has already been subscribed by " + subscriberSink.sinkId());
			}
			subscriptions.put(senderSinkId, subscriberSink);
			
			messenger.responseSender().sendResponseForRequest(subscriberSink, request, ResultType.OK);
			
			final int entryCount = 2;
			AggregateOrderBookUpdateSbeEncoder sbe = new AggregateOrderBookUpdateSbeEncoder();
			EntryEncoder entryEncoder = sbe.wrap(unsafeBuffer, 0)
				.secSid(secSid)
				.entryCount(entryCount);
			entryEncoder.next()
				.numOrders(1)
				.price(1)
				.priceLevel((byte)1)
				.quantity(1)
				.tickLevel(1);
			entryEncoder.next()
				.numOrders(2)
				.price(2)
				.priceLevel((byte)2)
				.quantity(2)
				.tickLevel(2);
		
			LOG.info("encoded {} entry, encoded length: {}", entryCount, sbe.encodedLength());
			
			final int count = 8192 * 10;
			for (int i = 0; i < count; i++){
				if (i % 2000 == 0){
					try {
						Thread.sleep(25);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				sendMarketData(subscriberSink, secSid);
			}
			return;
		}
		throw new UnsupportedOperationException("unexpected operation");
	}

	private MutableDirectBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
	private void sendMarketData(MessageSinkRef sink, long secSid){
		// warm-up
		
		// pretend that i am receiving from exchange in binary format
		// encode market data
		// wrap around
		AggregateOrderBookUpdateSbeDecoder decoder = new AggregateOrderBookUpdateSbeDecoder();
		messenger.marketDataSender().sendAggregateOrderBookUpdate(
				sink, 
				decoder.wrap(unsafeBuffer, 0, 
						AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH, 
						AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION));
	}
	@Override
	public void activeExit() {
		// unregister command
		messenger.receiver().requestHandlerList().remove(this::handleRequest);
	}

    private static final String DATA_PATH = "/home/shayan/pub/";		
	private static final String RAW_DATA_FILE = "dev-omdsubscriber.message.log.2015-10-02.99";
	
	@SuppressWarnings("unused")
	private void readMarketDataFile() throws IOException {
		final Path path = FileSystems.getDefault().getPath(DATA_PATH, RAW_DATA_FILE);
		final Charset charset = Charset.forName("UTF-8");
		final BufferedReader reader = Files.newBufferedReader(path, charset);
		String line = null;
		
		while ((line = reader.readLine()) != null) {
			line = line.replaceAll(" +", " ");
			final String[] fields = line.split(" ");
			if (!(fields[6].equals("OMDC_AGGREGATE_ORDER_BOOK_UPDATE") || fields[6].equals("OMDC_TRADE")))
				continue;
			
			final String scty = fields[7];
			final long instrumentSid = Long.parseLong(scty);

			Long2ObjectLinkedOpenHashMap<MessageSinkRef> subscriptions = securitySubscriptions.get(instrumentSid);
			if (subscriptions == null)
				continue;

			final String timeStr = fields[1].split("=")[1];
			final LocalTime time = LocalTime.parse(timeStr.replace(",", "."), DateTimeFormatter.ISO_LOCAL_TIME);
			final long timestamp = time.toNanoOfDay();
			if (fields[6].equals("OMDC_TRADE")) {
				final int price = (int)(Float.parseFloat(fields[9].split("=")[1]) * 1000.0f);
				final int quantity = Integer.parseInt(fields[10].split("=")[1]);
				final int type = Integer.parseInt(fields[11].split("=")[1]);
				if (type == 0) {
					//onTradeUpdate(mdHandler, instrumentSid, security, time, timestamp, side, price, quantity);
				}
			}
			else {
				final String updatesString = line.split("\\[")[2].trim();
				final String[] updates = updatesString.substring(0, updatesString.length() - 1).split(",");
				for (String update : updates) {
					final String[] updateFields = update.trim().split("( |@|#|/)");
					final String action = updateFields[0];
					if (action.equals("DROPPED")) {
					}
					else {
						final String side = updateFields[1];
						final int price = Integer.parseInt(updateFields[3]);
						final long quantity = Long.parseLong(updateFields[2]);
						final int numOrders = Integer.parseInt(updateFields[4]);
						final int priceLevel = Byte.parseByte(updateFields[5]);
						final int tickLevel = SpreadTableBuilder.get(SecurityType.WARRANT).priceToTick(price);
						
						if (action.equals("NEW")) {
						}
						else if (action.equals("CHANGE")) {
						}
						else if (action.equals("DELETE")) {
						}
					}
				}
				//onOrderUpdate(mdHandler, instrumentSid, security, time, timestamp);
			}
		}
	}
	
	private void handleSecurity(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security){
		LOG.info("Received target security {}", security.sid());
		byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
		security.getCode(bytes, 0);
		try {
			this.securities.put(security.sid(), new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim());
		} 
		catch (UnsupportedEncodingException e) {
			LOG.error("Caught exception", e);
			this.messageService.stateEvent(StateTransitionEvent.FAIL);
			return;
		}
		this.messageService.stateEvent(StateTransitionEvent.ACTIVATE);
	}

}
