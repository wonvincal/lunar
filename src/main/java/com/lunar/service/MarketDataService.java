package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.config.MarketDataServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.MultiThreadEntitySubscriptionManager;
import com.lunar.core.MultiThreadSubscriberList;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.MarketDataSource;
import com.lunar.marketdata.ReplayMarketDataSource;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
abstract public class MarketDataService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(MarketDataService.class);
	private final String name;
	protected final LunarService messageService;
	protected final Messenger messenger;
	protected final Collection<Security> securities;
	protected final Long2ObjectOpenHashMap<Security> securitiesMap;
	protected final Long2ObjectOpenHashMap<String> exchangeSymbolsMap;
	protected MultiThreadEntitySubscriptionManager securitySubscriptionManager;
	protected MultiThreadEntitySubscriptionManager exchangeSubscriptionManager;
	protected MultiThreadSubscriberList performanceSubscriptions;
	protected MarketDataSource marketDataSource;
	protected final Optional<String> connectorFile;   
	protected final Optional<String> omdcConfigFile;
	protected final Optional<String> omddConfigFile;
	
	protected MarketDataService(final ServiceConfig config, final LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof MarketDataServiceConfig){
		    final MarketDataServiceConfig specificConfig = (MarketDataServiceConfig)config;
		    marketDataSource = ReplayMarketDataSource.instanceOf();
		    securities = new ArrayList<Security>(specificConfig.numSecurities());
		    securitiesMap = new Long2ObjectOpenHashMap<Security>(specificConfig.numSecurities());
		    //TODO shayan - should grap from reference data service
		    exchangeSymbolsMap = new Long2ObjectOpenHashMap<String>(5);
		    exchangeSymbolsMap.put(0, ServiceConstant.PRIMARY_EXCHANGE);
		    connectorFile = specificConfig.connectorFile();
		    omdcConfigFile = specificConfig.omdcConfigFile();
		    omddConfigFile = specificConfig.omddConfigFile();
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a MarketDataServiceConfig config");
		}
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
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
	public StateTransitionEvent readyEnter() {
		messenger.receiver().securityHandlerList().add(securityHandler);

		final CompletableFuture<Request> retrieveSecurityFuture = messenger.sendRequest(messenger.referenceManager().rds(),
				RequestType.SUBSCRIBE,
				new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
				ResponseHandler.NULL_HANDLER);
		retrieveSecurityFuture.thenAccept((r) -> { 
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		});
		return StateTransitionEvent.NULL;
	}

	@Override
	public void readyExit() {
	}
	
	abstract protected MarketDataSource createMarketDataSource();
	
	abstract protected void registerMarketDataSourceCallbacks();

	@Override
	public StateTransitionEvent activeEnter() {
		// register commands
		messenger.receiver().requestHandlerList().add(requestHandler);

		securitySubscriptionManager = MultiThreadEntitySubscriptionManager.create(this.securities, ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		exchangeSubscriptionManager = new MultiThreadEntitySubscriptionManager(this.exchangeSymbolsMap, ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		performanceSubscriptions = new MultiThreadSubscriberList(ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		marketDataSource = createMarketDataSource();
		registerMarketDataSourceCallbacks();
        marketDataSource.initialize(securities);
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public void activeExit() {
		marketDataSource.close();
		// unregister command		
		messenger.receiver().requestHandlerList().remove(requestHandler);
		messenger.receiver().securityHandlerList().remove(securityHandler);
	}

    final byte[] securityCodeBytes = new byte[SecuritySbeDecoder.codeLength()];
	private Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
            LOG.info("Received target security {}", security.sid());
            security.getCode(securityCodeBytes, 0);
            try {
                final SpreadTable spreadTable = SpreadTableBuilder.getById(security.spreadTableCode());
                final Security newSecurity = Security.of(security.sid(), 
                        security.securityType(), 
                        new String(securityCodeBytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
                        security.exchangeSid(), 
                        security.isAlgo() == BooleanType.TRUE,
                        spreadTable);
                securities.add(newSecurity);
                securitiesMap.put(newSecurity.sid(), newSecurity);
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
            LOG.info("Received request from sink[{}]", senderSinkId);
            final MessageSinkRef sender = messenger.sinkRef(senderSinkId);
            try {
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
		if (templateType == TemplateType.ORDERBOOK_SNAPSHOT.value()) {
			parameters.next();
			if (parameters.parameterType() == ParameterType.SECURITY_SID) {
			    final long secSid = parameters.parameterValueLong();
				final Security secInfo = securitiesMap.get(secSid);
				if (secInfo != null) {
					LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
					if (request.requestType() == RequestType.SUBSCRIBE) {
						LOG.info("Subscribing {} to sink[{}]", secInfo.code(), sender.sinkId());
						final boolean result = securitySubscriptionManager.addMessageSink(secSid, sender);
						marketDataSource.requestSnapshot(secSid);
						messenger.responseSender().sendResponseForRequest(sender, request, result ? ResultType.OK : ResultType.FAILED);
					}
					else if (request.requestType() == RequestType.UNSUBSCRIBE) {
						LOG.info("Unsubscribing {} from sink[{}]", secInfo.code(), sender.sinkId());
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
		else if (templateType == TemplateType.MARKETSTATUS.value()) {
			parameters.next();
			if (parameters.parameterType() == ParameterType.EXCHANGE_CODE) {
               final ByteBuffer byteBuffer = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
                parameters.getParameterValue(byteBuffer.array(), 0);
                final String exchangeCode = new String(byteBuffer.array(), 0, ParametersDecoder.parameterValueLength());
                if (exchangeCode.equals(ServiceConstant.PRIMARY_EXCHANGE)) {
					if (request.requestType() == RequestType.SUBSCRIBE) {
						LOG.info("Subscribing {} to sink[{}]", ServiceConstant.PRIMARY_EXCHANGE, sender.sinkId());						
						final boolean result = exchangeSubscriptionManager.addMessageSink(0, sender);
						if (result) {						    						    
							messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
							// TODO shayan potential racing condition here with the exchange feed thread							
							messenger.marketStatusSender().sendMarketStatus(sender, 0, marketDataSource.marketStatus());
						}
						else {
							messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
						}
					}
					else if (request.requestType() == RequestType.UNSUBSCRIBE) {
						LOG.info("Unsubscribing {} from sink[{}]", ServiceConstant.PRIMARY_EXCHANGE, sender.sinkId());
						final boolean result = exchangeSubscriptionManager.removeMessageSink(0, sender);
						messenger.responseSender().sendResponseForRequest(sender, request, result ? ResultType.OK : ResultType.FAILED);
					}
					else {
						throw new IllegalArgumentException("No support for " + request.requestType() + " request for security");
					}
				}
				else {
					throw new IllegalArgumentException("Received " + request.requestType() + " request for exchange with invalid code " + exchangeCode);
                }
			}
			else {
				throw new IllegalArgumentException("Received " + request.requestType() + " request for MARKETSTATUS with unsupported parameters");
			}
		}
		else if (templateType == TemplateType.GENERIC_TRACKER.value()) {
            if (request.requestType() == RequestType.SUBSCRIBE) {
    		    this.performanceSubscriptions.add(sender);
    		    messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                this.performanceSubscriptions.removeMessageSink(sender);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);                
            }
		}
		else {
			throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type");
		}
	}

}
