package com.lunar.core;

import java.util.Collection;

import com.lunar.entity.Security;
import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/*
 * This class is to be instantiate by MarketDataService
 * MarketDataService will add or remove subscribers
 * The MarketDataSource, which is expected to run on a different thread from MarketDataService, will read the list of subscribers
 * This class needs to support one producer and multiple consumers.
 * To avoid locking, the subscription lists are immutable.
 * The assumption is that we have far fewer subscription calls (especially when running) than reading the list of subscribers.
 * To avoid using concurrent lock, there is a limitation in that the list of instruments must be known when this class is constructed
 * (although we can get around it by making subscriptionsBySymbol/subscriptionsBySid non-final and volatile,
 * and re-create the list when the instrument universe is updated)
 */
public class MultiThreadEntitySubscriptionManager {
    public static class EntitySubscription {
        public final long sid;
        public volatile MessageSinkRef[] sinks;

        public EntitySubscription(final long sid) {
            this.sid = sid;
            this.sinks = null;
        }

    }

    // change to volatile and not final if we need to support instrument universe updates. then write a function to rebuild the map (i.e. map itself should be immutable)
    final private Object2ObjectOpenHashMap<String, EntitySubscription> subscriptionsBySymbol;
    final private Long2ObjectOpenHashMap<EntitySubscription> subscriptionsBySid;
    final private int maxCapacity;

    public MultiThreadEntitySubscriptionManager(final Long2ObjectOpenHashMap<String> entities, int maxCapacity) {
        subscriptionsBySymbol = new Object2ObjectOpenHashMap<String, EntitySubscription>(entities.size());
        subscriptionsBySid = new Long2ObjectOpenHashMap<EntitySubscription>(entities.size());
        this.maxCapacity = maxCapacity;
        for (final Long2ObjectMap.Entry<String> entity : entities.long2ObjectEntrySet()) {
            final EntitySubscription subscription = new EntitySubscription(entity.getLongKey());
            subscriptionsBySid.put(entity.getLongKey(), subscription);
            subscriptionsBySymbol.put(entity.getValue(), subscription);			
        }
    }

    // TODO should be Collection<Entity> but Entity does not have code() - so either add code to entity or create EntityWithCode interface
    public static MultiThreadEntitySubscriptionManager create(final Collection<Security> entities, int maxCapacity) {
        Object2ObjectOpenHashMap<String, EntitySubscription> subscriptionsBySymbol = new Object2ObjectOpenHashMap<String, EntitySubscription>(entities.size());
        Long2ObjectOpenHashMap<EntitySubscription> subscriptionsBySid = new Long2ObjectOpenHashMap<EntitySubscription>(entities.size());
        for (final Security entity : entities) {
            final EntitySubscription subscription = new EntitySubscription(entity.sid());
            subscriptionsBySid.put(entity.sid(), subscription);
            subscriptionsBySymbol.put(entity.code(), subscription);		
        }
        return new MultiThreadEntitySubscriptionManager(subscriptionsBySymbol, subscriptionsBySid, maxCapacity);
    }

    MultiThreadEntitySubscriptionManager(Object2ObjectOpenHashMap<String, EntitySubscription> subscriptionsBySymbol, Long2ObjectOpenHashMap<EntitySubscription> subscriptionsBySid, int maxCapacity) {
        this.subscriptionsBySid = subscriptionsBySid;
        this.subscriptionsBySymbol = subscriptionsBySymbol;
        this.maxCapacity = maxCapacity;		
    }

    //Comment out this if we need to support adding instruments
    //Remove method will be something similar...
    //public void addInstrument(final Security security) {
    //	final Object2ObjectOpenHashMap<String, SecuritySubscription> subscriptionsBySymbol = new Object2ObjectOpenHashMap<String, SecuritySubscription>(this.subscriptionsBySymbol);
    //	final Long2ObjectOpenHashMap<SecuritySubscription> subscriptionsBySid = new Long2ObjectOpenHashMap<SecuritySubscription>(this.subscriptionsBySid);
    //	final SecuritySubscription subscription = new SecuritySubscription(security.sid());
    //	subscriptionsBySid.put(security.sid(), subscription);
    //	subscriptionsBySymbol.put(security.code(),  subscription);
    //	this.subscriptionsBySid = subscriptionsBySid;
    //	this.subscriptionsBySymbol = subscriptionsBySymbol;
    //}

    public boolean isSubscribed(final long sid) {
        final EntitySubscription subscription = subscriptionsBySid.get(sid);
        return subscription != null && subscription.sinks != null;
    }

    public boolean isSubscribed(final String symbol) {
        final EntitySubscription subscription = subscriptionsBySymbol.get(symbol);
        return subscription != null && subscription.sinks != null;
    }

    public boolean addMessageSink(final long sid, final MessageSinkRef sink) {
        final EntitySubscription subscription = subscriptionsBySid.get(sid);
        if (subscription != null) {
            final MessageSinkRef[] newSinks = new MessageSinkRef[maxCapacity];
            if (subscription.sinks == null) {
                newSinks[0] = sink;
                subscription.sinks = newSinks;
                return true;
            }
            else {
                for (int i = 0; i < maxCapacity; i++) {
                    final MessageSinkRef oldSink = subscription.sinks[i];				
                    if (oldSink == null) {
                        newSinks[i] = sink;
                        subscription.sinks = newSinks;
                        return true;
                    }
                    else if (oldSink.sinkId() == sink.sinkId()) {
                        return true;
                    }
                    else {
                        newSinks[i] = oldSink;
                    }
                }				
            }
        }
        return false;
    }

    public boolean removeMessageSink(final long sid, final MessageSinkRef sink) {
        final EntitySubscription subscription = subscriptionsBySid.get(sid);
        if (subscription != null) {
            if (subscription.sinks == null) {
                return true;
            }
            else {
                final MessageSinkRef[] newSinks = new MessageSinkRef[maxCapacity];
                int j = 0;
                boolean hasRemoved = false;
                for (int i = 0; i < maxCapacity; i++) {
                    final MessageSinkRef oldSink = subscription.sinks[i];		
                    if (oldSink == null) {
                        break;
                    }
                    else if (oldSink.sinkId() != sink.sinkId()) {
                        newSinks[j++] = oldSink;
                    }
                    else {
                        hasRemoved = true;
                    }
                }
                if (hasRemoved) {
                    subscription.sinks = j == 0 ? null : newSinks;
                }
            }
            return true;
        }
        return false;
    }

    public MessageSinkRef[] getMessageSinks(final long sid) {
        final EntitySubscription subscription = subscriptionsBySid.get(sid);
        return subscription != null ? subscription.sinks : null;
    }

    public EntitySubscription getSubscription(final long sid) {
        return subscriptionsBySid.get(sid);
    }

    public MessageSinkRef[] getMessageSinks(final String symbol) {
        final EntitySubscription subscription = subscriptionsBySymbol.get(symbol);
        return subscription != null ? subscription.sinks : null;
    }

    public EntitySubscription getSubscription(final String symbol) {
        return subscriptionsBySymbol.get(symbol);
    }

}
