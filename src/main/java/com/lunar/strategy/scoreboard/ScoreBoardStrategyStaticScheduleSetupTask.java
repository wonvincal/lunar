package com.lunar.strategy.scoreboard;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.binary.Messenger;
import com.lunar.strategy.MarketHoursUtils;
import com.lunar.strategy.StrategyManager;
import com.lunar.strategy.StrategyScheduler;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategyStaticScheduleSetupTask;
import com.lunar.strategy.parameters.WrtOutputParams;

public class ScoreBoardStrategyStaticScheduleSetupTask extends StrategyStaticScheduleSetupTask {
    private static final Logger LOG = LogManager.getLogger(ScoreBoardStrategyStaticScheduleSetupTask.class);

    public class PersistGreeksTask extends PredeterminedSchedulerTask {
        public PersistGreeksTask(final long scheduledTime) {
            super(scheduledTime);
        }
        @Override
        public void run(final int scheduleId, final long scheduledNanoOfDay, final long actualNanoOfDay) {
            try {
                LOG.info("Scheduled to persist greeks");
                for (final StrategySecurity security : strategyManager().warrants().values()) {
                    final ScoreBoardSecurityInfo scoreBoardSecurity = (ScoreBoardSecurityInfo)security;
                    if (scoreBoardSecurity.wrtParams() != null) {
                        messenger.pricingSender().trySendGreeks(messenger.referenceManager().persi(), ((WrtOutputParams)scoreBoardSecurity.wrtParams()).greeks());
                    }
                }
            }
            catch (final Exception e) {
                LOG.error("Cannot persist greeks...", e);
            }           
        }
    }
    
    private final Messenger messenger;

    public ScoreBoardStrategyStaticScheduleSetupTask(final StrategyScheduler scheduler, final StrategyManager strategyManager,  final Messenger messenger, final boolean canAutoSwitchOn) {
        super(scheduler, strategyManager, canAutoSwitchOn);
        this.messenger = messenger;
    }

    protected void populatePredeterminedSchedulerTask(final List<PredeterminedSchedulerTask> scheduledTasks, final long currentNanoOfDay) {
        super.populatePredeterminedSchedulerTask(scheduledTasks, currentNanoOfDay);
        if (currentNanoOfDay < MarketHoursUtils.MARKET_PREP_VERY_NEAR_CLOSE_TIME) {
            LOG.info("Registering persist greeks scheduler at {}", MarketHoursUtils.MARKET_PREP_CLOSE_TIME);
            scheduledTasks.add(new PersistGreeksTask(MarketHoursUtils.MARKET_PREP_CLOSE_TIME));
        }
    }
}
