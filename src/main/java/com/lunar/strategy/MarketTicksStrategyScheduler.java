package com.lunar.strategy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.apache.logging.log4j.util.Unbox.box;

/*
 * Scheduler by market data ticks
 * Designed to be efficient, but that is not scientifically or non-scientifically proven ^^
 * It assumes that tasks scheduled for the same ScheduleId are scheduled in order
 * (i.e. for the same ScheduleId, no task may be scheduled such that it expires before a prior task - 
 * tasks that break the requirement will still be executed, but after the tasks scheduled prior to it regardless of their expire time)
 */
public class MarketTicksStrategyScheduler implements StrategyScheduler, TimeHandler {
    static final Logger LOG = LogManager.getLogger(MarketTicksStrategyScheduler.class);    

    static private final int MAX_TASKS_PER_QUEUE = 4096;  
    static private final int TASK_COUNTER_MASK = MAX_TASKS_PER_QUEUE - 1;
    
    private class TaskSet {
        // use two arrays rather than an array of a wrapper object.
        // i believe using 2 arrays is more efficient due to caching,
        // comparing the current time to the nanoOfDays long array should be very quick
        long[] nanoOfDays = new long[MAX_TASKS_PER_QUEUE];
        Task[] tasks = new Task[MAX_TASKS_PER_QUEUE];
        int startIndex = 0;
        int count = 0;
    }
    final private TaskSet[] taskSets = new TaskSet[StrategyScheduler.NUMBER_SCHEDULE_IDS];
    
    public MarketTicksStrategyScheduler() {
        for (int i = 0; i < StrategyScheduler.NUMBER_SCHEDULE_IDS; i++) {
            taskSets[i] = new TaskSet();
        }
    }

    @Override
    public boolean scheduleTask(final int scheduleId, final long nanoOfDay, final Task task) {
        final TaskSet taskSet = taskSets[scheduleId];
        if (taskSet.count == MAX_TASKS_PER_QUEUE) {
            LOG.warn("Queue is full for scheduleId {}...", box(scheduleId));
            return false;
        }
        final int index = (taskSet.startIndex + taskSet.count++) & TASK_COUNTER_MASK;
        taskSet.nanoOfDays[index] = nanoOfDay;
        taskSet.tasks[index] = task;
        return true;
    }

    @Override
    public void handleTimestamp(long timestamp) {
        for (int i = 0; i < StrategyScheduler.NUMBER_SCHEDULE_IDS; i++) {
            final TaskSet taskSet = taskSets[i];
            int j;
            final int maxIndex = taskSet.startIndex + taskSet.count;
            for (j = taskSet.startIndex; j < maxIndex; j++) {
                final int maskedIndex = j & TASK_COUNTER_MASK;
                final long taskNanoOfDay = taskSet.nanoOfDays[maskedIndex];
                if (taskNanoOfDay > timestamp)
                    break;
                taskSet.tasks[maskedIndex].run(i, taskNanoOfDay, timestamp);
            }
            final int tasksExpired = j - taskSet.startIndex;
            if (tasksExpired > 0) {
                taskSet.startIndex = (taskSet.startIndex + tasksExpired) & TASK_COUNTER_MASK;
                taskSet.count -= tasksExpired;
            }
        }
    }

}
