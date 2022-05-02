package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.NoteSbeDecoder;


public class NoteFbsEncoder {
	static final Logger LOG = LogManager.getLogger(NoteFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, NoteSbeDecoder note){
		int limit = note.limit();
		
		stringBuffer.clear();
		stringBuffer.limit(note.getDescription(stringBuffer.array(), 0, note.descriptionLength()));
		int descriptionOffset = builder.createString(stringBuffer);
		
		NoteFbs.startNoteFbs(builder);
		NoteFbs.addNoteSid(builder, note.noteSid());
		NoteFbs.addCreateDate(builder, note.createDate());
		NoteFbs.addCreateTime(builder, note.createTime());
		NoteFbs.addUpdateDate(builder, note.updateDate());
		NoteFbs.addUpdateTime(builder, note.updateTime());
		NoteFbs.addEntitySid(builder, note.entitySid());
		NoteFbs.addIsArchived(builder, note.isArchived() == BooleanType.TRUE);
		NoteFbs.addIsDeleted(builder, note.isDeleted() == BooleanType.TRUE);
		NoteFbs.addDescription(builder, descriptionOffset);
		note.limit(limit);
		return NoteFbs.endNoteFbs(builder);
	}

}
