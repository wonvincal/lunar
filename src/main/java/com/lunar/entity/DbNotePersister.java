package com.lunar.entity;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SystemClock;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.util.DateUtil;

public class DbNotePersister implements NotePersister {
	private static final Logger LOG = LogManager.getLogger(DbNotePersister.class);
	private static final int SELECT_SID_INDEX = 1;
	private static final int SELECT_SEC_SYMBOL_INDEX = 2;
	private static final int SELECT_ENTITY_TYPE_SID_INDEX = 3;
	private static final int SELECT_CREATE_TIME_INDEX = 4;
	private static final int SELECT_MODIFY_TIME_INDEX = 5;
	private static final int SELECT_IS_DELETED_INDEX = 6;
	private static final int SELECT_IS_ARCHIVED_INDEX = 7;
	private static final int SELECT_DESCRIPTION_INDEX = 8;

	private static final int INSERT_ENTITY_TYPE_SID_INDEX = 1;
	private static final int INSERT_IS_DELETED_INDEX = 2;
	private static final int INSERT_IS_ARCHIVED_INDEX = 3;
	private static final int INSERT_DESCRIPTION_INDEX = 4;
	private static final int INSERT_SEC_SYMBOL_INDEX = 5;
	private static final int INSERT_LISTED_DATE_INDEX = 6;
	private static final int INSERT_DELISTED_DATE_INDEX = 7;	
	
	private final SystemClock systemClock;
    private final DbConnection connection;
	private final SidManager<String> securitySidManager;
    private PreparedStatement selectNote;
    private PreparedStatement insertNote;
    private PreparedStatement updateNote;

    public DbNotePersister(final SystemClock systemClock, final DbConnection connection, SidManager<String> securitySidManager) {
        this.systemClock = systemClock;
        this.connection = connection;
        this.securitySidManager = securitySidManager;
    }

    @Override
    public void close() {
    	try {
    		insertNote.close();
		} 
    	catch (SQLException e) {
			LOG.error("Could not close insertNote prepared statement", e);
		}
    	try {
			updateNote.close();
		}
    	catch (SQLException e) {
			LOG.error("Could not close updateNote prepared statement", e);
		}
    }

