package com.lunar.core;

public class VerifiedDouble {
	private boolean verified;
	private double value;
	
	public static VerifiedDouble of(double value, boolean verified){
		return new VerifiedDouble(value, verified);
	}
	
	VerifiedDouble(double value, boolean verified){
		this.value = value;
		this.verified = verified;
	}
	
	public double value(){
		return value;
	}
	
	public boolean verified(){
		return verified;
	}
	
	public VerifiedDouble value(double value){
		this.value = value;
		return this;
	}
	
	public VerifiedDouble verified(boolean verified){
		this.verified = verified;
		return this;
	}
}
