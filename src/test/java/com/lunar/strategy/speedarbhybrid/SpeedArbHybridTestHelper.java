package com.lunar.strategy.speedarbhybrid;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import com.lunar.config.StrategyServiceConfig;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.StrategyFactory;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyManager;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategyStaticScheduleSetupTask;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class SpeedArbHybridTestHelper {
    final protected Collection<Security> securityList;
    final protected LongEntityManager<StrategySecurity> securities;
    final protected LongEntityManager<StrategyIssuer> issuers;
    final protected MarketTicksStrategyScheduler scheduler;
    final protected SpeedArbBtParams btParams;
    
    final protected StrategyServiceConfig specificConfig;    
    protected StrategyManager strategyManager;
    
    static final private long OMDC_SID_END = 99999L;
    static final public long HSI_SID = OMDC_SID_END + 1;
    static final public long HSI_FUTURES_SID = OMDC_SID_END + 2;
    static final public long HSCEI_SID = OMDC_SID_END + 3;
    static final public long HSCEI_FUTURES_SID = OMDC_SID_END + 4;    

    public SpeedArbHybridTestHelper(final int expectedNumWrts, final int expectedNumUnds, final int expectedNumWrtsPerUnd) {
        specificConfig = new StrategyServiceConfig(1,
                "btTest", 
                "btTest", 
                "btTest",
                ServiceType.StrategyService,
                Optional.of("com.lunar.service.StrategyService"), 
                0,
                Optional.of(1024),
                Optional.of(1024),
                Optional.empty(),
                Optional.empty(),
                true,
                false,
                Duration.ofSeconds(1),
                false,
                "",
                expectedNumUnds,        // numUnderlyings
                expectedNumWrts,        // numWarrants
                expectedNumWrtsPerUnd,  // numWrtsPerUnd
                "",
                "",
                true, //autoSwitchOn
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        securityList = new ArrayList<Security>(specificConfig.numUnderlyings() + specificConfig.numWarrants());
        securities = new LongEntityManager<StrategySecurity>(specificConfig.numUnderlyings() + specificConfig.numWarrants());
        issuers = new LongEntityManager<StrategyIssuer>(100);
        
        scheduler = new MarketTicksStrategyScheduler();
        
        btParams = new SpeedArbBtParams();
    }

    public void addSecurity(final StrategySecurity security) {
        securities.add(security);
        securityList.add(security);
    }
    
    public void addIssuer(final StrategyIssuer issuer) {
        issuers.add(issuer);
    }

    public Collection<Security> securityList() {
        return securityList;
    }
    
    public LongEntityManager<StrategySecurity> securities() {
        return securities;
    }
    
    public LongEntityManager<StrategyIssuer> issuers() {
        return issuers;
    }
    
    public MarketTicksStrategyScheduler scheduler() {
        return scheduler;
    }

    public SpeedArbBtParams btParams() {
        return btParams;
    }
    
    public StrategyManager strategyManager() {
        return strategyManager;
    }
    
    public void setupStrategyManager(final StrategyOrderService orderService) throws Exception {
        setupStrategyManager(orderService, createParamsUpdateHandler());
    }
    
    public void setupStrategyManager(final StrategyOrderService orderService, final StrategyInfoSender paramsUpdateHandler) throws Exception {
        strategyManager = new StrategyManager(specificConfig, securities, issuers, orderService, paramsUpdateHandler, scheduler);
        new StrategyStaticScheduleSetupTask(scheduler, strategyManager, specificConfig.canAutoSwitchOn());
        strategyManager.registerStrategyFactory(strategyFactory());
    }
    
    public void createStrategies() throws Exception {
        strategyManager.onEntitiesLoaded();
        btParams.setupStrategyManager(strategyManager);
        strategyManager.setupStrategies();
    }
    
    public void readInstruments(final String defFolder, final String defFile, final Set<Long> warrantsFilter, final Set<Long> underlyingFilter, final Set<String> issuerFilter) throws IOException, SQLException {
        final LongOpenHashSet underlyingSids = new LongOpenHashSet(200);
        final Object2LongOpenHashMap<String> issuerCodes = new Object2LongOpenHashMap<String>(20);
        int sortOrder = 1;
        final Path path = FileSystems.getDefault().getPath(defFolder, defFile);
        final BufferedReader reader = Files.newBufferedReader(path, Charset.forName("UTF-8"));
        String line = null;
        reader.readLine();
        while ((line = reader.readLine()) != null) {
            final String[] fields = line.split(",");
            final String undSymbol = fields[2];
            final long undSid;
            if (undSymbol.equals(ServiceConstant.HSI_CODE)) {
                undSid = HSI_SID;
            }
            else if (undSymbol.equals(ServiceConstant.HSCEI_CODE)) {
                undSid = HSCEI_SID;
            }
            else {
                undSid = Long.parseLong(fields[2]);
            }
            final long instrumentSid = Long.parseLong(fields[1]);
            final String symbol = fields[1];
            final String issuerShortName = fields[6];
            if (warrantsFilter != null && !warrantsFilter.contains(instrumentSid))
                continue;
            if (underlyingFilter != null && !underlyingFilter.contains(undSid))
                continue;
            if (issuerFilter != null && !issuerFilter.contains(issuerShortName))
                continue;
            
            if (fields.length < 9)
                continue;            
            final int convRatio = (int)(Float.parseFloat(fields[4]) * 1000.0f);
            final PutOrCall putOrCall = fields[3].equals("Call") ? PutOrCall.CALL : PutOrCall.PUT;
            final OptionStyle optionStyle = undSid > 100000 ? OptionStyle.EUROPEAN : OptionStyle.ASIAN;
            final int strikePrice = 123456;
            if (!btParams.isTradable(undSid)) {
                continue;
            }
            if (!underlyingSids.contains(undSid)) {
                underlyingSids.add(undSid);
                final StrategySecurity underlying = createUnderlying(undSid, undSymbol);
                securities.add(underlying);
                securityList.add(underlying);
            }
            final StrategyIssuer issuer;
            if (!issuerCodes.containsKey(issuerShortName)) {
                final int issuerSid = issuers.entities().size() + 1;
                issuer = StrategyIssuer.of(issuerSid, issuerShortName, issuerShortName, ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX);
                issuerCodes.put(issuerShortName, issuerSid);
                issuers.add(issuer);                
            }
            else {
                issuer = issuers.get(issuerCodes.getLong(issuerShortName));
            }
            
            int lotSize;
            final int mmSizeColumn;
            if (fields.length == 10) {
                lotSize = (int)Float.parseFloat(fields[7]);
                if (lotSize == 0) {
                    lotSize = 10000;
                }
                mmSizeColumn = 8;
            }
            else {
                lotSize = 1;
                mmSizeColumn = 7;
            }
            
            final StrategySecurity warrant = createWarrant(instrumentSid, symbol, undSid, putOrCall, optionStyle, strikePrice, convRatio, (int)issuer.sid(), lotSize);
            warrant.sortOrder(sortOrder++);
            securities.add(warrant);
            securityList.add(warrant);
            
            final int mmBidSize = (int)(Float.parseFloat(fields[mmSizeColumn]));
            final int mmAskSize = (int)(Float.parseFloat(fields[mmSizeColumn + 1]));
            final int delta = (int)(Float.parseFloat(fields[5]) * 100000.0f);
            btParams.setWarrantParams(warrant.sid(), mmBidSize, mmAskSize, delta);            
        }
    }
    
    public void registerFutures(final String hsiFuturesCode, final String hsceiFuturesCode) {
        final StrategySecurity hsiFutures = createFutures(HSI_FUTURES_SID, hsiFuturesCode, HSI_SID);
        final StrategySecurity hsceiFutures = createFutures(HSCEI_FUTURES_SID, hsceiFuturesCode, HSCEI_SID);
        securities.add(hsiFutures);
        securities.add(hsceiFutures);
        securityList.add(hsiFutures);
        securityList.add(hsceiFutures);
    }
    
    protected StrategyFactory strategyFactory() {
        return new SpeedArbHybridFactory();
    }
    
    protected StrategyInfoSender createParamsUpdateHandler() {
        return StrategyInfoSender.NULL_PARAMS_SENDER;        
    }

    protected StrategySecurity createUnderlying(long undSid, String undSymbol) {
        final SecurityType securityType = undSid > OMDC_SID_END ? SecurityType.INDEX : SecurityType.STOCK;
        return createSecurity(undSid, securityType, undSymbol, 0L, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, -1, 1);    
    }
    
    protected StrategySecurity createWarrant(long sid, String code, long undSecSid, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize) {
    	return createSecurity(sid, SecurityType.WARRANT, code, undSecSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize);
    }
    
    protected StrategySecurity createFutures(long sid, String code, long indexSecSid) {
        return createSecurity(sid, SecurityType.FUTURES, code, indexSecSid, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, -1, 1);
    }
    
    protected StrategySecurity createSecurity(long sid, SecurityType securityType, String code, long undSecSid, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize) {
        final SpreadTable spreadTable = SpreadTableBuilder.get(securityType);
        final MarketOrderBook orderBook = MarketOrderBook.of(sid, 10, spreadTable, Integer.MIN_VALUE, Integer.MIN_VALUE);
        return StrategySecurity.of(sid, securityType, code, 0, undSecSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, true, spreadTable, orderBook);
    }

}
