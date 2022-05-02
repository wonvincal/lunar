package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.PricingServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.MultiThreadEntitySubscriptionManager;
import com.lunar.core.SubscriberList;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.DividendCurveSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.pricing.DividendCurve;
import com.lunar.pricing.DummyPricer;
import com.lunar.pricing.Greeks;
import com.lunar.pricing.Pricer;
import com.lunar.pricing.QuantLibPricer;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.agrona.DirectBuffer;

/**
 * A general market data snapshot service that serves all snapshot needs (i.e. different exchanges)
 * It receives real-time updates from {@link MarketDataRealtimeService} and get order book 
 * snapshots from {@link MarketDataRefreshService} 
 * @author wongca
 *
 */
@SuppressWarnings("unused")
public class PricingService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(PricingService.class);
	
	private final LunarService messageService;
	private final Messenger messenger;
	private final String name;
    private final PricingServiceConfig specificConfig;
    private final LongEntityManager<SecurityWrapper> securities;
	private final Long2ObjectOpenHashMap<String> securitySymbolsMap;
	private MultiThreadEntitySubscriptionManager securitySubscriptionManager;
    private final Long2ObjectOpenHashMap<SecurityWrapper> underlyings;
    private final Long2ObjectOpenHashMap<SecurityWrapper> warrants;
    private final Long2ObjectOpenHashMap<DividendCurve> dividendCurves;
	private final Messenger pricingMessenger;
    private final Long2ObjectLinkedOpenHashMap<Greeks> greeksMap;

    private SubscriberList performanceSubscribers;
    private final ExecutorService pricingExecutor;
    private Pricer pricer;
    volatile private boolean canPrice; 
    volatile private boolean persistAll;
	
	public class SecurityWrapper extends Security {
		private volatile long bidAsk;
		private long prevBidAsk;
		private volatile boolean subscriptionsChanged;
		
		public SecurityWrapper(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid, Optional<LocalDate> maturity, LocalDate listedDate, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean isAlgo, SpreadTable spreadTable) {
			super(sid, secType, code, exchangeSid, undSecSid, maturity, listedDate, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, isAlgo, spreadTable);
		}
	}

    public static PricingService of(final ServiceConfig config, final LunarService messageService){
        return new PricingService(config, messageService);
    }
    
	public PricingService(final ServiceConfig config, final LunarService messageService) {
		this.name = config.name();
        pricingExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory(this.name, this.name + "-pricing"));
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.pricingMessenger = this.messenger.createChildMessenger();
		this.performanceSubscribers = SubscriberList.of(ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		this.persistAll = false;
        if (config instanceof PricingServiceConfig) {
            specificConfig = (PricingServiceConfig)config;
            securities = new LongEntityManager<SecurityWrapper>(specificConfig.numUnderlyings() + specificConfig.numWarrants());
            underlyings = new Long2ObjectOpenHashMap<SecurityWrapper>(specificConfig.numUnderlyings());
            warrants = new Long2ObjectOpenHashMap<SecurityWrapper>(specificConfig.numWarrants());
		    securitySymbolsMap = new Long2ObjectOpenHashMap<String>(specificConfig.numWarrants());
		    dividendCurves = new Long2ObjectOpenHashMap<DividendCurve>(specificConfig.numUnderlyings());
	        greeksMap = new Long2ObjectLinkedOpenHashMap<Greeks>(specificConfig.numWarrants());
        }
        else{
            throw new IllegalArgumentException("Service " + this.name + " expects a PricingServiceConfig config");
        }
        pricer = new QuantLibPricer(this.messageService.systemClock());
        //pricer = new DummyPricer();
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataSnapshotService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus((final boolean status) -> {
			if (status){
				messageService.stateEvent(StateTransitionEvent.READY);
			}
			else { // DOWN or INITIALIZING
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}	
		});
		return StateTransitionEvent.WAIT;
	}
	
	@Override
	public StateTransitionEvent idleRecover() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent warmupEnter() {
		return StateTransitionEvent.WAIT;
	};
	
	@Override
	public void warmupExit() {
	}

	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForServicesExit() {
	}

	@Override
	public StateTransitionEvent readyEnter() {
		securitySubscriptionManager = new MultiThreadEntitySubscriptionManager(this.securitySymbolsMap, ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		messenger.receiver().dividendCurveHandlerList().add(dividendCurveHandler);
        messenger.receiver().securityHandlerList().add(securityHandler);
        messenger.receiver().boobsHandlerList().add(boobsHandler);

        final CompletableFuture<Request> retrieveDividendFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                RequestType.GET,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.DIVIDEND_CURVE.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveDividendFuture.thenAccept((r) -> { 
            final CompletableFuture<Request> retrieveSecurityFuture = messenger.sendRequest(messenger.referenceManager().rds(),
                    RequestType.SUBSCRIBE,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                    ResponseHandler.NULL_HANDLER);
            retrieveSecurityFuture.thenAccept((r2) -> {
                messageService.stateEvent(StateTransitionEvent.ACTIVATE);
            });
        });        
        return StateTransitionEvent.NULL;	    
	}

	@Override
	public void readyExit() {
	}

	@Override
	public StateTransitionEvent activeEnter() {
		messenger.receiver().requestHandlerList().add(requestHandler);
		messenger.receiver().commandHandlerList().add(commandHandler);

		securitySubscriptionManager = new MultiThreadEntitySubscriptionManager(this.securitySymbolsMap, ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		canPrice = true;
        pricer.initialize();
        pricer.setYieldRate(0);
        final Long2ObjectOpenHashMap<SecurityWrapper> securitiesChanged = new Long2ObjectOpenHashMap<SecurityWrapper>();
        final Long2IntOpenHashMap divRegistered = new Long2IntOpenHashMap(); 
        final Object[] underlyingList = this.underlyings.values().toArray();
        final Object[] warrantList = this.warrants.values().toArray();
		pricingExecutor.submit(() -> {
		    while (canPrice) {
		        for (final Object securityObject : underlyingList) {
		            final SecurityWrapper security = (SecurityWrapper)securityObject;
		            if (!security.isAlgo())
		                continue;
		            final long bidAsk = security.bidAsk;		            
		            if (security.prevBidAsk != bidAsk) {
		                securitiesChanged.put(security.sid(), security);
		                security.prevBidAsk = bidAsk;
	                    if (!divRegistered.containsKey(security.sid())) {
	                        divRegistered.put(security.sid(), 1);
	                        DividendCurve dividendCurve = dividendCurves.get(security.sid());
	                        if (dividendCurve == null) {
	                        	dividendCurve = new DividendCurve(security.sid(), 0); 
	                        	dividendCurves.put(security.sid(), dividendCurve);
	                        }
	                        pricer.setDividendCurve(security.sid(), dividendCurves.get(security.sid()));
	                    }
		            }
		        }
		        for (final Object securityObject : warrantList) {
		            final SecurityWrapper security = (SecurityWrapper)securityObject;
                    if (!security.isAlgo())
                        continue;                    
                    final SecurityWrapper underlying = underlyings.get(security.underlyingSid());
                    final long undBidAsk = underlying.bidAsk;
                    final long bidAsk = security.bidAsk;
                    final long prevBidAsk = security.prevBidAsk;
                    
                    final int undPrice = getMidPrice(undBidAsk);
                    final int bid = (int)((bidAsk >> Integer.SIZE) & 0xFFFFFFFF);
                    final int ask = (int)(bidAsk & 0xFFFFFFFF);
                    if (undPrice == 0 || bid == 0 || ask == 0) {
                    	continue;
                    }
                    final int price = getMidPrice(bid, ask);
                    final int prevPrice = getMidPrice(prevBidAsk);
                    if (!divRegistered.containsKey(underlying.sid())) {
                        divRegistered.put(underlying.sid(), 1);
                        DividendCurve dividendCurve = dividendCurves.get(underlying.sid());
                        if (dividendCurve == null) {
                        	dividendCurve = new DividendCurve(underlying.sid(), 0); 
                        	dividendCurves.put(underlying.sid(), dividendCurve);
                        }
                        pricer.setDividendCurve(underlying.sid(), dividendCurves.get(underlying.sid()));
                    }
                    
                    if (security.subscriptionsChanged || prevPrice != price || securitiesChanged.containsKey(security.underlyingSid())) {
                    	final MessageSinkRef[] sinks = securitySubscriptionManager.getMessageSinks(security.sid());
                    	if (sinks != null) {
	                        Greeks greeks = greeksMap.get(security.sid());
	                        if (greeks == null) {
	                            greeks = Greeks.of(security.sid());
	                            greeksMap.put(security.sid(), greeks);
	                            pricer.registerEuroOption(security);
	                        }
                        	if (pricer.priceEuroOption(security, undPrice, price * security.convRatio() / 1000, bid * security.convRatio() / 1000, ask * security.convRatio() / 1000, greeks)) {
                        		pricingMessenger.pricingSender().sendGreeks(sinks, greeks);
                        	}
                            security.prevBidAsk = price;
                            security.subscriptionsChanged = false;                            
                        }
                    }
                }
		        securitiesChanged.clear();	
		        
		        if (persistAll) {
		            LOG.info("Persisting all greeks...");
		            persistAll(pricingMessenger);
		            persistAll = false;
		        }
		    }
		    
		});
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
	    canPrice = false;
	    pricingExecutor.shutdown();
	    pricer.close();
		messenger.receiver().boobsHandlerList().remove(boobsHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
        messenger.receiver().securityHandlerList().remove(securityHandler);
        messenger.receiver().dividendCurveHandlerList().remove(dividendCurveHandler);
		messenger.receiver().commandHandlerList().remove(commandHandler);
	}

	@Override
	public StateTransitionEvent stopEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stopExit() {
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
	}
	
	private Handler<DividendCurveSbeDecoder> dividendCurveHandler = new Handler<DividendCurveSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, DividendCurveSbeDecoder dividendCurve) {
            LOG.info("Received target dividend for security {}", dividendCurve.secSid());
            int limit = dividendCurve.limit();
            DividendCurveSbeDecoder.PointsDecoder points = dividendCurve.points();
            final DividendCurve curve = new DividendCurve(dividendCurve.secSid(), points.count());        
            for (DividendCurveSbeDecoder.PointsDecoder point : points) {
               curve.addPoint(point.date(), point.amount());                
            }        
            dividendCurve.limit(limit);
            dividendCurves.put(dividendCurve.secSid(), curve);            
        }
	};
	
	private Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
            LOG.info("Received target security {}", security.sid());
            if (security.isAlgo() == BooleanType.FALSE)
                return;     
            final byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
            security.getCode(bytes, 0);
            try {
                final SecurityWrapper newSecurity = new SecurityWrapper(security.sid(), 
                        security.securityType(), 
                        new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
                        security.exchangeSid(),
                        security.undSid(),
                        (security.maturity() == SecuritySbeDecoder.maturityNullValue()) ? 
                                Optional.empty() :
                                Optional.of(LocalDate.of(security.maturity() / 10000, security.maturity() % 10000 / 100, security.maturity() % 100)),
                        LocalDate.of(security.listingDate() / 10000, security.listingDate() % 10000 / 100, security.listingDate() % 100),
                        security.putOrCall(),
                        security.style(),
                        security.strikePrice(),
                        security.conversionRatio(),
                        security.issuerSid(),
                        security.lotSize(),
                        security.isAlgo() == BooleanType.TRUE,
                        SpreadTableBuilder.getById(security.spreadTableCode()));
                securities.add(newSecurity);
                securitySymbolsMap.put(newSecurity.sid(), newSecurity.code());
                if (newSecurity.securityType() == SecurityType.STOCK) {
                    underlyings.put(newSecurity.sid(),  newSecurity);
                }
                else if (newSecurity.securityType() == SecurityType.WARRANT && newSecurity.underlyingSid() != Security.INVALID_SECURITY_SID) {
                    warrants.put(newSecurity.sid(),  newSecurity);
                }
                messenger.sendRequest(messenger.referenceManager().mdsss(),
                        RequestType.SUBSCRIBE,
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.BOOBS.value()), Parameter.of(ParameterType.SECURITY_SID, security.sid())).build(),
                        new ResponseHandler() {
                            @Override
                            public void handleResponse(final DirectBuffer buffer, final int offset, final ResponseSbeDecoder response) {
                                LOG.info("Received market data subscription respones: {}", response.resultType());
                                if (response.resultType() != ResultType.OK){
                                    LOG.error("Cannot subscribe market data due to {}", response.resultData());
                                    messageService.stateEvent(StateTransitionEvent.FAIL);
                                }
                            }

                            @Override
                            public void handleResponse(final Response response) {
                                LOG.info("Received market data subscription respones: {}", response.resultType());
                                if (response.resultType() != ResultType.OK){
                                    LOG.error("Cannot subscribe market data due to some reasons");
                                    messageService.stateEvent(StateTransitionEvent.FAIL);
                                }
                            }
                });             
            } 
            catch (final UnsupportedEncodingException e) {
                LOG.error("Failed to decode SecuritySbe", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }   
        }
	};
	
	private Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            byte senderSinkId = header.senderSinkId();
            final MessageSinkRef sender = messenger.sinkRef(senderSinkId);
            try {
                LOG.info("Received request from sink[{}]", senderSinkId);
                handleRequest(sender, request);
            }
            catch (final Exception e) {
                LOG.error("Failed to handle request", e);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
            }
        }	    
	};

	private void handleRequest(final MessageSinkRef sender, final RequestSbeDecoder request) {
		// Send response
	    final ParametersDecoder parameters = request.parameters();
		if (parameters.count() < 1) {
			throw new IllegalArgumentException("Received request with insufficient parameters");
		}
		parameters.next();
		if (parameters.parameterType() != ParameterType.TEMPLATE_TYPE) {
			throw new IllegalArgumentException("Received request with first parameter not a TEMPLATE_TYPE");
		}
		final long templateType = parameters.parameterValueLong();
		if (templateType == TemplateType.GREEKS.value()) {
			parameters.next();
			if (parameters.parameterType() == ParameterType.SECURITY_SID) {
			    final long secSid = parameters.parameterValueLong();
			    final SecurityWrapper security = securities.get(secSid);
				if (security != null) {
					LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
					if (request.requestType() == RequestType.SUBSCRIBE) {
						LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
						final boolean result = securitySubscriptionManager.addMessageSink(secSid, sender);
						messenger.responseSender().sendResponseForRequest(sender, request, result ? ResultType.OK : ResultType.FAILED);
					}
					else if (request.requestType() == RequestType.UNSUBSCRIBE) {
						LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
						final boolean result = securitySubscriptionManager.removeMessageSink(secSid, sender);
						messenger.responseSender().sendResponseForRequest(sender, request, result ? ResultType.OK : ResultType.FAILED);
					}
					else {
						throw new IllegalArgumentException("No support for " + request.requestType() + " request for security");
					}
				}
				else {
					throw new IllegalArgumentException("Received " + request.requestType() + " request for security with invalid sid " + secSid);
				}
			}
			else {
				throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
			}
        }	
		else if (templateType == TemplateType.GENERIC_TRACKER.value()) {
            if (request.requestType() == RequestType.SUBSCRIBE) {
                this.performanceSubscribers.add(sender);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                this.performanceSubscribers.remove(sender);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
		}
		else {
			throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type");
		}
	}
	
	private Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
            try {
                handleCommand(header.senderSinkId(), codec);
            }
            catch (final Exception e) {
                LOG.error("Failed to handle command", e);
                messenger.sendCommandAck(header.senderSinkId(), codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            }
        }
	};
    
    private void persistAll(final Messenger messenger) {
        for (final Greeks greeks : greeksMap.values()) {
            messenger.pricingSender().sendGreeks(this.messenger.referenceManager().persi(), greeks);
        }
    }
    
    private void handleCommand(final int senderSinkId, final CommandSbeDecoder codec) throws Exception {
        switch (codec.commandType()){
        case SEND_ECHO: {
            LOG.info("Received SEND_ECHO command");
            this.persistAll = true;
            break;
        }
        default:
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            break;
        }
    }    
    
    private Handler<BoobsSbeDecoder> boobsHandler = new Handler<BoobsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, BoobsSbeDecoder boobs) {
            final SecurityWrapper security = securities.get(boobs.secSid());
            if (security != null) {
                final long bidAsk = (long)boobs.bid() << Integer.SIZE | (long)boobs.ask();
                security.bidAsk = (long)boobs.bid() << Integer.SIZE | (long)boobs.ask();
            }
        }
    };
    
    private int getMidPrice(final long bidAsk) {
        final int bid = (int)((bidAsk >> Integer.SIZE) & 0xFFFFFFFF);
        final int ask = (int)(bidAsk & 0xFFFFFFFF);
        return getMidPrice(bid, ask);
    }
    
    private int getMidPrice(final int bid, final int ask) {
        if (ask != 0) {
            return bid != 0 ? (bid + ask) / 2 : ask;
        }
        else {
            return bid;
        }
    }

}
