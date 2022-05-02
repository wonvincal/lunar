package com.lunar.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import com.lunar.config.ServiceConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

public class ConfigTest {
	private final String CONFIG_FILE = "test.conf";
	
	@Test 
	public void testUncheckedCast(){
		ConfigFactory.invalidateCaches();
		String file = getClass().getClassLoader().getResource(CONFIG_FILE).getFile();
		Config config = ConfigFactory.parseFile(new File(file)).resolve();
		for (Entry<String, ConfigValue> entry : config.getObject("lunar.service").entrySet()){
			@SuppressWarnings("unchecked")
			ServiceConfig serviceConfig = new ServiceConfig(1, entry.getKey(), ConfigValueFactory.fromMap((Map<String,Object>)entry.getValue().unwrapped()).toConfig());
			serviceConfig.desc();
			serviceConfig.serviceType();
			serviceConfig.create();
		}
	}
	
	@Test
	public void test(){
		ConfigFactory.invalidateCaches();
		String file = getClass().getClassLoader().getResource(CONFIG_FILE).getFile();
		Config config = ConfigFactory.parseFile(new File(file)).resolve();
		Config tigerConfig = config.getConfig("tiger").withFallback(config);
		
		int count = 0;
		int create = 0;
		int warmup = 0;
		for (Map.Entry<String, ConfigValue> entry : tigerConfig.getObject("lunar.service").entrySet()){
			@SuppressWarnings("unchecked")
			ServiceConfig serviceConfig = new ServiceConfig(1, entry.getKey(), ConfigValueFactory.fromMap((Map<String,Object>)entry.getValue().unwrapped()).toConfig());
			if (serviceConfig.create()){
				create++;
			}
			if (serviceConfig.warmup()){
				warmup++;
			}
			System.out.println(entry.getKey().toString());
			System.out.println(entry.getValue().toString());
			count++;
		}
		
		assertEquals(6, count);
		assertEquals(3, create);
		assertEquals(2, warmup);
	}
}
