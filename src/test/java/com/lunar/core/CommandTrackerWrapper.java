package com.lunar.core;

import com.lunar.core.CommandTracker.CommandContext;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class CommandTrackerWrapper {
	private final CommandTracker commandTracker;
	public CommandTrackerWrapper(CommandTracker commandTracker){
		this.commandTracker = commandTracker;
	}
	public Int2ObjectOpenHashMap<CommandContext> commands(){
		return commandTracker.commands;
	}
	@Override
	public String toString() {
		return commandTracker.toString();
	}

}
