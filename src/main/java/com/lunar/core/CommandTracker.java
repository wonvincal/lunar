package com.lunar.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Command;
import com.lunar.message.MessageFactory;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.CommandSender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class CommandTracker {
	private static final Logger LOG = LogManager.getLogger(CommandTracker.class);

	final Int2ObjectOpenHashMap<CommandContext> commands;
	private final TimerService timerService;
	private final long defaultTimeout;
	private final CommandSender commandSender;
	private final MessageSinkRef self;

	public static CommandTracker of(MessageFactory messageFactory, Messenger messenger) {
		return new CommandTracker(messenger.self(), messenger.timerService(), messageFactory.commandTimeoutNs(),
				messenger.commandSender());
	}

	public static CommandTracker of(MessageSinkRef self, TimerService timerService, long commandTimeoutNs,
			CommandSender commandSender) {
		return new CommandTracker(self, timerService, commandTimeoutNs, commandSender);
	}

	private CommandTracker(MessageSinkRef self, TimerService timerService, long commandTimeoutNs,
			CommandSender commandSender) {
		this(100, 0.6f, self, timerService, commandTimeoutNs, commandSender);
	}

	private CommandTracker(int expected, float load, MessageSinkRef self, TimerService timerService,
			long commandTimeoutNs, CommandSender commandSender) {
		this.commands = new Int2ObjectOpenHashMap<CommandContext>(expected, load);
		this.timerService = timerService;
		this.defaultTimeout = commandTimeoutNs;
		this.commandSender = commandSender;
		this.self = self;
	}

	public CompletableFuture<Command> sendAndTrack(MessageSinkRef sink, Command command) {
		CommandContext context = new CommandContext(command);
		long result = commandSender.sendCommand(sink, command);
		if (result == MessageSink.OK) {
			final long nanoOfDay = timerService.toNanoOfDay();

			commands.put(command.clientKey(), context);
			TimeoutHandlerTimerTask timerTask = timerService.createTimerTask(new TimeoutHandler() {

				@Override
				public void handleTimeoutThrowable(Throwable ex) {
					LOG.error("Caught throwable when procesing timeout for [clientKey:" + command.clientKey() + "]",
							ex);
				}

				@Override
				public void handleTimeout(TimerEventSender timerEventSender) {
					long timeoutNs = (command.timeoutNs().isPresent() ? command.timeoutNs().get() : defaultTimeout);
					long result = timerEventSender.sendTimerEvent(self, command.clientKey(),
							TimerEventType.COMMAND_TIMEOUT, nanoOfDay, nanoOfDay + timeoutNs);
					if (result != MessageSink.OK) {
						context.timeout(
								timerService.newTimeout(context.timerTask(), timeoutNs, TimeUnit.NANOSECONDS));
					}
				}
			}, "command-tracker-timeout-" + command.clientKey());
			context.timerTask(timerTask);
			long timeout = command.timeoutNs().isPresent() ? command.timeoutNs().get() : defaultTimeout;
			context.timeout(timerService.newTimeout(context.timerTask(), timeout, TimeUnit.NANOSECONDS));
		} else {
			LOG.error("Could not deliver new command. [result:{}]", result);
			context.result()
					.completeExceptionally(new Exception("Could not deliver new command. [result:" + result + "]"));
		}
		return context.result();
	}

	private final Handler<CommandAckSbeDecoder> commandHandler = new Handler<CommandAckSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder ack) {
			LOG.debug("Received ack [senderSinkId:{}, dstSinkId:{}, clientKey:{}, commandType:{}, ackType:{}]", 
					header.senderSinkId(), 
					header.dstSinkId(), ack.clientKey(), ack.commandType().name(), ack.ackType().name());
					
			CommandContext context = commands.remove(ack.clientKey());
			if (context == commands.defaultReturnValue()) {
				return;
			}
			complete(context, ack.ackType());
		}
	};

	public Handler<CommandAckSbeDecoder> commandHandler() {
		return commandHandler;
	}

	private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoder,
				TimerEventSbeDecoder timerEvent) {
			CommandContext context = commands.remove(timerEvent.clientKey());
			if (context == commands.defaultReturnValue()) {
				return;
			}
			if (timerEvent.timerEventType() != TimerEventType.COMMAND_TIMEOUT) {
				LOG.error("received unexpected timer event {} for command {}", timerEvent, context.command());
				return;
			}
			complete(context, CommandAckType.TIMEOUT);
		}
	};

	public Handler<TimerEventSbeDecoder> timerEventHandler() {
		return timerEventHandler;
	}

	private void complete(CommandContext context, CommandAckType ackType) {
		Command command = context.command();
		context.timeout().cancel();
		context.result().complete(command.ackType(ackType));
	}

	public int numOutstandings() {
		return commands.size();
	}

	public static class CommandContext {
		private final Command command;
		private TimerTask timerTask;
		private Timeout timeout;
		private final CompletableFuture<Command> result;

		CommandContext(Command command) {
			this.command = command;
			this.result = new CompletableFuture<Command>();
		}

		public Command command() {
			return command;
		}

		Timeout timeout() {
			return timeout;
		}

		TimerTask timerTask() {
			return timerTask;
		}

		CommandContext timerTask(TimerTask task) {
			this.timerTask = task;
			return this;
		}

		CommandContext timeout(Timeout timeout) {
			this.timeout = timeout;
			return this;
		}

		public CompletableFuture<Command> result() {
			return this.result;
		}

	}

	public long commandTimeoutNs() {
		return defaultTimeout;
	}
	
	Int2ObjectOpenHashMap<CommandContext> commands(){
		return commands;
	}
}
