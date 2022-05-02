package com.lunar.config;

import java.time.Duration;

import com.typesafe.config.Config;

public class TimerServiceConfig {
	private static String LUNAR_TIMER_SERVICE_PATH = "lunar.timerService";
	private static String TICK_DURATION_KEY = "tickDuration";
	private static String TICKS_PER_WHEEL_KEY = "ticksPerWheel";

	private Duration tickDuration;
	private int ticksPerWheel;
	
	public TimerServiceConfig(Duration tickDuration, int ticksPerWheel){
		this.tickDuration = tickDuration;		
		this.ticksPerWheel = ticksPerWheel;
	}
	
	public TimerServiceConfig(Config config){
		this(config.getConfig(LUNAR_TIMER_SERVICE_PATH).getDuration(TICK_DURATION_KEY),
				config.getConfig(LUNAR_TIMER_SERVICE_PATH).getInt(TICKS_PER_WHEEL_KEY));
	}
	
	public Duration tickDuration(){
		return tickDuration;
	}
	
	public int ticksPerWheel(){
		return ticksPerWheel;
	}
}
