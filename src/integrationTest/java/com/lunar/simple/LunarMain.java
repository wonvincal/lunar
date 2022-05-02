package com.lunar.simple;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Ignore;

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
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.ConcurrentUtil;

public class LunarMain {
	private static final Logger LOG = LogManager.getLogger(LunarMain.class);
	
    public static void main(String[] args) {
        LunarMain main = new LunarMain();
        try {
            main.testStart();
        } catch (final InterruptedException | UnsupportedEncodingException | ConfigurationException e) {
            e.printStackTrace();
        }        
    }

    @Ignore
	public void testStart() throws InterruptedException, UnsupportedEncodingException, ConfigurationException{
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
		
		ConcurrentUtil.sleep(15000);
		
		system.adminService().messenger().createChildMessenger().sendRequest(system.getMessageSinkRefMgr().omes(), RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, 1000000000)));
		
		final MessageSinkRef senderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.DashboardService, "test");
		final int clientKey = 1212;
		
        Command command = Command.of(senderSink.sinkId(),
                clientKey,
                CommandType.LINE_HANDLER_ACTION, 
                new ImmutableList.Builder<Parameter>().add(
                        Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW.value())).build());
        //CommandSender.of(sender).sendCommand(system.getMessageSinkRefMgr().get(7), command);
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
        replayer.startFeed(false, 0, Optional.empty(), systemClock, false);
		
		ConcurrentUtil.sleep(5000);
        command = Command.of(senderSink.sinkId(),
                clientKey,
                CommandType.STOP, 
                 new ArrayList<Parameter>(), 
                 BooleanType.TRUE);
		for (int i = 0; i < strats.size(); i++){
			system.adminService().messenger().createChildMessenger().sendCommand(strats.elements()[i], command);
		}
		
		ConcurrentUtil.sleep(15000);
		
		replayer.waitForFeedToFinish();
		
		ConcurrentUtil.sleep(30000);
		
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
}
