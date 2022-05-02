package com.lunar.message;

import java.util.concurrent.TimeUnit;

public class ProcessingRate {
	private double processed;
	private long duration;
	private TimeUnit unit;
	private Modifier modifier;
	
	private ProcessingRate(double processed, long duration, TimeUnit unit){
		this.processed = processed;
		this.duration = duration;
		this.unit = unit;
		modifier = new Modifier(this);
	}
	public static ProcessingRate of(double processed, long duration, TimeUnit unit){
		return new ProcessingRate(processed, duration, unit);
	}
	public static class Modifier {
		private final ProcessingRate self;
		public Modifier(final ProcessingRate rate){
			this.self = rate;
		}
		public Modifier processed(double value){
			self.processed = value;
			return this;
		}
		public Modifier duration(long duration){
			self.duration = duration;
			return this;
		}
		public Modifier unit(final TimeUnit unit){
			self.unit = unit;
			return this;
		}
	}
	public double processed(){
		return this.processed;
	}
	public long duration(){
		return this.duration;
	}
	public TimeUnit unit(){
		return this.unit;
	}
	/**
	 * Modifier is just a way to discourage people from modifying the object
	 * Actor communication builds on the fact that messages are immutable, we
	 * strive to follow this rule.
	 * @return
	 */
	public Modifier modifier(){
		return this.modifier;
	}
	public double get(final TimeUnit unit){
		return unit.convert(this.duration, this.unit);
	}
	@Override
	public String toString() {
		return String.format("%.3f/%d %s", processed, duration, unit.name());
	}
}
