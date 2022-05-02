package com.lunar.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.lunar.core.LunarSystem;
import com.lunar.core.LunarSystem.LunarSystemState;
import com.lunar.core.MessageSinkRefList;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.exception.ConfigurationException;
import com.lunar.marketdata.MarketDataReplayer;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessageFbsEncoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.OrderBookSnapshotFbs;
import com.lunar.message.io.fbs.StrategyParametersFbs;
import com.lunar.message.io.fbs.StrategySwitchFbs;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.SimpleSocket;
import com.lunar.util.ConcurrentUtil;

public class LunarAdminTrueStrategyIT {
	private static final Logger LOG = LogManager.getLogger(LunarAdminTrueStrategyIT.class);
	@Test
	@Ignore
	public void testStart() throws InterruptedException, UnsupportedEncodingException, ExecutionException, TimeoutException, ConfigurationException{
		LOG.info("Start running!");
		
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.true.strat.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		UserControlledSystemClock systemClock = new UserControlledSystemClock(LocalDate.of(2015, 10, 2));
		
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(500, TimeUnit.SECONDS);
		
		system.adminService().messenger().createChildMessenger().sendRequest(system.getMessageSinkRefMgr().omes(), RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, 10_000_000_000L)));
		
		final MessageSinkRef senderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.DashboardService, "test");
		final int clientKey = 1212;
		
        Command command = Command.of(senderSink.sinkId(),
                clientKey,
                CommandType.LINE_HANDLER_ACTION, 
                new ImmutableList.Builder<Parameter>().add(
                        Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW.value())).build());
        system.adminService().messenger().createChildMessenger().sendCommand(7, command);
        
		command = Command.of(senderSink.sinkId(),
		                            clientKey,
									CommandType.START, 
									 new ArrayList<Parameter>(), 
									 BooleanType.TRUE);
		MessageSinkRefList strats = system.getMessageSinkRefMgr().strats();
		for (int i = 0; i < strats.size(); i++){
	        system.adminService().messenger().createChildMessenger().sendCommand(strats.elements()[i], command);			
		}

        MarketDataReplayer replayer = MarketDataReplayer.instanceOf();
        replayer.startFeed(true, 100, Optional.empty(), systemClock, true);
		
		ConcurrentUtil.sleep(30000);
		system.adminService().messenger().createChildMessenger().sendRequest(system.getMessageSinkRefMgr().perf(), RequestType.ECHO, new ImmutableList.Builder<Parameter>().build());

		//ConcurrentUtil.sleep(30000);
		
		system.close();

		while (system.state() != LunarSystemState.STOPPED){
			//LOG.info("current state: {}, waiting...", system.state());
			LockSupport.parkNanos(500_000_000);
		}
		LOG.info("current state: {}, done...", system.state());
		assertEquals(system.state(), LunarSystemState.STOPPED);
		
		while (!system.isCompletelyShutdown()){
			LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
			LockSupport.parkNanos(500_000_000);
		}

	}
	
    @Test
    @Ignore
    public void testStrategyServiceSubscribeStratParam() throws InterruptedException, UnsupportedEncodingException, ConfigurationException {
        String host = "127.0.0.1";
        int port = 8192;
        int jmxPort = 8787;
        String systemName = "tiger";
        System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
        System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
        System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.true.strat.conf").toString()).getPath(), "UTF-8"));
        System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
        System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
   
        UserControlledSystemClock systemClock = new UserControlledSystemClock(LocalDate.of(2015, 10, 2));

        LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock);
        
        final MessageSinkRef senderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.DashboardService, "test");
        final int clientKey = 1212;
        
        system.adminService().messenger().createChildMessenger().sendRequest(system.getMessageSinkRefMgr().omes(), RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, 1000000000)));
        Command command = Command.of(senderSink.sinkId(),
                clientKey,
                CommandType.LINE_HANDLER_ACTION, 
                new ImmutableList.Builder<Parameter>().add(
                        Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW.value())).build());
        system.adminService().messenger().createChildMessenger().sendCommand(7, command);
        
        while (system.state() != LunarSystemState.STARTED){
            LOG.info("current state: {}, waiting to be started...", system.state());
            LockSupport.parkNanos(500_000_000);
        }
        LockSupport.parkNanos(500_000_000);
        String wsPath = "ws";
        int wsPort = 8123;
        String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
        WebSocketClient client = new WebSocketClient();
        final AtomicInteger expectedMessages = new AtomicInteger(2);
        
        Thread.sleep(10000);
        MarketDataReplayer replayer = MarketDataReplayer.instanceOf();
        replayer.startFeed(false, 0, Optional.empty(), systemClock, false);
        
        Thread.sleep(10000);
        
        SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return expectedMessages.get() == 0;
            }
        }, new SimpleSocket.BinaryMessageHandler() {
            
            @Override
            public void onMessage(ByteBuffer buffer) {
                MessageFbsDecoder decoder = MessageFbsDecoder.of();
                switch (decoder.init(buffer)){
                case MessagePayloadFbs.StrategyParametersFbs:
                    expectedMessages.decrementAndGet();
                    LOG.debug("Current remaining count: {}", expectedMessages.get());
                    break;
                case MessagePayloadFbs.StrategySwitchFbs:
                    expectedMessages.decrementAndGet();
                    LOG.debug("Current remaining count: {}", expectedMessages.get());
                    break;
                }
            }
        }, new SimpleSocket.TextMessageHandler() {
            
            @Override
            public void onMessage(String text) {
                
            }
        });
        
        ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
                Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATPARAMUPDATE.value()),
                Parameter.of(ParameterType.STRATEGY_ID, 2));
        final int anySinkId = 1;
        final int refClientKey = 5656565;
        final RequestType refRequestType = RequestType.SUBSCRIBE;

        Request request = Request.of(anySinkId,
                 refRequestType,
                 refParameters).clientKey(refClientKey);

        MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
        ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);
        
        // When
        boolean latchResult = false;
        try {
            client.start();
            URI uri = new URI(destUri);
            ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
            Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
            Session session = sessionFuture.get(5, TimeUnit.SECONDS);
            LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
            socket.send(buffer.slice());
            latchResult = socket.latch().await(100, TimeUnit.SECONDS);
        }
        catch (Throwable t){
            LOG.error("Caught throwable", t);
            throw new AssertionError("Caught throwable", t);
        }
        finally {
            try {
                LOG.info("Stopping web client");
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        LOG.info("Received number of binary messages [count:{}]", socket.receivedBinaryMessages().size());
        int numParams = 0;
        int numSwitches = 0;
        int numOther = 0;
        MessageFbsDecoder decoder = MessageFbsDecoder.of();
        for (ByteBuffer message : socket.receivedBinaryMessages()){
            switch (decoder.init(message)){
            case MessagePayloadFbs.StrategyParametersFbs:
                {
                    final StrategyParametersFbs fbs = decoder.asStrategyParametersFbs();
                    assertEquals(2, fbs.strategyId());
                    assertEquals(3, fbs.parametersLength());
                    numParams++;
                }
                break;
            case MessagePayloadFbs.StrategySwitchFbs:
                {
                    final StrategySwitchFbs fbs = decoder.asStrategySwitchFbs();
                    assertEquals(StrategyParamSource.STRATEGY_ID.value(), fbs.switchSource());
                    assertEquals(2, fbs.sourceSid());
                    numSwitches++;
                }   
                break;
            default:
                LOG.error("Got unexpected message back");
                numOther++;
                break;
            }
        }
        LOG.info("Received messages [numParams:{}, numSwitches:{}, numOther:{}]", numParams, numSwitches, numOther);
        assertTrue("Did not receive expected messages or number of messages", latchResult);
        
        system.close();
        
        while (system.state() != LunarSystemState.STOPPED){
            LOG.info("current state: {}, waiting...", system.state());
            LockSupport.parkNanos(500_000_000);
        }
        LOG.info("current state: {}, done...", system.state());
        assertEquals(system.state(), LunarSystemState.STOPPED);
        
        while (!system.isCompletelyShutdown()){
            LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
            LockSupport.parkNanos(500_000_000);
        }

    }
    
    @Test
    @Ignore
    public void testStrategyServiceSubscribeStratSwitch() throws InterruptedException, UnsupportedEncodingException, ConfigurationException{
        String host = "127.0.0.1";
        int port = 8192;
        int jmxPort = 8787;
        String systemName = "tiger";
        System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
        System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
        System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.true.strat.conf").toString()).getPath(), "UTF-8"));
        System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
        System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
   
        UserControlledSystemClock systemClock = new UserControlledSystemClock(LocalDate.of(2015, 10, 2));

        LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock);
        
        final MessageSinkRef senderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.DashboardService, "test");
        final int clientKey = 1212;
        
        system.adminService().messenger().createChildMessenger().sendRequest(system.getMessageSinkRefMgr().omes(), RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, 1000000000)));
        Command command = Command.of(senderSink.sinkId(),
                clientKey,
                CommandType.LINE_HANDLER_ACTION, 
                new ImmutableList.Builder<Parameter>().add(
                        Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW.value())).build());
        system.adminService().messenger().createChildMessenger().sendCommand(7, command);
        
        while (system.state() != LunarSystemState.STARTED){
            LOG.info("current state: {}, waiting to be started...", system.state());
            LockSupport.parkNanos(500_000_000);
        }
        LockSupport.parkNanos(500_000_000);
        String wsPath = "ws";
        int wsPort = 8123;
        String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
        WebSocketClient client = new WebSocketClient();
        final AtomicInteger expectedMessages = new AtomicInteger(18);
        
        Thread.sleep(10000);
        MarketDataReplayer replayer = MarketDataReplayer.instanceOf();
        replayer.startFeed(false, 0, Optional.empty(), systemClock, false);
        
        Thread.sleep(10000);
        
        SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return expectedMessages.get() == 0;
            }
        }, new SimpleSocket.BinaryMessageHandler() {
            
            @Override
            public void onMessage(ByteBuffer buffer) {
                MessageFbsDecoder decoder = MessageFbsDecoder.of();
                switch (decoder.init(buffer)){
                case MessagePayloadFbs.StrategySwitchFbs:
                    expectedMessages.decrementAndGet();
                    LOG.debug("Current remaining count: {}", expectedMessages.get());
                    break;
                }
            }
        }, new SimpleSocket.TextMessageHandler() {
            
            @Override
            public void onMessage(String text) {
                
            }
        });
        
        ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
                Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.StrategySwitch.value()));
        final int anySinkId = 1;
        final int refClientKey = 5656565;
        final RequestType refRequestType = RequestType.SUBSCRIBE;

        Request request = Request.of(anySinkId,
                 refRequestType,
                 refParameters).clientKey(refClientKey);

        MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
        ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);
        
        // When
        boolean latchResult = false;
        try {
            client.start();
            URI uri = new URI(destUri);
            ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
            Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
            Session session = sessionFuture.get(5, TimeUnit.SECONDS);
            LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
            socket.send(buffer.slice());
            latchResult = socket.latch().await(100, TimeUnit.SECONDS);
        }
        catch (Throwable t){
            LOG.error("Caught throwable", t);
            throw new AssertionError("Caught throwable", t);
        }
        finally {
            try {
                LOG.info("Stopping web client");
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        LOG.info("Received number of binary messages [count:{}]", socket.receivedBinaryMessages().size());
        int numMainSwitches = 0;
        int numIssuerSwitches = 0;
        int numOther = 0;
        MessageFbsDecoder decoder = MessageFbsDecoder.of();
        for (ByteBuffer message : socket.receivedBinaryMessages()){
            switch (decoder.init(message)){
            case MessagePayloadFbs.StrategySwitchFbs:
                {
                    final StrategySwitchFbs fbs = decoder.asStrategySwitchFbs();
                    if (fbs.switchSource() == StrategyParamSource.NULL_VAL.value()) {
                        numMainSwitches++;
                    }
                    else if (fbs.switchSource() == StrategyParamSource.ISSUER_SID.value()) {
                        numIssuerSwitches++;
                    }
                    else {
                        LOG.error("Got unexpected message back");
                        numOther++;                        
                    }
                }
                break;
            default:
                LOG.error("Got unexpected message back");
                numOther++;
                break;
            }
        }
        LOG.info("Received messages [numMainSwitches:{}, numIssuerSwitches:{}, numOther:{}]", numMainSwitches, numIssuerSwitches, numOther);
        assertTrue("Did not receive expected messages or number of messages", latchResult);
        
        system.close();
        
        while (system.state() != LunarSystemState.STOPPED){
            LOG.info("current state: {}, waiting...", system.state());
            LockSupport.parkNanos(500_000_000);
        }
        LOG.info("current state: {}, done...", system.state());
        assertEquals(system.state(), LunarSystemState.STOPPED);
        
        while (!system.isCompletelyShutdown()){
            LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
            LockSupport.parkNanos(500_000_000);
        }

    }   
    
    
    @Test
    @Ignore
    public void testOrderBookSnapshot() throws InterruptedException, ConfigurationException{
        String host = "127.0.0.1";
        int port = 8192;
        int jmxPort = 8787;
        String systemName = "tiger";
        System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
        System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
        System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
        System.setProperty(LunarSystem.CONFIG_FILE_PROP, 
//              "C:\\repo\\lunar\\lunar\\bin\\config\\it\\simple\\lunar.admin.dummy.strat.mds.omes.real.rds.dashboard.conf");
                this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.true.strat.conf").toString()).getPath());
                 // "C:/Users/andre/Coda/dev/repo/lunar/lunar/bin/config/it/simple/lunar.admin.true.strat.conf");
                  //"C:/Users/andre/Coda/dev/repo/lunar/lunar/bin/config/it/simple/lunar.admin.dummy.strat.mds.omes.real.rds.dashboard.conf");
        System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
        System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
   
        UserControlledSystemClock systemClock = new UserControlledSystemClock(LocalDate.of(2015, 10, 2));

        LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock);
        
        while (system.state() != LunarSystemState.STARTED){
            LOG.info("current state: {}, waiting to be started...", system.state());
            LockSupport.parkNanos(500_000_000);
        }
        LockSupport.parkNanos(500_000_000);
        String wsPath = "ws";
        int wsPort = 8123;
        String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
        WebSocketClient client = new WebSocketClient();
        final AtomicInteger expectedMessages = new AtomicInteger(18);
        
        Thread.sleep(10000);
        MarketDataReplayer replayer = MarketDataReplayer.instanceOf();
        replayer.startFeed(true, 0, Optional.empty(), systemClock, false);
        
        Thread.sleep(10000);
        
        SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return expectedMessages.get() == 0;
            }
        }, new SimpleSocket.BinaryMessageHandler() {
            
            @Override
            public void onMessage(ByteBuffer buffer) {
                MessageFbsDecoder decoder = MessageFbsDecoder.of();
                switch (decoder.init(buffer)){
                case MessagePayloadFbs.OrderBookSnapshotFbs:
                    expectedMessages.decrementAndGet();
                    LOG.debug("Current remaining count: {}", expectedMessages.get());
                    break;
                }
            }
        }, new SimpleSocket.TextMessageHandler() {
            
            @Override
            public void onMessage(String text) {
                
            }
        });

        ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
                Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ORDERBOOK_SNAPSHOT.value()),
                Parameter.of(ParameterType.SECURITY_SID, 2));
        final int anySinkId = 1;
        final int refClientKey = 5656565;
        final RequestType refRequestType = RequestType.SUBSCRIBE;

        Request request = Request.of(anySinkId,
                 refRequestType,
                 refParameters).clientKey(refClientKey);

        MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
        ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);
        
        // When
        boolean latchResult = false;
        try {
            client.start();
            URI uri = new URI(destUri);
            ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
            Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
            Session session = sessionFuture.get(5, TimeUnit.SECONDS);
            LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
            socket.send(buffer.slice());
            latchResult = socket.latch().await(100, TimeUnit.SECONDS);
        }
        catch (Throwable t){
            LOG.error("Caught throwable", t);
            throw new AssertionError("Caught throwable", t);
        }
        finally {
            try {
                LOG.info("Stopping web client");
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        LOG.info("Received number of binary messages [count:{}]", socket.receivedBinaryMessages().size());
        int numSnapshots = 0;
        int numOther = 0;
        MessageFbsDecoder decoder = MessageFbsDecoder.of();
        for (ByteBuffer message : socket.receivedBinaryMessages()){
            switch (decoder.init(message)){
            case MessagePayloadFbs.OrderBookSnapshotFbs:
                {
                    final OrderBookSnapshotFbs fbs = decoder.asOrderBookSnapshotFbs();
                    int askDepthLength = fbs.askDepthLength();
                    int bidDepthLength = fbs.bidDepthLength();
                    long secSid = fbs.secSid();
                    assertTrue(askDepthLength > 0 && askDepthLength <= 5);
                    assertTrue(bidDepthLength > 0 && bidDepthLength <= 5);
                    assertEquals(secSid, 2);
                    numSnapshots++;
                  }
                break;
            default:
                LOG.error("Got unexpected message back");
                numOther++;
                break;
            }
        }
        LOG.info("Received messages [numSnapshots:{}, numOther:{}]", numSnapshots, numOther);
        assertTrue("Did not receive expected messages or number of messages", latchResult);
        
        system.close();
        
        while (system.state() != LunarSystemState.STOPPED){
            LOG.info("current state: {}, waiting...", system.state());
            LockSupport.parkNanos(500_000_000);
        }
        LOG.info("current state: {}, done...", system.state());
        assertEquals(system.state(), LunarSystemState.STOPPED);
        
        while (!system.isCompletelyShutdown()){
            LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
            LockSupport.parkNanos(500_000_000);
        }

    }   
}
