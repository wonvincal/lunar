package com.lunar.util;

import java.util.ArrayList;
import java.util.List;

public class EscapeAnalysisTest {
	public static class Listener{
		private long count;
		public void onEvent(Event e){
			count++;
		}
		public long count(){
			return count;
		}
	}
	
	public static class Event{
		private int value;
		Event(int value){
			this.value = value;
		}
		public int value(){
			return value;
		}
	}

	public static void loop(List<Listener> listeners, Event event){
		for(int i = 0; i < 10000000; i++) {
			for(Listener listener : listeners) {
				listener.onEvent(event);
			}
		}
	}

	public static void main(final String[] args) throws Exception
	{
		List<Listener> listeners = new ArrayList<Listener>();
		listeners.add(new Listener());
		
		Event event = new Event(1);
		System.out.println("Warmup begins: " + listeners.get(0).count());
		
		loop(listeners, event);
		
		System.out.println("Event Count: " + listeners.get(0).count());
		System.out.println("Press any key to continue (check jmap -histo)");
        System.in.read();

		loop(listeners, event);

		System.out.println("Event Count: " + listeners.get(0).count());
		System.out.println("Press any key to continue (check jmap -histo)");
        System.in.read();

		loop(listeners, event);

		System.out.println("Event Count: " + listeners.get(0).count());
		System.out.println("Press any key to continue (check jmap -histo)");
        System.in.read();
	}
}
