package com.lunar.entity;

public interface Loadable<T extends Entity> {
	void load(T entity);
}
