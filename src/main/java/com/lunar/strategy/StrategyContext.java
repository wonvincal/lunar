package com.lunar.strategy;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.LongEntityManager;
import com.lunar.entity.StrategyType;
import com.lunar.strategy.parameters.BucketOutputParams;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.parameters.IssuerInputParams;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class StrategyContext {
    @SuppressWarnings("unused")
    private static final Logger LOG = LogManager.getLogger(StrategyContext.class);

    private final StrategyType m_strategyType;
    private final Long2ObjectOpenHashMap<StrategySecurity> m_underlyings;
    private final Long2ObjectOpenHashMap<StrategySecurity> m_warrants;
    private final LongEntityManager<StrategyIssuer> m_issuers;
    private final StrategyOrderService m_orderService;
    private final StrategyInfoSender m_strategyInfoSender;
    private final GenericStrategyTypeParams m_strategyTypeParams;
    private final Long2ObjectOpenHashMap<GenericUndParams> m_undParams;
    private final Long2ObjectOpenHashMap<GenericIssuerParams> m_issuerParams;
    private final Long2ObjectOpenHashMap<GenericIssuerUndParams> m_issuerUndParams;
    private final Long2ObjectOpenHashMap<GenericWrtParams> m_wrtParams;
    private final Long2ObjectOpenHashMap<BucketOutputParams> m_bucketParams;
	
	public StrategyContext(final StrategyType strategyType, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final LongEntityManager<StrategyIssuer> issuers, final StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender) {
	    m_strategyType = strategyType;
		m_underlyings = underlyings;
		m_warrants = warrants;
		m_issuers = issuers;
		m_orderService = orderService;
		m_strategyTypeParams = new GenericStrategyTypeParams();
		m_strategyTypeParams.strategyId(strategyType.sid());
		m_undParams = new Long2ObjectOpenHashMap<GenericUndParams>(underlyings.size());
		m_wrtParams = new Long2ObjectOpenHashMap<GenericWrtParams>(m_warrants.size());
		m_issuerParams = new Long2ObjectOpenHashMap<GenericIssuerParams>(m_issuers.entities().size());
		m_issuerUndParams = new Long2ObjectOpenHashMap<GenericIssuerUndParams>(underlyings.size() * m_issuers.entities().size());
		m_bucketParams = new Long2ObjectOpenHashMap<BucketOutputParams>(m_warrants.size());
		m_strategyInfoSender = strategyInfoSender;
	}
	
	public StrategyType getStrategyType() {
	    return m_strategyType;
	}
	
	public Long2ObjectOpenHashMap<StrategySecurity> getUnderlyings() {
    	return m_underlyings;
    }
    
	public Long2ObjectOpenHashMap<StrategySecurity> getWarrants() {
    	return m_warrants;
    }
	
    public LongEntityManager<StrategyIssuer> getIssuers() {
        return m_issuers;
    }
    
	public StrategyOrderService getOrderService() {
    	return m_orderService;
    }
	
	public StrategyInfoSender getStrategyInfoSender() {
	    return m_strategyInfoSender;
	}
	
	public GenericStrategyTypeParams getStrategyTypeParams() {
	    return m_strategyTypeParams;
	}	
	
    public GenericUndParams getStrategyUndParams(final long underlyingSid, final boolean createDefaultIfNone) {
        GenericUndParams undParams = m_undParams.get(underlyingSid);
        if (undParams == null && createDefaultIfNone) {
            final StrategySecurity underlying = this.m_underlyings.get(underlyingSid);
            if (underlying == null) {
                return null;
            }
            undParams = new GenericUndParams();
            undParams.strategyId(m_strategyType.sid());
            undParams.underlyingSid(underlyingSid);            
            m_strategyTypeParams.defaultUndInputParams().copyTo(undParams);
            m_undParams.put(underlyingSid, undParams);
        }
        return undParams;
    }

    public GenericIssuerParams getStrategyIssuerParams(final long issuerSid, final boolean createDefaultIfNone) {
        GenericIssuerParams issuerParams = m_issuerParams.get(issuerSid);
        if (issuerParams == null && createDefaultIfNone) {
            final StrategyIssuer issuer = this.m_issuers.get(issuerSid);
            if (issuer == null) {
                return null;
            }
            issuerParams = new GenericIssuerParams();
            issuerParams.strategyId(m_strategyType.sid());
            issuerParams.issuerSid(issuerSid);
            m_strategyTypeParams.defaultIssuerInputParams().copyTo(issuerParams);
            m_issuerParams.put(issuerSid, issuerParams);
        }
        return issuerParams;
    }

    public Collection<GenericIssuerUndParams> getAllStrategyIssuerUndParams(){
    	return m_issuerUndParams.values();
    }
    
    public GenericIssuerUndParams getStrategyIssuerUndParams(final long issuerSid, final long undSid, final boolean createDefaultIfNone) {
    	long issuerUndSid = GenericIssuerUndParams.convertToIssuerUndSid(issuerSid, undSid);
        GenericIssuerUndParams issuerUndParams = m_issuerUndParams.get(issuerUndSid);
        if (issuerUndParams == null && createDefaultIfNone) {
            final StrategyIssuer issuer = this.m_issuers.get(issuerSid);
            if (issuer == null) {
                return null;
            }
            final StrategySecurity underlying = this.m_underlyings.get(undSid);
            if (underlying == null) {
                return null;
            }
            issuerUndParams = new GenericIssuerUndParams();
            issuerUndParams.strategyId(m_strategyType.sid());
            issuerUndParams.issuerSid(issuerSid);
            issuerUndParams.undSid(undSid);
            issuerUndParams.issuerUndSid(issuerUndSid);
            m_strategyTypeParams.defaultIssuerUndInputParams().copyTo(issuerUndParams);
            m_issuerUndParams.put(issuerUndSid, issuerUndParams);
        }
        return issuerUndParams;
    }

    public GenericWrtParams getStrategyWrtParams(final long secSid, final boolean createDefaultIfNone) {
        GenericWrtParams wrtParams = m_wrtParams.get(secSid);
        if (wrtParams == null && createDefaultIfNone) {
            final StrategySecurity warrant = this.m_warrants.get(secSid);
            if (warrant == null) {
                return null;
            }
            wrtParams = new GenericWrtParams();
            wrtParams.strategyId(m_strategyType.sid());
            wrtParams.secSid(secSid); 
            // copy default warrant parameters
            m_strategyTypeParams.defaultWrtInputParams().copyTo(wrtParams);
            // find the issuer parameter for the warrant and copy it over
            final GenericIssuerParams defaultIssuerParams = getStrategyIssuerParams(warrant.issuerSid(), false);
            if (defaultIssuerParams != null) {
                defaultIssuerParams.copyTo((IssuerInputParams)wrtParams);
            }
            m_wrtParams.put(secSid, wrtParams);
        }
        return wrtParams;
    }
    
    public BucketOutputParams getBucketUpdateParams(final long secSid, final boolean createDefaultIfNone) {
        BucketOutputParams params = m_bucketParams.get(secSid);
        if (params == null && createDefaultIfNone) {
            final StrategySecurity warrant = this.m_warrants.get(secSid);
            if (warrant == null) {
                return null;
            }
            params = new BucketOutputParams();
            params.strategyId(m_strategyType.sid());
            params.secSid(secSid); 

            m_bucketParams.put(secSid, params);
        }
        return params;
    }
    
    private static final long LOWER_ORDER_MASK = 0x00000000FFFFFFFFl;
    protected static long convertToIssuerUndSid(final long issuerSid, final long undSid){
        return (issuerSid << 32) | (undSid & LOWER_ORDER_MASK);
    }

}
