package com.lunar.service;

import static org.apache.logging.log4j.util.Unbox.box;

import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableListMultimap;
import com.lunar.config.PersistServiceConfig;
import com.lunar.config.PersistServiceConfig.EntityTypeSetting;
import com.lunar.config.ServiceConfig;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.database.ResilientDbConnection;
import com.lunar.entity.CsvFileEntityLoader;
import com.lunar.entity.DatabaseEntityLoader;
import com.lunar.entity.DbNotePersister;
import com.lunar.entity.EntityLoader;
import com.lunar.entity.EntityLoaderType;
import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Note;
import com.lunar.entity.NotePersister;
import com.lunar.entity.NullEntityLoader;
import com.lunar.entity.RawToIssuerConverter;
import com.lunar.entity.RawToNoteConverter;
import com.lunar.entity.RawToSecurityConverter;
import com.lunar.entity.Security;
import com.lunar.entity.SidManager;
import com.lunar.entity.StrategyType;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Parameter;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.binary.ScoreBoardDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeEncoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.sender.PositionSender;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.DbOrderTradePersister;
import com.lunar.order.NullOrderTradePersister;
import com.lunar.order.OrderTradePersister;
import com.lunar.order.OrderUpdateEvent;
import com.lunar.order.OrderUpdateEventStore;
import com.lunar.position.DbPositionLoader;
import com.lunar.position.NullPositionLoader;
import com.lunar.position.PositionLoader;
import com.lunar.pricing.CurvesLoader;
import com.lunar.pricing.DbCurvesLoader;
import com.lunar.pricing.DividendCurve;
import com.lunar.pricing.Greeks;
import com.lunar.pricing.NullCurvesLoader;
import com.lunar.strategy.DbStrategyLoader;
import com.lunar.strategy.DbStrategyPersister;
import com.lunar.strategy.NullStrategyLoader;
import com.lunar.strategy.NullStrategyPersister;
import com.lunar.strategy.StrategyLoader;
import com.lunar.strategy.StrategyPersister;
import com.lunar.strategy.StrategyServiceHelper;
import com.lunar.strategy.StrategySwitch;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

/**
 * 
 * HOW CAN SOMEBODY BE THIS SLOPPY?  JUST COPY AND PASTE AN INCORRECT COMMENT FROM ANOTHER CLASS...
 * 
 * A general market data snapshot service that serves all snapshot needs (i.e. different exchanges)
 * It receives real-time updates from {@link MarketDataRealtimeService} and get order book 
 * snapshots from {@link MarketDataRefreshService} 
 * @author wongca
 *
 */
