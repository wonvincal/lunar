package com.lunar.entity;

public interface EntityAddRemoveHandler<T extends Entity> {
	void onAdd(T entity);
	void onRemove(T entity);
	
}
