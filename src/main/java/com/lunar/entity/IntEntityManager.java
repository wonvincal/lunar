package com.lunar.entity;

import java.util.Collection;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.agrona.collections.Long2ObjectHashMap;

import com.lunar.message.sink.MessageSinkRef;

/**
 * Subscription should be part of any entity manager
 * 
 * The basic structure of each entity manager is:
 * [Entity [Subscription]][Entity [Subscription]][Entity [Subscription]]
 * 
 * You want to put related entities close by each other.  Let say, for example, we want to store
 * Instrument, group by Instrument Type, group by Underlying Instrument
 * 
 * When you create entity instances, put them side by side of each other according to their
 * nature.  
 * 
 * [Underlying][Warrants with same underlying][CBBCs with same underlying]..
 * [Options with same underlying]
 * 
 * When we create them, if we can assign them with sequential id, they will be placed sequentially
 * in the hash map of instruments.  It will be difficult to have underlying id close to warrants 
 * id.
 * 
 * [Underlying [Multimap of InstrumentType to EntityId]]] - structure-wise, this is good because
 * we can get 
 * Note: using multimap allows me to save memory on creating vector(list) to hold the ids, which
 * is good.  The best part of using multimap is that the underlying storage size is fixed, which
 * is good for memory and latency.
 * 
 * 1) Entities for a particular InstrumentType of a Underlying
 * 2) Entities for a Underlying
 * However, using map, we are not getting any advantage in locality of reference.  Actually, it 
 * does....
 * 
 * [Group [Subscription][MemberId][MemberId]][Group [Subscription]][Group [Subscription]]
 * 
 * Subscription is an ObjArrayList.  The size of the list should be a configurable parameter.
 * 
 * create / read / update / delete / subscribe per entity / subscribe per entity group
 * 
 * create / update / delete are done on the entity manager
 * read is done on a separate service for performance purpose
 * 
 * create / update / delete (generic)
 * 
 * generic create by field id
 * generic update by field id
 * generic delete by entity sid
 * 
 * create request, parameter
 * 
 * each entity source should have an entity manager
 * 
 * How about clients of entities?
 * 
 * Load from specific message type
 * Security Definition
 * Exchange Definition
 * Broker Definition
 * 
 * Security Definition
 * - each of these should have an action type to indicate SOURCE_CREATE, SOURCE_UPDATE, SOURCE_OBSOLETE, UPDATE.
 * - each of these has a status field { ACTIVE, OBSOLETE }
 * 
 * SOURCE_CREATE - applicable to EntityManager.
 * DELETE - we don't support actual delete, that will complicate the relationship between different entities.
 * SOURCE_UPDATE - applicable to EntityManager.
 * SOURCE_OBSOLETE - applicable to EntityManager.  EntityManager should decide if it is safe to mark an entity obsolete.
 * It can reject the change request if there exists any valid foreign reference to the to-be-obsoleted entity.
 * UPDATE - applicable to clients
 * 
 * @author Calvin
 *
 * @param <E>
 */
public class IntEntityManager<T extends Entity> implements Loadable<T> {
	static final Logger LOG = LogManager.getLogger(IntEntityManager.class);

	private static final int DEFAULT_INITIAL_CAPACITY = 128;
	private static final float DEFAULT_LOAD_FACTOR = 0.6f;
	private final EntityLoader<T> loader;
	private final Long2ObjectHashMap<T> entities;
	private EntityAddRemoveHandlerList<T> handlers;

	public IntEntityManager(EntityAddRemoveHandler<T> nullHandler, EntityLoader<T> loader){
		this(DEFAULT_INITIAL_CAPACITY, nullHandler, loader);
	}
	
	public IntEntityManager(int initialCapacity, EntityAddRemoveHandler<T> nullHandler, EntityLoader<T> loader){
		this.entities = new Long2ObjectHashMap<T>(initialCapacity, DEFAULT_LOAD_FACTOR);
		this.handlers = new EntityAddRemoveHandlerList<T>(2, nullHandler);
		this.loader = loader;
	}
	
	public IntEntityManager<T> addHandler(EntityAddRemoveHandler<T> handler){
		this.handlers.add(handler);
		return this;
	}
	
	public IntEntityManager<T> removeHandler(EntityAddRemoveHandler<T> handler){
		this.handlers.remove(handler);
		return this;
	}
	
	public Collection<T> entities(){
		return this.entities.values();
	}
	
	public void subscribe(long sid, MessageSinkRef sink){
		T entity = entities.get(sid);
		if (entity != null){
			entity.addSubscriber(sink);
		}
	}
	
	public void unsubscribe(long sid, MessageSinkRef sink){
		T entity = entities.get(sid);
		if (entity != null){
			entity.removeSubscriber(sink);
		}
	}

	public void load() throws Exception{
		loader.loadInto(this);
	}
	
	public void load(T entity){
		if (entities.containsKey(entity.sid())){
			LOG.warn("cannot load entity {}, it already exists", entity.sid());
			return;
		}
		entities.put(entity.sid(), entity);		
	}
	
	public void add(T entity){
		if (entities.containsKey(entity.sid())){
			LOG.warn("cannot add entity {}, it already exists", entity.sid());
			return;
		}
		entities.put(entity.sid(), entity);
		handlers.handler().onAdd(entity);
	}
	
	public void remove(T entity){
		if (entities.remove(entity.sid()) != null){
			handlers.handler().onRemove(entity);			
		}
	}	
}
