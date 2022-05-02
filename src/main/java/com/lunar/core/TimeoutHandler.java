package com.lunar.core;

import com.lunar.message.sender.TimerEventSender;

public interface TimeoutHandler {
	void handleTimeout(TimerEventSender timerEventSender);
	void handleTimeoutThrowable(Throwable ex);
}
