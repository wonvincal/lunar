package com.lunar.entity;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.util.DateUtil;

public class RawToNoteConverter implements EntityConverter<Note> {
	private static final int SID_INDEX = 1;
	private static final int SEC_SYMBOL_INDEX = 2;
//	private static final int ENTITY_TYPE_SID_INDEX = 2;
	private static final int CREATE_TIME_INDEX = 3;
	private static final int MODIFY_TIME_INDEX = 4;
	private static final int IS_DELETED_INDEX = 5;
	private static final int IS_ARCHIVED_INDEX = 6;
	private static final int DESCRIPTION_INDEX = 7;
	private final SidManager<String> securitySidManager;
	
	public RawToNoteConverter(SidManager<String> securitySidManager) {
		this.securitySidManager = securitySidManager;
	}
	
	@Override
	public Note toEntity(ResultSet rs) throws SQLException {
	    final long sid = rs.getInt(SID_INDEX);
	    String secSymbol = rs.getString(SEC_SYMBOL_INDEX);
	    long entitySid = this.securitySidManager.getSidForKey(secSymbol);
	    LocalDate createDate = rs.getDate(CREATE_TIME_INDEX).toLocalDate();
	    LocalTime createTime = rs.getTime(CREATE_TIME_INDEX).toLocalTime();
	    LocalDate modifyDate = rs.getDate(MODIFY_TIME_INDEX).toLocalDate();
	    LocalTime modifyTime = rs.getTime(MODIFY_TIME_INDEX).toLocalTime();
	    boolean isDeleted = rs.getBoolean(IS_DELETED_INDEX);
	    boolean isArchived = rs.getBoolean(IS_ARCHIVED_INDEX);
	    String desc = rs.getString(DESCRIPTION_INDEX);
		return Note.of(sid, 
				entitySid,
				DateUtil.toIntDate(createDate),
				createTime.toNanoOfDay(),
				DateUtil.toIntDate(modifyDate),
				modifyTime.toNanoOfDay(), 
				isDeleted ? BooleanType.TRUE: BooleanType.FALSE, 
				isArchived ? BooleanType.TRUE: BooleanType.FALSE, 
				desc);
	}

	@Override
	public Note toEntity(String csv) {
		throw new UnsupportedOperationException();	}

	@Override
	public Note toEntity(ByteBuffer buffer) {
		throw new UnsupportedOperationException();	}
}
