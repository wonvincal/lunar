package com.lunar.strategy.speedarbhybrid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.SecurityType;
import com.lunar.pricing.Greeks;
import com.lunar.service.ServiceConstant;

public interface SpreadTableScaleFormulaBridge {
    static final Logger LOG = LogManager.getLogger(SpreadTableScaleFormulaBridge.class);

    public static SpreadTableScaleFormulaBridge of(final int wrtPriceScale, final int undPriceScale) {
        return new GenericWrtSpreadTableScaleFormulaBridge(wrtPriceScale, undPriceScale);
    }
    
    public static SpreadTableScaleFormulaBridge of(final SecurityType underlyingType) {
        if (underlyingType.equals(SecurityType.INDEX)) {
            return INDEX_BRIDGE;
        }
        return EQUITY_BRIDGE;
    }
    
    //TODO test that GenericWrtSpreadTableScaleFormulaBridge match with EquityWrtSpreadTableScaleFormulaBridge
    //static final SpreadTableScaleFormulaBridge EQUITY_BRIDGE = new TestGenericWrtSpreadTableScaleFormulaBridge(new EquityWrtSpreadTableScaleFormulaBridge(), 1000, 1000);
    static final SpreadTableScaleFormulaBridge EQUITY_BRIDGE = new EquityWrtSpreadTableScaleFormulaBridge();
    //TODO to optimize
    static final SpreadTableScaleFormulaBridge INDEX_BRIDGE = new GenericWrtSpreadTableScaleFormulaBridge(1000, 1);

    public float calcPricePerUnderlyingTick(final int undTickSize, final Greeks greeks, final int convRatio);
    public float calcAdjustedDelta(final long spotPrice, final Greeks greeks);
    public long calcSpotChangeForPriceChange(final int priceChange, final float adjustedDeltaC);
    public long calcSpotChangeForPriceChange(final int priceChange, final int convRatio, final float adjustedDelta);
    public long calcSpotChangeForPriceChangeForCall(final int priceChange, final int convRatio, final float adjustedDelta, final Greeks greeks);
    public long calcSpotChangeForPriceChangeForPut(final int priceChange, final int convRatio, final float adjustedDelta, final Greeks greeks);
    public float calcPriceChangeFloatForSpotChange(final long spotChange, final int convRatio, final float adjustedDelta);
    public long calcSpotBufferFromTickBuffer(final int wrtTickSize, final int tickBuffer, final float adjustedDeltaC);
    
    public class TestGenericWrtSpreadTableScaleFormulaBridge implements SpreadTableScaleFormulaBridge {
        private final SpreadTableScaleFormulaBridge refBridge;
        private final SpreadTableScaleFormulaBridge genericBridge;
        
        public TestGenericWrtSpreadTableScaleFormulaBridge(final SpreadTableScaleFormulaBridge refBridge, final int wrtPriceScale, final int undPriceScale) {
            this.refBridge = refBridge;
            this.genericBridge = new GenericWrtSpreadTableScaleFormulaBridge(wrtPriceScale, undPriceScale);
        }

        @Override
        public float calcPricePerUnderlyingTick(int undTickSize, Greeks greeks, int convRatio) {
            float refResult = this.refBridge.calcPricePerUnderlyingTick(undTickSize, greeks, convRatio);
            float genericResult = this.genericBridge.calcPricePerUnderlyingTick(undTickSize, greeks, convRatio);
            if (Math.abs((int)(refResult * 1000) - (int)(genericResult * 1000)) > 1) {
                LOG.error("Mismatch on calcPricePerUnderlyingTick! Need to debug!");
            }
            return refResult;
        }

        @Override
        public float calcAdjustedDelta(long spotPrice, Greeks greeks) {
            float refResult = this.refBridge.calcAdjustedDelta(spotPrice, greeks);
            float genericResult = this.genericBridge.calcAdjustedDelta(spotPrice, greeks);
            if (Math.abs((int)(refResult * 1000) - (int)(genericResult * 1000)) > 1) {
                LOG.error("Mismatch on calcAdjustedDelta! Need to debug!");
            }
            return refResult;
        }

        @Override
        public long calcSpotChangeForPriceChange(int priceChange, float adjustedDeltaC) {
            long refResult = this.refBridge.calcSpotChangeForPriceChange(priceChange, adjustedDeltaC);
            long genericResult = this.genericBridge.calcSpotChangeForPriceChange(priceChange, adjustedDeltaC);
            if (Math.abs(refResult - genericResult) > 1) {
                LOG.error("Mismatch on calcSpotChangeForPriceChange with adjustedDeltaC! Need to debug!");
            }
            return refResult;
        }

