package com.lunar.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyExitMode;

public class StrategyStaticScheduleSetupTask implements StrategyScheduler.Task {
    private static final Logger LOG = LogManager.getLogger(StrategyStaticScheduleSetupTask.class);

    final private StrategyScheduler scheduler;
    final private StrategyManager strategyManager;
    final private boolean canAutoSwitchOn;
    
    public abstract class PredeterminedSchedulerTask implements StrategyScheduler.Task {
        private long scheduledTime;
        
        public PredeterminedSchedulerTask(final long scheduledTime) {
            this.scheduledTime = scheduledTime;
        }
        
        public long scheduledTime() { return scheduledTime; }
    }
    
    public class StrategyOnTask extends PredeterminedSchedulerTask {
        public StrategyOnTask(final long scheduledTime) {
            super(scheduledTime);
        }
        @Override
        public void run(final int scheduleId, final long scheduledNanoOfDay, final long actualNanoOfDay) {
            try {
                LOG.info("Scheduled to turn on main switch");
                strategyManager.flipMainSwitch(BooleanType.TRUE, StrategyExitMode.NULL_VAL, true);
            }
            catch (final Exception e) {
                LOG.error("Cannot update main switch...", e);
            }           
        }
    }
    
    public class StrategyOffTask extends PredeterminedSchedulerTask {
        final StrategyExitMode exitMode;
        public StrategyOffTask(final long scheduledTime, final StrategyExitMode exitMode) {
            super(scheduledTime);
            this.exitMode = exitMode;
        }
        @Override
        public void run(final int scheduleId, final long scheduledNanoOfDay, final long actualNanoOfDay) {
            try {
                LOG.info("Scheduled to turn off main switch with exit mode {}", this.exitMode);
                strategyManager.flipMainSwitch(BooleanType.FALSE, this.exitMode, true);
            }
            catch (final Exception e) {
                LOG.error("Cannot update main switch...", e);
            }           
        }
    }

    public StrategyStaticScheduleSetupTask(final StrategyScheduler scheduler, final StrategyManager strategyManager, final boolean canAutoSwitchOn) {
        this.scheduler = scheduler;
        this.strategyManager = strategyManager;
        this.canAutoSwitchOn = canAutoSwitchOn;
        scheduler.scheduleTask(StrategyScheduler.ScheduleIds.PRE_DETERMINED_STATIC, 0, this);
    }
    
    protected StrategyManager strategyManager() {
        return this.strategyManager;
    }

    @Override
    public void run(final int scheduleId, final long scheduledNanoOfDay, final long actualNanoOfDay) {
        final List<PredeterminedSchedulerTask> scheduledTasks = new ArrayList<PredeterminedSchedulerTask>();
        
        populatePredeterminedSchedulerTask(scheduledTasks, actualNanoOfDay);
        
        scheduledTasks.sort(new Comparator<PredeterminedSchedulerTask>() {
            @Override
            public int compare(final PredeterminedSchedulerTask o1, final PredeterminedSchedulerTask o2) {
                return Long.compare(o1.scheduledTime(), o2.scheduledTime());
            }            
        });
        for (final PredeterminedSchedulerTask task : scheduledTasks) {
            scheduler.scheduleTask(StrategyScheduler.ScheduleIds.PRE_DETERMINED_STATIC, task.scheduledTime(), task);
        }
    }

    protected void populatePredeterminedSchedulerTask(final List<PredeterminedSchedulerTask> scheduledTasks, final long currentNanoOfDay) {
        if (currentNanoOfDay < MarketHoursUtils.MARKET_PREP_MORNING_CLOSE_TIME && canAutoSwitchOn) {
            LOG.info("Registering strategy on/off scheduler: ON at {}", MarketHoursUtils.MARKET_MORNING_OPEN_TIME);
            scheduledTasks.add(new StrategyOnTask(MarketHoursUtils.MARKET_MORNING_OPEN_TIME));
        }
        if (currentNanoOfDay < MarketHoursUtils.MARKET_AFTERNOON_OPEN_TIME) {
            LOG.info("Registering strategy on/off scheduler: FORCE_STRATEGY_EXIT at {}", MarketHoursUtils.MARKET_PREP_MORNING_CLOSE_TIME);
            scheduledTasks.add(new StrategyOffTask(MarketHoursUtils.MARKET_PREP_MORNING_CLOSE_TIME, StrategyExitMode.CLOSING_STRATEGY_EXIT));
        }
        if (currentNanoOfDay < MarketHoursUtils.MARKET_PREP_CLOSE_TIME && canAutoSwitchOn) {
            LOG.info("Registering strategy on/off scheduler: ON at {}", MarketHoursUtils.MARKET_AFTERNOON_OPEN_TIME);
            scheduledTasks.add(new StrategyOnTask(MarketHoursUtils.MARKET_AFTERNOON_OPEN_TIME));
        }
        if (currentNanoOfDay < MarketHoursUtils.MARKET_PREP_VERY_NEAR_CLOSE_TIME) {
            LOG.info("Registering strategy on/off scheduler: FORCE_STRATEGY_EXIT at {}", MarketHoursUtils.MARKET_PREP_CLOSE_TIME);
            scheduledTasks.add(new StrategyOffTask(MarketHoursUtils.MARKET_PREP_CLOSE_TIME, StrategyExitMode.CLOSING_STRATEGY_EXIT));
        }
        LOG.info("Registering strategy on/off scheduler: FORCE_PRICE_CHECK_EXIT at {}", MarketHoursUtils.MARKET_PREP_VERY_NEAR_CLOSE_TIME);
        scheduledTasks.add(new StrategyOffTask(MarketHoursUtils.MARKET_PREP_VERY_NEAR_CLOSE_TIME, StrategyExitMode.CLOSING_PRICE_CHECK_EXIT));        
    }
}
