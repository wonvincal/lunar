package com.lunar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.database.LunarQueries;
import com.lunar.database.ResilientDbConnection;
import com.lunar.entity.DbNotePersister;
import com.lunar.entity.Note;
import com.lunar.entity.SidManager;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeEncoder;
import com.lunar.service.ServiceConstant;

@RunWith(MockitoJUnitRunner.class)
public class MySqlConnectionTest {
	@Test
	public void testMySqlConnection() throws SQLException {
		ResilientDbConnection connection = new ResilientDbConnection("jdbc:mysql://localhost/pentagon?user=paris&password=h1lt0nXXXyay");
		ResultSet rs = connection.executeQuery("select name from instrument_type");
		if (rs != null) {
			while (rs.next()) {
				@SuppressWarnings("unused")
                String name = rs.getString(1);
			}
			connection.closeQuery(rs);
		}
	}
	
	@Test
	@Ignore
	public void testSelectNote() throws SQLException {
		ResilientDbConnection connection = new ResilientDbConnection("jdbc:mysql://192.168.1.225/pentagon?user=paris&password=h1lt0nXXXyay");
				
		PreparedStatement selectNote = connection.prepareStatement(LunarQueries.SELECT_NOTE_QUERY);		
		selectNote.setLong(1, 3);
		ResultSet rs = selectNote.executeQuery();
		assertTrue(rs.next());		
	}
	
	@Test
	@Ignore
	public void testInsertNote() throws Exception {
		ResilientDbConnection connection = new ResilientDbConnection("jdbc:mysql://192.168.1.225/pentagon?user=paris&password=h1lt0nXXXyay");
		
		RealSystemClock systemClock = new RealSystemClock();
		
		SidManager<String> securitySidManager = new SidManager<>(128, 1);
		securitySidManager.setSidForKey(11682, "11682");
		DbNotePersister persister = new DbNotePersister(systemClock, connection, securitySidManager);
		
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		NoteSbeEncoder sbe = new NoteSbeEncoder();
		long entitySid = 11682;
		sbe.wrap(buffer, 0)
			.entitySid(entitySid)
			.isDeleted(BooleanType.FALSE)
			.isArchived(BooleanType.FALSE)
			.description("Test Insert New");
		
		NoteSbeDecoder noteSbe = new NoteSbeDecoder();
		noteSbe.wrap(buffer, 0, NoteSbeDecoder.BLOCK_LENGTH, NoteSbeDecoder.SCHEMA_VERSION);
		int origLimit = noteSbe.limit();
		
		Note note = persister.insertNote(noteSbe);
		assertNotNull(note);
		System.out.println("Note: " + note);
		noteSbe.limit(origLimit);
		note = persister.insertNote(noteSbe);
		//PreparedStatement insertNote = connection.prepareStatement(LunarQueries.INSERT_NOTE_QUERY);
		//selectNote.setLong(1, 1);
		//ResultSet rs = selectNote.executeQuery();
		//assertFalse(rs.next());		
	} 

	@Test
	@Ignore
	public void testUpdateNote() throws Exception {
		ResilientDbConnection connection = new ResilientDbConnection("jdbc:mysql://192.168.1.225/pentagon?user=paris&password=h1lt0nXXXyay");
		
		RealSystemClock systemClock = new RealSystemClock();
		SidManager<String> securitySidManager = new SidManager<>(128, 1);
		securitySidManager.setSidForKey(22223, "11682");
		DbNotePersister persister = new DbNotePersister(systemClock, connection, securitySidManager);
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		
		NoteSbeEncoder sbe = new NoteSbeEncoder();
		int sid = 5;
		long entitySid = 22223;
		sbe.wrap(buffer, 0)
			.noteSid(sid)
			.entitySid(entitySid)
			.isDeleted(BooleanType.FALSE)
			.isArchived(BooleanType.TRUE)
			.description("Test Update");
		
		NoteSbeDecoder noteSbe = new NoteSbeDecoder();
		noteSbe.wrap(buffer, 0, NoteSbeDecoder.BLOCK_LENGTH, NoteSbeDecoder.SCHEMA_VERSION);
		
		
		Note note = Note.of(sid, entitySid, ServiceConstant.NULL_INT_DATE, ServiceConstant.NULL_TIME_NS, ServiceConstant.NULL_INT_DATE, ServiceConstant.NULL_TIME_NS, BooleanType.FALSE, BooleanType.FALSE, "Test Insert");
		persister.updateNote(noteSbe, note);
		assertNotNull(note);
		assertEquals(BooleanType.FALSE, note.isDeleted());
		assertEquals(BooleanType.TRUE, note.isArchived());
		assertNotEquals(ServiceConstant.NULL_TIME_NS, note.createTime());
		System.out.println("Note: " + note);
	}
}
