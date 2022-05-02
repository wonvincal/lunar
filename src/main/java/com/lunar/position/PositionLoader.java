package com.lunar.position;

import com.lunar.entity.SidManager;

public interface PositionLoader {
    public class Position {
        private long secSid;
        private long openPosition;
        
        public static Position of(final long secSid, final long openPosition) {
            return new Position(secSid, openPosition);
        }
        
        private Position(final long secSid, final long openPosition) {
            this.secSid = secSid;
            this.openPosition = openPosition;
        }
        
        public long secSid() {
            return this.secSid;
        }
        
        public long openPosition() {
            return this.openPosition;
        }
    }

    public interface Loadable<T> {
        void loadInfo(T obj);
    }
    
    void loadPositions(final SidManager<String> securitySidManager, final Loadable<Position> loadable) throws Exception;

}
