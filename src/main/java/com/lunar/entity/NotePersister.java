package com.lunar.entity;

import com.lunar.message.io.sbe.NoteSbeDecoder;

public interface NotePersister {
	Note insertNote(final NoteSbeDecoder note) throws Exception;
	Note updateNote(final NoteSbeDecoder note, final Note entity) throws Exception;
    public static final NotePersister NullPersister = new NotePersister() {
		@Override
		public Note insertNote(NoteSbeDecoder note) throws Exception { return null; }
		public Note updateNote(NoteSbeDecoder note, final Note entity) throws Exception { return null; }
		public void close(){}
	};
	void close();
}

