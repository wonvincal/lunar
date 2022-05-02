package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.ScoreBoardDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.scoreboard.ScoreBoard;
import com.lunar.util.ServiceTestHelper;

public class ScoreBoardSenderTest {
    static final Logger LOG = LogManager.getLogger(ScoreBoardSenderTest.class);
    private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
    
    private MessageSender sender;
    private ScoreBoardSender scoreBoardSender;
    private final int testSinkId = 1;
    private RingBufferMessageSinkPoller testSinkPoller;
    private MessageSinkRef testSinkRef;
    private Messenger testMessenger;

    @Before
    public void setup(){
        buffer.setMemory(0, buffer.capacity(), (byte)0);
        final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.ClientService, "test-client");
        sender = MessageSender.of(ServiceConstant.MAX_MESSAGE_SIZE, refSenderSink);
        scoreBoardSender = ScoreBoardSender.of(sender);
        
        ServiceTestHelper testHelper = ServiceTestHelper.of();
        testSinkPoller = testHelper.createRingBufferMessageSinkPoller(testSinkId, ServiceType.DashboardService, 256, "testDashboard");
        RingBufferMessageSink sink = testSinkPoller.sink();
        testSinkRef = MessageSinkRef.of(sink, "testSink");
        testMessenger = testHelper.createMessenger(sink, "testSinkMessenger");
        testMessenger.registerEvents();
        testSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
            @Override
            public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
                testMessenger.receive(event, 0);
                return false;
            }
        });
    }
    
    @Test
    public void testEncodeMessageAndDecode(){
        final ScoreBoard scoreBoard = ScoreBoard.of();
        scoreBoard.setSecSid(12345);
        scoreBoard.speedArbHybridStats().setOurPrevScoreWithPunter(999);
        scoreBoard.marketStats().setPrevDayOsPercent(123);
        scoreBoard.marketStats().setPrevDayOsPercentChange(123456);
        scoreBoard.marketStats().setPrevDayOutstanding(56789);
        scoreBoard.marketStats().setPrevDayNetSold(500);
        scoreBoard.marketStats().setPrevDayNetVegaSold(543);
        scoreBoard.marketStats().setPrevDayImpliedVol(543);

        scoreBoardSender.trySendScoreBoard(testSinkRef, scoreBoard);

        final ScoreBoard out = ScoreBoard.of();
        final ScoreBoardDecoder decoder = ScoreBoardDecoder.of();
        testMessenger.receiver().scoreBoardHandlerList().add(new Handler<ScoreBoardSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSbeDecoder codec) {
                out.setSecSid(codec.secSid());
                decoder.decodeScoreBoard(codec, out);
            }
        });
        testSinkPoller.pollAll();        
        assertEquals(scoreBoard.getSecSid(), out.getSecSid());
        assertEquals(scoreBoard.speedArbHybridStats().getOurPrevScoreWithPunter(), out.speedArbHybridStats().getOurPrevScoreWithPunter());
        assertEquals(scoreBoard.marketStats().getPrevDayOsPercent(), out.marketStats().getPrevDayOsPercent());
        assertEquals(scoreBoard.marketStats().getPrevDayOsPercentChange(), out.marketStats().getPrevDayOsPercentChange());
        assertEquals(scoreBoard.marketStats().getPrevDayOutstanding(), out.marketStats().getPrevDayOutstanding());
        assertEquals(scoreBoard.marketStats().getPrevDayNetSold(), out.marketStats().getPrevDayNetSold());
        assertEquals(scoreBoard.marketStats().getPrevDayNetVegaSold(), out.marketStats().getPrevDayNetVegaSold());
        assertEquals(scoreBoard.marketStats().getPrevDayImpliedVol(), out.marketStats().getPrevDayImpliedVol());

    }
}