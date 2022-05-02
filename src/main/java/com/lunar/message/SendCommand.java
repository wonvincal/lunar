package com.lunar.message;

public class SendCommand {
	private Command command;
	public SendCommand(Command command){
		this.command = command;
	}
	public Command command(){ return command;}
}
