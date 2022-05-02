package com.lunar.entity;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class NullEntityLoader<T extends Entity> implements EntityLoader<T> {
	private static final Logger LOG = LogManager.getLogger(NullEntityLoader.class);
	private final String uri;
	
	public NullEntityLoader(String uri, EntityConverter<T> converter){
		this.uri = uri;
	}
	
	@Override
	public void loadInto(Loadable<T> loadable) throws Exception {
		LOG.warn("Load into null entity loader [uri:{}]", uri);
	}
}
