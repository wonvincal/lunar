package com.lunar.entity;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.sink.MessageSinkRef;

public class GroupEntityManager<E extends Entity> implements EntityAddRemoveHandler<E> {
	static final Logger LOG = LogManager.getLogger(GroupEntityManager.class);
	private final LongEntityManager<E> entityManager;
	private Function<E, GroupKey> keyFunction;
	private final Object2ObjectArrayMap<GroupKey, ObjectArrayList<E>> entitiesByGroupKey;
	private final Object2ObjectArrayMap<GroupKey, ObjectArrayList<MessageSinkRef>> subscribersByGroupKey;
	
	public GroupEntityManager(LongEntityManager<E> entityManager, Function<E, GroupKey> keyFunction){
		this.keyFunction = keyFunction;
		this.entityManager = entityManager;
		this.entitiesByGroupKey = new Object2ObjectArrayMap<GroupKey, ObjectArrayList<E>>();
		this.subscribersByGroupKey = new Object2ObjectArrayMap<GroupKey, ObjectArrayList<MessageSinkRef>>();
	}
	
	public Function<E, GroupKey> keyFunction(){
		return keyFunction;
	}
	
	public void load(){
		Collection<E> entities = this.entityManager.entities();
		for (E entity : entities){
			GroupKey key = keyFunction.apply(entity);
			ObjectArrayList<E> entityList = this.entitiesByGroupKey.get(key);
			if (entityList == null){
				entityList = new ObjectArrayList<E>();
				this.entitiesByGroupKey.put(key, entityList);
			}
			entityList.add(entity);
		}
	}
	
	@Override
	public void onAdd(E entity) {
		GroupKey key = keyFunction.apply(entity);
		// TODO find out if we can specify the 'type' of the list
		// we want 'set' to avoid duplicate entries.
		List<E> entities;
		entities = entitiesByGroupKey.get(key);
		if (entities == null){
			entities = new ArrayList<E>();
		}
		entities.add(entity);
	}

	@Override
	public void onRemove(E entity) {
		GroupKey key = keyFunction.apply(entity);
		// TODO find out if we can specify the 'type' of the list
		// we want 'set' to avoid duplicate entries.
		List<E> entities;
		entities = entitiesByGroupKey.get(key);
		if (entities == null){
			LOG.error("entity {} cannot be removed, group key does not exist", key);
			return;
		}
		if (!entitiesByGroupKey.get(key).remove(entity)){
			LOG.error("entity {} cannot be removed, it is not in the list", entity.sid());
		}
 	}
	
	public void subscribe(GroupKey key, MessageSinkRef sink){
		ObjectArrayList<MessageSinkRef> subscribers = subscribersByGroupKey.get(key);
		if (subscribers == null){
			subscribers = new ObjectArrayList<MessageSinkRef>();
			subscribersByGroupKey.put(key, subscribers);
		}
		subscribers.add(sink);
		List<E> entities = entitiesByGroupKey.get(key);
		for (Entity entity : entities){
			if (!entity.addSubscriber(sink)){
				LOG.warn("trying to subscriber to something that has already been subscribed: sink:{}, groupKey:{}", sink.sinkId(), key);
			}
		}
	}
	
	public void unsubscribe(GroupKey key, MessageSinkRef sink){
		ObjectArrayList<MessageSinkRef> subscribers = subscribersByGroupKey.get(key);
		if (subscribers == null){
			return;
		}
		if (subscribers.rem(sink)){
			List<E> entities = entitiesByGroupKey.get(key);
			for (Entity entity : entities){
				if (!entity.removeSubscriber(sink)){
					LOG.warn("unable to subscribe to something that has not been subscribed: sink:{}, groupKey:{}", sink.sinkId(), key);
				}
			}
		}		
	}
}