public class PersistService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(PersistService.class);
	private static final int EXPECTED_NUM_ORDERS = 20000;
	private static final int EXPECTED_NUM_TRADES = 2000;
	private static final int EXPECTED_UPDATES_PER_ORDER = 3;
	private static final int NUM_UNDERLYINGS = 100;

	private final LunarService messageService;
	private final Messenger messenger;
	private final String name;
    private final ScoreBoardDecoder scoreBoardDecoder = ScoreBoardDecoder.of();

    private final DbConnection conn;
    
    private final LongEntityManager<Issuer> issuers;
    private final SidManager<String> issuerSidManager;

    private final LongEntityManager<Security> securities;
    private final SidManager<String> securitySidManager;
    
    private final Int2ObjectOpenHashMap<MutableDirectBuffer> orders;
    private final Int2ObjectOpenHashMap<MutableDirectBuffer> trades;
    private final Long2ObjectOpenHashMap<MutableDirectBuffer> positions;
    
    private final PositionLoader positionLoader;
    private final PositionSbeEncoder positionEncoder = new PositionSbeEncoder(); 
    private final OrderTradePersister orderTradePersister;

    private final CurvesLoader curvesLoader;
    private final Long2ObjectLinkedOpenHashMap<DividendCurve> dividendCurves;

    private final StrategyServiceHelper strategyServiceHelper;

    private final SidManager<String> strategyTypeSidManager;    
    private final StrategyPersister strategyPersister;
    private final StrategyLoader strategyLoader;
    
    final Long2ObjectLinkedOpenHashMap<ScoreBoard> scoreBoards;

    private final LongEntityManager<Note> notes;
    private final NotePersister notePersister;
    private final NoteSbeDecoder noteSbeDecoder;
    
    
    final int numSecurities;
    final int numIssuers;
    
    public static PersistService of(final ServiceConfig config, final LunarService messageService){
        return new PersistService(config, messageService);
    }
    
	public PersistService(final ServiceConfig config, final LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof PersistServiceConfig) {
		    final PersistServiceConfig specificConfig = (PersistServiceConfig)config;
            try {
                conn = new ResilientDbConnection(specificConfig.dbUrl());
            } catch (final SQLException ex) {
                // handle any errors
                LOG.error("SQLException: {}", ex.getMessage());
                LOG.error("SQLState: {}", ex.getSQLState());
                LOG.error("VendorError: {}", ex.getErrorCode());
                throw new RuntimeException(ex);
            }
            final EntityTypeSetting issuerSpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.ISSUER);
            issuerSidManager = new SidManager<String>(issuerSpecificSetting.numEntities(), 0);
            issuers = new LongEntityManager<>(issuerSpecificSetting.numEntities(), createIssuerLoader(issuerSpecificSetting));
            
            final EntityTypeSetting securitySpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.SECURITY);
            securitySidManager = new SidManager<String>(securitySpecificSetting.numEntities(), 100000);
            securities = new LongEntityManager<>(securitySpecificSetting.numEntities(), createSecurityLoader(securitySpecificSetting));

            final EntityTypeSetting strategySpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.STRATEGYTYPE);
            strategyTypeSidManager = new SidManager<String>(strategySpecificSetting.numEntities(), 0);
            
            strategyServiceHelper = new StrategyServiceHelper(strategyUpdateHandler, securities, issuers, strategySpecificSetting.numEntities(), securitySpecificSetting.numEntities() / 10, securitySpecificSetting.numEntities(), issuerSpecificSetting.numEntities());
            
            scoreBoards = new Long2ObjectLinkedOpenHashMap<ScoreBoard>(securitySpecificSetting.numEntities());
            
            numSecurities = securitySpecificSetting.numEntities();
            numIssuers = issuerSpecificSetting.numEntities();
            
            strategyPersister = createStrategyPersister(specificConfig); 
            strategyLoader = createStrategyLoader(specificConfig);
            
            dividendCurves = new Long2ObjectLinkedOpenHashMap<DividendCurve>(securitySpecificSetting.numEntities());
            curvesLoader = createCurvesLoader(specificConfig);
            
            orderTradePersister = createOrderTradePersister(specificConfig);
            positionLoader = createPositionLoader(specificConfig);
            
            final EntityTypeSetting noteSpecificSetting = specificConfig.entityTypeSettings().get(TemplateType.NOTE);
            notes = new LongEntityManager<>(noteSpecificSetting.numEntities(), createNoteLoader(noteSpecificSetting));
            notePersister = createNotePersister(specificConfig);
            noteSbeDecoder = new NoteSbeDecoder();
		}
		else {
		    throw new IllegalArgumentException("Service " + this.name + " expects a PersistServiceConfig config");
		}
		this.orders = new Int2ObjectOpenHashMap<>(EXPECTED_NUM_ORDERS);
		this.trades = new Int2ObjectOpenHashMap<>(EXPECTED_NUM_TRADES);
		this.orderUpdateStore = OrderUpdateEventStore.of(EXPECTED_NUM_ORDERS, EXPECTED_UPDATES_PER_ORDER);
		this.positions = new Long2ObjectOpenHashMap<>(EXPECTED_NUM_TRADES);
	}

