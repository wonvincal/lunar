package com.lunar.strategy.scoreboard;

import com.lunar.core.SubscriberList;
import com.lunar.entity.LongEntityManager;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategySwitch;

public class LunarScoreBoardStrategyInfoSender extends ScoreBoardStrategyInfoSender {
    private final Messenger messenger;
    private final SubscriberList subscriberList;

    public LunarScoreBoardStrategyInfoSender(final LongEntityManager<? extends StrategySecurity> securities, final Messenger messenger, final SubscriberList subscriberList) {
        super(securities);
        this.messenger = messenger;
        this.subscriberList = subscriberList;
    }
    
    @Override
    public void sendSwitch(StrategySwitch strategySwitch, MessageSinkRef sink) {
        if (strategySwitch.switchType() == StrategySwitchType.SCOREBOARD_DAY_ONLY || strategySwitch.switchType() == StrategySwitchType.SCOREBOARD_PERSIST) {
            messenger.strategySender().sendSbeEncodable(sink, strategySwitch);
        }
    }

    @Override
    public void sendSwitch(StrategySwitch strategySwitch, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
        if (strategySwitch.switchType() == StrategySwitchType.SCOREBOARD_DAY_ONLY || strategySwitch.switchType() == StrategySwitchType.SCOREBOARD_PERSIST) {
            messenger.responseSender().sendSbeEncodable(sink, request.clientKey(), BooleanType.FALSE, seqNum, ResultType.OK, strategySwitch);
        }
    }

    @Override
    public void broadcastSwitch(StrategySwitch strategySwitch) {
        if (strategySwitch.switchType() == StrategySwitchType.SCOREBOARD_DAY_ONLY || strategySwitch.switchType() == StrategySwitchType.SCOREBOARD_PERSIST) {
            messenger.strategySender().sendSbeEncodable(messenger.referenceManager().persi(), subscriberList.elements(), strategySwitch);
        }
    }

}
