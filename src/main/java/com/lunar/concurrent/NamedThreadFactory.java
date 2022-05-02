package com.lunar.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Just for the sake of naming the thread
 * @author Calvin
 *
 */
public class NamedThreadFactory implements ThreadFactory{
	private final String groupName;
	private final String threadName;
	private final AtomicInteger counter;
	
	public NamedThreadFactory(String groupName, String threadName){
		this.groupName = groupName;
		this.threadName = threadName;
		this.counter = new AtomicInteger();
	}
	
	@Override
	public Thread newThread(Runnable r) {
		return new Thread(new ThreadGroup(groupName), r, threadName + "-" + counter.getAndIncrement());
	}

}
