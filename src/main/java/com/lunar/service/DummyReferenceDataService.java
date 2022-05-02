package com.lunar.service;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.ReferenceDataServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.entity.EntityLoader;
import com.lunar.entity.Issuer;
import com.lunar.entity.Loadable;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;

public class DummyReferenceDataService implements ServiceLifecycleAware {
	static final int BEGINNING_SID = 400_000; 
	static final int DEFAULT_SECURITY_SIZE = 20000;
	private static final Logger LOG = LogManager.getLogger(DummyReferenceDataService.class);
	private final String name;
	private LunarService messageService;
	private final Messenger messenger;
	private final LongEntityManager<Issuer> issuers;
	private final LongEntityManager<Security> securities;

	public static DummyReferenceDataService of(ServiceConfig config, LunarService messageService){
		return new DummyReferenceDataService(config, messageService);
	}
	
	DummyReferenceDataService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof ReferenceDataServiceConfig){			
			ReferenceDataServiceConfig specificConfig = (ReferenceDataServiceConfig)config;
			this.securities = new LongEntityManager<>(
                specificConfig.entityInitialCapacity().orElse(ServiceConstant.DEFAULT_ENTITY_MANAGER_SIZE),
                loader);
			this.issuers = new LongEntityManager<>(
	                specificConfig.entityInitialCapacity().orElse(ServiceConstant.DEFAULT_ENTITY_MANAGER_SIZE),
	                issuerLoader);
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a ReferenceDataServiceConfig config");
		}
    }

	private final EntityLoader<Issuer> issuerLoader = new EntityLoader<Issuer>() {
		@Override
		public void loadInto(Loadable<Issuer> loadable) throws Exception {
		}
	};
	
	private final EntityLoader<Security> loader = new EntityLoader<Security>() {
		@Override
		public void loadInto(Loadable<Security> loadable) throws Exception {
			long nextSid = BEGINNING_SID;
			Security underlying = Security.of(nextSid,
					SecurityType.STOCK, 
					"700",
					1,
					false,
					SpreadTableBuilder.getById(0));
			nextSid++;
			// this will be changed in production
			underlying.omesSink(messenger.referenceManager().omes());
			underlying.mdsSink(messenger.referenceManager().mds());
			underlying.mdsssSink(messenger.referenceManager().mdsss());
			loadable.load(underlying);
			Security security = new Security(nextSid,
					SecurityType.WARRANT, 
					"62345",
					1,
					underlying.sid(),
					Optional.of(LocalDate.of(2016, 3, 31)),
					ServiceConstant.NULL_LISTED_DATE,
                    PutOrCall.CALL,
                    OptionStyle.AMERICAN,
                    123456,
                    10,
                    -1,
                    0,
                    false,
                    SpreadTableBuilder.getById(0));
			nextSid++;
			security.omesSink(messenger.referenceManager().omes());
			security.mdsSink(messenger.referenceManager().mds());
			security.mdsssSink(messenger.referenceManager().mdsss());
			loadable.load(security);
		}
	};
	
	LongEntityManager<Security> securities(){
		return securities;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::handleAggregatedServiceStatusChange);
		try {
			this.securities.load();
		} 
		catch (Exception e) {
			LOG.error("Caught exception when loading securities", e);
			return StateTransitionEvent.FAIL;
		}
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
	public StateTransitionEvent activeEnter() {
		// register commands
		messenger.receiver().requestHandlerList().add(this::handleRequest);
		return StateTransitionEvent.NULL;
	}
	
	private void handleRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
        LOG.info("Received request from sink[{}]", header.senderSinkId());
        final MessageSinkRef sender = messenger.sinkRef(header.senderSinkId());

        // Send response
        final ParametersDecoder parameters = request.parameters();
        if (parameters.count() <= 1) {
            throw new IllegalArgumentException("Received request with insufficient parameters");
        }
        parameters.next();
        if (parameters.parameterType() != ParameterType.TEMPLATE_TYPE) {
            throw new IllegalArgumentException("Received request with first parameter not a TEMPLATE_TYPE");
        }
        final long templateType = parameters.parameterValueLong();
        if (templateType == TemplateType.ISSUER.value()) {
            parameters.next();
            if (parameters.parameterType() == ParameterType.EXCHANGE_CODE) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
                parameters.getParameterValue(byteBuffer.array(), 0);
                final String exchangeCode = new String(byteBuffer.array(), 0, ParametersDecoder.parameterValueLength());
                if (exchangeCode.equals(ServiceConstant.PRIMARY_EXCHANGE)) {
                    final int size = issuers.entities().size();
                    LOG.info("Received {} request for {} issuers in exchange {}", request.requestType(), size, exchangeCode);
                    if (request.requestType() == RequestType.GET) {
                        int count = 0;
                        for (final Issuer issuer : issuers.entities()) {
                            LOG.info("Sending {} to sink[{}]", issuer.code(), sender.sinkId());
                            this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), count == size - 1 ? BooleanType.TRUE : BooleanType.FALSE, count, ResultType.OK, issuer);
                            count++;
                        }
                    }
                    else {
                        throw new IllegalArgumentException("No support for " + request.requestType() + " request for issuer");
                    }                    
                }
                else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for issuer in an invalid exchange " + exchangeCode);
                }                
            }
            else {
                throw new IllegalArgumentException("Received " + request.requestType() + " request for Issuer with unsupported parameters");
            }            
        }
        else if (templateType == TemplateType.SECURITY.value()) {
            parameters.next();
            if (parameters.parameterType() == ParameterType.SECURITY_SID) {
                final long secSid = parameters.parameterValueLong();
                final Security security = this.securities.get(secSid);
                if (security != null) {
                    LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
                    if (request.requestType() == RequestType.GET) {
                        LOG.info("Sending {} to sink[{}]", security.code(), sender.sinkId());
                        this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 1, ResultType.OK, security);
                    }
                    else if (request.requestType() == RequestType.SUBSCRIBE) {
                        LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
                        securities.subscribe(security.sid(), sender);
                        this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 1, ResultType.OK, security);
                    }
                    else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                        LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
                        securities.unsubscribe(security.sid(), sender);
                        this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                    }
                    else {
                        throw new IllegalArgumentException("No support for " + request.requestType() + " request for security");
                    }
                }
                else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for security with invalid sid " + secSid);
                }
            }
            else if (parameters.parameterType() == ParameterType.EXCHANGE_CODE) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
                parameters.getParameterValue(byteBuffer.array(), 0);
                final String exchangeCode = new String(byteBuffer.array(), 0, ParametersDecoder.parameterValueLength());
                if (exchangeCode.equals(ServiceConstant.PRIMARY_EXCHANGE)) {
                    final int size = securities.entities().size();
                    LOG.info("Received {} request for {} securities in exchange {}", request.requestType(), size, exchangeCode);
                    if (request.requestType() == RequestType.GET) {
                        int count = 0;
                        for (final Security security : securities.entities()) {
                            LOG.info("Sending {} to sink[{}]", security.code(), sender.sinkId());
                            this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), count == size - 1 ? BooleanType.TRUE : BooleanType.FALSE, count, ResultType.OK, security);
                            count++;
                        }
                    }
                    else if (request.requestType() == RequestType.SUBSCRIBE) {
                        int count = 0;
                        for (final Security security : securities.entities()) {
                            LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
                            securities.subscribe(security.sid(), sender);
                            this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), count == size - 1 ? BooleanType.TRUE : BooleanType.FALSE, count, ResultType.OK, security);
                            count++;
                        }
                    }
                    else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                        for (final Security security : securities.entities()) {
                            LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
                            securities.unsubscribe(security.sid(), sender);
                        }
                        this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                    }
                    else {
                        throw new IllegalArgumentException("No support for " + request.requestType() + " request for security");
                    }
                }
                else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for securities in an invalid exchange " + exchangeCode);
                }
            } 
            else {
                throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
            }
        }
        else {
            throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type");
        }
	}

	@Override
	public void activeExit() {
		// unregister command
		messenger.receiver().requestHandlerList().remove(this::handleRequest);
	}
}