        @Override
        public long calcSpotChangeForPriceChange(int priceChange, int convRatio, float adjustedDelta) {
            long refResult = this.refBridge.calcSpotChangeForPriceChange(priceChange, convRatio, adjustedDelta);
            long genericResult = this.genericBridge.calcSpotChangeForPriceChange(priceChange, convRatio, adjustedDelta);
            if (Math.abs(refResult - genericResult) > 1) {
                LOG.error("Mismatch on calcSpotChangeForPriceChange with convRatio! Need to debug!");
            }
            return refResult;
        }

        @Override
        public long calcSpotChangeForPriceChangeForCall(int priceChange, int convRatio, float adjustedDelta, Greeks greeks) {
            long refResult = this.refBridge.calcSpotChangeForPriceChangeForCall(priceChange, convRatio, adjustedDelta, greeks);
            long genericResult = this.genericBridge.calcSpotChangeForPriceChangeForCall(priceChange, convRatio, adjustedDelta, greeks);
            if (Math.abs(refResult - genericResult) > 1) {
                LOG.error("Mismatch on calcSpotChangeForPriceChangeForCall! Need to debug!");
            }
            return refResult;
        }

        @Override
        public long calcSpotChangeForPriceChangeForPut(int priceChange, int convRatio, float adjustedDelta, Greeks greeks) {
            long refResult = this.refBridge.calcSpotChangeForPriceChangeForPut(priceChange, convRatio, adjustedDelta, greeks);
            long genericResult = this.genericBridge.calcSpotChangeForPriceChangeForPut(priceChange, convRatio, adjustedDelta, greeks);
            if (Math.abs(refResult - genericResult) > 1) {
                LOG.error("Mismatch on calcSpotChangeForPriceChangeForPut! Need to debug!");
            }
            return refResult;
        }

        @Override
        public float calcPriceChangeFloatForSpotChange(long spotChange, int convRatio, float adjustedDelta) {
            float refResult = this.refBridge.calcPriceChangeFloatForSpotChange(spotChange, convRatio, adjustedDelta);
            float genericResult = this.genericBridge.calcPriceChangeFloatForSpotChange(spotChange, convRatio, adjustedDelta);
            if (Math.abs((int)(refResult * 1000) - (int)(genericResult * 1000)) > 1) {
                LOG.error("Mismatch on calcPriceChangeFloatForSpotChange! Need to debug!");
            }
            return refResult;
        }

        @Override
        public long calcSpotBufferFromTickBuffer(int wrtTickSize, int tickBuffer, float adjustedDeltaC) {
            long refResult = this.refBridge.calcSpotBufferFromTickBuffer(wrtTickSize, tickBuffer, adjustedDeltaC);
            long genericResult = this.genericBridge.calcSpotBufferFromTickBuffer(wrtTickSize, tickBuffer, adjustedDeltaC);
            if (Math.abs(refResult - genericResult) > 1) {
                LOG.error("Mismatch on calcSpotBufferFromTickBuffer! Need to debug!");
            }
            return refResult;
        }        
    }
    
    public class GenericWrtSpreadTableScaleFormulaBridge implements SpreadTableScaleFormulaBridge {        
        private final long wrtPriceScale;
        private final long undPriceScale;
        private final long spotScale;
        private final long wrtToSpotScaleFactor;
        
        public GenericWrtSpreadTableScaleFormulaBridge(final int wrtPriceScale, final int undPriceScale) {
            this.wrtPriceScale = wrtPriceScale;
            this.undPriceScale = undPriceScale;
            this.spotScale = this.undPriceScale * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
            this.wrtToSpotScaleFactor = spotScale / wrtPriceScale;
        }
        
        @Override
        public float calcPricePerUnderlyingTick(int undTickSize, Greeks greeks, int convRatio) {
            return wrtPriceScale * Math.abs(undTickSize * greeks.delta() + 0.5f * (undTickSize * undTickSize) * greeks.gamma() / undPriceScale) / (convRatio * 100.0f * undPriceScale) ;
        }

        @Override
        public float calcAdjustedDelta(final long spotPrice, final Greeks greeks) {
            return ((float)(spotPrice - greeks.refSpot() * ServiceConstant.WEIGHTED_AVERAGE_SCALE) * greeks.gamma() / spotScale + (float)greeks.delta());
        }

        @Override
        public long calcSpotChangeForPriceChange(final int priceChange, final float adjustedDeltaC) {
            return (long)(priceChange * wrtToSpotScaleFactor / adjustedDeltaC);
        }

        @Override
        public long calcSpotChangeForPriceChange(final int priceChange, final int convRatio, final float adjustedDelta) {
            return (long)(priceChange * convRatio * 100L * wrtToSpotScaleFactor / adjustedDelta);
        }

