package com.lunar.util;

public interface ClientKey {
	public static int INVALID_CLIENT_KEY = -1;
	
	int key();
	
	static ClientKey NULL_INSTANCE = new ClientKey() {
		@Override
		public int key() {
			return INVALID_CLIENT_KEY;
		}
	};
}
