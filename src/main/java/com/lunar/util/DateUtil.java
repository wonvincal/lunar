package com.lunar.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.service.ServiceConstant;

public class DateUtil {
	public static int MAX_INT_DATE = 99991231;
	public static long NUM_NANO_IN_SEC = 1_000_000_000L;
	public static long NUM_NANO_IN_MIN = NUM_NANO_IN_SEC * 60;
	public static long NUM_NANO_IN_HOUR = NUM_NANO_IN_MIN * 60;
	
	public static int toIntDate(LocalDate date){
		return date.getYear() % 10000 * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
	}

	public static int getIntDate(LocalDateTime dateTime){
		return dateTime.getYear() % 10000 * 10000 + dateTime.getMonthValue() * 100 + dateTime.getDayOfMonth();		
	}
	
	public static long getNanoOfDay(LocalDateTime dateTime){
		return dateTime.getHour() * NUM_NANO_IN_HOUR + 
				dateTime.getMinute() * NUM_NANO_IN_MIN + 
				dateTime.getSecond() * NUM_NANO_IN_SEC + 
				dateTime.getNano();
	}
	
	public static int toIntDate(Optional<LocalDate> date){
		if (date.isPresent()){
			return date.get().getYear() % 10000 * 10000 + date.get().getMonthValue() * 100 + date.get().getDayOfMonth();
		}
		return MAX_INT_DATE;
	}
	
	public static int toFbsDate(Optional<LocalDate> date){
		if (date.isPresent()){
			return date.get().getYear() % 10000 * 10000 + date.get().getMonthValue() * 100 + date.get().getDayOfMonth();
		}
		return 0;
	}

	public static int toFbsDate(LocalDate date){
		return date.getYear() % 10000 * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
	}
	
	public static int toSbeMaturityDate(Optional<LocalDate> date){
		if (date.isPresent()){
			return date.get().getYear() % 10000 * 10000 + date.get().getMonthValue() * 100 + date.get().getDayOfMonth();
		}
		return SecuritySbeDecoder.maturityNullValue();
	}
	
	public static int toSbeListedDate(LocalDate date){
		return date.getYear() % 10000 * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
	}

	public static LocalDate fromSbeToLocalDate(int sbeDate){
		if (sbeDate == SecuritySbeDecoder.listingDateNullValue()){
			return ServiceConstant.NULL_LISTED_DATE;
		}
		return LocalDate.of(sbeDate / 10000, sbeDate % 10000 / 100, sbeDate % 100);
	}
}
