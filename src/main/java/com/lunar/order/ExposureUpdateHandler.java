package com.lunar.order;

import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.sink.MessageSinkRefMgr;

public class ExposureUpdateHandler {
	private final Messenger childMessenger;
	private final int targetSinkId;
	private final MessageSinkRefMgr refMgr;

	public static ExposureUpdateHandler createWithChildMessenger(Messenger messenger, int targetSinkId){
		return new ExposureUpdateHandler(messenger.createChildMessenger(), targetSinkId);
	}

	ExposureUpdateHandler(Messenger messenger, int targetSinkId){
		this.childMessenger = messenger;
		this.refMgr = messenger.referenceManager();
		this.targetSinkId = targetSinkId;
	}

	public void updateValidationBook(Side side, long secSid, int price, long quantity){
		childMessenger.sendRequest(refMgr.get(targetSinkId), 
				RequestType.UPDATE, 
				Parameters.listOf(Parameter.of(DataType.VALIDATION_BOOK),
						Parameter.of(ParameterType.SECURITY_SID, secSid),
						Parameter.of(ParameterType.SIDE, side.value()),
						Parameter.of(ParameterType.PRICE, price),
						Parameter.of(ParameterType.QUANTITY, quantity)));
	}
}
