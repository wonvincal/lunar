package com.lunar.message.binary;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventTranslatorOneArg;

public class FrameTranslator {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(FrameTranslator.class);

	public static final EventTranslatorOneArg<Frame, Frame> FRAME_EVENT_TRANSLATOR = new EventTranslatorOneArg<Frame, Frame>() {
		@Override
		public void translateTo(Frame event, long sequence, Frame input) {
			// LOG.debug("translating {} to {}", event, input);
			event.merge(input);
		}
	};
}
