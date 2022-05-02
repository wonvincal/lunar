package com.lunar.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.lunar.core.GrowthFunction;
import com.lunar.core.LunarSystem;
import com.lunar.core.WaitStrategy;
import com.lunar.message.RequestTypeSetting;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class LunarConfigTest {
	private String configFile;
	private final String systemName = "tiger";
	private final String host = "127.0.0.1";
	private final String aeronDir = "/lunar/aeron/";
	private final int port = 1234;
	
	@Before
	public void setup() throws UnsupportedEncodingException{
		configFile = URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "lunar.config.conf").toString()).getPath(), "UTF-8");
	}
	
	@Test
	public void test() throws IOException{
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, configFile);
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, aeronDir);
		
		ConfigFactory.invalidateCaches();
		Config rawConfig = ConfigFactory.load().resolve();
		
		// SystemConfig
		SystemConfig systemConfig = SystemConfig.of(systemName, host, port, rawConfig);
		assertEquals(0, systemConfig.deadLettersSinkId());
		assertEquals(host, systemConfig.host());
		assertEquals(systemName, systemConfig.name());
		assertEquals(port, systemConfig.port());
		assertEquals("tempLogging", systemConfig.loggingFolder());
		assertEquals("tempArchive", systemConfig.loggingArchiveFolder());
		assertEquals(false, systemConfig.loggingArchiveOnStart());

		// AdminServiceConfig
		AdminServiceConfig adminServiceConfig = systemConfig.adminServiceConfig();
		assertEquals(true, adminServiceConfig.create());
		assertEquals(1, adminServiceConfig.sinkId());
		assertEquals("lunarAdmin", adminServiceConfig.name());
		
		// AeronConfig
		CommunicationConfig aeronConfig = systemConfig.aeronConfig();
		assertEquals("aeron:udp?endpoint=127.0.0.1:55160", aeronConfig.channel());
		assertEquals(host, aeronConfig.hostname());
		assertEquals(aeronDir, aeronConfig.aeronDir());
		assertEquals(55160, aeronConfig.port());
		assertEquals(1, aeronConfig.streamId());
		assertEquals("udp", aeronConfig.transport());
		
		// ServiceConfig
		List<ServiceConfig> serviceConfigs = adminServiceConfig.childServiceConfigs();
		for (ServiceConfig serviceConfig : serviceConfigs){
			if (serviceConfig.serviceType() == ServiceType.ClientService){
				assertTrue(serviceConfig instanceof ClientServiceConfig);
				ClientServiceConfig specificConfig = (ClientServiceConfig)serviceConfig;
				assertEquals(10, specificConfig.exchangeSinkId());
			}
			else if (serviceConfig.serviceType() == ServiceType.ExchangeService){
				assertTrue(serviceConfig instanceof ExchangeServiceConfig);
			}
			else if (serviceConfig.serviceType() == ServiceType.PerformanceService){
				assertTrue(serviceConfig instanceof PerformanceServiceConfig);
				PerformanceServiceConfig perfConfig = (PerformanceServiceConfig)serviceConfig;
				assertEquals(Duration.ofSeconds(5).toNanos(), perfConfig.statGatheringFreqNs());
			}
			else if (serviceConfig.serviceType() == ServiceType.OrderManagementAndExecutionService){
				assertTrue(serviceConfig instanceof OrderManagementAndExecutionServiceConfig);
				OrderManagementAndExecutionServiceConfig omesConfig = (OrderManagementAndExecutionServiceConfig)serviceConfig;
				assertEquals(128, omesConfig.numOutstandingOrderRequests());
				assertEquals(1024, omesConfig.numOutstandingOrders());
				assertEquals(1024, omesConfig.numOutstandingOrderBooks());
				assertTrue(omesConfig.avoidMultiCancelOrAmend());
				
				LineHandlerConfig lineHandlerConfig = omesConfig.lineHandlerConfig();
				assertNotNull(lineHandlerConfig);
				assertEquals(1024, lineHandlerConfig.numOutstandingOrders());
				assertEquals(0, "line handler".compareTo(lineHandlerConfig.desc()));
				assertEquals(12, lineHandlerConfig.id());
				assertEquals("lunarLineHandler", lineHandlerConfig.name());
				assertEquals("com.lunar.order.NullLineHandlerEngine", lineHandlerConfig.lineHandlerEngineClass().getName());
				assertEquals(128, lineHandlerConfig.ordExecQueueSize());
				assertEquals(WaitStrategy.BLOCKING_WAIT, lineHandlerConfig.ordExecWaitStrategy());
				assertEquals(1024, lineHandlerConfig.ordUpdRecvQueueSize());
				assertEquals(WaitStrategy.BLOCKING_WAIT, lineHandlerConfig.ordUpdRecvWaitStrategy());
				assertTrue(lineHandlerConfig.singleProducerToOrdExec());
				assertFalse(lineHandlerConfig.singleProducerToOrdUpdRecv());
				assertEquals(4, lineHandlerConfig.throttle());
				assertEquals(123456789, omesConfig.initPurchasingPower().get().intValue());
				assertEquals(0, "28629,21000".compareTo(omesConfig.existingPositions().get()));
				List<Integer> items = lineHandlerConfig.throttleArrangement().get();
				assertEquals(2, items.get(0).intValue());
				assertEquals(2, items.get(1).intValue());
			}
			else if (serviceConfig.serviceType() == ServiceType.OrderAndTradeSnapshotService){
				assertTrue(serviceConfig instanceof OrderAndTradeSnapshotServiceConfig);
				OrderAndTradeSnapshotServiceConfig config = (OrderAndTradeSnapshotServiceConfig)serviceConfig;
				assertEquals(4, config.numChannels());
				assertEquals(32, config.expectedNumOrdersPerChannel());
				assertEquals(128, config.expectedNumTradesPerChannel());
				assertEquals(1024, config.expectedNumOrders());
				assertEquals(1024, config.expectedNumTrades());
				assertEquals(TimeUnit.MILLISECONDS.toNanos(500), config.publishFrequency().toNanos());
			}
			else if (serviceConfig.serviceType() == ServiceType.PortfolioAndRiskService){
				assertTrue(serviceConfig instanceof PortfolioAndRiskServiceConfig);
				PortfolioAndRiskServiceConfig config = (PortfolioAndRiskServiceConfig)serviceConfig;
				assertEquals(TimeUnit.MILLISECONDS.toNanos(500), config.publishFrequency().toNanos());
				assertRiskControlConfig(PortfolioAndRiskServiceConfig.RiskControlConfig.of(
						Optional.of(100000l), Optional.of(1000000d), Optional.of(-1000000d), Optional.of(1000000d)), 
						config.defaultSecurityRiskControlConfig());
				assertRiskControlConfig(PortfolioAndRiskServiceConfig.RiskControlConfig.of(
						Optional.of(200000l), Optional.of(2000000d), Optional.of(-2000000d), Optional.of(2000000d)), config.defaultIssuerRiskControlConfig());
				assertRiskControlConfig(PortfolioAndRiskServiceConfig.RiskControlConfig.of(
						Optional.of(300000l), Optional.of(3000000d), Optional.of(-3000000d), Optional.of(3000000d)), config.defaultUndRiskControlConfig());
				assertRiskControlConfig(PortfolioAndRiskServiceConfig.RiskControlConfig.of(
						Optional.of(500000l), Optional.of(5000000d), Optional.of(-5000000d), Optional.of(-5000000d)), config.defaultFirmRiskControlConfig());
			}
			else if (serviceConfig.serviceType() == ServiceType.DashboardService){
				assertTrue(serviceConfig instanceof DashboardServiceConfig);
				DashboardServiceConfig config = (DashboardServiceConfig)serviceConfig;
				assertEquals(1, config.marketDataAutoSubSecSids().get().get(0).intValue(), 0);
				assertEquals(5, config.marketDataAutoSubSecSids().get().get(1).intValue(), 0);
				assertEquals(700, config.marketDataAutoSubSecSids().get().get(2).intValue(), 0);
				assertEquals(1235, config.tcpListeningPort().get().intValue());
			}
			else if (serviceConfig.serviceType() == ServiceType.DashboardStandAloneWebService){
				assertTrue(serviceConfig instanceof DashboardStandAloneWebServiceConfig);
				DashboardStandAloneWebServiceConfig config = (DashboardStandAloneWebServiceConfig)serviceConfig;
				assertEquals(1234, config.port());
				assertEquals(0, config.wsPath().compareTo("ws"));
				assertEquals(10, config.numIoThreads().get().intValue());
				assertEquals(80, config.numCoreThreads().get().intValue());
				assertEquals(32768, config.bufferSizeInBytes().get().intValue());
				assertEquals(60_000_000_000l, config.requestTimeout().get().toNanos());
				assertEquals(1235, config.tcpListeningPort().get().intValue());
				assertEquals(0, config.tcpListeningUrl().get().compareTo("http://127.0.0.1"));
			}
			else if (serviceConfig.serviceType() == ServiceType.MarketDataSnapshotService){
				assertTrue(serviceConfig instanceof MarketDataSnapshotServiceConfig);
				MarketDataSnapshotServiceConfig config = (MarketDataSnapshotServiceConfig)serviceConfig;
				assertEquals(5000000000L, config.orderFlowWindowSizeNs());
				assertEquals(1.0d, config.orderFlowSupportedWindowSizeMultipliers().get(0).doubleValue(), 0);
				assertEquals(5.0d, config.orderFlowSupportedWindowSizeMultipliers().get(1).doubleValue(), 0);
				assertEquals(10.0d, config.orderFlowSupportedWindowSizeMultipliers().get(2).doubleValue(), 0);
				
				assertEquals(1, config.orderFlowEnabledSecSids().get().get(0).intValue(), 0);
				assertEquals(5, config.orderFlowEnabledSecSids().get().get(1).intValue(), 0);
				assertEquals(700, config.orderFlowEnabledSecSids().get().get(2).intValue(), 0);
			}
			assertTrue(serviceConfig.create());
			assertTrue(serviceConfig.sinkId() > 0);
		}
		
		// MessagingConfig
		MessagingConfig messagingConfig = systemConfig.messagingConfig();
		assertEquals(128, messagingConfig.frameSize());
		assertEquals(Duration.ofSeconds(5), messagingConfig.commandTimeout());
		EnumMap<RequestType, RequestTypeSetting> reqTypeSettings = messagingConfig.reqTypeSettings();
		for (Entry<RequestType, RequestTypeSetting> item : reqTypeSettings.entrySet()){
			RequestType requestType = item.getKey();
			RequestTypeSetting setting = item.getValue();
			if (requestType == RequestType.GET_PERF_STAT){
				assertEquals(Duration.ofSeconds(2).toNanos(), setting.timeoutNs());
				assertEquals(Duration.ofSeconds(2).toNanos(), setting.initDelayNs());
				assertEquals(-1, setting.maxRetryAttempts());
				assertEquals(GrowthFunction.CONSTANT, setting.growth());
			}
			else{
				assertEquals(Duration.ofSeconds(5).toNanos(), setting.timeoutNs());
				assertEquals(Duration.ofSeconds(5).toNanos(), setting.initDelayNs());
				assertEquals(0, setting.maxRetryAttempts());
				assertEquals(GrowthFunction.CONSTANT, setting.growth());				
			}
		}
		
		// TimerServiceConfig
		final long expectedTickDurationInMs = 100;
		final int expectedTickPerWheel = 256;
		TimerServiceConfig timerServiceConfig = systemConfig.timerServiceConfig();
		assertEquals(expectedTickDurationInMs, timerServiceConfig.tickDuration().toMillis());
		assertEquals(expectedTickPerWheel, timerServiceConfig.ticksPerWheel());
	}
	
	public static void assertRiskControlConfig(PortfolioAndRiskServiceConfig.RiskControlConfig expected, 
			PortfolioAndRiskServiceConfig.RiskControlConfig value){
		assertTrue(expected.maxOpenPosition().equals(value.maxOpenPosition()));
		assertTrue(expected.maxLoss().equals(value.maxLoss()));
		assertTrue(expected.maxProfit().equals(value.maxProfit()));
	}
}
