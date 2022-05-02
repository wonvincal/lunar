package com.lunar.escape;

import java.util.ArrayList;
import java.util.List;

public class EscapeAnalysisTest {
	public static class Listener {
		private int count;
		public void handle(Event e){
			count++;
		}
		public int count(){
			return count;
		}
	}
	public static class Event {
		
	}
	public static void main(final String[] args) throws Exception
	{
		List<Listener> listeners = new ArrayList<>();
		listeners.add(new Listener());
		
		Event e = new Event();
		for (int i = 0; i < 1000000000; i++){
			for (Listener listener : listeners){
				listener.handle(e);
			}
		}
		
		System.out.println("Total count is: " + listeners.get(0).count());
	}
}
