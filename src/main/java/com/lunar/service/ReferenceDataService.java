package com.lunar.service;

import static org.apache.logging.log4j.util.Unbox.box;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.config.ReferenceDataServiceConfig;
import com.lunar.config.ReferenceDataServiceConfig.EntityTypeSetting;
import com.lunar.config.ServiceConfig;
import com.lunar.core.SubscriberList;
import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Note;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.DateUtil;

public class ReferenceDataService implements ServiceLifecycleAware {
    //TODO shayan, initially wanted 400_000 to identify the exchange
    //static final int BEGINNING_SID = 400_000;

    private static final Logger LOG = LogManager.getLogger(ReferenceDataService.class);

    private static final int DEFAULT_SECURITY_SIZE = 10000;
    private static final int DEFAULT_ISSUER_SIZE = 100;
    private static final int DEFAULT_NOTE_SIZE = 1000;

    private final String name;
    private final LunarService messageService;
    private final Messenger messenger;
    private final LongEntityManager<Issuer> issuers;
    private final LongEntityManager<Security> securities;
    private final LongEntityManager<Note> notes;
    private final SubscriberList noteSubscribers;
	private final NoteSbeDecoder noteDecoder;

    public static ReferenceDataService of(final ServiceConfig config, final LunarService messageService){
        return new ReferenceDataService(config, messageService);
    }

    ReferenceDataService(final ServiceConfig config, final LunarService messageService){
        this.name = config.name();
        this.messageService = messageService;
        this.messenger = messageService.messenger();
        if (config instanceof ReferenceDataServiceConfig){			
            ReferenceDataServiceConfig specificConfig = (ReferenceDataServiceConfig)config;
            final EntityTypeSetting securitySpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.SECURITY);
            securities = new LongEntityManager<Security>(securitySpecificSetting == null ? DEFAULT_SECURITY_SIZE : securitySpecificSetting.numEntities());
            
            final EntityTypeSetting issuerSpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.ISSUER);
            issuers = new LongEntityManager<Issuer>(issuerSpecificSetting == null ? DEFAULT_ISSUER_SIZE :issuerSpecificSetting.numEntities());
            
