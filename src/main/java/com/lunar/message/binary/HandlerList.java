package com.lunar.message.binary;

import java.io.PrintStream;
import java.lang.reflect.Array;

import org.agrona.DirectBuffer;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public class HandlerList<C> implements Handler<C>{
	private final ListWrapper<C> handlersWrapper;
	private final Handler<C> nullHandler;
	private Handler<C> handler;

	public HandlerList(int capacity, Handler<C> nullHandler){
		this.handlersWrapper = new ListWrapper<C>(capacity);
		this.handler = nullHandler;
		this.nullHandler = nullHandler;
	}
	public Handler<C> handler(){
		return this.handler;
	}

	@Override
	public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, C codec) {
		this.handler.handle(buffer, offset, header, codec);
	}
	
	@Override
	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, C codec) {
		this.handler.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, codec);
	}
	
	@Override
	public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header, C payload) {
		return handler.output(outputStream, userSupplied, journalRecord, buffer, offset, header, payload);
	}

	public HandlerList<C> clear(){
		if (handlersWrapper.isEmpty()){
			if (handler == nullHandler){
				return this;
			}
		}
		handlersWrapper.clear();
		handler = nullHandler;
		return this;
	}
	
	public HandlerList<C> add(HandlerList<C> sourceHandlerList){
		if (sourceHandlerList.handlersWrapper.isEmpty()){
			if (sourceHandlerList.handler == nullHandler){
				return this;
			}
			this.add(sourceHandlerList.handler);
			return this;
		}
		for (int i = 0; i < sourceHandlerList.handlersWrapper.handlers.size(); i++){
			this.add(sourceHandlerList.handlersWrapper.handlers.elements()[i]);
		}
		return this;
	}
	
	public boolean prepend(Handler<C> newHandler){
        if (newHandler == null || newHandler == nullHandler){
            throw new IllegalArgumentException("cannot add null handler");
        }
        if (this.handler == nullHandler){ // no handler at the moment
            this.handler = newHandler;
            return true;
        }
        if (this.handler == newHandler){ // one handler and is same as input
            return true;
        }
        if (handlersWrapper.isEmpty()){
            this.handlersWrapper.add(newHandler);
            this.handlersWrapper.add(handler);
            this.handler = this.handlersWrapper;
            return true;
        }
        if (!this.handlersWrapper.contains(newHandler)){
            return this.handlersWrapper.prepend(newHandler);
        }
        return false;
	}
	
	Handler<C> get(int index){
	    if (this.handler == nullHandler){
	        throw new IndexOutOfBoundsException();
	    }
	    if (this.handler != this.handlersWrapper)
	    {
	        if (index == 0){
	            return this.handler;
	        }
	        throw new IndexOutOfBoundsException();
	    }
	    return this.handlersWrapper.get(index);
	}
	
	public HandlerList<C> add(Handler<C> newHandler){
		if (newHandler == null || newHandler == nullHandler){
			throw new IllegalArgumentException("cannot add null handler");
		}
		if (this.handler == nullHandler){ // no handler at the moment
			this.handler = newHandler;
			return this;
		}
		if (this.handler == newHandler){ // one handler and is same as input
			return this;
		}
		if (handlersWrapper.isEmpty()){
			this.handlersWrapper.add(handler);
			this.handlersWrapper.add(newHandler);
		}
		else if (!handlersWrapper.contains(newHandler)){
			this.handlersWrapper.add(newHandler);
		}
		this.handler = this.handlersWrapper;
		return this;
	}
	
	public Handler<C> remove(Handler<C> remove){
		if (remove == null || remove == nullHandler){
			throw new IllegalArgumentException("cannot remoive null handler");
		}
		if (handlersWrapper.remove(remove)){
			if (handlersWrapper.size() >= 2){
				assert this.handler == handlersWrapper;
				return this.handler; // return list wrapper is size >= 2
			}
			this.handler = handlersWrapper.get(0); // take the last handler out
			handlersWrapper.clear();
			return this.handler;
		}
		if (this.handler == remove){
			this.handler = nullHandler; // replace last active handler with null handler
		}
		return this.handler;
	}
	public Handler<C> nullHandler(){
		return nullHandler;
	}
	
	boolean contains(Handler<C> value){
		if (this.handler == this.handlersWrapper){
			return this.handlersWrapper.contains(value);			
		}
		return this.handler == value;
	}
	
	private static class ListWrapper<C> implements Handler<C> {
		private final ReferenceArrayList<Handler<C>> handlers;
		ListWrapper(int capacity){
			@SuppressWarnings("unchecked")
			Handler<C>[] item = (Handler<C>[])(Array.newInstance(Handler.class, capacity));
			handlers = ReferenceArrayList.wrap(item);
			handlers.size(0);
		}
		boolean add(Handler<C> handler){
			if (!handlers.contains(handler)){
				return handlers.add(handler);
			}
			return true; // return turn if it is already in the list
		}
		boolean prepend(Handler<C> handler){
            if (!handlers.contains(handler)){
                handlers.add(0, handler);
                return true;
            }
            return false;
        }
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, C codec) {
			Handler<C>[] items = handlers.elements();
			for (int i = 0; i < handlers.size(); i++){
				items[i].handle(buffer, offset, header, codec);
			}
		}
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, C codec) {
			Handler<C>[] items = handlers.elements();
			for (int i = 0; i < handlers.size(); i++){
				items[i].handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, codec);
			}			
		}
		@Override
		public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header, C payload) {
			Handler<C>[] items = handlers.elements();
			boolean result = false;
			for (int i = 0; i < handlers.size(); i++){
				result |= items[i].output(outputStream, userSupplied, journalRecord, buffer, offset, header, payload);
			}
			return result;
		}
		public boolean contains(Handler<C> handler){
			return handlers.contains(handler);
		}
		public boolean remove(Handler<C> handler){
			return handlers.rem(handler);
		}		
		public void clear(){
			handlers.clear();
		}
		public Handler<C> get(int index){
			return handlers.get(index);
		}
		public int size(){
			return handlers.size();
		}
		public boolean isEmpty(){
			return handlers.isEmpty();
		}
	}
}
