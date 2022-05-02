package com.lunar.entity;

public interface EntityLoader<T extends Entity> {
	void loadInto(Loadable<T> loadable) throws Exception;
}