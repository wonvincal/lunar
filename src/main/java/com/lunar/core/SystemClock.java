package com.lunar.core;

import java.time.LocalDate;
import java.time.LocalTime;

public interface SystemClock {
    public abstract String dateString();
	public abstract LocalDate date();
	public abstract LocalTime time();
	public abstract long nanoOfDay();
	public abstract long timestamp();
}