    @Override
	public Note insertNote(NoteSbeDecoder note) throws Exception {
    	long sid;
    	try {
    		String secSymbol = securitySidManager.getKeyForSid(note.entitySid());
    		
    		insertNote = connection.prepareStatement(LunarQueries.INSERT_NOTE_QUERY, Statement.RETURN_GENERATED_KEYS);
    		
    		insertNote.setInt(INSERT_ENTITY_TYPE_SID_INDEX, EntityType.SECURITY.value());
    		insertNote.setInt(INSERT_IS_DELETED_INDEX, (note.isDeleted() == BooleanType.FALSE || note.isDeleted() == BooleanType.NULL_VAL) ? 0 : 1);
    		insertNote.setInt(INSERT_IS_ARCHIVED_INDEX, (note.isArchived() == BooleanType.FALSE || note.isArchived() == BooleanType.NULL_VAL) ? 0 : 1);
    		insertNote.setString(INSERT_DESCRIPTION_INDEX, note.description());
    		insertNote.setString(INSERT_SEC_SYMBOL_INDEX, secSymbol);
    		
    		Date today = Date.valueOf(systemClock.date());
			insertNote.setDate(INSERT_LISTED_DATE_INDEX, today);
			insertNote.setDate(INSERT_DELISTED_DATE_INDEX, today);
			
    		int affectedRows = insertNote.executeUpdate();
    		if (affectedRows == 0){
    			throw new SQLException("Create note failed, no rows affected");
    		}
    		try (ResultSet generatedKeys = insertNote.getGeneratedKeys()){
    			if (generatedKeys.next()){
    				sid = generatedKeys.getLong(1);
    			}
    			else {
    				throw new SQLException("Create note failed, no ID obtained");
    			}
    		}
    	}
    	catch (SQLException e){
			LOG.error("Could not execute insertNote prepared statement", e);
			return null;
    	}
    	finally {
    		insertNote.close();
    	}
    	try {
            selectNote = connection.prepareStatement(LunarQueries.SELECT_NOTE_QUERY);        		
    		selectNote.setLong(1, sid);
    		ResultSet rs = selectNote.executeQuery();
    		if (!rs.next()){
    			LOG.error("Could not select note after insert, nothing from resultset [noteSid:{}]", sid);
    			return null;
    		}
    	    String secSymbol = rs.getString(SELECT_SEC_SYMBOL_INDEX);
    	    final long entitySid = securitySidManager.getSidForKey(secSymbol);
    	    LocalDateTime createDate = rs.getTimestamp(SELECT_CREATE_TIME_INDEX).toLocalDateTime();
    	    LocalDateTime modifyDate = rs.getTimestamp(SELECT_MODIFY_TIME_INDEX).toLocalDateTime();
    	    boolean isDeleted = rs.getBoolean(SELECT_IS_DELETED_INDEX);
    	    boolean isArchived = rs.getBoolean(SELECT_IS_ARCHIVED_INDEX);
    	    String desc = rs.getString(SELECT_DESCRIPTION_INDEX);
    		return Note.of(sid, 
    				entitySid,
    				DateUtil.getIntDate(createDate),
    				DateUtil.getNanoOfDay(createDate),
    				DateUtil.getIntDate(modifyDate),
    				DateUtil.getNanoOfDay(modifyDate),
    				isDeleted ? BooleanType.TRUE: BooleanType.FALSE, 
    				isArchived ? BooleanType.TRUE: BooleanType.FALSE, 
    				desc);
    	}
    	catch (SQLException e){
			LOG.error("Could not select note after insert [noteSid:{}]", sid, e);
			return null;
    	}
    	finally {
    		selectNote.close();
    	}
    }
    

    
	@Override
	public Note updateNote(NoteSbeDecoder note, Note entity) throws Exception {
		try {
	        updateNote = connection.prepareStatement(LunarQueries.UPDATE_NOTE_QUERY);
    		updateNote.setInt(1, (note.isDeleted() == BooleanType.FALSE || note.isDeleted() == BooleanType.NULL_VAL) ? 0 : 1);			
    		updateNote.setInt(2, (note.isArchived() == BooleanType.FALSE || note.isArchived() == BooleanType.NULL_VAL) ? 0 : 1);
    		updateNote.setString(3, note.description());
    		updateNote.setLong(4, note.noteSid());
    		if (updateNote.executeUpdate() != 1){
				throw new SQLException("Update note failed [noteSid:" + note.noteSid() + "]");
    		}
		}
		catch (SQLException e){
			LOG.error("Could not execute updateNote prepared statement", e);
			return null;			
		}
		finally {
			updateNote.close();
		}
    	try {
    		selectNote = connection.prepareStatement(LunarQueries.SELECT_NOTE_QUERY);
    		selectNote.setLong(1, note.noteSid());
    		ResultSet rs = selectNote.executeQuery();
    		if (!rs.next()){
    			LOG.error("Could not select note after update, nothing from resultset [noteSid:{}]", note.noteSid());
    			return null;
    		}
    		LocalDate createDate = rs.getDate(SELECT_CREATE_TIME_INDEX).toLocalDate();
    	    LocalTime createTime = rs.getTime(SELECT_CREATE_TIME_INDEX).toLocalTime();
    	    LocalDate modifyDate = rs.getDate(SELECT_MODIFY_TIME_INDEX).toLocalDate();
    	    LocalTime modifyTime = rs.getTime(SELECT_MODIFY_TIME_INDEX).toLocalTime();
    	    boolean isDeleted = rs.getBoolean(SELECT_IS_DELETED_INDEX);
    	    boolean isArchived = rs.getBoolean(SELECT_IS_ARCHIVED_INDEX);
    	    String desc = rs.getString(SELECT_DESCRIPTION_INDEX);
    	    
    	    return entity.createDate(DateUtil.toIntDate(createDate))
    	    	.createTime(createTime.toNanoOfDay())
    	    	.updateDate(DateUtil.toIntDate(modifyDate))
    	    	.updateTime(modifyTime.toNanoOfDay())
    	    	.isDeleted(isDeleted ? BooleanType.TRUE: BooleanType.FALSE)
    	    	.isArchived(isArchived ? BooleanType.TRUE: BooleanType.FALSE)
    	    	.description(desc);
    	}
    	catch (SQLException e){
			LOG.error("Could not select note after update [noteSid:{}]", note.noteSid(), e);
			return null;
    	}
    	finally {
    		selectNote.close();
    	}
	}
}