//	private final RawToNoteConverter rawToNoteConverter = new RawToNoteConverter();
    private EntityLoader<Note> createNoteLoader(final EntityTypeSetting entityTypeSetting) {
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
        case DATABASE:
            return new DatabaseEntityLoader<Note>(conn, LunarQueries.getAllNotesForActiveInstruments(messageService.systemClock().dateString()), new RawToNoteConverter(securitySidManager));
        case CSV:
            return new CsvFileEntityLoader<Note>(entityTypeSetting.uri(), new RawToNoteConverter(securitySidManager));
        default:
            return new NullEntityLoader<Note>(null, null);
        }
    }

    private NotePersister createNotePersister(final PersistServiceConfig specificConfig){
        final EntityTypeSetting entityTypeSetting = specificConfig.entityTypeSettings().get(TemplateType.NOTE);
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
            case DATABASE:
                return new DbNotePersister(messageService.systemClock(), conn, securitySidManager);
            default:
                return NotePersister.NullPersister;
        }
    }
    
    private EntityLoader<Security> createSecurityLoader(final EntityTypeSetting entityTypeSetting) {
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
        case DATABASE:
            return new DatabaseEntityLoader<Security>(conn, LunarQueries.getAllActiveInstruments(messageService.systemClock().dateString()), new RawToSecurityConverter(securitySidManager, issuerSidManager));
        case CSV:
            return new CsvFileEntityLoader<Security>(entityTypeSetting.uri(), new RawToSecurityConverter(securitySidManager, issuerSidManager));
        default:
            return new NullEntityLoader<Security>(null, null);
        }
    }
    
    private EntityLoader<Issuer> createIssuerLoader(final EntityTypeSetting entityTypeSetting) {
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
        case DATABASE:
            return new DatabaseEntityLoader<Issuer>(conn, LunarQueries.getAllActiveIssuers(messageService.systemClock().dateString()), new RawToIssuerConverter(issuerSidManager));
        case CSV:
            return new CsvFileEntityLoader<Issuer>(entityTypeSetting.uri(), new RawToIssuerConverter(issuerSidManager));
        default:
            return new NullEntityLoader<Issuer>(null, null);
        }
    }
	
	private StrategyPersister createStrategyPersister(final PersistServiceConfig specificConfig) {
        final EntityTypeSetting entityTypeSetting = specificConfig.entityTypeSettings().get(TemplateType.STRATEGYTYPE);
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
            case DATABASE:
                return new DbStrategyPersister(messageService.systemClock(), conn);
            default:
                return new NullStrategyPersister();
        }
	}
	
    private OrderTradePersister createOrderTradePersister(final PersistServiceConfig specificConfig) {
        final EntityTypeSetting entityTypeSetting = specificConfig.entityTypeSettings().get(TemplateType.ORDER);
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
            case DATABASE:
                return new DbOrderTradePersister(messageService.systemClock(), conn, securitySidManager);
            default:
                return new NullOrderTradePersister();
        }
    }	    
	
    private PositionLoader createPositionLoader(final PersistServiceConfig specificConfig) {
        final EntityTypeSetting entityTypeSetting = specificConfig.entityTypeSettings().get(TemplateType.POSITION);
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
            case DATABASE:
                return new DbPositionLoader(messageService.systemClock(), conn);
            default:
                return new NullPositionLoader();
        }
    }

	private StrategyLoader createStrategyLoader(final PersistServiceConfig specificConfig) {
        final EntityTypeSetting entityTypeSetting = specificConfig.entityTypeSettings().get(TemplateType.STRATEGYTYPE);
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
            case DATABASE:
                return new DbStrategyLoader(messageService.systemClock(), conn, strategyTypeSidManager, securitySidManager, issuerSidManager);
            default:
                return new NullStrategyLoader();
        }
	}

	private CurvesLoader createCurvesLoader(final PersistServiceConfig specificConfig) {
        final EntityTypeSetting entityTypeSetting = specificConfig.entityTypeSettings().get(TemplateType.STRATEGYTYPE);
        final EntityLoaderType loaderType = entityTypeSetting != null ? entityTypeSetting.loaderType() : EntityLoaderType.DATABASE;
        switch (loaderType) {
            case DATABASE:
        		return new DbCurvesLoader(messageService.systemClock(), conn);
            default:
                return new NullCurvesLoader();
        }
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
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
        try {
            this.issuers.load();
            this.securities.load();
            this.notes.load();
            this.curvesLoader.loadDividendCurve((secCode,  curve)->{
            	this.dividendCurves.put(curve.secSid(), curve);
            }, securitySidManager);
            this.strategyServiceHelper.load(this.strategyLoader);
            this.strategyLoader.loadScoreBoards((sid, scoreBoard)-> {
               if (sid != SidManager.INVALID_SID) {
                   scoreBoard.setSecSid(sid);
                   scoreBoards.put(sid, scoreBoard);
               }
            });
            this.positionLoader.loadPositions(securitySidManager, (p)-> {
            	// Note: This secSid must be in the convention of what the system is using, not the plain secSid from database
            	// E.g. secSid = [exchange sid][sid from db]
                final long secSid = p.secSid();
                
            	MutableDirectBuffer positionBuffer = positions.get(secSid);
            	if (positionBuffer == positions.defaultReturnValue()){
            		positionBuffer = new UnsafeBuffer(ByteBuffer.allocate(PositionSbeEncoder.BLOCK_LENGTH));
            		positions.put(secSid, positionBuffer);
            	}
				PositionSender.encodePositionOnly(positionBuffer, 0, positionEncoder, p);                
            });
        } 
        catch (final Exception e) {
            LOG.error("Caught exception when loading securities", e);
            return StateTransitionEvent.FAIL;
        }
        return StateTransitionEvent.ACTIVATE;
    }

    private final OrderUpdateEventStore orderUpdateStore;
    
	@Override
	public StateTransitionEvent activeEnter() {
        messenger.receiver().requestHandlerList().add(requestHandler);
        strategyServiceHelper.registerHandlers(messenger);
	    messenger.receiver().greeksHandlerList().add(greeksHandler);
	    messenger.receiver().orderHandlerList().add(orderHandler);
	    messenger.receiver().tradeHandlerList().add(tradeHandler);
	    messenger.receiver().scoreBoardHandlerList().add(scoreBoardHandler);
	    orderUpdateStore.register(messenger);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
        messenger.receiver().requestHandlerList().remove(requestHandler);
        strategyServiceHelper.unregisterHandlers(messenger);
	    messenger.receiver().greeksHandlerList().remove(greeksHandler);
        messenger.receiver().orderHandlerList().remove(orderHandler);
        messenger.receiver().tradeHandlerList().remove(tradeHandler);
        messenger.receiver().scoreBoardHandlerList().remove(scoreBoardHandler);
        orderUpdateStore.unregister(messenger);
	}

	private Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            LOG.info("Received request [senderSinkId:{}, clientKey:{}, requestType:{}]", header.senderSinkId(), box(request.clientKey()), request.requestType().name());
            byte senderSinkId = header.senderSinkId();
            final MessageSinkRef sender = messenger.sinkRef(senderSinkId);
            try {
                handleRequest(buffer, offset, sender, request);
            }
            catch (final Exception e) {
                LOG.error("Failed to handle request", e);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
            } 
        }	    
	};

    private void handleRequest(DirectBuffer buffer, int offset, final MessageSinkRef sender, final RequestSbeDecoder request) throws Exception {
    	ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
    	Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);
    	if (templateType.isPresent()){
    		switch (templateType.get()){
    		case ISSUER:
    			handleIssuerGetRequest(sender, request, parameters);
    			break;
    		case SECURITY:
    			handleSecurityGetRequest(sender, request, parameters);
    			break;
    		case STRATEGYTYPE:
    			handleStrategyTypeGetRequest(sender, request, parameters);
    			break;
    		case DIVIDEND_CURVE:
    			handleDividendCurveGetRequest(sender, request, parameters);
    			break;
    		case POSITION:
    			handlePositionGetRequest(sender, request, parameters);
    			break;
    		case ORDER:
    	    	handleVanillaTemplateTypeGetRequest(sender, request, parameters, orders.values(), OrderSbeEncoder.TEMPLATE_ID, OrderSbeEncoder.BLOCK_LENGTH);
    			break;
    		case TRADE:
    	    	handleVanillaTemplateTypeGetRequest(sender, request, parameters, trades.values(), TradeSbeEncoder.TEMPLATE_ID, TradeSbeEncoder.BLOCK_LENGTH);
    			break;
    		case SCOREBOARD:
    		    handleScoreBoardGetRequest(sender, request, parameters);
    		    break;
    		case NOTE:
    			handleNoteRequest(buffer, offset, sender, request, parameters);
    			break;
    		default:
    			throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type: " + templateType.get().name());
    		}
    		return;
    	}
    	Optional<DataType> dataType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
    	if (dataType.isPresent()){
    		switch (dataType.get()){
    		case ALL_ORDER_AND_TRADE_UPDATE:
    			handleAllOrderAndTradeUpdate(sender, request);
    			break;
    		default:
    			throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported data type: " + dataType.get().name());
    		}
    		return;
    	}
    }
    
    /**
     * Send all order and trade update to the sender
     * @param sender
     */
    private void handleAllOrderAndTradeUpdate(final MessageSinkRef sender, final RequestSbeDecoder request){
    	int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
    	for (OrderUpdateEvent orderUpdate : orderUpdateStore.orderUpdates()){
    		messenger.responseSender().sendResponse(sender, 
    				request.clientKey(), 
    				responseMsgSeq++, 
    				orderUpdate.buffer(), 
    				orderUpdate.buffer().capacity(), 
    				orderUpdate.templateId(), 
    				orderUpdate.blockLength());
    	}
    	messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
    }

    private void handleIssuerGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }

    	Optional<String> exchangeCode = Parameter.getParameterValueString(parameters, ParameterType.EXCHANGE_CODE);
    	if (exchangeCode.isPresent() && exchangeCode.get().compareTo(ServiceConstant.PRIMARY_EXCHANGE) == 0){
            final int size = issuers.entities().size();                
            int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
            LOG.info("Received GET request for {} issuers in exchange {}", size, exchangeCode);
            for (final Issuer issuer : issuers.entities()) {
                LOG.info("Sending issuer {} to sink[{}]", issuer.code(), sender.sinkId());
                this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, responseSeq, ResultType.OK, issuer);
                responseSeq++;
            }
            messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseSeq, ResultType.OK);
    	}
    	else{
            throw new IllegalArgumentException("Received GET request for issuer in an invalid exchange " + exchangeCode);    		
    	}
    }
    
    private void handleDividendCurveGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }

        Optional<String> exchangeCode = Parameter.getParameterValueString(parameters, ParameterType.EXCHANGE_CODE);
    	if (exchangeCode.isPresent() && exchangeCode.get().compareTo(ServiceConstant.PRIMARY_EXCHANGE) == 0){
    		final int size = dividendCurves.size();                
    		int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
    		LOG.info("Received GET request for {} dividend curves in exchange {}", size, exchangeCode);
    		for (final DividendCurve dividend : dividendCurves.values()) {
    			LOG.info("Sending dividend curve {} to sink[{}]", dividend.secSid(), sender.sinkId());
    			this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, responseSeq, ResultType.OK, dividend);
    			responseSeq++;
    		}
    		messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseSeq, ResultType.OK);
    	}
    	else {
    		throw new IllegalArgumentException("Received GET request for dividend curves in an invalid exchange " + exchangeCode);        		
    	}
    }
    
    private void handlePositionGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }

        Optional<Long> securitySid = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
    	if (securitySid.isPresent()){
        	int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
    		MutableDirectBuffer buffer = positions.get(securitySid.get().longValue());
    		if (buffer != positions.defaultReturnValue()){
            	messenger.responseSender().sendResponse(sender,
        				request.clientKey(),
        				responseMsgSeq++,
        				buffer,
        				buffer.capacity(),
        				PositionSbeEncoder.TEMPLATE_ID,
        				PositionSbeEncoder.BLOCK_LENGTH);
        	}
        	messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK); 
    	}
    	else {
	    	handleVanillaTemplateTypeGetRequest(sender, request, parameters, positions.values(), PositionSbeEncoder.TEMPLATE_ID, PositionSbeEncoder.BLOCK_LENGTH);    		
    	}
    }
    
    private void handleVanillaTemplateTypeGetRequest(final MessageSinkRef sender, 
    		final RequestSbeDecoder request, 
    		final ImmutableListMultimap<ParameterType, Parameter> parameters,
    		ObjectCollection<MutableDirectBuffer> buffers,
    		int templateId,
    		int blockLength) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }

        int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
    	for (MutableDirectBuffer buffer : buffers){
    		messenger.responseSender().sendResponse(sender, 
    				request.clientKey(), 
    				responseMsgSeq++, 
    				buffer,
    				buffer.capacity(), 
    				templateId, 
    				blockLength);
    	}
    	messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);    	
    }
    
    private void handleSecurityGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }

        Optional<String> exchangeCode = Parameter.getParameterValueString(parameters, ParameterType.EXCHANGE_CODE);
    	if (exchangeCode.isPresent() && exchangeCode.get().compareTo(ServiceConstant.PRIMARY_EXCHANGE) == 0){
            final int size = securities.entities().size();                
            int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
            LOG.info("Received GET request for {} securities in exchange {}", box(size), exchangeCode);
            for (final Security security : securities.entities()) {
                LOG.info("Sending security {} to sink[{}]", security.code(), sender.sinkId());
                this.messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, responseSeq, ResultType.OK, security);
                responseSeq++;
            }
            messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseSeq, ResultType.OK);
    	}
    	else {
            throw new IllegalArgumentException("Received GET request for securities in an invalid exchange " + exchangeCode);    		
    	}
    }
    
    private void handleStrategyTypeGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }

        int count = 0;
        LOG.info("Received GET request for strategy types");
        strategyServiceHelper.replyStrategyTypeGetRequest(sender, request, messenger, count);
        // send the isLast response to end the request
        this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, count, ResultType.OK);
    }
    
    private void handleScoreBoardGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) {
        if (request.requestType() != RequestType.GET) {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for PeristService");
        }
        int count = 0;
        LOG.info("Received GET request for scoreboard");
        for (final ScoreBoard scoreBoard : scoreBoards.values()) {
            LOG.info("Sending scoreboard {} to sink[{}]", box(scoreBoard.getSecSid()), sender.sinkId());
            count = ScoreBoardSender.sendScoreBoard(this.messenger.responseSender(), sender, request.clientKey(), BooleanType.FALSE, count, ResultType.OK, scoreBoard);
        }
        // send the isLast response to end the request
        this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, count, ResultType.OK);
    }

    private void handleNoteRequest(DirectBuffer buffer, int offset, final MessageSinkRef sender, final RequestSbeDecoder requestSbe, final ImmutableListMultimap<ParameterType, Parameter> parameters) throws Exception {
    	switch (requestSbe.requestType()){
    	case CREATE:
    	{
    		RequestDecoder.wrapEmbeddedNote(buffer, offset, parameters.size(), requestSbe, noteSbeDecoder);
        	Note note = notePersister.insertNote(noteSbeDecoder);
        	notes.add(note);
        	
        	// Send back to request
        	this.messenger.responseSender().sendSbeEncodable(sender, requestSbe.clientKey(), BooleanType.TRUE, ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK, note);
    		break;
    	}
    	case UPDATE:
    	{
    		RequestDecoder.wrapEmbeddedNote(buffer, offset, parameters.size(), requestSbe, noteSbeDecoder);
    		Note note = notes.get(noteSbeDecoder.noteSid());
    		note = notePersister.updateNote(noteSbeDecoder, note);
    		
    		// Send back to request
        	this.messenger.responseSender().sendSbeEncodable(sender, requestSbe.clientKey(), BooleanType.TRUE, ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK, note);
    		break;
    	}
    	case GET:
            int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
            for (final Note note: notes.entities()) {
            	this.messenger.responseSender().sendSbeEncodable(sender, requestSbe.clientKey(), BooleanType.FALSE, responseMsgSeq++, ResultType.OK, note);
            }
            this.messenger.responseSender().sendResponse(sender, requestSbe.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
    		break;
    	default:
    		LOG.warn("Unsupported request type for Note [requestType:{}]", requestSbe.requestType());
    		this.messenger.responseSender().sendResponse(sender, requestSbe.clientKey(), BooleanType.TRUE, ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.ILLEGAL_ARGUMENT_OPERATION);
    		break;
    	}
    }
    
    final private StrategyServiceHelper.UpdateHandler strategyUpdateHandler = new StrategyServiceHelper.UpdateHandler() {
        @Override
        public void onStrategyTypeParamsUpdated(byte senderId, StrategyType strategyType, GenericStrategyTypeParams params) {
        }

        @Override
        public void onStrategyUndParamsUpdated(byte senderId, StrategyType strategyType, Security underlying, GenericUndParams params) {
            strategyPersister.persistStrategyUndParams(strategyType.name(), underlying.code(), params);
        }

        @Override
        public void onStrategyIssuerParamsUpdated(byte senderId, StrategyType strategyType, Issuer issuer, GenericIssuerParams params) {
            strategyPersister.persistStrategyIssuerParams(strategyType.name(), issuer.code(), params);                    
        }

        @Override
        public void onStrategyWrtParamsUpdated(byte senderId, StrategyType strategyType, Security security, GenericWrtParams params) {
            strategyPersister.persistStrategyWrtParams(strategyType.name(), security.code(), params);
        }

        @Override
        public void onStrategyIssuerUndParamsUpdated(byte senderId, StrategyType strategyType, Issuer issuer, Security underlying, GenericIssuerUndParams params) {
            strategyPersister.persistStrategyIssuerUndParams(strategyType.name(), issuer.code(), underlying.code(), params);                    
        }

        @Override
        public void onStrategySwitchUpdated(byte senderId, StrategyType strategyType, StrategySwitch strategySwitch) {
            strategyPersister.persistStrategySwitch(strategyType.name(), strategySwitch.onOff());
        }
        
        @Override
        public void onIssuerSwitchUpdated(byte senderId, Issuer issuer, StrategySwitch strategySwitch) {
            strategyPersister.persistIssuerSwitch(issuer.code(), strategySwitch.onOff());
        }

        @Override
        public void onUnderlyingSwitchUpdated(byte senderId, Security underlying, StrategySwitch strategySwitch) {
            strategyPersister.persistUnderlyingSwitch(underlying.code(), strategySwitch.onOff());
        }

        @Override
        public void onWarrantSwitchUpdated(byte senderId, Security warrant, StrategySwitch strategySwitch) {
            strategyPersister.persistWarrantSwitch(warrant.code(), strategySwitch.onOff());
        }

        @Override
        public void onDayOnlyWarrantSwitchUpdated(byte senderId, Security warrant, StrategySwitch strategySwitch) {
        }
        
    };
        
    private Handler<GreeksSbeDecoder> greeksHandler = new Handler<GreeksSbeDecoder>() {
        final private Greeks greeks = Greeks.of(Security.INVALID_SECURITY_SID);
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, GreeksSbeDecoder sbe) {
            try {
                LOG.debug("Received greeks for security {}", box(sbe.secSid()));
                final Security security = securities.get(sbe.secSid());
                if (security != null) {
                    greeks.decodeFrom(sbe);
                    strategyPersister.persistPricing(security.code(), greeks);
                }
            }
            catch (final Exception e) {
                LOG.error("Failed to persist GreeksSbeDecoder", e);
            }
        }
    };

    private Handler<OrderSbeDecoder> orderHandler = new Handler<OrderSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder order) {
            try {
            	MutableDirectBuffer orderBuffer = orders.get(order.orderSid());
            	if (orderBuffer == orders.defaultReturnValue()){
    	    		orderBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderSbeEncoder.BLOCK_LENGTH));
            		orders.put(order.orderSid(), orderBuffer);
            	}
            	orderBuffer.putBytes(0, buffer, offset + header.encodedLength(), OrderSbeEncoder.BLOCK_LENGTH);
                orderTradePersister.persistOrder(order);
            } catch (final Exception e) {
                LOG.error("Failed to persist order with OrderSid {}", order.orderSid(), e);
            }
        }
    };
    
    private Handler<TradeSbeDecoder> tradeHandler = new Handler<TradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder trade) {
            try {
            	MutableDirectBuffer tradeBuffer = trades.get(trade.tradeSid());
            	if (tradeBuffer == trades.defaultReturnValue()){
    	    		tradeBuffer = new UnsafeBuffer(ByteBuffer.allocate(TradeSbeDecoder.BLOCK_LENGTH));
    	    		trades.put(trade.tradeSid(), tradeBuffer);
            	}
            	tradeBuffer.putBytes(0, buffer, offset + header.encodedLength(), TradeSbeDecoder.BLOCK_LENGTH);
                orderTradePersister.persistTrade(trade);
            } catch (final Exception e) {
                LOG.error("Failed to persist trade with TradeSid {}", trade.tradeSid(), e);
            }
        }
    };

    private Handler<ScoreBoardSbeDecoder> scoreBoardHandler = new Handler<ScoreBoardSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSbeDecoder sbe) {
            try {
                LOG.debug("Received target scoreboard for warrant {}", box(sbe.secSid()));
                final Security security = securities.get(sbe.secSid());
                if (security != null) {
                    ScoreBoard scoreBoard = scoreBoards.get(sbe.secSid());
                    if (scoreBoard == null) {
                        scoreBoard = ScoreBoard.of();
                        scoreBoard.setSecSid(sbe.secSid());
                        scoreBoards.put(sbe.secSid(), scoreBoard);
                    }
                    scoreBoardDecoder.decodeScoreBoard(sbe, scoreBoard);
                    strategyPersister.persistScoreBoard(security.code(), scoreBoard);
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist ScoreBoardSbeDecoder", e);
            }   
        }        
    };    
}
