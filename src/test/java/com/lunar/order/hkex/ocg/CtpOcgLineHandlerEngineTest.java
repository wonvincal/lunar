package com.lunar.order.hkex.ocg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.order.OrderUpdateEventProducer;
import com.lunar.service.ServiceConstant;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class CtpOcgLineHandlerEngineTest {
	static {
		ServiceConstant.LOAD_JNI = false;
	}
	@Mock
	private CtpOcgApi api;
	
	@Mock
	private OrderUpdateEventProducer eventProducer;

	private TestHelper helper = TestHelper.of();
	@SuppressWarnings("unused")
	private CtpOcgLineHandlerEngine engine;
	
	@Before
	public void setup(){
		String account = "test-account";
		String user = "test-user";
		String configFile = "test-config-file";
		String connectorFile = "test-connector-file";
		String name = "test-engine";
		CtpOcgLineHandlerEngine engine = new CtpOcgLineHandlerEngine(name, helper.timerService(), helper.systemClock(), connectorFile, configFile, user, account);
		engine.setOcgApi(api);
		engine.init(eventProducer);
		assertEquals(0, name.compareTo(engine.name()));
		assertEquals(0, configFile.compareTo(engine.configFile()));
		assertEquals(0, connectorFile.compareTo(engine.connectorFile()));
		assertEquals(0, account.compareTo(engine.account()));
		assertEquals(0, user.compareTo(engine.user()));
		assertFalse(engine.isConnected());
		assertFalse(engine.isRecovered());
	}
	
	@Test
	public void testCreate(){
		TestHelper helper = TestHelper.of();
		String account = "test-account";
		String user = "test-user";
		String configFile = "test-config-file";
		String connectorFile = "test-connector-file";
		String name = "test-engine";
		CtpOcgLineHandlerEngine engine = new CtpOcgLineHandlerEngine(name, helper.timerService(), helper.systemClock(), connectorFile, configFile, user, account);
		assertNotNull(engine);
		engine.setOcgApi(api);
	}
	
	@Test
	public void givenCreatedWhenWarmupThen(){
	}
}
