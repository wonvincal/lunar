package com.lunar.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class UserControlledSystemClock implements SystemClock {
    private LocalDate date;
    private String dateString;
	private volatile long nanoOfDay;

	public UserControlledSystemClock() {
		this(LocalDate.now());
	}
	
	public UserControlledSystemClock(final LocalDate date) {
	    this.date = date;
	    this.dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
	}
	
	public UserControlledSystemClock nanoOfDay(final long nanoOfDay) {
	    this.nanoOfDay = nanoOfDay;
		return this;
	}
	
	public String dateString() {
	    return dateString;
	}
	
	@Override
	public LocalDate date() {
		return date;
	}

	@Override
	public LocalTime time() {
		return LocalTime.ofNanoOfDay(nanoOfDay);
	}

	@Override
	public long nanoOfDay() {
		return nanoOfDay;
	}

	@Override
	public long timestamp() {
		return System.nanoTime();
	}

}