            final EntityTypeSetting noteSpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.NOTE);
            notes = new LongEntityManager<Note>(noteSpecificSetting == null ? DEFAULT_NOTE_SIZE :noteSpecificSetting.numEntities());
            noteSubscribers = SubscriberList.of();
            noteDecoder = new NoteSbeDecoder();
        }
        else{
            throw new IllegalArgumentException("Service " + this.name + " expects a ReferenceDataServiceConfig config");
        }

    }

    LongEntityManager<Issuer> issuers() {
        return issuers;
    }

    LongEntityManager<Security> securities(){
        return securities;
    }	

    LongEntityManager<Note> notes(){
        return notes;
    }	
    
    @Override
    public StateTransitionEvent idleStart() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService);
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
    public StateTransitionEvent waitingForServicesEnter() {
        // we should wait for health check on database connection
        return StateTransitionEvent.NULL;
    }

    @Override
    public StateTransitionEvent readyEnter() {
        messenger.receiver().issuerHandlerList().add(issuerHandler);
        messenger.receiver().securityHandlerList().add(securityHandler);
        messenger.receiver().noteHandlerList().add(noteHandler);
        CompletableFuture<Request> retrieveIssuerFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                RequestType.GET,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(TemplateType.ISSUER), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveIssuerFuture.thenAccept((r) -> { 
            CompletableFuture<Request> retrieveSecurityFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                    RequestType.GET,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(TemplateType.SECURITY), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                    ResponseHandler.NULL_HANDLER);
            retrieveSecurityFuture.thenAccept((r2) -> { 

                CompletableFuture<Request> retrieveNoteFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                        RequestType.GET,
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(TemplateType.NOTE)).build(),
                        ResponseHandler.NULL_HANDLER);
                retrieveNoteFuture.thenAccept((r3) -> {
                	
                    messageService.stateEvent(StateTransitionEvent.ACTIVATE);
                	
                });
            });
        });        
        return StateTransitionEvent.NULL;
    }

    @Override
    public StateTransitionEvent activeEnter() {
        // register commands
        messenger.receiver().requestHandlerList().add(requestHandler);
        messenger.receiver().commandHandlerList().add(commandHandler);
        /*
        try {
            for (final Issuer issuer : issuers.entities()) {
                messageService.messenger().issuerSender().sendIssuer(messenger.referenceManager().persi(), issuer);
            }            
            for (final Security security : securities.entities()) {
                messageService.messenger().securitySender().sendSecurity(messenger.referenceManager().persi(), security);
            }
        } catch (final Exception e) {
            LOG.error("Cannot initialize Strategy Manager...", e);
            return StateTransitionEvent.FAIL;
        }
        */
        return StateTransitionEvent.NULL;
    }

    @Override
    public void activeExit() {
        // unregister command
        messenger.receiver().issuerHandlerList().remove(issuerHandler);
        messenger.receiver().securityHandlerList().remove(securityHandler);
        messenger.receiver().noteHandlerList().remove(noteHandler);
        messenger.receiver().commandHandlerList().remove(commandHandler);
        messenger.receiver().requestHandlerList().remove(requestHandler);
    }
    
    private final Handler<NoteSbeDecoder> noteHandler = new Handler<NoteSbeDecoder>(){
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder noteSbe) {
			long noteSid = noteSbe.noteSid();
	        LOG.info("Received and added/updated note {} for {}", box(noteSbe.noteSid()), box(noteSbe.entitySid()));
	        
	        Note note = notes.get(noteSid);
	        if (note == null){
	        	note = Note.of(noteSbe, messenger.stringBuffer());
				notes.add(note);
	        }
	        else {
	        	note = note.createDate(noteSbe.createDate())
	        		.createTime(noteSbe.createTime())
	        		.updateDate(noteSbe.updateDate())
	        		.updateTime(noteSbe.updateTime())
	        		.isArchived(noteSbe.isArchived())
	        		.isDeleted(noteSbe.isDeleted());
	        }
	        LOG.info("Received note [{}]", note);
			
			for (int i = 0; i < noteSubscribers.size(); i++){
				messenger.noteSender().sendNote(noteSubscribers.elements()[i], 
						(int)noteSid, 
						note.entitySid(), 
						note.createDate(), 
						note.createTime(), 
						note.updateDate(), 
						note.updateTime(), 
						note.isDeleted(), 
						note.isArchived(), 
						note.description());
			}
/*			
	        messenger.trySendWithHeaderInfo(noteSubscribers,	        		
	        		NoteSbeEncoder.BLOCK_LENGTH, 
	        		NoteSbeEncoder.TEMPLATE_ID, 
	        		NoteSbeEncoder.SCHEMA_ID, 
	        		NoteSbeEncoder.SCHEMA_VERSION, 
	        		buffer, 
	        		offsetToEntity, 
	        		NoteSbeDecoder.BLOCK_LENGTH + NoteSbeDecoder.descriptionHeaderLength() + note.description().length(),
//	        		noteSbe.encodedLength() + NoteSbeDecoder.descriptionHeaderLength() + noteSbe.descriptionLength(), 
	        		sinkSendResults);*/
		}
    };
    
    private final Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
	        LOG.info("Received target security {}", box(security.sid()));
	        final byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
	        security.getCode(bytes, 0);
	        try {
	            Security newSecurity = Security.of(security.sid(), 
	                    security.securityType(), 
	                    new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
	                    security.exchangeSid(), 
	                    security.undSid(),
	                    (security.maturity() == SecuritySbeDecoder.maturityNullValue()) ? 
	                            Optional.empty() :
	                            Optional.of(LocalDate.of(security.maturity() / 10000, security.maturity() % 10000 / 100, security.maturity() % 100)),
	                    DateUtil.fromSbeToLocalDate(security.listingDate()),
	                    security.putOrCall(),
	                    security.style(),
	                    security.strikePrice(),
	                    security.conversionRatio(),
	                    security.issuerSid(),
	                    security.lotSize(),
	                    security.isAlgo() == BooleanType.TRUE,
	                    SpreadTableBuilder.getById(security.spreadTableCode()))
	                    .omesSink(messenger.referenceManager().omes())
	                    .mdsSink(messenger.referenceManager().mds())
	                    .mdsssSink(messenger.referenceManager().mdsss());
	            securities.add(newSecurity);
	        } 
	        catch (final UnsupportedEncodingException e) {
	            LOG.error("Caught exception", e);
	            messageService.stateEvent(StateTransitionEvent.FAIL);
	            return;
	        }
		}
	};
    
    private final Handler<IssuerSbeDecoder> issuerHandler = new Handler<IssuerSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, IssuerSbeDecoder issuer) {
	        LOG.info("Received target issuer {}", box(issuer.sid()));
	        //TODO
	        final byte[] codeBytes = new byte[IssuerSbeDecoder.codeLength()];
	        issuer.getCode(codeBytes, 0);
	        final byte[] nameBytes = new byte[IssuerSbeDecoder.nameLength()];
	        issuer.getName(nameBytes, 0);
	        try {
	            final Issuer newIssuer = Issuer.of(issuer.sid(),
	                    new String(codeBytes, IssuerSbeDecoder.codeCharacterEncoding()).trim(),
	                    new String(nameBytes, IssuerSbeDecoder.nameCharacterEncoding()).trim());
	            issuers.add(newIssuer);
	        } 
	        catch (final UnsupportedEncodingException e) {
	            LOG.error("Caught exception", e);
	            messageService.stateEvent(StateTransitionEvent.FAIL);
	            return;
	        }
		}
	};

	private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            LOG.info("Received request [senderSinkId:{}, clientKey:{}, requestType:{}]", box(header.senderSinkId()), box(request.clientKey()), request.requestType().name());
	        final MessageSinkRef sender = messenger.sinkRef(header.senderSinkId());
	        try {
	            handleRequest(sender, buffer, offset, request);
	        }
	        catch (final Exception e) {
	            LOG.error("Failed to handle request [senderSinkId:{}, request:{}]", box(sender.sinkId()), RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()), e);
	            messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
	        }
		}
	};

    private void handleRequest(final MessageSinkRef sender, final DirectBuffer buffer, final int offset, final RequestSbeDecoder request) throws UnsupportedEncodingException {
        // Send response   	
    	ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
        if (parameters.size() <= 1) {
            throw new IllegalArgumentException("Received request with insufficient parameters");
        }
        Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);
        if (!templateType.isPresent()){
            throw new IllegalArgumentException("Received request with no TEMPLATE_TYPE");
        }
        if (templateType.get() == TemplateType.ISSUER) {
        	Optional<String> exchangeCodeParam = Parameter.getParameterValueString(parameters, ParameterType.EXCHANGE_CODE);
        	if (exchangeCodeParam.isPresent()){
        		String exchangeCode = exchangeCodeParam.get();
                if (exchangeCode.equals(ServiceConstant.PRIMARY_EXCHANGE)) {
                    final int size = issuers.entities().size();
                    LOG.info("Received {} request for {} issuers in exchange {}", request.requestType(), box(size), exchangeCode);
                    if (request.requestType() == RequestType.GET) {
                        int count = 0;
                        for (final Issuer issuer : issuers.entities()) {
                            LOG.info("Sending {} to sink[{}]", issuer.code(), box(sender.sinkId()));
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
        else if (templateType.get() == TemplateType.SECURITY) {
        	Optional<Long> secSidParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
        	Optional<String> exchangeCodeParam = Parameter.getParameterValueString(parameters, ParameterType.EXCHANGE_CODE);
            if (secSidParam.isPresent()) {
                final long secSid = secSidParam.get();
                final Security security = this.securities.get(secSid);
                if (security != null) {
                    LOG.info("Received {} request for security with sid {}", request.requestType(), box(secSid));
                    if (request.requestType() == RequestType.GET) {
                        LOG.info("Sending {} to sink[{}]", security.code(), box(sender.sinkId()));
                        this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 1, ResultType.OK, security);
                    }
                    else if (request.requestType() == RequestType.SUBSCRIBE) {
                        LOG.info("Subscribing {} to sink[{}]", security.code(), box(sender.sinkId()));
                        securities.subscribe(security.sid(), sender);
                        this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 1, ResultType.OK, security);
                    }
                    else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                        LOG.info("Unsubscribing {} from sink[{}]", security.code(), box(sender.sinkId()));
                        securities.unsubscribe(security.sid(), sender);
                        this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                    }
                    else {
                    	LOG.error("Unsupported request type [requestType:{}]", request.requestType().name());
                    	this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.UNSUPPORTED_OPERATION);
                    }
                }
                else {
                	LOG.error("Cannot find security [secSid:{}]", box(secSid));
                	this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
                }
            }
            else if (exchangeCodeParam.isPresent()) {
                final String exchangeCode = exchangeCodeParam.get();
                if (exchangeCode.equals(ServiceConstant.PRIMARY_EXCHANGE)) {
                    final int size = securities.entities().size();
                    LOG.info("Received {} request for {} securities in exchange {}", request.requestType(), box(size), exchangeCode);
                    if (request.requestType() == RequestType.GET) {
                        int count = 0;
                        for (final Security security : securities.entities()) {
                            LOG.info("Sending {} to sink[{}]", security.code(), box(sender.sinkId()));
                            this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), count == size - 1 ? BooleanType.TRUE : BooleanType.FALSE, count, ResultType.OK, security);
                            count++;
                        }
                    }
                    else if (request.requestType() == RequestType.SUBSCRIBE) {
                        int count = 0;
                        for (final Security security : securities.entities()) {
                            LOG.info("Subscribing {} to sink[{}]", security.code(), box(sender.sinkId()));
                            securities.subscribe(security.sid(), sender);
                            this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), count == size - 1 ? BooleanType.TRUE : BooleanType.FALSE, count, ResultType.OK, security);
                            count++;
                        }
                    }
                    else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                        for (final Security security : securities.entities()) {
                            LOG.info("Unsubscribing {} from sink[{}]", security.code(), box(sender.sinkId()));
                            securities.unsubscribe(security.sid(), sender);
                        }
                        this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                    }
                    else {
                    	LOG.error("Unsupported request type [requestType:{}]", request.requestType().name());
                    	this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.UNSUPPORTED_OPERATION);
                    }
                }
                else {
                	LOG.error("Cannot find security for invalid exchange [exchange:{}]", exchangeCode);
                	this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
                }
            } 
            else {
            	LOG.error("Cannot find security with unsupported parameters [parameterType:{}]", templateType.get().name());
            	this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
            }
        }
        else if (templateType.get() == TemplateType.NOTE) {
        	if (request.requestType() == RequestType.GET){
                final int size = notes.entities().size();
                LOG.info("Received {} request for to get all {} notes", request.requestType(), box(size));
                int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
                for (final Note note: notes.entities()) {
                	this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, responseMsgSeq++, ResultType.OK, note);
                }
                this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
                return;
        	}
        	else if (request.requestType() == RequestType.SUBSCRIBE){
        		noteSubscribers.add(sender);
        		LOG.info("Subscribing Note to sink[{}]", box(sender.sinkId()));
        		return;
        	}
        	else if (request.requestType() == RequestType.GET_AND_SUBSCRIBE){
        		noteSubscribers.add(sender);
        		LOG.info("Subscribing Note to sink[{}]", box(sender.sinkId()));
                final int size = notes.entities().size();
                LOG.info("Received {} request for to get all {} notes", request.requestType(), box(size));
                int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
                for (final Note note: notes.entities()) {
                	this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, responseMsgSeq++, ResultType.OK, note);
                }
                this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
                return;
        	}
        	// Not allow unsubscription
        	
        	// Extract Note from the request
        	Request extractedRequest = Request.of(sender.sinkId(), request.requestType(), parameters, request.toSend());
        	if (request.clientKey() != RequestSbeDecoder.clientKeyNullValue()){
        		extractedRequest.clientKey(request.clientKey());
        	}

        	RequestDecoder.wrapEmbeddedNote(buffer, offset, parameters.size(), request, noteDecoder);
        	extractedRequest.entity(Note.of(noteDecoder, messenger.stringBuffer()));
        	Note extractedNote = (Note)extractedRequest.entity().get();
        	
        	if (request.requestType() == RequestType.CREATE){
        		// Make sure noteSid does not exist
        		if (extractedNote.sid() != ServiceConstant.NULL_NOTE_SID){
        			LOG.warn("Request for creating note must not contain noteSid [noteSid:{}]", extractedNote.sid());
        			this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
        			return;
        		}
        		if (extractedNote.entitySid() == ServiceConstant.NULL_ENTITY_SID){
        			LOG.warn("Request for creating note must contain entitySid [entitySid:{}]", extractedNote.entitySid());
        			this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
        			return;        			
        		}
        		if (extractedNote.description().isEmpty()){
        			LOG.warn("Request for creating note must contain non empty description [entitySid:{}]", extractedNote.entitySid());
        			this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
        			return;        			
        		}
        		// Sending this same request to persistence service using the same clientKey.
        		// Create a request object so that we can respond later
        		
        		CompletableFuture<Request> requestFuture = messenger.sendRequest(messenger.referenceManager().persi(), extractedRequest);        		
        		requestFuture.thenAccept((r) -> {
        			this.messenger.responseSender().sendResponse(sender, r.clientKey(), BooleanType.TRUE, ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK);
        		});
        	}
        	else if (request.requestType() == RequestType.UPDATE){ 
        		if (extractedNote.sid() == ServiceConstant.NULL_NOTE_SID){
        			LOG.warn("Request for updating note must contain noteSid [noteSid:{}]", extractedNote.sid());
        			this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
        			return;
        		}
        		CompletableFuture<Request> requestFuture = messenger.sendRequest(messenger.referenceManager().persi(), extractedRequest);        		
        		requestFuture.thenAccept((r) -> {
        			this.messenger.responseSender().sendResponse(sender, r.clientKey(), BooleanType.TRUE, ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK);
        		});
        	}

        }
        else {
        	LOG.error("Unsupported template type [templateType:{}]", templateType);
        	this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.UNSUPPORTED_OPERATION);
        }
    }

    private final Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
	        switch (codec.commandType()){
	        case REFRESH:
	            LOG.info("Received REFRESH command from {}", box(header.senderSinkId()));
	            // TODO shayan
	            break;
	        default:
	            break;
	        }	
		}
	};
}