        @Override
        public long calcSpotChangeForPriceChangeForCall(final int priceChange, final int convRatio, final float adjustedDelta, final Greeks greeks) {
            if (greeks.gamma() == 0) {
                return calcSpotChangeForPriceChange(priceChange, convRatio, adjustedDelta);
            }
            return (long)(spotScale * (-adjustedDelta + Math.sqrt(adjustedDelta*adjustedDelta + greeks.gamma() * convRatio * 200.0 * priceChange / wrtPriceScale)) / (greeks.gamma())) ;
        }

        @Override
        public long calcSpotChangeForPriceChangeForPut(final int priceChange, final int convRatio, final float adjustedDelta, final Greeks greeks) {
            if (greeks.gamma() == 0) {
                return calcSpotChangeForPriceChange(priceChange, convRatio, adjustedDelta);
            }
            return (long)(spotScale * (-adjustedDelta - Math.sqrt(adjustedDelta*adjustedDelta + greeks.gamma() * convRatio * 200.0 * priceChange / wrtPriceScale)) / (greeks.gamma())) ;
        }
        
        @Override
        public float calcPriceChangeFloatForSpotChange(final long spotChange, final int convRatio, final float adjustedDelta) {
            return spotChange * adjustedDelta * wrtPriceScale / (convRatio * 100 * spotScale);
        }

        @Override
        public long calcSpotBufferFromTickBuffer(final int wrtTickSize, final int tickBuffer, final float adjustedDeltaC) {
            /// 1000 1000 / 1 = 1m 
            return (long)(wrtTickSize * tickBuffer * wrtToSpotScaleFactor / (1000 * adjustedDeltaC));
        }

    }

    public class EquityWrtSpreadTableScaleFormulaBridge implements SpreadTableScaleFormulaBridge {
        static private final long WRT_PRICE_SCALE = 1000;
        static private final long UND_PRICE_SCALE = 1000;
        static private final long FULL_SPOT_SCALE = ServiceConstant.WEIGHTED_AVERAGE_SCALE * UND_PRICE_SCALE;

        @Override
        public float calcPricePerUnderlyingTick(int undTickSize, Greeks greeks, int convRatio) {
            return Math.abs(undTickSize * greeks.delta() + 0.5f * (undTickSize * undTickSize) * greeks.gamma() / UND_PRICE_SCALE) / (convRatio * 100.0f) ;
        }
        
        @Override
        public float calcAdjustedDelta(final long spotPrice, final Greeks greeks) {
            return ((float)(spotPrice - greeks.refSpot() * ServiceConstant.WEIGHTED_AVERAGE_SCALE) * greeks.gamma() / FULL_SPOT_SCALE + (float)greeks.delta());
        }
        
        @Override
        public long calcSpotChangeForPriceChange(final int priceChange, final float adjustedDeltaC) {
            return (long)(priceChange * ServiceConstant.WEIGHTED_AVERAGE_SCALE / adjustedDeltaC);
        }

        @Override
        public long calcSpotChangeForPriceChange(final int priceChange, final int convRatio, final float adjustedDelta) {
            return (long)(priceChange * convRatio * 100000L / adjustedDelta);
        }

        @Override
        public long calcSpotChangeForPriceChangeForCall(final int priceChange, final int convRatio, final float adjustedDelta, final Greeks greeks) {
            if (greeks.gamma() == 0) {
                return calcSpotChangeForPriceChange(priceChange, convRatio, adjustedDelta);
            }
            return (long)(FULL_SPOT_SCALE * (-adjustedDelta + Math.sqrt(adjustedDelta*adjustedDelta + greeks.gamma() * priceChange * convRatio / 5.0)) / (greeks.gamma())) ;
        }

        @Override
        public long calcSpotChangeForPriceChangeForPut(final int priceChange, final int convRatio, final float adjustedDelta, final Greeks greeks) {
            if (greeks.gamma() == 0) {
                return calcSpotChangeForPriceChange(priceChange, convRatio, adjustedDelta);
            }
            return (long)(FULL_SPOT_SCALE * (-adjustedDelta - Math.sqrt(adjustedDelta*adjustedDelta + greeks.gamma() * priceChange * convRatio / 5.0)) / (greeks.gamma())) ;
        }
        
        @Override
        public float calcPriceChangeFloatForSpotChange(final long spotChange, final int convRatio, final float adjustedDelta) {
            return spotChange * adjustedDelta / (convRatio * 100000);
        }

        @Override
        public long calcSpotBufferFromTickBuffer(final int wrtTickSize, final int tickBuffer, final float adjustedDeltaC) {
            return (long)(wrtTickSize * tickBuffer / adjustedDeltaC);
        }

    }
    

}
