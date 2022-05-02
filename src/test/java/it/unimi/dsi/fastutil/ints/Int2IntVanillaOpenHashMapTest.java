package it.unimi.dsi.fastutil.ints;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Int2IntVanillaOpenHashMapTest {

	@Test
	public void test(){
		Int2IntVanillaOpenHashMap map = new Int2IntVanillaOpenHashMap(1000);
		
		map.put(1000, 1001);
		assertEquals(1001, map.get(1000));
	}
}
