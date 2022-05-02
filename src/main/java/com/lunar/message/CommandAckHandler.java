package com.lunar.message;

import com.lunar.message.io.sbe.CommandAckType;


public interface CommandAckHandler {

	void handleCommandCompletion(Command command);
	void handleCommandFailure(Command command, CommandAckType ackType);

	public static CommandAckHandler NULL_HANDLER = new CommandAckHandler() {

		@Override
		public void handleCommandCompletion(Command command) {
			// noop
		}

		@Override
		public void handleCommandFailure(Command command, CommandAckType ackType) {
			// noop
		}
	};

}
