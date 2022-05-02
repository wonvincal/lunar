package com.lunar.position;

public interface PositionChangeHandler {
	public void handleChange(SecurityPositionDetails current, SecurityPositionDetails previous);
	public void handleChange(PositionDetails current, PositionDetails previous);
}
