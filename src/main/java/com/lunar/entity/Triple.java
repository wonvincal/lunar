package com.lunar.entity;

public class Triple<U, V, W> implements GroupKey {
	private final U first;
	private final V second;
	private final W third;
	public Triple(U first, V second, W third){
		this.first = first;
		this.second = second;
		this.third = third;
	}
	public U getFirst() {
		return first;
	}
	public V getSecond() {
		return second;
	}
	public W getThird() {
		return third;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((first == null) ? 0 : first.hashCode());
		result = prime * result + ((second == null) ? 0 : second.hashCode());
		result = prime * result + ((third == null) ? 0 : third.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Triple))
			return false;
		@SuppressWarnings("rawtypes")
		Triple other = (Triple) obj;
		if (first == null) {
			if (other.first != null)
				return false;
		} else if (!first.equals(other.first))
			return false;
		if (second == null) {
			if (other.second != null)
				return false;
		} else if (!second.equals(other.second))
			return false;
		if (third == null) {
			if (other.third != null)
				return false;
		} else if (!third.equals(other.third))
			return false;
		return true;
	}
	public static <U, V, W> Triple<U, V, W> of(U first, V second, W third){
		return new Triple<U, V, W>(first, second, third);
	}
	
}
