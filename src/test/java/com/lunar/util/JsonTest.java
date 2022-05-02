package com.lunar.util;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class JsonTest {
	static final Logger LOG = LogManager.getLogger(JsonTest.class);

	@Test
	public void test(){
		ServiceStatus status = ServiceStatus.of(1, 2, ServiceType.AdminService, ServiceStatusType.DOWN, System.nanoTime());
		
		Gson gson = new Gson();
		LOG.info(gson.toJson(status));
		
		JsonElement element = gson.toJsonTree(status);
		JsonObject object = new JsonObject();
		object.add(TemplateType.SERVICE_STATUS.name(), element);
		LOG.info("JsonObject: {}", gson.toJson(object));

		
		
		Int2ObjectOpenHashMap<ServiceStatus> statuses = new Int2ObjectOpenHashMap<>();
		statuses.put(2, status);
		statuses.put(3, ServiceStatus.of(1, 3, ServiceType.RefDataService, ServiceStatusType.DOWN, System.nanoTime()));
		
		List<ServiceStatus> statusList = new ArrayList<ServiceStatus>();
		statusList.add(status);
		statusList.add(ServiceStatus.of(1, 3, ServiceType.RefDataService, ServiceStatusType.DOWN, System.nanoTime()));
		LOG.info(gson.toJson(statusList));
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void testMap(){
		String sample = "{\"meta\":{\"remote\":\"true\"},\"type\":\"GET_SERVICE_STATUS\", \"sinkId\": \"123\"}";
		
		Gson gson = new Gson();
		LinkedTreeMap map = gson.fromJson(sample, LinkedTreeMap.class);
		assertTrue(map.get("type").toString().compareTo("GET_SERVICE_STATUS") == 0);
		
		
		LinkedTreeMap meta = (LinkedTreeMap)map.get("meta");
		LOG.info("meta: {}", meta.getClass().getName());
		assertTrue(meta.get("remote").toString().compareTo("true") == 0);
	}
}
