package com.lunar.core;

public interface ObjectFactory<T> {
	T create(ObjectPool<T> pool);
}
