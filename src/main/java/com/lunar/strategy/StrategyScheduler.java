package com.lunar.strategy;

/*
 * A soft real-time scheduler for strategies.
 * Implementations of this interface may make assumptions that make the schedule not suitable for hard real-time
 * The assumptions are usually for simplicity or performance reasons.
 * For example, implementations may assume that tasks scheduled for the same ScheduleIds are scheduled in order
 * (i.e. for the same ScheduleIds, no task may be scheduled such that it expires before a prior task - 
 * tasks that break the requirement will still be executed, but after the tasks scheduled prior to it regardless of their expire time)
 */
public interface StrategyScheduler {
    static public final int NUMBER_SCHEDULE_IDS = 3;
    public class ScheduleIds {
        static public final int WAIT_ISSUER_INITIAL_RESPONSE = 0; // Schedule to check for issuer initial response
        static public final int WAIT_ISSUER_FULL_RESPONSE = 1; // Schedule to check for issuer full response
        static public final int PRE_DETERMINED_STATIC = 2; // pre-determined schedule (static fixed schedule)
    }
    
    public interface Task {
        void run(final int scheduleId, final long scheduledNanoOfDay, final long actualNanoOfDay);
    }
    
    boolean scheduleTask(final int scheduleId, final long nanoOfDay, final Task task);
}
