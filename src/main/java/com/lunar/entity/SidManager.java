package com.lunar.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class SidManager<T> {
    static public final long INVALID_SID = -1L;    
    
	private long m_nextSid;
	final private long m_initialSid;
	final private Object2LongOpenHashMap<T> m_keyToSidMap;
	final private Long2ObjectOpenHashMap<T> m_sidToKeyMap;
	
	public SidManager(final int defaultSize, final long initialSid) {		
		m_nextSid = initialSid;
		m_initialSid = initialSid;
		m_keyToSidMap = new Object2LongOpenHashMap<T>(defaultSize);		
		m_keyToSidMap.defaultReturnValue(INVALID_SID);
		m_sidToKeyMap = new Long2ObjectOpenHashMap<T>(defaultSize);
	}
	
	public long getInitialSid() {
		return m_initialSid;
	}
	
	public long peekNextSid() {
	    return m_nextSid;
	}
	
	public long setSidForKey(final long sid, final T key) {
	    m_keyToSidMap.put(key,  sid);
	    m_sidToKeyMap.put(sid, key);
	    return sid;
	}
	
	public long getNextSid() {
		return m_nextSid++;
	}
	
	public long getNextSid(final T key) {
		m_keyToSidMap.put(key, m_nextSid);
		m_sidToKeyMap.put(m_nextSid, key);
		return m_nextSid++;
	}
	
	public long getSidForKey(final T key) {
		return m_keyToSidMap.getLong(key);
	}
	
	public T getKeyForSid(final long sid) {
	    return m_sidToKeyMap.get(sid);
	}
}
